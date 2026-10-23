package com.becomelm;

/** Pulos especiais: o cliente de quem pulou avisa, e quem está vendo toca a animação certa. */
public enum LmMove {
    /** Pulo comum de quatro (o pulinho de sapo). */
    HOP,
    /** Bote: pulo galopando, longo e baixo. */
    LEAP,
    /** Pulo super alto (carregado segurando o agachar). */
    SKY;

    public static LmMove byOrdinal(int i) {
        LmMove[] all = values();
        return i >= 0 && i < all.length ? all[i] : HOP;
    }
}
