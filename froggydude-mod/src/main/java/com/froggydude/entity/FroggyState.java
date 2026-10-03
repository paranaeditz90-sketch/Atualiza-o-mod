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
    FEEDING(80),
    CONTORTING(50),
    /** Salto de sapo na perseguição: agacha, voa rente ao chão, cai nas mãos. */
    LEAP(24),
    /** Montado em cima da vítima derrubada, mordendo. Dura o tempo do "preso". */
    PIN_HOLD(50);

    public final int durationTicks;

    FroggyState(int durationTicks) {
        this.durationTicks = durationTicks;
    }

    public boolean isAttack() {
        return this == TONGUE_WHIP || this == TONGUE_GRAB || this == TONGUE_CAPTURE
                || this == JUMP_PIN || this == HIGH_JUMP || this == BITE;
    }

    /** Qualquer ação conduzida pela Goal de combate (ataques, salto e montado). */
    public boolean isCombatAction() {
        return isAttack() || this == LEAP || this == PIN_HOLD;
    }
}
