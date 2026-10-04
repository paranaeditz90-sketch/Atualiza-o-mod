package com.froggydude.entity.ai;

import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.PinTracker;
import com.froggydude.init.ModSounds;
import com.froggydude.network.ModNetwork;
import com.froggydude.player.ArmLoss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * O "cérebro" de combate. A regra principal: só começa um ataque se ele tem
 * chance real de acertar (alcance, linha de visão, alvo no chão e parado o
 * bastante pros pulos). Se nenhum ataque serve, ele persegue: de longe, aos
 * saltos de sapo (como nos vídeos); de perto, em pulinhos de quatro.
 *
 * Quando um pulo acerta, a vítima vai pro chão e ele escolhe: devorar (montado,
 * mordendo) ou esmagar (em pé, socando com os dois punhos). O pulo altíssimo
 * sempre termina em esmagamento. A língua pode puxar a vítima pra ele arrancar
 * o braço esquerdo e comer na frente dela.
 *
 * "Matar" x "apavorar": às vezes ele não ataca de cara; segue de longe,
 * devagar, e fica encarando (ver FroggydudeEntity#isStalking). A inteligência
 * de verdade pra decidir isso vem na Parte 7.
 */
public class FroggyCombatGoal extends Goal {

    private static final FroggyState[] ATTACKS = {
            FroggyState.BITE, FroggyState.TONGUE_WHIP, FroggyState.TONGUE_GRAB, FroggyState.TONGUE_CAPTURE,
            FroggyState.JUMP_PIN, FroggyState.HIGH_JUMP, FroggyState.SKY_DROP};

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

    // Pulo altíssimo (vs AJ, 0:56): sobe ~12 blocos em 17 ticks, cai em ~36.
    private static final double SKY_VY = 1.5D;
    private static final int SKY_LAUNCH = 8;
    private static final double SKY_MIN_DIST = 7.0D;
    private static final double SKY_MAX_DIST = 20.0D;

    // Salto de sapo da perseguição (referência: o vídeo contra o Parallax).
    private static final double LEAP_VY = 0.42D;        // 12 ticks no ar, ~1,25 bloco de altura
    private static final int LEAP_AIR = 12;
    private static final int LEAP_LAUNCH = 5;
    private static final double LEAP_MIN_DIST = 7.0D;   // mais perto que isso: pulinhos de quatro
    private static final double LEAP_MAX_DIST = 24.0D;
    private static final double LEAP_MAX_JUMP = 7.5D;   // blocos por salto
    private static final double LEAP_STOP_SHORT = 3.0D; // cai antes do alvo, pra emendar o bote

    /** Caçando pra matar: galope rápido (uns 5,4 m/s, quase um jogador correndo). */
    private static final double HUNT_SPEED = 1.25D;
    /** Apavorando: galope lento, de longe. */
    private static final double STALK_SPEED = 0.9D;

    // Em cima da vítima. Distâncias pro rosto/corpo dela, sem entrar na câmera.
    private static final double PIN_DISTANCE = 1.85D;
    private static final double SMASH_DISTANCE = 1.15D;
    // (o braço: um pouco mais longe que antes - mordendo o ombro, a cabeça dele entrava na câmera)
    private static final double RIP_DISTANCE = 1.8D;

    // Devorar: 5 s, uma mordida por segundo (bate com a animação pin_hold de 1 s).
    private static final int PIN_TICKS = 100;
    private static final int PIN_FIRST_BITE = 10;
    private static final int PIN_BITE_EVERY = 20;
    private static final float PIN_BITE_DAMAGE = 1.5F;

    // Esmagar: 8 socos, dois por segundo (soco aos 0,25 s de cada volta de 0,5 s da
    // animação; o primeiro conta os 3 ticks de transição da pose anterior). O
    // dano total é o mesmo de antes (4 x 2), só que vem duas vezes mais rápido.
    private static final int SMASH_FIRST = 8;
    private static final int SMASH_EVERY = 10;
    private static final int SMASH_HITS = 8;
    private static final float SMASH_DAMAGE = 1.0F;

    // Arrancar o braço: agarra, arranca aos 22 ticks, solta a vítima aos 32.
    private static final int RIP_AT = 22;
    /** Crava os dentes no ombro (animação arm_rip, 0,42 s) e sacode como cachorro até arrancar. */
    private static final int RIP_BITE = 8;
    private static final float FRENZY_DAMAGE = 1.6F;
    private static final int RIP_RELEASE = 32;
    // Comer o braço: mastiga a cada 15 ticks, engole aos 80, digere até 90.
    private static final int EAT_SWALLOW = 80;

    private final FroggydudeEntity froggy;

    private LivingEntity trackedTarget;
    private Vec3 lastTargetPos;
    private Vec3 targetVel = Vec3.ZERO;
    private int repathTicks;
    private int leapCooldown;
    private int tongueCooldown;
    private boolean fired1;
    private boolean fired2;
    private boolean launched;
    private int stateLength;
    private int hits;
    private boolean ripPlanned;
    private int ripAt = -1;

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
        if (froggy.getFroggyState() == FroggyState.ARM_EAT) return true;
        LivingEntity target = froggy.getTarget();
        return target != null && target.isAlive() && !froggy.isVulnerableState();
    }

    @Override
    public boolean canContinueToUse() {
        FroggyState state = froggy.getFroggyState();
        if (state == FroggyState.ARM_EAT) return true; // termina de comer, com ou sem alvo
        LivingEntity target = froggy.getTarget();
        return target != null && target.isAlive()
                && (state == FroggyState.CHASE || state.isCombatAction());
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
        froggy.setHeldArm(null);
        if (froggy.getFroggyState().isCombatAction()) {
            froggy.setFroggyState(FroggyState.CHASE);
        }
        froggy.setTongueLength(0F);
    }

    @Override
    public void tick() {
        if (tongueCooldown > 0) tongueCooldown--;
        if (leapCooldown > 0) leapCooldown--;
        FroggyState state = froggy.getFroggyState();
        if (state == FroggyState.ARM_EAT) {
            runArmEat();
            return;
        }

        LivingEntity target = froggy.getTarget();
        if (target == null) return;
        // teste com ataque forçado: espera o jogador terminar de entrar no mundo
        if (froggy.getDebugAttack() != null && froggy.tickCount < 60) return;

        trackTarget(target);
        double dist = froggy.distanceTo(target);

        switch (state) {
            case PIN_HOLD -> {
                runPinHold(target, dist);
                return;
            }
            case SMASH -> {
                runSmash(target, dist);
                return;
            }
            case ARM_RIP -> {
                runArmRip();
                return;
            }
            case LEAP -> {
                runLeap(target);
                return;
            }
            default -> {
            }
        }
        if (state.isAttack()) {
            runAttack(state, target, dist);
            return;
        }
        if (state != FroggyState.CHASE) {
            froggy.setFroggyState(FroggyState.CHASE);
        }

        froggy.getLookControl().setLookAt(target, 40.0F, 40.0F);

        // perto demais: acabou o "só olhando"
        if (froggy.isStalking() && dist <= 3.0D) {
            froggy.stopStalking();
        }
        if (froggy.isStalking()) {
            runStalk(target, dist);
            return;
        }

        if (froggy.getAttackCooldown() <= 0) {
            FroggyState next = pickAttack(target, dist);
            if (next != null) {
                beginAttack(next, target, dist);
                return;
            }
        }

        // longe: salta como sapo; perto: pulinhos de quatro (na fase 2 ele só corre,
        // que é mais rápido que saltar)
        if (!froggy.isFrenzyActive() && leapCooldown <= 0 && canLeap(target, dist)) {
            beginLeap(target);
            return;
        }
        if (dist > 2.0D) {
            if (--repathTicks <= 0) {
                repathTicks = 4;
                froggy.getNavigation().moveTo(target, froggy.isFrenzyActive() ? 1.0D : HUNT_SPEED);
            }
        } else {
            froggy.getNavigation().stop();
        }
    }

    // ---------------------------------------------------------------- apavorar

    /**
     * Segue de longe (8 a 13 blocos) num galope lento, para e encara. Se a
     * vítima chega perto, ele recua um pouco. Não ataca.
     */
    private void runStalk(LivingEntity target, double dist) {
        froggy.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (--repathTicks > 0) return;
        repathTicks = 10;
        if (dist > 13.0D) {
            froggy.getNavigation().moveTo(target, STALK_SPEED);
        } else if (dist < 7.0D) {
            Vec3 away = froggy.position().subtract(target.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4D ? new Vec3(1, 0, 0) : away.normalize();
            Vec3 spot = froggy.position().add(away.scale(5.0D));
            froggy.getNavigation().moveTo(spot.x, spot.y, spot.z, STALK_SPEED);
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

    /** Peso de cada ataque no sorteio: a língua é rara (é especial, não spam). */
    private static int weight(FroggyState s) {
        return switch (s) {
            case BITE, JUMP_PIN -> 3;
            case HIGH_JUMP, SKY_DROP -> 2;
            default -> 1;
        };
    }

    /** Teste: DebugAttack "SMASH"/"PIN_HOLD" usam o bote, "ARM_RIP" usa a língua de captura. */
    private FroggyState debugAttackKind() {
        FroggyState only = froggy.getDebugAttack();
        if (only == null) return null;
        return switch (only) {
            case SMASH, PIN_HOLD -> FroggyState.JUMP_PIN;
            case ARM_RIP, ARM_EAT -> FroggyState.TONGUE_CAPTURE;
            default -> only;
        };
    }

    private FroggyState pickAttack(LivingEntity target, double dist) {
        if (!froggy.hasLineOfSight(target)) return null;

        List<FroggyState> valid = new ArrayList<>();
        FroggyState only = debugAttackKind();
        // pendurado na parede: só dá pra morder (o resto precisa de chão)
        boolean onWall = froggy.isClimbing() && !froggy.onGround();
        for (FroggyState s : ATTACKS) {
            if (only != null && s != only) continue;
            if (onWall && s != FroggyState.BITE) continue;
            if (s.isTongue() && tongueCooldown > 0 && only == null) continue;
            if (froggy.isReady(s) && canHit(s, target, dist)) {
                valid.add(s);
            }
        }
        if (valid.isEmpty()) return null;
        // nunca repete o último ataque, se houver outra opção
        if (valid.size() > 1) {
            valid.remove(froggy.getLastAttack());
        }
        List<FroggyState> bag = new ArrayList<>();
        for (FroggyState s : valid) {
            for (int i = 0; i < weight(s); i++) bag.add(s);
        }
        return bag.get(froggy.getRandom().nextInt(bag.size()));
    }

    private boolean canHit(FroggyState s, LivingEntity target, double dist) {
        return switch (s) {
            case BITE -> dist <= 2.6D;
            case TONGUE_WHIP -> dist >= 2.8D && dist <= 8.0D;
            case TONGUE_GRAB -> dist >= 4.0D && dist <= 8.0D;
            case TONGUE_CAPTURE -> dist >= 5.0D && dist <= 10.0D && target.onGround();
            case JUMP_PIN -> dist >= 3.5D && dist <= 7.0D && canPounce(target, JUMP_PIN_LAUNCH + JUMP_PIN_AIR, 7.5D);
            case HIGH_JUMP -> dist >= 5.0D && dist <= 9.0D && canPounce(target, HIGH_JUMP_LAUNCH + HIGH_JUMP_AIR, 10.0D);
            case SKY_DROP -> dist >= SKY_MIN_DIST && dist <= SKY_MAX_DIST && canSkyDrop(target);
            default -> false;
        };
    }

    /** O alvo está no chão, na mesma altura e não está correndo pra longe? */
    private boolean canPounce(LivingEntity target, int totalTicks, double maxReach) {
        if (!target.onGround()) return false;
        if (Math.abs(target.getY() - froggy.getY()) > 1.4D) return false;
        // só desiste se a vítima estiver fugindo rápido; vindo NA DIREÇÃO dele, pode pular
        Vec3 toTarget = target.position().subtract(froggy.position()).multiply(1, 0, 1);
        double away = toTarget.lengthSqr() < 1.0E-4D ? 0
                : (targetVel.x * toTarget.x + targetVel.z * toTarget.z) / toTarget.length();
        if (away > 0.25D) return false;
        Vec3 aim = target.position().add(targetVel.x * totalTicks, 0, targetVel.z * totalTicks);
        double dx = aim.x - froggy.getX();
        double dz = aim.z - froggy.getZ();
        return Math.sqrt(dx * dx + dz * dz) <= maxReach;
    }

    /** Pulo altíssimo: precisa de céu aberto em cima dele e em cima da vítima (nada de caverna). */
    private boolean canSkyDrop(LivingEntity target) {
        if (!froggy.onGround() || froggy.isInWater() || !target.onGround()) return false;
        if (Math.abs(target.getY() - froggy.getY()) > 3.0D) return false;
        Level level = froggy.level();
        return level.canSeeSky(froggy.blockPosition().above()) && level.canSeeSky(target.blockPosition().above());
    }

    private void beginAttack(FroggyState s, LivingEntity target, double dist) {
        fired1 = false;
        fired2 = false;
        launched = false;
        froggy.faceTarget(target);
        froggy.getNavigation().stop();
        froggy.setFroggyState(s);
        if (s.isTongue()) froggy.openMouth(s.durationTicks + 4);
        switch (s) {
            case TONGUE_WHIP, TONGUE_GRAB -> {
                froggy.setTongueLength(tongueLength(target, 8.0D));
                froggy.onTongueOut();
            }
            case TONGUE_CAPTURE -> {
                froggy.setTongueLength(tongueLength(target, 10.0D));
                froggy.onTongueOut();
                FroggyState dbg = froggy.getDebugAttack();
                boolean force = dbg == FroggyState.ARM_RIP || dbg == FroggyState.ARM_EAT;
                ripPlanned = target instanceof ServerPlayer p && ArmLoss.canLoseArm(p)
                        && (force || froggy.getRandom().nextFloat() < 0.5F);
            }
            default -> froggy.setTongueLength(0F);
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
        // a vítima veio correndo na direção dele: em vez de se cruzarem no ar
        // (e "nada acontecer"), ele agarra no meio do salto ou assim que pousa
        if (launched && t > LEAP_LAUNCH && (touching(target, 0.45D)
                || (froggy.onGround() && t > LEAP_LAUNCH + 4 && froggy.distanceTo(target) <= 2.2D))) {
            debug("salto pegou a vitima: t=" + t + " dist=" + froggy.distanceTo(target));
            froggy.setDeltaMovement(0, Math.min(0, froggy.getDeltaMovement().y), 0);
            pounceHit(FroggyState.JUMP_PIN, target);
            return;
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
            case BITE -> {
                if (t < 6) froggy.faceTarget(target);
                if (!fired1 && t >= 6) {
                    fired1 = true;
                    if (dist <= 3.0D && froggy.hasLineOfSight(target)) {
                        hurt(target, 8.0F);
                        froggy.spawnBlood(target, 8);
                        froggy.onBiteHit();
                    }
                }
            }
            case TONGUE_WHIP -> {
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
            }
            case TONGUE_GRAB -> {
                if (t < 10) aimTongue(target, 8.0D);
                if (!fired1 && t >= 10) {
                    fired1 = true;
                    if (tongueReaches(target, dist, 8.5D)) {
                        pull(target, 1.3D);
                        hurt(target, 3.0F);
                        froggy.onTongueTaste();
                    }
                }
            }
            case TONGUE_CAPTURE -> {
                if (t < 12) aimTongue(target, 10.0D);
                if (!fired1 && t >= 12) {
                    fired1 = true;
                    debug("lingua de captura: t=" + t + " dist=" + dist + " acerta=" + tongueReaches(target, dist, 10.5D)
                            + " braco=" + ripPlanned);
                    if (tongueReaches(target, dist, 10.5D)) {
                        // agarra com a língua e puxa até a boca (sem derrubar ainda)
                        pull(target, 1.1D);
                        hold(target, 20);
                        shake(target, 0.6F, 14);
                        froggy.spawnBlood(target, 4);
                        froggy.onTongueTaste();
                    } else {
                        fired2 = true; // errou a língua: não tem mordida nem braço
                    }
                }
                if (t > 12 && t < 20 && !fired2) {
                    // recolhe a língua trazendo a vítima até a boca (vs AJ, 2:18)
                    froggy.faceTarget(target);
                    reelIn(target);
                }
                if (!fired2 && t >= 20) {
                    fired2 = true;
                    debug("captura puxou: dist=" + dist + " braco=" + ripPlanned);
                    if (dist <= 4.2D) {
                        if (ripPlanned && target instanceof ServerPlayer p && ArmLoss.canLoseArm(p)) {
                            // puxou de volta com a língua: agora arranca o braço (vs AJ, 2:21)
                            finishAttack(state);
                            beginArmRip(target);
                            return;
                        }
                        if (dist <= 3.5D) {
                            hurt(target, 5.0F);
                            froggy.spawnBlood(target, 6);
                            froggy.onBiteHit();
                        }
                    }
                }
            }
            case JUMP_PIN, HIGH_JUMP -> {
                boolean high = state == FroggyState.HIGH_JUMP;
                int launchAt = high ? HIGH_JUMP_LAUNCH : JUMP_PIN_LAUNCH;
                if (!launched && t < launchAt) {
                    froggy.faceTarget(target);
                    // a vítima veio correndo pra cima dele: nem pula, agarra e derruba na hora
                    if (touching(target, 0.6D)) {
                        debug("tackle antes do pulo: dist=" + dist);
                        pounceHit(state, target);
                        return;
                    }
                }
                if (!launched && t >= launchAt) {
                    launched = true;
                    boolean ok = high ? launch(target, HIGH_JUMP_VY, HIGH_JUMP_AIR, 3.5D, 10.0D)
                            : launch(target, JUMP_PIN_VY, JUMP_PIN_AIR, 2.5D, 7.5D);
                    if (!ok) {
                        if (froggy.distanceTo(target) <= 3.0D && froggy.hasLineOfSight(target)) {
                            // perto demais pra pular (vinha correndo na direção dele): se joga em cima
                            debug("tackle no lugar do pulo: dist=" + froggy.distanceTo(target));
                            pounceHit(state, target);
                        } else {
                            abort(state);
                        }
                        return;
                    }
                }
                if (launched && !fired1) {
                    // no ar ele confere TODO tick se trombou na vítima (de frente ela passava por baixo)
                    boolean contact = t > launchAt && touching(target, 0.45D);
                    if (contact || landed(t, launchAt, state)) {
                        fired1 = true;
                        debug((contact ? "trombou no ar" : "pousou") + ": t=" + t + " dist="
                                + froggy.distanceTo(target) + " chao=" + froggy.onGround());
                        if (contact || froggy.distanceTo(target) <= (high ? 3.4D : 3.0D)) {
                            froggy.setDeltaMovement(0, Math.min(0, froggy.getDeltaMovement().y), 0);
                            pounceHit(state, target);
                            return;
                        }
                    }
                }
            }
            case SKY_DROP -> {
                if (runSkyDrop(target, t)) return;
            }
            default -> {
            }
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

    /** As caixas de colisão (com uma folga) se encostam. */
    private boolean touching(LivingEntity target, double slack) {
        return froggy.getBoundingBox().inflate(slack, 0.2D, slack).intersects(target.getBoundingBox());
    }

    /**
     * O bote pegou: derruba. O bote baixo às vezes devora, às vezes esmaga; o
     * salto alto bate mais forte e sempre monta em cima pra devorar.
     */
    private void pounceHit(FroggyState state, LivingEntity target) {
        if (state == FroggyState.HIGH_JUMP) {
            hurtNoKnockback(target, 7.0F);
            froggy.spawnBlood(target, 6);
            pinVictim(state, target, 1.6F);
            return;
        }
        hurtNoKnockback(target, 5.0F);
        froggy.spawnBlood(target, 5);
        FroggyState dbg = froggy.getDebugAttack();
        boolean smash = dbg == FroggyState.SMASH
                || (dbg != FroggyState.PIN_HOLD && froggy.getRandom().nextBoolean());
        if (smash) beginSmash(state, target, 1.4F);
        else pinVictim(state, target, 1.2F);
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
        if (state.isTongue()) {
            // depois de qualquer língua, a língua toda descansa um tempo
            int rest = 160 + froggy.getRandom().nextInt(100);
            tongueCooldown = froggy.isFrenzyActive() ? rest / 2 : rest;
        }
        int gap = 12 + froggy.getRandom().nextInt(10);
        froggy.setAttackCooldown(froggy.isFrenzyActive() ? gap / 2 : gap);
        froggy.setFroggyState(FroggyState.CHASE);
        froggy.setTongueLength(0F);
    }

    private static int cooldownFor(FroggyState s) {
        return switch (s) {
            case BITE -> 25;
            case TONGUE_WHIP -> 140;
            case TONGUE_GRAB -> 220;
            case TONGUE_CAPTURE -> 320;
            case JUMP_PIN -> 180;
            case HIGH_JUMP -> 240;
            case SKY_DROP -> 500;
            default -> 40;
        };
    }

    // ---------------------------------------------------------------- pulo altíssimo

    /** Devolve true se já trocou de estado (pousou). */
    private boolean runSkyDrop(LivingEntity target, int t) {
        if (t < SKY_LAUNCH) {
            froggy.faceTarget(target);
            froggy.getNavigation().stop();
        }
        if (!launched && t >= SKY_LAUNCH) {
            launched = true;
            if (!froggy.onGround() || !canSkyDrop(target)) {
                abort(FroggyState.SKY_DROP);
                return true;
            }
            Vec3 dir = target.position().subtract(froggy.position()).multiply(1, 0, 1);
            dir = dir.lengthSqr() < 1.0E-4D ? Vec3.ZERO : dir.normalize().scale(0.15D);
            froggy.setDeltaMovement(dir.x, SKY_VY, dir.z);
            froggy.setNoFallTicks(120);
            froggy.hasImpulse = true;
            froggy.onLeapLaunch();
            return false;
        }
        if (launched && !fired1) {
            if (t > SKY_LAUNCH + 1) steerSkyDrop(target);
            if (froggy.onGround() && t >= SKY_LAUNCH + 6) {
                fired1 = true;
                landSkyDrop(target);
                return true;
            }
        }
        return false;
    }

    /** No ar ele "mira": sobe quase reto e, na queda, corrige pra cair do lado da vítima, mesmo se ela correr. */
    private void steerSkyDrop(LivingEntity target) {
        Vec3 side = froggy.position().subtract(target.position()).multiply(1, 0, 1);
        side = side.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : side.normalize();
        Vec3 aim = target.position().add(side.scale(SMASH_DISTANCE));
        double dx = aim.x - froggy.getX();
        double dz = aim.z - froggy.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        Vec3 now = froggy.getDeltaMovement();
        // subindo: quase reto pra cima (some no céu); caindo: mira em cheio na vítima
        double v = now.y > 0.0D ? Math.min(d * 0.08D, 0.3D) : Math.min(d * 0.2D, 1.0D);
        if (d > 1.0E-3D) {
            froggy.setDeltaMovement(dx / d * v, now.y, dz / d * v);
        } else {
            froggy.setDeltaMovement(0, now.y, 0);
        }
        froggy.faceTarget(target);
    }

    private void landSkyDrop(LivingEntity target) {
        froggy.onSkyLand();
        if (froggy.level() instanceof ServerLevel server) {
            BlockState ground = froggy.getBlockStateOn();
            if (!ground.isAir()) {
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                        froggy.getX(), froggy.getY() + 0.1D, froggy.getZ(), 60, 1.2D, 0.2D, 1.2D, 0.3D);
            }
            server.sendParticles(ParticleTypes.CLOUD, froggy.getX(), froggy.getY() + 0.2D, froggy.getZ(),
                    20, 1.0D, 0.1D, 1.0D, 0.05D);
            // o chão treme pra todo mundo por perto
            for (ServerPlayer p : server.players()) {
                double d = p.distanceTo(froggy);
                if (d < 16.0D) ModNetwork.sendShake(p, (float) (1.6D * (1.0D - d / 16.0D)), 16);
            }
        }
        // quem estiver colado é jogado pra longe (menos a vítima, que vai pro chão)
        for (LivingEntity e : froggy.level().getEntitiesOfClass(LivingEntity.class,
                froggy.getBoundingBox().inflate(3.0D), e -> e != froggy && e != target && e.isAlive())) {
            push(e, 1.2D, 0.4D);
        }
        if (froggy.distanceTo(target) <= 2.6D && Math.abs(target.getY() - froggy.getY()) < 1.5D) {
            hurtNoKnockback(target, 6.0F);
            froggy.spawnBlood(target, 6);
            beginSmash(FroggyState.SKY_DROP, target, 1.8F);
        } else {
            finishAttack(FroggyState.SKY_DROP);
        }
    }

    // ---------------------------------------------------------------- em cima da vítima

    /** Põe o Froggy do lado em que caiu, na distância certa (sem "teletransportar" em volta). */
    private void placeBeside(LivingEntity target, double distance) {
        Vec3 dir = froggy.position().subtract(target.position()).multiply(1, 0, 1);
        if (dir.lengthSqr() < 0.04D) {
            Vec3 look = target.getLookAngle();
            dir = new Vec3(look.x, 0, look.z);
        }
        dir = dir.lengthSqr() < 1.0E-4D ? new Vec3(0, 0, 1) : dir.normalize();
        froggy.setPos(target.getX() + dir.x * distance, target.getY(), target.getZ() + dir.z * distance);
        froggy.setDeltaMovement(Vec3.ZERO);
        froggy.faceTarget(target);
        froggy.getNavigation().stop();
    }

    /** Derruba a vítima: jogador deitado, controles travados, câmera presa no rosto dele. */
    private void knockDown(LivingEntity target, int ticks, float shake) {
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
    }

    /** O pulo acertou: monta em cima pra devorar. Às vezes, no meio, arranca o braço. */
    private void pinVictim(FroggyState jump, LivingEntity target, float shake) {
        finishAttack(jump);
        placeBeside(target, PIN_DISTANCE);
        knockDown(target, PIN_TICKS, shake);
        stateLength = PIN_TICKS;
        boolean canRip = target instanceof ServerPlayer p && ArmLoss.canLoseArm(p);
        ripAt = canRip && froggy.getRandom().nextFloat() < 0.35F ? 50 : -1;
        froggy.setFroggyState(FroggyState.PIN_HOLD);
        froggy.onPinStart();
    }

    private static void debug(String msg) {
        if (Boolean.getBoolean("froggydude.debug")) System.out.println("[FROGGYDEBUG] " + msg);
    }

    /** A vítima continua presa embaixo dele? */
    private boolean stillPinned(LivingEntity victim, LivingEntity target, double dist) {
        return victim != null && victim.isAlive() && victim == target && dist <= 2.8D
                && (!(victim instanceof ServerPlayer p) || PinTracker.isPinnedBy(p, froggy));
    }

    private void holdOver(LivingEntity victim, double distance) {
        froggy.getNavigation().stop();
        froggy.setDeltaMovement(0, froggy.getDeltaMovement().y, 0);
        froggy.faceTarget(victim);
        froggy.getLookControl().setLookAt(victim.getX(), victim.getEyeY(), victim.getZ(), 90.0F, 90.0F);
        keepDistance(victim, distance);
    }

    private void runPinHold(LivingEntity target, double dist) {
        int t = froggy.getStateTicks();
        LivingEntity victim = froggy.getPinnedVictim();
        if (!stillPinned(victim, target, dist) || t >= stateLength) {
            debug("solta: t=" + t + "/" + stateLength + " dist=" + dist);
            endOnVictim();
            return;
        }
        holdOver(victim, PIN_DISTANCE);

        if (t == ripAt && victim instanceof ServerPlayer p && ArmLoss.canLoseArm(p)) {
            beginArmRip(victim);
            return;
        }
        // morde uma vez por segundo (o primeiro logo depois de cair em cima)
        if (t >= PIN_FIRST_BITE && (t - PIN_FIRST_BITE) % PIN_BITE_EVERY == 0) {
            hurtNoKnockback(victim, PIN_BITE_DAMAGE);
            froggy.spawnBlood(victim, 4);
            froggy.onBiteHit();
            shake(victim, 0.7F, 8);
        }
    }

    // ---------------------------------------------------------------- esmagamento

    /** Derruba e fica em pé por cima, pra descer os punhos (vs AJ, 0:57). */
    private void beginSmash(FroggyState from, LivingEntity target, float shake) {
        finishAttack(from);
        placeBeside(target, SMASH_DISTANCE);
        knockDown(target, FroggyState.SMASH.durationTicks, shake);
        stateLength = FroggyState.SMASH.durationTicks;
        hits = 0;
        froggy.setFroggyState(FroggyState.SMASH);
        froggy.onPinStart();
    }

    private void runSmash(LivingEntity target, double dist) {
        int t = froggy.getStateTicks();
        LivingEntity victim = froggy.getPinnedVictim();
        if (!stillPinned(victim, target, dist) || t >= stateLength) {
            debug("fim do esmagamento: t=" + t + "/" + stateLength + " dist=" + dist);
            endOnVictim();
            return;
        }
        holdOver(victim, SMASH_DISTANCE);

        if (hits < SMASH_HITS && t >= SMASH_FIRST && (t - SMASH_FIRST) % SMASH_EVERY == 0) {
            hits++;
            debug("soco " + hits + " t=" + t + " dist=" + dist);
            hurtNoKnockback(victim, SMASH_DAMAGE);
            froggy.spawnBlood(victim, 5);
            froggy.onSmashHit();
            shake(victim, 1.1F, 10);
        }
    }

    // ---------------------------------------------------------------- braço

    /** Vítima presa: agarra o braço esquerdo e puxa até arrancar. */
    private void beginArmRip(LivingEntity target) {
        placeBeside(target, RIP_DISTANCE);
        knockDown(target, FroggyState.ARM_RIP.durationTicks, 0.8F);
        froggy.setFroggyState(FroggyState.ARM_RIP);
        froggy.vocalize(ModSounds.PIN.get(), 1.4F);
    }

    private void runArmRip() {
        int t = froggy.getStateTicks();
        LivingEntity victim = froggy.getPinnedVictim();
        double dist = victim == null ? Double.MAX_VALUE : froggy.distanceTo(victim);
        if (t < RIP_AT) {
            boolean ok = victim != null && victim.isAlive() && dist <= 3.2D
                    && (!(victim instanceof ServerPlayer p) || PinTracker.isPinnedBy(p, froggy));
            if (!ok) {
                endOnVictim();
                return;
            }
            holdOver(victim, RIP_DISTANCE);
            if (t == RIP_BITE) {
                // crava os dentes no ombro
                hurtNoKnockback(victim, 1.0F);
                froggy.onBiteHit();
                froggy.spawnBlood(victim, 8);
                shake(victim, 0.9F, 8);
            } else if (t > RIP_BITE && t < RIP_AT - 1 && (t - RIP_BITE) % 2 == 0) {
                // sacode a cabeça rasgando a carne (a cada tranco espirra)
                froggy.spawnBlood(victim, 3);
                shake(victim, 0.5F, 4);
                froggy.openMouth(4);
            }
            return;
        }
        if (t == RIP_AT && victim != null) {
            froggy.faceTarget(victim);
            Vec3 wound = victim instanceof Player pl ? ArmLoss.shoulder(pl)
                    : victim.position().add(0, victim.getBbHeight() * 0.6D, 0);
            if (victim instanceof ServerPlayer p && ArmLoss.canLoseArm(p)) {
                ArmLoss.removeArm(p);
                froggy.setHeldArm(p);
            }
            hurtNoKnockback(victim, 4.0F);
            froggy.spawnBlood(victim, 20);
            // jorra do ombro pra cima e pra cima DELE (e não na câmera de quem perdeu o braço)
            Vec3 out = wound.subtract(victim.position()).multiply(1, 0, 1);
            Vec3 dir = out.lengthSqr() < 1.0E-4D ? new Vec3(0, 1, 0) : out.normalize().add(0, 1.6D, 0);
            froggy.bloodBurst(wound, dir, 60, 0.5F, 1.6F);
            froggy.addGore(0.6F);
            shake(victim, 1.4F, 20);
        }
        if (t > RIP_AT && t <= RIP_AT + 12 && (t - RIP_AT) % 3 == 0) {
            // o braço atravessado na boca, sacudindo: sangue voando pra todo lado
            froggy.spawnMouthBlood(5);
            froggy.openMouth(4);
        }
        if (t == RIP_RELEASE) {
            // larga a vítima: ela pode fugir enquanto ele come
            releaseVictim();
        }
        if (t >= FroggyState.ARM_RIP.durationTicks) {
            releaseVictim();
            if (froggy.getHeldArmOwner().isPresent()) {
                froggy.setFroggyState(FroggyState.ARM_EAT);
                froggy.vocalize(ModSounds.EAT.get(), 1.4F);
            } else {
                froggy.setAttackCooldown(15);
                froggy.setFroggyState(FroggyState.CHASE);
            }
        }
    }

    /** Come o braço na frente da vítima: mastiga, engole e digere. Não persegue enquanto isso. */
    private void runArmEat() {
        int t = froggy.getStateTicks();
        froggy.getNavigation().stop();
        froggy.setDeltaMovement(0, froggy.getDeltaMovement().y, 0);
        LivingEntity target = froggy.getTarget();
        if (target != null) froggy.getLookControl().setLookAt(target, 10.0F, 10.0F);

        if (t < EAT_SWALLOW && t % 15 == 10) {
            froggy.onChew();
            froggy.spawnMouthBlood(7);
        } else if (t < EAT_SWALLOW && t % 15 == 13) {
            // arranca o pedaço com um tranco da cabeça
            froggy.spawnMouthBlood(4);
            froggy.openMouth(5);
        }
        if (t == EAT_SWALLOW) {
            froggy.finishEatingArm();
            froggy.vocalize(ModSounds.EAT.get(), 1.6F);
        }
        if (t >= FroggyState.ARM_EAT.durationTicks) {
            froggy.setHeldArm(null);
            froggy.setAttackCooldown(20);
            froggy.setFroggyState(FroggyState.CHASE);
        }
    }

    /** Se a vítima escorregou, ele se ajeita (ele vai até ela, não o contrário). */
    private void keepDistance(LivingEntity victim, double distance) {
        Vec3 dir = froggy.position().subtract(victim.position()).multiply(1, 0, 1);
        double d = dir.length();
        if (d < 1.0E-3D || Math.abs(d - distance) < 0.25D) return;
        Vec3 spot = victim.position().add(dir.scale(distance / d));
        froggy.setPos(spot.x, froggy.getY(), spot.z);
    }

    /** Sai de cima da vítima. Às vezes recua e fica só olhando (brincando com a comida). */
    private void endOnVictim() {
        releaseVictim();
        froggy.setAttackCooldown(20);
        froggy.setFroggyState(FroggyState.CHASE);
        if (!froggy.isFrenzyActive() && froggy.getRandom().nextFloat() < 0.35F) {
            froggy.startStalking(60 + froggy.getRandom().nextInt(60));
        }
    }

    private void releaseVictim() {
        LivingEntity victim = froggy.getPinnedVictim();
        if (victim instanceof ServerPlayer player && PinTracker.isPinnedBy(player, froggy)) {
            PinTracker.release(player);
        }
        froggy.setPinnedVictim(null);
    }

    // ---------------------------------------------------------------- efeitos

    /**
     * Fase 2 (ataque feroz): TODO ataque bate mais forte - língua, mordida,
     * esmagamento, pulo, comer vivo e arrancar o braço.
     */
    private float dmg(float base) {
        return froggy.isFrenzyActive() ? base * FRENZY_DAMAGE : base;
    }

    private void hurt(LivingEntity target, float base) {
        target.hurt(froggy.damageSources().mobAttack(froggy), dmg(base));
    }

    /** Dano sem o empurrão normal (vítima no chão fica no chão). */
    private void hurtNoKnockback(LivingEntity target, float base) {
        double kb = 0;
        AttributeInstance res = target.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
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
        dir = new Vec3(dir.x, 0, dir.z);
        dir = dir.lengthSqr() < 1.0E-4D ? new Vec3(1, 0, 0) : dir.normalize();
        dir = dir.scale(horizontal);
        target.push(dir.x, vertical, dir.z);
        target.hurtMarked = true; // sem isso o jogador não sente o empurrão
    }

    /** Puxa a vítima presa na língua até ficar colada na boca dele (a língua encurta junto). */
    private void reelIn(LivingEntity target) {
        Vec3 dir = froggy.position().subtract(target.position()).multiply(1, 0, 1);
        double d = dir.length();
        froggy.setTongueLength(tongueLength(target, 10.0D));
        if (d <= 1.7D) {
            target.setDeltaMovement(0, target.getDeltaMovement().y, 0);
        } else {
            Vec3 v = dir.normalize().scale(Math.min(1.0D, (d - 1.5D) * 0.5D));
            target.setDeltaMovement(v.x, Math.max(target.getDeltaMovement().y, 0.0D), v.z);
        }
        target.hurtMarked = true;
    }

    private void pull(LivingEntity target, double horizontal) {
        Vec3 dir = froggy.position().subtract(target.position());
        dir = new Vec3(dir.x, 0, dir.z).normalize().scale(horizontal);
        target.push(dir.x, 0.1D, dir.z);
        target.hurtMarked = true;
    }
}
