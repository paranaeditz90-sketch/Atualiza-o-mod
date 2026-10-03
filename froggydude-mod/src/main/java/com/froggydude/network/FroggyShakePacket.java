package com.froggydude.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor -> cliente: treme a câmera e, se pinTicks > 0, prende o jogador
 * (trava os controles e inclina a tela) por esse tempo.
 * Registrado em ModNetwork; enviado por ModNetwork.sendEffects(...).
 */
public class FroggyShakePacket {

    private final float intensity;
    private final int durationTicks;
    private final int pinTicks;

    public FroggyShakePacket(float intensity, int durationTicks, int pinTicks) {
        this.intensity = intensity;
        this.durationTicks = durationTicks;
        this.pinTicks = pinTicks;
    }

    public static void encode(FroggyShakePacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.intensity);
        buf.writeInt(msg.durationTicks);
        buf.writeInt(msg.pinTicks);
    }

    public static FroggyShakePacket decode(FriendlyByteBuf buf) {
        return new FroggyShakePacket(buf.readFloat(), buf.readInt(), buf.readInt());
    }

    public static void handle(FroggyShakePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientShakeHandler.start(msg.intensity, msg.durationTicks);
            if (msg.pinTicks > 0) {
                ClientShakeHandler.pin(msg.pinTicks);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
