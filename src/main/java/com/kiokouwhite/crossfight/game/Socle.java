package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.data.type.Slab;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Le socle d'un lieu : a quelle hauteur le poser, quoi combler dessous, quoi creuser, pour qu'il ne flotte pas.
 *
 * Un lieu vient d'une map faite pour etre regardee, pas pour etre deplacee. Beaucoup sont des coquilles : un sol d'un
 * bloc d'epaisseur bati tres au-dessus du sol plat d'un monde vide, une dalle posee sur rien, une colline creuse, une
 * ville dont les terrasses surplombent le vide. Posees telles quelles, elles flottent, et l'on se promene dessous.
 * Avant de poser un lieu, on lit donc toute sa decoupe ({@link #scan}), et l'on en tire une decision ({@link #decide}) :
 *   - le niveau : quelle hauteur du lieu vient au niveau du terrain de la map ;
 *   - le comblement : les cases qui sont du sol et non du lieu (le vide sous lui) ;
 *   - les colonnes qu'on ne pose pas (le reste d'un sol plat sans rien dessus) ;
 *   - les creux : la ou le lieu descend sous son niveau a ciel ouvert (riviere, douve, cour basse).
 * La pose (game/Structures.java) applique la decision.
 *
 * La regle, en bref (le detail est dans {@link #decide}) :
 *   1. La base. Le sol plat le plus frequent du bas du volume. Si rien n'est pose dessus, c'est le reste d'un monde
 *      vide : on le retire, le lieu est ce qu'il y a au-dessus.
 *   2. Les vides sous le sol. Les colonnes dont le plus bas vide est couvert, sans rien au sol ni lumiere, forment des
 *      zones. Une zone au sol de terre est du vide si c'est une poche fermee, un vide ou l'on ne tient pas debout, une
 *      grande etendue basse, ou le dessous d'un sol naturel ; jamais une piece (fermee, avec des ouvertures), ni le
 *      dessous d'un arbre.
 *   3. Le niveau. Le quart bas des hauteurs du sol du lieu (le dessus des vides compte comme le sol).
 *   4. Le comblement. Les vides de l'etape 2, et le dessous de ce qui flotte quand c'est le corps du lieu.
 *   5. Les creux. La ou le lieu est a ciel ouvert sous son niveau, on creuse jusqu'a son sol.
 * Elle est verifiee, case par case, contre une reference ecrite a part.
 */
final class Socle {

    private Socle() {}

    private static final Material[] ALL = Material.values();
    /** Dans un identifiant de case : le bloc est un slab double (un bloc plein, pas un demi-bloc). */
    static final int DOUBLE = 0x4000;

    /** Le volume d'un lieu : sa decoupe, case par case. Les indices : x (ouest-est), y (du bas vers le haut), z (nord-sud). */
    static final class Volume {
        final int w, h, d;
        /** Pour chaque case, l'ordinal de son materiau (plus {@link #DOUBLE} pour un slab double) : (y * d + z) * w + x. */
        final short[] ids;

        Volume(int w, int h, int d) {
            this.w = w;
            this.h = h;
            this.d = d;
            this.ids = new short[w * h * d];
            short air = (short) Material.AIR.ordinal();
            if (air != 0) java.util.Arrays.fill(ids, air);
        }

        int index(int x, int y, int z) { return (y * d + z) * w + x; }

        Material at(int x, int y, int z) { return ALL[ids[index(x, y, z)] & (DOUBLE - 1)]; }

        boolean doubled(int x, int y, int z) { return (ids[index(x, y, z)] & DOUBLE) != 0; }

        /** Recopie d'un chunk du monde d'origine ce qui tombe dans le volume (appele hors du fil du serveur). */
        void read(ChunkSnapshot snap, int srcX, int srcZ, int y1, int minHeight, int maxHeight) {
            final int bx = snap.getX() << 4, bz = snap.getZ() << 4;
            final int xa = Math.max(bx, srcX), xb = Math.min(bx + 15, srcX + w - 1), za = Math.max(bz, srcZ), zb = Math.min(bz + 15, srcZ + d - 1);
            final int ya = Math.max(y1, minHeight), yb = Math.min(y1 + h - 1, maxHeight - 1);
            for (int y = ya; y <= yb; y++) {
                // Une tranche de seize blocs entierement vide est sautee d'un coup.
                if (((y - minHeight) & 15) == 0 && y + 15 <= yb && snap.isSectionEmpty((y - minHeight) >> 4)) { y += 15; continue; }
                for (int z = za; z <= zb; z++) for (int x = xa; x <= xb; x++) {
                    Material m = snap.getBlockType(x & 15, y, z & 15);
                    if (m.isAir()) continue;
                    int id = m.ordinal();
                    if (Tag.SLABS.isTagged(m) && snap.getBlockData(x & 15, y, z & 15) instanceof Slab slab && slab.getType() == Slab.Type.DOUBLE) id |= DOUBLE;
                    ids[index(x - srcX, y - y1, z - srcZ)] = (short) id;
                }
            }
        }
    }

    /** Ce qu'on fait d'un lieu. Les indices y sont ceux du volume (0 : son bas). */
    static final class Decision {
        /** L'indice y du volume qui vient au niveau du terrain de la map : au-dessus, le lieu ; en dessous, le sol de la map. */
        int sol;
        /**
         * Les cases qui sont du sol et non du lieu, par leur indice dans le volume : au niveau et au-dessus, on y met
         * de la terre et de la pierre ; en dessous, le terrain de la map y reste (la case du lieu n'y est pas copiee).
         */
        BitSet fill = new BitSet();
        /** Les colonnes qu'on ne pose pas (z * w + x). */
        boolean[] skip;
        /**
         * Par colonne : sous le niveau, les cases d'indice >= dig sont copiees telles quelles, air compris (un creux
         * a ciel ouvert). dig = sol : rien n'est creuse.
         */
        short[] dig;
        /**
         * Par colonne : la case (indice y, -1 s'il n'y en a pas) qui recoit le bloc {@link #capId} : le sol d'un
         * vide, remonte sous un couvercle de verre pour qu'on le voie toujours a travers.
         */
        short[] capY, capId;
        /** En clair, pour la console : ce qui a ete decide et pourquoi. */
        String note = "";
    }

    /**
     * Lit la decoupe d'un lieu dans son monde d'origine : les chunks sont charges sans attendre, par paquets, copies
     * sur le fil du serveur et lus hors de lui. {@code alive} dit si la pose est toujours d'actualite ; {@code then}
     * recoit le volume, sur le fil du serveur.
     */
    static void scan(CrossfightPlugin plugin, World from, int srcX, int srcZ, int wide, int deep, int y1, int y2, BooleanSupplier alive, Consumer<Volume> then) {
        final Volume v = new Volume(wide, y2 - y1 + 1, deep);
        final List<int[]> todo = new ArrayList<>();
        for (int cx = srcX >> 4; cx <= (srcX + wide - 1) >> 4; cx++) for (int cz = srcZ >> 4; cz <= (srcZ + deep - 1) >> 4; cz++) todo.add(new int[]{cx, cz});
        final int low = from.getMinHeight(), high = from.getMaxHeight();
        new Object() {
            int i = 0;

            void next() {
                if (!plugin.isEnabled() || !alive.getAsBoolean()) return;
                if (i >= todo.size()) { then.accept(v); return; }
                final List<CompletableFuture<Chunk>> loads = new ArrayList<>();
                for (int n = 0; n < 24 && i < todo.size(); n++, i++) loads.add(from.getChunkAtAsync(todo.get(i)[0], todo.get(i)[1]));
                CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).whenComplete((r, error) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!plugin.isEnabled() || !alive.getAsBoolean()) return;
                    final List<ChunkSnapshot> snaps = new ArrayList<>();
                    for (CompletableFuture<Chunk> f : loads) {
                        Chunk c = f.getNow(null);
                        if (c != null) snaps.add(c.getChunkSnapshot(false, false, false, false));
                    }
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                        for (ChunkSnapshot s : snaps) v.read(s, srcX, srcZ, y1, low, high);
                        Bukkit.getScheduler().runTask(plugin, this::next);
                    });
                }));
            }
        }.next();
    }

    /**
     * Aide au developpement : avec -Dcrossfight.socle=<dossier> au lancement du serveur, chaque decision est ecrite dans
     * ce dossier (le volume lu, le contour, le niveau, le comblement, les colonnes ecartees, les creux), pour etre
     * comparee a la reference hors jeu. Sans cette option, ne fait rien.
     */
    static void dump(String name, Volume v, boolean[] inside, Decision d, int srcX, int srcZ, int y1, int sheet) {
        String dir = System.getProperty("crossfight.socle");
        if (dir == null) return;
        try {
            java.io.File folder = new java.io.File(dir);
            folder.mkdirs();
            try (var out = new java.io.PrintWriter(new java.io.File(folder, name + ".txt"), java.nio.charset.StandardCharsets.UTF_8)) {
                out.println(v.w + " " + v.h + " " + v.d + " " + srcX + " " + srcZ + " " + y1 + " " + d.sol + " " + sheet);
                out.println(d.note);
            }
            Kinds k = kinds();
            try (var out = new java.io.PrintWriter(new java.io.File(folder, "ordinaux.txt"), java.nio.charset.StandardCharsets.UTF_8)) {
                for (Material m : ALL) {
                    int o = m.ordinal();
                    out.println(o + " " + (m.isLegacy() ? "-" : m.getKey().getKey()) + " " + (k.air[o] ? 1 : 0) + (k.solid[o] ? 1 : 0) + (k.liquid[o] ? 1 : 0) + (k.thing[o] ? 1 : 0)
                            + (k.light[o] ? 1 : 0) + (k.soil[o] ? 1 : 0) + (k.opening[o] ? 1 : 0) + (k.plant[o] ? 1 : 0) + (k.glass[o] ? 1 : 0));
                }
            }
            try (var out = new java.util.zip.GZIPOutputStream(new java.io.FileOutputStream(new java.io.File(folder, name + ".ids.gz")))) {
                java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(v.ids.length * 2);
                buf.asShortBuffer().put(v.ids);
                out.write(buf.array());
            }
            try (var out = new java.util.zip.GZIPOutputStream(new java.io.FileOutputStream(new java.io.File(folder, name + ".fill.gz")))) {
                byte[] bits = new byte[(v.ids.length + 7) / 8];
                for (int i = d.fill.nextSetBit(0); i >= 0; i = d.fill.nextSetBit(i + 1)) bits[i >> 3] |= (byte) (1 << (i & 7));
                out.write(bits);
            }
            try (var out = new java.util.zip.GZIPOutputStream(new java.io.FileOutputStream(new java.io.File(folder, name + ".colonnes.gz")))) {
                int n = v.w * v.d;
                java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(n * 8);
                for (int i = 0; i < n; i++) buf.putShort((short) (inside[i] ? 1 : 0));
                for (int i = 0; i < n; i++) buf.putShort((short) (d.skip[i] ? 1 : 0));
                for (int i = 0; i < n; i++) buf.putShort(d.dig[i]);
                for (int i = 0; i < n; i++) buf.putShort(d.capY[i]);
                out.write(buf.array());
                java.nio.ByteBuffer ids = java.nio.ByteBuffer.allocate(n * 2);
                for (int i = 0; i < n; i++) ids.putShort(d.capId[i]);
                out.write(ids.array());
            }
        } catch (java.io.IOException ex) {
            Bukkit.getLogger().warning("Socle : decision de " + name + " non ecrite : " + ex);
        }
    }

    // ------------------------------------------------------------------ la regle

    /** Part des colonnes au meme sol plat pour parler d'une base : un cinquieme. */
    private static final int BASE_DEN = 5;
    /** Part des colonnes qui se servent de la base, en dessous de laquelle elle est un reste : un cinquantieme. */
    private static final int LEFTOVER_DEN = 50;
    /** Rayon inscrit minimal d'un vide (un carre de 7 x 7 y tient) ; d'un vide que rien n'ouvre ; d'une grande etendue. */
    private static final int THICK = 4, THICK_SHUT = 2, THICK_WIDE = 12;
    /** Hauteur d'un vide ou l'on ne tient pas debout a l'aise : un vide sanitaire, un double sol. */
    private static final int CRAWL = 3;
    /** Epaisseur (mediane) a partir de laquelle un couvercle est du sol et non un toit. */
    private static final int COVER_THICK = 4;
    /** Hauteur sous laquelle ce qui flotte, meme detache du lieu, est pose sur un socle. */
    private static final int LOW = 4;
    /** Hauteur maximale d'un vide comble. */
    private static final int CEILING = 32;
    /** Profondeur maximale d'un creux sous le niveau ; profondeur jusqu'a laquelle le lieu est copie sous le niveau. */
    static final int HOLLOW = 12, DEPTH = 12;

    /** Ce que sont les blocs, pour la regle : un tableau par propriete, indexe par l'ordinal du materiau. */
    static final class Kinds {
        final boolean[] air, solid, liquid, thing, light, soil, opening, plant, glass;

        Kinds() {
            int n = ALL.length;
            air = new boolean[n]; solid = new boolean[n]; liquid = new boolean[n]; thing = new boolean[n]; light = new boolean[n];
            soil = new boolean[n]; opening = new boolean[n]; plant = new boolean[n]; glass = new boolean[n];
            java.util.regex.Pattern open = java.util.regex.Pattern.compile("(glass|_pane$|_door$|trapdoor|fence|_gate$|iron_bars|_bars$|chain$|_wall$|ladder|scaffolding|barrier)");
            java.util.regex.Pattern green = java.util.regex.Pattern.compile("(mushroom_block$|^mushroom_stem$|^bamboo$|^bamboo_block$|^cactus$)");
            java.util.Set<String> earth = java.util.Set.of("gravel", "clay", "snow_block", "sandstone", "red_sandstone");
            for (Material m : ALL) {
                if (m.isLegacy() || !m.isBlock()) continue;
                int o = m.ordinal();
                String name = m.getKey().getKey();
                boolean leaves = Tag.LEAVES.isTagged(m);
                air[o] = m.isAir();
                solid[o] = m.isSolid() && !leaves;
                liquid[o] = m == Material.WATER || m == Material.LAVA || m == Material.BUBBLE_COLUMN;
                // Une chose : ni air, ni liquide, ni plante sans importance (herbes, fleurs, feuilles, lianes...).
                thing[o] = !air[o] && !liquid[o] && !(Tag.REPLACEABLE.isTagged(m) || Tag.FLOWERS.isTagged(m) || leaves);
                int glow = 0;
                try { glow = m.createBlockData().getLightEmission(); } catch (RuntimeException ignored) { }
                light[o] = glow > 0;
                soil[o] = Tag.DIRT.isTagged(m) || Tag.SAND.isTagged(m) || earth.contains(name);
                opening[o] = open.matcher(name).find();
                plant[o] = Tag.LOGS.isTagged(m) || leaves || Tag.WART_BLOCKS.isTagged(m) || green.matcher(name).find();
                glass[o] = name.contains("glass") && !name.contains("pane");
            }
        }
    }

    private static Kinds kinds;

    /** Les proprietes des blocs (calculees une fois ; a appeler d'abord sur le fil du serveur). */
    static synchronized Kinds kinds() {
        if (kinds == null) kinds = new Kinds();
        return kinds;
    }

    /** La valeur de rang (n - 1) * num / den parmi des valeurs comptees par un histogramme. */
    private static int kth(int[] histogram, long n, int num, int den) {
        long rank = (n - 1) * num / den, seen = 0;
        for (int i = 0; i < histogram.length; i++) {
            seen += histogram[i];
            if (seen > rank) return i;
        }
        return histogram.length - 1;
    }

    /** Erosion par un carre de cote 2r + 1 : vrai la ou tout le carre est vrai (le dehors du tableau compte pour vrai). */
    private static boolean[] erode(boolean[] mask, int w, int d, int r) {
        boolean[] rows = new boolean[mask.length], out = new boolean[mask.length];
        for (int z = 0; z < d; z++) for (int x = 0; x < w; x++) {
            boolean all = true;
            for (int k = Math.max(0, x - r); k <= Math.min(w - 1, x + r) && all; k++) all = mask[z * w + k];
            rows[z * w + x] = all;
        }
        for (int z = 0; z < d; z++) for (int x = 0; x < w; x++) {
            boolean all = true;
            for (int k = Math.max(0, z - r); k <= Math.min(d - 1, z + r) && all; k++) all = rows[k * w + x];
            out[z * w + x] = all;
        }
        return out;
    }

    /** Dilatation par un carre de cote 2r + 1 : vrai la ou une case du carre est vraie (le dehors compte pour faux). */
    private static boolean[] dilate(boolean[] mask, int w, int d, int r) {
        boolean[] rows = new boolean[mask.length], out = new boolean[mask.length];
        for (int z = 0; z < d; z++) for (int x = 0; x < w; x++) {
            boolean any = false;
            for (int k = Math.max(0, x - r); k <= Math.min(w - 1, x + r) && !any; k++) any = mask[z * w + k];
            rows[z * w + x] = any;
        }
        for (int z = 0; z < d; z++) for (int x = 0; x < w; x++) {
            boolean any = false;
            for (int k = Math.max(0, z - r); k <= Math.min(d - 1, z + r) && !any; k++) any = rows[k * w + x];
            out[z * w + x] = any;
        }
        return out;
    }

    private static int root(int[] parent, int i) {
        while (parent[i] != i) { parent[i] = parent[parent[i]]; i = parent[i]; }
        return i;
    }

    /**
     * La decision, d'apres le volume. {@code inside} : les colonnes dans le contour du lieu (z * w + x) ; {@code sheet} :
     * l'indice du sol que dit la fiche du lieu (il ne sert que si le lieu n'a aucun sol). Ce qu'est le lieu (base,
     * vides) se juge sur toute la decoupe ; le contour ne limite que ce qui est pose, et les colonnes qui votent pour
     * le niveau.
     */
    static Decision decide(Volume v, boolean[] inside, int sheet) {
        final int W = v.w, H = v.h, D = v.d, N = W * D;
        final short[] ids = v.ids;
        final Kinds k = kinds();
        final int M = DOUBLE - 1;
        final int[][] around = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};          // (dz, dx)

        // --- 1. La base : le couple (dessus du massif du bas, bloc de ce dessus) le plus frequent. Est-elle un reste ?
        java.util.HashMap<Integer, int[]> counts = new java.util.HashMap<>();
        for (int n = 0; n < N; n++) {
            if (!k.solid[ids[n] & M]) continue;
            int y = 1;
            while (y < H && k.solid[ids[y * N + n] & M]) y++;
            if (y >= H) continue;
            counts.computeIfAbsent(y * 65536 + (ids[(y - 1) * N + n] & 0xFFFF), key -> new int[1])[0]++;
        }
        int baseKey = -1, baseCount = 0;
        for (var e : counts.entrySet()) if (e.getValue()[0] > baseCount || (e.getValue()[0] == baseCount && e.getKey() < baseKey)) { baseCount = e.getValue()[0]; baseKey = e.getKey(); }
        final boolean based = baseKey >= 0 && (long) baseCount * BASE_DEN >= N;
        boolean leftover = false;
        int usage = 0, baseHeight = 0;
        if (based) {
            baseHeight = baseKey / 65536;
            final int material = baseKey % 65536;
            for (int n = 0; n < N; n++) {
                int floor = ids[(baseHeight - 1) * N + n] & 0xFFFF;
                if (floor == material) {
                    boolean on = false;
                    for (int y = baseHeight; y < Math.min(H, baseHeight + 3) && !on; y++) on = k.thing[ids[y * N + n] & M];
                    if (on) usage++;
                } else if (k.solid[floor & M]) usage++;
            }
            leftover = (long) usage * LEFTOVER_DEN <= N;
        }
        final int bottom = leftover ? baseHeight : 0;                       // sous cet indice, rien n'est au lieu

        // --- 2. Les colonnes : le premier massif [lo, g0), puis le plus bas vide couvert [g0, q), son couvercle [q, top).
        final int[] lo = new int[N], g0 = new int[N], q = new int[N], top = new int[N];
        final boolean[] has = new boolean[N], covered = new boolean[N], cand = new boolean[N];
        for (int n = 0; n < N; n++) {
            int y = bottom;
            while (y < H && !k.solid[ids[y * N + n] & M]) y++;
            lo[n] = y;
            has[n] = y < H;
            g0[n] = q[n] = top[n] = H;
            if (!has[n]) continue;
            while (y < H && k.solid[ids[y * N + n] & M]) y++;
            g0[n] = y;
            while (y < H && !k.solid[ids[y * N + n] & M]) y++;
            q[n] = y;
            covered[n] = g0[n] < H && q[n] < H;
            if (!covered[n]) continue;
            while (y < H && k.solid[ids[y * N + n] & M]) y++;
            top[n] = y;
            // Candidate : rien au sol (trois cases), aucune lumiere dans le vide.
            boolean used = false;
            for (int i = g0[n]; i < q[n] && !used; i++) {
                int id = ids[i * N + n] & M;
                used = k.light[id] || (i < g0[n] + 3 && k.thing[id]);
            }
            cand[n] = !used;
        }

        // --- 3. Les zones : candidates voisines dont les vides se recouvrent. Lesquelles sont du vide sous le sol ?
        final boolean[] empty = new boolean[N];
        final int[] label = new int[N];
        java.util.Arrays.fill(label, -1);
        int[] queue = new int[N];
        int zones = 0;
        for (int start = 0; start < N; start++) {
            if (!cand[start] || label[start] >= 0) continue;
            int head = 0, tail = 0;
            queue[tail++] = start;
            label[start] = zones;
            while (head < tail) {
                int n = queue[head++], x = n % W, z = n / W;
                for (int[] a : around) {
                    int nz = z + a[0], nx = x + a[1];
                    if (nz < 0 || nz >= D || nx < 0 || nx >= W) continue;
                    int o = nz * W + nx;
                    if (cand[o] && label[o] < 0 && Math.max(g0[n], g0[o]) < Math.min(q[n], q[o])) { label[o] = zones; queue[tail++] = o; }
                }
            }
            final int zone = zones++, area = tail;
            if (area < 4) continue;
            // L'epaisseur : la plus grande distance (en echiquier) d'une colonne de la zone a son dehors.
            int x1 = W, x2 = -1, z1 = D, z2 = -1;
            for (int i = 0; i < area; i++) { int x = queue[i] % W, z = queue[i] / W; x1 = Math.min(x1, x); x2 = Math.max(x2, x); z1 = Math.min(z1, z); z2 = Math.max(z2, z); }
            final int bw = x2 - x1 + 3, bd = z2 - z1 + 3;
            int[] dist = new int[bw * bd];
            for (int i = 0; i < area; i++) dist[(queue[i] / W - z1 + 1) * bw + (queue[i] % W - x1 + 1)] = Integer.MAX_VALUE / 2;
            for (int z = 1; z < bd - 1; z++) for (int x = 1; x < bw - 1; x++) {
                int i = z * bw + x;
                if (dist[i] == 0) continue;
                dist[i] = Math.min(dist[i], 1 + Math.min(Math.min(dist[i - bw - 1], dist[i - bw]), Math.min(dist[i - bw + 1], dist[i - 1])));
            }
            int thick = 0;
            for (int z = bd - 2; z >= 1; z--) for (int x = bw - 2; x >= 1; x--) {
                int i = z * bw + x;
                if (dist[i] == 0) continue;
                dist[i] = Math.min(dist[i], 1 + Math.min(Math.min(dist[i + bw + 1], dist[i + bw]), Math.min(dist[i + bw - 1], dist[i + 1])));
                thick = Math.max(thick, dist[i]);
            }
            if (thick < THICK_SHUT) continue;
            // Le pourtour : ouvert, ouverture (porte, vitre, grille), mur, ou espace couvert qui sert.
            long open = 0, doors = 0, walls = 0, used = 0;
            int soil = 0, topSoil = 0, plants = 0;
            int[] heights = new int[H + 1], covers = new int[H + 1];
            for (int i = 0; i < area; i++) {
                int n = queue[i], x = n % W, z = n / W;
                if (k.soil[ids[(g0[n] - 1) * N + n] & M]) soil++;
                if (k.soil[ids[(top[n] - 1) * N + n] & M]) topSoil++;
                if (k.plant[ids[q[n] * N + n] & M]) plants++;
                heights[q[n] - g0[n]]++;
                covers[top[n] - q[n]]++;
                for (int[] a : around) {
                    int nz = z + a[0], nx = x + a[1];
                    if (nz < 0 || nz >= D || nx < 0 || nx >= W) { open++; continue; }
                    int o = nz * W + nx;
                    if (label[o] == zone) continue;
                    if (covered[o] && !cand[o] && Math.max(g0[n], g0[o]) < Math.min(q[n], q[o])) { used++; continue; }
                    boolean passable = true, door = false;
                    for (int j = 0; j < 4; j++) {
                        int y = g0[n] + j;
                        if (y >= q[n]) break;
                        int id = ids[y * N + o] & M;
                        if (j < 2 && k.solid[id] && y >= bottom) passable = false;
                        if (k.opening[id]) door = true;
                    }
                    if (passable) open++; else if (door) doors++; else walls++;
                }
            }
            final long perimeter = open + doors + walls + used, closed = doors + walls + used;
            final int height = kth(heights, area, 1, 2), cover = kth(covers, area, 1, 2);
            final boolean room = perimeter > 0 && 4 * closed >= 3 * perimeter && (50 * doors >= perimeter || 10 * used >= perimeter);
            final boolean shut = 50 * (open + doors + used) <= perimeter;
            final boolean ground = 2 * topSoil >= area || cover >= COVER_THICK;
            final boolean pocket = shut && 2 * height <= 3 * thick;
            final boolean crawl = thick >= THICK && height <= CRAWL;
            final boolean wide = thick >= THICK_WIDE && thick >= 2 * height;
            final boolean under = thick >= THICK && ground && height <= 2 * thick;
            if (2 * soil >= area && height <= CEILING && !room && 2 * plants < area && (pocket || crawl || wide || under))
                for (int i = 0; i < area; i++) empty[queue[i]] = true;
        }
        queue = null;

        // --- 4. Le sol de chaque colonne (l'eau posee dessus compte), et le niveau : le quart bas, parmi les colonnes
        // dont le plus bas plein est pres du bas du lieu.
        final int[] ground = new int[N];
        int[] lows = new int[H + 1];
        long voters = 0;
        for (int n = 0; n < N; n++) {
            int g = empty[n] ? top[n] : g0[n];
            if (has[n] && g < H) while (g < H && k.liquid[ids[g * N + n] & M]) g++;
            ground[n] = g;
            if (inside[n] && has[n] && g < H) { lows[lo[n]]++; voters++; }
        }
        int sol = sheet;
        if (voters > 0) {
            final int near = kth(lows, voters, 1, 10) + CEILING;
            int[] grounds = new int[H + 1];
            long count = 0;
            for (int n = 0; n < N; n++) if (inside[n] && has[n] && ground[n] < H && lo[n] <= near) { grounds[ground[n]]++; count++; }
            sol = kth(grounds, count, 1, 4);
        }
        sol = Math.max(0, Math.min(H - 1, sol));

        // --- 5. Les colonnes non posees : rien a portee du niveau (ou rien que le reste de base).
        final Decision d = new Decision();
        d.sol = sol;
        d.skip = new boolean[N];
        d.dig = new short[N];
        d.capY = new short[N];
        d.capId = new short[N];
        final boolean[] put = new boolean[N];
        int skipped = 0;
        final int from = Math.max(0, Math.max(sol - 1 - HOLLOW, bottom));
        for (int n = 0; n < N; n++) {
            d.capY[n] = -1;
            d.dig[n] = (short) sol;
            if (!inside[n]) continue;
            boolean content = false;
            for (int y = from; y < H && !content; y++) content = !k.air[ids[y * N + n] & M];
            d.skip[n] = !content;
            put[n] = content;
            if (!content) skipped++;
        }

        // --- 6. Le comblement : les vides de l'etape 3 ; le dessous de ce qui flotte, si c'est massif et que cela
        // tient au corps du lieu (ou flotte tres bas).
        final int lowest = Math.max(0, sol - DEPTH);
        final boolean[] mass = new boolean[N], rooted = new boolean[N];
        final int[] crest = new int[N], parent = new int[N];
        for (int n = 0; n < N; n++) {
            parent[n] = n;
            if (!has[n]) continue;
            boolean green = k.plant[ids[lo[n] * N + n] & M];
            mass[n] = lo[n] <= sol + CEILING && !green;
            rooted[n] = !green;
            if (!rooted[n]) continue;
            // Le massif s'arrete au premier bloc vegetal : un arbre plante sur le lieu ne relie pas sa frondaison au lieu.
            int y = lo[n];
            while (y < H) { int id = ids[y * N + n] & M; if (!k.solid[id] || k.plant[id]) break; y++; }
            crest[n] = y;
        }
        final boolean[] massive = dilate(erode(erode(dilate(mass, W, D, 1), W, D, 1), W, D, 2), W, D, 2);
        for (int n = 0; n < N; n++) {
            if (!rooted[n]) continue;
            int x = n % W;
            if (x + 1 < W && rooted[n + 1] && lo[n] <= crest[n + 1] && lo[n + 1] <= crest[n]) parent[root(parent, n)] = root(parent, n + 1);
            if (n + W < N && rooted[n + W] && lo[n] <= crest[n + W] && lo[n + W] <= crest[n]) parent[root(parent, n)] = root(parent, n + W);
        }
        final boolean[] anchored = new boolean[N];
        for (int n = 0; n < N; n++) if (rooted[n] && lo[n] <= sol) anchored[root(parent, n)] = true;
        int voids = 0, floats = 0;
        for (int n = 0; n < N; n++) {
            if (!put[n]) continue;
            if (empty[n]) {
                voids++;
                for (int y = Math.max(g0[n], lowest); y < q[n]; y++) d.fill.set(y * N + n);
                // Sous un couvercle de verre, la derniere case reprend le sol du vide : on le voit a travers.
                int floor = ids[(g0[n] - 1) * N + n];
                if (k.glass[ids[q[n] * N + n] & M] && k.solid[floor & M] && q[n] - 1 >= lowest && q[n] > g0[n]) { d.capY[n] = (short) (q[n] - 1); d.capId[n] = (short) floor; }
            }
            if (has[n] && lo[n] > bottom) {
                boolean fixed = rooted[n] && anchored[root(parent, n)];
                boolean under = massive[n] && lo[n] > sol && lo[n] <= sol + (fixed ? CEILING : LOW);
                if (under) floats++;
                if (under || lo[n] <= sol) for (int y = lowest; y < lo[n]; y++) d.fill.set(y * N + n);
            }
            if (leftover) for (int y = lowest; y < bottom; y++) d.fill.set(y * N + n);       // le reste de base n'est pas copie
        }

        // --- 7. Les creux : la ou la case du niveau est libre, on descend jusqu'au sol du lieu, s'il est a portee.
        int dug = 0;
        if (sol > 0) for (int n = 0; n < N; n++) {
            if (!put[n]) continue;
            int at = sol * N + n;
            if ((k.solid[ids[at] & M] && sol >= bottom) || d.fill.get(at)) continue;
            int f = sol;
            while (f > 0) {
                int i = (f - 1) * N + n;
                if ((k.solid[ids[i] & M] && f - 1 >= bottom) || d.fill.get(i)) break;
                f--;
            }
            if (f >= 1 && f < sol && sol - f <= HOLLOW && f - 1 >= bottom && k.solid[ids[(f - 1) * N + n] & M] && !d.fill.get((f - 1) * N + n)) { d.dig[n] = (short) f; dug++; }
        }

        d.note = (!based ? "pas de sol plat" : leftover ? "sol plat residuel retire" : "sol plat utilise") + ", niveau " + (sol - sheet >= 0 ? "+" : "") + (sol - sheet) + " par rapport a la fiche, "
                + voids + " colonnes de vide comblees, " + floats + " sous ce qui flotte, " + skipped + " non posees, " + dug + " creusees";
        return d;
    }
}
