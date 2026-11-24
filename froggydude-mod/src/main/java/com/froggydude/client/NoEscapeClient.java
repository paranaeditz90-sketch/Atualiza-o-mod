package com.froggydude.client;

import com.froggydude.init.ModEntityTypes;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * (v0.7.58) O corte do jumpscare sem saída (entity.ai.NoEscape), no cliente de quem morreu. O vídeo do LM (Grox no
 * beco, 23/11): a cara dele enche a tela por 1-2 quadros e corta seco pro preto. Aqui:
 * <ul>
 * <li>na hora do pacote: tela toda preta e silêncio (para todo som; nenhum som novo toca enquanto durar);</li>
 * <li>a tela de morte que vem em seguida sai preta, só "Você morreu!" e os botões (sem o fundo vermelho, sem a causa e
 * sem a pontuação). É a DeathScreen do jogo por baixo (o Renascer e o Menu funcionam igual), com o desenho trocado; e
 * é escolhida por último (prioridade mais baixa), então mod de tela de morte não passa por cima;</li>
 * <li>renasceu: volta tudo ao normal. Se por algum motivo não morrer em 3 s, o preto some sozinho.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class NoEscapeClient {

    private static final long GIVE_UP_MS = 3000L;
    /** Preto e mudo desde (relógio de verdade); -1 = desligado. */
    private static long since = -1L;
    /** A próxima tela de morte sai preta. */
    private static boolean blackDeath;

    private NoEscapeClient() {
    }

    public static void cut() {
        Minecraft mc = Minecraft.getInstance();
        since = Util.getMillis();
        blackDeath = true;
        mc.getSoundManager().stop();
    }

    private static boolean black() {
        if (since < 0L) return false;
        Minecraft mc = Minecraft.getInstance();
        boolean dead = mc.player != null && mc.player.isDeadOrDying();
        if (!dead && Util.getMillis() - since > GIVE_UP_MS) {
            since = -1L;
            blackDeath = false;
            return false;
        }
        return true;
    }

    /** Depois de tudo desenhado (antes de ir pra tela): o preto por cima do mundo e do HUD, até abrir a tela de morte. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !black()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return; // a tela de morte preta já cuida
        GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        RenderSystem.disableDepthTest();
        g.fill(0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), 0xFF000000);
        g.flush();
        RenderSystem.enableDepthTest();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenOpening(ScreenEvent.Opening event) {
        // a primeira, e qualquer outra enquanto durar o preto (o Renascer do "tem certeza?" reabre a do jogo, vermelha,
        // por um instante antes de renascer)
        if (!blackDeath && since < 0L || !(event.getNewScreen() instanceof DeathScreen)
                || event.getNewScreen() instanceof BlackDeathScreen) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        blackDeath = false;
        event.setNewScreen(new BlackDeathScreen(mc.level != null && mc.level.getLevelData().isHardcore()));
    }

    /**
     * O "Tem certeza que quer sair?" do Menu da tela de morte preta também fica preto (o do jogo mostra o mundo escurecido
     * por trás).
     */
    @SubscribeEvent
    public static void onBackground(ScreenEvent.BackgroundRendered event) {
        if (since >= 0L && event.getScreen() instanceof DeathScreen.TitleConfirmScreen s) {
            event.getGuiGraphics().fill(0, 0, s.width, s.height, 0xFF000000);
        }
    }

    /** Silêncio no corte e na tela de morte preta. */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (event.getSound() == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (black() || mc.screen instanceof BlackDeathScreen
                || since >= 0L && mc.screen instanceof DeathScreen.TitleConfirmScreen) {
            // os cliques dos botões tocam: são da interface, não do mundo
            if (!event.getSound().getLocation().getPath().startsWith("ui.")) event.setSound(null);
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || since < 0L) return;
        Minecraft mc = Minecraft.getInstance();
        // renasceu: acabou
        if (mc.player != null && !mc.player.isDeadOrDying() && !(mc.screen instanceof BlackDeathScreen)
                && !(mc.screen instanceof DeathScreen.TitleConfirmScreen)
                && Util.getMillis() - since > 500L && !blackDeath) {
            since = -1L;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        since = -1L;
        blackDeath = false;
    }

    /** A tela de morte do jogo, desenhada no preto: só o título e os botões. */
    public static final class BlackDeathScreen extends DeathScreen {

        BlackDeathScreen(boolean hardcore) {
            super(null, hardcore);
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            g.fill(0, 0, this.width, this.height, 0xFF000000);
            g.pose().pushPose();
            g.pose().scale(2.0F, 2.0F, 2.0F);
            g.drawCenteredString(this.font, this.title, this.width / 2 / 2, 30, 0xFFFFFF);
            g.pose().popPose();
            for (GuiEventListener c : this.children()) {
                if (c instanceof Renderable r) r.render(g, mouseX, mouseY, partialTick);
            }
        }
    }
}
