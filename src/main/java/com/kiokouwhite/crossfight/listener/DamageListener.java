package com.kiokouwhite.crossfight.listener;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * PvP (ouverture a 30 min, pas de tir ami) et charge de l'ultime :
 * 0.25 % d'ultime par 1 % de degats infliges ou recus (1 % = 0.2 PV).
 */
public final class DamageListener implements Listener {

    private final CrossfightPlugin plugin;

    public DamageListener(CrossfightPlugin plugin) {
        this.plugin = plugin;
    }

    /** Remonte au joueur a l'origine des degats (direct ou via projectile). */
    public static Player attackerOf(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    /** Idem mais accepte toute entite vivante (mannequins, boss). */
    public static org.bukkit.entity.LivingEntity livingAttackerOf(Entity damager) {
        if (damager instanceof org.bukkit.entity.LivingEntity le) return le;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof org.bukkit.entity.LivingEntity le) return le;
        return null;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = attackerOf(e.getDamager());
        if (attacker == null || attacker == victim) return;

        if (!plugin.game().isRunning()) {
            e.setCancelled(true);
            return;
        }
        if (!plugin.game().isPvpEnabled()) {
            e.setCancelled(true);
            Msg.actionBar(attacker, "<red>Le PvP n'est pas encore actif.");
            return;
        }
        if (plugin.teams().sameTeam(attacker, victim)) {
            e.setCancelled(true);
        }
    }

    /** Capacites de l'attaquant et de la victime, puis resistance fractionnaire. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAbilityHooks(EntityDamageByEntityEvent e) {
        if (!plugin.game().isRunning()) return;
        if (!(e.getEntity() instanceof org.bukkit.entity.LivingEntity victim)) return;
        if (!(victim instanceof Player) && !com.kiokouwhite.crossfight.util.Mannequins.isMannequin(victim)) return;
        Player attacker = attackerOf(e.getDamager());
        if (attacker != null && attacker != victim) {
            plugin.abilities().dispatchDamage(attacker, victim, e);
            if (e.isCancelled()) return;
            var cf = plugin.players().get(attacker);
            if (cf.character() != null && cf.character().licence() != null && cf.character().licence().mechanic() != null) {
                cf.character().licence().mechanic().onDealDamage(attacker, victim, e);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamagedHooks(EntityDamageEvent e) {
        if (!plugin.game().isRunning()) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        if (com.kiokouwhite.crossfight.util.Effects.isInvincible(victim)) {
            e.setCancelled(true);
            victim.getWorld().spawnParticle(org.bukkit.Particle.ENCHANTED_HIT, victim.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3);
            return;
        }
        plugin.abilities().dispatchDamaged(victim, e);
        if (e.isCancelled()) return;
        double mul = com.kiokouwhite.crossfight.util.Boosts.damageTakenMultiplier(victim);
        if (mul < 0.999) e.setDamage(e.getDamage() * mul);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUltimateCharge(EntityDamageByEntityEvent e) {
        if (!plugin.game().isRunning()) return;
        Player attacker = attackerOf(e.getDamager());
        if (attacker == null) {
            // Coup d'un mannequin agressif sur un joueur : charge l'ultime de la victime.
            if (e.getEntity() instanceof Player victim && com.kiokouwhite.crossfight.util.Mannequins.isMannequin(e.getDamager())) {
                double percent = e.getFinalDamage() / 20.0 * 100.0;
                double rate = plugin.getConfig().getDouble("ultimate.charge-per-percent-damage", 0.25);
                CfPlayer v = plugin.players().get(victim);
                v.addDamageTaken(percent);
                boolean ready = v.isUltimateReady();
                v.addUltimateCharge(percent * rate);
                if (!ready && v.isUltimateReady()) announceReady(victim);
            }
            return;
        }
        if (attacker == e.getEntity()) return;
        boolean mannequin = com.kiokouwhite.crossfight.util.Mannequins.isMannequin(e.getEntity());
        if (!(e.getEntity() instanceof Player) && !mannequin) return;

        double percentOfBar = e.getFinalDamage() / 20.0 * 100.0;
        if (percentOfBar <= 0) return;
        double rate = plugin.getConfig().getDouble("ultimate.charge-per-percent-damage", 0.25);

        CfPlayer a = plugin.players().get(attacker);
        a.addDamageDealt(percentOfBar);
        boolean aReady = a.isUltimateReady();
        a.addUltimateCharge(percentOfBar * rate);
        if (!aReady && a.isUltimateReady()) announceReady(attacker);

        if (e.getEntity() instanceof Player victim) {
            CfPlayer v = plugin.players().get(victim);
            v.addDamageTaken(percentOfBar);
            boolean vReady = v.isUltimateReady();
            v.addUltimateCharge(percentOfBar * rate);
            if (!vReady && v.isUltimateReady()) announceReady(victim);
        }
    }

    private void announceReady(Player p) {
        Msg.send(p, "<light_purple><bold>Ton Ultime est pret !");
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.5f);
    }

    /** Pas de degats avant le lancement ni pour les joueurs en attente. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        if (!plugin.game().isRunning()) {
            e.setCancelled(true);
            return;
        }
        CfPlayer cf = plugin.players().get(p);
        if (cf.isWaitingRespawn() || !cf.isAlive()) e.setCancelled(true);
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && com.kiokouwhite.crossfight.util.Effects.noFall(p)) e.setCancelled(true);
    }
}
