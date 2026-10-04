package com.froggydude.brain;

/** Como ele começa uma caçada (o que o cérebro aprende a escolher). */
public enum Strategy {
    /** Segue de longe, para e encara; ataca quando a vítima chega perto ou vacila. */
    STALK("apavorar"),
    /** Galope direto pra cima. */
    RUSH("caçar direto"),
    /** Dá a volta e chega por trás de onde a vítima está olhando. */
    FLANK("cercar por trás");

    public final String label;

    Strategy(String label) {
        this.label = label;
    }
}
