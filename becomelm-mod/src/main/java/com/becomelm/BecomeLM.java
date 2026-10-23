package com.becomelm;

import com.becomelm.network.LmNetwork;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * BecomeLM mode: o mod privado do LM pra gravar vídeos como o FroggyDude.
 * O jogador vira o FroggyDude (o mesmo rig e as mesmas animações do mod Froggydude,
 * com a skin do próprio jogador), escolhe o jeito numa roda (galope de 4 / em pé),
 * corre e pula como ele e tem câmeras de cinema. Funciona com amigos (o modo de cada
 * um vai pra todo mundo que está vendo).
 */
@Mod(BecomeLM.MOD_ID)
public final class BecomeLM {

    public static final String MOD_ID = "becomelm";

    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MOD_ID);
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MOD_ID);

    /** Patada do galope (cavalo grave, como no Froggydude). */
    public static final RegistryObject<SoundEvent> GALLOP = sound("gallop");
    /** Saída do bote e do pulo alto. */
    public static final RegistryObject<SoundEvent> LEAP = sound("leap");
    /** Chegada do pulo super alto no chão. */
    public static final RegistryObject<SoundEvent> SKY_LAND = sound("sky_land");
    /** Pulo alto carregado (segurando o agachar). */
    public static final RegistryObject<SoundEvent> CHARGED = sound("charged");

    public static final RegistryObject<SimpleParticleType> DUST_PUFF =
            PARTICLES.register("dust_puff", () -> new SimpleParticleType(false));

    public BecomeLM() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        SOUNDS.register(bus);
        PARTICLES.register(bus);
        LmNetwork.register();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }

    private static RegistryObject<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id(name)));
    }
}
