package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le bilan des vies (regle annonce-vies) : les morts et les vies perdues ne sont plus dites dans le chat. Toutes les
 * vingt minutes de partie, un grand texte s'ecrit dans le ciel, devant chaque joueur : qui a perdu une vie depuis le
 * dernier bilan, combien il lui en reste, et les equipes eliminees. Le texte se voit a travers les blocs (sous terre
 * aussi), reste une quinzaine de secondes et suit le regard du joueur (toujours devant lui, haut dans le ciel). /cf annonce le montre tout de suite.
 */
public final class LifeReport {

    private static final int SHOW_TICKS = 20 * 15, GROW_TICKS = 10, MAX_LINES = 12;
    /** Ou s'ecrit le texte : a cette distance devant le joueur, et a cette hauteur au-dessus de ses yeux (le bas du texte). */
    private static final double AHEAD = 36, UP = 4.5;
    private static final float SCALE = 8.5f;

    private final CrossfightPlugin plugin;
    /** Les vies perdues depuis le dernier bilan, par joueur, dans l'ordre ou elles l'ont ete. */
    private final Map<UUID, Integer> lost = new LinkedHashMap<>();
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private final List<String> teamsOut = new ArrayList<>();
    private int lastMark;
    /** Le texte montre a chacun : un nouveau bilan remplace l'ancien. */
    private final Map<UUID, TextDisplay> shown = new java.util.HashMap<>();

    public LifeReport(CrossfightPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private int everySeconds() {
        return Math.max(1, plugin.game().rules().value("annonce-vies")) * 60;
    }

    private void tick() {
        GameManager game = plugin.game();
        if (game.state() != GameState.RUNNING) {
            // Hors partie : on repart de zero (le mode test garde ce qu'il a releve, pour /cf annonce).
            if (!game.isTest()) { lost.clear(); names.clear(); teamsOut.clear(); }
            lastMark = 0;
            return;
        }
        int mark = game.elapsedSeconds() / everySeconds();
        if (mark > lastMark) {
            lastMark = mark;
            announce();
        }
    }

    /** Un joueur a perdu une vie (mort, Regna Ferox, Omori...). */
    public void lifeLost(Player p) {
        lost.merge(p.getUniqueId(), 1, Integer::sum);
        names.put(p.getUniqueId(), p.getName());
    }

    public void teamEliminated(Team t) {
        teamsOut.add(t.coloredName());
    }

    /** Le bilan, maintenant, pour tous les joueurs connectes ; puis on repart de zero. */
    public void announce() {
        List<String> lines = lines();
        lost.clear();
        names.clear();
        teamsOut.clear();
        for (Player p : Bukkit.getOnlinePlayers()) show(p, lines);
    }

    private List<String> lines() {
        List<String> out = new ArrayList<>();
        int minutes = plugin.game().elapsedSeconds() / 60;
        out.add("<gold><bold>BILAN" + (minutes > 0 ? " - " + Msg.time(plugin.game().elapsedSeconds()) : "") + "</bold>");
        int every = everySeconds() / 60;
        out.add("<gray>Vies perdues ces " + every + " dernieres minutes");
        List<String> rows = new ArrayList<>();
        for (var e : lost.entrySet()) {
            CfPlayer cf = plugin.players().get(e.getKey());
            Team team = cf == null ? null : cf.team();
            String name = names.getOrDefault(e.getKey(), "?");
            String who = team == null ? "<white>" + name : "<" + team.color() + ">" + name + "</" + team.color() + ">";
            int n = e.getValue();
            int left = cf == null ? 0 : cf.lives();
            String lostText = "<red>-" + n + " vie" + (n > 1 ? "s" : "");
            String rest = plugin.game().isTest() ? "<gray>mode test" : left <= 0 ? "<dark_red>elimine" : "<white>" + left + " restante" + (left > 1 ? "s" : "");
            rows.add(who + " " + lostText + " <dark_gray>> " + rest);
        }
        for (String t : teamsOut) rows.add(t + " <dark_red>eliminee");
        if (rows.isEmpty() && plugin.game().isTest()) {
            // Mode test sans mort relevee : des exemples, pour voir a quoi ressemble le bilan.
            rows.add("<red>Joueur1</red> <red>-1 vie <dark_gray>> <white>2 restantes");
            rows.add("<blue>Joueur2</blue> <red>-2 vies <dark_gray>> <white>1 restante");
            rows.add("<green>Joueur3</green> <red>-1 vie <dark_gray>> <dark_red>elimine");
            rows.add("<green>Equipe 3</green> <dark_red>eliminee");
        }
        if (rows.isEmpty()) rows.add("<green>Aucune vie perdue");
        if (rows.size() > MAX_LINES) {
            int more = rows.size() - (MAX_LINES - 1);
            rows = new ArrayList<>(rows.subList(0, MAX_LINES - 1));
            rows.add("<gray>... et " + more + " autres");
        }
        out.add("");
        out.addAll(rows);
        return out;
    }

    private static net.kyori.adventure.text.Component text(Player viewer, List<String> lines) {
        boolean font = Sounds.has("font.hud_titre") && Sounds.hasPackLoaded(viewer);
        net.kyori.adventure.text.Component out = net.kyori.adventure.text.Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            net.kyori.adventure.text.Component c;
            if (font && line.contains("<bold>")) {
                c = Msg.mm(line.replace("<bold>", "").replace("</bold>", "")).font(net.kyori.adventure.key.Key.key(Sounds.NS, "hud_titre"));
            } else if (font) {
                c = Msg.mm(line).font(net.kyori.adventure.key.Key.key(Sounds.NS, "hud_texte"));
            } else {
                c = Msg.mm(line);
            }
            if (i > 0) out = out.append(net.kyori.adventure.text.Component.newline());
            out = out.append(c);
        }
        return out;
    }

    /** Ou est le texte pour ce joueur : devant lui, la ou il regarde a l'horizontale, haut dans le ciel. */
    private static Location skyOf(Player p) {
        double yaw = Math.toRadians(p.getLocation().getYaw());
        return p.getLocation().add(-Math.sin(yaw) * AHEAD, UP + p.getEyeHeight(), Math.cos(yaw) * AHEAD);
    }

    /** Le grand texte dans le ciel, pour ce joueur seul : il grandit, reste, puis s'efface. Il suit son regard. */
    private void show(Player p, List<String> lines) {
        TextDisplay old = shown.remove(p.getUniqueId());
        if (old != null) old.remove();
        Location at = skyOf(p);
        TextDisplay d = p.getWorld().spawn(at, TextDisplay.class, t -> {
            t.setVisibleByDefault(false);
            t.setPersistent(false);
            t.text(text(p, lines));
            t.setBillboard(Display.Billboard.CENTER);
            t.setAlignment(TextDisplay.TextAlignment.CENTER);
            t.setSeeThrough(true);
            t.setShadowed(true);
            t.setLineWidth(400);
            t.setBackgroundColor(Color.fromARGB(110, 12, 12, 24));
            t.setBrightness(new Display.Brightness(15, 15));
            t.setViewRange(4f);
            t.setTeleportDuration(3);
            t.setTransformation(transform(0.01f));
        });
        p.showEntity(plugin, d);
        shown.put(p.getUniqueId(), d);
        p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.7f);
        p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.8f, 1f);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                if (!p.isOnline() || !d.isValid() || !p.getWorld().equals(d.getWorld())) { d.remove(); cancel(); return; }
                if (t == 2) grow(d, SCALE);
                if (t == SHOW_TICKS - GROW_TICKS) grow(d, 0.01f);
                if (t >= SHOW_TICKS) { d.remove(); shown.remove(p.getUniqueId(), d); cancel(); return; }
                // Il reste devant lui dans le ciel, ou qu'il se tourne.
                d.teleport(skyOf(p));
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static void grow(TextDisplay d, float scale) {
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(GROW_TICKS);
        d.setTransformation(transform(scale));
    }

    private static Transformation transform(float scale) {
        return new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(scale, scale, scale), new AxisAngle4f());
    }
}
