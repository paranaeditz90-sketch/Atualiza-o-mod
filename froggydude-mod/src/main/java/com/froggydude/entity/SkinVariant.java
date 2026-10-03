package com.froggydude.entity;

/**
 * Tipo de "estrago" na skin, conforme o último tipo de dano relevante.
 * O id vira parte do nome da textura: froggydude_<id>_<estágio>.png
 */
public enum SkinVariant {
    NORMAL("normal"),     // dano comum (golpes): só o sangue aumenta com a vida perdida
    BURNED("burned"),     // fogo / lava
    FED("fed"),           // acabou de devorar alguém
    SMASHED("smashed");   // queda / explosão ("se arrebentou")

    public final String id;

    SkinVariant(String id) {
        this.id = id;
    }

    public static SkinVariant byOrdinal(int ordinal) {
        SkinVariant[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, ordinal))];
    }
}
