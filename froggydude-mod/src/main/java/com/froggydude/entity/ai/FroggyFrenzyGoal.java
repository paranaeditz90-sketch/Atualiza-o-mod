package com.froggydude.entity.ai;

import net.minecraft.util.Mth;

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
 *    e volta -, e ele fica encurvado com a cabeça tombada, gemendo, depois um
 *    segundo calado e então solta o grito que se ouve de longe, ainda parado e
 *    torto (é quando dá pra bater nele; os tempos são os do vídeo do Grox).
 * 2. Ataque feroz: no meio do grito a cabeça levanta num estalo, ele cai de
 *    quatro e parte no galope por 30 segundos - mais
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

    /** Ticks da contorção (animação "contort", 2,95 s: a rotoscopia do vídeo do LM, 89,28-91,75 s). */
    private static final int BEND_TICKS = FroggyState.CONTORTING.durationTicks;
    /** A cabeça joga pra trás (animação "roar", 0,1 s): o estalo (e o grito, se ainda não saiu). */
    private static final int ROAR_SCREAM_AT = 2;
    /**
     * Os estalos da dobra, nos trancos da rotoscopia (os ticks em que a pose anda mais
     * rápido em contort_fit.json: o 1º tranco 0,5-0,6 s, a cabeça afundando 1,1-1,3 s,
     * o ombro subindo 2,2-2,5 s). Com o pacote de voz eles (e os gemidos) ficam mudos:
     * o CONTORT toca o som original, que já tem os estalos.
     */
    private static final int[] CRACK_AT = {3, 10, 12, 18, 22, 25, 31, 37, 45, 51};
    /**
     * O som da contorção entra quando a dobra rápida aparece (a animação começa devagar,
     * 0,46 s até a 1ª pose, mais a transição): no short o gemido começa junto com a dobra.
     */
    private static final int CONTORT_AT = 8;
    /** O gemido curto no 1º tranco e quando o ombro sobe (a voz tem 1,5 s de folga entre uma e outra). */
    private static final int[] GRUNT_AT = {11, 45};
    /**
     * O grito, ainda parado e torto (vídeo do Grox, "Manhunt but I have RABIES", depois dos
     * blazes): o gemido começa em 315,4 s, acaba, 1 s de silêncio, e o "AAAAAAHHH!!!" sai em
     * 319,86 (4,5 s depois do começo do gemido), morrendo por 3,7 s com ele parado lá no fundo
     * do corredor; a continuação, alta e colada, entra quando ele dispara (323,55), e ele segue
     * gritando correndo enquanto o Grox atira (até 333,6). O pacote de voz toca tudo num clipe
     * só (froggydude.scream, 13,6 s: o grito isolado, sem as falas nem os tiros do Grox); sem
     * ele é o urro do mod no mesmo lugar.
     */
    private static final int SCREAM_AT = CONTORT_AT + 90;
    /** O clipe do grito (13,6 s): ele não fala nem grunhe por cima. */
    private static final int SCREAM_TICKS = 272;
    /** A arrancada: o ROAR começa 7 ticks antes da continuação, pra ela cair no "inclina e arranca". */
    private static final int CONTORT_TOTAL = SCREAM_AT + 74 - 7;

    private final FroggydudeEntity froggy;
    private int contortTicks;
    private int nextCrack;
    private boolean screamed;
    /** Pra onde ele estava virado quando começou: contorce sem girar atrás de quem bate. */
    private float lockedYaw;

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
        if (Boolean.getBoolean("froggydude.debug")) System.out.println("[FROGGYDEBUG] fase 2: contorção começou (vida "
                + Math.round(froggy.getHealth()) + "/" + Math.round(froggy.getMaxHealth()) + ")");
        froggy.markPhase2Started();
        froggy.stopStalking();
        froggy.setFroggyState(FroggyState.CONTORTING);
        froggy.getNavigation().stop();
        lockedYaw = froggy.getYRot();
        holdYaw();
        // contorção (2,95 s) e fica torto, com o ombro deslocado, gemendo, calado e então
        // gritando, até disparar (8,25 s ao todo, os tempos do vídeo do Grox)
        contortTicks = CONTORT_TOTAL;
        nextCrack = BEND_TICKS + 6;
        screamed = false;
        // calado do começo ao grito (nada de "I'm hungry" no meio do gemido ou do silêncio)
        froggy.mute(SCREAM_AT);
        // os sons vêm no tick(), junto com os trancos.
        // boca FECHADA: no vídeo ele se contorce com a cara de choro de sempre, só abre no grito
    }

    @Override
    public void tick() {
        froggy.getNavigation().stop();
        int t = froggy.getStateTicks();

        if (froggy.getFroggyState() == FroggyState.CONTORTING) {
            holdYaw();
            if (t < BEND_TICKS) {
                // com o pacote de voz, o som original inteiro (3,4 s)
                if (t == CONTORT_AT) froggy.playSound(ModSounds.CONTORT.get(), 2.5F, 1.0F);
                for (int c : CRACK_AT) if (t == c) crack();
                for (int g : GRUNT_AT) if (t == g) froggy.vocalize(ModSounds.CONTORT_GRUNT.get(), 1.6F, false);
            } else if (t < SCREAM_AT && t >= nextCrack) {
                // curvado: os ossos continuam estalando e ele grunhe (o "pain" tem fala,
                // "freaking spicy", que não cabe aqui); com o pacote, fica o silêncio do vídeo
                crack();
                if (froggy.getRandom().nextInt(3) == 0) froggy.vocalize(ModSounds.CONTORT_GRUNT.get(), 1.6F, false);
                nextCrack = t + 8 + froggy.getRandom().nextInt(14);
            }
            if (t == SCREAM_AT) {
                // grita ainda torto, de boca aberta até disparar
                froggy.playScream(SCREAM_TICKS);
                froggy.openMouth(contortTicks - SCREAM_AT + 20);
                screamed = true;
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
            if (!screamed) froggy.playScream();
            crack();
        }
        if (t < ROAR_SCREAM_AT) facePrey();
    }

    private void holdYaw() {
        float r = lockedYaw * Mth.DEG_TO_RAD;
        froggy.faceDirection(-Mth.sin(r), Mth.cos(r));
    }

    private void facePrey() {
        LivingEntity target = froggy.getTarget();
        if (target != null) {
            froggy.faceTarget(target);
        }
    }

    private void crack() {
        froggy.playSound(ModSounds.CONTORT_CRACK.get(), 1.6F, 0.8F + froggy.getRandom().nextFloat() * 0.4F);
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
