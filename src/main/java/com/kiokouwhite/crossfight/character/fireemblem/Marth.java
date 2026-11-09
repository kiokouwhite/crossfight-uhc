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
        addAbility(new Falchion());
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
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>Actif");
        lore.addAll(Items.wrap("Marth regenere " + (holy ? 10 : 5) + " coeurs en 3 secondes sans pouvoir bouger."));
        lore.add("");
        lore.add("<yellow>Temps de chargement : <white>01:00");
        ItemStack sword = Weapons.melee(holy ? Material.DIAMOND_SWORD : Material.IRON_SWORD, holy ? 9 : 8,
                holy ? "<aqua><bold>Sainte-Falchion" : "<gold><bold>Falchion", lore);
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
                Items.tag(holy, AbilityEngine.WEAPON_KEY, "marth");
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
            super(FALCHION_ID, "Falchion", "Marth regenere 5 coeurs en 3 secondes sans pouvoir bouger.", Material.IRON_SWORD, 60);
        }

        @Override public boolean hasItem() { return false; }

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
        Contre() {
            super("marth_contre", "Contre",
                    "Si Marth prend un coup dans la seconde qui suit, il ne subit rien, renvoie les degats et immobilise l'adversaire 1 seconde.",
                    Material.SHIELD, 5);
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            st.set("counterUntil", System.currentTimeMillis() + 1000L);
            p.getWorld().spawnParticle(Particle.ENCHANTED_HIT, p.getLocation().add(0, 1, 0), 15, 0.4, 0.5, 0.4);
            p.playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.3f);
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
            Effects.damage(attacker, dmg, victim);
            Effects.root(attacker, 20);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
            victim.getWorld().spawnParticle(Particle.CRIT, attacker.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3);
            Msg.actionBar(victim, "<gold>Contre reussi !");
            if (attacker instanceof Player ap) Msg.actionBar(ap, "<red>Marth a contre ton coup !");
        }
    }

    static final class Charisme extends PassiveAbility {
        Charisme() {
            super("marth_charisme", "Charisme", "Marth a Vitesse. Ses allies a moins de 10 blocs aussi.");
        }

        @Override
        public void tick(Player p, CfPlayer cf, AbilityState st) {
            Effects.refresh(p, PotionEffectType.SPEED, 0, 45);
            for (Player ally : Effects.alliesWithin(p, 10)) Effects.refresh(ally, PotionEffectType.SPEED, 0, 45);
        }
    }

    static final class EspritDuLeader extends PassiveAbility {
        EspritDuLeader() {
            super("marth_leader", "Esprit du Leader", "Tant que Marth a plus de 5 coeurs, ses allies ont Force.");
        }

        @Override
        public void tick(Player p, CfPlayer cf, AbilityState st) {
            if (p.getHealth() <= 10.0) return;
            for (Player ally : Effects.alliesWithin(p, 1000)) Effects.refresh(ally, PotionEffectType.STRENGTH, 0, 45);
        }
    }

    // ----------------------------------------------------------------- ultime

    static final class CoupCritique extends UltimateAbility {
        CoupCritique() {
            super("marth_ult", "Coup Critique",
                    "Marth fonce de 10 blocs. Les ennemis touches perdent 5 coeurs et tous les allies gagnent Force (1 niveau par ennemi touche) pendant 1 minute.",
                    Material.NETHER_STAR);
        }

        @Override
        public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
            dir.normalize();
            Location start = p.getLocation();
            int hits = 0;
            List<org.bukkit.entity.LivingEntity> touched = new ArrayList<>();
            for (org.bukkit.entity.LivingEntity enemy : Effects.enemiesWithin(p, 12)) {
                Vector rel = enemy.getLocation().toVector().subtract(start.toVector());
                double along = rel.dot(dir);
                if (along < 0 || along > 10.5) continue;
                double perp = rel.clone().subtract(dir.clone().multiply(along)).setY(0).length();
                double dy = Math.abs(rel.getY());
                if (perp <= 1.6 && dy <= 2.5) touched.add(enemy);
            }
            for (org.bukkit.entity.LivingEntity enemy : touched) {
                Effects.damage(enemy, 10.0, p);
                hits++;
            }
            p.setVelocity(dir.clone().multiply(2.6).setY(0.25));
            for (double d = 0; d <= 10; d += 0.5) {
                Location l = start.clone().add(dir.clone().multiply(d)).add(0, 1, 0);
                p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, l, 1);
                p.getWorld().spawnParticle(Particle.CRIT, l, 3, 0.3, 0.3, 0.3, 0);
            }
            p.getWorld().playSound(start, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
            if (hits > 0) {
                int level = Math.min(hits, 5) - 1;
                Effects.give(p, PotionEffectType.STRENGTH, level, 20 * 60);
                for (Player ally : Effects.alliesWithin(p, 1000)) Effects.give(ally, PotionEffectType.STRENGTH, level, 20 * 60);
                Msg.send(p, "<gold>Coup Critique : " + hits + " ennemi(s) touche(s), Force " + (level + 1) + " pour l'equipe !");
            } else {
                Msg.send(p, "<gray>Coup Critique : aucun ennemi touche.");
            }
            return true;
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
