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
    /** Fase 2: a cabeça gira quase de ponta-cabeça e ele grita (vídeo "I'm the horror mod"). */
    CONTORTING(54),
    /** Salto de sapo na perseguição: agacha, voa rente ao chão, cai nas mãos. */
    LEAP(24),
    /** Montado em cima da vítima derrubada, mordendo e rasgando ("devorar"). */
    PIN_HOLD(100),
    /** Esmagamento: em pé por cima da vítima derrubada, desce os dois punhos juntos (vs AJ, 0:57). */
    SMASH(90),
    /** Pulo altíssimo: some no céu e cai em cima da vítima (vs AJ, 0:56). Dura até pousar. */
    SKY_DROP(80),
    /** Agarra a vítima presa e arranca o braço esquerdo (vs AJ, 2:22). */
    ARM_RIP(40),
    /** Come o braço arrancado: mastiga, engole e digere. Distraído e vulnerável. */
    ARM_EAT(90);

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
        return this == PIN_HOLD || this == SMASH || this == ARM_RIP;
    }

    /** Qualquer ação conduzida pela Goal de combate (ataques, salto, montado, braço). */
    public boolean isCombatAction() {
        return isAttack() || isOnVictim() || this == LEAP || this == ARM_EAT;
    }
}
