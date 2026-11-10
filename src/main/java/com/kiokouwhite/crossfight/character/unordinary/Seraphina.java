package com.kiokouwhite.crossfight.character.unordinary;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Seraphina - Path of Nihility.
 */
public final class Seraphina extends Character {

    static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 215, 120), 1.0f);

    public Seraphina() {
        super("seraphina", "Seraphina", Voie.NIHILITE, Material.CLOCK);
        addAbility(Unordinary.AURA);
        addAbility(new RetourDansLeTemps());
        addAbility(new ChocTemporel());
        addAbility(new ArretTemporel());
        setUltimate(new ExplosionTemporelle());
    }

    @Override public ItemStack createWeapon() { return null; }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Epee d'Aura a crafter (actif selon le craft)",
                "<white>Retour dans le temps : revient 5 s en arriere",
                "<white>Choc Temporel : tous les 6 coups, immobilise 2 s",
                "<white>Arret Temporel : fige les ennemis a 10 blocs 3 s",
                "<light_purple>Ultime : immobilise tous les ennemis a 30 blocs 5 s");
    }

    static void clockBurst(World w, Location c, double radius, int count) {
        for (int i = 0; i < count; i++) {
            double ang = i * 2 * Math.PI / count;
            w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(ang) * radius, 0.2, Math.sin(ang) * radius), 1, 0, 0, 0, 0, GOLD);
        }
        w.spawnParticle(Particle.END_ROD, c.clone().add(0, 1, 0), 12, radius * 0.5, 0.5, radius * 0.5, 0.02);
    }

    /** Une position par seconde, les 6 dernieres (le hook tick tourne une fois par seconde). */
    static final class RetourDansLeTemps extends ActiveAbility {
        private final Map<UUID, Deque<Location>> history = new HashMap<>();
        RetourDansLeTemps() {
            super("seraphina_retour", "Retour dans le temps", "Seraphina revient a la position ou elle etait 5 secondes auparavant.", Material.CLOCK, 10);
        }
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) {
            Deque<Location> h = history.computeIfAbsent(p.getUniqueId(), u -> new ArrayDeque<>());
            h.addLast(p.getLocation().clone());
            while (h.size() > 6) h.removeFirst();
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Deque<Location> h = history.get(p.getUniqueId());
            if (h == null || h.isEmpty()) { Msg.actionBar(p, "<red>Pas encore de passe a rejoindre."); return false; }
            Location back = h.peekFirst();
            World w = p.getWorld();
            w.spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
            w.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1f, 1.6f);
            Effects.noFallUntil(p, 3000);
            p.teleport(back);
            w.spawnParticle(Particle.REVERSE_PORTAL, back.clone().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0.05);
            w.playSound(back, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.4f);
            h.clear();
            return true;
        }
        @Override public void onUnequip(Player p, CfPlayer cf) { history.remove(p.getUniqueId()); }
    }

    static final class ChocTemporel extends PassiveAbility {
        ChocTemporel() {
            super("seraphina_choc", "Choc Temporel", "Tous les 6 coups infliges, l'adversaire touche est immobilise pendant 2 secondes.");
        }
        @Override public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) {
            if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
            int hits = st.get("hits", 0) + 1;
            if (hits < 6) { st.set("hits", hits); return; }
            st.set("hits", 0);
            Effects.root(victim, 40);
            clockBurst(victim.getWorld(), victim.getLocation(), 1.0, 16);
            victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.8f, 1.6f);
            Effects.say(victim, "<gold>Choc Temporel : immobilise 2 s !");
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { return "<gold>Choc <white>" + st.get("hits", 0) + "/6"; }
    }

    static final class ArretTemporel extends ActiveAbility {
        ArretTemporel() {
            super("seraphina_arret", "Arret Temporel", "Seraphina arrete le temps a 10 blocs : les ennemis sont immobilises et aveugles 3 secondes, elle gagne Vitesse III, mais ne peut pas les frapper pendant ce temps.", Material.RECOVERY_COMPASS, 15);
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            List<LivingEntity> enemies = Effects.enemiesWithin(p, 10);
            World w = p.getWorld();
            w.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.6f);
            w.playSound(p.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1.5f);
            clockBurst(w, p.getLocation(), 10, 60);
            Effects.give(p, PotionEffectType.SPEED, 2, 60);
            java.util.List<String> frozen = new java.util.ArrayList<>();
            for (LivingEntity enemy : enemies) {
                Effects.root(enemy, 60);
                Effects.give(enemy, PotionEffectType.BLINDNESS, 0, 60);
                frozen.add(enemy.getUniqueId().toString());
                w.spawnParticle(Particle.DUST, enemy.getLocation().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0, GOLD);
            }
            st.set("frozen", frozen);
            st.set("frozenUntil", System.currentTimeMillis() + 3000L);
            Msg.actionBar(p, "<gold>Arret Temporel : " + enemies.size() + " ennemi(s) fige(s) 3 s");
            return true;
        }
        /** Pendant l'arret, Seraphina ne peut pas frapper les ennemis figes. */
        @Override public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) {
            if (System.currentTimeMillis() > st.get("frozenUntil", 0L)) return;
            List<String> frozen = st.get("frozen", null);
            if (frozen != null && frozen.contains(victim.getUniqueId().toString())) {
                e.setCancelled(true);
                Msg.actionBar(attacker, "<gray>Le temps est arrete : impossible de frapper.");
            }
        }
    }

    static final class ExplosionTemporelle extends UltimateAbility {
        ExplosionTemporelle() {
            super("seraphina_ult", "Explosion temporelle", "Seraphina immobilise tous les ennemis dans un rayon de 30 blocs pendant 5 secondes.", Material.NETHER_STAR);
        }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            World w = p.getWorld();
            Location c = p.getLocation();
            w.playSound(c, Sound.BLOCK_BELL_RESONATE, 1f, 0.5f);
            w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
            int n = 0;
            for (LivingEntity enemy : Effects.enemiesWithin(p, 30)) {
                Effects.root(enemy, 100);
                Effects.say(enemy, "<gold>Explosion temporelle : fige 5 s !");
                n++;
            }
            final int count = n;
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (t > 10) { cancel(); return; }
                    clockBurst(w, c, t * 3.0, 24 + t * 6);
                }
            }.runTaskTimer(Unordinary.plugin(), 0L, 1L);
            Msg.actionBar(p, "<gold>Explosion temporelle : " + count + " ennemi(s) fige(s)");
            return true;
        }
    }
}
