package com.froggydude.client;

import com.froggydude.init.ModEntityTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * (v0.7.44, pedido do LM) O easter egg do FroggyDude de verdade: quando quem joga é o "FroggyDudeMC", 3 s depois de
 * entrar no mundo aparece só no chat dele "<FroggyDudeMC> Vamos ver quem de nós dois sai vivo dessa... Impostor." (no
 * idioma do jogo). Uma vez na vida: a marca fica no aparelho dele ({@code froggydude/impostor.flag} na pasta do jogo),
 * então nem mundo novo nem servidor mostram de novo.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class EasterEgg {

    private static final String NAME = "FroggyDudeMC";
    private static final int DELAY = 60;
    /** Ticks até mostrar (-1 = nada marcado). */
    private static int wait = -1;

    private EasterEgg() {
    }

    private static File flag() {
        return new File(Minecraft.getInstance().gameDirectory, "froggydude/impostor.flag");
    }

    @SubscribeEvent
    public static void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        if (event.getPlayer() == null || !NAME.equalsIgnoreCase(event.getPlayer().getGameProfile().getName())) return;
        if (flag().exists()) return;
        wait = DELAY;
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        wait = -1;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || wait < 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return; // espera a tela de carregamento sair
        if (--wait > 0) return;
        wait = -1;
        mc.gui.getChat().addMessage(Component.translatable("chat.type.text", "FroggyDudeMC",
                Component.translatable("froggydude.easter.impostor").withStyle(ChatFormatting.DARK_RED)));
        try {
            File f = flag();
            Files.createDirectories(f.getParentFile().toPath());
            Files.writeString(f.toPath(), "1");
        } catch (IOException ignored) {
            // sem poder gravar: aparece de novo na próxima vez (melhor que travar)
        }
    }
}
