package com.froggydude.entity.ai;

import com.froggydude.brain.PreyStudy;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.player.Paranoia;
import com.froggydude.world.FroggyWorldData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Mede se um susto funcionou (IA Real, v0.6): a paranoia dela antes (Paranoia, o
 * jeito que ela mexe a câmera) contra o pico durante o susto e uns segundos depois,
 * e se ela saiu correndo. Funcionou: a tática "susto_<nome>" ganha ponto contra
 * essa pessoa (o mesmo Thompson dos ataques) e o medo dela (o "tempero",
 * PreyStudy#fear) sobe mais.
 */
final class ScareJudge {

    /** Subiu isso de paranoia (0-100): assustou. */
    private static final float WORKED = 12.0F;
    /** Fugindo dele a mais que isso (blocos por tick): assustou. */
    private static final double FLEEING = 0.15D;

    private final FroggydudeEntity froggy;
    @Nullable
    private UUID who;
    private String key = "";
    private float base;
    private float peak;
    private int fled;
    private int until = -1;
    @Nullable
    private Vec3 lastPos;

    ScareJudge(FroggydudeEntity froggy) {
        this.froggy = froggy;
    }

    boolean judging() {
        return who != null;
    }

    /** Começa um susto (antes dele acontecer: a paranoia de agora é a base). */
    void start(ServerPlayer p, String scare) {
        if (who != null && !who.equals(p.getUUID())) cancel();
        who = p.getUUID();
        key = scare;
        base = Paranoia.score(p);
        peak = base;
        fled = 0;
        until = -1;
        lastPos = p.position();
    }

    /** O susto aconteceu (ou acabou): dá o veredito daqui a {@code ticks}. */
    void closeIn(int ticks) {
        if (who != null && until < 0) until = froggy.tickCount + ticks;
    }

    void cancel() {
        who = null;
        until = -1;
    }

    void tick(@Nullable ServerPlayer p) {
        if (who == null) return;
        if (p == null || !p.getUUID().equals(who)) {
            cancel();
            return;
        }
        peak = Math.max(peak, Paranoia.score(p));
        Vec3 now = p.position();
        if (lastPos != null) {
            Vec3 move = now.subtract(lastPos).multiply(1, 0, 1);
            Vec3 away = now.subtract(froggy.position()).multiply(1, 0, 1);
            if (away.lengthSqr() > 1.0E-4D && move.dot(away.normalize()) > FLEEING) fled++;
        }
        lastPos = now;
        if (until >= 0 && froggy.tickCount >= until) verdict(p);
    }

    private void verdict(ServerPlayer p) {
        float delta = peak - base;
        boolean ran = fled >= 20;
        boolean worked = delta >= WORKED || ran;
        froggy.recordTactic(p, "susto_" + key, worked);
        if (froggy.level() instanceof ServerLevel level) {
            FroggyWorldData data = FroggyWorldData.get(level);
            PreyStudy study = data.brain.memory(p.getUUID(), p.getScoreboardName()).study;
            long t = level.getGameTime();
            study.addFear(5.0F + Math.max(0F, delta) * 0.5F + (ran ? 8.0F : 0F), t);
            data.setDirty();
            froggy.brainLog(String.format(java.util.Locale.ROOT, "susto %s em %s: paranoia %.0f -> %.0f%s, %s (medo %.0f)",
                    key, p.getScoreboardName(), base, peak, ran ? ", saiu correndo" : "",
                    worked ? "funcionou" : "nem ligou", study.fear(t)));
        }
        cancel();
    }
}
