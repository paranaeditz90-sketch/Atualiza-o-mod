package com.becomelm;

import com.becomelm.network.LmNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
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

    // Morder e agarrar: no lugar da língua do Froggydude (o personagem do LM é um cachorro).
    // Galopando, atacar bem de perto = morde e segura na boca; andando, arrasta; atacar de
    // novo = sacode; pular = arremessa.
    public static final double GRAB_RANGE = 2.3D;
    public static final float GRAB_DAMAGE = 2F;
    public static final float SHAKE_DAMAGE = 1.5F;
    /** A sacudida: 0,8 s, uma mordida forte a cada ida e volta da cabeça (0,2 s). */
    public static final int SHAKE_TICKS = 16;
    public static final int SHAKE_EVERY = 4;
    /** Solta sozinho depois de 10 s. */
    public static final int GRAB_MAX = 200;
    /** A animação de jogar longe. */
    public static final int THROW_TICKS = 9;
    private static final double THROW_SPEED = 0.85D;
    private static final double THROW_UP = 0.42D;
    /** A boca de quem segura: na frente e rente ao chão (a pose de segurar abaixa a cara). */
    public static final double MOUTH = 1.05D;
    /** Deitado no monte: levanta sozinho depois de 1 min. */
    public static final int PILE_MAX = 1200;
    /** Segurando alguém na boca anda com 55% da velocidade. */
    private static final double DRAG_SLOW = -0.45D;
    private static final UUID DRAG_ID = UUID.fromString("5b2d7e19-0c4a-4f6e-9d83-1a7c2e4b6f05");

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
        /** Agarrado: quando começou a última sacudida, e há quantos ticks a vítima não acompanha. */
        int shakeAt = -1000;
        int stuck;
        /** No monte: o rumo do corpo deitado (graus x10, quem vê desenha com ele). */
        int data;

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
            case GRAB -> startGrab(p, targetId);
            case SHAKE -> shake(p);
            case THROW -> toss(p);
            case PILE -> startPile(p, targetId);
            case GET_UP -> {
                Act a = ACTS.get(p.getUUID());
                if (a != null && a.kind == LmAttack.PILE) end(p, a);
            }
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

    private static void startGrab(ServerPlayer p, int targetId) {
        if (!LmModes.get(p).fast() || !free(p)) return;
        Entity e = p.level().getEntity(targetId);
        if (!(e instanceof LivingEntity t) || !canHit(p, t, GRAB_RANGE + SLACK) || !LmKnock.canKnock(t)
                || holder(t) != null) return;
        ACTS.put(p.getUUID(), new Act(LmAttack.GRAB, t));
        drag(p, true);
        LmKnock.grab(t, p, p.getYRot());
        boolean hurt = hurtInPlace(p, t, GRAB_DAMAGE);
        LmNetwork.sendAct(p, LmAttack.GRAB, t.getId(), 0);
        LmNetwork.sendHit(p, t, LmAttack.GRAB, hurt, 0);
    }

    private static void shake(ServerPlayer p) {
        Act a = ACTS.get(p.getUUID());
        if (a == null || a.kind != LmAttack.GRAB || a.target == null || a.age - a.shakeAt < SHAKE_TICKS) return;
        a.shakeAt = a.age;
        LmNetwork.sendAct(p, LmAttack.SHAKE, a.target.getId(), a.age);
    }

    private static void toss(ServerPlayer p) {
        Act a = ACTS.get(p.getUUID());
        if (a != null && a.kind == LmAttack.GRAB) release(p, a, true);
    }

    /** Deita no monte de pedaços na frente (os pedaços são do cliente: o servidor só trava e conta). */
    private static void startPile(ServerPlayer p, int yawX10) {
        if (!LmModes.get(p).froggy() || !free(p)) return;
        pile(p, yawX10);
    }

    private static void pile(ServerPlayer p, int yawX10) {
        Act a = new Act(LmAttack.PILE, null);
        a.data = yawX10;
        ACTS.put(p.getUUID(), a);
        lock(p, true);
        LmNetwork.sendAct(p, LmAttack.PILE, yawX10, 0);
    }

    /**
     * Quanto o corpo de quem ele matou se espalha: montado em cima, pouco (o monte fica
     * embaixo dele, como no 10v1); na boca, médio; no bote, voa.
     */
    public static float breakPower(ServerPlayer killer) {
        Act a = ACTS.get(killer.getUUID());
        if (a == null) return 1.0F;
        return switch (a.kind) {
            case SMASH -> 0.35F;
            case GRAB -> 0.6F;
            default -> 1.0F;
        };
    }

    /** Quem está segurando esse na boca (ou null). */
    @Nullable
    private static UUID holder(LivingEntity t) {
        for (Map.Entry<UUID, Act> en : ACTS.entrySet()) {
            if (en.getValue().kind == LmAttack.GRAB && en.getValue().target == t) return en.getKey();
        }
        return null;
    }

    /**
     * Onde fica quem está sendo arrastado: {x, z, rumo do corpo}. A boca fica na frente de quem
     * segura. De costas: a cabeça da vítima na boca e o corpo esticado pra frente, cara pro céu.
     * De lado (bicho de quatro): virado pra quem segura, a cabeça na boca. "swing" = o balanço
     * da sacudida (graus, em volta da boca). Vale pros dois lados (o cliente desenha com isso).
     */
    public static double[] dragSpot(double hx, double hz, float holderYaw, float swing, LivingEntity victim,
                                    boolean back, int side) {
        float y = holderYaw * Mth.DEG_TO_RAD;
        double mx = hx - Mth.sin(y) * MOUTH, mz = hz + Mth.cos(y) * MOUTH;
        float body = holderYaw + swing;
        float b = body * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(b), fz = Mth.cos(b);
        if (back) {
            double eh = victim.getEyeHeight();
            return new double[] {mx + fx * eh, mz + fz * eh, body};
        }
        // de lado: as costas vão pra direita dele ("side") e a cabeça fica na frente do corpo,
        // meio de lado: os pés ficam pra trás e pro outro lado
        float vyaw = body + 180F;
        float v = vyaw * Mth.DEG_TO_RAD;
        double rx = -Mth.cos(v) * side, rz = -Mth.sin(v) * side;
        double fwd = victim.getBbWidth() * 0.6D, up = victim.getBbHeight() * 0.6D;
        return new double[] {mx + fx * fwd - rx * up, mz + fz * fwd - rz * up, vyaw};
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
            case GRAB -> tickGrab(p, a);
            case THROW -> {
                if (a.age >= THROW_TICKS) end(p, a);
            }
            case PILE -> {
                if (a.age >= PILE_MAX) end(p, a);
            }
            default -> end(p, a);
        }
    }

    private static void tickGrab(ServerPlayer p, Act a) {
        LivingEntity t = a.target;
        LmKnock.State ks = t == null ? null : LmKnock.get(t);
        if (t == null || ks == null || !t.isAlive() || t.isRemoved() || t.level() != p.level() || a.age > GRAB_MAX
                || !LmModes.get(p).fast() || LmTransform.active(p) || LmKnock.lying(p)) {
            release(p, a, false);
            return;
        }
        double[] spot = dragSpot(p.getX(), p.getZ(), p.getYRot(), 0F, t, ks.back, ks.side);
        LmKnock.steer(t, (float) spot[2], 10);
        double dx = spot[0] - t.getX(), dz = spot[1] - t.getZ();
        if (DEBUG && a.age % 20 == 0) {
            System.out.printf("[LMATK] segurando t=%d quem=%.2f,%.2f rumo=%.0f lugar=%.2f,%.2f vitima=%.2f,%.2f costas=%s%n",
                    a.age, p.getX(), p.getZ(), p.getYRot(), spot[0], spot[1], t.getX(), t.getZ(), ks.back);
        }
        // preso atrás de uma parede (ou o jogador vítima não acompanha): solta
        if (dx * dx + dz * dz > 9D) {
            if (++a.stuck > 10) {
                release(p, a, false);
                return;
            }
        } else {
            a.stuck = 0;
        }
        if (!(t instanceof Player)) {
            // arrastado com colisão (não atravessa parede nem entra no bloco); o jogador vítima
            // se arrasta no cliente dele
            t.move(MoverType.SELF, new Vec3(dx, 0D, dz));
            Vec3 v = t.getDeltaMovement();
            t.setDeltaMovement(0D, v.y, 0D);
        }
        int k = a.age - a.shakeAt;
        if (k > 0 && k <= SHAKE_TICKS && k % SHAKE_EVERY == 0) {
            boolean hurt = hurtInPlace(p, t, SHAKE_DAMAGE);
            LmNetwork.sendHit(p, t, LmAttack.SHAKE, hurt, k / SHAKE_EVERY);
        }
    }

    /** Solta quem estava na boca; arremessando, joga pra frente e faz a animação de jogar. */
    private static void release(ServerPlayer p, Act a, boolean toss) {
        ACTS.remove(p.getUUID());
        drag(p, false);
        LivingEntity t = a.target;
        if (t != null && t.isAlive() && !t.isRemoved()) {
            if (toss) {
                float y = p.getYRot() * Mth.DEG_TO_RAD;
                t.setDeltaMovement(-Mth.sin(y) * THROW_SPEED, THROW_UP, Mth.cos(y) * THROW_SPEED);
                t.hurtMarked = true;
                t.hasImpulse = true;
            }
            LmKnock.letGo(t, LmKnock.DOWN);
        }
        if (DEBUG) System.out.printf("[LMATK] solta t=%d arremesso=%s%n", a.age, toss);
        if (toss) {
            ACTS.put(p.getUUID(), new Act(LmAttack.THROW, null));
            LmNetwork.sendAct(p, LmAttack.THROW, -1, 0);
            if (t != null) LmNetwork.sendHit(p, t, LmAttack.THROW, false, 0);
        } else {
            LmNetwork.sendAct(p, null, -1, 0);
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
        if (t != null && t.isDeadOrDying() && t.level() == p.level()) {
            // matou montado: o corpo virou o monte embaixo dele e ele deita em cima (10v1 0:21)
            double dx = t.getX() - p.getX(), dz = t.getZ() - p.getZ();
            float yaw = dx * dx + dz * dz < 1.0E-4D ? p.getYRot() : (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
            pile(p, Math.round(Mth.wrapDegrees(yaw) * 10F));
            return;
        }
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
        if (a.kind == LmAttack.GRAB) {
            release(p, a, false);
            return;
        }
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
            int id = a.kind == LmAttack.PILE ? a.data : a.target == null ? -1 : a.target.getId();
            LmNetwork.sendActTo(viewer, p, a.kind, id, a.age);
        }
    }

    private static void drag(ServerPlayer p, boolean on) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) return;
        if (speed.getModifier(DRAG_ID) != null) speed.removeModifier(DRAG_ID);
        if (on) speed.addTransientModifier(new AttributeModifier(DRAG_ID, "BecomeLM arrastando", DRAG_SLOW,
                AttributeModifier.Operation.MULTIPLY_TOTAL));
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
