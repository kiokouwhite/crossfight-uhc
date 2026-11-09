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

    private void updateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) update(p);
    }

    private void update(Player p) {
        Scoreboard board = boards.computeIfAbsent(p.getUniqueId(), u -> Bukkit.getScoreboardManager().getNewScoreboard());
        Objective old = board.getObjective("cf");
        if (old != null) old.unregister();
        Objective obj = board.registerNewObjective("cf", Criteria.DUMMY, Msg.mm("<gradient:#ff4d4d:#ffb84d><bold>CROSSFIGHT UHC"));
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        List<String> lines = buildLines(p);
        int score = lines.size();
        for (String line : lines) {
            // Chaque entree doit etre unique : on ajoute des codes couleur invisibles au besoin
            String entry = unique(line, lines, score);
            obj.getScore(entry).setScore(score--);
        }
        if (p.getScoreboard() != board) p.setScoreboard(board);
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
        switch (game.state()) {
            case LOBBY, ENDED -> {
                lines.add("<white>Etat : <yellow>Lobby");
                lines.add("<white>Joueurs : <yellow>" + Bukkit.getOnlinePlayers().size());
                lines.add("<white>Equipes : <yellow>" + plugin.teams().teamSize().label());
                Team t = cf.team();
                lines.add("<white>Ton equipe : " + (t == null ? "<gray>aucune" : t.coloredName()));
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
