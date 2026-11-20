package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le modele articule d'un personnage : pendant une animation, le joueur devient invisible et un
 * pantin a son image (tete, torse, bras, jambes, objet tenu) prend la pose a sa place.
 *
 * Chaque partie est un objet du pack de ressources (tools/add_skin.py), centre sur son articulation,
 * affiche par un ItemDisplay qui la fait tourner. Les poses sont dans poses.txt ; tools/pose_preview.py
 * applique les memes calculs pour les dessiner sans lancer le jeu.
 */
public final class Puppet {

    /** Echelle du modele de joueur de Minecraft (32 pixels pour 1,875 bloc). */
    private static final float K = 0.9375f;
    private static final float ITEM_SCALE = 0.85f;
    /** La main, dans le repere du bras ; la poignee d'une epee dans l'image de l'objet. En pixels. */
    private static final Vector3f HAND = new Vector3f(0, -9, 0), GRIP = new Vector3f(5.5f, -5.5f, 0);
    /** Un arc se tient par le milieu de son bois. */
    private static final Vector3f GRIP_BOW = new Vector3f(2, 2, 0);
    /** Amene la lame d'un objet tenu (dessinee en diagonale) perpendiculaire au bras, tranchant vertical. */
    private static final Quaternionf ITEM_BASE = euler(90, 0, 0).mul(euler(0, 90, 0)).mul(euler(0, 0, -45));

    /** Les parties du corps et leur articulation, en pixels (X = gauche du personnage, Y = haut, Z = devant). */
    private enum Part {
        HEAD("head", 0, 24, 0), BODY("body", 0, 24, 0), ARM_R("arm_r", -5, 22, 0), ARM_L("arm_l", 5, 22, 0),
        LEG_R("leg_r", -1.9f, 12, 0), LEG_L("leg_l", 1.9f, 12, 0);

        final String id;
        final Vector3f pivot;

        Part(String id, float x, float y, float z) { this.id = id; this.pivot = new Vector3f(x, y, z); }
    }

    /** Rotation (degres) et decalage (pixels) d'une partie dans une pose. */
    private record Joint(Vector3f angles, Vector3f offset) {
        static final Joint REST = new Joint(new Vector3f(), new Vector3f());
    }

    private static final Map<String, Map<String, Joint>> POSES = new LinkedHashMap<>();
    private static final Map<UUID, Puppet> ACTIVE = new HashMap<>();

    /** Le corps que le pantin remplace : un joueur, ou une autre entite (un clone...). */
    private final org.bukkit.entity.LivingEntity entity;
    /** Le meme, quand c'est un joueur (null sinon). */
    private final Player player;
    private final Map<Part, ItemDisplay> parts = new EnumMap<>(Part.class);
    private ItemDisplay held;
    /** Un second objet, dans l'autre main (deux lames...) : null le plus souvent. */
    private ItemDisplay second;
    private BukkitTask task;
    private boolean addedInvisibility, madeInvisible, ownItemsHidden;
    /** Le pantin reste a cet endroit au lieu de suivre son corps (null : il le suit). */
    private Location pinned;
    private int heldSlot;
    private int age, endAt = -1, removeAt = 20 * 30;
    private String pose, restPose = "debout";
    private Vector3f grip = GRIP, secondGrip = GRIP;
    private boolean rawModel;
    /**
     * Le modele 3D d'arme que montre l'objet tenu (la Falchion, le Yumi Fujin... : util/Models), ou null pour une
     * image d'objet. Droit dans son fichier, il penche pour se poser comme l'image, et se tient par sa poignee.
     */
    private Models.Model model;
    private Float fixedYaw;
    /** Rotation de tout le pantin autour d'un centre (vrille, toupie) : null au repos. */
    private Quaternionf spin;
    private final Vector3f spinCenter = new Vector3f();
    /** Une statue (ecran de selection) : agrandie, et aplatie vers l'avant pour se poser sur une carte (1 : non). */
    private float zoom = 1f, flat = 1f;
    private java.util.function.Consumer<ItemDisplay> setup;
    private final List<Runnable> restore = new ArrayList<>();

    private Puppet(org.bukkit.entity.LivingEntity entity) {
        this.entity = entity;
        this.player = entity instanceof Player p ? p : null;
    }

    // ------------------------------------------------------------ poses

    public static void load(CrossfightPlugin plugin) {
        POSES.clear();
        try (InputStream in = plugin.getResource("poses.txt")) {
            if (in == null) return;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            Map<String, Joint> current = null;
            for (String line; (line = r.readLine()) != null; ) {
                int hash = line.indexOf('#');
                String[] t = (hash >= 0 ? line.substring(0, hash) : line).trim().split("\\s+");
                if (t.length < 2 || t[0].isEmpty()) continue;
                if (t[0].equals("pose")) { current = new HashMap<>(); POSES.put(t[1], current); continue; }
                if (current == null) continue;
                float[] v = new float[6];
                for (int i = 0; i < 6 && i + 1 < t.length; i++) v[i] = Float.parseFloat(t[i + 1]);
                current.put(t[0], new Joint(new Vector3f(v[0], v[1], v[2]), new Vector3f(v[3], v[4], v[5])));
            }
        } catch (Exception ex) { plugin.getLogger().warning("poses.txt illisible : " + ex); }
    }

    public static Collection<String> poses() { return POSES.keySet(); }

    /** Vrai si le pack contient le modele articule de ce personnage. */
    public static boolean available(String character) {
        return CrossfightPlugin.get().getConfig().getBoolean("poses.enabled", true) && Sounds.has("skin." + character);
    }

    /** Ry * Rx * Rz, en degres : l'ordre de poses.txt. */
    private static Quaternionf euler(float rx, float ry, float rz) {
        return new Quaternionf().rotationYXZ((float) Math.toRadians(ry), (float) Math.toRadians(rx), (float) Math.toRadians(rz));
    }

    private static Quaternionf euler(Vector3f a) { return euler(a.x, a.y, a.z); }

    // ------------------------------------------------------------ vie du pantin

    public static Puppet of(org.bukkit.entity.Entity e) { return ACTIVE.get(e.getUniqueId()); }

    /**
     * Remplace le joueur par son pantin, dans la pose donnee. Rend null si le pack n'a pas le modele du
     * personnage : l'appelant garde alors ses effets habituels.
     *
     * @param heldItem l'objet montre dans la main droite (null : aucun)
     */
    public static Puppet show(org.bukkit.entity.LivingEntity p, String character, ItemStack heldItem, String pose) {
        return show(p, character, heldItem, pose, 1f);
    }

    /** Le meme, a une autre taille (2 : un Titan, deux fois plus grand que son joueur). */
    public static Puppet show(org.bukkit.entity.LivingEntity p, String character, ItemStack heldItem, String pose, float zoom) {
        if (!available(character) || !POSES.containsKey(pose)) return null;
        Puppet old = ACTIVE.remove(p.getUniqueId());
        if (old != null) old.remove(false);
        clearLinger(p.getUniqueId());
        Puppet pu = new Puppet(p);
        pu.zoom = zoom;
        Location at = pu.anchor();
        for (Part part : Part.values()) pu.parts.put(part, pu.spawn(at, partModel(character, part, null)));
        pu.spawnHeld(at, heldItem);
        pu.apply(pose, 0);
        pu.hidePlayer();
        ACTIVE.put(p.getUniqueId(), pu);
        pu.task = Bukkit.getScheduler().runTaskTimer(CrossfightPlugin.get(), pu::tick, 1L, 1L);
        return pu;
    }

    private static ItemStack partModel(String character, Part part, org.bukkit.Color tint) {
        ItemStack model = new ItemStack(Material.PAPER);
        ItemMeta meta = model.getItemMeta();
        meta.setItemModel(new NamespacedKey(Sounds.NS, "pose/" + character + "_" + part.id));
        if (tint != null) {
            var cmd = meta.getCustomModelDataComponent();
            cmd.setColors(List.of(tint));
            meta.setCustomModelDataComponent(cmd);
        }
        model.setItemMeta(meta);
        return model;
    }

    /**
     * Une statue : le modele articule d'un personnage fige dans une pose, sans corps a remplacer (l'ecran de
     * selection). Elle regarde vers le sud quand {@code at} a une orientation nulle. Rend ses affichages (liste
     * vide si le pack n'a pas ce modele) ; a l'appelant de les deplacer et de les retirer.
     *
     * @param tint  une couleur qui remplace celles du skin (une silhouette), ou null
     * @param zoom  taille (1 : celle d'un joueur)
     * @param flat  epaisseur vers l'avant (1 : normale ; 0.1 : presque a plat, comme imprimee sur un mur)
     * @param setup reglages de chaque affichage a son apparition (visibilite, lumiere...)
     */
    public static List<ItemDisplay> statue(Location at, String character, org.bukkit.Color tint, ItemStack heldItem, String pose,
                                           float zoom, float flat, java.util.function.Consumer<ItemDisplay> setup) {
        List<ItemDisplay> out = new ArrayList<>();
        if (!available(character) || !POSES.containsKey(pose)) return out;
        Puppet pu = new Puppet(null);
        pu.pinned = at.clone();
        pu.zoom = zoom;
        pu.flat = flat;
        pu.setup = setup;
        for (Part part : Part.values()) pu.parts.put(part, pu.spawn(at, partModel(character, part, tint)));
        pu.spawnHeld(at, heldItem);
        pu.apply(pose, 0);
        out.addAll(pu.parts.values());
        if (pu.held != null) out.add(pu.held);
        return out;
    }

    private void spawnHeld(Location at, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        held = spawn(at, item.clone());
        String type = item.getType().name();
        // Un objet du pack (un livre ouvert...) est un vrai modele, centre sur la main et deja dans le bon
        // sens : ni rotation d'objet plat, ni point de prise. Les autres sont des images tenues par un bout.
        ItemMeta meta = item.getItemMeta();
        rawModel = meta != null && meta.hasItemModel() && Sounds.NS.equals(meta.getItemModel().getNamespace());
        model = rawModel ? null : Models.of(item);
        grip = rawModel ? new Vector3f() : model != null ? model.grip() : type.equals("BOW") || type.equals("CROSSBOW") ? GRIP_BOW : GRIP;
    }

    /** Change l'image de l'objet tenu sans le faire disparaitre (pages d'un livre qui tournent). */
    public void heldLook(ItemStack item) {
        if (held != null && held.isValid() && item != null) held.setItemStack(item.clone());
    }

    /** Change l'objet montre dans la main droite en cours d'animation (null : la main se vide). */
    public void held(ItemStack item) {
        if (held != null) { held.remove(); held = null; }
        if (task == null) return;
        spawnHeld(anchor(), item);
        if (held != null && pose != null) apply(pose, 0);
    }

    /**
     * Un second objet, tenu dans l'autre main (la gauche, ou la droite si la pose tient le premier a gauche) :
     * une image d'objet ordinaire (une epee...), orientee par "item2" dans la pose. null : cette main se vide.
     */
    public void second(ItemStack item) {
        if (second != null) { second.remove(); second = null; }
        if (task == null || item == null || item.getType().isAir()) return;
        second = spawn(anchor(), item.clone());
        String type = item.getType().name();
        secondGrip = type.equals("BOW") || type.equals("CROSSBOW") ? GRIP_BOW : GRIP;
        if (pose != null) apply(pose, 0);
    }

    /** La pose de repos vers laquelle l'animation revient avant de finir ("debout" par defaut). */
    public void rest(String restPose) { if (POSES.containsKey(restPose)) this.restPose = restPose; }

    /** La main droite du pantin, en coordonnees du monde, dans la pose actuelle. */
    public Location hand() { return armPoint(true, 0.9); }

    /**
     * Un point le long d'un bras du pantin, en coordonnees du monde, dans la pose actuelle :
     * 0 a l'epaule, 1 au bout du poing.
     */
    public Location armPoint(boolean right, double along) {
        Part part = right ? Part.ARM_R : Part.ARM_L;
        Map<String, Joint> joints = POSES.get(pose == null ? "debout" : pose);
        Joint root = joints.getOrDefault("root", Joint.REST), arm = joints.getOrDefault(part.id, Joint.REST);
        Quaternionf rootR = euler(root.angles);
        Vector3f t = rootR.transform(new Vector3f(part.pivot).add(arm.offset).mul(K / 16f)).add(new Vector3f(root.offset).mul(K / 16f));
        Vector3f point = new Quaternionf(rootR).mul(euler(arm.angles)).transform(new Vector3f(0, (float) (-10 * along), 0).mul(K / 16f)).add(t);
        // Du repere du pantin (X gauche, Z devant) vers le monde, selon l'orientation du joueur.
        new Quaternionf().rotationY((float) Math.toRadians(-anchor().getYaw())).transform(point.mul(zoom));
        return anchor().add(point.x, point.y, point.z);
    }

    private ItemDisplay spawn(Location at, ItemStack item) {
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTeleportDuration(1);
            d.setViewRange(1.5f);
            if (setup != null) setup.accept(d);
            // En vue a la premiere personne, les pieces du pantin sont dans la camera : son joueur ne les voit pas.
            if (hiddenFromOwner()) player.hideEntity(CrossfightPlugin.get(), d);
        });
    }

    /**
     * Le joueur ne voit pas son propre pantin (animations.voir-son-pantin: false, par defaut) : le serveur ne sait
     * pas s'il est en vue exterieure, et a la premiere personne le pantin lui boucherait la vue. Le pilote d'un
     * mannequin (mode d'essai a la troisieme personne) le voit toujours.
     */
    private boolean hiddenFromOwner() {
        return player != null && !MannequinPlayer.isProxy(player) && !CrossfightPlugin.get().getConfig().getBoolean("animations.voir-son-pantin", false);
    }

    private Location anchor() {
        Location l = pinned != null ? pinned.clone() : entity.getLocation();
        l.setPitch(0);
        if (fixedYaw != null) l.setYaw(fixedYaw);
        return l;
    }

    /** Le pantin garde cette orientation au lieu de suivre le regard du joueur (coup dont la direction est figee). */
    public void face(float yaw) {
        fixedYaw = yaw;
    }

    /** Le pantin reste ici (sa pose s'y joue) meme si son corps s'en va : quelqu'un qui s'enfonce dans le sol... */
    public void pin(Location at) {
        pinned = at.clone();
    }

    /**
     * Teint une partie du pantin ("head", "body", "arm_r", "arm_l", "leg_r", "leg_l" ; null : toutes) d'une
     * couleur, ou lui rend ses vraies couleurs (color null). Le modele doit l'accepter (tools/add_skin.py).
     */
    public void tint(String partId, org.bukkit.Color color) {
        for (Part part : Part.values()) {
            if (partId != null && !part.id.equals(partId)) continue;
            ItemDisplay d = parts.get(part);
            if (d == null || !d.isValid()) continue;
            ItemStack it = d.getItemStack();
            ItemMeta meta = it.getItemMeta();
            var cmd = meta.getCustomModelDataComponent();
            cmd.setColors(color == null ? List.of() : List.of(color));
            meta.setCustomModelDataComponent(cmd);
            it.setItemMeta(meta);
            d.setItemStack(it);
        }
    }

    /**
     * Donne a une partie (null : toutes) l'apparence d'un autre modele articule : le squelette, les muscles, puis
     * la peau d'un Titan qui se forme... character null : la partie disparait (elle garde sa place et sa pose).
     */
    public void look(String partId, String character) {
        for (Part part : Part.values()) {
            if (partId != null && !part.id.equals(partId)) continue;
            ItemDisplay d = parts.get(part);
            if (d != null && d.isValid()) d.setItemStack(character == null ? invisibleItem() : partModel(character, part, null));
        }
    }

    /** Le milieu d'une partie du pantin ("head", "body", "arm_r"...), en coordonnees du monde, dans la pose actuelle. */
    public Location center(String partId) {
        for (Part part : Part.values()) {
            if (!part.id.equals(partId)) continue;
            Map<String, Joint> joints = POSES.get(pose == null ? "debout" : pose);
            Joint root = joints.getOrDefault("root", Joint.REST), j = joints.getOrDefault(part.id, Joint.REST);
            Quaternionf rootR = euler(root.angles);
            // Le milieu de la boite, depuis son articulation : la tete est au-dessus, le reste pend dessous.
            Vector3f middle = new Vector3f(0, part == Part.HEAD ? 4 : part == Part.BODY || part == Part.LEG_R || part == Part.LEG_L ? -6 : -4, 0);
            Vector3f point = new Quaternionf(rootR).mul(euler(j.angles)).transform(middle)
                    .add(rootR.transform(new Vector3f(part.pivot).add(j.offset))).add(root.offset).mul(K / 16f * zoom);
            new Quaternionf().rotationY((float) Math.toRadians(-anchor().getYaw())).transform(point);
            return anchor().add(point.x, point.y, point.z);
        }
        return anchor();
    }

    /** Un contour lumineux de cette couleur autour d'une partie (null : toutes), ou plus de contour (color null). */
    public void glow(String partId, org.bukkit.Color color) {
        for (Part part : Part.values()) {
            if (partId != null && !part.id.equals(partId)) continue;
            ItemDisplay d = parts.get(part);
            if (d == null || !d.isValid()) continue;
            d.setGlowing(color != null);
            if (color != null) d.setGlowColorOverride(color);
        }
    }

    /** La pose actuelle (celle vers laquelle le pantin va, ou dans laquelle il est). */
    public String currentPose() { return pose; }

    /**
     * Fait tourner tout le pantin d'un angle autour d'un axe, dans son repere (X a sa gauche, Y en haut, Z devant),
     * l'axe passant par {@code centerPx} (pixels de skin, depuis ses pieds). Appele a chaque tick avec un angle
     * qui augmente, il vrille sur lui-meme ; le retour a la position debout de la fin annule la rotation.
     */
    public void spin(Vector3f axis, float degrees, Vector3f centerPx, int ticks) {
        spin = new Quaternionf().rotationAxis((float) Math.toRadians(degrees), new Vector3f(axis).normalize());
        spinCenter.set(centerPx).mul(K / 16f);
        if (task != null && pose != null && age >= 2) apply(pose, ticks);
    }

    /** Passe a une autre pose en quelques ticks (0 : tout de suite). */
    public void pose(String pose, int ticks) {
        if (!POSES.containsKey(pose)) return;
        // Un affichage qui vient d'apparaitre n'a pas d'etat precedent : il sauterait directement a la pose.
        if (age < 2) Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> { if (task != null) apply(pose, ticks); }, 2L - age);
        else apply(pose, ticks);
    }

    /** Le pantin reste jusqu'a ce qu'on le retire (un boss), au lieu de s'effacer de lui-meme au bout de 30 secondes. */
    public Puppet keep() {
        removeAt = Integer.MAX_VALUE;
        return this;
    }

    /** Vrai tant que le pantin est affiche. */
    public boolean shown() { return task != null && ACTIVE.get(entity.getUniqueId()) == this; }

    /** Termine l'animation dans {@code after} ticks : retour a la position debout en {@code blend} ticks, puis le joueur reapparait. */
    public void endAfter(int after, int blend) {
        endAt = age + after;
        removeAt = endAt + blend + 1;
        this.blend = blend;
    }

    private int blend = 3;

    private void tick() {
        age++;
        if (!alive() || age >= removeAt || parts.values().stream().anyMatch(d -> !d.isValid())
                || !entity.getWorld().equals(parts.get(Part.BODY).getWorld())) { remove(); return; }
        // Un clic sur une capacite fait renvoyer tout l'inventaire au joueur juste apres : ses objets
        // reapparaitraient chez lui. On les re-masque donc regulierement tant que dure l'animation, et
        // tout de suite s'il change de case (l'ancienne retrouve son objet, la nouvelle est masquee).
        if (ownItemsHidden && player != null) {
            int now = player.getInventory().getHeldItemSlot();
            if (now != heldSlot) { heldSlot = now; player.updateInventory(); hideOwnItems(player, !hiddenFromOwner()); }
            else if (age % 4 == 1) hideOwnItems(player, !hiddenFromOwner());
        }
        Location at = anchor();
        for (ItemDisplay d : parts.values()) d.teleport(at);
        if (held != null) held.teleport(at);
        if (second != null) second.teleport(at);
        if (age == endAt) { spin = null; apply(restPose, blend); }
    }

    private void apply(String pose, int ticks) {
        this.pose = pose;
        Map<String, Joint> joints = POSES.get(pose);
        Joint root = joints.getOrDefault("root", Joint.REST);
        Quaternionf rootR = euler(root.angles);
        Vector3f rootT = new Vector3f(root.offset).mul(K / 16f);
        for (Part part : Part.values()) {
            Joint j = joints.getOrDefault(part.id, Joint.REST);
            Quaternionf r = new Quaternionf(rootR).mul(euler(j.angles));
            Vector3f t = rootR.transform(new Vector3f(part.pivot).add(j.offset).mul(K / 16f)).add(rootT);
            set(parts.get(part), spun(t, r, K), ticks);
            boolean leftHand = joints.containsKey("item_left");
            if (part == (leftHand ? Part.ARM_L : Part.ARM_R) && held != null) {
                // "item" : oriente par rapport au bras ; "item_world" : par rapport au corps ;
                // "item_abs" : par rapport au joueur, quels que soient le corps et le bras.
                Joint world = joints.get("item_world"), abs = joints.get("item_abs");
                Quaternionf ri = abs != null ? euler(abs.angles)
                        : world != null ? new Quaternionf(rootR).mul(euler(world.angles))
                        : new Quaternionf(r).mul(euler(joints.getOrDefault("item", Joint.REST).angles));
                if (!rawModel) ri.mul(ITEM_BASE);
                if (model != null) ri.mul(model.tilt());
                float size = rawModel ? K : model != null ? model.scale() : ITEM_SCALE;
                Vector3f hand = r.transform(new Vector3f(HAND).mul(K / 16f)).add(t);
                // "item_shift" : decalage de l'objet par rapport a la main, dans le repere du joueur (pixels).
                Joint shift = joints.get("item_shift");
                if (shift != null) hand.add(new Vector3f(shift.angles).mul(K / 16f));
                Vector3f ti = hand.sub(ri.transform(new Vector3f(grip).mul(size / 16f)));
                set(held, spun(ti, ri, size), ticks);
            }
            if (part == (leftHand ? Part.ARM_R : Part.ARM_L) && second != null) {
                // "item2" : comme "item", pour l'objet de l'autre main ; sans lui, la meme tenue en miroir.
                Joint own = joints.get("item2");
                Vector3f a = own != null ? own.angles : new Vector3f(joints.getOrDefault("item", Joint.REST).angles).mul(1, -1, -1);
                Quaternionf ri = new Quaternionf(r).mul(euler(a)).mul(ITEM_BASE);
                Vector3f hand = r.transform(new Vector3f(HAND).mul(K / 16f)).add(t);
                Vector3f ti = hand.sub(ri.transform(new Vector3f(secondGrip).mul(ITEM_SCALE / 16f)));
                set(second, spun(ti, ri, ITEM_SCALE), ticks);
            }
        }
    }

    /** La transformation d'une partie, tournee avec tout le pantin si une vrille est en cours. */
    private Transformation spun(Vector3f t, Quaternionf r, float size) {
        if (spin != null) {
            t = spin.transform(new Vector3f(t).sub(spinCenter)).add(spinCenter);
            r = new Quaternionf(spin).mul(r);
        }
        return new Transformation(t, r, new Vector3f(size, size, size), new Quaternionf());
    }

    private void set(ItemDisplay d, Transformation tr, int ticks) {
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
        if (zoom == 1f && flat == 1f) { d.setTransformation(tr); return; }
        // Une statue : la meme pose, agrandie, puis ecrasee le long de son axe avant-arriere.
        Vector3f t = new Vector3f(tr.getTranslation()).mul(zoom);
        d.setTransformationMatrix(new org.joml.Matrix4f().scale(1f, 1f, flat).translate(t).rotate(tr.getLeftRotation()).scale(new Vector3f(tr.getScale()).mul(zoom)));
    }

    private boolean alive() {
        return entity.isValid() && !entity.isDead() && (player == null || player.isOnline());
    }

    /** Le joueur disparait : invisible, et son equipement n'est plus montre (il flotterait dans le vide). */
    private void hidePlayer() {
        if (player != null) {
            if (!player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 30, 0, false, false, false));
                addedInvisibility = true;
            }
        } else if (!entity.isInvisible()) {
            entity.setInvisible(true);
            madeInvisible = true;
        }
        EquipmentSlot[] all = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND};
        for (Player viewer : entity.getWorld().getPlayers()) {
            if (viewer.equals(entity) || viewer.getLocation().distanceSquared(entity.getLocation()) > 96 * 96) continue;
            Map<EquipmentSlot, ItemStack> empty = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot s : all) empty.put(s, ItemStack.empty());
            // Le joueur pilote d'un mannequin (troisieme personne) n'est pas une entite du serveur : on passe son mannequin.
            var shown = MannequinPlayer.real(entity);
            viewer.sendEquipmentChange(shown, empty);
            restore.add(() -> {
                if (!viewer.isOnline() || !alive()) return;
                Map<EquipmentSlot, ItemStack> real = new EnumMap<>(EquipmentSlot.class);
                var eq = player != null ? null : entity.getEquipment();
                for (EquipmentSlot s : all) {
                    ItemStack it = player != null ? player.getInventory().getItem(s) : eq == null ? null : eq.getItem(s);
                    real.put(s, it == null ? ItemStack.empty() : it);
                }
                viewer.sendEquipmentChange(shown, real);
            });
        }
        // Le joueur lui-meme (vue exterieure) : son client ne tient pas compte d'un paquet d'equipement
        // qui le concerne, il affiche ce que contient son propre inventaire. On lui envoie donc, a lui seul,
        // des cases d'armure vides et un objet invisible a la place de ce qu'il tient ; son vrai inventaire
        // n'est pas touche et lui est renvoye tel quel a la fin.
        if (player != null && !MannequinPlayer.isProxy(player) && hideOwnItems(player, !hiddenFromOwner())) {
            ownItemsHidden = true;
            heldSlot = player.getInventory().getHeldItemSlot();
            restore.add(() -> { if (player.isOnline()) player.updateInventory(); });
        }
    }

    private static boolean ownItemsWarned;
    private static ItemStack invisible;

    /** Un objet sans apparence (items/hidden.json du pack) : la main parait vide, mais le clic droit part toujours. */
    private static ItemStack invisibleItem() {
        if (invisible == null) {
            ItemStack it = new ItemStack(Material.PAPER);
            ItemMeta meta = it.getItemMeta();
            meta.setItemModel(new NamespacedKey(Sounds.NS, "hidden"));
            meta.setHideTooltip(true);
            it.setItemMeta(meta);
            invisible = it;
        }
        return invisible;
    }

    /**
     * Envoie au joueur ses quatre cases d'armure vides (cases 5 a 8 de son inventaire) et un objet
     * invisible dans la case qu'il tient et dans sa main gauche, sans toucher a son vrai inventaire.
     * L'API de Paper n'a pas d'equivalent : on passe par les classes du serveur.
     */
    private static boolean hideOwnItems(Player p, boolean hands) {
        var inv = p.getInventory();
        // L'orbe de l'ultime et les passifs (objets invisibles des bottes et du casque) restent : ils ne se dessinent pas sur le corps.
        var hud = CrossfightPlugin.get().effectHud();
        ItemStack orb = hud == null ? null : hud.shownIn(p, 8), passives = hud == null ? null : hud.shownIn(p, 5);
        ItemStack infl0 = hud == null ? null : hud.shownIn(p, 6), infl1 = hud == null ? null : hud.shownIn(p, 7);
        // Sans pantin a ses yeux, le joueur garde ce qu'il tient : sa vue a la premiere personne ne change pas.
        int[] slots = hands ? new int[]{5, 6, 7, 8, 36 + inv.getHeldItemSlot(), 45} : new int[]{5, 6, 7, 8};
        ItemStack[] items = hands ? new ItemStack[]{passives, infl0, infl1, orb,
                inv.getItemInMainHand().getType().isAir() ? null : invisibleItem(),
                inv.getItemInOffHand().getType().isAir() ? (hud == null ? null : hud.shownIn(p, 45)) : invisibleItem()} : new ItemStack[]{passives, infl0, infl1, orb};
        boolean ok = ClientSlots.show(p, slots, items);
        if (!ok && !ownItemsWarned) { ownItemsWarned = true; CrossfightPlugin.get().getLogger().warning("Objets du joueur non masques pour lui-meme."); }
        return ok;
    }

    /** Pieces d'un pantin fini, gardees le temps que le joueur reapparaisse chez les clients. */
    private static final Map<UUID, List<ItemDisplay>> LINGER = new HashMap<>();
    private static final long LINGER_TICKS = 3L;

    private static void clearLinger(UUID id) {
        List<ItemDisplay> left = LINGER.remove(id);
        if (left != null) for (ItemDisplay d : left) d.remove();
    }

    /**
     * Retire le pantin et fait reapparaitre le joueur. Le joueur reapparait d'abord ; les pieces du pantin
     * restent quelques ticks dans leur derniere pose, sinon il n'y a plus rien a l'ecran le temps que la fin
     * de l'invisibilite et l'equipement arrivent chez les clients.
     */
    public void remove() { remove(true); }

    private void remove(boolean linger) {
        if (task != null) { task.cancel(); task = null; }
        ACTIVE.remove(entity.getUniqueId(), this);
        List<ItemDisplay> left = new ArrayList<>(parts.values());
        if (held != null) left.add(held);
        if (second != null) left.add(second);
        if (addedInvisibility && player != null && player.isOnline()) player.removePotionEffect(PotionEffectType.INVISIBILITY);
        if (madeInvisible && entity.isValid()) entity.setInvisible(false);
        for (Runnable r : restore) r.run();
        restore.clear();
        UUID id = entity.getUniqueId();
        clearLinger(id);
        if (!linger || !alive() || !CrossfightPlugin.get().isEnabled()) { for (ItemDisplay d : left) d.remove(); return; }
        LINGER.put(id, left);
        Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> {
            if (LINGER.remove(id, left)) for (ItemDisplay d : left) d.remove();
        }, LINGER_TICKS);
    }

    public static void removeAll() {
        for (Puppet pu : new ArrayList<>(ACTIVE.values())) pu.remove(false);
        for (UUID id : new ArrayList<>(LINGER.keySet())) clearLinger(id);
    }
}
