package com.froggydude.network;

import com.froggydude.init.ModEntityTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal de rede do mod. Se seu mod já tem um SimpleChannel, use o seu e
 * apague este: só copie a linha do registerMessage pra lá.
 * Chame ModNetwork.register() no setup comum do mod (FMLCommonSetupEvent).
 */
public final class ModNetwork {

    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ModEntityTypes.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, FroggyShakePacket.class,
                FroggyShakePacket::encode, FroggyShakePacket::decode, FroggyShakePacket::handle);
    }

    /** Só treme a câmera. */
    public static void sendShake(ServerPlayer player, float intensity, int durationTicks) {
        sendEffects(player, intensity, durationTicks, 0);
    }

    /** Treme a câmera e prende o jogador por pinTicks (0 = não prende). */
    public static void sendEffects(ServerPlayer player, float intensity, int durationTicks, int pinTicks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new FroggyShakePacket(intensity, durationTicks, pinTicks));
    }
}
