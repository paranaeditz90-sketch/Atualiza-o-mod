package com.froggydude.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor -> cliente: treme a câmera e, se pinTicks > 0, derruba e prende o
 * jogador (controles travados, câmera virada pro rosto do Froggy pinnerId).
 * pinTicks == RELEASE solta na hora.
 */
public class FroggyShakePacket {

    public static final int RELEASE = -1;

    private final float intensity;
    private final int durationTicks;
    private final int pinTicks;
    private final int pinnerId;

    public FroggyShakePacket(float intensity, int durationTicks, int pinTicks, int pinnerId) {
        this.intensity = intensity;
        this.durationTicks = durationTicks;
        this.pinTicks = pinTicks;
        this.pinnerId = pinnerId;
    }

    public static void encode(FroggyShakePacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.intensity);
        buf.writeVarInt(msg.durationTicks);
        buf.writeInt(msg.pinTicks);
        buf.writeInt(msg.pinnerId);
    }

    public static FroggyShakePacket decode(FriendlyByteBuf buf) {
        return new FroggyShakePacket(buf.readFloat(), buf.readVarInt(), buf.readInt(), buf.readInt());
    }

    public static void handle(FroggyShakePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (msg.pinTicks == RELEASE) {
                ClientShakeHandler.release();
                return;
            }
            if (msg.durationTicks > 0) {
                ClientShakeHandler.start(msg.intensity, msg.durationTicks);
            }
            if (msg.pinTicks > 0) {
                ClientShakeHandler.pin(msg.pinTicks, msg.pinnerId);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
