package com.froggydude.player;

import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * No servidor: quem está na cutscene do golpe final (ou prestes a entrar) e
 * de qual FroggyDude. Enquanto está, nenhum outro dano entra (nem lava, nem
 * outro mob, nem flecha) - só a cabeça arrancada no fim.
 */
public final class FinisherTracker {

    private static final Map<UUID, Integer> VICTIMS = new ConcurrentHashMap<>();

    private FinisherTracker() {
    }

    public static void mark(Player victim, int froggyId) {
        VICTIMS.put(victim.getUUID(), froggyId);
    }

    public static boolean isVictim(Player p) {
        return VICTIMS.containsKey(p.getUUID());
    }

    public static int froggyOf(Player p) {
        return VICTIMS.getOrDefault(p.getUUID(), -1);
    }

    public static void clear(Player p) {
        VICTIMS.remove(p.getUUID());
    }

    public static void clearAll() {
        VICTIMS.clear();
    }
}
