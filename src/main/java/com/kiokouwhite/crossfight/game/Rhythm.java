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
 * La forge d'Anna (le service "rythme", a Shiganshina) : on y forge ses materiaux en musique. Des notes tombent sur
 * quatre colonnes, et l'on frappe avec
 * les touches 1, 2, 3 et 4 du clavier quand elles atteignent la ligne du bas. Neuf musiques, trois par difficulte ; la
 * note obtenue (C, B ou A) donne des materiaux, une seule fois par musique : rejouer ne rapporte que ce qu'une meilleure
 * note ajoute.
 *
 * Des musiques importees d'osu!mania (tools/import_osu.py : la carte dans plugins/CrossfightUHC/musiques/, le son dans
 * le pack) prennent la place de celles du plugin, trois par difficulte au plus.
 *
 * Les musiques du plugin sont composees ici : une melodie tiree d'une graine (toujours la meme pour une musique),
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
    private static final int[] BEAT = {12, 10, 8}, BARS = {16, 20, 24}, FALL = {40, 32, 26};
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
        TextDisplay streak;                  // la serie en cours (« x12 »), a droite de la piste
        TextDisplay verdict;                 // « Parfait », « Bien »... en haut de la piste
        final TextDisplay[] beams = new TextDisplay[4];   // la lumiere qui monte d'un couloir quand on y frappe une note
        final int[] beamUntil = new int[4];
        int verdictUntil;
        final Custom custom;                 // une musique importee : son audio vient du pack, le plugin ne joue ni melodie ni batterie
        final boolean drawn;                 // avec le pack : le tableau et les notes sont des images ; sinon, des rectangles de couleur
        int tick = -70, next, score, combo, best, perfect, good, ok, missed;

        Play(Player player, Song song) {
            this.player = player;
            this.song = song;
            this.notes = Rhythm.this.notes(song);
            this.custom = customs.get(song.id());
            this.judged = new boolean[notes.size()];
            this.length = notes.get(notes.size() - 1).tick() + 30;
            this.held = player.getInventory().getHeldItemSlot();
            this.start = player.getLocation();
            Vector look = start.getDirection().setY(0);
            if (look.lengthSquared() < 0.01) look = new Vector(0, 0, 1);
            look.normalize();
            this.right = new Vector(-look.getZ(), 0, look.getX());
            this.base = player.getEyeLocation().add(look.multiply(7)).add(0, -0.4, 0);
            this.base.setYaw(0);                 // sans cela, les affichages gardent le regard du joueur et penchent avec lui
            this.base.setPitch(0);
            this.drawn = com.kiokouwhite.crossfight.util.Sounds.has("font.rythme") && com.kiokouwhite.crossfight.util.Sounds.hasPackLoaded(player);
            this.bar = BossBar.bossBar(Msg.mm(LEVEL_COLORS[song.level()] + song.title() + " <gray>- " + LEVELS[song.level()]), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
        }

        Location lane(int lane, double up) {
            return base.clone().add(right.clone().multiply((lane - 1.5) * PITCH)).add(0, up, 0);
        }
    }

    /** Un pixel des images du tableau, en blocs (elles sont montrees agrandies 1,4 fois : tools/build_rythme.py). */
    private static final double PIXEL = 0.035;
    private static final double HEIGHT = 160 * PIXEL;   // la hauteur de chute : 160 pixels du tableau, soit 5,6 blocs
    private static final double PITCH = 27 * PIXEL;     // d'un couloir au suivant

    private final CrossfightPlugin plugin;
    private final Map<UUID, Play> plays = new HashMap<>();
    /** La meilleure note de chaque joueur a chaque musique (0 : C, 1 : B, 2 : A), pour la partie en cours. */
    private final Map<UUID, Map<String, Integer>> grades = new HashMap<>();

    /** Une musique importee (tools/import_osu.py) : le son du pack qui la joue, et ses notes. */
    private record Custom(String sound, List<Note> notes) {}

    private final Map<String, Custom> customs = new HashMap<>();
    /** Les neuf musiques du menu, trois par difficulte : les musiques importees d'abord, celles du plugin pour completer. */
    private final List<Song> songs = new ArrayList<>();

    /**
     * Relit plugins/CrossfightUHC/musiques/ : un fichier par musique importee (titre, niveau, son, puis une note par
     * ligne : l'instant en millisecondes et la colonne, 0 a 3). Rend le nombre de musiques importees.
     */
    public int reload() {
        customs.clear();
        songs.clear();
        List<Song> imported = new ArrayList<>();
        java.io.File[] files = new java.io.File(plugin.getDataFolder(), "musiques").listFiles((dir, name) -> name.endsWith(".txt"));
        if (files != null) {
            java.util.Arrays.sort(files);
            for (java.io.File f : files) {
                try {
                    String title = null, sound = null;
                    int level = 0;
                    List<Note> notes = new ArrayList<>();
                    for (String line : java.nio.file.Files.readAllLines(f.toPath(), java.nio.charset.StandardCharsets.UTF_8)) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        if (line.startsWith("titre:")) title = line.substring(6).trim();
                        else if (line.startsWith("niveau:")) { String l = line.substring(7).trim().toLowerCase(java.util.Locale.ROOT); level = l.startsWith("d") ? 2 : l.startsWith("m") ? 1 : 0; }
                        else if (line.startsWith("son:")) sound = line.substring(4).trim();
                        else {
                            String[] parts = line.split("\\s+");
                            if (parts.length >= 2) notes.add(new Note((int) Math.round(Double.parseDouble(parts[0]) / 50.0), Math.max(0, Math.min(3, Integer.parseInt(parts[1]))), 12));
                        }
                    }
                    if (title == null || sound == null || notes.isEmpty()) { plugin.getLogger().warning("Musique ignoree (titre, son ou notes manquants) : " + f.getName()); continue; }
                    notes.sort(java.util.Comparator.comparingInt(Note::tick));
                    String id = f.getName().substring(0, f.getName().length() - 4).toLowerCase(java.util.Locale.ROOT);
                    customs.put(id, new Custom(sound, notes));
                    imported.add(new Song(id, title, level, 0L, null));
                } catch (Exception ex) { plugin.getLogger().warning("Musique illisible : " + f.getName() + " (" + ex.getMessage() + ")"); }
            }
        }
        for (int level = 0; level < 3; level++) {
            List<Song> row = new ArrayList<>();
            for (Song song : imported) if (song.level() == level && row.size() < 3) row.add(song);
            for (Song song : SONGS) if (song.level() == level && row.size() < 3) row.add(song);
            songs.addAll(row);
        }
        return imported.size();
    }

    /** Une musique par son nom (celles du menu, puis toutes celles du plugin), ou null. */
    public Song find(String id) {
        for (Song song : songs) if (song.id().equalsIgnoreCase(id)) return song;
        for (Song song : SONGS) if (song.id().equalsIgnoreCase(id)) return song;
        return null;
    }

    public List<String> ids() {
        List<String> out = new ArrayList<>();
        for (Song song : songs) out.add(song.id());
        return out;
    }

    private List<Note> notes(Song song) {
        Custom custom = customs.get(song.id());
        return custom != null ? custom.notes() : chart(song);
    }

    public Rhythm(CrossfightPlugin plugin) {
        this.plugin = plugin;
        int imported = reload();
        if (imported > 0) plugin.getLogger().info("Forge : " + imported + " musique(s) importee(s).");
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
    private int seconds(Song song) {
        Custom custom = customs.get(song.id());
        if (custom != null) return custom.notes().get(custom.notes().size() - 1).tick() / 20;
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
        final Inventory inv = Bukkit.createInventory(this, 27, Component.text("Anna - la forge"));

        Menu(Player p) {
            Map<String, Integer> mine = grades.getOrDefault(p.getUniqueId(), Map.of());
            Material[] icons = {Material.LIME_DYE, Material.ORANGE_DYE, Material.RED_DYE};
            for (int i = 0; i < songs.size(); i++) {
                Song song = songs.get(i);
                ItemStack it = new ItemStack(icons[song.level()]);
                ItemMeta meta = it.getItemMeta();
                meta.displayName(plain(LEVEL_COLORS[song.level()] + song.title()));
                List<Component> lore = new ArrayList<>();
                lore.add(plain("<gray>" + LEVELS[song.level()] + " - " + seconds(song) + " secondes"));
                Integer best = mine.get(song.id());
                lore.add(plain(best == null ? "<dark_gray>Pas encore de note" : "<white>Ta meilleure note : <gold>" + GRADES[best]));
                for (int g = 0; g < 3; g++)
                    lore.add(plain((best != null && best >= g ? "<dark_gray>" : "<white>") + GRADES[g] + " <gray>: " + reward(REWARDS[song.level()][g]) + (best != null && best >= g ? " (déjà reçu)" : "")));
                lore.add(plain("<yellow>Clic : forger <gray>(touches 1, 2, 3, 4)"));
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
        int index = row * 3 + (col - 2) / 2;
        if (index < songs.size()) start(p, songs.get(index));
    }

    // ------------------------------------------------------------------ une partie

    private TextDisplay text(Play play, Location at, String mini, Color background, float sx, float sy) {
        TextDisplay d = at.getWorld().spawn(at, TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setVisibleByDefault(false);
            t.setBillboard(Display.Billboard.VERTICAL);        // droit comme un mur : tourne vers le joueur, mais sans pencher quand il leve les yeux
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

    /** Une image du tableau (police "rythme" du pack), posee par son bas, au milieu. */
    private TextDisplay image(Play play, Location at, char glyph) {
        TextDisplay d = at.getWorld().spawn(at, TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setVisibleByDefault(false);
            t.setBillboard(Display.Billboard.VERTICAL);        // droit comme un mur : tourne vers le joueur, mais sans pencher quand il leve les yeux
            t.setBrightness(new Display.Brightness(15, 15));
            t.setShadowed(false);
            t.setViewRange(1f);
            t.text(Component.text(String.valueOf(glyph)).font(net.kyori.adventure.key.Key.key(com.kiokouwhite.crossfight.util.Sounds.NS, "rythme")).color(net.kyori.adventure.text.format.NamedTextColor.WHITE));
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setTransformation(new Transformation(new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(1.4f, 1.4f, 1), new Quaternionf()));
        });
        play.player.showEntity(plugin, d);
        return d;
    }

    /** Une note, en haut (`up` = HEIGHT) ou sur la ligne (`up` = 0) de son couloir : une image, centree sur ce point, ou un rectangle. */
    private Location noteAt(Play play, int lane, double up) {
        return play.lane(lane, play.drawn ? up - 8 * PIXEL : up);
    }

    /** Lance une musique pour ce joueur : le tableau se pose devant lui, un decompte, puis les notes. */
    public void start(Player p, Song song) {
        stop(p, false);
        Play play = new Play(p, song);
        plays.put(p.getUniqueId(), play);
        if (play.held < 4) p.getInventory().setHeldItemSlot(8);      // les touches 1 a 4 doivent changer de case pour se voir
        if (play.drawn) {
            // le tableau du pack : une seule image, dont la ligne de frappe est a 40 pixels du bas
            Location back = play.base.clone().add(play.start.getDirection().setY(0).normalize().multiply(0.12)).add(0, -40 * PIXEL, 0);
            play.board.add(image(play, back, '\uE400'));
        } else {
            // sans le pack : un fond sombre, la ligne de frappe, le chiffre de chaque touche
            Location back = play.base.clone().add(play.start.getDirection().setY(0).normalize().multiply(0.12)).add(0, -0.9, 0);
            play.board.add(text(play, back, " ", Color.fromARGB(190, 16, 16, 28), 33f, 27f));
            for (int lane = 0; lane < 4; lane++) {
                Color c = LANES[lane];
                play.board.add(text(play, play.lane(lane, -0.08), " ", Color.fromARGB(110, c.getRed(), c.getGreen(), c.getBlue()), 6.8f, 0.9f));
                play.board.add(text(play, play.lane(lane, -0.5), "<white><bold>" + (lane + 1), Color.fromARGB(0, 0, 0, 0), 1.6f, 1.6f));
            }
        }
        // l'appreciation, en haut de la piste, et un faisceau par couloir (eteint) : juste devant le tableau
        Vector toward = play.start.getDirection().setY(0).normalize();
        play.verdict = text(play, play.base.clone().add(toward.clone().multiply(-0.1)).add(0, HEIGHT - 1.3, 0), "", Color.fromARGB(0, 0, 0, 0), 2.2f, 2.2f);
        play.verdict.setShadowed(true);
        play.board.add(play.verdict);
        play.streak = text(play, play.base.clone().add(play.right.clone().multiply(2 * PITCH + 1.5)).add(0, HEIGHT * 0.42, 0), "", Color.fromARGB(0, 0, 0, 0), 3f, 3f);
        play.streak.setShadowed(true);
        play.board.add(play.streak);
        streak(play);
        for (int lane = 0; lane < 4; lane++) {
            Location at = play.lane(lane, 0).add(toward.clone().multiply(0.06));
            play.beams[lane] = play.drawn ? image(play, at, ' ') : text(play, at, "", Color.fromARGB(0, 0, 0, 0), 6.8f, (float) (HEIGHT / 0.25));
            play.beams[lane].text(Component.empty());
            play.board.add(play.beams[lane]);
        }
        p.showBossBar(play.bar);
        Msg.send(p, "<gold>" + song.title() + " <gray>: frappe au marteau avec <white>1, 2, 3, 4 <gray>quand un lingot touche la ligne. <dark_gray>S'accroupir pour arrêter.");
    }

    /** Arrete la partie de ce joueur (sans note si elle n'est pas finie) : le tableau disparait, la main revient ou elle etait. */
    private void stop(Player p, boolean message) {
        Play play = plays.remove(p.getUniqueId());
        if (play == null) return;
        play.board.forEach(Entity::remove);
        play.falling.values().forEach(Entity::remove);
        p.hideBossBar(play.bar);
        // dans la categorie ou elle a ete lancee : sans la nommer, l'arret vise une autre categorie et la musique continue
        if (play.custom != null) p.stopSound(play.custom.sound(), org.bukkit.SoundCategory.RECORDS);
        if (p.isOnline()) {
            p.getInventory().setHeldItemSlot(play.held);
            if (message) Msg.send(p, "<gray>Forge arrêtée : rien n'est compté.");
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
                TextDisplay d = play.drawn ? image(play, noteAt(play, n.lane(), HEIGHT), (char) (0xE401 + n.lane()))
                        : text(play, noteAt(play, n.lane(), HEIGHT), " ", Color.fromARGB(255, c.getRed(), c.getGreen(), c.getBlue()), 6f, 0.8f);
                d.setTeleportDuration(Math.min(59, fall - 1));
                play.falling.put(play.next, d);
                final int lane = n.lane();
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (d.isValid()) d.teleport(noteAt(play, lane, 0)); }, 1L);
                play.next++;
            }
            if (t == 0 && play.custom != null) p.playSound(p.getLocation(), play.custom.sound(), org.bukkit.SoundCategory.RECORDS, 1f, 1f);
            if (t >= 0) {
                // la batterie et la basse (une musique importee a son propre son)
                if (play.custom != null) { }
                else if (t % beat == 0) {
                    int count = t / beat % 4;
                    p.playSound(p.getLocation(), count % 2 == 0 ? Sound.BLOCK_NOTE_BLOCK_BASEDRUM : Sound.BLOCK_NOTE_BLOCK_SNARE, 0.55f, 1f);
                    if (count == 0) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, pitch(SCALES[(int) (song.seed() % SCALES.length)][0] + (t / (beat * 8) % 4 == 3 ? 2 : 0)));
                } else if (level > 0 && t % (beat / 2) == 0) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.3f, 1.4f);
                // la melodie : chaque note sonne quand elle touche la ligne ; une note laissee passer est manquee
                for (int i = 0; i < play.notes.size(); i++) {
                    Note n = play.notes.get(i);
                    if (n.tick() > t) break;
                    if (n.tick() == t && play.custom == null) p.playSound(p.getLocation(), song.lead(), 0.9f, pitch(n.pitch()));
                    if (!play.judged[i] && t - n.tick() > 6) { play.judged[i] = true; Entity e = play.falling.remove(i); if (e != null) e.remove(); miss(play); }
                }
                play.bar.progress(Math.max(0f, Math.min(1f, t / (float) play.length)));
                if (t % 4 == 0) Msg.actionBar(p, "<white>" + percent(play) + " % <dark_gray>| <gold>série " + play.combo);
            }
            // les effets s'eteignent
            if (play.verdictUntil != 0 && t >= play.verdictUntil) { play.verdict.text(Component.empty()); play.verdictUntil = 0; }
            for (int lane = 0; lane < 4; lane++) if (play.beamUntil[lane] != 0 && t >= play.beamUntil[lane]) {
                play.beams[lane].text(Component.empty());
                if (!play.drawn) play.beams[lane].setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                play.beamUntil[lane] = 0;
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
        // Un accord : les notes qui tombent au meme instant sur les autres colonnes comptent avec celle-ci. Quand deux
        // touches de la barre d'objets sont enfoncees dans le meme instant, le jeu n'en envoie qu'une au serveur : sans
        // cela, un accord ne pourrait jamais etre reussi.
        int at0 = play.notes.get(found).tick();
        List<Integer> struck = new ArrayList<>(List.of(found));
        for (int i = 0; i < play.notes.size(); i++) {
            Note n = play.notes.get(i);
            if (n.tick() - at0 > 1) break;
            if (i != found && !play.judged[i] && Math.abs(n.tick() - at0) <= 1) struck.add(i);
        }
        String word = "";
        for (int i : struck) {
            play.judged[i] = true;
            Entity e = play.falling.remove(i);
            if (e != null) e.remove();
            if (gap <= 2) { play.score += 300; play.perfect++; word = "<gold>Parfait"; }
            else if (gap <= 4) { play.score += 200; play.good++; word = "<green>Bien"; }
            else { play.score += 100; play.ok++; word = "<gray>Juste"; }
            play.combo++;
            burst(play, play.notes.get(i).lane());
        }
        play.best = Math.max(play.best, play.combo);
        Msg.actionBar(p, word + " <dark_gray>| <white>" + percent(play) + " % <dark_gray>| <gold>série " + play.combo);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1f, gap <= 2 ? 1.9f : 1.5f);             // le coup de marteau, bien net
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, gap <= 2 ? 2f : 1.6f);
        verdict(play, word);
        streak(play);
    }

    /** Un texte du tableau, dans la police du panneau de quete quand le joueur a le pack (tools/build_quete_panneau.py) ; sinon, celle du jeu. */
    private static Component lettered(Play play, String mini) {
        Component text = Msg.mm(mini);
        return play.drawn && com.kiokouwhite.crossfight.util.Sounds.has("font.quete_texte") ? text.font(net.kyori.adventure.key.Key.key(com.kiokouwhite.crossfight.util.Sounds.NS, "quete_texte")) : text;
    }

    /** La serie en cours, a droite de la piste : « x12 », doree a partir de dix, grise a zero. */
    private void streak(Play play) {
        if (play.streak == null || !play.streak.isValid()) return;
        play.streak.text(lettered(play, (play.combo >= 10 ? "<gold>" : play.combo > 0 ? "<white>" : "<dark_gray>") + "x" + play.combo + "\n<gray>série"));
    }

    /** Une note manquee : la serie retombe a zero ; si elle comptait (trois notes au moins), on l'entend se briser. */
    private void miss(Play play) {
        if (play.combo >= 3) {
            play.player.playSound(play.player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.8f);
            play.player.playSound(play.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
        }
        play.missed++;
        play.combo = 0;
        verdict(play, "<red>Raté");
        streak(play);
    }

    /** L'appreciation, ecrite en haut de la piste pendant trois quarts de seconde. */
    private void verdict(Play play, String mini) {
        if (play.verdict == null || !play.verdict.isValid()) return;
        play.verdict.text(lettered(play, mini));
        play.verdictUntil = play.tick + 15;
    }

    /**
     * Une note frappee : elle eclate (une etoile qui grossit et disparait, des etincelles), et son couloir s'illumine un
     * instant, comme si de la lumiere montait de la ligne.
     */
    private void burst(Play play, int lane) {
        Player p = play.player;
        Color c = LANES[lane];
        Location at = play.lane(lane, 0);
        p.spawnParticle(org.bukkit.Particle.DUST, at.clone().add(0, 0.1, 0), 10, 0.3, 0.15, 0.05, 0, new org.bukkit.Particle.DustOptions(c, 1.4f));
        p.spawnParticle(org.bukkit.Particle.END_ROD, at.clone().add(0, 0.1, 0), 2, 0.2, 0.1, 0.05, 0.03);
        Vector toward = play.start.getDirection().setY(0).normalize();
        Location front = at.clone().add(toward.clone().multiply(-0.08));
        TextDisplay star;
        if (play.drawn) {
            star = image(play, front, '\uE405');
            // l'etoile grossit autour de son centre : son image est posee par le bas, on la redescend d'une demi-hauteur
            star.setTransformation(new Transformation(new Vector3f(0, -0.4f, 0), new Quaternionf(), new Vector3f(1f, 1f, 1), new Quaternionf()));
        } else {
            star = text(play, front, " ", Color.fromARGB(230, 255, 255, 255), 6f, 0.8f);
        }
        final TextDisplay shown = star;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!shown.isValid()) return;
            shown.setInterpolationDelay(0);
            shown.setInterpolationDuration(4);
            if (play.drawn) shown.setTransformation(new Transformation(new Vector3f(0, -1.0f, 0), new Quaternionf(), new Vector3f(2.5f, 2.5f, 1), new Quaternionf()));
            else shown.setTransformation(new Transformation(new Vector3f(-0.0097f * 9f, -0.1f, 0), new Quaternionf(), new Vector3f(9f, 1.6f, 1), new Quaternionf()));
            shown.setTextOpacity((byte) 90);
        }, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, shown::remove, 6L);
        // le faisceau du couloir
        TextDisplay beam = play.beams[lane];
        if (beam != null && beam.isValid()) {
            if (play.drawn) beam.text(Component.text("\uE406").font(net.kyori.adventure.key.Key.key(com.kiokouwhite.crossfight.util.Sounds.NS, "rythme")).color(net.kyori.adventure.text.format.TextColor.color(c.getRed(), c.getGreen(), c.getBlue())));
            else { beam.text(Component.text(" ")); beam.setBackgroundColor(Color.fromARGB(70, c.getRed(), c.getGreen(), c.getBlue())); }
            play.beamUntil[lane] = play.tick + 5;
        }
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
        Msg.send(p, "<green>Note " + GRADES[grade] + " : tu as forgé <white>" + (reward(gain).isEmpty() ? "rien de plus" : reward(gain)) + "<green>." + (grade < 2 ? " <gray>Une meilleure note rapporte la différence." : ""));
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
