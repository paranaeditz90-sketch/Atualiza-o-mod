package com.becomelm;

/** O jeito do jogador, escolhido na roda. */
public enum LmMode {
    /** Jogador normal (o mod não mexe em nada). */
    HUMAN("human"),
    /** FroggyDude em pé, andando como gente. */
    STAND("stand"),
    /** FroggyDude de quatro, galopando (rápido). */
    GALLOP("gallop"),
    /** Fase 2: depois da transformação, em pé curvado, correndo desesperado (mais rápido ainda). */
    FRENZY("frenzy");

    public final String id;

    LmMode(String id) {
        this.id = id;
    }

    public boolean froggy() {
        return this != HUMAN;
    }

    /** Os jeitos de correr rápido (galope e fase 2): bote, poeira, campo de visão. */
    public boolean fast() {
        return this == GALLOP || this == FRENZY;
    }

    public static LmMode byId(String id) {
        for (LmMode m : values()) {
            if (m.id.equals(id)) return m;
        }
        return HUMAN;
    }

    public static LmMode byOrdinal(int i) {
        LmMode[] all = values();
        return i >= 0 && i < all.length ? all[i] : HUMAN;
    }
}
