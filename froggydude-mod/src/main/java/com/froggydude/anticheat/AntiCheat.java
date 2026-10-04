package com.froggydude.anticheat;

import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.entity.voice.VoiceSituation;
import com.froggydude.network.ModNetwork;
import com.froggydude.world.FroggyKeeper;
import com.froggydude.world.FroggyWorldData;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Anti-trapaça do manhunt (PLANO, seção 7). Só vale com um manhunt rolando,
 * com {@code anti_trapaca.ligado = true} e NUNCA em mundo com "-dev" no nome.
 * 1ª trapaça: aviso ("I SEE YOU") e ele aparece te olhando. 2ª: a invasão.
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
        return FroggyConfig.get(FroggyConfig.ANTI_TRAPACA) && !FroggyWorldData.isDev(server)
                && FroggyWorldData.get(server).manhuntActive;
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

    /** Pegou trapaça: conta e reage. */
    public static void offense(ServerPlayer player, String what) {
        MinecraftServer server = player.getServer();
        if (server == null || !active(server) || Invasion.isRunning(player)) return;
        long now = server.overworld().getGameTime();
        Long last = lastOffense.get(player.getUUID());
        if (last != null && now - last < 200) return;
        lastOffense.put(player.getUUID(), now);
        FroggyWorldData data = FroggyWorldData.get(server);
        int strikes = data.strikes.merge(player.getUUID(), 1, Integer::sum);
        data.setDirty();
        LOGGER.info("[FroggyDude] trapaça de {} ({}), advertência {}", player.getScoreboardName(), what, strikes);
        if (strikes <= 1) {
            warn(player);
        } else {
            Invasion.start(player, false);
        }
    }

    /**
     * 1ª vez: a tela dá um "glitch", aparece no chat {@code <FroggyDude> I SEE YOU, nome}
     * e ele está ali, parado, te olhando. Mais nada.
     */
    public static void warn(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("<FroggyDude> I SEE YOU, " + player.getScoreboardName()));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false));
        ModNetwork.sendShake(player, 1.2F, 16);
        MinecraftServer server = player.getServer();
        if (server == null) return;
        FroggydudeEntity froggy = FroggyKeeper.find(server);
        if (froggy == null) {
            froggy = FroggyKeeper.spawnNear(player, 7.0D, 10.0D, FroggyKeeper.Sight.IN_VIEW, -1F);
        } else {
            FroggyKeeper.bringNear(froggy, player, 7.0D, 10.0D, FroggyKeeper.Sight.IN_VIEW);
            froggy = FroggyKeeper.find(server);
        }
        if (froggy != null) {
            // parado te olhando uns segundos (ou até o fim da vantagem do manhunt)
            FroggyWorldData data = FroggyWorldData.get(server);
            long headStart = data.released ? 0 : data.releaseAt - server.overworld().getGameTime();
            froggy.holdStill((int) Math.max(80, headStart), player);
            froggy.speak(VoiceSituation.FOUND, true);
        }
    }
}
