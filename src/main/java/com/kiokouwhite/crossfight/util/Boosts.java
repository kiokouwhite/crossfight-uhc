package com.kiokouwhite.crossfight.util;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Bonus fractionnaires de Force, Vitesse et Resistance (par exemple "Force 0.5"),
 * que les potions ne permettent pas. Chaque source a sa cle pour pouvoir etre
 * ajoutee ou retiree independamment.
 *
 *  - Force 1 = +3 degats de melee
 *  - Vitesse 1 = +20 % de vitesse
 *  - Resistance 1 = -20 % de degats recus (plafond 80 %)
 *  - Arme = degats plats ajoutes a toutes les attaques
 */
public final class Boosts {

    public static final class Boost {
        public double force, speed, resistance, weapon;
        public Boost(double force, double speed, double resistance, double weapon) {
            this.force = force; this.speed = speed; this.resistance = resistance; this.weapon = weapon;
        }
    }

    private static final NamespacedKey FORCE_KEY = new NamespacedKey("crossfight", "boost_force");
    private static final NamespacedKey SPEED_KEY = new NamespacedKey("crossfight", "boost_speed");
    private static final Map<UUID, Map<String, Boost>> BOOSTS = new HashMap<>();

    private Boosts() {}

    public static void set(LivingEntity p, String source, Boost boost) {
        BOOSTS.computeIfAbsent(p.getUniqueId(), u -> new HashMap<>()).put(source, boost);
        apply(p);
    }

    public static Boost get(LivingEntity p, String source) {
        Map<String, Boost> m = BOOSTS.get(p.getUniqueId());
        return m == null ? null : m.get(source);
    }

    public static void remove(LivingEntity p, String source) {
        Map<String, Boost> m = BOOSTS.get(p.getUniqueId());
        if (m != null && m.remove(source) != null) apply(p);
    }

    public static void clear(LivingEntity p) {
        BOOSTS.remove(p.getUniqueId());
        apply(p);
    }

    public static void clearAll() {
        BOOSTS.clear();
    }

    public static double totalForce(LivingEntity p) { return sum(p, b -> b.force); }
    public static double totalSpeed(LivingEntity p) { return sum(p, b -> b.speed); }
    public static double totalResistance(LivingEntity p) { return sum(p, b -> b.resistance); }
    public static double totalWeapon(LivingEntity p) { return sum(p, b -> b.weapon); }

    private static double sum(LivingEntity p, java.util.function.ToDoubleFunction<Boost> f) {
        Map<String, Boost> m = BOOSTS.get(p.getUniqueId());
        if (m == null) return 0;
        double s = 0;
        for (Boost b : m.values()) s += f.applyAsDouble(b);
        return s;
    }

    /** Multiplicateur a appliquer aux degats recus. */
    public static double damageTakenMultiplier(LivingEntity p) {
        return 1.0 - Math.min(0.8, totalResistance(p) * 0.2);
    }

    /** Reapplique les modificateurs d'attribut du joueur. A appeler apres chaque changement et a la reapparition. */
    public static void apply(LivingEntity p) {
        AttributeInstance dmg = p.getAttribute(Attribute.ATTACK_DAMAGE);
        AttributeInstance spd = p.getAttribute(Attribute.MOVEMENT_SPEED);
        double addDamage = totalForce(p) * 3.0 + totalWeapon(p);
        double speedMul = totalSpeed(p) * 0.2;
        if (dmg != null) {
            dmg.removeModifier(FORCE_KEY);
            if (Math.abs(addDamage) > 1e-6) {
                dmg.addTransientModifier(new AttributeModifier(FORCE_KEY, addDamage, AttributeModifier.Operation.ADD_NUMBER));
            }
        }
        if (spd != null) {
            spd.removeModifier(SPEED_KEY);
            if (Math.abs(speedMul) > 1e-6) {
                spd.addTransientModifier(new AttributeModifier(SPEED_KEY, speedMul, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
            }
        }
    }

    /** Resume lisible, pour le scoreboard ou le debug. */
    public static String summary(LivingEntity p) {
        StringBuilder sb = new StringBuilder();
        double f = totalForce(p), s = totalSpeed(p), r = totalResistance(p), w = totalWeapon(p);
        if (f > 0) sb.append("<red>Force ").append(fmt(f)).append(" ");
        if (s > 0) sb.append("<aqua>Vitesse ").append(fmt(s)).append(" ");
        if (r > 0) sb.append("<gray>Resistance ").append(fmt(r)).append(" ");
        if (w > 0) sb.append("<gold>Arme +").append(fmt(w)).append(" ");
        return sb.toString().trim();
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.format(java.util.Locale.US, "%.1f", d);
    }
}
