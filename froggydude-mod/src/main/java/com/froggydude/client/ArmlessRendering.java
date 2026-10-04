package com.froggydude.client;

import com.froggydude.init.ModEntityTypes;
import com.froggydude.network.ClientArmState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Jogador sem o braço esquerdo, no cliente:
 * - o braço (e a manga da skin) some do modelo;
 * - a manga da armadura do peitoral também some (camada de armadura trocada);
 * - no ombro aparece um toco vermelho com o osso;
 * - em primeira pessoa o braço esquerdo não é desenhado.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ArmlessRendering {

    private static final ResourceLocation STUMP =
            new ResourceLocation(ModEntityTypes.MOD_ID, "textures/entity/arm_stump.png");

    /** A Forge já chamou setModelProperties antes deste evento: o que mudamos aqui vale pro desenho. */
    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (!ClientArmState.isArmless(event.getEntity().getId())) return;
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        model.leftArm.visible = false;
        model.leftSleeve.visible = false;
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (!ClientArmState.isArmless(event.getEntity().getId())) return;
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        model.leftArm.visible = true;
        model.leftSleeve.visible = event.getEntity().isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
    }

    /** Primeira pessoa: sem braço esquerdo pra mostrar (vale pra quem é canhoto também). */
    @SubscribeEvent
    public static void onRenderArm(RenderArmEvent event) {
        if (event.getArm() == HumanoidArm.LEFT && ClientArmState.isArmless(event.getPlayer().getId())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientArmState.clear();
    }

    // ---------------------------------------------------------------- camadas (barramento do mod)

    /**
     * Troca a camada de armadura dos jogadores por uma que esconde a manga
     * esquerda do peitoral, e põe a camada do toco. Chamado pelo ClientModEvents.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        ModelManager models = event.getContext().getModelManager();
        for (String skin : event.getSkins()) {
            LivingEntityRenderer renderer = event.getSkin(skin);
            if (renderer == null) continue;
            try {
                List<RenderLayer> layers = ObfuscationReflectionHelper.getPrivateValue(
                        LivingEntityRenderer.class, renderer, "f_115291_");
                if (layers != null) {
                    for (int i = 0; i < layers.size(); i++) {
                        if (layers.get(i).getClass() == HumanoidArmorLayer.class) {
                            HumanoidArmorLayer old = (HumanoidArmorLayer) layers.get(i);
                            HumanoidModel inner = ObfuscationReflectionHelper.getPrivateValue(
                                    HumanoidArmorLayer.class, old, "f_117071_");
                            HumanoidModel outer = ObfuscationReflectionHelper.getPrivateValue(
                                    HumanoidArmorLayer.class, old, "f_117072_");
                            layers.set(i, new ArmlessArmorLayer(renderer, inner, outer, models));
                        }
                    }
                }
            } catch (RuntimeException e) {
                // se outro mod mexeu na armadura, só a manga do peitoral continua aparecendo
                System.err.println("[froggydude] não deu pra trocar a camada de armadura: " + e);
            }
            renderer.addLayer(new StumpLayer(renderer));
        }
    }

    /** Armadura que esconde a manga esquerda do peitoral de quem está sem braço. */
    private static class ArmlessArmorLayer
            extends HumanoidArmorLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>, HumanoidModel<AbstractClientPlayer>> {

        ArmlessArmorLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                          HumanoidModel<AbstractClientPlayer> inner, HumanoidModel<AbstractClientPlayer> outer,
                          ModelManager models) {
            super(parent, inner, outer, models);
        }

        @Override
        protected Model getArmorModelHook(AbstractClientPlayer entity, ItemStack stack, EquipmentSlot slot,
                                          HumanoidModel<AbstractClientPlayer> model) {
            Model result = super.getArmorModelHook(entity, stack, slot, model);
            if (slot == EquipmentSlot.CHEST && ClientArmState.isArmless(entity.getId())
                    && result instanceof HumanoidModel<?> humanoid) {
                humanoid.leftArm.visible = false;
            }
            return result;
        }
    }

    /** O toco no ombro esquerdo: carne vermelha com o osso na ponta. */
    private static class StumpLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

        StumpLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (!ClientArmState.isArmless(player.getId()) || player.isInvisible()) return;
            poseStack.pushPose();
            getParentModel().body.translateAndRotate(poseStack);
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(STUMP));
            // ombro esquerdo do modelo: x 4..7,5, y 0..3 (y cresce pra baixo), z -1,8..1,8
            box(vc, poseStack.last().pose(), poseStack.last().normal(), light,
                    4.0F / 16F, 7.6F / 16F, -0.2F / 16F, 3.0F / 16F, -1.8F / 16F, 1.8F / 16F);
            poseStack.popPose();
        }

        private static void box(VertexConsumer vc, Matrix4f m, Matrix3f n, int light,
                                float x0, float x1, float y0, float y1, float z0, float z1) {
            quad(vc, m, n, light, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1);
            quad(vc, m, n, light, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1);
            quad(vc, m, n, light, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, 0, -1, 0);
            quad(vc, m, n, light, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, 0, 1, 0);
            quad(vc, m, n, light, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0);
            quad(vc, m, n, light, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0);
        }

        private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, int light,
                                 float ax, float ay, float az, float bx, float by, float bz,
                                 float cx, float cy, float cz, float dx, float dy, float dz,
                                 float nx, float ny, float nz) {
            int ov = OverlayTexture.NO_OVERLAY;
            vc.vertex(m, ax, ay, az).color(1F, 1F, 1F, 1F).uv(0F, 0F).overlayCoords(ov).uv2(light)
                    .normal(n, nx, ny, nz).endVertex();
            vc.vertex(m, bx, by, bz).color(1F, 1F, 1F, 1F).uv(1F, 0F).overlayCoords(ov).uv2(light)
                    .normal(n, nx, ny, nz).endVertex();
            vc.vertex(m, cx, cy, cz).color(1F, 1F, 1F, 1F).uv(1F, 1F).overlayCoords(ov).uv2(light)
                    .normal(n, nx, ny, nz).endVertex();
            vc.vertex(m, dx, dy, dz).color(1F, 1F, 1F, 1F).uv(0F, 1F).overlayCoords(ov).uv2(light)
                    .normal(n, nx, ny, nz).endVertex();
        }
    }
}
