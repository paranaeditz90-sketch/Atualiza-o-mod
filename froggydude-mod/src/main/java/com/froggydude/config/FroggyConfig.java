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
    public static final ForgeConfigSpec.BooleanValue INVASAO;
    public static final ForgeConfigSpec.DoubleValue CACA_BLOCOS_POR_SEGUNDO;
    public static final ForgeConfigSpec.IntValue IRRITADO_CHANCE;
    public static final ForgeConfigSpec.IntValue RAIVA_CEU_CHANCE;
    public static final ForgeConfigSpec.EnumValue<AiLevel> IA;
    public static final ForgeConfigSpec.BooleanValue TLM_GORE;

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

        b.comment("Froggy's in your world: ele entra no teu mundo num dia sorteado (4 a 10) e te caça até o dragão morrer.").push("invasao");
        INVASAO = b.comment("Liga o modo. Cada mundo sorteia o dia dele (escondido) na primeira vez.")
                .define("ligado", false);
        b.pop();

        b.push("froggydude");
        CACA_BLOCOS_POR_SEGUNDO = b.comment("Velocidade do galope fora da perseguição - seguindo rastro, indo pra vila, se escondendo (blocos/s).",
                        "Original: ~15. Perseguindo alguém à vista, ele mede a vítima e corre ~25% mais (transformado, ~50%).")
                .defineInRange("caca_blocos_por_segundo", 15.0D, 6.0D, 20.0D);
        IRRITADO_CHANCE = b.comment("Chance (%) de ele se irritar do nada (os ribbits subindo) e se transformar quando parte",
                        "pra cima de um jogador, mesmo com a vida cheia. Sorteada ao começar a perseguição e a cada 30 s dela",
                        "(depois de uma transformação, só de novo em 60-90 s). 0 = só com a vida baixa, como antes.")
                .defineInRange("irritado_chance", 25, 0, 100);
        RAIVA_CEU_CHANCE = b.comment("Chance (%) de, quando um jogador mata ele (e o renascer com a língua não veio), ele não morrer:",
                        "sobe flutuando até virar um pontinho no céu, \"RUN FOR YOUR LIFE!!!! NOW!!\" e ~10 s pra correr,",
                        "e mergulha em quem matou (mata na hora). Só com céu aberto em cima dele. 0 = nunca.")
                .defineInRange("raiva_ceu_chance", 30, 0, 100);
        IA = b.comment("O quanto ele pensa: MIN, MEDIO, ALTO (o de sempre), EXTRA, MAX, REAL.",
                        "Muda alcance de visão/faro, reação, previsão, táticas (cercar, cortar caminho, pôr bloco,",
                        "arrombar, sabotar), o uso do que ele aprendeu de cada jogador e, no EXTRA/MAX, truques novos.",
                        "REAL: a esperteza do MAX com o jeito do FroggyDude dos vídeos (sabe o que está na tua tela).",
                        "Também pela tela do mod (Mods > Froggydude > Configurar) ou /froggydude ia <nível>.")
                .defineEnum("ia", AiLevel.ALTO);
        b.pop();

        b.comment("Compatibilidade com outros mods").push("compatibilidade");
        TLM_GORE = b.comment("Touhou Little Maid (pedido do time do TLM). false (padrão) = protege o mod deles: ele não ataca",
                        "nem come as maids, e nada de sangue nem pedaços nelas. true = ele ataca normal, com sangue normal.",
                        "Também pela tela do mod (Mods > Froggydude > Configurar > Gore).")
                .define("touhou_little_maid_gore", false);
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

    /** Froggy's in your world ligado. */
    public static boolean invasion() {
        return get(INVASAO);
    }

    public static void setInvasion(boolean on) {
        if (!SPEC.isLoaded()) return;
        INVASAO.set(on);
        SPEC.save();
    }

    /** Gore (e ataque) nas entidades do Touhou Little Maid; desligado = protegidas (GoreSafe). */
    public static boolean tlmGore() {
        return get(TLM_GORE);
    }

    public static void setTlmGore(boolean on) {
        if (!SPEC.isLoaded()) return;
        TLM_GORE.set(on);
        SPEC.save();
    }

    /** O config ainda não carregou (ex.: chamado cedo demais): usa o padrão. */
    public static <T> T get(ForgeConfigSpec.ConfigValue<T> value) {
        return SPEC.isLoaded() ? value.get() : value.getDefault();
    }
}
