package com.froggydude.event;

import com.froggydude.entity.PinTracker;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.network.ModNetwork;
import com.froggydude.player.ArmLoss;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Eventos do lado do servidor (valem pro mundo de um jogador também). */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModForgeEvents {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.player instanceof ServerPlayer player) {
            PinTracker.tick(player);
            ArmLoss.tick(player);
        }
    }

    /** Sem braço esquerdo não dá pra trocar de mão (tecla F). */
    @SubscribeEvent
    public static void onSwapHands(LivingSwapItemsEvent.Hands event) {
        if (event.getEntity() instanceof Player player && ArmLoss.isArmless(player)) {
            event.setCanceled(true);
        }
    }

    /**
     * O Forge só copia parte dos dados na troca de corpo. Na morte o braço
     * volta (é a regra); voltando do End, não: aí a gente copia a marca.
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) return;
        if (ArmLoss.isArmless(event.getOriginal())) {
            event.getEntity().getPersistentData().putBoolean("froggydude.armless", true);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        syncSelf(event.getEntity());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        syncSelf(event.getEntity());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        syncSelf(event.getEntity());
    }

    /** Quando um jogador aparece pra outro, conta se ele está sem braço. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof Player target && event.getEntity() instanceof ServerPlayer viewer) {
            ModNetwork.sendArmStateTo(viewer, target.getId(), ArmLoss.isArmless(target));
        }
    }

    private static void syncSelf(Player player) {
        if (player instanceof ServerPlayer sp) {
            ModNetwork.sendArmState(sp, ArmLoss.isArmless(sp));
        }
    }
}
