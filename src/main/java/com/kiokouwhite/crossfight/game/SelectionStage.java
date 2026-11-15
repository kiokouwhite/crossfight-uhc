package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Licence;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.util.ArenaWorlds;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Puppet;
import com.kiokouwhite.crossfight.util.Skins;
import com.kiokouwhite.crossfight.util.Sounds;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L'ecran de choix du personnage, en 3D : chaque equipe est emmenee devant un mur de panneaux inclines,
 * un par personnage de sa licence (illustration, nom sur un bandeau a sa couleur, icone de sa voie, logo
 * de la licence au-dessus). Le joueur vise un panneau avec son viseur : il s'illumine (halo a la couleur
 * du personnage, contour lumineux, il grandit et avance) et ses capacites s'affichent ; un clic le choisit.
 * Les choix des coequipiers apparaissent en direct ("Choisi par ...").
 *
 * Chaque joueur a ses propres affichages (invisibles pour les autres) : l'effet de survol ne suit que
 * son regard. Les images viennent du pack (tools/build_selection.py) ; un personnage sans illustration
 * est montre avec son skin : son modele articule du pack, pose a plat sur une carte a sa couleur.
 */
public final class SelectionStage implements Listener {

    public static final String WORLD = "crossfight_selection";
    private static final NamespacedKey ITEM_KEY = new NamespacedKey("crossfight", "selection_item");

    // Geometrie d'une carte, en blocs : la meme que tools/build_selection.py.
    private static final double PH = 6.2, QW = PH * 512 / 992, SLANT = 0.96, PW = QW - SLANT;
    private static final double GW = QW * 1.16, GH = PH * 1.07;
    /** La bande de couleur (haut), le bandeau sombre (haut), en fractions de la hauteur depuis le haut. */
    private static final double BAND_TOP = 0.70, STRIP_TOP = 0.885;
    /** Ecart entre deux cartes (presque collees). */
    private static final double STEP = PW + 0.08;
    /** Hauteur des yeux d'un joueur debout. */
    private static final double EYE = 1.62;

    private final CrossfightPlugin plugin;
    private final Map<String, Stage> stages = new HashMap<>();
    private final Map<UUID, Viewer> viewers = new HashMap<>();
    private final Map<String, Color> colors = new HashMap<>();
    /** Le nom court ecrit sur la carte (selection.txt) ; a defaut, le nom complet du personnage. */
    private final Map<String, String> names = new HashMap<>();
    private BukkitTask task;
    private int nextIndex;

    public SelectionStage(CrossfightPlugin plugin) {
        this.plugin = plugin;
        try (InputStream in = plugin.getResource("selection.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line; (line = r.readLine()) != null; ) {
                    String[] t = line.trim().split("\\s+");
                    if (t.length >= 3 && !t[0].startsWith("#") && t[t.length - 1].startsWith("#")) {
                        colors.put(t[0], Color.fromRGB(Integer.parseInt(t[t.length - 1].substring(1), 16)));
                        names.put(t[0], String.join(" ", java.util.Arrays.copyOfRange(t, 1, t.length - 1)));
                    }
                }
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("selection.txt illisible : " + ex);
        }
    }

    // Cadrage (config "selection.") : les cartes sont un mur plat, face au joueur, comme une affiche. Les valeurs
    // par defaut remplissent l'ecran d'un champ de vision normal (70) : le haut des cartes sous la barre du haut,
    // les noms et les icones de voie au-dessus de la barre de vie.
    /** Distance du mur de cartes. */
    private double dist() { return plugin.getConfig().getDouble("selection.distance", 4.9); }
    /** Hauteur du centre des cartes au-dessus des pieds du joueur. */
    private double height() { return plugin.getConfig().getDouble("selection.hauteur", 1.74); }
    /** Regard de depart (0 : droit devant ; negatif : vers le haut). */
    private float gaze() { return (float) plugin.getConfig().getDouble("selection.regard", 0.0); }

    /** Une carte de personnage sur le mur d'un joueur. */
    private record Slot(Character character, Location center, double theta) {
        Vector normal() { return new Vector(-Math.sin(theta), 0, Math.cos(theta)); }
        Vector right() { return new Vector(Math.cos(theta), 0, Math.sin(theta)); }
        /** Un point du panneau : dx vers la droite, dy vers le haut, dz vers les joueurs. */
        Location at(double dx, double dy, double dz) {
            return center.clone().add(right().multiply(dx)).add(0, dy, 0).add(normal().multiply(dz));
        }
    }

    /** La scene d'une equipe (ou d'un testeur qui l'essaie seul). */
    private final class Stage {
        final String key;
        final Team team;
        final Licence licence;
        final Location origin;
        final List<Character> characters;
        /** Distance du mur et hauteur du centre des cartes, fixees a l'ouverture de la scene. */
        final double dist, cy;
        /** personnage -> joueur qui l'a pris. */
        final Map<String, UUID> picks = new HashMap<>();
        final boolean preview;

        Stage(String key, Team team, Licence licence, Location origin, boolean preview) {
            this.key = key;
            this.team = team;
            this.licence = licence;
            this.origin = origin;
            this.preview = preview;
            this.characters = List.copyOf(licence.characters());
            this.dist = dist();
            this.cy = height();
            build();
        }

        /** Le sol des joueurs, et des murs invisibles autour. */
        private void build() {
            World w = origin.getWorld();
            int ox = origin.getBlockX(), oy = origin.getBlockY() - 1, oz = origin.getBlockZ();
            for (int x = -5; x <= 5; x++) {
                for (int z = -2; z <= 2; z++) {
                    boolean edge = Math.abs(x) == 5 || Math.abs(z) == 2;
                    w.getBlockAt(ox + x, oy, oz + z).setType(edge ? Material.POLISHED_BLACKSTONE_BRICKS : Material.BLACK_CONCRETE, false);
                    for (int y = 1; y <= 3; y++) w.getBlockAt(ox + x, oy + y, oz + z).setType(edge ? Material.BARRIER : Material.AIR, false);
                }
            }
            // Des lumieres invisibles le long du mur de cartes : sans elles, un mannequin (personnage sans modele
            // dans le pack) serait dans le noir. Les affichages, eux, ont leur propre lumiere.
            int wz = (int) Math.floor(origin.getZ() - dist + 0.3);
            for (int x = -7; x <= 7; x++) for (int y = 1; y <= 5; y += 2) w.getBlockAt(ox + x, oy + y, wz).setType(Material.LIGHT, false);
        }

        List<Viewer> viewers() {
            List<Viewer> out = new ArrayList<>();
            for (Viewer v : SelectionStage.this.viewers.values()) if (v.stage == this) out.add(v);
            return out;
        }
    }

    /** Ce que voit un joueur : ses affichages, le panneau qu'il vise. */
    private final class Viewer {
        final Player player;
        final Stage stage;
        final Location spot;
        /** Ses cartes : un mur plat a lui, centre sur sa place (chaque joueur voit l'affiche bien en face). */
        final List<Slot> slots = new ArrayList<>();
        final Map<String, Panel> panels = new LinkedHashMap<>();
        final List<Entity> extra = new ArrayList<>();
        BossBar bar;
        String hovered;
        /** Le survol ne commence qu'a ce tick : a l'ouverture, on voit d'abord l'affiche entiere. */
        int hoverFrom;
        long lastPick;
        /** Ce que le joueur avait dans sa premiere case (un testeur a deja son arme) : rendu a la fermeture. */
        ItemStack replaced;

        Viewer(Player player, Stage stage, Location spot) {
            this.player = player;
            this.stage = stage;
            this.spot = spot;
            int n = stage.characters.size();
            for (int i = 0; i < n; i++) {
                Location c = spot.clone().add((i - (n - 1) / 2.0) * STEP, stage.cy, -stage.dist);
                c.setYaw(0);
                c.setPitch(0);
                slots.add(new Slot(stage.characters.get(i), c, 0));
            }
        }

        Slot slot(String id) {
            for (Slot s : slots) if (s.character.id().equals(id)) return s;
            return null;
        }

        /** Un point devant le joueur : dx vers sa droite, dy au-dessus de ses pieds, a `depth` blocs devant lui. */
        Location front(double dx, double dy, double depth) {
            Location l = spot.clone().add(dx, dy, -depth);
            l.setYaw(0);
            l.setPitch(0);
            return l;
        }

        void remove() {
            for (Panel p : panels.values()) p.remove();
            for (Entity e : extra) if (e.isValid()) e.remove();
            panels.clear();
            extra.clear();
            if (bar != null) player.hideBossBar(bar);
        }
    }

    /** Ce qui est pose sur une carte sans illustration (personnage, nom, icone) : sa place par rapport au centre de la carte. */
    private record Layer(Entity entity, double dx, double dy, double dz) {}

    /** Les affichages d'un panneau, pour un joueur. */
    private static final class Panel {
        /** La carte entiere (pack prive), ou la carte vide teinte (sans illustration). */
        ItemDisplay card, plate, glow, icon;
        /** Sans illustration : le personnage en statue (son skin du pack), ou a defaut en mannequin. */
        List<ItemDisplay> statue = List.of();
        Mannequin body;
        TextDisplay name, voie, info, stamp;
        /** Ce qui suit la carte quand elle avance et grandit. */
        final List<Layer> layers = new ArrayList<>();

        void remove() {
            for (Entity e : new Entity[]{card, plate, glow, icon, body, name, voie, info, stamp}) if (e != null && e.isValid()) e.remove();
            for (ItemDisplay d : statue) if (d.isValid()) d.remove();
        }

        List<Display> displays() {
            List<Display> out = new ArrayList<>(statue);
            for (Display d : new Display[]{card, plate, icon, name, voie}) if (d != null) out.add(d);
            return out;
        }
    }

    // ---------------------------------------------------------------- ouverture

    /** Un monde vide, de nuit : les panneaux, en pleine lumiere, se detachent sur le ciel etoile. */
    public static World world() {
        World w = ArenaWorlds.get(WORLD);
        if (w.getTime() != 18000) w.setTime(18000);
        return w;
    }

    /** La scene 3D est-elle activee (config "selection.scene-3d", vrai par defaut) ? */
    public boolean enabled() {
        return plugin.getConfig().getBoolean("selection.scene-3d", true);
    }

    public boolean isViewing(Player p) {
        return viewers.containsKey(p.getUniqueId());
    }

    /**
     * Le joueur choisit son personnage : la scene de son equipe en 3D, ou le menu d'inventaire si la scene
     * est desactivee ou n'a rien a montrer.
     */
    public void open(Player p) {
        CfPlayer cf = plugin.players().get(p);
        Team team = cf.team();
        if (!enabled() || team == null || team.licence() == null || team.licence().characters().isEmpty()) {
            plugin.characters().selectMenu(plugin).open(p);
            return;
        }
        Stage stage = stages.computeIfAbsent("team:" + team.id(), k -> new Stage(k, team, team.licence(), originFor(nextIndex++), false));
        for (Player mate : team.onlineMembers()) {
            CfPlayer mcf = plugin.players().get(mate);
            if (mcf.character() != null) stage.picks.putIfAbsent(mcf.character().id(), mate.getUniqueId());
        }
        show(p, stage);
    }

    /** Essai de l'ecran en mode test (/cf test selection) : un choix fait changer le testeur de personnage. */
    public void openPreview(Player p, Licence licence) {
        close(p, false);
        Stage old = stages.remove("test:" + p.getUniqueId());
        if (old != null) clearStage(old);
        Stage stage = new Stage("test:" + p.getUniqueId(), null, licence, originFor(nextIndex++), true);
        stages.put(stage.key, stage);
        show(p, stage);
        Msg.send(p, "<aqua>Ecran de selection de <gold>" + licence.name() + "</gold>. <gray>Vise un personnage, clic pour le choisir ; <white>/cf test selection</white> pour revenir.");
    }

    private Location originFor(int index) {
        World w = world();
        return new Location(w, index * 64 + 0.5, ArenaWorlds.FLOOR_Y + 1, 0.5, 180f, 0f);
    }

    /** La place d'un joueur dans la rangee de son equipe. */
    private Location spotFor(Stage stage, Player p) {
        List<UUID> line = new ArrayList<>();
        if (stage.team != null) line.addAll(stage.team.members());
        if (!line.contains(p.getUniqueId())) line.add(p.getUniqueId());
        int i = line.indexOf(p.getUniqueId()), n = line.size();
        Location s = stage.origin.clone().add((i - (n - 1) / 2.0) * 1.3, 0, 0);
        s.setYaw(180f);
        s.setPitch(gaze());
        return s;
    }

    private void show(Player p, Stage stage) {
        Viewer old = viewers.remove(p.getUniqueId());
        if (old != null) old.remove();
        Viewer v = new Viewer(p, stage, spotFor(stage, p));
        viewers.put(p.getUniqueId(), v);
        p.teleport(v.spot);
        p.setFallDistance(0);
        if (p.getGameMode() != GameMode.SPECTATOR) p.setGameMode(GameMode.ADVENTURE);
        // Immobile, mais sans resserrer sa vue : les cartes sont cadrees pour le champ de vision normal.
        Effects.hold(p);
        plugin.scoreboard().refresh(p);
        ItemStack first = p.getInventory().getItem(0);
        v.replaced = old != null ? old.replaced : first == null || Items.tag(first, ITEM_KEY) != null ? null : first.clone();
        giveItem(p);
        v.bar = BossBar.bossBar(Msg.mm("<gold>Choisis ton personnage"), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        p.showBossBar(v.bar);
        p.showTitle(net.kyori.adventure.title.Title.title(Msg.mm("<gold><bold>" + stage.licence.name()), Msg.mm("<gray>Choisis ton personnage"),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(200), java.time.Duration.ofMillis(1500), java.time.Duration.ofMillis(600))));
        // Les panneaux apparaissent quelques ticks apres l'arrivee : juste apres un changement de monde, le
        // serveur n'envoie pas au joueur les entites creees dans le meme instant (sa vue du monde n'est pas prete).
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (viewers.get(p.getUniqueId()) != v || !p.isOnline()) return;
            for (Slot s : v.slots) v.panels.put(s.character.id(), spawnPanel(v, s));
            spawnHeader(v);
            v.hoverFrom = age + 44;    // le temps que le titre s'efface
            v.hovered = null;
            refresh(v);
        }, 6L);
        start();
    }

    /** Un objet invisible en main : le clic droit "dans le vide" part alors aussi au serveur. */
    private void giveItem(Player p) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey(Sounds.NS, "hidden"));
        meta.displayName(Msg.mm("<!italic><gold>Clic : choisir le personnage vise"));
        it.setItemMeta(meta);
        Items.tag(it, ITEM_KEY, "1");
        p.getInventory().setItem(0, it);
        p.getInventory().setHeldItemSlot(0);
    }

    private static void takeItem(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (Items.tag(inv.getItem(i), ITEM_KEY) != null) inv.setItem(i, null);
    }

    // ------------------------------------------------------------ affichages

    private ItemDisplay item(Viewer v, Location at, String model, double w, double h, Color tint) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey(Sounds.NS, model));
        if (tint != null) {
            var cmd = meta.getCustomModelDataComponent();
            cmd.setColors(List.of(tint));
            meta.setCustomModelDataComponent(cmd);
        }
        it.setItemMeta(meta);
        ItemDisplay out = at.getWorld().spawn(at, ItemDisplay.class, d -> {
            prepare(d);
            d.setBillboard(Display.Billboard.FIXED);
            d.setItemStack(it);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTransformation(scaled(w, h, 0));
        });
        v.player.showEntity(plugin, out);
        return out;
    }

    private TextDisplay text(Viewer v, Location at, String mini, float scale, int lineWidth, Color background) {
        TextDisplay out = at.getWorld().spawn(at, TextDisplay.class, d -> {
            prepare(d);
            d.text(Msg.mm(mini));
            d.setLineWidth(lineWidth);
            d.setShadowed(true);
            d.setBackgroundColor(background);
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf()));
        });
        v.player.showEntity(plugin, out);
        return out;
    }

    /** Commun a tous les affichages : visibles du seul joueur (montres apres leur apparition), pleine lumiere. Les textes se tournent vers lui ; les images restent a plat sur le mur. */
    private void prepare(Display d) {
        d.setPersistent(false);
        d.setVisibleByDefault(false);
        d.setBillboard(Display.Billboard.VERTICAL);
        d.setBrightness(new Display.Brightness(15, 15));
        d.setViewRange(4f);
        d.setTeleportDuration(4);
    }

    private static Transformation scaled(double w, double h, double up) {
        return new Transformation(new Vector3f(0, (float) up, 0), new Quaternionf(), new Vector3f((float) w, (float) h, 1f), new Quaternionf());
    }

    /** Le milieu de la carte a la hauteur dy (au-dessus de son centre) : elle penche vers la droite en montant. */
    private static double midX(double dy) { return SLANT * dy / PH; }

    /** La hauteur (au-dessus du centre de la carte) d'un niveau donne en fraction depuis son haut. */
    private static double level(double fromTop) { return PH / 2 - fromTop * PH; }

    private Panel spawnPanel(Viewer v, Slot s) {
        Panel p = new Panel();
        Character c = s.character;
        String id = c.id();
        Color color = colors.getOrDefault(id, voieColor(c));
        p.glow = item(v, s.at(0, 0, -0.05), "select/card_glow", 0, 0, color);
        if (Sounds.has("select.card." + id)) {
            // Tout est dans l'image : illustration recadree, bande et nom, bandeau et icone de voie.
            p.card = item(v, s.at(0, 0, 0), "select/card/" + id, QW, PH, null);
        } else {
            // Sans illustration : la carte vide a sa couleur, le personnage pose dessus, son nom en texte.
            p.plate = item(v, s.at(0, 0, 0), "select/card_plate", QW, PH, color);
            spawnFigure(v, s, p, color);
            String label = names.getOrDefault(id, c.name()).toUpperCase();
            // Le nom tient dans la largeur de la carte ; il penche un peu, comme sur les cartes illustrees.
            float scale = (float) Math.min(2.2, 0.88 * PW * 40 / textWidth(label));
            double nameY = level(BAND_TOP + 0.42 * (STRIP_TOP - BAND_TOP));
            boolean light = luminance(color) > 0.6;
            double nx = midX(nameY), ny = nameY - 0.1625 * scale;    // un texte s'accroche par le bas ; ses capitales sont a 0.1625 au-dessus
            p.name = text(v, s.at(nx, ny, 0.14), "<bold><italic>" + (light ? "<#141418>" : "<white>") + label, scale, 400, Color.fromARGB(0, 0, 0, 0));
            p.name.setBillboard(Display.Billboard.FIXED);
            p.name.setShadowed(!light);
            p.name.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationZ((float) Math.toRadians(7)), new Vector3f(scale, scale, scale), new Quaternionf()));
            p.layers.add(new Layer(p.name, nx, ny, 0.14));
            String voie = voieId(c);
            double iconY = level(STRIP_TOP + 0.012);
            if (Sounds.has("select.voie." + voie)) {
                p.icon = item(v, s.at(midX(iconY), iconY, 0.16), "select/voie/" + voie, 0.48, 0.48, null);
                p.layers.add(new Layer(p.icon, midX(iconY), iconY, 0.16));
            } else {
                double vy = level((STRIP_TOP + 1) / 2 + 0.02) - 0.1625 * 0.9;
                p.voie = text(v, s.at(midX(vy), vy, 0.16), "<" + c.voie().color() + ">" + c.voie().label(), 0.9f, 200, Color.fromARGB(0, 0, 0, 0));
                p.voie.setBillboard(Display.Billboard.FIXED);
                p.layers.add(new Layer(p.voie, midX(vy), vy, 0.16));
            }
        }
        // La fiche du personnage s'affiche a cote de sa carte, par-dessus la voisine (vers le milieu de l'ecran) :
        // elle ne cache pas le personnage qu'on regarde.
        int index = v.slots.indexOf(s);
        double side = index < v.slots.size() / 2.0 ? 1 : -1;
        double infoY = level(0.62);    // le bas de la fiche : elle monte de la, entre le visage de la voisine et son bandeau
        p.info = text(v, s.at(side * STEP + midX(infoY), infoY, 0.75), "", 0.40f, 210, Color.fromARGB(190, 12, 12, 16));
        p.info.setTextOpacity((byte) 0);
        p.info.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        p.stamp = text(v, s.at(midX(0.6), 0.6, 0.8), "", 1.2f, 200, Color.fromARGB(0, 0, 0, 0));
        return p;
    }

    /** Taille du personnage pose sur une carte sans illustration (1 : un joueur), et son epaisseur une fois aplati. */
    private static final float FIGURE_ZOOM = 2.1f, FIGURE_FLAT = 0.1f;

    /**
     * Le personnage d'une carte sans illustration, debout sur la bande de couleur : son modele articule du pack
     * (son vrai skin, presque a plat sur la carte comme une image) ; a defaut un mannequin a son skin signe ;
     * a defaut une silhouette a sa couleur.
     */
    private void spawnFigure(Viewer v, Slot s, Panel p, Color color) {
        Character c = s.character;
        String id = c.id();
        double feetY = level(BAND_TOP);
        double x = midX(feetY + 0.9375 * FIGURE_ZOOM);    // le milieu de la carte a mi-hauteur du personnage
        java.util.function.Consumer<ItemDisplay> setup = d -> { prepare(d); d.setBillboard(Display.Billboard.FIXED); };
        if (Puppet.available(id)) {
            p.statue = Puppet.statue(s.at(x, feetY, 0.12), id, null, null, "debout", FIGURE_ZOOM, FIGURE_FLAT, setup);
        } else if (Skins.profile(id) == null && Puppet.available("silhouette")) {
            Color shade = Color.fromRGB(color.getRed() / 2 + 34, color.getGreen() / 2 + 34, color.getBlue() / 2 + 38);
            p.statue = Puppet.statue(s.at(x, feetY, 0.12), "silhouette", shade, null, "debout", FIGURE_ZOOM, FIGURE_FLAT, setup);
        }
        if (!p.statue.isEmpty()) {
            for (ItemDisplay d : p.statue) {
                d.setGlowColorOverride(color);
                v.player.showEntity(plugin, d);
                p.layers.add(new Layer(d, x, feetY, 0.12));
            }
            return;
        }
        Location feet = s.at(x, feetY, 0.3);
        feet.setYaw(0);
        p.body = feet.getWorld().spawn(feet, Mannequin.class, m -> {
            m.setPersistent(false);
            m.setVisibleByDefault(false);
            m.setImmovable(true);
            m.setSilent(true);
            m.setInvulnerable(true);
            m.setGravity(false);
            m.setCollidable(false);
            m.setDescription(net.kyori.adventure.text.Component.empty());
            var skin = Skins.profile(id);
            if (skin != null) m.setProfile(skin);
            var scale = m.getAttribute(org.bukkit.attribute.Attribute.SCALE);
            if (scale != null) scale.setBaseValue(2.0);
            var eq = m.getEquipment();
            if (eq != null) eq.setItemInMainHand(c.createWeapon());
        });
        v.player.showEntity(plugin, p.body);
        p.layers.add(new Layer(p.body, x, feetY, 0.3));
    }

    /** Largeur d'un texte en gras dans la police du jeu, en pixels (40 pixels par bloc a l'echelle 1). */
    private static int textWidth(String text) {
        int w = 0;
        for (char ch : text.toCharArray()) w += ch == 'I' || ch == ' ' ? 5 : ch == '\'' || ch == '.' ? 3 : 7;
        return Math.max(7, w);
    }

    /** Luminosite d'une couleur, de 0 (noir) a 1 (blanc). */
    private static double luminance(Color c) {
        return (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue()) / 255.0;
    }

    /** Le mur sombre derriere les cartes, et le logo de la licence (ou son nom) en haut a gauche de l'ecran. */
    private void spawnHeader(Viewer v) {
        Stage st = v.stage;
        v.extra.add(item(v, v.front(0, st.cy + 0.3, st.dist + 1.4), "select/backdrop", 21, 10.5, null));
        // Le logo flotte devant les cartes (meme devant celle qu'on vise) : place et taille en parts d'ecran.
        double depth = st.dist - 0.9;
        double halfH = depth * Math.tan(Math.toRadians(35)), halfW = halfH * 16 / 9;
        Location logoAt = v.front(-0.66 * halfW, EYE + 0.80 * halfH, depth);
        String lic = st.licence.id();
        if (Sounds.has("select.logo." + lic)) {
            double w = 0.50 * halfW;
            v.extra.add(item(v, logoAt, "select/logo/" + lic, w, w * 0.22, null));
        } else {
            // Sans logo : le nom de la licence, a la taille qui tient dans le coin, son bord gauche pres de celui de l'ecran.
            String name = st.licence.name();
            float scale = (float) Math.min(1.6, 0.62 * halfW * 40 / textWidth(name));
            double width = textWidth(name) * scale / 40.0;
            Location at = v.front(-0.95 * halfW + width / 2, EYE + 0.80 * halfH, depth);
            v.extra.add(text(v, at, "<gold><bold>" + name, scale, 600, Color.fromARGB(0, 0, 0, 0)));
        }
    }

    private static String voieId(Character c) {
        return switch (c.voie()) {
            case DESTRUCTION -> "destruction";
            case CHASSE -> "chasse";
            case HARMONIE -> "harmonie";
            case NIHILITE -> "nihilite";
        };
    }

    private static Color voieColor(Character c) {
        return switch (c.voie()) {
            case DESTRUCTION -> Color.fromRGB(0xd04848);
            case CHASSE -> Color.fromRGB(0x4fbf5a);
            case HARMONIE -> Color.fromRGB(0x48b8d0);
            case NIHILITE -> Color.fromRGB(0x8a4fd0);
        };
    }

    // ----------------------------------------------------------------- etats

    /** Remet chaque panneau d'un joueur dans son etat : libre, vise, pris par lui, pris par un autre. */
    private void refresh(Viewer v) {
        for (Slot s : v.slots) {
            String id = s.character.id();
            Panel p = v.panels.get(id);
            if (p == null) continue;
            UUID owner = v.stage.picks.get(id);
            boolean mine = owner != null && owner.equals(v.player.getUniqueId());
            boolean taken = owner != null && !mine;
            boolean hover = id.equals(v.hovered) && owner == null;
            // Les autres cartes s'assombrissent un peu quand le joueur en vise une.
            int light = taken ? 5 : v.hovered != null && !hover && !mine ? 12 : 15;
            for (Display d : p.displays()) d.setBrightness(new Display.Brightness(light, light));
            boolean up = hover || mine;
            ItemDisplay face = p.card != null ? p.card : p.plate;
            if (face != null) {
                animate(face, up ? QW * 1.06 : QW, up ? PH * 1.06 : PH, 0, 4);
                face.teleport(s.at(0, 0, up ? 0.4 : 0));
            }
            // Ce qui est pose sur la carte avance et s'ecarte avec elle.
            double k = up ? 1.06 : 1, lift = up ? 0.4 : 0;
            for (Layer l : p.layers) l.entity().teleport(s.at(k * l.dx(), k * l.dy(), l.dz() + lift));
            if (p.body != null) p.body.setGlowing(up);
            for (ItemDisplay d : p.statue) d.setGlowing(up);
            animate(p.glow, up ? GW * 1.06 : 0, up ? GH * 1.06 : 0, 0, 5);
            if (p.glow != null) p.glow.teleport(s.at(0, 0, up ? 0.3 : -0.05));
            if (hover) {
                StringBuilder sb = new StringBuilder("<gold><bold>" + s.character.name() + "</bold>\n<" + s.character.voie().color() + ">" + s.character.voie().label());
                for (String line : s.character.summary()) sb.append("\n").append(line);
                p.info.text(Msg.mm(sb.toString()));
                p.info.setTextOpacity((byte) -1);
                p.info.setBackgroundColor(Color.fromARGB(190, 12, 12, 16));
            } else {
                p.info.setTextOpacity((byte) 0);
                p.info.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            }
            if (owner != null) {
                Player op = Bukkit.getPlayer(owner);
                String who = mine ? "<gold><bold>TON CHOIX" : "<red><bold>CHOISI</bold>\n<white>" + (op != null ? op.getName() : "un allie");
                p.stamp.text(Msg.mm(who));
            } else {
                p.stamp.text(net.kyori.adventure.text.Component.empty());
            }
        }
    }

    private static void animate(Display d, double w, double h, double up, int ticks) {
        if (d == null) return;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
        d.setTransformation(scaled(w, h, up));
    }

    // ------------------------------------------------------------ chaque tick

    private void start() {
        if (task == null) task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private int age;

    private void tick() {
        age++;
        for (Viewer v : new ArrayList<>(viewers.values())) {
            Player p = v.player;
            if (!p.isOnline()) { close(p, false); continue; }
            // Il reste a sa place (il peut tourner la tete).
            if (!p.getWorld().equals(v.spot.getWorld()) || p.getLocation().distanceSquared(v.spot) > 1.5) {
                Location back = v.spot.clone();
                back.setYaw(p.getLocation().getYaw());
                back.setPitch(p.getLocation().getPitch());
                p.teleport(back);
            }
            String now = age < v.hoverFrom ? null : aimed(v);
            if (!java.util.Objects.equals(now, v.hovered)) {
                v.hovered = now;
                refresh(v);
                if (now != null && !v.stage.picks.containsKey(now)) p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.35f, 1.9f);
            }
            if (age % 20 == 0) updateBar(v);
        }
        if (viewers.isEmpty() && task != null) { task.cancel(); task = null; }
    }

    /** Le panneau que vise le joueur (null si aucun) : le rayon de son regard coupe-t-il un parallelogramme ? */
    private String aimed(Viewer v) {
        Location eye = v.player.getEyeLocation();
        Vector o = eye.toVector(), dir = eye.getDirection();
        String best = null;
        double bestT = Double.MAX_VALUE;
        for (Slot s : v.slots) {
            Vector n = s.normal();
            double denom = dir.dot(n);
            if (denom > -1e-4) continue;
            double t = s.center.toVector().subtract(o).dot(n) / denom;
            if (t <= 0 || t >= bestT) continue;
            Vector hit = o.clone().add(dir.clone().multiply(t));
            Vector rel = hit.subtract(s.center.toVector());
            double dx = rel.dot(s.right()), dy = rel.getY();
            if (Math.abs(dy) > PH / 2 + 0.3) continue;
            if (Math.abs(dx - SLANT * dy / PH) > PW / 2 + 0.05) continue;
            best = s.character.id();
            bestT = t;
        }
        return best;
    }

    private void updateBar(Viewer v) {
        if (v.bar == null) return;
        GameManager g = plugin.game();
        if (v.stage.preview) {
            v.bar.name(Msg.mm("<aqua>Essai de l'ecran de selection <gray>(" + v.stage.licence.name() + ") - vise une carte, clic pour choisir"));
            return;
        }
        CfPlayer cf = plugin.players().get(v.player);
        int left = g.phaseSeconds();
        if (g.isSelecting()) {
            v.bar.name(Msg.mm(cf.character() == null ? "<gold>Choisis ton personnage <gray>: vise une carte et clique - " + left + " s" : "<green>" + cf.character().name() + " <gray>- en attente de l'equipe (" + left + " s)"));
            v.bar.progress(Math.max(0f, Math.min(1f, left / (float) GameManager.SELECTION_SECONDS)));
        } else {
            v.bar.name(Msg.mm("<yellow>Teleportation imminente"));
            v.bar.progress(1f);
        }
    }

    // ------------------------------------------------------------------ choix

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwing(PlayerAnimationEvent e) {
        if (e.getAnimationType() == PlayerAnimationType.ARM_SWING) click(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        Viewer v = viewers.get(e.getPlayer().getUniqueId());
        if (v == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) click(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (!viewers.containsKey(e.getPlayer().getUniqueId())) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) click(e.getPlayer());
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (Items.tag(e.getItemDrop().getItemStack(), ITEM_KEY) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        close(e.getPlayer(), false);
    }

    private void click(Player p) {
        Viewer v = viewers.get(p.getUniqueId());
        if (v == null || v.hovered == null) return;
        long now = System.currentTimeMillis();
        if (now - v.lastPick < 400) return;
        v.lastPick = now;
        Slot s = v.slot(v.hovered);
        if (s == null) return;
        Character c = s.character;
        if (v.stage.preview) {
            v.stage.picks.clear();
            v.stage.picks.put(c.id(), p.getUniqueId());
            celebrate(v, s);
            refresh(v);
            if (plugin.testMode().isTester(p)) {
                plugin.testMode().change(p, c);
                v.replaced = null;    // son equipement vient d'etre remplace par celui du personnage choisi
            }
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline() || viewers.get(p.getUniqueId()) != v) return;
                close(p, true);
            }, 50L);
            return;
        }
        CfPlayer cf = plugin.players().get(p);
        if (cf.character() != null) {
            Msg.actionBar(p, "<gray>Tu as deja choisi " + cf.character().name() + ".");
            return;
        }
        UUID owner = v.stage.picks.get(c.id());
        if (owner != null) {
            Player op = Bukkit.getPlayer(owner);
            Msg.actionBar(p, "<red>" + c.name() + " est deja pris par " + (op != null ? op.getName() : "un allie") + ".");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.6f);
            return;
        }
        // CharacterSelectMenu.choose previent la scene (onChosen) : les panneaux de toute l'equipe se mettent a jour.
        if (!plugin.characters().selectMenu(plugin).choose(p, c)) {
            Msg.actionBar(p, "<red>" + c.name() + " n'est plus disponible.");
        }
    }

    /** Un personnage vient d'etre choisi (clic, choix automatique a la fin du temps...). */
    public void onChosen(Player p, Character c) {
        Viewer pv = viewers.get(p.getUniqueId());
        Stage stage = pv != null ? pv.stage : null;
        if (stage == null) {
            Team t = plugin.players().get(p).team();
            stage = t == null ? null : stages.get("team:" + t.id());
        }
        if (stage == null) return;
        stage.picks.put(c.id(), p.getUniqueId());
        for (Viewer v : stage.viewers()) {
            refresh(v);
            if (v == pv) {
                Slot s = v.slot(c.id());
                if (s != null) celebrate(v, s);
            } else {
                v.player.playSound(v.player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, 1.4f);
            }
        }
        if (pv != null) updateBar(pv);
    }

    /** Le panneau choisi brille un instant, une gerbe d'etincelles, un son de validation. */
    private void celebrate(Viewer v, Slot s) {
        Panel p = v.panels.get(s.character.id());
        if (p == null) return;
        Player pl = v.player;
        pl.playSound(pl.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.3f);
        pl.playSound(pl.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        Location c = s.at(0, 0.4, 0.6);
        pl.spawnParticle(Particle.END_ROD, c, 40, 0.9, 1.8, 0.1, 0.06);
        pl.spawnParticle(Particle.FIREWORK, c, 25, 0.8, 1.6, 0.1, 0.08);
        // La carte bondit vers le joueur, entouree d'un eclair blanc, puis revient a sa taille choisie.
        ItemDisplay face = p.card != null ? p.card : p.plate;
        if (face != null) {
            face.setGlowing(true);
            face.setGlowColorOverride(Color.WHITE);
            animate(face, QW * 1.14, PH * 1.14, 0, 3);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!face.isValid()) return;
                face.setGlowing(false);
                animate(face, QW * 1.06, PH * 1.06, 0, 6);
            }, 6L);
        }
    }

    // --------------------------------------------------------------- fermeture

    /**
     * Retire l'ecran d'un joueur. {@code back} : il revient d'ou il vient (le terrain de test pour un essai,
     * le lobby sinon) ; sans cela, il reste ou il est (la partie le teleporte elle-meme).
     */
    public void close(Player p, boolean back) {
        Viewer v = viewers.remove(p.getUniqueId());
        if (v == null) return;
        v.remove();
        if (p.isOnline()) {
            Effects.unroot(p);
            takeItem(p);
            if (v.replaced != null && p.getInventory().getItem(0) == null) p.getInventory().setItem(0, v.replaced);
            plugin.scoreboard().refresh(p);
            if (back) {
                if (v.stage.preview && plugin.testMode().isTester(p)) p.teleport(ArenaWorlds.center(TestMode.world()));
                else p.teleport(plugin.game().isLobby() ? plugin.game().lobbySpot() : plugin.game().world().getSpawnLocation());
                p.setFallDistance(0);
            }
        }
        if (v.stage.preview) {
            stages.remove(v.stage.key);
            clearStage(v.stage);
        }
    }

    /** Fin de la selection (lancement de la partie, arret) : tout le monde quitte les scenes. */
    public void closeAll(boolean back) {
        for (Viewer v : new ArrayList<>(viewers.values())) close(v.player, back);
        for (Stage s : stages.values()) clearStage(s);
        stages.clear();
        nextIndex = 0;
    }

    private void clearStage(Stage s) {
        for (Viewer v : s.viewers()) { viewers.remove(v.player.getUniqueId()); v.remove(); }
    }

    /** Pour /cf test selection : la licence a essayer (celle du personnage du testeur par defaut). */
    public boolean isPreviewing(Player p) {
        Viewer v = viewers.get(p.getUniqueId());
        return v != null && v.stage.preview;
    }
}
