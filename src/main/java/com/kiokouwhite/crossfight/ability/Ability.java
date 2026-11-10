package com.kiokouwhite.crossfight.ability;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
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
    /** Capacite portee par l'arme : pas d'item, son texte est dans la description de l'arme. */
    private boolean weaponBound;

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

    /** Degats infliges par le porteur a un ennemi. Peut modifier ou annuler l'evenement. */
    public void onDealDamage(Player attacker, org.bukkit.entity.LivingEntity victim, EntityDamageByEntityEvent e, AbilityState state) {}

    /** Degats recus par le porteur (toute source). Peut modifier ou annuler l'evenement. */
    public void onDamaged(Player victim, org.bukkit.event.entity.EntityDamageEvent e, AbilityState state) {}

    /** Le porteur vient de tuer un ennemi. */
    public void onKill(Player killer, Player victim, AbilityState state) {}

    /**
     * Le porteur vient de mourir. Retourner true pour que la mort ne coute pas de vie
     * (la capacite prend alors en charge la reapparition).
     */
    public boolean onDeath(Player player, CfPlayer cf, AbilityState state) { return false; }

    /** Marque la capacite comme portee par l'arme. */
    public Ability bindToWeapon() { this.weaponBound = true; return this; }

    /** Texte a afficher dans la barre d'action a la place du statut standard (null = standard). */
    public String barText(Player player, CfPlayer cf, AbilityState state) { return null; }
    public boolean isWeaponBound() { return weaponBound; }

    /** Toutes les capacites ont un item d'information, sauf celles portees par l'arme. */
    public boolean hasItem() { return !weaponBound; }

    /** Vrai si l'item garde son usage vanilla (arc a bander...) : le moteur ne l'intercepte pas au clic droit. */
    public boolean vanillaUse() { return false; }

    /** Lignes de description a ajouter sur l'arme pour une capacite qui lui est liee. */
    public List<String> weaponLore() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("<gold>" + name + " <dark_gray>(" + type.label() + ")");
        for (String line : Items.wrap(description)) lore.add("<gray>" + line);
        lore.addAll(extraLore());
        return lore;
    }

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
