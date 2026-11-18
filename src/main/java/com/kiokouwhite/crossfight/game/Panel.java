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
    /** Les tailles de tableau qui ont leur fenetre dans le pack (tools/build_enigmes_franuka.py, meme ordre), a 32 points par bloc. */
    private static final double[][] WINDOWS = {{5.0, 3.2}, {3.6, 3.6}, {4.4, 3.5}, {5.0, 3.7}};
    /** Ou tombe le bas d'une image de la police, en points de l'image, sous le point ou l'affichage est pose. */
    private static final double DROP = -2;

    /** Avec le pack d'interface : la fenetre et les cases sont des images. Sinon : des rectangles bleu nuit. */
    final boolean drawn;
    /** Les couleurs du tableau : une case vide, visee, pleine (doree), annulee, la piece qu'on tient, le fond d'une tuile sombre. */
    final Color empty, pointed, gold, faded, carried, dark;
    private final java.util.Map<java.util.UUID, net.kyori.adventure.text.Component> images = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, int[]> sizes = new java.util.HashMap<>();
    /** Avec le pack : les textes sont dans sa police (celle du panneau de quete), pas dans celle du jeu. */
    final boolean lettered;

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
        this.drawn = com.kiokouwhite.crossfight.util.Sounds.has("font.enigme") && com.kiokouwhite.crossfight.util.Sounds.hasPackLoaded(player);
        this.lettered = com.kiokouwhite.crossfight.util.Sounds.has("font.quete_texte") && com.kiokouwhite.crossfight.util.Sounds.hasPackLoaded(player);
        this.empty = drawn ? Color.fromARGB(255, 226, 180, 158) : Color.fromARGB(255, 34, 40, 76);
        this.pointed = drawn ? Color.fromARGB(255, 255, 236, 214) : Color.fromARGB(255, 70, 80, 130);
        this.gold = drawn ? Color.fromARGB(255, 250, 184, 48) : Color.fromARGB(255, 246, 190, 60);
        this.faded = drawn ? Color.fromARGB(255, 150, 112, 96) : Color.fromARGB(255, 110, 92, 58);
        this.carried = drawn ? Color.fromARGB(255, 255, 132, 44) : Color.fromARGB(200, 255, 140, 40);
        this.dark = drawn ? Color.fromARGB(255, 84, 90, 140) : Color.fromARGB(255, 34, 40, 76);
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

    /** Une image de la police "enigme" (w x h points), etiree a a x b blocs, centree en (u, v), teintee. */
    TextDisplay picture(String font, char glyph, int w, int h, double u, double v, double a, double b, double depth, Color tint) {
        float sx = (float) (a / (w / 40.0)), sy = (float) (b / (h / 40.0));
        TextDisplay d = centre.getWorld().spawn(spot(u, v - b / 2, depth), TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setVisibleByDefault(false);
            t.setBillboard(Display.Billboard.FIXED);
            t.setBrightness(new Display.Brightness(15, 15));
            t.setViewRange(1f);
            t.setBackgroundColor(NONE);
            t.setShadowed(false);
            t.setTransformation(new Transformation(new Vector3f((float) (-OFF * sx), (float) (DROP / 40.0 * sy), 0), new Quaternionf(), new Vector3f(sx, sy, 1), new Quaternionf()));
        });
        sizes.put(d.getUniqueId(), new int[]{w, h});
        images.put(d.getUniqueId(), net.kyori.adventure.text.Component.text(String.valueOf(glyph)).font(net.kyori.adventure.key.Key.key(com.kiokouwhite.crossfight.util.Sounds.NS, font)));
        paint(d, tint);
        player.showEntity(plugin, d);
        all.add(d);
        return d;
    }

    /** Change la taille d'un rectangle ou d'une image : a x b blocs. (Il reste pose par le milieu de son bas.) */
    void resize(TextDisplay d, double a, double b) {
        int[] px = sizes.get(d.getUniqueId());
        float sx = (float) (a / (px == null ? W0 : px[0] / 40.0)), sy = (float) (b / (px == null ? H0 : px[1] / 40.0));
        d.setTransformation(new Transformation(new Vector3f((float) (-OFF * sx), px == null ? 0 : (float) (DROP / 40.0 * sy), 0), new Quaternionf(), new Vector3f(sx, sy, 1), new Quaternionf()));
    }

    /** Le fond du tableau : la fenetre du pack, ou un rectangle bleu nuit. */
    void backdrop() {
        if (drawn) for (int k = 0; k < WINDOWS.length; k++) if (WINDOWS[k][0] == width && WINDOWS[k][1] == height) {
            picture("enigme", (char) (0xE600 + k), (int) Math.round(width * 32), (int) Math.round(height * 32), 0, 0, width, height, 0, Color.WHITE);
            return;
        }
        rect(0, 0, width, height, 0, Color.fromARGB(235, 12, 14, 36));
    }

    /** Une case de a x b blocs, centree en (u, v) : celle du pack, teintee, ou un rectangle de cette couleur. */
    TextDisplay tile(double u, double v, double a, double b, double depth, Color color) {
        return drawn ? picture("enigme", '\uE610', 16, 16, u, v, a, b, depth, color) : rect(u, v, a, b, depth, color);
    }

    /** Un panneau clair de a x b blocs, centre en (u, v), ou l'on pose les choses : le cadre du pack, ou rien. */
    void plate(double u, double v, double a, double b) {
        if (drawn) picture("enigme", '\uE611', 64, 64, u, v, a, b, 0.01, Color.WHITE);
    }

    /** Change la couleur d'un rectangle ou d'une case (NONE : on ne le voit plus). */
    void paint(TextDisplay d, Color color) {
        net.kyori.adventure.text.Component image = images.get(d.getUniqueId());
        if (image == null) { d.setBackgroundColor(color); return; }
        d.text(color.getAlpha() == 0 ? net.kyori.adventure.text.Component.empty() : image.color(net.kyori.adventure.text.format.TextColor.color(color.getRed(), color.getGreen(), color.getBlue())));
        d.setTextOpacity((byte) Math.max(26, color.getAlpha()));
    }

    /** Les couleurs des textes, sur la fenetre claire du pack : sombres, sans ombre. */
    private String inked(String mini) {
        return mini.replace("<aqua>", "<#3e426e>").replace("<white>", "<#3e426e>").replace("<gray>", "<#5c3a40>").replace("<yellow>", "<#7a3410>").replace("<gold>", "<#c06a10>")
                .replace("<red>", "<#b02c3c>").replace("<green>", "<#28783c>");
    }

    TextDisplay label(double u, double v, String mini, float scale) {
        // sur la fenetre du pack, la ligne du bas remonte au-dessus de son cadre
        double up = drawn && v < -height / 2 + 0.2 ? 0.2 : 0;
        // la police du pack n'a ni fleche ni carre plein : ces textes-la gardent celle du jeu ; ses lettres sont plus grosses, on les reduit
        boolean own = lettered && mini.indexOf('←') < 0 && mini.indexOf('■') < 0;
        float size = own ? scale * 0.82f : scale;
        TextDisplay d = centre.getWorld().spawn(spot(u, v + up, 0.03), TextDisplay.class, t -> {
            t.setPersistent(false);
            t.setVisibleByDefault(false);
            t.setBillboard(Display.Billboard.FIXED);
            t.setBrightness(new Display.Brightness(15, 15));
            t.setViewRange(1f);
            t.setLineWidth(400);
            net.kyori.adventure.text.Component text = Msg.mm(drawn ? inked(mini) : mini);
            t.text(own ? text.font(net.kyori.adventure.key.Key.key(com.kiokouwhite.crossfight.util.Sounds.NS, "quete_texte")) : text);
            t.setBackgroundColor(NONE);
            t.setShadowed(!drawn);
            t.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(size, size, 1), new Quaternionf()));
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
