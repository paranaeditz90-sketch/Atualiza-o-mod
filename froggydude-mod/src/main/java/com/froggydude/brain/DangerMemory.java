package com.froggydude.brain;

import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;

/**
 * Onde ele já se machucou (lava, fogo, cacto, explosão, armadilha de flecha,
 * neve fofa...), por dimensão. A navegação dele trata esses blocos como
 * perigosos e dá a volta - quem montou uma armadilha de TNT na entrada da base
 * pega ele uma vez, não duas. Esquece depois de 3 dias de jogo.
 */
public class DangerMemory {

    /** Quanto tempo ele lembra de um perigo (3 dias de jogo). */
    public static final long FORGET_AFTER = 72000L;
    private static final int MAX_PER_DIMENSION = 1024;

    /** dimensão -> (bloco -> hora do jogo em que esquece) */
    private final Map<String, Long2LongOpenHashMap> dangers = new HashMap<>();

    public boolean isDangerous(String dimension, int x, int y, int z, long now) {
        Long2LongOpenHashMap m = dangers.get(dimension);
        if (m == null || m.isEmpty()) return false;
        long until = m.getOrDefault(BlockPos.asLong(x, y, z), 0L);
        return until > now;
    }

    /** Marca o bloco e os vizinhos (raio em blocos, no plano e 1 pra cima/baixo). */
    public void mark(String dimension, BlockPos center, int radius, long now) {
        Long2LongOpenHashMap m = dangers.computeIfAbsent(dimension, k -> new Long2LongOpenHashMap());
        if (m.size() > MAX_PER_DIMENSION) forget(m, now);
        long until = now + FORGET_AFTER;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    m.put(BlockPos.asLong(center.getX() + dx, center.getY() + dy, center.getZ() + dz), until);
                }
            }
        }
    }

    public int size(String dimension) {
        Long2LongOpenHashMap m = dangers.get(dimension);
        return m == null ? 0 : m.size();
    }

    private static void forget(Long2LongOpenHashMap m, long now) {
        m.long2LongEntrySet().removeIf(e -> e.getLongValue() <= now);
        if (m.size() > MAX_PER_DIMENSION) m.clear(); // cheio de perigo "vivo": recomeça
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        dangers.forEach((dim, m) -> {
            long[] flat = new long[m.size() * 2];
            int i = 0;
            for (Long2LongMap.Entry e : m.long2LongEntrySet()) {
                flat[i++] = e.getLongKey();
                flat[i++] = e.getLongValue();
            }
            tag.put(dim, new LongArrayTag(flat));
        });
        return tag;
    }

    public void load(CompoundTag tag) {
        dangers.clear();
        for (String dim : tag.getAllKeys()) {
            if (tag.getTagType(dim) != Tag.TAG_LONG_ARRAY) continue;
            long[] flat = tag.getLongArray(dim);
            Long2LongOpenHashMap m = new Long2LongOpenHashMap();
            for (int i = 0; i + 1 < flat.length; i += 2) m.put(flat[i], flat[i + 1]);
            dangers.put(dim, m);
        }
    }
}
