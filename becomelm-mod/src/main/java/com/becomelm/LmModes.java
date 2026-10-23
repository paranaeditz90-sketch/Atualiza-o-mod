package com.becomelm;

import com.becomelm.network.LmNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * O modo de cada jogador. No servidor fica salvo no próprio jogador (sobrevive a sair e
 * voltar); no cliente, num mapa por UUID que o servidor mantém atualizado.
 */
public final class LmModes {

    private static final String KEY = "becomelm_mode";
    private static final Map<UUID, LmMode> CLIENT = new ConcurrentHashMap<>();

    private LmModes() {
    }

    public static LmMode get(Player player) {
        if (player.level().isClientSide()) return CLIENT.getOrDefault(player.getUUID(), LmMode.HUMAN);
        return LmMode.byId(player.getPersistentData().getString(KEY));
    }

    /** Servidor: troca, salva e avisa todo mundo que vê esse jogador (e ele mesmo). */
    public static void set(ServerPlayer player, LmMode mode) {
        player.getPersistentData().putString(KEY, mode.id);
        LmMovement.tick(player, mode);
        LmNetwork.sendMode(player, mode);
    }

    /** Morreu/trocou de mundo: o jogador novo herda o modo do antigo. */
    public static void copy(Player from, Player to) {
        String mode = from.getPersistentData().getString(KEY);
        if (!mode.isEmpty()) to.getPersistentData().putString(KEY, mode);
    }

    public static void setClient(UUID id, LmMode mode) {
        if (mode == LmMode.HUMAN) CLIENT.remove(id);
        else CLIENT.put(id, mode);
    }

    public static void clearClient() {
        CLIENT.clear();
    }
}
