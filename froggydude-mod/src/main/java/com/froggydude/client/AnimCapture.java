package com.froggydude.client;

import com.froggydude.init.ModEntityTypes;
import com.froggydude.entity.PacedAnimationController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * Ferramenta de teste (froggydude.capture): um print por quadro desenhado, com o
 * tempo da animação no nome (screenshots/cap_SSS.SSSS.png). Com a animação em
 * câmera lenta (froggydude.animSlow), dá a pose exata de cada 1/60 s pra sobrepor
 * no vídeo original, mesmo o jogo rodando a poucos quadros por segundo.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class AnimCapture {

    private static final boolean ON = Boolean.getBoolean("froggydude.capture");
    private static int frames;

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        double t = PacedAnimationController.debugLastSeconds;
        if (++frames % 40 == 1) {
            System.out.println("[ANIMCAP] quadro " + frames + " t=" + t + " tela=" + mc.screen + " mundo=" + (mc.level != null));
        }
        if (mc.level == null || mc.screen != null || t < 0D) return;
        Screenshot.grab(mc.gameDirectory, String.format(Locale.ROOT, "cap_%08.4f.png", t),
                mc.getMainRenderTarget(), c -> { });
    }
}
