package com.froggydude.client.gore;

import com.froggydude.client.screen.GoreWarningScreen;
import com.froggydude.config.FroggyClientConfig;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.network.GoreLevelPacket;
import com.froggydude.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** O aviso da primeira vez e contar o nível de gore pro servidor ao entrar. */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class GoreClientEvents {

    private GoreClientEvents() {
    }

    /** Primeira vez que o menu abre com o mod: antes dele, o aviso (e a escolha do nível). */
    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof TitleScreen title && !FroggyClientConfig.warningSeen()) {
            event.setNewScreen(new GoreWarningScreen(title, true));
        }
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null && ModNetwork.CHANNEL.isRemotePresent(conn.getConnection())) {
            ModNetwork.CHANNEL.sendToServer(new GoreLevelPacket(FroggyClientConfig.gore()));
        }
    }
}
