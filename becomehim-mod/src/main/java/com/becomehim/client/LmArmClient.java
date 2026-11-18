package com.becomehim.client;

import com.becomehim.BecomeHim;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;

/**
 * O braço arrancado no cliente: quem está sem o braço esquerdo não mostra ele (no corpo de jogador, em 1ª pessoa e
 * no boneco do FroggyDude, LmModel), e o braço na boca de quem arrancou (com a skin da vítima: braço fino/normal e
 * a manga certos), atravessado de um lado ao outro do rosto, que encurta enquanto ele come.
 */
@Mod.EventBusSubscriber(modid = BecomeHim.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LmArmClient {

    private static final Set<Integer> ARMLESS = new HashSet<>();

    private LmArmClient() {
    }

    public static void set(int entityId, boolean armless) {
        if (armless) ARMLESS.add(entityId);
        else ARMLESS.remove(entityId);
    }

    public static boolean isArmless(int entityId) {
        return ARMLESS.contains(entityId);
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!isArmless(event.getEntity().getId())) return;
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        model.leftArm.visible = false;
        model.leftSleeve.visible = false;
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (!isArmless(event.getEntity().getId())) return;
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        model.leftArm.visible = true;
        model.leftSleeve.visible = event.getEntity().isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
    }

    /** Primeira pessoa: sem braço esquerdo pra mostrar. */
    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        if (event.getArm() == HumanoidArm.LEFT && isArmless(event.getPlayer().getId())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ARMLESS.clear();
    }

    /**
     * O braço da vítima na boca. O poseStack está no osso da cabeça do FroggyDude (pixels do modelo): mouthY/mouthZ é
     * a boca. eaten = 0 (inteiro) a 1 (sumiu).
     */
    static void drawHeldArm(PoseStack ps, MultiBufferSource buffers, int light, int victimId, float eaten,
                            float mouthY, float mouthZ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(victimId) instanceof AbstractClientPlayer victim)) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(victim) instanceof PlayerRenderer pr)) return;
        PlayerModel<AbstractClientPlayer> model = pr.getModel();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(victim.getSkinTextureLocation()));
        boolean slim = "slim".equals(victim.getModelName());
        float k = Math.max(0.05F, 1F - eaten);
        ps.pushPose();
        ps.translate(0F, mouthY / 16F, mouthZ / 16F);
        ps.scale(-1F, -1F, 1F);
        // atravessado: o comprimento do braço vai de um lado ao outro do rosto
        ps.mulPose(Axis.ZP.rotationDegrees(90F));
        ps.mulPose(Axis.XP.rotationDegrees(8F));
        ps.scale(1F, k, 1F);
        ps.translate(-(slim ? 0.5F : 1F) / 16F, -4F / 16F, 0F);
        boolean v1 = model.leftArm.visible, v2 = model.leftSleeve.visible;
        model.leftArm.visible = true;
        model.leftSleeve.visible = true;
        float x = model.leftArm.x, y = model.leftArm.y, z = model.leftArm.z;
        float rx = model.leftArm.xRot, ry = model.leftArm.yRot, rz = model.leftArm.zRot;
        model.leftArm.setPos(0F, 0F, 0F);
        model.leftArm.setRotation(0F, 0F, 0F);
        model.leftSleeve.copyFrom(model.leftArm);
        model.leftArm.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
        model.leftSleeve.render(ps, vc, light, OverlayTexture.NO_OVERLAY);
        model.leftArm.setPos(x, y, z);
        model.leftArm.setRotation(rx, ry, rz);
        model.leftSleeve.copyFrom(model.leftArm);
        model.leftArm.visible = v1;
        model.leftSleeve.visible = v2;
        ps.popPose();
    }
}
