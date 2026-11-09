package com.kiokouwhite.crossfight.character;

import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Particularite d'une licence, partagee par tous ses personnages
 * (Soutien de Fire Emblem, gaz de SNK, credits de Valorant...).
 */
public interface LicenceMechanic {

    /** Debut de partie ou reapparition. */
    default void onEquip(Player p) {}

    /** Mort ou fin de partie. */
    default void onUnequip(Player p) {}

    /** Une fois par seconde pour chaque joueur vivant de la licence. */
    default void tick(Player p) {}

    /** Degats infliges par un joueur de la licence a un ennemi (apres calcul). */
    default void onDealDamage(Player attacker, org.bukkit.entity.LivingEntity victim, EntityDamageByEntityEvent e) {}

    /** Un joueur de la licence vient de mourir (avant la perte de vie). */
    default void onDeath(Player p) {}

    /** Un joueur de la licence a tue un ennemi. */
    default void onKill(Player killer, Player victim) {}

    /** Remise a zero complete (nouvelle partie). */
    default void reset() {}
}
