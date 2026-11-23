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

    /** model : une icone du pack complet ("icone/<model>") a la place de l'objet, quand le pack l'a (null : l'objet). */
    public record Option(Material icon, String name, List<String> lore, Runnable action, String model) {
        public Option(Material icon, String name, List<String> lore, Runnable action) { this(icon, name, lore, action, null); }
    }

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        List<Option> options;
        Consumer<Player> onCancel;
        /** Celui pour qui le menu est ouvert (un joueur pilote voit le sien s'afficher chez son testeur). */
        Player owner;
        boolean done;
        /** Un menu en roue : pour chaque case du coffre, le choix qu'elle porte (-1 : aucun). Null : un menu en rangees. */
        int[] wheel;
        @Override public Inventory getInventory() { return inv; }
    }

    /** Les rangees du coffre d'un menu en roue, et le caractere de son image dans la police "choix". */
    private static final int WHEEL_ROWS = 5;
    private static final char WHEEL_IMAGE = '\uE030';

    /** Le choix (0, 1, 2) que porte une case de la roue, ou -1 : la roue est centree sur la case du milieu, coupee en trois parts. */
    private static int wheelSector(int slot) {
        int dx = (slot % 9 - 4) * 18, dy = (slot / 9 - 2) * 18;
        double d = Math.hypot(dx, dy);
        if (d < 1 || d > 41) return -1;
        double a = Math.toDegrees(Math.atan2(-dy, dx));
        if (a < 0) a += 360;
        // En haut (de 30 a 150 degres) le premier choix, en bas a gauche le deuxieme, en bas a droite le troisieme.
        return a > 30 && a < 150 ? 0 : a >= 150 && a < 270 ? 1 : 2;
    }

    /**
     * Un menu a trois choix presente en roue : un rond coupe en trois parts, une par choix, avec son icone
     * (tools/build_menu_choix.py dessine la roue ; l'ordre des choix est celui de ses parts : en haut, en bas a
     * gauche, en bas a droite). Chaque part est cliquable sur toute sa surface. Sans le pack complet charge, ou
     * avec un autre nombre de choix, c'est le menu en rangees.
     */
    public static void openWheel(Player p, String title, List<Option> options, Consumer<Player> onCancel) {
        if (options.size() != 3 || !Sounds.has("font.choix") || !Sounds.hasPackLoaded(p)) { open(p, title, options, onCancel); return; }
        Holder holder = new Holder();
        holder.options = options;
        holder.onCancel = onCancel;
        holder.owner = p;
        holder.wheel = new int[WHEEL_ROWS * 9];
        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(Msg.mm(title));
        Inventory inv = Bukkit.createInventory(holder, WHEEL_ROWS * 9, net.kyori.adventure.text.Component.text("\uE100" + WHEEL_IMAGE + "\uE101")
                .font(net.kyori.adventure.key.Key.key(Sounds.NS, "choix")).color(net.kyori.adventure.text.format.NamedTextColor.WHITE)
                .append(net.kyori.adventure.text.Component.text(text).font(net.kyori.adventure.key.Key.key("minecraft", "default")).color(INK)));
        holder.inv = inv;
        for (int slot = 0; slot < holder.wheel.length; slot++) {
            int k = holder.wheel[slot] = wheelSector(slot);
            if (k < 0) continue;
            Option o = options.get(k);
            List<String> lore = new ArrayList<>(o.lore());
            lore.add("");
            lore.add("<green>Clique pour choisir");
            // Un objet invisible : l'icone est dans l'image de la roue, la case ne porte que le nom et le clic.
            var item = Items.of(org.bukkit.Material.PAPER, "<gold><bold>" + o.name(), lore);
            var meta = item.getItemMeta();
            meta.setItemModel(new org.bukkit.NamespacedKey(Sounds.NS, "hidden"));
            item.setItemMeta(meta);
            inv.setItem(slot, item);
        }
        p.openInventory(inv);
        if (autoPick) {
            Option first = options.get(0);
            Bukkit.getScheduler().runTaskLater(com.kiokouwhite.crossfight.CrossfightPlugin.get(), () -> {
                if (holder.done) return;
                holder.done = true;
                for (Player viewer : new ArrayList<>(Bukkit.getOnlinePlayers()))
                    if (viewer.getOpenInventory().getTopInventory().getHolder() == holder) viewer.closeInventory();
                first.action().run();
            }, 30L);
        }
    }

    public static void open(Player p, String title, List<Option> options, Consumer<Player> onCancel) {
        Holder holder = new Holder();
        holder.options = options;
        holder.onCancel = onCancel;
        holder.owner = p;
        int size = options.size() <= 5 ? 9 : Math.min(54, ((Math.min(options.size(), 54) + 8) / 9) * 9);
        if (options.size() > 54) options = options.subList(0, 54);
        Inventory inv = Bukkit.createInventory(holder, size, dressed(p, title, options.size(), size / 9));
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
            var item = Items.of(o.icon(), "<gold><bold>" + o.name(), lore);
            if (o.model() != null && Sounds.has("icone." + o.model())) {
                var meta = item.getItemMeta();
                meta.setItemModel(new org.bukkit.NamespacedKey(Sounds.NS, "icone/" + o.model()));
                item.setItemMeta(meta);
            }
            inv.setItem(start + i * step, item);
        }
        p.openInventory(inv);
        if (autoPick && !options.isEmpty()) {
            // Tournage des videos du guide : le menu reste un instant a l'ecran, puis le premier choix est pris.
            Option first = options.get(0);
            Bukkit.getScheduler().runTaskLater(com.kiokouwhite.crossfight.CrossfightPlugin.get(), () -> {
                if (holder.done) return;
                holder.done = true;
                for (Player viewer : new ArrayList<>(Bukkit.getOnlinePlayers()))
                    if (viewer.getOpenInventory().getTopInventory().getHolder() == holder) viewer.closeInventory();
                first.action().run();
            }, 30L);
        }
    }

    /** La couleur du titre sur la fenetre du pack d'interface (le brun de ses textes). */
    private static final net.kyori.adventure.text.format.TextColor INK = net.kyori.adventure.text.format.TextColor.color(0x3E426E);

    /**
     * Le titre du menu. Avec le pack complet charge, l'image du pack d'interface qui habille le coffre
     * (tools/build_menu_choix.py : une image par forme de menu, dans la police "choix") puis le titre, en clair,
     * dans la couleur du pack ; sinon le titre tel quel, sur le coffre de Minecraft.
     */
    private static net.kyori.adventure.text.Component dressed(Player p, String title, int choices, int rows) {
        net.kyori.adventure.text.Component plain = Msg.mm(title);
        if (!Sounds.has("font.choix") || !Sounds.hasPackLoaded(p)) return plain;
        char image = (char) (choices <= 5 ? 0xE010 + Math.max(1, choices) - 1 : 0xE020 + Math.max(1, Math.min(6, rows)) - 1);
        String text = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(plain);
        return net.kyori.adventure.text.Component.text("\uE100" + image + "\uE101")
                .font(net.kyori.adventure.key.Key.key(Sounds.NS, "choix")).color(net.kyori.adventure.text.format.NamedTextColor.WHITE)
                .append(net.kyori.adventure.text.Component.text(text).font(net.kyori.adventure.key.Key.key("minecraft", "default")).color(INK));
    }

    /** Vrai pendant le tournage des videos (/cf tournage) : chaque menu choisit tout seul sa premiere option. */
    public static boolean autoPick;

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
        int idx;
        if (h.wheel != null) {
            idx = e.getRawSlot() >= 0 && e.getRawSlot() < h.wheel.length ? h.wheel[e.getRawSlot()] : -1;
            if (idx < 0) return;
        } else {
            int start = n <= 5 ? (9 - (n * 2 - 1)) / 2 : 0;
            int step = n <= 5 ? 2 : 1;
            idx = (e.getRawSlot() - start) / step;
            if (idx < 0 || idx >= n || (e.getRawSlot() - start) % step != 0) return;
        }
        h.done = true;
        p.closeInventory();
        Bukkit.getScheduler().runTask(CrossfightPlugin.get(), h.options.get(idx).action());
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        if (!h.done && h.onCancel != null && e.getPlayer() instanceof Player p) {
            h.done = true;
            Player owner = h.owner != null ? h.owner : p;
            Bukkit.getScheduler().runTask(CrossfightPlugin.get(), () -> h.onCancel.accept(owner));
        }
    }
}
