package com.froggydude.network;

import com.froggydude.client.ClientBloodFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor -> cliente: sangue. Um pacote por efeito; o cliente cria os
 * pedaços, a poça, a mancha na skin e o respingo na tela sozinho, do tamanho
 * que o nível de gore DELE pedir (o mesmo golpe pode ser nada pra um e um
 * banho de sangue pra outro).
 */
public class BloodFxPacket {

    /** Pedaços voando de x,y,z na direção d. */
    public static final byte SPRAY = 0;
    /** Poça no chão embaixo de x,y,z (power = diâmetro). */
    public static final byte POOL = 1;
    /** Golpe: entityA levou power de dano de entityB (ou -1), no ponto x,y,z, na direção d. */
    public static final byte HIT = 2;
    /** Jato contínuo saindo de entityA (x,y,z = ponto no corpo dele: direita, cima, frente), por ticks. */
    public static final byte STREAM = 3;
    /** Explosão de sangue (membro arrancado, morte): pedaços, poça grande e respingo em quem está perto. */
    public static final byte BURST = 4;

    final byte kind;
    final float x, y, z;
    final float dx, dy, dz;
    final int count;
    final float power;
    final int color;
    final int entityA;
    final int entityB;
    final int ticks;

    public BloodFxPacket(byte kind, double x, double y, double z, double dx, double dy, double dz,
                         int count, float power, int color, int entityA, int entityB, int ticks) {
        this.kind = kind;
        this.x = (float) x;
        this.y = (float) y;
        this.z = (float) z;
        this.dx = (float) dx;
        this.dy = (float) dy;
        this.dz = (float) dz;
        this.count = count;
        this.power = power;
        this.color = color;
        this.entityA = entityA;
        this.entityB = entityB;
        this.ticks = ticks;
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
        buf.writeInt(msg.color);
        buf.writeVarInt(msg.entityA + 1);
        buf.writeVarInt(msg.entityB + 1);
        buf.writeVarInt(msg.ticks);
    }

    public static BloodFxPacket decode(FriendlyByteBuf buf) {
        return new BloodFxPacket(buf.readByte(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readFloat(), buf.readInt(),
                buf.readVarInt() - 1, buf.readVarInt() - 1, buf.readVarInt());
    }

    public static void handle(BloodFxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            switch (msg.kind) {
                case POOL -> ClientBloodFx.pool(msg.x, msg.y, msg.z, msg.power, msg.color);
                case HIT -> ClientBloodFx.hit(msg.entityA, msg.entityB, msg.x, msg.y, msg.z,
                        msg.dx, msg.dy, msg.dz, msg.power, msg.color, msg.count);
                case STREAM -> ClientBloodFx.stream(msg.entityA, msg.x, msg.y, msg.z, msg.dx, msg.dy, msg.dz,
                        msg.power, msg.ticks, msg.color);
                case BURST -> ClientBloodFx.burst(msg.x, msg.y, msg.z, msg.dx, msg.dy, msg.dz, msg.count,
                        msg.power, msg.color, msg.entityA);
                default -> ClientBloodFx.spray(msg.x, msg.y, msg.z, msg.dx, msg.dy, msg.dz, msg.count,
                        msg.power, msg.color);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
