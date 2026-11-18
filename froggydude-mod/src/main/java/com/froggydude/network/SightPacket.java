package com.froggydude.network;

import com.froggydude.player.PlayerSight;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Cliente -> servidor, 4 vezes por segundo com um FroggyDude por perto: ele
 * está na TELA desse jogador (dentro do campo de visão, sem bloco no meio, não
 * escondido no escuro)? E o jogador está distraído (com uma tela aberta, comendo,
 * minerando)? É o que a IA Real usa pra saber se foi visto (ver entity/ai/RealTactics).
 */
public class SightPacket {

    public static final int SCREEN = 1;
    public static final int USING = 2;
    public static final int MINING = 4;

    private final int froggyId;
    private final boolean seen;
    private final byte flags;

    public SightPacket(int froggyId, boolean seen, int flags) {
        this.froggyId = froggyId;
        this.seen = seen;
        this.flags = (byte) flags;
    }

    public static void encode(SightPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.froggyId);
        buf.writeBoolean(msg.seen);
        buf.writeByte(msg.flags);
    }

    public static SightPacket decode(FriendlyByteBuf buf) {
        return new SightPacket(buf.readVarInt(), buf.readBoolean(), buf.readByte());
    }

    public static void handle(SightPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) PlayerSight.report(sender, msg.froggyId, msg.seen, msg.flags);
        });
        ctx.get().setPacketHandled(true);
    }
}
