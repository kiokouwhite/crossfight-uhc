package com.kiokouwhite.crossfight.ability;

import org.bukkit.Material;

/**
 * Capacite passive : pas d'item, uniquement des hooks et un tick.
 */
public abstract class PassiveAbility extends Ability {

    protected PassiveAbility(String id, String name, String description) {
        this(id, name, description, Material.PAPER);
    }

    protected PassiveAbility(String id, String name, String description, Material icon) {
        super(id, name, description, AbilityType.PASSIVE, icon);
    }

    @Override
    protected java.util.List<String> extraLore() {
        return java.util.List.of("", "<dark_gray>Toujours actif, rien a activer");
    }
}
