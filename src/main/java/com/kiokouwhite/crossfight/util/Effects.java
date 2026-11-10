package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
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
    public static void refresh(LivingEntity p, PotionEffectType type, int amplifier, int ticks) {
        PotionEffect current = p.getPotionEffect(type);
        if (current != null) {
            if (current.getAmplifier() > amplifier) return;
            if (current.getAmplifier() == amplifier && current.getDuration() >= ticks) return;
        }
        p.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, false, true));
    }

    /** Effet affiche normalement (particules), utile pour les buffs temporaires. */
    public static void give(LivingEntity p, PotionEffectType type, int amplifier, int ticks) {
        PotionEffect current = p.getPotionEffect(type);
        if (current != null && current.getAmplifier() > amplifier) return;
        p.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, true, true));
    }

    /** Immobilise un joueur (plus de deplacement ni de saut) pendant un nombre de ticks. */
    public static void root(LivingEntity p, int ticks) {
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

    public static void unroot(LivingEntity p) {
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        AttributeInstance jump = p.getAttribute(Attribute.JUMP_STRENGTH);
        if (speed != null) speed.removeModifier(ROOT_SPEED);
        if (jump != null) jump.removeModifier(ROOT_JUMP);
    }

    public static boolean isRooted(LivingEntity p) {
        AttributeInstance speed = p.getAttribute(Attribute.MOVEMENT_SPEED);
        return speed != null && speed.getModifier(ROOT_SPEED) != null;
    }

    /** Vitesse verticale approximative pour atteindre une hauteur donnee en blocs. */
    public static double launchVelocityFor(double heightBlocks) {
        // Le frottement de l'air freine davantage les grandes projections : on compense.
        return Math.sqrt(0.16 * heightBlocks) * (heightBlocks >= 20 ? 1.18 : 1.05);
    }

    /** Projette un joueur en l'air d'environ {@code height} blocs. */
    public static void launchUp(LivingEntity p, double height) {
        Vector v = p.getVelocity();
        p.setVelocity(new Vector(v.getX() * 0.2, launchVelocityFor(height), v.getZ() * 0.2));
    }

    /** Repousse un joueur loin d'un point, d'environ {@code distance} blocs. */
    public static void knockbackFrom(LivingEntity p, Location from, double distance) {
        Vector dir = p.getLocation().toVector().subtract(from.toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 0.01) dir = p.getLocation().getDirection().setY(0).multiply(-1);
        dir.normalize();
        double horizontal = Math.min(3.5, 0.3 + distance * 0.16);
        p.setVelocity(dir.multiply(horizontal).setY(0.45));
    }

    // ------------------------------------------------------------ recherche

    /** Un ennemi : un joueur vivant d'une autre equipe, ou un mannequin d'entrainement. */
    public static boolean isEnemy(Player self, LivingEntity entity) {
        if (entity == self || entity == null || entity.isDead()) return false;
        if (Mannequins.isMannequin(entity)) return !Mannequins.isAlly(entity);
        if (!(entity instanceof Player other)) return false;
        var plugin = CrossfightPlugin.get();
        var cf = plugin.players().get(other);
        if (cf.team() == null || !cf.isAlive() || cf.isWaitingRespawn()) return false;
        if (other.getGameMode() == org.bukkit.GameMode.SPECTATOR) return false;
        return !plugin.teams().sameTeam(self, other);
    }

    /** Joueurs ennemis uniquement (pour ce qui n'a de sens que sur un joueur). */
    public static List<Player> enemyPlayersWithin(Player self, double radius) {
        List<Player> out = new ArrayList<>();
        for (LivingEntity e : enemiesWithin(self, radius)) if (e instanceof Player p) out.add(p);
        return out;
    }

    /** Un allie : un joueur vivant de la meme equipe, ou un mannequin allie. */
    public static boolean isAlly(Player self, LivingEntity entity) {
        if (entity == self || entity == null || entity.isDead()) return false;
        if (Mannequins.isMannequin(entity)) return Mannequins.isAlly(entity);
        if (!(entity instanceof Player other)) return false;
        var plugin = CrossfightPlugin.get();
        var cf = plugin.players().get(other);
        if (cf.team() == null || !cf.isAlive() || cf.isWaitingRespawn()) return false;
        return plugin.teams().sameTeam(self, other);
    }

    /** Joueurs allies uniquement (Soutien, messages...). */
    public static List<Player> allyPlayersWithin(Player self, double radius) {
        List<Player> out = new ArrayList<>();
        for (LivingEntity e : alliesWithin(self, radius)) if (e instanceof Player p) out.add(p);
        return out;
    }

    public static List<LivingEntity> enemiesWithin(Player self, double radius) {
        return enemiesWithin(self, self.getLocation(), radius);
    }

    public static List<LivingEntity> enemiesWithin(Player self, Location center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (org.bukkit.entity.Entity e : center.getWorld().getNearbyEntities(center, radius + 1, radius + 2, radius + 1)) {
            if (e instanceof LivingEntity le && isEnemy(self, le) && distanceToBox(center, le) <= radius) out.add(le);
        }
        return out;
    }

    /** Distance entre un point et la boite de collision d'une entite (0 si le point est dedans). */
    public static double distanceToBox(Location point, org.bukkit.entity.Entity e) {
        var box = e.getBoundingBox();
        double dx = Math.max(Math.max(box.getMinX() - point.getX(), 0), point.getX() - box.getMaxX());
        double dy = Math.max(Math.max(box.getMinY() - point.getY(), 0), point.getY() - box.getMaxY());
        double dz = Math.max(Math.max(box.getMinZ() - point.getZ(), 0), point.getZ() - box.getMaxZ());
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public static List<LivingEntity> alliesWithin(Player self, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        Location center = self.getLocation();
        for (org.bukkit.entity.Entity e : center.getWorld().getNearbyEntities(center, radius + 1, radius + 2, radius + 1)) {
            if (e instanceof LivingEntity le && isAlly(self, le) && distanceToBox(center, le) <= radius) out.add(le);
        }
        return out;
    }

    public static LivingEntity nearestEnemy(Player self, double radius) {
        return enemiesWithin(self, radius).stream()
                .min(Comparator.comparingDouble(o -> o.getLocation().distanceSquared(self.getLocation())))
                .orElse(null);
    }

    public static LivingEntity nearestAlly(Player self, double radius) {
        return alliesWithin(self, radius).stream()
                .min(Comparator.comparingDouble(o -> o.getLocation().distanceSquared(self.getLocation())))
                .orElse(null);
    }

    /** Degats "bruts" infliges par un joueur a un autre, en points de vie (2 = 1 coeur). */
    public static void damage(LivingEntity victim, double amount, Player source) {
        if (victim.isDead()) return;
        victim.damage(amount, source);
    }

    public static void heal(LivingEntity p, double amount) {
        var attr = p.getAttribute(Attribute.MAX_HEALTH);
        double max = attr == null ? 20 : attr.getValue();
        p.setHealth(Math.min(max, p.getHealth() + amount));
    }
}
