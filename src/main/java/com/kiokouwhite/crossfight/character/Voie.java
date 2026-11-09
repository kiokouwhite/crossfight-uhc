package com.kiokouwhite.crossfight.character;

/**
 * La voie d'un personnage. Purement indicative, sans effet de jeu.
 */
public enum Voie {
    DESTRUCTION("Voie de la Destruction", "red"),
    CHASSE("Voie de la Chasse", "green"),
    HARMONIE("Voie de l'Harmonie", "aqua"),
    NIHILITE("Voie de la Nihilite", "dark_purple");

    private final String label;
    private final String color;

    Voie(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public String label() { return label; }
    public String color() { return color; }
}
