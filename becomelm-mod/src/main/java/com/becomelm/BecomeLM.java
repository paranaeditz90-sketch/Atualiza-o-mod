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
    /** O bote acertando o bicho. */
    public static final RegistryObject<SoundEvent> POUNCE_HIT = sound("pounce_hit");
    /** Transformação: o som original inteiro (vazio sem o pacote de sons; com ele, só os estalos). */
    public static final RegistryObject<SoundEvent> CONTORT = sound("contort");
    /** Os estalos da contorção nos tempos do original (osso do jogo; o pacote cala, o clipe já tem). */
    public static final RegistryObject<SoundEvent> CRACK_SEQ = sound("crack_seq");
    /** Um estalo solto (torto, esperando arrancar). */
    public static final RegistryObject<SoundEvent> CRACK = sound("crack");
    /** Os socos montado: dois sons, um pra cada punho (no vídeo eles se alternam). */
    public static final RegistryObject<SoundEvent> PUNCH_A = sound("punch_a");
    public static final RegistryObject<SoundEvent> PUNCH_B = sound("punch_b");
    /** A mordida. */
    public static final RegistryObject<SoundEvent> BITE = sound("bite");
    /** Arremessou quem estava na boca (o vento do tranco). */
    public static final RegistryObject<SoundEvent> THROW = sound("throw");

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
