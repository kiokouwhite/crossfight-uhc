package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.listener.DamageListener;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Clones de personnage (Ombre de Blake, duo Blake et Yang) : des copies du joueur
 * (skin et equipement) qui courent vers un ennemi sans jamais attaquer. Si l'ennemi
 * frappe un clone, un effet se declenche ; si le clone atteint l'ennemi sans etre
 * frappe, il disparait.
 */
public final class Clones implements Listener {

    public static final String TAG = "cf_clone";
    private static final NamespacedKey OWNER_KEY = new NamespacedKey("crossfight", "clone_owner");
    private static final Map<UUID, BiConsumer<LivingEntity, Player>> ON_HIT = new HashMap<>();
    /** Vitesse de course des clones, en blocs par tick (7 blocs par seconde). */
    private static final double SPEED = 0.35;

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
    public static LivingEntity spawn(Player owner, LivingEntity target, int lifetimeTicks, BiConsumer<LivingEntity, Player> onHit) {
        return spawn(owner, owner, target, lifetimeTicks, onHit);
    }

    /**
     * Cree un clone de {@code source} (le joueur lui-meme, ou un mannequin duo a l'image d'un personnage)
     * dans le camp de {@code owner}, qui court vers {@code target}.
     */
    public static LivingEntity spawn(Player owner, LivingEntity source, LivingEntity target, int lifetimeTicks, BiConsumer<LivingEntity, Player> onHit) {
        Location at = source.getLocation().clone().add(Math.random() * 2 - 1, 0, Math.random() * 2 - 1);
        at.setDirection(target != null ? target.getLocation().toVector().subtract(at.toVector()) : source.getLocation().getDirection());
        Mannequin m = (Mannequin) source.getWorld().spawnEntity(at, EntityType.MANNEQUIN);
        m.addScoreboardTag(TAG);
        m.getPersistentDataContainer().set(OWNER_KEY, PersistentDataType.STRING, owner.getUniqueId().toString());
        // Le mannequin d'un joueur pilote (troisieme personne) ou un mannequin duo : on copie son apparence.
        if (MannequinPlayer.real(source) instanceof Mannequin look) m.setProfile(look.getProfile());
        else m.setProfile(ResolvableProfile.resolvableProfile(owner.getPlayerProfile()));
        m.setDescription(Component.empty());
        m.setImmovable(false);
        m.setSilent(true);
        m.setPersistent(false);
        if (source == owner && !MannequinPlayer.isProxy(owner)) {
            try {
                m.setSkinParts(owner.getClientOption(com.destroystokyo.paper.ClientOption.SKIN_PARTS));
                m.setMainHand(owner.getClientOption(com.destroystokyo.paper.ClientOption.MAIN_HAND));
            } catch (Exception ignored) { }
        }
        var src = source.getEquipment();
        var eq = m.getEquipment();
        if (src != null && eq != null) {
            if (!CrossfightPlugin.get().getConfig().getBoolean("visuals.hide-armor", true)) {
                eq.setHelmet(src.getHelmet() == null ? null : src.getHelmet().clone());
                eq.setChestplate(src.getChestplate() == null ? null : src.getChestplate().clone());
                eq.setLeggings(src.getLeggings() == null ? null : src.getLeggings().clone());
                eq.setBoots(src.getBoots() == null ? null : src.getBoots().clone());
            }
            eq.setItemInMainHand(src.getItemInMainHand().clone());
            eq.setItemInOffHand(src.getItemInOffHand().clone());
        }
        if (onHit != null) ON_HIT.put(m.getUniqueId(), onHit);
        source.getWorld().spawnParticle(Particle.SMOKE, at.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
        source.getWorld().playSound(at, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.5f, 1.8f);
        if (target != null) run(m, target, lifetimeTicks);
        return m;
    }

    /** Fait courir le clone vers sa cible ; il disparait en l'atteignant ou a la fin de sa duree de vie. */
    /** Un clone qui se balade au hasard (leurre) au lieu de courir vers une cible. */
    public static LivingEntity spawnWanderer(Player owner, int lifetimeTicks, BiConsumer<LivingEntity, Player> onHit) {
        LivingEntity clone = spawn(owner, null, 0, onHit);
        if (!(clone instanceof Mannequin m)) return clone;
        new BukkitRunnable() {
            int ticks = 0; Vector dir = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5).normalize();
            @Override public void run() {
                ticks++;
                if (!m.isValid()) { cancel(); return; }
                if (ticks > lifetimeTicks) { remove(m); cancel(); return; }
                if (ticks % 40 == 0 || Math.random() < 0.02) dir = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5).normalize();
                Location next = settle(m.getLocation().clone().add(dir.clone().multiply(0.12)));
                next.setDirection(dir);
                m.teleport(next);
            }
        }.runTaskTimer(CrossfightPlugin.get(), 1L, 1L);
        return m;
    }

    private static void run(Mannequin m, LivingEntity target, int lifetimeTicks) {
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                ticks++;
                if (!m.isValid()) { cancel(); return; }
                if (ticks > lifetimeTicks || target == null || !target.isValid() || target.isDead() || !target.getWorld().equals(m.getWorld())) {
                    remove(m);
                    cancel();
                    return;
                }
                Location cur = m.getLocation();
                Vector to = target.getLocation().toVector().subtract(cur.toVector());
                double flat = Math.sqrt(to.getX() * to.getX() + to.getZ() * to.getZ());
                if (flat <= 1.2 && Math.abs(to.getY()) < 2.5) {
                    // Le clone atteint l'ennemi sans avoir ete frappe : il se dissipe.
                    remove(m);
                    cancel();
                    return;
                }
                Vector dir = new Vector(to.getX(), 0, to.getZ()).normalize();
                Location next = cur.clone().add(dir.multiply(Math.min(SPEED, flat)));
                next = settle(next);
                next.setDirection(new Vector(to.getX(), 0, to.getZ()));
                m.teleport(next);
                if (ticks % 3 == 0) m.getWorld().spawnParticle(Particle.SMOKE, cur.clone().add(0, 0.2, 0), 1, 0.1, 0, 0.1, 0);
            }
        }.runTaskTimer(CrossfightPlugin.get(), 1L, 1L);
    }

    /** Suit le relief : monte d'un bloc devant un obstacle d'un bloc, descend s'il y a du vide. */
    private static Location settle(Location next) {
        Block feet = next.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        if (!feet.isPassable() || !head.isPassable()) {
            Block up = feet.getRelative(0, 1, 0), up2 = feet.getRelative(0, 2, 0);
            if (up.isPassable() && up2.isPassable()) return next.add(0, 1, 0);
            return next.subtract(next.getDirection().setY(0).multiply(0.0)); // bloque : reste sur place
        }
        for (int i = 0; i < 3; i++) {
            Block below = next.clone().subtract(0, 0.05, 0).getBlock();
            if (below.isPassable() && next.getY() > next.getWorld().getMinHeight()) next.subtract(0, 1, 0); else break;
        }
        return next;
    }

    public static void remove(LivingEntity clone) {
        ON_HIT.remove(clone.getUniqueId());
        clone.getWorld().spawnParticle(Particle.SMOKE, clone.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.03);
        clone.getWorld().playSound(clone.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.4f, 1.4f);
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
        if (!(e instanceof EntityDamageByEntityEvent)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity clone) || !isClone(clone)) return;
        e.setCancelled(true);
        Player attacker = DamageListener.attackerOf(e.getDamager());
        if (attacker == null || sameTeam(clone, attacker)) return;
        BiConsumer<LivingEntity, Player> cb = ON_HIT.remove(clone.getUniqueId());
        if (cb != null) cb.accept(clone, attacker);
        remove(clone);
    }

    @EventHandler
    public void onCombust(EntityCombustEvent e) {
        if (isClone(e.getEntity())) e.setCancelled(true);
    }
}
