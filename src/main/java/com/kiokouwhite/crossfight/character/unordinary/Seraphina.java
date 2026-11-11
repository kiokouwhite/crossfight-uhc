package com.kiokouwhite.crossfight.character.unordinary;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Seraphina - Path of Nihility.
 */
public final class Seraphina extends Character {

    static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 215, 120), 1.0f);

    public Seraphina() {
        super("seraphina", "Seraphina", Voie.NIHILITE, Material.CLOCK);
        addAbility(Unordinary.AURA);
        addAbility(new RetourDansLeTemps());
        addAbility(new ChocTemporel());
        addAbility(new ArretTemporel());
        setUltimate(new ExplosionTemporelle());
    }

    @Override public ItemStack createWeapon() { return null; }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Epee d'Aura a crafter (actif selon le craft)",
                "<white>Retour dans le temps : revient 5 s en arriere",
                "<white>Choc Temporel : tous les 6 coups, immobilise 2 s",
                "<white>Arret Temporel : fige les ennemis a 10 blocs 3 s",
                "<light_purple>Ultime : immobilise tous les ennemis a 30 blocs 5 s");
    }

    static void clockBurst(World w, Location c, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double ang = i * 2 * Math.PI / count;
            w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(ang) * radius, 0.2, Math.sin(ang) * radius), 1, 0, 0, 0, 0, GOLD);
        }
        w.spawnParticle(Particle.END_ROD, c.clone().add(0, 1, 0), 12, radius * 0.5, 0.5, radius * 0.5, 0.02);
    }

    static final Particle.DustOptions PINK = new Particle.DustOptions(Color.fromRGB(255, 90, 200), 1.1f);
    static final Particle.DustOptions PINK_SOFT = new Particle.DustOptions(Color.fromRGB(255, 160, 230), 0.7f);

    /** Un engrenage rose dans un plan vertical (axe "side"), dents comprises, tourne avec spin. */
    static void gear(World w, Location c, org.bukkit.util.Vector side, double r, double spin) {
        org.bukkit.util.Vector up = new org.bukkit.util.Vector(0, 1, 0);
        int teeth = 8;
        for (int i = 0; i < teeth * 4; i++) {
            double a = i * 2 * Math.PI / (teeth * 4) + spin;
            double rad = (i % 4 < 2) ? r + 0.28 : r;
            w.spawnParticle(Particle.DUST, c.clone().add(side.clone().multiply(Math.cos(a) * rad)).add(up.clone().multiply(Math.sin(a) * rad)), 1, 0, 0, 0, 0, PINK);
        }
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6 - spin;
            w.spawnParticle(Particle.DUST, c.clone().add(side.clone().multiply(Math.cos(a) * r * 0.45)).add(up.clone().multiply(Math.sin(a) * r * 0.45)), 1, 0, 0, 0, 0, PINK_SOFT);
        }
    }

    /** Trois engrenages roses qui suivent Seraphina. */
    static void gears(Player p, double spin) {
        World w = p.getWorld();
        org.bukkit.util.Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) dir = new org.bukkit.util.Vector(1, 0, 0);
        dir.normalize();
        org.bukkit.util.Vector side = dir.clone().crossProduct(new org.bukkit.util.Vector(0, 1, 0)).normalize();
        Location c = p.getLocation().add(0, 1.1, 0).subtract(dir.clone().multiply(0.6));
        gear(w, c, side, 1.1, spin);
        gear(w, c.clone().add(side.clone().multiply(1.6)).add(0, 0.6, 0), side, 0.5, -spin * 2.2);
        gear(w, c.clone().add(side.clone().multiply(-1.5)).add(0, -0.4, 0), side, 0.6, -spin * 1.8);
    }

    /** Les positions sont relevees a chaque tick (5 secondes = 100 positions). */
    static final class RetourDansLeTemps extends ActiveAbility {
        private final Map<UUID, Deque<Location>> history = new HashMap<>();
        RetourDansLeTemps() {
            super("seraphina_retour", "Retour dans le temps", "Seraphina rembobine son propre temps : elle refait a toute vitesse son trajet a l'envers jusqu'a la ou elle etait 5 secondes auparavant.", Material.CLOCK, 10);
        }
        @Override public void onEquip(Player p, CfPlayer cf) {
            Deque<Location> h = new ArrayDeque<>();
            history.put(p.getUniqueId(), h);
            new BukkitRunnable() {
                @Override public void run() {
                    if (!p.isOnline() || history.get(p.getUniqueId()) != h) { cancel(); return; }
                    AbilityState st = Unordinary.plugin().abilities().state(p, RetourDansLeTemps.this);
                    if (st.get("rewinding", false)) return;
                    h.addLast(p.getLocation().clone());
                    while (h.size() > 100) h.removeFirst();
                }
            }.runTaskTimer(Unordinary.plugin(), 1L, 1L);
        }
        @Override public void onUnequip(Player p, CfPlayer cf) { history.remove(p.getUniqueId()); }

        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Deque<Location> h = history.get(p.getUniqueId());
            if (h == null || h.size() < 5) { Msg.actionBar(p, "<red>Pas encore de passe a rembobiner."); return false; }
            List<Location> path = new ArrayList<>(h);
            java.util.Collections.reverse(path);
            h.clear();
            st.set("rewinding", true);
            World w = p.getWorld();
            Effects.noFallUntil(p, 4000);
            Effects.invincible(p, 1500L);
            w.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
            w.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.5f);
            final int perTick = 8; // 100 positions en ~12 ticks : comme une bobine qu'on remonte
            new BukkitRunnable() {
                int i = 0, t = 0;
                @Override public void run() {
                    t++;
                    try {
                        if (!p.isOnline() || p.isDead()) { st.set("rewinding", false); cancel(); return; }
                        if (i < path.size()) {
                            int next = Math.min(path.size() - 1, i + perTick - 1);
                            Location from = p.getLocation();
                            for (int k = i; k <= next; k++) {
                                Location l = path.get(k);
                                w.spawnParticle(Particle.DUST, l.clone().add(0, 1, 0), 2, 0.15, 0.3, 0.15, 0, PINK);
                                w.spawnParticle(Particle.DUST, l.clone().add(0, 0.4, 0), 1, 0.2, 0.2, 0.2, 0, PINK_SOFT);
                            }
                            Location to = path.get(next).clone();
                            to.setYaw(from.getYaw()); to.setPitch(from.getPitch());
                            p.teleport(to);
                            i = next + 1;
                            if (t % 2 == 0) w.playSound(to, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 2f - t * 0.08f);
                        } else if (t > 24) { st.set("rewinding", false); cancel(); return; }
                        gears(p, t * 0.35);
                    } catch (Exception ex) { st.set("rewinding", false); cancel(); }
                }
            }.runTaskTimer(Unordinary.plugin(), 0L, 1L);
            return true;
        }
    }

    static final class ChocTemporel extends PassiveAbility {
        ChocTemporel() {
            super("seraphina_choc", "Choc Temporel", "Tous les 6 coups infliges, l'adversaire touche est immobilise pendant 2 secondes.");
        }
        @Override public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) {
            if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
            int hits = st.get("hits", 0) + 1;
            if (hits < 6) { st.set("hits", hits); return; }
            st.set("hits", 0);
            Effects.root(victim, 40);
            clockBurst(victim.getWorld(), victim.getLocation(), 1.0, 16);
            victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.8f, 1.6f);
            Effects.say(victim, "<gold>Choc Temporel : immobilise 2 s !");
            com.kiokouwhite.crossfight.util.Sounds.ability(attacker, "seraphina_choc");
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { return "<gold>Choc <white>" + st.get("hits", 0) + "/6"; }
    }

    static final Material[] GREYS = {Material.LIGHT_GRAY_CONCRETE, Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE_POWDER, Material.CYAN_TERRACOTTA};
    static final Particle.DustOptions GREY = new Particle.DustOptions(Color.fromRGB(150, 150, 155), 1.3f);

    /** Le temps s'arrete : le sol devient gris (blocs temporaires) et une brume grise flotte dans la zone. */
    static void greyZone(Location c, int radius, int ticks) {
        World w = c.getWorld();
        com.kiokouwhite.crossfight.util.TempBlocks floor = new com.kiokouwhite.crossfight.util.TempBlocks().unbreakable();
        for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > radius) continue;
            int y = Effects.groundAround(w, c.getBlockX() + x, c.getBlockZ() + z, c.getBlockY());
            if (y == Integer.MIN_VALUE) continue;
            org.bukkit.block.Block top = w.getBlockAt(c.getBlockX() + x, y, c.getBlockZ() + z);
            if (!top.getType().isSolid()) continue;
            Material m = d > radius - 1.5 ? Material.GRAY_CONCRETE : GREYS[(int) (Math.random() * (GREYS.length - 1))];
            floor.replace(top, m);
        }
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t += 4;
                if (t > ticks) { floor.restore(); cancel(); return; }
                for (int i = 0; i < 14; i++) {
                    double a = Math.random() * Math.PI * 2, r = Math.random() * radius;
                    w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * r, 0.3 + Math.random() * 2.5, Math.sin(a) * r), 1, 0.1, 0.1, 0.1, 0, GREY);
                }
                w.spawnParticle(Particle.SMOKE, c.clone().add(0, 1, 0), 6, radius * 0.6, 0.8, radius * 0.6, 0.005);
            }
        }.runTaskTimer(Unordinary.plugin(), 0L, 4L);
    }

    static final class ArretTemporel extends ActiveAbility {
        ArretTemporel() {
            super("seraphina_arret", "Arret Temporel", "Seraphina arrete le temps a 10 blocs : les ennemis sont immobilises et aveugles 3 secondes, elle gagne Vitesse III, mais ne peut pas les frapper pendant ce temps.", Material.RECOVERY_COMPASS, 15);
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            List<LivingEntity> enemies = Effects.enemiesWithin(p, 10);
            World w = p.getWorld();
            w.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.6f);
            w.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1.5f);
            clockBurst(w, p.getLocation(), 10, 60);
            greyZone(p.getLocation(), 10, 60);
            Effects.give(p, PotionEffectType.SPEED, 2, 60);
            java.util.List<String> frozen = new java.util.ArrayList<>();
            for (LivingEntity enemy : enemies) {
                Effects.root(enemy, 60);
                Effects.give(enemy, PotionEffectType.BLINDNESS, 0, 60);
                frozen.add(enemy.getUniqueId().toString());
                w.spawnParticle(Particle.DUST, enemy.getLocation().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0, GOLD);
            }
            st.set("frozen", frozen);
            st.set("frozenUntil", System.currentTimeMillis() + 3000L);
            Msg.actionBar(p, "<gold>Arret Temporel : " + enemies.size() + " ennemi(s) fige(s) 3 s");
            return true;
        }
        /** Pendant l'arret, Seraphina ne peut pas frapper les ennemis figes. */
        @Override public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) {
            if (System.currentTimeMillis() > st.get("frozenUntil", 0L)) return;
            List<String> frozen = st.get("frozen", null);
            if (frozen != null && frozen.contains(victim.getUniqueId().toString())) {
                e.setCancelled(true);
                Msg.actionBar(attacker, "<gray>Le temps est arrete : impossible de frapper.");
            }
        }
    }

    static final class ExplosionTemporelle extends UltimateAbility {
        ExplosionTemporelle() {
            super("seraphina_ult", "Explosion temporelle", "Une explosion temporelle part de Seraphina : l'onde grise progressivement le sol sur 30 blocs et immobilise 5 secondes chaque ennemi qu'elle atteint.", Material.NETHER_STAR);
        }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            World w = p.getWorld();
            Location c = p.getLocation().clone();
            final double R = 30.0;
            w.spawnParticle(Particle.EXPLOSION_EMITTER, c.clone().add(0, 1, 0), 3, 0.5, 0.5, 0.5, 0);
            w.spawnParticle(Particle.FLASH, c.clone().add(0, 1, 0), 1, 0, 0, 0, 0, Color.fromRGB(230, 230, 235));
            w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
            w.playSound(c, Sound.BLOCK_BELL_RESONATE, 1f, 0.5f);
            com.kiokouwhite.crossfight.util.TempBlocks floor = new com.kiokouwhite.crossfight.util.TempBlocks().unbreakable();
            java.util.Set<UUID> frozen = new java.util.HashSet<>();
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    try {
                        // L'onde avance de 1,5 bloc par tick : en 20 ticks elle a grise les 30 blocs.
                        if (t <= 20) {
                            double rNow = R * t / 20.0, rPrev = R * (t - 1) / 20.0;
                            int ri = (int) Math.ceil(rNow);
                            for (int x = -ri; x <= ri; x++) for (int z = -ri; z <= ri; z++) {
                                double d = Math.sqrt(x * x + z * z);
                                if (d > rNow || d <= rPrev) continue;
                                int y = Effects.groundAround(w, c.getBlockX() + x, c.getBlockZ() + z, c.getBlockY());
                                if (y == Integer.MIN_VALUE) continue;
                                org.bukkit.block.Block top = w.getBlockAt(c.getBlockX() + x, y, c.getBlockZ() + z);
                                if (top.getType().isSolid()) floor.replace(top, GREYS[(int) (Math.random() * (GREYS.length - 1))]);
                            }
                            // Le front de l'onde : anneau de poussiere grise et de fumee, qui fige ce qu'il touche.
                            int pts = (int) (2 * Math.PI * rNow);
                            for (int i = 0; i < Math.max(8, pts); i++) {
                                double a = i * 2 * Math.PI / Math.max(8, pts);
                                Location l = c.clone().add(Math.cos(a) * rNow, 0.5 + Math.random() * 1.5, Math.sin(a) * rNow);
                                w.spawnParticle(Particle.DUST, l, 1, 0.2, 0.3, 0.2, 0, GREY);
                                if (i % 3 == 0) w.spawnParticle(Particle.CLOUD, l, 1, 0.2, 0.2, 0.2, 0.02);
                            }
                            if (t % 4 == 0) w.playSound(c.clone().add(rNow, 0, 0), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.2f + t * 0.02f);
                            for (LivingEntity enemy : Effects.within(c, rNow + 1.0, le -> Effects.isEnemy(p, le))) {
                                if (!frozen.add(enemy.getUniqueId())) continue;
                                Effects.root(enemy, 100);
                                Effects.say(enemy, "<gold>Explosion temporelle : fige 5 s !");
                                w.spawnParticle(Particle.DUST, enemy.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0, GREY);
                                clockBurst(w, enemy.getLocation(), 1.0, 16);
                            }
                            if (t == 20) Msg.actionBar(p, "<gold>Explosion temporelle : " + frozen.size() + " ennemi(s) fige(s)");
                        } else if (t % 4 == 0) {
                            for (int i = 0; i < 20; i++) {
                                double a = Math.random() * Math.PI * 2, r = Math.random() * R;
                                w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * r, 0.3 + Math.random() * 2.5, Math.sin(a) * r), 1, 0.1, 0.1, 0.1, 0, GREY);
                            }
                        }
                        if (t >= 120) { floor.restore(); w.playSound(c, Sound.BLOCK_BELL_USE, 0.8f, 1.2f); cancel(); }
                    } catch (Exception ex) { floor.restore(); cancel(); }
                }
            }.runTaskTimer(Unordinary.plugin(), 0L, 1L);
            return true;
        }
    }
}
