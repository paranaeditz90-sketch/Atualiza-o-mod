package com.froggydude.network;

import com.froggydude.init.ModEntityTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal de rede do mod. Chame ModNetwork.register() no setup comum do mod
 * (FMLCommonSetupEvent).
 */
public final class ModNetwork {

    private static final String PROTOCOL = "2";

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
        send(player, new FroggyShakePacket(intensity, durationTicks, 0, -1));
    }

    /**
     * Treme a câmera e prende o jogador por pinTicks (0 = não prende).
     * pinnerId é o Froggy que está em cima: a câmera vira pro rosto dele.
     */
    public static void sendEffects(ServerPlayer player, float intensity, int durationTicks,
                                   int pinTicks, int pinnerId) {
        send(player, new FroggyShakePacket(intensity, durationTicks, pinTicks, pinnerId));
    }

    /** Solta o jogador antes do tempo (Froggy derrubado, morto ou longe). */
    public static void sendRelease(ServerPlayer player) {
        send(player, new FroggyShakePacket(0F, 0, FroggyShakePacket.RELEASE, -1));
    }

    private static void send(ServerPlayer player, FroggyShakePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
