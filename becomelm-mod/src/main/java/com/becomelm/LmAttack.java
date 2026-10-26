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
    RISE,
    /** Golpe de finalização: a cena da barriga (LmFinish). Raríssimo. */
    FINISH,
    /**
     * Em pé, segurando o atacar com o bicho na mira: derruba de costas, monta e martela com os
     * dois braços juntos enquanto segurar (o esmagamento do Froggydude).
     */
    HAMMER,
    /** (pedido) soltou o atacar: para de martelar e levanta. */
    HAMMER_STOP;

    public static LmAttack byOrdinal(int i) {
        LmAttack[] all = values();
        return i >= 0 && i < all.length ? all[i] : POUNCE;
    }
}
