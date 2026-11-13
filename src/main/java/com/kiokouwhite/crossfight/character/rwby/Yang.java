package com.kiokouwhite.crossfight.character.rwby;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.EnemyCastable;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.ToggleAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Puppet;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Yang Xiao Long - Path of Hunt.
 */
public final class Yang extends Character {

    public Yang() {
        super("yang", "Yang Xiao Long", Voie.CHASSE, Material.GOLDEN_AXE);
        addAbility(new EmberCelica().bindToWeapon());
        addAbility(new Brulure());
        addAbility(new IncendieInterne());
        addAbility(new ColereFlamboyante());
        addAbility(RWBY.DUO_ITEM);
        setUltimate(new CourrouxImperissable());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.GOLDEN_AXE, 9, "<gold><bold>Ember Celica", List.of());
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Ember Celica (9) : 2 decharges de feu sur l'ennemi proche",
                "<white>Brulure : Force selon les degats subis",
                "<white>Incendie Interne : Force I, perd 0.5 coeur/s",
                "<white>Colere Flamboyante : Fire Aspect sous 5 coeurs",
                "<light_purple>Ultime : 25 s sans pouvoir mourir, puis soin complet");
    }

    // ------------------------------------------------------------------ arme

    /** Explosion vers l'adversaire le plus proche (4 blocs), qui est enflamme. Hypothese : 2 coeurs de degats. */
    static final class EmberCelica extends ActiveAbility implements EnemyCastable {
        EmberCelica() {
            super("yang_ember", "Ember Celica", "Clic droit : Yang frappe deux fois dans le vide, chaque coup de gantelet tire une decharge de feu sur l'adversaire le plus proche (4 blocs) : 1 coeur, puis 1 coeur et une explosion qui le met en feu.", Material.GOLDEN_AXE, 5);
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            LivingEntity target = Effects.nearestEnemy(p, 4);
            if (target == null) { Msg.actionBar(p, "<red>Ember Celica : aucun adversaire a moins de 4 blocs."); return false; }
            perform(p, target);
            return true;
        }

        @Override
        public boolean castOn(LivingEntity caster, Player target, AbilityState st) {
            if (target.getLocation().distance(caster.getLocation()) > 6) { Msg.actionBar(target, "<red>Approche-toi du mannequin (4 blocs)."); return false; }
            perform(caster, target);
            return true;
        }

        /** Ticks des deux directs (le poing arrive au bout) et de la fin de l'animation. */
        static final int PUNCH_1 = 4, PUNCH_2 = 12, END = 20;

        /**
         * Comme dans la serie : Yang frappe deux fois dans le vide (direct du droit, puis du gauche), chaque
         * coup de son gantelet tire une decharge de feu sur la cible. La premiere fait 1 coeur, la seconde
         * explose (1 coeur, feu, recul) : 2 coeurs en tout, comme avant.
         */
        static void perform(LivingEntity caster, LivingEntity target) {
            final Puppet puppet = caster instanceof Player pl ? Puppet.show(pl, "yang", null, "debout") : null;
            if (puppet != null) {
                puppet.face(RWBY.yawTowards(caster, target));
                puppet.pose("yang_garde", 2);
                puppet.endAfter(END, 4);
            }
            caster.getWorld().playSound(caster.getLocation(), Sound.ITEM_ARMOR_EQUIP_GOLD, 1f, 1.4f);
            new org.bukkit.scheduler.BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    boolean animated = puppet != null && caster instanceof Player pl && Puppet.of(pl) == puppet;
                    if (!caster.isValid() || caster.isDead()) { cancel(); return; }
                    if (animated && (t == PUNCH_1 - 2 || t == PUNCH_2 - 2)) {
                        puppet.face(RWBY.yawTowards(caster, target));
                        puppet.pose(t == PUNCH_1 - 2 ? "yang_direct_droit" : "yang_direct_gauche", 2);
                    }
                    if (animated && (t == PUNCH_1 + 3 || t == PUNCH_2 + 3)) puppet.pose("yang_garde", 3);
                    if (t == PUNCH_1 || t == PUNCH_2) {
                        boolean second = t == PUNCH_2;
                        Location fist = animated ? puppet.armPoint(!second, 1.0) : caster.getEyeLocation().add(0, -0.3, 0);
                        blast(caster, fist, target, second);
                    }
                    if (t >= PUNCH_2) cancel();
                }
            }.runTaskTimer(plugin(), 1L, 1L);
        }

        /** Une decharge du gantelet : flamme au poing, douille ejectee, trainee de feu jusqu'a la cible. */
        static void blast(LivingEntity caster, Location fist, LivingEntity target, boolean explode) {
            var w = caster.getWorld();
            boolean reach = target.isValid() && !target.isDead() && target.getWorld().equals(w)
                    && target.getLocation().distanceSquared(caster.getLocation()) <= 8 * 8;
            Location aim = reach ? target.getLocation().add(0, target.getHeight() * 0.55, 0)
                    : fist.clone().add(caster.getLocation().getDirection().setY(0).normalize().multiply(4));
            Vector line = aim.toVector().subtract(fist.toVector());
            double len = Math.max(0.1, line.length());
            line.normalize();
            w.spawnParticle(Particle.FLAME, fist, 14, 0.12, 0.12, 0.12, 0.06);
            w.spawnParticle(Particle.LARGE_SMOKE, fist, 4, 0.1, 0.1, 0.1, 0.02);
            w.spawnParticle(Particle.EXPLOSION, fist, 1);
            for (double d = 0.3; d < len; d += 0.35) {
                Location at = fist.clone().add(line.clone().multiply(d));
                w.spawnParticle(Particle.FLAME, at, 2, 0.08, 0.08, 0.08, 0.01);
                if (((int) (d / 0.35)) % 2 == 0) w.spawnParticle(Particle.SMALL_FLAME, at, 1, 0.05, 0.05, 0.05, 0);
            }
            w.playSound(fist, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.8f);
            w.playSound(fist, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0.7f);
            RWBY.ejectShell(caster, fist);
            if (!reach) return;
            if (explode) {
                w.spawnParticle(Particle.EXPLOSION, aim, 1);
                w.spawnParticle(Particle.FLAME, aim, 40, 0.5, 0.5, 0.5, 0.08);
                w.playSound(aim, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.3f);
            } else {
                w.spawnParticle(Particle.FLAME, aim, 12, 0.25, 0.3, 0.25, 0.04);
            }
            // Le second coup suit le premier de peu : sans cela l'invulnerabilite de la cible l'annulerait.
            target.setNoDamageTicks(0);
            Effects.damage(target, 2.0, caster);
            if (explode) {
                target.setFireTicks(Math.max(target.getFireTicks(), 100));
                Effects.knockbackFrom(target, caster.getLocation(), 3);
            }
        }
    }

    // --------------------------------------------------------------- pouvoirs

    /** Yang convertit les degats subis depuis la derniere utilisation : 1 coeur subi = +0.5 de force pendant 10 secondes. */
    static final class Brulure extends ActiveAbility {
        Brulure() {
            super("yang_brulure", "Brulure", "Yang gagne de la force selon les degats subis depuis la derniere Brulure : 1 coeur subi = +0.5 point d'attaque, pendant 10 secondes.", Material.BLAZE_POWDER, 20);
        }

        @Override
        public void onDamaged(Player victim, EntityDamageEvent e, AbilityState st) {
            if (e.isCancelled()) return;
            st.set("taken", (double) st.get("taken", 0.0) + e.getFinalDamage());
        }

        @Override
        public String barText(Player p, CfPlayer cf, AbilityState st) {
            double hearts = (double) st.get("taken", 0.0) / 2.0;
            String stock = String.format(java.util.Locale.US, "%.1f", hearts);
            return st.onCooldown() ? "<gray>Brulure <red>" + st.cooldownLeftSeconds() + "s" : "<green>Brulure ✔ <gray>(" + stock + " ♥ subis)";
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            double taken = st.get("taken", 0.0);
            if (taken < 1.0) { Msg.actionBar(p, "<red>Brulure : aucun degat subi a convertir."); return false; }
            double bonus = taken / 2.0 * 0.5;
            st.set("taken", 0.0);
            Boosts.set(p, "yang_brulure", new Boosts.Boost(0, 0, 0, bonus));
            Msg.actionBar(p, "<gold>Brulure : +" + String.format(java.util.Locale.US, "%.1f", bonus) + " d'attaque pendant 10 s");
            p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.05);
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);
            Bukkit.getScheduler().runTaskLater(plugin(), () -> Boosts.remove(p, "yang_brulure"), 200L);
            return true;
        }
    }

    /** Force I tant que c'est actif, mais Yang perd un demi-coeur par seconde. */
    static final class IncendieInterne extends ToggleAbility {
        IncendieInterne() {
            super("yang_incendie", "Incendie Interne", "Tant que c'est actif, Yang a Force I mais perd un demi-coeur par seconde.", Material.FIRE_CHARGE, 0.0, 0.0);
        }

        @Override public boolean onEnable(Player p, CfPlayer cf, AbilityState st) { return true; }

        @Override
        public void tickActive(Player p, CfPlayer cf, AbilityState st) {
            Effects.refresh(p, PotionEffectType.STRENGTH, 0, 45);
            if (p.getHealth() > 2.0) {
                p.setHealth(p.getHealth() - 1.0);
                p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0.02);
            } else {
                st.setEnabled(false);
                onDisable(p, cf, st);
                Msg.actionBar(p, "<red>Incendie Interne s'eteint : plus assez de vie.");
            }
        }

        @Override
        public void onDisable(Player p, CfPlayer cf, AbilityState st) {
            p.removePotionEffect(PotionEffectType.STRENGTH);
        }

        @Override
        public String barText(Player p, CfPlayer cf, AbilityState st) {
            return st.isEnabled() ? "<gold>Incendie Interne <red>actif" : "<gray>Incendie Interne";
        }
    }

    static final class ColereFlamboyante extends PassiveAbility {
        ColereFlamboyante() {
            super("yang_colere", "Colere Flamboyante", "Sous 5 coeurs, les coups de Yang enflamment ses adversaires.", Material.MAGMA_CREAM);
        }

        @Override
        public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) {
            if (attacker.getHealth() < 10.0 && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
                victim.setFireTicks(Math.max(victim.getFireTicks(), 80));
                // Sa colere s'allume : elle le dit, au plus une fois toutes les 30 secondes.
                long now = System.currentTimeMillis();
                if (now - (long) st.get("said", 0L) > 30_000L) {
                    st.set("said", now);
                    com.kiokouwhite.crossfight.util.Sounds.voice(attacker, "yang_colere");
                }
            }
        }
    }

    // ----------------------------------------------------------------- ultime

    /** Pendant 25 secondes Yang ne peut pas mourir ; a la fin elle regenere toute sa vie. */
    static final class CourrouxImperissable extends UltimateAbility {
        CourrouxImperissable() {
            super("yang_ult", "Courroux Imperissable", "Pendant 25 secondes Yang ne peut pas mourir. A la fin, elle regenere toute sa vie.", Material.TOTEM_OF_UNDYING);
        }

        @Override
        public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            st.set("until", System.currentTimeMillis() + 25_000L);
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 0.7f);
            p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 60, 0.6, 0.8, 0.6, 0.1);
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                if (!p.isOnline() || p.isDead()) return;
                var mh = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                p.setHealth(mh == null ? 20 : mh.getValue());
                p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.5, 0), 20, 0.5, 0.5, 0.5);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
                Msg.send(p, "<gold>Courroux Imperissable : vie entierement regeneree.");
            }, 20L * 25);
            return true;
        }

        @Override
        public void onDamaged(Player victim, EntityDamageEvent e, AbilityState st) {
            if (System.currentTimeMillis() > (long) st.get("until", 0L)) return;
            if (e.getFinalDamage() >= victim.getHealth()) {
                // Un coup mortel laisse Yang a un demi-coeur.
                double allowed = Math.max(0, victim.getHealth() - 1.0);
                e.setDamage(allowed <= 0 ? 0 : allowed * (e.getDamage() / Math.max(0.01, e.getFinalDamage())));
                victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0.05);
            }
        }

        @Override
        public String barText(Player p, CfPlayer cf, AbilityState st) {
            long left = (long) st.get("until", 0L) - System.currentTimeMillis();
            return left > 0 ? "<gold>Courroux <red>" + (int) Math.ceil(left / 1000.0) + "s" : null;
        }
    }
}
