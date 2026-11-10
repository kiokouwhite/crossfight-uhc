package com.kiokouwhite.crossfight.ability;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Capacite qu'un mannequin adversaire peut lancer contre un joueur, pour tester
 * ce que l'on ressent en face. Le lanceur n'est pas un joueur : la capacite
 * doit donc se contenter d'une position, d'une direction et d'une cible.
 */
public interface EnemyCastable {

    /**
     * Lance la capacite depuis {@code caster} contre {@code target}.
     * @return false si la capacite n'a pas pu etre lancee.
     */
    boolean castOn(LivingEntity caster, Player target, AbilityState state);
}
