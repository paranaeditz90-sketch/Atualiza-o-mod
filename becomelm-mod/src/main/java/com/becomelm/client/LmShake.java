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
 * A tremida da tela na marreta (LM, v0.3.6: "tremidinha normal para quem apanha, tremidinha em 3 pessoa apenas para
 * mim. Tenta seguir a mesma tremida do vídeo"):
 * - quem APANHA, em primeira pessoa: um tranco curto a cada pancada (o mesmo da 0.3.5);
 * - quem BATE, em terceira pessoa (F5 e as câmeras de cinema): o chacoalhão do vídeo (VS Grox MANHUNT, a câmera de
 *   fora enquanto ele martela: miúdo e contínuo, ~0,8° rms e ~±1,5° no pico, umas 10 vezes por segundo, e some uns
 *   0,5 s depois da última pancada). Só na tela de quem bate.
 * - (v0.3.9, LM: "1ª treme, F5 igual") quem BATE treme também em PRIMEIRA pessoa, o mesmo chacoalhão da 3ª pessoa
 *   (antes, na 0.3.6 a 0.3.8, em primeira pessoa quem bate não tremia). O F5 e as câmeras de cinema ficam iguais.
 */
@Mod.EventBusSubscriber(modid = BecomeLM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LmShake {

    /** Quem apanha: até ~1,6 grau. */
    static final float VICTIM = 1.6F;
    /** Dura 0,16 s (menos que o intervalo entre pancadas, 0,25 s) e balança 2 vezes, sumindo. */
    private static final float DURATION_S = 0.16F;
    private static final float SWINGS = 2F;

    /** O chacoalhão de quem bate: o pico (as duas ondas juntas), quanto segura cheio e quanto leva pra sumir. */
    private static final float JITTER_DEG = 1.5F;
    private static final float JITTER_HOLD_S = 0.3F;
    private static final float JITTER_FADE_S = 0.5F;

    private static long start = -1L;
    private static float amp;
    private static long lastHammer = -1L;

    private LmShake() {
    }

    /** Quem apanha (primeira pessoa). */
    static void kick(float degrees) {
        start = System.nanoTime();
        amp = degrees;
    }

    /** Quem bate: mais uma pancada (o chacoalhão segue enquanto elas vêm). */
    static void hammer() {
        lastHammer = System.nanoTime();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) return;
        long now = System.nanoTime();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson() && !LmCamera.active();
        if (start >= 0L) {
            float t = (now - start) / 1.0E9F / DURATION_S;
            if (t >= 1F) {
                start = -1L;
            } else if (firstPerson) {
                float fade = (1F - t) * (1F - t);
                float s = Mth.sin(t * Mth.TWO_PI * SWINGS) * amp * fade;
                event.setPitch(event.getPitch() + s);
                event.setRoll(event.getRoll() - s * 0.7F);
            }
        }
        if (lastHammer >= 0L) {
            float since = (now - lastHammer) / 1.0E9F;
            if (since >= JITTER_HOLD_S + JITTER_FADE_S) {
                lastHammer = -1L;
            } else {
                float k = since <= JITTER_HOLD_S ? 1F : 1F - (since - JITTER_HOLD_S) / JITTER_FADE_S;
                float a = JITTER_DEG * k * k;
                // duas ondas fora de compasso em cada eixo: parece aleatório, sem virar um balanço só
                float s = (now / 1000000L % 1000000L) / 1000F;
                float pitch = 0.65F * Mth.sin(s * Mth.TWO_PI * 9.1F + 0.3F) + 0.35F * Mth.sin(s * Mth.TWO_PI * 13.7F + 1.7F);
                float yaw = 0.65F * Mth.sin(s * Mth.TWO_PI * 7.3F + 2.1F) + 0.35F * Mth.sin(s * Mth.TWO_PI * 11.9F + 0.8F);
                event.setPitch(event.getPitch() + pitch * a);
                event.setYaw(event.getYaw() + yaw * a);
            }
        }
    }
}
