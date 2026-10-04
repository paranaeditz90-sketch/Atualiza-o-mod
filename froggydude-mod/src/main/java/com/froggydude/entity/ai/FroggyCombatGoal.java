package com.froggydude.entity.ai;

import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.PinTracker;
import com.froggydude.brain.Strategy;
import com.froggydude.brain.Style;
import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.voice.VoiceSituation;
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

    /** Vítima mais alta que isso (blocos): ele sobe atrás em vez de atacar de baixo. */
    private static final double ELEVATED = 2.5D;

    /**
     * Caçando pra matar: galope de ~0,75 bloco/tick (15 blocos/s, quase 3 vezes um
     * jogador correndo), a do original (RACING A CHEETAH: ~15 b/s, passada de ~3,8
     * blocos). A velocidade real cresce ~2,15 x (atributo x isto)^2: 1,72 dava 0,5 b/t
     * (v0.3.4) e, com o salto de sapo no meio, parecia "um sapo pulando, bem lento".
     * Ajustável no config (caca_blocos_por_segundo); 15 b/s dá 2,11.
     */
    private static double huntSpeed() {
        double perTick = FroggyConfig.get(FroggyConfig.CACA_BLOCOS_POR_SEGUNDO) / 20.0D;
        return Math.sqrt(perTick / 2.15D) / 0.28D;
    }
    /** Apavorando: galope lento, de longe. */
    private static final double STALK_SPEED = 0.9D;
    /** Contra-ataque (a vítima avançou, ele avança junto e derruba): no máximo a cada 3 s. */
    private static final int COUNTER_COOLDOWN = 60;

    // Em cima da vítima: distância dos pés dele até ela. Medidas com
    // tools/anim/check_poses.py junto com as poses (v0.3.4): a 1,5 bloco as mãos
    // seguram/socam a vítima, a cabeça nunca entra na câmera dela (fica a 0,2-0,5
    // bloco no ponto mais perto) e os pés dele ficam no chão.
    private static final double PIN_DISTANCE = 1.5D;
    private static final double SMASH_DISTANCE = 1.5D;
    private static final double RIP_DISTANCE = 1.5D;

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
    /** Mordidas do arm_eat (0,30 s, 1,45 s e 2,60 s): crava, arranca e sacode (Eating a Zebra). */
    private static final int[] EAT_BITES = {6, 29, 52};

    private final FroggydudeEntity froggy;

    private LivingEntity trackedTarget;
    private Vec3 lastTargetPos;
    private Vec3 targetVel = Vec3.ZERO;
    /** Ticks até poder gritar de novo atrás de quem foge ("get back here!"). */
    private int chaseTalk = 0;
    /** Já falou montado nessa vítima ("stay on the ground" / "the last thing you see")? */
    private boolean pinTalked = false;
    /** O ataque em andamento já acertou? (o cérebro aprende com isso) */
    private boolean attackHit = false;
    private int repathTicks;
    private int counterCooldown;
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
        if (counterCooldown > 0) counterCooldown--;
        if (chaseTalk > 0) chaseTalk--;
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
        if (froggy.tickCount % 10 == 0) observe(target, dist);

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

        // a vítima subiu numa torre/parede: ele vai atrás, de quatro, parede acima.
        // Nada de ficar olhando de longe nem de puxar com a língua lá de baixo - antes,
        // se ela ficava na beirada (dava pra ver de baixo), ele usava a língua ou ficava
        // parado, e só subia se ela ficasse bem no meio, escondida.
        boolean elevated = target.getY() - froggy.getY() > ELEVATED;
        // a vítima veio pra cima dele: ele NÃO recua - avança junto e derruba (v0.3.4;
        // antes, no modo "só olhando", ele fugia de quem se aproximava e voltava depois)
        boolean charging = !elevated && dist <= 8.0D && approachSpeed(target) > 0.06D;
        // perto (ou lá em cima, ou vindo pra cima dele): acabou o "só olhando"
        if (froggy.isStalking() && (dist <= 7.0D || elevated || charging)) {
            froggy.stopStalking();
        }
        if (froggy.isStalking()) {
            runStalk(target, dist);
            return;
        }
        if (charging && froggy.getAttackCooldown() <= 0 && counterPounce(target, dist)) {
            return;
        }

        if (froggy.getAttackCooldown() <= 0) {
            FroggyState next = pickAttack(target, dist, elevated);
            if (next != null) {
                beginAttack(next, target, dist);
                return;
            }
        }

        // fugindo dele: "get back here!", "you can run but you can't hide"... (na ordem)
        if (chaseTalk <= 0 && target instanceof Player && dist > 6.0D && approachSpeed(target) < -0.12D) {
            chaseTalk = froggy.speak(VoiceSituation.CHASE) ? 200 + froggy.getRandom().nextInt(200) : 20;
        }

        // sem salto de sapo na perseguição (v0.3.5): o salto cobria 7,5 blocos em 24
        // ticks (0,31 b/t), mais devagar que o galope - "parece um sapo pulando, bem
        // lento" (LM). Agora é galope o caminho todo, como o guepardo do original.
        if (dist > 2.0D) {
            if (--repathTicks <= 0) {
                repathTicks = 4;
                if (!flank(target, dist)) {
                    froggy.getNavigation().moveTo(target, froggy.isFrenzyActive() ? 1.0D : huntSpeed());
                }
            }
        } else {
            froggy.getNavigation().stop();
        }
        if (froggy.tickCount % 10 == 0) {
            net.minecraft.world.level.pathfinder.Path path = froggy.getNavigation().getPath();
            debug("persegue: dist=" + String.format("%.2f", dist) + " dy=" + String.format("%.2f", target.getY() - froggy.getY())
                    + " navFeita=" + froggy.getNavigation().isDone()
                    + " fim=" + (path == null ? "sem" : path.getEndNode() == null ? "?" : path.getEndNode().asBlockPos().toShortString()
                    + (path.canReach() ? " alcanca" : " parcial"))
                    + " pos=" + String.format("%.2f %.2f %.2f", froggy.getX(), froggy.getY(), froggy.getZ())
                    + " bateu=" + froggy.horizontalCollision + " subindo=" + froggy.isClimbing()
                    + " chao=" + froggy.onGround() + " visao=" + froggy.hasLineOfSight(target)
                    + " olhando=" + froggy.isStalking());
        }
    }

    // ---------------------------------------------------------------- cérebro

    /** O que a vítima está fazendo agora (o cérebro junta isso no "estilo" dela). */
    private void observe(LivingEntity target, double dist) {
        if (!(target instanceof Player)) return;
        if (target.getY() - froggy.getY() > ELEVATED) {
            froggy.observe(target, Style.TOWER, 0.5F);
        } else if (dist > 4.0D && approachSpeed(target) < -0.12D) {
            froggy.observe(target, Style.FLEE, 0.5F);
        }
        if (dist < 24.0D && !froggy.hasLineOfSight(target)) {
            froggy.observe(target, Style.HIDE, 0.5F);
        }
    }

    /**
     * Estratégia "cercar por trás": de longe, vai pra um ponto atrás de onde a
     * vítima está olhando (quem encara pra bater ou mirar o arco não vê ele
     * chegando); de perto, vai direto. Devolve false se não é pra cercar agora.
     */
    private boolean flank(LivingEntity target, double dist) {
        if (froggy.getStrategy() != Strategy.FLANK || froggy.isFrenzyActive() || dist < 9.0D || dist > 40.0D) return false;
        Vec3 look = target.getViewVector(1.0F).multiply(1, 0, 1);
        if (look.lengthSqr() < 1.0E-4D) return false;
        Vec3 behind = target.position().subtract(look.normalize().scale(6.0D));
        return froggy.getNavigation().moveTo(behind.x, target.getY(), behind.z, huntSpeed());
    }

    /** Invasão (anti-trapaça): derruba na hora, sem bote. */
    public void forcePin(LivingEntity target) {
        pinVictim(FroggyState.JUMP_PIN, target, 1.4F);
    }

    /** Invasão: arranca o braço de quem já está no chão. */
    public void forceArmRip(LivingEntity target) {
        beginArmRip(target);
    }

    // ---------------------------------------------------------------- apavorar

    /**
     * Segue de longe (7 a 13 blocos) num galope lento, para e encara. Nunca
     * recua: se a vítima chega perto (ou vem pra cima dele), o "só olhando"
     * acaba e ele ataca (ver tick).
     */
    private void runStalk(LivingEntity target, double dist) {
        froggy.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (--repathTicks > 0) return;
        repathTicks = 10;
        if (dist > 13.0D) {
            froggy.getNavigation().moveTo(target, STALK_SPEED);
        } else {
            froggy.getNavigation().stop();
        }
    }

    /** Quanto a vítima está vindo NA DIREÇÃO dele (blocos por tick; < 0 = se afastando). */
    private double approachSpeed(LivingEntity target) {
        Vec3 toFroggy = froggy.position().subtract(target.position()).multiply(1, 0, 1);
        double len = toFroggy.length();
        if (len < 1.0E-3D) return 0.0D;
        return (targetVel.x * toFroggy.x + targetVel.z * toFroggy.z) / len;
    }

    /**
     * Contra-ataque: a vítima avançou pra cima dele, ele avança junto e derruba.
     * Usa o bote baixo (JUMP_PIN), que já agarra na trombada quando ela chega
     * antes do pulo - então vale de perto também (antes só a partir de 3,5 blocos).
     */
    private boolean counterPounce(LivingEntity target, double dist) {
        if (dist < 1.2D || counterCooldown > 0) return false;
        if (!target.onGround() || Math.abs(target.getY() - froggy.getY()) > 1.4D) return false;
        if (!froggy.hasLineOfSight(target) || froggy.isClimbing()) return false;
        FroggyState only = debugAttackKind();
        if (only != null && only != FroggyState.JUMP_PIN) return false;
        debug("contra-ataque: vítima vindo a " + String.format("%.2f", approachSpeed(target)) + " b/t, dist=" + String.format("%.2f", dist));
        counterCooldown = COUNTER_COOLDOWN;
        beginAttack(FroggyState.JUMP_PIN, target, dist);
        return true;
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

    private FroggyState pickAttack(LivingEntity target, double dist, boolean elevated) {
        if (!froggy.hasLineOfSight(target)) return null;

        List<FroggyState> valid = new ArrayList<>();
        FroggyState only = debugAttackKind();
        // pendurado na parede (ou com a vítima lá no alto): só dá pra morder quando chegar
        boolean onWall = (froggy.isClimbing() && !froggy.onGround()) || elevated;
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
        // peso do projeto x taxa de acerto plausível contra ESSA vítima (Thompson):
        // o que ela sempre desvia aparece menos, o que pega nela aparece mais
        float[] w = new float[valid.size()];
        float total = 0F;
        for (int i = 0; i < valid.size(); i++) {
            FroggyState s = valid.get(i);
            w[i] = weight(s) * (0.25F + 1.5F * froggy.attackHitChance(target, s));
            total += w[i];
        }
        float roll = froggy.getRandom().nextFloat() * total;
        for (int i = 0; i < valid.size(); i++) {
            roll -= w[i];
            if (roll <= 0F) return valid.get(i);
        }
        return valid.get(valid.size() - 1);
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
        attackHit = false;
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
                    } else {
                        froggy.onTongueMiss();
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
                    } else {
                        froggy.onTongueMiss();
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
                        attackHit = true;
                        pull(target, 1.1D);
                        hold(target, 20);
                        shake(target, 0.6F, 14);
                        froggy.spawnBlood(target, 4);
                        froggy.onTongueTaste();
                    } else {
                        fired2 = true; // errou a língua: não tem mordida nem braço
                        froggy.onTongueMiss();
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
                        // errou: volta a correr na hora. Antes o estado durava até o fim e a
                        // animação ficava na pose de "montado" no chão, sem vítima (os pranchões)
                        finishAttack(state);
                        return;
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
        LivingEntity target = froggy.getTarget();
        if (target != null) froggy.recordAttack(target, state, attackHit);
        attackHit = false;
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
        froggy.onPinnedVictim(target);
        pinTalked = false;
    }

    private static void debug(String msg) {
        if (Boolean.getBoolean("froggydude.debug")) System.out.println("[FROGGYDEBUG] " + msg);
    }

    /** A vítima continua presa embaixo dele? */
    private boolean stillPinned(LivingEntity victim, LivingEntity target, double dist) {
        return victim != null && victim.isAlive() && victim == target && dist <= 2.8D
                && (!(victim instanceof ServerPlayer p) || PinTracker.isPinnedBy(p, froggy));
    }

    /**
     * Montado, depois do "give me your body": "stay on the ground", "you are
     * mine"... e, com a vítima quase morta, "I'll be the last thing you see".
     */
    private void talkOnTop(LivingEntity victim, int t) {
        if (pinTalked || t < 15) return;
        boolean dying = victim instanceof Player && victim.getHealth() <= 6.0F;
        pinTalked = froggy.speak(dying ? VoiceSituation.LAST_BITE : VoiceSituation.ON_TOP);
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
        talkOnTop(victim, t);

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
        froggy.onPinnedVictim(target);
        pinTalked = false;
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
        talkOnTop(victim, t);
        if (t % 3 == 0) {
            double vx = victim.getX() - froggy.getX(), vz = victim.getZ() - froggy.getZ();
            float toV = (float) (Math.atan2(vz, vx) * 180.0D / Math.PI) - 90F;
            var mc = froggy.getMoveControl();
            debug("esmaga t=" + t + String.format(" yRot=%.0f corpo=%.0f paraVitima=%.0f", net.minecraft.util.Mth.wrapDegrees(froggy.getYRot()),
                    net.minecraft.util.Mth.wrapDegrees(froggy.yBodyRot), net.minecraft.util.Mth.wrapDegrees(toV))
                    + " moveQuer=" + mc.hasWanted() + String.format(" alvoMove=%.2f %.2f", mc.getWantedX(), mc.getWantedZ())
                    + String.format(" pos=%.2f %.2f vit=%.2f %.2f", froggy.getX(), froggy.getZ(), victim.getX(), victim.getZ()));
        }

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
        froggy.speak(VoiceSituation.PIN);
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
            // o jato sai do braço que ficou na boca dele (meio caminho entre o ombro e a
            // boca), pra cima e pra trás dele - nascendo no ombro, em primeira pessoa,
            // a nuvem inteira passava na frente da câmera de quem perdeu o braço
            Vec3 mouth = froggy.getFacePosition(1.0F);
            Vec3 at = wound.add(mouth.subtract(wound).scale(0.55D));
            Vec3 back = froggy.position().subtract(victim.position()).multiply(1, 0, 1);
            Vec3 dir = back.lengthSqr() < 1.0E-4D ? new Vec3(0, 1, 0) : back.normalize().add(0, 1.6D, 0);
            froggy.bloodBurst(at, dir, 60, 0.5F, 1.6F);
            froggy.addGore(0.6F);
            shake(victim, 1.4F, 20);
        }
        if (t > RIP_AT && t <= RIP_AT + 18 && (t - RIP_AT) % 2 == 0) {
            // o braço atravessado na boca, sacudindo (animação: 1,2-1,9 s): jorro de sangue
            froggy.spawnMouthBlood(4);
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

        for (int bite : EAT_BITES) {
            if (t == bite) {
                froggy.onChew();                       // crava
                froggy.speak(VoiceSituation.CHEWING);  // "mmmm" (se não estiver falando)
            } else if (t == bite + 2) {
                froggy.spawnMouthBlood(8);             // tranco: arranca o pedaço
                froggy.openMouth(5);
            } else if (t > bite + 4 && t <= bite + 19 && (t - bite) % 2 == 0) {
                froggy.spawnMouthBlood(3);             // sacudindo o pedaço na boca
                froggy.openMouth(3);
            }
        }
        if (t == EAT_SWALLOW) {
            froggy.finishEatingArm();
            froggy.speak(VoiceSituation.ATE_ARM, true); // "I love the taste of humans"
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
        if (d < 1.0E-3D || Math.abs(d - distance) < 0.08D) return;
        Vec3 spot = victim.position().add(dir.scale(distance / d));
        froggy.setPos(spot.x, froggy.getY(), spot.z);
    }

    /** Sai de cima da vítima e continua a caçada (não recua: ele não foge de ninguém). */
    private void endOnVictim() {
        releaseVictim();
        froggy.setAttackCooldown(20);
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

    /**
     * Fase 2 (ataque feroz): TODO ataque bate mais forte - língua, mordida,
     * esmagamento, pulo, comer vivo e arrancar o braço.
     */
    private float dmg(float base) {
        return froggy.isFrenzyActive() ? base * FRENZY_DAMAGE : base;
    }

    private void hurt(LivingEntity target, float base) {
        float before = target.getHealth();
        if (target.hurt(froggy.damageSources().mobAttack(froggy), dmg(base))) dealt(target, before);
    }

    /** Conta o dano que entrou de verdade (depois da armadura) pro cérebro. */
    private void dealt(LivingEntity target, float healthBefore) {
        if (froggy.getFroggyState().isAttack()) attackHit = true;
        froggy.onDealtDamage(target, Math.max(0F, healthBefore - target.getHealth()));
    }

    /** Dano sem o empurrão normal (vítima no chão fica no chão). */
    private void hurtNoKnockback(LivingEntity target, float base) {
        double kb = 0;
        AttributeInstance res = target.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (res != null) {
            kb = res.getBaseValue();
            res.setBaseValue(1.0D);
        }
        float before = target.getHealth();
        if (target.hurt(froggy.damageSources().mobAttack(froggy), dmg(base))) dealt(target, before);
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
