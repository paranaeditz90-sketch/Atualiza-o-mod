package com.froggydude.entity.ai;

import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.BloodFx;
import com.froggydude.entity.BloodType;
import com.froggydude.entity.FeedLock;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Quando a vida do Froggy está baixa, ele ignora o jogador, procura um mob
 * próximo (de preferência já ferido) e corre até ele, derruba e devora pra
 * recuperar vida. Se apanhar durante a refeição, larga tudo e revida na
 * hora (ver FroggydudeEntity#hurt).
 *
 * E ele está sempre com fome (LM, v0.4.3): sem gente na mira e de barriga vazia
 * (FroggydudeEntity#isHungry, uns 15-30 s depois de comer), qualquer bicho que
 * estiver por perto vira comida - vaca, porco, ovelha, golem, o cachorro de alguém.
 *
 * Blaze é diferente: ele vai atrás de blaze sempre que vê um, com ou sem fome.
 * Comer blaze arde ("freaking spicy") e joga ele direto na fase 2.
 *
 * Aldeão é a comida favorita (LM: "players e aldeões, mesmas animações"): sem
 * ninguém pra caçar, ele vai atrás do aldeão que vir; dá o bote, come em
 * flexão uns segundos e passa pra sequência da zebra (FroggyFinisherGoal):
 * arranca os braços (uma peça só, cruzada) e a cabeça. Aldeão criança fica fora.
 */
public class FroggyFeedGoal extends Goal {

    private static final double SEARCH_RADIUS = 16.0D;
    /**
     * Comendo, de bruços em cima do bicho caído: a boca dele fica 1 bloco na frente
     * dos pés (rig da flexão) - em cima do meio do corpo deitado.
     */
    private static final double FEED_DISTANCE = 1.0D;
    /** Aldeão: 3 s de flexão (10 mordidas) antes de arrancar os braços e a cabeça. */
    private static final int VILLAGER_PUSHUPS = 60;
    /** O bote (animação jump_pin, igual ao do jogador): impulso no tick 4, 11 ticks no ar. */
    private static final int POUNCE_LAUNCH = 4;
    private static final int POUNCE_AIR = 11;
    private static final double POUNCE_VY = 0.40D;

    private final FroggydudeEntity froggy;
    private int chaseTimeout;
    private boolean pouncing;

    public FroggyFeedGoal(FroggydudeEntity froggy) {
        this.froggy = froggy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (froggy.getFroggyState().isCombatAction() || froggy.isFrenzyActive()) return false;
        if (froggy.getFroggyState() == FroggyState.CONTORTING || froggy.getFroggyState() == FroggyState.ROAR) return false;
        if (froggy.getFeedSearchCooldown() > 0) return false;

        LivingEntity blaze = findBlaze();
        if (blaze != null) {
            froggy.setFeedTarget(blaze);
            return true;
        }
        // speedrun: aldeão perto de quem quer zerar vira comida (sem troca de pérola/cama)
        LivingEntity villager = findSabotageVillager();
        if (villager != null) {
            froggy.setFeedTarget(villager);
            froggy.brainLog("comendo o aldeão perto da vítima (sem troca pra quem quer zerar)");
            return true;
        }
        // a comida favorita: sem gente pra caçar, o aldeão que ele vê vira a refeição
        if (froggy.wantsVillager() && !(froggy.getTarget() instanceof Player) && !froggy.isHolding()) {
            LivingEntity v = findFavoriteVillager();
            if (v != null) {
                froggy.setFeedTarget(v);
                froggy.brainLog("aldeão à vista: comida favorita");
                return true;
            }
        }
        // sempre com fome (LM): sem gente na mira e de barriga vazia, qualquer bicho serve
        boolean starving = froggy.isHungry() && !(froggy.getTarget() instanceof Player) && !froggy.isHolding();
        if (!froggy.isLowHealth() && !starving) return false;

        LivingEntity victim = findVictim();
        if (victim == null) {
            froggy.setFeedSearchCooldown(60);
            return false;
        }
        froggy.setFeedTarget(victim);
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity victim = froggy.getFeedTarget();
        return victim != null && victim.isAlive()
                && (froggy.getFroggyState() == FroggyState.FEEDING || chaseTimeout > 0);
    }

    @Override
    public void start() {
        chaseTimeout = 100;
        pouncing = false;
        froggy.setFroggyState(FroggyState.CHASE);
    }

    @Override
    public void stop() {
        LivingEntity victim = froggy.getFeedTarget();
        if (victim != null) FeedLock.release(victim);
        froggy.setFeedTarget(null);
        froggy.setFeedSearchCooldown(100);
        FroggyState st = froggy.getFroggyState();
        if (st == FroggyState.FEEDING || pouncing && st == FroggyState.JUMP_PIN) {
            froggy.setFroggyState(FroggyState.CHASE);
        }
        pouncing = false;
    }

    @Override
    public void tick() {
        LivingEntity victim = froggy.getFeedTarget();
        if (victim == null) return;

        if (froggy.getFroggyState() == FroggyState.FEEDING) {
            eat(victim);
            return;
        }
        if (pouncing) {
            if (froggy.getFroggyState() == FroggyState.JUMP_PIN) {
                pounce(victim);
                return;
            }
            pouncing = false; // apanhou no ar e largou
        }
        // apanhou no meio (largou a comida pra revidar): o bicho levanta
        FeedLock.release(victim);

        chaseTimeout--;
        froggy.getLookControl().setLookAt(victim, 30F, 30F);
        froggy.getNavigation().moveTo(victim, 1.3D);

        double dist = froggy.distanceTo(victim);
        if (favorite(victim) && dist >= 2.6D && dist <= 6.5D && froggy.onGround() && victim.onGround()
                && froggy.hasLineOfSight(victim) && Math.abs(victim.getY() - froggy.getY()) < 1.2D) {
            // aldeão: o bote, igual ao do jogador
            pouncing = true;
            chaseTimeout = Math.max(chaseTimeout, 40);
            froggy.getNavigation().stop();
            froggy.faceTarget(victim);
            froggy.setFroggyState(FroggyState.JUMP_PIN);
            return;
        }
        if (dist <= (victim instanceof Blaze ? 2.8D : 2.2D)) tackle(victim);
    }

    /** Derruba e começa a devorar. */
    private void tackle(LivingEntity victim) {
        froggy.faceTarget(victim);
        victim.hurt(froggy.damageSources().mobAttack(froggy), 4.0F);
        FeedLock.lock(victim);
        froggy.setFroggyState(FroggyState.FEEDING);
        froggy.getNavigation().stop();
    }

    /** No ar até cair em cima (a animação jump_pin): no pouso já começa a flexão. */
    private void pounce(LivingEntity victim) {
        int t = froggy.getStateTicks();
        if (t < POUNCE_LAUNCH) {
            froggy.faceTarget(victim);
            return;
        }
        if (t == POUNCE_LAUNCH) {
            // o aldeão trava de medo; ele cai na distância de comer
            FeedLock.lock(victim);
            double dx = victim.getX() - froggy.getX(), dz = victim.getZ() - froggy.getZ();
            double d = Math.max(1.0E-3D, Math.sqrt(dx * dx + dz * dz));
            double vh = Math.max(0D, d - FEED_DISTANCE) / FroggyCombatGoal.horizontalFactor(POUNCE_AIR);
            froggy.faceDirection(dx, dz);
            froggy.setDeltaMovement(dx / d * vh, POUNCE_VY, dz / d * vh);
            froggy.setNoFallTicks(60);
            froggy.hasImpulse = true;
            froggy.onLeapLaunch();
            return;
        }
        if ((froggy.onGround() && t >= POUNCE_LAUNCH + 5) || t >= POUNCE_LAUNCH + POUNCE_AIR + 4) {
            pouncing = false;
            froggy.spawnBlood(victim, 4);
            tackle(victim);
        }
    }

    /** Aldeão adulto: a comida favorita, que leva a sequência inteira (bote, flexão, braços, cabeça). */
    private static boolean favorite(LivingEntity e) {
        return e instanceof AbstractVillager v && !v.isBaby();
    }

    /**
     * A refeição ("Taste testing Minecraft mobs"): o bicho caído de lado, ele de
     * bruços por cima fazendo "flexão" - a cabeça desce mordendo a cada 0,3 s e
     * arranca pedaço (sangue, ou lasca da cor do que o bicho é feito).
     */
    private void eat(LivingEntity victim) {
        int t = froggy.getStateTicks();
        FeedLock.lock(victim);
        froggy.getNavigation().stop();
        froggy.setDeltaMovement(0, froggy.getDeltaMovement().y, 0);
        Vec3 dir = froggy.position().subtract(victim.position()).multiply(1, 0, 1);
        double d = dir.length();
        if (d > 1.0E-3D && Math.abs(d - FEED_DISTANCE) > 0.08D) {
            Vec3 spot = victim.position().add(dir.scale(FEED_DISTANCE / d));
            froggy.setPos(spot.x, froggy.getY(), spot.z);
        }
        if (t % 10 == 0) froggy.faceTarget(victim);
        if (favorite(victim) && t >= VILLAGER_PUSHUPS) {
            // chega de flexão: agora arranca os braços e a cabeça (mesma tabela do jogador).
            // Sem alvo de comida esta Goal sai, e a do golpe final pega a mesma vítima
            froggy.setFeedTarget(null);
            froggy.requestFinisher(victim);
            return;
        }
        if (FroggydudeEntity.pushupBite(t)) {
            froggy.onPushupBite(t);
            BloodType type = BloodType.of(victim);
            Vec3 at = victim.position().add(0, Math.min(0.5D, victim.getBbWidth() * 0.5D), 0);
            if (type.bleeds()) {
                froggy.spawnBlood(victim, 2);
            } else {
                // golem, esqueleto, blaze...: lascas do que ele é feito voando da boca
                BloodFx.spray(froggy.level(), at, new Vec3(0, 1, 0), 5, 0.22F, BloodType.residue(victim));
            }
            if ((t / FroggydudeEntity.PUSHUP_TICKS) % 2 == 0) froggy.stainMouth(victim, 0.08F);
            if ((t / FroggydudeEntity.PUSHUP_TICKS) % 3 == 1) {
                froggy.spawnMouthBlood(2, BloodType.residue(victim), type.bleeds());
            }
        }
        if (t >= FroggyState.FEEDING.durationTicks) {
            FeedLock.release(victim);
            froggy.eatVictim(victim);
            froggy.setFeedTarget(null);
        }
    }

    private LivingEntity findBlaze() {
        AABB area = froggy.getBoundingBox().inflate(SEARCH_RADIUS, 6.0D, SEARCH_RADIUS);
        return froggy.level().getEntitiesOfClass(Blaze.class, area, e -> e.isAlive() && froggy.hasLineOfSight(e))
                .stream().min(Comparator.comparingDouble(froggy::distanceTo)).orElse(null);
    }

    /**
     * Manhunt speedrun, longe da luta: o aldeão mais perto dele que esteja perto de
     * alguém que ele caça ("I guess Froggy ate this villager", vs Grox 4:15).
     */
    private LivingEntity findSabotageVillager() {
        if (!(froggy.level() instanceof net.minecraft.server.level.ServerLevel level)) return null;
        com.froggydude.world.FroggyWorldData data = com.froggydude.world.FroggyWorldData.get(level);
        if (!data.manhuntActive || !data.released || data.manhuntMode != com.froggydude.world.ManhuntMode.SPEEDRUN) return null;
        if (froggy.isHolding()) return null;
        List<net.minecraft.server.level.ServerPlayer> runners = level.players().stream()
                .filter(p -> !p.isSpectator() && !p.isCreative()).toList();
        for (Player p : runners) {
            if (p.distanceTo(froggy) < 12.0D) return null; // luta primeiro
        }
        AABB area = froggy.getBoundingBox().inflate(24.0D);
        return level.getEntitiesOfClass(net.minecraft.world.entity.npc.AbstractVillager.class, area,
                        v -> v.isAlive() && runners.stream().anyMatch(p -> p.distanceTo(v) < 48.0D))
                .stream().min(Comparator.comparingDouble(froggy::distanceTo)).orElse(null);
    }

    private LivingEntity findVictim() {
        AABB area = froggy.getBoundingBox().inflate(SEARCH_RADIUS * FroggyConfig.ai().sense);
        List<LivingEntity> nearby = froggy.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != froggy && e instanceof Mob && !(e instanceof Monster) && !(e instanceof Player)
                        && !(e instanceof AbstractVillager v && v.isBaby()));
        if (nearby.isEmpty()) return null;

        // aldeão primeiro (a comida favorita); depois os feridos (% de vida menor) e os mais próximos
        return nearby.stream()
                .min(Comparator
                        .comparingInt((LivingEntity e) -> favorite(e) ? 0 : 1)
                        .thenComparingDouble((LivingEntity e) -> e.getHealth() / e.getMaxHealth())
                        .thenComparingDouble(froggy::distanceTo))
                .orElse(null);
    }

    /** O aldeão adulto mais perto que ele está vendo. */
    private LivingEntity findFavoriteVillager() {
        AABB area = froggy.getBoundingBox().inflate(SEARCH_RADIUS, 6.0D, SEARCH_RADIUS);
        return froggy.level().getEntitiesOfClass(AbstractVillager.class, area,
                        v -> v.isAlive() && !v.isBaby() && froggy.hasLineOfSight(v))
                .stream().min(Comparator.comparingDouble(froggy::distanceTo)).orElse(null);
    }
}
