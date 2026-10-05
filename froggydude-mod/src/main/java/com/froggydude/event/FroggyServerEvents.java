package com.froggydude.event;

import com.froggydude.anticheat.AntiCheat;
import com.froggydude.anticheat.Invasion;
import com.froggydude.command.FroggyCommand;
import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.init.ModEntityTypes;
import com.froggydude.world.FroggyKeeper;
import com.froggydude.world.FroggyWorldData;
import com.froggydude.world.Manhunt;
import com.froggydude.world.ManhuntMode;
import com.froggydude.world.Sabotage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/** Parte 4: um FroggyDude por mundo, manhunt, anti-trapaça, comando e mundo -dev. */
@Mod.EventBusSubscriber(modid = ModEntityTypes.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class FroggyServerEvents {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        FroggyCommand.register(event.getDispatcher());
    }

    /** Um por mundo: o que não for o do registro some ao entrar. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof FroggydudeEntity froggy)) return;
        if (!FroggyKeeper.onJoin(froggy, event.loadedFromDisk())) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Manhunt.tick(event.getServer());
        Invasion.tickAll(event.getServer());
    }

    /** Obsidiana posta por um jogador: ele "sabe" que vem portal (ver Sabotage). */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) Sabotage.onPlace(p, event.getPos(), event.getPlacedBlock());
    }

    @SubscribeEvent
    public static void onPortal(BlockEvent.PortalSpawnEvent event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel level) Sabotage.onPortal(level, event.getPos());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Sabotage.clear();
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        MinecraftServer server = event.getEntity().getServer();
        if (server == null || event.getEntity().level().isClientSide()) return;
        FroggyWorldData data = FroggyWorldData.get(server);
        if (!data.manhuntActive) return;
        if (event.getEntity() instanceof EnderDragon && data.manhuntMode == ManhuntMode.SPEEDRUN) {
            Manhunt.win(server, Component.translatable("froggydude.manhunt.win.dragon"));
        } else if (event.getEntity() instanceof ServerPlayer p
                && event.getSource().getEntity() instanceof FroggydudeEntity) {
            Manhunt.lose(server, p);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.getServer() == null) return;
        MinecraftServer server = p.getServer();
        FroggyWorldData data = FroggyWorldData.get(server);
        if (data.bans.containsKey(p.getUUID())) {
            if (FroggyConfig.get(FroggyConfig.PERDOAR)) {
                data.bans.remove(p.getUUID());
                data.strikes.remove(p.getUUID());
                data.setDirty();
                p.sendSystemMessage(Component.translatable("froggydude.ban.forgiven").withStyle(ChatFormatting.GREEN));
            } else {
                p.connection.disconnect(Component.translatable("froggydude.ban.report", p.getScoreboardName()));
                return;
            }
        }
        if (FroggyWorldData.isDev(server)) {
            p.sendSystemMessage(Component.translatable("froggydude.dev.world").withStyle(ChatFormatting.YELLOW));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) Invasion.onLogout(p);
    }

    // ----- anti-trapaça

    @SubscribeEvent
    public static void onGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        GameType to = event.getNewGameMode();
        if (Invasion.isRunning(p) && to != GameType.SURVIVAL) {
            event.setCanceled(true); // nem o criativo te salva
            return;
        }
        if (to == GameType.CREATIVE || to == GameType.SPECTATOR) AntiCheat.offense(p, "modo " + to.getName());
    }

    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        if (!(event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer p)) return;
        String line = event.getParseResults().getReader().getString().trim();
        if (line.startsWith("/")) line = line.substring(1);
        String root = line.split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (root.contains(":")) root = root.substring(root.indexOf(':') + 1);
        if (root.equals("froggydude")) return;
        if (Invasion.isRunning(p)) {
            event.setCanceled(true);
            return;
        }
        if (AntiCheat.isCheatCommand(root)) AntiCheat.offense(p, "/" + root);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer p) || p.tickCount % 20 != 0) return;
        if (p.getServer() == null || !AntiCheat.active(p.getServer())) return;
        if (p.getAbilities().flying && !p.isCreative() && !p.isSpectator()) {
            AntiCheat.offense(p, "voando");
        } else if (p.hasEffect(MobEffects.DAMAGE_RESISTANCE)
                && p.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() >= 4) {
            AntiCheat.offense(p, "resistência " + (p.getEffect(MobEffects.DAMAGE_RESISTANCE).getAmplifier() + 1));
        }
    }
}
