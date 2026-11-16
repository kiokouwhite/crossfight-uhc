package com.kiokouwhite.crossfight.team;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.character.Licence;
import com.kiokouwhite.crossfight.player.CfPlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Gere les equipes, leur remplissage et l'attribution des licences. Chaque equipe a son format (solitaire, duo,
 * trio) : les joueurs choisissent le leur dans le lobby ; "teamSize" est le format par defaut, celui des equipes
 * creees au lancement pour les joueurs qui n'ont rien choisi.
 */
public final class TeamManager {

    private static final String[] COLORS = {
            "red", "blue", "green", "yellow", "aqua", "light_purple", "gold", "dark_green",
            "dark_aqua", "dark_purple", "dark_red", "dark_blue", "white", "gray"
    };

    private final CrossfightPlugin plugin;
    private final Map<Integer, Team> teams = new LinkedHashMap<>();
    private TeamSize teamSize;
    private int nextId = 1;

    public TeamManager(CrossfightPlugin plugin) {
        this.plugin = plugin;
        TeamSize fromConfig = TeamSize.parse(plugin.getConfig().getString("game.team-size", "TRIO"));
        this.teamSize = fromConfig == null ? TeamSize.TRIO : fromConfig;
    }

    public TeamSize teamSize() { return teamSize; }

    public void setTeamSize(TeamSize size) {
        this.teamSize = size;
        plugin.getConfig().set("game.team-size", size.name());
        plugin.saveConfig();
    }

    public Team create() { return create(teamSize); }

    public Team create(TeamSize kind) {
        Team t = new Team(nextId, COLORS[(nextId - 1) % COLORS.length], kind);
        teams.put(nextId, t);
        nextId++;
        return t;
    }

    public Team get(int id) { return teams.get(id); }

    public List<Team> all() { return new ArrayList<>(teams.values()); }

    public List<Team> alive() {
        List<Team> out = new ArrayList<>();
        for (Team t : teams.values()) if (!t.isEliminated() && t.size() > 0) out.add(t);
        return out;
    }

    public Team teamOf(UUID uuid) {
        for (Team t : teams.values()) if (t.contains(uuid)) return t;
        return null;
    }

    public Team teamOf(Player p) { return teamOf(p.getUniqueId()); }

    public boolean sameTeam(Player a, Player b) {
        Team ta = teamOf(a);
        return ta != null && ta.contains(b.getUniqueId());
    }

    /** Ajoute un joueur a une equipe, en le retirant de son ancienne. Refuse si l'equipe est pleine. */
    public boolean join(Player p, Team team) {
        if (team.isFull() && !team.contains(p.getUniqueId())) return false;
        Team old = teamOf(p);
        if (old != null) old.remove(p.getUniqueId());
        team.add(p.getUniqueId());
        CfPlayer cp = plugin.players().get(p);
        cp.setTeam(team);
        return true;
    }

    /** Ajoute un joueur a une equipe sans verifier la taille (invocation de Weiss). */
    public void forceJoin(Player p, Team team) {
        Team old = teamOf(p);
        if (old != null) old.remove(p.getUniqueId());
        team.add(p.getUniqueId());
        plugin.players().get(p).setTeam(team);
    }

    public void leave(Player p) {
        Team old = teamOf(p);
        if (old != null) old.remove(p.getUniqueId());
        plugin.players().get(p).setTeam(null);
    }

    /** Dans le lobby : les equipes que tout le monde a quittees disparaissent. */
    public void prune() {
        teams.values().removeIf(t -> t.size() == 0);
    }

    public void clear() {
        teams.clear();
        nextId = 1;
        for (CfPlayer cp : plugin.players().all()) cp.setTeam(null);
    }

    /** Place tous les joueurs en ligne sans equipe dans des equipes, en completant celles qui existent. */
    public void autoFill() {
        List<Player> free = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamOf(p) == null && !plugin.players().get(p).isSpectatorOnly()) free.add(p);
        }
        Collections.shuffle(free, ThreadLocalRandom.current());
        for (Player p : free) {
            Team target = null;
            for (Team t : teams.values()) {
                if (!t.isFull()) { target = t; break; }
            }
            if (target == null) target = create();
            join(p, target);
        }
        // Supprime les equipes vides
        teams.values().removeIf(t -> t.size() == 0);
    }

    /** Tire une licence au sort pour chaque equipe, sans doublon tant que c'est possible. */
    public void assignLicences() {
        // Un sac de licences par format : une equipe tire la sienne parmi celles de son format.
        Map<TeamSize, List<Licence>> bags = new java.util.EnumMap<>(TeamSize.class);
        for (Team t : teams.values()) {
            List<Licence> bag = bags.computeIfAbsent(t.kind(), k -> new ArrayList<>());
            if (bag.isEmpty()) {
                bag.addAll(plugin.characters().licencesFor(t.kind()));
                if (bag.isEmpty()) {
                    plugin.getLogger().warning("Aucune licence disponible pour le format " + t.kind());
                    continue;
                }
                Collections.shuffle(bag, ThreadLocalRandom.current());
            }
            t.setLicence(bag.remove(bag.size() - 1));
        }
    }

    /** Appele quand un joueur perd sa derniere vie : verifie si l'equipe est eliminee. */
    public boolean checkElimination(Team team) {
        if (team.isEliminated()) return true;
        for (UUID u : team.members()) {
            CfPlayer cp = plugin.players().get(u);
            if (cp != null && cp.lives() > 0) return false;
        }
        team.setEliminated(true);
        return true;
    }
}
