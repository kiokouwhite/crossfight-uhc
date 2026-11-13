package com.kiokouwhite.crossfight.character.rwby;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.Sounds;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Un signe de Glyph de Weiss : une image du pack prive (tools/build_glyphs.py, "glyph.&lt;nom&gt;"), blanche,
 * affichee a plat par un ItemDisplay et teinte a la couleur du Dust. Il apparait en grandissant, peut suivre
 * Weiss (dans son dos), tourner sur lui-meme, puis s'efface en retrecissant.
 * Sans le pack prive, {@link #spawn} ne fait rien et rend null : les Glyphs gardent leurs particules.
 */
final class Glyph {

    /** Le flocon des Schnee (dans le dos de Weiss) et le grand cercle (au sol, sur un mur, devant elle). */
    static final String FLOCON = "schnee_flocon", CERCLE = "schnee_cercle";

    /** A plat sur le sol (le carre de l'image est debout, face a l'avant de l'affichage). */
    static final Quaternionf FLAT = new Quaternionf().rotationX((float) Math.toRadians(-90));
    static final Quaternionf UPRIGHT = new Quaternionf();

    private static final int GROW = 5;

    private final ItemDisplay display;
    private final float size;
    private final Quaternionf base;
    private final Vector3f offset;
    private float angle, speed;
    private Player follow;
    private int age, closeAt = -1;
    private BukkitRunnable task;

    private Glyph(ItemDisplay display, float size, Quaternionf base, Vector3f offset) {
        this.display = display;
        this.size = size;
        this.base = new Quaternionf(base);
        this.offset = new Vector3f(offset);
    }

    static boolean available(String name) { return Sounds.has("glyph." + name); }

    /** Juste au-dessus du sol sous ce point (le haut du bloc, demi-dalle comprise), sans orientation. */
    static Location ground(Location at) {
        var w = at.getWorld();
        Location l = at.clone();
        int y = com.kiokouwhite.crossfight.util.Effects.groundAround(w, at.getBlockX(), at.getBlockZ(), at.getBlockY());
        if (y != Integer.MIN_VALUE) {
            var box = w.getBlockAt(at.getBlockX(), y, at.getBlockZ()).getBoundingBox();
            l.setY((box.getVolume() > 0 ? box.getMaxY() : y + 1) + 0.03);
        } else {
            l.setY(at.getY() + 0.03);
        }
        l.setYaw(0);
        l.setPitch(0);
        return l;
    }

    /**
     * Fait apparaitre un signe en {@code at} (son orientation donne celle de l'image : yaw et pitch de la
     * position), {@code size} blocs de large, decale de {@code offset} dans son propre repere.
     */
    static Glyph spawn(Location at, String name, Color color, float size, Quaternionf base, Vector3f offset) {
        if (!available(name) || at.getWorld() == null) return null;
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey(Sounds.NS, "glyph/" + name));
        var cmd = meta.getCustomModelDataComponent();
        cmd.setColors(List.of(color));
        meta.setCustomModelDataComponent(cmd);
        meta.setHideTooltip(true);
        it.setItemMeta(meta);
        Glyph g = new Glyph(null, size, base, offset);
        ItemDisplay d = at.getWorld().spawn(at, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.setItemStack(it);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setBillboard(Display.Billboard.FIXED);
            e.setBrightness(new Display.Brightness(15, 15));
            e.setViewRange(2f);
            e.setTeleportDuration(1);
            e.setTransformation(g.transformation(0.02f));
        });
        Glyph out = new Glyph(d, size, base, offset);
        out.start();
        return out;
    }

    /** Le signe tourne dans son plan, de {@code degreesPerTick} degres a chaque tick. */
    Glyph spin(float degreesPerTick) { this.speed = degreesPerTick; return this; }

    /** Le signe suit ce joueur (sa position et l'orientation de son corps), avec son decalage. */
    Glyph follow(Player p) { this.follow = p; return this; }

    /** Le signe s'efface (il retrecit puis disparait). */
    void close() {
        if (closeAt < 0) closeAt = age;
    }

    boolean alive() { return task != null && display.isValid(); }

    Location location() { return display.getLocation(); }

    private Transformation transformation(float scale) {
        Quaternionf r = new Quaternionf(base).rotateZ((float) Math.toRadians(angle));
        float s = size * scale;
        return new Transformation(new Vector3f(offset), r, new Vector3f(s, s, 1f), new Quaternionf());
    }

    private void start() {
        task = new BukkitRunnable() {
            @Override public void run() {
                age++;
                if (!display.isValid() || (follow != null && (!follow.isOnline() || !follow.getWorld().equals(display.getWorld())))) { stop(); return; }
                if (follow != null) {
                    Location l = follow.getLocation();
                    l.setYaw(follow.getBodyYaw());
                    l.setPitch(0);
                    display.teleport(l);
                }
                if (closeAt >= 0 && age - closeAt > GROW) { stop(); return; }
                // Apparition et disparition en GROW ticks ; entre les deux, la rotation avance tous les 2 ticks.
                boolean grow = age == 1 || (closeAt >= 0 && age == closeAt + 1);
                if (speed != 0 && age % 2 == 0) angle = (angle + speed * 2) % 360;
                if (grow || (speed != 0 && age % 2 == 0)) {
                    display.setInterpolationDelay(0);
                    display.setInterpolationDuration(grow ? GROW : 2);
                    display.setTransformation(transformation(closeAt >= 0 ? 0.02f : 1f));
                }
            }
        };
        task.runTaskTimer(CrossfightPlugin.get(), 1L, 1L);
    }

    private void stop() {
        if (task != null) { task.cancel(); task = null; }
        display.remove();
    }
}
