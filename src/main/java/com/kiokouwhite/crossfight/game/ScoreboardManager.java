package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tableau lateral : temps, equipe, personnage, vies, ultime.
 */
public final class ScoreboardManager {

    private final CrossfightPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();
    private BukkitTask task;

    public ScoreboardManager(CrossfightPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::updateAll, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) p.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        boards.clear();
    }

    public void remove(Player p) {
        boards.remove(p.getUniqueId());
    }

    /** Met a jour le tableau d'un joueur tout de suite (il vient d'entrer dans l'ecran de selection, ou d'en sortir). */
    public void refresh(Player p) {
        if (task != null && p.isOnline()) update(p);
    }

    private void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) update(p);
    }

    private void update(Player p) {
        Scoreboard board = boards.computeIfAbsent(p.getUniqueId(), u -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective old = board.getObjective("cf");
        if (old != null) old.unregister();
        Objective obj = board.registerNewObjective("cf", Criteria.DUMMY, title(p));
        // Pendant le choix du personnage, le tableau laisse l'ecran aux cartes (la barre du haut donne le temps).
        if (plugin.selection() == null || !plugin.selection().isViewing(p)) obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = buildLines(p);
        // Le logo descend dans le gris du tableau : des lignes vides lui gardent sa place.
        if (showsLogo(p)) for (int i = 0; i < LOGO_LINES; i++) lines.add(0, "");
        int score = lines.size();
        for (String line : lines) {
            // Chaque entree doit etre unique : on ajoute des codes couleur invisibles au besoin
            String entry = unique(line, lines, score);
            obj.getScore(entry).setScore(score--);
        }
        if (p.getScoreboard() != board) p.setScoreboard(board);
    }

    /** Le logo dans la police "crossfight:logo" du pack (tools/build_logo.js) : ses deux moities, recollees. */
    private static final net.kyori.adventure.text.Component LOGO = net.kyori.adventure.text.Component.text("\uE000\uE002\uE001")
            .font(net.kyori.adventure.key.Key.key(com.kiokouwhite.crossfight.util.Sounds.NS, "logo")).color(net.kyori.adventure.text.format.NamedTextColor.WHITE);

    /**
     * Le titre du tableau : le logo Crossfight UHC pour qui a charge le pack, le nom en lettres sinon.
     * scoreboard.logo dans config.yml : auto (selon le pack), toujours (le pack est installe a la main), jamais.
     */
    private net.kyori.adventure.text.Component title(Player p) {
        return showsLogo(p) ? LOGO : Msg.mm("<gradient:#ff4d4d:#ffb84d><bold>CROSSFIGHT UHC");
    }

    /** Lignes vides sous le titre, pour la hauteur du logo (30 pixels : la ligne du titre et trois autres de 9). */
    private static final int LOGO_LINES = 3;

    private boolean showsLogo(Player p) {
        String mode = plugin.getConfig().getString("scoreboard.logo", "auto").toLowerCase(java.util.Locale.ROOT);
        return com.kiokouwhite.crossfight.util.Sounds.has("font.logo") && !mode.equals("jamais") && !mode.equals("false")
                && (mode.equals("toujours") || mode.equals("true") || com.kiokouwhite.crossfight.util.Sounds.hasPackLoaded(p));
    }

    private String unique(String line, List<String> all, int idx) {
        String plain = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(Msg.mm(line));
        StringBuilder sb = new StringBuilder(plain);
        for (int i = 0; i < idx; i++) sb.append("§r");
        return sb.toString();
    }

    private List<String> buildLines(Player p) {
        CfPlayer cf = plugin.players().get(p);
        GameManager game = plugin.game();
        List<String> lines = new ArrayList<>();
        lines.add("<dark_gray>" + "━".repeat(18));
        // Dans un lieu : sa quete, tout en haut.
        String quest = plugin.places().questOf(p);
        if (quest != null) { lines.add("<yellow><bold>!</bold> <white>Quete"); lines.add("<gold>" + quest); lines.add(""); }
        switch (game.state()) {
            case LOBBY, ENDED -> {
                lines.add("<white>Etat : <yellow>Lobby");
                lines.add("<white>Joueurs : <yellow>" + Bukkit.getOnlinePlayers().size());
                int formed = 0;
                for (Team each : plugin.teams().all()) if (each.size() > 0) formed++;
                lines.add("<white>Equipes : <yellow>" + formed);
                Team t = cf.team();
                lines.add("<white>Ton equipe : " + (t == null ? "<gray>aucune" : t.coloredName() + " <gray>" + t.kind().shortLabel() + " " + t.size() + "/" + t.capacity()));
            }
            case TEST -> {
                lines.add("<white>Etat : <aqua>Mode test");
                lines.add("<white>Perso : <gold>" + (cf.character() == null ? "<gray>/cf change" : cf.character().name()));
                lines.add("<white>Ultime : <light_purple>" + (int) cf.ultimateCharge() + "%");
                String boosts = com.kiokouwhite.crossfight.util.Boosts.summary(p);
                if (!boosts.isEmpty()) lines.add("<white>Bonus : " + boosts);
                lines.add("<gray>/cf mannequin, /cf ult, /cf test");
            }
            case SELECTING, STARTING -> {
                lines.add("<white>Etat : <yellow>Preparation");
                Team t = cf.team();
                lines.add("<white>Equipe : " + (t == null ? "<gray>spectateur" : t.coloredName()));
                if (t != null && t.licence() != null) lines.add("<white>Licence : <gold>" + t.licence().name());
                lines.add("<white>Perso : <gold>" + (cf.character() == null ? "<gray>a choisir" : cf.character().name()));
            }
            case RUNNING -> {
                lines.add("<white>Temps : <yellow>" + Msg.time(game.elapsedSeconds()));
                lines.add("<white>PvP : " + (game.isPvpEnabled() ? "<red>actif" : "<green>desactive"));
                lines.add("<white>Equipes en vie : <yellow>" + plugin.teams().alive().size());
                lines.add("");
                Team t = cf.team();
                if (t != null) {
                    lines.add("<white>Equipe : " + t.coloredName());
                    if (t.licence() != null) lines.add("<white>Licence : <gold>" + t.licence().name());
                    lines.add("<white>Perso : <gold>" + (cf.character() == null ? "<gray>aucun" : cf.character().name()));
                    lines.add("<white>Vies : <red>" + "❤".repeat(Math.max(0, cf.lives())) + "<dark_gray>" + "❤".repeat(Math.max(0, plugin.getConfig().getInt("game.lives", 3) - cf.lives())));
                    lines.add("<white>Ultime : <light_purple>" + (int) cf.ultimateCharge() + "%");
                    String boosts = com.kiokouwhite.crossfight.util.Boosts.summary(p);
                    if (!boosts.isEmpty()) lines.add("<white>Bonus : " + boosts);
                    StringBuilder allies = new StringBuilder();
                    for (Player ally : t.onlineMembers()) {
                        if (ally == p) continue;
                        CfPlayer acf = plugin.players().get(ally);
                        allies.append(acf.isAlive() ? "<green>" : "<gray>").append(ally.getName()).append(" ");
                    }
                    if (!allies.isEmpty()) lines.add("<white>Allies : " + allies.toString().trim());
                } else {
                    lines.add("<gray>Spectateur");
                }
            }
        }
        lines.add("<dark_gray>" + "━".repeat(18));
        return lines;
    }
}
