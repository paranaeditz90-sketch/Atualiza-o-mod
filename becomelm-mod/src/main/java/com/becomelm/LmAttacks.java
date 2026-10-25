package com.becomelm;

import com.becomelm.network.LmNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Os golpes, do lado do servidor. O cliente de quem ataca escolhe o alvo e faz o
 * movimento (o corpo do jogador é dele); o servidor confere, espera o encontro e dá o
 * dano como golpe do próprio jogador (conta a morte pra ele, o bicho fica bravo com ele).
 */
public final class LmAttacks {

    /** Até onde o bote alcança (blocos). */
    public static final double POUNCE_RANGE = 7.0D;
    /** 3,5 corações. */
    public static final float POUNCE_DAMAGE = 7F;
    /** Tempo pra chegar no bicho depois de pular (o bote leva ~0,4 s). */
    private static final int POUNCE_TIME = 16;
    private static final int POUNCE_COOLDOWN = 10;
    /** A folga da conferência do servidor (o pacote chega com atraso). */
    private static final double SLACK = 2.0D;

    private static final class Act {
        final LmAttack kind;
        final LivingEntity target;
        int age;

        Act(LmAttack kind, LivingEntity target) {
            this.kind = kind;
            this.target = target;
        }
    }

    private static final Map<UUID, Act> ACTS = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();

    private LmAttacks() {
    }

    /** O cliente pediu um golpe nesse bicho. */
    public static void request(ServerPlayer p, LmAttack kind, int targetId) {
        if (kind == LmAttack.POUNCE) startPounce(p, targetId);
    }

    private static void startPounce(ServerPlayer p, int targetId) {
        if (LmModes.get(p) != LmMode.GALLOP || LmKnock.lying(p) || ACTS.containsKey(p.getUUID())) return;
        long now = p.level().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < POUNCE_COOLDOWN) return;
        Entity e = p.level().getEntity(targetId);
        if (!(e instanceof LivingEntity t) || !canHit(p, t, POUNCE_RANGE + SLACK)) return;
        ACTS.put(p.getUUID(), new Act(LmAttack.POUNCE, t));
        LAST.put(p.getUUID(), now);
    }

    /** Dá pra bater nesse? (vale pros dois lados: o cliente usa pra escolher o alvo). */
    public static boolean canTarget(Player p, LivingEntity t) {
        if (t == p || !t.isAlive() || t.isInvulnerable() || t instanceof ArmorStand) return false;
        if (t instanceof Player tp && (tp.isSpectator() || tp.isCreative())) return false;
        return !t.isPassengerOfSameVehicle(p);
    }

    private static boolean canHit(ServerPlayer p, LivingEntity t, double range) {
        if (!canTarget(p, t) || t.level() != p.level() || p.distanceToSqr(t) > range * range) return false;
        return !(t instanceof Player tp) || p.canHarmPlayer(tp);
    }

    /** Servidor, fim do tick de cada jogador. */
    public static void tick(ServerPlayer p) {
        Act a = ACTS.get(p.getUUID());
        if (a == null) return;
        a.age++;
        if (!p.isAlive() || !a.target.isAlive() || a.target.level() != p.level()) {
            ACTS.remove(p.getUUID());
            return;
        }
        if (a.kind == LmAttack.POUNCE) tickPounce(p, a);
    }

    private static void tickPounce(ServerPlayer p, Act a) {
        LivingEntity t = a.target;
        if (a.age > POUNCE_TIME) {
            ACTS.remove(p.getUUID());
            return;
        }
        AABB reach = p.getBoundingBox().inflate(0.6D, 0.5D, 0.6D);
        if (!reach.intersects(t.getBoundingBox())) return;
        ACTS.remove(p.getUUID());
        Vec3 d = new Vec3(t.getX() - p.getX(), 0D, t.getZ() - p.getZ());
        d = d.lengthSqr() < 1.0E-4D ? Vec3.directionFromRotation(0F, p.getYRot()) : d.normalize();
        boolean hurt = t.hurt(p.damageSources().playerAttack(p), POUNCE_DAMAGE);
        if (hurt) {
            t.knockback(0.8D, -d.x, -d.z);
            t.hurtMarked = true;
            p.setLastHurtMob(t);
            LmKnock.knock(t, p, LmKnock.DOWN);
        }
        LmNetwork.sendHit(p, t, LmAttack.POUNCE, hurt);
    }

    public static void forget(Player p) {
        ACTS.remove(p.getUUID());
        LAST.remove(p.getUUID());
    }

    public static void clear() {
        ACTS.clear();
        LAST.clear();
    }
}
