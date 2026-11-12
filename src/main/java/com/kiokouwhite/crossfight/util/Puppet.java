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

    private final Player player;
    private final Map<Part, ItemDisplay> parts = new EnumMap<>(Part.class);
    private ItemDisplay held;
    private BukkitTask task;
    private boolean addedInvisibility, ownArmorHidden;
    private int age, endAt = -1, removeAt = 20 * 30;
    private final List<Runnable> restore = new ArrayList<>();

    private Puppet(Player player) { this.player = player; }

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

    public static Puppet of(Player p) { return ACTIVE.get(p.getUniqueId()); }

    /**
     * Remplace le joueur par son pantin, dans la pose donnee. Rend null si le pack n'a pas le modele du
     * personnage : l'appelant garde alors ses effets habituels.
     *
     * @param heldItem l'objet montre dans la main droite (null : aucun)
     */
    public static Puppet show(Player p, String character, ItemStack heldItem, String pose) {
        if (!available(character) || !POSES.containsKey(pose)) return null;
        Puppet old = ACTIVE.remove(p.getUniqueId());
        if (old != null) old.remove();
        Puppet pu = new Puppet(p);
        Location at = pu.anchor();
        for (Part part : Part.values()) {
            ItemStack model = new ItemStack(Material.PAPER);
            ItemMeta meta = model.getItemMeta();
            meta.setItemModel(new NamespacedKey(Sounds.NS, "pose/" + character + "_" + part.id));
            model.setItemMeta(meta);
            pu.parts.put(part, pu.spawn(at, model));
        }
        if (heldItem != null && !heldItem.getType().isAir()) pu.held = pu.spawn(at, heldItem.clone());
        pu.apply(pose, 0);
        pu.hidePlayer();
        ACTIVE.put(p.getUniqueId(), pu);
        pu.task = Bukkit.getScheduler().runTaskTimer(CrossfightPlugin.get(), pu::tick, 1L, 1L);
        return pu;
    }

    private ItemDisplay spawn(Location at, ItemStack item) {
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setTeleportDuration(1);
            d.setViewRange(1.5f);
        });
    }

    private Location anchor() {
        Location l = player.getLocation();
        l.setPitch(0);
        return l;
    }

    /** Passe a une autre pose en quelques ticks (0 : tout de suite). */
    public void pose(String pose, int ticks) {
        if (!POSES.containsKey(pose)) return;
        // Un affichage qui vient d'apparaitre n'a pas d'etat precedent : il sauterait directement a la pose.
        if (age < 2) Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> { if (task != null) apply(pose, ticks); }, 2L - age);
        else apply(pose, ticks);
    }

    /** Termine l'animation dans {@code after} ticks : retour a la position debout en {@code blend} ticks, puis le joueur reapparait. */
    public void endAfter(int after, int blend) {
        endAt = age + after;
        removeAt = endAt + blend + 1;
        this.blend = blend;
    }

    private int blend = 3;

    private void tick() {
        age++;
        if (!player.isOnline() || player.isDead() || age >= removeAt || parts.values().stream().anyMatch(d -> !d.isValid())
                || !player.getWorld().equals(parts.get(Part.BODY).getWorld())) { remove(); return; }
        // Un clic sur une capacite fait renvoyer tout l'inventaire au joueur juste apres : son armure
        // reapparaitrait chez lui. On la re-masque donc regulierement tant que dure l'animation.
        if (ownArmorHidden && age % 4 == 1) hideOwnArmor(player);
        Location at = anchor();
        for (ItemDisplay d : parts.values()) d.teleport(at);
        if (held != null) held.teleport(at);
        if (age == endAt) apply("debout", blend);
    }

    private void apply(String pose, int ticks) {
        Map<String, Joint> joints = POSES.get(pose);
        Joint root = joints.getOrDefault("root", Joint.REST);
        Quaternionf rootR = euler(root.angles);
        Vector3f rootT = new Vector3f(root.offset).mul(K / 16f);
        for (Part part : Part.values()) {
            Joint j = joints.getOrDefault(part.id, Joint.REST);
            Quaternionf r = new Quaternionf(rootR).mul(euler(j.angles));
            Vector3f t = rootR.transform(new Vector3f(part.pivot).add(j.offset).mul(K / 16f)).add(rootT);
            set(parts.get(part), new Transformation(t, r, new Vector3f(K, K, K), new Quaternionf()), ticks);
            if (part == Part.ARM_R && held != null) {
                // "item" : oriente par rapport au bras ; "item_world" : par rapport au corps, quel que soit le bras.
                Joint world = joints.get("item_world");
                Quaternionf ri = (world != null ? new Quaternionf(rootR).mul(euler(world.angles))
                        : new Quaternionf(r).mul(euler(joints.getOrDefault("item", Joint.REST).angles))).mul(ITEM_BASE);
                Vector3f hand = r.transform(new Vector3f(HAND).mul(K / 16f)).add(t);
                Vector3f ti = hand.sub(ri.transform(new Vector3f(GRIP).mul(ITEM_SCALE / 16f)));
                set(held, new Transformation(ti, ri, new Vector3f(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE), new Quaternionf()), ticks);
            }
        }
    }

    private static void set(ItemDisplay d, Transformation tr, int ticks) {
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
        d.setTransformation(tr);
    }

    /** Le joueur disparait : invisible, et son equipement n'est plus montre (il flotterait dans le vide). */
    private void hidePlayer() {
        if (!player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20 * 30, 0, false, false, false));
            addedInvisibility = true;
        }
        EquipmentSlot[] all = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HAND, EquipmentSlot.OFF_HAND};
        for (Player viewer : player.getWorld().getPlayers()) {
            if (viewer.equals(player) || viewer.getLocation().distanceSquared(player.getLocation()) > 96 * 96) continue;
            Map<EquipmentSlot, ItemStack> empty = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot s : all) empty.put(s, ItemStack.empty());
            viewer.sendEquipmentChange(player, empty);
            restore.add(() -> {
                if (!viewer.isOnline() || !player.isOnline()) return;
                Map<EquipmentSlot, ItemStack> real = new EnumMap<>(EquipmentSlot.class);
                for (EquipmentSlot s : all) { ItemStack it = player.getInventory().getItem(s); real.put(s, it == null ? ItemStack.empty() : it); }
                viewer.sendEquipmentChange(player, real);
            });
        }
        // Le joueur lui-meme (vue exterieure) : son client ne tient pas compte d'un paquet d'equipement
        // qui le concerne, il affiche l'armure de son propre inventaire. On vide donc chez lui, et chez lui
        // seulement, ses quatre cases d'armure ; elles sont renvoyees telles quelles a la fin.
        if (hideOwnArmor(player)) {
            ownArmorHidden = true;
            restore.add(() -> { if (player.isOnline()) player.updateInventory(); });
        }
    }

    private static boolean ownArmorWarned;

    /**
     * Envoie au joueur quatre cases d'armure vides (cases 5 a 8 de son inventaire), sans toucher a son
     * vrai inventaire. L'API de Paper n'a pas d'equivalent : on passe par les classes du serveur.
     */
    private static boolean hideOwnArmor(Player p) {
        try {
            Object handle = p.getClass().getMethod("getHandle").invoke(p);
            Object menu = handle.getClass().getField("inventoryMenu").get(handle);
            int state = (int) menu.getClass().getMethod("getStateId").invoke(menu);
            Object connection = handle.getClass().getField("connection").get(handle);
            Class<?> stack = Class.forName("net.minecraft.world.item.ItemStack");
            Object empty = stack.getField("EMPTY").get(null);
            var packet = Class.forName("net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket")
                    .getConstructor(int.class, int.class, int.class, stack);
            var send = connection.getClass().getMethod("send", Class.forName("net.minecraft.network.protocol.Packet"));
            for (int slot = 5; slot <= 8; slot++) send.invoke(connection, packet.newInstance(0, state, slot, empty));
            return true;
        } catch (Throwable t) {
            if (!ownArmorWarned) { ownArmorWarned = true; CrossfightPlugin.get().getLogger().warning("Armure du joueur non masquee pour lui-meme : " + t); }
            return false;
        }
    }

    /** Retire le pantin et fait reapparaitre le joueur. */
    public void remove() {
        if (task != null) { task.cancel(); task = null; }
        ACTIVE.remove(player.getUniqueId(), this);
        for (ItemDisplay d : parts.values()) d.remove();
        if (held != null) held.remove();
        if (addedInvisibility && player.isOnline()) player.removePotionEffect(PotionEffectType.INVISIBILITY);
        for (Runnable r : restore) r.run();
        restore.clear();
    }

    public static void removeAll() {
        for (Puppet pu : new ArrayList<>(ACTIVE.values())) pu.remove();
    }
}
