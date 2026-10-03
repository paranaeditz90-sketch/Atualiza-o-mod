package com.froggydude.init;

import com.froggydude.entity.FroggydudeEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Troque "froggydude" pelo MOD_ID real do seu mod se for diferente, e
 * chame ModEntityTypes.register(modEventBus) no construtor do seu mod.
 */
public class ModEntityTypes {

    public static final String MOD_ID = "froggydude";

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MOD_ID);

    public static final RegistryObject<EntityType<FroggydudeEntity>> FROGGYDUDE =
            ENTITY_TYPES.register("froggydude", () -> EntityType.Builder
                    .of(FroggydudeEntity::new, MobCategory.MONSTER)
                    .sized(0.8F, 2.1F)
                    .clientTrackingRange(16)
                    .build("froggydude"));

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }
}
