package com.froggydude.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * (v0.7.62) Servidor -> cliente: a raiva do céu (entity.SkyRage). RUN: o aviso "RUN FOR YOUR LIFE!!!! NOW!!" (ticks até o
 * mergulho); HIT: ele acertou (a tela fica vermelha antes da tela de morte); END: acabou pra quem só corria.
 */
public class SkyRagePacket {

    public static final byte RUN = 0;
    public static final byte HIT = 1;
    public static final byte END = 2;
    /** Ele chegou na cara: a câmera de quem ele mata vem pra frente (na horizontal), pra cara dele encher a tela. */
    public static final byte FACE = 3;

    final byte kind;
    final int ticks;

    public SkyRagePacket(byte kind, int ticks) {
        this.kind = kind;
        this.ticks = ticks;
    }

    public static void encode(SkyRagePacket m, FriendlyByteBuf buf) {
        buf.writeByte(m.kind);
        buf.writeVarInt(m.ticks);
    }

    public static SkyRagePacket decode(FriendlyByteBuf buf) {
        return new SkyRagePacket(buf.readByte(), buf.readVarInt());
    }

    public static void handle(SkyRagePacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.froggydude.client.SkyRageClient.onPacket(m.kind, m.ticks)));
        ctx.get().setPacketHandled(true);
    }
}
