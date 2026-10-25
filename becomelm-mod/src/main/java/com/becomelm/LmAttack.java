package com.becomelm;

/** Os golpes do FroggyDude (a roda escolhe o jeito; o contexto escolhe o golpe). */
public enum LmAttack {
    /** Galopando, atacar com um bicho na frente: se joga nele e derruba. */
    POUNCE;

    public static LmAttack byOrdinal(int i) {
        LmAttack[] all = values();
        return i >= 0 && i < all.length ? all[i] : POUNCE;
    }
}
