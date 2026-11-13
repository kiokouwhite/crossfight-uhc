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
import org.bukkit.entity.LivingEntity;
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

    private final ThirdPerson thirdPerson;

    public TestMode(CrossfightPlugin plugin) {
        this.plugin = plugin;
        this.thirdPerson = new ThirdPerson(plugin, this);
    }

    /** Le mode troisieme personne (mannequin qui incarne le personnage et vise le testeur). */
    public ThirdPerson thirdPerson() { return thirdPerson; }

    public static World world() {
        return built(ArenaWorlds.get(WORLD));
    }

    /** Construit la plateforme de test dans un monde vide, si ce n'est pas deja fait. */
    static World built(World w) {
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
        com.kiokouwhite.crossfight.util.TempBlocks.restoreAll();
        int changed = resetPlatform(world());
        World duo = Bukkit.getWorld(DUO_WORLD);
        if (duo != null) changed += resetPlatform(duo);
        World third = Bukkit.getWorld(ThirdPerson.WORLD);
        if (third != null) changed += resetPlatform(third);
        Mannequins.clearAll();
        for (World w : duo == null ? new World[]{world()} : new World[]{world(), duo}) {
            for (Player p : w.getPlayers()) {
                p.teleport(ArenaWorlds.center(w));
                p.setFireTicks(0);
                var mh = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                p.setHealth(mh == null ? 20 : mh.getValue());
                if (isTester(p)) gear(p);
            }
        }
        // La zone duo retrouve ses cibles et chaque testeur son mannequin duo.
        for (UUID u : new ArrayList<>(duoTesters)) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) setupDuoZone(p, null);
        }
        // Troisieme personne : chaque mannequin pilote et son testeur reprennent leur place.
        if (third != null) {
            for (Player p : third.getPlayers()) {
                p.setFireTicks(0);
                var mh = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                p.setHealth(mh == null ? 20 : mh.getValue());
                if (isTester(p)) gear(p);
                if (!thirdPerson.isActive(p)) p.teleport(ArenaWorlds.center(third));
            }
            thirdPerson.afterClear();
        }
        return changed;
    }

    /** Remet une plateforme de test a neuf (sol, murs, lumieres) et retire tout ce qui n'est pas un joueur. */
    private int resetPlatform(World w) {
        int changed = 0;
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                boolean edge = Math.abs(x) == HALF || Math.abs(z) == HALF;
                // Jusqu'a 64 blocs au-dessus du sol : un dome ou une bulle oubliee par un redemarrage disparait aussi.
                for (int y = ArenaWorlds.FLOOR_Y - 5; y <= ArenaWorlds.FLOOR_Y + 64; y++) {
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
            // Le mannequin pilote (troisieme personne) reste : il est rendu a sa place juste apres.
            if (!(e instanceof Player) && !Mannequins.isPilot(e)) e.remove();
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
            // En troisieme personne, c'est le mannequin qui a le personnage : "3p:<personnage>".
            String third = thirdPerson.savedState(u);
            if (third != null) ch = third;
            yml.set("testers." + u, ch);
            // En texte : un Location relu au demarrage, avant le chargement de son monde, ferait une erreur.
            Location back = returnPoints.get(u);
            if (back != null && back.getWorld() != null) {
                yml.set("return." + u, back.getWorld().getName() + ";" + back.getX() + ";" + back.getY() + ";" + back.getZ() + ";" + back.getYaw() + ";" + back.getPitch());
            }
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
    /** Joueurs dont le menu de choix ne doit pas s'ouvrir apres la demande du pack (personnage deja restaure). */
    private final java.util.Set<java.util.UUID> skipMenu = new java.util.HashSet<>();

    public boolean resumeIfPending(Player p) {
        String charId = pending.remove(p.getUniqueId());
        if (charId == null) return false;
        if (charId.startsWith("3p:")) {
            // Il etait en troisieme personne : on l'y remet, avec le personnage de son mannequin.
            Character c = plugin.characters().character(charId.substring(3));
            if (com.kiokouwhite.crossfight.util.Sounds.packPending(p) && c != null) skipMenu.add(p.getUniqueId());
            if (!thirdPerson.enter(p, c)) return false;
            Msg.send(p, "<gray>Mode test (troisieme personne) repris apres le redemarrage du serveur.");
            return true;
        }
        if (!enter(p)) return false;
        Character c = charId.isEmpty() ? null : plugin.characters().character(charId);
        if (c != null) {
            // Le personnage est restaure : le menu, s'il attend la reponse au pack de sons, ne s'ouvrira pas.
            if (com.kiokouwhite.crossfight.util.Sounds.packPending(p)) skipMenu.add(p.getUniqueId());
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
        com.kiokouwhite.crossfight.util.Effects.resetMaxHealth(p);
        p.setHealth(20);
        p.setFoodLevel(20);
        p.setSaturation(20);
        gear(p);
        Msg.send(p, "<green>Mode test active. <gray>/cf change pour choisir un personnage, /cf mannequin pour des cibles, /cf test pour sortir.");
        save();
        // Si la demande du pack de sons est a l'ecran, le menu attend la reponse du joueur.
        com.kiokouwhite.crossfight.util.Sounds.afterPack(p, () -> { if (isTester(p) && !skipMenu.remove(p.getUniqueId())) openMenu(p); });
        return true;
    }

    public void leave(Player p) {
        thirdPerson.close(p);
        if (duoTesters.remove(p.getUniqueId())) clearDuoTargetsIfEmpty();
        Mannequins.removePartners(p);
        Location back = returnPoints.remove(p.getUniqueId());
        plugin.abilities().unequip(p);
        plugin.players().get(p).setCharacter(null);
        plugin.teams().leave(p);
        Boosts.clear(p);
        Effects.unroot(p);
        p.setAllowFlight(false);
        p.getInventory().clear();
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        com.kiokouwhite.crossfight.util.Effects.resetMaxHealth(p);
        p.setHealth(20);
        p.setGameMode(GameMode.ADVENTURE);
        boolean backInTest = back != null && (back.getWorld().getName().equals(WORLD) || back.getWorld().getName().equals(DUO_WORLD) || back.getWorld().getName().equals(ThirdPerson.WORLD));
        p.teleport(back != null && !backInTest ? back : plugin.game().world().getSpawnLocation());
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
        // En troisieme personne, /cf change (et le menu) changent le personnage du mannequin.
        if (thirdPerson.isActive(p)) { thirdPerson.setCharacter(p, c); return; }
        CfPlayer cf = plugin.players().get(p);
        Team team = cf.team();
        if (team == null) return;
        plugin.abilities().unequip(p);
        plugin.abilities().clearStates(p);
        com.kiokouwhite.crossfight.ability.Borrowed.clear(p);
        Boosts.clear(p);
        Effects.unroot(p);
        p.setAllowFlight(false);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        com.kiokouwhite.crossfight.util.Effects.resetMaxHealth(p);
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
        com.kiokouwhite.crossfight.util.Sounds.selection(p, c.id());
        if (isInDuoZone(p)) syncPartner(p, c);
        save();
    }

    /** Le testeur n'incarne plus personne (troisieme personne : il sert de cible au mannequin). */
    void clearCharacter(Player p) {
        CfPlayer cf = plugin.players().get(p);
        plugin.abilities().unequip(p);
        plugin.abilities().clearStates(p);
        com.kiokouwhite.crossfight.ability.Borrowed.clear(p);
        Boosts.clear(p);
        Effects.unroot(p);
        p.setAllowFlight(false);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        com.kiokouwhite.crossfight.util.Effects.resetMaxHealth(p);
        p.setHealth(20);
        cf.resetUltimate();
        cf.setUltimateLocked(false);
        cf.setCooldownFactor(1.0);
        cf.setCharacter(null);
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && Items.tag(it, GEAR_KEY) == null) inv.setItem(i, null);
        }
        gear(p);
    }

    /**
     * La cible de la troisieme personne repart a neuf : les effets, la vie maximale (une capacite peut en
     * retirer pour toujours) et les immobilisations que le mannequin lui a infliges disparaissent.
     */
    void resetTarget(Player p) {
        Effects.unroot(p);
        Boosts.clear(p);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        com.kiokouwhite.crossfight.util.Effects.resetMaxHealth(p);
        p.setHealth(20);
        p.setFireTicks(0);
        p.setFoodLevel(20);
    }

    /** Le menu de choix ne s'ouvrira pas a la prochaine entree (un autre menu, ou un personnage deja choisi, prend le relais). */
    void skipNextMenu(Player p) {
        skipMenu.add(p.getUniqueId());
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

    static boolean isGear(ItemStack it) {
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
                CfPlayer cf = plugin.players().get(p);
                if (!cf.isUltimateLocked()) cf.setUltimateCharge(100);
                if (isInDuoZone(p)) fillDuo(p);
            }
        }
    }

    /** Mort en mode test : reapparition immediate au centre, sans perte de vie. */
    public void onDeath(Player p, boolean handled) {
        CfPlayer cf = plugin.players().get(p);
        cf.setWaitingRespawn(false);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (!handled) p.teleport(thirdPerson.isActive(p) ? thirdPerson.spotOf(p) : isInDuoZone(p) ? duoCenter() : ArenaWorlds.center(world()));
            p.setGameMode(GameMode.SURVIVAL);
            if (thirdPerson.isActive(p)) resetTarget(p);
            var mh = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            p.setHealth(mh == null ? 20 : mh.getValue());
            p.setFoodLevel(20);
            if (cf.character() != null) plugin.abilities().equip(p);
            gear(p);
            Msg.send(p, "<gray>Tu es mort en mode test : reapparition.");
        });
    }

    // -------------------------------------------------------------- zone duo

    /**
     * Zone duo : une plateforme a part pour essayer les ultimes en duo seul. Le testeur y a un mannequin
     * duo a l'image de sa partenaire (clic droit pour en changer), trois cibles devant lui, et ses
     * ultimes en duo toujours prets.
     */
    public static final String DUO_WORLD = "crossfight_test_duo";
    /** Les licences qui ont un ultime en duo : l'Ultime en Duo de RWBY et les Synergies des groupes duo. */
    public static final java.util.Set<String> DUO_LICENCES = java.util.Set.of("rwby", "bloody_snow", "shojas_memories", "gunvolt");
    private final java.util.Set<UUID> duoTesters = new java.util.HashSet<>();

    public static World duoWorld() {
        return built(ArenaWorlds.get(DUO_WORLD));
    }

    private static Location duoCenter() {
        Location c = ArenaWorlds.center(duoWorld());
        c.setYaw(0);
        c.setPitch(0);
        return c;
    }

    public boolean isInDuoZone(Player p) {
        return duoTesters.contains(p.getUniqueId());
    }

    static boolean hasDuo(Character c) {
        return c != null && c.licence() != null && DUO_LICENCES.contains(c.licence().id());
    }

    /** La partenaire proposee par defaut : le premier autre personnage de la licence. */
    private static Character defaultPartner(Character mine) {
        if (!hasDuo(mine)) return null;
        for (Character c : mine.licence().characters()) if (c != mine) return c;
        return null;
    }

    /**
     * Envoie le testeur dans la zone duo (en ouvrant le mode test si besoin).
     * @param partner le personnage du mannequin duo, ou null pour celui par defaut
     */
    public boolean enterDuo(Player p, Character partner) {
        if (thirdPerson.isActive(p)) {
            // On quitte la troisieme personne : le testeur reprend le personnage du mannequin.
            Character c = thirdPerson.characterOf(p);
            thirdPerson.close(p);
            if (c != null) change(p, c);
        }
        if (!isTester(p)) {
            // Le menu complet de personnages ne s'ouvre pas : la zone duo propose le sien.
            skipMenu.add(p.getUniqueId());
            if (!enter(p)) { skipMenu.remove(p.getUniqueId()); return false; }
        }
        duoTesters.add(p.getUniqueId());
        p.teleport(duoCenter());
        p.setFallDistance(0);
        setupDuoZone(p, partner);
        Msg.send(p, "<light_purple><bold>Zone duo.</bold> <gray>Tes ultimes en duo sont toujours prets. Clic droit sur le mannequin duo pour changer son personnage. "
                + "<white>/cf test duo</white> pour revenir au terrain de test, <white>/cf test</white> pour quitter.");
        Character mine = plugin.players().get(p).character();
        if (!hasDuo(mine)) {
            Msg.send(p, "<yellow>Choisis un personnage qui a un ultime en duo (RWBY, Bloody Snow, Shoja's Memories, Armed Blue Gunvolt).");
            com.kiokouwhite.crossfight.util.Sounds.afterPack(p, () -> { if (isInDuoZone(p)) openMenu(p, TestMode::hasDuo, "<dark_gray>Zone duo : choisis ton personnage"); });
        }
        save();
        return true;
    }

    /** Sortie de la zone duo sans retour au terrain (on part vers une autre zone de test). */
    void leaveDuoQuietly(Player p) {
        duoTesters.remove(p.getUniqueId());
        Mannequins.removePartners(p);
        clearDuoTargetsIfEmpty();
    }

    /** Retour au terrain de test normal. */
    public void leaveDuo(Player p) {
        duoTesters.remove(p.getUniqueId());
        Mannequins.removePartners(p);
        p.teleport(ArenaWorlds.center(world()));
        p.setFallDistance(0);
        clearDuoTargetsIfEmpty();
        Msg.send(p, "<gray>Retour au terrain de test.");
    }

    /** Les cibles de la zone duo et le mannequin duo du testeur ; les jauges de duo pleines. */
    private void setupDuoZone(Player p, Character partner) {
        World w = duoWorld();
        Location c = duoCenter();
        // Trois cibles devant le testeur (il regarde vers le sud a son arrivee).
        int targets = 0;
        for (LivingEntity e : w.getLivingEntities()) if (Mannequins.isMannequin(e) && !Mannequins.isAlly(e)) targets++;
        double[][] spots = {{-4, 8}, {0, 11}, {4, 8}};
        for (int i = targets; i < spots.length; i++) {
            Location at = c.clone().add(spots[i][0], 0, spots[i][1]);
            at.setYaw(180);
            Mannequins.spawn(at);
        }
        Mannequins.removePartners(p);
        Character mine = plugin.players().get(p).character();
        Character chosen = partner != null ? partner : defaultPartner(mine);
        if (chosen != null) Mannequins.spawnPartner(partnerSpot(p), chosen, p);
        fillDuo(p);
    }

    /** A gauche du testeur, tourne dans la meme direction que lui. */
    private static Location partnerSpot(Player p) {
        Location at = p.getLocation().clone();
        at.setPitch(0);
        org.bukkit.util.Vector dir = at.getDirection().setY(0);
        if (dir.lengthSquared() < 1e-4) dir = new org.bukkit.util.Vector(0, 0, 1);
        dir.normalize();
        return at.add(new org.bukkit.util.Vector(dir.getZ(), 0, -dir.getX()).multiply(2.0));
    }

    /** Le testeur a change de personnage : sa partenaire reste d'une autre personne de la meme licence. */
    private void syncPartner(Player p, Character mine) {
        if (!hasDuo(mine)) {
            Msg.send(p, "<yellow>" + mine.name() + " n'a pas d'ultime en duo.");
            return;
        }
        List<LivingEntity> partners = Mannequins.partnersOf(p);
        if (partners.isEmpty()) {
            Character d = defaultPartner(mine);
            if (d != null) Mannequins.spawnPartner(partnerSpot(p), d, p);
            return;
        }
        for (LivingEntity m : partners) {
            Character theirs = plugin.characters().character(Mannequins.partnerCharacterOf(m));
            if (theirs == null || theirs == mine || theirs.licence() != mine.licence()) {
                Character d = defaultPartner(mine);
                if (d != null) Mannequins.setPartnerCharacter(m, d);
            }
        }
    }

    /** Les jauges de duo du testeur avec ses vrais partenaires (un second testeur) a 100 %. */
    private void fillDuo(Player p) {
        var rwby = plugin.characters().licence("rwby");
        if (rwby != null && rwby.mechanic() instanceof com.kiokouwhite.crossfight.character.rwby.RWBY.Duo duo) duo.fill(p, 100);
        var syn = com.kiokouwhite.crossfight.character.duo.Synergy.of(p);
        if (syn != null) syn.fill(p, 100);
    }

    private void clearDuoTargetsIfEmpty() {
        if (!duoTesters.isEmpty()) return;
        World w = Bukkit.getWorld(DUO_WORLD);
        if (w == null) return;
        for (LivingEntity e : w.getLivingEntities()) if (Mannequins.isMannequin(e)) e.remove();
    }

    /** Clic droit sur un mannequin duo : le choix de son personnage. */
    @EventHandler
    public void onClickPartner(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND || !Mannequins.isPartner(e.getRightClicked())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!(e.getRightClicked() instanceof LivingEntity m)) return;
        if (!p.equals(Mannequins.partnerOwner(m))) { Msg.actionBar(p, "<red>Ce mannequin duo est celui d'un autre testeur."); return; }
        Character mine = plugin.players().get(p).character();
        List<com.kiokouwhite.crossfight.util.ChoiceMenu.Option> options = new ArrayList<>();
        for (Licence l : plugin.characters().licences()) {
            if (!DUO_LICENCES.contains(l.id())) continue;
            // Avec un personnage a ultime en duo, seules les autres personnes de sa licence ; sinon, toutes.
            if (hasDuo(mine) && l != mine.licence()) continue;
            for (Character c : l.characters()) {
                if (c == mine) continue;
                List<String> lore = new ArrayList<>();
                lore.add("<gold>" + l.name());
                lore.add("<gray>Clique pour que le mannequin l'incarne.");
                options.add(new com.kiokouwhite.crossfight.util.ChoiceMenu.Option(c.icon(), "<gold><bold>" + c.name(), lore, () -> setPartner(p, m, c)));
            }
        }
        com.kiokouwhite.crossfight.util.ChoiceMenu.open(p, "<dark_gray>Mannequin duo : quel personnage ?", options);
    }

    private void setPartner(Player p, LivingEntity m, Character c) {
        if (!m.isValid()) return;
        Mannequins.setPartnerCharacter(m, c);
        Character mine = plugin.players().get(p).character();
        Msg.actionBar(p, "<light_purple>Le mannequin duo incarne " + c.name() + ".");
        // L'echange des deux personnages s'il existe ; sinon la replique de selection de la partenaire.
        if (mine != null && com.kiokouwhite.crossfight.util.Sounds.hasDialogue(mine.id(), c.id())) {
            com.kiokouwhite.crossfight.util.Sounds.duoDialogue(p, mine.id(), p, c.id());
        } else {
            com.kiokouwhite.crossfight.util.Sounds.voice(m, c.id() + "_selection");
        }
        if (mine != null && mine.licence() != c.licence()) Msg.send(p, "<yellow>Pour lancer l'ultime en duo, prends un personnage de " + c.licence().name() + " (/cf change).");
    }

    // ------------------------------------------------------------------ menu

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        @Override public Inventory getInventory() { return inv; }
    }

    public void openMenu(Player p) {
        if (thirdPerson.isActive(p)) openMenu(p, c -> true, "<dark_aqua>Troisieme personne : quel personnage pour le mannequin ?");
        else openMenu(p, c -> true, "<dark_gray>Mode test : choisis un personnage");
    }

    public void openMenu(Player p, java.util.function.Predicate<Character> filter, String title) {
        List<Character> all = new ArrayList<>();
        for (Licence l : plugin.characters().licences()) for (Character c : l.characters()) if (filter.test(c)) all.add(c);
        int rows = Math.max(1, Math.min(6, (all.size() + 8) / 9));
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, rows * 9, Msg.mm(title));
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
