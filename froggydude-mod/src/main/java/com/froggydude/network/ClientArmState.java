package com.froggydude.network;

import java.util.HashSet;
import java.util.Set;

/**
 * No cliente: quais jogadores (por id de entidade) estão sem o braço esquerdo.
 * Sem classes de cliente do Minecraft aqui, então é seguro no servidor também.
 */
public final class ClientArmState {

    private static final Set<Integer> ARMLESS = new HashSet<>();

    private ClientArmState() {
    }

    public static void set(int entityId, boolean armless) {
        if (armless) ARMLESS.add(entityId);
        else ARMLESS.remove(entityId);
    }

    public static boolean isArmless(int entityId) {
        return ARMLESS.contains(entityId);
    }

    public static void clear() {
        ARMLESS.clear();
    }
}
