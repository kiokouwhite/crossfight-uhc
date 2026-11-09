package com.kiokouwhite.crossfight.ability;

import com.kiokouwhite.crossfight.player.CfPlayer;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * L'ultime d'un personnage. Se charge via les degats infliges et recus et se
 * lance quand la jauge atteint 100 %.
 */
public abstract class UltimateAbility extends Ability {

    protected UltimateAbility(String id, String name, String description, Material icon) {
        super(id, name, description, AbilityType.ULTIMATE, icon);
    }

    /** Lance l'ultime. Retourne false pour ne pas consommer la jauge. */
    public abstract boolean cast(Player player, CfPlayer cf, AbilityState state);

    @Override
    protected List<String> extraLore() {
        return List.of("", "<light_purple>Se charge avec les degats infliges et recus");
    }
}
