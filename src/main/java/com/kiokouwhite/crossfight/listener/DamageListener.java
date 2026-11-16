package com.kiokouwhite.crossfight.listener;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.MannequinPlayer;
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

    /**
     * Remonte au joueur a l'origine des degats (direct ou via projectile). Le mannequin pilote du mode
     * troisieme personne compte comme son joueur pilote.
     */
    public static Player attackerOf(Entity damager) {
        Player p = meleeAttackerOf(damager);
        if (p != null) return p;
        if (damager instanceof Projectile proj) {
            if (proj.getShooter() instanceof Player shooter) return shooter;
            if (proj.getShooter() instanceof Entity se) return MannequinPlayer.of(se);
        }
        return null;
    }

    /** Le joueur qui frappe directement (pas de projectile), mannequin pilote compris. */
    public static Player meleeAttackerOf(Entity damager) {
        if (damager instanceof Player p) return p;
        return MannequinPlayer.of(damager);
    }

    /** Idem mais accepte toute entite vivante (mannequins, boss). */
    public static org.bukkit.entity.LivingEntity livingAttackerOf(Entity damager) {
        Player pilot = MannequinPlayer.of(damager);
        if (pilot != null) return pilot;
        if (damager instanceof org.bukkit.entity.LivingEntity le) return le;
        if (damager instanceof Projectile proj && proj.getShooter() instanceof org.bukkit.entity.LivingEntity le) {
            Player shooterPilot = MannequinPlayer.of(le);
            return shooterPilot != null ? shooterPilot : le;
        }
        return null;
    }

    /** Le joueur touche : un joueur, ou le joueur pilote d'un mannequin en troisieme personne. */
    private static Player victimOf(Entity e) {
        if (e instanceof Player p) return p;
        return MannequinPlayer.of(e);
    }

    /**
     * Le mannequin pilote frappe comme un joueur : la difficulte du serveur, qui reduit (facile) ou
     * augmente (difficile) les coups des monstres sur les joueurs, ne s'applique pas a lui.
     */
    /** La Force ne vaut que la regle "force" par niveau : son correctif suit l'effet des qu'il arrive, change ou part. */
    @EventHandler
    public void onStrength(org.bukkit.event.entity.EntityPotionEffectEvent e) {
        if (!org.bukkit.potion.PotionEffectType.STRENGTH.equals(e.getModifiedType()) || !(e.getEntity() instanceof org.bukkit.entity.LivingEntity le)) return;
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> { if (le.isValid()) com.kiokouwhite.crossfight.util.Boosts.apply(le); });
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPilotDifficulty(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        var src = e.getDamageSource();
        if (!src.scalesWithDifficulty() || MannequinPlayer.of(src.getCausingEntity()) == null) return;
        double d = e.getDamage();
        switch (e.getEntity().getWorld().getDifficulty()) {
            case EASY -> d = d <= 2.0 ? d : 2.0 * (d - 1.0);
            case HARD -> d = d / 1.5;
            default -> { }
        }
        e.setDamage(d);
    }

    /**
     * Les degats d'une capacite (Effects.damage) font perdre le nombre de coeurs annonce : l'armure et ses
     * enchantements de protection ne les reduisent pas. Repris en dernier, au cas ou un autre reglage des
     * degats les aurait recalcules entre-temps. Les coups d'arme et les fleches passent toujours par l'armure.
     */
    @SuppressWarnings("deprecation")
    private void ignoreArmor(EntityDamageEvent e) {
        if (!com.kiokouwhite.crossfight.util.Effects.isAbilityDamage() || !plugin.getConfig().getBoolean("abilities.ignore-armor", true)) return;
        if (e.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) e.setDamage(EntityDamageEvent.DamageModifier.ARMOR, 0);
        if (e.isApplicable(EntityDamageEvent.DamageModifier.MAGIC)) e.setDamage(EntityDamageEvent.DamageModifier.MAGIC, 0);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAbilityDamageEarly(EntityDamageEvent e) { ignoreArmor(e); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAbilityDamageLate(EntityDamageEvent e) { ignoreArmor(e); }

    /** Le mannequin pilote ne se blesse pas lui-meme (explosions, zones). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPilotSelfHit(EntityDamageByEntityEvent e) {
        if (!MannequinPlayer.isBody(e.getEntity())) return;
        if (MannequinPlayer.same(attackerOf(e.getDamager()), e.getEntity())) e.setCancelled(true);
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
        // Dans l'arene de Regna Ferox, on se bat meme si le PvP n'est pas encore ouvert sur la map.
        if (!plugin.game().isPvpEnabled() && !(plugin.ferox().fighting(attacker) && plugin.ferox().fighting(victim))) {
            e.setCancelled(true);
            Msg.actionBar(attacker, "<red>Le PvP n'est pas encore actif.");
            return;
        }
        if (plugin.teams().sameTeam(attacker, victim)) {
            e.setCancelled(true);
        }
    }

    /** Une hache fabriquee est un outil : un coup porte avec elle a un joueur ne compte pas. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onAxeHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player attacker) || com.kiokouwhite.crossfight.util.Effects.isAbilityDamage()) return;
        if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK && e.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (!(e.getEntity() instanceof Player) && !com.kiokouwhite.crossfight.util.Mannequins.isMannequin(e.getEntity())) return;
        if (!RestrictionListener.isToolAxe(attacker.getInventory().getItemInMainHand())) return;
        e.setCancelled(true);
        Msg.actionBar(attacker, "<red>Une hache fabriquee ne blesse pas les joueurs.");
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
        Player victim = victimOf(e.getEntity());
        if (victim == null) return;
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
        Player pilot = MannequinPlayer.isBody(e.getEntity()) ? MannequinPlayer.of(e.getEntity()) : null;
        if (pilot != null && e.getCause() == EntityDamageEvent.DamageCause.FALL && com.kiokouwhite.crossfight.util.Effects.noFall(pilot)) {
            e.setCancelled(true);
            return;
        }
        if (pilot != null && e.getCause() == EntityDamageEvent.DamageCause.FALL && com.kiokouwhite.crossfight.util.Effects.softFall(pilot)) {
            e.setDamage(e.getDamage() * com.kiokouwhite.crossfight.util.Effects.SOFT_FALL_KEPT);
        }
        if (!(e.getEntity() instanceof Player p)) return;
        if (!plugin.game().isRunning()) {
            e.setCancelled(true);
            return;
        }
        CfPlayer cf = plugin.players().get(p);
        if (cf.isWaitingRespawn() || !cf.isAlive()) e.setCancelled(true);
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && com.kiokouwhite.crossfight.util.Effects.noFall(p)) e.setCancelled(true);
        // Une chute amortie (equipement tridimensionnel) : les degats restent, reduits de 75 %.
        else if (e.getCause() == EntityDamageEvent.DamageCause.FALL && com.kiokouwhite.crossfight.util.Effects.softFall(p)) {
            e.setDamage(e.getDamage() * com.kiokouwhite.crossfight.util.Effects.SOFT_FALL_KEPT);
        }
    }
}
