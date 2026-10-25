package com.becomelm;

/** Os golpes do FroggyDude (a roda escolhe o jeito; o contexto escolhe o golpe). */
public enum LmAttack {
    /** Galopando, atacar com um bicho na frente: se joga nele e derruba. */
    POUNCE,
    /** Caiu do pulo alto em cima do bicho: monta, soca e morde. */
    SMASH,
    /** Caiu do pulo alto e não tinha ninguém embaixo: de cara no chão. */
    FACEPLANT,
    /** (efeito) um soco do SMASH. */
    PUNCH,
    /** (efeito) uma mordida. */
    BITE,
    /** Galopando, atacar um bicho bem perto: morde e segura na boca (andando, arrasta). */
    GRAB,
    /** Segurando, atacar: sacode a cabeça (mordidas fortes). */
    SHAKE,
    /** Segurando, pular: arremessa pra frente. */
    THROW;

    public static LmAttack byOrdinal(int i) {
        LmAttack[] all = values();
        return i >= 0 && i < all.length ? all[i] : POUNCE;
    }
}
