package com.froggydude.entity.ai;

import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModSounds;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Fase 2 (vs AJTHEBOLD 7:26 no YouTube, o vídeo "Eu sou o mod de terror" e o fim do manhunt contra o Grox:
 * aos 5:10 ele come os blazes, 5:16 se contorce gemendo com os ossos
 * estalando, 5:30 está à prova de bala). Duas etapas:
 *
 * 1. Contorção (vs AJTHEBOLD, 7:26 no YouTube): os ossos estalam e o tronco
 *    chicoteia - dobra de lado, a cabeça vai de ponta-cabeça, dobra até o chão
 *    e volta -, e ele fica encurvado com a cabeça tombada, balançando e dando
 *    trancos, de 2 a 4 segundos, gemendo (é quando dá pra bater nele).
 * 2. Ataque feroz: a cabeça levanta num estalo soltando o grito que se ouve
 *    de longe, ele cai de quatro e parte no galope por 30 segundos - mais
 *    rápido que um jogador correndo, de boca aberta, imune a projétil e com
 *    todos os ataques (língua, esmagamento, comer vivo...) batendo mais forte
 *    (ver FroggyCombatGoal).
 *
 * Começa quando ele come um blaze, ou quando a vida cai abaixo de 55% (uma
 * vez). Depois disso pode voltar, de vez em quando, se a vida estiver abaixo
 * de 50% e já tiver passado um tempo desde a última. Só começa entre um ataque
 * e outro, nunca no meio de um pulo ou em cima de alguém.
 */
public class FroggyFrenzyGoal extends Goal {

    /** Ticks da contorção (animação "contort", 2,2 s: encara, dobra, levanta, trava torto). */
    private static final int BEND_TICKS = FroggyState.CONTORTING.durationTicks;
    /** O grito sai quando a cabeça joga pra trás (animação "roar", 0,1 s). */
    private static final int ROAR_SCREAM_AT = 2;
    /** Estalos (batem com a animação: começa a dobrar 0,55 s, dobrado 0,7 s, tranco 1,15 s, trava 2,2 s). */
    private static final int[] BEND_CRACKS = {11, 14, 23, 43};

    private final FroggydudeEntity froggy;
    private int contortTicks;
    private int nextCrack;

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
        if (froggy.getFroggyState() == FroggyState.ROAR) return false;
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
        FroggyState s = froggy.getFroggyState();
        return froggy.isAlive() && (s == FroggyState.CONTORTING
                || (s == FroggyState.ROAR && froggy.getStateTicks() < FroggyState.ROAR.durationTicks));
    }

    @Override
    public void start() {
        froggy.markPhase2Started();
        froggy.stopStalking();
        froggy.setFroggyState(FroggyState.CONTORTING);
        froggy.getNavigation().stop();
        // contorção (2,2 s) e fica torto, com o braço na cabeça, de 2 a 4 s (no short ele
        // fica um tempão assim antes de disparar)
        contortTicks = BEND_TICKS + 40 + froggy.getRandom().nextInt(41);
        nextCrack = BEND_TICKS + 6;
        froggy.vocalize(ModSounds.PAIN.get(), 2.5F); // geme antes de tudo
    }

    @Override
    public void tick() {
        froggy.getNavigation().stop();
        int t = froggy.getStateTicks();

        if (froggy.getFroggyState() == FroggyState.CONTORTING) {
            for (int c : BEND_CRACKS) {
                if (t == c) crack();
            }
            if (t >= BEND_TICKS && t >= nextCrack) {
                // curvado: os ossos continuam estalando e ele geme
                crack();
                if (froggy.getRandom().nextInt(3) == 0) froggy.vocalize(ModSounds.PAIN.get(), 2.0F);
                nextCrack = t + 8 + froggy.getRandom().nextInt(14);
            }
            if (t >= contortTicks) {
                // etapa 2: levanta e grita
                froggy.setFroggyState(FroggyState.ROAR);
                facePrey();
            }
            return;
        }

        // ROAR
        if (t == ROAR_SCREAM_AT) {
            froggy.playScream();
            crack();
        }
        if (t < ROAR_SCREAM_AT) facePrey();
    }

    private void facePrey() {
        LivingEntity target = froggy.getTarget();
        if (target != null) {
            froggy.faceTarget(target);
        }
    }

    private void crack() {
        froggy.playSound(ModSounds.CRACK.get(), 1.6F, 0.8F + froggy.getRandom().nextFloat() * 0.4F);
    }

    @Override
    public void stop() {
        FroggyState s = froggy.getFroggyState();
        if (s == FroggyState.CONTORTING || s == FroggyState.ROAR) {
            froggy.setFroggyState(froggy.getTarget() != null ? FroggyState.CHASE : FroggyState.IDLE);
        }
        if (froggy.isAlive()) {
            froggy.startFrenzy(FroggydudeEntity.FRENZY_TICKS);
        }
    }
}
