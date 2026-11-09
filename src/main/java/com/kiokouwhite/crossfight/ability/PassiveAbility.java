package com.kiokouwhite.crossfight.ability;

import org.bukkit.Material;

/**
 * Capacite passive : pas d'item, uniquement des hooks et un tick.
 */
public abstract class PassiveAbility extends Ability {

    protected PassiveAbility(String id, String name, String description) {
        super(id, name, description, AbilityType.PASSIVE, Material.PAPER);
    }
}
