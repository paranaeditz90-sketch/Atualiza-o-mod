package com.froggydude.brain;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;

/**
 * O que ele aprendeu sobre UM jogador (salvo no mundo):
 * - o estilo dele (foge, sobe em torre, briga, atira, se esconde), com esquecimento;
 * - quanto cada estratégia de caçada rendeu contra cada estilo (Q, a média das recompensas);
 * - quantas vezes cada ataque acertou ou errou ESSE jogador (pra amostragem de Thompson).
 */
public class PlayerMemory {

    static final int STYLES = Style.values().length;
    static final int STRATEGIES = Strategy.values().length;

    public String name = "";
    /** Quanto de cada estilo ele já viu (decai devagar: o jogador muda). */
    public final float[] style = new float[STYLES];
    /** Valor de cada estratégia por estilo do jogador. */
    public final float[][] q = new float[STYLES][STRATEGIES];
    public final int[][] n = new int[STYLES][STRATEGIES];
    /** Ataque -> {acertos, erros} (com esquecimento). */
    public final Map<String, float[]> attacks = new HashMap<>();

    public int hunts;
    public int kills;
    public int escapes;
    public int froggyDeaths;

    public Style dominantStyle() {
        float total = 0F;
        Style best = Style.UNKNOWN;
        float bestV = 0F;
        for (Style s : Style.OBSERVED) {
            total += style[s.ordinal()];
            if (style[s.ordinal()] > bestV) {
                bestV = style[s.ordinal()];
                best = s;
            }
        }
        // pouca observação ainda: não chuta
        return total < 4.0F ? Style.UNKNOWN : best;
    }

    /** Parcela (0-1) de cada estilo observado. */
    public float share(Style s) {
        float total = 0F;
        for (Style o : Style.OBSERVED) total += style[o.ordinal()];
        return total <= 0F ? 0F : style[s.ordinal()] / total;
    }

    public void observe(Style s, float amount) {
        for (int i = 0; i < STYLES; i++) style[i] *= 0.998F;
        style[s.ordinal()] += amount;
    }

    public float[] attack(String attack) {
        return attacks.computeIfAbsent(attack, k -> new float[2]);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        CompoundTag st = new CompoundTag();
        for (Style s : Style.OBSERVED) st.putFloat(s.name(), style[s.ordinal()]);
        tag.put("Style", st);
        ListTag ql = new ListTag();
        for (Style c : Style.values()) {
            for (Strategy a : Strategy.values()) {
                if (n[c.ordinal()][a.ordinal()] == 0) continue;
                CompoundTag e = new CompoundTag();
                e.putString("C", c.name());
                e.putString("A", a.name());
                e.putFloat("Q", q[c.ordinal()][a.ordinal()]);
                e.putInt("N", n[c.ordinal()][a.ordinal()]);
                ql.add(e);
            }
        }
        tag.put("Q", ql);
        CompoundTag at = new CompoundTag();
        attacks.forEach((k, v) -> {
            CompoundTag e = new CompoundTag();
            e.putFloat("Hit", v[0]);
            e.putFloat("Miss", v[1]);
            at.put(k, e);
        });
        tag.put("Attacks", at);
        tag.putInt("Hunts", hunts);
        tag.putInt("Kills", kills);
        tag.putInt("Escapes", escapes);
        tag.putInt("FroggyDeaths", froggyDeaths);
        return tag;
    }

    public static PlayerMemory load(CompoundTag tag) {
        PlayerMemory m = new PlayerMemory();
        m.name = tag.getString("Name");
        CompoundTag st = tag.getCompound("Style");
        for (Style s : Style.OBSERVED) m.style[s.ordinal()] = st.getFloat(s.name());
        for (Tag t : tag.getList("Q", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            try {
                Style c = Style.valueOf(e.getString("C"));
                Strategy a = Strategy.valueOf(e.getString("A"));
                m.q[c.ordinal()][a.ordinal()] = e.getFloat("Q");
                m.n[c.ordinal()][a.ordinal()] = e.getInt("N");
            } catch (IllegalArgumentException ignored) {
                // estilo/estratégia que não existe mais nesta versão
            }
        }
        CompoundTag at = tag.getCompound("Attacks");
        for (String k : at.getAllKeys()) {
            CompoundTag e = at.getCompound(k);
            m.attacks.put(k, new float[]{e.getFloat("Hit"), e.getFloat("Miss")});
        }
        m.hunts = tag.getInt("Hunts");
        m.kills = tag.getInt("Kills");
        m.escapes = tag.getInt("Escapes");
        m.froggyDeaths = tag.getInt("FroggyDeaths");
        return m;
    }
}
