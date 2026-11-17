package com.froggydude.entity;

import com.froggydude.init.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;

/**
 * (v0.7.40) A RAIVA depois das balas (LM 16/11: "os braços dele em '()' e estralar do pescoço quando ele tá COM MUITA
 * RAIVA após o jogador tentar matar ele com balas. Ele avança rápido e para só para o jogador recuar"; vídeo 4 dele).
 * <p>
 * Levantou do ragdoll depois de levar bala caído: estala o pescoço (a cabeça tomba de lado num tranco) e grita com a
 * boca escancarada; aí AVANÇA correndo agachado e PARA colado ("()": agachado fundo, braços abertos até o chão, boca
 * aberta), só pra pessoa recuar. Recuou? Avança de novo e para. Depois de umas vezes (ou se a pessoa chega perto ou bate
 * nele), parte pra cima. Sem as Goals enquanto dura (ele mesmo anda e olha); o cliente escolhe a animação pela fase
 * ({@link #phase}).
 */
public final class Rage {

    public static final byte CRACK = 0, RUSH = 1, STAND = 2, STAND_CRACK = 3;
    /** O estalo do pescoço ("rage_crack", 0,6 s). */
    public static final int CRACK_TICKS = 12;
    /** Avança até ficar a isso da pessoa (o vídeo 4: para a ~3 blocos). */
    static final double STOP_DIST = 3.2D;
    /** Ela recuou pra mais longe que isso: avança de novo. */
    static final double AGAIN_DIST = 6.5D;
    /** Chegou mais perto que isso dele parado: acabou a paciência. */
    static final double TOO_CLOSE = 2.0D;
    static final double RUSH_SPEED = 2.4D;
    static final int RUSH_MAX = 50;
    static final int STAND_MIN = 30;
    static final int STAND_MAX = 70;
    static final int RUSHES = 3;

    private Rage() {
    }

    /** O que o servidor guarda enquanto dura. */
    static final class Run {
        final LivingEntity target;
        byte phase = CRACK;
        int phaseAt;
        int standFor;
        int rushes;
        /** A que distância ele parou (a pessoa chegar MAIS perto que isso é que conta). */
        double standDist;
        boolean cracked;
        boolean hit;

        Run(LivingEntity target) {
            this.target = target;
        }
    }

    /** Levantou do ragdoll depois de levar bala caído, com alguém pra odiar: começa. */
    static boolean begin(FroggydudeEntity f, @Nullable Player target) {
        if (target == null || !(f.level() instanceof ServerLevel)) return false;
        f.beginRage(new Run(target));
        f.playSound(ModSounds.CRACK.get(), 1.2F, 0.8F);
        f.playScream(60);
        f.openMouth(40);
        f.brainLog("raiva: estalei o pescoço, agora " + target.getScoreboardName() + " vai recuar");
        return true;
    }

    static void tick(FroggydudeEntity f, Run run) {
        LivingEntity t = run.target;
        int age = f.tickCount - run.phaseAt;
        if (!t.isAlive() || t.isSpectator() || t.level() != f.level() || f.distanceToSqr(t) > 48D * 48D) {
            f.endRage();
            return;
        }
        f.getLookControl().setLookAt(t, 30F, 30F);
        double d = Math.sqrt(f.distanceToSqr(t));
        switch (run.phase) {
            case CRACK -> {
                f.getNavigation().stop();
                if (age >= CRACK_TICKS) rush(f, run);
            }
            case RUSH -> {
                if (age % 5 == 0) f.getNavigation().moveTo(t, RUSH_SPEED);
                // freia antes, pelo embalo (correndo a ~0,5 bloco/tick ele ainda anda uns 2 blocos)
                double v = f.getDeltaMovement().horizontalDistance();
                if (d <= STOP_DIST + v * 3D || age >= RUSH_MAX) stand(f, run, d);
            }
            case STAND, STAND_CRACK -> {
                f.getNavigation().stop();
                if (run.phase == STAND_CRACK && age >= CRACK_TICKS) {
                    run.phase = STAND;
                    f.setRagePhase(STAND);
                }
                // no meio da parada, uma vez: o estalo do pescoço de novo
                if (!run.cracked && run.phase == STAND && age == run.standFor / 2) {
                    run.cracked = true;
                    run.phase = STAND_CRACK;
                    f.setRagePhase(STAND_CRACK);
                    f.playSound(ModSounds.CRACK.get(), 1.0F, 0.9F + f.getRandom().nextFloat() * 0.2F);
                }
                // a pessoa veio pra cima dele parado (não é ele que parou colado): acabou a paciência
                boolean closer = age >= 10 && d < TOO_CLOSE && d < run.standDist - 0.8D;
                if (run.hit || closer) {
                    f.brainLog("raiva: chegou perto demais - agora é ataque");
                    f.endRage();
                    return;
                }
                if (age >= STAND_MIN && d > AGAIN_DIST && run.rushes < RUSHES) {
                    rush(f, run);
                } else if (age >= run.standFor) {
                    if (run.rushes >= RUSHES || d > AGAIN_DIST) {
                        f.endRage();
                    } else {
                        rush(f, run);
                    }
                }
            }
            default -> f.endRage();
        }
    }

    private static void rush(FroggydudeEntity f, Run run) {
        run.phase = RUSH;
        run.phaseAt = f.tickCount;
        run.rushes++;
        f.setRagePhase(RUSH);
        f.getNavigation().moveTo(run.target, RUSH_SPEED);
        f.ribbit(2.0F);
    }

    private static void stand(FroggydudeEntity f, Run run, double d) {
        run.phase = STAND;
        run.standDist = d;
        run.phaseAt = f.tickCount;
        run.cracked = false;
        run.standFor = STAND_MIN + f.getRandom().nextInt(STAND_MAX - STAND_MIN + 1);
        f.getNavigation().stop();
        f.setDeltaMovement(f.getDeltaMovement().multiply(0.2D, 1D, 0.2D)); // freia seco
        f.setRagePhase(STAND);
        f.openMouth(run.standFor);
    }
}
