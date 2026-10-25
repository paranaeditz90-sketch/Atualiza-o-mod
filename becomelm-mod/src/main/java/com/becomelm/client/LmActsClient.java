package com.becomelm.client;

import com.becomelm.BecomeLM;
import com.becomelm.LmAttack;
import com.becomelm.LmAttacks;
import com.becomelm.LmKnock;
import com.becomelm.network.LmNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Os golpes longos de cada jogador (montado socando, de cara no chão, segurando na boca), do
 * lado de quem vê: a animação certa e, pro próprio jogador, ficar parado em cima da vítima até
 * acabar, ou arrastar quem está na boca.
 */
@Mod.EventBusSubscriber(modid = BecomeLM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LmActsClient {

    /** Um golpe longo: qual, em quem e há quantos ticks. */
    static final class Act {
        final LmAttack kind;
        final int target;
        int age;
        /** O próprio jogador já virou o olhar pra vítima. */
        boolean faced;
        /** O servidor já confirmou (o que o próprio jogador começou sozinho espera por isso). */
        boolean confirmed;
        /** Segurando na boca: há quantos ticks está sacudindo (-1 = não está). */
        int shake = -1;

        Act(LmAttack kind, int target, int age) {
            this.kind = kind;
            this.target = target;
            this.age = age;
        }
    }

    private static final Map<UUID, Act> ACTS = new HashMap<>();
    /** De costas: senta em cima do quadril dela, virado pra cabeça (os socos descem na cara). */
    private static final double SEAT = 0.5D;
    /** De lado (bicho de quatro): ajoelhado do lado da barriga, a essa distância do corpo. */
    private static final double BESIDE = 0.45D;
    /** O que o próprio jogador começou e o servidor não confirmou nesse tempo, não valeu. */
    private static final int CONFIRM = 20;
    /** O quanto quem está na boca balança pra cada lado na sacudida (graus). */
    private static final float SWING = 28F;

    private static boolean jumpHeld;

    private LmActsClient() {
    }

    /** Chegou do servidor (kind -1 = acabou). */
    public static void set(UUID player, int kind, int target, int age) {
        if (kind < 0) {
            ACTS.remove(player);
            return;
        }
        LmAttack k = LmAttack.byOrdinal(kind);
        if (k == LmAttack.SHAKE) {
            // a sacudida é um pedaço do "segurando na boca"
            Act g = ACTS.get(player);
            if (g == null || g.kind != LmAttack.GRAB || g.target != target) {
                g = new Act(LmAttack.GRAB, target, age);
                ACTS.put(player, g);
            }
            g.age = age;
            g.confirmed = true;
            // o próprio jogador já começou a dele: não volta do começo
            if (g.shake < 0 || g.shake > 4) g.shake = 0;
            return;
        }
        Act old = ACTS.get(player);
        if (old != null && old.kind == k && old.target == target) {
            old.age = age;
            old.confirmed = true;
            return;
        }
        Act a = new Act(k, target, age);
        a.confirmed = true;
        ACTS.put(player, a);
        Minecraft mc = Minecraft.getInstance();
        Player p = k == LmAttack.FACEPLANT && mc.level != null ? mc.level.getPlayerByUUID(player) : null;
        if (p != null) faceplantBurst(p);
    }

    /** O próprio jogador já começa (sem esperar o servidor): a animação sai no quadro do pouso. */
    static void startLocal(LocalPlayer p, LmAttack kind, int target) {
        ACTS.put(p.getUUID(), new Act(kind, target, 0));
        if (kind == LmAttack.FACEPLANT) faceplantBurst(p);
    }

    @Nullable
    static Act get(Player p) {
        return ACTS.get(p.getUUID());
    }

    static boolean busy(Player p) {
        return ACTS.containsKey(p.getUUID());
    }

    /** Montado socando ou de cara no chão: não anda nem bate. */
    static boolean locked(Player p) {
        Act a = ACTS.get(p.getUUID());
        return a != null && (a.kind == LmAttack.SMASH || a.kind == LmAttack.FACEPLANT);
    }

    /** Alguém na boca (anda arrastando). */
    static boolean holding(Player p) {
        Act a = ACTS.get(p.getUUID());
        return a != null && a.kind == LmAttack.GRAB;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTS.clear();
            return;
        }
        if (mc.isPaused()) return;
        ACTS.values().removeIf(a -> {
            a.age++;
            if (a.shake >= 0 && ++a.shake >= LmAttacks.SHAKE_TICKS) a.shake = -1;
            // sem notícia do servidor (ele não tem o mod, recusou, ou o pacote se perdeu): solta sozinho
            return (!a.confirmed && a.age > CONFIRM) || a.age > limit(a.kind);
        });
    }

    private static int limit(LmAttack kind) {
        return switch (kind) {
            case SMASH -> LmAttacks.SMASH_TICKS + 20;
            case FACEPLANT -> LmAttacks.FACEPLANT_TICKS + 10;
            case GRAB -> LmAttacks.GRAB_MAX + 20;
            case THROW -> LmAttacks.THROW_TICKS + 10;
            default -> 40;
        };
    }

    /** Montado: o próprio jogador fica no peito da vítima (antes de andar, todo tick). */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof LocalPlayer p)) return;
        Act a = ACTS.get(p.getUUID());
        if (a == null) {
            victimTick(p);
            return;
        }
        if (a.kind != LmAttack.SMASH && a.kind != LmAttack.FACEPLANT) return;
        Vec3 v = p.getDeltaMovement();
        p.setDeltaMovement(0D, Math.min(v.y, 0D), 0D);
        double[] s = seat(p, a);
        if (s == null) return;
        p.setPos(s[0], s[1], s[2]);
        float yaw = (float) s[3];
        if (!a.faced) {
            // olha pra cara dela lá embaixo (depois dá pra olhar em volta)
            a.faced = true;
            p.setYRot(yaw);
            p.yRotO = yaw;
            p.setYHeadRot(yaw);
            p.yHeadRotO = yaw;
            p.setXRot(Math.max(p.getXRot(), 35F));
            p.xRotO = p.getXRot();
        }
        p.setYBodyRot(yaw);
    }

    /** Eu sou a vítima: montado em mim, não saio debaixo; na boca de alguém, vou arrastado. */
    private static void victimTick(LocalPlayer me) {
        Player h = holder(me);
        if (h != null) {
            LmKnock.State ks = LmKnockClient.get(me);
            if (ks == null) return;
            double[] spot = LmAttacks.dragSpot(h.getX(), h.getZ(), h.getYRot(), 0F, me, ks.back, ks.side);
            // a velocidade que leva até lá neste tick (o jogo move com colisão: não entro na parede)
            Vec3 v = me.getDeltaMovement();
            me.setDeltaMovement(Mth.clamp(spot[0] - me.getX(), -1.5D, 1.5D), v.y,
                    Mth.clamp(spot[1] - me.getZ(), -1.5D, 1.5D));
            return;
        }
        if (mounted(me)) {
            Vec3 v = me.getDeltaMovement();
            me.setDeltaMovement(0D, v.y, 0D);
        }
    }

    /**
     * Onde quem está montado fica e pra onde olha: {x, y, z, rumo}. Usa o derrubado que veio do
     * servidor; antes dele chegar, a mesma conta que o servidor faz.
     */
    @Nullable
    private static double[] seat(Player p, Act a) {
        if (a.kind != LmAttack.SMASH) return null;
        Entity e = p.level().getEntity(a.target);
        if (!(e instanceof LivingEntity victim)) return null;
        LmKnock.State ks = LmKnockClient.get(victim);
        boolean back;
        Vec3 d;
        if (ks != null) {
            back = ks.back;
            d = LmKnockClient.headDir(ks, ks.yaw);
        } else if (LmKnock.upright(victim)) {
            back = true;
            float y = LmKnock.backYaw(victim, p) * Mth.DEG_TO_RAD;
            d = new Vec3(Mth.sin(y), 0D, -Mth.cos(y));
        } else {
            back = false;
            float y = victim.yBodyRot * Mth.DEG_TO_RAD;
            int side = LmKnock.sideAwayFrom(victim, p);
            d = new Vec3(-Mth.cos(y) * side, 0D, -Mth.sin(y) * side);
        }
        Vec3 at = victim.position();
        // de costas a cabeça dela vai pra "d" e ele senta no quadril; de lado as costas dela vão
        // pra "d" e ele fica do lado da barriga, virado pra ela
        Vec3 spot = back ? at.add(d.scale(SEAT)) : at.subtract(d.scale(victim.getBbWidth() * 0.5D + BESIDE));
        return new double[] {spot.x, at.y, spot.z, Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG - 90D};
    }

    /** Montado: o corpo virado pra vítima no desenho (o jogo vira o corpo pro olhar a cada tick). */
    static void faceBody(Player p) {
        Act a = ACTS.get(p.getUUID());
        if (a != null && a.kind == LmAttack.GRAB) {
            // segurando: o corpo no rumo do olhar (a boca aponta pra onde se olha)
            float yaw = p.getYRot();
            p.yBodyRot = yaw;
            p.yBodyRotO = p.yRotO;
            return;
        }
        double[] s = a == null ? null : seat(p, a);
        if (s == null) return;
        p.yBodyRot = (float) s[3];
        p.yBodyRotO = (float) s[3];
    }

    private static boolean mounted(LocalPlayer me) {
        for (Act a : ACTS.values()) {
            if (a.kind == LmAttack.SMASH && a.target == me.getId()) return true;
        }
        return false;
    }

    /** Quem está segurando esse na boca (ou null). */
    @Nullable
    static Player holder(Entity victim) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return null;
        for (Map.Entry<UUID, Act> en : ACTS.entrySet()) {
            Act a = en.getValue();
            if (a.kind == LmAttack.GRAB && a.target == victim.getId()) {
                Player p = mc.level.getPlayerByUUID(en.getKey());
                if (p != null) return p;
            }
        }
        return null;
    }

    /**
     * Quem está na boca de alguém: onde desenhar (os pés) e o rumo do corpo, {x, z, rumo}, a
     * partir de quem segura neste quadro (a posição que vem do servidor chega atrasada e o corpo
     * ia ficar pra trás da boca). Com o balanço da sacudida.
     */
    @Nullable
    static double[] heldSpot(LivingEntity victim, LmKnock.State ks, float partial) {
        Player h = holder(victim);
        if (h == null) return null;
        Act a = ACTS.get(h.getUUID());
        float swing = 0F;
        if (a != null && a.shake >= 0) {
            float t = (a.shake + partial) / LmAttacks.SHAKE_EVERY;
            swing = SWING * Mth.sin(t * Mth.TWO_PI);
        }
        double hx = Mth.lerp(partial, h.xo, h.getX()), hz = Mth.lerp(partial, h.zo, h.getZ());
        return LmAttacks.dragSpot(hx, hz, h.getViewYRot(partial), swing, victim, ks.back, ks.side);
    }

    /** Segurando, atacar: sacode (o próprio jogador já começa a animação). */
    static void localShake(LocalPlayer p) {
        Act a = ACTS.get(p.getUUID());
        // o mesmo clique que agarrou não sacode
        if (a == null || a.kind != LmAttack.GRAB || a.shake >= 0 || a.age < 4) return;
        a.shake = 0;
        LmNetwork.CHANNEL.sendToServer(new LmNetwork.Attack(LmAttack.SHAKE, a.target));
    }

    /** No meio de um golpe longo não anda, não pula e não bate (o golpe é que bate). */
    @SubscribeEvent
    public static void onInput(MovementInputUpdateEvent event) {
        Player p = event.getEntity();
        var in = event.getInput();
        // só o apertar conta (segurando o pulo desde antes de agarrar não arremessa na hora)
        boolean jumpPressed = in.jumping && !jumpHeld;
        jumpHeld = in.jumping;
        Act a = ACTS.get(p.getUUID());
        if (a != null && a.kind == LmAttack.GRAB && p instanceof LocalPlayer lp) {
            // segurando: anda (devagar, o servidor freia) e o pulo vira arremesso
            if (jumpPressed) {
                ACTS.put(lp.getUUID(), new Act(LmAttack.THROW, -1, 0));
                LmNetwork.CHANNEL.sendToServer(new LmNetwork.Attack(LmAttack.THROW, a.target));
            }
            in.jumping = false;
            return;
        }
        if (a != null && a.kind == LmAttack.THROW) {
            // o pulo que arremessou não vira pulo
            in.jumping = false;
            return;
        }
        if (!locked(p)) return;
        in.forwardImpulse = 0F;
        in.leftImpulse = 0F;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null || !busy(p)) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if (event.isAttack() && holding(p)) localShake(p);
    }

    /** De cara no chão: o chão estoura (terra/areia voando pra todo lado e a poeira em roda). */
    private static void faceplantBurst(Player p) {
        var level = p.level();
        var rnd = level.getRandom();
        float r = p.getYRot() * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(r), fz = Mth.cos(r);
        // o corpo cai pra frente: o meio dele fica ~0,8 bloco na frente dos pés
        double cx = p.getX() + fx * 0.8D, cz = p.getZ() + fz * 0.8D;
        for (int i = 0; i < 20; i++) {
            double a = i / 20D * Math.PI * 2D;
            double s = 0.12D + rnd.nextDouble() * 0.1D;
            level.addParticle(BecomeLM.DUST_PUFF.get(), cx + Math.cos(a) * 0.6D, p.getY() + 0.08D,
                    cz + Math.sin(a) * 0.6D, Math.cos(a) * s, 0.02D, Math.sin(a) * s);
        }
        BlockState below = p.getBlockStateOn();
        if (below.isAir() || (p == Minecraft.getInstance().player && LmCamera.active())) return;
        BlockParticleOption crumbs = new BlockParticleOption(ParticleTypes.BLOCK, below);
        for (int i = 0; i < 40; i++) {
            level.addParticle(crumbs, cx + (rnd.nextDouble() - 0.5D) * 1.6D, p.getY() + 0.15D,
                    cz + (rnd.nextDouble() - 0.5D) * 1.6D,
                    (rnd.nextDouble() - 0.5D) * 0.5D, 0.2D + rnd.nextDouble() * 0.35D, (rnd.nextDouble() - 0.5D) * 0.5D);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ACTS.clear();
        jumpHeld = false;
    }
}
