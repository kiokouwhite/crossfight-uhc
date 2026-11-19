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

    /** Plus d'objet dans l'inventaire : le passif s'affiche en haut a gauche de l'ecran (hud/PassiveHud). */
    @Override
    public boolean hasItem() { return false; }

    /** Le passif vient d'agir : son icone s'allume un instant. */
    protected void signal(org.bukkit.entity.Player p) { com.kiokouwhite.crossfight.hud.PassiveHud.flash(p, id()); }

    /** Le passif agit tant que {@code on} : son icone reste allumee. */
    protected void active(org.bukkit.entity.Player p, boolean on) { com.kiokouwhite.crossfight.hud.PassiveHud.setActive(p, id(), on); }

    @Override
    protected java.util.List<String> extraLore() {
        return java.util.List.of("", "<dark_gray>Toujours actif, rien a activer");
    }
}
