package com.froggydude.command;

import com.froggydude.brain.FroggyBrain;
import com.froggydude.brain.PlayerMemory;
import com.froggydude.brain.Strategy;
import com.froggydude.brain.Style;
import com.froggydude.config.AiLevel;
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
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
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
                .then(ai())
                .then(invasion())
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
                .then(Commands.literal("irritar").requires(FroggyCommand::admin).executes(c -> irk(c.getSource())))
                .then(Commands.literal("susto").requires(FroggyCommand::admin).executes(c -> jumpscare(c.getSource())))
                // jeito de jogador, pra testar sem ter que morrer 3 vezes
                .then(Commands.literal("zombar").requires(FroggyCommand::admin).executes(c -> mock(c.getSource())))
                .then(Commands.literal("dancar").requires(FroggyCommand::admin).executes(c -> dance(c.getSource())))
                .then(Commands.literal("golpefinal").requires(FroggyCommand::admin).executes(c -> finisher(c.getSource())))
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
                .then(study())
                .then(Commands.literal("falas").requires(FroggyCommand::admin).then(Commands.literal("reiniciar").executes(c -> {
                    FroggyWorldData data = FroggyWorldData.get(c.getSource().getServer());
                    data.voice.load(new net.minecraft.nbt.CompoundTag());
                    data.setDirty();
                    c.getSource().sendSuccess(() -> Component.literal("Falas de volta pro começo (\"Hey, humans!\")."), true);
                    return 1;
                }))));
    }

    /**
     * /froggydude estudo: o que ele sabe da tua rotina (IA Real, v0.6). Pra testar:
     * "lugar <tipo>" marca onde tu está como um lugar que tu usa (já na hora de voltar),
     * "tocaia" manda ele esperar lá agora,
     * "massacre" manda ele já pra tua vila (a cama/baú marcados perto de aldeões).
     */
    /** /froggydude invasao [ligar|desligar|agora|reiniciar]: o "Froggy's in your world" (sem nada: como está). */
    private static LiteralArgumentBuilder<CommandSourceStack> invasion() {
        return Commands.literal("invasao").requires(FroggyCommand::admin)
                .executes(c -> {
                    c.getSource().sendSuccess(() -> com.froggydude.world.Invasion.status(c.getSource().getServer()), false);
                    return 1;
                })
                .then(Commands.literal("ligar").executes(c -> toggleInvasion(c.getSource(), true)))
                .then(Commands.literal("desligar").executes(c -> toggleInvasion(c.getSource(), false)))
                .then(Commands.literal("agora").executes(c -> {
                    com.froggydude.world.Invasion.arriveNow(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal("Ele chega agora (teste)."), true);
                    return 1;
                }))
                .then(Commands.literal("reiniciar").executes(c -> {
                    com.froggydude.world.Invasion.reset(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> Component.literal("Dia sorteado de novo (escondido)."), true);
                    return 1;
                }));
    }

    private static int toggleInvasion(CommandSourceStack src, boolean on) {
        com.froggydude.config.FroggyConfig.setInvasion(on);
        src.sendSuccess(() -> com.froggydude.world.Invasion.status(src.getServer()), true);
        return 1;
    }

    private static LiteralArgumentBuilder<CommandSourceStack> study() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("estudo")
                .executes(c -> showStudy(c.getSource()));
        LiteralArgumentBuilder<CommandSourceStack> place = Commands.literal("lugar").requires(FroggyCommand::admin);
        for (com.froggydude.brain.PreyStudy.Kind kind : com.froggydude.brain.PreyStudy.Kind.values()) {
            String name = switch (kind) {
                case STORAGE -> "bau";
                case BED -> "cama";
                case WORK -> "bancada";
                case MINE -> "mina";
            };
            place.then(Commands.literal(name).executes(c -> markPlace(c.getSource(), kind)));
        }
        root.then(place);
        root.then(Commands.literal("tocaia").requires(FroggyCommand::admin).executes(c -> {
            FroggydudeEntity f = FroggyKeeper.find(c.getSource().getServer());
            if (f == null) {
                c.getSource().sendFailure(Component.literal("O FroggyDude não está carregado."));
                return 0;
            }
            f.forceStakeout();
            c.getSource().sendSuccess(() -> Component.literal("Ele vai te esperar no lugar que tu mais usa."), true);
            return 1;
        }));
        root.then(Commands.literal("massacre").requires(FroggyCommand::admin).executes(c -> {
            FroggydudeEntity f = FroggyKeeper.find(c.getSource().getServer());
            if (f == null) {
                c.getSource().sendFailure(Component.literal("O FroggyDude não está carregado."));
                return 0;
            }
            f.forceMassacre();
            c.getSource().sendSuccess(() -> Component.literal("Ele vai na tua vila comer os aldeões (se tu mora numa)."), true);
            return 1;
        }));
        return root;
    }

    private static int showStudy(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        var st = FroggyWorldData.get(src.getServer()).brain.memory(p.getUUID(), p.getScoreboardName()).study;
        long now = p.serverLevel().getGameTime();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "Medo: %.0f/100, paranoia agora: %.0f", st.fear(now),
                com.froggydude.player.Paranoia.score(p)));
        if (st.places.isEmpty()) sb.append("\nAinda não sabe onde tu passa o tempo.");
        for (var q : st.places) {
            sb.append(String.format(Locale.ROOT, "\n%s em %d %d %d: %d volta(s), %s, peso %.1f%s", q.kind.name().toLowerCase(Locale.ROOT),
                    q.pos.getX(), q.pos.getY(), q.pos.getZ(), q.visits,
                    q.gap > 0 ? String.format(Locale.ROOT, "volta a cada %.0f s (%.0f%% da hora)", q.gap / 20F, q.due(now) * 100F) : "sem ritmo ainda",
                    q.weight, q.roofed ? ", com teto" : ""));
        }
        String text = sb.toString();
        src.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }

    private static int markPlace(CommandSourceStack src, com.froggydude.brain.PreyStudy.Kind kind) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        FroggyWorldData data = FroggyWorldData.get(src.getServer());
        var st = data.brain.memory(p.getUUID(), p.getScoreboardName()).study;
        long now = p.serverLevel().getGameTime();
        net.minecraft.core.BlockPos at = p.blockPosition();
        st.visit(kind, at, p.level().dimension().location().toString(), now, !p.level().canSeeSky(at.above()));
        // como se ela voltasse ali a cada minuto e já fizesse 2 minutos que saiu
        for (var q : st.places) {
            if (q.kind == kind && q.pos.distSqr(at) <= 16) {
                q.visits = Math.max(q.visits, 3);
                q.gap = 1200F;
                q.last = now - 2400L;
                q.weight = Math.max(q.weight, 4F);
            }
        }
        data.setDirty();
        src.sendSuccess(() -> Component.literal("Ele anotou: tu usa " + kind.name().toLowerCase(Locale.ROOT) + " aqui."), true);
        return 1;
    }

    /** /froggydude ia [min|medio|alto|extra|max|real]: o quanto ele pensa (é escolha de quem joga, não trapaça). */
    private static LiteralArgumentBuilder<CommandSourceStack> ai() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ia").executes(c -> {
            AiLevel now = FroggyConfig.ai();
            c.getSource().sendSuccess(() -> Component.literal("IA do FroggyDude: ").append(Component.translatable(now.key()))
                    .append(". Muda com /froggydude ia <min|medio|alto|extra|max|real>."), false);
            return 1;
        });
        for (AiLevel level : AiLevel.values()) {
            root.then(Commands.literal(level.id).requires(FroggyCommand::player).executes(c -> {
                FroggyConfig.setAi(level);
                c.getSource().sendSuccess(() -> Component.literal("IA do FroggyDude: ").append(Component.translatable(level.key()))
                        .append(" - ").append(Component.translatable(level.key() + ".desc")), true);
                return 1;
            }));
        }
        return root;
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

    /** Faz de conta que ele te matou 3 vezes hoje: vem zombar ("Não mexe comigo") e vai embora. */
    private static int mock(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        FroggydudeEntity f = FroggyKeeper.find(src.getServer());
        if (f == null) {
            src.sendFailure(Component.literal("O FroggyDude não está carregado (use /froggydude invocar)."));
            return 0;
        }
        f.mockNow(p);
        src.sendSuccess(() -> Component.literal("Ele vem zombar de ti e ir embora."), true);
        return 1;
    }

    /** A dancinha de jogador, agora. */
    private static int dance(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        FroggydudeEntity f = FroggyKeeper.find(src.getServer());
        if (f == null) {
            src.sendFailure(Component.literal("O FroggyDude não está carregado (use /froggydude invocar)."));
            return 0;
        }
        f.danceNow(p);
        src.sendSuccess(() -> Component.literal("Dancinha."), true);
        return 1;
    }

    private static int irk(CommandSourceStack src) {
        FroggydudeEntity f = FroggyKeeper.find(src.getServer());
        if (f == null) {
            src.sendFailure(Component.literal("O FroggyDude não está carregado."));
            return 0;
        }
        if (f.getTarget() == null) {
            src.sendFailure(Component.literal("Ele só se irrita partindo pra cima de alguém."));
            return 0;
        }
        if (!f.irk()) {
            src.sendFailure(Component.literal("Agora não: já transformado, cansado ou a transformação recarregando."));
            return 0;
        }
        src.sendSuccess(() -> Component.literal("Irritado: os ribbits e a transformação."), true);
        return 1;
    }

    /** O jumpscare raro, agora (sem esperar os 3 dias): na próxima vez que ele estiver te caçando. */
    private static int jumpscare(CommandSourceStack src) {
        FroggydudeEntity f = FroggyKeeper.find(src.getServer());
        if (f == null) {
            src.sendFailure(Component.literal("O FroggyDude não está carregado."));
            return 0;
        }
        f.forceJumpscare();
        src.sendSuccess(() -> Component.literal("Jumpscare armado: quando ele estiver te caçando, ele some..."), true);
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

    /** Teste: ele aparece atrás de ti e faz a cutscene do golpe final (em sobrevivência). */
    private static int finisher(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        FroggydudeEntity f = FroggyKeeper.find(src.getServer());
        if (f == null || f.level() != p.level()) {
            src.sendFailure(Component.literal("O FroggyDude não está carregado (use /froggydude invocar)."));
            return 0;
        }
        if (p.isCreative() || p.isSpectator()) {
            src.sendFailure(Component.literal("Só em sobrevivência/aventura."));
            return 0;
        }
        // de frente pra onde tu olha: a cabeça vai cair pra lá
        net.minecraft.world.phys.Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        f.teleportTo(p.getX() - look.x * 1.4D, p.getY(), p.getZ() - look.z * 1.4D);
        com.froggydude.player.FinisherTracker.mark(p, f.getId());
        f.requestFinisher(p);
        src.sendSuccess(() -> Component.literal("Golpe final."), true);
        return 1;
    }

    private static int info(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        FroggyWorldData data = FroggyWorldData.get(server);
        boolean dev = FroggyWorldData.isDev(server);
        StringBuilder sb = new StringBuilder();
        sb.append("Mundo: ").append(dev ? "-dev (o cérebro conta no chat o que aprende)" : "normal");
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
