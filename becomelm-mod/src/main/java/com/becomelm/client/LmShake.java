package com.becomelm.client;

import com.becomelm.BecomeLM;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * (v0.3.5, LM: "tremidinha nos dois") Um tranco curto na tela, em primeira pessoa, a cada pancada de marreta: em
 * quem bate e em quem apanha (mais forte). As câmeras de cinema não tremem (são pra gravar).
 */
@Mod.EventBusSubscriber(modid = BecomeLM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LmShake {

    /** Quem apanha: até ~1,6 grau, e quem bate a metade. */
    static final float VICTIM = 1.6F;
    static final float HITTER = 0.8F;
    /** Dura 0,16 s (menos que o intervalo entre pancadas, 0,25 s) e balança 2 vezes, sumindo. */
    private static final float DURATION_S = 0.16F;
    private static final float SWINGS = 2F;

    private static long start = -1L;
    private static float amp;

    private LmShake() {
    }

    static void kick(float degrees) {
        start = System.nanoTime();
        amp = degrees;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        if (start < 0L) return;
        Minecraft mc = Minecraft.getInstance();
        float t = (System.nanoTime() - start) / 1.0E9F / DURATION_S;
        if (t >= 1F || mc.isPaused()) {
            start = -1L;
            return;
        }
        if (!mc.options.getCameraType().isFirstPerson() || LmCamera.active()) return;
        float fade = (1F - t) * (1F - t);
        float s = Mth.sin(t * Mth.TWO_PI * SWINGS) * amp * fade;
        event.setPitch(event.getPitch() + s);
        event.setRoll(event.getRoll() - s * 0.7F);
    }
}
