package com.kiokouwhite.crossfight.listener;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Le sac a dos d'equipe : pendant la partie, chaque joueur a dans son inventaire (en haut a droite) un sac qu'il
 * ne peut ni deplacer ni jeter. Un clic dessus ouvre un coffre de trois rangees, le meme pour toute son equipe :
 * ce que l'un y pose, les autres le voient et peuvent le prendre, ou qu'ils soient. Les objets de capacite et les
 * armes des personnages n'y entrent pas. Le sac est vide a chaque nouvelle partie ; en mode test, chacun a le sien.
 */
public final class Backpack implements Listener {

    public static final NamespacedKey KEY = new NamespacedKey("crossfight", "backpack");
    /** Sa place : la derniere case de la premiere rangee de l'inventaire (en haut a droite). */
    public static final int SLOT = 17;

    private final CrossfightPlugin plugin;
    private final Map<String, Inventory> bags = new HashMap<>();

    private static final class Bag implements InventoryHolder {
        Inventory inv;
        /** Vrai si son titre porte la fenetre du pack d'interface. */
        boolean fancy;
        @Override public Inventory getInventory() { return inv; }
    }

    public Backpack(CrossfightPlugin plugin) {
        this.plugin = plugin;
        // Le sac revient a sa place s'il en a disparu (mort, changement de personnage, inventaire vide).
        Bukkit.getScheduler().runTaskTimer(plugin, this::ensureAll, 20L, 20L);
    }

    /** Vide tous les sacs (nouvelle partie). */
    public void reset() {
        for (Inventory inv : bags.values()) {
            for (var viewer : List.copyOf(inv.getViewers())) viewer.closeInventory();
            inv.clear();
        }
        bags.clear();
    }

    public static boolean isBackpack(ItemStack it) { return it != null && Items.tag(it, KEY) != null; }

    private static ItemStack item() {
        ItemStack it = Items.of(Material.CHEST, "<gold><bold>Sac à dos d'équipe",
                List.of("<gray>Un coffre partage par toute ton equipe,", "<gray>ou que soient tes allies.", "", "<yellow>Clic <gray>: ouvrir"));
        Items.tag(it, KEY, "1");
        // Son icone du pack complet, quand il en a une (tools/build_icones.py : "sac_a_dos").
        if (Sounds.has("icone.sac_a_dos")) {
            var meta = it.getItemMeta();
            meta.setItemModel(new NamespacedKey(Sounds.NS, "icone/sac_a_dos"));
            it.setItemMeta(meta);
        }
        return it;
    }

    /** La cle du sac d'un joueur : son equipe en partie, lui seul en mode test ; null s'il n'en a pas. */
    private String keyOf(Player p) {
        if (!plugin.game().isRunning() || p.getGameMode() == GameMode.SPECTATOR) return null;
        if (plugin.testMode().isTester(p)) return "test:" + p.getUniqueId();
        if (plugin.game().isTest()) return null;
        CfPlayer cf = plugin.players().get(p);
        Team t = plugin.teams().teamOf(p);
        return t == null || cf == null || !cf.isAlive() ? null : "team:" + t.id();
    }

    private void ensureAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            var inv = p.getInventory();
            boolean owns = keyOf(p) != null;
            for (int i = 0; i < inv.getSize(); i++) {
                if (isBackpack(inv.getItem(i)) && (!owns || i != SLOT)) inv.setItem(i, null);
            }
            if (!owns || isBackpack(inv.getItem(SLOT))) continue;
            ItemStack there = inv.getItem(SLOT);
            inv.setItem(SLOT, item());
            // Ce qui occupait la case retourne ailleurs dans l'inventaire, ou tombe a ses pieds s'il est plein.
            if (there != null && !there.getType().isAir())
                for (ItemStack left : inv.addItem(there).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }

    private void open(Player p) {
        String key = keyOf(p);
        if (key == null) return;
        Inventory inv = bags.get(key);
        // La fenetre du pack d'interface est posee par le titre, et chacun la voit ou non selon son pack : le coffre est
        // donc recree pour celui qui l'ouvre si son titre ne lui convient pas, avec le meme contenu.
        var title = com.kiokouwhite.crossfight.util.ChoiceMenu.window(p, "Sac à dos d'équipe", 3);
        boolean fancy = Sounds.has("font.choix") && Sounds.hasPackLoaded(p);
        if (inv == null || ((Bag) inv.getHolder()).fancy != fancy) {
            Bag holder = new Bag();
            holder.fancy = fancy;
            holder.inv = Bukkit.createInventory(holder, 27, title);
            if (inv != null) {
                holder.inv.setContents(inv.getContents());
                for (var viewer : List.copyOf(inv.getViewers())) viewer.closeInventory();
                inv.clear();
            }
            inv = holder.inv;
            bags.put(key, inv);
        }
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, 0.8f, 1f);
    }

    // (meme annule par un autre ecouteur : un clic sur le sac doit toujours l'ouvrir)
    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack hotbar = e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0 ? p.getInventory().getItem(e.getHotbarButton()) : null;
        // Le sac ne bouge pas : un clic dessus l'ouvre.
        if (isBackpack(e.getCurrentItem()) || isBackpack(e.getCursor()) || isBackpack(hotbar)) {
            e.setCancelled(true);
            if (isBackpack(e.getCurrentItem()) && !(e.getView().getTopInventory().getHolder() instanceof Bag))
                Bukkit.getScheduler().runTask(plugin, () -> open(p));
            return;
        }
        // Dans le sac : ni objet de capacite, ni arme de personnage.
        if (e.isCancelled() || !(e.getView().getTopInventory().getHolder() instanceof Bag)) return;
        ItemStack offhand = e.getClick() == ClickType.SWAP_OFFHAND ? p.getInventory().getItemInOffHand() : null;
        for (ItemStack it : new ItemStack[]{e.getCurrentItem(), e.getCursor(), hotbar, offhand}) {
            if (it != null && (AbilityEngine.isAbilityItem(it) || AbilityEngine.isWeaponItem(it))) {
                e.setCancelled(true);
                Msg.actionBar(p, "<red>Cet objet ne va pas dans le sac a dos.");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (isBackpack(e.getOldCursor())) { e.setCancelled(true); return; }
        if (e.getView().getTopInventory().getHolder() instanceof Bag
                && (AbilityEngine.isAbilityItem(e.getOldCursor()) || AbilityEngine.isWeaponItem(e.getOldCursor()))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (isBackpack(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        e.getDrops().removeIf(Backpack::isBackpack);
    }
}
