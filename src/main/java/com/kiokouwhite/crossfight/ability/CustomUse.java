package com.kiokouwhite.crossfight.ability;

import com.kiokouwhite.crossfight.player.CfPlayer;
import org.bukkit.entity.Player;

/**
 * Capacite qui gere elle-meme le clic droit sur son item (ou sur l'arme qui la porte),
 * a la place du comportement standard de son type. Utile pour les armes a plusieurs
 * modes ou les ultimes qui restent actifs apres leur lancement.
 */
public interface CustomUse {

    /** @return true si le clic a ete consomme ; false pour laisser le comportement standard. */
    boolean onUse(Player p, CfPlayer cf, AbilityState state, boolean sneaking);
}
