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
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Takumi - Path of Hunt.
 *
 * Hypothese : l'arc a Infinite et une fleche est fournie, sinon un archer sans
 * fleches est injouable en debut de partie.
 */
public final class Takumi extends Character implements Listener {

    static final NamespacedKey TEMP_SWORD = new NamespacedKey("crossfight", "takumi_sword");
    static final int BASE_POWER = 2;

    public Takumi() {
        super("takumi", "Takumi", Voie.CHASSE, Material.BOW);
        addAbility(new YumiFujin());
        addAbility(new ContreRapproche());
        addAbility(new Vengeance());
        addAbility(new Furie());
        setUltimate(new RayonDemoniaque());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.bow(BASE_POWER, true, "<gold><bold>Yumi Fujin",
                Items.wrap("Puissance II. Les fleches infligent Lenteur pendant 10 secondes."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Yumi Fujin (Power II) : Lenteur 10 s",
                "<white>Contre Rapproche : epee 9 dmg pendant 30 s",
                "<white>Vengeance : +1 Power par 3 coeurs perdus (5 s)",
                "<white>Furie : +1 Power et Vitesse II contre 5 coeurs",
                "<light_purple>Ultime : rayon 10x30 blocs, 5 coeurs");
    }

    /** Recalcule le niveau de Puissance de l'arc selon les bonus temporaires. */
    static void refreshPower(Player p) {
        var engine = plugin().abilities();
        var ch = plugin().characters().character("takumi");
        int bonus = 0;
        bonus += engine.state(p, ch.ability("takumi_vengeance")).get("bonus", 0);
        bonus += engine.state(p, ch.ability("takumi_furie")).get("bonus", 0);
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if ("takumi".equals(Items.tag(it, AbilityEngine.WEAPON_KEY))) Weapons.setPower(it, BASE_POWER + bonus);
        }
    }

    static boolean isBowLocked(Player p) {
        var st = plugin().abilities().state(p, plugin().characters().character("takumi").ability("takumi_contre"));
        return System.currentTimeMillis() < (long) st.get("lockedUntil", 0L);
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        CfPlayer cf = plugin().players().get(p);
        if (cf.character() == null || !"takumi".equals(cf.character().id())) return;
        if (isBowLocked(p)) {
            e.setCancelled(true);
            Msg.actionBar(p, "<red>Ton arc est range pendant Contre Rapproche.");
        }
    }

    private static com.kiokouwhite.crossfight.CrossfightPlugin plugin() {
        return com.kiokouwhite.crossfight.CrossfightPlugin.get();
    }

    // ------------------------------------------------------------------ arme

    static final class YumiFujin extends PassiveAbility {
        YumiFujin() {
            super("takumi_yumi", "Yumi Fujin", "Les fleches infligent Lenteur pendant 10 secondes.", Material.ARROW);
        }

        @Override
        public void onDealDamage(Player attacker, org.bukkit.entity.LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) {
            if (e.getDamager() instanceof AbstractArrow) {
                Effects.give(victim, PotionEffectType.SLOWNESS, 0, 20 * 10);
            }
        }

        @Override
        public void onEquip(Player p, CfPlayer cf) {
            // Une fleche pour l'Infinite.
            boolean has = false;
            for (ItemStack it : p.getInventory().getContents()) if (it != null && it.getType() == Material.ARROW) { has = true; break; }
            if (!has) p.getInventory().addItem(new ItemStack(Material.ARROW));
        }
    }

    // --------------------------------------------------------------- pouvoirs

    static final class ContreRapproche extends ActiveAbility {
        ContreRapproche() {
            super("takumi_contre", "Contre Rapproche", "Takumi recoit une epee de 9 degats pendant 30 secondes mais ne peut plus tirer a l'arc.",
                    Material.IRON_SWORD, 90);
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            ItemStack sword = Weapons.melee(Material.IRON_SWORD, 9, "<red><bold>Lame de Takumi", Items.wrap("Disparait au bout de 30 secondes."));
            Items.tag(sword, TEMP_SWORD, "1");
            Items.tag(sword, AbilityEngine.WEAPON_KEY, "takumi_temp");
            var left = p.getInventory().addItem(sword);
            if (!left.isEmpty()) {
                Msg.actionBar(p, "<red>Inventaire plein.");
                return false;
            }
            st.set("lockedUntil", System.currentTimeMillis() + 30_000L);
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 0.8f);
            Bukkit.getScheduler().runTaskLater(plugin(), () -> removeSword(p), 20L * 30);
            return true;
        }

        static void removeSword(Player p) {
            var inv = p.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                if (Items.tag(inv.getItem(i), TEMP_SWORD) != null) inv.setItem(i, null);
            }
            if (p.isOnline()) Msg.actionBar(p, "<gray>Ta lame disparait, ton arc est de nouveau pret.");
        }

        @Override
        public void onUnequip(Player p, CfPlayer cf) {
            removeSword(p);
        }
    }

    static final class Vengeance extends ActiveAbility {
        Vengeance() {
            super("takumi_vengeance", "Vengeance", "Takumi gagne un niveau de Puissance par tranche de 3 coeurs perdus, pendant 5 secondes.",
                    Material.GLOWSTONE_DUST, 30);
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            int lostHearts = (int) Math.floor((max - p.getHealth()) / 2.0);
            int bonus = lostHearts / 3;
            if (bonus <= 0) {
                Msg.actionBar(p, "<red>Pas assez de coeurs perdus (3 minimum).");
                return false;
            }
            st.set("bonus", bonus);
            refreshPower(p);
            Msg.actionBar(p, "<red>Vengeance : Puissance +" + bonus + " pendant 5 s");
            p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f);
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                st.set("bonus", 0);
                if (p.isOnline()) refreshPower(p);
            }, 100L);
            return true;
        }
    }

    static final class Furie extends ActiveAbility {
        Furie() {
            super("takumi_furie", "Furie", "Takumi gagne un niveau de Puissance et Vitesse II pendant 10 secondes, mais perd 5 coeurs.",
                    Material.BLAZE_POWDER, 10);
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            if (p.getHealth() <= 10.0) {
                Msg.actionBar(p, "<red>Pas assez de vie pour Furie.");
                return false;
            }
            p.setHealth(p.getHealth() - 10.0);
            st.set("bonus", 1);
            refreshPower(p);
            Effects.give(p, PotionEffectType.SPEED, 1, 20 * 10);
            p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.02);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.7f);
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                st.set("bonus", 0);
                if (p.isOnline()) refreshPower(p);
            }, 200L);
            return true;
        }
    }

    // ----------------------------------------------------------------- ultime

    static final class RayonDemoniaque extends UltimateAbility {
        RayonDemoniaque() {
            super("takumi_ult", "Rayon Demoniaque",
                    "Takumi charge 4 secondes puis tire un rayon de 10 blocs de large et 30 de long infligeant 5 coeurs a tous les ennemis visibles.",
                    Material.AMETHYST_SHARD);
        }

        @Override
        public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            Effects.give(p, PotionEffectType.SLOWNESS, 2, 80);
            Msg.actionBar(p, "<light_purple>Rayon Demoniaque en charge...");
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    ticks += 5;
                    if (!p.isOnline() || p.isDead()) { cancel(); return; }
                    p.getWorld().spawnParticle(Particle.PORTAL, p.getEyeLocation().add(p.getLocation().getDirection()), 20, 0.2, 0.2, 0.2, 0.5);
                    p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1f, 0.5f + ticks / 80f);
                    if (ticks >= 80) {
                        cancel();
                        fire(p);
                    }
                }
            }.runTaskTimer(plugin(), 0L, 5L);
            return true;
        }

        private void fire(Player p) {
            Location eye = p.getEyeLocation();
            Vector dir = eye.getDirection().normalize();
            List<org.bukkit.entity.LivingEntity> hit = new ArrayList<>();
            for (org.bukkit.entity.LivingEntity enemy : Effects.enemiesWithin(p, 35)) {
                Vector rel = enemy.getLocation().add(0, 1, 0).toVector().subtract(eye.toVector());
                double along = rel.dot(dir);
                if (along < 0 || along > 30) continue;
                double perp = rel.clone().subtract(dir.clone().multiply(along)).length();
                if (perp > 5.0) continue;
                if (!p.hasLineOfSight(enemy)) continue;
                hit.add(enemy);
            }
            for (org.bukkit.entity.LivingEntity enemy : hit) Effects.damage(enemy, 10.0, p);
            for (double d = 0; d <= 30; d += 1.0) {
                Location l = eye.clone().add(dir.clone().multiply(d));
                l.getWorld().spawnParticle(Particle.DRAGON_BREATH, l, 12, 1.5, 1.5, 1.5, 0.01, 1.0f);
                l.getWorld().spawnParticle(Particle.END_ROD, l, 4, 0.8, 0.8, 0.8, 0.02);
            }
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 1f, 0.5f);
            Msg.send(p, hit.isEmpty() ? "<gray>Rayon Demoniaque : aucun ennemi touche." : "<light_purple>Rayon Demoniaque : " + hit.size() + " ennemi(s) touche(s) !");
        }
    }
}
