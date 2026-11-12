package com.kiokouwhite.crossfight.character.fireemblem;

import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Marth - Path of Harmony.
 */
public final class Marth extends Character {

    static final String FALCHION_ID = "marth_falchion";

    public Marth() {
        super("marth", "Marth", Voie.HARMONIE, Material.IRON_SWORD);
        addAbility(new Falchion().bindToWeapon());
        addAbility(new Contre());
        addAbility(new Charisme());
        addAbility(new EspritDuLeader());
        setUltimate(new CoupCritique());
    }

    @Override
    public ItemStack createWeapon() {
        return falchion(false);
    }

    static ItemStack falchion(boolean holy) {
        ItemStack sword = Weapons.melee(holy ? Material.DIAMOND_SWORD : Material.IRON_SWORD, holy ? 9 : 8,
                holy ? "<aqua><bold>Sainte-Falchion" : "<gold><bold>Falchion",
                holy ? List.of("<aqua>L'Embleme de Feu a revele sa vraie forme.") : List.of());
        Items.tag(sword, AbilityEngine.ABILITY_KEY, FALCHION_ID);
        return sword;
    }

    /** Transforme la Falchion de Marth en Sainte-Falchion (Embleme de Feu recupere). */
    public static void upgrade(Player marth) {
        var inv = marth.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (FALCHION_ID.equals(Items.tag(it, AbilityEngine.ABILITY_KEY))) {
                ItemStack holy = falchion(true);
                AbilityEngine.decorateWeapon(holy, plugin().characters().character("marth"));
                inv.setItem(i, holy);
            }
        }
        plugin().abilities().state(marth, plugin().characters().character("marth").ability(FALCHION_ID)).set("holy", true);
        Msg.send(marth, "<aqua><bold>Ta Falchion devient la Sainte-Falchion !");
        marth.playSound(marth.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.2f);
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Falchion (8) : soin de 5 coeurs immobile",
                "<white>Contre : renvoie le prochain coup",
                "<white>Charisme : Vitesse pour l'equipe proche",
                "<white>Esprit du Leader : Force aux allies",
                "<light_purple>Ultime : charge de 10 blocs, 5 coeurs");
    }

    // ------------------------------------------------------------------ arme

    static final class Falchion extends ActiveAbility {
        Falchion() {
            super(FALCHION_ID, "Falchion", "Clic droit : Marth regenere 5 coeurs (10 avec la Sainte-Falchion) en 3 secondes sans pouvoir bouger.", Material.IRON_SWORD, 60);
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            boolean holy = st.get("holy", false);
            double total = holy ? 20.0 : 10.0;
            Effects.root(p, 60);
            Msg.actionBar(p, "<green>Regeneration...");
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (!p.isOnline() || p.isDead() || ticks >= 60) { cancel(); return; }
                    ticks += 10;
                    Effects.heal(p, total / 6.0);
                    p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.5, 0), 2, 0.4, 0.3, 0.4);
                }
            }.runTaskTimer(plugin(), 10L, 10L);
            p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.4f);
            return true;
        }
    }

    // --------------------------------------------------------------- pouvoirs

    static final class Contre extends ActiveAbility {
        static final Particle.DustOptions WHITE = new Particle.DustOptions(org.bukkit.Color.fromRGB(250, 250, 255), 1.3f);
        static final Particle.DustOptions PALE_BLUE = new Particle.DustOptions(org.bukkit.Color.fromRGB(170, 215, 255), 1.1f);

        Contre() {
            super("marth_contre", "Contre",
                    "Marth prend la pose, immobile : s'il recoit un coup dans la seconde, il ne subit rien, renvoie les degats d'un grand coup d'epee qui projette l'adversaire au loin, puis l'immobilise 1 seconde.",
                    Material.NAUTILUS_SHELL, 5);
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            long until = System.currentTimeMillis() + 1000L;
            st.set("counterUntil", until);
            // Marth tient la pose sans bouger pendant toute la seconde (il peut encore tourner la tete).
            Effects.root(p, 20);
            p.playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.6f);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.4f);
            // La pose du Contre : le joueur est remplace une seconde par son modele articule (si le pack l'a).
            var puppet = com.kiokouwhite.crossfight.util.Puppet.show(p, "marth", heldFalchion(p), "debout");
            if (puppet != null) { puppet.pose("marth_contre", 3); puppet.endAfter(21, 3); }
            // Marth scintille en blanc, comme le flash du Contre de Melee.
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (t > 20 || !p.isOnline() || st.get("counterUntil", 0L) != until) { cancel(); return; }
                    Location c = p.getLocation().add(0, 1, 0);
                    p.getWorld().spawnParticle(Particle.DUST, c, 6, 0.35, 0.6, 0.35, 0, WHITE);
                    p.getWorld().spawnParticle(Particle.END_ROD, c, 2, 0.3, 0.5, 0.3, 0.01);
                    if (t % 5 == 0) p.getWorld().spawnParticle(Particle.FIREWORK, c.clone().add(0, 0.6, 0), 3, 0.2, 0.2, 0.2, 0.02);
                }
            }.runTaskTimer(plugin(), 0L, 1L);
            return true;
        }

        @Override
        public void onDamaged(Player victim, EntityDamageEvent e, AbilityState st) {
            long until = st.get("counterUntil", 0L);
            if (System.currentTimeMillis() > until) return;
            if (!(e instanceof EntityDamageByEntityEvent ede)) return;
            org.bukkit.entity.LivingEntity attacker = com.kiokouwhite.crossfight.listener.DamageListener.livingAttackerOf(ede.getDamager());
            if (attacker == null || attacker == victim) return;
            double dmg = e.getFinalDamage();
            e.setCancelled(true);
            st.set("counterUntil", 0L);
            Effects.unroot(victim);
            // Marth se tourne vers l'adversaire et riposte d'un grand coup d'epee.
            Vector toward = attacker.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
            if (toward.lengthSquared() < 0.01) toward = victim.getLocation().getDirection().setY(0);
            toward.normalize();
            Location face = victim.getLocation().clone(); face.setDirection(toward);
            victim.teleport(face);
            attacker.setNoDamageTicks(0);
            Effects.damage(attacker, dmg, victim);
            // Projection au loin, comme dans Melee : l'adversaire part en cloche, puis reste immobilise 1 s en retombant.
            attacker.setVelocity(toward.clone().multiply(2.2).setY(0.85));
            Bukkit.getScheduler().runTaskLater(plugin(), () -> { if (!attacker.isDead()) Effects.root(attacker, 20); }, 16L);
            crescent(victim, toward);
            // La riposte : fente avant, epee tendue.
            var puppet = com.kiokouwhite.crossfight.util.Puppet.of(victim);
            if (puppet != null) { puppet.pose("marth_contre_coup", 2); puppet.endAfter(11, 3); }
            org.bukkit.World w = victim.getWorld();
            w.spawnParticle(Particle.FLASH, attacker.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0, org.bukkit.Color.fromRGB(245, 250, 255));
            w.spawnParticle(Particle.CRIT, attacker.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.3);
            w.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.7f);
            w.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.6f);
            w.playSound(victim.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.9f);
            w.playSound(attacker.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.4f);
            Msg.actionBar(victim, "<gold>Contre reussi !");
            com.kiokouwhite.crossfight.util.Sounds.ability(victim, "marth_contre_hit");
            if (attacker instanceof Player ap) Msg.actionBar(ap, "<red>Marth a contre ton coup !");
        }

        /** La Falchion que porte Marth (ou la Sainte-Falchion), pour la montrer dans la main du modele. */
        static ItemStack heldFalchion(Player p) {
            for (ItemStack it : p.getInventory().getContents()) {
                if (it != null && FALCHION_ID.equals(Items.tag(it, AbilityEngine.ABILITY_KEY))) return it;
            }
            return new ItemStack(Material.IRON_SWORD);
        }

        /** Le croissant du coup d'epee : un grand arc blanc balaye devant Marth en quelques ticks. */
        static void crescent(Player p, Vector dir) {
            org.bukkit.World w = p.getWorld();
            Location o = p.getLocation().add(0, 1.1, 0);
            Vector side = new Vector(-dir.getZ(), 0, dir.getX());
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (t > 5) { cancel(); return; }
                    // L'arc va du haut-gauche au bas-droit, de plus en plus large.
                    double from = -Math.PI / 2 + (t - 1) * Math.PI / 5, to = from + Math.PI / 5;
                    for (double a = from; a <= to; a += Math.PI / 40) {
                        for (double r = 1.0; r <= 2.6; r += 0.4) {
                            Vector v = dir.clone().multiply(Math.cos(a * 0.6) * r).add(side.clone().multiply(Math.sin(a) * r));
                            Location at = o.clone().add(v).add(0, -Math.sin(a) * 1.1, 0);
                            w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, r > 2.2 ? PALE_BLUE : WHITE);
                        }
                    }
                    w.spawnParticle(Particle.SWEEP_ATTACK, o.clone().add(dir.clone().multiply(1.6)), 1);
                    w.spawnParticle(Particle.END_ROD, o.clone().add(dir.clone().multiply(1.8)), 4, 0.5, 0.5, 0.5, 0.05);
                }
            }.runTaskTimer(plugin(), 0L, 1L);
        }
    }

    static final class Charisme extends PassiveAbility {
        Charisme() {
            super("marth_charisme", "Charisme", "Marth a Vitesse. Ses allies a moins de 10 blocs aussi.", Material.SUGAR);
        }

        @Override
        public void tick(Player p, CfPlayer cf, AbilityState st) {
            Effects.refresh(p, PotionEffectType.SPEED, 0, 45);
            for (var ally : Effects.alliesWithin(p, 10)) Effects.refresh(ally, PotionEffectType.SPEED, 0, 45);
        }
    }

    static final class EspritDuLeader extends PassiveAbility {
        EspritDuLeader() {
            super("marth_leader", "Esprit du Leader", "Tant que Marth a plus de 5 coeurs, ses allies ont Force.", Material.GOLD_INGOT);
        }

        @Override
        public void tick(Player p, CfPlayer cf, AbilityState st) {
            if (p.getHealth() <= 10.0) return;
            for (var ally : Effects.alliesWithin(p, 1000)) Effects.refresh(ally, PotionEffectType.STRENGTH, 0, 45);
        }
    }

    // ----------------------------------------------------------------- ultime

    static final class CoupCritique extends UltimateAbility implements com.kiokouwhite.crossfight.ability.EnemyCastable {
        CoupCritique() {
            super("marth_ult", "Coup Critique",
                    "Marth fonce de 10 blocs, invulnerable pendant la charge. Les ennemis touches perdent 5 coeurs et tous les allies gagnent Force (1 niveau par ennemi touche) pendant 1 minute.",
                    Material.NETHER_STAR);
        }

        @Override
        public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            st.set("invulnUntil", System.currentTimeMillis() + 1200L);
            int hits = perform(p, Effects.towards(p, null), Effects.enemiesOf(p, null));
            if (hits > 0) {
                int level = Math.min(hits, 5) - 1;
                Effects.give(p, PotionEffectType.STRENGTH, level, 20 * 60);
                for (var ally : Effects.alliesWithin(p, 1000)) Effects.give(ally, PotionEffectType.STRENGTH, level, 20 * 60);
                Msg.send(p, "<gold>Coup Critique : " + hits + " ennemi(s) touche(s), Force " + (level + 1) + " pour l'equipe !");
            } else {
                Msg.send(p, "<gray>Coup Critique : aucun ennemi touche.");
            }
            return true;
        }

        @Override
        public boolean castOn(org.bukkit.entity.LivingEntity caster, Player target, AbilityState st) {
            perform(caster, Effects.towards(caster, target), Effects.enemiesOf(caster, target));
            return true;
        }

        /** La charge : fonce de 10 blocs et frappe tout ce qui passe le filtre sur le trajet. */
        static int perform(org.bukkit.entity.LivingEntity caster, Vector dir, java.util.function.Predicate<org.bukkit.entity.LivingEntity> isTarget) {
            Location start = caster.getLocation();
            List<org.bukkit.entity.LivingEntity> touched = new ArrayList<>();
            for (org.bukkit.entity.LivingEntity enemy : Effects.within(start, 12, isTarget)) {
                Vector rel = enemy.getLocation().toVector().subtract(start.toVector());
                double along = rel.dot(dir);
                if (along < 0 || along > 10.5) continue;
                double perp = rel.clone().subtract(dir.clone().multiply(along)).setY(0).length();
                if (perp <= 1.6 && Math.abs(rel.getY()) <= 2.5) touched.add(enemy);
            }
            for (org.bukkit.entity.LivingEntity enemy : touched) {
                Effects.damage(enemy, 10.0, caster);
                // Comme dans Fire Emblem : une barre de vie se vide de 100 a 0 au-dessus de la cible (effet visuel).
                com.kiokouwhite.crossfight.util.HealthBar.drainToZero(enemy);
            }
            caster.setVelocity(dir.clone().multiply(2.6).setY(0.25));
            for (double d = 0; d <= 10; d += 0.5) {
                Location l = start.clone().add(dir.clone().multiply(d)).add(0, 1, 0);
                caster.getWorld().spawnParticle(Particle.SWEEP_ATTACK, l, 1);
                caster.getWorld().spawnParticle(Particle.CRIT, l, 3, 0.3, 0.3, 0.3, 0);
            }
            caster.getWorld().playSound(start, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
            return touched.size();
        }

        @Override
        public void onDamaged(Player victim, EntityDamageEvent e, AbilityState st) {
            if (System.currentTimeMillis() < (long) st.get("invulnUntil", 0L)) {
                e.setCancelled(true);
                victim.getWorld().spawnParticle(Particle.ENCHANTED_HIT, victim.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3);
            }
        }
    }

    @SuppressWarnings("unused")
    private static void later(Runnable r, long ticks) {
        Bukkit.getScheduler().runTaskLater(plugin(), r, ticks);
    }

    private static com.kiokouwhite.crossfight.CrossfightPlugin plugin() {
        return com.kiokouwhite.crossfight.CrossfightPlugin.get();
    }
}
