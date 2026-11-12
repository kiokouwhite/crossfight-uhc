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

    private static final int SELECTION_SECONDS = 60;
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
    public boolean isRunning() { return state == GameState.RUNNING || state == GameState.TEST; }
    public boolean isTest() { return state == GameState.TEST; }
    public boolean isSelecting() { return state == GameState.SELECTING; }
    public boolean isLobby() { return state == GameState.LOBBY || state == GameState.ENDED; }
    public int elapsedSeconds() { return elapsed; }
    public int elapsedMinutes() { return elapsed / 60; }
    public boolean isPvpEnabled() { return pvpEnabled || state == GameState.TEST; }
    public GameEvents events() { return events; }

    public World world() {
        World w = Bukkit.getWorld(plugin.getConfig().getString("world.name", "world"));
        return w != null ? w : Bukkit.getWorlds().get(0);
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

        state = GameState.SELECTING;
        phaseCountdown = SELECTION_SECONDS;
        elapsed = 0;
        pvpEnabled = false;

        for (Team t : plugin.teams().all()) {
            String lic = t.licence() == null ? "aucune" : t.licence().name();
            for (Player p : t.onlineMembers()) {
                Msg.send(p, "Ton equipe : " + t.coloredName() + " <gray>- Licence : <gold>" + lic);
                if (t.licence() != null) Msg.send(p, "<gray><italic>" + t.licence().particularite());
                plugin.characters().selectMenu(plugin).open(p);
            }
        }
        Msg.broadcast("<yellow>Choisissez votre personnage ! <gray>(" + SELECTION_SECONDS + " secondes)");
        startClock();
        return true;
    }

    private void prepareWorld() {
        World w = world();
        w.setGameRule(GameRule.NATURAL_REGENERATION, false);
        w.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        w.setTime(0);
        w.setStorm(false);
        w.setThundering(false);
        WorldBorder border = w.getWorldBorder();
        border.setCenter(0, 0);
        border.setSize(plugin.getConfig().getInt("world.border-radius", 1000) * 2.0);
        border.setWarningDistance(20);
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
        state = GameState.RUNNING;
        elapsed = 0;
        int spread = plugin.getConfig().getInt("world.spread-radius", 800);
        for (Team t : plugin.teams().all()) {
            Location loc = randomGroundLocation(spread);
            for (Player p : t.onlineMembers()) {
                p.teleport(loc.clone().add(ThreadLocalRandom.current().nextInt(3) - 1, 0, ThreadLocalRandom.current().nextInt(3) - 1));
                p.setGameMode(GameMode.SURVIVAL);
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20 * 30, 4, false, false));
                plugin.abilities().equip(p);
                p.showTitle(Title.title(Msg.mm("<gradient:#ff4d4d:#ffb84d><bold>CROSSFIGHT"), Msg.mm("<gray>Bonne chance !"),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofSeconds(1))));
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1f);
            }
        }
        int pvpMin = plugin.getConfig().getInt("game.pvp-minute", 30);
        Msg.broadcast("<green>La partie commence ! <gray>PvP dans " + pvpMin + " minutes.");
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
            // Retour au lobby : chacun retrouve son propre skin.
            com.kiokouwhite.crossfight.util.Skins.restore(p);
        }
        plugin.teams().clear();
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
