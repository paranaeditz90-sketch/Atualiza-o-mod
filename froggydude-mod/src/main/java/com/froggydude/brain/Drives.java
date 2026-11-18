package com.froggydude.brain;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;

/**
 * (v0.7.42) As vontades dele (IA dinâmica, etapa 1; LM 16/11: "não uma qualquer que segue normas, mas que realmente
 * age por si só"; escolheu fome, raiva e curiosidade/tédio). Cada uma vai de 0 a 1, sobe e desce com o tempo e com o
 * que acontece, e fica salva com ele. Quem usa é o decisor (entity.ai.FroggyDecider), que dá nota pras intenções com
 * elas. A regra mora nas vontades, não nos comportamentos: o mesmo FroggyDude, com fome ou com raiva, escolhe diferente.
 */
public final class Drives {

    /** Fome: do zero ao máximo em ~4 min sem comer; correndo, ~50% mais rápido. */
    private static final float HUNGER_PER_SEC = 1F / 240F;
    private static final float HUNGER_RUN_PER_SEC = 0.5F / 240F;
    /** Raiva: esfria do máximo ao zero em ~5 min. */
    private static final float ANGER_COOL_PER_SEC = 1F / 300F;
    /** Tédio: sobe do zero ao máximo em ~2,5 min sem nada acontecer; com coisa acontecendo, some em ~40 s. */
    private static final float BORED_PER_SEC = 1F / 150F;
    private static final float BORED_BUSY_PER_SEC = 1F / 40F;
    /** Explorando, o tédio vai passando devagar (~90 s). */
    private static final float BORED_EXPLORE_PER_SEC = 1F / 90F;

    public float hunger = 0.4F;
    public float anger = 0.1F;
    public float boredom = 0.2F;

    /** Um segundo passou. busy = tem presa/comida/tocaia na mão; exploring = saiu andando pelo mapa. */
    public void tickSecond(boolean running, boolean busy, boolean exploring) {
        hunger = clamp(hunger + HUNGER_PER_SEC + (running ? HUNGER_RUN_PER_SEC : 0F));
        anger = clamp(anger - ANGER_COOL_PER_SEC);
        if (busy) boredom = clamp(boredom - BORED_BUSY_PER_SEC);
        else if (exploring) boredom = clamp(boredom - BORED_EXPLORE_PER_SEC);
        else boredom = clamp(boredom + BORED_PER_SEC);
    }

    /** Comeu (bicho ~0,3; aldeão ~0,5; gente ~0,7). */
    public void ate(float amount) {
        hunger = clamp(hunger - amount);
        boredom = clamp(boredom - 0.2F);
    }

    /** Apanhou: fração da vida que perdeu; bala de arma de mod dá mais raiva. */
    public void hurt(float lifeFraction, boolean gun) {
        anger = clamp(anger + Mth.clamp(lifeFraction * 4F, 0.02F, 0.3F) + (gun ? 0.05F : 0F));
        boredom = clamp(boredom - 0.3F);
    }

    /** A presa fugiu dele (a caçada acabou sem pegar). */
    public void preyEscaped() {
        anger = clamp(anger + 0.25F);
    }

    /** Perdeu o rastro (foi enganado). */
    public void lostTrail() {
        anger = clamp(anger + 0.08F);
    }

    /** Pegou quem caçava: a raiva passa, a barriga enche. */
    public void killedPrey() {
        anger = clamp(anger - 0.5F);
        ate(0.7F);
        boredom = 0F;
    }

    /** Achou alguma coisa (presa à vista, cheiro novo): o tédio passa. */
    public void excite(float amount) {
        boredom = clamp(boredom - amount);
    }

    /** Muda uma (o comando de teste): fome/hunger, raiva/anger, tedio/tédio/boredom. Nome desconhecido: false. */
    public boolean set(String which, float value) {
        float v = clamp(value);
        switch (which) {
            case "fome", "hunger" -> hunger = v;
            case "raiva", "anger" -> anger = v;
            case "tedio", "tédio", "boredom" -> boredom = v;
            default -> {
                return false;
            }
        }
        return true;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putFloat("Hunger", hunger);
        t.putFloat("Anger", anger);
        t.putFloat("Boredom", boredom);
        return t;
    }

    public void load(CompoundTag t) {
        if (t.contains("Hunger")) hunger = clamp(t.getFloat("Hunger"));
        if (t.contains("Anger")) anger = clamp(t.getFloat("Anger"));
        if (t.contains("Boredom")) boredom = clamp(t.getFloat("Boredom"));
    }

    @Override
    public String toString() {
        return String.format(java.util.Locale.ROOT, "fome %d%% raiva %d%% tédio %d%%",
                Math.round(hunger * 100F), Math.round(anger * 100F), Math.round(boredom * 100F));
    }

    private static float clamp(float v) {
        return Mth.clamp(v, 0F, 1F);
    }
}
