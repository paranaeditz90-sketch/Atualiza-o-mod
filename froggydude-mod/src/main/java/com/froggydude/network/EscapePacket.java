package com.froggydude.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** (v0.7.67) Servidor -> cliente: o sistema de fuga. 0 = a adrenalina disparou (ticks de duração). */
public class EscapePacket {

    final byte kind;
    final int ticks;

    public EscapePacket(byte kind, int ticks) {
        this.kind = kind;
        this.ticks = ticks;
    }

    public static void encode(EscapePacket m, FriendlyByteBuf buf) {
        buf.writeByte(m.kind);
        buf.writeVarInt(m.ticks);
    }

    public static EscapePacket decode(FriendlyByteBuf buf) {
        return new EscapePacket(buf.readByte(), buf.readVarInt());
    }

    public static void handle(EscapePacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.froggydude.client.EscapeClient.onPacket(m.kind, m.ticks)));
        ctx.get().setPacketHandled(true);
    }
}
