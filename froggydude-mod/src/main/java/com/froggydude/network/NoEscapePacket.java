package com.froggydude.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * (v0.7.58) Servidor -> cliente: o corte do jumpscare sem saída (entity.ai.NoEscape). O cliente apaga a tela e o som na
 * hora e a tela de morte que vem logo depois sai preta, só com "Você morreu!" e os botões (client.NoEscapeClient).
 */
public class NoEscapePacket {

    public NoEscapePacket() {
    }

    public static void encode(NoEscapePacket m, FriendlyByteBuf buf) {
    }

    public static NoEscapePacket decode(FriendlyByteBuf buf) {
        return new NoEscapePacket();
    }

    public static void handle(NoEscapePacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.froggydude.client.NoEscapeClient.cut()));
        ctx.get().setPacketHandled(true);
    }
}
