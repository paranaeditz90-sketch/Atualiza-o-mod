package com.becomehim.client;

import com.becomehim.BecomeHim;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registros do cliente (teclas, partícula de poeira, o aviso na tela). */
@Mod.EventBusSubscriber(modid = BecomeHim.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LmClient {

    private LmClient() {
    }

    @SubscribeEvent
    public static void onKeys(RegisterKeyMappingsEvent event) {
        event.register(LmKeys.WHEEL);
        event.register(LmKeys.CAMERA);
    }

    @SubscribeEvent
    public static void onParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(BecomeHim.DUST_PUFF.get(), DustPuffParticle.Provider::new);
        event.registerSpriteSet(BecomeHim.BLOOD.get(), com.becomehim.client.pieces.LmBloodParticle.Provider::new);
    }

    /** (Become HIM...) O gancho da língua não aparece (só quem está nele). */
    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(BecomeHim.TONGUE_HOLD.get(), NoopRenderer::new);
    }

    /** O toco no ombro de quem perdeu o braço, no corpo de jogador (normal e fino). */
    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            if (event.getSkin(skin) instanceof net.minecraft.client.renderer.entity.player.PlayerRenderer pr) {
                pr.addLayer(new LmArmClient.StumpLayer(pr));
            }
        }
    }

    @SubscribeEvent
    public static void onOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(LmHud.ID.getPath(), (gui, gfx, partial, w, h) -> LmHud.render(gfx, partial, w, h));
    }
}
