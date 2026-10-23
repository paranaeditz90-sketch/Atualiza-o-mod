package com.froggydude.event;

import com.froggydude.command.FroggyCommand;
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
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
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

/** Parte 4: um FroggyDude por mundo, manhunt, comando e mundo -dev. */
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
        long now = event.getServer().overworld().getGameTime();
        if (now % 20 == 0) FroggyKeeper.keepAwake(event.getServer());
        if (now % 10 == 0) {
            // o rastro de cheiro de cada um (o faro do FroggyDude)
            for (net.minecraft.server.level.ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
                com.froggydude.brain.Scent.record(p, p.serverLevel().getGameTime());
            }
        }
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
        com.froggydude.brain.Scent.clear();
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
        if (FroggyWorldData.isDev(p.getServer())) {
            p.sendSystemMessage(Component.translatable("froggydude.dev.world").withStyle(ChatFormatting.YELLOW));
        }
    }
}
