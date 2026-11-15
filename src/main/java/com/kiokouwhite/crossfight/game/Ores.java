package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Des minerais plus abondants dans le monde de jeu (regle "minerais" : 5 fois plus par defaut). Chaque troncon
 * du monde est traite une seule fois, la premiere fois qu'il est charge : chacun de ses filons est recopie
 * ailleurs dans le meme troncon, a peu pres a la meme profondeur, dans la roche. Le troncon garde la marque de
 * son passage : un monde deja explore est donc enrichi lui aussi, sans jamais l'etre deux fois.
 */
public final class Ores implements Listener {

    /** Chaque minerai de pierre et sa version d'ardoise des abimes. */
    private static final Map<Material, Material> DEEP = new EnumMap<>(Material.class);
    private static final Map<Material, Material> SHALLOW = new EnumMap<>(Material.class);
    /** La roche qu'un filon recopie peut remplacer. */
    private static final Set<Material> HOSTS = EnumSet.of(Material.STONE, Material.DEEPSLATE, Material.TUFF, Material.GRANITE, Material.DIORITE, Material.ANDESITE);
    /** Si la place visee n'est pas de la roche (une grotte, un autre filon, la surface) : on essaie autour, puis plus bas. */
    private static final int[] RETRY = {0, 1, -1, 2, -2, -3, 3, -4, -6, -8, -11, -14, -18};

    static {
        for (String ore : new String[]{"COAL", "IRON", "COPPER", "GOLD", "REDSTONE", "LAPIS", "DIAMOND", "EMERALD"}) {
            Material stone = Material.valueOf(ore + "_ORE"), deep = Material.valueOf("DEEPSLATE_" + ore + "_ORE");
            DEEP.put(stone, deep);
            SHALLOW.put(deep, stone);
        }
    }

    private final CrossfightPlugin plugin;
    private final NamespacedKey done;
    private final Set<Long> pending = new HashSet<>();

    public Ores(CrossfightPlugin plugin) {
        this.plugin = plugin;
        this.done = new NamespacedKey(plugin, "minerais");
    }

    /** Les troncons deja charges du monde de jeu (au demarrage du serveur, apres un changement de la regle). */
    public void boostLoaded() {
        for (Chunk c : plugin.game().world().getLoadedChunks()) boost(c);
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent e) {
        boost(e.getChunk());
    }

    private void boost(Chunk chunk) {
        World w = chunk.getWorld();
        int times = plugin.game().rules().value("minerais");
        if (times <= 1 || !w.equals(plugin.game().world()) || chunk.getPersistentDataContainer().has(done)) return;
        int cx = chunk.getX(), cz = chunk.getZ();
        long key = Chunk.getChunkKey(cx, cz);
        if (!pending.add(key)) return;
        // Le troncon est lu a part du serveur (sur une copie), puis les filons sont poses d'un coup.
        ChunkSnapshot snap = chunk.getChunkSnapshot(false, false, false);
        int min = w.getMinHeight(), max = w.getMaxHeight();
        long seed = w.getSeed();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<Integer, Material> plan = plan(snap, min, max, times, seed);
            Bukkit.getScheduler().runTask(plugin, () -> {
                pending.remove(key);
                if (!w.isChunkLoaded(cx, cz)) return;
                Chunk c = w.getChunkAt(cx, cz);
                for (var e : plan.entrySet()) {
                    int at = e.getKey();
                    var b = c.getBlock(at & 15, (at >> 8) + min, (at >> 4) & 15);
                    if (HOSTS.contains(b.getType())) b.setType(e.getValue(), false);
                }
                c.getPersistentDataContainer().set(done, PersistentDataType.INTEGER, times);
            });
        });
    }

    /** Ou poser quel minerai : chaque bloc de minerai du troncon recopie {@code times - 1} fois, decale. */
    private static Map<Integer, Material> plan(ChunkSnapshot snap, int min, int max, int times, long seed) {
        Random r = new Random(seed ^ (snap.getX() * 341873128712L + snap.getZ() * 132897987541L));
        // Un meme decalage pour tout le troncon : les filons gardent leur forme (ceux du bord reviennent de l'autre cote).
        int[][] shift = new int[times - 1][];
        for (int i = 0; i < shift.length; i++) shift[i] = new int[]{3 + r.nextInt(11), r.nextInt(7) - 3, 3 + r.nextInt(11)};
        Map<Integer, Material> plan = new HashMap<>();
        for (int y = min; y < max; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            Material m = snap.getBlockType(x, y, z);
            Material stone = DEEP.containsKey(m) ? m : SHALLOW.get(m);
            if (stone == null) continue;
            for (int[] s : shift) {
                placing:
                for (int side = 0; side < 3; side++) {
                    int tx = (x + s[0] + side) & 15, tz = (z + s[2] + (side == 2 ? 1 : 0)) & 15;
                    for (int up : RETRY) {
                        int ty = y + s[1] + up;
                        if (ty < min || ty >= max) continue;
                        int at = tx | tz << 4 | (ty - min) << 8;
                        Material host = snap.getBlockType(tx, ty, tz);
                        if (!HOSTS.contains(host) || plan.containsKey(at)) continue;
                        plan.put(at, host == Material.DEEPSLATE || host == Material.TUFF ? DEEP.get(stone) : stone);
                        break placing;
                    }
                }
            }
        }
        return plan;
    }
}
