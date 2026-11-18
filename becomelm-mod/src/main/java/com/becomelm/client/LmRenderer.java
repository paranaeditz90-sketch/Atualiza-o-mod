package com.becomelm.client;

import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.util.RenderUtils;
import software.bernie.geckolib.cache.object.GeoBone;
import com.becomelm.LmAttacks;
import com.becomelm.LmAttack;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;

import javax.annotation.Nullable;

/**
 * Desenha o jogador como o FroggyDude, com a skin dele. Chamado no lugar do desenho do
 * jogador (LmClientEvents); sombra, fogo e nome continuam os do vanilla.
 */
public class LmRenderer extends GeoReplacedEntityRenderer<AbstractClientPlayer, LmAnimatable> {

    /** Do tamanho do Froggydude (que já é do tamanho de jogador). */
    private static final float SCALE = 1.05F;

    /** Quem está sendo desenhado agora (o controlador e o modelo leem). */
    @Nullable
    static AbstractClientPlayer rendering;

    /** Onde ficou a cabeça do próprio jogador no último quadro (relativo aos pés), pras câmeras. */
    @Nullable
    static Vec3 localHead;
    static long localHeadFrame = -1;

    private static LmRenderer instance;

    /** A boca (px), onde o braço arrancado fica atravessado (a do mod da IA). */
    private static final float MOUTH_Y = 26.0F;
    private static final float MOUTH_Z = -4.5F;

    private LmRenderer(EntityRendererProvider.Context context) {
        super(context, new LmModel(), LmAnimatable.INSTANCE);
        this.shadowRadius = 0.5F;
        withScale(SCALE);
        addRenderLayer(new LmBloodFace.Layer(this));
        addRenderLayer(new LmFroggyStains(this));
    }

    static LmRenderer get() {
        if (instance == null) {
            Minecraft mc = Minecraft.getInstance();
            instance = new LmRenderer(new EntityRendererProvider.Context(mc.getEntityRenderDispatcher(),
                    mc.getItemRenderer(), mc.getBlockRenderer(), mc.gameRenderer.itemInHandRenderer,
                    mc.getResourceManager(), mc.getEntityModels(), mc.font));
        }
        return instance;
    }

    @Override
    public ResourceLocation getTextureLocation(LmAnimatable animatable) {
        return this.currentEntity != null ? this.currentEntity.getSkinTextureLocation() : DefaultPlayerSkin.getDefaultSkin();
    }

    /**
     * (v0.3.9, LM 03/11: o nado "nos dois mods") Nadando (correndo dentro d'água): o boneco deita como o jogador do
     * jogo e inclina pra onde tu olha, com o meio do corpo no meio da caixa de nado (0,3 bloco do chão). A batida das
     * pernas e a gangorra são da animação "swim", a mesma do mod da IA (o dardo do vídeo).
     */
    @Override
    protected void applyRotations(LmAnimatable animatable, PoseStack poseStack, float ageInTicks, float rotationYaw,
                                  float partialTick) {
        super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick);
        AbstractClientPlayer p = this.currentEntity;
        if (p == null) return;
        float swim = p.getSwimAmount(partialTick);
        if (swim <= 0F) return;
        float tilt = (p.isInWater() ? 90F + p.getXRot() : 90F) * swim;
        poseStack.translate(0.0D, Mth.lerp(swim, 1.0D, 0.3D), 0.0D);
        poseStack.mulPose(Axis.XP.rotationDegrees(-tilt));
        poseStack.translate(0.0D, -1.0D, 0.0D);
    }

    @Override
    public void renderRecursively(PoseStack poseStack, LmAnimatable animatable, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                                  float partialTick, int packedLight, int packedOverlay,
                                  float red, float green, float blue, float alpha) {
        AbstractClientPlayer p = this.currentEntity;
        if (!isReRender && p != null && "head".equals(bone.getName())) {
            // o braço arrancado atravessado na boca (arrancando depois do puxão, e comendo até engolir)
            LmActsClient.Act a = LmActsClient.get(p);
            float eaten = -1F;
            if (a != null && a.kind == LmAttack.ARM_RIP && a.age >= LmAttacks.RIP_AT) eaten = 0F;
            if (a != null && a.kind == LmAttack.ARM_EAT && a.age < LmAttacks.EAT_SWALLOW) {
                eaten = 0.75F * Math.min(1F, (a.age + partialTick) / LmAttacks.EAT_SWALLOW);
            }
            if (a != null && !a.stolen.isEmpty() && (a.kind == LmAttack.WEAPON_EAT && a.age < LmAttacks.WEAPON_SWALLOW
                    || a.kind == LmAttack.STEAL && a.age >= LmAttacks.STEAL_TICKS - 2)) {
                poseStack.pushPose();
                RenderUtils.prepMatrixForBone(poseStack, bone);
                poseStack.translate(0F, (MOUTH_Y - 2.5F) / 16F, (MOUTH_Z - 1.0F) / 16F);
                if (a.kind == LmAttack.WEAPON_EAT) {
                    // vai engolindo: a ponta de dentro parada na boca e o pedaço de fora encurtando (mod da IA 0.7.30)
                    float in = Mth.clamp((a.age + partialTick - 2F) / (LmAttacks.WEAPON_SWALLOW - 2F), 0F, 1F);
                    float out = 1F - 0.92F * in;
                    poseStack.translate(7F / 16F * (0.15F - out), 0F, 0F);
                    poseStack.scale(out, 1F - 0.2F * in, 1F);
                }
                poseStack.mulPose(Axis.ZP.rotationDegrees(-45F));
                poseStack.scale(1.4F, 1.4F, 1.4F);
                Minecraft.getInstance().getItemRenderer().renderStatic(a.stolen, ItemDisplayContext.GROUND, packedLight,
                        OverlayTexture.NO_OVERLAY, poseStack, bufferSource, p.level(), p.getId());
                poseStack.popPose();
                buffer = bufferSource.getBuffer(renderType);
            }
            if (eaten >= 0F) {
                poseStack.pushPose();
                RenderUtils.prepMatrixForBone(poseStack, bone);
                LmArmClient.drawHeldArm(poseStack, bufferSource, packedLight, a.target, eaten, MOUTH_Y, MOUTH_Z);
                poseStack.popPose();
                buffer = bufferSource.getBuffer(renderType);
            }
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void render(AbstractClientPlayer player, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        boolean local = player == Minecraft.getInstance().player;
        if (local) getGeoModel().getBone("head").ifPresent(b -> b.setTrackingMatrices(true));
        rendering = player;
        try {
            super.render(player, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        } finally {
            rendering = null;
        }
        if (local) {
            getGeoModel().getBone("head").ifPresent(b -> {
                Vector3d at = b.getLocalPosition();
                localHead = new Vec3(at.x, at.y, at.z);
                localHeadFrame = LmCamera.frame();
            });
        }
    }
}
