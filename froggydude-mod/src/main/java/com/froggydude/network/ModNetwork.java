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

    private static final String PROTOCOL = "3";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ModEntityTypes.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private ModNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, FroggyShakePacket.class,
                FroggyShakePacket::encode, FroggyShakePacket::decode, FroggyShakePacket::handle);
        CHANNEL.registerMessage(1, ArmStatePacket.class,
                ArmStatePacket::encode, ArmStatePacket::decode, ArmStatePacket::handle);
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

    /** Avisa o próprio jogador e quem está vendo ele que o braço esquerdo foi arrancado (ou voltou). */
    public static void sendArmState(ServerPlayer player, boolean armless) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player),
                new ArmStatePacket(player.getId(), armless));
    }

    /** Conta pra um jogador como está o braço de outro (quando ele aparece na tela). */
    public static void sendArmStateTo(ServerPlayer receiver, int playerId, boolean armless) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> receiver), new ArmStatePacket(playerId, armless));
    }

    private static void send(ServerPlayer player, FroggyShakePacket packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
