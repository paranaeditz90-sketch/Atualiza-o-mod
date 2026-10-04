package com.froggydude.config;

import com.froggydude.world.ManhuntMode;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * config/froggydude-common.toml (dá pra editar no celular com qualquer
 * gerenciador de arquivos; o jogo relê ao abrir o mundo).
 */
public final class FroggyConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ANTI_TRAPACA;
    public static final ForgeConfigSpec.BooleanValue PERDOAR;
    public static final ForgeConfigSpec.EnumValue<ManhuntMode> MANHUNT_MODO;
    public static final ForgeConfigSpec.IntValue MANHUNT_DIAS;
    public static final ForgeConfigSpec.IntValue VANTAGEM_SEGUNDOS;
    public static final ForgeConfigSpec.IntValue VOLTA_SEGUNDOS;
    public static final ForgeConfigSpec.DoubleValue CACA_BLOCOS_POR_SEGUNDO;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Anti-trapaça (só vale durante um manhunt e nunca em mundo com -dev no nome)").push("anti_trapaca");
        ANTI_TRAPACA = b.comment("Liga o anti-trapaça: 1a trapaça = aviso (I SEE YOU), 2a = invasão e banimento do mundo.")
                .define("ligado", true);
        PERDOAR = b.comment("true = na próxima vez que o jogador entrar, o banimento some (e as advertências zeram).")
                .define("perdoar", false);
        b.pop();

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
        b.pop();

        SPEC = b.build();
    }

    private FroggyConfig() {
    }

    /** O config ainda não carregou (ex.: chamado cedo demais): usa o padrão. */
    public static <T> T get(ForgeConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
