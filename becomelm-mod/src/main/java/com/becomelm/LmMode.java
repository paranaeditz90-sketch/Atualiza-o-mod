package com.becomelm;

/** O jeito do jogador, escolhido na roda. */
public enum LmMode {
    /** Jogador normal (o mod não mexe em nada). */
    HUMAN("human"),
    /** FroggyDude em pé, andando como gente. */
    STAND("stand"),
    /** FroggyDude de quatro, galopando (rápido). */
    GALLOP("gallop");

    public final String id;

    LmMode(String id) {
        this.id = id;
    }

    public boolean froggy() {
        return this != HUMAN;
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
