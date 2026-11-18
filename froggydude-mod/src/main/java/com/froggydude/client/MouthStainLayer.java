package com.froggydude.client;

import com.froggydude.config.FroggyClientConfig;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModEntityTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * A boca suja do que ele acabou de comer (LM: comendo um golem de ferro a boca
 * dele ficou meio branca): lábios, cantos, o fundo da boca e uns pingos no
 * peito, da cor do sangue do bicho (ou do que ele é feito, se não sangra).
 * Some aos poucos (FroggydudeEntity.stainMouth). No gore Desligado não aparece.
 */
public class MouthStainLayer extends GeoRenderLayer<FroggydudeEntity> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(ModEntityTypes.MOD_ID, "textures/entity/froggydude_mouth_stain.png");

    public MouthStainLayer(GeoRenderer<FroggydudeEntity> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, FroggydudeEntity froggy, BakedGeoModel model, RenderType renderType,
                       MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int light, int overlay) {
        float a = froggy.getMouthStain();
        if (a < 0.02F || !FroggyClientConfig.gore().any() || froggy.isInvisible()) return;
        int c = froggy.getMouthColor();
        RenderType type = RenderType.entityTranslucent(TEXTURE);
        // um pouco mais escuro que a cor pura: molhado, grudado
        getRenderer().reRender(model, poseStack, buffers, froggy, type, buffers.getBuffer(type), partialTick, light,
                OverlayTexture.NO_OVERLAY, ((c >> 16) & 255) / 255F * 0.9F, ((c >> 8) & 255) / 255F * 0.9F,
                (c & 255) / 255F * 0.9F, Math.min(1F, 0.35F + a * 0.65F));
    }
}
