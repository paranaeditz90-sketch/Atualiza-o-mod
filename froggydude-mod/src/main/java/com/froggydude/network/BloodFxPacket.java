package com.froggydude.network;

import com.froggydude.client.ClientBloodFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor -> cliente: sangue. Um pacote por jato (o cliente cria as dezenas
 * de pedaços sozinho, cada um com a sua velocidade) ou por poça no chão.
 */
public class BloodFxPacket {

    public static final byte SPRAY = 0;
    public static final byte POOL = 1;

    final byte kind;
    final float x, y, z;
    final float dx, dy, dz;
    final int count;
    final float power;

    public BloodFxPacket(byte kind, double x, double y, double z, double dx, double dy, double dz,
                         int count, float power) {
        this.kind = kind;
        this.x = (float) x;
        this.y = (float) y;
        this.z = (float) z;
        this.dx = (float) dx;
        this.dy = (float) dy;
        this.dz = (float) dz;
        this.count = count;
        this.power = power;
    }

    public static void encode(BloodFxPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.kind);
        buf.writeFloat(msg.x);
        buf.writeFloat(msg.y);
        buf.writeFloat(msg.z);
        buf.writeFloat(msg.dx);
        buf.writeFloat(msg.dy);
        buf.writeFloat(msg.dz);
        buf.writeVarInt(msg.count);
        buf.writeFloat(msg.power);
    }

    public static BloodFxPacket decode(FriendlyByteBuf buf) {
        return new BloodFxPacket(buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readFloat());
    }

    public static void handle(BloodFxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (msg.kind == POOL) {
                ClientBloodFx.pool(msg.x, msg.y, msg.z, msg.power);
            } else {
                ClientBloodFx.spray(msg.x, msg.y, msg.z, msg.dx, msg.dy, msg.dz, msg.count, msg.power);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
