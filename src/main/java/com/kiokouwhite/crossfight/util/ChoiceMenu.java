package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Menu generique a options (choix de voie, categorie de Tutorat, pardon d'Omori...).
 */
public final class ChoiceMenu implements Listener {

    public record Option(Material icon, String name, List<String> lore, Runnable action) {}

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        List<Option> options;
        Consumer<Player> onCancel;
        boolean done;
        @Override public Inventory getInventory() { return inv; }
    }

    public static void open(Player p, String title, List<Option> options, Consumer<Player> onCancel) {
        Holder holder = new Holder();
        holder.options = options;
        holder.onCancel = onCancel;
        int size = options.size() <= 5 ? 9 : Math.min(54, ((Math.min(options.size(), 54) + 8) / 9) * 9);
        if (options.size() > 54) options = options.subList(0, 54);
        Inventory inv = Bukkit.createInventory(holder, size, Msg.mm(title));
        holder.inv = inv;
        // Centre les options sur la premiere ligne si elles sont peu nombreuses.
        int n = options.size();
        int start = n <= 5 ? (9 - (n * 2 - 1)) / 2 : 0;
        int step = n <= 5 ? 2 : 1;
        for (int i = 0; i < n; i++) {
            Option o = options.get(i);
            List<String> lore = new ArrayList<>(o.lore());
            lore.add("");
            lore.add("<green>Clique pour choisir");
            inv.setItem(start + i * step, Items.of(o.icon(), "<gold><bold>" + o.name(), lore));
        }
        p.openInventory(inv);
    }

    public static void open(Player p, String title, List<Option> options) {
        open(p, title, options, null);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || it.getType().isAir()) return;
        int n = h.options.size();
        int start = n <= 5 ? (9 - (n * 2 - 1)) / 2 : 0;
        int step = n <= 5 ? 2 : 1;
        int idx = (e.getRawSlot() - start) / step;
        if (idx < 0 || idx >= n || (e.getRawSlot() - start) % step != 0) return;
        h.done = true;
        p.closeInventory();
        Bukkit.getScheduler().runTask(CrossfightPlugin.get(), h.options.get(idx).action());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        if (!h.done && h.onCancel != null && e.getPlayer() instanceof Player p) {
            h.done = true;
            Bukkit.getScheduler().runTask(CrossfightPlugin.get(), () -> h.onCancel.accept(p));
        }
    }
}
