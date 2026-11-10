package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.listener.DamageListener;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Clones de personnage (Ombre de Blake, duo Blake et Yang) : des leurres qui foncent
 * sur un ennemi et declenchent un effet quand cet ennemi les frappe.
 */
public final class Clones implements Listener {

    public static final String TAG = "cf_clone";
    private static final NamespacedKey OWNER_KEY = new NamespacedKey("crossfight", "clone_owner");
    private static final Map<UUID, BiConsumer<LivingEntity, Player>> ON_HIT = new HashMap<>();

    public static boolean isClone(Entity e) {
        return e != null && e.getScoreboardTags().contains(TAG);
    }

    public static UUID ownerOf(Entity e) {
        String s = e.getPersistentDataContainer().get(OWNER_KEY, PersistentDataType.STRING);
        return s == null ? null : UUID.fromString(s);
    }

    /**
     * Cree un clone de {@code owner} qui court vers {@code target}.
     * @param onHit appele (clone, attaquant) quand un joueur ennemi frappe le clone ; le clone est retire ensuite.
     */
    public static LivingEntity spawn(Player owner, LivingEntity target, Color color, int lifetimeTicks, BiConsumer<LivingEntity, Player> onHit) {
        Location at = owner.getLocation().add(Math.random() * 2 - 1, 0, Math.random() * 2 - 1);
        Zombie z = (Zombie) owner.getWorld().spawnEntity(at, EntityType.ZOMBIE);
        z.addScoreboardTag(TAG);
        z.getPersistentDataContainer().set(OWNER_KEY, PersistentDataType.STRING, owner.getUniqueId().toString());
        z.setAdult();
        z.setSilent(true);
        z.setShouldBurnInDay(false);
        z.setCanPickupItems(false);
        z.setPersistent(false);
        z.customName(Msg.mm("<gray>" + owner.getName() + " <dark_gray>(clone)"));
        z.setCustomNameVisible(true);
        var atk = z.getAttribute(Attribute.ATTACK_DAMAGE);
        if (atk != null) atk.setBaseValue(0.0);
        var spd = z.getAttribute(Attribute.MOVEMENT_SPEED);
        if (spd != null) spd.setBaseValue(0.38);
        var range = z.getAttribute(Attribute.FOLLOW_RANGE);
        if (range != null) range.setBaseValue(64.0);
        var reinf = z.getAttribute(Attribute.SPAWN_REINFORCEMENTS);
        if (reinf != null) reinf.setBaseValue(0.0);
        var mh = z.getAttribute(Attribute.MAX_HEALTH);
        if (mh != null) mh.setBaseValue(20.0);
        z.setHealth(20.0);
        var eq = z.getEquipment();
        eq.setHelmet(dyed(Material.LEATHER_HELMET, color));
        eq.setChestplate(dyed(Material.LEATHER_CHESTPLATE, color));
        eq.setLeggings(dyed(Material.LEATHER_LEGGINGS, color));
        eq.setBoots(dyed(Material.LEATHER_BOOTS, color));
        eq.setHelmetDropChance(0f); eq.setChestplateDropChance(0f); eq.setLeggingsDropChance(0f); eq.setBootsDropChance(0f);
        if (target != null) z.setTarget(target);
        if (onHit != null) ON_HIT.put(z.getUniqueId(), onHit);
        owner.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, at.add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
        Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> { if (z.isValid()) remove(z); }, lifetimeTicks);
        return z;
    }

    private static ItemStack dyed(Material m, Color c) {
        ItemStack it = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        meta.setColor(c);
        it.setItemMeta(meta);
        return it;
    }

    public static void remove(LivingEntity clone) {
        ON_HIT.remove(clone.getUniqueId());
        clone.getWorld().spawnParticle(org.bukkit.Particle.SMOKE, clone.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.03);
        clone.remove();
    }

    public static void removeAll() {
        for (var w : Bukkit.getWorlds()) for (LivingEntity e : w.getLivingEntities()) if (isClone(e)) e.remove();
        ON_HIT.clear();
    }

    /** Le clone appartient-il a l'equipe de ce joueur ? */
    public static boolean sameTeam(Entity clone, Player p) {
        UUID owner = ownerOf(clone);
        if (owner == null) return false;
        if (owner.equals(p.getUniqueId())) return true;
        var plugin = CrossfightPlugin.get();
        var team = plugin.teams().teamOf(p);
        return team != null && team.contains(owner);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamage(EntityDamageEvent e) {
        if (!isClone(e.getEntity())) return;
        switch (e.getCause()) {
            case FIRE, FIRE_TICK, LAVA, DROWNING, SUFFOCATION, FALL -> e.setCancelled(true);
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity clone) || !isClone(clone)) return;
        Player attacker = DamageListener.attackerOf(e.getDamager());
        if (attacker == null || sameTeam(clone, attacker)) { e.setCancelled(true); return; }
        e.setCancelled(true);
        BiConsumer<LivingEntity, Player> cb = ON_HIT.remove(clone.getUniqueId());
        if (cb != null) cb.accept(clone, attacker);
        remove(clone);
    }

    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        if (!isClone(e.getEntity())) return;
        // Un clone ne change pas de cible tout seul.
        if (e.getReason() != EntityTargetEvent.TargetReason.CUSTOM) e.setCancelled(true);
    }

    @EventHandler
    public void onCombust(EntityCombustEvent e) {
        if (isClone(e.getEntity())) e.setCancelled(true);
    }
}
