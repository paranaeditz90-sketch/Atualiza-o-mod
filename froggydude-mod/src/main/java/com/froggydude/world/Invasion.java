package com.froggydude.world;

import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.FroggydudeEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

import java.util.List;

/**
 * "Froggy's in your world" (v0.7.16, respostas do LM de 27/10 - ver TAREFA.md): ligado no
 * config, cada mundo sorteia escondido o dia em que ele chega (4 a 10). No dia, uns minutos
 * depois do amanhecer: a mensagem de mentira de "jogo local hospedado" (nenhuma rede abre
 * de verdade), "FroggyDude entrou no jogo" em amarelo e, uns segundos depois - o tempo dele
 * vir de longe -, ele aparece fora da tua vista e caça direto (com a IA do config). Sabe
 * onde tu está (o mesmo rastro do manhunt). Te pegou: some uns 4 minutos e volta. Dragão
 * morto: "NAO! MERDA! Ah... Vá se ferrar" no chat e "saiu do jogo" - acabou naquele mundo.
 * As falas ficam em português mesmo com o jogo em inglês (LM).
 */
public final class Invasion {

    static final String NAME = "FroggyDude";
    private static final int FIRST_DAY = 4;
    private static final int LAST_DAY = 10;
    /** Te pegou: some esse tempo (LM: "uns 4 minutos"). */
    private static final long AWAY_AFTER_KILL = 4L * 60L * 20L;
    /** Da LAN falsa ao "entrou no jogo". */
    private static final long LAN_TO_JOIN = 100L;
    /** Do "entrou no jogo" a ele aparecer: o tempo de vir de 300 blocos no galope. */
    private static final long JOIN_TO_ARRIVE = 400L;

    private Invasion() {
    }

    /** Já chegou e está caçando (o resto do mod trata como o manhunt: volta depois de morrer). */
    public static boolean hunting(FroggyWorldData data) {
        return data.invasionStage == 3;
    }

    /** Dia do mundo, contando do 1 (dormir pula a noite e conta). */
    static int day(MinecraftServer server) {
        return (int) (server.overworld().getDayTime() / 24000L) + 1;
    }

    /** Sorteia o dia (mundo já passado do dia 10 quando ligou: ele vem em 1 a 3 dias). */
    static void roll(MinecraftServer server, FroggyWorldData data) {
        RandomSource r = server.overworld().random;
        int today = day(server);
        int d = FIRST_DAY + r.nextInt(LAST_DAY - FIRST_DAY + 1);
        if (d <= today) d = today + 1 + r.nextInt(3);
        data.invasionDay = d;
        data.invasionStage = 0;
        data.invasionAt = 0;
        data.setDirty();
    }

    /** A cada segundo. */
    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        long now = overworld.getGameTime();
        if (now % 20 != 0 || !FroggyConfig.invasion()) return;
        FroggyWorldData data = FroggyWorldData.get(server);
        if (data.manhuntActive || data.invasionStage >= 4) return;
        if (data.invasionDay == 0) roll(server, data);
        List<ServerPlayer> players = runners(server);
        if (players.isEmpty()) return;
        switch (data.invasionStage) {
            case 0 -> {
                if (day(server) < data.invasionDay) return;
                if (data.invasionAt == 0) {
                    // não na virada do dia: uns minutos depois, quando tu já está fazendo as coisas
                    data.invasionAt = now + 1200 + overworld.random.nextInt(3600);
                    data.setDirty();
                } else if (now >= data.invasionAt) {
                    int port = 49152 + overworld.random.nextInt(16000);
                    broadcast(server, Component.translatable("commands.publish.started", port));
                    next(data, 1, now + LAN_TO_JOIN);
                }
            }
            case 1 -> {
                if (now < data.invasionAt) return;
                broadcast(server, Component.translatable("multiplayer.player.joined", NAME).withStyle(ChatFormatting.YELLOW));
                next(data, 2, now + JOIN_TO_ARRIVE);
            }
            case 2 -> {
                if (now < data.invasionAt) return;
                ServerPlayer p = players.get(overworld.random.nextInt(players.size()));
                FroggydudeEntity froggy = FroggyKeeper.find(server);
                if (froggy == null) froggy = FroggyKeeper.spawnNear(p, 48.0D, 72.0D, FroggyKeeper.Sight.HIDDEN, -1F);
                else FroggyKeeper.bringNear(froggy, p, 48.0D, 72.0D, FroggyKeeper.Sight.HIDDEN);
                froggy = FroggyKeeper.find(server);
                if (froggy == null) return; // sem lugar agora: tenta de novo no próximo segundo
                froggy.setTarget(p);
                froggy.brainLog("in your world: chegou no mundo, atrás de " + p.getScoreboardName());
                next(data, 3, 0);
            }
            default -> Manhunt.keepOnTrail(server, data, now);
        }
    }

    /** Alguém morreu: ele te pegou (some 4 min) ou o dragão caiu (ele vai embora de vez). */
    public static void onDeath(MinecraftServer server, LivingDeathEvent event) {
        FroggyWorldData data = FroggyWorldData.get(server);
        if (!hunting(data) || data.manhuntActive) return;
        if (event.getEntity() instanceof EnderDragon) {
            say(server, "NAO! MERDA! Ah... Vá se ferrar");
            broadcast(server, Component.translatable("multiplayer.player.left", NAME).withStyle(ChatFormatting.YELLOW));
            FroggydudeEntity froggy = FroggyKeeper.find(server);
            if (froggy != null) froggy.discard();
            data.froggyAlive = false;
            data.respawnAt = -1;
            next(data, 4, 0);
        } else if (event.getEntity() instanceof ServerPlayer
                && event.getSource().getEntity() instanceof FroggydudeEntity froggy) {
            froggy.leaveAfterWin(100);
            data.respawnAt = server.overworld().getGameTime() + AWAY_AFTER_KILL;
            data.setDirty();
        }
    }

    /** Ele fala no chat como jogador: "<FroggyDude> ...". */
    public static void say(MinecraftServer server, String text) {
        broadcast(server, Component.translatable("chat.type.text", NAME, text));
    }

    /** /froggydude invasao: o estado (pra testar). */
    public static Component status(MinecraftServer server) {
        FroggyWorldData data = FroggyWorldData.get(server);
        String stage = switch (data.invasionStage) {
            case 0 -> "esperando";
            case 1, 2 -> "chegando";
            case 3 -> "caçando";
            default -> "foi embora (dragão)";
        };
        return Component.literal("In your world: " + (FroggyConfig.invasion() ? "ligado" : "desligado")
                + ", hoje é o dia " + day(server) + ", ele chega no dia " + (data.invasionDay == 0 ? "?" : data.invasionDay)
                + " (" + stage + ").");
    }

    /** /froggydude invasao agora: pula pra chegada (teste). */
    public static void arriveNow(MinecraftServer server) {
        FroggyWorldData data = FroggyWorldData.get(server);
        data.invasionDay = day(server);
        next(data, 0, server.overworld().getGameTime() + 20);
    }

    /** /froggydude invasao reiniciar: sorteia de novo, como mundo novo. */
    public static void reset(MinecraftServer server) {
        FroggyWorldData data = FroggyWorldData.get(server);
        roll(server, data);
    }

    private static void next(FroggyWorldData data, int stage, long at) {
        data.invasionStage = stage;
        data.invasionAt = at;
        data.setDirty();
    }

    private static List<ServerPlayer> runners(MinecraftServer server) {
        return server.getPlayerList().getPlayers().stream()
                .filter(p -> p.isAlive() && !p.isSpectator() && !p.isCreative()).toList();
    }

    private static void broadcast(MinecraftServer server, Component msg) {
        server.getPlayerList().broadcastSystemMessage(msg, false);
    }
}
