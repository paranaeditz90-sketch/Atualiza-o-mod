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

    private static final String PROTOCOL = "14";

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
        CHANNEL.registerMessage(2, BloodFxPacket.class,
                BloodFxPacket::encode, BloodFxPacket::decode, BloodFxPacket::handle);
        CHANNEL.registerMessage(3, GoreLevelPacket.class,
                GoreLevelPacket::encode, GoreLevelPacket::decode, GoreLevelPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(4, CutscenePacket.class,
                CutscenePacket::encode, CutscenePacket::decode, CutscenePacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(5, SightPacket.class,
                SightPacket::encode, SightPacket::decode, SightPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER));
        CHANNEL.registerMessage(6, FlingPacket.class,
                FlingPacket::encode, FlingPacket::decode, FlingPacket::handle,
                java.util.Optional.of(net.minecraftforge.network.NetworkDirection.PLAY_TO_CLIENT));
        // (v0.7.42) o anti-trapaça: vai e volta (o vídeo começa no cliente; o "acabou" volta pro servidor)
        CHANNEL.registerMessage(7, AntiCheatPacket.class,
                AntiCheatPacket::encode, AntiCheatPacket::decode, AntiCheatPacket::handle);
        CHANNEL.registerMessage(8, NoEscapePacket.class,
                NoEscapePacket::encode, NoEscapePacket::decode, NoEscapePacket::handle);
        CHANNEL.registerMessage(9, SkyRagePacket.class,
                SkyRagePacket::encode, SkyRagePacket::decode, SkyRagePacket::handle);
        CHANNEL.registerMessage(10, EscapePacket.class,
                EscapePacket::encode, EscapePacket::decode, EscapePacket::handle);
    }

    /** (v0.7.62) A raiva do céu: o aviso de correr, a tela vermelha do acerto, o fim. */
    /** (v0.7.67) O sistema de fuga (EscapePacket). */
    public static void sendEscape(ServerPlayer player, byte kind, int ticks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new EscapePacket(kind, ticks));
    }

    public static void sendSkyRage(ServerPlayer player, byte kind, int ticks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SkyRagePacket(kind, ticks));
    }

    /** (v0.7.58) O corte do jumpscare sem saída: tela preta e silêncio na hora, tela de morte preta. */
    public static void sendNoEscapeCut(ServerPlayer player) {
        CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), new NoEscapePacket());
    }

    /** (v0.7.33) A língua soltou essa entidade no arremesso: quem vê (e ela mesma) desenha o voo mole. */
    public static void sendFling(net.minecraft.world.entity.Entity flung, int ticks, float dx, float dz) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> flung), new FlingPacket(flung.getId(), ticks, dx, dz));
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

    /**
     * O jumpscare: tremor forte, a cutscene (sem HUD, faixas pretas) por {@code ticks} e a
     * câmera virada à força pra entidade {@code turnTo} em {@code turnTicks}.
     */
    public static void sendJumpscare(ServerPlayer player, int ticks, int turnTo, int turnTicks) {
        send(player, new FroggyShakePacket(2.6F, 14, 0, -1, ticks, turnTo, turnTicks));
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
