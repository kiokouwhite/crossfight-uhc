package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Du vrai feu de Minecraft accroche a un corps : des flammes sur les bras (Incendie Interne de Yang) ou tout le
 * corps en feu (Courroux Imperissable). Des BlockDisplay de feu qui suivent le joueur tant que {@code keep} est
 * vrai ; lui-meme ne les voit pas (elles lui boucheraient la vue), il voit quelques flammes en particules.
 */
public final class Flames {

    /** Un feu : son pied dans le repere du corps (x a sa gauche, y en haut, z devant lui), sa largeur et sa hauteur, en blocs. */
    private record Piece(double x, double y, double z, float width, float height) {}

    /** Les deux bras d'un joueur debout : un feu qui part de chaque main et monte au-dessus de l'epaule. */
    private static final List<Piece> ARMS = List.of(new Piece(-0.36, 0.68, 0, 0.36f, 0.95f), new Piece(0.36, 0.68, 0, 0.36f, 0.95f));
    /** Le corps entier : le feu a ses pieds, sur ses deux bras et dans ses cheveux. On la voit encore entre les flammes. */
    private static final List<Piece> BODY = List.of(new Piece(0, 0, 0, 0.8f, 0.95f), new Piece(-0.36, 0.68, 0, 0.36f, 0.95f),
            new Piece(0.36, 0.68, 0, 0.36f, 0.95f), new Piece(0, 1.62, -0.05, 0.56f, 0.8f));

    private final LivingEntity owner;
    private final List<Piece> pieces;
    private final List<BlockDisplay> displays = new ArrayList<>();
    private BukkitRunnable task;

    private Flames(LivingEntity owner, List<Piece> pieces) { this.owner = owner; this.pieces = pieces; }

    /** Des flammes sur les deux bras, tant que {@code keep} est vrai. */
    public static Flames arms(LivingEntity owner, BooleanSupplier keep) { return start(owner, ARMS, keep); }

    /** Tout le corps en feu, tant que {@code keep} est vrai. */
    public static Flames body(LivingEntity owner, BooleanSupplier keep) { return start(owner, BODY, keep); }

    private static Flames start(LivingEntity owner, List<Piece> pieces, BooleanSupplier keep) {
        Flames f = new Flames(owner, pieces);
        // Le pilote d'un mannequin (mode d'essai a la troisieme personne) voit son mannequin de l'exterieur : il voit le feu.
        Player self = owner instanceof Player p && !MannequinPlayer.isProxy(p) ? p : null;
        for (Piece piece : pieces) {
            BlockDisplay d = owner.getWorld().spawn(f.position(piece), BlockDisplay.class, disp -> {
                disp.setBlock(Material.FIRE.createBlockData());
                // Le bloc de feu occupe un cube d'un bloc : ramene a la taille voulue, centre sur son pied.
                disp.setTransformation(new Transformation(new Vector3f(-piece.width / 2, 0, -piece.width / 2), new Quaternionf(),
                        new Vector3f(piece.width, piece.height, piece.width), new Quaternionf()));
                disp.setBrightness(new Display.Brightness(15, 15));
                disp.setTeleportDuration(2);
                disp.setPersistent(false);
                disp.setViewRange(2f);
            });
            if (self != null) self.hideEntity(CrossfightPlugin.get(), d);
            f.displays.add(d);
        }
        f.task = new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                if (!owner.isValid() || owner.isDead() || (owner instanceof Player op && !op.isOnline()) || !keep.getAsBoolean()
                        || f.displays.stream().anyMatch(d -> !d.isValid() || !d.getWorld().equals(owner.getWorld()))) { f.remove(); return; }
                for (int i = 0; i < f.displays.size(); i++) f.displays.get(i).teleport(f.position(f.pieces.get(i)));
                // Ce que voit le joueur lui-meme : quelques flammes qui montent de chaque feu.
                if (self != null && t % 3 == 0) for (Piece piece : f.pieces) {
                    Location at = f.position(piece).add(0, piece.height * 0.35, 0);
                    self.spawnParticle(Particle.SMALL_FLAME, at, 1, piece.width * 0.3, piece.height * 0.25, piece.width * 0.3, 0.005);
                }
                // Un corps entier en feu : des braises s'en echappent, pour tout le monde.
                if (f.pieces == BODY && t % 3 == 0) owner.getWorld().spawnParticle(Particle.FLAME, owner.getLocation().add(0, 1.2, 0), 2, 0.4, 0.8, 0.4, 0.03);
                if (t % 40 == 1) owner.getWorld().playSound(owner.getLocation(), org.bukkit.Sound.BLOCK_FIRE_AMBIENT, 0.7f, 1f);
            }
        };
        f.task.runTaskTimer(CrossfightPlugin.get(), 1L, 1L);
        return f;
    }

    /**
     * Un feu pose quelque part (un pic de glace en flammes...) : {@code width} blocs de large, {@code height} de
     * haut, le pied en {@code foot}, pendant {@code ticks}. Du feu d'affichage : il ne brule rien et ne s'etend pas.
     */
    public static void pillar(Location foot, float width, float height, int ticks) {
        Location at = foot.clone();
        at.setYaw(0);
        at.setPitch(0);
        BlockDisplay d = at.getWorld().spawn(at, BlockDisplay.class, disp -> {
            disp.setBlock(Material.FIRE.createBlockData());
            disp.setTransformation(new Transformation(new Vector3f(-width / 2, 0, -width / 2), new Quaternionf(),
                    new Vector3f(width, 0.05f, width), new Quaternionf()));
            disp.setBrightness(new Display.Brightness(15, 15));
            disp.setPersistent(false);
            disp.setViewRange(2f);
        });
        // Il s'eleve en quelques ticks, puis s'eteint a la fin.
        org.bukkit.Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> {
            if (!d.isValid()) return;
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(6);
            d.setTransformation(new Transformation(new Vector3f(-width / 2, 0, -width / 2), new Quaternionf(),
                    new Vector3f(width, height, width), new Quaternionf()));
        }, 2L);
        org.bukkit.Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> { if (d.isValid()) d.remove(); }, ticks);
    }

    /** Le pied d'un feu dans le monde : le corps tourne avec le joueur (son regard, a plat). */
    private Location position(Piece piece) {
        Location base = owner.getLocation();
        double yaw = Math.toRadians(owner.getBodyYaw());
        // Devant lui : (-sin, cos) ; a sa gauche : (cos, sin).
        double x = piece.x * Math.cos(yaw) - piece.z * Math.sin(yaw), z = piece.x * Math.sin(yaw) + piece.z * Math.cos(yaw);
        Location at = base.clone().add(x, piece.y, z);
        at.setYaw(0);
        at.setPitch(0);
        return at;
    }

    public boolean alive() { return task != null; }

    public void remove() {
        if (task != null) { task.cancel(); task = null; }
        for (BlockDisplay d : displays) if (d.isValid()) d.remove();
        displays.clear();
    }
}
