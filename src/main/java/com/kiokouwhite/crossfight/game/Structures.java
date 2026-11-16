package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.structure.Mirror;
import org.bukkit.block.structure.StructureRotation;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
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
import org.bukkit.structure.Structure;
import org.bukkit.util.BlockTransformer;
import org.bukkit.util.BlockVector;
import org.bukkit.util.EntityTransformer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Les structures : de grandes constructions (des maps de la communaute, une ville, une academie...) posees a des
 * endroits tires au sort sur la map de jeu, avant la partie, comme l'arene de Ferox l'est en son centre.
 *
 * Une structure est un dossier de plugins/CrossfightUHC/structures/ : une fiche (structure.yml : taille, niveau du
 * sol, auteur) et des tuiles d'un chunk de cote et de 64 blocs de haut (des fichiers de structure de Minecraft,
 * blocs, coffres, cadres et porte-armures compris). On la fabrique avec /cf structure exporter, depuis le monde ou elle est batie ; les
 * tuiles vides ne sont pas gardees. Ces dossiers ne sont pas dans le depot : ce sont les oeuvres de leurs auteurs.
 * Chaque tuile reprend aussi la derniere couche de celles qui la precedent (un bloc de debord, vers le bas de
 * chaque axe) : ce qui tient a un bloc d'a cote (une torche sur un mur, un lit a cheval sur deux tuiles) est
 * repose avec son appui. Une tuile ne depasse pas un chunk et ce debord : c'est ce que l'API laisse poser d'un coup.
 *
 * La pose se fait par tranches (quatre colonnes de tuiles a la fois, leurs chunks charges sans figer le serveur) :
 * le terrain est degage au-dessus du niveau du sol, la structure posee, et le dessous comble jusqu'au terrain.
 * L'endroit et l'avancement sont retenus par monde dans config.yml : une structure n'est posee qu'une fois par map,
 * et une pose interrompue par un arret du serveur reprend au demarrage.
 */
public final class Structures implements Listener {

    /** Cote d'une tuile, en blocs : un chunk (avec son debord d'un bloc, c'est ce que l'API pose d'un coup) ; sa hauteur. */
    private static final int TILE = 16, TALL = 64;
    /** Colonnes de tuiles posees par tranche. */
    private static final int BATCH = 4;

    /** Une structure disponible : sa fiche. */
    public record Sheet(String name, File folder, int sx, int sy, int sz, int ground, boolean carve, String author, String source, List<String> tiles) {}

    /** Une structure posee sur une map : son coin bas, sa taille. */
    public record Placed(String name, String world, int x, int y, int z, int sx, int sy, int sz) {
        boolean contains(Location l) {
            return l.getWorld().getName().equals(world) && l.getBlockX() >= x && l.getBlockX() < x + sx && l.getBlockZ() >= z && l.getBlockZ() < z + sz
                    && l.getBlockY() >= y && l.getBlockY() < y + sy;
        }
    }

    private final CrossfightPlugin plugin;
    private final List<Placed> placed = new ArrayList<>();
    /** Une pose ou un export est en cours : un seul a la fois. */
    private boolean working;
    /** La structure en cours de pose : ce qui tombe de ses blocs pendant ce temps (une torche sans mur) ne laisse pas d'objet. */
    private Placed pasting;
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
        pasting = null;
    }

    /** Le nom d'une structure tel qu'on l'annonce : "beacon_academy" -> "Beacon academy". */
    public static String title(String name) {
        String t = name.replace('_', ' ');
        return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
    }

    /** Les structures entierement posees sur cette map. */
    public List<Placed> finished(World w) {
        List<Placed> out = new ArrayList<>();
        for (Placed p : placed(w)) if (plugin.getConfig().getBoolean(key(w, p.name()) + ".finie", false)) out.add(p);
        return out;
    }

    // ------------------------------------------------------------------ les fiches

    /** Les structures disponibles sur ce serveur (un dossier et sa fiche chacune). */
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

    public Sheet sheet(String name) {
        File dir = new File(folder(), name);
        File index = new File(dir, "structure.yml");
        if (!name.matches("[a-z0-9_]+") || !index.isFile()) return null;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(index);
        List<Integer> size = y.getIntegerList("taille");
        if (size.size() != 3) return null;
        return new Sheet(name, dir, size.get(0), size.get(1), size.get(2), y.getInt("sol", 0), y.getString("sous-sol", "garde").equals("creuse"),
                y.getString("auteur", ""), y.getString("source", ""), y.getStringList("tuiles"));
    }

    /** Les structures posees sur les maps (lues dans config.yml). */
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

    // ------------------------------------------------------------------ l'export

    /**
     * Fabrique une structure a partir d'une boite du monde : ses tuiles et sa fiche. {@code ground} est la hauteur,
     * dans ce monde, du premier niveau au-dessus du sol (celui qui se posera au niveau du terrain) ; {@code carve}
     * garde les vides de ses sous-sols (caves, egouts) au lieu de laisser le terrain les remplir.
     */
    public void export(CommandSender to, String name, World w, int x1, int y1, int z1, int x2, int y2, int z2, int ground, boolean carve, String author, String source) {
        if (working) { Msg.send(to, "<red>Une pose ou un export est deja en cours."); return; }
        if (!name.matches("[a-z0-9_]+")) { Msg.send(to, "<red>Nom : des minuscules, des chiffres et des _ seulement."); return; }
        final int ox = Math.min(x1, x2), oy = Math.max(w.getMinHeight(), Math.min(y1, y2)), oz = Math.min(z1, z2);
        final int sx = Math.abs(x2 - x1) + 1, sy = Math.min(w.getMaxHeight() - 1, Math.max(y1, y2)) - oy + 1, sz = Math.abs(z2 - z1) + 1;
        final File dir = new File(folder(), name);
        if (dir.isDirectory()) { File[] old = dir.listFiles(); if (old != null) for (File f : old) f.delete(); }
        dir.mkdirs();
        working = true;
        final int nx = (sx + TILE - 1) / TILE, ny = (sy + TALL - 1) / TALL, nz = (sz + TILE - 1) / TILE;
        final List<String> tiles = new ArrayList<>();
        Msg.send(to, "<yellow>Export de <white>" + name + "</white> : " + sx + " x " + sy + " x " + sz + " blocs, " + (nx * ny * nz) + " tuiles a examiner...");
        new BukkitRunnable() {
            int i = 0;
            @Override public void run() {
                long until = System.currentTimeMillis() + 35;
                while (i < nx * ny * nz && System.currentTimeMillis() < until) {
                    int ix = i / (ny * nz), iy = (i / nz) % ny, iz = i % nz;
                    i++;
                    int x0 = ox + ix * TILE, y0 = oy + iy * TALL, z0 = oz + iz * TILE;
                    int w0 = Math.min(TILE, ox + sx - x0), h0 = Math.min(TALL, oy + sy - y0), l0 = Math.min(TILE, oz + sz - z0);
                    if (empty(w, x0, y0, z0, w0, h0, l0)) continue;
                    // La tuile reprend la derniere couche de celles qui la precedent (un bloc, vers le bas de chaque axe).
                    int lowX = ix > 0 ? 1 : 0, lowY = iy > 0 ? 1 : 0, lowZ = iz > 0 ? 1 : 0;
                    Structure s = Bukkit.getStructureManager().createStructure();
                    s.fill(new Location(w, x0 - lowX, y0 - lowY, z0 - lowZ), new BlockVector(w0 + lowX, h0 + lowY, l0 + lowZ), true);
                    String id = ix + "_" + iy + "_" + iz;
                    try {
                        Bukkit.getStructureManager().saveStructure(new File(dir, id + ".nbt"), s);
                        tiles.add(id);
                    } catch (IOException ex) {
                        plugin.getLogger().warning("Structure " + name + " : tuile " + id + " non ecrite : " + ex);
                    }
                }
                if (i < nx * ny * nz) return;
                cancel();
                YamlConfiguration y = new YamlConfiguration();
                y.set("taille", List.of(sx, sy, sz));
                y.set("sol", Math.max(0, Math.min(sy, ground - oy)));
                y.set("sous-sol", carve ? "creuse" : "garde");
                y.set("auteur", author);
                y.set("source", source);
                y.set("tuiles", tiles);
                try { y.save(new File(dir, "structure.yml")); } catch (IOException ex) { plugin.getLogger().warning("Fiche de " + name + " non ecrite : " + ex); }
                working = false;
                Msg.send(to, "<green>Structure <white>" + name + "</white> exportee : " + tiles.size() + " tuiles gardees sur " + (nx * ny * nz) + " (" + dir.getPath() + ").");
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static boolean empty(World w, int x0, int y0, int z0, int sx, int sy, int sz) {
        for (int x = x0; x < x0 + sx; x++) for (int z = z0; z < z0 + sz; z++) for (int y = y0; y < y0 + sy; y++) if (!w.getBlockAt(x, y, z).getType().isAir()) return false;
        return true;
    }

    // ------------------------------------------------------------------ la pose

    /**
     * Pose une structure, son coin (x, z) aligne sur les chunks, son niveau du sol a la hauteur mediane du terrain.
     * Reprend la ou elle s'etait arretee si la pose avait ete interrompue. {@code done} est appele a la fin.
     */
    public void place(CommandSender to, Sheet s, World w, int atX, int atZ, Runnable done) {
        if (working) { Msg.send(to, "<red>Une pose ou un export est deja en cours."); return; }
        working = true;
        final int mine = run;
        final int x0 = Math.floorDiv(atX, 16) * 16, z0 = Math.floorDiv(atZ, 16) * 16;
        Placed before = find(w, s.name());
        if (before != null && before.x() == x0 && before.z() == z0) { paste(to, s, w, before, plugin.getConfig().getInt(key(w, s.name()) + ".avancement", 0), done); return; }
        // Le niveau du terrain : la mediane de vingt-cinq hauteurs prises sous la structure, chunks charges sans attendre.
        List<int[]> points = new ArrayList<>();
        for (int i = 0; i < 5; i++) for (int j = 0; j < 5; j++) points.add(new int[]{x0 + (int) ((i + 0.5) * s.sx() / 5.0), z0 + (int) ((j + 0.5) * s.sz() / 5.0)});
        List<CompletableFuture<?>> loads = new ArrayList<>();
        for (int[] p : points) loads.add(w.getChunkAtAsync(p[0] >> 4, p[1] >> 4));
        CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).whenComplete((v, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
            if (mine != run) return;
            List<Integer> heights = new ArrayList<>();
            for (int[] p : points) heights.add(w.getHighestBlockYAt(p[0], p[1], HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1);
            Collections.sort(heights);
            int level = Math.max(w.getMinHeight() + s.ground() + 2, Math.min(w.getMaxHeight() - (s.sy() - s.ground()) - 2, heights.get(heights.size() / 2)));
            Placed box = new Placed(s.name(), w.getName(), x0, level - s.ground(), z0, s.sx(), s.sy(), s.sz());
            placed.removeIf(p -> p.world().equals(w.getName()) && p.name().equals(s.name()));
            placed.add(box);
            plugin.getConfig().set(key(w, s.name()) + ".boite", List.of(box.x(), box.y(), box.z(), box.sx(), box.sy(), box.sz()));
            plugin.getConfig().set(key(w, s.name()) + ".avancement", 0);
            plugin.getConfig().set(key(w, s.name()) + ".finie", false);
            plugin.saveConfig();
            paste(to, s, w, box, 0, done);
        }));
    }

    /** Pose les colonnes de tuiles, quatre par tranche, a partir de la {@code from}-ieme. */
    private void paste(CommandSender to, Sheet s, World w, Placed box, int from, Runnable done) {
        final int nx = (s.sx() + TILE - 1) / TILE, ny = (s.sy() + TALL - 1) / TALL, nz = (s.sz() + TILE - 1) / TILE;
        final int level = box.y() + s.ground();
        final java.util.Set<String> kept = new java.util.HashSet<>(s.tiles());
        final Random random = new Random(w.getSeed() ^ s.name().hashCode());
        final int mine = run;
        // Les blocs que la structure pose sous le niveau du sol, dans la colonne en cours : le comblement n'y touche pas.
        final java.util.Set<Long> own = new java.util.HashSet<>();
        // Ce que la structure laisse vide ne remplace rien (le terrain a deja ete degage au-dessus du sol), sauf dans
        // ses sous-sols quand elle les creuse ; les coffres arrivent vides.
        final BlockTransformer blocks = (region, x, y, z, current, state) -> {
            if (current.getType().isAir() && !(s.carve() && y < level)) return w.getBlockAt(x, y, z).getState();
            if (y < level) own.add(pack(x, y, z));
            if (current instanceof Container c) c.getSnapshotInventory().clear();
            return current;
        };
        // Le decor seulement : cadres, tableaux, porte-armures, affichages. Pas d'animaux ni de villageois ; et seulement
        // ce qui est dans le coeur de la tuile (son debord appartient aux tuiles d'avant, qui l'ont deja apporte).
        final int[] core = new int[6];
        final EntityTransformer entities = (region, x, y, z, entity, allowed) -> allowed
                && (entity instanceof Hanging || entity instanceof ArmorStand || entity instanceof Display)
                && x >= core[0] && x < core[3] && y >= core[1] && y < core[4] && z >= core[2] && z < core[5];
        pasting = box;
        Msg.send(to, "<yellow>Pose de <white>" + s.name() + "</white> en " + box.x() + " " + level + " " + box.z() + " (" + s.sx() + " x " + s.sz() + " blocs)...");
        new Object() {
            int column = from;
            void next() {
                if (!plugin.isEnabled() || mine != run) return;
                if (column >= nx * nz) { finish(); return; }
                final int first = column, last = Math.min(nx * nz, column + BATCH);
                List<CompletableFuture<?>> loads = new ArrayList<>();
                for (int c = first; c < last; c++) loads.add(w.getChunkAtAsync((box.x() >> 4) + c / nz, (box.z() >> 4) + c % nz));
                CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).whenComplete((v, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!plugin.isEnabled() || mine != run) return;
                    for (int c = first; c < last; c++) column(c / nz, c % nz);
                    column = last;
                    if (column % 32 < BATCH || column == nx * nz) { plugin.getConfig().set(key(w, s.name()) + ".avancement", column); plugin.saveConfig(); }
                    Bukkit.getScheduler().runTaskLater(plugin, this::next, 1L);
                }));
            }
            /** Une colonne de tuiles : un chunk de la structure, de bas en haut. */
            void column(int ix, int iz) {
                final int cx = box.x() + ix * TILE, cz = box.z() + iz * TILE;
                final int wide = Math.min(TILE, box.x() + s.sx() - cx), deep = Math.min(TILE, box.z() + s.sz() - cz);
                // 1. Le terrain est degage au-dessus du niveau du sol : buttes, arbres. Le decor d'une pose precedente
                // au meme endroit (une pose interrompue, puis reprise) est retire : il ne doit pas se dedoubler.
                for (var e : w.getNearbyEntities(new org.bukkit.util.BoundingBox(cx, box.y(), cz, cx + wide, box.y() + s.sy(), cz + deep)))
                    if (e instanceof Hanging || e instanceof ArmorStand || e instanceof Display) e.remove();
                for (int x = cx; x < cx + wide; x++) for (int z = cz; z < cz + deep; z++) {
                    int top = w.getHighestBlockYAt(x, z);
                    for (int y = level; y <= top; y++) { Block b = w.getBlockAt(x, y, z); if (!b.getType().isAir()) b.setType(Material.AIR, false); }
                }
                // 2. Ses tuiles, de bas en haut, chacune avec la derniere couche des tuiles d'avant.
                own.clear();
                for (int iy = 0; iy < ny; iy++) {
                    String id = ix + "_" + iy + "_" + iz;
                    if (!kept.contains(id)) continue;
                    int cy = box.y() + iy * TALL;
                    core[0] = cx; core[1] = cy; core[2] = cz;
                    core[3] = cx + wide; core[4] = Math.min(cy + TALL, box.y() + s.sy()); core[5] = cz + deep;
                    try {
                        Structure tile = Bukkit.getStructureManager().loadStructure(new File(s.folder(), id + ".nbt"));
                        tile.place(new Location(w, cx - (ix > 0 ? 1 : 0), cy - (iy > 0 ? 1 : 0), cz - (iz > 0 ? 1 : 0)), true, StructureRotation.NONE, Mirror.NONE, 0, 1f, random,
                                List.of(blocks), List.of(entities));
                    } catch (IOException | RuntimeException ex) {
                        plugin.getLogger().warning("Structure " + s.name() + " : tuile " + id + " non posee : " + ex);
                    }
                }
                // 3. Le dessous est comble jusqu'au terrain. On descend depuis le niveau du sol : ce que la structure
                // a pose reste tel quel, les vides sont remplis (de l'herbe a l'air libre, de la terre, puis de la
                // pierre) ; une fois sous la structure, on s'arrete au premier bloc du terrain.
                int under = s.carve() ? box.y() - 1 : level - 1;
                for (int x = cx; x < cx + wide; x++) for (int z = cz; z < cz + deep; z++) {
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
                plugin.getConfig().set(key(w, s.name()) + ".finie", true);
                plugin.saveConfig();
                working = false;
                // Un instant encore sans objets tombes : les derniers blocs sans appui finissent de se detacher.
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (pasting == box) pasting = null; }, 40L);
                Msg.send(to, "<green>Structure <white>" + s.name() + "</white> posee en <white>" + (box.x() + s.sx() / 2) + " " + level + " " + (box.z() + s.sz() / 2) + "</white>.");
                if (done != null) done.run();
            }
        }.next();
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    /** Ce que le comblement remplace sous une structure : l'air, l'eau, la lave, les plantes, les feuilles. */
    private static boolean fillable(Material m) {
        return m.isAir() || m == Material.WATER || m == Material.LAVA || !m.isSolid() || org.bukkit.Tag.LEAVES.isTagged(m);
    }

    // ------------------------------------------------------------------ le tirage des emplacements

    /**
     * Pose sur la map, l'une apres l'autre, les structures qui n'y sont pas encore (ou dont la pose a ete
     * interrompue), chacune a un endroit tire au sort : dans la bordure, loin de l'arene du centre et des autres
     * structures, de preference hors de l'eau.
     */
    public void scatter(World w) {
        if (!enabled() || working) return;
        for (Sheet s : sheets()) {
            Placed before = find(w, s.name());
            if (before != null && plugin.getConfig().getBoolean(key(w, s.name()) + ".finie", false)) continue;
            int[] at = before != null ? new int[]{before.x(), before.z()} : spot(w, s);
            if (at == null) { plugin.getLogger().warning("Structure " + s.name() + " : aucun emplacement libre sur cette map (" + s.sx() + " x " + s.sz() + " blocs)."); continue; }
            place(Bukkit.getConsoleSender(), s, w, at[0], at[1], () -> scatter(w));
            return;
        }
    }

    /** Un emplacement libre pour cette structure (son coin, aligne sur les chunks), ou null. */
    private int[] spot(World w, Sheet s) {
        int border = plugin.game().rules().value("bordure"), margin = plugin.getConfig().getInt("structures.marge", 60);
        int keepOut = plugin.getConfig().getInt("structures.arene", 160);
        Random r = new Random(w.getSeed() * 31 + s.name().hashCode());
        int roomX = border - margin - s.sx(), roomZ = border - margin - s.sz();
        if (roomX < -border + margin || roomZ < -border + margin) return null;
        int[] wet = null;
        for (int attempt = 0; attempt < 400; attempt++) {
            int x = Math.floorDiv(-border + margin + r.nextInt(Math.max(1, roomX + border - margin + 1)), 16) * 16;
            int z = Math.floorDiv(-border + margin + r.nextInt(Math.max(1, roomZ + border - margin + 1)), 16) * 16;
            // Loin de l'arene : le rectangle de la structure, elargi, ne touche pas le disque du centre.
            double nearX = Math.max(x, Math.min(0, x + s.sx())), nearZ = Math.max(z, Math.min(0, z + s.sz()));
            if (Math.hypot(nearX, nearZ) < keepOut) continue;
            boolean free = true;
            for (Placed p : placed(w)) {
                if (p.name().equals(s.name())) continue;
                if (x < p.x() + p.sx() + margin && x + s.sx() + margin > p.x() && z < p.z() + p.sz() + margin && z + s.sz() + margin > p.z()) { free = false; break; }
            }
            if (!free) continue;
            // Hors de l'eau, si possible : le biome, lu sans generer le terrain.
            int water = 0;
            for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
                String biome = w.getBiome(x + (int) ((i + 0.5) * s.sx() / 3.0), 64, z + (int) ((j + 0.5) * s.sz() / 3.0)).getKey().getKey();
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

    /** Les structures ne se cassent pas, ne se completent pas, n'explosent pas et ne brulent pas (structures.protegees). */
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

    /** Pendant la pose, ce qui se detache de la structure (un bloc dont l'appui n'est pas encore pose) ne laisse pas d'objet au sol. */
    @EventHandler(ignoreCancelled = true)
    public void onDrop(org.bukkit.event.entity.ItemSpawnEvent e) {
        Placed p = pasting;
        Location l = e.getLocation();
        if (p != null && l.getWorld().getName().equals(p.world()) && l.getX() >= p.x() - 2 && l.getX() < p.x() + p.sx() + 2 && l.getZ() >= p.z() - 2 && l.getZ() < p.z() + p.sz() + 2) e.setCancelled(true);
    }
}
