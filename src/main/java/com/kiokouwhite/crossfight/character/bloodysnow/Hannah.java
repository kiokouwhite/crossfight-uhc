package com.kiokouwhite.crossfight.character.bloodysnow;

import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.character.duo.Synergy;
import com.kiokouwhite.crossfight.listener.DamageListener;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Mannequins;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hannah Fedorava - Voie de la Destruction. Elle encaisse, et transforme les coups recus en protection ou en rage.
 */
public final class Hannah extends Character implements Listener {

    /** Provocation : adversaire -> (Hannah, expiration). */
    static final Map<UUID, Object[]> TAUNTED = new HashMap<>();

    public Hannah() {
        super("hannah", "Hannah Fedorava", Voie.DESTRUCTION, Material.SHIELD);
        addAbility(new AldifaeBlade().bindToWeapon());
        addAbility(new ResisterPourRegner());
        addAbility(new ProtectionChevaleresque());
        addAbility(new AbnegationEnrageante());
        addAbility(Synergy.ITEM);
        setUltimate(new RelachementDeRage());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.IRON_SWORD, 6.0, "<gold><bold>Aldifae Blade", List.of("<gray>6 dmg. Resistance II tant qu'elle est en main."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Aldifae Blade : Resistance II en main",
                "<white>Resister pour mieux regner : chaque coup recu charge la barre (10 max)",
                "<white>Protection chevaleresque : la barre devient des coeurs d'absorption",
                "<white>Abnegation Enrageante : provoque les adversaires a 10 blocs 10 s",
                "<light_purple>Ultime : Relachement de Rage, la barre devient de la Force");
    }

    static AbilityState bar(Player p) {
        CfPlayer cf = BloodySnow.plugin().players().get(p);
        Ability a = cf.character() == null ? null : cf.character().ability("hannah_resister");
        return a == null ? null : BloodySnow.plugin().abilities().state(p, a);
    }
    static int resistance(Player p) { AbilityState st = bar(p); return st == null ? 0 : st.get("hits", 0); }
    static int drain(Player p) { AbilityState st = bar(p); if (st == null) return 0; int n = st.get("hits", 0); st.set("hits", 0); return n; }

    static final class AldifaeBlade extends PassiveAbility {
        AldifaeBlade() { super("hannah_blade", "Aldifae Blade", "Tant qu'Hannah tient son arme, elle a Resistance II.", Material.IRON_SWORD); }
        @Override public List<String> weaponLore() { return List.of("<gold>Aldifae Blade : <gray>Resistance II en main"); }
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if ("hannah".equals(Items.tag(hand, AbilityEngine.WEAPON_KEY))) Effects.give(p, PotionEffectType.RESISTANCE, 1, 40);
        }
    }

    static final class ResisterPourRegner extends PassiveAbility {
        ResisterPourRegner() { super("hannah_resister", "Resister pour mieux regner", "Chaque coup recu par Hannah charge sa barre de resistance (10 coups max), qui alimente ses pouvoirs.", Material.SHIELD); }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { int n = st.get("hits", 0); return "<gold>Resistance <white>" + n + "/10"; }
        @Override public void onDamaged(Player victim, EntityDamageEvent e, AbilityState st) {
            if (!(e instanceof EntityDamageByEntityEvent be)) return;
            LivingEntity attacker = DamageListener.livingAttackerOf(be.getDamager());
            if (attacker == null || attacker == victim || !Effects.isEnemy(victim, attacker)) return;
            int before = st.get("hits", 0);
            int n = Math.min(10, before + 1);
            st.set("hits", n);
            if (n == 10 && before < 10) com.kiokouwhite.crossfight.util.Sounds.ability(victim, "hannah_resister");
            victim.getWorld().spawnParticle(Particle.DUST, victim.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0, new Particle.DustOptions(Color.fromRGB(255, 200, 60), 0.9f));
        }
    }

    static final class ProtectionChevaleresque extends ActiveAbility {
        ProtectionChevaleresque() { super("hannah_protection", "Protection chevaleresque", "Hannah vide sa barre de resistance : elle et son partenaire gagnent autant de coeurs d'absorption.", Material.GOLDEN_CHESTPLATE, 5); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            int n = resistance(p);
            if (n <= 0) { Msg.actionBar(p, "<red>Barre de resistance vide."); return false; }
            drain(p);
            for (Player m : new Player[]{p, BloodySnow.partner(p)}) {
                if (m == null) continue;
                m.setAbsorptionAmount(Math.min(40, m.getAbsorptionAmount() + n * 2));
                m.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, m.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.1);
                m.playSound(m.getLocation(), Sound.ITEM_ARMOR_EQUIP_GOLD, 1f, 1.2f);
                Msg.actionBar(m, "<gold>Protection chevaleresque : +" + n + " coeur(s) d'absorption");
            }
            return true;
        }
    }

    static final class AbnegationEnrageante extends ActiveAbility {
        AbnegationEnrageante() { super("hannah_abnegation", "Abnegation Enrageante", "Les adversaires a 10 blocs sont provoques pendant 10 secondes : ils ne peuvent attaquer qu'Hannah.", Material.RED_BANNER, 30); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            int n = 0;
            for (LivingEntity e : Effects.enemiesWithin(p, 10)) {
                TAUNTED.put(e.getUniqueId(), new Object[]{p.getUniqueId(), System.currentTimeMillis() + 10000L});
                if (e instanceof Mob mob) mob.setTarget(com.kiokouwhite.crossfight.util.MannequinPlayer.real((LivingEntity) p));
                if (e instanceof Player ep) Msg.send(ep, "<red>Provoque par " + p.getName() + " : tu ne peux attaquer qu'elle pendant 10 s !");
                e.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, e.getLocation().add(0, 2, 0), 5, 0.3, 0.2, 0.3);
                n++;
            }
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.6f, 1.4f);
            Msg.actionBar(p, "<red>Abnegation Enrageante : " + n + " adversaire(s) provoque(s).");
            return true;
        }
    }

    static final class RelachementDeRage extends UltimateAbility {
        RelachementDeRage() { super("hannah_ult", "Relachement de Rage", "Hannah vide sa barre de resistance : chaque coup devient 0.25 point de Force pendant 30 secondes.", Material.BLAZE_POWDER); }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            int n = drain(p);
            if (n <= 0) { Msg.actionBar(p, "<red>Barre de resistance vide."); return false; }
            Boosts.set(p, "hannah_rage", new Boosts.Boost(n * 0.25, 0, 0, 0));
            Bukkit.getScheduler().runTaskLater(BloodySnow.plugin(), () -> Boosts.remove(p, "hannah_rage"), 20L * 30);
            p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 40, 0.5, 0.7, 0.5, 0.05);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 0.8f);
            Msg.actionBar(p, "<gold>Relachement de Rage : Force " + (n * 0.25) + " pendant 30 s");
            return true;
        }
    }

    /** Un adversaire provoque ne peut frapper qu'Hannah. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTauntedHit(EntityDamageByEntityEvent e) {
        LivingEntity attacker = DamageListener.livingAttackerOf(e.getDamager());
        if (attacker == null) return;
        Object[] t = TAUNTED.get(attacker.getUniqueId());
        if (t == null) return;
        if (System.currentTimeMillis() > (long) t[1]) { TAUNTED.remove(attacker.getUniqueId()); return; }
        if (e.getEntity().getUniqueId().equals(t[0])) return;
        if (!(e.getEntity() instanceof Player) && !Mannequins.isMannequin(e.getEntity())) return;
        e.setCancelled(true);
        if (attacker instanceof Player ap) Msg.actionBar(ap, "<red>Provoque : tu ne peux attaquer qu'Hannah !");
    }
}
