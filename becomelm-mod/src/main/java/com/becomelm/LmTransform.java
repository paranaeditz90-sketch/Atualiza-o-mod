package com.becomelm;

import com.becomelm.network.LmNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A transformação (entrar na fase 2), do lado do servidor: contorção (a animação
 * "contort", 2,95 s, a mesma do Froggydude acertada com o vídeo), fica torto até a pessoa
 * andar (o grito entra na edição) e então joga a cabeça pra trás e arranca. Parado no
 * lugar o tempo todo. Só os estalos dos ossos tocam (sem os gemidos dele).
 */
public final class LmTransform {

    public enum Phase {
        /** Se contorcendo. */
        CONTORT(59),
        /** Torto, esperando a pessoa andar (até 15 s). */
        HOLD(300),
        /** A cabeça vai pra trás e ele arranca. */
        ROAR(10);

        public final int ticks;

        Phase(int ticks) {
            this.ticks = ticks;
        }

        public static Phase byOrdinal(int i) {
            Phase[] all = values();
            return i >= 0 && i < all.length ? all[i] : null;
        }
    }

    public static final class State {
        public Phase phase;
        public int age;

        public State(Phase phase, int age) {
            this.phase = phase;
            this.age = age;
        }
    }

    private static final UUID LOCK_ID = UUID.fromString("3d6c8f0a-91b2-4c3e-8a77-5b0e2f1d9c44");
    private static final Map<UUID, State> SERVER = new HashMap<>();

    private LmTransform() {
    }

    public static void start(ServerPlayer p) {
        State s = new State(Phase.CONTORT, 0);
        SERVER.put(p.getUUID(), s);
        lock(p, true);
        LmNetwork.sendTransform(p, s);
    }

    /** Trocou de jeito no meio (ou morreu): para tudo. */
    public static void cancel(ServerPlayer p) {
        if (SERVER.remove(p.getUUID()) == null) return;
        lock(p, false);
        LmNetwork.sendTransform(p, null);
    }

    /** A pessoa andou (ou pulou) enquanto estava torto: arranca. */
    public static void go(ServerPlayer p) {
        State s = SERVER.get(p.getUUID());
        if (s == null || s.phase != Phase.HOLD) return;
        s.phase = Phase.ROAR;
        s.age = 0;
        LmNetwork.sendTransform(p, s);
    }

    public static boolean active(Player p) {
        return SERVER.containsKey(p.getUUID());
    }

    public static void tick(ServerPlayer p) {
        State s = SERVER.get(p.getUUID());
        if (s == null) return;
        if (!p.isAlive() || LmModes.get(p) != LmMode.FRENZY) {
            cancel(p);
            return;
        }
        if (++s.age < s.phase.ticks) return;
        if (s.phase == Phase.ROAR) {
            SERVER.remove(p.getUUID());
            lock(p, false);
            LmNetwork.sendTransform(p, null);
            return;
        }
        s.phase = s.phase == Phase.CONTORT ? Phase.HOLD : Phase.ROAR;
        s.age = 0;
        LmNetwork.sendTransform(p, s);
    }

    /** Alguém passou a ver: se está no meio da transformação, conta em que pedaço. */
    public static void sendTo(ServerPlayer viewer, Entity target) {
        if (target instanceof ServerPlayer p) {
            State s = SERVER.get(p.getUUID());
            if (s != null) LmNetwork.sendTransformTo(viewer, p, s);
        }
    }

    public static void forget(Player p) {
        SERVER.remove(p.getUUID());
    }

    public static void clear() {
        SERVER.clear();
    }

    private static void lock(ServerPlayer p, boolean on) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (speed.getModifier(LOCK_ID) != null) speed.removeModifier(LOCK_ID);
        if (on) speed.addTransientModifier(new AttributeModifier(LOCK_ID, "BecomeLM transformação", -1D,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }
}
