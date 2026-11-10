package com.kiokouwhite.crossfight.listener;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Restrictions d'equipement :
 *  - aucun craft ni enchantement d'arme (epee, hache, arc, arbalete, trident, masse) ;
 *  - 2 pieces diamant/netherite maximum ;
 *  - Protection 2 max sur le diamant/netherite, Protection 3 max sur le fer.
 */
public final class RestrictionListener implements Listener {

    private final CrossfightPlugin plugin;

    public RestrictionListener(CrossfightPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ armes

    public static boolean isWeapon(ItemStack it) {
        if (it == null) return false;
        String n = it.getType().name();
        return n.endsWith("_SWORD") || n.endsWith("_AXE") || n.equals("BOW") || n.equals("CROSSBOW")
                || n.equals("TRIDENT") || n.equals("MACE");
    }

    @EventHandler
    public void onBreakProtected(org.bukkit.event.block.BlockBreakEvent e) {
        if (com.kiokouwhite.crossfight.util.TempBlocks.isProtected(e.getBlock())) {
            e.setCancelled(true);
            Msg.actionBar(e.getPlayer(), "<red>Ce bloc est incassable pour le moment.");
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        ItemStack result = e.getInventory().getResult();
        if (isWeapon(result)) {
            e.getInventory().setResult(null);
            if (!e.getViewers().isEmpty() && e.getViewers().get(0) instanceof Player p) {
                Msg.actionBar(p, "<red>Les armes ne peuvent pas etre craftees.");
            }
        }
    }

    @EventHandler
    public void onPrepareEnchant(PrepareItemEnchantEvent e) {
        if (isWeapon(e.getItem())) e.setCancelled(true);
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent e) {
        if (isWeapon(e.getItem())) {
            e.setCancelled(true);
            Msg.actionBar(e.getEnchanter(), "<red>Les armes ne peuvent pas etre enchantees.");
        }
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        ItemStack first = e.getInventory().getFirstItem();
        if (isWeapon(first) || isWeapon(e.getResult())) {
            e.setResult(null);
            return;
        }
        // On plafonne la Protection des armures sortant de l'enclume.
        ItemStack result = e.getResult();
        if (result != null && capProtection(result)) e.setResult(result);
    }

    @EventHandler
    public void onGrindstone(PrepareGrindstoneEvent e) {
        if (isWeapon(e.getResult())) e.setResult(null);
    }

    // ---------------------------------------------------------------- armures

    private static boolean isDiamondOrNetherite(ItemStack it) {
        if (it == null) return false;
        String n = it.getType().name();
        return n.startsWith("DIAMOND_") || n.startsWith("NETHERITE_");
    }

    private static boolean isIron(ItemStack it) {
        return it != null && it.getType().name().startsWith("IRON_") && isArmor(it);
    }

    private static boolean isArmor(ItemStack it) {
        if (it == null) return false;
        String n = it.getType().name();
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS");
    }

    /** Reduit la Protection au maximum autorise. Retourne true si l'item a ete modifie. */
    private boolean capProtection(ItemStack it) {
        if (!isArmor(it)) return false;
        int max;
        if (isDiamondOrNetherite(it)) max = plugin.getConfig().getInt("restrictions.max-protection-diamond", 2);
        else if (isIron(it)) max = plugin.getConfig().getInt("restrictions.max-protection-iron", 3);
        else return false;
        int level = it.getEnchantmentLevel(Enchantment.PROTECTION);
        if (level <= max) return false;
        ItemMeta meta = it.getItemMeta();
        meta.removeEnchant(Enchantment.PROTECTION);
        if (max > 0) meta.addEnchant(Enchantment.PROTECTION, max, true);
        it.setItemMeta(meta);
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmorChange(PlayerArmorChangeEvent e) {
        Player p = e.getPlayer();
        ItemStack added = e.getNewItem();
        if (added == null || added.getType() == Material.AIR) return;

        if (capProtection(added)) {
            Msg.actionBar(p, "<red>Protection reduite au maximum autorise.");
        }

        if (isDiamondOrNetherite(added)) {
            int max = plugin.getConfig().getInt("restrictions.max-diamond-pieces", 2);
            int count = 0;
            for (ItemStack piece : p.getInventory().getArmorContents()) {
                if (isDiamondOrNetherite(piece)) count++;
            }
            if (count > max) {
                // On retire la piece qui vient d'etre mise.
                ItemStack back = added.clone();
                p.getInventory().setItem(e.getSlot(), null);
                var left = p.getInventory().addItem(back);
                for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
                Msg.send(p, "<red>Maximum " + max + " pieces en diamant/netherite.");
            }
        }
    }
}
