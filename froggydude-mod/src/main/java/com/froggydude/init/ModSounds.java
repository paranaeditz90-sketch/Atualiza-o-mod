package com.froggydude.init;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * O grito da fase 2. Por enquanto o assets/froggydude/sounds.json aponta pro
 * rugido do Ravager (grave e alto). Pra usar um som seu, veja o NOTES.md.
 */
public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ModEntityTypes.MOD_ID);

    public static final RegistryObject<SoundEvent> SCREAM = SOUNDS.register("froggydude.scream",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ModEntityTypes.MOD_ID, "froggydude.scream")));

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
