package com.kiokouwhite.crossfight.character.rwby;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.Clones;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Stealth;
import com.kiokouwhite.crossfight.util.TempBlocks;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les six Ultimes en Duo de RWBY.
 */
final class DuoUltimates {

    private DuoUltimates() {}

    /** Joueurs sous "Weiss et Blake" : leurs coups immobilisent 5 secondes. Valeur = fin en millisecondes. */
    static final Map<UUID, Long> FREEZING_HITS = new HashMap<>();

    private static Player of(Player a, Player b, String id) {
        if (RWBY.characterId(a).equals(id)) return a;
        if (RWBY.characterId(b).equals(id)) return b;
        return null;
    }

    static String name(Player a, Player b) {
        String x = RWBY.characterId(a), y = RWBY.characterId(b);
        java.util.Set<String> pair = java.util.Set.of(x, y);
        if (pair.equals(java.util.Set.of("ruby", "weiss"))) return "la Patinoire";
        if (pair.equals(java.util.Set.of("ruby", "yang"))) return "le Lancer de Rose";
        if (pair.equals(java.util.Set.of("ruby", "blake"))) return "l'Ombre Rouge";
        if (pair.equals(java.util.Set.of("weiss", "yang"))) return "les Piques de Glace";
        if (pair.equals(java.util.Set.of("weiss", "blake"))) return "le Gel Silencieux";
        if (pair.equals(java.util.Set.of("blake", "yang"))) return "les Leurres Explosifs";
        return "l'Ultime en Duo";
    }

    static String describe(Player a, Player b) {
        String x = RWBY.characterId(a), y = RWBY.characterId(b);
        java.util.Set<String> pair = java.util.Set.of(x, y);
        if (pair.equals(java.util.Set.of("ruby", "weiss"))) return "Patinoire de 50 blocs, Vitesse III pour les allies";
        if (pair.equals(java.util.Set.of("ruby", "yang"))) return "Yang projette Ruby sur 10 blocs, 5 coeurs autour d'elle";
        if (pair.equals(java.util.Set.of("ruby", "blake"))) return "Invisibles et Vitesse III pendant 30 s";
        if (pair.equals(java.util.Set.of("weiss", "yang"))) return "3 piques de glace qui enflamment et ralentissent";
        if (pair.equals(java.util.Set.of("weiss", "blake"))) return "Invisibles 10 s, chaque coup immobilise 5 s";
        if (pair.equals(java.util.Set.of("blake", "yang"))) return "3 clones de Yang qui explosent (3 coeurs)";
        return "";
    }

    static boolean cast(Player a, Player b) {
        Player ruby = of(a, b, "ruby"), weiss = of(a, b, "weiss"), yang = of(a, b, "yang"), blake = of(a, b, "blake");
        if (ruby != null && weiss != null) return patinoire(weiss, ruby);
        if (ruby != null && yang != null) return lancerDeRose(yang, ruby);
        if (ruby != null && blake != null) return ombreRouge(ruby, blake);
        if (weiss != null && yang != null) return piquesDeGlace(weiss, yang);
        if (weiss != null && blake != null) return gelSilencieux(weiss, blake);
        if (blake != null && yang != null) return leurresExplosifs(yang, blake);
        Msg.actionBar(a, "<red>Cette paire n'a pas d'Ultime en Duo.");
        return false;
    }

    // ------------------------------------------------------------ Ruby + Weiss

    /** Patinoire de 50 blocs de large : les allies dessus ont Vitesse III. 30 secondes. */
    static boolean patinoire(Player weiss, Player ruby) {
        World w = weiss.getWorld();
        Location c = weiss.getLocation();
        TempBlocks rink = new TempBlocks();
        int radius = 25;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) continue;
                int bx = c.getBlockX() + x, bz = c.getBlockZ() + z;
                int y = Effects.groundAround(w, bx, bz, c.getBlockY());
                if (y == Integer.MIN_VALUE) continue;
                Block top = w.getBlockAt(bx, y, bz);
                if (top.getType() == Material.WATER || top.getType() == Material.LAVA) { rink.replace(top, Material.PACKED_ICE); continue; }
                if (top.getType().isSolid()) rink.replace(top, Material.PACKED_ICE);
            }
        }
        w.playSound(c, Sound.BLOCK_GLASS_PLACE, 1f, 0.6f);
        w.playSound(c, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 0.8f);
        w.spawnParticle(Particle.SNOWFLAKE, c.clone().add(0, 1, 0), 200, radius / 2.0, 1, radius / 2.0, 0.02);
        new BukkitRunnable() {
            int seconds = 0;
            @Override public void run() {
                if (seconds++ >= 30) { rink.restore(); cancel(); return; }
                for (Player p : w.getPlayers()) {
                    if (!(p == weiss || Effects.isAlly(weiss, p))) continue;
                    Block under = p.getLocation().subtract(0, 0.1, 0).getBlock();
                    if (rink.contains(under)) Effects.refresh(p, PotionEffectType.SPEED, 2, 45);
                }
            }
        }.runTaskTimer(CrossfightPlugin.get(), 0L, 20L);
        return true;
    }

    // ------------------------------------------------------------- Ruby + Yang

    /** Yang projette Ruby sur 10 blocs ; Ruby detruit ce qu'elle traverse et inflige 5 coeurs autour d'elle. */
    static boolean lancerDeRose(Player yang, Player ruby) {
        Vector dir = yang.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
        dir.normalize();
        ruby.teleport(yang.getLocation().add(dir.clone().multiply(0.6)).setDirection(dir));
        ruby.setVelocity(dir.clone().multiply(2.4).setY(0.35));
        World w = yang.getWorld();
        w.playSound(yang.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.4f);
        w.spawnParticle(Particle.EXPLOSION, yang.getLocation().add(0, 1, 0), 1);
        List<UUID> hit = new ArrayList<>();
        final Vector fDir = dir;
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                ticks++;
                if (ticks > 24 || !ruby.isOnline()) { cancel(); return; }
                Location at = ruby.getLocation();
                w.spawnParticle(Particle.CHERRY_LEAVES, at.clone().add(0, 1, 0), 10, 0.6, 0.6, 0.6, 0.05);
                w.spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 8, 0.5, 0.5, 0.5, 0, new Particle.DustOptions(Color.fromRGB(220, 20, 60), 1.5f));
                // Ruby detruit ce qu'elle traverse (sauf les blocs indestructibles ou proteges).
                for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 2; dy++) for (int dz = -1; dz <= 1; dz++) {
                    Block b = at.clone().add(dx, dy, dz).getBlock();
                    Material t = b.getType();
                    if (t.isAir() || t == Material.BEDROCK || t == Material.OBSIDIAN || t == Material.BARRIER || t == Material.WATER || t == Material.LAVA) continue;
                    if (TempBlocks.isProtected(b)) continue;
                    w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0, b.getBlockData());
                    b.setType(Material.AIR, false);
                }
                for (LivingEntity enemy : Effects.enemiesWithin(ruby, 3.0)) {
                    if (hit.contains(enemy.getUniqueId())) continue;
                    hit.add(enemy.getUniqueId());
                    Effects.damage(enemy, 10.0, ruby);
                    w.playSound(enemy.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
                }
                if (ticks < 12) ruby.setVelocity(fDir.clone().multiply(1.2).setY(ruby.getVelocity().getY()));
            }
        }.runTaskTimer(CrossfightPlugin.get(), 1L, 1L);
        Ruby.noFallUntil(ruby, 4000);
        return true;
    }

    // ------------------------------------------------------------ Ruby + Blake

    /** Les deux deviennent invisibles avec Vitesse III pendant 30 secondes. */
    static boolean ombreRouge(Player ruby, Player blake) {
        for (Player p : new Player[]{ruby, blake}) {
            Effects.give(p, PotionEffectType.SPEED, 2, 20 * 30);
            Effects.give(p, PotionEffectType.INVISIBILITY, 0, 20 * 30);
            Stealth.hideFromAll(p, "duo_rb", v -> Effects.isEnemy(p, v));
            Stealth.hideArmor(p);
            p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.02);
            Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> { Stealth.reveal(p, "duo_rb"); Stealth.showArmor(p); }, 20L * 30);
        }
        ruby.getWorld().playSound(ruby.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 0.8f);
        return true;
    }

    // ------------------------------------------------------------ Weiss + Yang

    /** Trois piques de glace de 3x3 a moins de 10 blocs ; enflamment et ralentissent a 2 blocs. 30 secondes. */
    static boolean piquesDeGlace(Player weiss, Player yang) {
        World w = weiss.getWorld();
        List<Location> centers = new ArrayList<>();
        List<LivingEntity> enemies = Effects.enemiesWithin(weiss, 10);
        for (int i = 0; i < 3; i++) {
            Location c;
            if (i < enemies.size()) c = enemies.get(i).getLocation().add(Math.random() * 3 - 1.5, 0, Math.random() * 3 - 1.5);
            else {
                double a = Math.random() * Math.PI * 2, r = 3 + Math.random() * 7;
                c = weiss.getLocation().add(Math.cos(a) * r, 0, Math.sin(a) * r);
            }
            int y = Effects.groundAround(w, c.getBlockX(), c.getBlockZ(), c.getBlockY());
            if (y == Integer.MIN_VALUE) y = c.getBlockY() - 1;
            centers.add(new Location(w, c.getBlockX(), y + 1, c.getBlockZ()));
        }
        TempBlocks spikes = new TempBlocks();
        for (Location c : centers) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                spikes.place(c.clone().add(dx, 0, dz).getBlock(), Material.PACKED_ICE);
                spikes.place(c.clone().add(dx, 1, dz).getBlock(), Material.PACKED_ICE);
                if (dx == 0 || dz == 0) spikes.place(c.clone().add(dx, 2, dz).getBlock(), Material.BLUE_ICE);
            }
            spikes.place(c.clone().add(0, 3, 0).getBlock(), Material.BLUE_ICE);
            spikes.place(c.clone().add(0, 4, 0).getBlock(), Material.ICE);
            w.spawnParticle(Particle.SNOWFLAKE, c.clone().add(0.5, 2, 0.5), 40, 1, 2, 1, 0.02);
        }
        w.playSound(weiss.getLocation(), Sound.BLOCK_GLASS_PLACE, 1f, 0.5f);
        w.playSound(weiss.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 0.6f);
        new BukkitRunnable() {
            int seconds = 0;
            @Override public void run() {
                if (seconds++ >= 30) { spikes.restore(); cancel(); return; }
                for (Location c : centers) {
                    for (LivingEntity enemy : Effects.enemiesWithin(weiss, c.clone().add(0.5, 1, 0.5), 2.0 + 1.5)) {
                        enemy.setFireTicks(Math.max(enemy.getFireTicks(), 100));
                        Effects.give(enemy, PotionEffectType.SLOWNESS, 0, 100);
                    }
                    w.spawnParticle(Particle.SNOWFLAKE, c.clone().add(0.5, 3, 0.5), 5, 0.6, 1, 0.6, 0.01);
                }
            }
        }.runTaskTimer(CrossfightPlugin.get(), 0L, 20L);
        return true;
    }

    // ----------------------------------------------------------- Weiss + Blake

    /** Invisibles 10 secondes ; chaque adversaire touche est immobilise 5 secondes. */
    static boolean gelSilencieux(Player weiss, Player blake) {
        long until = System.currentTimeMillis() + 10_000L;
        for (Player p : new Player[]{weiss, blake}) {
            FREEZING_HITS.put(p.getUniqueId(), until);
            Effects.give(p, PotionEffectType.INVISIBILITY, 0, 20 * 10);
            Stealth.hideFromAll(p, "duo_wb", v -> Effects.isEnemy(p, v));
            Stealth.hideArmor(p);
            p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.02);
            Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> { Stealth.reveal(p, "duo_wb"); Stealth.showArmor(p); FREEZING_HITS.remove(p.getUniqueId()); }, 20L * 10);
        }
        weiss.getWorld().playSound(weiss.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 0.6f);
        return true;
    }

    /** Appele sur les degats : si l'attaquant est sous Gel Silencieux, la victime est immobilisee. */
    static void onFreezingHit(Player attacker, LivingEntity victim) {
        Long until = FREEZING_HITS.get(attacker.getUniqueId());
        if (until == null || System.currentTimeMillis() > until) return;
        Effects.root(victim, 100);
        victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.02);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1f);
        Effects.say(victim, "<aqua>Gele sur place !");
    }

    // ------------------------------------------------------------ Blake + Yang

    /** Trois clones de Yang foncent sur les trois ennemis les plus proches et explosent (3 coeurs) quand on les frappe. */
    static boolean leurresExplosifs(Player yang, Player blake) {
        List<LivingEntity> enemies = Effects.enemiesWithin(yang, 40);
        enemies.sort(java.util.Comparator.comparingDouble(e -> e.getLocation().distanceSquared(yang.getLocation())));
        for (int i = 0; i < 3; i++) {
            LivingEntity target = i < enemies.size() ? enemies.get(i) : (enemies.isEmpty() ? null : enemies.get(i % enemies.size()));
            Clones.spawn(yang, target, 20 * 20, (clone, attacker) -> {
                World w = clone.getWorld();
                w.spawnParticle(Particle.EXPLOSION, clone.getLocation().add(0, 1, 0), 2);
                w.spawnParticle(Particle.FLAME, clone.getLocation().add(0, 1, 0), 40, 0.6, 0.6, 0.6, 0.05);
                w.playSound(clone.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
                Effects.damage(attacker, 6.0, yang);
                attacker.setFireTicks(Math.max(attacker.getFireTicks(), 60));
            });
        }
        yang.getWorld().playSound(yang.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);
        return true;
    }
}
