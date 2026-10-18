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

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Violência gráfica (sangue, braço e cabeça arrancados, o corpo que fica).").push("gore");
        GORE = b.comment("OFF = desligado, LIGHT = leve, MEDIUM = médio, HEAVY = pesado, EXTREME = extremo.")
                .defineEnum("nivel", GoreLevel.HEAVY);
        WARNING_SEEN = b.comment("false = mostra a tela de aviso de novo na próxima vez que o jogo abrir.")
                .define("aviso_visto", false);
        b.pop();
        SPEC = b.build();
    }

    private FroggyClientConfig() {
    }

    public static GoreLevel gore() {
        return SPEC.isLoaded() ? GORE.get() : GORE.getDefault();
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
