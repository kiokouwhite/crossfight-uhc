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
        // Pendant le choix du personnage, le tableau laisse l'ecran aux cartes (la barre du haut donne le temps). Un joueur
        // peut aussi le cacher (/cf scoreboard hide, ou /cf hud) : le tableau reste a jour (les pseudos colores), sans s'afficher.
        boolean visible = com.kiokouwhite.crossfight.hud.HudPrefs.Pref.TABLEAU.of(p);
        if (visible && (plugin.selection() == null || !plugin.selection().isViewing(p))) obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        nameTags(p, board);
        List<String> lines = buildLines(p);
        // Le logo descend dans le gris du tableau : des lignes vides lui gardent sa place.
        if (showsLogo(p)) for (int i = 0; i < LOGO_LINES; i++) lines.add(0, "");
        // Le jeu ne montre que 15 lignes : au-dela, le trait du haut laisse sa place pour que celui du bas reste a l'ecran.
        if (lines.size() > 15) {
            for (int i = 0; i < lines.size() - 1; i++) if (lines.get(i).contains("\u2501\u2501\u2501")) { lines.remove(i); break; }
        }
        int score = lines.size();
        for (String line : lines) {
            // Chaque entree doit etre unique : on ajoute des codes couleur invisibles au besoin
            String entry = unique(line, lines, score);
            org.bukkit.scoreboard.Score sc = obj.getScore(entry);
            sc.setScore(score--);
            // Une ligne qui porte un glyphe du pack (la tete d'un personnage) : son entree, du texte simple, perdrait la
            // police ; on lui donne donc son vrai texte comme nom affiche.
            if (line.contains("<font:")) sc.customName(Msg.mm(line));
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
        if (!showsLogo(p)) return Msg.mm("<gradient:#ff4d4d:#ffb84d><bold>CROSSFIGHT UHC");
        String[] quest = showsPanel(p) ? plugin.places().questPanel(p) : null;
        return quest == null ? LOGO : panel(quest);
    }

    // Le panneau de quete (tools/build_quete_panneau.py) : un parchemin dessine au-dessus du tableau, avec le nom de la
    // quete et son etat ecrits dessus. C'est un caractere du titre : on recule avant de le dessiner pour le centrer sur le
    // logo, on recule encore pour ecrire chaque ligne, puis on revient ou l'on etait, pour que le titre garde sa largeur.
    private static final net.kyori.adventure.key.Key QUEST_FONT = net.kyori.adventure.key.Key.key(com.kiokouwhite.crossfight.util.Sounds.NS, "quete"),
            QUEST_FONT_2 = net.kyori.adventure.key.Key.key(com.kiokouwhite.crossfight.util.Sounds.NS, "quete2"),
            // les memes caracteres, sans image : chacun recule de sa largeur (les lettres peuvent donc etre de largeurs inegales)
            QUEST_BACK = net.kyori.adventure.key.Key.key(com.kiokouwhite.crossfight.util.Sounds.NS, "quete_recul");
    private static final int PANEL_WIDTH = 172, LOGO_WIDTH = 53, LETTER = 7, MARGIN = 12;

    private boolean showsPanel(Player p) {
        return com.kiokouwhite.crossfight.util.Sounds.has("font.quete");
    }

    /** Un deplacement du curseur, en pixels (les caracteres d'espace de la police du panneau : puissances de deux). */
    private static String shift(int pixels) {
        StringBuilder b = new StringBuilder();
        int n = Math.abs(pixels);
        for (int k = 8; k >= 0; k--) if ((n & (1 << k)) != 0) b.append((char) ((pixels < 0 ? 0xE101 : 0xE111) + k));
        return b.toString();
    }

    /** Ce que la police du panneau sait ecrire : les caracteres simples, sans accent, et pas plus que la largeur du parchemin. */
    private static String plain(String text) {
        // (les lettres que la police du panneau connait : tools/build_quete_panneau.py, ACCENTS)
        String t = text.replaceAll("[^\\x20-\\x7EéèêëàâçùûîïôœÉÈÊÀÇ’…]", "");
        int max = 22;                    // le plus long nom de quete ; les lettres n'ont plus toutes la meme largeur
        return t.length() > max ? t.substring(0, max) : t;
    }

    private net.kyori.adventure.text.Component panel(String[] quest) {
        String name = plain(quest[0]), state = plain(quest[1]);
        // Le titre garde la largeur du panneau : le tableau s'elargit donc a sa mesure, et le panneau, centre comme tout
        // titre, tient dans l'ecran. Le logo est ensuite dessine au milieu de cette largeur.
        int side = (PANEL_WIDTH + 1 - LOGO_WIDTH) / 2;
        var white = net.kyori.adventure.text.format.NamedTextColor.WHITE;
        // Avec les polices de centrage (quete_demi : chaque lettre recule de la moitie de sa largeur ; quete_demi2 : elle
        // avance d'autant), chaque ligne est ecrite au milieu du panneau ; sinon, contre sa marge gauche.
        boolean centred = com.kiokouwhite.crossfight.util.Sounds.has("font.quete_demi");
        int from = centred ? PANEL_WIDTH / 2 : MARGIN;
        var half = net.kyori.adventure.key.Key.key(com.kiokouwhite.crossfight.util.Sounds.NS, "quete_demi");
        var half2 = net.kyori.adventure.key.Key.key(com.kiokouwhite.crossfight.util.Sounds.NS, "quete_demi2");
        var out = net.kyori.adventure.text.Component.text("\uE100" + shift(-(PANEL_WIDTH + 1) + from)).font(QUEST_FONT).color(white);
        Object[][] lines = {{name, QUEST_FONT, net.kyori.adventure.text.format.TextColor.fromHexString("#4a2c0a")}, {state, QUEST_FONT_2, net.kyori.adventure.text.format.TextColor.fromHexString(quest[2])}};
        for (Object[] line : lines) {
            String text = (String) line[0];
            if (centred) out = out.append(net.kyori.adventure.text.Component.text(text).font(half).color(white));
            out = out.append(net.kyori.adventure.text.Component.text(text).font((net.kyori.adventure.key.Key) line[1]).color((net.kyori.adventure.text.format.TextColor) line[2]))
                    .append(net.kyori.adventure.text.Component.text(text).font(QUEST_BACK).color(white));
            if (centred) out = out.append(net.kyori.adventure.text.Component.text(text).font(half2).color(white));
        }
        return out.append(gauge(quest, from))
                .append(net.kyori.adventure.text.Component.text(shift(-from + side)).font(QUEST_FONT).color(white))
                .append(LOGO)
                .append(net.kyori.adventure.text.Component.text(shift(PANEL_WIDTH + 1 - side - LOGO_WIDTH)).font(QUEST_FONT).color(white));
    }

    /** La jauge du panneau (avec le pack d'interface : police quete_jauge) : a 14 points du bord, large de 144 ; on y va, on la dessine, on revient. */
    private static final int GAUGE_X = 14, GAUGE_WIDTH = PANEL_WIDTH - 2 * GAUGE_X;

    private net.kyori.adventure.text.Component gauge(String[] quest, int from) {
        if (quest.length < 4 || !com.kiokouwhite.crossfight.util.Sounds.has("font.quete_jauge")) return net.kyori.adventure.text.Component.empty();
        int tenths;
        try { tenths = Math.max(0, Math.min(10, Integer.parseInt(quest[3]))); } catch (NumberFormatException e) { tenths = 0; }
        var white = net.kyori.adventure.text.format.NamedTextColor.WHITE;
        return net.kyori.adventure.text.Component.text(shift(GAUGE_X - from)).font(QUEST_FONT).color(white)
                .append(net.kyori.adventure.text.Component.text(String.valueOf((char) (0xE200 + tenths))).font(net.kyori.adventure.key.Key.key(com.kiokouwhite.crossfight.util.Sounds.NS, "quete_jauge")).color(white))
                .append(net.kyori.adventure.text.Component.text(shift(-(GAUGE_WIDTH + 1) - (GAUGE_X - from))).font(QUEST_FONT).color(white));
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

    private static double maxHealth(Player p) {
        var attr = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        return attr == null ? 20.0 : attr.getValue();
    }

    /** Une ligne d'allie : son nom et sa vie en pourcentage de sa vie maximale, ou "mort". */
    private static String allyLine(String head, String name, double health, double max, boolean alive) {
        return allyLine(head, name, health, max, alive, false);
    }

    /** La meme, pour un allie a terre (game.Downs) : en rouge, avec ce qu'il lui reste avant de mourir. */
    private static String allyLine(String head, String name, double health, double max, boolean alive, boolean down) {
        // Avec sa tete, plus besoin de puce devant le nom.
        String lead = head.isEmpty() ? "<gray> \u2022 " : " " + head;
        if (!alive) return lead + "<gray>" + name + " <dark_gray>mort";
        // Arrondi vers le haut : un allie encore en vie n'est jamais a 0 %.
        int percent = max <= 0 ? 0 : (int) Math.min(100, Math.ceil(health * 100.0 / max));
        if (down) return lead + "<red>" + name + " <dark_red>A TERRE <white>" + percent + "%";
        return lead + "<green>" + name + " <red>\u2764 <white>" + percent + "%";
    }

    /**
     * Devant le pseudo de chaque joueur (au-dessus de sa tete et dans la liste des joueurs), la tete de son
     * personnage : une equipe de tableau par personnage, sur le tableau de celui qui regarde, dont le prefixe est
     * le glyphe. Un joueur sans personnage, ou dont la tete n'est pas dans le pack, quitte son equipe.
     */
    private void nameTags(Player viewer, Scoreboard board) {
        for (Player q : Bukkit.getOnlinePlayers()) face(viewer, board, q.getName(), characterId(q));
        // Le mannequin du mode test en troisieme personne a la tete de son personnage devant son nom, comme un joueur
        // (une entite qui n'est pas un joueur est inscrite dans une equipe par son uuid).
        for (Player q : com.kiokouwhite.crossfight.util.MannequinPlayer.all()) {
            var body = com.kiokouwhite.crossfight.util.MannequinPlayer.body(q);
            if (body != null) face(viewer, board, body.getUniqueId().toString(), characterId(q));
        }
        // L'adversaire de demonstration (/cf demo) : la tete du personnage dont il porte le skin, devant son faux pseudo.
        for (var e : com.kiokouwhite.crossfight.game.DemoAdversaire.tetes().entrySet()) face(viewer, board, e.getKey(), e.getValue());
    }

    private String characterId(Player q) {
        CfPlayer qcf = plugin.players().get(q);
        return qcf == null || qcf.character() == null ? null : qcf.character().id();
    }

    /** La tete du personnage devant le nom de cette entree (un pseudo, ou l'uuid d'une entite), pour ce joueur. */
    private void face(Player viewer, Scoreboard board, String entry, String id) {
        net.kyori.adventure.text.Component face = com.kiokouwhite.crossfight.util.Heads.prefix(viewer, id);
        org.bukkit.scoreboard.Team now = board.getEntryTeam(entry);
        if (face == null) {
            if (now != null && now.getName().startsWith("tete_")) now.removeEntry(entry);
            return;
        }
        String name = "tete_" + id;
        if (now != null && now.getName().equals(name)) return;
        org.bukkit.scoreboard.Team team = board.getTeam(name);
        if (team == null) { team = board.registerNewTeam(name); team.prefix(face); }
        team.addEntry(entry);
    }

    private List<String> buildLines(Player p) {
        CfPlayer cf = plugin.players().get(p);
        GameManager game = plugin.game();
        List<String> lines = new ArrayList<>();
        lines.add("<dark_gray>" + "━".repeat(18));
        // Dans un lieu : sa quete, tout en haut.
        // (sans le pack, qui dessine le panneau de quete au-dessus du tableau, elle s'ecrit ici)
        if (!showsLogo(p) || !showsPanel(p)) lines.addAll(plugin.places().questOf(p));
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
                if (cf.character() != null) lines.addAll(cf.character().sidebarLines(p));
                if (!plugin.effectHud().showsUltimate(p)) lines.add("<white>Ultime : <light_purple>" + (int) cf.ultimateCharge() + "%");   // sinon l'orbe le dit
                String boosts = com.kiokouwhite.crossfight.util.Boosts.summary(p);
                if (!boosts.isEmpty()) lines.add("<white>Bonus : " + boosts);
                // La vie des allies, comme en partie : des noms d'essai, le mode test n'a pas d'equipe.
                lines.add("<white>Allies :");
                List<String> faces = com.kiokouwhite.crossfight.util.Heads.known();
                for (int i = 1; i <= 3; i++)
                    lines.add(allyLine(faces.isEmpty() ? "" : com.kiokouwhite.crossfight.util.Heads.tag(p, faces.get((i - 1) % faces.size())), "Allie" + i, 20.0, 20.0, true));
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
                lines.add("");
                Team t = cf.team();
                if (t != null) {
                    lines.add("<white>Equipe : " + t.coloredName());
                    if (t.licence() != null) lines.add("<white>Licence : <gold>" + t.licence().name());
                    lines.add("<white>Perso : <gold>" + (cf.character() == null ? "<gray>aucun" : cf.character().name()));
                    if (cf.character() != null) lines.addAll(cf.character().sidebarLines(p));
                    lines.add("<white>Vies : <red>" + "❤".repeat(Math.max(0, cf.lives())) + "<dark_gray>" + "❤".repeat(Math.max(0, plugin.getConfig().getInt("game.lives", 3) - cf.lives())));
                    if (!plugin.effectHud().showsUltimate(p)) lines.add("<white>Ultime : <light_purple>" + (int) cf.ultimateCharge() + "%");
                    String boosts = com.kiokouwhite.crossfight.util.Boosts.summary(p);
                    if (!boosts.isEmpty()) lines.add("<white>Bonus : " + boosts);
                    // Chaque allie sur sa ligne, avec sa vie en coeurs (mise a jour chaque seconde, comme le reste du tableau).
                    List<String> allies = new ArrayList<>();
                    for (Player ally : t.onlineMembers()) {
                        if (ally == p) continue;
                        CfPlayer acf = plugin.players().get(ally);
                        allies.add(allyLine(com.kiokouwhite.crossfight.util.Heads.tag(p, ally), ally.getName(), ally.getHealth(), maxHealth(ally), acf.isAlive() && !ally.isDead(), plugin.downs().isDown(ally)));
                    }
                    if (!allies.isEmpty()) { lines.add("<white>Allies :"); lines.addAll(allies); }
                } else {
                    lines.add("<gray>Spectateur");
                }
            }
        }
        lines.add("<dark_gray>" + "━".repeat(18));
        return lines;
    }
}
