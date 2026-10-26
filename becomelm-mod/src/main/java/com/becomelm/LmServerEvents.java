package com.becomelm;

import com.becomelm.network.LmNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
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

    /** Alguém passou a ver esse jogador (ou bicho): conta o modo dele e se está derrubado. */
    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer viewer)) return;
        if (event.getTarget() instanceof ServerPlayer target) LmNetwork.sendModeTo(viewer, target, LmModes.get(target));
        LmKnock.sendTo(viewer, event.getTarget());
        LmTransform.sendTo(viewer, event.getTarget());
        LmAttacks.sendTo(viewer, event.getTarget());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        LmModes.copy(event.getOriginal(), event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (event.player instanceof ServerPlayer sp) {
                LmAttacks.tick(sp);
                LmTransform.tick(sp);
            }
            return;
        }
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

    // ---------------------------------------------------------------- derrubado

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.level instanceof ServerLevel level) LmKnock.tick(level);
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!event.getLevel().isClientSide()) LmKnock.forget(event.getEntity());
    }

    /** Quem o FroggyDude mata vira pedaços (no lugar de cair de lado e sumir). */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide() || !(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        if (!LmModes.get(killer).froggy() || killer == victim) return;
        double dx = victim.getX() - killer.getX(), dz = victim.getZ() - killer.getZ();
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 1.0E-3D) {
            dx /= len;
            dz /= len;
        }
        LmBloodType blood = LmBloodType.of(victim);
        LmNetwork.sendBreak(victim, (float) dx, 0.15F, (float) dz, LmAttacks.breakPower(killer), blood.color, blood.bleeds());
    }

    /** O soco normal (em pé) também pode virar a finalização, raríssimo. */
    @SubscribeEvent
    public static void onMelee(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && event.getTarget() instanceof LivingEntity t
                && LmAttacks.melee(p, t)) {
            event.setCanceled(true);
        }
    }

    /** Deitado ninguém bate (o zumbi no chão não morde o pé de quem passa). */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && LmKnock.lying(attacker)
                && event.getSource().getDirectEntity() == attacker) {
            event.setCanceled(true);
        }
    }

    /** ...nem atira (esqueleto derrubado). */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Projectile proj)) return;
        Entity owner = proj.getOwner();
        if (owner instanceof LivingEntity le && LmKnock.lying(le)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LmAttacks.forget(event.getEntity());
        LmTransform.forget(event.getEntity());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LmKnock.clear();
        LmAttacks.clear();
        LmTransform.clear();
    }
}
