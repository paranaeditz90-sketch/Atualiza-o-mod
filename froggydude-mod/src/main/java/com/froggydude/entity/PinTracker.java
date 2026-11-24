package com.froggydude.entity;

import com.froggydude.network.ModNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;

/**
 * Jogador derrubado e preso pelo Froggy (lado do servidor).
 *
 * Enquanto preso, o jogador fica deitado no chão (pose de rastejar, câmera
 * rente ao chão) e o cliente trava os controles e vira a câmera pro rosto
 * do Froggy. Solta quando o tempo acaba, quando o Froggy morre ou se afasta.
 */
public final class PinTracker {

    private static final String TICKS = "froggydude.pinTicks";
    private static final String PINNER = "froggydude.pinner";

    private PinTracker() {
    }

    public static void pin(ServerPlayer player, FroggydudeEntity pinner, int ticks) {
        CompoundTag data = player.getPersistentData();
        data.putInt(TICKS, ticks);
        data.putInt(PINNER, pinner.getId());
        player.setForcedPose(Pose.SWIMMING);
    }

    public static boolean isPinned(ServerPlayer player) {
        return player.getPersistentData().getInt(TICKS) > 0;
    }

    public static boolean isPinnedBy(ServerPlayer player, FroggydudeEntity pinner) {
        CompoundTag data = player.getPersistentData();
        return data.getInt(TICKS) > 0 && data.getInt(PINNER) == pinner.getId();
    }

    /** Solta o jogador agora (avisa o cliente também). */
    public static void release(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        if (data.getInt(TICKS) <= 0 && player.getForcedPose() == null) return;
        data.remove(TICKS);
        data.remove(PINNER);
        player.setForcedPose(null);
        ModNetwork.sendRelease(player);
    }

    /** Chamado todo tick do jogador no servidor. */
    public static void tick(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        int ticks = data.getInt(TICKS);
        if (ticks <= 0) {
            return;
        }
        Entity pinner = player.level().getEntity(data.getInt(PINNER));
        boolean pinnerGone = !(pinner instanceof FroggydudeEntity) || !pinner.isAlive()
                || pinner.distanceToSqr(player) > 3.2D * 3.2D;
        if (--ticks <= 0 || pinnerGone || !player.isAlive()) {
            release(player);
            return;
        }
        data.putInt(TICKS, ticks);
        player.setForcedPose(Pose.SWIMMING);
    }
}
