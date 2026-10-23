package com.becomelm.client;

import com.becomelm.BecomeLM;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registros do cliente (teclas, partícula de poeira, o aviso na tela). */
@Mod.EventBusSubscriber(modid = BecomeLM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
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
        event.registerSpriteSet(BecomeLM.DUST_PUFF.get(), DustPuffParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(LmHud.ID.getPath(), (gui, gfx, partial, w, h) -> LmHud.render(gfx, partial, w, h));
    }
}
