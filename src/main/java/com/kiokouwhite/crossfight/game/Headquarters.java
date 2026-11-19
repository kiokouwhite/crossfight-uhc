package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Le quartier general (le lieu "jouki") : pas de quete, une offre. Anna le cede a une equipe contre 5 diamants, 10
 * pommes dorees ou 20 niveaux d'experience ; une seule equipe le tient a la fois, et une autre qui paie le lui prend.
 *
 * Tant qu'ils sont dans le lieu, ses occupants voient sur leur carte (/cf map, qu'Anna leur donne) tous les joueurs en
 * vie. Un clic droit avec la carte ouvre la visee : une croix suit le regard, un clic sur un joueur ouvre sa fiche (son
 * equipement, son inventaire, ses vies, ses kills) et propose une boussole qui le traque. Un joueur traque le sait :
 * une barre rouge le lui dit tant qu'une boussole le suit.
 */
public final class Headquarters implements Listener {

    public static final String PLACE = "jouki";
    /** Les trois prix : ce qu'on donne, combien, et comment on le dit. */
    private static final Object[][] PRICES = {{Material.DIAMOND, 5, "5 diamants"}, {Material.GOLDEN_APPLE, 10, "10 pommes dorées"}, {Material.EXPERIENCE_BOTTLE, 20, "20 niveaux d'expérience"}};
    private static final int[] OFFER_SLOTS = {11, 13, 15};

    private final CrossfightPlugin plugin;
    private final NamespacedKey tracker;
    /** L'equipe qui tient le lieu (sa cle : Quests.teamKey), ou null. */
    private String owner;
    /** Qui est traque : le joueur suivi, et la barre rouge qu'il voit. */
    private final Map<UUID, BossBar> hunted = new HashMap<>();

    public Headquarters(CrossfightPlugin plugin) {
        this.plugin = plugin;
        this.tracker = new NamespacedKey(plugin, "traque");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 20L);
    }

    // ------------------------------------------------------------------ a qui est le lieu

    public String owner() { return owner; }

    public boolean owns(Player p) {
        return owner != null && owner.equals(plugin.quests().teamKey(p));
    }

    /** Vrai si ce joueur est dans le quartier general. */
    public boolean inside(Player p) {
        Structures.Placed here = plugin.structures().at(p.getLocation());
        return here != null && here.name().equals(PLACE);
    }

    /** Vrai si la carte de ce joueur montre tous les joueurs : son equipe tient le lieu, et il s'y trouve. */
    public boolean seesAll(Player p) {
        return owns(p) && inside(p) && p.getGameMode() != GameMode.SPECTATOR;
    }

    /** Ce que dit le panneau du lieu a ce joueur : {etat, couleur}. */
    public String[] state(Player p) {
        if (owner == null) return new String[]{"libre : parle à Anna", "#7a4a12"};
        return owns(p) ? new String[]{"à ton équipe", "#2e6b1f"} : new String[]{"occupé", "#a02020"};
    }

    /** Donne le lieu a l'equipe de ce joueur (l'ancienne le perd, et ses boussoles avec). */
    public void grant(Player p) {
        String team = plugin.quests().teamKey(p);
        if (team.equals(owner)) return;
        String before = owner;
        if (before != null) {
            for (Player old : Bukkit.getOnlinePlayers()) if (before.equals(plugin.quests().teamKey(old))) {
                stripCompasses(old);
                Msg.send(old, "<red>Le quartier général vous a été pris par " + plugin.quests().teamLabel(team) + "<red>.");
                old.playSound(old.getLocation(), Sound.ENTITY_WITHER_HURT, 0.6f, 1.2f);
            }
        }
        owner = team;
        Msg.broadcast("<gold>" + plugin.quests().teamLabel(team) + " <yellow>tient maintenant le <white>quartier général<yellow>.");
        for (Player m : Bukkit.getOnlinePlayers()) if (team.equals(plugin.quests().teamKey(m))) {
            plugin.worldMap().give(m);
            Msg.send(m, "<green>Le quartier général est à vous. <gray>Tant que tu y es, ta carte montre tous les joueurs : <white>clic droit<gray> avec la carte pour viser, <white>clic<gray> sur un joueur pour ouvrir sa fiche.");
            m.playSound(m.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        }
    }

    /** Le lieu redevient libre (nouvelle partie, ou /cf qg liberer). */
    public void reset() {
        for (Player p : Bukkit.getOnlinePlayers()) stripCompasses(p);
        for (Map.Entry<UUID, BossBar> e : hunted.entrySet()) { Player t = Bukkit.getPlayer(e.getKey()); if (t != null) t.hideBossBar(e.getValue()); }
        hunted.clear();
        owner = null;
    }

    // ------------------------------------------------------------------ l'offre d'Anna

    private static ItemStack named(ItemStack it, String name, String... lore) {
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.mm(name).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(Msg.mm(l).decoration(TextDecoration.ITALIC, false));
        meta.lore(lines);
        it.setItemMeta(meta);
        return it;
    }

    private boolean affords(Player p, int i) {
        Material m = (Material) PRICES[i][0];
        int n = (int) PRICES[i][1];
        return m == Material.EXPERIENCE_BOTTLE ? p.getLevel() >= n : p.getInventory().containsAtLeast(new ItemStack(m), n);
    }

    private final class Offer implements InventoryHolder {
        final Inventory inv = Bukkit.createInventory(this, 27, Component.text("Anna - le quartier général"));

        Offer(Player p) {
            for (int i = 0; i < PRICES.length; i++)
                inv.setItem(OFFER_SLOTS[i], named(new ItemStack((Material) PRICES[i][0], Math.min(64, (int) PRICES[i][1])), "<gold>Payer " + PRICES[i][2],
                        affords(p, i) ? "<green>Clic : le quartier général est à ton équipe" : "<red>Tu n'as pas de quoi payer"));
            inv.setItem(22, named(new ItemStack(Material.FILLED_MAP), "<white>Quartier général",
                    owner == null ? "<gray>Libre." : "<gray>Tenu par " + plugin.quests().teamLabel(owner) + "<gray> : payer le lui prend.",
                    "<gray>Une seule équipe le tient à la fois.", "<gray>D'ici, la carte montre tous les joueurs ;", "<gray>on y ouvre leur fiche et on les traque."));
        }

        @Override public Inventory getInventory() { return inv; }
    }

    /** Parler a Anna : l'offre, ou le rappel de ce qu'on peut faire quand le lieu est a soi. */
    public void talk(Player p) {
        if (owns(p)) {
            plugin.worldMap().give(p);
            Msg.send(p, "<yellow>Anna <gray>: <white>Le quartier général est à ton équipe. Tant que tu y es, ta carte montre tous les joueurs : clic droit avec la carte pour viser, clic sur un joueur pour ouvrir sa fiche.");
            return;
        }
        p.openInventory(new Offer(p).inv);
    }

    @EventHandler
    public void onOffer(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Offer offer)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != offer.inv || !(e.getWhoClicked() instanceof Player p)) return;
        for (int i = 0; i < OFFER_SLOTS.length; i++) {
            if (OFFER_SLOTS[i] != e.getSlot()) continue;
            if (!affords(p, i)) { p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
            Material m = (Material) PRICES[i][0];
            int n = (int) PRICES[i][1];
            if (m == Material.EXPERIENCE_BOTTLE) p.setLevel(p.getLevel() - n); else p.getInventory().removeItem(new ItemStack(m, n));
            p.closeInventory();
            grant(p);
            return;
        }
    }

    // ------------------------------------------------------------------ la fiche d'un joueur

    /** Les joueurs que la carte montre a celui-ci : en vie, dans le monde de la partie, pas de son equipe. */
    public List<Player> targets(Player viewer) {
        List<Player> out = new ArrayList<>();
        String mine = plugin.quests().teamKey(viewer);
        for (Player t : plugin.game().world().getPlayers()) {
            if (t.equals(viewer) || t.isDead() || t.getGameMode() == GameMode.SPECTATOR || mine.equals(plugin.quests().teamKey(t))) continue;
            if (out(t)) continue;
            out.add(t);
        }
        return out;
    }

    /** Vrai si ce joueur est elimine : plus de vies, pendant une vraie partie (hors partie et en mode test, personne n'a de vies). */
    private boolean out(Player t) {
        CfPlayer cf = plugin.players().get(t);
        return cf != null && !cf.isAlive() && plugin.game().isRunning() && !plugin.game().isTest();
    }

    private static final int TRACK_SLOT = 8;

    private final class Sheet implements InventoryHolder {
        final Inventory inv;
        final UUID target;

        Sheet(Player viewer, Player t) {
            target = t.getUniqueId();
            inv = Bukkit.createInventory(this, 54, Component.text("Fiche de " + t.getName()));
            CfPlayer cf = plugin.players().get(t);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta skull = (SkullMeta) head.getItemMeta();
            skull.setOwningPlayer(t);
            head.setItemMeta(skull);
            Location at = t.getLocation();
            inv.setItem(0, named(head, "<gold>" + t.getName(),
                    "<gray>Équipe : " + plugin.quests().teamLabel(plugin.quests().teamKey(t)),
                    "<gray>Personnage : <white>" + (cf == null || cf.character() == null ? "aucun" : cf.character().name()),
                    "<gray>Vies : <red>" + "❤".repeat(Math.max(0, cf == null ? 0 : cf.lives())) + " <gray>Kills : <white>" + (cf == null ? 0 : cf.kills()),
                    "<gray>Position : <white>" + at.getBlockX() + ", " + at.getBlockY() + ", " + at.getBlockZ(),
                    "<gray>Distance : <white>" + (viewer.getWorld().equals(t.getWorld()) ? (int) viewer.getLocation().distance(at) + " blocs" : "autre monde")));
            PlayerInventory theirs = t.getInventory();
            ItemStack[] worn = {theirs.getHelmet(), theirs.getChestplate(), theirs.getLeggings(), theirs.getBoots(), theirs.getItemInOffHand()};
            for (int k = 0; k < worn.length; k++) inv.setItem(2 + k, worn[k] == null || worn[k].getType().isAir() ? named(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), "<dark_gray>vide") : worn[k].clone());
            // son inventaire : les trois rangees du sac, puis sa barre d'objets
            for (int k = 9; k < 36; k++) if (theirs.getItem(k) != null) inv.setItem(k, theirs.getItem(k).clone());
            for (int k = 0; k < 9; k++) if (theirs.getItem(k) != null) inv.setItem(36 + k, theirs.getItem(k).clone());
            boolean tracking = hasCompass(viewer, target);
            inv.setItem(TRACK_SLOT, named(new ItemStack(Material.COMPASS), tracking ? "<gray>Tu le traques déjà" : "<red>Traquer ce joueur",
                    tracking ? "<gray>Ta boussole pointe vers lui." : "<gray>Clic : une boussole qui pointe vers lui.", "<dark_gray>Il saura qu'il est traqué."));
            inv.setItem(49, named(new ItemStack(Material.BARRIER), "<red>Fermer"));
        }

        @Override public Inventory getInventory() { return inv; }
    }

    /** Ouvre a ce joueur la fiche d'un autre. */
    public void open(Player viewer, Player target) {
        viewer.openInventory(new Sheet(viewer, target).inv);
        viewer.playSound(viewer.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1f);
    }

    @EventHandler
    public void onSheet(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Sheet sheet)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != sheet.inv || !(e.getWhoClicked() instanceof Player p)) return;
        if (e.getSlot() == 49) { p.closeInventory(); return; }
        if (e.getSlot() != TRACK_SLOT) return;
        Player t = Bukkit.getPlayer(sheet.target);
        if (t == null) { p.closeInventory(); Msg.send(p, "<gray>Ce joueur n'est plus là."); return; }
        if (hasCompass(p, sheet.target)) { p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
        if (p.getInventory().firstEmpty() < 0) { Msg.send(p, "<red>Ton inventaire est plein : libère une place pour la boussole."); return; }
        p.getInventory().addItem(compass(t));
        p.closeInventory();
        p.playSound(p.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 1f, 1f);
        Msg.send(p, "<red>Traque lancée : <white>ta boussole pointe vers " + t.getName() + "<red>. <gray>Il le sait.");
        Msg.send(t, "<red><bold>Tu es traqué !</bold> <gray>Quelqu'un te suit à la boussole depuis le quartier général.");
        t.playSound(t.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.5f, 1.4f);
        tick();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Sheet || e.getInventory().getHolder() instanceof Offer) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ la traque

    private ItemStack compass(Player t) {
        ItemStack it = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) it.getItemMeta();
        meta.setLodestone(t.getLocation());
        meta.setLodestoneTracked(false);
        meta.displayName(Msg.mm("<red>Traque : <white>" + t.getName()).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(tracker, PersistentDataType.STRING, t.getUniqueId().toString());
        it.setItemMeta(meta);
        return it;
    }

    private UUID trackedBy(ItemStack it) {
        if (it == null || it.getType() != Material.COMPASS || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(tracker, PersistentDataType.STRING);
        try { return id == null ? null : UUID.fromString(id); } catch (IllegalArgumentException e) { return null; }
    }

    private boolean hasCompass(Player p, UUID target) {
        for (ItemStack it : p.getInventory().getContents()) if (target.equals(trackedBy(it))) return true;
        return false;
    }

    private void stripCompasses(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (trackedBy(inv.getItem(i)) != null) inv.setItem(i, null);
    }

    /** Chaque seconde : les boussoles suivent leur cible, et qui est traque le voit. */
    private void tick() {
        Set<UUID> now = new HashSet<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerInventory inv = p.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack it = inv.getItem(i);
                UUID id = trackedBy(it);
                if (id == null) continue;
                Player t = Bukkit.getPlayer(id);
                // la boussole d'une equipe qui n'a plus le lieu, ou dont la cible est morte pour de bon, disparait
                if (!owns(p) || t == null || t.getGameMode() == GameMode.SPECTATOR || out(t)) { inv.setItem(i, null); continue; }
                if (!t.isDead() && t.getWorld().equals(p.getWorld())) {
                    CompassMeta meta = (CompassMeta) it.getItemMeta();
                    meta.setLodestone(t.getLocation());
                    meta.setLodestoneTracked(false);
                    it.setItemMeta(meta);
                    inv.setItem(i, it);
                }
                now.add(id);
            }
        }
        for (UUID id : now) {
            Player t = Bukkit.getPlayer(id);
            if (t != null && !hunted.containsKey(id)) {
                BossBar bar = BossBar.bossBar(Msg.mm("<red><bold>⚠ Tu es traqué</bold> <gray>- une boussole te suit"), 1f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
                hunted.put(id, bar);
                t.showBossBar(bar);
            }
        }
        hunted.entrySet().removeIf(e -> {
            if (now.contains(e.getKey())) return false;
            Player t = Bukkit.getPlayer(e.getKey());
            if (t != null) { t.hideBossBar(e.getValue()); Msg.actionBar(t, "<green>Tu n'es plus traqué."); }
            return true;
        });
    }

    /** Une boussole de traque ne se jette pas (elle ne doit pas changer de mains). */
    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (trackedBy(e.getItemDrop().getItemStack()) != null) { e.getItemDrop().remove(); Msg.actionBar(e.getPlayer(), "<gray>Boussole de traque abandonnée."); }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        BossBar bar = hunted.remove(e.getPlayer().getUniqueId());
        if (bar != null) e.getPlayer().hideBossBar(bar);
    }
}
