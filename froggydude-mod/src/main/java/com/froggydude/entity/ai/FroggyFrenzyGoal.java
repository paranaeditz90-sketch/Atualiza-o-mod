package com.froggydude.entity.ai;

import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Fase 2. Quando a vida cai abaixo de 60% (uma vez) e, depois, de vez em
 * quando: o Froggy para, se contorce/inclina o corpo por uns 2,5 segundos
 * (parado e vulnerável), e então dá o grito e sai correndo em cima do jogador
 * (velocidade, dano extra e resistência a projétil - ver FroggydudeEntity).
 * Só começa entre um ataque e outro, nunca no meio de um pulo.
 */
public class FroggyFrenzyGoal extends Goal {

    private static final int FRENZY_DURATION = 200;

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
        LivingEntity target = froggy.getTarget();
        if (target == null || !target.isAlive()) return false;
        if (froggy.getFroggyState().isCombatAction() || froggy.isVulnerableState()) return false;
        if (froggy.isFrenzyActive() || froggy.isFrenzyTired()) return false;

        if (froggy.shouldStartPhase2()) return true;
        return froggy.isInPhase2()
                && froggy.getFrenzyCooldown() <= 0
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
        froggy.setFroggyState(FroggyState.CONTORTING);
        froggy.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = froggy.getTarget();
        if (target != null) {
            froggy.getLookControl().setLookAt(target, 10.0F, 10.0F);
        }
    }

    @Override
    public void stop() {
        if (froggy.getFroggyState() == FroggyState.CONTORTING) {
            froggy.setFroggyState(FroggyState.CHASE);
        }
        froggy.startFrenzy(FRENZY_DURATION);
        froggy.playScream();
        froggy.setFrenzyCooldown(600 + froggy.getRandom().nextInt(400));
    }
}
