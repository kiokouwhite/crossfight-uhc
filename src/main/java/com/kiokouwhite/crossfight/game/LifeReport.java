package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.util.Heads;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Sounds;
import io.papermc.paper.entity.TeleportFlag;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Le bilan des vies (regle annonce-vies) : les morts et les vies perdues ne sont plus dites dans le chat. Toutes les
 * vingt minutes de partie, un hologramme s'allume dans le ciel, devant chaque joueur : une carte par joueur qui a perdu
 * une vie depuis le dernier bilan, avec son pseudo, le personnage qu'il incarne (et sa tete) et une rangee de coeurs,
 * ses vies ; le coeur perdu gresille puis eclate. Sous les coeurs vides, "ELIMINE" ; tout en bas, les equipes eliminees.
 *
 * L'hologramme est fait de textes flottants (un par element), tous au meme point du ciel et places les uns par rapport
 * aux autres dans le plan de l'ecran : ils font face au joueur, et leur decalage est pris dans ce plan. Il se projette
 * a partir d'une ligne, est balaye par une ligne de lumiere, gresille de temps en temps, et suit le regard du joueur.
 * A l'air libre, chaque carte a son panneau sombre (les textes sont un peu devant). Sous terre, l'hologramme se voit a
 * travers les blocs, sans panneaux (un texte vu a travers les blocs passerait sous son panneau, assombri).
 * /cf annonce le montre tout de suite.
 */
public final class LifeReport {

    /** Ou s'ecrit l'hologramme : a cette distance devant le joueur, et a cette hauteur au-dessus de ses yeux (son bas). */
    private static final double AHEAD = 21, UP = 2.2;
    /** Hauteur d'une ligne de texte, en blocs, pour une echelle de 1. */
    private static final float LINE = 0.25f;
    private static final float S_TITLE = 6.5f, S_SUB = 3.6f, S_NAME = 5.2f, S_CHAR = 3.8f, S_HEART = 6.5f, S_TAG = 4.6f, S_TEAM = 4.2f;
    /** Tout l'hologramme est decale vers la gauche : le tableau de score occupe la droite de l'ecran. */
    private static final float SHIFT = -3.5f;
    private static final float CARD_W = 12.5f, HEART_GAP = 1.9f, PANEL_SCALE = 6f;
    /** De combien les textes sont devant les panneaux (vers le joueur). */
    private static final float FRONT = 0.25f;
    private static final int PER_ROW = 3, MAX_CARDS = 12, GROW = 8, FIRST_BREAK = 34, BREAK_GAP = 16;

    private static final String CYAN = "#7fe9ff", CYAN_PALE = "#c8f7ff", CYAN_DEEP = "#3fb8e0", HEART = "#ff4d63";
    private static final Color PANEL = Color.fromARGB(150, 8, 26, 48), SCAN = Color.fromARGB(120, 120, 235, 255), CLEAR = Color.fromARGB(0, 0, 0, 0);

    private final CrossfightPlugin plugin;
    private final Random random = new Random();
    /** Les vies perdues depuis le dernier bilan, par joueur, dans l'ordre ou elles l'ont ete. */
    private final Map<UUID, Integer> lost = new LinkedHashMap<>();
    private final Map<UUID, String> names = new LinkedHashMap<>();
    /** Le personnage de chacun quand il a perdu sa vie. */
    private final Map<UUID, String> characters = new HashMap<>();
    private final List<String> teamsOut = new ArrayList<>();
    private int lastMark;
    /** L'hologramme montre a chacun : un nouveau bilan remplace l'ancien. */
    private final Map<UUID, Holo> shown = new HashMap<>();

    /** Une carte : un joueur, son personnage, ses vies avant le bilan et celles qu'il a perdues. */
    private record Card(String name, String color, String characterId, String characterName, int before, int lost) {}

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
            if (!game.isTest()) { lost.clear(); names.clear(); characters.clear(); teamsOut.clear(); }
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
        CfPlayer cf = plugin.players().get(p);
        if (cf != null && cf.character() != null) characters.put(p.getUniqueId(), cf.character().id());
    }

    public void teamEliminated(Team t) {
        teamsOut.add(t.coloredName());
    }

    /** Le bilan, maintenant, pour tous les joueurs connectes ; puis on repart de zero. */
    public void announce() {
        List<Card> cards = cards();
        List<String> teams = new ArrayList<>(teamsOut);
        if (cards.isEmpty() && teams.isEmpty() && plugin.game().isTest()) {
            // Mode test sans mort relevee : des exemples, pour voir a quoi ressemble le bilan.
            List<String> known = Heads.known();
            String[][] demo = {{"marth", "Marth"}, {"ruby", "Ruby"}, {"arlo", "Arlo"}};
            for (int i = 0; i < demo.length; i++) {
                String id = known.contains(demo[i][0]) || known.isEmpty() ? demo[i][0] : known.get(i % known.size());
                cards.add(new Card("Joueur" + (i + 1), new String[]{"red", "blue", "green"}[i], id, nameOf(id, demo[i][1]), 3, i == 2 ? 3 : i + 1));
            }
            teams.add("<green>Equipe 3</green>");
        }
        lost.clear();
        names.clear();
        characters.clear();
        teamsOut.clear();
        String title = "BILAN" + (plugin.game().elapsedSeconds() >= 60 ? "  " + Msg.time(plugin.game().elapsedSeconds()) : "");
        String sub = "Vies perdues ces " + everySeconds() / 60 + " dernieres minutes";
        for (Player p : Bukkit.getOnlinePlayers()) {
            Holo old = shown.remove(p.getUniqueId());
            if (old != null) old.remove();
            Holo h = new Holo(p, title, sub, cards, teams);
            shown.put(p.getUniqueId(), h);
            h.runTaskTimer(plugin, 1L, 1L);
        }
    }

    private String nameOf(String characterId, String fallback) {
        var c = characterId == null ? null : plugin.characters().character(characterId);
        return c == null ? fallback : c.name();
    }

    private List<Card> cards() {
        List<Card> out = new ArrayList<>();
        int max = Math.max(1, plugin.game().rules().value("vies"));
        for (var e : lost.entrySet()) {
            CfPlayer cf = plugin.players().get(e.getKey());
            Team team = cf == null ? null : cf.team();
            int n = e.getValue();
            // En mode test, les vies ne baissent pas : on montre ce qu'elles auraient fait.
            int left = plugin.game().isTest() ? Math.max(0, max - n) : cf == null ? 0 : cf.lives();
            String id = characters.get(e.getKey());
            out.add(new Card(names.getOrDefault(e.getKey(), "?"), team == null ? "white" : team.color(), id, id == null ? null : nameOf(id, id),
                    Math.min(10, left + n), Math.min(n, 10)));
        }
        return out;
    }

    // ------------------------------------------------------------------ l'hologramme

    private static Location skyOf(Player p) {
        double yaw = Math.toRadians(p.getLocation().getYaw());
        return p.getLocation().add(-Math.sin(yaw) * AHEAD, UP + p.getEyeHeight(), Math.cos(yaw) * AHEAD);
    }

    private static Transformation transform(float x, float y, float z, float sx, float sy) {
        return new Transformation(new Vector3f(x + SHIFT, y, z), new AxisAngle4f(), new Vector3f(sx, sy, sx), new AxisAngle4f());
    }

    /** Un texte de l'hologramme, dans les polices du HUD pour qui a le pack. */
    private static Component styled(Player viewer, String mini, boolean title) {
        String font = title ? "hud_titre" : "hud_texte";
        if (!Sounds.has("font." + font) || !Sounds.hasPackLoaded(viewer)) return Msg.mm(mini);
        return Msg.mm(mini.replace("<bold>", "").replace("</bold>", "")).font(Key.key(Sounds.NS, font));
    }

    /** La police de titre du HUD est deux fois plus grande que celle de Minecraft : on la reduit d'autant. */
    private static float titleRatio(Player viewer) {
        return Sounds.has("font.hud_titre") && Sounds.hasPackLoaded(viewer) ? 0.5f : 1f;
    }

    /** L'hologramme d'un joueur : ses textes, leur place, et leur animation. */
    private final class Holo extends BukkitRunnable {
        final Player p;
        /** Le texte du titre porte tous les autres (ils montent dessus) : on ne deplace que lui. */
        TextDisplay root;
        final List<TextDisplay> all = new ArrayList<>();
        final Map<TextDisplay, Transformation> target = new HashMap<>();
        final Map<TextDisplay, Color> background = new HashMap<>();
        /** Les coeurs a perdre, dans l'ordre ou ils eclatent, avec le tick ou chacun commence. */
        final List<Object[]> breaks = new ArrayList<>();
        /** Les mentions "ELIMINE", et le tick ou chacune apparait. */
        final List<Object[]> tags = new ArrayList<>();
        TextDisplay scan;
        /** A l'air libre (le ciel au-dessus de lui) : panneaux, textes masques par le relief ; sinon vus a travers les blocs. */
        final boolean open;
        float top, bottom, mid;
        int t, end, dimUntil = -1;

        Holo(Player p, String title, String sub, List<Card> cards, List<String> teams) {
            this.p = p;
            this.open = p.getWorld().getHighestBlockYAt(p.getLocation()) <= p.getEyeLocation().getY();
            Location at = skyOf(p);
            float tr = titleRatio(p);
            int shown = Math.min(cards.size(), MAX_CARDS);
            int rows = cards.isEmpty() ? 1 : (shown + PER_ROW - 1) / PER_ROW;
            float rowH = (S_NAME + S_CHAR + S_HEART) * LINE + 0.9f, rowGap = 1.4f;
            float teamsH = teams.isEmpty() ? 0 : teams.size() * S_TEAM * LINE + 0.6f;
            float cardsTop = teamsH + rows * rowH + (rows - 1) * rowGap;
            bottom = 0;
            // Le titre et la ligne sous le titre.
            float subY = cardsTop + 1.1f, titleY = subY + S_SUB * LINE + 0.4f;
            top = titleY + S_TITLE * tr * LINE;
            mid = (top + bottom) / 2;
            root = text(at, styled(p, "<" + CYAN + "><bold>" + title + "</bold>", true), 0, titleY, 0, S_TITLE * tr, CLEAR);
            text(at, styled(p, "<" + CYAN_DEEP + ">" + sub, false), 0, subY, 0, S_SUB, CLEAR);

            int maxLost = 0;
            if (cards.isEmpty()) {
                text(at, styled(p, "<" + CYAN_PALE + ">Aucune vie perdue", false), 0, cardsTop - rowH / 2 - 0.6f, 0, S_NAME, CLEAR);
            }
            for (int i = 0; i < shown; i++) {
                Card c = cards.get(i);
                int row = i / PER_ROW, col = i % PER_ROW, inRow = Math.min(PER_ROW, shown - row * PER_ROW);
                float x = (col - (inRow - 1) / 2f) * CARD_W;
                float rowTop = cardsTop - row * (rowH + rowGap);
                // Le panneau de la carte, juste derriere.
                if (open) panel(at, x, rowTop - rowH - 0.3f, rowH + 0.6f);
                float nameY = rowTop - S_NAME * LINE, charY = nameY - S_CHAR * LINE - 0.25f, heartY = charY - S_HEART * LINE - 0.35f;
                text(at, styled(p, "<" + CYAN_PALE + "><bold>" + c.name() + "</bold>", false), x, nameY, FRONT, S_NAME, CLEAR);
                String head = c.characterId() == null ? "" : Heads.tag(p, c.characterId());
                if (c.characterName() != null) text(at, styled(p, head + "<" + CYAN + ">" + c.characterName(), false), x, charY, FRONT, S_CHAR, CLEAR);
                int n = Math.max(1, c.before());
                for (int h = 0; h < n; h++) {
                    float hx = x + (h - (n - 1) / 2f) * HEART_GAP;
                    TextDisplay heart = text(at, Msg.mm("<" + HEART + ">❤"), hx, heartY, FRONT, S_HEART, CLEAR);
                    // Les derniers coeurs de la rangee sont ceux qu'il a perdus : ils eclatent, du dernier vers le premier.
                    int order = n - 1 - h;
                    if (order < c.lost()) breaks.add(new Object[]{heart, FIRST_BREAK + order * BREAK_GAP, hx, heartY});
                }
                maxLost = Math.max(maxLost, c.lost());
                // Plus aucun coeur : "ELIMINE" s'allume a leur place, une fois le dernier eclate.
                if (c.before() - c.lost() <= 0) tags.add(new Object[]{null, FIRST_BREAK + c.lost() * BREAK_GAP + 4, x, heartY - 0.15f});
            }
            if (cards.size() > MAX_CARDS) text(at, styled(p, "<" + CYAN_DEEP + ">... et " + (cards.size() - MAX_CARDS) + " autres", false),
                    0, teamsH + 0.1f, 0, S_TEAM, CLEAR);
            for (int i = 0; i < teams.size(); i++)
                text(at, styled(p, teams.get(i) + " <#ff5a5a>eliminee", false), 0, (teams.size() - 1 - i) * S_TEAM * LINE, 0, S_TEAM, CLEAR);

            // La ligne de lumiere qui balaie l'hologramme de haut en bas.
            scan = at.getWorld().spawn(at, TextDisplay.class, d -> prepare(d, Component.text(" ".repeat(70)), SCAN));
            scan.setTransformation(transform(0, top, FRONT * 3, 10f, 0.5f));
            mount(scan);
            scan.setTextOpacity((byte) 0);

            end = Math.max(20 * 15, FIRST_BREAK + maxLost * BREAK_GAP + 20 * 6);
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.9f, 1.7f);
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 0.8f, 0.7f);
        }

        private void prepare(TextDisplay d, Component text, Color bg) {
            d.setVisibleByDefault(false);
            d.setPersistent(false);
            d.text(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setSeeThrough(!open);
            d.setShadowed(false);
            d.setLineWidth(1000);
            d.setBackgroundColor(bg);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(4f);
            d.setTeleportDuration(3);
        }

        private void mount(TextDisplay d) {
            p.showEntity(plugin, d);
            all.add(d);
            if (root != null && d != root) root.addPassenger(d);
        }

        /** Un texte, place dans le plan de l'ecran ; il apparait plat (une ligne) puis se deplie. */
        private TextDisplay text(Location at, Component c, float x, float y, float z, float s, Color bg) {
            TextDisplay d = at.getWorld().spawn(at, TextDisplay.class, e -> prepare(e, c, bg));
            target.put(d, transform(x, y, z, s, s));
            background.put(d, bg);
            d.setTransformation(transform(x * 0.2f, mid, z, s, 0.02f));
            mount(d);
            return d;
        }

        private void panel(Location at, float x, float y, float h) {
            int lines = Math.max(1, Math.round(h / (PANEL_SCALE * LINE)));
            String row = " ".repeat(Math.round((CARD_W - 1.5f) / (PANEL_SCALE * 0.1f)));
            text(at, Component.text(String.join("\n", java.util.Collections.nCopies(lines, row))), x, y, -FRONT, PANEL_SCALE, PANEL);
        }

        private void move(TextDisplay d, Transformation to, int ticks) {
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(ticks);
            d.setTransformation(to);
        }

        @Override
        public void run() {
            t++;
            if (!p.isOnline() || root == null || !root.isValid() || !p.getWorld().equals(root.getWorld())) { remove(); return; }
            // Il reste devant lui dans le ciel, ou qu'il se tourne.
            root.teleport(skyOf(p), TeleportFlag.EntityState.RETAIN_PASSENGERS);

            if (t == 2) for (TextDisplay d : all) if (d != scan && target.containsKey(d)) move(d, target.get(d), GROW);
            // La ligne de lumiere : de haut en bas, toutes les deux secondes.
            int cycle = (t - 12) % 40;
            if (t >= 12 && t < end - GROW) {
                if (cycle == 0) { scan.setTextOpacity((byte) -1); scan.setInterpolationDuration(0); scan.setTransformation(transform(0, top, FRONT * 3, 10f, 0.5f)); }
                if (cycle == 1) move(scan, transform(0, bottom - 0.5f, FRONT * 3, 10f, 0.5f), 26);
            }
            // Il gresille : de temps en temps, tout baisse un instant.
            if (t > GROW + 4 && t < end - GROW && dimUntil < t && random.nextInt(45) == 0) {
                dimUntil = t + 1 + random.nextInt(3);
                for (TextDisplay d : all) if (d != scan) d.setTextOpacity((byte) 110);
            }
            if (t == dimUntil) for (TextDisplay d : all) if (d != scan && !isHiddenTag(d)) d.setTextOpacity((byte) -1);

            for (Object[] b : breaks) heartBreak((TextDisplay) b[0], t - (int) b[1], (float) b[2], (float) b[3]);
            for (Object[] tg : tags) {
                int at = t - (int) tg[1];
                if (at == 0 && t < end - GROW) {
                    TextDisplay tag = text(root.getLocation(), styled(p, "<#ff5a5a><bold>ELIMINE</bold>", false), (float) tg[2], (float) tg[3], FRONT * 2, S_TAG, CLEAR);
                    tg[0] = tag;
                    p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.6f, 1.4f);
                }
                if (tg[0] instanceof TextDisplay tag && tag.isValid()) {
                    if (at == 1) move(tag, target.get(tag), 5);
                    // Il gresille en s'allumant.
                    if (at > 1 && at < 16 && at % 3 == 0) tag.text(styled(p, (at % 6 == 0 ? "<white>" : "<#ff5a5a>") + "<bold>ELIMINE</bold>", false));
                }
            }

            if (t == end - GROW) {
                for (TextDisplay d : all) {
                    Transformation tr = d.getTransformation();
                    move(d, transform((tr.getTranslation().x - SHIFT) * 0.2f, mid, tr.getTranslation().z, tr.getScale().x, 0.02f), GROW);
                }
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.7f, 1.6f);
            }
            if (t >= end) remove();
        }

        private boolean isHiddenTag(TextDisplay d) {
            return false;
        }

        /** Le coeur perdu : il gresille, palit, gonfle, puis eclate en eclats. */
        private void heartBreak(TextDisplay heart, int at, float x, float y) {
            if (at < 0 || at > 16 || !heart.isValid()) return;
            if (at < 10 && at % 2 == 0) heart.text(Msg.mm(at % 4 == 0 ? "<white>❤" : "<" + HEART + ">❤"));
            if (at == 0) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.7f, 0.6f);
            if (at == 10) {
                heart.text(Msg.mm("<#8a9aa6>❤"));
                move(heart, transform(x, y + 0.3f, FRONT, S_HEART * 1.35f, S_HEART * 1.35f), 4);
            }
            if (at == 14) {
                move(heart, transform(x, y + 0.6f, FRONT, 0.01f, 0.01f), 3);
                p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.7f, 1.5f);
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.8f, 1.2f);
                shards(x, y + 0.5f);
            }
        }

        /** Des eclats de coeur, qui partent dans tous les sens et s'eteignent. */
        private void shards(float x, float y) {
            Location at = root.getLocation();
            for (int i = 0; i < 6; i++) {
                final int k = i;
                double a = Math.PI * 2 * k / 6 + random.nextDouble() * 0.6;
                float dx = (float) Math.cos(a) * 2.2f, dy = (float) Math.sin(a) * 1.8f;
                TextDisplay s = at.getWorld().spawn(at, TextDisplay.class, d -> prepare(d, Msg.mm(k % 2 == 0 ? "<" + HEART + ">▪" : "<" + CYAN_PALE + ">▪"), CLEAR));
                s.setTransformation(transform(x, y, FRONT * 2, 3.2f, 3.2f));
                mount(s);
                Bukkit.getScheduler().runTask(plugin, () -> { if (s.isValid()) move(s, transform(x + dx, y + dy, FRONT * 2, 0.01f, 0.01f), 10); });
                Bukkit.getScheduler().runTaskLater(plugin, () -> { all.remove(s); s.remove(); }, 12L);
            }
        }

        void remove() {
            cancel();
            for (TextDisplay d : all) d.remove();
            all.clear();
            shown.remove(p.getUniqueId(), this);
        }
    }
}
