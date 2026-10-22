package com.froggydude.init;

import com.froggydude.entity.CutsceneCamera;
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
                    .sized(0.7F, 1.95F) // do tamanho de um jogador, um pouco maior
                    .clientTrackingRange(16)
                    .build("froggydude"));

    /** A câmera da cutscene (só no cliente de quem está nela; nunca entra no mundo). */
    public static final RegistryObject<EntityType<CutsceneCamera>> CUTSCENE_CAMERA =
            ENTITY_TYPES.register("cutscene_camera", () -> EntityType.Builder
                    .<CutsceneCamera>of(CutsceneCamera::new, MobCategory.MISC)
                    .sized(0.2F, 0.2F)
                    .noSave()
                    .noSummon()
                    .clientTrackingRange(0)
                    .build("cutscene_camera"));

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }
}
