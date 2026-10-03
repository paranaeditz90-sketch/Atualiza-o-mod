package com.froggydude.init;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sons do Froggy. Os arquivos .ogg ficam em assets/froggydude/sounds/ e a
 * lista de quais arquivos cada som sorteia está em assets/froggydude/sounds.json.
 */
public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ModEntityTypes.MOD_ID);

    /** Parado/andando: ribbit, latido, risadinha. */
    public static final RegistryObject<SoundEvent> AMBIENT = register("froggydude.ambient");
    /** Quando ele te vê pela primeira vez ("I SEE YOU", risada). */
    public static final RegistryObject<SoundEvent> HUNT = register("froggydude.hunt");
    /** Apanhou: grunhido curto. */
    public static final RegistryObject<SoundEvent> HURT = register("froggydude.hurt");
    /** De vez em quando, no lugar do grunhido: "I love pain". */
    public static final RegistryObject<SoundEvent> LOVE_PAIN = register("froggydude.love_pain");
    public static final RegistryObject<SoundEvent> DEATH = register("froggydude.death");
    /** O grito da fase 2. */
    public static final RegistryObject<SoundEvent> SCREAM = register("froggydude.scream");
    public static final RegistryObject<SoundEvent> BITE = register("froggydude.bite");
    /** Comendo / "you taste so good". */
    public static final RegistryObject<SoundEvent> EAT = register("froggydude.eat");
    /** O estalo da língua saindo. */
    public static final RegistryObject<SoundEvent> TONGUE = register("froggydude.tongue");
    /** Provou com a língua. */
    public static final RegistryObject<SoundEvent> TASTE = register("froggydude.taste");
    /** Esforço do salto de sapo. */
    public static final RegistryObject<SoundEvent> LEAP = register("froggydude.leap");
    /** Montado em cima da vítima. */
    public static final RegistryObject<SoundEvent> PIN = register("froggydude.pin");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ModEntityTypes.MOD_ID, name)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
