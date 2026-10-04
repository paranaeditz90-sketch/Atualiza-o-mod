package com.froggydude.entity.ai;

import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Quando a vida do Froggy está baixa, ele ignora o jogador, procura um mob
 * próximo (de preferência já ferido) e corre até ele, derruba e devora pra
 * recuperar vida. Se apanhar durante a refeição, larga tudo e revida na
 * hora (ver FroggydudeEntity#hurt).
 *
 * Blaze é diferente: ele vai atrás de blaze sempre que vê um, com ou sem fome.
 * Comer blaze arde ("freaking spicy") e joga ele direto na fase 2.
 */
public class FroggyFeedGoal extends Goal {

    private static final double SEARCH_RADIUS = 16.0D;

    private final FroggydudeEntity froggy;
    private int chaseTimeout;

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
        if (froggy.getFeedSearchCooldown() > 0) return false;

        LivingEntity blaze = findBlaze();
        if (blaze != null) {
            froggy.setFeedTarget(blaze);
            return true;
        }
        if (!froggy.isLowHealth()) return false;

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
        froggy.setFroggyState(FroggyState.CHASE);
    }

    @Override
    public void stop() {
        froggy.setFeedTarget(null);
        froggy.setFeedSearchCooldown(100);
        if (froggy.getFroggyState() == FroggyState.FEEDING) {
            froggy.setFroggyState(FroggyState.CHASE);
        }
    }

    @Override
    public void tick() {
        LivingEntity victim = froggy.getFeedTarget();
        if (victim == null) return;

        if (froggy.getFroggyState() == FroggyState.FEEDING) {
            if (froggy.getStateTicks() >= FroggyState.FEEDING.durationTicks) {
                froggy.eatVictim(victim);
                froggy.setFeedTarget(null);
            }
            return;
        }

        chaseTimeout--;
        froggy.getLookControl().setLookAt(victim, 30F, 30F);
        froggy.getNavigation().moveTo(victim, 1.3D);

        if (froggy.distanceTo(victim) <= (victim instanceof Blaze ? 2.8D : 2.2D)) {
            // derruba e começa a devorar
            froggy.faceTarget(victim);
            victim.hurt(froggy.damageSources().mobAttack(froggy), 4.0F);
            froggy.setFroggyState(FroggyState.FEEDING);
            froggy.getNavigation().stop();
        }
    }

    private LivingEntity findBlaze() {
        AABB area = froggy.getBoundingBox().inflate(SEARCH_RADIUS, 6.0D, SEARCH_RADIUS);
        return froggy.level().getEntitiesOfClass(Blaze.class, area, e -> e.isAlive() && froggy.hasLineOfSight(e))
                .stream().min(Comparator.comparingDouble(froggy::distanceTo)).orElse(null);
    }

    private LivingEntity findVictim() {
        AABB area = froggy.getBoundingBox().inflate(SEARCH_RADIUS);
        List<LivingEntity> nearby = froggy.level().getEntitiesOfClass(LivingEntity.class, area,
                e -> e.isAlive() && e != froggy && !(e instanceof Monster) && !(e instanceof Player));
        if (nearby.isEmpty()) return null;

        // prioriza alvos feridos (% de vida menor) e depois os mais próximos
        return nearby.stream()
                .min(Comparator
                        .comparingDouble((LivingEntity e) -> e.getHealth() / e.getMaxHealth())
                        .thenComparingDouble(froggy::distanceTo))
                .orElse(null);
    }
}
