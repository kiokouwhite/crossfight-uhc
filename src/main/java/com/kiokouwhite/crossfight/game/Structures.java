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
        File[] dirs = folder().listFiles(File::isDirectory);
        if (dirs == null) return out;
        java.util.Arrays.sort(dirs);
        for (File d : dirs) {
            Sheet s = sheet(d.getName());
            if (s != null) out.add(s);
        }
        return out;
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
        File index = new File(new File(folder(), name), "structure.yml");
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
        File dir = new File(folder(), name);
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
        File dir = new File(folder(), name);
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
     * {@code done} est appele a la fin.
     */
    public void place(CommandSender to, Sheet s, World w, int atX, int atZ, int max, Runnable done) {
        if (working) { Msg.send(to, "<red>Une pose est deja en cours."); return; }
        final World from = source(s);
        if (from == null) {
            String no = unusable(s);
            Msg.send(to, "<red>Le monde de " + s.name() + " n'a pas pu etre charge" + (no != null ? " : " + no : "") + ".");
            if (done != null) done.run();
            return;
        }
        working = true;
        final int mine = run;
        final int x0 = Math.floorDiv(atX, 16) * 16, z0 = Math.floorDiv(atZ, 16) * 16;
        Placed before = find(w, s.name());
        List<Integer> saved = plugin.getConfig().getIntegerList(key(w, s.name()) + ".decoupe");
        if (before != null && before.x() == x0 && before.z() == z0 && saved.size() == 2) {
            paste(to, s, from, w, before, saved.get(0), saved.get(1), plugin.getConfig().getInt(key(w, s.name()) + ".avancement", 0), done);
            return;
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
            plugin.getConfig().set(key(w, s.name()) + ".finie", false);
            plugin.saveConfig();
            paste(to, s, from, w, box, cut[0], cut[1], 0, done);
        }));
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
                    int top = w.getHighestBlockYAt(x, z);
                    for (int y = level; y <= top; y++) { Block b = w.getBlockAt(x, y, z); if (!b.getType().isAir()) b.setType(Material.AIR, false); }
                }
                // 2. Les blocs, copies tels quels, sans mise a jour. Ce que le lieu laisse vide ne remplace rien, sauf
                // dans ses sous-sols quand il les creuse.
                own.clear();
                final int minY = Math.max(s.y1(), from.getMinHeight()), maxY = Math.min(s.y2(), from.getMaxHeight() - 1);
                for (int x = xa; x <= xb; x++) for (int z = za; z <= zb; z++) {
                    final int sx = x - dx, sz = z - dz;
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
                        if (ty < level) own.add(pack(x, ty, z));
                    }
                }
                // 3. Ce que contiennent les blocs (panneaux, bannieres, tetes...) ; les coffres arrivent vides. Puis le decor.
                Set<Long> seen = new HashSet<>();
                for (int sx = (xa - dx) >> 4; sx <= (xb - dx) >> 4; sx++) for (int sz = (za - dz) >> 4; sz <= (zb - dz) >> 4; sz++) {
                    if (!seen.add(Chunk.getChunkKey(sx, sz))) continue;
                    Chunk c = from.getChunkAt(sx, sz);
                    for (BlockState st : c.getTileEntities(b -> b.getX() + dx >= xa && b.getX() + dx <= xb && b.getZ() + dz >= za && b.getZ() + dz <= zb
                            && b.getY() >= minY && b.getY() <= maxY, false)) {
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
                                || l.getBlockY() < minY || l.getBlockY() > maxY) continue;
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
                    int filled = 0;
                    for (int y = under; y > w.getMinHeight() && filled < 96; y--) {
                        Block b = w.getBlockAt(x, y, z);
                        if (own.contains(pack(x, y, z))) continue;
                        if (!fillable(b.getType())) {
                            if (y >= box.y()) continue;
                            break;
                        }
                        boolean open = filled == 0 && w.getBlockAt(x, y + 1, z).getType().isAir();
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
        if (!plugin.getConfig().getBoolean("structures.dechirure", true)) return;
        final double seed = (w.getSeed() ^ box.name().hashCode()) % 1000 / 7.0;
        final Location arena = plugin.ferox().centre();
        final List<int[]> columns = new ArrayList<>();
        for (int x = box.x() - 10; x < box.x() + box.sx() + 10; x++) for (int z = box.z() - 10; z < box.z() + box.sz() + 10; z++) {
            int dx = Math.max(box.x() - x, x - (box.x() + box.sx() - 1)), dz = Math.max(box.z() - z, z - (box.z() + box.sz() - 1));
            int d = Math.max(dx, dz);
            if (d < 1) continue;
            double t = x * 0.9 + z * 1.1;
            // Des passages restent : la faille s'efface sur un bon tiers du tour.
            if (Math.sin(t * 0.021 + seed) + 0.5 * Math.sin(t * 0.057 + seed * 2) < -0.25) continue;
            double width = 2 + 7 * (0.5 + 0.5 * Math.sin(t * 0.11 + seed * 3));
            if (d > width) continue;
            if (Math.hypot(x - arena.getX(), z - arena.getZ()) < 110) continue;
            int depth = (int) (12 + 28 * Math.abs(Math.sin(t * 0.17 + d * 0.6 + seed)) * (1 - (d - 1) / (width + 1)));
            columns.add(new int[]{x, z, depth});
        }
        final int mine = run;
        new BukkitRunnable() {
            int i = 0;
            final Random r = new Random(w.getSeed() ^ box.name().hashCode());
            @Override public void run() {
                if (mine != run) { cancel(); return; }
                for (int n = 0; n < 120 && i < columns.size(); n++, i++) {
                    int[] c = columns.get(i);
                    Location at = new Location(w, c[0], 0, c[1]);
                    int top = w.getHighestBlockYAt(c[0], c[1]);
                    for (int y = top; y > Math.max(w.getMinHeight() + 4, top - c[2]); y--) {
                        at.setY(y);
                        if (guarded(at)) break;
                        // Un eclat reste parfois suspendu dans la faille.
                        if (y < top - 3 && r.nextInt(90) == 0) { y -= 2; continue; }
                        Block b = w.getBlockAt(c[0], y, c[1]);
                        if (!b.getType().isAir()) b.setType(Material.AIR, false);
                    }
                }
                if (i >= columns.size()) cancel();
            }
        }.runTaskTimer(plugin, 20L, 1L);
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
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
            int[] cut = cut(s, max);
            int[] at = before != null ? new int[]{before.x(), before.z()} : s.name().equals(middle) ? new int[]{-cut[2] / 2, -cut[3] / 2} : spot(w, s.name(), cut[2], cut[3]);
            if (at == null) { plugin.getLogger().warning("Lieu " + s.name() + " : aucun emplacement libre sur cette map (" + cut[2] + " x " + cut[3] + " blocs)."); continue; }
            place(Bukkit.getConsoleSender(), s, w, at[0], at[1], max, () -> scatter(w));
            return;
        }
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

    private boolean guarded(Location l) {
        if (!plugin.getConfig().getBoolean("structures.protegees", true)) return false;
        for (Placed p : placed) if (p.contains(l)) return true;
        return false;
    }

    /** Les lieux ne se cassent pas, ne se completent pas, n'explosent pas et ne brulent pas (structures.protegees). */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (guarded(e.getBlock().getLocation())) { e.setCancelled(true); Msg.actionBar(e.getPlayer(), "<red>Ce lieu est indestructible."); }
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
