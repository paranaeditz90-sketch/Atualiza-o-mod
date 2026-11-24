package com.froggydude.client;

import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.network.ClientShakeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A cutscene do jumpscare raro (v0.7.7): o jogo vira a tua câmera pra ele (v0.7.8) e ele se joga em ti -
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

    /** De onde a câmera saiu no giro forçado (e qual giro foi). */
    private static float startYaw, startPitch;
    private static int seenSeq = -1;

    private JumpscareView() {
    }

    /**
     * O jogo vira a tua câmera pra ele (LM: "o jogo deveria forçar o jogador a olhar pra trás
     * e tomar o jumpscare"): um giro rápido, suave no começo e no fim, até a cara dele; depois
     * segura nele enquanto ele se joga (aí a câmera de "derrubado" assume).
     */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !ClientShakeHandler.isCinematic()
                || ClientShakeHandler.isKnockedDown()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        Entity e = mc.level.getEntity(ClientShakeHandler.getTurnId());
        if (e == null) return;
        if (seenSeq != ClientShakeHandler.turnSeq()) {
            seenSeq = ClientShakeHandler.turnSeq();
            startYaw = player.getYRot();
            startPitch = player.getXRot();
        }
        float partial = event.renderTickTime;
        Vec3 eye = player.getEyePosition(partial);
        Vec3 face = e instanceof FroggydudeEntity f ? f.getFacePosition(partial) : e.getEyePosition(partial);
        Vec3 d = face.subtract(eye);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        float wantYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
        float wantPitch = (float) (-(Mth.atan2(d.y, flat) * Mth.RAD_TO_DEG));
        float p = ClientShakeHandler.turnProgress(partial);
        float k = p * p * (3F - 2F * p); // suave no começo e no fim
        float yaw = startYaw + Mth.wrapDegrees(wantYaw - startYaw) * k;
        float pitch = Mth.clamp(startPitch + (wantPitch - startPitch) * k, -90F, 90F);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
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
