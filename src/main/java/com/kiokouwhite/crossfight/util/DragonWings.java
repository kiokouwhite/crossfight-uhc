package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Une paire d'ailes de dragon (membrane translucide rose pale et blanche, aretes argentees)
 * faite de BlockDisplay qui suivent le joueur et battent. Pour Sang Draconique de Corrin.
 */
public final class DragonWings {

    /** Un morceau d'aile, dans le plan de l'aile : centre (u sur le cote, v vers le haut), angle, taille. */
    private record Part(boolean right, double u, double v, double angle, double length, double width, double thick, Material mat) {}

    private final Player p;
    private final List<Part> parts = new ArrayList<>();
    private final List<BlockDisplay> displays = new ArrayList<>();
    private BukkitRunnable task;
    private int t;

    /** Ou les ailes s'attachent : distance devant le joueur (negative : dans son dos) et hauteur, en blocs. */
    private final double forward, height;

    private DragonWings(Player p, double forward, double height) { this.p = p; this.forward = forward; this.height = height; }

    /** Fait pousser les ailes dans le dos d'un joueur debout et les anime jusqu'a {@link #remove()} (ou le joueur disparait). */
    public static DragonWings grow(Player p) { return grow(p, -0.3, 1.25); }

    /** Les memes ailes, attachees ailleurs (ex. sur le dos d'un personnage a quatre pattes). */
    public static DragonWings grow(Player p, double forward, double height) {
        DragonWings w = new DragonWings(p, forward, height);
        w.build();
        w.spawn();
        w.task = new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline() || p.isDead()) { w.remove(); return; }
                w.t++;
                w.update();
            }
        };
        w.task.runTaskTimer(CrossfightPlugin.get(), 1L, 1L);
        return w;
    }

    public void remove() {
        if (task != null) { task.cancel(); task = null; }
        for (BlockDisplay d : displays) if (d.isValid()) d.remove();
        displays.clear();
    }

    public boolean alive() { return task != null; }

    // -------------------------------------------------------------- geometrie

    /** Cinq "doigts" par aile (angle en degres depuis l'horizontale, longueur en blocs), comme une aile de chauve-souris. */
    private static final double[][] FINGERS = { {62, 4.0}, {40, 3.6}, {20, 3.0}, {2, 2.3}, {-14, 1.6} };

    private void build() {
        for (boolean right : new boolean[]{true, false}) {
            for (double[] f : FINGERS) {
                double a = Math.toRadians(f[0]), len = f[1];
                parts.add(new Part(right, Math.cos(a) * len / 2, Math.sin(a) * len / 2, a, len, 0.07, 0.07, Material.IRON_BLOCK));
                parts.add(new Part(right, Math.cos(a) * len, Math.sin(a) * len, a, 0.16, 0.12, 0.12, Material.IRON_BLOCK)); // la griffe au bout
            }
            for (int i = 0; i < FINGERS.length - 1; i++) {
                double a0 = Math.toRadians(FINGERS[i][0]), a1 = Math.toRadians(FINGERS[i + 1][0]);
                double mid = (a0 + a1) / 2, gap = Math.abs(a0 - a1);
                double max = Math.min(FINGERS[i][1], FINGERS[i + 1][1]) - 0.15;
                for (double r = 0.4; r <= max; r += 0.5) {
                    double chord = 2 * r * Math.sin(gap / 2) + 0.1;
                    Material m = r < 1.3 ? Material.PINK_STAINED_GLASS : Material.WHITE_STAINED_GLASS;
                    parts.add(new Part(right, Math.cos(mid) * r, Math.sin(mid) * r, mid, 0.55, chord, 0.03, m));
                }
            }
            // L'epaule : un petit bloc argente a la racine.
            parts.add(new Part(right, 0.15, 0.1, 0, 0.3, 0.25, 0.25, Material.IRON_BLOCK));
        }
    }

    private void spawn() {
        World w = p.getWorld();
        Frame fr = frame();
        for (Part part : parts) {
            Location at = position(fr, part);
            BlockDisplay d = w.spawn(at, BlockDisplay.class, disp -> {
                disp.setBlock(part.mat.createBlockData());
                disp.setTransformation(transformation(fr, part));
                disp.setBrightness(new Display.Brightness(15, 15));
                disp.setTeleportDuration(1);
                disp.setInterpolationDuration(1);
                disp.setPersistent(false);
                disp.setViewRange(2f);
            });
            displays.add(d);
        }
    }

    private void update() {
        Frame fr = frame();
        for (int i = 0; i < parts.size() && i < displays.size(); i++) {
            BlockDisplay d = displays.get(i);
            if (!d.isValid()) continue;
            Part part = parts.get(i);
            d.teleport(position(fr, part));
            d.setInterpolationDelay(0);
            d.setTransformation(transformation(fr, part));
        }
        // Eclaboussures d'eau le long des ailes.
        World w = p.getWorld();
        for (int k = 0; k < 3; k++) {
            Part part = parts.get((int) (Math.random() * parts.size()));
            w.spawnParticle(Particle.SPLASH, position(fr, part), 2, 0.15, 0.15, 0.15, 0);
        }
        if (t % 9 == 0) w.spawnParticle(Particle.BUBBLE_POP, fr.origin, 4, 0.8, 0.4, 0.8, 0);
    }

    /** Le repere des ailes a cet instant : origine dans le dos, axes cote/haut (incline en arriere) et battement. */
    private record Frame(Location origin, Vector side, Vector up, Vector normal, double flap) {}

    private Frame frame() {
        Location base = p.getLocation();
        double yaw = Math.toRadians(base.getYaw());
        Vector forward = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        Vector side = new Vector(Math.cos(yaw), 0, Math.sin(yaw)); // la droite du joueur
        Vector upWorld = new Vector(0, 1, 0);
        // Le plan de l'aile est incline vers l'arriere de 32 degres.
        double tilt = Math.toRadians(32);
        Vector up = upWorld.clone().multiply(Math.cos(tilt)).add(forward.clone().multiply(-Math.sin(tilt)));
        Vector normal = side.clone().crossProduct(up).normalize();
        Location origin = base.clone().add(0, height, 0).add(forward.clone().multiply(this.forward));
        // Les morceaux sont orientes en coordonnees du monde : leur entite ne doit pas, en plus, tourner
        // avec le regard du joueur (sinon toute l'aile part de travers des qu'il tourne la tete).
        origin.setYaw(0);
        origin.setPitch(0);
        // Battement : plus ample et plus rapide en vol, lent en plane.
        double period = p.isFlying() ? 16.0 : 36.0, amp = p.isFlying() ? 0.32 : 0.14;
        double flap = amp * Math.sin(t * 2 * Math.PI / period);
        return new Frame(origin, side, up, normal, flap);
    }

    /** Point du plan (u, v) une fois battu, en coordonnees monde. */
    private Vector world(Frame fr, boolean right, double u, double v) {
        // Le battement tourne toute l'aile dans son plan autour de l'epaule.
        double cu = u * Math.cos(fr.flap) - v * Math.sin(fr.flap), cv = u * Math.sin(fr.flap) + v * Math.cos(fr.flap);
        return fr.side.clone().multiply(right ? cu : -cu).add(fr.up.clone().multiply(cv));
    }

    private Location position(Frame fr, Part part) {
        Vector off = world(fr, part.right, part.u, part.v);
        return fr.origin.clone().add(off);
    }

    private Transformation transformation(Frame fr, Part part) {
        double a = part.angle + fr.flap;
        Vector dir = fr.side.clone().multiply(part.right ? Math.cos(a) : -Math.cos(a)).add(fr.up.clone().multiply(Math.sin(a))).normalize();
        Vector n = fr.normal;
        // Troisieme axe : longueur x epaisseur, dans cet ordre, pour un repere direct (dans l'autre sens la
        // matrice est un miroir, pas une rotation, et l'orientation obtenue n'a aucun sens).
        Vector bin = dir.clone().crossProduct(n).normalize();
        Matrix3f m = new Matrix3f(
                (float) dir.getX(), (float) dir.getY(), (float) dir.getZ(),
                (float) n.getX(), (float) n.getY(), (float) n.getZ(),
                (float) bin.getX(), (float) bin.getY(), (float) bin.getZ());
        Quaternionf rot = new Quaternionf().setFromNormalized(m);
        Vector3f scale = new Vector3f((float) part.length, (float) part.thick, (float) part.width);
        Vector3f half = new Vector3f(scale).mul(0.5f);
        rot.transform(half);
        return new Transformation(half.negate(), rot, scale, new Quaternionf());
    }
}
