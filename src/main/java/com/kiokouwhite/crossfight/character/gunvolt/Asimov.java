package com.kiokouwhite.crossfight.character.gunvolt;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.character.duo.Synergy;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Bullets;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Asimov - Voie de la Chasse. Un fusil qui se charge, des zones electriques, une tourelle et des chaines.
 */
public final class Asimov extends Character implements Listener {

    static final NamespacedKey RIFLE = new NamespacedKey("crossfight", "asimov_rifle");
    static final NamespacedKey BALL = new NamespacedKey("crossfight", "asimov_ball");

    public Asimov() {
        super("asimov", "Asimov", Voie.CHASSE, Material.IRON_HOE);
        addAbility(new AntiTankRifle().bindToWeapon());
        addAbility(new SeptimalDash());
        addAbility(new ElectricShotgun());
        addAbility(new KunaiTurret());
        addAbility(Synergy.ITEM);
        setUltimate(new VoltaicChain());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.DIAMOND_HOE, 1.0, "<aqua><bold>Electromagnetic Anti-Tank Rifle", List.of("<gray>Fusil : clic droit, charge 3 s puis tire (Puissance III)."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Anti-Tank Rifle : tir charge 3 s, double a moins de 5 blocs",
                "<white>Septimal Dash : 10 blocs avec une trainee electrique",
                "<white>Electric Shotgun : envol puis 3 tirs au sol",
                "<white>Kunai Tourelle : 8 boules electriques toutes les 5 s",
                "<light_purple>Ultime : Voltaic Chain, chaines sur 30 blocs, 2 coeurs/s");
    }

    // ------------------------------------------------------------------ arme

    static final class AntiTankRifle extends ActiveAbility implements CustomUse {
        AntiTankRifle() { super("asimov_rifle", "Anti-Tank Rifle", "Clic droit : Asimov charge 3 secondes puis tire (Puissance III). Le tir fait le double de degats si l'adversaire est a moins de 5 blocs.", Material.DIAMOND_HOE, 0); }
        @Override public List<String> weaponLore() { return List.of("<aqua>Clic droit : <gray>charger 3 s puis tirer, x2 a moins de 5 blocs"); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { return st.get("charging", false) ? "<aqua>Fusil <white>en charge..." : null; }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            if (st.get("charging", false)) return true;
            st.set("charging", true);
            World w = p.getWorld();
            w.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.2f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    try {
                        if (!p.isOnline() || p.isDead()) { st.set("charging", false); cancel(); return; }
                        Location eye = p.getEyeLocation();
                        Vector dir = eye.getDirection();
                        Location muzzle = eye.clone().add(dir.clone().multiply(1.0)).add(0, -0.3, 0);
                        double f = t / 60.0;
                        w.spawnParticle(Particle.ELECTRIC_SPARK, muzzle, 1 + (int) (f * 5), 0.15 * f + 0.05, 0.15 * f + 0.05, 0.15 * f + 0.05, 0.02);
                        if (t % 10 == 0) w.playSound(muzzle, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 0.6f + (float) f);
                        if (t >= 60) {
                            st.set("charging", false);
                            Arrow a = Bullets.shoot(p, dir, 4.0, 9.0, Material.LIGHT_BLUE_CONCRETE, Color.fromRGB(120, 220, 255));
                            a.getPersistentDataContainer().set(RIFLE, PersistentDataType.BYTE, (byte) 1);
                            w.playSound(eye, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 2f);
                            w.playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.8f);
                            w.spawnParticle(Particle.FLASH, muzzle, 1, 0, 0, 0, 0, Color.fromRGB(180, 230, 255));
                            cancel();
                        }
                    } catch (Exception ex) { st.set("charging", false); cancel(); }
                }
            }.runTaskTimer(ABG.plugin(), 1L, 1L);
            return true;
        }
    }

    @EventHandler
    public void onRifleHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow a) || !(a.getShooter() instanceof Player p)) return;
        var pdc = a.getPersistentDataContainer();
        if (pdc.has(RIFLE) && e.getHitEntity() instanceof LivingEntity t && Effects.isEnemy(p, t)) {
            if (t.getLocation().distance(p.getLocation()) <= 5) {
                // Le double a bout portant : on ajoute l'equivalent d'un second tir.
                t.setNoDamageTicks(0);
                Effects.damage(t, 9.0, p);
                t.getWorld().spawnParticle(Particle.EXPLOSION, t.getLocation().add(0, 1, 0), 1);
                Msg.actionBar(p, "<aqua>Tir a bout portant : degats doubles !");
            }
        } else if (pdc.has(BALL) && e.getHitEntity() instanceof LivingEntity t && Effects.isEnemy(p, t)) {
            Effects.give(t, PotionEffectType.SLOWNESS, 1, 60);
            t.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, t.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.1);
        }
    }

    // ---------------------------------------------------------------- pouvoirs

    static final class SeptimalDash extends ActiveAbility {
        SeptimalDash() { super("asimov_dash", "Septimal Dash", "Dash de vitesse moyenne sur 10 blocs : une zone electrique de 3x3 suit le trajet pendant 5 secondes (1 coeur par seconde, Lenteur).", Material.FEATHER, 15); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
            dir.normalize();
            Location from = p.getLocation();
            List<Location> points = new ArrayList<>();
            for (double d = 0; d <= 10; d += 1.5) { Location l = from.clone().add(dir.clone().multiply(d)); if (!l.getBlock().isPassable()) break; points.add(l); }
            Effects.rush(p, from.clone().add(dir.multiply(10)), 8, Particle.ELECTRIC_SPARK);
            p.getWorld().playSound(from, Sound.ENTITY_BREEZE_SHOOT, 1f, 1f);
            for (Location l : points) ABG.zone(p, () -> l, 1.5, 5, 2.0, 0);
            return true;
        }
    }

    static final class ElectricShotgun extends ActiveAbility {
        ElectricShotgun() { super("asimov_shotgun", "Electric Shotgun", "Asimov s'envole de 5 blocs puis dash de 12 blocs en tirant 3 fois au sol (tous les 4 blocs) : 2 coeurs et une zone electrique de 2x2 (1 coeur par seconde, Lenteur).", Material.FIREWORK_ROCKET, 20); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
            dir.normalize();
            World w = p.getWorld();
            Effects.launchUp(p, 5);
            Effects.noFallUntil(p, 8000);
            w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 1.2f);
            final Vector d = dir;
            new BukkitRunnable() {
                @Override public void run() {
                    Location start = p.getLocation();
                    Effects.rush(p, start.clone().add(d.clone().multiply(12)), 8, Particle.ELECTRIC_SPARK);
                    for (int k = 1; k <= 3; k++) {
                        final int kk = k;
                        new BukkitRunnable() {
                            @Override public void run() {
                                Location ground = p.getLocation();
                                int gy = Effects.groundAround(w, ground.getBlockX(), ground.getBlockZ(), ground.getBlockY());
                                Location target = new Location(w, ground.getX(), gy == Integer.MIN_VALUE ? ground.getY() : gy + 1, ground.getZ());
                                ABG.arc(w, p.getEyeLocation(), target);
                                w.spawnParticle(Particle.EXPLOSION, target, 1);
                                w.playSound(target, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
                                for (LivingEntity e : Effects.within(target, 1.5, le -> Effects.isEnemy(p, le))) { e.setNoDamageTicks(0); Effects.damage(e, 4.0, p); }
                                ABG.zone(p, () -> target, 1.0, 5, 2.0, 0);
                            }
                        }.runTaskLater(ABG.plugin(), kk * 3L);
                    }
                }
            }.runTaskLater(ABG.plugin(), 12L);
            return true;
        }
    }

    static final class KunaiTurret extends ActiveAbility {
        KunaiTurret() { super("asimov_turret", "Kunai Tourelle", "Asimov jette un kunai 5 blocs devant lui : la tourelle tire 8 boules electriques dans 8 directions (20 blocs, 3 coeurs, Lenteur II) toutes les 5 secondes, pendant 10 secondes.", Material.IRON_NUGGET, 20); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            World w = p.getWorld();
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
            Location at = p.getLocation().add(dir.normalize().multiply(5));
            int gy = Effects.groundAround(w, at.getBlockX(), at.getBlockZ(), at.getBlockY());
            if (gy != Integer.MIN_VALUE) at.setY(gy + 1);
            ItemDisplay kunai = w.spawn(at.clone().add(0, 0.3, 0), ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(Material.IRON_SWORD));
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationX((float) Math.PI / 2), new Vector3f(0.7f, 0.7f, 0.7f), new Quaternionf()));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setPersistent(false);
            });
            w.playSound(at, Sound.ITEM_TRIDENT_HIT_GROUND, 1f, 1.5f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || t > 200) { kunai.remove(); w.spawnParticle(Particle.SMOKE, at, 10, 0.2, 0.2, 0.2, 0.02); cancel(); return; }
                    w.spawnParticle(Particle.ELECTRIC_SPARK, at.clone().add(0, 0.5, 0), 1, 0.2, 0.2, 0.2, 0.02);
                    if (t % 100 == 0) {
                        w.playSound(at, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.8f);
                        for (int i = 0; i < 8; i++) {
                            double a = i * Math.PI / 4;
                            Vector d = new Vector(Math.cos(a), 0.05, Math.sin(a));
                            Arrow ball = Bullets.shoot(p, d, 2.0, 6.0, Material.LIGHT_BLUE_CONCRETE, Color.fromRGB(120, 220, 255));
                            ball.teleport(at.clone().add(0, 1, 0).add(d.clone().multiply(0.5)).setDirection(d));
                            ball.setVelocity(d.clone().multiply(2.0));
                            ball.setGravity(false);
                            ball.getPersistentDataContainer().set(BALL, PersistentDataType.BYTE, (byte) 1);
                            org.bukkit.Bukkit.getScheduler().runTaskLater(ABG.plugin(), () -> { if (ball.isValid()) ball.remove(); }, 12L);
                        }
                    }
                }
            }.runTaskTimer(ABG.plugin(), 1L, 1L);
            return true;
        }
    }

    // ----------------------------------------------------------------- ultime

    static final class VoltaicChain extends UltimateAbility {
        VoltaicChain() { super("asimov_ult", "Voltaic Chain", "Des chaines electriques se dispersent au hasard sur 30 blocs pendant 8 secondes : 2 coeurs par seconde a qui se trouve dessus.", Material.IRON_CHAIN); }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            World w = p.getWorld();
            Location c = p.getLocation().clone();
            // Les chaines : segments qui partent du centre et se ramifient au hasard.
            List<Location[]> segments = new ArrayList<>();
            for (int k = 0; k < 10; k++) {
                Location cur = c.clone();
                double ang = Math.random() * Math.PI * 2;
                for (int seg = 0; seg < 6; seg++) {
                    ang += (Math.random() - 0.5) * 1.4;
                    Location next = cur.clone().add(Math.cos(ang) * 5, 0, Math.sin(ang) * 5);
                    if (next.distance(c) > 30) break;
                    int gy = Effects.groundAround(w, next.getBlockX(), next.getBlockZ(), next.getBlockY());
                    if (gy != Integer.MIN_VALUE) next.setY(gy + 1);
                    segments.add(new Location[]{cur.clone(), next.clone()});
                    cur = next;
                }
            }
            w.playSound(c, Sound.BLOCK_CHAIN_PLACE, 1f, 0.6f);
            w.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.6f);
            Particle.DustOptions chain = new Particle.DustOptions(Color.fromRGB(190, 190, 200), 0.9f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (t > 160) { cancel(); return; }
                    for (Location[] s : segments) {
                        Vector d = s[1].toVector().subtract(s[0].toVector());
                        double len = d.length();
                        if (len < 0.1) continue;
                        d.normalize();
                        for (double x = (t % 2) * 0.35; x <= len; x += 0.7) {
                            Location l = s[0].clone().add(d.clone().multiply(x));
                            w.spawnParticle(Particle.DUST, l.add(0, 0.15 + 0.1 * Math.sin(x * 3 + t * 0.4), 0), 1, 0, 0, 0, 0, chain);
                        }
                        if (t % 4 == 0 && Math.random() < 0.3) { Location l = s[0].clone().add(d.clone().multiply(Math.random() * len)); ABG.sparks(w, l, 0.8, 1); }
                    }
                    if (t % 20 == 0) {
                        Set<UUID> hit = new HashSet<>();
                        for (Location[] s : segments) {
                            Vector d = s[1].toVector().subtract(s[0].toVector());
                            double len = d.length();
                            if (len < 0.1) continue;
                            d.normalize();
                            for (double x = 0; x <= len; x += 1.0) {
                                for (LivingEntity e : Effects.within(s[0].clone().add(d.clone().multiply(x)), 1.0, le -> Effects.isEnemy(p, le))) {
                                    if (!hit.add(e.getUniqueId())) continue;
                                    e.setNoDamageTicks(0);
                                    Effects.damage(e, 4.0, p);
                                    w.spawnParticle(Particle.ELECTRIC_SPARK, e.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
                                    w.playSound(e.getLocation(), Sound.BLOCK_CHAIN_HIT, 1f, 1.4f);
                                }
                            }
                        }
                        w.playSound(c, Sound.BLOCK_BEACON_AMBIENT, 1f, 1.8f);
                    }
                }
            }.runTaskTimer(ABG.plugin(), 0L, 1L);
            return true;
        }
    }
}
