package com.froggydude.network;

import com.froggydude.config.GoreLevel;
import com.froggydude.player.GorePrefs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Cliente -> servidor: o nível de gore que esse jogador escolheu. */
public class GoreLevelPacket {

    private final GoreLevel level;

    public GoreLevelPacket(GoreLevel level) {
        this.level = level;
    }

    public static void encode(GoreLevelPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.level.ordinal());
    }

    public static GoreLevelPacket decode(FriendlyByteBuf buf) {
        return new GoreLevelPacket(GoreLevel.byOrdinal(buf.readByte()));
    }

    public static void handle(GoreLevelPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) GorePrefs.set(sender, msg.level);
        });
        ctx.get().setPacketHandled(true);
    }
}
