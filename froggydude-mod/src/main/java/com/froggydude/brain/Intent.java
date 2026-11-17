package com.froggydude.brain;

/**
 * (v0.7.42) O que ele quer fazer agora (IA dinâmica, etapa 1; só na IA Real). Quem escolhe é o decisor
 * (entity.ai.FroggyDecider), pela nota de cada uma; os comportamentos que já existiam viram o "como" de cada intenção.
 */
public enum Intent {
    /** Ir atrás da presa: o combate (vendo) e o faro (pelo rastro). */
    HUNT("caçar"),
    /** Esperar a presa no lugar da rotina dela (FroggyStakeoutGoal). */
    STALK("tocaia"),
    /** Comer bicho ou aldeão que está à vista (FroggyFeedGoal). */
    FEED("comer"),
    /** Ela mora numa vila e saiu: ir lá comer os aldeões (FroggyMassacreGoal). */
    VILLAGE("vila"),
    /** Ferido do lado de quem bate nele: fugir pra se curar (FroggyRetreatGoal). */
    HEAL("fugir pra curar"),
    /** Entediado: sair andando pelo mapa, farejando (FroggyExploreGoal). */
    EXPLORE("explorar"),
    /** Nada a fazer: à toa. */
    REST("à toa");

    public final String label;

    Intent(String label) {
        this.label = label;
    }
}
