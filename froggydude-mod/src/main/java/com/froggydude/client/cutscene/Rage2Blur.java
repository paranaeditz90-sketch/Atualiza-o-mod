package com.froggydude.client.cutscene;

import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.init.ModSounds;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * (v0.7.49) O começo da raiva 2 no cliente:
 * <ul>
 * <li>o borrão de movimento durante as trocas de pose (LM 23/11: "precisa de um blur pra esconder bem os efeitos
 * errados"): o mesmo efeito da cena de morte (CinematicCamera, shaders/post/cutscene_blur.json), que só borra o que se
 * mexe (ele), mais forte, pra cada pose deixar rastro na seguinte; parou na pose final, desliga;</li>
 * <li>o som original dele se contorcendo (ModSounds RAGE2, 1,6 s = as trocas), tocado AQUI, depois do borrão ligar:
 * carregar o efeito trava a tela um instante (testado: ~0,6 s no PC da nuvem) e a animação só começa no quadro seguinte;
 * tocado pelo servidor, o som saía na frente da animação.</li>
 * </ul>
 * O tempo conta no relógio de verdade: depois de um travamento o jogo roda os ticks atrasados de uma vez. Só pra quem
 * viu ele entrar na raiva 2 (a até 64 blocos); quem chega no meio não ouve o som de novo nem vê borrão.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class Rage2Blur {

    /** Quanto do quadro anterior entra (a cena de morte usa 0,35). */
    static final float BLUR_MIX = 0.6F;
    /** As trocas de pose em milissegundos (FroggydudeEntity.RAGE2_GLITCH ticks), com uma folga de um tick. */
    private static final long GLITCH_MS = FroggydudeEntity.RAGE2_GLITCH * 50L + 50L;
    /** Quando (relógio de verdade) cada FroggyDude começou a raiva 2 aqui; -1 = chegou já no meio. */
    private static final Map<Integer, Long> since = new HashMap<>();
    /** Os que estavam fora da raiva 2 no tick passado (pra saber que ela começou agora). */
    private static final Set<Integer> calm = new HashSet<>();
    private static boolean on;

    private Rage2Blur() {
    }

    static boolean active() {
        return on;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            since.clear();
            calm.clear();
            on = false;
            return;
        }
        boolean other = CinematicCamera.active() || GrabCamera.active();
        boolean want = false;
        Set<Integer> seen = new HashSet<>(), nowCalm = new HashSet<>();
        for (FroggydudeEntity f : mc.level.getEntitiesOfClass(FroggydudeEntity.class, mc.player.getBoundingBox().inflate(64D))) {
            int id = f.getId();
            if (!f.inRage2()) {
                nowCalm.add(id);
                continue;
            }
            seen.add(id);
            Long t0 = since.get(id);
            if (t0 == null) {
                if (!calm.contains(id)) {
                    since.put(id, -1L); // chegou com ele já na raiva 2
                    continue;
                }
                // começou agora: liga o borrão (pode travar a tela um instante) e só depois o som
                if (!other) CinematicCamera.blurOn(mc);
                mc.level.playLocalSound(f.getX(), f.getY() + 1.0D, f.getZ(), ModSounds.RAGE2.get(), SoundSource.HOSTILE,
                        3.0F, 1.0F, false);
                t0 = Util.getMillis();
                since.put(id, t0);
            }
            if (t0 >= 0L && Util.getMillis() - t0 <= GLITCH_MS) want = true;
        }
        since.keySet().retainAll(seen);
        calm.clear();
        calm.addAll(nowCalm);
        if (want && !other) {
            CinematicCamera.blurOn(mc);
            on = true;
        } else if (on) {
            on = false;
            if (!other) CinematicCamera.blurOff(mc);
        }
    }
}
