package com.froggydude.entity;

import com.froggydude.init.ModEntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * O bicho que ele está comendo fica caído e parado embaixo dele (sem IA: não
 * anda, não bate, não foge) até acabar. Fica marcado no próprio mob: se o mundo
 * salvar no meio da refeição (ou o Froggy sumir), quando ele carregar de novo
 * volta ao normal - nada de mob congelado pra sempre.
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class FeedLock {

    private static final String TAG = "froggydude_feed_lock";

    private FeedLock() {
    }

    public static void lock(LivingEntity victim) {
        if (victim instanceof Mob m && !m.isNoAi()) {
            m.setNoAi(true);
            m.getNavigation().stop();
            m.setTarget(null);
            m.getPersistentData().putBoolean(TAG, true);
        }
        victim.setDeltaMovement(0, Math.min(victim.getDeltaMovement().y, 0), 0);
    }

    public static void release(LivingEntity victim) {
        if (victim instanceof Mob m && m.getPersistentData().getBoolean(TAG)) {
            m.getPersistentData().remove(TAG);
            m.setNoAi(false);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Mob m) release(m);
    }
}
