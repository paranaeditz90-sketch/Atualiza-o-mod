package com.froggydude.client;

import com.froggydude.init.ModEntityTypes;
import com.froggydude.network.ClientShakeHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A cutscene do jumpscare raro (v0.7.7): ele aparece na tua cara e se joga em ti -
 * clarão vermelho no instante, sem HUD nem mão, faixas pretas de cinema enquanto ele
 * está em cima de ti descendo os braços. A câmera presa no rosto dele é a de sempre
 * (derrubado); aqui é só o "virou filme".
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class JumpscareView {

    private static final ResourceLocation CUTSCENE_OVERLAY = new ResourceLocation(ModEntityTypes.MOD_ID, "cutscene");
    /** As faixas entram/saem em uns 4-6 ticks. */
    private static final float BARS_IN = 4F;
    private static final float BARS_OUT = 6F;
    /** O clarão do susto. */
    private static final float FLASH = 7F;

    private JumpscareView() {
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
        if (ClientShakeHandler.isCinematic() && !CUTSCENE_OVERLAY.equals(event.getOverlay().id())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (ClientShakeHandler.isCinematic()) event.setCanceled(true);
    }

    /** Desenhado no overlay "cutscene" (por cima de tudo). */
    public static void render(GuiGraphics gfx, float partial, int w, int h) {
        if (!ClientShakeHandler.isCinematic()) return;
        float age = ClientShakeHandler.cinematicAge() + partial;
        float left = ClientShakeHandler.cinematicLeft() - partial;
        float k = Mth.clamp(Math.min(age / BARS_IN, left / BARS_OUT), 0F, 1F);
        int bar = Math.round(h * 0.12F * k);
        if (bar > 0) {
            gfx.fill(0, 0, w, bar, 0xFF000000);
            gfx.fill(0, h - bar, w, h, 0xFF000000);
        }
        if (age < FLASH) {
            int a = Math.round(Mth.clamp(1F - age / FLASH, 0F, 1F) * 150F);
            gfx.fill(0, 0, w, h, (a << 24) | 0x8A0000);
        }
    }
}
