package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Licence;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.team.TeamSize;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Ce qui est jouable dans la partie : l'administrateur peut desactiver des personnages, des licences entieres
 * et des formats d'equipe (solitaire, duo, trio), et autoriser ou non les doublons (regle "doublons"). Tout est
 * garde dans config.yml (desactives.personnages, desactives.licences, desactives.formats). Le mode test ignore
 * ces reglages : on y essaie tout.
 */
public final class Roster {

    private final CrossfightPlugin plugin;

    public Roster(CrossfightPlugin plugin) { this.plugin = plugin; }

    private List<String> off(String what) { return plugin.getConfig().getStringList("desactives." + what); }

    private void set(String what, String id, boolean on) {
        List<String> list = new ArrayList<>(off(what));
        list.remove(id);
        if (!on) list.add(id);
        plugin.getConfig().set("desactives." + what, list);
        plugin.saveConfig();
    }

    public boolean on(Character c) { return !off("personnages").contains(c.id()); }
    public boolean on(Licence l) { return !off("licences").contains(l.id()); }
    public boolean on(TeamSize kind) { return !off("formats").contains(kind.name()); }

    public void set(Character c, boolean on) { set("personnages", c.id(), on); }
    public void set(Licence l, boolean on) { set("licences", l.id(), on); }
    public void set(TeamSize kind, boolean on) { set("formats", kind.name(), on); }

    /** Vrai si deux equipes peuvent avoir la meme licence (et deux solitaires le meme personnage). */
    public boolean duplicates() { return plugin.game().rules().on("doublons"); }

    private boolean testing() { return plugin.game().state() == GameState.TEST; }

    /** Les personnages qu'on peut choisir dans cette licence. */
    public List<Character> characters(Licence l) {
        if (testing()) return l.characters();
        List<Character> out = new ArrayList<>();
        for (Character c : l.characters()) if (on(c)) out.add(c);
        return out;
    }

    /** Une licence se joue si elle est activee et qu'il lui reste assez de personnages pour une equipe de son format. */
    public boolean playable(Licence l) {
        return on(l) && characters(l).size() >= l.teamSize().size();
    }

    public List<Licence> licences(TeamSize kind) {
        List<Licence> out = new ArrayList<>();
        for (Licence l : plugin.characters().licences()) if (l.teamSize() == kind && playable(l)) out.add(l);
        return out;
    }

    /**
     * Combien d'equipes de ce format la partie peut accueillir. Sans doublons : une par licence, et pour les
     * solitaires (qui partagent une licence) un par personnage.
     */
    public int room(TeamSize kind) {
        List<Licence> playable = licences(kind);
        if (!on(kind) || playable.isEmpty()) return 0;
        if (duplicates()) return Integer.MAX_VALUE;
        if (kind != TeamSize.SOLO) return playable.size();
        int n = 0;
        for (Licence l : playable) n += characters(l).size();
        return n;
    }

    /** Vrai si les equipes sont tirees au sort au lancement (regle "equipes-aleatoires") plutot que choisies. */
    public boolean randomTeams() { return plugin.game().rules().on("equipes-aleatoires"); }

    /**
     * Les equipes d'un tirage au sort pour ce nombre de joueurs : autant d'equipes completes du format choisi
     * que possible ; ceux qui restent forment une equipe plus petite (un duo, un solitaire) si ce format est
     * permis, sinon une equipe incomplete.
     */
    public List<TeamSize> randomPlan(int players) {
        TeamSize main = plugin.teams().teamSize();
        List<TeamSize> plan = new ArrayList<>();
        for (int i = 0; i < players / main.size(); i++) plan.add(main);
        int rest = players % main.size();
        if (rest > 0) {
            TeamSize small = rest == 1 ? TeamSize.SOLO : TeamSize.DUO;
            plan.add(room(small) > 0 ? small : main);
        }
        return plan;
    }

    /** Ce qui empeche de lancer la partie avec ces equipes, ou null. */
    public String problem(List<Team> teams) {
        List<TeamSize> kinds = new ArrayList<>();
        for (Team t : teams) if (t.size() > 0) kinds.add(t.kind());
        return problemFor(kinds);
    }

    public String problemFor(List<TeamSize> kinds) {
        Map<TeamSize, Integer> count = new EnumMap<>(TeamSize.class);
        for (TeamSize k : kinds) count.merge(k, 1, Integer::sum);
        for (var e : count.entrySet()) {
            TeamSize kind = e.getKey();
            String what = kind.label().toLowerCase(Locale.ROOT);
            if (!on(kind)) return "le format " + what + " est desactive";
            int room = room(kind);
            if (room == 0) return "aucune licence jouable pour le format " + what;
            if (e.getValue() > room) return "trop d'equipes au format " + what + " (" + e.getValue() + ") pour jouer sans doublons : " + room + " au plus";
        }
        return null;
    }

    /** Le resume de ce qui est desactive, pour l'annonce. */
    public String summary() {
        List<String> parts = new ArrayList<>();
        List<String> formats = new ArrayList<>();
        for (TeamSize k : TeamSize.values()) if (!on(k)) formats.add(k.shortLabel());
        if (!formats.isEmpty()) parts.add("<gray>formats desactives : <white>" + String.join(", ", formats));
        List<String> licences = new ArrayList<>(), characters = new ArrayList<>();
        for (Licence l : plugin.characters().licences()) {
            if (!on(l)) { licences.add(l.name()); continue; }
            for (Character c : l.characters()) if (!on(c)) characters.add(c.name());
        }
        if (!licences.isEmpty()) parts.add("<gray>licences desactivees : <white>" + String.join(", ", licences));
        if (!characters.isEmpty()) parts.add("<gray>personnages desactives : <white>" + String.join(", ", characters));
        parts.add("<gray>doublons : <white>" + (duplicates() ? "autorises" : "interdits"));
        parts.add("<gray>equipes : <white>" + (randomTeams() ? "tirees au sort (" + plugin.teams().teamSize().shortLabel() + ")" : "choisies par les joueurs"));
        return String.join(" <dark_gray>| ", parts);
    }
}
