package com.froggydude.entity;

/**
 * Os estados possíveis do Froggy. Cada um vira uma animação do lado do
 * renderer. "IDLE" e "CHASE" não têm uma duração fixa; os outros terminam
 * sozinhos depois de durationTicks.
 */
public enum FroggyState {
    IDLE(0),
    CHASE(0),
    TONGUE_WHIP(14),
    TONGUE_GRAB(16),
    TONGUE_CAPTURE(28),
    JUMP_PIN(20),
    HIGH_JUMP(32),
    BITE(12),
    FEEDING(120),
    /**
     * Fase 2, etapa 1 (o vídeo do LM em 60 fps, 89,28-91,75 s): os ossos estalam,
     * ele dobra até quase deitar, levanta, a cabeça afunda nos ombros e tomba, o
     * ombro direito sobe. Os 59 ticks são a animação "contort" (2,95 s, rotoscopia
     * do vídeo); o tempo torto depois disso quem decide é a FroggyFrenzyGoal.
     */
    CONTORTING(59),
    /** Montado em cima da vítima derrubada, mordendo e rasgando ("devorar"). */
    PIN_HOLD(100),
    /** Esmagamento: em pé por cima da vítima derrubada, desce os dois punhos juntos (vs AJ, 0:57). */
    SMASH(132),
    /** Pulo altíssimo: some no céu e cai em cima da vítima (vs AJ, 0:56). Dura até pousar. */
    SKY_DROP(80),
    /** Agarra a vítima presa e arranca o braço esquerdo (vs AJ, 2:22). */
    ARM_RIP(40),
    /** Come o braço arrancado: mastiga, engole e digere. Distraído e vulnerável. */
    ARM_EAT(90),
    /** Fase 2, etapa 2: a cabeça joga pra trás num estalo, grita e já dispara correndo. */
    ROAR(10),
    /**
     * O golpe final (cutscene): a vítima deitada, ele arranca o braço esquerdo,
     * o direito e a cabeça (entity.Finisher tem a tabela; quem cuida é a
     * FroggyFinisherGoal).
     */
    FINISHER(Finisher.END),
    /**
     * Atravessando água de vitória-régia em vitória-régia (fim do vídeo do LM,
     * 95,6-98,9 s): um salto a cada 6 ticks (FroggyPadHopGoal), enquanto tiver folha.
     */
    PAD_HOP(6),
    /** Pondo bloco como jogador pra chegar na vítima (ai.PlayerMoves: torre e ponte). */
    BUILD(0),
    /** A "dancinha" de jogador: agacha e levanta, pula, soca o ar (ai.PlayerMoves). */
    TAUNT(0);

    public final int durationTicks;

    FroggyState(int durationTicks) {
        this.durationTicks = durationTicks;
    }

    public boolean isAttack() {
        return this == TONGUE_WHIP || this == TONGUE_GRAB || this == TONGUE_CAPTURE
                || this == JUMP_PIN || this == HIGH_JUMP || this == BITE || this == SKY_DROP;
    }

    public boolean isTongue() {
        return this == TONGUE_WHIP || this == TONGUE_GRAB || this == TONGUE_CAPTURE;
    }

    /** Em cima da vítima derrubada (ela fica presa no chão). */
    public boolean isOnVictim() {
        return this == PIN_HOLD || this == SMASH || this == ARM_RIP || this == FINISHER;
    }

    /** Qualquer ação conduzida pela Goal de combate (ataques, montado, braço). */
    public boolean isCombatAction() {
        return isAttack() || isOnVictim() || this == ARM_EAT;
    }
}
