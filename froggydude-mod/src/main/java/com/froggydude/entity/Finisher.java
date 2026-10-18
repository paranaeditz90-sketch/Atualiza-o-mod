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
    public static final int BEND_H = 152;
    public static final int BITE_H = 158;
    public static final int RIP_H = 166;
    /** Engoliu a cabeça. */
    public static final int SWALLOW = 226;
    public static final int END = 232;
    /** Sem o braço esquerdo: começa direto no direito (a tabela anda isso pra frente). */
    public static final int SKIP = 60;

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
        if (t >= RIP_R && t < BEND_H) return 2;
        if (!skipLeft && t >= RIP_L && t < CUT_1) return 1;
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
