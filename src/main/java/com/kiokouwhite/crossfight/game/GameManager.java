package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.util.Msg;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Machine a etats de la partie et horloge principale.
 */
public final class GameManager {

    static final int SELECTION_SECONDS = 60;
    private static final int COUNTDOWN_SECONDS = 10;

    private final CrossfightPlugin plugin;
    private final GameEvents events;
    private GameState state = GameState.LOBBY;
    private BukkitTask clock;
    /** Secondes ecoulees depuis le debut de la partie. */
    private int elapsed;
    private int phaseCountdown;
    private boolean pvpEnabled;

    public GameManager(CrossfightPlugin plugin) {
        this.plugin = plugin;
        this.events = new GameEvents(plugin);
    }

    public GameState state() { return state; }
    /** Les secondes restantes de la phase en cours (choix du personnage, compte a rebours). */
    public int phaseSeconds() { return phaseCountdown; }
    public boolean isRunning() { return state == GameState.RUNNING || state == GameState.TEST; }
    public boolean isTest() { return state == GameState.TEST; }
    public boolean isSelecting() { return state == GameState.SELECTING; }
    public boolean isLobby() { return state == GameState.LOBBY || state == GameState.ENDED; }
    public int elapsedSeconds() { return elapsed; }
    public int elapsedMinutes() { return elapsed / 60; }
    public boolean isPvpEnabled() { return pvpEnabled || state == GameState.TEST; }
    public GameEvents events() { return events; }

    private Rules rules;

    /** Les regles de la partie (/cf regle) et ses administrateurs (/cf admin). */
    public Rules rules() {
        if (rules == null) rules = new Rules(plugin);
        return rules;
    }

    private boolean borderShrinking;

    /** Vrai une fois que la bordure a commence a se reduire. */
    public boolean borderShrinking() { return borderShrinking; }

    public World world() {
        World w = Bukkit.getWorld(plugin.getConfig().getString("world.name", "world"));
        return w != null ? w : Bukkit.getWorlds().get(0);
    }

    // ----------------------------------------------------------------- nouvelle map

    /** Les mondes de jeu crees par /cf regenere : les seuls que le plugin s'autorise a effacer. */
    public static final String MAP_PREFIX = "crossfight_map_";

    /** Au demarrage du serveur : la map creee par /cf regenere n'est pas chargee par le serveur lui-meme. */
    public void loadWorld() {
        String name = plugin.getConfig().getString("world.name", "world");
        if (Bukkit.getWorld(name) != null || !name.startsWith(MAP_PREFIX)) return;
        if (new java.io.File(Bukkit.getWorldContainer(), name).isDirectory()) new org.bukkit.WorldCreator(name).createWorld();
        else plugin.getLogger().warning("La map " + name + " n'existe plus : la partie se jouera dans le monde principal.");
    }

    /**
     * Une map toute neuve : un nouveau monde (graine au hasard, ou celle donnee) devient le monde de jeu, tous les
     * joueurs y sont amenes sur son lobby, et l'ancienne map est effacee si c'etait deja une map du plugin (le
     * monde principal du serveur, lui, est seulement delaisse). Hors partie uniquement. Rend null en cas d'echec.
     */
    public World regenerate(Long seed) {
        World old = world();
        String name = MAP_PREFIX + Long.toString(System.currentTimeMillis() / 1000, 36);
        var creator = new org.bukkit.WorldCreator(name).environment(World.Environment.NORMAL);
        if (seed != null) creator.seed(seed);
        World w = creator.createWorld();
        if (w == null) return null;
        w.setDifficulty(old.getDifficulty());
        removeLobby(old);
        plugin.getConfig().set("world.name", name);
        plugin.saveConfig();
        prepareWorld();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || (plugin.testMode().isTester(p) && !p.getWorld().equals(old))) continue;
            p.teleport(lobbySpot());
            p.setFallDistance(0);
        }
        if (old.getName().startsWith(MAP_PREFIX)) {
            java.io.File folder = old.getWorldFolder();
            if (Bukkit.unloadWorld(old, false)) Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> erase(folder));
            else plugin.getLogger().warning("L'ancienne map " + old.getName() + " n'a pas pu etre dechargee : son dossier reste sur le disque.");
        }
        return w;
    }

    /** Efface le dossier d'une ancienne map du plugin (et rien d'autre). */
    private void erase(java.io.File folder) {
        if (!folder.getName().startsWith(MAP_PREFIX) || !folder.isDirectory()) return;
        try (var files = java.nio.file.Files.walk(folder.toPath())) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(f -> f.toFile().delete());
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("L'ancienne map " + folder.getName() + " n'a pas pu etre effacee : " + ex.getMessage());
        }
    }

    // ----------------------------------------------------------------- lobby

    /** Hauteur des murs invisibles autour de la plateforme du lobby. */
    private static final int LOBBY_WALL = 5;

    /**
     * Ou attendent les joueurs avant le lancement : au milieu d'une plateforme de verre dans les airs, au-dessus
     * du point d'apparition du monde (lobby.plateforme: false : le point d'apparition lui-meme). La plateforme
     * est construite au besoin ; des murs et un plafond invisibles empechent d'en tomber.
     */
    public Location lobbySpot() {
        World w = world();
        Location spawn = w.getSpawnLocation();
        if (!plugin.getConfig().getBoolean("lobby.plateforme", true)) return spawn;
        int y = lobbyY(w), r = lobbyRadius();
        int cx = spawn.getBlockX(), cz = spawn.getBlockZ();
        // Deja la, a la bonne taille : son plafond au centre et son mur au bord (le sol, lui, change de couleur).
        boolean built = w.getBlockAt(cx, y + LOBBY_WALL + 1, cz).getType() == Material.BARRIER && w.getBlockAt(cx + r + 1, y + 1, cz).getType() == Material.BARRIER;
        if (!built) {
            clearLobby(w, cx, y, cz);
            for (int x = -r - 1; x <= r + 1; x++) {
                for (int z = -r - 1; z <= r + 1; z++) {
                    boolean edge = Math.abs(x) == r + 1 || Math.abs(z) == r + 1;
                    // Le sol : du verre, borde d'un lisere blanc ; autour et au-dessus, des barrieres invisibles.
                    if (!edge) w.getBlockAt(cx + x, y, cz + z).setType(Math.abs(x) == r || Math.abs(z) == r ? Material.WHITE_STAINED_GLASS : Material.GLASS, false);
                    for (int h = edge ? 0 : LOBBY_WALL + 1; h <= LOBBY_WALL + 1; h++) w.getBlockAt(cx + x, y + h, cz + z).setType(Material.BARRIER, false);
                }
            }
        }
        return new Location(w, cx + 0.5, y + 1, cz + 0.5, spawn.getYaw(), 0);
    }

    private static final int LOBBY_MAX = 64;

    private int lobbyY(World w) {
        return Math.max(w.getMinHeight() + 8, Math.min(w.getMaxHeight() - LOBBY_WALL - 3, plugin.getConfig().getInt("lobby.hauteur", 250)));
    }

    private int lobbyRadius() {
        return Math.max(2, Math.min(LOBBY_MAX, plugin.getConfig().getInt("lobby.rayon", 21)));
    }

    /**
     * Retire la plateforme du lobby, quelle que soit sa taille (une plus petite, d'un reglage precedent) : son
     * plafond de barrieres, suivi depuis le centre, dit jusqu'ou elle va. Sans plateforme, rien n'est touche.
     */
    private void clearLobby(World w, int cx, int y, int cz) {
        int reach = 0;
        while (reach <= LOBBY_MAX + 1 && w.getBlockAt(cx + reach, y + LOBBY_WALL + 1, cz).getType() == Material.BARRIER) reach++;
        if (reach == 0) return;
        for (int x = -reach; x <= reach; x++) for (int z = -reach; z <= reach; z++) for (int h = 0; h <= LOBBY_WALL + 1; h++) {
            var b = w.getBlockAt(cx + x, y + h, cz + z);
            if (!b.getType().isAir()) b.setType(Material.AIR, false);
        }
    }

    /** La partie commence : la plateforme du lobby disparait du ciel (elle revient au prochain retour au lobby). */
    private void removeLobby() { removeLobby(world()); }

    private void removeLobby(World w) {
        Location spawn = w.getSpawnLocation();
        int y = lobbyY(w), cx = spawn.getBlockX(), cz = spawn.getBlockZ();
        clearLobby(w, cx, y, cz);
    }

    /** Les couleurs que prend le sol du lobby sous les pas des joueurs. */
    private static final Material[] LOBBY_COLORS = {
            Material.RED_STAINED_GLASS, Material.ORANGE_STAINED_GLASS, Material.YELLOW_STAINED_GLASS, Material.LIME_STAINED_GLASS,
            Material.LIGHT_BLUE_STAINED_GLASS, Material.CYAN_STAINED_GLASS, Material.BLUE_STAINED_GLASS, Material.PURPLE_STAINED_GLASS,
            Material.MAGENTA_STAINED_GLASS, Material.PINK_STAINED_GLASS};

    /**
     * Pour patienter : le bloc de verre du lobby sur lequel un joueur vient de poser le pied change de couleur
     * (jamais deux fois la meme de suite), avec une petite note.
     */
    public void stepOnLobby(Player p, org.bukkit.block.Block under) {
        if (state == GameState.RUNNING || !plugin.getConfig().getBoolean("lobby.plateforme", true)) return;
        World w = world();
        if (!under.getWorld().equals(w) || under.getY() != lobbyY(w) || !under.getType().name().endsWith("GLASS")) return;
        Location spawn = w.getSpawnLocation();
        int r = lobbyRadius();
        // Le lisere blanc du bord reste blanc.
        if (Math.abs(under.getX() - spawn.getBlockX()) >= r || Math.abs(under.getZ() - spawn.getBlockZ()) >= r) return;
        int pick = ThreadLocalRandom.current().nextInt(LOBBY_COLORS.length);
        if (LOBBY_COLORS[pick] == under.getType()) pick = (pick + 1) % LOBBY_COLORS.length;
        under.setType(LOBBY_COLORS[pick], false);
        p.playSound(under.getLocation().add(0.5, 1, 0.5), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.35f, 0.6f + pick * 0.14f);
    }

    // ----------------------------------------------------------------- start

    /** Lance la sequence de depart : equipes, licences, choix de personnage. */
    public boolean start() {
        if (!isLobby()) return false;
        plugin.teams().autoFill();
        if (plugin.teams().all().isEmpty()) {
            Msg.broadcast("<red>Aucun joueur pour lancer la partie.");
            return false;
        }
        int lives = plugin.getConfig().getInt("game.lives", 3);
        for (Team t : plugin.teams().all()) t.setEliminated(false);
        com.kiokouwhite.crossfight.util.Boosts.clearAll();
        com.kiokouwhite.crossfight.util.Revived.clear();
        for (var l : plugin.characters().licences()) if (l.mechanic() != null) l.mechanic().reset();
        for (Player p : Bukkit.getOnlinePlayers()) {
            CfPlayer cf = plugin.players().get(p);
            cf.resetForNewGame(lives);
            plugin.abilities().clearStates(p);
            p.setGameMode(cf.team() == null ? GameMode.SPECTATOR : GameMode.ADVENTURE);
            p.getInventory().clear();
            com.kiokouwhite.crossfight.util.Boosts.apply(p);
            com.kiokouwhite.crossfight.util.Effects.resetMaxHealth(p);
            p.setHealth(20);
            p.setFoodLevel(20);
            p.setSaturation(20);
            p.setExp(0);
            p.setLevel(0);
            for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
            com.kiokouwhite.crossfight.util.Effects.unroot(p);
            p.setAllowFlight(false);
        }
        plugin.teams().assignLicences();
        prepareWorld();
        removeLobby();

        state = GameState.SELECTING;
        phaseCountdown = SELECTION_SECONDS;
        elapsed = 0;
        pvpEnabled = false;

        for (Team t : plugin.teams().all()) {
            String lic = t.licence() == null ? "aucune" : t.licence().name();
            for (Player p : t.onlineMembers()) {
                Msg.send(p, "Ton equipe : " + t.coloredName() + " <gray>- Licence : <gold>" + lic);
                if (t.licence() != null) Msg.send(p, "<gray><italic>" + t.licence().particularite());
                // L'ecran de selection en 3D (ou le menu d'inventaire s'il est desactive).
                plugin.selection().open(p);
            }
        }
        Msg.broadcast("<yellow>Choisissez votre personnage ! <gray>(" + SELECTION_SECONDS + " secondes)");
        startClock();
        return true;
    }

    private void prepareWorld() {
        World w = world();
        w.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        w.setTime(0);
        w.setStorm(false);
        w.setThundering(false);
        WorldBorder border = w.getWorldBorder();
        border.setCenter(0, 0);
        borderShrinking = false;
        border.setSize(rules().value("bordure") * 2.0);
        border.setWarningDistance(20);
        rules().apply(w);
    }

    private void startClock() {
        if (clock != null) clock.cancel();
        clock = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void tick() {
        switch (state) {
            case SELECTING -> tickSelecting();
            case STARTING -> tickStarting();
            case RUNNING -> tickRunning();
            case TEST -> {
                plugin.abilities().tickMechanics();
                plugin.testMode().tick();
            }
            default -> { }
        }
    }

    private void tickSelecting() {
        phaseCountdown--;
        boolean everyoneChose = true;
        for (Team t : plugin.teams().all()) {
            for (Player p : t.onlineMembers()) {
                if (plugin.players().get(p).character() == null
                        && t.licence() != null && !t.licence().characters().isEmpty()) {
                    everyoneChose = false;
                }
            }
        }
        if (phaseCountdown % 15 == 0 && phaseCountdown > 0 && !everyoneChose) {
            Msg.broadcast("<yellow>Choix du personnage : encore " + phaseCountdown + "s");
        }
        if (everyoneChose || phaseCountdown <= 0) {
            for (Team t : plugin.teams().all()) plugin.characters().selectMenu(plugin).autoAssign(t);
            state = GameState.STARTING;
            phaseCountdown = COUNTDOWN_SECONDS;
        }
    }

    private void tickStarting() {
        if (phaseCountdown > 0) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showTitle(Title.title(Msg.mm("<gold><bold>" + phaseCountdown), Msg.mm("<gray>Teleportation imminente"),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ZERO)));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            }
            phaseCountdown--;
            return;
        }
        launch();
    }

    private void launch() {
        // Fin de l'ecran de selection : les joueurs le quittent, la partie les teleporte juste apres.
        plugin.selection().closeAll(false);
        state = GameState.RUNNING;
        elapsed = 0;
        int spread = plugin.getConfig().getInt("world.spread-radius", 800);
        for (Team t : plugin.teams().all()) {
            Location loc = randomGroundLocation(spread);
            for (Player p : t.onlineMembers()) {
                p.teleport(loc.clone().add(ThreadLocalRandom.current().nextInt(3) - 1, 0, ThreadLocalRandom.current().nextInt(3) - 1));
                p.setGameMode(GameMode.SURVIVAL);
                int safe = rules().value("invincibilite");
                if (safe > 0) p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * safe, 4, false, false));
                plugin.abilities().equip(p);
                p.showTitle(Title.title(Msg.mm("<gradient:#ff4d4d:#ffb84d><bold>CROSSFIGHT"), Msg.mm("<gray>Bonne chance !"),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofSeconds(1))));
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1f);
            }
        }
        int pvpMin = plugin.getConfig().getInt("game.pvp-minute", 30);
        if (pvpMin <= 0) pvpEnabled = true;
        Msg.broadcast("<green>La partie commence ! <gray>" + (pvpMin <= 0 ? "Le PvP est actif des maintenant." : "PvP dans " + pvpMin + " minutes."));
    }

    private void tickRunning() {
        elapsed++;
        plugin.abilities().tickMechanics();
        if (elapsed % 60 == 0) {
            int minute = elapsed / 60;
            int pvpMin = plugin.getConfig().getInt("game.pvp-minute", 30);
            if (minute == pvpMin && !pvpEnabled) {
                pvpEnabled = true;
                Msg.broadcast("<red><bold>Le PvP est active !");
                for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.2f);
            } else if (!pvpEnabled && (pvpMin - minute) <= 5 && (pvpMin - minute) > 0) {
                Msg.broadcast("<yellow>PvP dans " + (pvpMin - minute) + " minute(s).");
            }
            // La bordure : elle se reduit a partir de sa minute, en un temps donne, jusqu'a son rayon final.
            int shrinkAt = rules().value("bordure-debut");
            if (shrinkAt > 0 && minute == shrinkAt && !borderShrinking) {
                borderShrinking = true;
                int last = Math.min(rules().value("bordure-finale"), rules().value("bordure")), minutes = rules().value("bordure-duree");
                world().getWorldBorder().setSize(last * 2.0, minutes * 60L);
                Msg.broadcast("<red><bold>La bordure se reduit !</bold> <gray>Rayon de " + last + " blocs dans " + minutes + " minute(s).");
                for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.6f);
            } else if (shrinkAt > 0 && !borderShrinking && (shrinkAt - minute) <= 5 && (shrinkAt - minute) > 0) {
                Msg.broadcast("<yellow>La bordure commence a se reduire dans " + (shrinkAt - minute) + " minute(s).");
            }
            events.onMinute(minute);
        }
    }

    // ------------------------------------------------------------------ test

    void enterTestState() {
        plugin.teams().clear();
        state = GameState.TEST;
        elapsed = 0;
        pvpEnabled = false;
        startClock();
    }

    void leaveTestState() {
        if (state != GameState.TEST) return;
        if (clock != null) { clock.cancel(); clock = null; }
        plugin.teams().clear();
        state = GameState.LOBBY;
    }

    // ------------------------------------------------------------------- end

    public void checkWin() {
        if (!isRunning()) return;
        List<Team> alive = plugin.teams().alive();
        if (alive.size() <= 1) {
            end(alive.isEmpty() ? null : alive.get(0));
        }
    }

    public void end(Team winner) {
        if (state == GameState.ENDED || state == GameState.LOBBY) return;
        state = GameState.ENDED;
        if (clock != null) { clock.cancel(); clock = null; }
        if (winner != null) {
            StringBuilder names = new StringBuilder();
            for (CfPlayer cf : plugin.players().all()) {
                if (cf.team() == winner) {
                    if (!names.isEmpty()) names.append(", ");
                    names.append(cf.name());
                }
            }
            Msg.broadcast("<gold><bold>Victoire de " + winner.coloredName() + " <gold><bold>!</bold> <gray>(" + names + ")");
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showTitle(Title.title(Msg.mm("<gold><bold>VICTOIRE"), Msg.mm(winner.coloredName()),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(5), Duration.ofSeconds(1))));
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        } else {
            Msg.broadcast("<gray>La partie est terminee sans vainqueur.");
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            plugin.abilities().unequip(p);
            p.setGameMode(GameMode.SPECTATOR);
        }
        com.kiokouwhite.crossfight.util.TempBlocks.restoreAll();
        com.kiokouwhite.crossfight.util.Clones.removeAll();
    }

    /** Arret force par un administrateur : retour au lobby. */
    public void stop() {
        plugin.selection().closeAll(true);
        if (state == GameState.TEST) {
            plugin.testMode().leaveAll();
            leaveTestState();
        }
        if (state != GameState.LOBBY) {
            if (state != GameState.ENDED) end(null);
            state = GameState.LOBBY;
        }
        if (clock != null) { clock.cancel(); clock = null; }
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setGameMode(GameMode.ADVENTURE);
            p.getInventory().clear();
            p.setHealth(20);
            plugin.players().get(p).setWaitingRespawn(false);
            // Retour au lobby : chacun retrouve son propre skin, et la plateforme dans les airs.
            com.kiokouwhite.crossfight.util.Skins.restore(p);
            if (!p.isDead()) { p.teleport(lobbySpot()); p.setFallDistance(0); }
        }
        plugin.teams().clear();
        plugin.hostTools().refreshAll();
        com.kiokouwhite.crossfight.util.Mannequins.clearAll();
        elapsed = 0;
        pvpEnabled = false;
    }

    public void shutdown() {
        if (clock != null) clock.cancel();
    }

    // --------------------------------------------------------------- helpers

    /** Position aleatoire au sol dans un rayon autour du centre, en evitant l'eau et la lave. */
    public Location randomGroundLocation(int radius) {
        World w = world();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int i = 0; i < 40; i++) {
            int x = r.nextInt(-radius, radius + 1);
            int z = r.nextInt(-radius, radius + 1);
            int y = w.getHighestBlockYAt(x, z);
            var type = w.getBlockAt(x, y, z).getType();
            if (type.isSolid() && !type.name().contains("LEAVES")) {
                return new Location(w, x + 0.5, y + 1, z + 0.5);
            }
        }
        return w.getSpawnLocation();
    }

    /** Envoie un message a tous les joueurs a moins de 50 blocs. */
    public void broadcastNear(Player source, String text) {
        for (Player p : source.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(source.getLocation()) <= 50 * 50) Msg.send(p, text);
        }
    }
}
