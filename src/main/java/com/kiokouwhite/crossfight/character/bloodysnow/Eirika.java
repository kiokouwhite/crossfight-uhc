package com.kiokouwhite.crossfight.character.bloodysnow;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.character.duo.Synergy;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

/**
 * Eirika Fischer - Voie de la Nihilite. Les Marques de Sang.
 */
public final class Eirika extends Character implements Listener {

    public Eirika() {
        super("eirika", "Eirika Fischer", Voie.NIHILITE, Material.REDSTONE);
        addAbility(new BloodyRain().bindToWeapon());
        addAbility(new MarqueDeSang());
        addAbility(new AbsorptionDeSang());
        addAbility(new EffroiSanglant());
        addAbility(Synergy.ITEM);
        setUltimate(new ExecutionAtroce());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.IRON_SWORD, 7.0, "<dark_red><bold>Bloody Rain", List.of("<gray>7 dmg."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Bloody Rain : 2 coeurs et une marque aux adversaires a 5 blocs",
                "<white>Marque de Sang : tous les 5 coups, saignement (5 marques max)",
                "<white>Absorption de sang : vole 0.5 coeur par marque a 10 blocs",
                "<white>Effroi sanglant : un effet selon les marques",
                "<light_purple>Ultime : Execution Atroce, 2 a 8 coeurs selon les marques, soigne Eirika");
    }

    static final class BloodyRain extends ActiveAbility {
        BloodyRain() { super("eirika_rain", "Bloody Rain", "Clic droit : 2 coeurs a tous les adversaires a 5 blocs et une Marque de Sang a chacun.", Material.IRON_SWORD, 25); }
        @Override public List<String> weaponLore() { return List.of("<dark_red>Clic droit : <gray>2 coeurs et une marque a 5 blocs"); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            World w = p.getWorld();
            int n = 0;
            for (LivingEntity e : Effects.enemiesWithin(p, 5)) { e.setNoDamageTicks(0); Effects.damage(e, 4.0, p); BloodySnow.addMark(p, e); n++; }
            w.spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 80, 2.5, 1.2, 2.5, 0, BloodySnow.BLOOD);
            w.spawnParticle(Particle.FALLING_LAVA, p.getLocation().add(0, 2.5, 0), 40, 2.5, 0.5, 2.5, 0);
            w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);
            w.playSound(p.getLocation(), Sound.BLOCK_HONEY_BLOCK_BREAK, 1f, 0.5f);
            if (n == 0) Msg.actionBar(p, "<gray>Bloody Rain : personne a 5 blocs.");
            return true;
        }
    }

    static final class MarqueDeSang extends PassiveAbility {
        MarqueDeSang() { super("eirika_marque", "Marque de Sang", "Tous les 5 coups, Eirika pose une Marque de Sang (5 max par adversaire, 6 secondes chacune). Un adversaire marque perd 0.5 coeur toutes les 2 secondes.", Material.REDSTONE); }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { return "<dark_red>Marques <white>" + BloodySnow.HIT_COUNTER.getOrDefault(p.getUniqueId(), 0) + "/5 <gray>max " + BloodySnow.maxMarks(p); }
    }

    static final class AbsorptionDeSang extends ActiveAbility {
        AbsorptionDeSang() { super("eirika_absorption", "Absorption de sang", "Les adversaires a 10 blocs perdent 0.5 coeur par Marque de Sang ; Eirika regagne cette vie.", Material.GHAST_TEAR, 30); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            double total = 0;
            World w = p.getWorld();
            for (LivingEntity e : Effects.enemiesWithin(p, 10)) {
                int n = BloodySnow.markCount(e);
                if (n <= 0) continue;
                e.setNoDamageTicks(0);
                Effects.damage(e, n * 1.0, p);
                total += n;
                for (double k = 0; k <= 1; k += 0.1) w.spawnParticle(Particle.DUST, e.getLocation().add(0, 1, 0).add(p.getLocation().add(0, 1, 0).subtract(e.getLocation().add(0, 1, 0)).toVector().multiply(k)), 1, 0, 0, 0, 0, BloodySnow.BLOOD);
            }
            if (total <= 0) { Msg.actionBar(p, "<red>Aucun adversaire marque a 10 blocs."); return false; }
            Effects.heal(p, total);
            w.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 0.6f);
            Msg.actionBar(p, "<dark_red>Absorption de sang : +" + (total / 2) + " coeur(s)");
            return true;
        }
    }

    static final class EffroiSanglant extends ActiveAbility {
        EffroiSanglant() { super("eirika_effroi", "Effroi sanglant", "Chaque adversaire a 30 blocs subit un effet selon ses marques : 1 Nausee 7 s, 2 Cecite 10 s, 3 Obscurite 15 s, 4 Lenteur 1 min, 5 Wither 10 s.", Material.WITHER_ROSE, 45); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            int n = 0;
            for (LivingEntity e : Effects.enemiesWithin(p, 30)) {
                int m = BloodySnow.markCount(e);
                if (m <= 0) continue;
                switch (Math.min(5, m)) {
                    case 1 -> Effects.give(e, PotionEffectType.NAUSEA, 0, 140);
                    case 2 -> Effects.give(e, PotionEffectType.BLINDNESS, 0, 200);
                    case 3 -> Effects.give(e, PotionEffectType.DARKNESS, 0, 300);
                    case 4 -> Effects.give(e, PotionEffectType.SLOWNESS, 0, 1200);
                    default -> Effects.give(e, PotionEffectType.WITHER, 0, 200);
                }
                e.getWorld().spawnParticle(Particle.DUST, e.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0, BloodySnow.BLOOD);
                n++;
            }
            if (n == 0) { Msg.actionBar(p, "<red>Aucun adversaire marque a 30 blocs."); return false; }
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.7f, 1.4f);
            Msg.actionBar(p, "<dark_red>Effroi sanglant : " + n + " adversaire(s).");
            return true;
        }
    }

    static final class ExecutionAtroce extends UltimateAbility {
        ExecutionAtroce() { super("eirika_ult", "Execution Atroce", "Chaque adversaire a 30 blocs subit des degats selon ses marques (1 : 2 coeurs, 2 : 3, 3 : 4, 4 : 5, 5 : 8), puis recoit une marque. Eirika regagne la vie retiree, le surplus en absorption.", Material.NETHERITE_SWORD); }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            double total = 0; int n = 0;
            World w = p.getWorld();
            for (LivingEntity e : Effects.enemiesWithin(p, 30)) {
                int m = BloodySnow.markCount(e);
                double hearts = m <= 0 ? 0 : m == 1 ? 2 : m == 2 ? 3 : m == 3 ? 4 : m == 4 ? 5 : 8;
                if (hearts > 0) {
                    double before = e.getHealth();
                    e.setNoDamageTicks(0);
                    Effects.damage(e, hearts * 2, p);
                    total += Math.max(0, before - e.getHealth());
                    w.spawnParticle(Particle.DUST, e.getLocation().add(0, 1, 0), 40, 0.4, 0.7, 0.4, 0, BloodySnow.BLOOD);
                    w.playSound(e.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
                }
                BloodySnow.addMark(p, e);
                n++;
            }
            double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            double room = max - p.getHealth();
            double heal = Math.min(room, total);
            if (heal > 0) Effects.heal(p, heal);
            double extra = total - heal;
            if (extra > 0) p.setAbsorptionAmount(Math.min(40, p.getAbsorptionAmount() + extra));
            w.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 1.6f);
            Msg.actionBar(p, "<dark_red>Execution Atroce : " + n + " adversaire(s), " + (int) (total / 2) + " coeur(s) regagne(s).");
            return true;
        }
    }
}
