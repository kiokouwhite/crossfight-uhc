package com.kiokouwhite.crossfight.character.fireemblem;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.character.Licence;
import com.kiokouwhite.crossfight.character.LicenceMechanic;
import com.kiokouwhite.crossfight.team.TeamSize;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Licence FIRE EMBLEM et sa particularite : le Soutien.
 *
 * A moins de 10 blocs d'un allie, on est "en soutien" avec lui. 100 % de degats
 * infliges en soutien font gagner un niveau (C, B, A, S) a la paire. Chaque
 * niveau donne a chacun Force, Vitesse ou Resistance 0.5 tant qu'ils sont a
 * moins de 10 blocs l'un de l'autre.
 */
public final class FireEmblem {

    public static final double SUPPORT_RANGE = 10.0;
    public static final String[] RANKS = {"-", "C", "B", "A", "S"};

    private FireEmblem() {}

    public static Licence create() {
        Licence l = new Licence("fire_emblem", "FIRE EMBLEM", TeamSize.TRIO,
                "Soutien entre allies a moins de 10 blocs : chaque niveau (C a S) donne Force, Vitesse ou Resistance 0.5.");
        l.mechanic(new Support());
        Takumi takumi = new Takumi();
        Byleth byleth = new Byleth();
        l.add(new Marth());
        l.add(new Corrin());
        l.add(takumi);
        l.add(byleth);
        l.listener(takumi);
        l.listener(byleth);
        return l;
    }

    /** Etat d'une paire d'allies. */
    static final class Pair {
        int level;              // 0 a 4
        double progress;        // 0 a 100
        final double[] force = {0}, speed = {0}, resistance = {0};
    }

    public static final class Support implements LicenceMechanic {

        private final Map<String, Pair> pairs = new HashMap<>();

        private static String key(UUID a, UUID b) {
            return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
        }

        Pair pair(Player a, Player b) {
            return pairs.computeIfAbsent(key(a.getUniqueId(), b.getUniqueId()), k -> new Pair());
        }

        public int level(Player a, Player b) {
            Pair p = pairs.get(key(a.getUniqueId(), b.getUniqueId()));
            return p == null ? 0 : p.level;
        }

        @Override
        public void reset() {
            pairs.clear();
        }

        @Override
        public void tick(Player p) {
            // Applique ou retire les bonus de chaque paire selon la distance.
            for (Player other : p.getWorld().getPlayers()) {
                if (!CrossfightPlugin.get().teams().sameTeam(p, other) || other == p) continue;
                Pair pair = pairs.get(key(p.getUniqueId(), other.getUniqueId()));
                String source = "support:" + other.getUniqueId();
                if (pair == null || pair.level == 0) {
                    Boosts.remove(p, source);
                    continue;
                }
                boolean near = Effects.isAlly(p, other)
                        && other.getLocation().distanceSquared(p.getLocation()) <= SUPPORT_RANGE * SUPPORT_RANGE;
                if (near) {
                    Boosts.Boost cur = Boosts.get(p, source);
                    if (cur == null || cur.force != pair.force[0] || cur.speed != pair.speed[0] || cur.resistance != pair.resistance[0]) {
                        Boosts.set(p, source, new Boosts.Boost(pair.force[0], pair.speed[0], pair.resistance[0], 0));
                    }
                } else {
                    Boosts.remove(p, source);
                }
            }
        }

        @Override
        public void onDealDamage(Player attacker, Player victim, EntityDamageByEntityEvent e) {
            double percent = e.getFinalDamage() / 20.0 * 100.0;
            if (percent <= 0) return;
            for (Player ally : Effects.alliesWithin(attacker, SUPPORT_RANGE)) {
                Pair pair = pair(attacker, ally);
                if (pair.level >= 4) continue;
                pair.progress += percent;
                while (pair.progress >= 100 && pair.level < 4) {
                    pair.progress -= 100;
                    pair.level++;
                    rollBonus(pair, attacker, ally);
                }
            }
        }

        private void rollBonus(Pair pair, Player a, Player b) {
            int roll = ThreadLocalRandom.current().nextInt(3);
            String stat;
            switch (roll) {
                case 0 -> { pair.force[0] += 0.5; stat = "<red>Force 0.5"; }
                case 1 -> { pair.speed[0] += 0.5; stat = "<aqua>Vitesse 0.5"; }
                default -> { pair.resistance[0] += 0.5; stat = "<gray>Resistance 0.5"; }
            }
            String rank = RANKS[pair.level];
            for (Player p : new Player[]{a, b}) {
                Player other = p == a ? b : a;
                Msg.send(p, "<gold>Soutien <bold>" + rank + "</bold> atteint avec <yellow>" + other.getName()
                        + "</yellow> : " + stat + " <gray>a moins de 10 blocs.");
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
            }
        }

        /** Resume des soutiens d'un joueur pour l'affichage. */
        public String summary(Player p) {
            StringBuilder sb = new StringBuilder();
            for (Player other : p.getWorld().getPlayers()) {
                if (other == p || !CrossfightPlugin.get().teams().sameTeam(p, other)) continue;
                Pair pair = pairs.get(key(p.getUniqueId(), other.getUniqueId()));
                if (pair == null) continue;
                sb.append(other.getName()).append(" ").append(RANKS[pair.level])
                        .append(" (").append((int) pair.progress).append("%) ");
            }
            return sb.toString().trim();
        }
    }
}
