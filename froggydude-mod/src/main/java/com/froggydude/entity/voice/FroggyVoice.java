package com.froggydude.entity.voice;

import net.minecraft.nbt.CompoundTag;

/**
 * A fila de falas de um FroggyDude: em cada situação ele diz a próxima fala da
 * sequência dos vídeos (e não uma sorteada). Salva no mundo junto com ele.
 */
public class FroggyVoice {

    private final int[] next = new int[VoiceSituation.values().length];

    /** A fala que vem agora nessa situação (sem andar a fila). */
    public VoiceLine peek(VoiceSituation s) {
        int i = next[s.ordinal()];
        return s.lines[Math.min(i, s.lines.length - 1)];
    }

    /** Falou: anda a fila (no fim, volta pro loopFrom). */
    public void advance(VoiceSituation s) {
        int i = next[s.ordinal()] + 1;
        next[s.ordinal()] = i >= s.lines.length ? s.loopFrom : i;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        for (VoiceSituation s : VoiceSituation.values()) {
            if (next[s.ordinal()] != 0) tag.putInt(s.name(), next[s.ordinal()]);
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        for (VoiceSituation s : VoiceSituation.values()) {
            int i = tag.getInt(s.name());
            next[s.ordinal()] = i >= 0 && i < s.lines.length ? i : 0;
        }
    }
}
