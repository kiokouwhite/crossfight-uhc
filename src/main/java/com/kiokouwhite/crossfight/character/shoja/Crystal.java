package com.kiokouwhite.crossfight.character.shoja;

import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.character.duo.Synergy;
import com.kiokouwhite.crossfight.listener.DamageListener;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.TempBlocks;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Crystal (Shoja) - Voie de la Nihilite. Elle paie ses pouvoirs avec sa vie.
 */
public final class Crystal extends Character implements Listener {

    static final Particle.DustOptions CRYSTAL = new Particle.DustOptions(Color.fromRGB(170, 230, 255), 1.1f);
    /** Synergies : Souffrance Abregee (plus de perte de vie) et Amitie Eternelle (plus de degats des coups). */
    static final Map<UUID, Long> NO_LOSS = new HashMap<>();
    static final Map<UUID, Long> NO_HIT = new HashMap<>();

    public Crystal() {
        super("crystal", "Crystal", Voie.NIHILITE, Material.PRISMARINE_CRYSTALS);
        addAbility(new LanceAdamante().bindToWeapon());
        addAbility(new PatinAGlace());
        addAbility(new Crystallization());
        addAbility(new SurchargeDeFlux());
        addAbility(Synergy.ITEM);
        setUltimate(new OeilPourOeil());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.DIAMOND_SWORD, 7.5, "<aqua><bold>Lance Adamante", List.of("<gray>7.5 dmg."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Lance Adamante : arme inutilisable 5 s, Wither II a l'ennemi le plus proche",
                "<white>Patin a glace (-2 coeurs) : Speed III et trainee de cristal (Faiblesse III)",
                "<white>Crystallization (-3 coeurs) : immobilise l'ennemi le plus proche 3 s",
                "<white>Surcharge de Flux (-4 coeurs) : Lenteur, Faiblesse et Wither II",
                "<light_purple>Ultime : Oeil pour Oeil (-8 coeurs), toute la carte affaiblie 10 s");
    }

    /** Le prix en vie d'un pouvoir, sauf pendant Souffrance Abregee. */
    static void pay(Player p, double hearts) {
        Long until = NO_LOSS.get(p.getUniqueId());
        if (until != null && System.currentTimeMillis() < until) { Msg.actionBar(p, "<light_purple>Souffrance Abregee : aucune vie perdue."); return; }
        Shoja.selfLoss(p, hearts * 2);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.6f, 1.6f);
    }

    static LivingEntity nearest(Player p) {
        LivingEntity t = Effects.nearestEnemy(p, 10);
        if (t == null) Msg.actionBar(p, "<red>Aucun adversaire a moins de 10 blocs.");
        return t;
    }

    // ------------------------------------------------------------------ arme

    static final class LanceAdamante extends ActiveAbility {
        LanceAdamante() { super("crystal_lance", "Lance Adamante", "Clic droit : l'arme devient inutilisable 5 secondes et Crystal a Lenteur, mais l'ennemi le plus proche (10 blocs) subit Wither II pendant 5 secondes.", Material.DIAMOND_SWORD, 15); }
        @Override public List<String> weaponLore() { return List.of("<aqua>Clic droit : <gray>arme inutilisable 5 s, Wither II a l'ennemi le plus proche"); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            LivingEntity t = nearest(p); if (t == null) return false;
            Effects.give(t, PotionEffectType.WITHER, 1, 100);
            Effects.give(p, PotionEffectType.SLOWNESS, 0, 100);
            World w = p.getWorld();
            w.spawnParticle(Particle.DUST, t.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0, new Particle.DustOptions(Color.fromRGB(40, 40, 50), 1.2f));
            w.playSound(t.getLocation(), Sound.ENTITY_WITHER_HURT, 0.6f, 1.4f);
            // L'arme est remplacee 5 secondes par une lance brisee, sans degats.
            var inv = p.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack it = inv.getItem(i);
                if (it == null || !"crystal".equals(Items.tag(it, AbilityEngine.WEAPON_KEY))) continue;
                ItemStack saved = it.clone();
                int slot = i;
                ItemStack broken = Items.of(Material.STICK, "<gray><bold>Lance Adamante <red>(brisee)", List.of("<gray>Inutilisable pendant 5 secondes."));
                Items.tag(broken, AbilityEngine.WEAPON_KEY, "crystal");
                inv.setItem(slot, broken);
                Bukkit.getScheduler().runTaskLater(Shoja.plugin(), () -> {
                    if (!p.isOnline()) return;
                    for (int k = 0; k < inv.getSize(); k++) { ItemStack cur = inv.getItem(k); if (cur != null && cur.getType() == Material.STICK && "crystal".equals(Items.tag(cur, AbilityEngine.WEAPON_KEY))) { inv.setItem(k, saved); return; } }
                    inv.addItem(saved);
                }, 100L);
                break;
            }
            Msg.actionBar(p, "<aqua>Lance Adamante : arme brisee 5 s, Wither II sur l'ennemi.");
            return true;
        }
    }

    // ---------------------------------------------------------------- pouvoirs

    static final class PatinAGlace extends ActiveAbility {
        PatinAGlace() { super("crystal_patin", "Patin a glace", "Crystal perd 2 coeurs, gagne Speed III pendant 5 secondes et laisse derriere elle une trainee de cristal : les ennemis dessus ont Faiblesse III.", Material.PACKED_ICE, 10); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            pay(p, 2);
            Effects.give(p, PotionEffectType.SPEED, 2, 100);
            World w = p.getWorld();
            TempBlocks trail = new TempBlocks().unbreakable();
            w.playSound(p.getLocation(), Sound.BLOCK_GLASS_PLACE, 1f, 1.4f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || t > 100) { trail.restoreAfter(100); cancel(); return; }
                    Location l = p.getLocation();
                    if (t % 2 == 0) {
                        int gy = Effects.groundAround(w, l.getBlockX(), l.getBlockZ(), l.getBlockY());
                        if (gy != Integer.MIN_VALUE) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) { var b = w.getBlockAt(l.getBlockX() + dx, gy, l.getBlockZ() + dz); if (b.getType().isSolid()) trail.replace(b, Math.random() < 0.5 ? Material.PACKED_ICE : Material.BLUE_ICE); }
                    }
                    w.spawnParticle(Particle.SNOWFLAKE, l.clone().add(0, 0.3, 0), 3, 0.3, 0.1, 0.3, 0.01);
                    if (t % 10 == 0) for (LivingEntity e : Effects.enemiesWithin(p, 60)) { var under = e.getLocation().subtract(0, 0.1, 0).getBlock(); if (trail.contains(under)) Effects.give(e, PotionEffectType.WEAKNESS, 2, 60); }
                }
            }.runTaskTimer(Shoja.plugin(), 0L, 1L);
            return true;
        }
    }

    static final class Crystallization extends ActiveAbility {
        Crystallization() { super("crystal_cristal", "Crystallization", "Crystal perd 3 coeurs : l'ennemi le plus proche (10 blocs) est immobilise pendant 3 secondes.", Material.AMETHYST_CLUSTER, 5); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            LivingEntity t = nearest(p); if (t == null) return false;
            pay(p, 3);
            Effects.root(t, 60);
            World w = t.getWorld();
            w.playSound(t.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 1f, 0.8f);
            new BukkitRunnable() { int k = 0; @Override public void run() { k++; if (k > 60 || t.isDead()) { cancel(); return; } Location c = t.getLocation().add(0, 1, 0); for (int i = 0; i < 6; i++) { double a = i * Math.PI / 3 + k * 0.05; w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 0.7, Math.sin(k * 0.1 + i) * 0.8, Math.sin(a) * 0.7), 1, 0, 0, 0, 0, CRYSTAL); } } }.runTaskTimer(Shoja.plugin(), 0L, 1L);
            Effects.say(t, "<aqua>Cristallise par " + p.getName() + " : immobilise 3 s !");
            return true;
        }
    }

    static final class SurchargeDeFlux extends ActiveAbility {
        SurchargeDeFlux() { super("crystal_surcharge", "Surcharge de Flux", "Crystal perd 4 coeurs : l'ennemi le plus proche (10 blocs) subit Lenteur, Faiblesse et Wither II pendant 5 secondes.", Material.END_CRYSTAL, 15); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            LivingEntity t = nearest(p); if (t == null) return false;
            pay(p, 4);
            Effects.give(t, PotionEffectType.SLOWNESS, 0, 100);
            Effects.give(t, PotionEffectType.WEAKNESS, 0, 100);
            Effects.give(t, PotionEffectType.WITHER, 1, 100);
            World w = t.getWorld();
            w.spawnParticle(Particle.DUST, t.getLocation().add(0, 1, 0), 40, 0.5, 0.7, 0.5, 0, CRYSTAL);
            w.spawnParticle(Particle.WITCH, t.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.05);
            w.playSound(t.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.6f);
            Effects.say(t, "<aqua>Surcharge de Flux !");
            return true;
        }
    }

    static final class OeilPourOeil extends UltimateAbility {
        OeilPourOeil() { super("crystal_ult", "Oeil pour Oeil", "Crystal perd 8 coeurs : tous les adversaires de la carte subissent Wither III, Lenteur II et Faiblesse IV pendant 10 secondes.", Material.ENDER_EYE); }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            pay(p, 8);
            int n = 0;
            for (Player o : Bukkit.getOnlinePlayers()) {
                if (!Effects.isEnemy(p, o)) continue;
                Effects.give(o, PotionEffectType.WITHER, 2, 200); Effects.give(o, PotionEffectType.SLOWNESS, 1, 200); Effects.give(o, PotionEffectType.WEAKNESS, 3, 200);
                o.getWorld().spawnParticle(Particle.DUST, o.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0, CRYSTAL);
                Msg.send(o, "<aqua>Oeil pour Oeil de " + p.getName() + " : Wither III, Lenteur II, Faiblesse IV !");
                n++;
            }
            for (LivingEntity e : Effects.enemiesWithin(p, 100)) if (!(e instanceof Player)) { Effects.give(e, PotionEffectType.WITHER, 2, 200); Effects.give(e, PotionEffectType.SLOWNESS, 1, 200); Effects.give(e, PotionEffectType.WEAKNESS, 3, 200); n++; }
            World w = p.getWorld();
            w.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.2f);
            w.spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 80, 1.5, 1.5, 1.5, 0, CRYSTAL);
            Msg.actionBar(p, "<aqua>Oeil pour Oeil : " + n + " adversaire(s) affaibli(s).");
            return true;
        }
    }

    /** Amitie Eternelle : Crystal ne prend plus les coups des adversaires. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player v)) return;
        Long until = NO_HIT.get(v.getUniqueId());
        if (until == null || System.currentTimeMillis() > until) return;
        LivingEntity attacker = DamageListener.livingAttackerOf(e.getDamager());
        if (attacker == null || attacker == v || !Effects.isEnemy(v, attacker)) return;
        e.setCancelled(true);
        v.getWorld().spawnParticle(Particle.DUST, v.getLocation().add(0, 1, 0), 12, 0.4, 0.6, 0.4, 0, CRYSTAL);
    }
}
