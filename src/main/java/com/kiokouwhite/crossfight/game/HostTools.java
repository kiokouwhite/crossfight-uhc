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
            new Tool("roster", 1, Material.ARMOR_STAND, "<aqua><bold>Personnages et licences", List.of("Desactiver des personnages, des licences,", "des formats d'equipe ; autoriser les doublons", "", "<yellow>Clic droit <gray>: ouvrir")),
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
    /** La place de chaque regle dans le menu (6 rangees). */
    private static final Map<String, Integer> PLACES = new LinkedHashMap<>();
    private static final int MENU_SIZE = 54;

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
        dial("doublons", 25, Material.ARMOR_STAND, 1);
        dial("lootbox", 29, Material.CHEST, 5);
        dial("lootbox-nombre", 28, Material.ENDER_CHEST, 1);
        dial("regna-ferox", 30, Material.GOLDEN_SWORD, 5);
        dial("match-found", 31, Material.BELL, 5);
        dial("judgement", 32, Material.LIGHTNING_ROD, 5);
        dial("omori", 33, Material.WHITE_TULIP, 5);
        dial("armure-depart", 34, Material.IRON_CHESTPLATE, 1);
        dial("blocs-poses", 37, Material.COBBLESTONE, 5);
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
            case "roster" -> openRoster(p);
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
    }

    // ------------------------------------------------------------------ le menu des equipes

    private static final class TeamMenu implements InventoryHolder {
        Inventory inv;
        Player owner;
        @Override public Inventory getInventory() { return inv; }
    }

    /** Places des boutons du haut (jouer seul, creer un duo, creer un trio, quitter) et de la premiere equipe. */
    private static final int SOLO_PLACE = 1, DUO_PLACE = 3, TRIO_PLACE = 5, LEAVE_PLACE = 7, WATCH_PLACE = 8, RANDOM_PLACE = 4, TEAMS_FIRST = 18, TEAM_MENU_SIZE = 54;

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
        boolean watching = plugin.players().get(p).isSpectatorOnly();
        ItemStack watch = Items.of(Material.ENDER_EYE, watching ? "<green><bold>Tu regardes la partie" : "<gray>Regarder la partie",
                List.of("Spectateur : tu ne joues pas cette partie", "", "<yellow>Clic <gray>: " + (watching ? "jouer finalement" : "regarder")));
        watch.editMeta(m -> m.setEnchantmentGlintOverride(watching));
        menu.inv.setItem(WATCH_PLACE, watch);
        if (plugin.roster().randomTeams()) {
            // Equipes tirees au sort : rien a choisir, sinon de regarder.
            menu.inv.setItem(RANDOM_PLACE, Items.of(Material.ENDER_CHEST, "<gold><bold>Equipes aleatoires",
                    List.of("Les equipes sont tirees au sort au lancement", "<white>" + plugin.teams().teamSize().label())));
            return;
        }
        menu.inv.setItem(SOLO_PLACE, create(TeamSize.SOLO, "<gold><bold>Jouer en solitaire", "Seul contre tous, avec un Personnage Solitaire", "jouer seul"));
        menu.inv.setItem(DUO_PLACE, create(TeamSize.DUO, "<gold><bold>Creer un duo", "Une equipe de deux, avec une licence de Groupe Duo", "creer un duo"));
        menu.inv.setItem(TRIO_PLACE, create(TeamSize.TRIO, "<gold><bold>Creer une equipe de trois", "Une equipe de trois, avec une licence d'Equipe de Trois", "creer une equipe de trois"));
        menu.inv.setItem(LEAVE_PLACE, mine == null ? Items.of(Material.LIGHT_GRAY_DYE, watching ? "<gray>Aucune equipe" : "<red>Aucune equipe", List.of(watching ? "Tu regardes la partie" : "La partie ne se lance pas tant que", watching ? "" : "tu n'as pas choisi"))
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
        String closed = closed(kind);
        ItemStack it = closed != null ? Items.of(Material.BARRIER, "<red>" + kind.label(), List.of(closed))
                : Items.of(Material.PLAYER_HEAD, name, List.of(what, "", "<yellow>Clic <gray>: " + action));
        it.setAmount(kind.size());
        return it;
    }

    /** Pourquoi on ne peut pas (ou plus) creer d'equipe de ce format, ou null. */
    private String closed(TeamSize kind) {
        if (!plugin.roster().on(kind)) return "Format desactive pour cette partie";
        int room = plugin.roster().room(kind), taken = 0;
        if (room == 0) return "Aucune licence jouable dans ce format";
        for (var t : plugin.teams().all()) if (t.size() > 0 && t.kind() == kind) taken++;
        return taken >= room ? "Complet : plus de licence libre sans doublons (" + room + " au plus)" : null;
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
        var cf = plugin.players().get(p);
        if (slot == WATCH_PLACE) {
            boolean watch = !cf.isSpectatorOnly();
            cf.setSpectatorOnly(watch);
            if (watch) leaveTeam(p, mine);
            Msg.send(p, watch ? "<yellow>Tu regarderas la partie en spectateur." : "<green>Tu joues : choisis ton equipe.");
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, watch ? 0.9f : 1.3f);
            refreshTeamMenus();
            return;
        }
        if (plugin.roster().randomTeams()) return;
        TeamSize kind = slot == SOLO_PLACE ? TeamSize.SOLO : slot == DUO_PLACE ? TeamSize.DUO : slot == TRIO_PLACE ? TeamSize.TRIO : null;
        if (kind != null) {
            // Deja seul dans une equipe de ce format : rien a refaire. Format ferme : refuse.
            if (mine != null && mine.kind() == kind && mine.size() == 1) { deny(p); return; }
            if (closed(kind) != null) { deny(p); Msg.actionBar(p, "<red>" + closed(kind) + "."); return; }
            leaveTeam(p, mine);
            cf.setSpectatorOnly(false);
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
            cf.setSpectatorOnly(false);
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

    // ------------------------------------------------------------------ le menu des personnages et licences

    /** Le menu des licences (licence == null) ou celui des personnages d'une licence. */
    private static final class RosterMenu implements InventoryHolder {
        Inventory inv;
        com.kiokouwhite.crossfight.character.Licence licence;
        @Override public Inventory getInventory() { return inv; }
    }

    private static final int FORMAT_FIRST = 1, DUPLICATES_PLACE = 5, RANDOM_TOGGLE = 7, RANDOM_FORMAT = 8, LICENCES_FIRST = 19, BACK_PLACE = 22;
    /** Ceux qui ont change quelque chose dans ces menus : une annonce a la fermeture. */
    private final java.util.Set<UUID> rosterChanged = new java.util.HashSet<>();

    private void openRoster(Player p) {
        RosterMenu menu = new RosterMenu();
        menu.inv = Bukkit.createInventory(menu, 45, Msg.mm("<dark_gray>Personnages et licences"));
        fill(menu);
        p.openInventory(menu.inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.5f, 1.3f);
    }

    private void openLicence(Player p, com.kiokouwhite.crossfight.character.Licence licence) {
        RosterMenu menu = new RosterMenu();
        menu.licence = licence;
        menu.inv = Bukkit.createInventory(menu, 27, Msg.mm("<dark_gray>" + licence.name()));
        fill(menu);
        p.openInventory(menu.inv);
    }

    private List<com.kiokouwhite.crossfight.character.Licence> shownLicences() {
        return new ArrayList<>(plugin.characters().licences());
    }

    private ItemStack toggle(Material icon, boolean on, String name, List<String> lore, int amount) {
        ItemStack it = Items.of(on ? icon : Material.GRAY_DYE, (on ? "<green><bold>" : "<red>") + name + (on ? "" : " <dark_gray>(desactive)"), lore);
        it.setAmount(amount);
        it.editMeta(m -> m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES));
        return it;
    }

    private void fill(RosterMenu menu) {
        Roster roster = plugin.roster();
        menu.inv.clear();
        ItemStack pane = Items.of(Material.GRAY_STAINED_GLASS_PANE, "<gray> ", null);
        if (menu.licence != null) {
            // Les personnages d'une licence.
            int place = 0;
            for (var c : menu.licence.characters()) {
                boolean on = roster.on(c);
                List<String> lore = List.of(on ? "<green>Jouable" : "<red>Desactive", "", "<yellow>Clic <gray>: " + (on ? "desactiver" : "activer"));
                menu.inv.setItem(place++, on ? com.kiokouwhite.crossfight.util.Skins.icon(c, c.icon(), "<green><bold>" + c.name(), lore)
                        : Items.of(Material.GRAY_DYE, "<red>" + c.name() + " <dark_gray>(desactive)", lore));
            }
            for (int i = 18; i < 27; i++) menu.inv.setItem(i, pane);
            menu.inv.setItem(BACK_PLACE, Items.of(Material.ARROW, "<white>Retour aux licences", null));
            return;
        }
        for (int i = 0; i < 45; i++) if (i < 9 || i >= 36 || i % 9 == 0 || i % 9 == 8) menu.inv.setItem(i, pane);
        for (TeamSize kind : TeamSize.values()) {
            boolean on = roster.on(kind);
            menu.inv.setItem(FORMAT_FIRST + kind.ordinal(), toggle(Material.PLAYER_HEAD, on, kind.label(),
                    List.of("Format d'equipe", "", "<yellow>Clic <gray>: " + (on ? "desactiver" : "activer")), kind.size()));
        }
        boolean dup = roster.duplicates();
        menu.inv.setItem(DUPLICATES_PLACE, toggle(Material.ARMOR_STAND, dup, "Doublons " + (dup ? "autorises" : "interdits"),
                List.of("Autorises : deux equipes peuvent avoir la meme licence,", "deux solitaires le meme personnage", "Interdits : une equipe par licence, un solitaire par personnage", "", "<yellow>Clic <gray>: " + (dup ? "interdire" : "autoriser")), 2));
        boolean random = roster.randomTeams();
        menu.inv.setItem(RANDOM_TOGGLE, toggle(Material.ENDER_CHEST, random, "Equipes aleatoires",
                List.of(random ? "Les equipes sont tirees au sort au lancement" : "Les joueurs choisissent leur equipe", "", "<yellow>Clic <gray>: " + (random ? "laisser choisir" : "tirer au sort")), 1));
        menu.inv.setItem(RANDOM_FORMAT, Items.of(Material.LEATHER_HELMET, "<gold>Format du tirage <white><bold>" + plugin.teams().teamSize().label(),
                List.of("Le format des equipes tirees au sort", "(ceux qui restent forment une equipe plus petite)", "", "<yellow>Clic <gray>: solitaire, duo, trio")));
        int place = LICENCES_FIRST;
        for (var l : shownLicences()) {
            boolean on = roster.on(l);
            List<String> lore = new ArrayList<>();
            lore.add(l.teamSize().label());
            for (var c : l.characters()) lore.add((roster.on(c) ? "<green>✔ <white>" : "<red>✘ <gray>") + c.name());
            if (on && !roster.playable(l)) lore.add("<red>Pas assez de personnages actifs : elle ne sera pas tiree");
            lore.add("");
            lore.add("<yellow>Clic gauche <gray>: " + (on ? "desactiver" : "activer") + " la licence");
            lore.add("<yellow>Clic droit <gray>: choisir ses personnages");
            var first = l.characters().isEmpty() ? null : l.characters().get(0);
            ItemStack it = !on || first == null ? Items.of(Material.GRAY_DYE, "<red>" + l.name() + (on ? "" : " <dark_gray>(desactivee)"), lore)
                    : com.kiokouwhite.crossfight.util.Skins.icon(first, first.icon(), "<green><bold>" + l.name(), lore);
            it.editMeta(m -> m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES));
            if (place % 9 == 8) place += 2;
            menu.inv.setItem(place++, it);
        }
    }

    @EventHandler
    public void onRosterClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof RosterMenu menu) || !(e.getWhoClicked() instanceof Player p)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != menu.inv) return;
        if (!waiting() || !canHost(p)) { p.closeInventory(); return; }
        Roster roster = plugin.roster();
        int slot = e.getRawSlot();
        if (menu.licence != null) {
            if (slot == BACK_PLACE) { openRoster(p); return; }
            if (slot < 0 || slot >= menu.licence.characters().size()) return;
            var c = menu.licence.characters().get(slot);
            roster.set(c, !roster.on(c));
        } else if (slot >= FORMAT_FIRST && slot < FORMAT_FIRST + TeamSize.values().length) {
            TeamSize kind = TeamSize.values()[slot - FORMAT_FIRST];
            setFormat(kind, !roster.on(kind));
        } else if (slot == DUPLICATES_PLACE) {
            plugin.game().rules().set(Rules.rule("doublons"), roster.duplicates() ? "non" : "oui");
        } else if (slot == RANDOM_TOGGLE) {
            setRandomTeams(!roster.randomTeams());
        } else if (slot == RANDOM_FORMAT) {
            plugin.teams().setTeamSize(TeamSize.values()[(plugin.teams().teamSize().ordinal() + 1) % TeamSize.values().length]);
        } else {
            com.kiokouwhite.crossfight.character.Licence licence = null;
            int place = LICENCES_FIRST;
            for (var l : shownLicences()) {
                if (place % 9 == 8) place += 2;
                if (place++ == slot) licence = l;
            }
            if (licence == null) return;
            if (e.isRightClick()) { rosterChanged.add(p.getUniqueId()); openLicence(p, licence); return; }
            roster.set(licence, !roster.on(licence));
        }
        rosterChanged.add(p.getUniqueId());
        fill(menu);
        refreshTeamMenus();
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.1f);
    }

    /** Un format desactive : les equipes du lobby qui l'avaient sont dissoutes. */
    public void setFormat(TeamSize kind, boolean on) {
        plugin.roster().set(kind, on);
        if (on || !plugin.game().isLobby()) return;
        for (var t : plugin.teams().all()) {
            if (t.kind() != kind) continue;
            for (Player member : t.onlineMembers()) {
                plugin.teams().leave(member);
                Msg.send(member, "<yellow>Le format " + kind.label() + " est desactive : ton equipe est dissoute, choisis-en une autre.");
            }
        }
        plugin.teams().prune();
        refreshTeamMenus();
    }

    /** Equipes tirees au sort : celles que les joueurs avaient formees n'ont plus lieu d'etre. */
    public void setRandomTeams(boolean on) {
        plugin.game().rules().set(Rules.rule("equipes-aleatoires"), on ? "oui" : "non");
        if (on && plugin.game().isLobby()) plugin.teams().clear();
        refreshTeamMenus();
    }

    /** Sorti des menus des personnages (et pas pour passer de l'un a l'autre) : une annonce de ce qui est regle. */
    @EventHandler
    public void onRosterClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof RosterMenu) || !(e.getPlayer() instanceof Player p)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof RosterMenu) return;
            if (rosterChanged.remove(p.getUniqueId())) Msg.broadcast("<aqua>Partie reglee par " + p.getName() + " <dark_gray>: " + plugin.roster().summary());
        });
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
        if (e.getInventory().getHolder() instanceof ScenarioMenu || e.getInventory().getHolder() instanceof TeamMenu || e.getInventory().getHolder() instanceof RosterMenu) return;
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
        {
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
