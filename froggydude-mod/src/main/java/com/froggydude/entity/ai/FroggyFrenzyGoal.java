package com.froggydude.entity.ai;

import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Fase 2 (vídeo "I'm the horror mod" e o manhunt dos blazes): o Froggy para,
 * se contorce com a cabeça girando quase de ponta-cabeça e solta um grito que
 * se ouve de longe. Depois corre por 30 segundos mais rápido que um jogador,
 * gemendo e reclamando de dor (velocidade, dano extra e quase imune a flecha -
 * ver FroggydudeEntity).
 *
 * Começa quando ele come um blaze, ou quando a vida cai abaixo de 55% (uma
 * vez). Depois disso pode voltar, de vez em quando, se a vida estiver abaixo
 * de 50% e já tiver passado um tempo desde a última. Só começa entre um ataque
 * e outro, nunca no meio de um pulo ou em cima de alguém.
 */
public class FroggyFrenzyGoal extends Goal {

    private final FroggydudeEntity froggy;

    public FroggyFrenzyGoal(FroggydudeEntity froggy) {
        this.froggy = froggy;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean canUse() {
        if (froggy.getFroggyState().isCombatAction() || froggy.isVulnerableState()) return false;
        if (froggy.isFrenzyActive() || froggy.isFrenzyTired()) return false;
        if (froggy.shouldStartPhase2()) return true;

        LivingEntity target = froggy.getTarget();
        if (target == null || !target.isAlive()) return false;
        return froggy.isInPhase2()
                && froggy.getFrenzyCooldown() <= 0
                && froggy.getHealth() < froggy.getMaxHealth() * 0.5F
                && froggy.getRandom().nextInt(60) == 0;
    }

    @Override
    public boolean canContinueToUse() {
        return froggy.getFroggyState() == FroggyState.CONTORTING
                && froggy.getStateTicks() < FroggyState.CONTORTING.durationTicks;
    }

    @Override
    public void start() {
        froggy.markPhase2Started();
        froggy.stopStalking();
        froggy.setFroggyState(FroggyState.CONTORTING);
        froggy.getNavigation().stop();
        froggy.playScream(); // o grito vem junto com a contorção, como no vídeo
    }

    @Override
    public void tick() {
        froggy.getNavigation().stop();
        LivingEntity target = froggy.getTarget();
        if (target != null) {
            froggy.getLookControl().setLookAt(target, 10.0F, 10.0F);
        }
    }

    @Override
    public void stop() {
        if (froggy.getFroggyState() == FroggyState.CONTORTING) {
            froggy.setFroggyState(froggy.getTarget() != null ? FroggyState.CHASE : FroggyState.IDLE);
        }
        froggy.startFrenzy(FroggydudeEntity.FRENZY_TICKS);
    }
}
