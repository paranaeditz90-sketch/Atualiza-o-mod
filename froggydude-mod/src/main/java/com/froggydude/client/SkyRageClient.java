package com.froggydude.client;

import com.froggydude.init.ModEntityTypes;
import com.froggydude.network.SkyRagePacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * (v0.7.62) A raiva do céu no cliente (entity.SkyRage):
 * <ul>
 * <li>o aviso tipo conquista "RUN FOR YOUR LIFE!!!! NOW!!", vermelho, com sangue escorrendo pela caixa (LM: "aviso tipo
 * conquista vermelho com sangue escorrendo"), até o mergulho;</li>
 * <li>o acerto: a tela vermelha (o vídeo: ~1,5 s de vermelho depois que a cara enche a tela) e só depois a tela de
 * morte.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SkyRageClient {

    /** O vermelho do acerto (ms). */
    static final long RED_MS = 1500L;
    private static long hitAt = -1L;
    private static RunToast toast;

    private SkyRageClient() {
    }

    public static void onPacket(byte kind, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (kind == SkyRagePacket.RUN) {
            if (toast != null) toast.done = true;
            toast = new RunToast(Math.max(1000L, ticks * 50L + 500L));
            mc.getToasts().addToast(toast);
        } else if (kind == SkyRagePacket.FACE) {
            // o tranco: a câmera vem pra frente, na horizontal, na cara dele
            if (mc.player != null) {
                mc.player.setXRot(0F);
                mc.player.xRotO = 0F;
            }
        } else {
            if (toast != null) toast.done = true;
            toast = null;
            if (kind == SkyRagePacket.HIT) hitAt = Util.getMillis();
        }
    }

    private static boolean red() {
        return hitAt >= 0L && Util.getMillis() - hitAt < RED_MS;
    }

    /** O vermelho por cima de tudo, até a tela de morte (que também espera o vermelho acabar). */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !red()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        GuiGraphics g = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        RenderSystem.disableDepthTest();
        drawRed(g, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), Util.getMillis() - hitAt);
        g.flush();
        RenderSystem.enableDepthTest();
    }

    /** Vermelho de sangue, com escorridos mais escuros descendo. */
    static void drawRed(GuiGraphics g, int w, int h, long ms) {
        g.fill(0, 0, w, h, 0xF08A0303);
        float k = Mth.clamp(ms / (float) RED_MS, 0F, 1F);
        for (int i = 0; i < 24; i++) {
            int x = (int) ((i * 97L + 13) % Math.max(1, w));
            int len = (int) (h * (0.15F + 0.85F * k) * (0.3F + ((i * 37) % 70) / 100F));
            int wd = 2 + (i * 7) % 5;
            g.fill(x, 0, x + wd, len, 0xFF4A0000);
            g.fill(x - 1, len - 2, x + wd + 1, len + 2, 0xFF4A0000);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (!red() || !(event.getNewScreen() instanceof DeathScreen next)) return;
        event.setNewScreen(new RedHold(next));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        hitAt = -1L;
        toast = null;
    }

    /** O vermelho segurando a tela de morte até acabar. */
    static final class RedHold extends Screen {
        private final Screen next;

        RedHold(Screen next) {
            super(Component.empty());
            this.next = next;
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            drawRed(g, this.width, this.height, Util.getMillis() - hitAt);
        }

        @Override
        public void tick() {
            if (!red() && this.minecraft != null) this.minecraft.setScreen(next);
        }

        @Override
        public boolean shouldCloseOnEsc() {
            return false;
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }

    /** O aviso de conquista vermelho, com o sangue escorrendo da borda de cima. */
    static final class RunToast implements Toast {
        private static final int W = 214, H = 32;
        private final long life;
        boolean done;

        RunToast(long lifeMs) {
            this.life = lifeMs;
        }

        @Override
        public int width() {
            return W;
        }

        @Override
        public int height() {
            return H;
        }

        @Override
        public Visibility render(GuiGraphics g, ToastComponent c, long t) {
            // a caixa: preta avermelhada com borda vermelha (o "desafio" das conquistas, só que em sangue)
            g.fill(0, 0, W, H, 0xF0140000);
            g.fill(0, 0, W, 1, 0xFFB00000);
            g.fill(0, H - 1, W, H, 0xFFB00000);
            g.fill(0, 0, 1, H, 0xFFB00000);
            g.fill(W - 1, 0, W, H, 0xFFB00000);
            Font font = c.getMinecraft().font;
            Component title = Component.translatable("froggydude.skyrage.title");
            Component line = Component.translatable("froggydude.skyrage.run");
            g.drawString(font, title, 8, 5, 0xFFFF5555, false);
            // a frase pisca entre vermelho vivo e escuro
            int pulse = (t / 250L) % 2 == 0 ? 0xFFFF2020 : 0xFFC00000;
            g.drawString(font, line, 8, 18, pulse, true);
            // o sangue escorrendo da borda de cima, cada fio no seu tempo, até sair da caixa
            for (int i = 0; i < 9; i++) {
                long t0 = i * 260L + (i * 131L) % 400L;
                if (t < t0) continue;
                int x = 6 + i * 23 + (i * 7) % 9;
                int len = (int) Math.min(H + 10, 2 + (t - t0) / (28 + (i * 11) % 30));
                g.fill(x, 0, x + 2, len, 0xFF7A0000);
                g.fill(x - 1, len - 1, x + 3, len + 2, 0xFF7A0000); // a gota na ponta
            }
            return done || t >= life ? Visibility.HIDE : Visibility.SHOW;
        }
    }
}
