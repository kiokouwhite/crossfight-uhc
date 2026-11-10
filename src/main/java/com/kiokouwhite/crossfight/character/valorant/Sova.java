package com.kiokouwhite.crossfight.character.valorant;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Bullets;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Allay;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sova - Path of Nihility.
 */
public final class Sova extends Character implements Listener {

    static final NamespacedKey SHOCK = new NamespacedKey("crossfight", "sova_shock");
    static final NamespacedKey RECON = new NamespacedKey("crossfight", "sova_recon");

    public Sova() {
        super("sova", "Sova", Voie.NIHILITE, Material.SPECTRAL_ARROW);
        addAbility(new Electrofleche());
        addAbility(new FlecheDeReconnaissance());
        addAbility(new DroneRapace());
        addAbility(Valorant.SHOP);
        addAbility(Guns.ABILITY);
        setUltimate(new FureurDuChasseur());
    }

    private static com.kiokouwhite.crossfight.CrossfightPlugin plugin() { return com.kiokouwhite.crossfight.CrossfightPlugin.get(); }

    @Override
    public ItemStack createWeapon() { return Valorant.meleeWeapon(0); }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Electrofleche : explosion electrique 3x3, 2 coeurs",
                "<white>Fleche de reco : ennemis en surbrillance a 40 blocs",
                "<white>Drone Rapace : tire des fleches de Faiblesse 10 s",
                "<light_purple>Ultime : 3 rayons de 10 blocs, 3 coeurs");
    }

    /** Deux fleches en reserve, une revient toutes les 6 secondes. */
    static final class Electrofleche extends ActiveAbility implements CustomUse {
        static final int MAX = 2; static final long RELOAD_MS = 6000L;
        Electrofleche() {
            super("sova_electro", "Electrofleche", "Sova tire une fleche : a l'impact, une explosion electrique de 3x3 inflige 2 coeurs et immobilise 1 seconde. 2 fleches, une revient toutes les 6 secondes.", Material.SPECTRAL_ARROW, 0);
        }
        int arrows(AbilityState st) {
            int b = st.get("arrows", MAX); long last = st.get("reloadAt", 0L); long now = System.currentTimeMillis();
            if (b < MAX) {
                if (last == 0L) st.set("reloadAt", now);
                else while (b < MAX && now - last >= RELOAD_MS) { b++; last += RELOAD_MS; }
                st.set("arrows", b); st.set("reloadAt", b >= MAX ? 0L : last);
            }
            return b;
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            int b = arrows(st);
            return "<aqua>Electro " + "●".repeat(b) + "<dark_gray>" + "●".repeat(MAX - b);
        }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            int b = arrows(st);
            if (b <= 0) { Msg.actionBar(p, "<red>Electrofleche : aucune fleche prete."); return true; }
            if (b == MAX) st.set("reloadAt", System.currentTimeMillis());
            st.set("arrows", b - 1);
            Arrow a = Bullets.shoot(p, p.getEyeLocation().getDirection(), 2.5, 1.0, Material.LIGHT_BLUE_CONCRETE, Color.fromRGB(80, 200, 255));
            a.getPersistentDataContainer().set(SHOCK, PersistentDataType.BYTE, (byte) 1);
            return true;
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }
    }

    static final class FlecheDeReconnaissance extends ActiveAbility {
        FlecheDeReconnaissance() {
            super("sova_reco", "Fleche de reconnaissance", "La ou la fleche atterrit, les ennemis a 40 blocs sont en surbrillance et ralentis pendant 10 secondes.", Material.ENDER_EYE, 40);
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Arrow a = Bullets.shoot(p, p.getEyeLocation().getDirection(), 2.5, 0.5, Material.LIME_CONCRETE, Color.LIME);
            a.getPersistentDataContainer().set(RECON, PersistentDataType.BYTE, (byte) 1);
            return true;
        }
    }

    @EventHandler
    public void onArrowLand(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow a) || !(a.getShooter() instanceof Player p)) return;
        Location c = a.getLocation();
        World w = c.getWorld();
        if (a.getPersistentDataContainer().has(SHOCK)) {
            w.spawnParticle(Particle.ELECTRIC_SPARK, c, 80, 1.5, 1, 1.5, 0.2);
            w.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.8f);
            for (LivingEntity enemy : Effects.enemiesWithin(p, c, 2.5)) {
                Effects.damage(enemy, 4.0, p);
                Effects.root(enemy, 20);
            }
        } else if (a.getPersistentDataContainer().has(RECON)) {
            w.spawnParticle(Particle.END_ROD, c, 60, 2, 2, 2, 0.1);
            w.playSound(c, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.5f);
            int n = 0;
            for (LivingEntity enemy : Effects.enemiesWithin(p, c, 40)) {
                Effects.give(enemy, PotionEffectType.GLOWING, 0, 200);
                Effects.give(enemy, PotionEffectType.SLOWNESS, 0, 200);
                n++;
            }
            Msg.actionBar(p, "<green>Reconnaissance : " + n + " ennemi(s) revele(s)");
        }
    }

    static final class DroneRapace extends ActiveAbility {
        DroneRapace() {
            super("sova_drone", "Drone Rapace", "Un drone suit Sova pendant 10 secondes et tire des fleches de Faiblesse sur les adversaires a moins de 20 blocs.", Material.PHANTOM_MEMBRANE, 20);
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Allay drone = (Allay) p.getWorld().spawnEntity(p.getEyeLocation().add(0, 1.5, 0), EntityType.ALLAY);
            drone.setAI(false);
            drone.setGravity(false);
            drone.setInvulnerable(true);
            drone.setSilent(true);
            drone.customName(Msg.mm("<aqua>Drone Rapace"));
            drone.setCustomNameVisible(true);
            drone.setGlowing(true);
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    ticks++;
                    if (ticks > 200 || !p.isOnline() || !drone.isValid()) { if (drone.isValid()) { drone.getWorld().spawnParticle(Particle.EXPLOSION, drone.getLocation(), 1); drone.remove(); } cancel(); return; }
                    Location want = p.getEyeLocation().add(0, 2.0, 0).add(Effects.towards(p, null).multiply(-1.0));
                    Location cur = drone.getLocation();
                    Location next = cur.clone().add(want.toVector().subtract(cur.toVector()).multiply(0.3));
                    LivingEntity target = Effects.nearestEnemy(p, 20);
                    if (target != null) next.setDirection(target.getEyeLocation().toVector().subtract(next.toVector()));
                    drone.teleport(next);
                    if (ticks % 20 == 0 && target != null) {
                        Vector dir = target.getEyeLocation().toVector().subtract(drone.getEyeLocation().toVector()).normalize();
                        Arrow a = drone.getWorld().spawnArrow(drone.getEyeLocation().add(dir), dir, 2.0f, 1f);
                        a.setShooter(p);
                        a.setDamage(1.0);
                        a.setBasePotionType(PotionType.WEAKNESS);
                        a.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
                        drone.getWorld().playSound(drone.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.7f, 1.4f);
                    }
                }
            }.runTaskTimer(plugin(), 1L, 1L);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ALLAY_ITEM_GIVEN, 1f, 1.2f);
            return true;
        }
    }

    static final class FureurDuChasseur extends UltimateAbility {
        FureurDuChasseur() {
            super("sova_ult", "Fureur du Chasseur", "Sova s'immobilise et tire trois rayons de 10 blocs devant lui, infligeant 3 coeurs aux adversaires touches.", Material.END_CRYSTAL);
        }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            Effects.root(p, 60);
            new BukkitRunnable() {
                int shots = 0;
                @Override public void run() {
                    if (shots >= 3 || !p.isOnline()) { Effects.unroot(p); cancel(); return; }
                    shots++;
                    ray(p);
                }
            }.runTaskTimer(plugin(), 10L, 14L);
            return true;
        }

        static void ray(Player p) {
            Location eye = p.getEyeLocation();
            Vector dir = eye.getDirection().normalize();
            World w = p.getWorld();
            List<UUID> hit = new ArrayList<>();
            for (double d = 0.5; d <= 10; d += 0.5) {
                Location l = eye.clone().add(dir.clone().multiply(d));
                if (!l.getBlock().isPassable()) break;
                w.spawnParticle(Particle.DUST, l, 2, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(Color.fromRGB(80, 200, 255), 1.6f));
                w.spawnParticle(Particle.ELECTRIC_SPARK, l, 1, 0.1, 0.1, 0.1, 0.02);
                for (LivingEntity enemy : Effects.enemiesWithin(p, l, 1.0)) {
                    if (hit.contains(enemy.getUniqueId())) continue;
                    hit.add(enemy.getUniqueId());
                    Effects.damage(enemy, 6.0, p);
                }
            }
            w.playSound(eye, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.7f, 1.9f);
        }
    }
}
