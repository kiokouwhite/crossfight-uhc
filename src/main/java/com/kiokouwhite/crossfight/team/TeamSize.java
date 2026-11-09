package com.kiokouwhite.crossfight.team;

/**
 * Taille des equipes d'une partie : solo, groupe duo ou equipe de trois.
 */
public enum TeamSize {
    SOLO(1, "Personnage Solitaire"),
    DUO(2, "Groupe Duo"),
    TRIO(3, "Equipe de Trois");

    private final int size;
    private final String label;

    TeamSize(int size, String label) {
        this.size = size;
        this.label = label;
    }

    public int size() { return size; }
    public String label() { return label; }

    public static TeamSize parse(String s) {
        for (TeamSize t : values()) {
            if (t.name().equalsIgnoreCase(s)) return t;
        }
        return null;
    }
}
