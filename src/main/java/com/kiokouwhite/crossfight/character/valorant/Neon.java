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
                "<white>Voie Rapide : deux murs de 15 blocs pendant 5 s",
                "<light_purple>Ultime : Force, vitesse auto et epee electrique");
    }

    static final class VitesseSuperieure extends ToggleAbility {
        VitesseSuperieure() {
            super("neon_vitesse", "Vitesse Superieure", "Neon gagne Vitesse II. Jauge de 100 : -5 par seconde active, +1 par seconde au repos.", Material.SUGAR, 5.0, 1.0);
        }
        @Override public boolean onEnable(Player p, CfPlayer cf, AbilityState st) { return true; }
        @Override public void tickActive(Player p, CfPlayer cf, AbilityState st) {
            Effects.refresh(p, PotionEffectType.SPEED, 1, 45);
            p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 0.5, 0), 4, 0.3, 0.3, 0.3, 0.05);
        }
        @Override public void onDisable(Player p, CfPlayer cf, AbilityState st) { }
    }

    static final class EclairRelais extends ActiveAbility {
        EclairRelais() {
            super("neon_relais", "Eclair Relais", "Neon tire une fleche au sol ; 2 secondes apres l'impact, une explosion de 3x3 immobilise les adversaires touches pendant 3 secondes.", Material.LIGHTNING_ROD, 10);
        }
        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Arrow a = Bullets.shoot(p, p.getEyeLocation().getDirection(), 2.0, 0.5, Material.LIGHT_BLUE_CONCRETE, Color.fromRGB(80, 200, 255));
            a.getPersistentDataContainer().set(RELAY_ARROW, PersistentDataType.BYTE, (byte) 1);
            return true;
        }
    }

    @EventHandler
    public void onRelayLand(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow a) || !a.getPersistentDataContainer().has(RELAY_ARROW)) return;
        if (!(a.getShooter() instanceof Player p)) return;
        Location c = a.getLocation();
        World w = c.getWorld();
        Bukkit.getScheduler().runTaskLater(plugin(), () -> {
            w.spawnParticle(Particle.ELECTRIC_SPARK, c, 80, 1.5, 1, 1.5, 0.2);
            w.spawnParticle(Particle.FLASH, c, 1, 0, 0, 0, 0, Color.fromRGB(120, 220, 255));
            w.playSound(c, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.8f, 1.6f);
            for (LivingEntity enemy : Effects.enemiesWithin(p, c, 1.5 + 1.0)) {
                Effects.root(enemy, 60);
                Effects.say(enemy, "<aqua>Immobilise par l'Eclair Relais !");
            }
        }, 40L);
    }

    static final class VoieRapide extends ActiveAbility {
        VoieRapide() {
            super("neon_voie", "Voie Rapide", "A 2 blocs a droite et a gauche de Neon, deux murs incassables de 15 blocs de long et 8 de haut, pendant 5 secondes.", Material.LIGHT_BLUE_STAINED_GLASS, 15);
        }
        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Vector fwd = Effects.towards(p, null);
            Vector side = new Vector(-fwd.getZ(), 0, fwd.getX());
            TempBlocks walls = new TempBlocks().unbreakable();
            Location base = p.getLocation();
            for (int s : new int[]{-2, 2}) {
                for (int i = 0; i < 15; i++) {
                    Location col = base.clone().add(side.clone().multiply(s)).add(fwd.clone().multiply(i));
                    for (int y = 0; y < 8; y++) walls.place(col.clone().add(0, y, 0).getBlock(), Material.LIGHT_BLUE_STAINED_GLASS);
                }
            }
            walls.restoreAfter(100L);
            p.getWorld().playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
            Msg.actionBar(p, "<aqua>Voie Rapide : " + walls.size() + " blocs pendant 5 s");
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
