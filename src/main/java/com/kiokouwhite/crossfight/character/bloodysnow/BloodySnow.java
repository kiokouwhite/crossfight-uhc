package com.kiokouwhite.crossfight.character.bloodysnow;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.character.Licence;
import com.kiokouwhite.crossfight.character.LicenceMechanic;
import com.kiokouwhite.crossfight.character.duo.Synergy;
import com.kiokouwhite.crossfight.listener.DamageListener;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.team.TeamSize;
import com.kiokouwhite.crossfight.util.Effects;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Licence BLOODY SNOW (groupe duo) : Netsu, Kaze, Hannah, Eirika et leurs six Synergies.
 * Partage : les Marques de Sang et la portee de frappe ("Reach").
 */
public final class BloodySnow {

    static final Particle.DustOptions BLOOD = new Particle.DustOptions(Color.fromRGB(170, 10, 20), 1.1f);

    private BloodySnow() {}

    public static Licence create() {
        Licence l = new Licence("bloody_snow", "BLOODY SNOW", TeamSize.DUO,
                "Synergie : second ultime commun a la paire, charge a 200 % de degats infliges par les deux.");
        Synergy syn = new Synergy("bloody_snow");
        l.mechanic(new Mechanic(syn));
        Netsu netsu = new Netsu();
        Kaze kaze = new Kaze();
        Hannah hannah = new Hannah();
        Eirika eirika = new Eirika();
        l.add(netsu); l.add(kaze); l.add(hannah); l.add(eirika);
        l.listener(new Events());
        l.listener(netsu);
        l.listener(kaze);
        l.listener(hannah);
        l.listener(eirika);
        l.listener(new BloodSynergies());
        BloodSynergies.register();
        return l;
    }

    static CrossfightPlugin plugin() { return CrossfightPlugin.get(); }

    static String characterId(Player p) {
        CfPlayer cf = plugin().players().get(p);
        return cf.character() == null ? "" : cf.character().id();
    }

    static Player partner(Player p) {
        Team t = plugin().teams().teamOf(p);
        if (t == null) return null;
        for (Player m : t.onlineMembers()) {
            if (m == p) continue;
            CfPlayer cf = plugin().players().get(m);
            if (cf.isAlive() && !cf.isWaitingRespawn()) return m;
        }
        return null;
    }

    /** La mecanique de licence : la Synergie, plus le saignement des Marques de Sang (une fois par seconde). */
    static final class Mechanic implements LicenceMechanic {
        final Synergy synergy; int tick;
        Mechanic(Synergy synergy) { this.synergy = synergy; }
        @Override public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e) { synergy.onDealDamage(attacker, victim, e); }
        @Override public void reset() { synergy.reset(); MARKS.clear(); MARKERS.clear(); REACH.clear(); }
        @Override public void tick(Player p) {
            // Un seul des joueurs de la licence fait saigner et applique le Power Hit : le premier de la liste.
            if (!isFirstOfLicence(p)) return;
            Kaze.tickPowerHit();
            tick++;
            if (tick % 2 != 0) return;
            for (UUID id : new ArrayList<>(MARKS.keySet())) {
                var e = Bukkit.getEntity(id);
                if (!(e instanceof LivingEntity le) || le.isDead()) { MARKS.remove(id); continue; }
                int n = markCount(le);
                if (n <= 0) continue;
                le.setNoDamageTicks(0);
                le.damage(1.0);
                le.getWorld().spawnParticle(Particle.DUST, le.getLocation().add(0, 1, 0), 6 * n, 0.3, 0.5, 0.3, 0, BLOOD);
            }
        }
        static boolean isFirstOfLicence(Player p) {
            for (Player o : Bukkit.getOnlinePlayers()) {
                CfPlayer cf = plugin().players().get(o);
                if (cf.character() != null && cf.character().licence() != null && "bloody_snow".equals(cf.character().licence().id()) && cf.isAlive()) return o == p;
            }
            return false;
        }
    }

    // ------------------------------------------------------------- marques

    /** Marques de Sang par victime : dates d'expiration (6 s). */
    static final Map<UUID, List<Long>> MARKS = new HashMap<>();
    /** Qui peut poser des marques en plus d'Eirika (Synergies) : joueur -> expiration. */
    static final Map<UUID, Long> MARKERS = new HashMap<>();
    static final Map<UUID, Integer> HIT_COUNTER = new HashMap<>();

    static List<Long> marks(LivingEntity e) {
        List<Long> l = MARKS.computeIfAbsent(e.getUniqueId(), u -> new ArrayList<>());
        l.removeIf(exp -> System.currentTimeMillis() >= exp);
        return l;
    }
    static int markCount(LivingEntity e) { return marks(e).size(); }

    static void addMark(Player by, LivingEntity e) {
        List<Long> l = marks(e);
        if (l.size() >= 5) return;
        l.add(System.currentTimeMillis() + 6000L);
        e.getWorld().spawnParticle(Particle.DUST, e.getLocation().add(0, 1.2, 0), 15, 0.3, 0.5, 0.3, 0, BLOOD);
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_PLAYER_HURT_DROWN, 0.6f, 0.6f);
        Effects.say(e, "<dark_red>Marque de Sang x" + l.size());
    }

    static boolean canMark(Player p) {
        if ("eirika".equals(characterId(p))) return true;
        Long until = MARKERS.get(p.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }

    /** Le plus grand nombre de marques porte par un adversaire du joueur. */
    static int maxMarks(Player p) {
        int best = 0;
        for (LivingEntity e : Effects.enemiesWithin(p, 100)) best = Math.max(best, markCount(e));
        return best;
    }

    // --------------------------------------------------------------- portee

    private static final Map<UUID, Map<String, Double>> REACH = new HashMap<>();
    private static final NamespacedKey REACH_KEY = new NamespacedKey("crossfight", "reach");

    /** Portee de frappe : la plus grande des sources ; une source "limit" impose un maximum. */
    static void setReach(Player p, String source, Double value) {
        Map<String, Double> m = REACH.computeIfAbsent(p.getUniqueId(), u -> new HashMap<>());
        if (value == null) m.remove(source); else m.put(source, value);
        double reach = 3.0;
        for (var en : m.entrySet()) if (!en.getKey().startsWith("limit")) reach = Math.max(reach, en.getValue());
        for (var en : m.entrySet()) if (en.getKey().startsWith("limit")) reach = Math.min(reach, en.getValue());
        AttributeInstance attr = p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (attr == null) return;
        attr.removeModifier(REACH_KEY);
        if (Math.abs(reach - 3.0) > 1e-6) attr.addTransientModifier(new AttributeModifier(REACH_KEY, reach - 3.0, AttributeModifier.Operation.ADD_NUMBER));
    }
    static void clearReach(Player p) {
        REACH.remove(p.getUniqueId());
        AttributeInstance attr = p.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
        if (attr != null) attr.removeModifier(REACH_KEY);
    }

    // ---------------------------------------------------------------- events

    static final class Events implements Listener {
        /** Tous les 5 coups, ceux qui peuvent marquer posent une Marque de Sang. */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onHit(EntityDamageByEntityEvent e) {
            if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK || !(e.getEntity() instanceof LivingEntity v)) return;
            Player a = DamageListener.attackerOf(e.getDamager());
            if (a == null || !canMark(a) || !Effects.isEnemy(a, v)) return;
            int n = HIT_COUNTER.merge(a.getUniqueId(), 1, Integer::sum);
            if (n >= 5) { HIT_COUNTER.put(a.getUniqueId(), 0); addMark(a, v); }
        }
    }
}
