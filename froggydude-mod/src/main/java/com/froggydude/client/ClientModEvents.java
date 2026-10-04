package com.froggydude.client;

import com.froggydude.init.ModEntityTypes;
import com.froggydude.init.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntityTypes.FROGGYDUDE.get(), FroggydudeRenderer::new);
    }

    /** Jogador sem o braço esquerdo: troca a armadura e põe o toco no ombro. */
    @SubscribeEvent
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        ArmlessRendering.addLayers(event);
    }

    /** Os pedaços de sangue. */
    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.BLOOD.get(), BloodParticle.Provider::new);
    }
}
