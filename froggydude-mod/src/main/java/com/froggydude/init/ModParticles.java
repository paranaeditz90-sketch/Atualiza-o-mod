package com.froggydude.init;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Partículas do mod. A textura e o comportamento ficam no cliente (client.BloodParticle). */
public class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ModEntityTypes.MOD_ID);

    /** Pedaço de sangue: voa na direção do jato, cai com peso e fica um pouco no chão. */
    public static final RegistryObject<SimpleParticleType> BLOOD =
            PARTICLES.register("blood", () -> new SimpleParticleType(false));

    /** Nuvem de poeira do galope (pega a cor do chão). */
    public static final RegistryObject<SimpleParticleType> DUST_PUFF =
            PARTICLES.register("dust_puff", () -> new SimpleParticleType(false));

    public static void register(IEventBus bus) {
        PARTICLES.register(bus);
    }
}
