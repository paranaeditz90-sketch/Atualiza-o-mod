package com.froggydude.config;

import com.froggydude.world.ManhuntMode;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * config/froggydude-common.toml (dá pra editar no celular com qualquer
 * gerenciador de arquivos; o jogo relê ao abrir o mundo).
 */
public final class FroggyConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.EnumValue<ManhuntMode> MANHUNT_MODO;
    public static final ForgeConfigSpec.IntValue MANHUNT_DIAS;
    public static final ForgeConfigSpec.IntValue VANTAGEM_SEGUNDOS;
    public static final ForgeConfigSpec.IntValue VOLTA_SEGUNDOS;
    public static final ForgeConfigSpec.DoubleValue CACA_BLOCOS_POR_SEGUNDO;
    public static final ForgeConfigSpec.EnumValue<AiLevel> IA;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Manhunt: /froggydude manhunt iniciar").push("manhunt");
        MANHUNT_MODO = b.comment("speedrun = matar o Ender Dragon antes dele te pegar; sobrevivencia = sobreviver X dias.")
                .defineEnum("modo", ManhuntMode.SPEEDRUN);
        MANHUNT_DIAS = b.comment("Dias pra sobreviver no modo sobrevivencia.")
                .defineInRange("dias", 5, 1, 100);
        VANTAGEM_SEGUNDOS = b.comment("Vantagem (segundos) antes dele ser solto, igual ao manhunt do Dream.")
                .defineInRange("vantagem_segundos", 30, 0, 300);
        VOLTA_SEGUNDOS = b.comment("Se ele morrer no meio do manhunt, volta depois desse tempo (segundos).")
                .defineInRange("volta_segundos", 60, 10, 1200);
        b.pop();

        b.push("froggydude");
        CACA_BLOCOS_POR_SEGUNDO = b.comment("Velocidade do galope caçando (blocos/s). Original: ~15. Um jogador correndo: ~5,6.")
                .defineInRange("caca_blocos_por_segundo", 15.0D, 6.0D, 20.0D);
        IA = b.comment("O quanto ele pensa: MIN, MEDIO, ALTO (o de sempre), EXTRA, MAX, REAL.",
                        "Muda alcance de visão/faro, reação, previsão, táticas (cercar, cortar caminho, pôr bloco,",
                        "arrombar, sabotar), o uso do que ele aprendeu de cada jogador e, no EXTRA/MAX, truques novos.",
                        "REAL: a esperteza do MAX com o jeito do FroggyDude dos vídeos (sabe o que está na tua tela).",
                        "Também pela tela do mod (Mods > Froggydude > Configurar) ou /froggydude ia <nível>.")
                .defineEnum("ia", AiLevel.ALTO);
        b.pop();

        SPEC = b.build();
    }

    private FroggyConfig() {
    }

    /** O nível de inteligência valendo agora (ALTO se o config ainda não carregou). */
    public static AiLevel ai() {
        return get(IA);
    }

    /** Muda o nível e salva o arquivo (tela do mod, comando). */
    public static void setAi(AiLevel level) {
        if (!SPEC.isLoaded()) return;
        IA.set(level);
        SPEC.save();
    }

    /** O config ainda não carregou (ex.: chamado cedo demais): usa o padrão. */
    public static <T> T get(ForgeConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
