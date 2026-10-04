package com.froggydude.brain;

/** Como o jogador costuma se virar contra ele (o "contexto" do cérebro). */
public enum Style {
    /** Ainda não deu pra saber. */
    UNKNOWN("?"),
    /** Corre pra longe. */
    FLEE("foge correndo"),
    /** Sobe em torre/parede. */
    TOWER("sobe em torre"),
    /** Encara e bate (espada, machado, soco). */
    MELEE("briga de perto"),
    /** Atira de longe (arco, besta, tridente). */
    RANGED("atira de longe"),
    /** Some da vista (se esconde, se tranca). */
    HIDE("se esconde");

    public final String label;

    Style(String label) {
        this.label = label;
    }

    /** Os estilos que dá pra observar (todos menos o UNKNOWN). */
    public static final Style[] OBSERVED = {FLEE, TOWER, MELEE, RANGED, HIDE};
}
