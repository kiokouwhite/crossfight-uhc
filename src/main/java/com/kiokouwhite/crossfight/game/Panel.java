package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Un tableau dresse dans le monde, que seul un joueur voit : des rectangles de couleur et des textes (des affichages de
 * texte) dans un plan tourne vers lui. Les positions se donnent en blocs depuis le milieu du tableau : u vers la droite
 * du joueur, v vers le haut. Le viseur est le point ou son regard coupe ce plan.
 */
abstract class Panel {

    /** Le fond d'un texte d'une espace, a l'echelle 1 : sa largeur et sa hauteur en blocs, et son debord vers la droite. */
    private static final double W0 = 0.125, H0 = 0.25, OFF = 0.0125;
    static final Color NONE = Color.fromARGB(0, 0, 0, 0);

    final CrossfightPlugin plugin;
    final Player player;
    final Location centre;
    final Vector normal, right;
    final float yaw;
    final double width, height;
    final List<Entity> all = new ArrayList<>();
    double cu, cv;
    boolean aimed;

    /** Le tableau se pose devant ce point (un pilier), tourne vers le joueur, son milieu deux blocs au-dessus du sol. */
    Panel(CrossfightPlugin plugin, Player player, Location pillar, double width, double height) {
        this.plugin = plugin;
        this.player = player;
        this.width = width;
        this.height = height;
        Vector to = player.getLocation().toVector().subtract(pillar.toVector()).setY(0);
        if (to.lengthSquared() < 0.01) to = new Vector(0, 0, 1);
        this.normal = to.normalize();
        this.right = new Vector(normal.getZ(), 0, -normal.getX());
        this.centre = pillar.clone().add(normal.clone().multiply(0.7)).add(0, 2.0, 0);
        this.yaw = new Location(pillar.getWorld(), 0, 0, 0).setDirection(normal).getYaw();
    }

    Location spot(double u, double v, double depth) {
        Location l = centre.clone().add(right.clone().multiply(u)).add(0, v, 0).add(normal.clone().multiply(depth));
        l.setYaw(yaw);
        l.setPitch(0);
        return l;
    }

    /** Un rectangle de couleur de a x b blocs, centre en (u, v). */
    TextDisplay rect(double u, double v, double a, double b, double depth, Color color) {
        float sx = (float) (a / W0), sy = (float) (b / H0);
        TextDisplay d = centre.getWorld().spawn(spot(u, v - b / 2, depth), TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setVisibleByDefault(false);
            t.setBillboard(Display.Billboard.FIXED);
            t.setBrightness(new Display.Brightness(15, 15));
            t.setViewRange(1f);
            t.text(net.kyori.adventure.text.Component.text(" "));
            t.setBackgroundColor(color);
            t.setTransformation(new Transformation(new Vector3f((float) (-OFF * sx), 0, 0), new Quaternionf(), new Vector3f(sx, sy, 1), new Quaternionf()));
        });
        player.showEntity(plugin, d);
        all.add(d);
        return d;
    }

    TextDisplay label(double u, double v, String mini, float scale) {
        TextDisplay d = centre.getWorld().spawn(spot(u, v, 0.03), TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setVisibleByDefault(false);
            t.setBillboard(Display.Billboard.FIXED);
            t.setBrightness(new Display.Brightness(15, 15));
            t.setViewRange(1f);
            t.setLineWidth(400);
            t.text(Msg.mm(mini));
            t.setBackgroundColor(NONE);
            t.setShadowed(true);
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(scale, scale, 1), new Quaternionf()));
        });
        player.showEntity(plugin, d);
        all.add(d);
        return d;
    }

    /** Un objet du jeu montre a plat, comme dans un inventaire, de `size` blocs de cote, centre en (u, v). */
    org.bukkit.entity.ItemDisplay item(double u, double v, double size, double depth, org.bukkit.inventory.ItemStack stack) {
        org.bukkit.entity.ItemDisplay d = centre.getWorld().spawn(spot(u, v, depth), org.bukkit.entity.ItemDisplay.class, t -> {
            t.setPersistent(false);
            t.setVisibleByDefault(false);
            t.setBillboard(Display.Billboard.FIXED);
            t.setBrightness(new Display.Brightness(15, 15));
            t.setViewRange(1f);
            t.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.GUI);
            t.setItemStack(stack);
            // un demi-tour : sans lui, on voit l'objet de dos (sombre, et a l'envers)
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateY((float) Math.PI), new Vector3f((float) size, (float) size, 0.01f), new Quaternionf()));
        });
        player.showEntity(plugin, d);
        all.add(d);
        return d;
    }

    /** De quoi recevoir les clics droits (a main nue, le jeu n'en envoie pas dans le vide) : une rangee de boites devant le tableau. */
    void boxes() {
        for (double u = -width / 2 + 0.25; u < width / 2; u += 0.5) {
            Interaction box = centre.getWorld().spawn(spot(u, -height / 2, 0.1), Interaction.class, t -> {
                t.setPersistent(false);
                t.setVisibleByDefault(false);
                t.setInteractionWidth(0.5f);
                t.setInteractionHeight((float) height);
                t.setResponsive(false);
            });
            player.showEntity(plugin, box);
            all.add(box);
        }
    }

    /** Ou le regard coupe le tableau. */
    void aim() {
        Location eye = player.getEyeLocation();
        Vector d = eye.getDirection();
        double facing = d.dot(normal);
        aimed = false;
        if (facing > -0.05) return;
        double t = centre.toVector().subtract(eye.toVector()).dot(normal) / facing;
        if (t < 0 || t > 12) return;
        Vector hit = eye.toVector().add(d.multiply(t)).subtract(centre.toVector());
        cu = hit.dot(right);
        cv = hit.getY();
        aimed = Math.abs(cu) < width / 2 + 0.5 && Math.abs(cv) < height / 2 + 0.5;
    }

    /** Vrai si le joueur n'est plus en etat de s'en servir (parti, mort, trop loin). */
    boolean lost() {
        return !player.isOnline() || player.isDead() || !player.getWorld().equals(centre.getWorld()) || player.getLocation().distanceSquared(centre) > 81;
    }

    void remove() {
        all.forEach(Entity::remove);
        all.clear();
    }
}
