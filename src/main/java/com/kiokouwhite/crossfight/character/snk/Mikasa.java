package com.kiokouwhite.crossfight.character.snk;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.ChoiceMenu;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Mannequins;
import com.kiokouwhite.crossfight.util.Msg;
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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mikasa Ackerman - Path of Destruction.
 */
public final class Mikasa extends Character {

    static final Particle.DustOptions SCARF = new Particle.DustOptions(Color.fromRGB(200, 30, 40), 1.2f);

    public Mikasa() {
        super("mikasa", "Mikasa Ackerman", Voie.DESTRUCTION, Material.DIAMOND_SWORD);
        addAbility(new StyleForce());
        addAbility(new Assommage());
        addAbility(new SusALaCible());
        addAbility(new LoyauteAckermann());
        setUltimate(new JeLeSauverais());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.DIAMOND_SWORD, 9.5, "<gold><bold>Lames (Style Force)", List.of("<gray>Equipement tridimensionnel de Mikasa."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Grappin Style Force : Force permanente",
                "<white>Assommage : le prochain coup etourdit 3 s",
                "<white>Sus a la Cible : choisit un adversaire a traquer",
                "<white>Loyaute Ackermann : Force, Speed, Resistance si Eren est bas",
                "<light_purple>Ultime : protege Eren (invincible a 10 blocs) 30 s");
    }

    /** Eren de l'equipe a moins de 5 coeurs : les capacites de Mikasa sont ameliorees. */
    static boolean erenLow(Player mikasa) {
        Player eren = SNK.erenOf(mikasa);
        return eren != null && SNK.isLow(eren);
    }

    /** L'equipement tridimensionnel, qui donne en plus Force (cumulable). */
    static final class StyleForce extends SNK.OdmGear {
        StyleForce() { super(SNK.Style.FORCE, "Style Force : Mikasa, tres a l'aise avec l'equipement, gagne Force (cumulable)."); }
        @Override public void onEquip(Player p, CfPlayer cf) { Boosts.set(p, "mikasa_force", new Boosts.Boost(1, 0, 0, 0)); }
        @Override public void onUnequip(Player p, CfPlayer cf) { Boosts.remove(p, "mikasa_force"); Boosts.remove(p, "mikasa_loyaute"); Boosts.remove(p, "mikasa_ult"); }
    }

    // ---------------------------------------------------------------- pouvoirs

    static final class Assommage extends ActiveAbility {
        Assommage() {
            super("mikasa_assommage", "Assommage", "Le prochain coup de Mikasa etourdit l'ennemi : immobile et Cecite pendant 3 secondes. Si Eren a moins de 5 coeurs, le coup inflige aussi Wither 5 secondes.", Material.IRON_NUGGET, 15);
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            st.set("armed", true);
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 0.8f);
            Msg.actionBar(p, "<yellow>Assommage pret : le prochain coup etourdit.");
            return true;
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            return st.get("armed", false) ? "<yellow>Assommage <white>arme" : null;
        }
        @Override public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) {
            if (!st.get("armed", false) || e.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
            st.set("armed", false);
            Effects.root(victim, 60);
            Effects.give(victim, PotionEffectType.BLINDNESS, 0, 60);
            if (erenLow(attacker)) Effects.give(victim, PotionEffectType.WITHER, 0, 100);
            World w = victim.getWorld();
            w.spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1.8, 0), 20, 0.3, 0.2, 0.3, 0.2);
            w.spawnParticle(Particle.ENCHANTED_HIT, victim.getLocation().add(0, 2.0, 0), 10, 0.3, 0.1, 0.3, 0.1);
            w.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
            w.playSound(victim.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.8f);
            Effects.say(victim, "<red>Assomme par " + attacker.getName() + " !");
        }
    }

    static final class SusALaCible extends ActiveAbility {
        SusALaCible() {
            super("mikasa_cible", "Sus a la Cible", "Menu des adversaires a 100 blocs : la cible choisie est en surbrillance 1 minute et Mikasa gagne Speed 30 secondes. Si Eren a moins de 5 coeurs, Mikasa gagne aussi Force contre cette cible.", Material.SPYGLASS, 60);
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            List<LivingEntity> targets = new ArrayList<>();
            for (LivingEntity e : Effects.enemiesWithin(p, 100)) if (e instanceof Player || Mannequins.isMannequin(e)) targets.add(e);
            if (targets.isEmpty()) { Msg.actionBar(p, "<red>Aucun adversaire a moins de 100 blocs."); return false; }
            List<ChoiceMenu.Option> options = new ArrayList<>();
            for (LivingEntity t : targets) {
                String name = t instanceof Player tp ? tp.getName() : "Mannequin";
                int dist = (int) t.getLocation().distance(p.getLocation());
                options.add(new ChoiceMenu.Option(t instanceof Player ? Material.PLAYER_HEAD : Material.ZOMBIE_HEAD, name,
                        List.of("<gray>A " + dist + " blocs", "<gray>Vie : <white>" + (int) Math.round(t.getHealth() / t.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue() * 100) + "%"),
                        () -> choose(p, cf, st, t)));
            }
            ChoiceMenu.open(p, "<dark_red><bold>Sus a la Cible", options);
            return false;
        }
        void choose(Player p, CfPlayer cf, AbilityState st, LivingEntity t) {
            if (t.isDead()) return;
            st.startCooldown((int) Math.ceil(cooldownSeconds() * cf.cooldownFactor()));
            Effects.give(p, PotionEffectType.SPEED, 0, 600);
            Effects.give(t, PotionEffectType.GLOWING, 0, 1200);
            if (erenLow(p)) { st.set("focus", t.getUniqueId().toString()); st.set("focusUntil", System.currentTimeMillis() + 30000L); }
            String name = t instanceof Player tp ? tp.getName() : "le mannequin";
            Msg.send(p, "<red>Sus a la Cible : <white>" + name + "</white> est traque" + (erenLow(p) ? " <gray>(Force contre lui)" : "") + ".");
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WOLF_GROWL, 1f, 0.8f);
            p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 20, 0.4, 0.5, 0.4, 0, SCARF);
        }
        @Override public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) {
            if (System.currentTimeMillis() > st.get("focusUntil", 0L)) return;
            if (victim.getUniqueId().toString().equals(st.get("focus", ""))) e.setDamage(e.getDamage() + 3.0);
        }
    }

    static final class LoyauteAckermann extends PassiveAbility {
        LoyauteAckermann() {
            super("mikasa_loyaute", "Loyaute Ackermann", "Si Eren a moins de 5 coeurs, Mikasa gagne Force, Vitesse et Resistance, et ses capacites sont ameliorees.");
        }
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) {
            boolean low = erenLow(p);
            SNK.boostIf(p, "mikasa_loyaute", low, new Boosts.Boost(1, 1, 1, 0));
            if (low && !st.get("warned", false)) { st.set("warned", true); Msg.actionBar(p, "<red>Eren est en danger ! Loyaute Ackermann active."); }
            if (!low) st.set("warned", false);
        }
    }

    // ----------------------------------------------------------------- ultime

    /** Mikasa se teleporte sur Eren ; tant qu'il est a 10 blocs il est invincible et elle gagne Force, Resistance et Speed (30 s). */
    static final class JeLeSauverais extends UltimateAbility {
        JeLeSauverais() {
            super("mikasa_ult", "Je le sauverai", "Mikasa se teleporte sur Eren : pendant 30 secondes, tant qu'Eren est a moins de 10 blocs, il est invincible et Mikasa gagne Force, Resistance et Speed. Sans Eren, Mikasa garde les bonus.", Material.RED_WOOL);
        }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            Player eren = SNK.erenOf(p);
            World w = p.getWorld();
            if (eren != null) {
                Location to = eren.getLocation().clone();
                Vector back = to.getDirection().setY(0).multiply(-1.2);
                to.add(back).setDirection(eren.getLocation().toVector().subtract(to.toVector()));
                w.spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0, SCARF);
                p.teleport(to);
                w.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.4f);
                Msg.send(eren, "<red>Mikasa te protege : tu es invincible tant qu'elle est a moins de 10 blocs.");
            } else {
                Msg.send(p, "<gray>Pas d'Eren en vie dans l'equipe : Mikasa garde les bonus pour elle.");
            }
            Boosts.set(p, "mikasa_ult", new Boosts.Boost(1, 1, 1, 0));
            w.playSound(p.getLocation(), Sound.ENTITY_WOLF_GROWL, 1f, 0.5f);
            UUID erenId = eren == null ? null : eren.getUniqueId();
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || p.isDead() || t > 30) { Boosts.remove(p, "mikasa_ult"); cancel(); return; }
                    Player e = erenId == null ? null : Bukkit.getPlayer(erenId);
                    if (e != null && !e.isDead() && e.getWorld() == p.getWorld() && e.getLocation().distance(p.getLocation()) <= 10) {
                        SNK.invincible(e, 1200L);
                        w.spawnParticle(Particle.DUST, e.getLocation().add(0, 1.2, 0), 6, 0.4, 0.6, 0.4, 0, SCARF);
                    }
                    w.spawnParticle(Particle.DUST, p.getLocation().add(0, 1.4, 0), 10, 0.4, 0.3, 0.4, 0, SCARF);
                }
            }.runTaskTimer(SNK.plugin(), 0L, 20L);
            return true;
        }
    }
}
