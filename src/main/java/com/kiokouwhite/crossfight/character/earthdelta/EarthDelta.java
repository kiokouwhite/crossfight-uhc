package com.kiokouwhite.crossfight.character.earthdelta;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.character.Licence;
import com.kiokouwhite.crossfight.character.LicenceMechanic;
import com.kiokouwhite.crossfight.listener.DamageListener;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.team.TeamSize;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Licence EARTH DELTA : a chaque mort, un effet passif aleatoire de plus (cumulables).
 */
public final class EarthDelta {

    private EarthDelta() {}

    public static Licence create() {
        Licence l = new Licence("earth_delta", "EARTH DELTA", TeamSize.TRIO,
                "A chaque mort, un effet passif aleatoire de plus : regeneration, coeurs, feu, vitesse hors combat, explosion au kill (cumulables).");
        Mutations m = new Mutations();
        l.mechanic(m);
        Kishi kishi = new Kishi();
        Kura kura = new Kura();
        Taka taka = new Taka();
        Hano hano = new Hano();
        l.add(kishi); l.add(kura); l.add(taka); l.add(hano);
        l.listener(m);
        l.listener(kishi);
        l.listener(taka);
        l.listener(hano);
        return l;
    }

    static CrossfightPlugin plugin() { return CrossfightPlugin.get(); }

    /** Moins de 5 coeurs. */
    static boolean inGame(Player p) { return plugin().game().isRunning(); }

    static boolean isDelta(Player p) {
        CfPlayer cf = plugin().players().get(p);
        return cf.character() != null && cf.character().licence() != null && "earth_delta".equals(cf.character().licence().id());
    }

    /** Allies en vie et en ligne du joueur (sans lui). */
    static List<Player> allies(Player p) {
        List<Player> out = new ArrayList<>();
        Team t = plugin().teams().teamOf(p);
        if (t == null) return out;
        for (Player m : t.onlineMembers()) {
            if (m == p) continue;
            CfPlayer cf = plugin().players().get(m);
            if (cf.isAlive() && !cf.isWaitingRespawn()) out.add(m);
        }
        return out;
    }

    static int initialLives() { return plugin().getConfig().getInt("game.lives", 3); }

    // ------------------------------------------------------------ mutations

    public enum Effect {
        REGEN("Regeneration", "1 coeur regagne toutes les 2 secondes"),
        HEARTS("Coeurs", "2 coeurs supplementaires"),
        IGNITE("Feu", "10% de chances d'enflammer l'adversaire a chaque coup"),
        SWIFT("Vitesse", "Vitesse tant que tu n'es pas en combat"),
        BLAST("Explosion", "2 coeurs a toute l'equipe adverse quand tu tues un adversaire");
        public final String label, description;
        Effect(String label, String description) { this.label = label; this.description = description; }
        public static Effect byName(String s) {
            for (Effect e : values()) if (e.name().equalsIgnoreCase(s) || e.label.equalsIgnoreCase(s)) return e;
            if (s.equalsIgnoreCase("coeurs") || s.equalsIgnoreCase("coeur")) return HEARTS;
            return null;
        }
    }

    /** Les effets passifs gagnes a chaque mort, par joueur. */
    public static final class Mutations implements LicenceMechanic, Listener {
        private static final NamespacedKey HEARTS_KEY = new NamespacedKey("crossfight", "delta_hearts");
        private final Map<UUID, List<Effect>> effects = new HashMap<>();
        private final Map<UUID, Long> lastCombat = new HashMap<>();
        private final Map<UUID, Integer> regenTick = new HashMap<>();

        public List<Effect> effectsOf(Player p) { return effects.computeIfAbsent(p.getUniqueId(), u -> new ArrayList<>()); }
        int count(Player p, Effect e) { int n = 0; for (Effect x : effectsOf(p)) if (x == e) n++; return n; }

        /** Ajoute un effet (aleatoire si null) et l'applique. */
        public Effect gain(Player p, Effect e) {
            if (e == null) e = Effect.values()[(int) (Math.random() * Effect.values().length)];
            effectsOf(p).add(e);
            apply(p);
            Msg.send(p, "<green>Mutation : <white>" + e.label + "</white> <gray>(" + e.description + ") - x" + count(p, e));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 0.7f);
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 20, 0.5, 0.6, 0.5);
            return e;
        }

        public String summary(Player p) {
            List<String> parts = new ArrayList<>();
            for (Effect e : Effect.values()) { int n = count(p, e); if (n > 0) parts.add(e.label + " x" + n); }
            return parts.isEmpty() ? "aucune" : String.join(", ", parts);
        }

        void apply(Player p) {
            AttributeInstance mh = p.getAttribute(Attribute.MAX_HEALTH);
            if (mh != null) {
                mh.removeModifier(HEARTS_KEY);
                int hearts = count(p, Effect.HEARTS);
                if (hearts > 0) mh.addTransientModifier(new AttributeModifier(HEARTS_KEY, 4.0 * hearts, AttributeModifier.Operation.ADD_NUMBER));
            }
        }

        @Override public void onEquip(Player p) {
            apply(p);
            if (!effectsOf(p).isEmpty()) Msg.send(p, "<gray>Mutations actives : <white>" + summary(p));
        }

        @Override public void onUnequip(Player p) {
            AttributeInstance mh = p.getAttribute(Attribute.MAX_HEALTH);
            if (mh != null) mh.removeModifier(HEARTS_KEY);
            Boosts.remove(p, "delta_swift");
        }

        @Override public void onDeath(Player p) {
            if (plugin().game().isTest()) return;
            gain(p, null);
        }

        @Override public void tick(Player p) {
            int regen = count(p, Effect.REGEN);
            if (regen > 0) {
                int t = regenTick.merge(p.getUniqueId(), 1, Integer::sum);
                if (t % 2 == 0) { Effects.heal(p, 2.0 * regen); p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), regen, 0.3, 0.2, 0.3); }
            }
            int swift = count(p, Effect.SWIFT);
            boolean calm = System.currentTimeMillis() - lastCombat.getOrDefault(p.getUniqueId(), 0L) > 8000;
            if (swift > 0 && calm) { if (Boosts.get(p, "delta_swift") == null) Boosts.set(p, "delta_swift", new Boosts.Boost(0, Math.min(2, swift), 0, 0)); }
            else if (Boosts.get(p, "delta_swift") != null) Boosts.remove(p, "delta_swift");
        }

        @Override public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e) {
            lastCombat.put(attacker.getUniqueId(), System.currentTimeMillis());
            int ignite = count(attacker, Effect.IGNITE);
            if (ignite > 0 && Math.random() < 0.10 * ignite) {
                victim.setFireTicks(Math.max(victim.getFireTicks(), 80));
                victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.02);
            }
        }

        @Override public void onKill(Player killer, Player victim) {
            int blast = count(killer, Effect.BLAST);
            if (blast <= 0) return;
            Team t = plugin().teams().teamOf(victim);
            if (t == null) return;
            for (Player m : t.onlineMembers()) {
                if (m == victim || m.isDead()) continue;
                CfPlayer cf = plugin().players().get(m);
                if (!cf.isAlive() || cf.isWaitingRespawn()) continue;
                m.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, m.getLocation().add(0, 1, 0), 1);
                m.getWorld().playSound(m.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                m.setNoDamageTicks(0);
                Effects.damage(m, 4.0 * blast, killer);
                Msg.send(m, "<red>L'explosion de " + killer.getName() + " te touche !");
            }
        }

        @Override public void reset() { effects.clear(); lastCombat.clear(); regenTick.clear(); }

        /** Prendre un coup compte aussi comme etre en combat. */
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onHit(EntityDamageByEntityEvent e) {
            if (e.getEntity() instanceof Player v && isDelta(v)) lastCombat.put(v.getUniqueId(), System.currentTimeMillis());
            Player a = DamageListener.attackerOf(e.getDamager());
            if (a != null && isDelta(a)) lastCombat.put(a.getUniqueId(), System.currentTimeMillis());
        }
    }

    /** Mode test : ajoute une mutation. */
    public static boolean grant(Player p, String which) {
        var lic = plugin().characters().licence("earth_delta");
        if (lic == null || !(lic.mechanic() instanceof Mutations m) || !isDelta(p)) return false;
        Effect e = which == null || which.equalsIgnoreCase("aleatoire") ? null : Effect.byName(which);
        if (which != null && !which.equalsIgnoreCase("aleatoire") && e == null) return false;
        m.gain(p, e);
        return true;
    }
}
