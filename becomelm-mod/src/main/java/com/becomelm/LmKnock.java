package com.becomelm;

import com.becomelm.network.LmNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.FlyingAnimal;

import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Derrubado: quem leva o golpe do FroggyDude cai de lado, fica no chão e levanta rolando
 * (medido nos vídeos: cai em ~0,13 s, fica ~1,4 s, levanta em ~0,45 s - o mesmo do
 * "knockdown" do Epic Fight). Vale pra mob e pra jogador (o amigo também cai).
 * O servidor conta o tempo e trava quem está no chão; quem vê recebe o estado e desenha.
 */
public final class LmKnock {

    /** Caindo de lado. */
    public static final int FALL = 3;
    /** No chão (o normal; os socos e a mordida seguram mais tempo). */
    public static final int DOWN = 28;
    /** Levantando rolando. */
    public static final int ROLL = 9;

    private static final UUID SLOW_ID = UUID.fromString("0c6f2d61-8f3a-4c55-b7a2-3e9b1d4c7a90");

    /** Um derrubado: idade em ticks, quanto tempo fica no chão, pra que lado caiu e o rumo do corpo. */
    public static final class State {
        public int age;
        public int down;
        public final int side;
        public final float yaw;

        public State(int age, int down, int side, float yaw) {
            this.age = age;
            this.down = down;
            this.side = side;
            this.yaw = yaw;
        }

        public int total() {
            return FALL + down + ROLL;
        }

        public boolean done() {
            return age >= total();
        }

        /** Ainda caindo ou deitado (o levantar rolando já solta o corpo). */
        public boolean lying() {
            return age < FALL + down;
        }

        /**
         * Quanto o corpo está tombado, em graus, de 0 (em pé) a 90 (deitado de lado); no
         * levantar continua girando pro mesmo lado até dar a volta (90 -> 360).
         */
        public float roll(float partial) {
            float t = age + partial;
            float a;
            if (t < FALL) {
                float k = t / FALL;
                a = 90F * k * k;
            } else if (t < FALL + down) {
                a = 90F;
            } else {
                float k = Mth.clamp((t - FALL - down) / ROLL, 0F, 1F);
                a = 90F + 270F * (k * k * (3F - 2F * k));
            }
            return a * side;
        }
    }

    private static final Map<LivingEntity, State> SERVER = new IdentityHashMap<>();

    private LmKnock() {
    }

    /** Dá pra derrubar? (bicho voador e chefão não caem de lado no ar). */
    public static boolean canKnock(LivingEntity e) {
        if (!e.isAlive() || e.isNoGravity() || e.isPassenger() || e.isVehicle()) return false;
        if (e instanceof EnderDragon || e instanceof WitherBoss || e instanceof Ghast || e instanceof Phantom
                || e instanceof Bat || e instanceof FlyingAnimal) return false;
        if (e instanceof ServerPlayer p && (p.isSpectator() || p.isCreative() || p.isFallFlying())) return false;
        return true;
    }

    /**
     * Servidor: derruba (ou, se já está no chão, segura lá pelo menos mais "down" ticks).
     * from = quem bateu (cai pro lado de fora do golpe).
     */
    public static void knock(LivingEntity victim, Entity from, int down) {
        if (victim.level().isClientSide() || !canKnock(victim)) return;
        State s = SERVER.get(victim);
        if (s != null && s.lying()) {
            int want = s.age + down - FALL;
            if (want > s.down) s.down = want;
        } else {
            int side = sideAwayFrom(victim, from);
            s = new State(0, down, side, victim.yBodyRot);
            SERVER.put(victim, s);
            slow(victim, true);
        }
        LmNetwork.sendKnock(victim, s);
    }

    public static State get(LivingEntity e) {
        return SERVER.get(e);
    }

    public static boolean lying(LivingEntity e) {
        State s = SERVER.get(e);
        return s != null && s.lying();
    }

    /** Servidor, todo tick do mundo: conta o tempo, segura no lugar e solta quem levantou. */
    public static void tick(ServerLevel level) {
        Iterator<Map.Entry<LivingEntity, State>> it = SERVER.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, State> en = it.next();
            LivingEntity e = en.getKey();
            if (e.level() != level) continue;
            State s = en.getValue();
            s.age++;
            if (!e.isAlive() || e.isRemoved() || s.done()) {
                slow(e, false);
                it.remove();
                continue;
            }
            hold(e, s);
        }
    }

    private static void hold(LivingEntity e, State s) {
        // o corpo não vira no chão (quem desenha tomba em volta do rumo da queda)
        e.setYRot(s.yaw);
        e.setYBodyRot(s.yaw);
        e.setYHeadRot(s.yaw);
        if (!s.lying()) return;
        if (e instanceof Mob m) {
            m.getNavigation().stop();
            m.setXxa(0F);
            m.setZza(0F);
            m.setJumping(false);
            if (m instanceof Creeper c) c.setSwellDir(-1);
        }
    }

    /** Sem andar enquanto está no chão (a física continua: o empurrão do golpe ainda leva). */
    private static void slow(LivingEntity e, boolean on) {
        AttributeInstance speed = e.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (speed.getModifier(SLOW_ID) != null) speed.removeModifier(SLOW_ID);
        if (on) speed.addTransientModifier(new AttributeModifier(SLOW_ID, "BecomeLM derrubado", -1D,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    /** Alguém passou a ver esse bicho: se está derrubado, conta pra ele. */
    public static void sendTo(ServerPlayer viewer, Entity target) {
        if (target instanceof LivingEntity e) {
            State s = SERVER.get(e);
            if (s != null) LmNetwork.sendKnockTo(viewer, e, s);
        }
    }

    public static void forget(Entity e) {
        if (e instanceof LivingEntity le && SERVER.remove(le) != null) slow(le, false);
    }

    public static void clear() {
        SERVER.clear();
    }

    /** O lado pra onde cai: pra longe de quem bateu (pela direita ou esquerda do corpo). */
    private static int sideAwayFrom(LivingEntity victim, Entity from) {
        if (from == null) return victim.getRandom().nextBoolean() ? 1 : -1;
        float yaw = victim.yBodyRot * Mth.DEG_TO_RAD;
        // direita do corpo
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);
        double dx = victim.getX() - from.getX(), dz = victim.getZ() - from.getZ();
        return dx * rx + dz * rz >= 0D ? 1 : -1;
    }
}
