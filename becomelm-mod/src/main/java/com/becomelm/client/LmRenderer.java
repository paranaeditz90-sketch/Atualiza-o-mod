package com.becomelm.client;

import com.mojang.blaze3d.vertex.PoseStack;
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
