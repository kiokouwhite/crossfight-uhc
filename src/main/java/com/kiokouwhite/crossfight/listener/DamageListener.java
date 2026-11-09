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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onUltimateCharge(EntityDamageByEntityEvent e) {
        if (!plugin.game().isRunning()) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        Player attacker = attackerOf(e.getDamager());
        if (attacker == null || attacker == victim) return;

        double percentOfBar = e.getFinalDamage() / 20.0 * 100.0;
        if (percentOfBar <= 0) return;
        double rate = plugin.getConfig().getDouble("ultimate.charge-per-percent-damage", 0.25);

        CfPlayer a = plugin.players().get(attacker);
        CfPlayer v = plugin.players().get(victim);
        a.addDamageDealt(percentOfBar);
        v.addDamageTaken(percentOfBar);
        boolean aReady = a.isUltimateReady();
        boolean vReady = v.isUltimateReady();
        a.addUltimateCharge(percentOfBar * rate);
        v.addUltimateCharge(percentOfBar * rate);
        if (!aReady && a.isUltimateReady()) announceReady(attacker);
        if (!vReady && v.isUltimateReady()) announceReady(victim);
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
    }
}
