package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.bukkit.persistence.PersistentDataType;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La carte de la map (/cf map) : un objet de carte qu'on tient en main et qui dit ou est quoi. Toute la map y tient,
 * d'un bord a l'autre de la bordure de depart, le nord en haut : chaque lieu pose est un rectangle a sa couleur, avec
 * une banniere et son nom ; l'arene de Ferox a son icone ; le cadre rouge est la bordure du moment, qui se resserre.
 * Le pointeur blanc est celui qui tient la carte (il tourne avec lui), les marques bleues sont ses coequipiers : la
 * carte est dessinee pour chaque joueur. Les adversaires n'y sont jamais.
 *
 * C'est une seule carte de Minecraft pour tout le serveur (son numero est garde dans config.yml, pour ne pas en
 * creer une a chaque demarrage), dont le dessin est fait ici au lieu d'etre celui du terrain.
 */
public final class WorldMap {

    private static final int SIZE = 128, MARGIN = 6;
    private static final Color PAPER = new Color(214, 194, 152), FRAME = new Color(120, 92, 58), GRID = new Color(196, 174, 132),
            AXIS = new Color(168, 140, 96), BORDER = new Color(200, 30, 30), ARENA = new Color(150, 96, 52), INK = new Color(60, 44, 28);

    /** Les couleurs des lieux, dans l'ordre : celle de la banniere, et celle du rectangle. */
    private static final MapCursor.Type[] BANNERS = {MapCursor.Type.BANNER_RED, MapCursor.Type.BANNER_BLUE, MapCursor.Type.BANNER_GREEN, MapCursor.Type.BANNER_YELLOW,
            MapCursor.Type.BANNER_PURPLE, MapCursor.Type.BANNER_ORANGE, MapCursor.Type.BANNER_CYAN, MapCursor.Type.BANNER_PINK, MapCursor.Type.BANNER_LIME,
            MapCursor.Type.BANNER_LIGHT_BLUE, MapCursor.Type.BANNER_MAGENTA, MapCursor.Type.BANNER_BROWN, MapCursor.Type.BANNER_WHITE, MapCursor.Type.BANNER_GRAY};
    private static final Color[] TINTS = {new Color(176, 46, 38), new Color(60, 68, 170), new Color(94, 124, 22), new Color(240, 200, 40), new Color(137, 50, 184),
            new Color(230, 128, 30), new Color(22, 156, 156), new Color(243, 139, 170), new Color(128, 199, 31), new Color(58, 179, 218), new Color(199, 78, 189),
            new Color(131, 84, 50), new Color(240, 240, 240), new Color(90, 90, 90)};
    private static final String[] TINT_TAGS = {"<red>", "<blue>", "<dark_green>", "<yellow>", "<dark_purple>", "<gold>", "<dark_aqua>", "<light_purple>", "<green>",
            "<aqua>", "<light_purple>", "<#835432>", "<white>", "<dark_gray>"};

    private final CrossfightPlugin plugin;
    private final NamespacedKey mark;
    private MapView view;

    public WorldMap(CrossfightPlugin plugin) {
        this.plugin = plugin;
        this.mark = new NamespacedKey(plugin, "carte");
    }

    /** La carte du serveur : retrouvee par son numero (ou creee), son dessin remplace par le notre. */
    private MapView view() {
        if (view != null) return view;
        int id = plugin.getConfig().getInt("carte.id", -1);
        @SuppressWarnings("deprecation")
        MapView v = id >= 0 ? Bukkit.getMap(id) : null;
        if (v == null) {
            v = Bukkit.createMap(plugin.game().world());
            plugin.getConfig().set("carte.id", v.getId());
            plugin.saveConfig();
        }
        for (MapRenderer r : new ArrayList<>(v.getRenderers())) v.removeRenderer(r);
        v.setTrackingPosition(false);
        v.setUnlimitedTracking(false);
        v.setLocked(true);
        v.addRenderer(new Painter());
        return view = v;
    }

    /** Au demarrage : les cartes deja distribuees retrouvent leur dessin (il n'est pas garde par le serveur). */
    public void attach() {
        if (plugin.getConfig().getInt("carte.id", -1) >= 0) view();
    }

    private boolean isMap(ItemStack it) {
        return it != null && it.getType() == Material.FILLED_MAP && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(mark, PersistentDataType.BYTE);
    }

    /** Les lieux de la map, dans un ordre stable (celui de leur nom) : chacun garde sa couleur. */
    private List<Structures.Placed> places(World w) {
        List<Structures.Placed> out = new ArrayList<>(plugin.structures().placed(w));
        out.sort(java.util.Comparator.comparing(Structures.Placed::name));
        return out;
    }

    /** Met la carte dans la main du joueur (ce qu'il tenait passe ailleurs dans son inventaire), et lui dit ce qu'on y voit. */
    public void give(Player p) {
        World w = plugin.game().world();
        List<Structures.Placed> places = places(w);
        Location arena = plugin.ferox().centre();
        ItemStack map = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) map.getItemMeta();
        meta.setMapView(view());
        meta.displayName(Msg.mm("<gold>Carte de la map").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < places.size(); i++) {
            Structures.Placed pl = places.get(i);
            lines.add(TINT_TAGS[i % TINT_TAGS.length] + "■ <white>" + plugin.structures().title(pl.name()) + " <gray>" + (pl.x() + pl.sx() / 2) + ", " + (pl.z() + pl.sz() / 2));
        }
        lines.add("<#96603a>● <white>Arene de Ferox <gray>" + arena.getBlockX() + ", " + arena.getBlockZ());
        lines.add("<gray>Pointeur blanc : toi. Marques bleues : ton equipe.");
        lines.add("<gray>Cadre rouge : la bordure.");
        for (String l : lines) lore.add(Msg.mm(l).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(mark, PersistentDataType.BYTE, (byte) 1);
        map.setItemMeta(meta);

        var inv = p.getInventory();
        // Une carte deja dans l'inventaire est remplacee (sa legende est a jour).
        for (int i = 0; i < inv.getSize(); i++) if (isMap(inv.getItem(i))) inv.setItem(i, null);
        ItemStack held = inv.getItemInMainHand();
        if (!held.getType().isAir()) {
            int free = inv.firstEmpty();
            if (free < 0) { Msg.send(p, "<red>Ton inventaire est plein : libere une place pour la carte."); return; }
            inv.setItem(free, held);
        }
        inv.setItemInMainHand(map);
        Msg.send(p, "<gold>La carte est dans ta main. <gray>Le nord est en haut ; elle couvre toute la map, d'un bord a l'autre de la bordure.");
        for (String l : lines.subList(0, lines.size() - 2)) Msg.send(p, l);
    }

    /** Dessine la carte pour chaque joueur qui la tient : le fond quand il change, les marques a chaque fois. */
    private final class Painter extends MapRenderer {
        /** Par joueur : ce que montrait le fond la derniere fois qu'on l'a dessine. */
        private final Map<UUID, Long> drawn = new HashMap<>();

        Painter() { super(true); }

        @Override
        public void render(MapView map, MapCanvas canvas, Player p) {
            World w = plugin.game().world();
            int border = Math.max(50, plugin.game().rules().value("bordure"));
            double scale = (SIZE - 2.0 * MARGIN) / (2.0 * border);
            List<Structures.Placed> places = places(w);
            Location arena = plugin.ferox().centre();
            Location middle = w.getWorldBorder().getCenter();
            double now = Math.min(w.getWorldBorder().getSize() / 2, border * 1.05);
            long stamp = 31L * border + w.getName().hashCode();
            for (Structures.Placed pl : places) stamp = stamp * 31 + pl.hashCode();
            stamp = stamp * 31 + arena.getBlockX() * 7919L + arena.getBlockZ();
            stamp = stamp * 31 + Math.round(now * scale) * 977L + Math.round(middle.getX() * scale) * 131L + Math.round(middle.getZ() * scale);
            if (!Long.valueOf(stamp).equals(drawn.put(p.getUniqueId(), stamp))) paint(canvas, border, scale, places, arena, middle, now);

            MapCursorCollection cursors = canvas.getCursors();
            while (cursors.size() > 0) cursors.removeCursor(cursors.getCursor(0));
            for (int i = 0; i < places.size(); i++) {
                Structures.Placed pl = places.get(i);
                cursors.addCursor(new MapCursor(at(pl.x() + pl.sx() / 2.0, scale), at(pl.z() + pl.sz() / 2.0, scale), (byte) 8, BANNERS[i % BANNERS.length], true,
                        Component.text(plugin.structures().title(pl.name()))));
            }
            cursors.addCursor(new MapCursor(at(arena.getX(), scale), at(arena.getZ(), scale), (byte) 8, MapCursor.Type.MANSION, true, Component.text("Arene de Ferox")));
            if (!p.getWorld().equals(w)) return;
            // Les coequipiers, puis le joueur par-dessus.
            Team team = plugin.teams().teamOf(p);
            if (team != null) for (Player mate : team.onlineMembers()) {
                if (mate.equals(p) || !mate.getWorld().equals(w) || mate.isDead()) continue;
                cursors.addCursor(new MapCursor(at(mate.getLocation().getX(), scale), at(mate.getLocation().getZ(), scale), (byte) 8, MapCursor.Type.BLUE_MARKER, true,
                        Component.text(mate.getName())));
            }
            float yaw = p.getLocation().getYaw();
            yaw += yaw < 0 ? -8 : 8;
            cursors.addCursor(new MapCursor(at(p.getLocation().getX(), scale), at(p.getLocation().getZ(), scale), (byte) ((int) (yaw * 16 / 360) & 15), MapCursor.Type.PLAYER, true));
        }

        /** Une coordonnee du monde, en position de marque sur la carte (de -128 a 127, deux par pixel). */
        private byte at(double block, double scale) {
            return (byte) Math.max(-128, Math.min(127, Math.round((SIZE / 2.0 + block * scale) * 2 - 128)));
        }

        private int px(double block, double scale) {
            return (int) Math.round(SIZE / 2.0 + block * scale);
        }

        private void dot(MapCanvas canvas, int x, int y, Color c) {
            if (x >= 0 && y >= 0 && x < SIZE && y < SIZE) canvas.setPixelColor(x, y, c);
        }

        private void paint(MapCanvas canvas, int border, double scale, List<Structures.Placed> places, Location arena, Location middle, double now) {
            int low = px(-border, scale), high = px(border, scale);
            for (int x = 0; x < SIZE; x++) for (int y = 0; y < SIZE; y++) canvas.setPixelColor(x, y, x < low || y < low || x > high || y > high ? FRAME : PAPER);
            // Un quadrillage : quatre cases par demi-cote ; les deux axes, un peu plus marques.
            for (int k = -3; k <= 3; k++) {
                int line = px(k * border / 4.0, scale);
                for (int i = low; i <= high; i++) { dot(canvas, line, i, k == 0 ? AXIS : GRID); dot(canvas, i, line, k == 0 ? AXIS : GRID); }
            }
            // L'arene : un disque.
            int ax = px(arena.getX(), scale), az = px(arena.getZ(), scale), ar = Math.max(3, (int) Math.round(92 * scale));
            for (int dx = -ar; dx <= ar; dx++) for (int dz = -ar; dz <= ar; dz++) if (dx * dx + dz * dz <= ar * ar) dot(canvas, ax + dx, az + dz, ARENA);
            // Les lieux : un rectangle a leur couleur (eclaircie), cerne de cette couleur.
            for (int i = 0; i < places.size(); i++) {
                Structures.Placed pl = places.get(i);
                Color edge = TINTS[i % TINTS.length];
                Color fill = new Color((edge.getRed() + PAPER.getRed()) / 2, (edge.getGreen() + PAPER.getGreen()) / 2, (edge.getBlue() + PAPER.getBlue()) / 2);
                int x1 = px(pl.x(), scale), x2 = Math.max(x1 + 2, px(pl.x() + pl.sx(), scale)), z1 = px(pl.z(), scale), z2 = Math.max(z1 + 2, px(pl.z() + pl.sz(), scale));
                for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) dot(canvas, x, z, x == x1 || x == x2 || z == z1 || z == z2 ? edge : fill);
            }
            // La bordure du moment : un cadre rouge.
            int bx1 = px(middle.getX() - now, scale), bx2 = px(middle.getX() + now, scale), bz1 = px(middle.getZ() - now, scale), bz2 = px(middle.getZ() + now, scale);
            for (int i = bx1; i <= bx2; i++) { dot(canvas, i, bz1, BORDER); dot(canvas, i, bz2, BORDER); }
            for (int i = bz1; i <= bz2; i++) { dot(canvas, bx1, i, BORDER); dot(canvas, bx2, i, BORDER); }
            // Le nord, en haut.
            for (int x = SIZE / 2 - 4; x <= SIZE / 2 + 4; x++) for (int y = 0; y < MARGIN + 3 && y < 9; y++) dot(canvas, x, y, FRAME);
            canvas.drawText(SIZE / 2 - 2, 0, MinecraftFont.Font, "§" + org.bukkit.map.MapPalette.matchColor(PAPER) + ";N");
        }
    }
}
