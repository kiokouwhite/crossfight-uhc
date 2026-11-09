package com.kiokouwhite.crossfight.ability;

/**
 * Les types de capacites decrits dans les regles.
 */
public enum AbilityType {
    /** S'active toute seule. */
    PASSIVE("Passif"),
    /** Clic pour activer, puis temps de chargement. */
    ACTIVE("Actif"),
    /** Peut etre activee et desactivee, avec une barre qui se decharge puis se recharge. */
    TOGGLE("Activable"),
    /** Toujours active, mais plusieurs modes qu'on change en cliquant. */
    SWITCH("Intervertible"),
    /** L'ultime, charge par les degats infliges et recus. */
    ULTIMATE("Ultime");

    private final String label;

    AbilityType(String label) { this.label = label; }

    public String label() { return label; }
}
