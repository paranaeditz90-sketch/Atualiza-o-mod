package com.froggydude.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * config/froggydude-client.toml - o que cada jogador escolhe pra si (o gore).
 * Muda pela tela do mod (Mods > Froggydude > Configurar); dá pra editar o
 * arquivo também.
 */
public final class FroggyClientConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.EnumValue<GoreLevel> GORE;
    public static final ForgeConfigSpec.BooleanValue WARNING_SEEN;
    public static final ForgeConfigSpec.IntValue PIECES_SECONDS;
    public static final ForgeConfigSpec.IntValue PIECES_MAX;
    public static final ForgeConfigSpec.IntValue PIECES_SHARDS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Violência gráfica (sangue, braço e cabeça arrancados, o corpo que fica).").push("gore");
        GORE = b.comment("OFF = desligado, LIGHT = leve, MEDIUM = médio, HEAVY = pesado, EXTREME = extremo.")
                .defineEnum("nivel", GoreLevel.HEAVY);
        WARNING_SEEN = b.comment("false = mostra a tela de aviso de novo na próxima vez que o jogo abrir.")
                .define("aviso_visto", false);
        b.pop();
        b.comment("Quem o FroggyDude mata se desmonta em pedaços com física (gore Desligado = morte normal).")
                .push("pedacos");
        PIECES_SECONDS = b.comment("Quanto tempo os pedaços ficam no chão (segundos).")
                .defineInRange("segundos", 90, 10, 600);
        PIECES_SHARDS = b.comment("Em quantos cacos cada corpo estilhaça (o vídeo do FroggyDude: ~60).")
                .defineInRange("cacos", 60, 6, 150);
        PIECES_MAX = b.comment("Máximo de cacos no mundo ao mesmo tempo (celular fraco: diminua).")
                .defineInRange("maximo", 300, 20, 1000);
        b.pop();
        SPEC = b.build();
    }

    private FroggyClientConfig() {
    }

    public static GoreLevel gore() {
        return SPEC.isLoaded() ? GORE.get() : GORE.getDefault();
    }

    public static int piecesSeconds() {
        return SPEC.isLoaded() ? PIECES_SECONDS.get() : PIECES_SECONDS.getDefault();
    }

    public static int piecesShards() {
        return SPEC.isLoaded() ? PIECES_SHARDS.get() : PIECES_SHARDS.getDefault();
    }

    public static int piecesMax() {
        return SPEC.isLoaded() ? PIECES_MAX.get() : PIECES_MAX.getDefault();
    }

    public static boolean warningSeen() {
        return SPEC.isLoaded() && WARNING_SEEN.get();
    }

    /** Salva a escolha (e marca que o aviso já foi visto). */
    public static void choose(GoreLevel level) {
        if (!SPEC.isLoaded()) return;
        GORE.set(level);
        WARNING_SEEN.set(true);
        SPEC.save();
    }
}
