package com.froggydude.client;

import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.network.ClientShakeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Efeitos de tela quando o Froggy acerta o jogador:
 * - tremor de câmera de verdade (sacode em vários eixos e some suave);
 * - derrubado e preso: deitado no chão, controles travados e a câmera presa
 *   no rosto do Froggy que está em cima. Não dá pra olhar pra outro lado.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientForgeEvents {

    /** Quanto a câmera anda em direção ao rosto dele a cada quadro (0 a 1). */
    private static final float LOOK_SNAP = 0.35F;

    private static boolean forcedPoseByUs = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ClientShakeHandler.clientTick();
        }
    }

    /** Deitado no chão enquanto preso (o servidor faz o mesmo do lado dele). */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !(event.player instanceof LocalPlayer player)) return;
        if (ClientShakeHandler.isKnockedDown()) {
            player.setForcedPose(Pose.SWIMMING);
            forcedPoseByUs = true;
            if (Boolean.getBoolean("froggydude.debug") && player.tickCount % 5 == 0) {
                System.out.println("[FROGGYDEBUG] preso: pose=" + player.getPose() + " olho=" + player.getEyeHeight()
                        + " pitch=" + player.getXRot());
            }
        } else if (forcedPoseByUs) {
            player.setForcedPose(null);
            forcedPoseByUs = false;
        }
    }

    /** Preso: ignora andar, pular e agachar. */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (!ClientShakeHandler.isPinned()) return;
        Input input = event.getInput();
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
        input.shiftKeyDown = false;
        input.forwardImpulse = 0F;
        input.leftImpulse = 0F;
    }

    /** Preso: a cada quadro a câmera é puxada pro rosto dele (depois do mouse). */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (event.phase != TickEvent.Phase.START || !ClientShakeHandler.isKnockedDown()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;
        Entity pinner = mc.level.getEntity(ClientShakeHandler.getPinnerId());
        if (pinner == null) return;

        Vec3 eye = player.getEyePosition(event.renderTickTime);
        Vec3 face = pinner instanceof FroggydudeEntity froggy
                ? froggy.getFacePosition(event.renderTickTime)
                : pinner.getEyePosition(event.renderTickTime);
        Vec3 d = face.subtract(eye);
        double flat = Math.sqrt(d.x * d.x + d.z * d.z);
        float wantYaw = (float) (Mth.atan2(d.z, d.x) * Mth.RAD_TO_DEG) - 90F;
        float wantPitch = (float) (-(Mth.atan2(d.y, flat) * Mth.RAD_TO_DEG));

        float yaw = player.getYRot() + Mth.wrapDegrees(wantYaw - player.getYRot()) * LOOK_SNAP;
        float pitch = Mth.clamp(player.getXRot() + (wantPitch - player.getXRot()) * LOOK_SNAP, -90F, 90F);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.setYHeadRot(yaw);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (Minecraft.getInstance().player == null) return;
        float partial = (float) event.getPartialTick();
        float amount = ClientShakeHandler.getShakeAmount(partial);
        if (amount <= 0F) return;

        float time = (Minecraft.getInstance().player.tickCount + partial) * 1.6F;
        event.setPitch(event.getPitch() + ClientShakeHandler.noise(0, time) * amount * 3.0F);
        event.setYaw(event.getYaw() + ClientShakeHandler.noise(1, time) * amount * 3.0F);
        // só um tiquinho de giro: o resto é sacudida
        event.setRoll(event.getRoll() + ClientShakeHandler.noise(2, time) * amount * 1.2F);
    }
}
