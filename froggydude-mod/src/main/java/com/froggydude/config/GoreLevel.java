package com.froggydude.config;

/**
 * Quanto de violência gráfica cada jogador quer ver (Mods > Froggydude >
 * Configurar, ou a tela de aviso da primeira vez). É do CLIENTE: cada um vê o
 * que escolheu, mesmo no mesmo mundo. O servidor só guarda o de cada jogador
 * pro que ele decide sozinho (o corpo que fica no chão no Extremo).
 *
 * Sem classes de cliente aqui: o servidor também usa.
 */
public enum GoreLevel {
    /** Nada de sangue nem de corpo despedaçado. */
    OFF(0.0F),
    /** Respingos pequenos, sem poça, sem sangue na skin nem na tela. */
    LIGHT(0.35F),
    /** Sangue no ar, poças e manchas na skin. */
    MEDIUM(0.7F),
    /** Tudo: jatos, poças grandes, sangue na skin e na tela. */
    HEAVY(1.0F),
    /** Jorra de verdade, não sai da skin sem água/chuva e o corpo fica no chão pra ele comer. */
    EXTREME(1.8F);

    /** Multiplicador da quantidade de sangue (pedaços, tamanho das poças). */
    public final float amount;

    GoreLevel(float amount) {
        this.amount = amount;
    }

    public boolean any() {
        return this != OFF;
    }

    public boolean pools() {
        return ordinal() >= MEDIUM.ordinal();
    }

    public boolean skinStains() {
        return ordinal() >= MEDIUM.ordinal();
    }

    /** Respingo na tela: Médio só nos golpes grandes, Pesado e Extremo sempre. */
    public boolean screen() {
        return ordinal() >= MEDIUM.ordinal();
    }

    /** O sangue jorra em jato contínuo (fluido), além dos pedaços. */
    public boolean streams() {
        return this == EXTREME;
    }

    /** As manchas na skin só saem na água ou na chuva. */
    public boolean stainsStick() {
        return this == EXTREME;
    }

    /** Braços e cabeça saem de verdade na cutscene (Desligado: a tela escurece na hora). */
    public boolean dismember() {
        return this != OFF;
    }

    public String key() {
        return "froggydude.gore." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public static GoreLevel byOrdinal(int i) {
        GoreLevel[] v = values();
        return i >= 0 && i < v.length ? v[i] : HEAVY;
    }
}
