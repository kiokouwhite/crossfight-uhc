package com.kiokouwhite.crossfight.ability;

import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Capacite active : un clic droit l'active, puis un temps de chargement s'applique.
 */
public abstract class ActiveAbility extends Ability {

    private final int cooldownSeconds;

    protected ActiveAbility(String id, String name, String description, Material icon, int cooldownSeconds) {
        super(id, name, description, AbilityType.ACTIVE, icon);
        this.cooldownSeconds = cooldownSeconds;
    }

    public int cooldownSeconds() { return cooldownSeconds; }

    /**
     * Effet de la capacite. Retourne false si l'activation echoue (pas de cible, etc.) :
     * dans ce cas aucun temps de chargement n'est consomme.
     */
    public abstract boolean activate(Player player, CfPlayer cf, AbilityState state);

    @Override
    protected List<String> extraLore() {
        return List.of("", "<yellow>Temps de chargement : <white>" + Msg.time(cooldownSeconds));
    }
}
