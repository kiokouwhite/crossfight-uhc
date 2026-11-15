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
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les six Ultimes en Duo de RWBY. La partenaire du lanceur peut etre un joueur ou, dans la zone duo
 * du mode test, un mannequin duo a l'image du personnage : les roles sont des entites vivantes, et ce
 * qui n'a de sens que pour un joueur (furtivite, chute amortie) ne s'applique qu'aux joueurs.
 */
final class DuoUltimates {

    private DuoUltimates() {}

    /** Joueurs sous "Weiss et Blake" : leurs coups immobilisent 5 secondes. Valeur = fin en millisecondes. */
    static final Map<UUID, Long> FREEZING_HITS = new HashMap<>();

    private static LivingEntity of(LivingEntity a, LivingEntity b, String id) {
        if (RWBY.characterId(a).equals(id)) return a;
        if (RWBY.characterId(b).equals(id)) return b;
        return null;
    }

    static String name(LivingEntity a, LivingEntity b) { return name(RWBY.characterId(a), RWBY.characterId(b)); }

    /** Le nom de l'attaque d'equipe de la paire, comme dans RWBY (Ruby et Yang n'en ont pas dans la serie : celui de leur duo). */
    static String name(String x, String y) {
        java.util.Set<String> pair = java.util.Set.of(x, y);
        if (pair.equals(java.util.Set.of("ruby", "weiss"))) return "Ice Flower";
        if (pair.equals(java.util.Set.of("ruby", "yang"))) return "Strawberry Sunrise";
        if (pair.equals(java.util.Set.of("ruby", "blake"))) return "Ladybug";
        if (pair.equals(java.util.Set.of("weiss", "yang"))) return "Freezerburn";
        if (pair.equals(java.util.Set.of("weiss", "blake"))) return "Checkmate";
        if (pair.equals(java.util.Set.of("blake", "yang"))) return "Bumblebee";
        return "l'Ultime en Duo";
    }

    static String describe(LivingEntity a, LivingEntity b) { return describe(RWBY.characterId(a), RWBY.characterId(b)); }

    static String describe(String x, String y) {
        java.util.Set<String> pair = java.util.Set.of(x, y);
        if (pair.equals(java.util.Set.of("ruby", "weiss"))) return "Patinoire de 50 blocs, Vitesse III pour les allies";
        if (pair.equals(java.util.Set.of("ruby", "yang"))) return "Yang projette Ruby sur 10 blocs, 5 coeurs autour d'elle";
        if (pair.equals(java.util.Set.of("ruby", "blake"))) return "Invisibles et Vitesse III pendant 30 s";
        if (pair.equals(java.util.Set.of("weiss", "yang"))) return "3 piques de glace qui enflamment et ralentissent";
        if (pair.equals(java.util.Set.of("weiss", "blake"))) return "Invisibles 10 s, chaque coup immobilise 5 s";
        if (pair.equals(java.util.Set.of("blake", "yang"))) return "3 clones de Yang qui explosent (3 coeurs)";
        return "";
    }

    static boolean cast(Player a, LivingEntity b) {
        LivingEntity ruby = of(a, b, "ruby"), weiss = of(a, b, "weiss"), yang = of(a, b, "yang"), blake = of(a, b, "blake");
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
    /** Ice Flower : le temps du geste de Weiss avant que l'epee touche le sol, puis celui que met la glace a s'etendre. */
    static final int PLANT = 6, SPREAD = 30;
    static final Particle.DustOptions PETAL = new Particle.DustOptions(Color.fromRGB(205, 24, 44), 1.3f),
            PETAL_DARK = new Particle.DustOptions(Color.fromRGB(140, 10, 30), 1.0f);

    /**
     * Weiss leve son epee et la plante dans le sol : la glace part de la lame et s'etend jusqu'a 25 blocs. Ruby
     * file autour de la patinoire a mesure qu'elle grandit, a toute vitesse, en semant des petales de rose qui
     * restent en couronne sur son bord. Elle revient ensuite d'ou elle est partie.
     */
    static boolean patinoire(LivingEntity weiss, LivingEntity ruby) {
        World w = weiss.getWorld();
        Location c = weiss.getLocation();
        TempBlocks rink = new TempBlocks();
        int radius = 25;
        // Les blocs de la patinoire, du centre vers le bord.
        List<Block> blocks = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) continue;
                int bx = c.getBlockX() + x, bz = c.getBlockZ() + z;
                int y = Effects.groundAround(w, bx, bz, c.getBlockY());
                if (y == Integer.MIN_VALUE) continue;
                Block top = w.getBlockAt(bx, y, bz);
                if (top.getType() == Material.WATER || top.getType() == Material.LAVA || top.getType().isSolid()) blocks.add(top);
            }
        }
        blocks.sort(java.util.Comparator.comparingDouble(bl -> bl.getLocation().add(0.5, 0, 0.5).distanceSquared(new Location(w, c.getX(), bl.getY(), c.getZ()))));

        // Weiss : l'epee levee des deux mains, puis plantee, un genou a terre, tant que la glace s'etend.
        ItemStack sword = weiss instanceof Player wp ? Weiss.sword(wp) : null;
        com.kiokouwhite.crossfight.util.Puppet wpu = com.kiokouwhite.crossfight.util.Puppet.show(weiss, "weiss", sword != null ? sword : new ItemStack(Material.IRON_SWORD), "debout");
        if (wpu != null) { wpu.pose("weiss_plante_leve", 3); wpu.endAfter(PLANT + SPREAD + 6, 5); }
        Effects.root(weiss, PLANT + SPREAD);

        // Ruby : couchee dans sa course, elle tourne autour de la glace. Elle garde son regard et retrouve sa place a la fin.
        Location rubyStart = ruby.getLocation();
        com.kiokouwhite.crossfight.util.Puppet rpu = com.kiokouwhite.crossfight.util.Puppet.show(ruby, "ruby", null, "ruby_petales_vol");
        if (rpu != null) rpu.endAfter(PLANT + SPREAD + 4, 4);
        if (ruby instanceof Player rp) Ruby.noFallUntil(rp, (PLANT + SPREAD) * 50L + 4000);
        double startAngle = Math.atan2(rubyStart.getZ() - c.getZ(), rubyStart.getX() - c.getX());
        w.playSound(c, Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 0.7f);

        final Player side = Effects.sideOf(weiss);
        new BukkitRunnable() {
            int t = 0, placed = 0;
            Location last = rubyStart.clone();
            @Override public void run() {
                t++;
                if (t == 3 && wpu != null && com.kiokouwhite.crossfight.util.Puppet.of(weiss) == wpu) wpu.pose("weiss_plante", 3);
                if (t == PLANT) {
                    w.playSound(c, Sound.BLOCK_GLASS_PLACE, 1f, 0.6f);
                    w.playSound(c, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 0.8f);
                    w.playSound(c, Sound.ITEM_TRIDENT_HIT_GROUND, 1f, 0.6f);
                    w.spawnParticle(Particle.SNOWFLAKE, c.clone().add(0, 0.3, 0), 60, 0.6, 0.2, 0.6, 0.12);
                }
                if (t < PLANT) return;
                double k = Math.min(1.0, (t - PLANT) / (double) SPREAD), reach = radius * k;
                // La glace gagne du terrain a partir de la lame.
                while (placed < blocks.size()) {
                    Block bl = blocks.get(placed);
                    double dx = bl.getX() + 0.5 - c.getX(), dz = bl.getZ() + 0.5 - c.getZ();
                    if (dx * dx + dz * dz > reach * reach) break;
                    rink.replace(bl, Material.PACKED_ICE);
                    placed++;
                }
                if (t % 3 == 0 && k < 1) {
                    for (int i = 0; i < 24; i++) {
                        double a = i * Math.PI / 12 + t * 0.2;
                        w.spawnParticle(Particle.SNOWFLAKE, c.clone().add(Math.cos(a) * reach, 0.3, Math.sin(a) * reach), 1, 0.2, 0.1, 0.2, 0.01);
                    }
                    w.playSound(c, Sound.BLOCK_GLASS_STEP, 0.8f, 0.7f + (float) k * 0.8f);
                }
                // Ruby court sur le bord de la glace : pres de trois tours le temps qu'elle s'etende.
                if (k < 1 && ruby.isValid() && !ruby.isDead()) {
                    double r = Math.max(2.5, reach + 0.8), a = startAngle + (t - PLANT) * 0.6;
                    Location at = new Location(w, c.getX() + Math.cos(a) * r, c.getY(), c.getZ() + Math.sin(a) * r);
                    int gy = Effects.groundAround(w, at.getBlockX(), at.getBlockZ(), c.getBlockY());
                    at.setY(gy == Integer.MIN_VALUE ? c.getY() : gy + 1);
                    at.setYaw(ruby.getLocation().getYaw());
                    at.setPitch(ruby.getLocation().getPitch());
                    if (at.getBlock().isPassable()) {
                        ruby.teleport(at);
                        ruby.setFallDistance(0);
                        // Le pantin regarde ou elle court (la tangente du cercle).
                        if (rpu != null && com.kiokouwhite.crossfight.util.Puppet.of(ruby) == rpu) rpu.face((float) Math.toDegrees(Math.atan2(-(-Math.sin(a)), Math.cos(a))));
                        petals(last, at);
                        last = at.clone();
                    }
                    if (t % 4 == 0) w.playSound(at, Sound.ENTITY_PHANTOM_FLAP, 0.7f, 1.8f);
                }
                if (t == PLANT + SPREAD) {
                    // La patinoire est faite : Ruby revient a sa place dans un dernier tourbillon de petales.
                    if (ruby.isValid() && !ruby.isDead()) {
                        w.spawnParticle(Particle.DUST, ruby.getLocation().add(0, 1, 0), 30, 0.5, 0.7, 0.5, 0, PETAL);
                        ruby.teleport(rubyStart);
                        ruby.setFallDistance(0);
                        w.spawnParticle(Particle.DUST, rubyStart.clone().add(0, 1, 0), 30, 0.5, 0.7, 0.5, 0, PETAL);
                        w.spawnParticle(Particle.CHERRY_LEAVES, rubyStart.clone().add(0, 1.6, 0), 20, 0.6, 0.5, 0.6, 0);
                    }
                    w.playSound(c, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.7f);
                }
                if (t >= PLANT + SPREAD && (t - PLANT - SPREAD) % 20 == 0) {
                    int seconds = (t - PLANT - SPREAD) / 20;
                    if (seconds >= 30) { rink.restore(); cancel(); return; }
                    for (Player p : w.getPlayers()) {
                        if (side == null || !(p == side || Effects.isAlly(side, p))) continue;
                        Block under = p.getLocation().subtract(0, 0.1, 0).getBlock();
                        if (rink.contains(under)) Effects.refresh(p, PotionEffectType.SPEED, 2, 45);
                    }
                    // La couronne de petales reste sur le bord de la patinoire.
                    for (int i = 0; i < 40; i++) {
                        double a = Math.random() * Math.PI * 2;
                        Location at = c.clone().add(Math.cos(a) * (radius + 0.5), 0.4 + Math.random() * 1.4, Math.sin(a) * (radius + 0.5));
                        w.spawnParticle(Particle.DUST, at, 1, 0.2, 0.2, 0.2, 0, i % 3 == 0 ? PETAL_DARK : PETAL);
                        if (i % 4 == 0) w.spawnParticle(Particle.CHERRY_LEAVES, at.clone().add(0, 0.8, 0), 1, 0.3, 0.2, 0.3, 0);
                    }
                }
            }
        }.runTaskTimer(CrossfightPlugin.get(), 1L, 1L);
        return true;
    }

    /** Les petales de rose que Ruby laisse derriere elle, d'un point de sa course au suivant. */
    private static void petals(Location from, Location to) {
        World w = to.getWorld();
        Vector step = to.toVector().subtract(from.toVector());
        double length = step.length();
        if (length < 0.1 || length > 40) return;
        int n = (int) Math.min(30, Math.ceil(length / 0.7));
        for (int i = 0; i < n; i++) {
            Location at = from.clone().add(step.clone().multiply((i + Math.random()) / n)).add(0, 0.3 + Math.random() * 1.3, 0);
            w.spawnParticle(Particle.DUST, at, 1, 0.15, 0.15, 0.15, 0, i % 3 == 0 ? PETAL_DARK : PETAL);
            if (i % 3 == 0) w.spawnParticle(Particle.CHERRY_LEAVES, at.clone().add(0, 0.5, 0), 1, 0.2, 0.2, 0.2, 0);
        }
    }

    // ------------------------------------------------------------- Ruby + Yang

    /** Yang projette Ruby sur 10 blocs ; Ruby detruit ce qu'elle traverse et inflige 5 coeurs autour d'elle. */
    static boolean lancerDeRose(LivingEntity yang, LivingEntity ruby) {
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
                if (ticks > 24 || !ruby.isValid()) { cancel(); return; }
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
                for (LivingEntity enemy : Effects.enemiesAround(ruby, 3.0)) {
                    if (hit.contains(enemy.getUniqueId())) continue;
                    hit.add(enemy.getUniqueId());
                    Effects.damage(enemy, 10.0, ruby);
                    w.playSound(enemy.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
                }
                if (ticks < 12) ruby.setVelocity(fDir.clone().multiply(1.2).setY(ruby.getVelocity().getY()));
            }
        }.runTaskTimer(CrossfightPlugin.get(), 1L, 1L);
        if (ruby instanceof Player rp) Ruby.noFallUntil(rp, 4000);
        return true;
    }

    // ------------------------------------------------------------ Ruby + Blake

    /** Les deux deviennent invisibles avec Vitesse III pendant 30 secondes. */
    static boolean ombreRouge(LivingEntity ruby, LivingEntity blake) {
        for (LivingEntity p : new LivingEntity[]{ruby, blake}) {
            Effects.give(p, PotionEffectType.SPEED, 2, 20 * 30);
            Effects.give(p, PotionEffectType.INVISIBILITY, 0, 20 * 30);
            p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.02);
            if (!(p instanceof Player pl)) continue;
            Stealth.hideFromAll(pl, "duo_rb", v -> Effects.isEnemy(pl, v));
            Stealth.hideArmor(pl);
            Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> { Stealth.reveal(pl, "duo_rb"); Stealth.showArmor(pl); }, 20L * 30);
        }
        ruby.getWorld().playSound(ruby.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 0.8f);
        return true;
    }

    // ------------------------------------------------------------ Weiss + Yang

    /** Trois piques de glace de 3x3 a moins de 10 blocs ; enflamment et ralentissent a 2 blocs. 30 secondes. */
    static boolean piquesDeGlace(LivingEntity weiss, LivingEntity yang) {
        World w = weiss.getWorld();
        List<Location> centers = new ArrayList<>();
        List<LivingEntity> enemies = Effects.enemiesAround(weiss, 10);
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
                    for (LivingEntity enemy : Effects.enemiesAround(weiss, c.clone().add(0.5, 1, 0.5), 2.0 + 1.5)) {
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
    static boolean gelSilencieux(LivingEntity weiss, LivingEntity blake) {
        long until = System.currentTimeMillis() + 10_000L;
        for (LivingEntity p : new LivingEntity[]{weiss, blake}) {
            FREEZING_HITS.put(p.getUniqueId(), until);
            Effects.give(p, PotionEffectType.INVISIBILITY, 0, 20 * 10);
            p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.02);
            if (p instanceof Player pl) {
                Stealth.hideFromAll(pl, "duo_wb", v -> Effects.isEnemy(pl, v));
                Stealth.hideArmor(pl);
            }
            Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> {
                if (p instanceof Player pl) { Stealth.reveal(pl, "duo_wb"); Stealth.showArmor(pl); }
                FREEZING_HITS.remove(p.getUniqueId());
            }, 20L * 10);
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
    static boolean leurresExplosifs(LivingEntity yang, LivingEntity blake) {
        Player owner = Effects.sideOf(yang);
        if (owner == null) return false;
        List<LivingEntity> enemies = Effects.enemiesAround(yang, 40);
        enemies.sort(java.util.Comparator.comparingDouble(e -> e.getLocation().distanceSquared(yang.getLocation())));
        for (int i = 0; i < 3; i++) {
            LivingEntity target = i < enemies.size() ? enemies.get(i) : (enemies.isEmpty() ? null : enemies.get(i % enemies.size()));
            Clones.spawn(owner, yang, target, 20 * 20, (clone, attacker) -> {
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
