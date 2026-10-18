package com.froggydude.event;

import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModDamageTypes;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.player.FinisherTracker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * O golpe dele que MATARIA vira a cutscene (LM: "quando ele te mataria"): o
 * dano é cancelado e, no próximo tick, a FroggyFinisherGoal começa. Durante a
 * cutscene a vítima não leva mais nada (só a cabeça arrancada no fim) e não
 * consegue bater, usar item nem quebrar bloco.
 *
 * Não começa se: criativo/espectador, ou com um totem na mão (o totem salva,
 * como no jogo normal - ninguém foi roubado de um totem).
 */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FinisherEvents {

    /** Na cutscene nada encosta (nem empurrão, nem som de dano). */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && FinisherTracker.isVictim(p)
                && !event.getSource().is(ModDamageTypes.DEVOURED)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (FinisherTracker.isVictim(p)) {
            if (!event.getSource().is(ModDamageTypes.DEVOURED)) event.setCanceled(true);
            return;
        }
        // o dano do fim da própria cutscene (às vezes chega depois, no Extremo) não vira outra cutscene
        if (event.getSource().is(ModDamageTypes.DEVOURED)) return;
        FroggydudeEntity froggy = froggyOf(event.getSource());
        if (froggy == null || !froggy.isAlive() || froggy.isFinishing()) return;
        if (p.getHealth() - event.getAmount() > 0F) return;
        if (p.isCreative() || p.isSpectator() || holdsTotem(p) || p.level() != froggy.level()) return;
        event.setCanceled(true);
        p.setHealth(Math.max(1F, p.getHealth()));
        FinisherTracker.mark(p, froggy.getId());
        froggy.requestFinisher(p);
    }

    private static FroggydudeEntity froggyOf(DamageSource s) {
        if (s.getEntity() instanceof FroggydudeEntity f) return f;
        if (s.getDirectEntity() instanceof FroggydudeEntity f) return f;
        return null;
    }

    private static boolean holdsTotem(Player p) {
        return p.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || p.getOffhandItem().is(Items.TOTEM_OF_UNDYING);
    }

    // ---- a vítima não faz nada durante a cutscene

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && FinisherTracker.isVictim(p)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer p && FinisherTracker.isVictim(p)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer p && FinisherTracker.isVictim(p)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onBreak(PlayerEvent.BreakSpeed event) {
        if (event.getEntity() instanceof ServerPlayer p && FinisherTracker.isVictim(p)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // saiu no meio (ou assistindo ele comer o corpo): não escapa - volta morto
        if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp && FinisherTracker.deathAt(sp) >= 0) {
            ModForgeEvents.finishVictim(sp);
        }
        FinisherTracker.clear(event.getEntity());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        FinisherTracker.clearAll();
    }
}
