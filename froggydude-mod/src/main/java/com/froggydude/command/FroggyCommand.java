package com.froggydude.command;

import com.froggydude.anticheat.Invasion;
import com.froggydude.brain.FroggyBrain;
import com.froggydude.brain.PlayerMemory;
import com.froggydude.brain.Strategy;
import com.froggydude.brain.Style;
import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.world.FroggyKeeper;
import com.froggydude.world.FroggyWorldData;
import com.froggydude.world.Manhunt;
import com.froggydude.world.ManhuntMode;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

/**
 * /froggydude. Começar/parar o manhunt, ver o info e o cérebro vale no mundo de
 * um jogador mesmo sem cheats (começar a caçada não é trapaça); o resto precisa
 * de OP/cheats. Os textos são em português: é ferramenta de quem joga/testa o mod.
 */
public final class FroggyCommand {

    private FroggyCommand() {
    }

    /** OP / cheats ligados. */
    private static boolean admin(CommandSourceStack s) {
        return s.hasPermission(2);
    }

    /** Quem joga: OP, ou o dono do mundo de um jogador só. */
    private static boolean player(CommandSourceStack s) {
        return s.hasPermission(2) || s.getServer().isSingleplayer();
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("froggydude").requires(FroggyCommand::player)
                .then(Commands.literal("invocar").requires(FroggyCommand::admin).executes(c -> summon(c.getSource())))
                .then(Commands.literal("info").executes(c -> info(c.getSource())))
                .then(Commands.literal("manhunt")
                        .then(Commands.literal("speedrun")
                                .executes(c -> manhunt(c.getSource(), ManhuntMode.SPEEDRUN, 0)))
                        .then(Commands.literal("sobrevivencia")
                                .executes(c -> manhunt(c.getSource(), ManhuntMode.SOBREVIVENCIA,
                                        FroggyConfig.get(FroggyConfig.MANHUNT_DIAS)))
                                .then(Commands.argument("dias", IntegerArgumentType.integer(1, 100))
                                        .executes(c -> manhunt(c.getSource(), ManhuntMode.SOBREVIVENCIA,
                                                IntegerArgumentType.getInteger(c, "dias")))))
                        .then(Commands.literal("iniciar")
                                .executes(c -> manhunt(c.getSource(), FroggyConfig.get(FroggyConfig.MANHUNT_MODO),
                                        FroggyConfig.get(FroggyConfig.MANHUNT_DIAS))))
                        .then(Commands.literal("parar").executes(c -> stopManhunt(c.getSource()))))
                .then(Commands.literal("fase2").requires(FroggyCommand::admin).executes(c -> phase2(c.getSource())))
                .then(Commands.literal("cerebro")
                        .executes(c -> brain(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.argument("jogador", EntityArgument.player()).requires(FroggyCommand::admin)
                                .executes(c -> brain(c.getSource(), EntityArgument.getPlayer(c, "jogador")))))
                .then(Commands.literal("esquecer").requires(FroggyCommand::admin)
                        .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                .executes(c -> {
                                    int n = 0;
                                    FroggyWorldData data = FroggyWorldData.get(c.getSource().getServer());
                                    for (GameProfile gp : GameProfileArgument.getGameProfiles(c, "jogador")) {
                                        data.brain.forget(gp.getId());
                                        n++;
                                    }
                                    data.setDirty();
                                    int count = n;
                                    c.getSource().sendSuccess(() -> Component.literal("Ele esqueceu tudo sobre " + count + " jogador(es)."), true);
                                    return n;
                                })))
                .then(Commands.literal("falas").requires(FroggyCommand::admin).then(Commands.literal("reiniciar").executes(c -> {
                    FroggyWorldData data = FroggyWorldData.get(c.getSource().getServer());
                    data.voice.load(new net.minecraft.nbt.CompoundTag());
                    data.setDirty();
                    c.getSource().sendSuccess(() -> Component.literal("Falas de volta pro começo (\"Hey, humans!\")."), true);
                    return 1;
                })))
                .then(Commands.literal("trapaca").requires(FroggyCommand::admin).then(Commands.literal("testar")
                        .executes(c -> testInvasion(c.getSource(), c.getSource().getPlayerOrException()))
                        .then(Commands.argument("jogador", EntityArgument.player())
                                .executes(c -> testInvasion(c.getSource(), EntityArgument.getPlayer(c, "jogador"))))))
                .then(Commands.literal("perdoar").requires(FroggyCommand::admin)
                        .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                .executes(c -> {
                                    int n = 0;
                                    FroggyWorldData data = FroggyWorldData.get(c.getSource().getServer());
                                    for (GameProfile gp : GameProfileArgument.getGameProfiles(c, "jogador")) {
                                        if (data.bans.remove(gp.getId()) != null) n++;
                                        data.strikes.remove(gp.getId());
                                    }
                                    data.setDirty();
                                    int count = n;
                                    c.getSource().sendSuccess(() -> Component.literal("Perdoado(s): " + count + "."), true);
                                    return n;
                                }))));
    }

    private static int summon(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        FroggydudeEntity f = FroggyKeeper.spawnNear(p, 4.0D, 6.0D, FroggyKeeper.Sight.IN_VIEW, -1F);
        if (f == null) {
            src.sendFailure(Component.literal("Não achei chão livre perto de ti."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Ele está aqui (só existe um FroggyDude por mundo: o anterior sumiu)."), true);
        return 1;
    }

    private static int manhunt(CommandSourceStack src, ManhuntMode mode, int days) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        Manhunt.start(p, mode, Math.max(1, days));
        int head = FroggyConfig.get(FroggyConfig.VANTAGEM_SEGUNDOS);
        String goal = mode == ManhuntMode.SPEEDRUN ? "matar o Ender Dragon" : "sobreviver " + days + " dia(s)";
        src.sendSuccess(() -> Component.literal("Manhunt começou: " + goal + ". Vantagem de " + head + " s."), true);
        return 1;
    }

    private static int stopManhunt(CommandSourceStack src) {
        Manhunt.stop(src.getServer());
        src.sendSuccess(() -> Component.literal("Manhunt parado."), true);
        return 1;
    }

    private static int phase2(CommandSourceStack src) {
        FroggydudeEntity f = FroggyKeeper.find(src.getServer());
        if (f == null) {
            src.sendFailure(Component.literal("O FroggyDude não está carregado."));
            return 0;
        }
        f.requestPhase2();
        src.sendSuccess(() -> Component.literal("Fase 2."), true);
        return 1;
    }

    private static int testInvasion(CommandSourceStack src, ServerPlayer target) {
        Invasion.start(target, true);
        src.sendSuccess(() -> Component.literal("Invasão de teste em " + target.getScoreboardName()
                + " (sem banimento; as tochas por perto apagam de verdade)."), true);
        return 1;
    }

    private static int info(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        FroggyWorldData data = FroggyWorldData.get(server);
        boolean dev = FroggyWorldData.isDev(server);
        StringBuilder sb = new StringBuilder();
        sb.append("Mundo: ").append(dev ? "-dev (anti-trapaça desligado)" : "normal")
                .append(" | anti-trapaça no config: ").append(FroggyConfig.get(FroggyConfig.ANTI_TRAPACA) ? "ligado" : "desligado");
        FroggydudeEntity f = FroggyKeeper.find(server);
        sb.append("\nFroggyDude: ");
        if (f != null) {
            sb.append(String.format(Locale.ROOT, "%s %.0f %.0f %.0f, vida %.0f/%.0f, estado %s",
                    f.level().dimension().location().getPath(), f.getX(), f.getY(), f.getZ(),
                    f.getHealth(), f.getMaxHealth(), f.getFroggyState().name()));
            if (f.getStrategy() != null) sb.append(", estratégia ").append(f.getStrategy().label);
        } else if (data.froggyAlive) {
            sb.append("vivo, num lugar do mapa que não está carregado");
        } else {
            sb.append(data.respawnAt >= 0 ? "morto (volta já já)" : "nenhum");
        }
        sb.append("\nManhunt: ");
        if (data.manhuntActive) {
            long elapsed = server.overworld().getGameTime() - data.manhuntStart;
            sb.append(data.manhuntMode == ManhuntMode.SPEEDRUN ? "speedrun (matar o dragão)"
                    : "sobrevivência, dia " + (elapsed / 24000L + 1) + "/" + data.manhuntDays);
            if (!data.released) sb.append(", ainda na vantagem");
        } else {
            sb.append("parado");
        }
        if (!data.bans.isEmpty()) sb.append("\nBanidos: ").append(String.join(", ", data.bans.values()));
        String text = sb.toString();
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int brain(CommandSourceStack src, ServerPlayer target) {
        FroggyWorldData data = FroggyWorldData.get(src.getServer());
        PlayerMemory m = data.brain.find(target.getUUID());
        if (m == null) {
            src.sendSuccess(() -> Component.literal("Ele ainda não sabe nada sobre " + target.getScoreboardName() + "."), false);
            return 0;
        }
        String text = describe(m, target.getScoreboardName());
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    /** O que ele aprendeu, em português. */
    public static String describe(PlayerMemory m, String name) {
        StringBuilder sb = new StringBuilder();
        sb.append("[cérebro] ").append(name).append(": ").append(m.hunts).append(" caçada(s), ")
                .append(m.kills).append(" morte(s), ").append(m.escapes).append(" fuga(s); ele morreu ")
                .append(m.froggyDeaths).append("x");
        sb.append("\n  estilo:");
        for (Style s : Style.OBSERVED) {
            float share = m.share(s);
            if (share >= 0.05F) sb.append(String.format(Locale.ROOT, " %s %.0f%%,", s.label, share * 100F));
        }
        Style ctx = m.dominantStyle();
        sb.append("\n  contra quem ").append(ctx == Style.UNKNOWN ? "ainda não deu pra saber" : ctx.label).append(":");
        for (Strategy a : Strategy.values()) {
            int n = m.n[ctx.ordinal()][a.ordinal()];
            if (n == 0) sb.append(" ").append(a.label).append(" (nunca testou) |");
            else sb.append(String.format(Locale.ROOT, " %s %+.1f (%dx) |", a.label, m.q[ctx.ordinal()][a.ordinal()], n));
        }
        sb.append("\n  acerto dos ataques:");
        for (FroggyState s : new FroggyState[]{FroggyState.BITE, FroggyState.TONGUE_WHIP, FroggyState.TONGUE_GRAB,
                FroggyState.TONGUE_CAPTURE, FroggyState.JUMP_PIN, FroggyState.HIGH_JUMP, FroggyState.SKY_DROP}) {
            float[] hm = m.attacks.get(s.name());
            if (hm == null) continue;
            sb.append(String.format(Locale.ROOT, " %s %.0f%%,", s.name().toLowerCase(Locale.ROOT),
                    FroggyBrain.meanHitRate(m, s.name()) * 100F));
        }
        return sb.toString();
    }
}
