package com.becomehim.client;

import com.becomehim.LmAttacks;
import com.becomehim.LmAttack;
import com.becomehim.network.LmNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * (roda) Grudado no teto, do lado do próprio jogador (o corpo dele é dele): sobe até o teto logo acima (até 4 blocos)
 * e fica de cabeça pra baixo (ceiling_hang, a tocaia do mod da IA). Atacar = se joga em cima do bicho da mira (o bote,
 * caindo de cima: ceiling_dive); pular ou agachar = solta.
 *
 * A cabeça fica encostada no teto: assim o servidor não acha que ele está voando (o "voo" só conta sem bloco nenhum a
 * 1/16 de bloco da caixa).
 */
final class LmCeilingClient {

    /** Até onde procura teto em cima da cabeça. */
    private static final int REACH = 4;
    /** Folga entre o topo da caixa e o teto (menos que 1/16, senão conta como voo). */
    private static final double GAP = 0.02D;
    /** Até onde o mergulho alcança (blocos). */
    static final double DIVE_RANGE = 9.0D;

    private static double hangY = Double.NaN;

    static boolean hanging() {
        return !Double.isNaN(hangY);
    }

    private LmCeilingClient() {
    }

    /** Tocou no gomo: sobe pro teto, se tiver. */
    static void start(LocalPlayer p) {
        Minecraft mc = Minecraft.getInstance();
        BlockPos head = BlockPos.containing(p.getX(), p.getBoundingBox().maxY, p.getZ());
        for (int i = 0; i <= REACH; i++) {
            BlockPos b = head.above(i);
            if (p.level().getBlockState(b).isAir()) continue;
            AABB solid = p.level().getBlockState(b).getCollisionShape(p.level(), b).isEmpty() ? null
                    : p.level().getBlockState(b).getCollisionShape(p.level(), b).bounds().move(b);
            if (solid == null) continue;
            double y = solid.minY - p.getBbHeight() - GAP;
            if (y < p.getY() - 0.01D) break;
            AABB moved = p.getBoundingBox().move(0D, y - p.getY(), 0D);
            if (!p.level().noCollision(p, moved.expandTowards(0D, p.getY() - y, 0D))) break;
            if (Boolean.getBoolean("becomehim.debug")) System.out.printf("[LMATK] teto em %s, pendura em y=%.2f%n", b, y);
            hangY = y;
            p.setPos(p.getX(), y, p.getZ());
            p.setDeltaMovement(Vec3.ZERO);
            p.setNoGravity(true);
            p.setOnGround(false);
            LmActsClient.startLocal(p, LmAttack.CEILING, -1);
            LmNetwork.CHANNEL.sendToServer(new LmNetwork.Attack(LmAttack.CEILING, -1));
            return;
        }
        if (Boolean.getBoolean("becomehim.debug")) System.out.printf("[LMATK] sem teto em cima de %s%n", head);
        mc.gui.setOverlayMessage(Component.translatable("becomehim.ceiling.none"), false);
    }

    /** Todo tick, pendurado: parado lá em cima. */
    static void hold(LocalPlayer p) {
        if (Double.isNaN(hangY)) hangY = p.getY();
        p.setNoGravity(true);
        p.setDeltaMovement(Vec3.ZERO);
        p.setPos(p.getX(), hangY, p.getZ());
        p.fallDistance = 0F;
        // parado lá em cima o jogo não recalcula o "no chão": ficava o de antes de subir e o servidor achava que ele
        // tinha caído (e soltava)
        p.setOnGround(false);
    }

    /** Solta (pulo, agachar, ou acabou do lado do servidor). */
    static void drop(LocalPlayer p, boolean tell) {
        hangY = Double.NaN;
        p.setNoGravity(false);
        if (tell) LmNetwork.CHANNEL.sendToServer(new LmNetwork.Attack(LmAttack.CEILING, -1));
    }

    /**
     * O bicho lá embaixo pro mergulho: o mais no meio do olhar (em 3D, olhando pra baixo vale) e mais perto. O alvo
     * normal (pickTarget) ignora quem está mais de 3,5 blocos abaixo e não acha nada olhando reto pra baixo.
     */
    @javax.annotation.Nullable
    private static LivingEntity diveTarget(LocalPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1F);
        LivingEntity best = null;
        double bestScore = -1.0E9D;
        for (var e : p.level().getEntities(p, p.getBoundingBox().inflate(DIVE_RANGE), x -> x instanceof LivingEntity)) {
            LivingEntity le = (LivingEntity) e;
            if (!LmAttacks.canTarget(p, le)) continue;
            Vec3 to = le.position().add(0D, le.getBbHeight() * 0.5D, 0D).subtract(eye);
            double dist = to.length();
            if (dist > DIVE_RANGE || dist < 0.05D) continue;
            double dot = to.scale(1D / dist).dot(look);
            if (dot < 0.75D || !p.hasLineOfSight(le)) continue;
            double score = dot * 2D - dist / DIVE_RANGE;
            if (score > bestScore) {
                bestScore = score;
                best = le;
            }
        }
        return best;
    }

    /** Atacou pendurado: se joga no bicho da mira (sem bicho, só solta). */
    static void dive(LocalPlayer p) {
        Minecraft mc = Minecraft.getInstance();
        LivingEntity t = diveTarget(p);
        hangY = Double.NaN;
        p.setNoGravity(false);
        if (t == null) {
            drop(p, true);
            LmActsClient.stopLocal(p);
            return;
        }
        Vec3 d = t.position().subtract(p.position());
        Vec3 dirH = new Vec3(d.x, 0D, d.z);
        double dist = dirH.length();
        dirH = dist < 1.0E-3D ? Vec3.directionFromRotation(0F, p.getYRot()) : dirH.scale(1D / dist);
        // cai e anda: chega lá mais ou menos junto com o chão
        double v = Mth.clamp(dist / 6.0D, 0.15D, 1.2D);
        p.setDeltaMovement(dirH.x * v, -0.2D, dirH.z * v);
        p.hasImpulse = true;
        p.setYBodyRot((float) (Mth.atan2(dirH.z, dirH.x) * Mth.RAD_TO_DEG) - 90F);
        LmActsClient.startLocal(p, LmAttack.CEILING_DIVE, t.getId());
        LmNetwork.CHANNEL.sendToServer(new LmNetwork.Attack(LmAttack.CEILING_DIVE, t.getId()));
    }
}
