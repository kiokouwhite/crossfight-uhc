package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Petits effets visuels partages : les images d'effets du pack (tools/build_glyphs.py : un orbe, un anneau de
 * vent, un couteau de lancer...) montrees par des affichages lumineux, et des traits de particules.
 */
public final class Fx {

    private Fx() {}

    /** Vrai si le pack contient cette image d'effet ("glyph.<nom>"). */
    public static boolean has(String image) { return Sounds.has("glyph." + image); }

    /** Une image d'effet du pack, teinte de cette couleur (blanc : telle quelle) ; a montrer avec {@link #show}. */
    public static ItemStack image(String name, Color color) {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey(Sounds.NS, "glyph/" + name));
        var cmd = meta.getCustomModelDataComponent();
        cmd.setColors(List.of(color));
        meta.setCustomModelDataComponent(cmd);
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        return it;
    }

    /** L'image d'effet si le pack l'a, sinon cet objet de Minecraft a sa place. */
    public static ItemStack imageOr(String name, Color color, Material fallback) {
        return has(name) ? image(name, color) : new ItemStack(fallback);
    }

    /**
     * Un affichage lumineux de cet objet, large de {@code size} blocs, qui ne dure pas au-dela de la partie.
     *
     * @param billboard toujours face a celui qui regarde (un orbe) ; sinon il garde l'orientation de {@code at}
     */
    public static ItemDisplay show(Location at, ItemStack item, float size, boolean billboard) {
        return at.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(item);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBillboard(billboard ? Display.Billboard.CENTER : Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(2f);
            d.setTeleportDuration(1);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(size, size, size), new Quaternionf()));
        });
    }

    /** Amene un affichage a cette taille, tourne de {@code turn} degres dans son plan, en {@code ticks} ticks. */
    public static void resize(ItemDisplay d, float size, float turn, int ticks) {
        if (!d.isValid()) return;
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
        d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationZ((float) Math.toRadians(turn)), new Vector3f(size, size, size), new Quaternionf()));
    }

    /** Retire une entite d'effet dans {@code ticks} ticks. */
    public static void removeLater(Entity e, int ticks) {
        Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> { if (e.isValid()) e.remove(); }, ticks);
    }

    /**
     * Un anneau qui s'ouvre puis disparait : l'image {@code image} posee face a {@code normal} (vers le haut : a
     * plat sur le sol), qui passe de {@code from} a {@code to} blocs en tournant de {@code turn} degres.
     * Sans l'image dans le pack : un cercle de nuages.
     */
    public static void burst(Location at, Vector normal, String image, Color color, float from, float to, int ticks, float turn) {
        if (!has(image)) {
            Vector n = normal.clone().normalize();
            Vector a = Math.abs(n.getY()) > 0.9 ? new Vector(1, 0, 0) : n.clone().crossProduct(new Vector(0, 1, 0)).normalize();
            Vector b = n.clone().crossProduct(a).normalize();
            for (int i = 0; i < 20; i++) {
                double ang = i * Math.PI / 10;
                Vector out = a.clone().multiply(Math.cos(ang)).add(b.clone().multiply(Math.sin(ang)));
                at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(out.clone().multiply(from / 2)), 0, out.getX(), out.getY(), out.getZ(), 0.12 * to / Math.max(1, ticks) * 4);
            }
            return;
        }
        Location l = at.clone().setDirection(normal);
        ItemDisplay d = show(l, image(image, color), from, false);
        Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> resize(d, to, turn, ticks), 2L);
        removeLater(d, ticks + 3);
    }

    /** Un trait de poussiere coloree entre deux points, un grain tous les {@code step} blocs. */
    public static void line(Location a, Location b, double step, Particle.DustOptions dust) {
        if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) return;
        Vector v = b.toVector().subtract(a.toVector());
        double length = v.length();
        if (length < 0.05 || length > 120) return;
        v.multiply(1 / length);
        for (double d = 0; d <= length; d += step) a.getWorld().spawnParticle(Particle.DUST, a.clone().add(v.clone().multiply(d)), 1, 0, 0, 0, 0, dust);
    }

    /** Un eclair : une ligne brisee entre deux points, qui zigzague de {@code jitter} blocs. */
    public static void bolt(Location a, Location b, double jitter, Particle.DustOptions dust) {
        if (a.getWorld() == null || !a.getWorld().equals(b.getWorld())) return;
        Vector v = b.toVector().subtract(a.toVector());
        int pieces = (int) Math.max(2, Math.min(24, Math.ceil(v.length() / 0.9)));
        Location from = a.clone();
        for (int i = 1; i <= pieces; i++) {
            Location to = a.clone().add(v.clone().multiply(i / (double) pieces));
            if (i < pieces) to.add((Math.random() - 0.5) * 2 * jitter, (Math.random() - 0.5) * 2 * jitter, (Math.random() - 0.5) * 2 * jitter);
            line(from, to, 0.22, dust);
            from = to;
        }
    }

    /**
     * L'orientation qui couche une image d'objet de Minecraft (pointe en haut a droite, comme une epee) le long
     * d'une direction : la pointe vers {@code tip}, le plat de l'image tourne autant que possible vers {@code face}.
     * Pour un affichage sans orientation propre (yaw et pitch nuls).
     */
    public static Quaternionf along(Vector tip, Vector face) {
        return turn(new Vector3f(-1, 1, 0).normalize(), tip, face);
    }

    /** La meme chose pour une image d'effet dessinee debout (pointe en haut : le couteau de lancer). */
    public static Quaternionf upright(Vector tip, Vector face) {
        return turn(new Vector3f(0, 1, 0), tip, face);
    }

    /** Amene la pointe d'une image (donnee dans le repere de son affichage) sur {@code tip}, son plat vers {@code face}. */
    private static Quaternionf turn(Vector3f imageTip, Vector tip, Vector face) {
        Vector3f t = tip.toVector3f().normalize();
        Vector3f f = face.toVector3f();
        f.sub(new Vector3f(t).mul(f.dot(t)));
        if (f.lengthSquared() < 0.04f) f.set(0, 1, 0).sub(new Vector3f(t).mul(t.y));
        if (f.lengthSquared() < 0.04f) f.set(1, 0, 0);
        f.normalize();
        Vector3f imageFlat = new Vector3f(0, 0, 1);
        org.joml.Matrix3f image = new org.joml.Matrix3f(imageTip, imageFlat, new Vector3f(imageTip).cross(imageFlat));
        org.joml.Matrix3f world = new org.joml.Matrix3f(t, f, new Vector3f(t).cross(f));
        return new Quaternionf().setFromNormalized(world.mul(image.transpose()));
    }
}
