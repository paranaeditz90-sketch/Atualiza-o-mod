package com.froggydude.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * (v0.7.33) Servidor -> cliente: a língua soltou {@code entityId} no arremesso. O cliente desenha ela mole, rodando
 * no ar (o "ragdoll" da réplica), até cair ou acabar {@code ticks}; (dx, dz) é pra onde ela voa.
 */
public class FlingPacket {

    private final int entityId;
    private final int ticks;
    private final float dx, dz;

    public FlingPacket(int entityId, int ticks, float dx, float dz) {
        this.entityId = entityId;
        this.ticks = ticks;
        this.dx = dx;
        this.dz = dz;
    }

    public static void encode(FlingPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeVarInt(msg.ticks);
        buf.writeFloat(msg.dx);
        buf.writeFloat(msg.dz);
    }

    public static FlingPacket decode(FriendlyByteBuf buf) {
        return new FlingPacket(buf.readVarInt(), buf.readVarInt(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(FlingPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientFling.start(msg.entityId, msg.ticks, msg.dx, msg.dz));
        ctx.get().setPacketHandled(true);
    }
}
