package com.froggydude.network;

import com.froggydude.world.AntiCheat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * (v0.7.42) O anti-trapaça (world.AntiCheat). Servidor -> cliente: PLAY (vê o vídeo), CONDEMNED (morto pra sempre:
 * trava o renascer), CLEAR (perdoado). Cliente -> servidor: STARTED (começou a passar) e DONE (acabou, ou não tem
 * vídeo no jar).
 */
public class AntiCheatPacket {

    public static final byte PLAY = 0;
    public static final byte CONDEMNED = 1;
    public static final byte CLEAR = 2;
    public static final byte DONE = 3;
    /** Cliente -> servidor: o vídeo começou de verdade (o prazo do servidor conta daqui). */
    public static final byte STARTED = 4;

    final byte action;

    public AntiCheatPacket(byte action) {
        this.action = action;
    }

    public static void encode(AntiCheatPacket m, FriendlyByteBuf buf) {
        buf.writeByte(m.action);
    }

    public static AntiCheatPacket decode(FriendlyByteBuf buf) {
        return new AntiCheatPacket(buf.readByte());
    }

    public static void handle(AntiCheatPacket m, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        if (c.getDirection().getReceptionSide().isServer()) {
            c.enqueueWork(() -> {
                ServerPlayer sender = c.getSender();
                if (sender == null) return;
                if (m.action == DONE) AntiCheat.videoDone(sender);
                else if (m.action == STARTED) AntiCheat.videoStarted(sender);
            });
        } else {
            c.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.froggydude.client.anticheat.AntiCheatClient.onPacket(m.action)));
        }
        c.setPacketHandled(true);
    }
}
