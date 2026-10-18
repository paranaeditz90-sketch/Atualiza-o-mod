package com.froggydude.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Servidor -> cliente: começou (ou acabou) a cutscene do golpe final. O
 * cliente conta a tabela (entity.Finisher) a partir de {@code start} (tick do
 * jogo), então a pose, a câmera e o sangue batem com o servidor sem mais
 * nenhum pacote no meio.
 */
public class CutscenePacket {

    public static final byte START = 0;
    public static final byte END = 1;

    final byte kind;
    final int froggyId;
    final int victimId;
    final long start;
    final double x, y, z;
    final float yaw;
    final boolean skipLeft;

    public CutscenePacket(byte kind, int froggyId, int victimId, long start, double x, double y, double z,
                          float yaw, boolean skipLeft) {
        this.kind = kind;
        this.froggyId = froggyId;
        this.victimId = victimId;
        this.start = start;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.skipLeft = skipLeft;
    }

    public static void encode(CutscenePacket m, FriendlyByteBuf buf) {
        buf.writeByte(m.kind);
        buf.writeVarInt(m.froggyId);
        buf.writeVarInt(m.victimId);
        buf.writeLong(m.start);
        buf.writeDouble(m.x);
        buf.writeDouble(m.y);
        buf.writeDouble(m.z);
        buf.writeFloat(m.yaw);
        buf.writeBoolean(m.skipLeft);
    }

    public static CutscenePacket decode(FriendlyByteBuf buf) {
        return new CutscenePacket(buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readLong(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readBoolean());
    }

    public static void handle(CutscenePacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.froggydude.client.cutscene.ClientFinisher.onPacket(m.kind, m.froggyId, m.victimId, m.start,
                        m.x, m.y, m.z, m.yaw, m.skipLeft)));
        ctx.get().setPacketHandled(true);
    }
}
