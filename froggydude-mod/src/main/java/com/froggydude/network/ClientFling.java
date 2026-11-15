package com.froggydude.network;

import java.util.HashMap;
import java.util.Map;

/**
 * (v0.7.33) No cliente: quem está voando mole do arremesso da língua (entityId -> quando começou, quanto dura e pra
 * onde voa). Sem classe de cliente do Minecraft aqui: o pacote chama isso só no cliente, mas a classe carrega nos dois.
 */
public final class ClientFling {

    public static final class Flight {
        public final long start;
        public final int ticks;
        public final float dx, dz;
        /** Já saiu do chão (antes disso o "chão" é o do gancho). */
        public boolean airborne;

        Flight(long start, int ticks, float dx, float dz) {
            this.start = start;
            this.ticks = ticks;
            this.dx = dx;
            this.dz = dz;
        }
    }

    private static final Map<Integer, Flight> FLYING = new HashMap<>();
    private static long clock;

    private ClientFling() {
    }

    public static void start(int entityId, int ticks, float dx, float dz) {
        FLYING.put(entityId, new Flight(clock, ticks, dx, dz));
    }

    public static Flight get(int entityId) {
        return FLYING.get(entityId);
    }

    /** Ticks desde que soltou (com a fração do quadro). */
    public static float age(Flight f, float partial) {
        return clock - f.start + partial;
    }

    /** Cópia dos ids (dá pra parar no meio da volta). */
    public static java.util.List<Integer> ids() {
        return new java.util.ArrayList<>(FLYING.keySet());
    }

    public static void stop(int entityId) {
        FLYING.remove(entityId);
    }

    public static void clientTick() {
        clock++;
        FLYING.values().removeIf(f -> clock - f.start > f.ticks);
    }

    public static void clear() {
        FLYING.clear();
    }
}
