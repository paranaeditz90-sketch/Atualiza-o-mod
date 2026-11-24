package com.froggydude.player;

import com.froggydude.config.GoreLevel;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * No servidor: o nível de gore que cada jogador escolheu no jogo dele (o
 * cliente manda ao entrar e quando muda). Quem não mandou (servidor sem o mod
 * no cliente, por exemplo) conta como Pesado.
 */
public final class GorePrefs {

    private static final Map<UUID, GoreLevel> LEVELS = new ConcurrentHashMap<>();

    private GorePrefs() {
    }

    public static void set(Player player, GoreLevel level) {
        LEVELS.put(player.getUUID(), level);
    }

    public static GoreLevel of(Player player) {
        return LEVELS.getOrDefault(player.getUUID(), GoreLevel.HEAVY);
    }

    public static void forget(Player player) {
        LEVELS.remove(player.getUUID());
    }
}
