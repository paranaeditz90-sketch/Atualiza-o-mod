package com.froggydude.client.screen;

import com.froggydude.config.FroggyClientConfig;
import com.froggydude.config.GoreLevel;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.network.GoreLevelPacket;
import com.froggydude.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

/** O que as três telas do gore têm em comum. */
public final class GoreScreens {

    static final ResourceLocation[] SPLAT = new ResourceLocation[6];

    static {
        for (int i = 0; i < SPLAT.length; i++) {
            SPLAT[i] = new ResourceLocation(ModEntityTypes.MOD_ID, "textures/gui/blood_splat_" + i + ".png");
        }
    }

    private GoreScreens() {
    }

    /** Salva a escolha e conta pro servidor (se estiver num mundo). */
    public static void apply(GoreLevel level) {
        FroggyClientConfig.choose(level);
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener conn = mc.getConnection();
        if (conn != null && ModNetwork.CHANNEL.isRemotePresent(conn.getConnection())) {
            ModNetwork.CHANNEL.sendToServer(new GoreLevelPacket(level));
        }
    }

    public static Component name(GoreLevel level) {
        return Component.translatable(level.key());
    }

    public static Component describe(GoreLevel level) {
        return Component.translatable(level.key() + ".desc");
    }

    static void heartbeat(float volume) {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.8F, volume));
    }

    /** Cor do botão/nome de cada nível (do cinza ao vermelho vivo). */
    public static int color(GoreLevel level) {
        return switch (level) {
            case OFF -> 0xB0B0B0;
            case LIGHT -> 0xE0C070;
            case MEDIUM -> 0xE89040;
            case HEAVY -> 0xE04030;
            case EXTREME -> 0xFF1010;
        };
    }
}
