package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Petits outils d'effets : potions sans retrogradation, immobilisation,
 * projection, recherche d'allies et d'ennemis.
 */
public final class Effects {

    private static final NamespacedKey ROOT_SPEED = new NamespacedKey("crossfight", "root_speed");
    private static final NamespacedKey ROOT_JUMP = new NamespacedKey("crossfight", "root_jump");

    private Effects() {}

    /**
     * Applique un effet de potion sans jamais retrograder un effet plus fort
     * ou plus long deja present.
     */
    public static void refresh(Player p, PotionEffectType type, int amplifier, int ticks) {
        PotionEffect current = p.getPotionEffect(type);
        if (current != null) {
            if (current.getAmplifier() > amplifier) return;
            if (current.getAmplifier() == amplifier && current.getDuration() >= ticks) return;
        }
        p.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, false, true));
    }

    /** Effet affiche normalement (particules), utile pour les buffs temporaires. */
    public static void give(Player p, PotionEffectType type, int amplifier, int ticks) {
        PotionEffect current = p.getPotionEffect(type);
        if (current != null && current.getAmplifier() > amplifier) return;
        p.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, true, true));
    }

    /** Immobilise un joueur (plus de deplacement ni de saut) pendant un nombre de ticks. */
    public static void root(Player p, int ticks) {
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        AttributeInstance jump = p.getAttribute(Attribute.JUMP_STRENGTH);
        if (speed != null) {
            speed.removeModifier(ROOT_SPEED);
            speed.addTransientModifier(new AttributeModifier(ROOT_SPEED, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
        if (jump != null) {
            jump.removeModifier(ROOT_JUMP);
            jump.addTransientModifier(new AttributeModifier(ROOT_JUMP, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
        p.setVelocity(new Vector(0, p.getVelocity().getY(), 0));
        Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> unroot(p), ticks);
    }

    public static void unroot(Player p) {
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        AttributeInstance jump = p.getAttribute(Attribute.JUMP_STRENGTH);
        if (speed != null) speed.removeModifier(ROOT_SPEED);
        if (jump != null) jump.removeModifier(ROOT_JUMP);
    }

    public static boolean isRooted(Player p) {
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        return speed != null && speed.getModifier(ROOT_SPEED) != null;
    }

    /** Vitesse verticale approximative pour atteindre une hauteur donnee en blocs. */
    public static double launchVelocityFor(double heightBlocks) {
        return Math.sqrt(0.16 * heightBlocks) * 1.05;
    }

    /** Projette un joueur en l'air d'environ {@code height} blocs. */
    public static void launchUp(Player p, double height) {
        Vector v = p.getVelocity();
        p.setVelocity(new Vector(v.getX() * 0.2, launchVelocityFor(height), v.getZ() * 0.2));
    }

    /** Repousse un joueur loin d'un point, d'environ {@code distance} blocs. */
    public static void knockbackFrom(Player p, Location from, double distance) {
        Vector dir = p.getLocation().toVector().subtract(from.toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 0.01) dir = p.getLocation().getDirection().setY(0).multiply(-1);
        dir.normalize();
        double horizontal = Math.min(3.5, 0.3 + distance * 0.16);
        p.setVelocity(dir.multiply(horizontal).setY(0.45));
    }

    // ------------------------------------------------------------ recherche

    public static boolean isEnemy(Player self, Player other) {
        if (self == other) return false;
        var plugin = CrossfightPlugin.get();
        var cf = plugin.players().get(other);
        if (cf.team() == null || !cf.isAlive() || cf.isWaitingRespawn()) return false;
        if (other.getGameMode() == org.bukkit.GameMode.SPECTATOR) return false;
        return !plugin.teams().sameTeam(self, other);
    }

    public static boolean isAlly(Player self, Player other) {
        if (self == other) return false;
        var plugin = CrossfightPlugin.get();
        var cf = plugin.players().get(other);
        if (cf.team() == null || !cf.isAlive() || cf.isWaitingRespawn()) return false;
        return plugin.teams().sameTeam(self, other);
    }

    public static List<Player> enemiesWithin(Player self, double radius) {
        List<Player> out = new ArrayList<>();
        double r2 = radius * radius;
        for (Player o : self.getWorld().getPlayers()) {
            if (isEnemy(self, o) && o.getLocation().distanceSquared(self.getLocation()) <= r2) out.add(o);
        }
        return out;
    }

    public static List<Player> enemiesWithin(Player self, Location center, double radius) {
        List<Player> out = new ArrayList<>();
        double r2 = radius * radius;
        for (Player o : center.getWorld().getPlayers()) {
            if (isEnemy(self, o) && o.getLocation().distanceSquared(center) <= r2) out.add(o);
        }
        return out;
    }

    public static List<Player> alliesWithin(Player self, double radius) {
        List<Player> out = new ArrayList<>();
        double r2 = radius * radius;
        for (Player o : self.getWorld().getPlayers()) {
            if (isAlly(self, o) && o.getLocation().distanceSquared(self.getLocation()) <= r2) out.add(o);
        }
        return out;
    }

    public static Player nearestEnemy(Player self, double radius) {
        return enemiesWithin(self, radius).stream()
                .min(Comparator.comparingDouble(o -> o.getLocation().distanceSquared(self.getLocation())))
                .orElse(null);
    }

    public static Player nearestAlly(Player self, double radius) {
        return alliesWithin(self, radius).stream()
                .min(Comparator.comparingDouble(o -> o.getLocation().distanceSquared(self.getLocation())))
                .orElse(null);
    }

    /** Degats "bruts" infliges par un joueur a un autre, en points de vie (2 = 1 coeur). */
    public static void damage(Player victim, double amount, Player source) {
        if (victim.isDead()) return;
        victim.damage(amount, source);
    }

    public static void heal(Player p, double amount) {
        var attr = p.getAttribute(Attribute.MAX_HEALTH);
        double max = attr == null ? 20 : attr.getValue();
        p.setHealth(Math.min(max, p.getHealth() + amount));
    }
}
