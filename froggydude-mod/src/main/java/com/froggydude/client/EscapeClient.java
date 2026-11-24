package com.froggydude.client;

import com.froggydude.init.ModEntityTypes;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * (v0.7.67) O sistema de fuga no cliente: a adrenalina (player.Adrenaline) pulsa as bordas da tela de vermelho no ritmo
 * do coração, que bate alto (o batimento do Warden), enquanto durar a velocidade.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class EscapeClient {

    /** Até quando (relógio de verdade) e desde quando. */
    private static long until = -1L;
    private static long since;
    private static int beats;

    private EscapeClient() {
    }

    public static void onPacket(byte kind, int ticks) {
        if (kind == 0) {
            since = Util.getMillis();
            until = since + ticks * 50L;
            beats = 0;
        }
    }

    /** ~150 bpm: o coração disparado. */
    private static final long BEAT_MS = 400L;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || until < 0L) return;
        long now = Util.getMillis();
        if (now >= until) {
            until = -1L;
            return;
        }
        int beat = (int) ((now - since) / BEAT_MS);
        if (beat >= beats) {
            beats = beat + 1;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 1.0F, 1.0F));
        }
    }

    @SubscribeEvent
    public static void onHud(RenderGuiEvent.Post event) {
        if (until < 0L) return;
        long now = Util.getMillis();
        float phase = ((now - since) % BEAT_MS) / (float) BEAT_MS;
        float pulse = phase < 0.25F ? phase / 0.25F : 1.0F - (phase - 0.25F) / 0.75F; // batida rápida, solta devagar
        float fade = Mth.clamp((until - now) / 600.0F, 0.0F, 1.0F); // some no último 0,6 s
        int a = (int) (0x90 * pulse * fade);
        if (a <= 2) return;
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = event.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // as bordas: faixas que esmaecem pro meio
        int band = Math.max(12, Math.min(w, h) / 6);
        for (int i = 0; i < band; i++) {
            int ai = (int) (a * (1.0F - i / (float) band));
            int c = ai << 24 | 0xB00000;
            g.fill(i, i, w - i, i + 1, c);
            g.fill(i, h - i - 1, w - i, h - i, c);
            g.fill(i, i + 1, i + 1, h - i - 1, c);
            g.fill(w - i - 1, i + 1, w - i, h - i - 1, c);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        until = -1L;
    }
}
