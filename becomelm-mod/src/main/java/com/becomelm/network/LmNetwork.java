package com.becomelm.network;

import com.becomelm.BecomeLM;
import com.becomelm.LmAttack;
import com.becomelm.LmAttacks;
import com.becomelm.LmKnock;
import com.becomelm.LmMode;
import com.becomelm.LmModes;
import com.becomelm.LmMove;
import com.becomelm.LmTransform;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
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

    private static final String VERSION = "6";
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
        CHANNEL.registerMessage(id++, KnockSync.class, KnockSync::encode, KnockSync::decode, KnockSync::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, Attack.class, Attack::encode, Attack::decode, Attack::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, HitFx.class, HitFx::encode, HitFx::decode, HitFx::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, TransformSync.class, TransformSync::encode, TransformSync::decode,
                TransformSync::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, TransformGo.class, TransformGo::encode, TransformGo::decode, TransformGo::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(id++, ActSync.class, ActSync::encode, ActSync::decode, ActSync::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(id++, BreakBody.class, BreakBody::encode, BreakBody::decode, BreakBody::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** O FroggyDude matou: quem vê despedaça o corpo (pra onde o golpe empurra, a força e o sangue). */
    public static void sendBreak(LivingEntity victim, float dx, float dy, float dz, float power, int color, boolean bleeds) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> victim),
                new BreakBody(victim.getId(), dx, dy, dz, power, color, bleeds));
    }

    public static void sendMode(ServerPlayer player, LmMode mode) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new ModeSync(player.getUUID(), mode));
    }

    public static void sendModeTo(ServerPlayer viewer, ServerPlayer target, LmMode mode) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new ModeSync(target.getUUID(), mode));
    }

    public static void sendKnock(LivingEntity e, LmKnock.State s) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> e), KnockSync.of(e, s));
    }

    public static void sendKnockTo(ServerPlayer viewer, LivingEntity e, LmKnock.State s) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), KnockSync.of(e, s));
    }

    /** O golpe pegou (ou bateu sem dano): quem vê a vítima ou o atacante faz o barulho e a poeira. */
    public static void sendHit(ServerPlayer attacker, LivingEntity target, LmAttack kind, boolean hurt, int n) {
        HitFx fx = new HitFx(attacker.getId(), target.getId(), kind, hurt, n);
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target), fx);
        if (target != attacker) CHANNEL.send(PacketDistributor.PLAYER.with(() -> attacker), fx);
    }

    /** O golpe longo que o jogador está dando (montado socando, segurando na boca, no monte...); kind null = acabou. */
    public static void sendAct(ServerPlayer p, LmAttack kind, int target, int age) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p),
                new ActSync(p.getUUID(), kind == null ? -1 : kind.ordinal(), target, age));
    }

    public static void sendActTo(ServerPlayer viewer, ServerPlayer p, LmAttack kind, int target, int age) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), new ActSync(p.getUUID(), kind.ordinal(), target, age));
    }

    /** Em que pedaço da transformação o jogador está (null = acabou ou parou). */
    public static void sendTransform(ServerPlayer p, LmTransform.State st) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> p), TransformSync.of(p, st));
    }

    public static void sendTransformTo(ServerPlayer viewer, ServerPlayer p, LmTransform.State st) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> viewer), TransformSync.of(p, st));
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
                    if (m.move == LmMove.SKY) LmAttacks.skyJumped(sender);
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

    /** Servidor -> quem vê: esse bicho (ou jogador) foi derrubado / continua no chão. */
    public record KnockSync(int entity, int age, int down, int side, float yaw, boolean back) {
        static KnockSync of(LivingEntity e, LmKnock.State s) {
            return new KnockSync(e.getId(), s.age, s.down, s.side, s.yaw, s.back);
        }

        static void encode(KnockSync m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entity);
            buf.writeVarInt(m.age);
            buf.writeVarInt(m.down);
            buf.writeByte(m.side);
            buf.writeFloat(m.yaw);
            buf.writeBoolean(m.back);
        }

        static KnockSync decode(FriendlyByteBuf buf) {
            return new KnockSync(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readByte(), buf.readFloat(),
                    buf.readBoolean());
        }

        static void handle(KnockSync m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.becomelm.client.LmKnockClient.set(m.entity, m.age, m.down, m.side, m.yaw, m.back)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Cliente -> servidor: quero dar esse golpe nesse bicho. */
    public record Attack(LmAttack kind, int target) {
        static void encode(Attack m, FriendlyByteBuf buf) {
            buf.writeByte(m.kind.ordinal());
            buf.writeVarInt(m.target);
        }

        static Attack decode(FriendlyByteBuf buf) {
            return new Attack(LmAttack.byOrdinal(buf.readByte()), buf.readVarInt());
        }

        static void handle(Attack m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sender != null) LmAttacks.request(sender, m.kind, m.target);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Servidor -> quem vê: o golpe encostou. */
    public record HitFx(int attacker, int target, LmAttack kind, boolean hurt, int n) {
        static void encode(HitFx m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.attacker);
            buf.writeVarInt(m.target);
            buf.writeByte(m.kind.ordinal());
            buf.writeBoolean(m.hurt);
            buf.writeByte(m.n);
        }

        static HitFx decode(FriendlyByteBuf buf) {
            return new HitFx(buf.readVarInt(), buf.readVarInt(), LmAttack.byOrdinal(buf.readByte()), buf.readBoolean(),
                    buf.readByte());
        }

        static void handle(HitFx m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.becomelm.client.LmAttackClient.hit(m.attacker, m.target, m.kind, m.hurt, m.n)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Servidor -> quem vê: a transformação desse jogador (fase -1 = acabou). */
    public record TransformSync(UUID player, int phase, int age) {
        static TransformSync of(ServerPlayer p, LmTransform.State st) {
            return st == null ? new TransformSync(p.getUUID(), -1, 0) : new TransformSync(p.getUUID(), st.phase.ordinal(), st.age);
        }

        static void encode(TransformSync m, FriendlyByteBuf buf) {
            buf.writeUUID(m.player);
            buf.writeByte(m.phase);
            buf.writeVarInt(m.age);
        }

        static TransformSync decode(FriendlyByteBuf buf) {
            return new TransformSync(buf.readUUID(), buf.readByte(), buf.readVarInt());
        }

        static void handle(TransformSync m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.becomelm.client.LmTransformClient.set(m.player, m.phase, m.age)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Cliente -> servidor: estava torto e andei (arranca). */
    public record TransformGo() {
        static void encode(TransformGo m, FriendlyByteBuf buf) {
        }

        static TransformGo decode(FriendlyByteBuf buf) {
            return new TransformGo();
        }

        static void handle(TransformGo m, Supplier<NetworkEvent.Context> ctx) {
            ServerPlayer sender = ctx.get().getSender();
            ctx.get().enqueueWork(() -> {
                if (sender != null) LmTransform.go(sender);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    /** Servidor -> quem vê: o golpe longo desse jogador (kind -1 = acabou). */
    public record ActSync(UUID player, int kind, int target, int age) {
        static void encode(ActSync m, FriendlyByteBuf buf) {
            buf.writeUUID(m.player);
            buf.writeByte(m.kind);
            buf.writeVarInt(m.target);
            buf.writeVarInt(m.age);
        }

        static ActSync decode(FriendlyByteBuf buf) {
            return new ActSync(buf.readUUID(), buf.readByte(), buf.readVarInt(), buf.readVarInt());
        }

        static void handle(ActSync m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.becomelm.client.LmActsClient.set(m.player, m.kind, m.target, m.age)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Servidor -> quem vê: esse corpo vira pedaços. */
    public record BreakBody(int entity, float dx, float dy, float dz, float power, int color, boolean bleeds) {
        static void encode(BreakBody m, FriendlyByteBuf buf) {
            buf.writeVarInt(m.entity);
            buf.writeFloat(m.dx);
            buf.writeFloat(m.dy);
            buf.writeFloat(m.dz);
            buf.writeFloat(m.power);
            buf.writeInt(m.color);
            buf.writeBoolean(m.bleeds);
        }

        static BreakBody decode(FriendlyByteBuf buf) {
            return new BreakBody(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readInt(), buf.readBoolean());
        }

        static void handle(BreakBody m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.becomelm.client.pieces.BodyPieces.breakEntity(m.entity, m.dx, m.dy, m.dz, m.power,
                            m.color, m.bleeds)));
            ctx.get().setPacketHandled(true);
        }
    }
}
