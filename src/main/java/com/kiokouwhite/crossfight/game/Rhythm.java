package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Le jeu de rythme d'Anna (le service "rythme", a Shiganshina) : des notes tombent sur quatre colonnes, et l'on appuie sur
 * les touches 1, 2, 3 et 4 du clavier quand elles atteignent la ligne du bas. Neuf musiques, trois par difficulte ; la
 * note obtenue (C, B ou A) donne des materiaux, une seule fois par musique : rejouer ne rapporte que ce qu'une meilleure
 * note ajoute.
 *
 * Les musiques sont composees ici, par le plugin : une melodie tiree d'une graine (toujours la meme pour une musique),
 * jouee avec les sons de blocs musicaux du jeu, sur une basse et une batterie. Les notes a frapper sont celles de la
 * melodie. Rien n'est repris d'un jeu ni d'un disque.
 *
 * Le tableau est fait d'affichages de texte poses devant le joueur, que lui seul voit. Les touches 1 a 4 sont lues par
 * le changement de case de la barre d'objets : le plugin l'annule aussitot, la main reste ou elle etait.
 */
public final class Rhythm implements Listener {

    /** Une musique : son nom, sa difficulte (0 facile, 1 moyen, 2 difficile), sa graine, l'instrument de sa melodie. */
    public record Song(String id, String title, int level, long seed, Sound lead) {}

    public static final Song[] SONGS = {
            new Song("recrues", "Marche des recrues", 0, 11L, Sound.BLOCK_NOTE_BLOCK_HARP),
            new Song("marche", "Ronde du marché", 0, 23L, Sound.BLOCK_NOTE_BLOCK_XYLOPHONE),
            new Song("berceuse", "Berceuse du rempart", 0, 37L, Sound.BLOCK_NOTE_BLOCK_FLUTE),
            new Song("cavalcade", "Cavalcade", 1, 41L, Sound.BLOCK_NOTE_BLOCK_GUITAR),
            new Song("lames", "Lames au vent", 1, 59L, Sound.BLOCK_NOTE_BLOCK_PLING),
            new Song("veillee", "Veillée d'armes", 1, 67L, Sound.BLOCK_NOTE_BLOCK_BELL),
            new Song("tonnerre", "Tonnerre", 2, 71L, Sound.BLOCK_NOTE_BLOCK_BIT),
            new Song("rempart", "Dernier rempart", 2, 83L, Sound.BLOCK_NOTE_BLOCK_PLING),
            new Song("envol", "L'envol", 2, 97L, Sound.BLOCK_NOTE_BLOCK_CHIME)};

    private static final String[] LEVELS = {"Facile", "Moyen", "Difficile"};
    private static final String[] LEVEL_COLORS = {"<green>", "<gold>", "<red>"};
    /** Par difficulte : les ticks par temps (20 ticks par seconde), le nombre de mesures, la duree de chute d'une note. */
    private static final int[] BEAT = {12, 10, 8}, BARS = {16, 20, 24}, FALL = {30, 24, 18};
    /** Les recompenses : [difficulte][note C, B, A] = {fer, or, diamants}. */
    private static final int[][][] REWARDS = {
            {{5, 0, 0}, {5, 5, 0}, {5, 10, 1}},
            {{10, 10, 0}, {10, 20, 1}, {10, 20, 2}},
            {{0, 10, 1}, {0, 10, 2}, {0, 20, 3}}};
    private static final String[] GRADES = {"C", "B", "A"};
    /** La part du score parfait qu'il faut pour chaque note. */
    private static final double[] NEEDED = {0.55, 0.75, 0.90};
    /** Les gammes (en demi-tons, sur les deux octaves des blocs musicaux) : une par musique, au tour de role. */
    private static final int[][] SCALES = {
            {6, 8, 10, 13, 15, 18, 20, 22},          // majeure pentatonique
            {6, 9, 11, 13, 16, 18, 21, 23},          // mineure pentatonique
            {6, 8, 10, 11, 13, 15, 17, 18, 20, 22}}; // majeure
    private static final Color[] LANES = {Color.fromRGB(240, 80, 110), Color.fromRGB(90, 170, 250), Color.fromRGB(110, 220, 130), Color.fromRGB(250, 200, 70)};

    /** Une note a frapper : quand (en ticks depuis le debut), sur quelle colonne, et le son qu'elle fait. */
    private record Note(int tick, int lane, int pitch) {}

    /** Une partie en cours. */
    private final class Play {
        final Player player;
        final Song song;
        final List<Note> notes;
        final boolean[] judged;
        final Map<Integer, Entity> falling = new HashMap<>();
        final List<Entity> board = new ArrayList<>();
        final BossBar bar;
        final Location start;
        final Vector right;
        final Location base;                 // le milieu de la ligne de frappe
        final int held, length;
        int tick = -70, next, score, combo, best, perfect, good, ok, missed;

        Play(Player player, Song song) {
            this.player = player;
            this.song = song;
            this.notes = chart(song);
            this.judged = new boolean[notes.size()];
            this.length = notes.get(notes.size() - 1).tick() + 30;
            this.held = player.getInventory().getHeldItemSlot();
            this.start = player.getLocation();
            Vector look = start.getDirection().setY(0);
            if (look.lengthSquared() < 0.01) look = new Vector(0, 0, 1);
            look.normalize();
            this.right = new Vector(-look.getZ(), 0, look.getX());
            this.base = player.getEyeLocation().add(look.multiply(4.5)).add(0, -1.3, 0);
            this.bar = BossBar.bossBar(Msg.mm(LEVEL_COLORS[song.level()] + song.title() + " <gray>- " + LEVELS[song.level()]), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
        }

        Location lane(int lane, double up) {
            return base.clone().add(right.clone().multiply((lane - 1.5) * 0.85)).add(0, up, 0);
        }
    }

    private static final double HEIGHT = 3.4;        // la hauteur de chute, en blocs

    private final CrossfightPlugin plugin;
    private final Map<UUID, Play> plays = new HashMap<>();
    /** La meilleure note de chaque joueur a chaque musique (0 : C, 1 : B, 2 : A), pour la partie en cours. */
    private final Map<UUID, Map<String, Integer>> grades = new HashMap<>();

    public Rhythm(CrossfightPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 1L);
    }

    // ------------------------------------------------------------------ les musiques

    private static float pitch(int semitone) {
        return (float) Math.pow(2, (Math.max(0, Math.min(24, semitone)) - 12) / 12.0);
    }

    /**
     * La partition d'une musique, toujours la meme : deux motifs de deux mesures, tires de sa graine, repris et
     * transposes (A A B A...). Plus la difficulte monte, plus les temps sont divises ; le difficile a des accords.
     */
    private static List<Note> chart(Song song) {
        Random random = new Random(song.seed());
        int level = song.level(), beat = BEAT[level];
        int[] scale = SCALES[(int) (song.seed() % SCALES.length)];
        // un motif : pour chacun de ses huit temps, les instants (en ticks dans le temps) ou tombe une note
        List<List<int[]>> motifs = new ArrayList<>();
        for (int m = 0; m < 3; m++) {
            List<int[]> motif = new ArrayList<>();
            int degree = 2 + random.nextInt(3);
            for (int b = 0; b < 8; b++) {
                int roll = random.nextInt(100);
                int[] offsets;
                if (level == 0) offsets = roll < 12 && b % 4 != 0 ? new int[0] : roll < 30 ? new int[]{0, beat / 2} : new int[]{0};
                else if (level == 1) offsets = roll < 6 && b % 4 != 0 ? new int[0] : roll < 34 ? new int[]{0} : roll < 88 ? new int[]{0, beat / 2} : new int[]{0, beat / 2, beat * 3 / 4};
                else offsets = roll < 14 ? new int[]{0} : roll < 62 ? new int[]{0, beat / 2} : roll < 86 ? new int[]{0, beat / 4, beat / 2, beat * 3 / 4} : new int[]{0, beat / 4, beat / 2};
                for (int offset : offsets) {
                    degree = Math.max(0, Math.min(scale.length - 1, degree + new int[]{-2, -1, -1, 0, 1, 1, 2}[random.nextInt(7)]));
                    motif.add(new int[]{b * beat + offset, degree});
                }
            }
            motifs.add(motif);
        }
        int[] order = {0, 0, 1, 0, 2, 1, 0, 0, 1, 2, 0, 0};
        List<Note> notes = new ArrayList<>();
        int last = -1, lastTick = -100;
        for (int part = 0; part * 2 < BARS[level]; part++) {
            List<int[]> motif = motifs.get(order[part % order.length]);
            int shift = part % 4 == 3 ? 1 : 0;                       // la quatrieme reprise monte d'un degre
            for (int[] n : motif) {
                int tick = part * 8 * beat + n[0], degree = Math.min(scale.length - 1, n[1] + shift);
                int lane = degree % 4;
                if (lane == last && tick - lastTick < beat) lane = (lane + 1 + (degree & 1) * 2) % 4;   // pas deux fois la meme touche coup sur coup
                notes.add(new Note(tick, lane, scale[degree]));
                // un accord, au difficile, sur le premier temps d'une mesure
                if (level == 2 && n[0] % (4 * beat) == 0 && random.nextInt(100) < 55) notes.add(new Note(tick, (lane + 2) % 4, scale[Math.max(0, degree - 2)]));
                last = lane;
                lastTick = tick;
            }
        }
        return notes;
    }

    /** Les secondes que dure une musique. */
    private static int seconds(Song song) {
        return BARS[song.level()] * 4 * BEAT[song.level()] / 20;
    }

    // ------------------------------------------------------------------ le menu

    private static String reward(int[] r) {
        List<String> parts = new ArrayList<>();
        if (r[0] > 0) parts.add(r[0] + " fer");
        if (r[1] > 0) parts.add(r[1] + " or");
        if (r[2] > 0) parts.add(r[2] + (r[2] > 1 ? " diamants" : " diamant"));
        return String.join(", ", parts);
    }

    private static Component plain(String mini) {
        return Msg.mm(mini).decoration(TextDecoration.ITALIC, false);
    }

    private final class Menu implements InventoryHolder {
        final Inventory inv = Bukkit.createInventory(this, 27, Component.text("Anna - le concert"));

        Menu(Player p) {
            Map<String, Integer> mine = grades.getOrDefault(p.getUniqueId(), Map.of());
            Material[] icons = {Material.LIME_DYE, Material.ORANGE_DYE, Material.RED_DYE};
            for (int i = 0; i < SONGS.length; i++) {
                Song song = SONGS[i];
                ItemStack it = new ItemStack(icons[song.level()]);
                ItemMeta meta = it.getItemMeta();
                meta.displayName(plain(LEVEL_COLORS[song.level()] + song.title()));
                List<Component> lore = new ArrayList<>();
                lore.add(plain("<gray>" + LEVELS[song.level()] + " - " + seconds(song) + " secondes"));
                Integer best = mine.get(song.id());
                lore.add(plain(best == null ? "<dark_gray>Pas encore de note" : "<white>Ta meilleure note : <gold>" + GRADES[best]));
                for (int g = 0; g < 3; g++)
                    lore.add(plain((best != null && best >= g ? "<dark_gray>" : "<white>") + GRADES[g] + " <gray>: " + reward(REWARDS[song.level()][g]) + (best != null && best >= g ? " (déjà reçu)" : "")));
                lore.add(plain("<yellow>Clic : jouer <gray>(touches 1, 2, 3, 4)"));
                meta.lore(lore);
                it.setItemMeta(meta);
                inv.setItem(song.level() * 9 + 2 + (i % 3) * 2, it);
            }
        }

        @Override public Inventory getInventory() { return inv; }
    }

    /** Ouvre le choix des musiques a ce joueur. */
    public void open(Player p) {
        if (plays.containsKey(p.getUniqueId())) return;
        p.openInventory(new Menu(p).inv);
    }

    @EventHandler
    public void onMenu(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != menu.inv || !(e.getWhoClicked() instanceof Player p)) return;
        int row = e.getSlot() / 9, col = e.getSlot() % 9;
        if (row > 2 || col < 2 || col > 6 || col % 2 != 0) return;
        p.closeInventory();
        start(p, SONGS[row * 3 + (col - 2) / 2]);
    }

    // ------------------------------------------------------------------ une partie

    private TextDisplay text(Play play, Location at, String mini, Color background, float sx, float sy) {
        TextDisplay d = at.getWorld().spawn(at, TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setVisibleByDefault(false);
            t.setBillboard(Display.Billboard.CENTER);
            t.setBrightness(new Display.Brightness(15, 15));
            t.setShadowed(false);
            t.setSeeThrough(false);
            t.setViewRange(1f);
            t.text(Msg.mm(mini));
            t.setBackgroundColor(background);
            // le fond d'un texte deborde d'un demi-pixel sur la droite : on le rattrape, a l'echelle de l'affichage
            t.setTransformation(new Transformation(new Vector3f(-0.0097f * sx, 0, 0), new Quaternionf(), new Vector3f(sx, sy, 1), new Quaternionf()));
        });
        play.player.showEntity(plugin, d);
        return d;
    }

    /** Lance une musique pour ce joueur : le tableau se pose devant lui, un decompte, puis les notes. */
    public void start(Player p, Song song) {
        stop(p, false);
        Play play = new Play(p, song);
        plays.put(p.getUniqueId(), play);
        if (play.held < 4) p.getInventory().setHeldItemSlot(8);      // les touches 1 a 4 doivent changer de case pour se voir
        // le tableau : un fond sombre, la ligne de frappe, le chiffre de chaque touche
        Location back = play.base.clone().add(play.start.getDirection().setY(0).normalize().multiply(0.15)).add(0, -0.45, 0);
        play.board.add(text(play, back, " ", Color.fromARGB(190, 16, 16, 28), 30f, 17f));
        for (int lane = 0; lane < 4; lane++) {
            Color c = LANES[lane];
            play.board.add(text(play, play.lane(lane, -0.08), " ", Color.fromARGB(110, c.getRed(), c.getGreen(), c.getBlue()), 6.2f, 0.9f));
            play.board.add(text(play, play.lane(lane, -0.42), "<white><bold>" + (lane + 1), Color.fromARGB(0, 0, 0, 0), 1.4f, 1.4f));
        }
        p.showBossBar(play.bar);
        Msg.send(p, "<gold>" + song.title() + " <gray>: appuie sur <white>1, 2, 3, 4 <gray>quand une note touche la ligne. <dark_gray>S'accroupir pour arrêter.");
    }

    /** Arrete la partie de ce joueur (sans note si elle n'est pas finie) : le tableau disparait, la main revient ou elle etait. */
    private void stop(Player p, boolean message) {
        Play play = plays.remove(p.getUniqueId());
        if (play == null) return;
        play.board.forEach(Entity::remove);
        play.falling.values().forEach(Entity::remove);
        p.hideBossBar(play.bar);
        if (p.isOnline()) {
            p.getInventory().setHeldItemSlot(play.held);
            if (message) Msg.send(p, "<gray>Musique arrêtée : rien n'est compté.");
        }
    }

    /** Chaque tick : le decompte, les notes qui apparaissent et tombent, la musique, les notes manquees, la fin. */
    private void tick() {
        for (Play play : new ArrayList<>(plays.values())) {
            Player p = play.player;
            if (!p.isOnline() || p.isDead() || !p.getWorld().equals(play.start.getWorld()) || p.getLocation().distanceSquared(play.start) > 36) { stop(p, true); continue; }
            Song song = play.song;
            int level = song.level(), beat = BEAT[level], fall = FALL[level], t = play.tick;
            if (t == -60 || t == -40 || t == -20) {
                p.showTitle(net.kyori.adventure.title.Title.title(Msg.mm("<white>" + (-t / 20)), Component.empty(),
                        net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(700), java.time.Duration.ofMillis(200))));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            }
            // les notes qui entrent : elles apparaissent en haut de leur colonne, puis glissent jusqu'a la ligne
            while (play.next < play.notes.size() && play.notes.get(play.next).tick() - fall <= t) {
                Note n = play.notes.get(play.next);
                Color c = LANES[n.lane()];
                TextDisplay d = text(play, play.lane(n.lane(), HEIGHT), " ", Color.fromARGB(255, c.getRed(), c.getGreen(), c.getBlue()), 5.4f, 0.75f);
                d.setTeleportDuration(Math.min(59, fall - 1));
                play.falling.put(play.next, d);
                final int lane = n.lane();
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (d.isValid()) d.teleport(play.lane(lane, 0)); }, 1L);
                play.next++;
            }
            if (t >= 0) {
                // la batterie et la basse
                if (t % beat == 0) {
                    int count = t / beat % 4;
                    p.playSound(p.getLocation(), count % 2 == 0 ? Sound.BLOCK_NOTE_BLOCK_BASEDRUM : Sound.BLOCK_NOTE_BLOCK_SNARE, 0.55f, 1f);
                    if (count == 0) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, pitch(SCALES[(int) (song.seed() % SCALES.length)][0] + (t / (beat * 8) % 4 == 3 ? 2 : 0)));
                } else if (level > 0 && t % (beat / 2) == 0) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.3f, 1.4f);
                // la melodie : chaque note sonne quand elle touche la ligne ; une note laissee passer est manquee
                for (int i = 0; i < play.notes.size(); i++) {
                    Note n = play.notes.get(i);
                    if (n.tick() > t) break;
                    if (n.tick() == t) p.playSound(p.getLocation(), song.lead(), 0.9f, pitch(n.pitch()));
                    if (!play.judged[i] && t - n.tick() > 6) { play.judged[i] = true; play.missed++; play.combo = 0; Entity e = play.falling.remove(i); if (e != null) e.remove(); }
                }
                play.bar.progress(Math.max(0f, Math.min(1f, t / (float) play.length)));
                if (t % 4 == 0) Msg.actionBar(p, "<white>" + percent(play) + " % <dark_gray>| <gold>série " + play.combo);
            }
            if (t >= play.length) { finish(play); continue; }
            play.tick++;
        }
    }

    private static int percent(Play play) {
        int done = play.perfect + play.good + play.ok + play.missed;
        return done == 0 ? 100 : (int) Math.round(100.0 * play.score / (done * 300.0));
    }

    /** Une touche (0 a 3) : la note la plus proche de la ligne sur cette colonne, si elle est a portee. */
    private void press(Play play, int lane) {
        Player p = play.player;
        // ce que le joueur voit a un demi-aller-retour de retard, et sa touche met autant a arriver
        int now = play.tick - Math.min(6, Math.round(p.getPing() / 50f));
        int found = -1, gap = 99;
        for (int i = 0; i < play.notes.size(); i++) {
            Note n = play.notes.get(i);
            if (n.tick() - now > 7) break;
            if (play.judged[i] || n.lane() != lane || Math.abs(n.tick() - now) > 6) continue;
            if (Math.abs(n.tick() - now) < gap) { gap = Math.abs(n.tick() - now); found = i; }
        }
        if (found < 0) return;
        play.judged[found] = true;
        Entity e = play.falling.remove(found);
        if (e != null) e.remove();
        String word;
        if (gap <= 2) { play.score += 300; play.perfect++; word = "<gold><bold>Parfait"; }
        else if (gap <= 4) { play.score += 200; play.good++; word = "<green>Bien"; }
        else { play.score += 100; play.ok++; word = "<gray>Juste"; }
        play.combo++;
        play.best = Math.max(play.best, play.combo);
        Msg.actionBar(p, word + " <dark_gray>| <white>" + percent(play) + " % <dark_gray>| <gold>série " + play.combo);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.8f);
        Location at = play.lane(lane, 0.1);
        p.spawnParticle(org.bukkit.Particle.DUST, at, 6, 0.3, 0.1, 0.05, 0, new org.bukkit.Particle.DustOptions(LANES[lane], 1.2f));
    }

    /** La musique est finie : la note, et ce qu'elle rapporte de plus que la meilleure note d'avant. */
    private void finish(Play play) {
        Player p = play.player;
        double share = play.score / (play.notes.size() * 300.0);
        int grade = share >= NEEDED[2] ? 2 : share >= NEEDED[1] ? 1 : share >= NEEDED[0] ? 0 : -1;
        Song song = play.song;
        stop(p, false);
        Msg.send(p, "<gold>" + song.title() + " <gray>: <white>" + Math.round(share * 100) + " % <gray>(" + play.perfect + " parfaits, " + play.good + " bien, " + play.ok + " justes, " + play.missed
                + " manquées ; plus longue série : " + play.best + ")");
        if (grade < 0) {
            p.showTitle(net.kyori.adventure.title.Title.title(Msg.mm("<red>Raté"), Msg.mm("<gray>Il faut " + Math.round(NEEDED[0] * 100) + " % pour un C")));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 1f, 0.6f);
            return;
        }
        p.showTitle(net.kyori.adventure.title.Title.title(Msg.mm("<gold><bold>" + GRADES[grade]), Msg.mm("<white>" + song.title())));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        Map<String, Integer> mine = grades.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        Integer before = mine.get(song.id());
        if (before != null && before >= grade) { Msg.send(p, "<gray>Tu avais déjà " + (before > grade ? "mieux" : "cette note") + " à cette musique : rien de plus à gagner pour l'instant."); return; }
        mine.put(song.id(), grade);
        int[] now = REWARDS[song.level()][grade], had = before == null ? new int[3] : REWARDS[song.level()][before];
        int[] gain = {Math.max(0, now[0] - had[0]), Math.max(0, now[1] - had[1]), Math.max(0, now[2] - had[2])};
        Material[] what = {Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND};
        for (int k = 0; k < 3; k++) if (gain[k] > 0) p.getInventory().addItem(new ItemStack(what[k], gain[k])).values().forEach(left -> p.getWorld().dropItem(p.getLocation(), left));
        Msg.send(p, "<green>Note " + GRADES[grade] + " : <white>" + (reward(gain).isEmpty() ? "rien de plus" : reward(gain)) + "<green>." + (grade < 2 ? " <gray>Une meilleure note rapporte la différence." : ""));
    }

    @EventHandler
    public void onKey(PlayerItemHeldEvent e) {
        Play play = plays.get(e.getPlayer().getUniqueId());
        if (play == null) return;
        e.setCancelled(true);                                    // la main ne bouge pas : la meme touche peut resservir
        if (e.getNewSlot() < 4 && play.tick >= -5) press(play, e.getNewSlot());
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (e.isSneaking() && plays.containsKey(e.getPlayer().getUniqueId())) stop(e.getPlayer(), true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stop(e.getPlayer(), false);
    }

    /** Une partie commence, ou le plugin s'arrete : tout repart de zero. */
    public void reset() {
        for (Play play : new ArrayList<>(plays.values())) stop(play.player, false);
        grades.clear();
    }

    /** Pour les essais : la partition d'une musique (instant, colonne), sans la jouer. */
    public static List<int[]> notesOf(Song song) {
        List<int[]> out = new ArrayList<>();
        for (Note n : chart(song)) out.add(new int[]{n.tick(), n.lane()});
        return out;
    }
}
