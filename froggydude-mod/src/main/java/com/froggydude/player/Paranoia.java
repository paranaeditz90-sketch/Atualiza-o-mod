package com.froggydude.player;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * No servidor: o quanto um jogador está paranoico agora, pelo jeito que ele
 * mexe a câmera (o servidor recebe o giro da cabeça a cada tick).
 *
 * - quanto ela gira a câmera por segundo comparado ao jeito NORMAL dela (o normal
 *   é medido quando ele não está por perto);
 * - quantas vezes ela olha pra trás de repente (mais de 120° em meio segundo);
 * - quanto tempo ela passa olhando pra cima (procurando ele no teto).
 *
 * É a "reação" que a IA Real usa pra saber qual susto funciona com cada pessoa
 * (a tocaia, FroggyStakeoutGoal) - o medo dela (PreyStudy#fear).
 */
public final class Paranoia {

    private static final class State {
        boolean init;
        float lastYaw, lastPitch;
        final float[] window = new float[10];
        int wi;
        float secSum;
        int secTicks;
        /** Graus por segundo, média curta (~5 s). */
        float fast;
        /** O jeito normal dela (média lenta, só sem ele por perto). */
        float base = 60F;
        /** Olhadas pra trás recentes (decaem). */
        float behind;
        int behindCooldown;
        /** Segundos recentes olhando pra cima (decaem). */
        float up;
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private Paranoia() {
    }

    /** A cada tick do jogador no servidor. {@code froggyNear}: ele está a menos de 64 blocos. */
    public static void tick(ServerPlayer p, boolean froggyNear) {
        State s = STATES.computeIfAbsent(p.getUUID(), k -> new State());
        float yaw = p.getYHeadRot(), pitch = p.getXRot();
        if (!s.init) {
            s.init = true;
            s.lastYaw = yaw;
            s.lastPitch = pitch;
            return;
        }
        float d = Math.abs(Mth.wrapDegrees(yaw - s.lastYaw)) + Math.abs(pitch - s.lastPitch);
        s.lastYaw = yaw;
        s.lastPitch = pitch;
        // teleporte/renascer/cutscene: não é a pessoa virando
        if (d > 170F) d = 0F;
        s.window[s.wi] = d;
        s.wi = (s.wi + 1) % s.window.length;
        float recent = 0F;
        for (float w : s.window) recent += w;
        if (s.behindCooldown > 0) s.behindCooldown--;
        if (recent > 120F && s.behindCooldown == 0) {
            s.behind += 1F;
            s.behindCooldown = 20;
        }
        s.secSum += d;
        if (++s.secTicks >= 20) {
            float rate = s.secSum;
            s.fast = s.fast * 0.8F + rate * 0.2F;
            if (!froggyNear) s.base = Math.max(25F, s.base * 0.985F + rate * 0.015F);
            s.behind *= 0.93F;
            s.up = s.up * 0.93F + (pitch < -40F ? 1F : 0F);
            s.secSum = 0F;
            s.secTicks = 0;
        }
    }

    /** 0 (tranquila) a 100 (apavorada). */
    public static float score(Player p) {
        State s = STATES.get(p.getUUID());
        if (s == null) return 0F;
        float look = Math.max(0F, s.fast / s.base - 1F) * 35F;
        return Mth.clamp(look + s.behind * 12F + s.up * 6F, 0F, 100F);
    }

    public static void forget(Player p) {
        STATES.remove(p.getUUID());
    }
}
