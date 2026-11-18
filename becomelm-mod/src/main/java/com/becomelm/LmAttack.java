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
    HAMMER_STOP,
    /** (roda) Estala o pescoço: a cabeça tomba de lado num tranco (rage_crack, 0,6 s). */
    NECK_CRACK,
    /**
     * (roda) A raiva "()" (mod da IA 0.7.40, o vídeo 4 do LM): agachado fundo, braços abertos até o chão, boca aberta;
     * andando, avança agachado. Até escolher de novo, atacar ou 30 s.
     */
    RAGE,
    /** (roda, armado) Arranca o braço esquerdo de um jogador: derruba, crava os dentes no ombro e puxa (2 s). */
    ARM_RIP,
    /** Come o braço arrancado: mastiga, engole e digere (4,5 s). */
    ARM_EAT,
    /** (roda, armado, em bicho) Quem o golpe vai comer deitado (vem logo antes do PILE). */
    FEED,
    /** (roda) Grudado no teto (mod da IA: a tocaia do canto do teto, Grox RABIES). Pedido de novo = solta. */
    CEILING,
    /** Do teto, se joga em cima do bicho (o bote, caindo de cima). */
    CEILING_DIVE,
    /** (roda, armado) Rouba a arma da mão de alguém (no BecomeLM, mordendo a mão). */
    STEAL,
    /** Come a arma roubada: sacode a cabeça e ela vai entrando aos poucos (o mod da IA, Grox 1:24). */
    WEAPON_EAT,
    /** (roda) Cansado: ofegando curvado, as mãos nos joelhos (o "tired" do mod da IA). Até escolher de novo ou atacar. */
    /**
     * (roda, armado) A cutscene da cabeça (mod da IA 0.7.41): derruba, monta, crava a boca na cabeça e chacoalha durante o
     * rugido (4,2 s). Jogador: se acertar ele (1 tiro / 2 golpes de arma / 3 socos), ele voa pra trás; senão arranca a
     * cabeça. Bicho não se defende.
     */
    HEAD_GRAB,
    /** (pedido da vítima da cabeça) bateu (alvo 0) ou atirou (alvo 1) em quem está em cima. */
    HEAD_HIT,
    TIRED;

    public static LmAttack byOrdinal(int i) {
        LmAttack[] all = values();
        return i >= 0 && i < all.length ? all[i] : POUNCE;
    }
}
