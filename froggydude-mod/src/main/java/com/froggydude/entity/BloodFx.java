package com.froggydude.entity;

import com.froggydude.network.BloodFxPacket;
import com.froggydude.network.ModNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

/**
 * Sangue do lado do servidor: manda pros jogadores por perto um jato (pedaços
 * que voam e caem, como no "Eating a Zebra") ou uma poça no chão (que vai
 * crescendo se cair mais sangue no mesmo lugar).
 */
public final class BloodFx {

    private static final double RANGE = 64.0D;

    private BloodFx() {
    }

    /**
     * Jato de sangue saindo de "at" na direção "dir" (não precisa ser unitária).
     * power ~0,15 = escorrendo, ~0,3 = mordida, ~0,5 = braço arrancado.
     */
    public static void spray(Level level, Vec3 at, Vec3 dir, int count, float power) {
        if (!(level instanceof ServerLevel server) || count <= 0) return;
        Vec3 d = dir.lengthSqr() < 1.0E-6D ? new Vec3(0, 1, 0) : dir.normalize();
        send(server, at, new BloodFxPacket(BloodFxPacket.SPRAY, at.x, at.y, at.z, d.x, d.y, d.z, count, power));
    }

    /** Poça no chão embaixo de "at" (size = diâmetro em blocos). */
    public static void pool(Level level, Vec3 at, float size) {
        if (!(level instanceof ServerLevel server)) return;
        send(server, at, new BloodFxPacket(BloodFxPacket.POOL, at.x, at.y, at.z, 0, 0, 0, 0, size));
    }

    private static void send(ServerLevel server, Vec3 at, BloodFxPacket packet) {
        ModNetwork.CHANNEL.send(PacketDistributor.NEAR.with(() ->
                new PacketDistributor.TargetPoint(at.x, at.y, at.z, RANGE, server.dimension())), packet);
    }
}
