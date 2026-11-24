package com.froggydude.brain;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * O faro do FroggyDude: cada jogador deixa um rastro de cheiro por onde passa
 * (uma marca a cada ~1,5 bloco). O cheiro dura 1 minuto; agachado ou na chuva,
 * metade disso; dentro d'água não fica marca nenhuma (a água lava). Ele segue
 * esse rastro sem precisar ver ninguém. Fica só na memória (não vai pro save).
 */
public final class Scent {

    /** Quanto tempo o cheiro dura (ticks). */
    public static final int LIFETIME = 1200;
    private static final int MAX_MARKS = 240;
    private static final double MIN_STEP = 1.5D;

    public record Mark(ResourceKey<Level> dim, Vec3 pos, long time, int life) {
        /** 1 = acabou de passar, 0 = já não tem cheiro. */
        public float strength(long now) {
            return Math.max(0F, 1F - (float) (now - time) / life);
        }
    }

    private static final Map<UUID, ArrayDeque<Mark>> TRAILS = new HashMap<>();

    private Scent() {
    }

    /** A cada 10 ticks, pra cada jogador (FroggyServerEvents). */
    public static void record(ServerPlayer p, long now) {
        if (p.isSpectator() || p.isCreative() || !p.isAlive()) return;
        if (p.isInWater()) return; // a água lava o cheiro
        ArrayDeque<Mark> trail = TRAILS.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        Mark last = trail.peekLast();
        ResourceKey<Level> dim = p.level().dimension();
        Vec3 pos = p.position();
        if (last != null && last.dim == dim && last.pos.distanceToSqr(pos) < MIN_STEP * MIN_STEP
                && now - last.time < 100) {
            return;
        }
        int life = LIFETIME;
        if (p.isCrouching()) life /= 2; // andando agachado deixa menos cheiro
        if (p.level().isRainingAt(BlockPos.containing(pos).above())) life /= 2;
        trail.addLast(new Mark(dim, pos, now, life));
        while (trail.size() > MAX_MARKS) trail.removeFirst();
    }

    /**
     * A marca mais nova de alguém que dá pra sentir daqui (até {@code range}
     * blocos, com algum cheiro ainda). Com {@code only}, só o rastro dessa pessoa.
     */
    @Nullable
    public static Sniff freshest(ServerLevel level, Vec3 nose, double range, @Nullable UUID only) {
        long now = level.getGameTime();
        Sniff best = null;
        double r2 = range * range;
        for (Map.Entry<UUID, ArrayDeque<Mark>> e : TRAILS.entrySet()) {
            if (only != null && !only.equals(e.getKey())) continue;
            Iterator<Mark> it = e.getValue().descendingIterator();
            while (it.hasNext()) {
                Mark m = it.next();
                if (m.strength(now) <= 0F) break; // daqui pra trás é tudo mais velho
                if (m.dim != level.dimension() || m.pos.distanceToSqr(nose) > r2) continue;
                if (best == null || m.time > best.mark.time) best = new Sniff(e.getKey(), m);
                break; // a primeira que serve é a mais nova desse rastro
            }
        }
        return best;
    }

    /** A última marca dessa pessoa (onde o rastro termina), se ainda tem cheiro. */
    @Nullable
    public static Mark newest(UUID player, long now) {
        ArrayDeque<Mark> trail = TRAILS.get(player);
        if (trail == null || trail.isEmpty()) return null;
        Mark m = trail.peekLast();
        return m.strength(now) > 0F ? m : null;
    }

    public static void forget(UUID player) {
        TRAILS.remove(player);
    }

    public static void clear() {
        TRAILS.clear();
    }

    public record Sniff(UUID player, Mark mark) {
    }
}
