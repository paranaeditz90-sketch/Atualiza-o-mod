package com.becomelm.client;

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
