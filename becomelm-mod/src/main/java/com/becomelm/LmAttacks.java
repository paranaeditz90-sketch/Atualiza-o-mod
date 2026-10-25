package com.becomelm;

import com.becomelm.network.LmNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
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
    /** Na fase 2 tudo bate mais forte (igual ao Froggydude). */
    public static final float FRENZY_DAMAGE = 1.6F;
    /** Tempo pra chegar no bicho depois de pular (o bote leva ~0,4 s). */
    private static final int POUNCE_TIME = 16;
    private static final int POUNCE_COOLDOWN = 10;
    /** A folga da conferência do servidor (o pacote chega com atraso). */
    private static final double SLACK = 2.0D;

    // Cair do céu + socos (10v1 0:12-0:17, 60 qps): o impacto derruba, 8 socos de 0,25 s
    // alternando os punhos, e emenda as mordidas no mesmo ritmo (a cabeça mergulha a cada 0,24 s).
    /** Até onde do bicho ele pode cair pra montar (blocos, na horizontal). */
    public static final double SMASH_REACH = 2.5D;
    public static final float SMASH_IMPACT = 4F;
    public static final float PUNCH_DAMAGE = 2F;
    public static final float BITE_DAMAGE = 2F;
    public static final int SMASH_EVERY = 5;
    public static final int SMASH_FIRST = 4;
    public static final int PUNCHES = 8;
    public static final int BITES = 5;
    /** As mordidas começam com um respiro depois do último soco. */
    public static final int BITE_FROM = SMASH_FIRST + PUNCHES * SMASH_EVERY + 2;
    public static final int SMASH_TICKS = BITE_FROM + BITES * SMASH_EVERY + 2;
    /** Errou: de cara no chão (a animação "faceplant", 1,7 s). */
    public static final int FACEPLANT_TICKS = 34;
    /** Até quanto tempo depois do pulo alto ainda vale cair em cima (ou de cara). */
    private static final int SKY_WINDOW = 120;

    private static final UUID LOCK_ID = UUID.fromString("9a4e1f32-5c7d-4b8e-a0f6-2d3c4b5a6e71");
    private static final boolean DEBUG = Boolean.getBoolean("becomelm.debug");

    private static final class Act {
        final LmAttack kind;
        @Nullable
        final LivingEntity target;
        /** Onde a vítima caiu (montado, ela não sai dali). */
        @Nullable
        final Vec3 at;
        int age;

        Act(LmAttack kind, @Nullable LivingEntity target) {
            this.kind = kind;
            this.target = target;
            this.at = target == null ? null : target.position();
        }
    }

    private static final Map<UUID, Act> ACTS = new HashMap<>();
    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final Map<UUID, Long> SKY = new HashMap<>();

    private LmAttacks() {
    }

    /** O cliente avisou que deu o pulo alto (o pouso dele pode virar queda em cima de alguém). */
    public static void skyJumped(ServerPlayer p) {
        SKY.put(p.getUUID(), p.level().getGameTime());
    }

    /** O cliente pediu um golpe nesse bicho. */
    public static void request(ServerPlayer p, LmAttack kind, int targetId) {
        switch (kind) {
            case POUNCE -> startPounce(p, targetId);
            case SMASH -> startSmash(p, targetId);
            case FACEPLANT -> startFaceplant(p);
            default -> {
            }
        }
    }

    private static boolean free(ServerPlayer p) {
        return LmModes.get(p).froggy() && !LmTransform.active(p) && !LmKnock.lying(p) && !ACTS.containsKey(p.getUUID());
    }

    private static boolean skyRecent(ServerPlayer p) {
        Long t = SKY.get(p.getUUID());
        return t != null && p.level().getGameTime() - t < SKY_WINDOW;
    }

    private static void startPounce(ServerPlayer p, int targetId) {
        if (!LmModes.get(p).fast() || !free(p)) return;
        long now = p.level().getGameTime();
        Long last = LAST.get(p.getUUID());
        if (last != null && now - last < POUNCE_COOLDOWN) return;
        Entity e = p.level().getEntity(targetId);
        if (!(e instanceof LivingEntity t) || !canHit(p, t, POUNCE_RANGE + SLACK)) return;
        ACTS.put(p.getUUID(), new Act(LmAttack.POUNCE, t));
        LAST.put(p.getUUID(), now);
    }

    private static void startSmash(ServerPlayer p, int targetId) {
        if (DEBUG) System.out.printf("[LMATK] pediu esmagar %d livre=%s ceu=%s%n", targetId, free(p), skyRecent(p));
        if (!free(p) || !skyRecent(p)) return;
        Entity e = p.level().getEntity(targetId);
        if (!(e instanceof LivingEntity t) || !canHit(p, t, SMASH_REACH + SLACK + 1.0D)) return;
        SKY.remove(p.getUUID());
        Act a = new Act(LmAttack.SMASH, t);
        ACTS.put(p.getUUID(), a);
        lock(p, true);
        if (LmKnock.upright(t)) LmKnock.knockBack(t, p, SMASH_TICKS + 6);
        else LmKnock.knock(t, p, SMASH_TICKS + 6);
        boolean hurt = hurtInPlace(p, t, SMASH_IMPACT);
        LmNetwork.sendAct(p, LmAttack.SMASH, t.getId(), 0);
        LmNetwork.sendHit(p, t, LmAttack.SMASH, hurt, 0);
    }

    private static void startFaceplant(ServerPlayer p) {
        if (!free(p) || !skyRecent(p)) return;
        SKY.remove(p.getUUID());
        ACTS.put(p.getUUID(), new Act(LmAttack.FACEPLANT, null));
        lock(p, true);
        LmNetwork.sendAct(p, LmAttack.FACEPLANT, -1, 0);
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
        if (!p.isAlive()) {
            end(p, a);
            return;
        }
        switch (a.kind) {
            case POUNCE -> tickPounce(p, a);
            case SMASH -> tickSmash(p, a);
            case FACEPLANT -> {
                if (a.age >= FACEPLANT_TICKS) end(p, a);
            }
            default -> end(p, a);
        }
    }

    private static void tickPounce(ServerPlayer p, Act a) {
        LivingEntity t = a.target;
        if (t == null || !t.isAlive() || t.level() != p.level() || a.age > POUNCE_TIME) {
            ACTS.remove(p.getUUID());
            return;
        }
        AABB reach = p.getBoundingBox().inflate(0.6D, 0.5D, 0.6D);
        if (!reach.intersects(t.getBoundingBox())) return;
        ACTS.remove(p.getUUID());
        Vec3 d = new Vec3(t.getX() - p.getX(), 0D, t.getZ() - p.getZ());
        d = d.lengthSqr() < 1.0E-4D ? Vec3.directionFromRotation(0F, p.getYRot()) : d.normalize();
        boolean hurt = t.hurt(p.damageSources().playerAttack(p), POUNCE_DAMAGE * mul(p));
        if (hurt) {
            t.knockback(0.8D, -d.x, -d.z);
            t.hurtMarked = true;
            p.setLastHurtMob(t);
            LmKnock.knock(t, p, LmKnock.DOWN);
        }
        LmNetwork.sendHit(p, t, LmAttack.POUNCE, hurt, 0);
    }

    private static void tickSmash(ServerPlayer p, Act a) {
        LivingEntity t = a.target;
        if (t == null || !t.isAlive() || t.isRemoved() || t.level() != p.level() || p.distanceToSqr(t) > 25D) {
            if (DEBUG) System.out.printf("[LMATK] fim do esmagamento t=%d vivo=%s dist=%.2f%n", a.age,
                    t != null && t.isAlive(), t == null ? -1D : Math.sqrt(p.distanceToSqr(t)));
            end(p, a);
            return;
        }
        // a vítima fica embaixo dele (o empurrão entre os corpos ia tirar ela de lá); o jogador
        // vítima se segura no cliente dele
        Vec3 v = t.getDeltaMovement();
        t.setDeltaMovement(0D, Math.min(v.y, 0D), 0D);
        if (!(t instanceof Player) && a.at != null) t.setPos(a.at.x, t.getY(), a.at.z);
        int k = a.age - SMASH_FIRST;
        if (k >= 0 && k % SMASH_EVERY == 0 && k / SMASH_EVERY < PUNCHES) {
            boolean hurt = hurtInPlace(p, t, PUNCH_DAMAGE);
            LmNetwork.sendHit(p, t, LmAttack.PUNCH, hurt, k / SMASH_EVERY);
        }
        int b = a.age - BITE_FROM;
        if (b >= 0 && b % SMASH_EVERY == 0 && b / SMASH_EVERY < BITES) {
            boolean hurt = hurtInPlace(p, t, BITE_DAMAGE);
            LmNetwork.sendHit(p, t, LmAttack.BITE, hurt, b / SMASH_EVERY);
        }
        if (a.age >= SMASH_TICKS) end(p, a);
    }

    /** Dano sem o empurrão (a vítima está presa embaixo) e sem a pausa de invencibilidade entre golpes. */
    private static boolean hurtInPlace(ServerPlayer p, LivingEntity t, float dmg) {
        Vec3 v = t.getDeltaMovement();
        t.invulnerableTime = 0;
        boolean hurt = t.hurt(p.damageSources().playerAttack(p), dmg * mul(p));
        t.setDeltaMovement(v);
        if (hurt) p.setLastHurtMob(t);
        return hurt;
    }

    private static float mul(ServerPlayer p) {
        return LmModes.get(p) == LmMode.FRENZY ? FRENZY_DAMAGE : 1F;
    }

    private static void end(ServerPlayer p, Act a) {
        if (DEBUG) System.out.printf("[LMATK] fim %s t=%d%n", a.kind, a.age);
        ACTS.remove(p.getUUID());
        if (a.kind == LmAttack.POUNCE) return;
        lock(p, false);
        LmNetwork.sendAct(p, null, -1, 0);
    }

    /** Alguém passou a ver esse jogador no meio de um golpe: conta qual. */
    public static void sendTo(ServerPlayer viewer, Entity target) {
        if (!(target instanceof ServerPlayer p)) return;
        Act a = ACTS.get(p.getUUID());
        if (a != null && a.kind != LmAttack.POUNCE) {
            LmNetwork.sendActTo(viewer, p, a.kind, a.target == null ? -1 : a.target.getId(), a.age);
        }
    }

    private static void lock(ServerPlayer p, boolean on) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (speed.getModifier(LOCK_ID) != null) speed.removeModifier(LOCK_ID);
        if (on) speed.addTransientModifier(new AttributeModifier(LOCK_ID, "BecomeLM golpe", -1D,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    public static void forget(Player p) {
        ACTS.remove(p.getUUID());
        LAST.remove(p.getUUID());
        SKY.remove(p.getUUID());
    }

    public static void clear() {
        ACTS.clear();
        LAST.clear();
        SKY.clear();
    }
}
