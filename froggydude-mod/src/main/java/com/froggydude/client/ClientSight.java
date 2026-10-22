package com.froggydude.client;

import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.network.ModNetwork;
import com.froggydude.network.SightPacket;
import com.froggydude.player.PlayerSight;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.Comparator;

/**
 * O que está na tela (IA Real): 4 vezes por segundo, com um FroggyDude a até 64
 * blocos, confere se ele aparece na imagem de verdade - dentro do campo de visão
 * (o FOV e o formato da janela do jogo), sem bloco no meio (vidro e mato deixam
 * ver; folha e parede, não) e, de longe, sem estar escondido no breu (conta o
 * brilho escolhido e a visão noturna) - e manda pro servidor (SightPacket).
 */
public final class ClientSight {

    private static final double RANGE = 64.0D;

    private ClientSight() {
    }

    public static void clientTick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null || mc.player.tickCount % 5 != 0) return;
        FroggydudeEntity froggy = mc.level.getEntitiesOfClass(FroggydudeEntity.class,
                        mc.player.getBoundingBox().inflate(RANGE), e -> e.isAlive())
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(mc.player))).orElse(null);
        if (froggy == null) return;
        int flags = 0;
        if (mc.screen != null) flags |= SightPacket.SCREEN;
        if (mc.player.isUsingItem()) flags |= SightPacket.USING;
        if (mc.gameMode != null && mc.gameMode.isDestroying()) flags |= SightPacket.MINING;
        ModNetwork.CHANNEL.sendToServer(new SightPacket(froggy.getId(), onScreen(mc, froggy), flags));
    }

    /** Algum pedaço dele aparece na imagem? */
    static boolean onScreen(Minecraft mc, FroggydudeEntity froggy) {
        Camera cam = mc.gameRenderer.getMainCamera();
        if (!cam.isInitialized()) return false;
        Vec3 eye = cam.getPosition();
        AABB box = froggy.getBoundingBox().deflate(0.08D);
        double dist = eye.distanceTo(box.getCenter());
        boolean blind = mc.player.hasEffect(MobEffects.BLINDNESS) || mc.player.hasEffect(MobEffects.DARKNESS);
        if (dist > 6.0D && (blind || tooDark(mc, froggy))) return false;

        Vector3f look = cam.getLookVector();
        Vector3f up = cam.getUpVector();
        Vector3f left = cam.getLeftVector();
        double fov = mc.options.fov().get() * (1.0D + (mc.player.getFieldOfViewModifier() - 1.0D) * mc.options.fovEffectScale().get());
        // um fiozinho na beirada da tela não conta
        double tanV = Math.tan(Math.toRadians(fov / 2.0D)) * 0.95D;
        double tanH = tanV * mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        Vec3[] points = {
                box.getCenter(), froggy.getEyePosition(), new Vec3(box.getCenter().x, box.minY + 0.2D, box.getCenter().z),
                new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ), new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.minX, box.maxY, box.maxZ), new Vec3(box.maxX, box.maxY, box.maxZ)};
        for (Vec3 p : points) {
            Vec3 v = p.subtract(eye);
            double z = v.x * look.x() + v.y * look.y() + v.z * look.z();
            if (z <= 0.05D) continue;
            double x = (v.x * left.x() + v.y * left.y() + v.z * left.z()) / z;
            double y = (v.x * up.x() + v.y * up.y() + v.z * up.z()) / z;
            if (Math.abs(x) > tanH || Math.abs(y) > tanV) continue;
            HitResult hit = mc.level.clip(new ClipContext(eye, p, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() == HitResult.Type.MISS) return true;
        }
        return false;
    }

    /** Ele no breu: com o brilho no máximo dá pra ver mais no escuro. */
    private static boolean tooDark(Minecraft mc, FroggydudeEntity froggy) {
        if (mc.player.hasEffect(MobEffects.NIGHT_VISION)) return false;
        int light = mc.level.getMaxLocalRawBrightness(BlockPos.containing(froggy.getEyePosition()));
        return light <= PlayerSight.DARK - (int) Math.round(mc.options.gamma().get() * 2.0D);
    }
}
