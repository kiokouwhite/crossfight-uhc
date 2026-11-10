package com.kiokouwhite.crossfight.character.shoja;

import com.kiokouwhite.crossfight.character.duo.Synergy;
import com.kiokouwhite.crossfight.util.Bullets;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.TempBlocks;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les six Synergies de Shoja's Memories.
 */
final class ShojaSynergies {

    private ShojaSynergies() {}

    static final NamespacedKey HEAL_SHOT = new NamespacedKey("crossfight", "shoja_heal_shot");
    /** Pardon Temporaire : joueurs dont les coups enflamment -> expiration. */
    static final Map<UUID, Long> FIRE_HITS = new HashMap<>();

    static Player byCharacter(Player a, Player b, String id) { return id.equals(Shoja.characterId(a)) ? a : b; }

    static void register() {
        Synergy.register("hiro", "pinkou", new Synergy.Effect() {
            public String name() { return "Pardon Temporaire"; }
            public String description() { return "Tous les ennemis a 50 blocs sont teleportes dans une cage en bois pendant 20 secondes ; le duo y a Fire Aspect et Regeneration."; }
            public boolean cast(Player a, Player b) {
                Player pinkou = byCharacter(a, b, "pinkou");
                World w = pinkou.getWorld();
                Location c = pinkou.getLocation().clone();
                int gy = Effects.groundAround(w, c.getBlockX(), c.getBlockZ(), c.getBlockY());
                if (gy != Integer.MIN_VALUE) c.setY(gy + 1);
                TempBlocks cage = new TempBlocks().unbreakable();
                for (int dx = -5; dx <= 5; dx++) for (int dy = -1; dy <= 5; dy++) for (int dz = -5; dz <= 5; dz++) {
                    boolean shell = Math.abs(dx) == 5 || Math.abs(dz) == 5 || dy == -1 || dy == 5;
                    if (!shell) continue;
                    var blk = w.getBlockAt(c.getBlockX() + dx, c.getBlockY() + dy, c.getBlockZ() + dz);
                    Material m = dy == -1 ? Material.OAK_PLANKS : (dy == 5 ? Material.OAK_SLAB : ((dx + dz) % 2 == 0 ? Material.OAK_FENCE : Material.OAK_PLANKS));
                    if (!cage.place(blk, m)) cage.replace(blk, m);
                }
                int n = 0;
                for (LivingEntity e : Effects.enemiesWithin(pinkou, 50)) {
                    double ang = n * 1.3; n++;
                    e.teleport(new Location(w, c.getBlockX() + 0.5 + Math.cos(ang) * 2.5, c.getBlockY(), c.getBlockZ() + 0.5 + Math.sin(ang) * 2.5));
                    Effects.say(e, "<light_purple>Pardon Temporaire : enferme 20 s !");
                }
                for (Player p : new Player[]{a, b}) {
                    p.teleport(new Location(w, c.getBlockX() + 0.5 + (p == a ? -1 : 1), c.getBlockY(), c.getBlockZ() + 0.5));
                    FIRE_HITS.put(p.getUniqueId(), System.currentTimeMillis() + 20000L);
                    Msg.send(p, "<light_purple>Pardon Temporaire : " + n + " ennemi(s) dans la cage, Regeneration et coups enflammes 20 s.");
                }
                w.playSound(c, Sound.BLOCK_WOOD_PLACE, 1f, 0.6f);
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        t++;
                        if (t > 20) { cage.restore(); w.playSound(c, Sound.BLOCK_WOOD_BREAK, 1f, 0.8f); cancel(); return; }
                        for (Player p : new Player[]{a, b}) if (p.isOnline() && p.getWorld() == w && p.getLocation().distance(c) < 8) Effects.give(p, PotionEffectType.REGENERATION, 1, 30);
                    }
                }.runTaskTimer(Shoja.plugin(), 0L, 20L);
                return true;
            }
        });

        Synergy.register("hiro", "aida", new Synergy.Effect() {
            public String name() { return "Bulles Bouillantes"; }
            public String description() { return "10 bulles bouillantes a 50 blocs pendant 1 minute : l'adversaire qui en touche une est enferme 5 secondes (1 coeur par seconde) ; les autres explosent a la fin."; }
            public boolean cast(Player a, Player b) {
                Player aida = byCharacter(a, b, "aida");
                World w = aida.getWorld();
                Location c = aida.getLocation();
                List<Location> bubbles = new ArrayList<>();
                for (int i = 0; i < 10; i++) {
                    double ang = Math.random() * Math.PI * 2, d = 4 + Math.random() * 46;
                    int x = c.getBlockX() + (int) (Math.cos(ang) * d), z = c.getBlockZ() + (int) (Math.sin(ang) * d);
                    int gy = Effects.groundAround(w, x, z, c.getBlockY());
                    if (gy == Integer.MIN_VALUE) gy = w.getHighestBlockYAt(x, z);
                    bubbles.add(new Location(w, x + 0.5, gy + 2, z + 0.5));
                }
                Particle.DustOptions hot = new Particle.DustOptions(Color.fromRGB(255, 120, 90), 1.0f);
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        t++;
                        if (t > 1200) { for (Location l : bubbles) Shoja.explosion(aida, l, 3, 4.0, true); cancel(); return; }
                        for (Location l : new ArrayList<>(bubbles)) {
                            if (t % 2 == 0) Aida.sphere(w, l, 1.2, 20, t * 0.1);
                            w.spawnParticle(Particle.DUST, l, 1, 0.5, 0.5, 0.5, 0, hot);
                            for (LivingEntity e : Effects.within(l, 1.4, le -> Effects.isEnemy(aida, le))) {
                                bubbles.remove(l);
                                Effects.root(e, 100);
                                w.playSound(l, Sound.ENTITY_PLAYER_SPLASH, 1f, 0.8f);
                                Effects.say(e, "<red>Bulle bouillante : enferme 5 s !");
                                new BukkitRunnable() { int k = 0; @Override public void run() { k++; if (k > 100 || e.isDead()) { cancel(); return; } Aida.sphere(w, e.getLocation().add(0, 1, 0), 1.3, 30, k * 0.1); if (k % 20 == 0) { e.setNoDamageTicks(0); Effects.damage(e, 2.0, aida); } } }.runTaskTimer(Shoja.plugin(), 0L, 1L);
                                break;
                            }
                        }
                    }
                }.runTaskTimer(Shoja.plugin(), 0L, 1L);
                for (Player p : new Player[]{a, b}) Msg.send(p, "<aqua>Bulles Bouillantes : 10 bulles posees a 50 blocs.");
                return true;
            }
        });

        Synergy.register("hiro", "crystal", new Synergy.Effect() {
            public String name() { return "Emprunt Douloureux"; }
            public String description() { return "Pendant 10 secondes, Crystal perd 1 coeur par seconde, mais tous les ennemis a 50 blocs perdent aussi 1 coeur par seconde et brulent."; }
            public boolean cast(Player a, Player b) {
                Player crystal = byCharacter(a, b, "crystal");
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        t++;
                        if (!crystal.isOnline() || t > 10) { cancel(); return; }
                        Shoja.selfLoss(crystal, 2.0);
                        for (LivingEntity e : Effects.enemiesWithin(crystal, 50)) { e.setNoDamageTicks(0); Effects.damage(e, 2.0, crystal); e.setFireTicks(Math.max(e.getFireTicks(), 40)); }
                        crystal.getWorld().spawnParticle(Particle.FLAME, crystal.getLocation().add(0, 1, 0), 15, 0.4, 0.6, 0.4, 0.03);
                    }
                }.runTaskTimer(Shoja.plugin(), 0L, 20L);
                for (Player p : new Player[]{a, b}) Msg.send(p, "<gold>Emprunt Douloureux : 10 s.");
                return true;
            }
        });

        Synergy.register("pinkou", "aida", new Synergy.Effect() {
            public String name() { return "Tir Curatif"; }
            public String description() { return "Pendant 20 secondes, Pinkou et Aida tirent toutes les 2 secondes devant elles : le tir rend 2 coeurs a la tireuse et donne Lenteur a l'adversaire touche."; }
            public boolean cast(Player a, Player b) {
                for (Player p : new Player[]{a, b}) {
                    new BukkitRunnable() {
                        int t = 0;
                        @Override public void run() {
                            t++;
                            if (!p.isOnline() || p.isDead() || t > 10) { cancel(); return; }
                            Arrow ar = Bullets.shoot(p, p.getEyeLocation().getDirection(), 2.5, 1.0, Material.PINK_CONCRETE, Color.fromRGB(255, 160, 210));
                            ar.getPersistentDataContainer().set(HEAL_SHOT, PersistentDataType.BYTE, (byte) 1);
                            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.6f);
                        }
                    }.runTaskTimer(Shoja.plugin(), 0L, 40L);
                    Msg.send(p, "<light_purple>Tir Curatif : un tir toutes les 2 s pendant 20 s.");
                }
                return true;
            }
        });

        Synergy.register("pinkou", "crystal", new Synergy.Effect() {
            public String name() { return "Souffrance Abregee"; }
            public String description() { return "Pendant 1 minute, Crystal ne perd plus de vie en utilisant ses pouvoirs."; }
            public boolean cast(Player a, Player b) {
                Player crystal = byCharacter(a, b, "crystal");
                Crystal.NO_LOSS.put(crystal.getUniqueId(), System.currentTimeMillis() + 60000L);
                crystal.getWorld().spawnParticle(Particle.HEART, crystal.getLocation().add(0, 1.5, 0), 15, 0.5, 0.5, 0.5);
                for (Player p : new Player[]{a, b}) Msg.send(p, "<light_purple>Souffrance Abregee : Crystal ne paie plus ses pouvoirs pendant 1 minute.");
                return true;
            }
        });

        Synergy.register("aida", "crystal", new Synergy.Effect() {
            public String name() { return "Amitie Eternelle"; }
            public String description() { return "Pendant 30 secondes, Crystal ne prend plus de degats des coups des adversaires."; }
            public boolean cast(Player a, Player b) {
                Player crystal = byCharacter(a, b, "crystal");
                Crystal.NO_HIT.put(crystal.getUniqueId(), System.currentTimeMillis() + 30000L);
                crystal.getWorld().spawnParticle(Particle.DUST, crystal.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0, Aida.WATER);
                for (Player p : new Player[]{a, b}) Msg.send(p, "<aqua>Amitie Eternelle : Crystal est intouchable 30 s.");
                return true;
            }
        });
    }
}
