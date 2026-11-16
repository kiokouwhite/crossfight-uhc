package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.team.TeamSize;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les objets du lobby. Tous les joueurs ont de quoi choisir leur equipe (jouer en solitaire, creer un duo ou
 * une equipe de trois, en rejoindre une). Les outils de l'administrateur de partie : dans le lobby, les operateurs et les joueurs nommes par /cf admin
 * ont en main des objets pour regler la partie sans taper de commande. Les regles s'ouvrent dans un menu (clic
 * gauche : plus, clic droit : moins), les scenarios dans un autre ; la taille des equipes, la nouvelle map, le rappel au lobby et le
 * lancement se font d'un clic droit, objet en main.
 */
public final class HostTools implements Listener {

    /** Un objet de la barre : sa place, son icone, son nom, ce qu'il fait. */
    private record Tool(String id, int slot, Material icon, String name, List<String> lore) {}

    private static final List<Tool> TOOLS = List.of(
            new Tool("regles", 0, Material.COMPARATOR, "<gold><bold>Regles de la partie", List.of("Bordure, PvP, vies, minerais, evenements...", "", "<yellow>Clic droit <gray>: ouvrir le menu")),
            new Tool("equipes", 1, Material.LEATHER_HELMET, "<aqua><bold>Equipes par defaut", List.of("Le format des equipes creees au lancement", "pour ceux qui n'ont pas choisi", "", "<yellow>Clic droit <gray>: solitaire, duo, trio")),
            new Tool("map", 2, Material.GRASS_BLOCK, "<green><bold>Nouvelle map", List.of("Cree un monde tout neuf et y amene tout le monde", "", "<yellow>Deux clics droits <gray>: creer la map")),
            new Tool("rappel", 3, Material.COMPASS, "<white><bold>Rappeler au lobby", List.of("Ramene tous les joueurs sur la plateforme", "", "<yellow>Clic droit <gray>: rappeler")),
            new Tool("scenarios", 4, Material.ENCHANTED_BOOK, "<light_purple><bold>Scenarios", List.of("CutClean, Hasty Boys, Timber...", "", "<yellow>Clic droit <gray>: choisir les scenarios")),
            new Tool("lancer", 8, Material.LIME_DYE, "<green><bold>Lancer la partie", List.of("", "<yellow>Deux clics droits <gray>: lancer")));

    /** L'objet de tous les joueurs : choisir son equipe (case 5 ; case 6 pour un administrateur, qui a deja les siens). */
    private static final Tool TEAM_TOOL = new Tool("equipe", 4, Material.WHITE_BANNER, "<yellow><bold>Choisir son equipe",
            List.of("Jouer en solitaire, creer un duo ou une equipe de trois,", "ou rejoindre une equipe", "", "<yellow>Clic droit <gray>: ouvrir"));

    /** Ce que change un clic dans le menu, par regle, et son icone. */
    private record Dial(Material icon, int step) {}

    private static final Map<String, Dial> DIALS = new LinkedHashMap<>();
    /** La place de chaque regle dans le menu (5 rangees). */
    private static final Map<String, Integer> PLACES = new LinkedHashMap<>();
    private static final int TEAM_PLACE = 25, MENU_SIZE = 45;

    private static void dial(String rule, int place, Material icon, int step) {
        DIALS.put(rule, new Dial(icon, step));
        PLACES.put(rule, place);
    }

    static {
        dial("bordure", 10, Material.LIGHT_BLUE_STAINED_GLASS, 50);
        dial("bordure-finale", 11, Material.BLUE_STAINED_GLASS, 10);
        dial("bordure-debut", 12, Material.CLOCK, 5);
        dial("bordure-duree", 13, Material.HOPPER, 1);
        dial("pvp", 14, Material.IRON_SWORD, 5);
        dial("invincibilite", 15, Material.SHIELD, 5);
        dial("vies", 16, Material.TOTEM_OF_UNDYING, 1);
        dial("dispersion", 19, Material.ENDER_PEARL, 50);
        dial("reapparition", 20, Material.RED_BED, 50);
        dial("force", 21, Material.BLAZE_POWDER, 1);
        dial("minerais", 22, Material.DIAMOND_ORE, 1);
        dial("regeneration", 23, Material.GOLDEN_APPLE, 1);
        dial("jour-permanent", 24, Material.SUNFLOWER, 1);
        dial("lootbox", 29, Material.CHEST, 5);
        dial("regna-ferox", 30, Material.GOLDEN_SWORD, 5);
        dial("match-found", 31, Material.BELL, 5);
        dial("judgement", 32, Material.LIGHTNING_ROD, 5);
        dial("omori", 33, Material.WHITE_TULIP, 5);
    }

    /** Le menu des regles d'un administrateur : ce qu'elles valaient a l'ouverture, pour annoncer les changements. */
    private static final class Menu implements InventoryHolder {
        Inventory inv;
        final Map<String, String> before = new LinkedHashMap<>();
        @Override public Inventory getInventory() { return inv; }
    }

    private final CrossfightPlugin plugin;
    private final NamespacedKey key;
    /** Le premier des deux clics d'un outil qui demande confirmation : lequel, et quand. */
    private final Map<UUID, String> armed = new HashMap<>();
    private final Map<UUID, Long> armedAt = new HashMap<>(), lastUse = new HashMap<>();

    public HostTools(CrossfightPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "outil_admin");
    }

    /** Vrai pour un operateur ou un administrateur de partie. */
    public boolean canHost(Player p) {
        return p.hasPermission("crossfight.admin") || plugin.game().rules().isHost(p.getUniqueId());
    }

    private boolean waiting() {
        return plugin.game().isLobby() || plugin.game().state() == GameState.TEST;
    }

    private String toolOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /**
     * Met les outils dans la barre de ce joueur s'il y a droit (administrateur, hors partie, hors mode test), les
     * lui retire sinon. A appeler quand il arrive au lobby ou que ses droits changent.
     */
    public void refresh(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (toolOf(inv.getItem(i)) != null) inv.setItem(i, null);
        if (!waiting() || plugin.testMode().isTester(p)) return;
        boolean host = canHost(p);
        inv.setItem(host ? TEAM_TOOL.slot() + 1 : TEAM_TOOL.slot(), item(TEAM_TOOL));
        if (host) for (Tool t : TOOLS) inv.setItem(t.slot(), item(t));
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) refresh(p);
    }

    private ItemStack item(Tool t) {
        List<String> lore = new ArrayList<>(t.lore());
        if (t.id().equals("equipes")) lore.add(0, "<white>" + plugin.teams().teamSize().label());
        ItemStack it = Items.of(t.icon(), t.name(), lore);
        it.editMeta(m -> m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DYE));
        return Items.tag(it, key, t.id());
    }

    // ------------------------------------------------------------------ les objets en main

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        String tool = toolOf(e.getItem());
        if (tool == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!waiting() || plugin.testMode().isTester(p) || (!tool.equals("equipe") && !canHost(p))) { refresh(p); return; }
        // Un clic droit maintenu se repete : un seul compte.
        long now = System.currentTimeMillis();
        Long last = lastUse.put(p.getUniqueId(), now);
        if (last != null && now - last < 300) return;
        if (tool.equals("equipe")) {
            if (!plugin.game().isLobby()) Msg.actionBar(p, "<red>Impossible pendant un mode test.");
            else openTeams(p);
            return;
        }
        switch (tool) {
            case "regles" -> openRules(p);
            case "scenarios" -> openScenarios(p);
            case "equipes" -> {
                if (!plugin.game().isLobby()) { Msg.actionBar(p, "<red>Impossible pendant un mode test."); return; }
                TeamSize next = TeamSize.values()[(plugin.teams().teamSize().ordinal() + 1) % TeamSize.values().length];
                plugin.teams().setTeamSize(next);
                Msg.broadcast("<yellow>Equipes par defaut : <gold>" + next.label() + " <gray>(pour ceux qui n'ont pas choisi d'equipe au lancement)");
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                refreshAll();
            }
            case "rappel" -> p.performCommand("cf lobby");
            case "map" -> { if (confirmed(p, tool, "creer une nouvelle map")) p.performCommand("cf regenere"); }
            case "lancer" -> { if (confirmed(p, tool, "lancer la partie")) p.performCommand("cf start"); }
            default -> { }
        }
    }

    /** Les actions lourdes demandent deux clics en moins de cinq secondes. */
    private boolean confirmed(Player p, String tool, String what) {
        long now = System.currentTimeMillis();
        if (tool.equals(armed.get(p.getUniqueId())) && now - armedAt.getOrDefault(p.getUniqueId(), 0L) < 5000) {
            armed.remove(p.getUniqueId());
            return true;
        }
        armed.put(p.getUniqueId(), tool);
        armedAt.put(p.getUniqueId(), now);
        Msg.actionBar(p, "<yellow>Reclique pour " + what + ".");
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7f, 1f);
        return false;
    }

    /** Les outils ne se jettent pas, ne se deplacent pas, ne changent pas de main. */
    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (toolOf(e.getItemDrop().getItemStack()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (toolOf(e.getMainHandItem()) != null || toolOf(e.getOffHandItem()) != null) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ le menu des regles

    private void openRules(Player p) {
        Menu menu = new Menu();
        menu.inv = Bukkit.createInventory(menu, MENU_SIZE, Msg.mm("<dark_gray>Regles de la partie"));
        Rules rules = plugin.game().rules();
        for (Rules.Rule r : Rules.all()) menu.before.put(r.name(), rules.shown(r));
        ItemStack pane = Items.of(Material.GRAY_STAINED_GLASS_PANE, "<gray> ", null);
        for (int i = 0; i < MENU_SIZE; i++) if (i < 9 || i >= MENU_SIZE - 9 || i % 9 == 0 || i % 9 == 8) menu.inv.setItem(i, pane);
        fill(menu);
        p.openInventory(menu.inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.3f);
    }

    private void fill(Menu menu) {
        Rules rules = plugin.game().rules();
        for (Rules.Rule r : Rules.all()) {
            Dial d = DIALS.get(r.name());
            Integer place = PLACES.get(r.name());
            if (d == null || place == null) continue;
            List<String> lore = new ArrayList<>();
            lore.add(r.help());
            lore.add("");
            if (r.toggle()) lore.add("<yellow>Clic <gray>: " + (rules.value(r) == 1 ? "desactiver" : "activer"));
            else {
                String unit = r.unit().isEmpty() ? "" : " " + r.unit();
                lore.add("<yellow>Clic gauche <gray>: +" + d.step() + unit + "   <yellow>Clic droit <gray>: -" + d.step() + unit);
                if (r.max() - r.min() > d.step() * 5) lore.add("<yellow>Maj + clic <gray>: de " + d.step() * 5 + unit + " a la fois");
            }
            ItemStack it = Items.of(d.icon(), "<gold>" + r.name() + " <white><bold>" + rules.shown(r), lore);
            it.editMeta(m -> {
                m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                if (r.toggle() && rules.value(r) == 1) m.setEnchantmentGlintOverride(true);
            });
            menu.inv.setItem(place, it);
        }
        menu.inv.setItem(TEAM_PLACE, Items.of(Material.LEATHER_HELMET, "<gold>equipes <white><bold>" + plugin.teams().teamSize().label(),
                List.of("Format des equipes creees au lancement", "pour ceux qui n'ont pas choisi", "", "<yellow>Clic <gray>: solitaire, duo, trio")));
    }

    // ------------------------------------------------------------------ le menu des equipes

    private static final class TeamMenu implements InventoryHolder {
        Inventory inv;
        Player owner;
        @Override public Inventory getInventory() { return inv; }
    }

    /** Places des boutons du haut (jouer seul, creer un duo, creer un trio, quitter) et de la premiere equipe. */
    private static final int SOLO_PLACE = 1, DUO_PLACE = 3, TRIO_PLACE = 5, LEAVE_PLACE = 7, TEAMS_FIRST = 18, TEAM_MENU_SIZE = 54;

    private static final Map<String, Material> WOOL = Map.ofEntries(
            Map.entry("red", Material.RED_WOOL), Map.entry("blue", Material.BLUE_WOOL), Map.entry("green", Material.LIME_WOOL),
            Map.entry("yellow", Material.YELLOW_WOOL), Map.entry("aqua", Material.LIGHT_BLUE_WOOL), Map.entry("light_purple", Material.MAGENTA_WOOL),
            Map.entry("gold", Material.ORANGE_WOOL), Map.entry("dark_green", Material.GREEN_WOOL), Map.entry("dark_aqua", Material.CYAN_WOOL),
            Map.entry("dark_purple", Material.PURPLE_WOOL), Map.entry("dark_red", Material.RED_TERRACOTTA), Map.entry("dark_blue", Material.BLUE_TERRACOTTA),
            Map.entry("white", Material.WHITE_WOOL), Map.entry("gray", Material.LIGHT_GRAY_WOOL));

    private void openTeams(Player p) {
        TeamMenu menu = new TeamMenu();
        menu.owner = p;
        menu.inv = Bukkit.createInventory(menu, TEAM_MENU_SIZE, Msg.mm("<dark_gray>Choisir son equipe"));
        fill(menu);
        p.openInventory(menu.inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.3f);
    }

    /** Les equipes ont change : les menus ouverts se redessinent. */
    public void refreshTeamMenus() {
        for (Player o : Bukkit.getOnlinePlayers()) {
            if (o.getOpenInventory().getTopInventory().getHolder() instanceof TeamMenu menu) fill(menu);
        }
    }

    /** Les equipes du lobby, dans l'ordre ou le menu les montre. */
    private List<com.kiokouwhite.crossfight.team.Team> shownTeams() {
        List<com.kiokouwhite.crossfight.team.Team> out = new ArrayList<>();
        for (var t : plugin.teams().all()) if (t.size() > 0 && out.size() < TEAM_MENU_SIZE - TEAMS_FIRST) out.add(t);
        return out;
    }

    private void fill(TeamMenu menu) {
        Player p = menu.owner;
        var mine = plugin.teams().teamOf(p);
        menu.inv.clear();
        ItemStack pane = Items.of(Material.GRAY_STAINED_GLASS_PANE, "<gray> ", null);
        for (int i = 0; i < TEAMS_FIRST; i++) menu.inv.setItem(i, pane);
        menu.inv.setItem(SOLO_PLACE, create(TeamSize.SOLO, "<gold><bold>Jouer en solitaire", "Seul contre tous, avec un Personnage Solitaire", "jouer seul"));
        menu.inv.setItem(DUO_PLACE, create(TeamSize.DUO, "<gold><bold>Creer un duo", "Une equipe de deux, avec une licence de Groupe Duo", "creer un duo"));
        menu.inv.setItem(TRIO_PLACE, create(TeamSize.TRIO, "<gold><bold>Creer une equipe de trois", "Une equipe de trois, avec une licence d'Equipe de Trois", "creer une equipe de trois"));
        menu.inv.setItem(LEAVE_PLACE, mine == null ? Items.of(Material.LIGHT_GRAY_DYE, "<gray>Aucune equipe", List.of("Sans equipe au lancement, tu es place", "dans une equipe au hasard (" + plugin.teams().teamSize().label() + ")"))
                : Items.of(Material.BARRIER, "<red><bold>Quitter mon equipe", List.of(mine.name() + " (" + mine.kind().shortLabel() + ")", "", "<yellow>Clic <gray>: quitter")));
        int place = TEAMS_FIRST;
        for (var t : shownTeams()) {
            List<String> lore = new ArrayList<>();
            for (var u : t.members()) {
                var cf = plugin.players().get(u);
                lore.add("<white>- " + (cf == null ? "?" : cf.name()));
            }
            for (int i = t.size(); i < t.capacity(); i++) lore.add("<dark_gray>- place libre");
            lore.add("");
            lore.add(t == mine ? "<green>Ton equipe" : t.isFull() ? "<red>Complete" : "<yellow>Clic <gray>: rejoindre");
            ItemStack it = Items.of(WOOL.getOrDefault(t.color(), Material.WHITE_WOOL), t.coloredName() + " <gray>" + t.kind().shortLabel() + " <white>" + t.size() + "/" + t.capacity(), lore);
            it.setAmount(Math.max(1, t.size()));
            if (t == mine) it.editMeta(m -> m.setEnchantmentGlintOverride(true));
            menu.inv.setItem(place++, it);
        }
    }

    private ItemStack create(TeamSize kind, String name, String what, String action) {
        ItemStack it = Items.of(Material.PLAYER_HEAD, name, List.of(what, "", "<yellow>Clic <gray>: " + action));
        it.setAmount(kind.size());
        return it;
    }

    @EventHandler
    public void onTeamClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof TeamMenu menu) || !(e.getWhoClicked() instanceof Player p)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != menu.inv) return;
        if (!plugin.game().isLobby()) { p.closeInventory(); return; }
        var teams = plugin.teams();
        var mine = teams.teamOf(p);
        int slot = e.getRawSlot();
        TeamSize kind = slot == SOLO_PLACE ? TeamSize.SOLO : slot == DUO_PLACE ? TeamSize.DUO : slot == TRIO_PLACE ? TeamSize.TRIO : null;
        if (kind != null) {
            // Deja seul dans une equipe de ce format : rien a refaire.
            if (mine != null && mine.kind() == kind && mine.size() == 1) { deny(p); return; }
            leaveTeam(p, mine);
            var t = teams.create(kind);
            teams.join(p, t);
            Msg.send(p, kind == TeamSize.SOLO ? "<green>Tu joues en solitaire <gray>(" + t.coloredName() + "<gray>)."
                    : "<green>Tu as cree " + t.coloredName() + " <gray>(" + kind.shortLabel() + ")<green> : les autres la rejoignent depuis ce menu.");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        } else if (slot == LEAVE_PLACE) {
            if (mine == null) { deny(p); return; }
            leaveTeam(p, mine);
            Msg.send(p, "<yellow>Tu as quitte ton equipe.");
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 0.9f);
        } else if (slot >= TEAMS_FIRST) {
            var shown = shownTeams();
            int i = slot - TEAMS_FIRST;
            if (i >= shown.size()) return;
            var t = shown.get(i);
            if (t == mine || t.isFull()) { deny(p); return; }
            leaveTeam(p, mine);
            teams.join(p, t);
            for (Player member : t.onlineMembers()) Msg.send(member, member == p ? "<green>Tu as rejoint " + t.coloredName() + "<green>." : "<green>" + p.getName() + " a rejoint ton equipe.");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.2f);
        } else return;
        refreshTeamMenus();
    }

    private void leaveTeam(Player p, com.kiokouwhite.crossfight.team.Team mine) {
        if (mine == null) return;
        plugin.teams().leave(p);
        for (Player member : mine.onlineMembers()) Msg.send(member, "<yellow>" + p.getName() + " a quitte ton equipe.");
        plugin.teams().prune();
    }

    private void deny(Player p) {
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
    }

    // ------------------------------------------------------------------ le menu des scenarios

    /** Le menu des scenarios : ceux qui etaient coches a l'ouverture, pour annoncer les changements. */
    private static final class ScenarioMenu implements InventoryHolder {
        Inventory inv;
        List<String> before;
        @Override public Inventory getInventory() { return inv; }
    }

    private static final int SCENARIO_FIRST = 10;

    private void openScenarios(Player p) {
        ScenarioMenu menu = new ScenarioMenu();
        menu.inv = Bukkit.createInventory(menu, 27, Msg.mm("<dark_gray>Scenarios"));
        menu.before = plugin.scenarios().active();
        ItemStack pane = Items.of(Material.GRAY_STAINED_GLASS_PANE, "<gray> ", null);
        for (int i = 0; i < 27; i++) if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) menu.inv.setItem(i, pane);
        fill(menu);
        p.openInventory(menu.inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.3f);
    }

    private void fill(ScenarioMenu menu) {
        var all = Scenarios.Scenario.values();
        for (int i = 0; i < all.length; i++) {
            var s = all[i];
            boolean on = plugin.scenarios().on(s);
            ItemStack it = Items.of(s.icon(), (on ? "<green><bold>" : "<gray>") + s.label() + (on ? " <white>active" : " <dark_gray>desactive"),
                    List.of(s.help(), "", "<yellow>Clic <gray>: " + (on ? "desactiver" : "activer")));
            it.editMeta(m -> {
                m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                m.setEnchantmentGlintOverride(on);
            });
            menu.inv.setItem(SCENARIO_FIRST + i, it);
        }
    }

    @EventHandler
    public void onScenarioClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ScenarioMenu menu) || !(e.getWhoClicked() instanceof Player p)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != menu.inv) return;
        if (!waiting() || !canHost(p)) { p.closeInventory(); return; }
        int i = e.getRawSlot() - SCENARIO_FIRST;
        var all = Scenarios.Scenario.values();
        if (i < 0 || i >= all.length) return;
        boolean on = !plugin.scenarios().on(all[i]);
        plugin.scenarios().set(all[i], on);
        fill(menu);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, on ? 1.3f : 0.9f);
    }

    @EventHandler
    public void onScenarioClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof ScenarioMenu menu)) return;
        List<String> now = plugin.scenarios().active();
        if (!now.equals(menu.before)) Msg.broadcast("<light_purple>Scenarios <gray>(" + e.getPlayer().getName() + ") : <white>" + (now.isEmpty() ? "aucun" : String.join("<gray>, <white>", now)));
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getInventory().getHolder() instanceof ScenarioMenu || e.getInventory().getHolder() instanceof TeamMenu) return;
        if (!(e.getInventory().getHolder() instanceof Menu menu)) {
            // Dans son propre inventaire : les outils restent a leur place.
            if (toolOf(e.getCurrentItem()) != null || toolOf(e.getCursor()) != null
                    || (e.getHotbarButton() >= 0 && toolOf(p.getInventory().getItem(e.getHotbarButton())) != null)) e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() != menu.inv) return;
        if (!waiting() || !canHost(p)) { p.closeInventory(); return; }
        Rules rules = plugin.game().rules();
        if (e.getRawSlot() == TEAM_PLACE) {
            if (!plugin.game().isLobby()) return;
            TeamSize next = TeamSize.values()[(plugin.teams().teamSize().ordinal() + 1) % TeamSize.values().length];
            plugin.teams().setTeamSize(next);
            Msg.broadcast("<yellow>Equipes par defaut : <gold>" + next.label() + " <gray>(pour ceux qui n'ont pas choisi d'equipe au lancement)");
            refreshAll();
        } else {
            Rules.Rule rule = null;
            for (var en : PLACES.entrySet()) if (en.getValue() == e.getRawSlot()) rule = Rules.rule(en.getKey());
            if (rule == null) return;
            String wanted;
            if (rule.toggle()) wanted = rules.value(rule) == 1 ? "non" : "oui";
            else {
                int step = DIALS.get(rule.name()).step() * (e.isShiftClick() && rule.max() - rule.min() > DIALS.get(rule.name()).step() * 5 ? 5 : 1);
                int v = rules.value(rule) + (e.isRightClick() ? -step : step);
                wanted = String.valueOf(Math.max(rule.min(), Math.min(rule.max(), v)));
            }
            rules.set(rule, wanted);
        }
        fill(menu);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, e.isRightClick() ? 0.9f : 1.3f);
    }

    /** A la fermeture : une seule annonce, avec tout ce qui a change. */
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu menu)) return;
        Rules rules = plugin.game().rules();
        List<String> changed = new ArrayList<>();
        for (Rules.Rule r : Rules.all()) {
            String now = rules.shown(r);
            if (!now.equals(menu.before.get(r.name()))) changed.add("<gold>" + r.name() + " <white>" + now);
        }
        if (!changed.isEmpty()) Msg.broadcast("<yellow>Regles changees par " + e.getPlayer().getName() + " : " + String.join("<gray>, ", changed));
    }
}
