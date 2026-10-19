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
        event.registerEntityRenderer(ModEntityTypes.CORPSE.get(), PlayerCorpseRenderer::new);
        // o cinegrafista da cutscene nunca aparece
        event.registerEntityRenderer(ModEntityTypes.CUTSCENE_CAMERA.get(), net.minecraft.client.renderer.entity.NoopRenderer::new);
    }

    /** Jogador sem o braço esquerdo: troca a armadura e põe o toco no ombro. E o sangue grudado na skin. */
    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void addLayers(EntityRenderersEvent.AddLayers event) {
        ArmlessRendering.addLayers(event);
        // aldeão na sequência da zebra: os tocos dos braços e do pescoço
        for (net.minecraft.world.entity.EntityType<?> type : java.util.List.of(
                net.minecraft.world.entity.EntityType.VILLAGER, net.minecraft.world.entity.EntityType.WANDERING_TRADER)) {
            net.minecraft.client.renderer.entity.LivingEntityRenderer r = event.getRenderer((net.minecraft.world.entity.EntityType) type);
            if (r != null) r.addLayer(new com.froggydude.client.cutscene.VillagerVictimRender.StumpLayer(r));
        }
        for (String skin : event.getSkins()) {
            net.minecraft.client.renderer.entity.LivingEntityRenderer renderer = event.getSkin(skin);
            if (renderer instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr) {
                pr.addLayer(new com.froggydude.client.gore.BloodStains.Layer(pr));
            }
        }
    }

    /** Sangue na tela: por baixo do HUD (a barra de itens fica por cima). */
    @SubscribeEvent
    public static void registerOverlays(net.minecraftforge.client.event.RegisterGuiOverlaysEvent event) {
        event.registerBelowAll("blood_screen", (gui, gfx, partial, w, h) ->
                com.froggydude.client.gore.ScreenBlood.render(gfx, partial, w, h));
        // faixas pretas e o escuro da cutscene do golpe final: por cima de tudo
        event.registerAboveAll("cutscene", (gui, gfx, partial, w, h) ->
                com.froggydude.client.cutscene.CinematicCamera.renderOverlay(gfx, partial, w, h));
    }

    /** Os pedaços de sangue. */
    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.BLOOD.get(), BloodParticle.Provider::new);
        event.registerSpriteSet(ModParticles.DUST_PUFF.get(), DustPuffParticle.Provider::new);
        event.registerSpriteSet(ModParticles.BLOOD_CLOUD.get(), BloodCloudParticle.Provider::new);
    }
}
