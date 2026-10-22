package com.froggydude.client.gore;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

/**
 * Como a camada de sangue da skin é desenhada (v0.5.1): igual ao "translúcido de
 * entidade" do jogo, mas pinta SÓ a cor, sem gravar profundidade.
 *
 * Por quê: a camada é o modelo de novo (a parte de fora da skin, um tico maior), com
 * a textura quase toda transparente. No celular (LM, Zalith Launcher 2) o pixel
 * transparente gravava profundidade mesmo assim - a caixa invisível da camada
 * tapava o corpo de baixo e o jogador sumia depois do "banho de sangue" (na 3ª
 * pessoa e na prévia do inventário), até a chuva lavar o sangue.
 */
public final class StainRenderType extends RenderType {

    private static final Function<ResourceLocation, RenderType> STAIN = Util.memoize(loc -> create(
            "froggydude_stain", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, true, true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(loc, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(true)));

    private StainRenderType(String name, VertexFormat format, VertexFormat.Mode mode, int size, boolean crumbling,
                            boolean sort, Runnable setup, Runnable clear) {
        super(name, format, mode, size, crumbling, sort, setup, clear);
    }

    public static RenderType stain(ResourceLocation loc) {
        return STAIN.apply(loc);
    }
}
