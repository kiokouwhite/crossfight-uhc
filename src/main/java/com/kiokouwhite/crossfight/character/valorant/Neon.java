package com.kiokouwhite.crossfight.character.valorant;

import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.ToggleAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Bullets;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.TempBlocks;
import com.kiokouwhite.crossfight.util.Weapons;
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
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Neon - Path of Hunt.
 */
public final class Neon extends Character implements Listener {

    static final NamespacedKey RELAY_ARROW = new NamespacedKey("crossfight", "neon_relay");
    static final NamespacedKey ULT_SWORD = new NamespacedKey("crossfight", "neon_sword");

    public Neon() {
        super("neon", "Neon", Voie.CHASSE, Material.LIGHT_BLUE_DYE);
        addAbility(new VitesseSuperieure());
        addAbility(new EclairRelais());
        addAbility(new VoieRapide());
        addAbility(Valorant.SHOP);
        addAbility(Guns.ABILITY);
        setUltimate(new Surmultipliee());
    }

    private static com.kiokouwhite.crossfight.CrossfightPlugin plugin() { return com.kiokouwhite.crossfight.CrossfightPlugin.get(); }

    @Override
    public ItemStack createWeapon() { return Valorant.meleeWeapon(0); }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Vitesse Superieure : Vitesse II a jauge",
                "<white>Eclair Relais : fleche qui explose et immobilise",
                "<white>Voie Rapide : deux murs de 50 blocs pendant 5 s",
                "<light_purple>Ultime : Force, vitesse auto et epee electrique");
    }

    static final class VitesseSuperieure extends ToggleAbility {
        VitesseSuperieure() {
            super("neon_vitesse", "Vitesse Superieure", "Neon gagne Vitesse II mais ne peut pas frapper au corps a corps. Jauge de 100 : -5 par seconde active, +1 par seconde au repos.", Material.SUGAR, 5.0, 1.0);
        }
        @Override public boolean onEnable(Player p, CfPlayer cf, AbilityState st) { return true; }
        @Override public void tickActive(Player p, CfPlayer cf, AbilityState st) {
            Effects.refresh(p, PotionEffectType.SPEED, 1, 45);
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 0.5, 0), 4, 0.3, 0.3, 0.3, 0.05);
        }
        @Override public void onDisable(Player p, CfPlayer cf, AbilityState st) { }

        /** Pendant Vitesse Superieure, Neon ne peut pas frapper au corps a corps, sauf avec la lame electrique de son Ultime. */
        @Override
        public void onDealDamage(Player attacker, LivingEntity victim, org.bukkit.event.entity.EntityDamageByEntityEvent e, AbilityState st) {
            if (!st.isEnabled() || e.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
            if (Items.tag(attacker.getInventory().getItemInMainHand(), ULT_SWORD) != null) return;
            e.setCancelled(true);
            Msg.actionBar(attacker, "<red>Impossible de frapper pendant Vitesse Superieure.");
        }
    }

    static final Particle.DustOptions YELLOW = new Particle.DustOptions(Color.fromRGB(255, 240, 80), 1.3f);
    static final Particle.DustOptions CYAN = new Particle.DustOptions(Color.fromRGB(90, 230, 255), 1.1f);
    static final Particle.DustTransition BLAST = new Particle.DustTransition(Color.fromRGB(255, 250, 120), Color.fromRGB(60, 200, 255), 2.4f);
    /** Rayon de l'explosion (15 blocs de large) et delai avant l'explosion (1 seconde). */
    static final double RELAY_RADIUS = 7.5;
    static final int RELAY_DELAY_TICKS = 20;

    static final class EclairRelais extends ActiveAbility {
        EclairRelais() {
            super("neon_relais", "Eclair Relais", "Neon lance un eclair qui rebondit une fois sur le sol. A chaque impact, un cercle electrique apparait puis explose une seconde plus tard sur 15 blocs de large, immobilisant les adversaires touches pendant 3 secondes.", Material.LIGHTNING_ROD, 10);
        }
        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            throwBolt(p, p.getEyeLocation().add(p.getEyeLocation().getDirection()), p.getEyeLocation().getDirection(), 2.0, 1);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BEE_STING, 1f, 1.8f);
            return true;
        }
    }

    /** Lance l'eclair (une fleche habillee) avec son nombre de rebonds restants. */
    static void throwBolt(Player p, Location from, Vector dir, double speed, int bounces) {
        Arrow a = p.getWorld().spawnArrow(from, dir, (float) speed, 0f);
        com.kiokouwhite.crossfight.util.Effects.setShooter(a, p);
        a.setDamage(0.5);
        a.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
        a.setInvisible(true);
        a.getPersistentDataContainer().set(RELAY_ARROW, PersistentDataType.INTEGER, bounces);
        new org.bukkit.scheduler.BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!a.isValid() || t++ > 60) { cancel(); return; }
                Location l = a.getLocation();
                a.getWorld().spawnParticle(Particle.DUST, l, 3, 0.12, 0.12, 0.12, 0, YELLOW);
                a.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, l, 2, 0.15, 0.15, 0.15, 0.05);
                a.getWorld().spawnParticle(Particle.DUST, l, 1, 0.2, 0.2, 0.2, 0, CYAN);
            }
        }.runTaskTimer(plugin(), 0L, 1L);
    }

    @EventHandler
    public void onRelayLand(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow a) || !a.getPersistentDataContainer().has(RELAY_ARROW)) return;
        if (!(a.getShooter() instanceof Player p)) return;
        int bounces = a.getPersistentDataContainer().getOrDefault(RELAY_ARROW, PersistentDataType.INTEGER, 0);
        Location impact = a.getLocation();
        World w = impact.getWorld();
        Vector velocity = a.getVelocity();
        a.remove();
        // Rebond sur un bloc : l'eclair repart dans la direction reflechie, moins vite.
        if (bounces > 0 && e.getHitBlock() != null && e.getHitBlockFace() != null) {
            Vector normal = e.getHitBlockFace().getDirection();
            Vector reflected = velocity.clone().subtract(normal.clone().multiply(2 * velocity.dot(normal)));
            if (reflected.lengthSquared() > 0.01) {
                Location from = impact.clone().add(normal.clone().multiply(0.3));
                w.playSound(impact, Sound.ENTITY_BEE_STING, 1f, 2f);
                throwBolt(p, from, reflected.normalize(), Math.max(0.8, velocity.length() * 0.6), bounces - 1);
            }
        }
        // Cercle electrique au sol, puis explosion 2 secondes plus tard.
        Location ground = impact.clone();
        while (ground.getY() > w.getMinHeight() && ground.clone().subtract(0, 0.5, 0).getBlock().isPassable() && impact.getY() - ground.getY() < 3) ground.subtract(0, 0.5, 0);
        final Location c = ground;
        w.playSound(c, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 2f);
        new org.bukkit.scheduler.BukkitRunnable() {
            int t = 0;
            boolean exploded = false;
            @Override public void run() {
                t++;
                if (t < RELAY_DELAY_TICKS) {
                    // Anneau exterieur a la taille reelle de l'explosion, et anneau interieur qui grandit vers lui.
                    ring(w, c, RELAY_RADIUS, 0.15, YELLOW, 0.05);
                    double inner = 1.0 + (RELAY_RADIUS - 1.0) * t / RELAY_DELAY_TICKS;
                    ring(w, c, inner, 0.3, CYAN, 0.12);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, c.clone().add(0, 0.3, 0), 10 + t, RELAY_RADIUS * 0.5, 0.3, RELAY_RADIUS * 0.5, 0.06);
                    if (t % 5 == 0) w.playSound(c, Sound.BLOCK_BEACON_AMBIENT, 0.8f, 1.4f + t / 20f);
                    return;
                }
                if (!exploded) {
                    exploded = true;
                    // Explosion : flash, disque rempli uniformement, colonnes electriques sur toute la zone.
                    w.spawnParticle(Particle.FLASH, c.clone().add(0, 1, 0), 3, 0, 0, 0, 0, Color.fromRGB(255, 240, 120));
                    for (int i = 0; i < 500; i++) {
                        double ang = Math.random() * Math.PI * 2, rr = RELAY_RADIUS * Math.sqrt(Math.random());
                        Location l = c.clone().add(Math.cos(ang) * rr, 0.2 + Math.random() * 2.5, Math.sin(ang) * rr);
                        w.spawnParticle(Particle.DUST_COLOR_TRANSITION, l, 1, 0.1, 0.1, 0.1, 0, BLAST);
                        if (i % 3 == 0) w.spawnParticle(Particle.ELECTRIC_SPARK, l, 1, 0.2, 0.2, 0.2, 0.3);
                    }
                    for (int i = 0; i < 40; i++) {
                        double ang = Math.random() * Math.PI * 2, rr = RELAY_RADIUS * Math.sqrt(Math.random());
                        for (double y = 0; y < 5; y += 0.3) w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(ang) * rr, y, Math.sin(ang) * rr), 1, 0.05, 0.05, 0.05, 0, CYAN);
                    }
                    w.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.4f);
                    w.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 1.2f);
                    w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f);
                    for (LivingEntity enemy : Effects.enemiesWithin(p, c, RELAY_RADIUS)) {
                        Effects.root(enemy, 60);
                        Effects.say(enemy, "<yellow>Immobilise par l'Eclair Relais !");
                    }
                }
                // Onde de choc : des anneaux qui s'etendent du centre jusqu'au bord pendant 8 ticks.
                int since = t - RELAY_DELAY_TICKS;
                if (since > 8) { cancel(); return; }
                double r = RELAY_RADIUS * since / 8.0;
                ring(w, c, r, 0.3, YELLOW, 0.06);
                ring(w, c, r, 1.2, CYAN, 0.1);
                ring(w, c, RELAY_RADIUS, 0.2, YELLOW, 0.05);
            }
        }.runTaskTimer(plugin(), 0L, 1L);
    }

    /** Anneau de particules au sol. */
    static void ring(World w, Location c, double radius, double y, Particle.DustOptions dust, double step) {
        for (double ang = 0; ang < Math.PI * 2; ang += step) {
            w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(ang) * radius, y, Math.sin(ang) * radius), 1, 0, 0, 0, 0, dust);
        }
    }

    static final class VoieRapide extends ActiveAbility {
        static final int LENGTH = 50, HEIGHT = 8, DURATION_TICKS = 100, BLOCKS_PER_TICK = 10;
        static final Particle.DustOptions ARC = new Particle.DustOptions(Color.fromRGB(255, 250, 170), 1.0f);
        static final Particle.DustOptions GLOW = new Particle.DustOptions(Color.fromRGB(120, 220, 255), 1.4f);

        VoieRapide() {
            super("neon_voie", "Voie Rapide", "A 2 blocs a droite et a gauche de Neon, deux murs d'energie incassables de 50 blocs de long et 8 de haut se deploient, pendant 5 secondes.", Material.LIGHT_BLUE_STAINED_GLASS, 15);
        }

        /** Degrade du mur : base blanche et jaune lumineuse, corps bleu clair, sommet bleu profond. */
        static Material layer(int y) {
            if (y == 0) return Material.WHITE_STAINED_GLASS;
            if (y == 1) return Material.YELLOW_STAINED_GLASS;
            if (y <= 4) return Material.LIGHT_BLUE_STAINED_GLASS;
            if (y <= 6) return Material.CYAN_STAINED_GLASS;
            return Material.BLUE_STAINED_GLASS;
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Vector fwd = Effects.towards(p, null);
            Vector side = new Vector(-fwd.getZ(), 0, fwd.getX());
            TempBlocks walls = new TempBlocks().unbreakable();
            Location base = p.getLocation();
            World w = p.getWorld();
            w.playSound(base, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 2f);
            w.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.8f);
            new org.bukkit.scheduler.BukkitRunnable() {
                int tick = 0;
                @Override public void run() {
                    tick++;
                    if (tick > DURATION_TICKS || !p.isOnline()) { walls.restore(); w.playSound(base, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.6f); cancel(); return; }
                    // Deploiement : les murs avancent de 10 blocs par tick.
                    int from = (tick - 1) * BLOCKS_PER_TICK, to = Math.min(LENGTH, tick * BLOCKS_PER_TICK);
                    for (int i = from; i < to; i++) {
                        for (int sgn : new int[]{-2, 2}) {
                            Location col = base.clone().add(side.clone().multiply(sgn)).add(fwd.clone().multiply(i));
                            for (int y = 0; y < HEIGHT; y++) walls.place(col.clone().add(0, y, 0).getBlock(), layer(y));
                            w.spawnParticle(Particle.ELECTRIC_SPARK, col.clone().add(0.5, 1, 0.5), 6, 0.3, 2.5, 0.3, 0.1);
                        }
                    }
                    if (to > from) w.playSound(base.clone().add(fwd.clone().multiply(to)), Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.9f);
                    // Arcs electriques le long des parois pendant toute la duree.
                    for (int n = 0; n < 12; n++) {
                        int sgn = Math.random() < 0.5 ? -2 : 2;
                        double along = Math.random() * Math.min(LENGTH, to);
                        double y = Math.random() * HEIGHT;
                        Location a = base.clone().add(side.clone().multiply(sgn + (sgn > 0 ? 0.55 : 0.45))).add(fwd.clone().multiply(along)).add(0, y, 0);
                        Vector dir = fwd.clone().multiply(Math.random() * 2 - 1).add(new Vector(0, Math.random() * 1.5 - 0.75, 0));
                        for (double d = 0; d < 1.5; d += 0.25) w.spawnParticle(Particle.DUST, a.clone().add(dir.clone().multiply(d)), 1, 0.05, 0.05, 0.05, 0, ARC);
                        w.spawnParticle(Particle.ELECTRIC_SPARK, a, 2, 0.2, 0.3, 0.2, 0.08);
                        w.spawnParticle(Particle.DUST, a, 1, 0.3, 0.6, 0.3, 0, GLOW);
                    }
                    if (tick % 10 == 0) w.playSound(base.clone().add(fwd.clone().multiply(Math.random() * Math.min(LENGTH, to))), Sound.BLOCK_BEACON_AMBIENT, 0.7f, 1.9f);
                }
            }.runTaskTimer(plugin(), 0L, 1L);
            Msg.actionBar(p, "<aqua>Voie Rapide deployee pendant 5 s");
            return true;
        }
    }

    static final class Surmultipliee extends UltimateAbility {
        Surmultipliee() {
            super("neon_ult", "Surmultipliee", "Neon gagne Force, Vitesse Superieure s'active toute seule jusqu'a vider la jauge, et elle recoit une epee electrique le temps de l'effet.", Material.NETHER_STAR);
        }
        @Override
        public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            var vitesse = (ToggleAbility) plugin().characters().character("neon").ability("neon_vitesse");
            AbilityState vs = plugin().abilities().state(p, vitesse);
            if (vs.gauge() < 5) vs.setGauge(100);
            if (!vs.isEnabled() && vitesse.onEnable(p, cf, vs)) vs.setEnabled(true);
            int seconds = (int) Math.ceil(vs.gauge() / vitesse.drainPerSecond());
            Effects.give(p, PotionEffectType.STRENGTH, 0, seconds * 20);
            ItemStack sword = Weapons.melee(Material.DIAMOND_SWORD, 10, "<aqua><bold>Lame electrique", Items.wrap("Disparait quand la jauge de Vitesse Superieure est vide."));
            Items.tag(sword, ULT_SWORD, "1");
            Items.tag(sword, AbilityEngine.WEAPON_KEY, "neon_ult");
            var left = p.getInventory().addItem(sword);
            if (!left.isEmpty()) p.getInventory().setItem(0, sword);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.5f);
            Bukkit.getScheduler().runTaskLater(plugin(), () -> removeSword(p), seconds * 20L + 5);
            Msg.send(p, "<aqua>Surmultipliee : " + seconds + " s de puissance.");
            return true;
        }

        static void removeSword(Player p) {
            var inv = p.getInventory();
            for (int i = 0; i < inv.getSize(); i++) if (Items.tag(inv.getItem(i), ULT_SWORD) != null) inv.setItem(i, null);
        }

        @Override
        public void onUnequip(Player p, CfPlayer cf) { removeSword(p); }
    }
}
