package com.froggydude.client.pieces;

import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;

/**
 * O "nhum nhum NHOM, nhum hum" com o sangue escorrendo (vídeo "WHAT HAPPENED TO
 * ME???", 2:50 - ele comendo o jogador despedaçado) em loop perfeito enquanto
 * ele come em cima dos pedaços, acompanhando ele, na velocidade da raiva e da
 * fome (o pitch do Minecraft acelera o clipe junto). Só com o pacote de voz -
 * sem ele o som do mod é mudo e soam as mordidas do servidor.
 */
final class FeastSounds {

    private static final Map<Integer, Loop> PLAYING = new HashMap<>();

    private FeastSounds() {
    }

    static void tick(ClientLevel level) {
        Minecraft mc = Minecraft.getInstance();
        PLAYING.values().removeIf(Loop::isStopped);
        if (mc.player == null || !hasLoop(mc)) return;
        for (FroggydudeEntity f : level.getEntitiesOfClass(FroggydudeEntity.class, mc.player.getBoundingBox().inflate(48))) {
            if (f.getFroggyState() != FroggyState.FEAST || PLAYING.containsKey(f.getId())) continue;
            Loop loop = new Loop(f);
            PLAYING.put(f.getId(), loop);
            mc.getSoundManager().play(loop);
        }
    }

    static void clear() {
        PLAYING.values().forEach(Loop::end);
        PLAYING.clear();
    }

    /** O pacote de voz pôs o clipe no evento (sem ele a lista é vazia). */
    private static boolean hasLoop(Minecraft mc) {
        WeighedSoundEvents w = mc.getSoundManager().getSoundEvent(ModSounds.FEAST.get().getLocation());
        return w != null && w.getWeight() > 0;
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final FroggydudeEntity froggy;

        Loop(FroggydudeEntity froggy) {
            super(ModSounds.FEAST.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
            this.froggy = froggy;
            this.looping = true;
            this.delay = 0;
            this.volume = 1.0F;
            this.pitch = froggy.getFeastRate();
            this.x = froggy.getX();
            this.y = froggy.getY();
            this.z = froggy.getZ();
        }

        @Override
        public void tick() {
            if (froggy.isRemoved() || !froggy.isAlive() || froggy.getFroggyState() != FroggyState.FEAST) {
                stop();
                return;
            }
            this.x = froggy.getX();
            this.y = froggy.getY();
            this.z = froggy.getZ();
            this.pitch = froggy.getFeastRate();
        }

        void end() {
            stop();
        }
    }
}
