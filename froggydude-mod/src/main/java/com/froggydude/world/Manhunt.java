package com.froggydude.world;

import com.froggydude.anticheat.AntiCheat;
import com.froggydude.anticheat.Invasion;
import com.froggydude.brain.Strategy;
import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.FroggydudeEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Manhunt (correção do LM: a proposta dos vídeos é caçada, corrida contra o
 * tempo, tipo o Speedrun do Dream). Dois modos, no config ou no comando:
 * - SPEEDRUN: matar o Ender Dragon antes dele te pegar;
 * - SOBREVIVENCIA: aguentar X dias ("How many days can you survive against me?").
 *
 * Começa com uma vantagem (ele parado, te encarando, contagem na tela). Se ele
 * pegar alguém, ele vence. Morreu? Volta depois de um tempo, fora da tua vista.
 * Foi pro Nether/End? Ele vai atrás. Longe demais? Ele "fareja" e reaparece perto.
 */
public final class Manhunt {

    /** Ticks seguidos sem achar o FroggyDude carregado / longe / em outra dimensão. */
    private static int missingTicks, farTicks, awayTicks;

    private Manhunt() {
    }

    public static void start(ServerPlayer starter, ManhuntMode mode, int days) {
        MinecraftServer server = starter.getServer();
        if (server == null) return;
        FroggyWorldData data = FroggyWorldData.get(server);
        long now = server.overworld().getGameTime();
        int head = FroggyConfig.get(FroggyConfig.VANTAGEM_SEGUNDOS);
        data.manhuntActive = true;
        data.manhuntMode = mode;
        data.manhuntDays = days;
        data.manhuntStart = now;
        data.manhuntStartDay = server.overworld().getDayTime();
        data.releaseAt = now + head * 20L;
        data.released = false;
        data.lastDayShown = 0;
        data.strikes.clear();
        data.respawnAt = -1;
        data.setDirty();
        missingTicks = farTicks = awayTicks = 0;

        // ele aparece perto, parado, te encarando até ser solto
        FroggydudeEntity froggy = FroggyKeeper.find(server);
        if (froggy == null) {
            froggy = FroggyKeeper.spawnNear(starter, 7.0D, 9.0D, FroggyKeeper.Sight.IN_VIEW, -1F);
        } else {
            FroggyKeeper.bringNear(froggy, starter, 7.0D, 9.0D, FroggyKeeper.Sight.IN_VIEW);
            froggy = FroggyKeeper.find(server); // trocar de dimensão cria outra instância
        }
        if (froggy != null) {
            froggy.setHealth(froggy.getMaxHealth());
            froggy.setTarget(null);
            froggy.holdStill(head * 20, starter);
        }
        Component sub = mode == ManhuntMode.SPEEDRUN
                ? Component.translatable("froggydude.manhunt.goal.speedrun")
                : Component.translatable("froggydude.manhunt.goal.survive", days);
        titleAll(server, Component.translatable("froggydude.manhunt.title"), sub);
        AntiCheat.onManhuntStart(server);
    }

    public static void stop(MinecraftServer server) {
        FroggyWorldData data = FroggyWorldData.get(server);
        data.manhuntActive = false;
        data.respawnAt = -1;
        data.setDirty();
        FroggydudeEntity froggy = FroggyKeeper.find(server);
        if (froggy != null) froggy.holdStill(0, null);
    }

    public static boolean isActive(MinecraftServer server) {
        return FroggyWorldData.get(server).manhuntActive;
    }

    /** A cada segundo: contagem, dias, e o FroggyDude sempre no teu rastro. */
    public static void tick(MinecraftServer server) {
        FroggyWorldData data = FroggyWorldData.get(server);
        if (!data.manhuntActive) return;
        ServerLevel overworld = server.overworld();
        long now = overworld.getGameTime();
        if (now % 20 != 0) return;

        if (!data.released) {
            long left = (data.releaseAt - now + 19) / 20;
            if (left > 0) {
                if (Invasion.anyRunning()) return;
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.displayClientMessage(Component.translatable("froggydude.manhunt.countdown", left), true);
                }
                return;
            }
            if (Invasion.anyRunning()) return; // a invasão manda nele agora
            data.released = true;
            data.setDirty();
            FroggydudeEntity froggy = FroggyKeeper.find(server);
            if (froggy != null) {
                froggy.holdStill(0, null);
                ServerPlayer first = nearestRunner(server, froggy);
                if (first != null) froggy.setTarget(first); // ao mirar alguém ele já fala ("hey, humans!")
            }
            titleAll(server, Component.translatable("froggydude.manhunt.run"), Component.empty());
        }

        if (data.manhuntMode == ManhuntMode.SOBREVIVENCIA) {
            // conta pelo tempo de jogo (dormir pula a noite, mas não pula o manhunt)
            long elapsed = now - data.manhuntStart;
            if (elapsed >= data.manhuntDays * 24000L) {
                win(server, Component.translatable("froggydude.manhunt.win.survived", data.manhuntDays));
                return;
            }
            int day = (int) (elapsed / 24000L) + 1;
            if (day > data.lastDayShown) {
                data.lastDayShown = day;
                data.setDirty();
                titleAll(server, Component.translatable("froggydude.manhunt.day", day, data.manhuntDays), Component.empty());
            }
        }
        keepOnTrail(server, data, now);
    }

    private static void keepOnTrail(MinecraftServer server, FroggyWorldData data, long now) {
        List<ServerPlayer> runners = server.getPlayerList().getPlayers().stream()
                .filter(p -> p.isAlive() && !p.isSpectator() && !p.isCreative()).toList();
        if (runners.isEmpty()) return;
        FroggydudeEntity froggy = FroggyKeeper.find(server);
        if (froggy == null) {
            if (data.respawnAt >= 0) {
                if (now >= data.respawnAt) {
                    ServerPlayer r = runners.get(server.overworld().random.nextInt(runners.size()));
                    FroggydudeEntity back = FroggyKeeper.spawnNear(r, 24.0D, 40.0D, FroggyKeeper.Sight.HIDDEN, -1F);
                    if (back != null) {
                        data.respawnAt = -1;
                        data.setDirty();
                        back.setTarget(r);
                    }
                }
            } else if (data.froggyAlive) {
                // vivo, mas num pedaço do mapa que não está carregado: reaparece perto
                missingTicks += 20;
                if (missingTicks >= 200) {
                    missingTicks = 0;
                    FroggyKeeper.spawnNear(runners.get(0), 28.0D, 40.0D, FroggyKeeper.Sight.HIDDEN, data.froggyHealth);
                }
            } else {
                // morreu sem a gente ver (ex.: /kill): volta no tempo normal
                data.respawnAt = now + 20L * FroggyConfig.get(FroggyConfig.VOLTA_SEGUNDOS);
                data.setDirty();
            }
            return;
        }
        missingTicks = 0;
        data.froggyHealth = froggy.getHealth();

        ServerPlayer nearest = null;
        double best = Double.MAX_VALUE;
        for (ServerPlayer p : runners) {
            if (p.level() != froggy.level()) continue;
            double d = p.distanceTo(froggy);
            if (d < best) {
                best = d;
                nearest = p;
            }
        }
        if (nearest == null) {
            // todo mundo foi pra outra dimensão: ele vai atrás ("he's going to the Nether")
            awayTicks += 20;
            if (awayTicks >= 400) {
                awayTicks = 0;
                FroggyKeeper.bringNear(froggy, runners.get(0), 28.0D, 40.0D, FroggyKeeper.Sight.HIDDEN);
            }
            return;
        }
        awayTicks = 0;
        if (best > 72.0D) {
            farTicks += 20;
            if (farTicks >= 200) {
                farTicks = 0;
                FroggyKeeper.bringNear(froggy, nearest, 30.0D, 44.0D, FroggyKeeper.Sight.HIDDEN);
            }
        } else {
            farTicks = 0;
        }
        if (!froggy.isHolding() && (froggy.getTarget() == null || !froggy.getTarget().isAlive())) {
            froggy.setTarget(nearest); // faro: sabe onde tu está
        }
    }

    @Nullable
    private static ServerPlayer nearestRunner(MinecraftServer server, FroggydudeEntity froggy) {
        ServerPlayer best = null;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative() || p.level() != froggy.level()) continue;
            if (best == null || p.distanceTo(froggy) < best.distanceTo(froggy)) best = p;
        }
        return best;
    }

    /** O Ender Dragon morreu (speedrun) ou os dias passaram (sobrevivência). */
    public static void win(MinecraftServer server, Component reason) {
        FroggyWorldData data = FroggyWorldData.get(server);
        data.manhuntActive = false;
        data.respawnAt = -1;
        data.setDirty();
        titleAll(server, Component.translatable("froggydude.manhunt.win"), reason);
        FroggydudeEntity froggy = FroggyKeeper.find(server);
        if (froggy != null) froggy.loseManhunt();
    }

    /** Ele pegou alguém. */
    public static void lose(MinecraftServer server, Player victim) {
        FroggyWorldData data = FroggyWorldData.get(server);
        data.manhuntActive = false;
        data.respawnAt = -1;
        data.setDirty();
        titleAll(server, Component.translatable("froggydude.manhunt.lose"),
                Component.translatable("froggydude.manhunt.lose.sub", victim.getName()));
        FroggydudeEntity froggy = FroggyKeeper.find(server);
        if (froggy != null) froggy.leaveAfterWin(160);
    }

    /**
     * Pressão do manhunt na escolha da estratégia (somada à Q do cérebro): no
     * speedrun, quanto mais perto do dragão, mais direto ele vem; na sobrevivência,
     * de noite e no último dia ele para de brincar.
     */
    @Nullable
    public static float[] bias(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return null;
        FroggyWorldData data = FroggyWorldData.get(server);
        if (!data.manhuntActive) return null;
        float rush = 0F;
        if (data.manhuntMode == ManhuntMode.SPEEDRUN) {
            if (player.level().dimension() == Level.NETHER) rush += 0.5F;
            if (player.level().dimension() == Level.END) rush += 1.0F;
            if (player.getInventory().contains(Items.ENDER_EYE.getDefaultInstance())
                    || player.getInventory().contains(Items.BLAZE_ROD.getDefaultInstance())) rush += 0.3F;
        } else {
            long elapsed = server.overworld().getGameTime() - data.manhuntStart;
            if (!server.overworld().isDay()) rush += 0.4F;
            if (elapsed >= (data.manhuntDays - 1) * 24000L) rush += 0.6F;
        }
        float[] b = new float[Strategy.values().length];
        b[Strategy.RUSH.ordinal()] = rush;
        b[Strategy.STALK.ordinal()] = -rush;
        return b;
    }

    public static void titleAll(MinecraftServer server, Component title, Component subtitle) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) title(p, title, subtitle);
    }

    public static void title(ServerPlayer p, Component title, Component subtitle) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        p.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }
}
