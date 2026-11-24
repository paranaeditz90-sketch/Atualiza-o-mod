package com.becomelm.client;

import net.minecraft.world.entity.EntityType;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * O "FroggyDude" que veste o jogador. Um objeto só pra todos os jogadores: o GeckoLib
 * guarda o estado da animação de cada um pelo id da entidade (o controlador sabe de
 * quem é a vez pelo jogador sendo desenhado).
 */
public final class LmAnimatable implements GeoReplacedEntity {

    public static final LmAnimatable INSTANCE = new LmAnimatable();

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private LmAnimatable() {
    }

    @Override
    public EntityType<?> getReplacingEntityType() {
        return EntityType.PLAYER;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new LmAnimController(this));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
