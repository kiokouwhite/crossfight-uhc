package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.listener.DamageListener;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Mannequins d'entrainement : des zombies immobiles de 50 coeurs qui affichent
 * les degats recus et se regenerent apres 5 secondes sans etre touches.
 * Les capacites les considerent comme des ennemis.
 */
public final class Mannequins implements Listener {

    public static final String TAG = "cf_mannequin";
    public static final String AGGRO_TAG = "cf_mannequin_aggro";
    public static final double MAX_HEALTH = 100.0;

    private static final Map<UUID, BukkitTask> REGEN = new HashMap<>();

    public static boolean isMannequin(Entity e) {
        return e != null && e.getScoreboardTags().contains(TAG);
    }

    public static boolean isAggressive(Entity e) {
        return e != null && e.getScoreboardTags().contains(AGGRO_TAG);
    }

    public static LivingEntity spawn(Location loc) {
        return spawn(loc, false);
    }

    /**
     * @param aggressive true pour un mannequin qui poursuit et frappe les joueurs
     *                   (2 coeurs par coup, vitesse d'un joueur qui marche).
     */
    public static LivingEntity spawn(Location loc, boolean aggressive) {
        World w = loc.getWorld();
        Zombie z = (Zombie) w.spawnEntity(loc, EntityType.ZOMBIE);
        z.addScoreboardTag(TAG);
        if (aggressive) z.addScoreboardTag(AGGRO_TAG);
        z.setAdult();
        // L'IA reste active pour que le mannequin subisse la gravite et les projections ;
        // un mannequin passif a simplement une vitesse nulle et ne cible personne.
        z.setAI(true);
        if (!aggressive) {
            var spd = z.getAttribute(Attribute.MOVEMENT_SPEED);
            if (spd != null) spd.setBaseValue(0.0);
        }
        if (aggressive) {
            var atk = z.getAttribute(Attribute.ATTACK_DAMAGE);
            if (atk != null) atk.setBaseValue(4.0);
            var spd = z.getAttribute(Attribute.MOVEMENT_SPEED);
            if (spd != null) spd.setBaseValue(0.28);
            var range = z.getAttribute(Attribute.FOLLOW_RANGE);
            if (range != null) range.setBaseValue(48.0);
        }
        z.setSilent(true);
        z.setShouldBurnInDay(false);
        z.setCanPickupItems(false);
        z.setPersistent(true);
        z.setRemoveWhenFarAway(false);
        z.setCustomNameVisible(true);
        var mh = z.getAttribute(Attribute.MAX_HEALTH);
        if (mh != null) mh.setBaseValue(MAX_HEALTH);
        var kb = z.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(0.0);
        var reinf = z.getAttribute(Attribute.SPAWN_REINFORCEMENTS);
        if (reinf != null) reinf.setBaseValue(0.0);
        z.setHealth(MAX_HEALTH);
        updateName(z);
        return z;
    }

    public static int clearAll() {
        int n = 0;
        for (World w : Bukkit.getWorlds()) {
            for (LivingEntity e : w.getLivingEntities()) {
                if (isMannequin(e)) { e.remove(); n++; }
            }
        }
        for (BukkitTask t : REGEN.values()) t.cancel();
        REGEN.clear();
        return n;
    }

    private static void updateName(LivingEntity e) {
        double hp = Math.max(0, e.getHealth());
        String label = isAggressive(e) ? "<dark_red>Mannequin agressif" : "<gold>Mannequin";
        e.customName(Msg.mm(label + " <red>❤ " + fmt(hp / 2.0) + "<gray>/" + fmt(MAX_HEALTH / 2.0)));
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.format(Locale.US, "%.1f", d);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageLowest(EntityDamageEvent e) {
        if (!isMannequin(e.getEntity())) return;
        switch (e.getCause()) {
            case FIRE, FIRE_TICK, LAVA, DROWNING, SUFFOCATION, HOT_FLOOR, FREEZE -> e.setCancelled(true);
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageHighest(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity m) || !isMannequin(m)) return;
        double dmg = e.getFinalDamage();
        boolean lethal = dmg >= m.getHealth();
        Player attacker = e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent ede ? DamageListener.attackerOf(ede.getDamager()) : null;
        if (attacker != null) {
            Msg.actionBar(attacker, "<gold>Mannequin : <red>" + fmt(dmg / 2.0) + " coeur(s)</red> <gray>(" + fmt(dmg) + " PV)"
                    + (lethal ? " <dark_red>K.O." : " <gray>reste " + fmt(Math.max(0, m.getHealth() - dmg) / 2.0) + " coeurs"));
        }
        if (lethal) {
            // Le mannequin ne meurt jamais : on encaisse puis on le remet a neuf.
            e.setDamage(0);
            m.setHealth(MAX_HEALTH);
            m.getWorld().playSound(m.getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_DEATH, 1f, 1.2f);
            m.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, m.getLocation().add(0, 1, 0), 1);
        }
        Bukkit.getScheduler().runTask(CrossfightPlugin.get(), () -> updateName(m));
        scheduleRegen(m);
    }

    private static void scheduleRegen(LivingEntity m) {
        BukkitTask old = REGEN.remove(m.getUniqueId());
        if (old != null) old.cancel();
        REGEN.put(m.getUniqueId(), Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> {
            if (m.isValid()) {
                m.setHealth(MAX_HEALTH);
                for (var eff : m.getActivePotionEffects()) m.removePotionEffect(eff.getType());
                m.setFireTicks(0);
                updateName(m);
            }
            REGEN.remove(m.getUniqueId());
        }, 100L));
    }

    @EventHandler
    public void onCombust(EntityCombustEvent e) {
        if (isMannequin(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        if (!isMannequin(e.getEntity())) return;
        // Les mannequins agressifs ne visent que des joueurs en survie.
        if (isAggressive(e.getEntity()) && e.getTarget() instanceof Player t && t.getGameMode() == org.bukkit.GameMode.SURVIVAL) return;
        e.setCancelled(true);
    }
}
