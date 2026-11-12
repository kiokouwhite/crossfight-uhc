package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
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

    /** Hauteur du bloc de surface dans une colonne, proche de la hauteur de reference (MIN_VALUE si aucune). */
    public static int groundAround(World w, int x, int z, int refY) {
        for (int dy = 0; dy <= 6; dy++) {
            for (int y : new int[]{refY - 1 - dy, refY - 1 + dy}) {
                Block b = w.getBlockAt(x, y, z);
                Block above = w.getBlockAt(x, y + 1, z);
                if ((b.getType().isSolid() || b.getType() == Material.WATER || b.getType() == Material.LAVA) && !above.getType().isSolid()) return y;
            }
        }
        return Integer.MIN_VALUE;
    }


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
    private static final java.util.Map<java.util.UUID, Long> NO_FALL = new java.util.HashMap<>();

    /** Protege un joueur des degats de chute pendant un moment (ruees, bonds, projections volontaires). */
    public static void noFallUntil(Player p, long millis) {
        NO_FALL.put(p.getUniqueId(), System.currentTimeMillis() + millis);
    }

    public static boolean noFall(Player p) {
        Long t = NO_FALL.get(p.getUniqueId());
        return t != null && System.currentTimeMillis() < t;
    }

    public static void say(LivingEntity e, String actionBar) {
        if (e instanceof Player p) Msg.actionBar(p, actionBar);
    }

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
        if (Clones.isClone(entity)) return !Clones.sameTeam(entity, self);
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

    /**
     * Le joueur dont une entite partage le camp : elle-meme pour un joueur, son testeur pour un
     * mannequin duo, null sinon. Sert aux ultimes en duo, dont la partenaire peut etre un mannequin.
     */
    public static Player sideOf(LivingEntity e) {
        if (e instanceof Player p) return p;
        return Mannequins.partnerOwner(e);
    }

    /** Les ennemis du camp d'une entite (joueur ou mannequin duo) autour d'un point. */
    public static List<LivingEntity> enemiesAround(LivingEntity self, Location center, double radius) {
        Player side = sideOf(self);
        if (side == null) return new ArrayList<>();
        return within(center, radius, le -> le != self && isEnemy(side, le));
    }

    public static List<LivingEntity> enemiesAround(LivingEntity self, double radius) {
        return enemiesAround(self, self.getLocation(), radius);
    }

    /** Vrai si l'entite est une ennemie du camp de {@code self} (joueur ou mannequin duo). */
    public static boolean isEnemyOf(LivingEntity self, LivingEntity entity) {
        Player side = sideOf(self);
        return side != null && entity != self && isEnemy(side, entity);
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
        return within(center, radius, le -> isEnemy(self, le));
    }

    /** Entites vivantes dans un rayon (distance a la boite de collision) qui passent le filtre. */
    public static List<LivingEntity> within(Location center, double radius, java.util.function.Predicate<LivingEntity> filter) {
        List<LivingEntity> out = new ArrayList<>();
        for (org.bukkit.entity.Entity e : center.getWorld().getNearbyEntities(center, radius + 1, radius + 2, radius + 1)) {
            if (e instanceof LivingEntity le && filter.test(le) && distanceToBox(center, le) <= radius) out.add(le);
        }
        return out;
    }

    /** Filtre "ennemi" pour un lanceur quelconque : les ennemis du joueur, ou la cible designee pour un mannequin. */
    public static java.util.function.Predicate<LivingEntity> enemiesOf(LivingEntity caster, LivingEntity designated) {
        if (caster instanceof Player p) return le -> isEnemy(p, le);
        return le -> le == designated;
    }

    /** Direction horizontale d'un lanceur vers une cible (ou son regard s'il n'y a pas de cible). */
    public static Vector towards(LivingEntity caster, LivingEntity target) {
        Vector dir = target == null ? caster.getLocation().getDirection()
                : target.getLocation().toVector().subtract(caster.getLocation().toVector());
        dir.setY(0);
        if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
        return dir.normalize();
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

    /** Ruee quasi instantanee vers un point : teleportations par etapes (une par tick), arretees par les murs. */
    public static void rush(Player p, Location to, int ticks, org.bukkit.Particle particle) {
        Location from = p.getLocation();
        Vector path = to.toVector().subtract(from.toVector());
        if (path.length() < 0.5) return;
        int steps = Math.max(1, ticks);
        Vector step = path.clone().multiply(1.0 / steps);
        noFallUntil(p, 4000);
        new org.bukkit.scheduler.BukkitRunnable() {
            int i = 0;
            Location current = from.clone();
            @Override public void run() {
                i++;
                if (!p.isOnline() || p.isDead() || i > steps) { cancel(); return; }
                Location next = current.clone().add(step);
                next.setDirection(p.getLocation().getDirection());
                if (!next.getBlock().isPassable() || !next.clone().add(0, 1, 0).getBlock().isPassable()) { cancel(); return; }
                Vector unit = step.clone().normalize();
                for (double d = 0; d < step.length(); d += 0.6) {
                    p.getWorld().spawnParticle(particle, current.clone().add(unit.clone().multiply(d)).add(0, 1, 0), 1, 0.1, 0.2, 0.1, 0.01);
                }
                p.teleport(next);
                current = next;
            }
        }.runTaskTimer(CrossfightPlugin.get(), 0L, 1L);
    }

    private static final java.util.Map<java.util.UUID, Long> INVINCIBLE = new java.util.HashMap<>();

    /** Rend une entite invulnerable a tous les degats pendant un temps (verifie dans DamageListener). */
    public static void invincible(LivingEntity e, long millis) {
        INVINCIBLE.merge(e.getUniqueId(), System.currentTimeMillis() + millis, Math::max);
    }
    public static boolean isInvincible(org.bukkit.entity.Entity e) {
        Long until = INVINCIBLE.get(e.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }

    /** Degats "bruts" infliges par un joueur a un autre, en points de vie (2 = 1 coeur). */
    public static void damage(LivingEntity victim, double amount, org.bukkit.entity.Entity source) {
        if (victim.isDead()) return;
        victim.damage(amount, source);
    }

    /**
     * Ligne de vue "de jeu" : seuls les blocs opaques (pierre, terre...) bloquent ;
     * le verre, l'eau, les feuilles ou les blocs d'une capacite en cours ne comptent pas.
     */
    public static boolean canSee(LivingEntity from, LivingEntity to) {
        Location a = from.getEyeLocation();
        Vector d = to.getEyeLocation().toVector().subtract(a.toVector());
        double dist = d.length();
        if (dist < 0.01) return true;
        d.normalize();
        for (double t = 0.5; t < dist; t += 0.5) {
            if (a.clone().add(d.clone().multiply(t)).getBlock().getType().isOccluding()) return false;
        }
        return true;
    }

    /** Tire une fleche quasi instantanee la ou l'on vise, sans tirer a l'arc. */
    public static org.bukkit.entity.Arrow shootInstantArrow(LivingEntity shooter, Vector dir, double speed, double damage) {
        Location from = shooter.getEyeLocation().add(dir.clone().multiply(0.5));
        org.bukkit.entity.Arrow arrow = shooter.getWorld().spawnArrow(from, dir, (float) speed, 0f);
        arrow.setShooter(shooter);
        arrow.setDamage(damage);
        arrow.setCritical(true);
        arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
        shooter.getWorld().playSound(from, org.bukkit.Sound.ENTITY_ARROW_SHOOT, 1f, 1.2f);
        return arrow;
    }

    /** Eclair visuel sur une entite, avec des degats attribues au lanceur. */
    public static void lightning(LivingEntity target, double damage, org.bukkit.entity.Entity source) {
        target.getWorld().strikeLightningEffect(target.getLocation());
        damage(target, damage, source);
    }

    /** Vie maximale remise a 20, tous les modificateurs du plugin (Burst de Yuki, Dieu des enfers...) retires. */
    public static void resetMaxHealth(Player p) {
        AttributeInstance mh = p.getAttribute(Attribute.MAX_HEALTH);
        if (mh == null) return;
        for (AttributeModifier m : new java.util.ArrayList<>(mh.getModifiers())) if ("crossfight".equals(m.getKey().getNamespace())) mh.removeModifier(m);
        mh.setBaseValue(20.0);
    }

    public static void heal(LivingEntity p, double amount) {
        var attr = p.getAttribute(Attribute.MAX_HEALTH);
        double max = attr == null ? 20 : attr.getValue();
        p.setHealth(Math.min(max, p.getHealth() + amount));
    }
}
