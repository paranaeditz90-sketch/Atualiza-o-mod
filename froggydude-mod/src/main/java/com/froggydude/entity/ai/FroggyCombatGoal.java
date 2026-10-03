package com.froggydude.entity.ai;

import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.PinTracker;
import com.froggydude.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * O "cérebro" de combate. A regra principal: só começa um ataque se ele tem
 * chance real de acertar (alcance, linha de visão, alvo no chão e parado o
 * bastante pros pulos). Se nenhum ataque serve, ele persegue: de longe, aos
 * saltos de sapo (como nos vídeos); de perto, correndo de quatro.
 * Cada ataque tem seu próprio tempo de recarga, e nunca repete o último.
 * Quando um pulo acerta, ele derruba a vítima e fica montado em cima, mordendo.
 */
public class FroggyCombatGoal extends Goal {

    private static final FroggyState[] ATTACKS = {
            FroggyState.BITE, FroggyState.TONGUE_WHIP, FroggyState.TONGUE_GRAB,
            FroggyState.TONGUE_CAPTURE, FroggyState.JUMP_PIN, FroggyState.HIGH_JUMP};

    // Física do Minecraft pros pulos: y += vy; vy = (vy - 0.08) * 0.98; no ar vx *= 0.91
    // (o primeiro tick, ainda no chão, usa atrito 0.546). Valores calculados pra
    // o tempo de voo bater com o frame de impacto da animação.
    private static final double AIR_FRICTION = 0.91D;
    private static final double GROUND_FRICTION = 0.546D;
    private static final double JUMP_PIN_VY = 0.40D;    // 11 ticks no ar, ~1,1 bloco de altura
    private static final int JUMP_PIN_AIR = 11;
    private static final int JUMP_PIN_LAUNCH = 4;
    private static final double HIGH_JUMP_VY = 0.62D;   // 16 ticks no ar, ~2,5 blocos de altura
    private static final int HIGH_JUMP_AIR = 16;
    private static final int HIGH_JUMP_LAUNCH = 6;

    // Salto de sapo da perseguição (referência: o vídeo contra o Parallax).
    private static final double LEAP_VY = 0.42D;        // 12 ticks no ar, ~1,25 bloco de altura
    private static final int LEAP_AIR = 12;
    private static final int LEAP_LAUNCH = 5;
    private static final double LEAP_MIN_DIST = 7.0D;   // mais perto que isso: corre de quatro
    private static final double LEAP_MAX_DIST = 24.0D;
    private static final double LEAP_MAX_JUMP = 7.5D;   // blocos por salto
    private static final double LEAP_STOP_SHORT = 3.0D; // cai antes do alvo, pra emendar o bote

    // Montado na vítima
    private static final double PIN_DISTANCE = 2.0D;    // fica na frente do rosto dela, não dentro
    private static final int PIN_FIRST_BITE = 10;
    private static final int PIN_BITE_EVERY = 14;
    private static final float PIN_BITE_DAMAGE = 2.5F;

    private final FroggydudeEntity froggy;

    private LivingEntity trackedTarget;
    private Vec3 lastTargetPos;
    private Vec3 targetVel = Vec3.ZERO;
    private int repathTicks;
    private int leapCooldown;
    private boolean fired1;
    private boolean fired2;
    private boolean launched;
    private int pinTicks;

    public FroggyCombatGoal(FroggydudeEntity froggy) {
        this.froggy = froggy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    /** Sem isso o Minecraft só roda a Goal a cada 2 ticks e o timing dos golpes falha. */
    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        LivingEntity target = froggy.getTarget();
        return target != null && target.isAlive() && !froggy.isVulnerableState();
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = froggy.getTarget();
        return target != null && target.isAlive()
                && (froggy.getFroggyState() == FroggyState.CHASE || froggy.getFroggyState().isCombatAction());
    }

    @Override
    public void start() {
        repathTicks = 0;
        leapCooldown = 0;
        if (froggy.getFroggyState() == FroggyState.IDLE) {
            froggy.setFroggyState(FroggyState.CHASE);
        }
    }

    @Override
    public void stop() {
        releaseVictim();
        if (froggy.getFroggyState().isCombatAction()) {
            froggy.setFroggyState(FroggyState.CHASE);
        }
        froggy.setTongueLength(0F);
    }

    @Override
    public void tick() {
        LivingEntity target = froggy.getTarget();
        if (target == null) return;
        // teste com ataque forçado: espera o jogador terminar de entrar no mundo
        if (froggy.getDebugAttack() != null && froggy.tickCount < 60) return;

        trackTarget(target);
        if (leapCooldown > 0) leapCooldown--;
        double dist = froggy.distanceTo(target);
        FroggyState state = froggy.getFroggyState();

        if (state == FroggyState.PIN_HOLD) {
            runPinHold(target, dist);
            return;
        }
        if (state == FroggyState.LEAP) {
            runLeap(target);
            return;
        }
        if (state.isAttack()) {
            runAttack(state, target, dist);
            return;
        }
        if (state != FroggyState.CHASE) {
            froggy.setFroggyState(FroggyState.CHASE);
        }

        froggy.getLookControl().setLookAt(target, 40.0F, 40.0F);

        if (froggy.getAttackCooldown() <= 0) {
            FroggyState next = pickAttack(target, dist);
            if (next != null) {
                beginAttack(next, target, dist);
                return;
            }
        }

        // longe: salta como sapo; perto: corre de quatro (navegação normal)
        if (leapCooldown <= 0 && canLeap(target, dist)) {
            beginLeap(target);
            return;
        }
        if (dist > 2.0D) {
            if (--repathTicks <= 0) {
                repathTicks = 4;
                froggy.getNavigation().moveTo(target, 1.0D);
            }
        } else {
            froggy.getNavigation().stop();
        }
    }

    // ---------------------------------------------------------------- decisão

    /** Velocidade do alvo por tick (suavizada), pra prever onde ele vai estar. */
    private void trackTarget(LivingEntity target) {
        Vec3 pos = target.position();
        if (target != trackedTarget) {
            trackedTarget = target;
            lastTargetPos = null;
            targetVel = Vec3.ZERO;
        }
        if (lastTargetPos != null) {
            targetVel = targetVel.scale(0.5D).add(pos.subtract(lastTargetPos).scale(0.5D));
        }
        lastTargetPos = pos;
    }

    private FroggyState pickAttack(LivingEntity target, double dist) {
        if (!froggy.hasLineOfSight(target)) return null;

        List<FroggyState> valid = new ArrayList<>();
        FroggyState only = froggy.getDebugAttack();
        for (FroggyState s : ATTACKS) {
            if (only != null && s != only) continue;
            if (froggy.isReady(s) && canHit(s, target, dist)) {
                valid.add(s);
            }
        }
        if (valid.isEmpty()) return null;
        // nunca repete o último ataque, se houver outra opção
        if (valid.size() > 1) {
            valid.remove(froggy.getLastAttack());
        }
        return valid.get(froggy.getRandom().nextInt(valid.size()));
    }

    private boolean canHit(FroggyState s, LivingEntity target, double dist) {
        switch (s) {
            case BITE:
                return dist <= 2.6D;
            case TONGUE_WHIP:
                return dist >= 2.8D && dist <= 8.0D;
            case TONGUE_GRAB:
                return dist >= 4.0D && dist <= 8.0D;
            case TONGUE_CAPTURE:
                return dist >= 5.0D && dist <= 10.0D && target.onGround();
            case JUMP_PIN:
                return dist >= 3.5D && dist <= 7.0D && canPounce(target, JUMP_PIN_LAUNCH + JUMP_PIN_AIR, 7.5D);
            case HIGH_JUMP:
                return dist >= 5.0D && dist <= 9.0D && canPounce(target, HIGH_JUMP_LAUNCH + HIGH_JUMP_AIR, 10.0D);
            default:
                return false;
        }
    }

    /** O alvo está no chão, na mesma altura e não está correndo pra longe? */
    private boolean canPounce(LivingEntity target, int totalTicks, double maxReach) {
        if (!target.onGround()) return false;
        if (Math.abs(target.getY() - froggy.getY()) > 1.4D) return false;
        double speed = Math.sqrt(targetVel.x * targetVel.x + targetVel.z * targetVel.z);
        if (speed > 0.25D) return false;
        Vec3 aim = target.position().add(targetVel.x * totalTicks, 0, targetVel.z * totalTicks);
        double dx = aim.x - froggy.getX();
        double dz = aim.z - froggy.getZ();
        return Math.sqrt(dx * dx + dz * dz) <= maxReach;
    }

    private void beginAttack(FroggyState s, LivingEntity target, double dist) {
        fired1 = false;
        fired2 = false;
        launched = false;
        froggy.faceTarget(target);
        froggy.getNavigation().stop();
        froggy.setFroggyState(s);
        switch (s) {
            case TONGUE_WHIP:
            case TONGUE_GRAB:
                froggy.setTongueLength(tongueLength(target, 8.0D));
                froggy.onTongueOut();
                break;
            case TONGUE_CAPTURE:
                froggy.setTongueLength(tongueLength(target, 10.0D));
                froggy.onTongueOut();
                break;
            default:
                froggy.setTongueLength(0F);
                break;
        }
    }

    /** Da boca dele até o peito do alvo (em 3D), um pouco além pra "entrar" no corpo. */
    private float tongueLength(LivingEntity target, double max) {
        Vec3 mouth = froggy.position().add(0, froggy.getBbHeight() * 0.88D, 0);
        Vec3 chest = target.position().add(0, target.getBbHeight() * 0.6D, 0);
        return (float) Math.min(mouth.distanceTo(chest), max) + 0.25F;
    }

    // ---------------------------------------------------------------- salto de sapo

    /**
     * Dá pra saltar? Alvo longe mas não demais, quase na mesma altura, à vista,
     * e o caminho até o ponto de queda está livre (nada de bater em parede ou
     * cair em buraco).
     */
    private boolean canLeap(LivingEntity target, double dist) {
        if (!froggy.onGround() || froggy.isInWater()) return false;
        if (dist < LEAP_MIN_DIST || dist > LEAP_MAX_DIST) return false;
        if (Math.abs(target.getY() - froggy.getY()) > 2.0D) return false;
        if (!froggy.hasLineOfSight(target)) return false;
        double dx = target.getX() - froggy.getX();
        double dz = target.getZ() - froggy.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        double jump = Math.min(LEAP_MAX_JUMP, flat - LEAP_STOP_SHORT);
        return jump >= 3.0D && pathClear(dx / flat, dz / flat, jump);
    }

    private boolean pathClear(double nx, double nz, double length) {
        Level level = froggy.level();
        double y = froggy.getY();
        for (double d = 1.0D; d <= length + 0.01D; d += 1.0D) {
            double x = froggy.getX() + nx * d;
            double z = froggy.getZ() + nz * d;
            // corpo (3 blocos de altura no arco) precisa estar livre
            for (int h = 0; h <= 2; h++) {
                BlockPos p = BlockPos.containing(x, y + 0.2D + h, z);
                if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) return false;
            }
        }
        // onde ele cai tem chão (no máximo 1 bloco abaixo)
        BlockPos land = BlockPos.containing(froggy.getX() + nx * length, y - 0.5D, froggy.getZ() + nz * length);
        BlockPos below = land.below();
        return !level.getBlockState(land).getCollisionShape(level, land).isEmpty()
                || !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }

    private void beginLeap(LivingEntity target) {
        launched = false;
        froggy.faceTarget(target);
        froggy.getNavigation().stop();
        froggy.setFroggyState(FroggyState.LEAP);
    }

    private void runLeap(LivingEntity target) {
        int t = froggy.getStateTicks();
        if (t < LEAP_LAUNCH) {
            froggy.faceTarget(target);
            froggy.getLookControl().setLookAt(target, 40.0F, 40.0F);
        }
        if (!launched && t >= LEAP_LAUNCH) {
            launched = true;
            if (!froggy.onGround()) {
                endLeap();
                return;
            }
            double dx = target.getX() - froggy.getX();
            double dz = target.getZ() - froggy.getZ();
            double flat = Math.sqrt(dx * dx + dz * dz);
            double jump = Math.max(2.0D, Math.min(LEAP_MAX_JUMP, flat - LEAP_STOP_SHORT));
            double vh = jump / horizontalFactor(LEAP_AIR);
            froggy.faceDirection(dx, dz);
            froggy.setDeltaMovement(dx / flat * vh, LEAP_VY, dz / flat * vh);
            froggy.setNoFallTicks(60);
            froggy.hasImpulse = true;
            froggy.onLeapLaunch();
        }
        if (t >= FroggyState.LEAP.durationTicks) {
            endLeap();
        }
    }

    private void endLeap() {
        froggy.setFroggyState(FroggyState.CHASE);
        // emenda o próximo salto logo depois de juntar as pernas
        leapCooldown = 2 + froggy.getRandom().nextInt(5);
        repathTicks = 0;
    }

    private static double horizontalFactor(int airTicks) {
        return 1.0D + GROUND_FRICTION * (1.0D - Math.pow(AIR_FRICTION, airTicks - 1)) / (1.0D - AIR_FRICTION);
    }

    // ---------------------------------------------------------------- execução

    private void runAttack(FroggyState state, LivingEntity target, double dist) {
        int t = froggy.getStateTicks();

        switch (state) {
            case BITE:
                if (t < 6) froggy.faceTarget(target);
                if (!fired1 && t >= 6) {
                    fired1 = true;
                    if (dist <= 3.0D && froggy.hasLineOfSight(target)) {
                        hurt(target, 8.0F);
                        froggy.spawnBlood(target, 8);
                        froggy.onBiteHit();
                    }
                }
                break;

            case TONGUE_WHIP:
                if (t < 8) aimTongue(target, 8.0D);
                if (!fired1 && t >= 8) {
                    fired1 = true;
                    if (tongueReaches(target, dist, 8.5D)) {
                        hurt(target, 6.0F);
                        push(target, 1.4D, 0.3D);
                        froggy.spawnBlood(target, 6);
                        froggy.onTongueTaste();
                    }
                }
                break;

            case TONGUE_GRAB:
                if (t < 10) aimTongue(target, 8.0D);
                if (!fired1 && t >= 10) {
                    fired1 = true;
                    if (tongueReaches(target, dist, 8.5D)) {
                        pull(target, 1.3D);
                        hurt(target, 3.0F);
                        froggy.onTongueTaste();
                    }
                }
                break;

            case TONGUE_CAPTURE:
                if (t < 12) aimTongue(target, 10.0D);
                if (!fired1 && t >= 12) {
                    fired1 = true;
                    if (tongueReaches(target, dist, 10.5D)) {
                        // agarra com a língua e puxa até a boca (sem derrubar ainda)
                        pull(target, 1.1D);
                        hold(target, 20);
                        shake(target, 0.6F, 14);
                        froggy.spawnBlood(target, 4);
                        froggy.onTongueTaste();
                    } else {
                        fired2 = true; // errou a língua: não tem mordida
                    }
                }
                if (t >= 12 && t < 20) froggy.faceTarget(target);
                if (!fired2 && t >= 20) {
                    fired2 = true;
                    if (dist <= 3.5D) {
                        hurt(target, 5.0F);
                        froggy.spawnBlood(target, 6);
                        froggy.onBiteHit();
                    }
                }
                break;

            case JUMP_PIN:
                if (t < JUMP_PIN_LAUNCH) froggy.faceTarget(target);
                if (!launched && t >= JUMP_PIN_LAUNCH) {
                    launched = true;
                    if (!launch(target, JUMP_PIN_VY, JUMP_PIN_AIR, 2.5D, 7.5D)) {
                        abort(state);
                        return;
                    }
                }
                if (launched && !fired1 && landed(t, JUMP_PIN_LAUNCH, state)) {
                    fired1 = true;
                    debug("bote pousou: t=" + t + " dist=" + froggy.distanceTo(target) + " chao=" + froggy.onGround());
                    if (froggy.distanceTo(target) <= 3.0D) {
                        // acertou: derruba e monta em cima
                        hurtNoKnockback(target, 6.0F);
                        froggy.spawnBlood(target, 5);
                        pinVictim(state, target, 50, 1.2F);
                        return;
                    }
                }
                break;

            case HIGH_JUMP:
                if (t < HIGH_JUMP_LAUNCH) froggy.faceTarget(target);
                if (!launched && t >= HIGH_JUMP_LAUNCH) {
                    launched = true;
                    if (!launch(target, HIGH_JUMP_VY, HIGH_JUMP_AIR, 3.5D, 10.0D)) {
                        abort(state);
                        return;
                    }
                }
                if (launched && !fired1 && landed(t, HIGH_JUMP_LAUNCH, state)) {
                    fired1 = true;
                    debug("salto alto pousou: t=" + t + " dist=" + froggy.distanceTo(target) + " chao=" + froggy.onGround());
                    if (froggy.distanceTo(target) <= 3.4D) {
                        // impacto forte: derruba e monta em cima
                        hurtNoKnockback(target, 8.0F);
                        froggy.spawnBlood(target, 6);
                        pinVictim(state, target, 44, 1.6F);
                        return;
                    }
                }
                break;

            default:
                break;
        }

        if (t >= state.durationTicks) {
            finishAttack(state);
        }
    }

    /** Enquanto a língua ainda "carrega", ele continua virado pro alvo e mira no peito. */
    private void aimTongue(LivingEntity target, double max) {
        froggy.faceTarget(target);
        froggy.getLookControl().setLookAt(target.getX(), target.getY() + target.getBbHeight() * 0.6D,
                target.getZ(), 90.0F, 90.0F);
        froggy.setTongueLength(tongueLength(target, max));
    }

    private boolean tongueReaches(LivingEntity target, double dist, double max) {
        if (dist > max || !froggy.hasLineOfSight(target)) return false;
        Vec3 look = froggy.getViewVector(1.0F);
        Vec3 to = target.position().subtract(froggy.position());
        double lookLen = Math.sqrt(look.x * look.x + look.z * look.z);
        double toLen = Math.sqrt(to.x * to.x + to.z * to.z);
        if (lookLen < 1.0E-4D || toLen < 0.3D) return true;
        double dot = (look.x * to.x + look.z * to.z) / (lookLen * toLen);
        return dot > 0.7D;
    }

    /**
     * Lança o Froggy num arco que termina em cima da posição PREVISTA do alvo.
     * Devolve false (sem pular) se o alvo se mexeu e não dá mais pra acertar.
     */
    private boolean launch(LivingEntity target, double vy0, int airTicks, double minD, double maxD) {
        if (!target.onGround() || !froggy.hasLineOfSight(target)) return false;
        if (Math.abs(target.getY() - froggy.getY()) > 1.4D) return false;

        Vec3 aim = target.position().add(targetVel.x * airTicks, 0, targetVel.z * airTicks);
        double dx = aim.x - froggy.getX();
        double dz = aim.z - froggy.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < minD || d > maxD) return false;

        // cai na frente do alvo, na distância de montar nele (nunca em cima: a
        // câmera do jogador entraria no modelo)
        double landDist = Math.max(1.0D, d - PIN_DISTANCE);
        double vh = landDist / horizontalFactor(airTicks);

        froggy.faceDirection(dx, dz);
        froggy.setDeltaMovement(dx / d * vh, vy0, dz / d * vh);
        froggy.setNoFallTicks(80);
        froggy.hasImpulse = true;
        return true;
    }

    private boolean landed(int t, int launchTick, FroggyState state) {
        return (froggy.onGround() && t >= launchTick + 5) || t >= state.durationTicks - 1;
    }

    private void abort(FroggyState state) {
        froggy.setCooldown(state, 30);
        froggy.setAttackCooldown(10);
        froggy.setFroggyState(FroggyState.CHASE);
        froggy.setTongueLength(0F);
    }

    private void finishAttack(FroggyState state) {
        froggy.setLastAttack(state);
        int cd = cooldownFor(state);
        froggy.setCooldown(state, froggy.isFrenzyActive() ? cd / 2 : cd);
        int gap = 12 + froggy.getRandom().nextInt(10);
        froggy.setAttackCooldown(froggy.isFrenzyActive() ? gap / 2 : gap);
        froggy.setFroggyState(FroggyState.CHASE);
        froggy.setTongueLength(0F);
    }

    private static int cooldownFor(FroggyState s) {
        switch (s) {
            case BITE:
                return 25;
            case TONGUE_WHIP:
                return 60;
            case TONGUE_GRAB:
                return 100;
            case TONGUE_CAPTURE:
                return 200;
            case JUMP_PIN:
                return 180;
            case HIGH_JUMP:
                return 240;
            default:
                return 40;
        }
    }

    // ---------------------------------------------------------------- montado na vítima

    /**
     * O pulo acertou: a vítima vai pro chão e ele fica em cima, na frente do
     * rosto dela (e não dentro dela, senão a câmera do jogador entra no modelo).
     */
    private void pinVictim(FroggyState jump, LivingEntity target, int ticks, float shake) {
        finishAttack(jump);

        // fica do lado em que caiu (sem "teletransportar" em volta da vítima),
        // só acerta a distância; a câmera do jogador vira pra ele de qualquer jeito
        Vec3 dir = froggy.position().subtract(target.position()).multiply(1, 0, 1);
        if (dir.lengthSqr() < 0.04D) {
            Vec3 look = target.getLookAngle();
            dir = new Vec3(look.x, 0, look.z);
        }
        dir = dir.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : dir.normalize();
        froggy.setPos(target.getX() + dir.x * PIN_DISTANCE, target.getY(), target.getZ() + dir.z * PIN_DISTANCE);
        froggy.setDeltaMovement(Vec3.ZERO);
        froggy.faceTarget(target);
        froggy.getNavigation().stop();

        if (target instanceof ServerPlayer player) {
            player.setSprinting(false);
            player.setDeltaMovement(0.0D, Math.min(player.getDeltaMovement().y, 0.0D), 0.0D);
            player.hurtMarked = true;
            PinTracker.pin(player, froggy, ticks);
            ModNetwork.sendEffects(player, shake, 18, ticks, froggy.getId());
        } else {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false));
        }
        froggy.setPinnedVictim(target);
        pinTicks = ticks;
        froggy.setFroggyState(FroggyState.PIN_HOLD);
        froggy.onPinStart();
    }

    private static void debug(String msg) {
        if (Boolean.getBoolean("froggydude.debug")) System.out.println("[FROGGYDEBUG] " + msg);
    }

    private void runPinHold(LivingEntity target, double dist) {
        int t = froggy.getStateTicks();
        LivingEntity victim = froggy.getPinnedVictim();
        boolean stillPinned = victim != null && victim.isAlive() && victim == target && dist <= 2.8D
                && (!(victim instanceof ServerPlayer p) || PinTracker.isPinnedBy(p, froggy));
        if (!stillPinned || t >= pinTicks) {
            debug("solta: t=" + t + "/" + pinTicks + " dist=" + dist + " preso=" + stillPinned);
            endPinHold();
            return;
        }

        froggy.getNavigation().stop();
        froggy.setDeltaMovement(0, froggy.getDeltaMovement().y, 0);
        froggy.faceTarget(victim);
        froggy.getLookControl().setLookAt(victim.getX(), victim.getEyeY(), victim.getZ(), 90.0F, 90.0F);
        keepPinDistance(victim);

        // morde a cada tantos ticks (o primeiro logo depois de cair em cima)
        if (t >= PIN_FIRST_BITE && (t - PIN_FIRST_BITE) % PIN_BITE_EVERY == 0) {
            hurtNoKnockback(victim, PIN_BITE_DAMAGE);
            froggy.spawnBlood(victim, 3);
            froggy.onBiteHit();
            shake(victim, 0.7F, 8);
        }
    }

    /** Se a vítima escorregou, ele se ajeita (ele vai até ela, não o contrário). */
    private void keepPinDistance(LivingEntity victim) {
        Vec3 dir = froggy.position().subtract(victim.position()).multiply(1, 0, 1);
        double d = dir.length();
        if (d < 1.0E-3D || Math.abs(d - PIN_DISTANCE) < 0.25D) return;
        Vec3 spot = victim.position().add(dir.scale(PIN_DISTANCE / d));
        froggy.setPos(spot.x, froggy.getY(), spot.z);
    }

    private void endPinHold() {
        releaseVictim();
        froggy.setAttackCooldown(15);
        froggy.setFroggyState(FroggyState.CHASE);
    }

    private void releaseVictim() {
        LivingEntity victim = froggy.getPinnedVictim();
        if (victim instanceof ServerPlayer player && PinTracker.isPinnedBy(player, froggy)) {
            PinTracker.release(player);
        }
        froggy.setPinnedVictim(null);
    }

    // ---------------------------------------------------------------- efeitos

    /** Ataques causam mais dano durante o frenesi. */
    private float dmg(float base) {
        return froggy.isFrenzyActive() ? base * 1.3F : base;
    }

    private void hurt(LivingEntity target, float base) {
        target.hurt(froggy.damageSources().mobAttack(froggy), dmg(base));
    }

    /** Dano sem o empurrão normal (vítima no chão fica no chão). */
    private void hurtNoKnockback(LivingEntity target, float base) {
        double kb = 0;
        net.minecraft.world.entity.ai.attributes.AttributeInstance res =
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        if (res != null) {
            kb = res.getBaseValue();
            res.setBaseValue(1.0D);
        }
        target.hurt(froggy.damageSources().mobAttack(froggy), dmg(base));
        if (res != null) res.setBaseValue(kb);
        if (target instanceof ServerPlayer player) {
            player.setDeltaMovement(0.0D, Math.min(player.getDeltaMovement().y, 0.0D), 0.0D);
            player.hurtMarked = true;
        }
    }

    /**
     * Segura o alvo por um instante, sem derrubar: jogador fica com os
     * controles travados (sem Lentidão, que dá zoom na tela); mob fica lento.
     */
    private void hold(LivingEntity target, int ticks) {
        if (target instanceof ServerPlayer player) {
            player.setSprinting(false);
            ModNetwork.sendEffects(player, 0F, 0, ticks, -1);
        } else {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 4, false, false));
        }
    }

    private void shake(LivingEntity target, float intensity, int ticks) {
        if (target instanceof ServerPlayer player) {
            ModNetwork.sendShake(player, intensity, ticks);
        }
    }

    private void push(LivingEntity target, double horizontal, double vertical) {
        Vec3 dir = target.position().subtract(froggy.position());
        dir = new Vec3(dir.x, 0, dir.z).normalize().scale(horizontal);
        target.push(dir.x, vertical, dir.z);
        target.hurtMarked = true; // sem isso o jogador não sente o empurrão
    }

    private void pull(LivingEntity target, double horizontal) {
        Vec3 dir = froggy.position().subtract(target.position());
        dir = new Vec3(dir.x, 0, dir.z).normalize().scale(horizontal);
        target.push(dir.x, 0.1D, dir.z);
        target.hurtMarked = true;
    }
}
