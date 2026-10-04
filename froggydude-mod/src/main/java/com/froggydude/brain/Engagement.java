package com.froggydude.brain;

import java.util.UUID;

/** Uma caçada em andamento: o que ele escolheu e o que está rendendo. */
public class Engagement {
    public final UUID player;
    /** O estilo do jogador quando a caçada começou (o contexto da escolha). */
    public final Style context;
    public final Strategy strategy;
    public final int startTick;
    public float dealt;
    public float taken;
    public int pins;
    /** Quando perdeu a vítima de vista (-1 = está vendo). */
    public int lostAt = -1;

    public Engagement(UUID player, Style context, Strategy strategy, int startTick) {
        this.player = player;
        this.context = context;
        this.strategy = strategy;
        this.startTick = startTick;
    }
}
