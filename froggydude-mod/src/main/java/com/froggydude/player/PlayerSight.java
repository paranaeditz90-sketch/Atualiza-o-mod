package com.froggydude.player;

import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.network.SightPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * No servidor: o que cada jogador está vendo, pelo que o cliente dele conta
 * (SightPacket). Sem relato recente (cliente parado, servidor sem o mod no
 * cliente), o servidor estima: olhando pro lado dele, com linha de visão e sem
 * ele estar no breu.
 */
public final class PlayerSight {

    /** Relato com mais de 1 s não vale mais. */
    private static final int STALE = 20;
    /** Luz (0-15) até onde ele some no escuro pra quem está a mais de 6 blocos. */
    public static final int DARK = 3;

    private static final class State {
        int froggyId = -1;
        boolean seen;
        int flags;
        long reportAt = Long.MIN_VALUE / 2;
        /** A última vez que ele apareceu na tela (desde que o cliente começou a contar). */
        long lastSeenAt = Long.MIN_VALUE / 2;
    }

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private PlayerSight() {
    }

    public static void report(ServerPlayer player, int froggyId, boolean seen, int flags) {
        long now = player.serverLevel().getGameTime();
        State s = STATES.computeIfAbsent(player.getUUID(), k -> new State());
        // primeiro relato (ou o cliente ficou quieto um tempo): conta "sem ver" a partir de agora
        if (now - s.reportAt > STALE * 10) s.lastSeenAt = now;
        s.froggyId = froggyId;
        s.seen = seen;
        s.flags = flags;
        s.reportAt = now;
        if (seen) s.lastSeenAt = now;
    }

    /** O jogador está vendo esse FroggyDude agora? */
    public static boolean sees(ServerPlayer player, FroggydudeEntity froggy) {
        State s = fresh(player);
        if (s != null && s.froggyId == froggy.getId()) return s.seen;
        return estimate(player, froggy);
    }

    /** Há quantos ticks ele não aparece na tela dela; -1 se o cliente não está contando. */
    public static int unseenFor(ServerPlayer player, FroggydudeEntity froggy) {
        State s = fresh(player);
        if (s == null || s.froggyId != froggy.getId()) return -1;
        return (int) Math.min(Integer.MAX_VALUE, player.serverLevel().getGameTime() - s.lastSeenAt);
    }

    /** De tela aberta (inventário, bau, fornalha, chat), comendo/bebendo ou minerando. */
    public static boolean distracted(ServerPlayer player) {
        State s = fresh(player);
        if (s != null) return (s.flags & (SightPacket.SCREEN | SightPacket.USING | SightPacket.MINING)) != 0;
        return player.isUsingItem();
    }

    /** O cliente desse jogador está contando o que vê (o resto é estimativa). */
    public static boolean reporting(ServerPlayer player) {
        return fresh(player) != null;
    }

    public static void forget(Player player) {
        STATES.remove(player.getUUID());
    }

    /** Ele está no breu (luz de bloco e do céu baixas onde está a cabeça dele). */
    public static boolean dark(FroggydudeEntity froggy) {
        return froggy.level().getMaxLocalRawBrightness(BlockPos.containing(froggy.getEyePosition())) <= DARK;
    }

    private static State fresh(ServerPlayer player) {
        State s = STATES.get(player.getUUID());
        if (s == null || player.serverLevel().getGameTime() - s.reportAt > STALE) return null;
        return s;
    }

    /** Sem o cliente: olhando na direção dele (~100° de campo), com linha de visão e sem ele no breu. */
    private static boolean estimate(ServerPlayer player, FroggydudeEntity froggy) {
        Vec3 eye = player.getEyePosition();
        Vec3 to = froggy.getBoundingBox().getCenter().subtract(eye);
        double d = to.length();
        if (d < 1.0E-3D) return true;
        if (player.getViewVector(1.0F).dot(to.scale(1.0D / d)) < 0.64D) return false;
        if (!player.hasLineOfSight(froggy)) return false;
        return d < 6.0D || !dark(froggy);
    }
}
