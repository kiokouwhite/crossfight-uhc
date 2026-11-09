package com.kiokouwhite.crossfight.ability;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Base de toutes les capacites. Une instance est partagee par tous les joueurs
 * qui possedent la capacite ; l'etat par joueur vit dans {@link AbilityState}.
 */
public abstract class Ability {

    private final String id;
    private final String name;
    private final String description;
    private final AbilityType type;
    private final Material icon;

    protected Ability(String id, String name, String description, AbilityType type, Material icon) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.icon = icon;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String description() { return description; }
    public AbilityType type() { return type; }
    public Material icon() { return icon; }

    /** Appele quand le joueur recoit la capacite (debut de partie, reapparition). */
    public void onEquip(Player player, CfPlayer cf) {}

    /** Appele quand le joueur perd la capacite (fin de partie, mort). */
    public void onUnequip(Player player, CfPlayer cf) {}

    /** Appele une fois par seconde tant que le joueur possede la capacite et est en vie. */
    public void tick(Player player, CfPlayer cf, AbilityState state) {}

    /** Les passifs n'ont pas d'item ; les autres oui. */
    public boolean hasItem() { return type != AbilityType.PASSIVE; }

    /** Cree l'item d'activation de la capacite. */
    public ItemStack createItem() {
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>" + type.label());
        lore.addAll(Items.wrap(description));
        lore.addAll(extraLore());
        ItemStack item = Items.of(icon, "<gold>" + name, lore);
        Items.tag(item, AbilityEngine.ABILITY_KEY, id);
        return Items.unbreakable(item);
    }

    /** Lignes de lore supplementaires (temps de chargement, modes...). */
    protected List<String> extraLore() { return List.of(); }

    protected static CrossfightPlugin plugin() { return CrossfightPlugin.get(); }
}
