package com.froggydude.command;

import com.froggydude.brain.FroggyBrain;
import com.froggydude.brain.PlayerMemory;
import com.froggydude.brain.Strategy;
import com.froggydude.brain.Style;
import com.froggydude.config.AiLevel;
import com.froggydude.config.FroggyConfig;
import com.froggydude.entity.FroggyState;
import com.froggydude.entity.FroggydudeEntity;
import com.froggydude.world.AntiCheat;
import com.froggydude.world.FroggyKeeper;
import com.froggydude.world.FroggyWorldData;
import com.froggydude.world.Manhunt;
import com.froggydude.world.ManhuntMode;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

    /**
     * (v0.7.42) Quem mexe no anti-trapaça: o console do servidor, ou o mundo de teste ("-dev"). Um jogador com
     * "Permitir trapaças" não desliga o "Trapaças? Não" nem se perdoa (senão um comando desfazia a escolha do mundo).
     */
    private static boolean warden(CommandSourceStack s) {
        return s.hasPermission(2) && (!(s.getEntity() instanceof ServerPlayer) || FroggyWorldData.isDev(s.getServer()));
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
                .then(director())
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
                .then(Commands.literal("raiva2").requires(FroggyCommand::admin).executes(c -> rage2(c.getSource())))
                .then(Commands.literal("emboscada").requires(FroggyCommand::admin).executes(c -> ambush(c.getSource())))
                // (v0.7.58) o jumpscare sem saída: mostra se o lugar conta como beco; "forcar" = o próximo sem intervalo
                .then(Commands.literal("semsaida").requires(FroggyCommand::admin).executes(c -> noEscape(c.getSource(), false))
                        .then(Commands.literal("forcar").executes(c -> noEscape(c.getSource(), true))))
                .then(Commands.literal("irritar").requires(FroggyCommand::admin).executes(c -> irk(c.getSource())))
                .then(Commands.literal("susto").requires(FroggyCommand::admin).executes(c -> jumpscare(c.getSource())))
                // (v0.7.35) o renascer com a língua agora (sem ter que matar ele e torcer pros 35%)
                .then(Commands.literal("renascer").requires(FroggyCommand::admin).executes(c -> revive(c.getSource(), false))
                        .then(Commands.literal("explosao").executes(c -> revive(c.getSource(), true))))
                .then(Commands.literal("ragdoll").requires(FroggyCommand::admin).executes(c -> ragdoll(c.getSource())))
                .then(Commands.literal("cabeca").requires(FroggyCommand::admin).executes(c -> headGrab(c.getSource())))
                // (v0.7.42) a IA dinâmica: ver e mexer nas vontades (pra testar sem esperar a fome subir)
                .then(Commands.literal("vontades").requires(FroggyCommand::admin).executes(c -> drives(c.getSource())))
                .then(Commands.literal("vontade").requires(FroggyCommand::admin)
                        .then(Commands.argument("qual", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        new String[]{"fome", "raiva", "tedio"}, b))
                                .then(Commands.argument("valor", IntegerArgumentType.integer(0, 100))
                                        .executes(c -> setDrive(c.getSource(),
                                                StringArgumentType.getString(c, "qual"),
                                                IntegerArgumentType.getInteger(c, "valor"))))))
                // (v0.7.42) o anti-trapaça do mundo: ver, ligar/desligar e perdoar quem foi pego
                .then(Commands.literal("trapacas").requires(FroggyCommand::admin)
                        .executes(c -> cheats(c.getSource()))
                        .then(Commands.literal("sim").requires(FroggyCommand::warden).executes(c -> setCheats(c.getSource(), true)))
                        .then(Commands.literal("nao").requires(FroggyCommand::warden).executes(c -> setCheats(c.getSource(), false))))
                .then(Commands.literal("perdoar").requires(FroggyCommand::warden)
                        .then(Commands.argument("jogador", GameProfileArgument.gameProfile())
                                .executes(c -> {
                                    int n = 0;
                                    for (GameProfile gp : GameProfileArgument.getGameProfiles(c, "jogador")) {
                                        if (AntiCheat.pardon(c.getSource().getServer(), gp.getId())) n++;
                                    }
                                    int count = n;
                                    c.getSource().sendSuccess(() -> t("pardoned", count), true);
                                    return n;
                                })))
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
                                    c.getSource().sendSuccess(() -> t("forgot", count), true);
                                    return n;
                                })))
                .then(study())
                .then(Commands.literal("falas").requires(FroggyCommand::admin).then(Commands.literal("reiniciar").executes(c -> {
                    FroggyWorldData data = FroggyWorldData.get(c.getSource().getServer());
                    data.voice.load(new net.minecraft.nbt.CompoundTag());
                    data.setDirty();
                    c.getSource().sendSuccess(() -> t("lines_reset"), true);
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
                    c.getSource().sendSuccess(() -> t("invasion_now"), true);
                    return 1;
                }))
                .then(Commands.literal("reiniciar").executes(c -> {
                    com.froggydude.world.Invasion.reset(c.getSource().getServer());
                    c.getSource().sendSuccess(() -> t("invasion_reroll"), true);
                    return 1;
                }));
    }

    /**
     * (v0.7.34) /froggydude diretor [fase calma|sinais|vislumbre|caca] [sinal ribbit|sangue|passos|bicho|tocha|porta|placa]:
     * o diretor de tensão do "aparece no teu mundo" (sem nada: em que fase está). Pra testar.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> director() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("diretor").requires(FroggyCommand::admin)
                .executes(c -> {
                    c.getSource().sendSuccess(() -> com.froggydude.world.TensionDirector.status(c.getSource().getServer()), false);
                    return 1;
                });
        LiteralArgumentBuilder<CommandSourceStack> phase = Commands.literal("fase");
        for (String[] e : new String[][]{{"calma", "CALM"}, {"sinais", "SIGNS"}, {"vislumbre", "GLIMPSE"}, {"caca", "HUNT"}}) {
            phase.then(Commands.literal(e[0]).executes(c -> {
                boolean ok = com.froggydude.world.TensionDirector.force(c.getSource().getServer(),
                        com.froggydude.world.TensionDirector.Phase.valueOf(e[1]));
                c.getSource().sendSuccess(() -> ok ? com.froggydude.world.TensionDirector.status(c.getSource().getServer())
                        : t("director_off"), true);
                return ok ? 1 : 0;
            }));
        }
        root.then(phase);
        LiteralArgumentBuilder<CommandSourceStack> sign = Commands.literal("sinal");
        for (String[] e : new String[][]{{"ribbit", "RIBBIT"}, {"sangue", "BLOOD"}, {"passos", "STEPS"}, {"bicho", "EATEN"},
                {"tocha", "TORCH"}, {"porta", "DOOR"}, {"placa", "PLACARD"}}) {
            sign.then(Commands.literal(e[0]).executes(c -> {
                ServerPlayer p = c.getSource().getPlayerOrException();
                boolean ok = com.froggydude.world.TensionDirector.forceSign(c.getSource().getServer(), p,
                        com.froggydude.world.TensionDirector.Sign.valueOf(e[1]));
                c.getSource().sendSuccess(() -> t(ok ? "director_sign" : "director_sign_none"), true);
                return ok ? 1 : 0;
            }));
        }
        root.then(sign);
        return root;
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
                c.getSource().sendFailure(t("not_loaded"));
                return 0;
            }
            f.forceStakeout();
            c.getSource().sendSuccess(() -> t("stakeout"), true);
            return 1;
        }));
        root.then(Commands.literal("massacre").requires(FroggyCommand::admin).executes(c -> {
            FroggydudeEntity f = FroggyKeeper.find(c.getSource().getServer());
            if (f == null) {
                c.getSource().sendFailure(t("not_loaded"));
                return 0;
            }
            f.forceMassacre();
            c.getSource().sendSuccess(() -> t("massacre"), true);
            return 1;
        }));
        return root;
    }

    private static int cheats(CommandSourceStack src) {
        byte v = FroggyWorldData.get(src.getServer()).cheats;
        String key = v == AntiCheat.FORBIDDEN ? "cheats_no"
                : v == AntiCheat.ALLOWED ? "cheats_yes" : "cheats_old";
        src.sendSuccess(() -> t(key), false);
        return 1;
    }

    private static int setCheats(CommandSourceStack src, boolean allowed) {
        AntiCheat.set(src.getServer(), allowed);
        src.sendSuccess(() -> t(allowed ? "cheats_yes" : "cheats_no"), true);
        return 1;
    }

    /** (v0.7.28) Resposta do comando no idioma de quem joga (en_us/pt_br: froggydude.cmd.*). */
    private static MutableComponent t(String key, Object... args) {
        return Component.translatable("froggydude.cmd." + key, args);
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
        src.sendSuccess(() -> t("place_marked", kind.name().toLowerCase(Locale.ROOT)), true);
        return 1;
    }

    /** /froggydude ia [min|medio|alto|extra|max|real]: o quanto ele pensa (é escolha de quem joga, não trapaça). */
    private static LiteralArgumentBuilder<CommandSourceStack> ai() {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("ia").executes(c -> {
            AiLevel now = FroggyConfig.ai();
            c.getSource().sendSuccess(() -> t("ai_now", Component.translatable(now.key())), false);
            return 1;
        });
        for (AiLevel level : AiLevel.values()) {
            root.then(Commands.literal(level.id).requires(FroggyCommand::player).executes(c -> {
                FroggyConfig.setAi(level);
                c.getSource().sendSuccess(() -> t("ai_set", Component.translatable(level.key()), Component.translatable(level.key() + ".desc")), true);
                return 1;
            }));
        }
        return root;
    }

    private static int summon(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        FroggydudeEntity f = FroggyKeeper.spawnNear(p, 4.0D, 6.0D, FroggyKeeper.Sight.IN_VIEW, -1F);
        if (f == null) {
            src.sendFailure(t("no_ground"));
            return 0;
        }
        src.sendSuccess(() -> t("summoned"), true);
        return 1;
    }

    private static int manhunt(CommandSourceStack src, ManhuntMode mode, int days) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        Manhunt.start(p, mode, Math.max(1, days));
        int head = FroggyConfig.get(FroggyConfig.VANTAGEM_SEGUNDOS);
        Component goal = mode == ManhuntMode.SPEEDRUN ? t("manhunt_goal_dragon") : t("manhunt_goal_days", days);
        src.sendSuccess(() -> t("manhunt_started", goal, head), true);
        return 1;
    }

    private static int stopManhunt(CommandSourceStack src) {
        Manhunt.stop(src.getServer());
        src.sendSuccess(() -> t("manhunt_stopped"), true);
        return 1;
    }

    /** Faz de conta que ele te matou 3 vezes hoje: vem zombar ("Não mexe comigo") e vai embora. */
    private static int mock(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        FroggydudeEntity f = FroggyKeeper.find(src.getServer());
        if (f == null) {
            src.sendFailure(t("not_loaded_summon"));
            return 0;
        }
        f.mockNow(p);
        src.sendSuccess(() -> t("mock"), true);
        return 1;
    }

    /** A dancinha de jogador, agora. */
    private static int dance(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        FroggydudeEntity f = FroggyKeeper.find(src.getServer());
        if (f == null) {
            src.sendFailure(t("not_loaded_summon"));
            return 0;
        }
        f.danceNow(p);
        src.sendSuccess(() -> t("dance"), true);
        return 1;
    }

    private static int irk(CommandSourceStack src) {
        FroggydudeEntity f = FroggyKeeper.find(src.getServer());
        if (f == null) {
            src.sendFailure(t("not_loaded"));
            return 0;
        }
        if (f.getTarget() == null) {
            src.sendFailure(t("irk_no_target"));
            return 0;
        }
        if (!f.irk()) {
            src.sendFailure(t("irk_not_now"));
            return 0;
        }
        src.sendSuccess(() -> t("irked"), true);
        return 1;
    }

    /** (v0.7.35) Ele se despedaça agora e renasce com a língua (o teste do Revive). */
    private static int revive(CommandSourceStack src, boolean boom) {
        FroggydudeEntity f = FroggyKeeper.find(src.getServer());
        if (f == null) {
            src.sendFailure(t("not_loaded"));
            return 0;
        }
        if (f.isReviving() || !com.froggydude.entity.Revive.start(f, boom)) {
            src.sendFailure(t("revive_not_now"));
            return 0;
        }
        src.sendSuccess(() -> t("revive_started"), true);
        return 1;
    }

    /** (v0.7.41) A cutscene da cabeça agora, em quem mandou (precisa estar no sobrevivência). */
    private static int headGrab(CommandSourceStack src) {
        FroggydudeEntity f = FroggyKeeper.find(src.getServer());
        if (f == null) {
            src.sendFailure(t("not_loaded"));
            return 0;
        }
        if (!com.froggydude.entity.HeadGrab.debugBegin(f, src.getEntity())) {
            src.sendFailure(t("headgrab_not_now"));
            return 0;
        }
        src.sendSuccess(() -> t("headgrab_started"), true);
        return 1;
    }

    /** (v0.7.42) As vontades dele e o que o decisor está escolhendo (a IA dinâmica só manda na IA Real). */
    private static int drives(CommandSourceStack src) {
        FroggydudeEntity f = FroggyKeeper.find(src.getServer());
        if (f == null) {
            src.sendFailure(t("not_loaded"));
            return 0;
        }
        String mode = com.froggydude.entity.ai.FroggyDecider.active() ? f.decider().describe() : "-";
        src.sendSuccess(() -> t("drives", Math.round(f.drives().hunger * 100F), Math.round(f.drives().anger * 100F),
                Math.round(f.drives().boredom * 100F), mode), false);
        return 1;
    }

    private static int setDrive(CommandSourceStack src, String which, int value) {
        FroggydudeEntity f = FroggyKeeper.find(src.getServer());
        if (f == null) {
            src.sendFailure(t("not_loaded"));
            return 0;
        }
        if (!f.drives().set(which.toLowerCase(Locale.ROOT), value / 100F)) {
            src.sendFailure(t("drive_unknown", which));
            return 0;
        }
        return drives(src);
    }

    /** (v0.7.40) Voa longe como se levasse um tiro parado (o teste do ragdoll, sem precisar de mod de arma). */
    private static int ragdoll(CommandSourceStack src) {
        FroggydudeEntity f = FroggyKeeper.find(src.getServer());
        if (f == null) {
            src.sendFailure(t("not_loaded"));
            return 0;
        }
        if (!com.froggydude.entity.Ragdoll.debugLaunch(f, src.getEntity())) {
            src.sendFailure(t("ragdoll_not_now"));
            return 0;
        }
        src.sendSuccess(() -> t("ragdoll_started"), true);
        return 1;
    }

    /** O jumpscare raro, agora (sem esperar os 3 dias): na próxima vez que ele estiver te caçando. */
    private static int jumpscare(CommandSourceStack src) {
        FroggydudeEntity f = FroggyKeeper.find(src.getServer());
        if (f == null) {
            src.sendFailure(t("not_loaded"));
            return 0;
        }
        f.forceJumpscare();
        src.sendSuccess(() -> t("jumpscare"), true);
        return 1;
    }

    private static int phase2(CommandSourceStack src) {
        FroggydudeEntity f = FroggyKeeper.find(src.getServer());
        if (f == null) {
            src.sendFailure(t("not_loaded"));
            return 0;
        }
        f.requestPhase2();
        src.sendSuccess(() -> t("phase2"), true);
        return 1;
    }

    /** (v0.7.47) Teste: a raiva 2 agora, contra quem mandou. */
    private static int rage2(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        FroggydudeEntity f = FroggyKeeper.find(src.getServer());
        if (f == null || f.level() != p.level() || !f.beginRage2(p)) {
            src.sendFailure(t("not_loaded"));
            return 0;
        }
        src.sendSuccess(() -> t("rage2"), true);
        return 1;
    }

    /** (v0.7.54) Teste: a emboscada da floresta agora (sem precisar de mata nem do sorteio). */
    private static int noEscape(CommandSourceStack src, boolean force) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        FroggydudeEntity f = FroggyKeeper.find(src.getServer());
        if (f == null || f.level() != p.level()) {
            src.sendFailure(t("not_loaded"));
            return 0;
        }
        String msg = f.noEscapeCheck(p, force);
        if (Boolean.getBoolean("froggydude.debug")) System.out.println("[FROGGYDEBUG] semsaida: " + msg);
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int ambush(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        FroggydudeEntity f = FroggyKeeper.find(src.getServer());
        if (f == null || f.level() != p.level() || !f.beginAmbush(p)) {
            src.sendFailure(t("not_loaded"));
            return 0;
        }
        src.sendSuccess(() -> t("ambush"), true);
        return 1;
    }

    /** Teste: ele aparece atrás de ti e faz a cutscene do golpe final (em sobrevivência). */
    private static int finisher(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer p = src.getPlayerOrException();
        FroggydudeEntity f = FroggyKeeper.find(src.getServer());
        if (f == null || f.level() != p.level()) {
            src.sendFailure(t("not_loaded_summon"));
            return 0;
        }
        if (p.isCreative() || p.isSpectator()) {
            src.sendFailure(t("survival_only"));
            return 0;
        }
        // de frente pra onde tu olha: a cabeça vai cair pra lá
        net.minecraft.world.phys.Vec3 look = p.getLookAngle().multiply(1, 0, 1).normalize();
        f.teleportTo(p.getX() - look.x * 1.4D, p.getY(), p.getZ() - look.z * 1.4D);
        com.froggydude.player.FinisherTracker.mark(p, f.getId());
        f.requestFinisher(p);
        src.sendSuccess(() -> t("finisher"), true);
        return 1;
    }

    private static int info(CommandSourceStack src) {
        MinecraftServer server = src.getServer();
        FroggyWorldData data = FroggyWorldData.get(server);
        boolean dev = FroggyWorldData.isDev(server);
        MutableComponent out = t("info.world", t(dev ? "info.world_dev" : "info.world_normal"));
        FroggydudeEntity f = FroggyKeeper.find(server);
        MutableComponent froggy;
        if (f != null) {
            froggy = t("info.froggy_here", f.level().dimension().location().getPath(),
                    String.format(Locale.ROOT, "%.0f %.0f %.0f", f.getX(), f.getY(), f.getZ()),
                    String.format(Locale.ROOT, "%.0f/%.0f", f.getHealth(), f.getMaxHealth()), f.getFroggyState().name());
            if (f.getStrategy() != null) froggy.append(t("info.strategy", f.getStrategy().label));
        } else if (data.froggyAlive) {
            froggy = t("info.froggy_unloaded");
        } else {
            froggy = t(data.respawnAt >= 0 ? "info.froggy_dead_back" : "info.froggy_none");
        }
        out.append("\n").append(t("info.froggy", froggy));
        MutableComponent hunt;
        if (data.manhuntActive) {
            long elapsed = server.overworld().getGameTime() - data.manhuntStart;
            hunt = data.manhuntMode == ManhuntMode.SPEEDRUN ? t("info.manhunt_speedrun")
                    : t("info.manhunt_survival", elapsed / 24000L + 1, data.manhuntDays);
            if (!data.released) hunt.append(t("info.manhunt_headstart"));
        } else {
            hunt = t("info.manhunt_off");
        }
        out.append("\n").append(t("info.manhunt", hunt));
        src.sendSuccess(() -> out, false);
        return 1;
    }

    private static int brain(CommandSourceStack src, ServerPlayer target) {
        FroggyWorldData data = FroggyWorldData.get(src.getServer());
        PlayerMemory m = data.brain.find(target.getUUID());
        if (m == null) {
            src.sendSuccess(() -> t("brain_unknown", target.getScoreboardName()), false);
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
                FroggyState.TONGUE_CAPTURE, FroggyState.TONGUE_THROW, FroggyState.JUMP_PIN, FroggyState.HIGH_JUMP,
                FroggyState.SKY_DROP}) {
            float[] hm = m.attacks.get(s.name());
            if (hm == null) continue;
            sb.append(String.format(Locale.ROOT, " %s %.0f%%,", s.name().toLowerCase(Locale.ROOT),
                    FroggyBrain.meanHitRate(m, s.name()) * 100F));
        }
        return sb.toString();
    }
}
