package com.kiokouwhite.crossfight.team;

/**
 * Taille des equipes d'une partie : solo, groupe duo ou equipe de trois.
 */
public enum TeamSize {
    SOLO(1, "Personnage Solitaire", "Solitaire"),
    DUO(2, "Groupe Duo", "Duo"),
    TRIO(3, "Equipe de Trois", "Trio");

    private final int size;
    private final String label, shortLabel;

    TeamSize(int size, String label, String shortLabel) {
        this.size = size;
        this.label = label;
        this.shortLabel = shortLabel;
    }

    /** En un mot : Solitaire, Duo, Trio. */
    public String shortLabel() { return shortLabel; }

    public int size() { return size; }
    public String label() { return label; }

    public static TeamSize parse(String s) {
        for (TeamSize t : values()) {
            if (t.name().equalsIgnoreCase(s) || t.shortLabel.equalsIgnoreCase(s)) return t;
        }
        return null;
    }
}
