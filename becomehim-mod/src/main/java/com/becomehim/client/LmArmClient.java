package com.becomehim.client;

import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
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

    // ---------------------------------------------------------------- o toco no ombro

    private static final ResourceLocation WHITE = BecomeHim.id("textures/misc/white.png");
    /** A carne (as faces de fora um tom mais escuras) e a ponta do osso no meio do corte. */
    private static final int[] MEAT = {150, 18, 18}, MEAT_DARK = {105, 10, 10}, BONE = {232, 222, 200};

    /** A camada do toco no corpo de jogador (o FroggyDude desenha o dele no LmRenderer). */
    public static final class StumpLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

        public StumpLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                           float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
            if (player.isInvisible() || !isArmless(player.getId())) return;
            ps.pushPose();
            getParentModel().body.translateAndRotate(ps);
            // o ombro esquerdo do modelo de jogador (pixels): x 4..7,6, y -0,2..3 (pra baixo), z -1,8..1,8; o corte fica
            // pra baixo (y 3), onde o braço saía
            stump(ps, buffers, light, 4.0F, 7.6F, -0.2F, 3.0F, -1.8F, 1.8F, 3.0F);
            ps.popPose();
        }
    }

    /**
     * Um toco (caixa em pixels do modelo): carne vermelha, e na face do corte (y = cut) a ponta do osso no meio.
     */
    static void stump(PoseStack ps, MultiBufferSource buffers, int light, float x0, float x1, float y0, float y1,
                      float z0, float z1, float cut) {
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));
        PoseStack.Pose pose = ps.last();
        float a = x0 / 16F, b = x1 / 16F, c = y0 / 16F, d = y1 / 16F, e = z0 / 16F, f = z1 / 16F;
        // as 6 faces
        quad(vc, pose, light, MEAT_DARK, a, c, e, b, c, e, b, d, e, a, d, e);
        quad(vc, pose, light, MEAT_DARK, a, c, f, a, d, f, b, d, f, b, c, f);
        quad(vc, pose, light, MEAT_DARK, a, c, e, a, d, e, a, d, f, a, c, f);
        quad(vc, pose, light, MEAT, b, c, e, b, c, f, b, d, f, b, d, e);
        quad(vc, pose, light, MEAT, a, c, e, a, c, f, b, c, f, b, c, e);
        quad(vc, pose, light, MEAT, a, d, e, b, d, e, b, d, f, a, d, f);
        // a ponta do osso, um tico pra fora do corte
        float cx = (a + b) / 2F, cz = (e + f) / 2F, r = Math.min(b - a, f - e) * 0.22F;
        float y = cut / 16F + (cut >= (y0 + y1) / 2F ? 0.002F : -0.002F);
        quad(vc, pose, light, BONE, cx - r, y, cz - r, cx + r, y, cz - r, cx + r, y, cz + r, cx - r, y, cz + r);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose, int light, int[] col, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, float x4, float y4, float z4) {
        float[][] v = {{x1, y1, z1}, {x2, y2, z2}, {x3, y3, z3}, {x4, y4, z4}};
        for (float[] p : v) {
            vc.vertex(pose.pose(), p[0], p[1], p[2]).color(col[0], col[1], col[2], 255).uv(0.5F, 0.5F)
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.normal(), 0F, 1F, 0F).endVertex();
        }
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
