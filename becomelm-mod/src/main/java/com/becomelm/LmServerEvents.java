package com.becomelm;

import com.becomelm.network.LmNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** O lado do servidor (e o que roda igual nos dois lados): modo salvo, sincronia e movimento. */
@Mod.EventBusSubscriber(modid = BecomeLM.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LmServerEvents {

    private LmServerEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) LmNetwork.sendMode(p, LmModes.get(p));
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) LmNetwork.sendMode(p, LmModes.get(p));
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) LmNetwork.sendMode(p, LmModes.get(p));
    }

    /** Alguém passou a ver esse jogador: conta pra ele em que modo o outro está. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer target && event.getEntity() instanceof ServerPlayer viewer) {
            LmNetwork.sendModeTo(viewer, target, LmModes.get(target));
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        LmModes.copy(event.getOriginal(), event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Player p = event.player;
        // no cliente só o próprio jogador (os outros vêm prontos do servidor)
        if (p.level().isClientSide() && !p.isLocalPlayer()) return;
        LmMovement.tick(p, LmModes.get(p));
    }

    /** O FroggyDude cai de qualquer altura (os pulos dele vão longe). */
    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player p && LmModes.get(p).froggy()) event.setCanceled(true);
    }
}
