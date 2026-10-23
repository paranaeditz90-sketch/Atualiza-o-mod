package com.becomelm.network;

import com.becomelm.BecomeLM;
import com.becomelm.LmMode;
import com.becomelm.LmModes;
import com.becomelm.LmMove;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Pacotes do BecomeLM. O mod também entra em servidor sem ele (aí só quem tem o mod
 * vê o próprio FroggyDude; os amigos veem o jogador normal).
 */
public final class LmNetwork {

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(BecomeLM.id("main"),
            () -> VERSION, NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    private LmNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, SetMode.class, SetMode::encode, SetMode::decode, SetMode::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ModeSync.class, ModeSync::encode, ModeSync::decode, ModeSync::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, DoMove.class, DoMove::encode, DoMove::decode, DoMove::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, MoveSync.class, MoveSync::encode, MoveSync::decode, MoveSync::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendMode(ServerPlayer player, LmMode mode) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new ModeSync(player.getUUID(), mode));
    }

    public static void sendModeTo(ServerPlayer viewer, ServerPlayer target, LmMode mode) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new ModeSync(target.getUUID(), mode));
    }

    // ------------------------------------------------------------------ pacotes

    /** Cliente -> servidor: escolhi esse modo na roda. */
    public record SetMode(LmMode mode) {
        static void encode(SetMode m, FriendlyByteBuf buf) {
            buf.writeByte(m.mode.ordinal());
        }

        static SetMode decode(FriendlyByteBuf buf) {
            return new SetMode(LmMode.byOrdinal(buf.readByte()));
        }

        static void handle(SetMode m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sender != null) LmModes.set(sender, m.mode);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Servidor -> clientes: o modo de um jogador. */
    public record ModeSync(UUID player, LmMode mode) {
        static void encode(ModeSync m, FriendlyByteBuf buf) {
            buf.writeUUID(m.player);
            buf.writeByte(m.mode.ordinal());
        }

        static ModeSync decode(FriendlyByteBuf buf) {
            return new ModeSync(buf.readUUID(), LmMode.byOrdinal(buf.readByte()));
        }

        static void handle(ModeSync m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> LmModes.setClient(m.player, m.mode));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Cliente -> servidor: dei um pulo especial (pros outros verem a animação certa). */
    public record DoMove(LmMove move) {
        static void encode(DoMove m, FriendlyByteBuf buf) {
            buf.writeByte(m.move.ordinal());
        }

        static DoMove decode(FriendlyByteBuf buf) {
            return new DoMove(LmMove.byOrdinal(buf.readByte()));
        }

        static void handle(DoMove m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sender != null && LmModes.get(sender).froggy()) {
                    CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> sender), new MoveSync(sender.getUUID(), m.move));
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Servidor -> quem vê: aquele jogador deu esse pulo. */
    public record MoveSync(UUID player, LmMove move) {
        static void encode(MoveSync m, FriendlyByteBuf buf) {
            buf.writeUUID(m.player);
            buf.writeByte(m.move.ordinal());
        }

        static MoveSync decode(FriendlyByteBuf buf) {
            return new MoveSync(buf.readUUID(), LmMove.byOrdinal(buf.readByte()));
        }

        static void handle(MoveSync m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.becomelm.client.LmMoves.remote(m.player, m.move)));
            ctx.get().setPacketHandled(true);
        }
    }
}
