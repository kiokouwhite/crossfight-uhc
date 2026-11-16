package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.ArenaWorlds;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hanging;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Les lieux : de grandes constructions (des maps de la communaute, une ville, une academie...) posees a des endroits
 * tires au sort sur la map de jeu, avant la partie. Un lieu est toujours au centre ; l'arene de Ferox est ailleurs.
 *
 * Un lieu est un dossier de plugins/CrossfightUHC/structures/ : son monde d'origine (le dossier "monde" : la map
 * telle qu'on la telecharge, de n'importe quelle version, le serveur la met a jour en la lisant) et sa fiche
 * (structure.yml : la boite a prendre dans ce monde, la hauteur de son sol, son coeur, son auteur). Ces dossiers ne
 * sont pas dans le depot : ce sont les oeuvres de leurs auteurs.
 *
 * Les lieux s'adaptent a la bordure : a la creation d'une map, la place de chacun est comptee d'apres le rayon de la
 * bordure et le nombre de lieux. Un lieu plus grand que sa part est recadre autour de son coeur : petite bordure,
 * des morceaux de monde ; grande bordure, des lieux entiers.
 *
 * La pose copie le monde d'origine dans la map, chunk par chunk, sans figer le serveur : le terrain est degage
 * au-dessus du niveau du sol, les blocs copies tels quels (sans mise a jour : rien ne tombe ni ne se casse), avec ce
 * que contiennent les panneaux et les bannieres (les coffres arrivent vides) et le decor (cadres, tableaux,
 * porte-armures, affichages ; pas les animaux ni les villageois), puis le dessous est comble jusqu'au terrain.
 * Un lieu pose est indestructible, et son decor ne se pille pas (structures.protegees).
 * L'endroit, la decoupe et l'avancement sont retenus par monde dans config.yml : un lieu n'est pose qu'une fois par
 * map, et une pose interrompue par un arret du serveur reprend au demarrage.
 */
public final class Structures implements Listener {

    /** Chunks de la map traites par tranche. */
    private static final int BATCH = 2;

    /** Un lieu disponible : sa fiche (la boite, le sol et le coeur sont dans les coordonnees de son monde d'origine). */
    public record Sheet(String name, File folder, String world, int x1, int y1, int z1, int x2, int y2, int z2, int ground, int heartX, int heartZ,
                        boolean carve, String title, String author, String source) {
        public int sx() { return x2 - x1 + 1; }
        public int sy() { return y2 - y1 + 1; }
        public int sz() { return z2 - z1 + 1; }
    }

    /** Un lieu pose sur une map : son coin bas, sa taille (celle de sa decoupe). */
    public record Placed(String name, String world, int x, int y, int z, int sx, int sy, int sz) {
        boolean contains(Location l) {
            return l.getWorld().getName().equals(world) && l.getBlockX() >= x && l.getBlockX() < x + sx && l.getBlockZ() >= z && l.getBlockZ() < z + sz
                    && l.getBlockY() >= y && l.getBlockY() < y + sy;
        }
    }

    private final CrossfightPlugin plugin;
    private final List<Placed> placed = new ArrayList<>();
    /** Une pose est en cours : une seule a la fois. */
    private boolean working;
    /** Change quand on abandonne ce qui est en cours (une nouvelle map) : les poses lancees avant s'arretent. */
    private int run;

    public Structures(CrossfightPlugin plugin) {
        this.plugin = plugin;
        folder().mkdirs();
        reload();
    }

    private File folder() { return new File(plugin.getDataFolder(), "structures"); }

    private boolean enabled() { return plugin.getConfig().getBoolean("structures.enabled", true); }

    public boolean working() { return working; }

    /** Abandonne la pose en cours (la map change) : elle reprendra, ou une autre commencera, au prochain scatter. */
    public void abort() {
        run++;
        working = false;
        skipped.clear();
    }

    /** Le nom d'un lieu tel qu'on l'annonce : son titre, ou "beacon_academy" -> "Beacon academy". */
    public String title(String name) {
        Sheet s = sheet(name);
        if (s != null && !s.title().isEmpty()) return s.title();
        String t = name.replace('_', ' ');
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    /** Les lieux entierement poses sur cette map. */
    public List<Placed> finished(World w) {
        List<Placed> out = new ArrayList<>();
        for (Placed p : placed(w)) if (plugin.getConfig().getBoolean(key(w, p.name()) + ".finie", false)) out.add(p);
        return out;
    }

    // ------------------------------------------------------------------ les fiches

    /** Les lieux disponibles sur ce serveur (un dossier et sa fiche chacun). */
    public List<Sheet> sheets() {
        List<Sheet> out = new ArrayList<>();
        Set<String> names = new java.util.TreeSet<>();
        File[] dirs = folder().listFiles(File::isDirectory);
        if (dirs == null) return out;
        for (File d : dirs) {
            if (holds(d)) { names.add(d.getName()); continue; }
            File[] inner = d.listFiles(File::isDirectory);
            if (inner != null) for (File e : inner) if (holds(e)) names.add(e.getName());
        }
        for (String name : names) {
            Sheet s = sheet(name);
            if (s != null) out.add(s);
        }
        return out;
    }

    /**
     * Le dossier d'un lieu : structures/&lt;nom&gt;. Une archive decompressee "dans un dossier a son nom" laisse souvent
     * un dossier de trop (structures/jouki/jouki/...) : un lieu range un cran plus bas est trouve aussi.
     */
    public File home(String name) {
        File direct = new File(folder(), name);
        if (holds(direct)) return direct;
        File[] dirs = folder().listFiles(File::isDirectory);
        if (dirs != null) for (File d : dirs) {
            File inner = new File(d, name);
            if (holds(inner)) return inner;
        }
        return direct;
    }

    /** Vrai si ce dossier est celui d'un lieu : il a sa fiche, ou au moins son monde. */
    private static boolean holds(File dir) {
        return new File(dir, "structure.yml").isFile() || new File(dir, "monde/level.dat").isFile();
    }

    /** Pour un admin : pourquoi cette map n'a aucun lieu, et quoi faire (en MiniMessage). Null si elle en a. */
    public String advice(World w) {
        if (!placed(w).isEmpty()) return null;
        if (!enabled()) return "les lieux sont desactives (<white>structures.enabled<gray> dans config.yml).";
        int count = sheets().size();
        if (count == 0) return "aucun lieu n'est installe sur ce serveur. Ils ne sont pas dans le plugin : decompresse leurs archives dans "
                + "<white>plugins/CrossfightUHC/structures/<gray> (un dossier par lieu, avec <white>structure.yml<gray> et <white>monde/<gray>), puis <white>/cf structure disperser<gray>.";
        if (working) return "la pose des lieux commence : ils apparaissent sur la carte un par un.";
        return count + " lieu(x) installe(s), aucun pose sur cette map : <white>/cf structure disperser<gray> les pose (<white>/cf structure liste<gray> pour le detail).";
    }

    /**
     * Les fiches deja lues : {la fiche, la date de son fichier, l'heure ou on l'a regardee}. La carte demande le titre
     * de chaque lieu a chaque tick : le fichier n'est relu que s'il a change, et regarde toutes les deux secondes au plus.
     */
    private final Map<String, Object[]> read = new HashMap<>();

    public Sheet sheet(String name) {
        long now = System.currentTimeMillis();
        Object[] known = read.get(name);
        if (known != null && now - (long) known[2] < 2000) return (Sheet) known[0];
        File index = new File(home(name), "structure.yml");
        long stamp = name.matches("[a-z0-9_]+") && index.isFile() ? index.lastModified() : -1;
        if (known != null && (long) known[1] == stamp) { known[2] = now; return (Sheet) known[0]; }
        Sheet s = stamp < 0 ? null : load(name);
        read.put(name, new Object[]{s, stamp, now});
        return s;
    }

    /** Le niveau du sol d'un lieu pose, la ou l'on marche (le bas de sa boite, si sa fiche n'est plus la). */
    public int level(Placed p) {
        Sheet s = sheet(p.name());
        return s != null ? p.y() + (s.ground() - s.y1()) : p.y();
    }

    private Sheet load(String name) {
        File dir = home(name);
        File index = new File(dir, "structure.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(index);
        List<Integer> b = y.getIntegerList("boite");
        if (b.size() != 6) return null;
        int x1 = Math.min(b.get(0), b.get(3)), y1 = Math.min(b.get(1), b.get(4)), z1 = Math.min(b.get(2), b.get(5));
        int x2 = Math.max(b.get(0), b.get(3)), y2 = Math.max(b.get(1), b.get(4)), z2 = Math.max(b.get(2), b.get(5));
        List<Integer> heart = y.getIntegerList("coeur");
        return new Sheet(name, dir, sourceName(dir, y.getString("monde", "monde")), x1, y1, z1, x2, y2, z2, y.getInt("sol", y1),
                heart.size() == 2 ? heart.get(0) : (x1 + x2) / 2, heart.size() == 2 ? heart.get(1) : (z1 + z2) / 2,
                y.getString("sous-sol", "garde").equals("creuse"), y.getString("titre", ""), y.getString("auteur", ""), y.getString("source", ""));
    }

    /** Le nom, pour le serveur, du monde d'origine d'un lieu : son dossier, vu depuis celui des mondes du serveur. */
    private static String sourceName(File dir, String sub) {
        return Bukkit.getWorldContainer().getAbsoluteFile().toPath().normalize().relativize(new File(dir, sub).getAbsoluteFile().toPath().normalize()).toString().replace('\\', '/');
    }

    /**
     * Pourquoi ce dossier de monde ne doit pas etre charge, ou null s'il peut l'etre. Paper arrete le serveur plutot
     * que de lire un chunk enregistre par une version de Minecraft plus recente que la sienne : un monde venu d'une
     * version plus recente est refuse ici, avant. (tools/lieu.py sait le ramener a la version du serveur.)
     */
    @SuppressWarnings("deprecation")
    public String refusal(File worldDir) {
        int made = dataVersion(new File(worldDir, "level.dat")), ours = Bukkit.getUnsafe().getDataVersion();
        return made > ours ? "il vient d'une version de Minecraft plus recente que ce serveur (donnees " + made + " > " + ours
                + ") ; a preparer avec tools/lieu.py --serveur " + ours : null;
    }

    /** Le numero de version des donnees d'un monde, lu dans son level.dat (-1 si on ne le trouve pas). */
    private static int dataVersion(File levelDat) {
        final byte[] mark = {3, 0, 11, 'D', 'a', 't', 'a', 'V', 'e', 'r', 's', 'i', 'o', 'n'};
        try (var in = new java.util.zip.GZIPInputStream(new java.io.FileInputStream(levelDat))) {
            byte[] all = in.readAllBytes();
            int best = -1;
            search:
            for (int i = 0; i + mark.length + 4 <= all.length; i++) {
                for (int k = 0; k < mark.length; k++) if (all[i + k] != mark[k]) continue search;
                int at = i + mark.length;
                best = Math.max(best, ((all[at] & 255) << 24) | ((all[at + 1] & 255) << 16) | ((all[at + 2] & 255) << 8) | (all[at + 3] & 255));
            }
            return best;
        } catch (IOException | RuntimeException ex) {
            return -1;
        }
    }

    /** Pourquoi le monde d'origine de ce lieu ne peut pas servir (absent, ou trop recent pour le serveur), ou null. */
    public String unusable(Sheet s) {
        if (Bukkit.getWorld(s.world()) != null) return null;
        File dir = new File(Bukkit.getWorldContainer(), s.world());
        return new File(dir, "level.dat").isFile() ? refusal(dir) : "il est introuvable (" + s.world() + ")";
    }

    /** Charge le monde d'origine d'un lieu (ou le rend, s'il l'est deja) : rien n'y vit, rien n'y pousse. */
    public World source(Sheet s) {
        World w = Bukkit.getWorld(s.world());
        if (w != null) return w;
        String no = unusable(s);
        if (no != null) { plugin.getLogger().warning("Lieu " + s.name() + " : son monde n'est pas charge, " + no + "."); return null; }
        w = new WorldCreator(s.world()).generator(new ArenaWorlds.VoidGenerator()).keepSpawnLoaded(net.kyori.adventure.util.TriState.FALSE).createWorld();
        if (w == null) return null;
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.DO_FIRE_TICK, false);
        w.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        w.setGameRule(GameRule.MOB_GRIEFING, false);
        return w;
    }

    /** Ecrit (ou reecrit) la fiche d'un lieu dont le monde est deja dans son dossier. */
    public boolean define(String name, int x1, int y1, int z1, int x2, int y2, int z2, int ground, int heartX, int heartZ, boolean carve, String title) {
        File dir = home(name);
        if (!name.matches("[a-z0-9_]+") || !new File(dir, "monde/level.dat").isFile()) return false;
        File index = new File(dir, "structure.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(index);
        y.set("boite", List.of(x1, y1, z1, x2, y2, z2));
        y.set("sol", ground);
        y.set("coeur", List.of(heartX, heartZ));
        y.set("sous-sol", carve ? "creuse" : "garde");
        if (!title.isEmpty()) y.set("titre", title);
        try { y.save(index); } catch (IOException ex) { plugin.getLogger().warning("Fiche de " + name + " non ecrite : " + ex); return false; }
        read.remove(name);
        return true;
    }

    /** Les lieux poses sur les maps (lus dans config.yml). */
    private void reload() {
        placed.clear();
        var worlds = plugin.getConfig().getConfigurationSection("structures.posees");
        if (worlds == null) return;
        for (String world : worlds.getKeys(false)) {
            var names = worlds.getConfigurationSection(world);
            if (names == null) continue;
            for (String name : names.getKeys(false)) {
                List<Integer> v = names.getIntegerList(name + ".boite");
                if (v.size() == 6) placed.add(new Placed(name, world, v.get(0), v.get(1), v.get(2), v.get(3), v.get(4), v.get(5)));
            }
        }
    }

    public List<Placed> placed(World w) {
        List<Placed> out = new ArrayList<>();
        for (Placed p : placed) if (p.world().equals(w.getName())) out.add(p);
        return out;
    }

    private Placed find(World w, String name) {
        for (Placed p : placed) if (p.world().equals(w.getName()) && p.name().equals(name)) return p;
        return null;
    }

    private String key(World w, String name) { return "structures.posees." + w.getName() + "." + name; }

    // ------------------------------------------------------------------ la part de chaque lieu

    /**
     * Le plus grand cote qu'un lieu peut occuper sur cette map : structures.taille-max si elle est reglee, sinon une
     * part comptee d'apres la bordure. Les lieux se partagent trois dixiemes de la surface de la map (au-dela, ils
     * ne trouveraient plus tous une place sans se toucher), a parts egales ; un multiple de 16, 64 au moins.
     */
    public int share(World w) {
        int fixed = plugin.getConfig().getInt("structures.taille-max", 0);
        if (fixed > 0) return Math.max(32, fixed);
        int border = plugin.game().rules().value("bordure"), margin = plugin.getConfig().getInt("structures.marge", 60);
        int count = Math.max(1, sheets().size());
        double side = (2.0 * border - 2 * margin) * Math.sqrt(0.30 / count);
        return Math.max(64, (int) side / 16 * 16);
    }

    /** La decoupe d'un lieu : le rectangle de son monde d'origine qui sera pose, de cote {@code max} au plus, autour de son coeur. */
    private static int[] cut(Sheet s, int max) {
        int wide = Math.min(s.sx(), max), deep = Math.min(s.sz(), max);
        int x = Math.max(s.x1(), Math.min(s.x2() - wide + 1, s.heartX() - wide / 2));
        int z = Math.max(s.z1(), Math.min(s.z2() - deep + 1, s.heartZ() - deep / 2));
        return new int[]{x, z, wide, deep};
    }

    // ------------------------------------------------------------------ la pose

    /**
     * Pose un lieu, recadre a {@code max} blocs de cote au plus, son coin (x, z) aligne sur les chunks, son niveau du
     * sol a la hauteur mediane du terrain. Reprend la ou elle s'etait arretee si la pose avait ete interrompue.
     * {@code done} est appele a la fin. Rend faux si la pose n'a pas pu commencer ({@code done} n'est alors pas appele).
     */
    public boolean place(CommandSender to, Sheet s, World w, int atX, int atZ, int max, Runnable done) {
        if (working) { Msg.send(to, "<red>Une pose est deja en cours."); return false; }
        final World from = source(s);
        if (from == null) {
            String no = unusable(s);
            Msg.send(to, "<red>Le monde de " + s.name() + " n'a pas pu etre charge" + (no != null ? " : " + no : "") + ".");
            return false;
        }
        working = true;
        final int mine = run;
        final int x0 = Math.floorDiv(atX, 16) * 16, z0 = Math.floorDiv(atZ, 16) * 16;
        Placed before = find(w, s.name());
        List<Integer> saved = plugin.getConfig().getIntegerList(key(w, s.name()) + ".decoupe");
        if (before != null && before.x() == x0 && before.z() == z0 && saved.size() == 2) {
            paste(to, s, from, w, before, saved.get(0), saved.get(1), plugin.getConfig().getInt(key(w, s.name()) + ".avancement", 0), done);
            return true;
        }
        final int[] cut = cut(s, max);
        // Le niveau du terrain : la mediane de vingt-cinq hauteurs prises sous le lieu, chunks charges sans attendre.
        List<int[]> points = new ArrayList<>();
        for (int i = 0; i < 5; i++) for (int j = 0; j < 5; j++) points.add(new int[]{x0 + (int) ((i + 0.5) * cut[2] / 5.0), z0 + (int) ((j + 0.5) * cut[3] / 5.0)});
        List<CompletableFuture<?>> loads = new ArrayList<>();
        for (int[] p : points) loads.add(w.getChunkAtAsync(p[0] >> 4, p[1] >> 4));
        CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).whenComplete((v, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (mine != run) return;
            List<Integer> heights = new ArrayList<>();
            for (int[] p : points) heights.add(w.getHighestBlockYAt(p[0], p[1], HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1);
            Collections.sort(heights);
            int under = s.ground() - s.y1(), over = s.y2() - s.ground() + 1;
            int level = Math.max(w.getMinHeight() + under + 2, Math.min(w.getMaxHeight() - over - 2, heights.get(heights.size() / 2)));
            Placed box = new Placed(s.name(), w.getName(), x0, level - under, z0, cut[2], s.sy(), cut[3]);
            placed.removeIf(p -> p.world().equals(w.getName()) && p.name().equals(s.name()));
            placed.add(box);
            plugin.getConfig().set(key(w, s.name()) + ".boite", List.of(box.x(), box.y(), box.z(), box.sx(), box.sy(), box.sz()));
            plugin.getConfig().set(key(w, s.name()) + ".decoupe", List.of(cut[0], cut[1]));
            plugin.getConfig().set(key(w, s.name()) + ".avancement", 0);
            // Le contour : une tache irreguliere pour un lieu recadre, a peine arrondie pour un lieu pose en entier.
            int rag = !plugin.getConfig().getBoolean("structures.contour", true) ? 0 : cut[2] < s.sx() || cut[3] < s.sz() ? 2 : 3;
            plugin.getConfig().set(key(w, s.name()) + ".contour", rag);
            ragged.put(w.getName() + "/" + s.name(), rag);
            plugin.getConfig().set(key(w, s.name()) + ".finie", false);
            plugin.saveConfig();
            paste(to, s, from, w, box, cut[0], cut[1], 0, done);
        }));
        return true;
    }

    /**
     * Copie le lieu dans la map, chunk par chunk, a partir du {@code start}-ieme. ({@code srcX}, {@code srcZ}) est le
     * coin, dans le monde d'origine, de la decoupe posee.
     */
    private void paste(CommandSender to, Sheet s, World from, World w, Placed box, int srcX, int srcZ, int start, Runnable done) {
        final int level = box.y() + (s.ground() - s.y1());
        final int cx0 = box.x() >> 4, cz0 = box.z() >> 4, nx = ((box.x() + box.sx() - 1) >> 4) - cx0 + 1, nz = ((box.z() + box.sz() - 1) >> 4) - cz0 + 1;
        final int mine = run;
        final boolean whole = box.sx() == s.sx() && box.sz() == s.sz();
        Msg.send(to, "<yellow>Pose de <white>" + s.name() + "</white> en " + box.x() + " " + level + " " + box.z() + " : " + box.sx() + " x " + box.sz() + " blocs"
                + (whole ? "" : " (recadre, sur " + s.sx() + " x " + s.sz() + ")") + ", " + (nx * nz) + " chunks...");
        new Object() {
            int column = start, waited = 0;
            /** Les chunks du monde d'origine gardes charges (avec leurs entites) tant qu'on en a besoin. */
            final Map<Long, Chunk> held = new HashMap<>();
            /** Les blocs que le lieu pose sous le niveau du sol, dans le chunk en cours : le comblement n'y touche pas. */
            final Set<Long> own = new HashSet<>();

            void next() {
                if (!plugin.isEnabled() || mine != run) { release(Integer.MAX_VALUE); return; }
                if (column >= nx * nz) { finish(); return; }
                final int first = column, last = Math.min(nx * nz, column + BATCH);
                List<CompletableFuture<Chunk>> sources = new ArrayList<>();
                List<CompletableFuture<?>> loads = new ArrayList<>();
                for (int c = first; c < last; c++) {
                    int tx = (cx0 + c / nz) << 4, tz = (cz0 + c % nz) << 4;
                    loads.add(w.getChunkAtAsync(tx >> 4, tz >> 4));
                    int ax = srcX + Math.max(tx, box.x()) - box.x(), bx = srcX + Math.min(tx + 15, box.x() + box.sx() - 1) - box.x();
                    int az = srcZ + Math.max(tz, box.z()) - box.z(), bz = srcZ + Math.min(tz + 15, box.z() + box.sz() - 1) - box.z();
                    for (int sx = ax >> 4; sx <= bx >> 4; sx++) for (int sz = az >> 4; sz <= bz >> 4; sz++) {
                        CompletableFuture<Chunk> f = from.getChunkAtAsync(sx, sz);
                        sources.add(f);
                        loads.add(f);
                    }
                }
                CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).whenComplete((v, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!plugin.isEnabled() || mine != run) { release(Integer.MAX_VALUE); return; }
                    // Les chunks d'origine restent charges, et l'on attend leurs entites (le decor) : deux secondes au plus.
                    boolean ready = true;
                    for (CompletableFuture<Chunk> f : sources) {
                        Chunk c = f.getNow(null);
                        if (c == null) continue;
                        if (held.putIfAbsent(Chunk.getChunkKey(c.getX(), c.getZ()), c) == null) c.addPluginChunkTicket(plugin);
                        if (!c.isEntitiesLoaded()) ready = false;
                    }
                    if (!ready && waited++ < 20) { Bukkit.getScheduler().runTaskLater(plugin, this::next, 2L); return; }
                    waited = 0;
                    Map<Long, ChunkSnapshot> snapshots = new HashMap<>();
                    for (int c = first; c < last; c++) chunk(cx0 + c / nz, cz0 + c % nz, snapshots);
                    column = last;
                    if (column % 32 < BATCH || column == nx * nz) { plugin.getConfig().set(key(w, s.name()) + ".avancement", column); plugin.saveConfig(); }
                    release((srcX + (((cx0 + column / nz) << 4) - box.x()) >> 4) - 2);
                    Bukkit.getScheduler().runTaskLater(plugin, this::next, 1L);
                }));
            }

            /** Relache les chunks d'origine dont on n'a plus besoin (ceux a l'ouest de {@code before}). */
            void release(int before) {
                held.values().removeIf(c -> {
                    if (c.getX() >= before) return false;
                    c.removePluginChunkTicket(plugin);
                    return true;
                });
            }

            /** Un chunk de la map : la part du lieu qui y tombe. */
            void chunk(int tcx, int tcz, Map<Long, ChunkSnapshot> snapshots) {
                final int xa = Math.max(tcx << 4, box.x()), xb = Math.min((tcx << 4) + 15, box.x() + box.sx() - 1);
                final int za = Math.max(tcz << 4, box.z()), zb = Math.min((tcz << 4) + 15, box.z() + box.sz() - 1);
                final int dx = box.x() - srcX, dy = box.y() - s.y1(), dz = box.z() - srcZ;
                // 1. Le decor d'une pose precedente au meme endroit (une pose interrompue, puis reprise) est retire ; le
                // terrain est degage au-dessus du niveau du sol : buttes, arbres.
                for (Entity e : w.getNearbyEntities(new BoundingBox(xa, box.y(), za, xb + 1, box.y() + box.sy(), zb + 1))) if (decor(e)) e.remove();
                for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) {
                    if (!within(w, box, x, z)) continue;
                    int top = w.getHighestBlockYAt(x, z);
                    for (int y = level; y <= top; y++) { Block b = w.getBlockAt(x, y, z); if (!b.getType().isAir()) b.setType(Material.AIR, false); }
                }
                // 2. Les blocs, copies tels quels, sans mise a jour. Ce que le lieu laisse vide ne remplace rien, sauf
                // dans ses sous-sols quand il les creuse.
                own.clear();
                // Par colonne : la hauteur du plus bas bloc pose, si c'est du sol (terre, pierre, sable...) pose au-dessus
                // du niveau du lieu. Certaines maps sont des coquilles : un sol mince, du vide dessous.
                final int[][] shell = new int[16][16];
                for (int[] row : shell) java.util.Arrays.fill(row, Integer.MIN_VALUE);
                final int minY = Math.max(s.y1(), from.getMinHeight()), maxY = Math.min(s.y2(), from.getMaxHeight() - 1);
                for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) {
                    if (!within(w, box, x, z)) continue;
                    final int sx = x - dx, sz = z - dz;
                    boolean first = true;
                    ChunkSnapshot snap = snapshots.computeIfAbsent(Chunk.getChunkKey(sx >> 4, sz >> 4), k -> from.getChunkAt(sx >> 4, sz >> 4).getChunkSnapshot(false, false, false, false));
                    for (int y = minY; y <= maxY; y++) {
                        // Une tranche de seize blocs entierement vide est sautee d'un coup.
                        if ((y & 15) == 0 && y + 15 <= maxY && snap.isSectionEmpty((y - from.getMinHeight()) >> 4) && !(s.carve() && y + dy < level)) { y += 15; continue; }
                        BlockData data = snap.getBlockData(sx & 15, y, sz & 15);
                        int ty = y + dy;
                        if (ty <= w.getMinHeight() || ty >= w.getMaxHeight()) continue;
                        if (data.getMaterial().isAir()) {
                            if (s.carve() && ty < level) { Block b = w.getBlockAt(x, ty, z); if (!b.getType().isAir()) b.setType(Material.AIR, false); }
                            continue;
                        }
                        w.getBlockAt(x, ty, z).setBlockData(data, false);
                        if (first) { first = false; if (ty >= level && soil(data.getMaterial())) shell[x & 15][z & 15] = ty; }
                        if (ty < level) own.add(pack(x, ty, z));
                    }
                }
                // 3. Ce que contiennent les blocs (panneaux, bannieres, tetes...) ; les coffres arrivent vides. Puis le decor.
                Set<Long> seen = new HashSet<>();
                for (int sx = (xa - dx) >> 4; sx <= (xb - dx) >> 4; sx++) for (int sz = (za - dz) >> 4; sz <= (zb - dz) >> 4; sz++) {
                    if (!seen.add(Chunk.getChunkKey(sx, sz))) continue;
                    Chunk c = from.getChunkAt(sx, sz);
                    for (BlockState st : c.getTileEntities(b -> b.getX() + dx >= xa && b.getX() + dx <= xb && b.getZ() + dz >= za && b.getZ() + dz <= zb
                            && b.getY() >= minY && b.getY() <= maxY && within(w, box, b.getX() + dx, b.getZ() + dz), false)) {
                        try {
                            BlockState copy = st.copy(new Location(w, st.getX() + dx, st.getY() + dy, st.getZ() + dz));
                            if (copy instanceof Container box2) box2.getSnapshotInventory().clear();
                            // Un bloc de commande arrive sans sa commande : celles d'une map n'ont rien a faire dans une partie.
                            if (copy instanceof org.bukkit.block.CommandBlock order) order.setCommand("");
                            copy.update(true, false);
                        } catch (RuntimeException ex) {
                            plugin.getLogger().warning("Lieu " + s.name() + " : bloc non copie en " + st.getX() + " " + st.getY() + " " + st.getZ() + " : " + ex);
                        }
                    }
                    for (Entity e : c.getEntities()) {
                        Location l = e.getLocation();
                        if (!decor(e) || l.getBlockX() + dx < xa || l.getBlockX() + dx > xb || l.getBlockZ() + dz < za || l.getBlockZ() + dz > zb
                                || l.getBlockY() < minY || l.getBlockY() > maxY || !within(w, box, l.getBlockX() + dx, l.getBlockZ() + dz)) continue;
                        try {
                            e.copy(new Location(w, l.getX() + dx, l.getY() + dy, l.getZ() + dz, l.getYaw(), l.getPitch()));
                        } catch (RuntimeException ex) {
                            plugin.getLogger().warning("Lieu " + s.name() + " : decor non copie (" + e.getType() + ") : " + ex);
                        }
                    }
                }
                // 4. Le dessous est comble jusqu'au terrain. On descend depuis le niveau du sol : ce que le lieu a pose
                // reste tel quel, les vides sont remplis (de l'herbe a l'air libre, de la terre, puis de la pierre) ;
                // une fois sous le lieu, on s'arrete au premier bloc du terrain.
                int under = s.carve() ? box.y() - 1 : level - 1;
                for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) {
                    if (!within(w, box, x, z)) continue;
                    int filled = 0;
                    // Sous un sol pose en hauteur, on comble depuis lui : le lieu ne flotte pas au-dessus du terrain.
                    int from = shell[x & 15][z & 15] != Integer.MIN_VALUE ? shell[x & 15][z & 15] - 1 : under;
                    for (int y = from; y > w.getMinHeight() && filled < 96; y--) {
                        Block b = w.getBlockAt(x, y, z);
                        if (own.contains(pack(x, y, z))) continue;
                        if (!fillable(b.getType())) {
                            if (y >= box.y()) continue;
                            break;
                        }
                        boolean open = filled == 0 && from == under && w.getBlockAt(x, y + 1, z).getType().isAir();
                        b.setType(open ? Material.GRASS_BLOCK : filled < 3 ? Material.DIRT : Material.STONE, false);
                        filled++;
                    }
                }
            }

            void finish() {
                release(Integer.MAX_VALUE);
                plugin.getConfig().set(key(w, s.name()) + ".finie", true);
                plugin.saveConfig();
                working = false;
                // Le monde d'origine n'a plus a rester en memoire (sauf si quelqu'un le visite).
                if (from.getPlayers().isEmpty()) Bukkit.unloadWorld(from, true);
                Msg.send(to, "<green>Lieu <white>" + s.name() + "</white> pose en <white>" + (box.x() + box.sx() / 2) + " " + level + " " + (box.z() + box.sz() / 2) + "</white>.");
                tear(w, box);
                if (done != null) done.run();
            }
        }.next();
    }

    /** Le decor qu'on copie : cadres, tableaux, porte-armures, affichages. Pas d'animaux ni de villageois. */
    private static boolean decor(Entity e) {
        return e instanceof Hanging || e instanceof ArmorStand || e instanceof Display;
    }

    /**
     * Le monde a l'air dechire : autour d'un lieu pose, le sol se fend. Une faille irreguliere (large de 2 a 9 blocs,
     * profonde de 12 a 40) court le long de son bord, comme si ce bout de monde avait ete arrache d'ailleurs et
     * enfonce ici de force ; elle s'interrompt par endroits, pour qu'on puisse passer, et des eclats de terrain y
     * restent suspendus. Elle ne touche ni aux autres lieux ni a l'arene (structures.dechirure).
     */
    private void tear(World w, Placed box) {
        tear(w, box, true);
    }

    /** Ce qui luit au fond des failles : une lumiere par lieu, celle du monde d'ou il vient. */
    private static final Material[] GLOWS = {Material.PEARLESCENT_FROGLIGHT, Material.SEA_LANTERN, Material.VERDANT_FROGLIGHT, Material.OCHRE_FROGLIGHT, Material.SHROOMLIGHT};

    private static Material glow(Placed box) {
        return GLOWS[Math.floorMod(box.name().hashCode(), GLOWS.length)];
    }

    /**
     * Par lieu pose ("monde/nom"), la forme de son contour, decidee a sa pose (structures.contour) : 0, le rectangle
     * de sa boite (les lieux d'avant) ; 1, le rectangle aux coins arrondis et au bord ondule (premiere version) ;
     * 2, une tache arrondie et irreguliere, pour un lieu recadre ; 3, la meme en plus sage, pour un lieu pose en entier.
     */
    private final Map<String, Integer> ragged = new HashMap<>();

    private int shape(World w, Placed box) {
        return ragged.computeIfAbsent(w.getName() + "/" + box.name(), k -> {
            String path = key(w, box.name()) + ".contour";
            return plugin.getConfig().isBoolean(path) ? (plugin.getConfig().getBoolean(path) ? 1 : 0) : plugin.getConfig().getInt(path, 0);
        });
    }

    /**
     * Ou est cette colonne par rapport au contour d'un lieu : positif dedans (en blocs depuis le bord), negatif ou nul
     * dehors. Un lieu n'est pas pose en rectangle : son contour est une tache arrondie inscrite dans sa boite, dont le
     * rayon change sans cesse le long du tour (de grandes ondulations, et de plus petites par-dessus) : un bout de
     * monde arrache, pas decoupe a la regle. Un lieu pose en entier garde presque tout : une boite aux coins tres
     * arrondis, au bord a peine rentre.
     */
    private double edge(World w, Placed box, int x, int z) {
        int kind = shape(w, box);
        int ax = Math.min(x - box.x(), box.x() + box.sx() - 1 - x), az = Math.min(z - box.z(), box.z() + box.sz() - 1 - z);
        double in = Math.min(ax, az) + 1;
        double seed = (w.getSeed() ^ box.name().hashCode()) % 1000 / 7.0;
        if (kind == 0) return in;
        if (kind == 1) {
            final double round = 56;
            if (ax < round && az < round && ax >= 0 && az >= 0) in = Math.min(in, round - Math.hypot(round - ax, round - az));
            double wave = 12 + 7 * Math.sin(x * 0.047 + seed) + 5 * Math.sin(z * 0.039 + seed * 1.7) + 3 * Math.sin((x + z) * 0.11 + seed * 3);
            return in - Math.max(1, wave);
        }
        // La tache : la distance au centre, mesuree dans la boite (1 a son bord), comparee au rayon du contour dans
        // cette direction. La puissance arrondit plus ou moins les coins : 2,4 fait presque une ellipse, 3,2 un rectangle tres arrondi.
        double a = box.sx() / 2.0, b = box.sz() / 2.0, dx = x + 0.5 - (box.x() + a), dz = z + 0.5 - (box.z() + b);
        double power = kind == 2 ? 2.4 : 3.2;
        double rho = Math.pow(Math.pow(Math.abs(dx) / a, power) + Math.pow(Math.abs(dz) / b, power), 1 / power);
        double angle = Math.atan2(dz, dx);
        double wave = 0.45 * Math.sin(2 * angle + seed) + 0.3 * Math.sin(3 * angle + seed * 1.7) + 0.2 * Math.sin(5 * angle + seed * 2.9)
                + 0.12 * Math.sin(9 * angle + seed * 4.3) + 0.08 * Math.sin(17 * angle + seed * 5.1);
        // Le rayon : de 0,66 a 1 pour un lieu recadre, de 0,88 a 1 pour un lieu entier.
        double radius = kind == 2 ? 0.83 + 0.17 * Math.max(-1, Math.min(1, wave)) : 0.94 + 0.06 * Math.max(-1, Math.min(1, wave));
        double length = Math.hypot(dx, dz);
        return rho < 1e-6 ? Math.min(a, b) : (radius - rho) * length / rho;
    }

    /** Vrai si cette colonne de la boite fait partie du lieu (dans son contour). */
    private boolean within(World w, Placed box, int x, int z) {
        return shape(w, box) == 0 || edge(w, box, x, z) > 0;
    }

    /**
     * La faille d'un lieu dans cette colonne : ou l'on est dans sa largeur, de 0 (son milieu) a 1 (son bord), ou -1 si
     * elle n'y passe pas. Elle longe le contour du lieu, large de 9 a 18 blocs, et s'interrompt par endroits.
     */
    private double rift(World w, Placed box, int x, int z) {
        if (x < box.x() - REACH || z < box.z() - REACH || x >= box.x() + box.sx() + REACH || z >= box.z() + box.sz() + REACH) return -1;
        double e = edge(w, box, x, z);
        if (e > 0) return -1;
        double d = 1 - e;
        if (d > REACH - 1) return -1;
        double seed = (w.getSeed() ^ box.name().hashCode()) % 1000 / 7.0;
        double t = x * 0.9 + z * 1.1;
        // Des passages restent : la faille s'efface sur un quart du tour.
        if (Math.sin(t * 0.021 + seed) + 0.5 * Math.sin(t * 0.057 + seed * 2) < -0.55) return -1;
        double width = 9 + 9 * (0.5 + 0.5 * Math.sin(t * 0.07 + seed * 3));
        if (d > width) return -1;
        Location arena = plugin.ferox().centre();
        if (Math.hypot(x - arena.getX(), z - arena.getZ()) < 110) return -1;
        return Math.min(1, Math.abs(d - width / 2) / (width / 2));
    }

    /** Jusqu'ou la faille s'ecarte du bord d'un lieu, en blocs. */
    private static final int REACH = 20;
    private static final Material[] GLASS = {Material.MAGENTA_STAINED_GLASS, Material.PURPLE_STAINED_GLASS, Material.BLUE_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS,
            Material.CYAN_STAINED_GLASS, Material.LIME_STAINED_GLASS, Material.YELLOW_STAINED_GLASS, Material.ORANGE_STAINED_GLASS, Material.RED_STAINED_GLASS,
            Material.PINK_STAINED_GLASS, Material.WHITE_STAINED_GLASS, Material.GREEN_STAINED_GLASS};
    private static final Material[] CRUST = {Material.OBSIDIAN, Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.BLACKSTONE, Material.CRYING_OBSIDIAN};
    private static final Set<Material> GLASS_SET = Set.of(GLASS);

    /**
     * Fend le monde autour d'un lieu (structures.dechirure) : le lieu a ete arrache a son monde et enfonce dans celui-ci,
     * et par la dechirure on voit le coeur du monde. Une large faille au bord d'obsidienne ; dedans, un lit de verre de
     * toutes les couleurs, en creux, eclaire par-dessous (structures.lueur) ; par endroits, au milieu, le verre manque
     * et c'est le vide, un puits de cinquante blocs. {@code fresh} faux : le lieu a deja une faille d'avant (plus
     * etroite, creusee profond) ; la nouvelle est batie par-dessus, d'apres la hauteur du terrain alentour.
     */
    private void tear(World w, Placed box, boolean fresh) {
        tear(w, box, fresh, null);
    }

    private void tear(World w, Placed box, boolean fresh, Runnable after) {
        if (!plugin.getConfig().getBoolean("structures.dechirure", true)) { if (after != null) after.run(); return; }
        final boolean shine = plugin.getConfig().getBoolean("structures.lueur", true);
        final Material light = glow(box);
        final double seed = (w.getSeed() ^ box.name().hashCode()) % 1000 / 7.0;
        final List<int[]> columns = new ArrayList<>();
        for (int x = box.x() - REACH; x < box.x() + box.sx() + REACH; x++) for (int z = box.z() - REACH; z < box.z() + box.sz() + REACH; z++) {
            double c = rift(w, box, x, z);
            if (c >= 0) columns.add(new int[]{x, z, (int) (c * 1000)});
        }
        final int mine = run;
        new BukkitRunnable() {
            int i = 0;
            final Random r = new Random(w.getSeed() ^ box.name().hashCode());
            @Override public void run() {
                if (mine != run) { cancel(); return; }
                // Peu de colonnes par tick : chacune peut demander au serveur de charger un chunk.
                for (int n = 0; n < (fresh ? 90 : 30) && i < columns.size(); n++, i++) {
                    int[] col = columns.get(i);
                    int x = col[0], z = col[1];
                    double c = col[2] / 1000.0;
                    int top = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                    // Une colonne deja creusee par l'ancienne faille : on repart de la hauteur du terrain alentour.
                    int around = fresh ? top : rim(w, box, x, z);
                    int s = top < around - 8 ? around : top;
                    Location at = new Location(w, x, s, z);
                    if (s <= w.getMinHeight() + 8 || held(at)) continue;
                    // Ce qui depasse (arbres, herbes) est retire.
                    // (Jusqu'au dernier bloc qui n'est pas de l'air : la neige posee sur un arbre ne reste pas en l'air.)
                    for (int y = w.getHighestBlockYAt(x, z, HeightMap.WORLD_SURFACE); y > s; y--) { at.setY(y); if (held(at)) break; w.getBlockAt(x, y, z).setType(Material.AIR, false); }
                    if (c > 0.8) {
                        // Le bord : une croute d'obsidienne, qui pleure par endroits.
                        w.getBlockAt(x, s, z).setType(CRUST[r.nextInt(CRUST.length)], false);
                        continue;
                    }
                    double in = 1 - c / 0.8;
                    // Le vide : au milieu de la faille, par taches.
                    boolean hole = in > 0.45 && Math.sin(x * 0.19 + seed) * Math.sin(z * 0.23 + seed * 1.3) + 0.4 * Math.sin((x - z) * 0.11) > 0.55;
                    int bottom = hole ? Math.max(w.getMinHeight() + 2, s - 50) : s - 2 - (int) Math.round(4 * in);
                    for (int y = s; y > bottom; y--) {
                        at.setY(y);
                        if (held(at)) break;
                        Block b = w.getBlockAt(x, y, z);
                        if (!b.getType().isAir()) b.setType(Material.AIR, false);
                    }
                    if (hole) continue;
                    // Le lit de verre : une lumiere au fond, du verre par-dessus, en creux vers le milieu. Les couleurs
                    // vont par taches de quelques blocs.
                    w.getBlockAt(x, bottom, z).setType(shine ? light : Material.OBSIDIAN, false);
                    int surface = s - 1 - (int) Math.round(2 * in);
                    int tone = Math.floorMod((int) Math.floor(3 * Math.sin(x * 0.31 + seed) + 3 * Math.sin(z * 0.27 + seed * 2) + (x + z) * 0.08) + r.nextInt(2), GLASS.length);
                    int high = Math.max(bottom + 1, surface);
                    for (int y = bottom + 1; y <= high; y++) w.getBlockAt(x, y, z).setType(GLASS[tone], false);
                    // Une lumiere invisible juste au-dessus du verre : il brille de jour comme de nuit, et la neige ne s'y pose pas.
                    if (shine) w.getBlockAt(x, high + 1, z).setType(Material.LIGHT, false);
                }
                if (i >= columns.size()) {
                    cancel();
                    plugin.getConfig().set(key(w, box.name()) + ".lueur", 6);
                    plugin.saveConfig();
                    if (after != null) after.run();
                }
            }
        }.runTaskTimer(plugin, 20L, 1L);
    }

    /** La hauteur du bord d'une faille deja creusee : celle du terrain juste au-dela, du cote oppose au lieu. */
    private static int rim(World w, Placed box, int x, int z) {
        int ox = x < box.x() ? -1 : x >= box.x() + box.sx() ? 1 : 0, oz = z < box.z() ? -1 : z >= box.z() + box.sz() ? 1 : 0;
        int dx = Math.max(box.x() - x, x - (box.x() + box.sx() - 1)), dz = Math.max(box.z() - z, z - (box.z() + box.sz() - 1));
        // Vingt blocs au-dela du lieu : plus loin que la faille la plus large.
        return w.getHighestBlockYAt(x + ox * (REACH + 2 - Math.max(dx, 0)), z + oz * (REACH + 2 - Math.max(dz, 0)), HeightMap.MOTION_BLOCKING_NO_LEAVES);
    }

    /** Les lieux deja poses dont la faille ne luit pas encore (une map d'avant la lueur) : son fond est eclaire. */
    public void shine(World w) {
        if (!enabled()) return;
        // L'une apres l'autre : refaire une faille charge des chunks, et le serveur ne doit pas en patir.
        for (Placed p : finished(w)) if (plugin.getConfig().getInt(key(w, p.name()) + ".lueur", 0) < 6) { tear(w, p, false, () -> shine(w)); return; }
    }

    /** Le lieu dont la faille passe par cette colonne, ou null. */
    private Placed riftOf(World w, int x, int z) {
        for (Placed p : placed) if (p.world().equals(w.getName()) && rift(w, p, x, z) >= 0) return p;
        return null;
    }

    /**
     * Le coeur du monde bat dans les failles (toutes les 5 ticks, pres des joueurs) : des etincelles en montent, le verre
     * change de couleur par vagues (un changement montre au joueur seulement : les blocs, eux, ne bougent pas), et un
     * battement sourd s'entend toutes les deux secondes.
     */
    public void sparkle() {
        if (!enabled() || !plugin.getConfig().getBoolean("structures.lueur", true) || !plugin.getConfig().getBoolean("structures.dechirure", true)) return;
        java.util.concurrent.ThreadLocalRandom r = java.util.concurrent.ThreadLocalRandom.current();
        beat++;
        // La couleur de la vague du moment : elle fait le tour des verres en une minute.
        int wave = beat / 6;
        for (org.bukkit.entity.Player pl : Bukkit.getOnlinePlayers()) {
            World w = pl.getWorld();
            Location at = pl.getLocation();
            boolean near = false;
            for (Placed p : placed) if (p.world().equals(w.getName()) && at.getX() > p.x() - 60 && at.getX() < p.x() + p.sx() + 60 && at.getZ() > p.z() - 60 && at.getZ() < p.z() + p.sz() + 60) { near = true; break; }
            if (!near) continue;
            Map<Location, BlockData> change = new HashMap<>();
            double closest = Double.MAX_VALUE;
            for (int n = 0; n < 260; n++) {
                int x = at.getBlockX() + r.nextInt(-40, 41), z = at.getBlockZ() + r.nextInt(-40, 41);
                if (!w.isChunkLoaded(x >> 4, z >> 4) || riftOf(w, x, z) == null) continue;
                int y = w.getHighestBlockYAt(x, z);
                if (w.getBlockAt(x, y, z).getType() == Material.LIGHT) y--;
                if (!GLASS_SET.contains(w.getBlockAt(x, y, z).getType())) continue;
                closest = Math.min(closest, at.distanceSquared(new Location(w, x, y, z)));
                // Un bloc sur trois prend la couleur de la vague, les autres une couleur voisine.
                change.put(new Location(w, x, y, z), GLASS[Math.floorMod(wave + (x + z) / 5 + r.nextInt(3), GLASS.length)].createBlockData());
                if (n % 3 == 0) pl.spawnParticle(org.bukkit.Particle.END_ROD, x + r.nextDouble(), y + 1.1, z + r.nextDouble(), 0, 0, 1, 0, 0.05 + r.nextDouble() * 0.2);
                // De temps en temps, un trait de lumiere jaillit de la faille.
                if (r.nextInt(60) == 0) for (int k = 0; k < 24; k++) pl.spawnParticle(org.bukkit.Particle.END_ROD, x + 0.5, y + 1 + k * 0.7, z + 0.5, 0, 0, 1, 0, 0.3);
            }
            if (!change.isEmpty()) pl.sendMultiBlockChange(change);
            if (beat % 8 == 0 && closest < 28 * 28) pl.playSound(pl.getLocation(), org.bukkit.Sound.ENTITY_WARDEN_HEARTBEAT, org.bukkit.SoundCategory.AMBIENT, (float) Math.max(0.15, 1 - Math.sqrt(closest) / 28), 0.6f);
        }
    }

    private int beat;

    private static final Set<Material> GLOW_SET = Set.of(GLOWS);

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** Du sol : ce sous quoi l'on comble quand un lieu le pose en hauteur (pas un toit, un pont, un feuillage). */
    private static boolean soil(Material m) {
        return org.bukkit.Tag.DIRT.isTagged(m) || org.bukkit.Tag.BASE_STONE_OVERWORLD.isTagged(m) || org.bukkit.Tag.SAND.isTagged(m) || org.bukkit.Tag.TERRACOTTA.isTagged(m)
                || m == Material.GRAVEL || m == Material.CLAY || m == Material.SNOW_BLOCK || m == Material.SANDSTONE || m == Material.RED_SANDSTONE || m == Material.COBBLESTONE
                || m == Material.MOSSY_COBBLESTONE || m == Material.BEDROCK;
    }

    /** Ce que le comblement remplace sous un lieu : l'air, l'eau, la lave, les plantes, les feuilles. */
    private static boolean fillable(Material m) {
        return m.isAir() || m == Material.WATER || m == Material.LAVA || !m.isSolid() || org.bukkit.Tag.LEAVES.isTagged(m);
    }

    // ------------------------------------------------------------------ le tirage des emplacements

    /**
     * Pose sur la map, l'un apres l'autre, les lieux qui n'y sont pas encore (ou dont la pose a ete interrompue).
     * Celui du centre (structures.centre) d'abord, au milieu de la map ; les autres a un endroit tire au sort : dans
     * la bordure, loin de l'arene et des autres lieux, de preference hors de l'eau. Chacun est recadre a la part que
     * la bordure lui laisse ({@link #share}).
     */
    public void scatter(World w) {
        if (!enabled() || working) return;
        String middle = plugin.getConfig().getString("structures.centre", "jouki");
        List<Sheet> all = sheets();
        all.sort(java.util.Comparator.comparing(s -> !s.name().equals(middle)));
        final int max = share(w);
        for (Sheet s : all) {
            Placed before = find(w, s.name());
            if (before != null && plugin.getConfig().getBoolean(key(w, s.name()) + ".finie", false)) continue;
            // Un lieu dont le monde ne peut pas servir est passe, et dit une fois : les autres sont poses.
            if (skipped.contains(s.name())) continue;
            String no = unusable(s);
            if (no != null) {
                skipped.add(s.name());
                plugin.getLogger().warning("Lieu " + s.name() + " : il n'est pas pose, son monde ne peut pas servir (" + no + ").");
                continue;
            }
            int[] cut = cut(s, max);
            int[] at = before != null ? new int[]{before.x(), before.z()} : s.name().equals(middle) ? new int[]{-cut[2] / 2, -cut[3] / 2} : spot(w, s.name(), cut[2], cut[3]);
            if (at == null) { plugin.getLogger().warning("Lieu " + s.name() + " : aucun emplacement libre sur cette map (" + cut[2] + " x " + cut[3] + " blocs)."); continue; }
            if (place(Bukkit.getConsoleSender(), s, w, at[0], at[1], max, () -> scatter(w))) {
                // Les admins en ligne suivent la pose : les lieux arrivent l'un apres l'autre, une minute chacun environ.
                int done = finished(w).size();
                for (org.bukkit.entity.Player pl : Bukkit.getOnlinePlayers()) if (pl.hasPermission("crossfight.admin"))
                    Msg.send(pl, "<gray>Lieux : pose de <white>" + title(s.name()) + "<gray> (" + (done + 1) + " sur " + all.size() + "). Ils arrivent l'un apres l'autre, une minute chacun environ.");
                return;
            }
            skipped.add(s.name());
        }
    }

    /** Les lieux qui n'ont pas pu etre poses depuis le demarrage : {@link #scatter} ne les retente pas a chaque passage. */
    private final Set<String> skipped = new HashSet<>();

    /** On retentera tout au prochain {@link #scatter} (un dossier a ete corrige, la map a change). */
    public void retry() {
        skipped.clear();
    }

    /** Un emplacement libre pour un lieu de cette taille (son coin, aligne sur les chunks), ou null. */
    private int[] spot(World w, String name, int wide, int deep) {
        int border = plugin.game().rules().value("bordure"), margin = plugin.getConfig().getInt("structures.marge", 60);
        int keepOut = plugin.getConfig().getInt("structures.arene", 160);
        Random r = new Random(w.getSeed() * 31 + name.hashCode());
        int low = -border + margin, highX = border - margin - wide, highZ = border - margin - deep;
        if (highX < low || highZ < low) return null;
        Location arena = plugin.ferox().centre();
        int[] wet = null;
        for (int attempt = 0; attempt < 6000; attempt++) {
            int x = Math.floorDiv(low + r.nextInt(highX - low + 1), 16) * 16, z = Math.floorDiv(low + r.nextInt(highZ - low + 1), 16) * 16;
            // Loin de l'arene de Ferox : le rectangle du lieu ne touche pas le disque garde autour d'elle.
            double nearX = Math.max(x, Math.min(arena.getX(), x + wide)), nearZ = Math.max(z, Math.min(arena.getZ(), z + deep));
            if (Math.hypot(nearX - arena.getX(), nearZ - arena.getZ()) < keepOut) continue;
            boolean free = true;
            for (Placed p : placed(w)) {
                if (p.name().equals(name)) continue;
                if (x < p.x() + p.sx() + margin && x + wide + margin > p.x() && z < p.z() + p.sz() + margin && z + deep + margin > p.z()) { free = false; break; }
            }
            if (!free) continue;
            // Hors de l'eau, si possible : le biome, lu sans generer le terrain.
            int water = 0;
            for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
                String biome = w.getBiome(x + (int) ((i + 0.5) * wide / 3.0), 64, z + (int) ((j + 0.5) * deep / 3.0)).getKey().getKey();
                if (biome.contains("ocean") || biome.contains("river")) water++;
            }
            if (water <= 1) return new int[]{x, z};
            if (wet == null) wet = new int[]{x, z};
        }
        return wet;
    }

    // ------------------------------------------------------------------ la protection

    /** Vrai si ce bloc est dans un lieu pose, a l'interieur de son contour : la faille n'y creuse pas. */
    private boolean held(Location l) {
        for (Placed p : placed) if (p.contains(l) && within(l.getWorld(), p, l.getBlockX(), l.getBlockZ())) return true;
        return false;
    }

    private boolean guarded(Location l) {
        if (!plugin.getConfig().getBoolean("structures.protegees", true)) return false;
        for (Placed p : placed) if (p.contains(l)) return true;
        return false;
    }

    /** Les lieux ne se cassent pas, ne se completent pas, n'explosent pas et ne brulent pas (structures.protegees). */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (guarded(e.getBlock().getLocation())) { e.setCancelled(true); Msg.actionBar(e.getPlayer(), "<red>Ce lieu est indestructible."); }
        // Le fond lumineux des failles ne se ramasse pas.
        else if ((GLOW_SET.contains(e.getBlock().getType()) || GLASS_SET.contains(e.getBlock().getType())) && plugin.getConfig().getBoolean("structures.protegees", true)
                && riftOf(e.getBlock().getWorld(), e.getBlock().getX(), e.getBlock().getZ()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (guarded(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> guarded(b.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> guarded(b.getLocation()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        if (guarded(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketEmptyEvent e) {
        if (guarded(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucket(PlayerBucketFillEvent e) {
        if (guarded(e.getBlock().getLocation())) e.setCancelled(true);
    }

    /**
     * Le decor d'un lieu ne tombe pas et ne se pille pas : un cadre ne se decroche pas (ni tout seul, quand son mur
     * n'est pas encore pose, ni sous les coups), on ne prend pas ce que portent les cadres et les porte-armures.
     */
    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(org.bukkit.event.hanging.HangingBreakEvent e) {
        if (guarded(e.getEntity().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDecorHit(org.bukkit.event.entity.EntityDamageEvent e) {
        if (decor(e.getEntity()) && guarded(e.getEntity().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onStand(org.bukkit.event.player.PlayerArmorStandManipulateEvent e) {
        if (guarded(e.getRightClicked().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFrame(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof Hanging && guarded(e.getRightClicked().getLocation())) e.setCancelled(true);
    }

    /** Pendant qu'un lieu se pose, rien ne tombe au sol dans sa boite (un objet qui se detacherait malgre tout). */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(org.bukkit.event.entity.ItemSpawnEvent e) {
        if (working && guarded(e.getLocation())) e.setCancelled(true);
    }
}
