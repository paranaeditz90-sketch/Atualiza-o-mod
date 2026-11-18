package com.froggydude.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Servidor -> cliente: o jogador entityId está (ou não) sem o braço esquerdo. */
public class ArmStatePacket {

    private final int entityId;
    private final boolean armless;

    public ArmStatePacket(int entityId, boolean armless) {
        this.entityId = entityId;
        this.armless = armless;
    }

    public static void encode(ArmStatePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeBoolean(msg.armless);
    }

    public static ArmStatePacket decode(FriendlyByteBuf buf) {
        return new ArmStatePacket(buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(ArmStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientArmState.set(msg.entityId, msg.armless));
        ctx.get().setPacketHandled(true);
    }
}
