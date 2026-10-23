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
    /** A cutscene do jumpscare: sem HUD e faixas pretas por esse tempo (0 = nada). */
    private final int cinematicTicks;
    /** O jumpscare vira a câmera à força pra essa entidade (-1 = não vira), em turnTicks. */
    private final int turnTo;
    private final int turnTicks;

    public FroggyShakePacket(float intensity, int durationTicks, int pinTicks, int pinnerId) {
        this(intensity, durationTicks, pinTicks, pinnerId, 0);
    }

    public FroggyShakePacket(float intensity, int durationTicks, int pinTicks, int pinnerId, int cinematicTicks) {
        this(intensity, durationTicks, pinTicks, pinnerId, cinematicTicks, -1, 0);
    }

    public FroggyShakePacket(float intensity, int durationTicks, int pinTicks, int pinnerId, int cinematicTicks,
                             int turnTo, int turnTicks) {
        this.intensity = intensity;
        this.durationTicks = durationTicks;
        this.pinTicks = pinTicks;
        this.pinnerId = pinnerId;
        this.cinematicTicks = cinematicTicks;
        this.turnTo = turnTo;
        this.turnTicks = turnTicks;
    }

    public static void encode(FroggyShakePacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.intensity);
        buf.writeVarInt(msg.durationTicks);
        buf.writeInt(msg.pinTicks);
        buf.writeInt(msg.pinnerId);
        buf.writeVarInt(msg.cinematicTicks);
        buf.writeInt(msg.turnTo);
        buf.writeVarInt(msg.turnTicks);
    }

    public static FroggyShakePacket decode(FriendlyByteBuf buf) {
        return new FroggyShakePacket(buf.readFloat(), buf.readVarInt(), buf.readInt(), buf.readInt(), buf.readVarInt(),
                buf.readInt(), buf.readVarInt());
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
            if (msg.cinematicTicks > 0) {
                ClientShakeHandler.cinematic(msg.cinematicTicks);
            }
            if (msg.turnTo >= 0 && msg.turnTicks > 0) {
                ClientShakeHandler.turn(msg.turnTo, msg.turnTicks);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
