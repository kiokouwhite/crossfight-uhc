package com.kiokouwhite.crossfight.character.valorant;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
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
                "<white>Electrofleche : arc a bander, sphere electrique de 6 blocs, 2 coeurs",
                "<white>Fleche de reco : arc a bander, 3 ondes revelent a 40 blocs",
                "<white>Drone Rapace : tire des fleches de Faiblesse 10 s",
                "<light_purple>Ultime : 6 s, 3 rayons au clic qui traversent les murs (60 blocs, 3 coeurs)");
    }

    static final String AMMO_ID = "sova_electro_ammo";
    static final Color BLUE = Color.fromRGB(70, 150, 255), PALE = Color.fromRGB(170, 220, 255);
    static final Particle.DustOptions ARC = new Particle.DustOptions(BLUE, 0.7f);

    /** Transforme l'item de capacite en arc Infinite (bande comme un arc normal). */
    static ItemStack electricBow(ItemStack bow) {
        var meta = bow.getItemMeta();
        meta.addEnchant(org.bukkit.enchantments.Enchantment.INFINITY, 1, true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        bow.setItemMeta(meta);
        return bow;
    }
    static boolean isElectricBow(ItemStack it, String id) {
        return it != null && it.getType() == Material.BOW && id.equals(com.kiokouwhite.crossfight.util.Items.tag(it, com.kiokouwhite.crossfight.ability.AbilityEngine.ABILITY_KEY));
    }
    /** La fleche "munition" qui permet de bander les arcs de Sova (jamais consommee grace a Infinite). */
    static void giveAmmo(Player p) {
        var inv = p.getInventory();
        for (ItemStack it : inv.getContents()) if (it != null && AMMO_ID.equals(com.kiokouwhite.crossfight.util.Items.tag(it, com.kiokouwhite.crossfight.ability.AbilityEngine.ABILITY_KEY))) return;
        ItemStack a = com.kiokouwhite.crossfight.util.Items.of(Material.ARROW, "<aqua>Fleches de Sova", List.of("Munitions de l'Electrofleche et de la", "Fleche de reconnaissance."));
        com.kiokouwhite.crossfight.util.Items.tag(a, com.kiokouwhite.crossfight.ability.AbilityEngine.ABILITY_KEY, AMMO_ID);
        if (inv.getItem(9) == null) inv.setItem(9, a); else inv.addItem(a);
    }
    /** Pendant que l'arc est bande : l'arc crepite et un trait d'energie bleue monte le long de la corde. */
    static void bowCharge(Player p, AbilityState st, String id) {
        if (!p.isHandRaised() || !isElectricBow(p.getActiveItem(), id)) { st.set("charge", 0); return; }
        int t = st.get("charge", 0) + 1; st.set("charge", t);
        double f = Math.min(1.0, t / 20.0);
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Location hand = eye.clone().add(dir.clone().multiply(0.9)).add(right.multiply(0.35)).add(0, -0.3, 0);
        w.spawnParticle(Particle.ELECTRIC_SPARK, hand, 1 + (int) (f * 3), 0.15, 0.25, 0.15, 0.02);
        for (double y = -0.45; y <= -0.45 + 0.9 * f; y += 0.12) w.spawnParticle(Particle.DUST, hand.clone().add(0, y + 0.3, 0), 1, 0, 0, 0, 0, ARC);
        if (t % 4 == 0 && t <= 20) w.playSound(hand, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.25f, 0.7f + (float) f * 1.0f);
        if (t == 20) { w.playSound(hand, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 2f); w.spawnParticle(Particle.ELECTRIC_SPARK, hand, 20, 0.3, 0.4, 0.3, 0.15); }
    }
    /** Tir d'un dard bleu a la vitesse de la charge. */
    static Arrow shootDart(Player p, double force, NamespacedKey tag) {
        Vector dir = p.getEyeLocation().getDirection();
        Arrow a = Bullets.shoot(p, dir, 0.6 + 2.4 * force, 1.0, Material.LIGHT_BLUE_CONCRETE, PALE);
        a.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.8f);
        p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getEyeLocation().add(dir), 15, 0.2, 0.2, 0.2, 0.1);
        return a;
    }

    /** Deux fleches en reserve, une revient toutes les 6 secondes. L'item est un vrai arc a bander. */
    static final class Electrofleche extends ActiveAbility {
        static final int MAX = 2; static final long RELOAD_MS = 6000L;
        Electrofleche() {
            super("sova_electro", "Electrofleche", "Sova bande son arc : la fleche se plante, clignote une seconde puis explose en une sphere electrique de 6 blocs qui inflige 2 coeurs et immobilise 1 seconde. 2 fleches, une revient toutes les 6 secondes.", Material.BOW, 0);
        }
        @Override public boolean vanillaUse() { return true; }
        @Override public ItemStack createItem() { return electricBow(super.createItem()); }
        static boolean isBow(ItemStack it) { return isElectricBow(it, "sova_electro"); }
        @Override public void onEquip(Player p, CfPlayer cf) { giveAmmo(p); }
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
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) { bowCharge(p, st, "sova_electro"); }
        /** Tir de l'arc : on remplace la fleche vanilla par le dard electrique, a la vitesse de la charge. */
        boolean shoot(Player p, CfPlayer cf, AbilityState st, double force) {
            int b = arrows(st);
            if (b <= 0) { Msg.actionBar(p, "<red>Electrofleche : aucune fleche prete."); return false; }
            if (b == MAX) st.set("reloadAt", System.currentTimeMillis());
            st.set("arrows", b - 1);
            shootDart(p, force, SHOCK);
            return true;
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }
    }

    @EventHandler
    public void onShootBow(org.bukkit.event.entity.EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        boolean electro = Electrofleche.isBow(e.getBow()), reco = FlecheDeReconnaissance.isBow(e.getBow());
        if (!electro && !reco) return;
        e.setCancelled(true);
        CfPlayer cf = plugin().players().get(p);
        if (cf.character() == null) return;
        if (!plugin().game().isRunning()) { Msg.send(p, "<red>La partie n'a pas commence."); return; }
        // getForce() vaut la puissance de bandage, de 0 a 1 (arc tendu a fond).
        double force = Math.max(0.1, Math.min(1.0, e.getForce()));
        if (electro && cf.character().ability("sova_electro") instanceof Electrofleche el) {
            el.shoot(p, cf, plugin().abilities().state(p, el), force);
        } else if (reco && cf.character().ability("sova_reco") instanceof FlecheDeReconnaissance fr) {
            AbilityState st = plugin().abilities().state(p, fr);
            if (st.onCooldown()) { Msg.actionBar(p, "<red>Fleche de reconnaissance : encore " + st.cooldownLeftSeconds() + "s"); return; }
            shootDart(p, force, RECON);
            st.startCooldown((int) Math.ceil(fr.cooldownSeconds() * cf.cooldownFactor()));
        }
    }

    /** Le dard se plante, clignote une seconde, puis libere une sphere electrique bleue. */
    static void shockDart(Player p, Location c) {
        World w = c.getWorld();
        final double R = 3.0;
        Particle.DustOptions shell = new Particle.DustOptions(BLUE, 1.6f);
        Particle.DustOptions pulse = new Particle.DustOptions(PALE, 0.8f);
        org.bukkit.entity.BlockDisplay dart = w.spawn(c, org.bukkit.entity.BlockDisplay.class, d -> {
            d.setBlock(Material.LIGHT_BLUE_CONCRETE.createBlockData());
            float s = 0.2f;
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(-s / 2, -s / 2, -s / 2), new org.joml.Quaternionf(), new org.joml.Vector3f(s, s, s), new org.joml.Quaternionf()));
            d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            d.setGlowing(true); d.setGlowColorOverride(BLUE); d.setPersistent(false);
        });
        w.playSound(c, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.6f);
        // Points repartis uniformement sur une sphere (spirale de Fibonacci).
        final int N = 150; final double golden = Math.PI * (3 - Math.sqrt(5));
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                tick++;
                try {
                    if (tick <= 20) {
                        // Clignotement : anneaux qui s'elargissent toutes les 5 ticks, bip de plus en plus aigu.
                        if (tick % 5 == 1) {
                            w.playSound(c, Sound.BLOCK_NOTE_BLOCK_BIT, 0.8f, 1.2f + tick * 0.04f);
                            dart.setGlowing(true);
                        } else if (tick % 5 == 3) dart.setGlowing(false);
                        double r = 0.3 + (tick % 5) * 0.25;
                        for (int i = 0; i < 16; i++) { double ang = i * Math.PI / 8; w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(ang) * r, 0.1, Math.sin(ang) * r), 1, 0, 0, 0, 0, pulse); }
                        w.spawnParticle(Particle.ELECTRIC_SPARK, c, 2, 0.1, 0.1, 0.1, 0.05);
                        return;
                    }
                    int t = tick - 21;
                    if (t == 0) {
                        dart.remove();
                        w.spawnParticle(Particle.FLASH, c, 1, 0, 0, 0, 0, PALE);
                        w.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 1.6f);
                        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.8f);
                        w.playSound(c, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.4f);
                        for (LivingEntity enemy : Effects.enemiesWithin(p, c, R)) {
                            Effects.damage(enemy, 4.0, p);
                            Effects.root(enemy, 20);
                        }
                    }
                    if (t > 9) { cancel(); return; }
                    // La sphere se gonfle en 3 ticks puis s'estompe.
                    double r = R * Math.min(1.0, (t + 1) / 3.0);
                    int n = t < 3 ? N : (int) (N * (9 - t) / 6.0);
                    for (int i = 0; i < n; i++) {
                        double y = 1 - (i / (double) (N - 1)) * 2, rad = Math.sqrt(1 - y * y), th = golden * i;
                        w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(th) * rad * r, y * r, Math.sin(th) * rad * r), 1, 0, 0, 0, 0, shell);
                    }
                    w.spawnParticle(Particle.ELECTRIC_SPARK, c, t < 3 ? 40 : 10, r * 0.6, r * 0.6, r * 0.6, 0.05);
                    if (t < 3) w.spawnParticle(Particle.END_ROD, c, 6, r * 0.4, r * 0.4, r * 0.4, 0.02);
                } catch (Exception ex) { dart.remove(); cancel(); }
            }
        }.runTaskTimer(plugin(), 0L, 1L);
    }

    static final class FlecheDeReconnaissance extends ActiveAbility {
        FlecheDeReconnaissance() {
            super("sova_reco", "Fleche de reconnaissance", "Sova bande son arc : la ou la fleche se plante, elle emet 3 impulsions pendant 6 secondes qui revelent et ralentissent les ennemis a 40 blocs.", Material.BOW, 40);
        }
        @Override public boolean vanillaUse() { return true; }
        @Override public ItemStack createItem() { return electricBow(super.createItem()); }
        static boolean isBow(ItemStack it) { return isElectricBow(it, "sova_reco"); }
        @Override public void onEquip(Player p, CfPlayer cf) { giveAmmo(p); }
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) { bowCharge(p, st, "sova_reco"); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }
    }

    /** Le dard de reconnaissance se plante et emet trois ondes bleues qui balaient le sol sur 40 blocs. */
    static void reconBolt(Player p, Location c) {
        World w = c.getWorld();
        final double RANGE = 40.0;
        Particle.DustOptions ring = new Particle.DustOptions(PALE, 1.2f);
        Particle.DustOptions core = new Particle.DustOptions(BLUE, 1.0f);
        org.bukkit.entity.BlockDisplay bolt = w.spawn(c, org.bukkit.entity.BlockDisplay.class, d -> {
            d.setBlock(Material.LIGHT_BLUE_CONCRETE.createBlockData());
            float s = 0.2f;
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(-s / 2, -s / 2, -s / 2), new org.joml.Quaternionf(), new org.joml.Vector3f(s, s, s), new org.joml.Quaternionf()));
            d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
            d.setGlowing(true); d.setGlowColorOverride(BLUE); d.setPersistent(false);
        });
        w.playSound(c, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.6f);
        new BukkitRunnable() {
            int tick = 0; int pulses = 0;
            @Override public void run() {
                tick++;
                try {
                    if (tick > 120) { bolt.remove(); w.spawnParticle(Particle.ELECTRIC_SPARK, c, 25, 0.3, 0.3, 0.3, 0.1); w.playSound(c, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.6f); cancel(); return; }
                    // Le dard respire en bleu entre les impulsions.
                    w.spawnParticle(Particle.DUST, c, 2, 0.12, 0.12, 0.12, 0, core);
                    if (tick % 8 == 0) w.spawnParticle(Particle.ELECTRIC_SPARK, c, 3, 0.15, 0.15, 0.15, 0.03);
                    // Trois impulsions : tick 1, 31, 61 ; chaque onde s'elargit de 2 blocs par tick pendant 1 s.
                    int phase = (tick - 1) % 30, k = (tick - 1) / 30;
                    if (k < 3 && phase < 20) {
                        if (phase == 0) {
                            pulses++;
                            w.playSound(c, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.6f);
                            w.playSound(c, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 0.6f);
                            w.spawnParticle(Particle.FLASH, c, 1, 0, 0, 0, 0, PALE);
                            for (double y = 0.3; y <= 3; y += 0.3) w.spawnParticle(Particle.DUST, c.clone().add(0, y, 0), 1, 0, 0, 0, 0, ring);
                            int n = 0;
                            for (LivingEntity enemy : Effects.enemiesWithin(p, c, RANGE)) {
                                Effects.give(enemy, PotionEffectType.GLOWING, 0, 80);
                                if (pulses == 1) Effects.give(enemy, PotionEffectType.SLOWNESS, 0, 200);
                                n++;
                            }
                            if (pulses == 1) Msg.actionBar(p, "<green>Reconnaissance : " + n + " ennemi(s) revele(s)");
                        }
                        double r = 2.0 * (phase + 1);
                        int pts = Math.max(12, (int) (2 * Math.PI * r));
                        for (int i = 0; i < pts; i++) {
                            double ang = i * 2 * Math.PI / pts;
                            w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(ang) * r, 0.25, Math.sin(ang) * r), 1, 0, 0.05, 0, 0, ring);
                        }
                    }
                } catch (Exception ex) { bolt.remove(); cancel(); }
            }
        }.runTaskTimer(plugin(), 0L, 1L);
    }

    @EventHandler
    public void onArrowLand(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow a) || !(a.getShooter() instanceof Player p)) return;
        Location c = a.getLocation();
        World w = c.getWorld();
        if (a.getPersistentDataContainer().has(SHOCK)) {
            shockDart(p, e.getHitEntity() != null ? e.getHitEntity().getLocation().add(0, 1, 0) : c);
        } else if (a.getPersistentDataContainer().has(RECON)) {
            reconBolt(p, e.getHitEntity() != null ? e.getHitEntity().getLocation().add(0, 1, 0) : c);
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

    /**
     * Fureur du Chasseur : pendant 6 secondes, Sova a trois tirs d'energie a declencher au clic droit.
     * Chaque rayon traverse les murs sur 60 blocs, inflige 3 coeurs et revele les ennemis touches.
     */
    static final class FureurDuChasseur extends UltimateAbility implements com.kiokouwhite.crossfight.ability.CustomUse {
        static final int SHOTS = 3; static final long DURATION_MS = 6000L; static final double RANGE = 60.0;
        static final Particle.DustOptions CORE = new Particle.DustOptions(Color.fromRGB(200, 240, 255), 2.2f);
        static final Particle.DustOptions BEAM = new Particle.DustOptions(BLUE, 1.6f);
        static final Particle.DustOptions BOLT = new Particle.DustOptions(Color.fromRGB(120, 200, 255), 0.9f);
        FureurDuChasseur() {
            super("sova_ult", "Fureur du Chasseur", "Pendant 6 secondes, Sova dispose de 3 tirs d'energie (clic droit) : chaque rayon traverse les murs sur 60 blocs, inflige 3 coeurs et revele les adversaires touches.", Material.END_CRYSTAL);
        }
        static boolean active(AbilityState st) { return System.currentTimeMillis() < st.get("furyUntil", 0L) && st.get("shots", 0) > 0; }

        /** Clic pendant la Fureur : un tir. Sinon, lancement normal de l'ultime. */
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            if (!active(st)) return false;
            if (st.get("winding", false)) return true;
            st.set("winding", true);
            windUp(p, cf, st);
            return true;
        }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            st.set("furyUntil", System.currentTimeMillis() + DURATION_MS);
            st.set("shots", SHOTS);
            st.set("winding", false);
            // Pas de charge d'ultime pendant la Fureur ; Sova est ralenti tant que l'arc est charge.
            org.bukkit.Bukkit.getScheduler().runTask(plugin(), () -> cf.setUltimateLocked(true));
            Effects.give(p, PotionEffectType.SLOWNESS, 0, (int) (DURATION_MS / 50));
            World w = p.getWorld();
            w.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.5f);
            w.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.5f, 1.8f);
            w.spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 60, 0.6, 0.8, 0.6, 0.2);
            new BukkitRunnable() {
                @Override public void run() {
                    if (!p.isOnline() || !active(st)) { end(p, cf, st); cancel(); return; }
                    // L'arc crepite en permanence tant que la Fureur est active.
                    Location eye = p.getEyeLocation();
                    Vector dir = eye.getDirection();
                    Vector right = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
                    Location hand = eye.clone().add(dir.clone().multiply(0.9)).add(right.multiply(0.35)).add(0, -0.3, 0);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, hand, 3, 0.2, 0.3, 0.2, 0.04);
                    w.spawnParticle(Particle.DUST, hand, 2, 0.1, 0.3, 0.1, 0, BOLT);
                }
            }.runTaskTimer(plugin(), 1L, 1L);
            return true;
        }
        static void end(Player p, CfPlayer cf, AbilityState st) {
            st.set("furyUntil", 0L); st.set("shots", 0); st.set("winding", false);
            cf.setUltimateLocked(false);
            p.removePotionEffect(PotionEffectType.SLOWNESS);
            if (p.isOnline()) p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.2f);
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            if (!active(st)) return null;
            long left = Math.max(0, st.get("furyUntil", 0L) - System.currentTimeMillis());
            int shots = st.get("shots", 0);
            return "<aqua><bold>FUREUR</bold> " + "<white>" + "➶".repeat(shots) + "<dark_gray>" + "➶".repeat(SHOTS - shots) + " <aqua>" + String.format(java.util.Locale.US, "%.1f", left / 1000.0) + "s";
        }

        /** Mise en tension d'un tir : 8 ticks ou l'energie se concentre dans l'arc, puis le rayon part. */
        static void windUp(Player p, CfPlayer cf, AbilityState st) {
            World w = p.getWorld();
            w.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.8f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    try {
                        if (!p.isOnline() || !active(st)) { st.set("winding", false); cancel(); return; }
                        Location eye = p.getEyeLocation();
                        Vector dir = eye.getDirection();
                        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0)).normalize();
                        Location hand = eye.clone().add(dir.clone().multiply(0.9)).add(right.multiply(0.35)).add(0, -0.3, 0);
                        // L'energie se rassemble en une boule qui grossit devant l'arc.
                        double r = 0.15 + t * 0.06;
                        for (int i = 0; i < 6; i++) {
                            double a = Math.random() * Math.PI * 2, b = Math.random() * Math.PI;
                            w.spawnParticle(Particle.DUST, hand.clone().add(Math.sin(b) * Math.cos(a) * r, Math.cos(b) * r, Math.sin(b) * Math.sin(a) * r), 1, 0, 0, 0, 0, BEAM);
                        }
                        w.spawnParticle(Particle.ELECTRIC_SPARK, hand, 6, r, r, r, 0.08);
                        w.playSound(hand, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 0.8f + t * 0.15f);
                        if (t >= 8) {
                            st.set("winding", false);
                            st.set("shots", st.get("shots", 0) - 1);
                            blast(p, cf);
                            cancel();
                        }
                    } catch (Exception ex) { st.set("winding", false); cancel(); }
                }
            }.runTaskTimer(plugin(), 1L, 1L);
        }

        /** Le rayon : un trait epais bleu-blanc entoure d'eclairs, qui traverse tout sur 60 blocs. */
        static void blast(Player p, CfPlayer cf) {
            Location eye = p.getEyeLocation();
            Vector dir = eye.getDirection().normalize();
            World w = p.getWorld();
            w.playSound(eye, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.9f);
            w.playSound(eye, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 1.5f);
            w.playSound(eye, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.6f);
            w.spawnParticle(Particle.FLASH, eye.clone().add(dir), 1, 0, 0, 0, 0, PALE);
            // Degats et revelation : tout ennemi a moins de 1,5 bloc de la ligne, murs compris.
            List<UUID> hit = new ArrayList<>();
            int touched = 0;
            for (double d = 1.0; d <= RANGE; d += 0.5) {
                Location l = eye.clone().add(dir.clone().multiply(d));
                for (LivingEntity enemy : Effects.enemiesWithin(p, l, 1.5)) {
                    if (hit.contains(enemy.getUniqueId())) continue;
                    hit.add(enemy.getUniqueId());
                    Effects.damage(enemy, 6.0, p);
                    Effects.give(enemy, PotionEffectType.GLOWING, 0, 60);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, enemy.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.2);
                    w.playSound(enemy.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 1.2f);
                    touched++;
                }
            }
            if (touched > 0) Msg.actionBar(p, "<aqua>Fureur : " + touched + " adversaire(s) touche(s) !");
            // Le trait se dessine en 3 ticks (20 blocs par tick) puis s'estompe.
            Vector up = Math.abs(dir.getY()) > 0.9 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
            Vector side = dir.clone().crossProduct(up).normalize();
            Vector norm = side.clone().crossProduct(dir).normalize();
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    try {
                        if (t > 7) { cancel(); return; }
                        double from = t <= 3 ? (t - 1) * 20.0 : 0, to = t <= 3 ? t * 20.0 : RANGE;
                        double step = t <= 3 ? 0.3 : 1.0;
                        for (double d = Math.max(1.0, from); d < to; d += step) {
                            Location l = eye.clone().add(dir.clone().multiply(d));
                            if (t <= 3) {
                                w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, CORE);
                                w.spawnParticle(Particle.DUST, l, 2, 0.35, 0.35, 0.35, 0, BEAM);
                                if (Math.random() < 0.3) w.spawnParticle(Particle.ELECTRIC_SPARK, l, 1, 0.3, 0.3, 0.3, 0.02);
                            } else {
                                w.spawnParticle(Particle.DUST, l, 1, 0.3, 0.3, 0.3, 0, BEAM);
                            }
                        }
                        // Eclairs en zigzag qui serpentent autour du trait.
                        if (t <= 4) for (int k = 0; k < 3; k++) {
                            double d = Math.random() * Math.min(to, RANGE - 6);
                            double ang = Math.random() * Math.PI * 2, rad = 0.6;
                            for (int seg = 0; seg < 8; seg++) {
                                ang += (Math.random() - 0.5) * 1.6; rad = Math.max(0.2, Math.min(1.6, rad + (Math.random() - 0.5) * 0.8));
                                d += 0.8;
                                Location l = eye.clone().add(dir.clone().multiply(d)).add(side.clone().multiply(Math.cos(ang) * rad)).add(norm.clone().multiply(Math.sin(ang) * rad));
                                w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, BOLT);
                                w.spawnParticle(Particle.ELECTRIC_SPARK, l, 1, 0.05, 0.05, 0.05, 0.01);
                            }
                        }
                    } catch (Exception ex) { cancel(); }
                }
            }.runTaskTimer(plugin(), 0L, 1L);
        }
    }
}
