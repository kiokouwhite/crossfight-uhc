package com.kiokouwhite.crossfight.character;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import java.util.UUID;

/**
 * Menu de choix du personnage en debut de partie. Un personnage ne peut etre
 * pris qu'une fois dans l'equipe.
 */
public final class CharacterSelectMenu implements Listener {

    private static final NamespacedKey CHAR_KEY = new NamespacedKey("crossfight", "select_character");

    private final CrossfightPlugin plugin;

    public CharacterSelectMenu(CrossfightPlugin plugin) {
        this.plugin = plugin;
    }

    /** Marqueur pour reconnaitre notre inventaire. */
    private static final class Holder implements InventoryHolder {
        private Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void open(Player p) {
        CfPlayer cf = plugin.players().get(p);
        Team team = cf.team();
        if (team == null || team.licence() == null) {
            Msg.send(p, "<red>Tu n'as pas de licence attribuee.");
            return;
        }
        Licence licence = team.licence();
        if (licence.characters().isEmpty()) {
            Msg.send(p, "<yellow>La licence <white>" + licence.name() + "</white> n'a pas encore de personnage.");
            return;
        }
        Holder holder = new Holder();
        int rows = Math.max(1, (licence.characters().size() + 8) / 9);
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Msg.mm("<dark_gray>Choix : <gold>" + licence.name()));
        holder.inv = inv;

        List<String> taken = takenIn(team);
        int slot = 0;
        for (Character c : licence.characters()) {
            boolean isTaken = taken.contains(c.id());
            List<String> lore = new ArrayList<>();
            lore.add("<" + c.voie().color() + ">" + c.voie().label());
            lore.add("");
            lore.addAll(c.summary());
            lore.add("");
            lore.add(isTaken ? "<red>Deja choisi par un allie" : "<green>Clique pour choisir");
            ItemStack item = Items.of(isTaken ? Material.BARRIER : c.icon(), "<gold><bold>" + c.name(), lore);
            if (!isTaken) Items.tag(item, CHAR_KEY, c.id());
            inv.setItem(slot++, item);
        }
        p.openInventory(inv);
    }

    private List<String> takenIn(Team team) {
        List<String> ids = new ArrayList<>();
        for (UUID u : team.members()) {
            CfPlayer other = plugin.players().get(u);
            if (other != null && other.character() != null) ids.add(other.character().id());
        }
        return ids;
    }

    /** Choisit un personnage pour le joueur et l'equipe si la partie tourne. */
    public boolean choose(Player p, Character c) {
        CfPlayer cf = plugin.players().get(p);
        Team team = cf.team();
        if (team == null || team.licence() == null || team.licence().character(c.id()) == null) return false;
        if (takenIn(team).contains(c.id())) return false;
        cf.setCharacter(c);
        Msg.send(p, "<green>Tu incarnes <gold><bold>" + c.name() + "</bold></gold> <gray>(" + c.voie().label() + ")");
        com.kiokouwhite.crossfight.util.Sounds.selection(p, c.id());
        for (Player ally : team.onlineMembers()) {
            if (ally != p) Msg.send(ally, "<gray>" + p.getName() + " incarne <gold>" + c.name());
        }
        // Un duo au complet : les deux personnages echangent une replique.
        java.util.List<Player> mates = team.onlineMembers();
        if (mates.size() == 2) {
            Player other = mates.get(0) == p ? mates.get(1) : mates.get(0);
            Character oc = plugin.players().get(other).character();
            if (oc != null) com.kiokouwhite.crossfight.util.Sounds.duoDialogue(p, c.id(), other, oc.id());
        }
        if (plugin.game().isRunning()) {
            plugin.abilities().equip(p);
        }
        return true;
    }

    /** Attribue un personnage libre au hasard a ceux qui n'ont pas choisi. */
    public void autoAssign(Team team) {
        Licence licence = team.licence();
        if (licence == null || licence.characters().isEmpty()) return;
        for (Player p : team.onlineMembers()) {
            CfPlayer cf = plugin.players().get(p);
            if (cf.character() != null) continue;
            List<String> taken = takenIn(team);
            List<Character> free = new ArrayList<>();
            for (Character c : licence.characters()) if (!taken.contains(c.id())) free.add(c);
            if (free.isEmpty()) free.addAll(licence.characters());
            Character pick = free.get((int) (Math.random() * free.size()));
            choose(p, pick);
            p.closeInventory();
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack it = e.getCurrentItem();
        String id = Items.tag(it, CHAR_KEY);
        if (id == null) return;
        Character c = plugin.characters().character(id);
        if (c == null) return;
        if (choose(p, c)) {
            p.closeInventory();
        } else {
            Msg.send(p, "<red>Ce personnage n'est plus disponible.");
            open(p);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        CfPlayer cf = plugin.players().get(p);
        if (cf.character() == null && plugin.game().isSelecting()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && cf.character() == null && plugin.game().isSelecting()) open(p);
            }, 20L);
        }
    }
}
