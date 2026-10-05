package com.froggydude.anticheat;

import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.world.FroggyKeeper;
import com.froggydude.world.FroggyWorldData;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Anti-trapaça (PLANO, seção 7). Vale com um manhunt rolando OU quando o
 * FroggyDude está te caçando (fugir dele pro criativo é trapaça), com
 * {@code anti_trapaca.ligado = true} e NUNCA em mundo com "-dev" no nome.
 * Pegou: ele vira pra ti, solta a reação dele (falada, não escrita) e começa a
 * invasão (Invasion).
 */
public final class AntiCheat {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Comandos que são trapaça no meio de um manhunt (o /gamemode é pego pelo evento de modo de jogo). */
    private static final Set<String> CHEAT_COMMANDS = Set.of(
            "give", "tp", "teleport", "effect", "kill", "time", "enchant", "xp", "experience", "attribute",
            "item", "setblock", "fill", "clone", "summon", "gamerule", "difficulty", "weather", "locate",
            "spreadplayers", "data", "loot", "ride", "tick");
    /** Mods de trapaça conhecidos (Viltrumita: viltrumitecore-forge). */
    private static final String[] CHEAT_MODS = {"viltrumitecore", "viltrumite"};

    /** Pra não contar a mesma trapaça várias vezes seguidas (voando, por exemplo). */
    private static final Map<UUID, Long> lastOffense = new HashMap<>();

    private AntiCheat() {
    }

    public static boolean active(MinecraftServer server) {
        return FroggyConfig.get(FroggyConfig.ANTI_TRAPACA) && !FroggyWorldData.isDev(server);
    }

    /** Esse jogador está "valendo"? Manhunt rolando, ou o FroggyDude caçando ele agora. */
    public static boolean watching(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !active(server)) return false;
        if (FroggyWorldData.get(server).manhuntActive) return true;
        FroggydudeEntity froggy = FroggyKeeper.find(server);
        return froggy != null && froggy.level() == player.level() && froggy.distanceTo(player) < 96.0F
                && froggy.isHunting(player);
    }

    public static boolean isCheatCommand(String root) {
        return CHEAT_COMMANDS.contains(root);
    }

    public static void onManhuntStart(MinecraftServer server) {
        lastOffense.clear();
        if (!active(server)) return;
        for (String id : CHEAT_MODS) {
            if (ModList.get().isLoaded(id)) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) offense(p, "mod " + id);
                return;
            }
        }
    }

    /** Pegou trapaça: ele reage e vem a invasão. */
    public static void offense(ServerPlayer player, String what) {
        MinecraftServer server = player.getServer();
        if (server == null || !watching(player) || Invasion.isRunning(player)) return;
        long now = server.overworld().getGameTime();
        Long last = lastOffense.get(player.getUUID());
        if (last != null && now - last < 200) return;
        lastOffense.put(player.getUUID(), now);
        FroggyWorldData data = FroggyWorldData.get(server);
        int strikes = data.strikes.merge(player.getUUID(), 1, Integer::sum);
        data.setDirty();
        LOGGER.info("[FroggyDude] trapaça de {} ({}), advertência {}", player.getScoreboardName(), what, strikes);
        Invasion.start(player, false);
    }
}
