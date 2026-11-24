package com.froggydude.client;

import com.froggydude.client.cutscene.Stumps;
import com.froggydude.client.cutscene.VictimRender;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.network.ClientArmState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.PlayerItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

import java.util.List;

/**
 * Jogador sem o braço esquerdo, no cliente:
 * - o braço (e a manga da skin) some do modelo;
 * - a manga da armadura do peitoral também some (camada de armadura trocada);
 * - no ombro aparece um toco vermelho com o osso;
 * - em primeira pessoa o braço esquerdo não é desenhado;
 * - o item da mão que não existe mais não fica boiando.
 * No golpe final (client.cutscene) vale o mesmo pro braço direito e a cabeça.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ArmlessRendering {

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
                        } else if (layers.get(i).getClass() == PlayerItemInHandLayer.class) {
                            layers.set(i, new MissingArmItemLayer(renderer, event.getContext().getItemInHandRenderer()));
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
            if (!(result instanceof HumanoidModel<?> humanoid)) return result;
            int miss = VictimRender.missing(entity, Minecraft.getInstance().getFrameTime());
            if (slot == EquipmentSlot.CHEST) {
                if (ClientArmState.isArmless(entity.getId()) || (miss & 1) != 0) humanoid.leftArm.visible = false;
                if ((miss & 2) != 0) humanoid.rightArm.visible = false;
            } else if (slot == EquipmentSlot.HEAD && (miss & 4) != 0) {
                // capacete sem cabeça dentro: não
                humanoid.head.visible = false;
                humanoid.hat.visible = false;
            }
            return result;
        }
    }

    /** Os tocos: ombro esquerdo (sem o braço) e, no golpe final, o direito e o pescoço. */
    private static class StumpLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

        StumpLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player,
                           float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            if (player.isInvisible()) return;
            int miss = VictimRender.missing(player, partialTick);
            boolean left = ClientArmState.isArmless(player.getId()) || (miss & 1) != 0;
            if (!left && miss == 0) return;
            poseStack.pushPose();
            getParentModel().body.translateAndRotate(poseStack);
            if (left) Stumps.leftShoulder(poseStack, buffers, light);
            if ((miss & 2) != 0) Stumps.rightShoulder(poseStack, buffers, light);
            if ((miss & 4) != 0) Stumps.neck(poseStack, buffers, light);
            poseStack.popPose();
        }
    }

    /** Item na mão de um braço que não existe mais: não desenha (ficava boiando do lado do ombro). */
    private static class MissingArmItemLayer
            extends PlayerItemInHandLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

        MissingArmItemLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent,
                            ItemInHandRenderer itemInHandRenderer) {
            super(parent, itemInHandRenderer);
        }

        @Override
        protected void renderArmWithItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                         HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffers, int light) {
            int miss = VictimRender.missing(entity, Minecraft.getInstance().getFrameTime());
            if (arm == HumanoidArm.LEFT && (ClientArmState.isArmless(entity.getId()) || (miss & 1) != 0)) return;
            if (arm == HumanoidArm.RIGHT && (miss & 2) != 0) return;
            super.renderArmWithItem(entity, stack, context, arm, poseStack, buffers, light);
        }
    }
}
