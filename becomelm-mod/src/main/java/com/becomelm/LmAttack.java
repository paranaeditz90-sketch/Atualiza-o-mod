package com.becomelm;

/** Os golpes do FroggyDude (a roda escolhe o jeito; o contexto escolhe o golpe). */
public enum LmAttack {
    /** Galopando, atacar com um bicho na frente: se joga nele e derruba. */
    POUNCE,
    /** Caiu do pulo alto em cima do bicho: monta, soca e morde. */
    SMASH,
    /** (efeito) um soco do SMASH. */
    PUNCH,
    /** (efeito) uma mordida. */
    BITE,
    /** Galopando, atacar um bicho bem perto: morde e segura na boca (andando, arrasta). */
    GRAB,
    /** Segurando, atacar: sacode a cabeça (mordidas fortes). */
    SHAKE,
    /** Segurando, pular: arremessa pra frente. */
    THROW,
    /** Deitado em cima do monte de pedaços de quem ele matou (não come: só fica em cima). */
    PILE,
    /** (pedido) levantar do monte. */
    GET_UP,
    /** (só no cliente) levantando do monte. */
    RISE;

    public static LmAttack byOrdinal(int i) {
        LmAttack[] all = values();
        return i >= 0 && i < all.length ? all[i] : POUNCE;
    }
}
