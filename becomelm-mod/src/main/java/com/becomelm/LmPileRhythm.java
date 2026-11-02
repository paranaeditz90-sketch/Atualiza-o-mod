package com.becomelm;

import net.minecraft.util.Mth;

/**
 * (v0.3.5) O ritmo da flexão no monte pela fome (LM: "vai variar a fome"): com a barra vazia ele
 * come desesperado, uma mordida a cada 0,25 s (o monte do golem do Grox); enchendo, vai acalmando
 * até ~0,39 s com a barra cheia (o Taste Testing perto da lava é 0,36 s).
 *
 * Servidor e cliente fazem a MESMA conta, tick a tick, só a partir da fome na hora de deitar (vai no
 * pacote junto do rumo do corpo) e das mordidas que a própria conta dá. Assim a mordida de verdade
 * (a que enche a barra), a cara afundando na câmera, os cacos pulando e o corpo (o pile_lie preso
 * neste relógio) andam juntos e não escorregam com o tempo. A barra de verdade é só do servidor: aqui
 * a fome é "a de quando deitou + o que já comeu no monte".
 */
public final class LmPileRhythm {

    /** Um mergulho = o pile_lie inteiro (o feed_mob do mod da IA): 7 ticks na velocidade 1 (0,35 s). */
    public static final int BOB = 7;
    /** No tick 3 do mergulho a cara está lá embaixo: é ali que ele morde. */
    public static final int DIP_AT = 3;
    /** De cada 8 mergulhos, o 2º, o 5º e o 8º comem de verdade (3 de 8, como era antes). */
    private static final int GROUP = 8;
    /** Meia coxinha por mordida de verdade, até 4 coxinhas por monte (o "médio" que o LM escolheu). */
    public static final int FOOD = 1;
    public static final int FOOD_MAX = 8;
    /** Barra cheia: 0,9 (0,39 s por mordida). Barra vazia: 1,4 (0,25 s), o máximo do mod da IA. */
    private static final float RATE_FULL = 0.9F;
    private static final float RATE_STARVING = 1.4F;
    /** No pacote, o "alvo" do monte leva o rumo (graus x10) e a fome (0-20) juntos. */
    private static final int PACK = 32;
    /**
     * (v0.3.5) No criativo a barra não desce, então em vez da fome vai este código: o ritmo extremo o tempo
     * todo (o LM: "velocidade extrema, não a ponto de virar o flash" - o máximo, 0,25 s por mordida).
     */
    public static final int CREATIVE = 31;

    private final int food0;
    private int ticks;
    private float phase;
    private int dips;
    private int fed;
    private boolean dipNow;
    private boolean biteNow;
    private boolean ateNow;

    /** @param food0 a barra de fome na hora de deitar (0-20), ou {@link #CREATIVE} */
    public LmPileRhythm(int food0) {
        this.food0 = food0 == CREATIVE ? CREATIVE : Mth.clamp(food0, 0, 20);
    }

    /** O que vai no pacote no lugar da fome: a barra, ou o código do criativo. */
    public static int foodCode(net.minecraft.world.entity.player.Player p) {
        return p.isCreative() ? CREATIVE : p.getFoodData().getFoodLevel();
    }

    /** A velocidade da flexão pela barra de fome (0-20). */
    public static float rate(int food) {
        float hunger = 1F - Mth.clamp(food, 0, 20) / 20F;
        return RATE_FULL + (RATE_STARVING - RATE_FULL) * hunger;
    }

    public static int pack(int yawX10, int food) {
        return yawX10 * PACK + (food == CREATIVE ? CREATIVE : Mth.clamp(food, 0, 20));
    }

    public static int yawX10(int packed) {
        return Math.floorDiv(packed, PACK);
    }

    public static int food(int packed) {
        return Math.floorMod(packed, PACK);
    }

    /** Leva a conta até 't' ticks de flexão (se voltou, como quando o servidor confirma, refaz do zero). */
    public void advanceTo(int t) {
        if (t < ticks) {
            ticks = 0;
            phase = 0F;
            dips = 0;
            fed = 0;
            dipNow = biteNow = ateNow = false;
        }
        while (ticks < t) step();
    }

    private void step() {
        float before = phase;
        phase += rate();
        ticks++;
        dipNow = biteNow = ateNow = false;
        if (Mth.floor((phase - DIP_AT) / BOB) > Mth.floor((before - DIP_AT) / BOB)) {
            dipNow = true;
            int k = dips++ % GROUP;
            if (k == 1 || k == 4 || k == 7) {
                biteNow = true;
                if (fed < FOOD_MAX) {
                    fed += FOOD;
                    ateNow = true;
                }
            }
        }
    }

    /** A velocidade agora (a do próximo tick). */
    public float rate() {
        return food0 == CREATIVE ? RATE_STARVING : rate(food0 + fed);
    }

    /** No último tick a cara chegou lá embaixo. */
    public boolean dipNow() {
        return dipNow;
    }

    /** ...e foi uma das mordidas de verdade (3 de cada 8). */
    public boolean biteNow() {
        return biteNow;
    }

    /** ...e ainda cabia comer (o servidor enche a barra). */
    public boolean ateNow() {
        return ateNow;
    }

    /** O tempo do pile_lie em ticks (0 a 7), entre um tick e outro. */
    public float animTicks(float partial) {
        return Mth.positiveModulo(phase + rate() * partial, (float) BOB);
    }

    /** Onde está no mergulho (0 a 1). */
    public float cycle(float partial) {
        return animTicks(partial) / BOB;
    }
}
