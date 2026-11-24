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

    private static CrossfightPlugin plugin;

    public RestrictionListener(CrossfightPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ armes

    /**
     * Une arme : epee, arc, arbalete, trident, masse, et la hache d'un personnage. Une hache fabriquee est un
     * outil (elle s'enchante comme une pioche) : elle ne blesse pas les joueurs.
     */
    public static boolean isWeapon(ItemStack it) {
        if (it == null) return false;
        String n = it.getType().name();
        return n.endsWith("_SWORD") || (n.endsWith("_AXE") && !isToolAxe(it)) || n.equals("BOW") || n.equals("CROSSBOW")
                || n.equals("TRIDENT") || n.equals("MACE");
    }

    /** Une hache qui n'est ni l'arme d'un personnage ni un objet de Lootbox : fabriquee, trouvee... */
    public static boolean isToolAxe(ItemStack it) {
        return it != null && it.getType().name().endsWith("_AXE") && !com.kiokouwhite.crossfight.ability.AbilityEngine.isWeaponItem(it)
                && !com.kiokouwhite.crossfight.game.Lootbox.isLoot(it);
    }

    @EventHandler
    public void onBreakProtected(org.bukkit.event.block.BlockBreakEvent e) {
        if (com.kiokouwhite.crossfight.util.TempBlocks.isProtected(e.getBlock())) {
            e.setCancelled(true);
            Msg.actionBar(e.getPlayer(), "<red>Ce bloc est incassable pour le moment.");
        }
    }

    /** Personnages autorises a crafter et enchanter des armes (l'arbre de Shoja l'exige). */
    public static boolean mayForgeWeapons(org.bukkit.entity.HumanEntity h) {
        if (!(h instanceof Player p)) return false;
        var cf = plugin.players().get(p);
        return cf.character() != null && "shoja".equals(cf.character().id());
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        ItemStack result = e.getInventory().getResult();
        if (e.getRecipe() instanceof org.bukkit.Keyed k && k.getKey().getNamespace().equals("crossfight")) return;
        // Les epees : personne (elles viennent du personnage, ou d'une recette du plugin). Les autres armes : Shoja seule.
        boolean sword = result != null && result.getType().name().endsWith("_SWORD");
        if (!sword && !e.getViewers().isEmpty() && mayForgeWeapons(e.getViewers().get(0))) return;
        if (isWeapon(result)) {
            e.getInventory().setResult(null);
            if (!e.getViewers().isEmpty() && e.getViewers().get(0) instanceof Player p) {
                Msg.actionBar(p, sword ? "<red>Les epees ne peuvent pas etre craftees." : "<red>Les armes ne peuvent pas etre craftees.");
            }
        }
    }

    @EventHandler
    public void onPrepareEnchant(PrepareItemEnchantEvent e) {
        if (mayForgeWeapons(e.getEnchanter())) return;
        if (isWeapon(e.getItem())) e.setCancelled(true);
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent e) {
        // Regle aura-fire-aspect : la table d'enchantement ne met pas Aura de feu sur une epee d'Aura.
        if (!plugin.game().rules().on("aura-fire-aspect") && com.kiokouwhite.crossfight.character.unordinary.Unordinary.isAura(e.getItem())
                && e.getEnchantsToAdd().remove(Enchantment.FIRE_ASPECT) != null) {
            Msg.actionBar(e.getEnchanter(), "<red>Aura de feu est desactivee sur les epees d'Aura.");
            if (e.getEnchantsToAdd().isEmpty()) { e.setCancelled(true); return; }
        }
        if (mayForgeWeapons(e.getEnchanter())) return;
        if (isWeapon(e.getItem())) {
            e.setCancelled(true);
            Msg.actionBar(e.getEnchanter(), "<red>Les armes ne peuvent pas etre enchantees.");
        }
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        ItemStack first = e.getInventory().getFirstItem();
        // Regle aura-fire-aspect : l'enclume ne sort pas d'epee d'Aura portant Aura de feu.
        ItemStack out = e.getResult();
        if (out != null && !plugin.game().rules().on("aura-fire-aspect") && com.kiokouwhite.crossfight.character.unordinary.Unordinary.isAura(out)
                && out.containsEnchantment(Enchantment.FIRE_ASPECT) && (first == null || !first.containsEnchantment(Enchantment.FIRE_ASPECT))) {
            e.setResult(null);
            return;
        }
        if (!e.getViewers().isEmpty() && mayForgeWeapons(e.getViewers().get(0))) { ItemStack r = e.getResult(); if (r != null && capProtection(r)) e.setResult(r); return; }
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
        if (!e.getViewers().isEmpty() && mayForgeWeapons(e.getViewers().get(0))) return;
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
        if (plugin.getConfig().getBoolean("visuals.hide-armor", true) && plugin.game().isRunning()) {
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) com.kiokouwhite.crossfight.util.Stealth.hideArmorOnly(p);
            });
        }
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

    /** Regle armures-incassables : une piece d'armure (casque, plastron, jambieres, bottes, elytres) ne s'use pas. */
    @EventHandler(ignoreCancelled = true)
    public void onArmorWear(org.bukkit.event.player.PlayerItemDamageEvent e) {
        if (!plugin.game().rules().on("armures-incassables")) return;
        org.bukkit.inventory.EquipmentSlot slot = e.getItem().getType().getEquipmentSlot();
        if (slot == org.bukkit.inventory.EquipmentSlot.HEAD || slot == org.bukkit.inventory.EquipmentSlot.CHEST
                || slot == org.bukkit.inventory.EquipmentSlot.LEGS || slot == org.bukkit.inventory.EquipmentSlot.FEET) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ feu et lave

    /** Regle seau-de-lave : un seau de lave ne se vide pas. */
    @EventHandler(ignoreCancelled = true)
    public void onLavaBucket(org.bukkit.event.player.PlayerBucketEmptyEvent e) {
        if (e.getBucket() != Material.LAVA_BUCKET || plugin.game().rules().on("seau-de-lave")) return;
        e.setCancelled(true);
        Msg.actionBar(e.getPlayer(), "<red>Les seaux de lave sont desactives.");
    }

    /** Regle briquet : ni le briquet ni la boule de feu n'allument quoi que ce soit (bloc, TNT, creeper). */
    @EventHandler(priority = EventPriority.LOW)
    public void onLighter(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || e.getItem() == null) return;
        Material held = e.getItem().getType();
        if ((held != Material.FLINT_AND_STEEL && held != Material.FIRE_CHARGE) || plugin.game().rules().on("briquet")) return;
        e.setCancelled(true);
        Msg.actionBar(e.getPlayer(), "<red>Les briquets sont desactives.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onLighterOnMob(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        ItemStack held = e.getPlayer().getInventory().getItem(e.getHand());
        if (held.getType() == Material.FLINT_AND_STEEL && !plugin.game().rules().on("briquet")) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ la roche des mines

    /** La roche qui n'est pas un minerai : minee, elle donne de la pierre taillee. */
    private static final java.util.Set<Material> ROCK = java.util.EnumSet.of(Material.STONE, Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, Material.GRANITE,
            Material.DIORITE, Material.ANDESITE, Material.TUFF, Material.CALCITE, Material.DRIPSTONE_BLOCK, Material.SMOOTH_BASALT, Material.BASALT, Material.BLACKSTONE);
    /** Minecraft ne permet pas de pile de plus de 99 objets. */
    private static final int COBBLE_STACK = 99;

    /** Une pile de pierre taillee qui s'empile par 99. */
    public static ItemStack cobble(int amount) {
        ItemStack it = new ItemStack(Material.COBBLESTONE, amount);
        ItemMeta meta = it.getItemMeta();
        meta.setMaxStackSize(COBBLE_STACK);
        it.setItemMeta(meta);
        return it;
    }

    /** Regle roche-en-cobble : la roche minee dans le monde de jeu lache de la pierre taillee. */
    @EventHandler(ignoreCancelled = true)
    public void onRockDrop(org.bukkit.event.block.BlockDropItemEvent e) {
        if (!ROCK.contains(e.getBlockState().getType()) || !plugin.game().rules().on("roche-en-cobble")) return;
        if (!e.getBlock().getWorld().equals(plugin.game().world())) return;
        for (org.bukkit.entity.Item drop : e.getItems()) drop.setItemStack(cobble(drop.getItemStack().getAmount()));
    }

    /** La pierre taillee ramassee autrement (coffre, autre joueur) rejoint les memes piles de 99. */
    @EventHandler(ignoreCancelled = true)
    public void onCobblePickup(org.bukkit.event.entity.EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player) || !plugin.game().rules().on("roche-en-cobble")) return;
        ItemStack it = e.getItem().getItemStack();
        if (it.getType() != Material.COBBLESTONE || it.hasItemMeta()) return;
        e.getItem().setItemStack(cobble(it.getAmount()));
    }
}
