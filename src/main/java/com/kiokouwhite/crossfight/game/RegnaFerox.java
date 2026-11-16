package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.util.ArenaWorlds;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * L'evenement Regna Ferox : a sa minute (30 par defaut), chaque joueur se voit proposer un duel d'equipes dans
 * l'arene de Ferox. Une equipe est inscrite quand tous ses membres en vie ont accepte ; les deux premieres
 * inscrites sont emmenees dans l'arene. L'equipe qui met l'autre a terre gagne : chacun de ses joueurs recoit le
 * contenu d'une Lootbox de tier IV ; chaque joueur de l'equipe battue perd une vie. Tomber dans l'arene ne coute
 * rien d'autre : on y regarde la fin du combat, puis tout le monde revient la ou il etait.
 */
public final class RegnaFerox implements Listener {

    public static final String WORLD = "crossfight_ferox";
    /** Demi-cote de l'arene, hauteur de ses murs, secondes pour s'inscrire, secondes de combat au plus. */
    private static final int HALF = 18, WALL = 12, ASK_SECONDS = 60, FIGHT_SECONDS = 300;

    private enum Phase { IDLE, ASKING, FIGHT }

    private final CrossfightPlugin plugin;
    private Phase phase = Phase.IDLE;
    private final Set<UUID> accepted = new HashSet<>();
    /** Les equipes inscrites, dans l'ordre ou elles l'ont ete. */
    private final Set<Team> ready = new LinkedHashSet<>();
    private Team teamA, teamB;
    private final Set<UUID> fighters = new HashSet<>(), down = new HashSet<>();
    private final Map<UUID, Location> back = new HashMap<>();
    private final Map<UUID, GameMode> modes = new HashMap<>();
    private BukkitTask timer;

    public RegnaFerox(CrossfightPlugin plugin) { this.plugin = plugin; }

    /** Vrai pour un joueur en train de se battre dans l'arene (pas encore a terre). */
    public boolean fighting(Player p) {
        return phase == Phase.FIGHT && fighters.contains(p.getUniqueId()) && !down.contains(p.getUniqueId());
    }

    /** Les joueurs d'une equipe qui comptent : en ligne, en vie, pas en attente de reapparition. */
    private List<Player> active(Team t) {
        List<Player> out = new ArrayList<>();
        for (Player p : t.onlineMembers()) {
            CfPlayer cf = plugin.players().get(p);
            if (cf.isAlive() && !cf.isWaitingRespawn() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR) out.add(p);
        }
        return out;
    }

    // ------------------------------------------------------------------ l'inscription

    /** La proposition, dans le chat de tous les joueurs en vie. */
    public void ask() {
        if (phase != Phase.IDLE) return;
        if (plugin.teams().alive().size() < 2) { Msg.broadcast("<gold><bold>Evenement :</bold> Regna Ferox n'ouvre pas son arene : il faut au moins deux equipes."); return; }
        phase = Phase.ASKING;
        accepted.clear();
        ready.clear();
        Component offer = Msg.prefixed("<gold><bold>Evenement : Regna Ferox ouvre son arene !</bold>")
                .append(Component.newline())
                .append(Msg.mm("<gray>Un duel d'equipes : les vainqueurs gagnent chacun une <gold>Lootbox de tier IV</gold>, les vaincus perdent une vie. Les deux premieres equipes dont tous les membres acceptent y vont."))
                .append(Component.newline())
                .append(Msg.mm("  <green><bold>[Participer]</bold></green>").clickEvent(ClickEvent.runCommand("/cf ferox oui")).hoverEvent(Msg.mm("<green>Clique pour t'inscrire")))
                .append(Msg.mm("   <red><bold>[Refuser]</bold></red>").clickEvent(ClickEvent.runCommand("/cf ferox non")).hoverEvent(Msg.mm("<red>Clique pour refuser")))
                .append(Msg.mm("   <dark_gray>(" + ASK_SECONDS + " secondes)"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.players().get(p).team() == null) continue;
            p.sendMessage(offer);
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.6f, 1.2f);
        }
        timer = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (phase != Phase.ASKING) return;
            phase = Phase.IDLE;
            Msg.broadcast("<gold>Regna Ferox referme son arene : il fallait deux equipes inscrites" + (ready.isEmpty() ? "." : ", une seule l'etait."));
        }, ASK_SECONDS * 20L);
    }

    /** La reponse d'un joueur a la proposition. */
    public void answer(Player p, boolean yes) {
        if (phase != Phase.ASKING) { Msg.send(p, "<red>Regna Ferox ne recrute pas en ce moment."); return; }
        Team team = plugin.players().get(p).team();
        if (team == null || !active(team).contains(p)) { Msg.send(p, "<red>Il faut etre en vie et dans une equipe pour participer."); return; }
        if (!yes) {
            accepted.remove(p.getUniqueId());
            if (ready.remove(team)) for (Player m : team.onlineMembers()) Msg.send(m, "<yellow>" + p.getName() + " se retire : ton equipe n'est plus inscrite a Regna Ferox.");
            else for (Player m : team.onlineMembers()) Msg.send(m, "<yellow>" + p.getName() + " refuse Regna Ferox.");
            return;
        }
        accepted.add(p.getUniqueId());
        List<Player> members = active(team);
        int in = 0;
        for (Player m : members) if (accepted.contains(m.getUniqueId())) in++;
        if (in < members.size()) {
            for (Player m : team.onlineMembers()) Msg.send(m, "<green>" + p.getName() + " veut aller a Regna Ferox <gray>(" + in + "/" + members.size() + " de ton equipe).");
            return;
        }
        if (!ready.add(team)) return;
        Msg.broadcast("<gold>" + team.coloredName() + " <gold>est inscrite a Regna Ferox <gray>(" + ready.size() + "/2).");
        if (ready.size() >= 2) {
            var it = ready.iterator();
            start(it.next(), it.next());
        }
    }

    // ------------------------------------------------------------------ le combat

    private World arena() {
        World w = ArenaWorlds.get(WORLD);
        if (w.getBlockAt(0, ArenaWorlds.FLOOR_Y, 0).getType() != Material.STONE_BRICKS) {
            ArenaWorlds.buildPlatform(w, HALF, Material.STONE_BRICKS, Material.STONE_BRICKS, WALL);
            w.getBlockAt(0, ArenaWorlds.FLOOR_Y, 0).setType(Material.STONE_BRICKS, false);
            // Un plafond invisible : on ne sort pas de l'arene par les airs. Un cercle de sable marque le centre.
            for (int x = -HALF; x <= HALF; x++) for (int z = -HALF; z <= HALF; z++) {
                w.getBlockAt(x, ArenaWorlds.FLOOR_Y + WALL + 1, z).setType(Material.BARRIER, false);
                double d = Math.hypot(x, z);
                if (d <= 6.5 && d >= 5.5) w.getBlockAt(x, ArenaWorlds.FLOOR_Y, z).setType(Material.CHISELED_STONE_BRICKS, false);
            }
        }
        return w;
    }

    private void start(Team a, Team b) {
        if (timer != null) timer.cancel();
        phase = Phase.FIGHT;
        teamA = a;
        teamB = b;
        fighters.clear();
        down.clear();
        World w = arena();
        Msg.broadcast("<gold><bold>Regna Ferox :</bold> " + a.coloredName() + " <gold>affronte " + b.coloredName() + " <gold>dans l'arene !");
        for (Team t : List.of(a, b)) {
            List<Player> members = active(t);
            for (int i = 0; i < members.size(); i++) {
                Player p = members.get(i);
                fighters.add(p.getUniqueId());
                back.put(p.getUniqueId(), p.getLocation());
                modes.put(p.getUniqueId(), p.getGameMode());
                double side = t == a ? -1 : 1;
                Location at = new Location(w, (i - (members.size() - 1) / 2.0) * 3 + 0.5, ArenaWorlds.FLOOR_Y + 1, side * (HALF - 6) + 0.5, side < 0 ? 0 : 180, 0);
                p.teleport(at);
                p.setFallDistance(0);
                Effects.invincible(p, 3000);
                p.showTitle(Title.title(Msg.mm("<gold><bold>Regna Ferox"), Msg.mm("<gray>Mettez l'equipe adverse a terre"), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(400))));
                p.playSound(at, Sound.EVENT_RAID_HORN, 1f, 0.9f);
            }
        }
        timer = Bukkit.getScheduler().runTaskLater(plugin, () -> { if (phase == Phase.FIGHT) finish(null); }, FIGHT_SECONDS * 20L);
    }

    /** Un coup qui tuerait dans l'arene ne tue pas : le joueur est a terre et regarde la fin du combat. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !fighting(p)) return;
        if (e.getFinalDamage() < p.getHealth()) return;
        e.setCancelled(true);
        fall(p);
    }

    private void fall(Player p) {
        if (!down.add(p.getUniqueId())) return;
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        if (p.isOnline() && !p.isDead()) {
            p.setHealth(max == null ? 20 : max.getValue());
            p.setFireTicks(0);
            p.setGameMode(GameMode.SPECTATOR);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_DEATH, 1f, 0.9f);
        }
        for (UUID u : fighters) { Player o = Bukkit.getPlayer(u); if (o != null) Msg.send(o, "<red>" + p.getName() + " <gray>est a terre."); }
        for (Team t : List.of(teamA, teamB)) {
            boolean standing = false;
            for (UUID u : t.members()) if (fighters.contains(u) && !down.contains(u)) standing = true;
            if (!standing) { finish(t == teamA ? teamB : teamA); return; }
        }
    }

    /** Fin du combat : les vainqueurs recompenses, les vaincus perdent une vie, chacun retourne d'ou il venait. */
    private void finish(Team winner) {
        if (phase != Phase.FIGHT) return;
        phase = Phase.IDLE;
        if (timer != null) timer.cancel();
        Team loser = winner == null ? null : winner == teamA ? teamB : teamA;
        Msg.broadcast(winner == null ? "<gold><bold>Regna Ferox :</bold> le temps est ecoule, personne ne l'emporte."
                : "<gold><bold>Regna Ferox :</bold> " + winner.coloredName() + " <gold>l'emporte sur " + loser.coloredName() + " <gold>!");
        for (UUID u : new ArrayList<>(fighters)) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            p.setGameMode(modes.getOrDefault(u, GameMode.SURVIVAL));
            Location home = back.remove(u);
            p.teleport(home != null ? home : plugin.game().randomGroundLocation(300));
            p.setFallDistance(0);
            Effects.invincible(p, 3000);
            Team mine = plugin.players().get(p).team();
            if (winner != null && mine == winner) {
                plugin.lootbox().reward(p, 4);
                Msg.send(p, "<gold>Victoire ! Tu recois le contenu d'une Lootbox de tier IV.");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            } else if (loser != null && mine == loser) {
                plugin.lives().loseLife(p, "Regna Ferox");
            }
        }
        fighters.clear();
        down.clear();
        modes.clear();
    }

    /** Fin de partie : l'arene se vide sans vainqueur ni vaincu. */
    public void cancel() {
        if (timer != null) timer.cancel();
        if (phase == Phase.FIGHT) {
            for (UUID u : fighters) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) p.setGameMode(modes.getOrDefault(u, GameMode.SURVIVAL));
            }
        }
        phase = Phase.IDLE;
        fighters.clear();
        down.clear();
        back.clear();
        modes.clear();
        accepted.clear();
        ready.clear();
    }

    /** Parti en plein combat : il compte comme a terre. */
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (fighting(e.getPlayer())) fall(e.getPlayer());
    }

    /** Revenu apres la fin du combat alors qu'il etait reste dans l'arene : il retourne sur la map. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!p.getWorld().getName().equals(WORLD) || (phase == Phase.FIGHT && fighters.contains(p.getUniqueId()))) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            Location home = back.remove(p.getUniqueId());
            p.teleport(home != null ? home : plugin.game().isRunning() ? plugin.game().randomGroundLocation(300) : plugin.game().lobbySpot());
            if (plugin.game().isRunning() && plugin.players().get(p).isAlive()) p.setGameMode(GameMode.SURVIVAL);
        });
    }
}
