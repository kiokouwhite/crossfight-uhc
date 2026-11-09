package com.kiokouwhite.crossfight.ability;

import com.kiokouwhite.crossfight.player.CfPlayer;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Capacite activable : on l'allume et on l'eteint. Une barre de 0 a 100 se vide
 * pendant qu'elle est active et se remplit quand elle est eteinte.
 */
public abstract class ToggleAbility extends Ability {

    private final double drainPerSecond;
    private final double regenPerSecond;

    protected ToggleAbility(String id, String name, String description, Material icon,
                            double drainPerSecond, double regenPerSecond) {
        super(id, name, description, AbilityType.TOGGLE, icon);
        this.drainPerSecond = drainPerSecond;
        this.regenPerSecond = regenPerSecond;
    }

    public double drainPerSecond() { return drainPerSecond; }
    public double regenPerSecond() { return regenPerSecond; }

    /** Appele a l'activation. Retourne false pour refuser l'activation. */
    public abstract boolean onEnable(Player player, CfPlayer cf, AbilityState state);

    /** Appele a la desactivation (volontaire, barre vide, mort). */
    public abstract void onDisable(Player player, CfPlayer cf, AbilityState state);

    /** Appele chaque seconde pendant que la capacite est active. */
    public void tickActive(Player player, CfPlayer cf, AbilityState state) {}

    @Override
    protected List<String> extraLore() {
        return List.of("", "<yellow>Clic droit pour activer / desactiver");
    }
}
