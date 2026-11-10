package com.kiokouwhite.crossfight.character.bloodysnow;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.character.duo.Synergy;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kaze Lewis - Voie de la Chasse. Le duelliste : plus fort seul face a un adversaire.
 */
public final class Kaze extends Character implements Listener {

    /** Degats infliges par Kaze : (date, degats), pour la Synergie Defense et Attaque. */
    static final Map<UUID, Deque<double[]>> DAMAGE_LOG = new HashMap<>();
    /** Joueurs qui ont le Power Hit (Kaze, Netsu en Synergie) : joueur -> expiration (MAX pour Kaze). */
    static final Map<UUID, Long> POWER_HIT = new HashMap<>();

    public Kaze() {
        super("kaze", "Kaze Lewis", Voie.CHASSE, Material.IRON_AXE);
        addAbility(new HomeRun().bindToWeapon());
        addAbility(new PowerHit());
        addAbility(new PriseEnTraitre());
        addAbility(new GoutDuRisque());
        addAbility(Synergy.ITEM);
        setUltimate(new AmourDuDanger());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.IRON_AXE, 8.0, "<red><bold>Home Run", List.of("<gray>8 dmg."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Home Run : clic droit sur un ennemi a 4 blocs, propulsion a deux sur 10 blocs",
                "<white>Power Hit : Force et Speed quand un seul adversaire est a 10 blocs",
                "<white>Prise en traitre : derriere l'adversaire le plus proche",
                "<white>Gout du risque : Force III mais portee de 2 blocs 5 s",
                "<light_purple>Ultime : Amour du danger, 1 a 9 coeurs selon la distance");
    }

    static double damageLastMinute(Player p) {
        Deque<double[]> d = DAMAGE_LOG.computeIfAbsent(p.getUniqueId(), u -> new ArrayDeque<>());
        long now = System.currentTimeMillis();
        while (!d.isEmpty() && now - (long) d.peekFirst()[0] > 60000) d.removeFirst();
        double s = 0; for (double[] e : d) s += e[1];
        return s;
    }

    /** Propulsion a deux : Kaze et l'ennemi partent de 10 blocs dans son regard. */
    static void homeRun(Player p, LivingEntity target) {
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
        Vector v = dir.normalize().multiply(1.9).setY(0.45);
        p.setVelocity(v);
        target.setVelocity(v.clone());
        Effects.noFallUntil(p, 5000);
        World w = p.getWorld();
        w.spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0);
        w.spawnParticle(Particle.CLOUD, p.getLocation(), 20, 0.4, 0.2, 0.4, 0.05);
        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.7f);
        w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1.2f);
    }

    static final class HomeRun extends ActiveAbility implements CustomUse {
        HomeRun() { super("kaze_homerun", "Home Run", "Clic droit sur un ennemi a 4 blocs : Kaze se propulse avec lui sur 10 blocs.", Material.IRON_AXE, 0); }
        @Override public List<String> weaponLore() { return List.of("<red>Clic droit sur un ennemi (4 blocs) : <gray>propulsion a deux sur 10 blocs"); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { return null; }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            long now = System.currentTimeMillis();
            if (now < st.get("next", 0L)) return true;
            RayTraceResult hit = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), 4, 0.8, e -> e instanceof LivingEntity le && Effects.isEnemy(p, le));
            if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) { Msg.actionBar(p, "<red>Home Run : vise un ennemi a 4 blocs."); return true; }
            st.set("next", now + 3000L);
            homeRun(p, target);
            return true;
        }
    }

    static final class PowerHit extends PassiveAbility {
        PowerHit() { super("kaze_powerhit", "Power Hit", "Quand un seul adversaire au plus est a moins de 10 blocs, Kaze gagne Force et Speed.", Material.BLAZE_POWDER); }
        @Override public void onEquip(Player p, CfPlayer cf) { POWER_HIT.put(p.getUniqueId(), Long.MAX_VALUE); }
        @Override public void onUnequip(Player p, CfPlayer cf) { POWER_HIT.remove(p.getUniqueId()); Boosts.remove(p, "kaze_powerhit"); }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { return Boosts.get(p, "kaze_powerhit") != null ? "<red>Power Hit <white>actif" : "<gray>Power Hit"; }
    }

    /** Applique le Power Hit a tous ceux qui l'ont (Kaze, et Netsu en Synergie). */
    static void tickPowerHit() {
        for (var en : new HashMap<>(POWER_HIT).entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null || System.currentTimeMillis() > en.getValue()) { POWER_HIT.remove(en.getKey()); if (p != null) Boosts.remove(p, "kaze_powerhit"); continue; }
            boolean alone = Effects.enemiesWithin(p, 10).size() <= 1;
            boolean has = Boosts.get(p, "kaze_powerhit") != null;
            if (alone && !has) Boosts.set(p, "kaze_powerhit", new Boosts.Boost(1, 1, 0, 0));
            else if (!alone && has) Boosts.remove(p, "kaze_powerhit");
        }
    }

    static final class PriseEnTraitre extends ActiveAbility {
        PriseEnTraitre() { super("kaze_prise", "Prise en traitre", "Kaze se teleporte derriere l'adversaire le plus proche (10 blocs).", Material.ENDER_PEARL, 15); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            LivingEntity t = Effects.nearestEnemy(p, 10);
            if (t == null) { Msg.actionBar(p, "<red>Aucun adversaire a moins de 10 blocs."); return false; }
            Vector facing = t.getLocation().getDirection().setY(0);
            if (facing.lengthSquared() < 0.01) facing = new Vector(1, 0, 0);
            Location to = t.getLocation().subtract(facing.normalize().multiply(1.3));
            if (!to.getBlock().isPassable()) to = t.getLocation();
            to.setDirection(t.getLocation().add(0, 1, 0).toVector().subtract(to.toVector().add(new Vector(0, 1.6, 0))));
            World w = p.getWorld();
            w.spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
            p.teleport(to);
            w.spawnParticle(Particle.SMOKE, to.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
            w.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.5f);
            return true;
        }
    }

    static final class GoutDuRisque extends ActiveAbility {
        GoutDuRisque() { super("kaze_risque", "Gout du risque", "Pendant 5 secondes, Kaze a Force III mais ne frappe plus qu'a 2 blocs.", Material.NETHERITE_SWORD, 30); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Boosts.set(p, "kaze_risque", new Boosts.Boost(3, 0, 0, 0));
            BloodySnow.setReach(p, "limit_risque", 2.0);
            Bukkit.getScheduler().runTaskLater(BloodySnow.plugin(), () -> { Boosts.remove(p, "kaze_risque"); BloodySnow.setReach(p, "limit_risque", null); }, 100L);
            p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.05);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.7f);
            Msg.actionBar(p, "<red>Gout du risque : Force III, portee 2 blocs (5 s)");
            return true;
        }
    }

    static final class AmourDuDanger extends UltimateAbility {
        AmourDuDanger() { super("kaze_ult", "Amour du danger", "Un coup enorme sur l'adversaire le plus proche (10 blocs) : 1 coeur a 10 blocs, jusqu'a 9 coeurs a 1 bloc.", Material.ANVIL); }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            LivingEntity t = Effects.nearestEnemy(p, 10);
            if (t == null) { Msg.actionBar(p, "<red>Aucun adversaire a moins de 10 blocs."); return false; }
            double dist = Math.max(1, Math.min(10, Effects.distanceToBox(p.getLocation().add(0, 1, 0), t) + 1));
            double hearts = Math.max(1, Math.min(9, 10 - Math.round(dist)));
            World w = t.getWorld();
            t.setNoDamageTicks(0);
            Effects.damage(t, hearts * 2, p);
            Effects.knockbackFrom(t, p.getLocation(), 6);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, t.getLocation().add(0, 1, 0), 1);
            w.spawnParticle(Particle.CRIT, t.getLocation().add(0, 1, 0), 50, 0.5, 0.7, 0.5, 0.3);
            w.playSound(t.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.5f);
            w.playSound(t.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
            Msg.actionBar(p, "<red>Amour du danger : " + (int) hearts + " coeur(s) a " + (int) dist + " bloc(s) !");
            return true;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKazeDamage(EntityDamageByEntityEvent e) {
        Player a = com.kiokouwhite.crossfight.listener.DamageListener.attackerOf(e.getDamager());
        if (a == null || !"kaze".equals(BloodySnow.characterId(a)) || !(e.getEntity() instanceof LivingEntity v) || !Effects.isEnemy(a, v)) return;
        DAMAGE_LOG.computeIfAbsent(a.getUniqueId(), u -> new ArrayDeque<>()).addLast(new double[]{System.currentTimeMillis(), e.getFinalDamage()});
    }
}
