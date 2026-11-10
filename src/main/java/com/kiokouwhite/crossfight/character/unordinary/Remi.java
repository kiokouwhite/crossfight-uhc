package com.kiokouwhite.crossfight.character.unordinary;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Remi - Path of Nihility.
 */
public final class Remi extends Character {

    static final Particle.DustOptions SPARK = new Particle.DustOptions(Color.fromRGB(255, 240, 120), 1.0f);

    public Remi() {
        super("remi", "Remi", Voie.NIHILITE, Material.LIGHTNING_ROD);
        addAbility(Unordinary.AURA);
        addAbility(new DashElectrique());
        addAbility(new Electricite());
        addAbility(new Electrocution());
        setUltimate(new DomeElectrique());
    }

    @Override public ItemStack createWeapon() { return null; }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Epee d'Aura a crafter (actif selon le craft)",
                "<white>Dash electrique : 10 blocs, zone electrique derriere elle",
                "<white>Electricite : onde de 10 blocs, immobilise 2 s",
                "<white>Electrocution : ceux qui la frappent perdent de la vie",
                "<light_purple>Ultime : dome electrique 15 s");
    }

    static void electricZone(Player p, World w, List<Location> points, int seconds, double radius) {
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                if (!p.isOnline() || t > seconds) { cancel(); return; }
                Set<UUID> hit = new HashSet<>();
                for (Location l : points) {
                    w.spawnParticle(Particle.ELECTRIC_SPARK, l.clone().add(0, 0.6, 0), 6, 0.5, 0.4, 0.5, 0.08);
                    w.spawnParticle(Particle.DUST, l.clone().add(0, 0.3, 0), 2, 0.4, 0.2, 0.4, 0, SPARK);
                    for (LivingEntity enemy : Effects.enemiesWithin(p, l, radius)) {
                        if (!hit.add(enemy.getUniqueId())) continue;
                        enemy.setNoDamageTicks(0);
                        Effects.damage(enemy, 2.0, p);
                        Effects.give(enemy, PotionEffectType.SLOWNESS, 0, 30);
                    }
                }
                if (!points.isEmpty()) w.playSound(points.get(points.size() / 2), Sound.BLOCK_BEACON_AMBIENT, 0.6f, 2f);
            }
        }.runTaskTimer(Unordinary.plugin(), 0L, 20L);
    }

    static final class DashElectrique extends ActiveAbility {
        DashElectrique() {
            super("remi_dash", "Dash electrique", "Remi fonce en avant sur 10 blocs et laisse derriere elle une zone d'electricite pendant 5 secondes : Lenteur et 1 coeur par seconde aux ennemis dedans.", Material.FEATHER, 25);
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
            dir.normalize();
            Location from = p.getLocation();
            List<Location> points = new ArrayList<>();
            for (double d = 0; d <= 10; d += 1.0) {
                Location l = from.clone().add(dir.clone().multiply(d));
                if (!l.getBlock().isPassable()) break;
                points.add(l);
            }
            Effects.rush(p, from.clone().add(dir.multiply(10)), 4, Particle.ELECTRIC_SPARK);
            p.getWorld().playSound(from, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 2f);
            electricZone(p, p.getWorld(), points, 5, 1.5);
            return true;
        }
    }

    static final Particle.DustOptions BOLT = new Particle.DustOptions(Color.fromRGB(140, 220, 255), 1.0f);
    static final Particle.DustOptions BOLT_CORE = new Particle.DustOptions(Color.fromRGB(235, 250, 255), 0.6f);

    /** Un trait d'eclair entre deux points. */
    static void arc(World w, Location a, Location b, boolean core) {
        Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        if (len < 0.05) return;
        d.normalize();
        for (double x = 0; x <= len; x += 0.22) {
            Location l = a.clone().add(d.clone().multiply(x));
            w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, core ? BOLT_CORE : BOLT);
        }
    }

    /** L'adversaire est electrise : arcs qui crepitent autour de son corps pendant un temps. */
    static void electrify(LivingEntity target, int ticks) {
        World w = target.getWorld();
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                if (t > ticks || target.isDead()) { cancel(); return; }
                Location c = target.getLocation().add(0, 1, 0);
                for (int k = 0; k < 3; k++) {
                    Location from = c.clone().add((Math.random() - 0.5) * 0.6, (Math.random() - 0.5) * 1.6, (Math.random() - 0.5) * 0.6);
                    Location cur = from.clone();
                    for (int seg = 0; seg < 4; seg++) {
                        Location next = cur.clone().add((Math.random() - 0.5) * 1.2, (Math.random() - 0.5) * 1.0, (Math.random() - 0.5) * 1.2);
                        arc(w, cur, next, seg == 0);
                        cur = next;
                    }
                }
                w.spawnParticle(Particle.ELECTRIC_SPARK, c, 6, 0.4, 0.7, 0.4, 0.1);
                if (t % 5 == 1) w.playSound(c, Sound.ENTITY_BEE_HURT, 0.6f, 1.9f + (float) Math.random() * 0.2f);
                if (t % 10 == 1) w.playSound(c, Sound.BLOCK_BEACON_DEACTIVATE, 0.4f, 2f);
            }
        }.runTaskTimer(Unordinary.plugin(), 0L, 1L);
    }

    /** L'eclair qui serpente : il avance de 8 blocs par seconde vers l'adversaire le plus proche devant Remi. */
    static final class Electricite extends ActiveAbility {
        Electricite() {
            super("remi_electricite", "Electricite", "Remi lance un eclair qui serpente vers l'adversaire le plus proche devant elle, sur 10 blocs (8 blocs par seconde). S'il le touche, l'adversaire est electrise : immobilise 2 secondes et 1.5 coeur de degats.", Material.LIGHTNING_ROD, 10);
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            World w = p.getWorld();
            Location start = p.getEyeLocation().add(0, -0.3, 0);
            Vector dir = p.getEyeLocation().getDirection().normalize();
            // Cible : l'ennemi le plus proche dans un cone de 60 degres devant Remi, a 12 blocs.
            LivingEntity target = null; double best = 99;
            for (LivingEntity e : Effects.enemiesWithin(p, 12)) {
                Vector to = e.getLocation().add(0, 1, 0).toVector().subtract(start.toVector());
                double dist = to.length();
                if (dist < 0.5 || to.normalize().dot(dir) < 0.5) continue;
                if (dist < best) { best = dist; target = e; }
            }
            final LivingEntity aim = target;
            w.playSound(start, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.9f);
            w.playSound(start, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.8f);
            List<Location> points = new ArrayList<>();
            points.add(start.clone());
            new BukkitRunnable() {
                int t = 0; double travelled = 0; Vector d = dir.clone(); Location head = start.clone();
                @Override public void run() {
                    t++;
                    try {
                        if (!p.isOnline() || travelled >= 10) { cancel(); return; }
                        // Le serpent se dirige vers sa cible.
                        if (aim != null && !aim.isDead()) {
                            Vector to = aim.getLocation().add(0, 1, 0).toVector().subtract(head.toVector());
                            if (to.lengthSquared() > 0.01) d = d.clone().multiply(0.6).add(to.normalize().multiply(0.4)).normalize();
                        }
                        Vector step = d.clone().multiply(0.4);
                        Location next = head.clone().add(step);
                        if (!next.getBlock().isPassable()) { w.spawnParticle(Particle.ELECTRIC_SPARK, head, 15, 0.2, 0.2, 0.2, 0.1); cancel(); return; }
                        head = next; travelled += 0.4;
                        // Un nouveau zigzag a chaque tick (tous les 0,4 bloc).
                        {
                            Vector side = d.clone().crossProduct(new Vector(0, 1, 0));
                            if (side.lengthSquared() < 0.01) side = new Vector(1, 0, 0);
                            side.normalize();
                            points.add(head.clone().add(side.multiply((Math.random() - 0.5) * 1.2)).add(0, (Math.random() - 0.5) * 0.8, 0));
                            while (points.size() > 40) points.remove(0);
                        }
                        // Dessin de l'eclair : segments en zigzag, certains clignotent.
                        for (int i = 1; i < points.size(); i++) {
                            if (Math.random() < 0.15) continue;
                            arc(w, points.get(i - 1), points.get(i), i == points.size() - 1);
                        }
                        arc(w, points.get(points.size() - 1), head, true);
                        w.spawnParticle(Particle.ELECTRIC_SPARK, head, 3, 0.15, 0.15, 0.15, 0.05);
                        if (t % 3 == 0) w.playSound(head, Sound.ENTITY_BEE_HURT, 0.3f, 2f);
                        for (LivingEntity enemy : Effects.enemiesWithin(p, head, 1.2)) {
                            Effects.damage(enemy, 3.0, p);
                            Effects.root(enemy, 40);
                            electrify(enemy, 40);
                            w.spawnParticle(Particle.FLASH, enemy.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0, Color.fromRGB(180, 230, 255));
                            w.playSound(enemy.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.9f, 1.7f);
                            Effects.say(enemy, "<aqua>Electrise par " + p.getName() + " !");
                            cancel();
                            return;
                        }
                    } catch (Exception ex) { cancel(); }
                }
            }.runTaskTimer(Unordinary.plugin(), 0L, 1L);
            return true;
        }
    }

    static final class Electrocution extends ActiveAbility {
        Electrocution() {
            super("remi_electrocution", "Electrocution", "Pendant 5 secondes, chaque ennemi qui frappe Remi perd 0.5 coeur et subit Faiblesse 10 secondes.", Material.GLOWSTONE_DUST, 10);
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            st.set("until", System.currentTimeMillis() + 5000L);
            World w = p.getWorld();
            w.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t += 5;
                    if (!p.isOnline() || t > 100) { cancel(); return; }
                    w.spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 4, 0.4, 0.6, 0.4, 0.05);
                }
            }.runTaskTimer(Unordinary.plugin(), 0L, 5L);
            Msg.actionBar(p, "<yellow>Electrocution : 5 s");
            return true;
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            long left = st.get("until", 0L) - System.currentTimeMillis();
            return left > 0 ? "<yellow>Electrocution <white>" + (left / 1000 + 1) + "s" : null;
        }
        @Override public void onDamaged(Player victim, EntityDamageEvent e, AbilityState st) {
            if (System.currentTimeMillis() > st.get("until", 0L) || !(e instanceof EntityDamageByEntityEvent be)) return;
            LivingEntity attacker = com.kiokouwhite.crossfight.listener.DamageListener.livingAttackerOf(be.getDamager());
            if (attacker == null || attacker == victim || !Effects.isEnemy(victim, attacker)) return;
            attacker.setNoDamageTicks(0);
            Effects.damage(attacker, 1.0, victim);
            Effects.give(attacker, PotionEffectType.WEAKNESS, 0, 200);
            attacker.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, attacker.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.15);
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5f, 2f);
        }
    }

    static final class DomeElectrique extends UltimateAbility {
        DomeElectrique() {
            super("remi_ult", "Dome electrique", "Pendant 15 secondes, une zone d'electricite de 10 blocs de large : les ennemis a l'interieur perdent 1 coeur par seconde et ont Lenteur.", Material.BEACON);
        }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            World w = p.getWorld();
            Location c = p.getLocation().clone();
            w.strikeLightningEffect(c);
            w.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.2f);
            final double R = 5.0;
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (t > 15 * 20) { cancel(); return; }
                    // Le dome : anneau au sol et arcs qui montent, chaque tick.
                    for (int i = 0; i < 10; i++) {
                        double ang = Math.random() * Math.PI * 2;
                        w.spawnParticle(Particle.ELECTRIC_SPARK, c.clone().add(Math.cos(ang) * R, 0.2 + Math.random() * 0.5, Math.sin(ang) * R), 1, 0, 0, 0, 0.02);
                        double h = Math.random() * Math.PI / 2;
                        w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(ang) * R * Math.cos(h), R * Math.sin(h), Math.sin(ang) * R * Math.cos(h)), 1, 0, 0, 0, 0, SPARK);
                    }
                    if (t % 20 == 0) {
                        w.playSound(c, Sound.BLOCK_BEACON_AMBIENT, 1f, 1.8f);
                        for (LivingEntity enemy : Effects.within(c, R, le -> Effects.isEnemy(p, le))) {
                            enemy.setNoDamageTicks(0);
                            Effects.damage(enemy, 2.0, p);
                            Effects.give(enemy, PotionEffectType.SLOWNESS, 0, 30);
                            w.spawnParticle(Particle.ELECTRIC_SPARK, enemy.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.1);
                        }
                    }
                }
            }.runTaskTimer(Unordinary.plugin(), 0L, 1L);
            return true;
        }
    }
}
