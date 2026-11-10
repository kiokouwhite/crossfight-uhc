package com.kiokouwhite.crossfight.listener;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Mannequins;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Combat facon 1.8 :
 *  - aucun temps de recharge d'attaque (spam clic a pleine puissance) ;
 *  - pas d'attaque balayante ;
 *  - maintenir le clic gauche sur un ennemi enchaine les coups tout seul.
 */
public final class LegacyCombatListener implements Listener {

    private static final double NO_COOLDOWN_ATTACK_SPEED = 1024.0;
    private static final double REACH = 3.0;

    private final CrossfightPlugin plugin;
    /** Dernier tick ou le joueur a reellement frappe une entite (clic normal). */
    private final Map<UUID, Integer> lastHitTick = new HashMap<>();

    public LegacyCombatListener(CrossfightPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("pvp.legacy-combat", true);
    }

    /** Supprime le temps de recharge d'attaque du joueur. */
    public void apply(Player p) {
        AttributeInstance speed = p.getAttribute(Attribute.ATTACK_SPEED);
        if (speed == null) return;
        speed.setBaseValue(enabled() ? NO_COOLDOWN_ATTACK_SPEED : 4.0);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        apply(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSweep(EntityDamageByEntityEvent e) {
        if (enabled() && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onHit(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            lastHitTick.put(p.getUniqueId(), Bukkit.getCurrentTick());
        }
    }

    /**
     * Chaque balancement de bras (clic maintenu) devient un coup si le joueur vise un ennemi
     * a portee et n'a pas deja frappe ce tick.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwing(PlayerAnimationEvent e) {
        if (!enabled() || e.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player p = e.getPlayer();
        if (!plugin.game().isRunning() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        Integer last = lastHitTick.get(p.getUniqueId());
        int now = Bukkit.getCurrentTick();
        if (last != null && now - last <= 1) return;
        // La portee suit l'attribut de portee d'interaction (capacites de "Reach").
        var range = p.getAttribute(org.bukkit.attribute.Attribute.ENTITY_INTERACTION_RANGE);
        double reach = range == null ? REACH : Math.max(1.0, range.getValue());
        RayTraceResult hit = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), reach, 0.3,
                ent -> ent != p && ent instanceof LivingEntity le && (Effects.isEnemy(p, le) || Mannequins.isMannequin(le)));
        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) return;
        // Un bloc entre les deux ? On ne frappe pas a travers les murs.
        var block = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), reach);
        if (block != null && block.getHitPosition().distanceSquared(p.getEyeLocation().toVector())
                < hit.getHitPosition().distanceSquared(p.getEyeLocation().toVector())) return;
        lastHitTick.put(p.getUniqueId(), now);
        p.attack(target);
    }
}
