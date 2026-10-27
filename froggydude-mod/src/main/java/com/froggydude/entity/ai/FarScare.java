package com.froggydude.entity.ai;

import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.voice.VoiceSituation;
import com.froggydude.init.ModSounds;
import com.froggydude.player.PlayerSight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Ele te viu lá de longe (v0.7.5): sorteia se vai direto pra cima ou se primeiro te dá
 * medo (LM: "ele vai poder escolher entre dar medo ao player ou atacar direto" - o
 * sorteio e o jeito do medo foram escolha do LM). O medo é o barulho sem aparecer:
 * chega por trás sem ser visto, a uns 10 blocos faz barulho (passos, ribbit, passos,
 * a risadinha) e, se ela virar e ver, some. Depois fica sumido um tempo e vem a caçada.
 * Vale do Mín ao Máx. Na REAL não: lá é "viu, vai" (v0.7.15, escolha do LM).
 */
final class FarScare {

    /** Mais longe que isso, quando ele começa a caçada, é "de longe". */
    static final double FAR = 32.0D;
    /** Chegando por trás: desiste do susto (e ataca) se não chegar em 45 s. */
    private static final int APPROACH_MAX = 900;
    /** Sumido depois do susto, antes de atacar. */
    private static final int HIDE_MIN = 160;
    private static final int HIDE_RANDOM = 160;

    private final FroggydudeEntity froggy;
    private final RealTactics tactics;

    @Nullable
    private UUID who;
    private int startedAt;
    private int stage;
    private int stageAt;
    private int noises;
    private int nextNoise;
    private int hideFor;
    @Nullable
    private Vec3 hide;

    FarScare(FroggydudeEntity froggy, RealTactics tactics) {
        this.froggy = froggy;
        this.tactics = tactics;
    }

    boolean active() {
        return who != null;
    }

    void start(ServerPlayer p) {
        who = p.getUUID();
        startedAt = froggy.tickCount;
        stage = 0;
        stageAt = froggy.tickCount;
        noises = 0;
        hide = null;
        froggy.stopStalking();
    }

    void stop() {
        who = null;
        hide = null;
    }

    /** Devolve true se cuidou do tick (assustando: nada de atacar). */
    boolean tick(LivingEntity target, double dist) {
        if (who == null) return false;
        if (!(target instanceof ServerPlayer p) || !p.getUUID().equals(who) || froggy.isFrenzyActive()) {
            stop();
            return false;
        }
        // apanhou dela: acabou o susto
        if (froggy.getLastHurtByMob() == p && froggy.getLastHurtByMobTimestamp() > startedAt) {
            froggy.brainLog(p.getScoreboardName() + " me bateu: acabou o susto");
            stop();
            return false;
        }
        froggy.mute(10);
        boolean seen = PlayerSight.sees(p, froggy);
        switch (stage) {
            case 0 -> {
                // chegando por trás, sem ser visto: de longe galopando, perto de mansinho
                if (seen && dist < 40.0D && froggy.tickCount - stageAt > 20) {
                    vanish(p);
                    return true;
                }
                if (froggy.tickCount % 10 == 0) {
                    tactics.moveTo(tactics.blindSpot(p, 11.0D), FroggyCombatGoal.huntSpeed() * (dist > 24.0D ? 0.9D : 0.5D));
                }
                froggy.getLookControl().setLookAt(p, 30.0F, 30.0F);
                if (dist < 14.0D && froggy.tickCount - stageAt > 40) {
                    next();
                    nextNoise = froggy.tickCount;
                    froggy.brainLog("atrás de " + p.getScoreboardName() + " sem ser visto: barulho");
                } else if (froggy.tickCount - stageAt > APPROACH_MAX) {
                    stop();
                    return false;
                }
            }
            case 1 -> {
                if (seen) {
                    vanish(p);
                    return true;
                }
                if (froggy.tickCount % 12 == 0) tactics.moveTo(tactics.blindSpot(p, 10.0D), FroggyCombatGoal.huntSpeed() * 0.4D);
                froggy.getLookControl().setLookAt(p, 30.0F, 30.0F);
                if (froggy.tickCount >= nextNoise) {
                    makeNoise(froggy, noises++);
                    nextNoise = froggy.tickCount + 35 + froggy.getRandom().nextInt(30);
                }
                if (noises >= 4) vanish(p);
            }
            default -> {
                // sumido: longe dela, fora da vista, olhando; depois, a caçada
                if (hide != null && froggy.tickCount % 20 == 0) tactics.moveTo(hide, FroggyCombatGoal.huntSpeed() * 1.05D);
                if (hide == null || froggy.getNavigation().isDone()) froggy.getLookControl().setLookAt(p, 30.0F, 30.0F);
                if (froggy.tickCount - stageAt > hideFor) {
                    froggy.brainLog("chega de susto em " + p.getScoreboardName() + ": hora de comer");
                    stop();
                    return false;
                }
            }
        }
        return true;
    }

    private void next() {
        stage++;
        stageAt = froggy.tickCount;
    }

    /** Sai correndo pra longe dela (some) e espera um pouco. */
    private void vanish(ServerPlayer p) {
        stage = 2;
        stageAt = froggy.tickCount;
        hideFor = HIDE_MIN + froggy.getRandom().nextInt(HIDE_RANDOM);
        hide = DefaultRandomPos.getPosAway(froggy, 24, 7, p.position());
        if (hide == null) {
            Vec3 away = froggy.position().subtract(p.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4D ? new Vec3(1, 0, 0) : away.normalize();
            hide = froggy.position().add(away.scale(20.0D));
        }
        tactics.moveTo(hide, FroggyCombatGoal.huntSpeed() * 1.05D);
        froggy.brainLog("susto de longe em " + p.getScoreboardName() + ": sumiu (" + noises + " barulhos)");
    }

    /** Um barulho sem aparecer: passos, ribbit, passos e, no 4º, a risadinha. */
    private static void makeNoise(FroggydudeEntity froggy, int n) {
        Level level = froggy.level();
        if (n >= 3) {
            froggy.speak(VoiceSituation.FOUND, true);
            return;
        }
        if (n == 1) {
            froggy.playSound(ModSounds.AMBIENT.get(), 1.2F, 0.75F + froggy.getRandom().nextFloat() * 0.2F);
            return;
        }
        // passos: três, rápidos, no chão em que ele está
        BlockPos below = froggy.blockPosition().below();
        SoundType st = level.getBlockState(below).getSoundType(level, below, froggy);
        for (int i = 0; i < 3; i++) {
            level.playSound(null, froggy.getX(), froggy.getY(), froggy.getZ(), st.getStepSound(), SoundSource.HOSTILE,
                    st.getVolume() * 0.9F, st.getPitch() * (0.9F + 0.1F * i));
        }
    }
}
