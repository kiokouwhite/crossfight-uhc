package com.kiokouwhite.crossfight.listener;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.kiokouwhite.crossfight.CrossfightPlugin;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * L'armure portee ne se voit plus sur le corps : on voit le skin du personnage, pas une armure de diamant.
 *
 * Une piece d'armure dit au jeu quel modele dessiner sur le corps (composant "equippable", champ
 * asset_id). On remplace ce modele par un modele vide du pack (equipment/hidden.json) sur toute piece
 * qu'un joueur enfile : elle protege autant, garde son icone dans l'inventaire, mais n'est plus dessinee,
 * ni pour lui ni pour les autres.
 *
 * Retirer le champ ne suffit pas : sans modele, le jeu prend un casque pour un objet quelconque pose sur
 * la tete et dessine son icone au-dessus du crane.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ArmorHider implements Listener {

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final CrossfightPlugin plugin;

    public ArmorHider(CrossfightPlugin plugin) { this.plugin = plugin; }

    private boolean enabled() { return plugin.getConfig().getBoolean("armor.hidden", true); }

    /** Le modele vide du pack. Un client sans le pack ne le trouve pas et ne dessine rien non plus. */
    private static final net.kyori.adventure.key.Key HIDDEN = net.kyori.adventure.key.Key.key("crossfight", "hidden");

    /** Rend la piece invisible une fois portee. Vrai si elle a ete modifiee. */
    public static boolean hide(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getType() == Material.ELYTRA) return false;
        // Seules les vraies pieces d'armure : celles qui, d'origine, ont un modele a dessiner sur le corps
        // (pas une citrouille ni une tete, qui se portent sans).
        Equippable original = item.getType().getDefaultData(DataComponentTypes.EQUIPPABLE);
        if (original == null || original.assetId() == null) return false;
        Equippable worn = item.getData(DataComponentTypes.EQUIPPABLE);
        if (worn == null) worn = original;
        if (HIDDEN.equals(worn.assetId())) return false;
        item.setData(DataComponentTypes.EQUIPPABLE, worn.toBuilder().assetId(HIDDEN));
        return true;
    }

    /** Passe en revue les quatre pieces que porte le joueur. */
    public void apply(Player p) {
        if (!enabled()) return;
        var inv = p.getInventory();
        for (EquipmentSlot slot : ARMOR) {
            ItemStack item = inv.getItem(slot);
            if (hide(item)) inv.setItem(slot, item);
        }
    }

    @EventHandler
    public void onArmorChange(PlayerArmorChangeEvent e) {
        if (!enabled() || e.getNewItem().getType().isAir()) return;
        Player p = e.getPlayer();
        // Au tick suivant : on est en plein changement d'equipement.
        Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) apply(p); });
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) apply(p); }, 5L);
    }
}
