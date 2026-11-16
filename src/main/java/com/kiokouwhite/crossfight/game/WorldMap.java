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
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.map.MinecraftFont;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

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
 *
 * Pour un admin, la carte sert aussi a se deplacer : un clic droit, carte en main, fait apparaitre une croix rouge
 * dessus. Minecraft n'a pas de curseur sur une carte tenue en main : c'est le regard qui en tient lieu. Tourner la
 * tete a droite pousse la croix vers l'est, baisser les yeux la pousse vers le sud ; un clic teleporte la ou elle est.
 */
public final class WorldMap implements Listener {

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

    /**
     * La visee. La carte tenue a deux mains n'est a plat devant les yeux que le regard baisse : la croix la parcourt
     * de haut en bas entre ces deux inclinaisons (en degres), et d'un bord a l'autre sur le meme angle.
     */
    private static final float LOOK_TOP = 46f, LOOK_BOTTOM = 89f, LOOK_MIDDLE = (LOOK_TOP + LOOK_BOTTOM) / 2;
    /** Positions de marque (deux par pixel de la carte) parcourues par degre de regard. */
    private static final float UNITS = 256f / (LOOK_BOTTOM - LOOK_TOP);
    /** En ticks : le temps avant qu'un clic compte (celui qui ouvre la visee ne la valide pas), et la duree d'une visee. */
    private static final int ARMED = 10, PATIENCE = 20 * 90;

    private final CrossfightPlugin plugin;
    private final NamespacedKey mark;
    private MapView view;

    /** La visee d'un admin : son regard d'avant (rendu a la fin), le lacet qui met la croix au milieu, et la croix. */
    private static final class Aim {
        final float yaw, pitch;
        float zero;
        final int since;
        /** La croix, en position de marque : de -128 (ouest, nord) a 127 (est, sud). */
        int u, v;
        int sent = -100, told = -100;

        Aim(float yaw, float pitch, int since) { this.yaw = yaw; this.pitch = pitch; this.zero = yaw; this.since = since; }
    }

    private final Map<UUID, Aim> aims = new HashMap<>();
    /** Le tick de la fin de la derniere visee de chacun : un clic maintenu ne la rouvre pas aussitot. */
    private final Map<UUID, Integer> rested = new HashMap<>();
    private BukkitTask tracking;

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
        if (admin(p)) lore.add(Msg.mm("<yellow>Admin : clic droit pour viser, clic pour s'y teleporter.").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
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
        if (places.isEmpty()) {
            // Une carte sans lieux : on le dit, et a un admin pourquoi (les lieux ne viennent pas avec le plugin).
            Msg.send(p, "<gray>Aucun lieu n'est pose sur cette map.");
            String advice = admin(p) ? plugin.structures().advice(w) : null;
            if (advice != null) Msg.send(p, "<yellow>Admin : <gray>" + advice);
        }
        if (admin(p)) Msg.send(p, "<yellow>Admin : <white>clic droit<gray> avec la carte pour viser un endroit (une croix rouge suit ton regard), <white>clic<gray> pour t'y teleporter.");
    }

    // ------------------------------------------------------------------------------------------------- la visee

    private boolean admin(Player p) {
        return p.hasPermission("crossfight.admin");
    }

    private boolean holds(Player p) {
        return isMap(p.getInventory().getItemInMainHand()) || isMap(p.getInventory().getItemInOffHand());
    }

    private double scale() {
        return (SIZE - 2.0 * MARGIN) / (2.0 * Math.max(50, plugin.game().rules().value("bordure")));
    }

    /** Ou est la croix dans le monde : {x, z}, jamais au-dela de la bordure de depart. */
    private int[] pointed(Aim aim) {
        int border = Math.max(50, plugin.game().rules().value("bordure"));
        double scale = scale();
        return new int[]{(int) Math.max(-border + 1, Math.min(border - 1, Math.round(aim.u / (2 * scale)))),
                (int) Math.max(-border + 1, Math.min(border - 1, Math.round(aim.v / (2 * scale))))};
    }

    /** Ce qu'il y a a cet endroit de la map : le titre d'un lieu, l'arene, ou rien. */
    private String nameAt(World w, int x, int z) {
        for (Structures.Placed pl : places(w)) if (x >= pl.x() && x < pl.x() + pl.sx() && z >= pl.z() && z < pl.z() + pl.sz()) return plugin.structures().title(pl.name());
        Location arena = plugin.ferox().centre();
        return Math.hypot(x - arena.getX(), z - arena.getZ()) <= 92 ? "Arene de Ferox" : null;
    }

    /** Un clic droit avec la carte ouvre la visee (admins) ; pendant la visee, n'importe quel clic la valide. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL) return;
        Player p = e.getPlayer();
        Aim aim = aims.get(p.getUniqueId());
        if (aim != null) {
            e.setCancelled(true);
            // Jeter la carte fait aussi un geste du bras : sans elle, ce n'est pas un clic sur la carte.
            if (holds(p) && Bukkit.getCurrentTick() - aim.since >= ARMED) jump(p, aim);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isMap(e.getItem()) || !admin(p) || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        if (Bukkit.getCurrentTick() - rested.getOrDefault(p.getUniqueId(), -100) < ARMED) { e.setCancelled(true); return; }
        // Une porte, un coffre, un levier : le clic est pour eux.
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && e.getClickedBlock().getType().isInteractable()) return;
        e.setCancelled(true);
        aim(p);
    }

    /** Pendant la visee, un coup donne a ce qui passe devant est un clic comme un autre. */
    @EventHandler(priority = EventPriority.LOW)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        Aim aim = aims.get(p.getUniqueId());
        if (aim == null) return;
        e.setCancelled(true);
        if (holds(p) && Bukkit.getCurrentTick() - aim.since >= ARMED) jump(p, aim);
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (!e.isSneaking()) return;
        Aim aim = aims.remove(e.getPlayer().getUniqueId());
        if (aim != null) drop(e.getPlayer(), aim, "<gray>Visee annulee.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        aims.remove(e.getPlayer().getUniqueId());
        rested.remove(e.getPlayer().getUniqueId());
    }

    private void aim(Player p) {
        Location at = p.getLocation();
        Aim aim = new Aim(at.getYaw(), at.getPitch(), Bukkit.getCurrentTick());
        aims.put(p.getUniqueId(), aim);
        // Le regard baisse sur la carte : elle se leve en entier, la croix en son milieu.
        if (at.getPitch() < LOOK_TOP + 3 || at.getPitch() > LOOK_BOTTOM - 3) p.setRotation(at.getYaw(), LOOK_MIDDLE);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
        Msg.send(p, "<gold>Visee sur la carte. <gray>La <red>croix rouge<gray> suit ton regard : <white>clic<gray> pour t'y teleporter, <white>accroupi<gray> pour annuler.");
        if (tracking == null) tracking = Bukkit.getScheduler().runTaskTimer(plugin, this::track, 1L, 1L);
    }

    /** A chaque tick : la croix suit le regard de ceux qui visent ; la carte leur est renvoyee des qu'elle bouge. */
    private void track() {
        int now = Bukkit.getCurrentTick();
        for (var it = aims.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            Player p = Bukkit.getPlayer(entry.getKey());
            Aim aim = entry.getValue();
            if (p == null) { it.remove(); continue; }
            String end = !holds(p) ? "<gray>Visee annulee : la carte n'est plus dans ta main." : !admin(p) ? "<gray>Visee annulee."
                    : now - aim.since > PATIENCE ? "<gray>Visee annulee : trop longue." : null;
            if (end != null) { it.remove(); drop(p, aim, end); continue; }
            Location at = p.getLocation();
            // Le lacet, ramene entre -180 et 180 autour de celui qui met la croix au milieu. Pousser la croix contre
            // un bord deplace ce milieu : des qu'on revient, elle repart (comme une souris au bord de l'ecran).
            float turn = at.getYaw() - aim.zero;
            turn -= 360f * Math.round(turn / 360f);
            float edge = 127.4f / UNITS;
            if (turn > edge) { aim.zero += turn - edge; turn = edge; }
            if (turn < -edge) { aim.zero += turn + edge; turn = -edge; }
            int u = Math.max(-128, Math.min(127, Math.round(turn * UNITS)));
            int v = Math.max(-128, Math.min(127, Math.round((at.getPitch() - LOOK_MIDDLE) * UNITS)));
            boolean moved = u != aim.u || v != aim.v;
            aim.u = u;
            aim.v = v;
            if (moved || now - aim.sent >= 20) { aim.sent = now; p.sendMap(view()); }
            if ((moved && now - aim.told >= 2) || now - aim.told >= 20) { aim.told = now; tell(p, aim); }
        }
        if (aims.isEmpty() && tracking != null) { tracking.cancel(); tracking = null; }
    }

    /** Dans la barre d'action : ou pointe la croix, et ce qu'il y a la. */
    private void tell(Player p, Aim aim) {
        World w = plugin.game().world();
        int[] at = pointed(aim);
        String name = nameAt(w, at[0], at[1]);
        boolean out = !w.getWorldBorder().isInside(new Location(w, at[0] + 0.5, 64, at[1] + 0.5));
        Msg.actionBar(p, "<white>x " + at[0] + "  z " + at[1] + (name != null ? " <gray>- <gold>" + name : "") + (out ? " <gray>- <red>hors de la bordure" : "")
                + " <gray>- clic : s'y teleporter");
    }

    /** La visee s'arrete sans teleportation : le regard d'avant est rendu, la croix quitte la carte. */
    private void drop(Player p, Aim aim, String why) {
        rested.put(p.getUniqueId(), Bukkit.getCurrentTick());
        p.setRotation(aim.yaw, aim.pitch);
        p.sendMap(view());
        Msg.actionBar(p, why);
    }

    /** Le clic : teleporte la ou est la croix, au sol de l'endroit (ou tout pres, s'il n'y en a pas ou s'il est dangereux). */
    private void jump(Player p, Aim aim) {
        aims.remove(p.getUniqueId());
        rested.put(p.getUniqueId(), Bukkit.getCurrentTick());
        World w = plugin.game().world();
        int[] at = pointed(aim);
        String name = nameAt(w, at[0], at[1]);
        Msg.actionBar(p, "<gray>Teleportation...");
        w.getChunkAtAsync(at[0] >> 4, at[1] >> 4).thenAccept(chunk -> {
            if (!p.isOnline()) return;
            Block ground = landing(w, at[0], at[1], p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE);
            if (ground == null) {
                drop(p, aim, "<gray>Pas de sol sur ou se poser a cet endroit.");
                return;
            }
            Location to = new Location(w, ground.getX() + 0.5, ground.getY() + 1, ground.getZ() + 0.5, aim.yaw, aim.pitch);
            p.setFallDistance(0);
            p.teleport(to);
            p.sendMap(view());
            p.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.3f);
            Msg.send(p, "<green>Teleporte" + (name != null ? " a <gold>" + name + "<green>" : "") + " : <white>" + to.getBlockX() + ", " + to.getBlockY() + ", " + to.getBlockZ() + "<green>.");
        });
    }

    /**
     * Le bloc sur lequel se poser, a cet endroit ou a quelques blocs de la : celui de {@link #stand}, sauf s'il n'y en
     * a pas (une faille jusqu'au vide) ou s'il blesse un joueur en survie (lave, magma, feu, cactus).
     */
    private Block landing(World w, int x, int z, boolean fragile) {
        for (int r = 0; r <= 8; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            Block b = stand(w, x + dx, z + dz);
            if (b == null) continue;
            if (fragile && (HARMFUL.contains(b.getType()) || HARMFUL.contains(b.getRelative(BlockFace.UP).getType()))) continue;
            return b;
        }
        return null;
    }

    /**
     * Le bloc qui porte, dans cette colonne. En pleine nature, le plus haut. Dans un lieu ou dans l'arene, le plus
     * haut est souvent un toit, un dome, le haut d'une tour : c'est le premier endroit ou l'on tient debout a partir
     * du niveau du sol du lieu (sa rue, son rez-de-chaussee) ; sur de l'eau, on se pose a sa surface.
     */
    private Block stand(World w, int x, int z) {
        int top = w.getHighestBlockYAt(x, z);
        if (top < w.getMinHeight()) return null;
        Integer floor = floorAt(w, x, z);
        if (floor != null) for (int y = Math.max(w.getMinHeight(), floor - 2); y <= top; y++) {
            Block b = w.getBlockAt(x, y, z);
            if ((b.isLiquid() || !b.isPassable()) && free(b.getRelative(BlockFace.UP)) && free(b.getRelative(BlockFace.UP, 2))) return b;
        }
        Block b = w.getBlockAt(x, top, z);
        return b.getType().isAir() ? null : b;
    }

    private boolean free(Block b) {
        return b.isPassable() && !b.isLiquid();
    }

    /** Le niveau du sol de ce qui est bati a cet endroit : celui du lieu, celui de la fosse de l'arene ; null en pleine nature. */
    private Integer floorAt(World w, int x, int z) {
        for (Structures.Placed pl : places(w)) if (x >= pl.x() && x < pl.x() + pl.sx() && z >= pl.z() && z < pl.z() + pl.sz()) return plugin.structures().level(pl);
        Location arena = plugin.ferox().centre();
        return Math.hypot(x - arena.getX(), z - arena.getZ()) <= 92 ? arena.getBlockY() : null;
    }

    private static final java.util.Set<Material> HARMFUL = java.util.Set.of(Material.LAVA, Material.MAGMA_BLOCK, Material.FIRE, Material.SOUL_FIRE, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.CACTUS, Material.SWEET_BERRY_BUSH, Material.POWDER_SNOW, Material.WITHER_ROSE, Material.POINTED_DRIPSTONE);

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
            Aim aim = aims.get(p.getUniqueId());
            if (!p.getWorld().equals(w)) {
                if (aim != null) cursors.addCursor(new MapCursor((byte) aim.u, (byte) aim.v, (byte) 8, MapCursor.Type.RED_X, true));
                return;
            }
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
            // La croix de visee d'un admin, par-dessus tout le reste.
            if (aim != null) cursors.addCursor(new MapCursor((byte) aim.u, (byte) aim.v, (byte) 8, MapCursor.Type.RED_X, true));
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
