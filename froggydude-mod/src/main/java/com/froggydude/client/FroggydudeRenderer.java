package com.froggydude.client;

import com.froggydude.entity.FroggydudeEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class FroggydudeRenderer extends GeoEntityRenderer<FroggydudeEntity> {

    public FroggydudeRenderer(EntityRendererProvider.Context context) {
        super(context, new FroggydudeModel());
        this.shadowRadius = 0.6F;
        // o modelo tem o tamanho de um jogador (1,8 bloco); a hitbox é 2,1
        this.withScale(1.15F);
    }
}
