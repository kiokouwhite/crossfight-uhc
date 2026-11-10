package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Licence;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.util.ArenaWorlds;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Mannequins;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mode test : un monde plat ou l'on peut changer de personnage a volonte,
 * avec 10 pommes dorees toujours reapprovisionnees, 2 pieces de diamant
 * Protection 2 et 2 pieces de fer Protection 3.
 */
public final class TestMode implements Listener {

    public static final String WORLD = "crossfight_test";
    private static final NamespacedKey GEAR_KEY = new NamespacedKey("crossfight", "test_gear");
    private static final NamespacedKey CHAR_KEY = new NamespacedKey("crossfight", "test_character");

    private final CrossfightPlugin plugin;
    private final Map<UUID, Location> returnPoints = new HashMap<>();
    /** Testeurs connus lors du dernier arret du serveur : uuid -> id de personnage (ou ""). */
    private final Map<UUID, String> pending = new HashMap<>();

    public TestMode(CrossfightPlugin plugin) {
        this.plugin = plugin;
    }

    public static World world() {
        World w = ArenaWorlds.get(WORLD);
        if (w.getBlockAt(0, ArenaWorlds.FLOOR_Y, 0).getType() == Material.AIR) {
            ArenaWorlds.buildPlatform(w, 45, Material.GRASS_BLOCK, Material.BARRIER, 12);
            // Une couche de terre sous l'herbe pour pouvoir creuser un peu.
            for (int x = -45; x <= 45; x++) for (int z = -45; z <= 45; z++) {
                for (int y = ArenaWorlds.FLOOR_Y - 4; y < ArenaWorlds.FLOOR_Y - 1; y++) w.getBlockAt(x, y, z).setType(Material.DIRT, false);
                w.getBlockAt(x, ArenaWorlds.FLOOR_Y - 1, z).setType(Material.DIRT, false);
                w.getBlockAt(x, ArenaWorlds.FLOOR_Y - 5, z).setType(Material.BEDROCK, false);
            }
            w.setTime(6000);
        }
        return w;
    }

    /** Taille de la plateforme (demi-cote) et hauteur des murs. */
    private static final int HALF = 45;
    private static final int WALL = 12;

    /**
     * Remet le terrain de test a neuf : sol d'herbe, couches de terre, murs, lumieres,
     * tout ce qui depasse est efface et toutes les entites (mannequins, items, fleches,
     * affichages) sont retirees. Les joueurs sont reposes au centre.
     */
    public int clearTerrain() {
        World w = world();
        int changed = 0;
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                boolean edge = Math.abs(x) == HALF || Math.abs(z) == HALF;
                for (int y = ArenaWorlds.FLOOR_Y - 5; y <= ArenaWorlds.FLOOR_Y + WALL + 1; y++) {
                    Material want;
                    if (y == ArenaWorlds.FLOOR_Y - 5) want = Material.BEDROCK;
                    else if (y < ArenaWorlds.FLOOR_Y) want = Material.DIRT;
                    else if (y == ArenaWorlds.FLOOR_Y) want = Material.GRASS_BLOCK;
                    else if (edge && y <= ArenaWorlds.FLOOR_Y + WALL) want = Material.BARRIER;
                    else want = Material.AIR;
                    var b = w.getBlockAt(x, y, z);
                    if (b.getType() != want) { b.setType(want, false); changed++; }
                }
            }
        }
        for (int x = -HALF + 2; x < HALF; x += 8) {
            for (int z = -HALF + 2; z < HALF; z += 8) w.getBlockAt(x, ArenaWorlds.FLOOR_Y, z).setType(Material.SEA_LANTERN, false);
        }
        for (var e : w.getEntities()) {
            if (!(e instanceof Player)) e.remove();
        }
        Mannequins.clearAll();
        for (Player p : w.getPlayers()) {
            p.teleport(ArenaWorlds.center(w));
            p.setFireTicks(0);
            var mh = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            p.setHealth(mh == null ? 20 : mh.getValue());
            if (isTester(p)) gear(p);
        }
        return changed;
    }

    public boolean isTester(Player p) {
        return returnPoints.containsKey(p.getUniqueId());
    }

    // ----------------------------------------------------------- persistance

    private java.io.File file() {
        return new java.io.File(plugin.getDataFolder(), "testmode.yml");
    }

    /** Sauvegarde les testeurs en cours pour les remettre en mode test apres un redemarrage. */
    public void save() {
        org.bukkit.configuration.file.YamlConfiguration yml = new org.bukkit.configuration.file.YamlConfiguration();
        for (UUID u : returnPoints.keySet()) {
            CfPlayer cf = plugin.players().get(u);
            String ch = cf != null && cf.character() != null ? cf.character().id() : "";
            yml.set("testers." + u, ch);
            Location back = returnPoints.get(u);
            if (back != null) yml.set("return." + u, back);
        }
        for (Map.Entry<UUID, String> e : pending.entrySet()) {
            if (!returnPoints.containsKey(e.getKey())) yml.set("testers." + e.getKey(), e.getValue());
        }
        try {
            yml.save(file());
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("Impossible de sauvegarder le mode test : " + ex.getMessage());
        }
    }

    /** Charge les testeurs du dernier arret ; ils reprendront le mode test a leur connexion. */
    public void load() {
        pending.clear();
        if (!file().exists()) return;
        var yml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file());
        var section = yml.getConfigurationSection("testers");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                pending.put(UUID.fromString(key), section.getString(key, ""));
            } catch (IllegalArgumentException ignored) { }
        }
        if (!pending.isEmpty()) plugin.getLogger().info(pending.size() + " testeur(s) seront remis en mode test a leur connexion.");
    }

    /** A appeler a la connexion : remet le joueur en mode test s'il y etait avant le redemarrage. */
    public boolean resumeIfPending(Player p) {
        String charId = pending.remove(p.getUniqueId());
        if (charId == null) return false;
        if (!enter(p)) return false;
        Character c = charId.isEmpty() ? null : plugin.characters().character(charId);
        if (c != null) {
            p.closeInventory();
            change(p, c);
        }
        Msg.send(p, "<gray>Mode test repris apres le redemarrage du serveur.");
        return true;
    }

    public boolean isActive() {
        return plugin.game().state() == GameState.TEST;
    }

    // ---------------------------------------------------------------- entree

    public boolean enter(Player p) {
        GameManager game = plugin.game();
        if (game.state() != GameState.TEST && !game.isLobby()) {
            Msg.send(p, "<red>Une partie est en cours, impossible d'ouvrir le mode test.");
            return false;
        }
        if (game.state() != GameState.TEST) {
            game.enterTestState();
        }
        returnPoints.put(p.getUniqueId(), p.getLocation());
        CfPlayer cf = plugin.players().get(p);
        cf.resetForNewGame(99);
        plugin.abilities().clearStates(p);
        Boosts.clear(p);
        Effects.unroot(p);

        Team team = plugin.teams().create();
        plugin.teams().join(p, team);

        World w = world();
        p.teleport(ArenaWorlds.center(w));
        p.setGameMode(GameMode.SURVIVAL);
        p.getInventory().clear();
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        var mh = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (mh != null) mh.setBaseValue(20.0);
        p.setHealth(20);
        p.setFoodLevel(20);
        p.setSaturation(20);
        gear(p);
        Msg.send(p, "<green>Mode test active. <gray>/cf change pour choisir un personnage, /cf mannequin pour des cibles, /cf test pour sortir.");
        save();
        openMenu(p);
        return true;
    }

    public void leave(Player p) {
        Location back = returnPoints.remove(p.getUniqueId());
        plugin.abilities().unequip(p);
        plugin.players().get(p).setCharacter(null);
        plugin.teams().leave(p);
        Boosts.clear(p);
        Effects.unroot(p);
        p.setAllowFlight(false);
        p.getInventory().clear();
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        var mh = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (mh != null) mh.setBaseValue(20.0);
        p.setHealth(20);
        p.setGameMode(GameMode.ADVENTURE);
        p.teleport(back != null && !back.getWorld().getName().equals(WORLD) ? back : plugin.game().world().getSpawnLocation());
        Msg.send(p, "<yellow>Mode test quitte.");
        pending.remove(p.getUniqueId());
        save();
        if (returnPoints.isEmpty() && plugin.game().state() == GameState.TEST) {
            Mannequins.clearAll();
            plugin.game().leaveTestState();
        }
    }

    public void leaveAll() {
        for (UUID u : new ArrayList<>(returnPoints.keySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) leave(p);
            else returnPoints.remove(u);
        }
    }

    // ------------------------------------------------------------ personnage

    public void change(Player p, Character c) {
        CfPlayer cf = plugin.players().get(p);
        Team team = cf.team();
        if (team == null) return;
        plugin.abilities().unequip(p);
        plugin.abilities().clearStates(p);
        Boosts.clear(p);
        Effects.unroot(p);
        p.setAllowFlight(false);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        var mh = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (mh != null) mh.setBaseValue(20.0);
        p.setHealth(20);
        cf.resetUltimate();
        cf.setUltimateLocked(false);
        cf.setCooldownFactor(1.0);
        // On retire les items non-equipement pour repartir propre.
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && Items.tag(it, GEAR_KEY) == null) inv.setItem(i, null);
        }
        team.setLicence(c.licence());
        cf.setCharacter(c);
        plugin.abilities().equip(p);
        gear(p);
        Msg.send(p, "<green>Tu incarnes <gold><bold>" + c.name() + "</bold></gold> <gray>(" + c.licence().name() + ")");
        save();
    }

    // ------------------------------------------------------------ equipement

    private static ItemStack armor(Material m, int prot) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.addEnchant(Enchantment.PROTECTION, prot, true);
        meta.setUnbreakable(true);
        it.setItemMeta(meta);
        Items.tag(it, GEAR_KEY, "armor");
        return it;
    }

    /** Pose l'equipement de test et remet les pommes dorees a 10. */
    public void gear(Player p) {
        PlayerInventory inv = p.getInventory();
        if (!isGear(inv.getHelmet())) inv.setHelmet(armor(Material.IRON_HELMET, 3));
        if (!isGear(inv.getChestplate())) inv.setChestplate(armor(Material.DIAMOND_CHESTPLATE, 2));
        if (!isGear(inv.getLeggings())) inv.setLeggings(armor(Material.DIAMOND_LEGGINGS, 2));
        if (!isGear(inv.getBoots())) inv.setBoots(armor(Material.IRON_BOOTS, 3));
        refillApples(p);
    }

    private static boolean isGear(ItemStack it) {
        return it != null && Items.tag(it, GEAR_KEY) != null;
    }

    private void refillApples(Player p) {
        PlayerInventory inv = p.getInventory();
        int slot = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && it.getType() == Material.GOLDEN_APPLE) { slot = i; break; }
        }
        if (slot >= 0) {
            ItemStack it = inv.getItem(slot);
            if (it.getAmount() != 10) { it.setAmount(10); inv.setItem(slot, it); }
            return;
        }
        ItemStack apples = new ItemStack(Material.GOLDEN_APPLE, 10);
        Items.tag(apples, GEAR_KEY, "apples");
        if (inv.getItem(7) == null) inv.setItem(7, apples);
        else inv.addItem(apples);
    }

    /** Appele chaque seconde en mode test. */
    public void tick() {
        for (UUID u : new ArrayList<>(returnPoints.keySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            if (p.getGameMode() == GameMode.SURVIVAL) {
                refillApples(p);
                p.setFoodLevel(20);
            }
        }
    }

    /** Mort en mode test : reapparition immediate au centre, sans perte de vie. */
    public void onDeath(Player p, boolean handled) {
        CfPlayer cf = plugin.players().get(p);
        cf.setWaitingRespawn(false);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (!handled) p.teleport(ArenaWorlds.center(world()));
            p.setGameMode(GameMode.SURVIVAL);
            var mh = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            p.setHealth(mh == null ? 20 : mh.getValue());
            p.setFoodLevel(20);
            if (cf.character() != null) plugin.abilities().equip(p);
            gear(p);
            Msg.send(p, "<gray>Tu es mort en mode test : reapparition.");
        });
    }

    // ------------------------------------------------------------------ menu

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void openMenu(Player p) {
        List<Character> all = new ArrayList<>();
        for (Licence l : plugin.characters().licences()) all.addAll(l.characters());
        int rows = Math.max(1, Math.min(6, (all.size() + 8) / 9));
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Msg.mm("<dark_gray>Mode test : choisis un personnage"));
        holder.inv = inv;
        int slot = 0;
        for (Character c : all) {
            if (slot >= inv.getSize()) break;
            List<String> lore = new ArrayList<>();
            lore.add("<gold>" + c.licence().name() + " <dark_gray>(" + c.licence().teamSize().label() + ")");
            lore.add("<" + c.voie().color() + ">" + c.voie().label());
            lore.add("");
            lore.addAll(c.summary());
            lore.add("");
            lore.add("<green>Clique pour incarner");
            ItemStack it = Items.of(c.icon(), "<gold><bold>" + c.name(), lore);
            Items.tag(it, CHAR_KEY, c.id());
            inv.setItem(slot++, it);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String id = Items.tag(e.getCurrentItem(), CHAR_KEY);
        if (id == null) return;
        Character c = plugin.characters().character(id);
        if (c == null || !isTester(p)) return;
        p.closeInventory();
        change(p, c);
    }
}
