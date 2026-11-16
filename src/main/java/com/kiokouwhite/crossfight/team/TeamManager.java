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
 * trio) : les joueurs choisissent le leur dans le lobby, et la partie ne se lance que quand chacun a choisi ;
 * "teamSize" n'est plus que le format par defaut de /cf team create et du mode test.
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

    /** Les joueurs en ligne qui jouent (ceux qui n'ont pas choisi de regarder). */
    public List<Player> playing() {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (!plugin.players().get(p).isSpectatorOnly()) out.add(p);
        return out;
    }

    /** Equipes aleatoires : tout le monde est melange et reparti dans les equipes de ce tirage. */
    public void shuffle(List<TeamSize> plan) {
        clear();
        List<Player> players = playing();
        Collections.shuffle(players, ThreadLocalRandom.current());
        int next = 0;
        for (TeamSize kind : plan) {
            Team t = create(kind);
            for (int i = 0; i < kind.size() && next < players.size(); i++) join(players.get(next++), t);
        }
        // Une equipe incomplete du format principal accueille ceux qui restent.
        Team last = teams.isEmpty() ? null : get(nextId - 1);
        while (next < players.size() && last != null) forceJoin(players.get(next++), last);
        prune();
    }

    /** Les joueurs en ligne qui n'ont ni equipe ni choisi de regarder : la partie ne se lance pas sans eux. */
    public List<Player> undecided() {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (teamOf(p) == null && !plugin.players().get(p).isSpectatorOnly()) out.add(p);
        }
        return out;
    }

    /** Tire une licence au sort pour chaque equipe, sans doublon tant que c'est possible. */
    public void assignLicences() {
        // Un sac de licences par format : une equipe tire la sienne parmi celles de son format.
        Map<TeamSize, List<Licence>> bags = new java.util.EnumMap<>(TeamSize.class);
        for (Team t : teams.values()) {
            List<Licence> bag = bags.computeIfAbsent(t.kind(), k -> new ArrayList<>());
            if (bag.isEmpty()) {
                bag.addAll(plugin.roster().licences(t.kind()));
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
