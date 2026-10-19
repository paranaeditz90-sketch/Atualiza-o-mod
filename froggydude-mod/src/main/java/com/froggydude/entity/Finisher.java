package com.froggydude.entity;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * A cutscene do golpe final (LM: "o froggydude arrancando o braço esquerdo do
 * jogador, e em seguida o braço direito e a cabeça por último - como
 * exatamente o vídeo da zebra"). Tempos tirados da zebra quadro a quadro
 * (vídeo do LM, 77,3-88,8 s; scratchpad/ref1017/REFS_1017.md):
 *
 *   em pé por cima olhando -> abaixa e crava no braço -> ARRANCA num tranco ->
 *   em pé com o braço na boca sacudindo, o ombro jorrando até a altura da
 *   cabeça dele -> corta pro outro lado -> o outro braço igual -> fica parado
 *   1,4 s mastigando -> abaixa na cabeça -> ARRANCA -> close dele com a cabeça
 *   na boca sacudindo e pedaço pra todo lado -> engole.
 *
 * Uma tabela só, usada pelo servidor (quando cada parte sai, quando mata) e
 * pelo cliente (pose da vítima, câmera, o que está na boca dele): os dois
 * contam a partir do MESMO tick do jogo, então batem até no quadro.
 *
 * Quem já estava sem o braço esquerdo (a língua arrancou antes) pula direto
 * pro direito ({@code skipLeft}): a tabela anda {@link #SKIP} ticks pra frente.
 *
 * Geometria (A = onde a vítima ficou, H = pra onde aponta a cabeça dela,
 * L = o lado esquerdo dela, deitada de costas): pés em A - 0,9H, cabeça em
 * A + 0,85H; o FroggyDude em pé por cima do quadril (A - 0,2H), virado pra
 * cabeça dela. O braço esquerdo dela fica do lado DIREITO dele.
 */
public final class Finisher {

    // ---- a tabela (ticks desde o começo; 20 por segundo)
    /** Abaixa no braço esquerdo. */
    public static final int BEND_L = 14;
    /** Cravou os dentes (morde e sacode até arrancar). */
    public static final int BITE_L = 20;
    public static final int RIP_L = 32;
    /** Fim do primeiro plano: corta pro outro lado. */
    public static final int CUT_1 = 66;
    public static final int BEND_R = 74;
    public static final int BITE_R = 80;
    public static final int RIP_R = 92;
    /** Para de sacudir e fica mastigando, parado (1,4 s). */
    public static final int STILL = 124;
    // a cabeça: no vídeo ele abaixa pra ela 0,3 s depois do que estava aqui (v0.4.5, conferido
    // quadro a quadro com o áudio do vídeo tocando junto)
    public static final int BEND_H = 158;
    public static final int BITE_H = 163;
    public static final int RIP_H = 169;
    /** Engoliu a cabeça. */
    public static final int SWALLOW = 228;
    public static final int END = 232;
    /** Sem o braço esquerdo: começa direto no direito (a tabela anda isso pra frente). */
    public static final int SKIP = 60;

    // ---- comendo (v0.4.5, o "Eating a Zebra" quadro a quadro, 60 fps): arrancou, a perna fica
    // ATRAVESSADA na boca, de um lado ao outro do rosto (como cachorro com osso), e ele engole
    // jogando a cabeça pra trás duas vezes - a boca virada pro céu, o pedaço descendo, a cabeça
    // volta pra frente - até sumir, 1,5 s depois de arrancar. Do 2º braço até abaixar pra
    // cabeça ele fica 2 s parado, de boca fechada. A cabeça: 0,6 s quase parada no close,
    // sacode rápido e no fim é engolida também em duas jogadas pra trás.
    // Ticks no tempo da tabela (o vídeo começa no tick 4, ver client.cutscene.FinisherAudio).
    /** Engolidas do braço esquerdo: {começa a jogar a cabeça pra trás, termina}. */
    public static final int[][] GULPS_L = {{37, 46}, {50, 60}};
    /** O braço esquerdo sumiu na boca. */
    public static final int GONE_L = 64;
    public static final int[][] GULPS_R = {{94, 102}, {110, 118}};
    public static final int GONE_R = 120;
    /** A cabeça: sacode até a 1ª engolida; a 2ª acaba com ela (SWALLOW). */
    public static final int[][] GULPS_H = {{215, 219}, {222, SWALLOW}};
    /** Quanto já desceu depois de cada engolida (braço / cabeça). */
    private static final float[] ARM_STEPS = {0F, 0.5F, 1F};
    private static final float[] HEAD_STEPS = {0F, 0.45F, 1F};

    private Finisher() {
    }

    /** O tick da tabela (com fração, pro cliente), contando o pulo do braço esquerdo. */
    public static float timeline(long now, long start, float partial, boolean skipLeft) {
        return (now - start) + partial + (skipLeft ? SKIP : 0);
    }

    /** Quanto dura de verdade (sem o braço esquerdo é mais curta). */
    public static int length(boolean skipLeft) {
        return END - (skipLeft ? SKIP : 0);
    }

    public static boolean leftGone(float t, boolean skipLeft) {
        return skipLeft || t >= RIP_L;
    }

    public static boolean rightGone(float t) {
        return t >= RIP_R;
    }

    public static boolean headGone(float t) {
        return t >= RIP_H;
    }

    /** O que está na boca dele agora: 0 nada, 1 braço esquerdo, 2 braço direito, 3 cabeça. */
    public static int held(float t, boolean skipLeft) {
        if (t >= RIP_H && t < SWALLOW) return 3;
        if (t >= RIP_R && t < GONE_R) return 2;
        if (!skipLeft && t >= RIP_L && t < GONE_L) return 1;
        return 0;
    }

    /** As engolidas do pedaço que está na boca (1 braço esquerdo, 2 direito, 3 cabeça), ou null. */
    public static int[][] gulps(int held) {
        return switch (held) {
            case 1 -> GULPS_L;
            case 2 -> GULPS_R;
            case 3 -> GULPS_H;
            default -> null;
        };
    }

    /**
     * Quanto do pedaço que está na boca já desceu (0 inteiro, 1 sumiu): só anda durante
     * as engolidas (cabeça pra trás); entre uma e outra fica parado.
     */
    public static float eaten(float t, int held) {
        int[][] g = gulps(held);
        if (g == null) return 0F;
        float[] steps = held == 3 ? HEAD_STEPS : ARM_STEPS;
        for (int i = g.length - 1; i >= 0; i--) {
            if (t >= g[i][0]) {
                float k = Mth.clamp((t - g[i][0]) / (float) (g[i][1] - g[i][0]), 0F, 1F);
                return Mth.lerp(k, steps[i], steps[i + 1]);
            }
        }
        return 0F;
    }

    /** O tick é o começo de uma engolida de braço? (o servidor abre a boca e toca o som) */
    public static int gulpAt(int t, boolean skipLeft) {
        for (int[] g : GULPS_R) if (t == g[0]) return g[1] - g[0];
        if (!skipLeft) for (int[] g : GULPS_L) if (t == g[0]) return g[1] - g[0];
        for (int[] g : GULPS_H) if (t == g[0]) return g[1] - g[0];
        return 0;
    }

    // ---- geometria

    /** Direção horizontal unitária a partir de um yaw do Minecraft (0 = +Z). */
    public static Vec3 dir(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    /** O lado esquerdo de quem está deitado de costas com a cabeça pra H. */
    public static Vec3 left(Vec3 h) {
        return new Vec3(-h.z, 0, h.x);
    }

    /** Ponto no corpo deitado: h ao longo da cabeça, l pro lado esquerdo dela, u pra cima. */
    public static Vec3 at(Vec3 anchor, Vec3 h, double along, double side, double up) {
        Vec3 l = left(h);
        return new Vec3(anchor.x + h.x * along + l.x * side, anchor.y + up, anchor.z + h.z * along + l.z * side);
    }

    public static Vec3 leftShoulder(Vec3 anchor, Vec3 h) {
        return at(anchor, h, 0.48D, 0.37D, 0.14D);
    }

    public static Vec3 rightShoulder(Vec3 anchor, Vec3 h) {
        return at(anchor, h, 0.48D, -0.37D, 0.14D);
    }

    public static Vec3 neck(Vec3 anchor, Vec3 h) {
        return at(anchor, h, 0.62D, 0D, 0.14D);
    }

    /** Onde ele fica: em pé por cima do quadril dela. */
    public static Vec3 froggySpot(Vec3 anchor, Vec3 h) {
        return at(anchor, h, -0.2D, 0D, 0D);
    }
}
