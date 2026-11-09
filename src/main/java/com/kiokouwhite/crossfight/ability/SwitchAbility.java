package com.kiokouwhite.crossfight.ability;

import com.kiokouwhite.crossfight.player.CfPlayer;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Capacite intervertible : toujours active, mais avec plusieurs modes que l'on
 * change en cliquant.
 */
public abstract class SwitchAbility extends Ability {

    private final List<String> modes;

    protected SwitchAbility(String id, String name, String description, Material icon, List<String> modes) {
        super(id, name, description, AbilityType.SWITCH, icon);
        this.modes = List.copyOf(modes);
    }

    public List<String> modes() { return modes; }
    public String modeName(int index) { return modes.get(Math.floorMod(index, modes.size())); }

    /** Appele quand le mode change. */
    public abstract void onSwitch(Player player, CfPlayer cf, AbilityState state, int oldMode, int newMode);

    @Override
    protected List<String> extraLore() {
        List<String> out = new ArrayList<>();
        out.add("");
        out.add("<yellow>Modes :");
        for (String m : modes) out.add("<white> - " + m);
        return out;
    }
}
