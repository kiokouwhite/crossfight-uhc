package com.kiokouwhite.crossfight.character.earthdelta;

import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.AbilityType;
import com.kiokouwhite.crossfight.ability.Borrowed;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Bullets;
import com.kiokouwhite.crossfight.util.Clones;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.TempBlocks;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
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
 * Les tomes de Hano : un pouvoir par enchantement.
 */
final class Tomes {

    private Tomes() {}

    enum Tome {
        AQUA_AFFINITY("aqua_affinity", "Prison Aquatique", "Aqua Affinity", Material.WATER_BUCKET, false, -1, "L'adversaire le plus proche (10 blocs) est enferme dans une cage d'eau pendant 6 secondes."),
        BANE("bane_of_arthropods", "Arachnophobie", "Bane of Arthropods", Material.SPIDER_EYE, false, -1, "Trois araignees foncent sur l'adversaire le plus proche pendant 20 secondes."),
        BLAST_PROTECTION("blast_protection", "Explosion Desarmante", "Blast Protection", Material.TNT, false, -1, "Au prochain coup recu, une explosion autour de Hano retire le plastron des adversaires touches."),
        CHANNELING("channeling", "Paratonnerre", "Channeling", Material.LIGHTNING_ROD, false, -1, "Zone d'electricite de 5 blocs pendant 5 secondes : Lenteur et 1 coeur par seconde."),
        DEPTH_STRIDER("depth_strider", "Patinoire Aqueuse", "Depth Strider", Material.PRISMARINE_SHARD, false, -1, "Zone d'eau de 10x10 pendant 10 secondes : les allies y ont Speed."),
        EFFICIENCY("efficiency", "Effondrement Terrestre", "Efficiency", Material.IRON_PICKAXE, false, -1, "L'adversaire le plus proche (10 blocs) tombe dans un trou de 3x3 et 10 blocs de profondeur."),
        FEATHER_FALLING("feather_falling", "Envolee Celeste", "Feather Falling", Material.FEATHER, false, -1, "Hano et l'adversaire le plus proche sont projetes a 10 blocs dans les airs. Hano ne subit jamais de degats de chute."),
        FIRE_ASPECT("fire_aspect", "Magma Torture", "Fire Aspect", Material.LAVA_BUCKET, false, -1, "L'adversaire le plus proche (10 blocs) est enferme dans une bulle de lave pendant 5 secondes."),
        FIRE_PROTECTION("fire_protection", "Armure d'Hades", "Fire Protection", Material.MAGMA_CREAM, false, -1, "Pendant 10 secondes, chaque adversaire qui touche Hano prend feu."),
        FLAME("flame", "Feu du ciel", "Flame", Material.FIRE_CHARGE, false, -1, "Zone de feu de 5x5 sur l'adversaire le plus proche (10 blocs)."),
        FORTUNE("fortune", "Jackpot !", "Fortune", Material.RAW_GOLD, true, -1, "Passif : tous les minerais mines sont triples."),
        IMPALING("impaling", "Empalement", "Impaling", Material.TRIDENT, false, -1, "Hano jette une lance qui immobilise l'adversaire touche pendant 10 secondes."),
        INFINITY("infinity", "Immortalite", "Infinity", Material.TOTEM_OF_UNDYING, true, 1, "Passif, une fois : le coup qui devait tuer Hano lui rend toute sa vie."),
        KNOCKBACK("knockback", "Projection", "Knockback", Material.PISTON, false, -1, "L'adversaire le plus proche (10 blocs) est projete a 10 blocs."),
        LOOTING("looting", "Par ici le butin !", "Looting", Material.GOLDEN_APPLE, true, -1, "Passif : les Pommes Dorees de l'adversaire tue sont doublees dans ses drops."),
        LOYALTY("loyalty", "Lance Affaiblissante", "Loyalty", Material.TRIDENT, false, -1, "Hano lance un trident qui fait l'aller-retour (10 blocs) : Faiblesse III aux adversaires touches."),
        LUCK("luck_of_the_sea", "Comptons sur la chance !", "Luck of the Sea", Material.FISHING_ROD, false, -1, "Hano tire 5 fleches aux effets aleatoires."),
        LURE("lure", "Clone", "Lure", Material.PLAYER_HEAD, false, -1, "Un leurre de Hano se balade au hasard : l'adversaire qui le frappe subit Wither 5 secondes."),
        PROJECTILE_PROTECTION("projectile_protection", "Explosion d'Epines", "Projectile Protection", Material.ARROW, false, -1, "Au prochain coup recu, une explosion de fleches Power IV part de Hano."),
        PROTECTION("protection", "Desarmez les soldats", "Protection", Material.CHAINMAIL_HELMET, false, -1, "L'adversaire le plus proche (10 blocs) perd son casque et ses bottes."),
        RESPIRATION("respiration", "Concentration Ultime", "Respiration", Material.TURTLE_HELMET, false, -1, "Hano reste immobile 10 secondes, puis inflige Wither II a tous les adversaires a 30 blocs."),
        RIPTIDE("riptide", "Dash Poison", "Riptide", Material.NAUTILUS_SHELL, false, -1, "Dash de 10 blocs : Faiblesse IV pendant 10 secondes aux adversaires touches."),
        SHARPNESS("sharpness", "Javelot Desarmant", "Sharpness", Material.IRON_SWORD, false, -1, "Hano lance un javelot : Faiblesse IV a l'adversaire touche."),
        SILK_TOUCH("silk_touch", "Vol Merite", "Silk Touch", Material.BOOK, false, 1, "Une fois : Hano copie une capacite au hasard de l'adversaire le plus proche, jusqu'a la fin de la partie."),
        SMITE("smite", "Apocalypse Now", "Smite", Material.ZOMBIE_HEAD, false, -1, "Trois Husks foncent sur l'adversaire le plus proche pendant 20 secondes."),
        SWEEPING("sweeping_edge", "Triple Slash", "Sweeping Edge", Material.DIAMOND_SWORD, false, -1, "Trois coupures a 2 blocs devant Hano : Wither II pendant 5 secondes aux adversaires touches."),
        THORNS("thorns", "Armure d'Epines", "Thorns", Material.CACTUS, true, -1, "Passif : chaque adversaire qui touche Hano est empoisonne 2 secondes."),
        UNBREAKING("unbreaking", "Brise Glace", "Unbreaking", Material.PACKED_ICE, false, -1, "L'adversaire le plus proche (10 blocs) prend le double des degats pendant 5 secondes.");

        final String id, label, enchant, description; final Material icon; final boolean passive; final int uses;
        Tome(String id, String label, String enchant, Material icon, boolean passive, int uses, String description) {
            this.id = id; this.label = label; this.enchant = enchant; this.icon = icon; this.passive = passive; this.uses = uses; this.description = description;
        }
        static Tome byId(String id) { for (Tome t : values()) if (t.id.equalsIgnoreCase(id)) return t; return null; }
    }

    static final org.bukkit.NamespacedKey SPEAR = new org.bukkit.NamespacedKey("crossfight", "hano_spear");
    static final org.bukkit.NamespacedKey LUCK_ARROW = new org.bukkit.NamespacedKey("crossfight", "hano_luck");

    static World w(Player p) { return p.getWorld(); }

    static LivingEntity nearest(Player p) {
        LivingEntity t = Effects.nearestEnemy(p, 10);
        if (t == null) Msg.actionBar(p, "<red>Aucun adversaire a moins de 10 blocs.");
        return t;
    }

    /** Lance l'actif d'un tome. Retourne false si rien ne s'est passe (pas de cooldown). */
    static boolean use(Player p, CfPlayer cf, AbilityState hano, Tome t) {
        World w = w(p);
        switch (t) {
            case AQUA_AFFINITY -> {
                LivingEntity target = nearest(p); if (target == null) return false;
                TempBlocks cage = new TempBlocks().unbreakable();
                Location c = target.getLocation();
                for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 2; dy++) for (int dz = -1; dz <= 1; dz++) cage.place(w.getBlockAt(c.getBlockX() + dx, c.getBlockY() + dy, c.getBlockZ() + dz), Material.WATER);
                Effects.root(target, 120);
                cage.restoreAfter(120);
                w.playSound(c, Sound.ITEM_BUCKET_EMPTY, 1f, 0.8f);
                Effects.say(target, "<aqua>Prison Aquatique !");
                return true;
            }
            case BANE, SMITE -> {
                LivingEntity target = nearest(p); if (target == null) return false;
                EntityType type = t == Tome.BANE ? EntityType.SPIDER : EntityType.HUSK;
                for (int i = 0; i < 3; i++) {
                    Location at = p.getLocation().add(Math.random() * 2 - 1, 0, Math.random() * 2 - 1);
                    Mob m = (Mob) w.spawnEntity(at, type);
                    m.addScoreboardTag("cf_hano_minion");
                    m.setPersistent(false);
                    m.setGlowing(true);
                    if (m instanceof org.bukkit.entity.Zombie z) z.setShouldBurnInDay(false);
                    m.setTarget(target);
                    new BukkitRunnable() {
                        int tk = 0;
                        @Override public void run() {
                            tk += 10;
                            if (!m.isValid() || tk > 400 || target.isDead()) { if (m.isValid()) { w.spawnParticle(Particle.SMOKE, m.getLocation().add(0, 0.5, 0), 10, 0.3, 0.3, 0.3, 0.02); m.remove(); } cancel(); return; }
                            m.setTarget(target);
                        }
                    }.runTaskTimer(EarthDelta.plugin(), 10L, 10L);
                }
                w.playSound(p.getLocation(), t == Tome.BANE ? Sound.ENTITY_SPIDER_AMBIENT : Sound.ENTITY_HUSK_AMBIENT, 1f, 1f);
                return true;
            }
            case BLAST_PROTECTION -> { hano.set("blast_armed", true); Msg.actionBar(p, "<gold>Explosion Desarmante armee : au prochain coup recu."); return true; }
            case PROJECTILE_PROTECTION -> { hano.set("arrows_armed", true); Msg.actionBar(p, "<gold>Explosion d'Epines armee : au prochain coup recu."); return true; }
            case FIRE_PROTECTION -> { hano.set("hades_until", System.currentTimeMillis() + 10000L); Msg.actionBar(p, "<gold>Armure d'Hades : 10 s"); w.spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 30, 0.5, 0.7, 0.5, 0.02); return true; }
            case CHANNELING -> {
                Location c = p.getLocation().clone();
                new BukkitRunnable() {
                    int tk = 0;
                    @Override public void run() {
                        tk++;
                        if (tk > 100) { cancel(); return; }
                        for (int i = 0; i < 8; i++) { double a = Math.random() * Math.PI * 2, r = Math.random() * 5; w.spawnParticle(Particle.ELECTRIC_SPARK, c.clone().add(Math.cos(a) * r, 0.3 + Math.random(), Math.sin(a) * r), 1, 0, 0, 0, 0.05); }
                        if (tk % 20 == 0) {
                            w.playSound(c, Sound.BLOCK_BEACON_AMBIENT, 0.8f, 2f);
                            for (LivingEntity e : Effects.within(c, 5, le -> Effects.isEnemy(p, le))) { e.setNoDamageTicks(0); Effects.damage(e, 2.0, p); Effects.give(e, PotionEffectType.SLOWNESS, 0, 30); }
                        }
                    }
                }.runTaskTimer(EarthDelta.plugin(), 0L, 1L);
                w.strikeLightningEffect(c);
                return true;
            }
            case DEPTH_STRIDER -> {
                Location c = p.getLocation().clone();
                TempBlocks water = new TempBlocks().unbreakable();
                for (int dx = -5; dx <= 4; dx++) for (int dz = -5; dz <= 4; dz++) {
                    int y = Effects.groundAround(w, c.getBlockX() + dx, c.getBlockZ() + dz, c.getBlockY());
                    if (y == Integer.MIN_VALUE) continue;
                    var top = w.getBlockAt(c.getBlockX() + dx, y, c.getBlockZ() + dz);
                    if (top.getType().isSolid()) water.replace(top, Material.WATER);
                }
                new BukkitRunnable() {
                    int tk = 0;
                    @Override public void run() {
                        tk++;
                        if (tk > 10) { water.restore(); cancel(); return; }
                        List<Player> team = new ArrayList<>(EarthDelta.allies(p)); team.add(p);
                        for (Player a : team) if (a.getWorld() == w && Math.abs(a.getLocation().getX() - c.getX()) <= 5.5 && Math.abs(a.getLocation().getZ() - c.getZ()) <= 5.5) Effects.give(a, PotionEffectType.SPEED, 0, 30);
                    }
                }.runTaskTimer(EarthDelta.plugin(), 0L, 20L);
                w.playSound(c, Sound.ITEM_BUCKET_EMPTY, 1f, 1f);
                return true;
            }
            case EFFICIENCY -> {
                LivingEntity target = nearest(p); if (target == null) return false;
                TempBlocks hole = new TempBlocks().unbreakable();
                Location c = target.getLocation();
                for (int dy = -1; dy >= -10; dy--) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    var b = w.getBlockAt(c.getBlockX() + dx, c.getBlockY() + dy, c.getBlockZ() + dz);
                    if (b.getType().isSolid() && b.getType() != Material.BEDROCK) hole.replace(b, Material.AIR);
                }
                hole.restoreAfter(300);
                w.playSound(c, Sound.BLOCK_GRAVEL_BREAK, 1f, 0.6f);
                w.spawnParticle(Particle.BLOCK, c, 60, 1.2, 0.5, 1.2, 0, Material.DIRT.createBlockData());
                return true;
            }
            case FEATHER_FALLING -> {
                LivingEntity target = Effects.nearestEnemy(p, 10);
                Effects.launchUp(p, 10); Effects.noFallUntil(p, 8000);
                if (target != null) Effects.launchUp(target, 10);
                w.spawnParticle(Particle.CLOUD, p.getLocation(), 30, 0.5, 0.2, 0.5, 0.05);
                w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 1f);
                return true;
            }
            case FIRE_ASPECT -> {
                LivingEntity target = nearest(p); if (target == null) return false;
                TempBlocks bubble = new TempBlocks().unbreakable();
                Location c = target.getLocation();
                for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 2; dy++) for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0 && dy < 2) continue;
                    bubble.place(w.getBlockAt(c.getBlockX() + dx, c.getBlockY() + dy, c.getBlockZ() + dz), Material.LAVA);
                }
                Effects.root(target, 100);
                target.setFireTicks(100);
                bubble.restoreAfter(100);
                w.playSound(c, Sound.ITEM_BUCKET_EMPTY_LAVA, 1f, 0.8f);
                Effects.say(target, "<red>Magma Torture !");
                return true;
            }
            case FLAME -> {
                LivingEntity target = nearest(p); if (target == null) return false;
                TempBlocks fire = new TempBlocks().unbreakable();
                Location c = target.getLocation();
                for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    int y = Effects.groundAround(w, c.getBlockX() + dx, c.getBlockZ() + dz, c.getBlockY());
                    if (y != Integer.MIN_VALUE) fire.place(w.getBlockAt(c.getBlockX() + dx, y + 1, c.getBlockZ() + dz), Material.FIRE);
                }
                fire.restoreAfter(160);
                w.playSound(c, Sound.ITEM_FIRECHARGE_USE, 1f, 1f);
                return true;
            }
            case IMPALING, SHARPNESS -> {
                Arrow a = Bullets.shoot(p, p.getEyeLocation().getDirection(), 2.5, 1.0, Material.IRON_BLOCK, Color.fromRGB(200, 200, 210));
                a.getPersistentDataContainer().set(SPEAR, PersistentDataType.STRING, t.id);
                w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 1f);
                return true;
            }
            case KNOCKBACK -> {
                LivingEntity target = nearest(p); if (target == null) return false;
                Effects.knockbackFrom(target, p.getLocation(), 10);
                w.spawnParticle(Particle.EXPLOSION, target.getLocation().add(0, 1, 0), 1);
                w.playSound(target.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f);
                return true;
            }
            case LOYALTY -> {
                Vector dir = p.getEyeLocation().getDirection().normalize();
                Location start = p.getEyeLocation().add(0, -0.3, 0);
                ItemDisplay trident = w.spawn(start, ItemDisplay.class, d -> {
                    d.setItemStack(new ItemStack(Material.TRIDENT));
                    d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.2f, 1.2f, 1.2f), new Quaternionf()));
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setPersistent(false);
                });
                Set<UUID> hit = new HashSet<>();
                w.playSound(start, Sound.ITEM_TRIDENT_THROW, 1f, 0.9f);
                new BukkitRunnable() {
                    int tk = 0;
                    @Override public void run() {
                        tk++;
                        if (!p.isOnline() || tk > 40) { trident.remove(); cancel(); return; }
                        double d = tk <= 20 ? tk * 0.5 : (40 - tk) * 0.5;
                        Location l = (tk <= 20 ? start : p.getEyeLocation().add(0, -0.3, 0)).clone().add(dir.clone().multiply(d));
                        if (!l.getBlock().isPassable()) { trident.remove(); cancel(); return; }
                        l.setDirection(dir);
                        trident.teleport(l);
                        w.spawnParticle(Particle.CRIT, l, 2, 0.1, 0.1, 0.1, 0.02);
                        for (LivingEntity e : Effects.enemiesWithin(p, l, 1.5)) if (hit.add(e.getUniqueId())) { Effects.give(e, PotionEffectType.WEAKNESS, 2, 200); Effects.damage(e, 1.0, p); w.playSound(l, Sound.ITEM_TRIDENT_HIT, 1f, 1f); }
                    }
                }.runTaskTimer(EarthDelta.plugin(), 0L, 1L);
                return true;
            }
            case LUCK -> {
                PotionType[] types = {PotionType.POISON, PotionType.SLOWNESS, PotionType.WEAKNESS, PotionType.HARMING, PotionType.NIGHT_VISION, PotionType.OOZING, PotionType.INFESTED, PotionType.WIND_CHARGED, PotionType.WEAVING};
                Vector dir = p.getEyeLocation().getDirection();
                for (int i = -2; i <= 2; i++) {
                    Vector d = dir.clone().rotateAroundY(Math.toRadians(i * 8));
                    Arrow a = Effects.shootInstantArrow(p, d, 2.5, 2.0);
                    a.setBasePotionType(types[(int) (Math.random() * types.length)]);
                }
                return true;
            }
            case LURE -> {
                Clones.spawnWanderer(p, 20 * 30, (clone, attacker) -> {
                    Effects.give(attacker, PotionEffectType.WITHER, 0, 100);
                    Effects.say(attacker, "<dark_gray>C'etait un leurre : Wither 5 s !");
                });
                Msg.actionBar(p, "<gray>Un leurre se balade.");
                return true;
            }
            case PROTECTION -> {
                LivingEntity target = nearest(p); if (target == null) return false;
                var eq = target.getEquipment();
                if (eq != null) {
                    for (ItemStack it : new ItemStack[]{eq.getHelmet(), eq.getBoots()}) if (it != null && !it.getType().isAir()) w.dropItemNaturally(target.getLocation(), it);
                    eq.setHelmet(null); eq.setBoots(null);
                }
                w.playSound(target.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 1f, 0.6f);
                Effects.say(target, "<gray>Desarme par " + p.getName() + " : casque et bottes !");
                return true;
            }
            case RESPIRATION -> {
                Effects.root(p, 200);
                Msg.actionBar(p, "<dark_purple>Concentration Ultime : 10 secondes...");
                new BukkitRunnable() {
                    int tk = 0;
                    @Override public void run() {
                        tk += 10;
                        if (!p.isOnline() || p.isDead()) { cancel(); return; }
                        w.spawnParticle(Particle.WITCH, p.getLocation().add(0, 1, 0), 5, 0.4, 0.6, 0.4, 0.02);
                        if (tk >= 200) {
                            int n = 0;
                            for (LivingEntity e : Effects.enemiesWithin(p, 30)) { Effects.give(e, PotionEffectType.WITHER, 1, 100); n++; }
                            w.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.4f);
                            Msg.actionBar(p, "<dark_purple>Concentration Ultime : " + n + " adversaire(s) touche(s).");
                            cancel();
                        }
                    }
                }.runTaskTimer(EarthDelta.plugin(), 10L, 10L);
                return true;
            }
            case RIPTIDE -> {
                Vector dir = p.getLocation().getDirection().setY(0);
                if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
                dir.normalize();
                Location from = p.getLocation();
                Set<UUID> hit = new HashSet<>();
                final Vector d = dir;
                new BukkitRunnable() {
                    int i = 0; Location cur = from.clone();
                    @Override public void run() {
                        i++;
                        if (!p.isOnline() || p.isDead() || i > 5) { cancel(); return; }
                        Location next = cur.clone().add(d.clone().multiply(2));
                        if (!next.getBlock().isPassable() || !next.clone().add(0, 1, 0).getBlock().isPassable()) { cancel(); return; }
                        for (double k = 0; k < 2; k += 0.5) { Location l = cur.clone().add(d.clone().multiply(k)).add(0, 1, 0); w.spawnParticle(Particle.ITEM_SLIME, l, 2, 0.2, 0.3, 0.2, 0.01); for (LivingEntity e : Effects.enemiesWithin(p, l, 1.5)) if (hit.add(e.getUniqueId())) Effects.give(e, PotionEffectType.WEAKNESS, 3, 200); }
                        p.teleport(next); cur = next;
                    }
                }.runTaskTimer(EarthDelta.plugin(), 0L, 1L);
                w.playSound(from, Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.2f);
                return true;
            }
            case SILK_TOUCH -> {
                LivingEntity target = nearest(p); if (target == null) return false;
                com.kiokouwhite.crossfight.character.Character ch = null;
                if (target instanceof Player tp) { CfPlayer tcf = EarthDelta.plugin().players().get(tp); ch = tcf.character(); }
                else if (com.kiokouwhite.crossfight.util.Mannequins.isMannequin(target)) { String id = com.kiokouwhite.crossfight.util.Mannequins.characterOf(target); if (id != null) ch = EarthDelta.plugin().characters().character(id); }
                if (ch == null) { Msg.actionBar(p, "<red>Cet adversaire n'a pas de capacite a voler."); return false; }
                List<Ability> pool = new ArrayList<>();
                for (Ability a : ch.abilities()) if (a.type() != AbilityType.ULTIMATE && !a.isWeaponBound() && !Borrowed.has(p, a.id())) pool.add(a);
                if (pool.isEmpty()) { Msg.actionBar(p, "<red>Rien a voler sur cet adversaire."); return false; }
                Ability a = pool.get((int) (Math.random() * pool.size()));
                Borrowed.add(p, a, ch.name(), Long.MAX_VALUE);
                Msg.send(p, "<light_purple>Vol Merite : <white>" + a.name() + "</white> (de " + ch.name() + ") est a toi jusqu'a la fin de la partie.");
                w.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1f, 1.4f);
                return true;
            }
            case SWEEPING -> {
                Location eye = p.getEyeLocation();
                Vector dir = eye.getDirection();
                Location c = eye.clone().add(dir.clone().multiply(1.5));
                Vector side = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
                for (int k = -1; k <= 1; k++) w.spawnParticle(Particle.SWEEP_ATTACK, c.clone().add(side.clone().multiply(k * 0.7)), 1);
                w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.9f);
                int n = 0;
                for (LivingEntity e : Effects.enemiesWithin(p, c, 2.5)) { Effects.give(e, PotionEffectType.WITHER, 1, 100); Effects.damage(e, 1.0, p); n++; }
                if (n == 0) Msg.actionBar(p, "<gray>Triple Slash : dans le vide.");
                return true;
            }
            case UNBREAKING -> {
                LivingEntity target = nearest(p); if (target == null) return false;
                Hano.FRAGILE.put(target.getUniqueId(), System.currentTimeMillis() + 5000L);
                w.spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0, new Particle.DustOptions(Color.fromRGB(170, 230, 255), 1.3f));
                w.playSound(target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.7f);
                Effects.say(target, "<aqua>Brise Glace : degats doubles 5 s !");
                return true;
            }
            default -> { Msg.actionBar(p, "<gray>" + t.label + " est un passif."); return false; }
        }
    }
}
