package com.froggydude.client.cutscene;

import com.froggydude.entity.Finisher;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.init.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * O áudio da cutscene (LM, v0.4.3): com o pacote de voz, a cutscene inteira toca o
 * som dele comendo a zebra, do próprio vídeo "Eating a Zebra" - a tabela do golpe
 * final (entity.Finisher) foi tirada desse vídeo, então o áudio cai junto com cada
 * mordida, arrancada e troca de câmera. O vídeo começa 0,2 s depois do tick 0 da
 * tabela (o corte pro outro lado, o rasgo do braço e a mordida na cabeça batem
 * assim). Enquanto ele toca, os sons de cada golpe (mordida, rasgo, mastigada,
 * osso, o gemido da vítima) ficam mudos pra não embolar. Sem o pacote, o evento é
 * vazio e fica tudo como antes.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FinisherAudio {

    /** Em que tick da tabela o vídeo da zebra começa. */
    private static final int START_TICK = 4;
    /** Daqui pra longe da cutscene, os outros sons continuam. */
    private static final double MUTE_RANGE = 24.0D;
    /** Sons do jogo que a zebra já tem (a vítima gritando, engolindo, mastigando). */
    private static final Set<String> MUTED_VANILLA = Set.of(
            "entity.player.hurt", "entity.player.death", "entity.villager.hurt", "entity.villager.death",
            "entity.generic.drink", "entity.generic.eat", "entity.frog.eat", "block.slime_block.step");

    private static final Map<ClientFinisher.Entry, SoundInstance> PLAYING = new HashMap<>();
    /** Cutscenes que já tocaram (vítima + tick de início): o servidor reenvia o início. */
    private static final Set<Long> STARTED = new HashSet<>();
    private static final boolean DEBUG = Boolean.getBoolean("froggydude.debug");

    private FinisherAudio() {
    }

    /** O pacote de voz trouxe o áudio da zebra? */
    private static boolean available() {
        WeighedSoundEvents w = Minecraft.getInstance().getSoundManager()
                .getSoundEvent(ModSounds.FINISHER.getId());
        return w != null && w.getWeight() > 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        PLAYING.entrySet().removeIf(p -> {
            ClientFinisher.Entry e = p.getKey();
            boolean over = e.ended() || e.t(0F) > Finisher.END + 10;
            if (e.ended() && e.t(0F) < Finisher.SWALLOW) mc.getSoundManager().stop(p.getValue()); // interrompida
            return over && !mc.getSoundManager().isActive(p.getValue());
        });
        boolean has = available();
        for (ClientFinisher.Entry e : ClientFinisher.all()) {
            if (!has || e.ended() || e.audio) continue;
            long key = ((long) e.victimId << 32) ^ e.start;
            if (STARTED.contains(key)) {
                e.audio = true;
                continue;
            }
            float t = e.t(0F);
            int from = e.skipLeft ? Finisher.SKIP : START_TICK;
            if (t < from) continue;
            e.audio = true;
            STARTED.add(key);
            if (STARTED.size() > 64) STARTED.clear();
            // chegou atrasado (entrou no mundo no meio): não começa o áudio no meio
            if (t > from + 10) continue;
            SoundInstance s = new SimpleSoundInstance(
                    (e.skipLeft ? ModSounds.FINISHER_R : ModSounds.FINISHER).get().getLocation(),
                    SoundSource.HOSTILE, 1.0F, 1.0F, RandomSource.create(), false, 0,
                    SoundInstance.Attenuation.LINEAR, e.anchor.x, e.anchor.y + 0.5D, e.anchor.z, false);
            // o grito da fase 2 (9 s) não continua por baixo da zebra
            mc.getSoundManager().stop(ModSounds.SCREAM.get().getLocation(), null);
            mc.getSoundManager().play(s);
            PLAYING.put(e, s);
            if (DEBUG) System.out.println("[FROGGYDEBUG] zebra: tocando " + s.getLocation() + " no tick " + t);
        }
    }

    /** Com o áudio da zebra tocando, cala os sons de cada golpe perto da cutscene. */
    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance s = event.getSound();
        if (s == null || PLAYING.isEmpty() || PLAYING.containsValue(s)) return;
        ResourceLocation loc = s.getLocation();
        boolean ours = ModEntityTypes.MOD_ID.equals(loc.getNamespace())
                && !loc.getPath().equals("froggydude.finisher") && !loc.getPath().equals("froggydude.finisher_r");
        boolean vanilla = "minecraft".equals(loc.getNamespace()) && MUTED_VANILLA.contains(loc.getPath());
        if (!ours && !vanilla) return;
        for (ClientFinisher.Entry e : PLAYING.keySet()) {
            if (e.ended() || e.t(0F) >= Finisher.END) continue;
            double dx = s.getX() - e.anchor.x, dy = s.getY() - e.anchor.y, dz = s.getZ() - e.anchor.z;
            if (!s.isRelative() && dx * dx + dy * dy + dz * dz < MUTE_RANGE * MUTE_RANGE) {
                if (DEBUG) System.out.println("[FROGGYDEBUG] zebra: calou " + loc);
                event.setSound(null);
                return;
            }
        }
    }
}
