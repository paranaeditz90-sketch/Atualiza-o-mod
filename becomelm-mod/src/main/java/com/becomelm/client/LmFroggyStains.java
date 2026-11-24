package com.becomelm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.lang.reflect.Method;

/**
 * Com o mod do Froggydude junto: o sangue que ele gruda na skin do jogador (BloodStains) também
 * aparece no corpo do FroggyDude. A camada dele só desenha no modelo de jogador do vanilla; aqui o
 * nosso modelo passa pelo mesmo desenho dos pingos (o layout da skin é o mesmo). Sem o mod, nada.
 */
final class LmFroggyStains extends GeoRenderLayer<LmAnimatable> {

    private static final Method DECALS = find();

    LmFroggyStains(GeoRenderer<LmAnimatable> renderer) {
        super(renderer);
    }

    private static Method find() {
        try {
            Class<?> c = Class.forName("com.froggydude.client.gore.BloodStains");
            return c.getMethod("decalsFor", AbstractClientPlayer.class, MultiBufferSource.class, int.class, int.class);
        } catch (ReflectiveOperationException | LinkageError e) {
            // sem o mod do Froggydude (ou uma versão antiga, sem a porta pra outro modelo)
            return null;
        }
    }

    @Override
    public void render(PoseStack poseStack, LmAnimatable animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay) {
        AbstractClientPlayer p = LmRenderer.rendering;
        if (DECALS == null || p == null) return;
        VertexConsumer decals;
        try {
            decals = (VertexConsumer) DECALS.invoke(null, p, bufferSource, packedLight, packedOverlay);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return;
        }
        if (decals == null) return;
        getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, renderType, decals, partialTick,
                packedLight, packedOverlay, 1F, 1F, 1F, 1F);
    }
}
