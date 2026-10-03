package com.froggydude.entity.ai;

import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.network.ModNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * O "cérebro" de combate. A regra principal: só começa um ataque se ele tem
 * chance real de acertar (alcance, linha de visão, alvo no chão e parado o
 * bastante pros pulos). Se nenhum ataque serve, ele só corre atrás do alvo.
 * Cada ataque tem seu próprio tempo de recarga, e nunca repete o último.
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

    private final FroggydudeEntity froggy;

    private LivingEntity trackedTarget;
    private Vec3 lastTargetPos;
    private Vec3 targetVel = Vec3.ZERO;
    private int repathTicks;
    private boolean fired1;
    private boolean fired2;
    private boolean launched;

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
                && (froggy.getFroggyState() == FroggyState.CHASE || froggy.getFroggyState().isAttack());
    }

    @Override
    public void start() {
        repathTicks = 0;
    }

    @Override
    public void stop() {
        if (froggy.getFroggyState().isAttack()) {
            froggy.setFroggyState(FroggyState.CHASE);
        }
        froggy.setTongueLength(0F);
    }

    @Override
    public void tick() {
        LivingEntity target = froggy.getTarget();
        if (target == null) return;

        trackTarget(target);
        double dist = froggy.distanceTo(target);
        FroggyState state = froggy.getFroggyState();

        if (state.isAttack()) {
            runAttack(state, target, dist);
            return;
        }

        froggy.getLookControl().setLookAt(target, 40.0F, 40.0F);
        if (dist > 2.0D) {
            if (--repathTicks <= 0) {
                repathTicks = 4;
                froggy.getNavigation().moveTo(target, 1.0D);
            }
        } else {
            froggy.getNavigation().stop();
        }

        if (froggy.getAttackCooldown() > 0) return;

        FroggyState next = pickAttack(target, dist);
        if (next != null) {
            beginAttack(next, target, dist);
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
        for (FroggyState s : ATTACKS) {
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
                froggy.setTongueLength(tongueLength(dist, 8.0D));
                break;
            case TONGUE_CAPTURE:
                froggy.setTongueLength(tongueLength(dist, 10.0D));
                break;
            default:
                froggy.setTongueLength(0F);
                break;
        }
    }

    private float tongueLength(double dist, double max) {
        return (float) Math.min(dist, max) + 0.6F;
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
                    }
                }
                break;

            case TONGUE_WHIP:
                if (t < 8) aimTongue(target, dist, 8.0D);
                if (!fired1 && t >= 8) {
                    fired1 = true;
                    if (tongueReaches(target, dist, 8.5D)) {
                        hurt(target, 6.0F);
                        push(target, 1.4D, 0.3D);
                        froggy.spawnBlood(target, 6);
                    }
                }
                break;

            case TONGUE_GRAB:
                if (t < 10) aimTongue(target, dist, 8.0D);
                if (!fired1 && t >= 10) {
                    fired1 = true;
                    if (tongueReaches(target, dist, 8.5D)) {
                        pull(target, 1.3D);
                        hurt(target, 3.0F);
                    }
                }
                break;

            case TONGUE_CAPTURE:
                if (t < 12) aimTongue(target, dist, 10.0D);
                if (!fired1 && t >= 12) {
                    fired1 = true;
                    if (tongueReaches(target, dist, 10.5D)) {
                        // prende o alvo e puxa pra perto da boca
                        lock(target, 45, 0.6F);
                        pull(target, 1.1D);
                        froggy.spawnBlood(target, 4);
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
                    if (dist <= 3.0D) {
                        // acertou: derruba e prende no chão
                        hurt(target, 7.0F);
                        lock(target, 50, 1.2F);
                        froggy.spawnBlood(target, 8);
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
                    if (dist <= 3.4D) {
                        // impacto forte: derruba e prende
                        hurt(target, 10.0F);
                        lock(target, 40, 1.6F);
                        froggy.spawnBlood(target, 12);
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

    /** Enquanto a língua ainda "carrega", ele continua virado pro alvo. */
    private void aimTongue(LivingEntity target, double dist, double max) {
        froggy.faceTarget(target);
        froggy.setTongueLength(tongueLength(dist, max));
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

        // cai um pouco antes do alvo, colado nele
        double landDist = Math.max(1.0D, d - 0.8D);
        double factor = 1.0D + GROUND_FRICTION * (1.0D - Math.pow(AIR_FRICTION, airTicks - 1)) / (1.0D - AIR_FRICTION);
        double vh = landDist / factor;

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

    // ---------------------------------------------------------------- efeitos

    /** Ataques causam mais dano durante o frenesi. */
    private float dmg(float base) {
        return froggy.isFrenzyActive() ? base * 1.3F : base;
    }

    private void hurt(LivingEntity target, float base) {
        target.hurt(froggy.damageSources().mobAttack(froggy), dmg(base));
    }

    /**
     * Prende o alvo no chão. Jogador: trava os controles e a câmera (pacote pro
     * cliente; nada de Lentidão, que dá zoom na tela). Outros mobs: lentidão.
     */
    private void lock(LivingEntity target, int ticks, float shake) {
        if (target instanceof ServerPlayer player) {
            player.setSprinting(false);
            Vec3 v = player.getDeltaMovement();
            player.setDeltaMovement(0.0D, Math.min(v.y, 0.0D), 0.0D);
            player.hurtMarked = true;
            ModNetwork.sendEffects(player, shake, 16, ticks);
        } else {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false));
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
