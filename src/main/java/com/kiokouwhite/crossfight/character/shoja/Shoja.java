package com.kiokouwhite.crossfight.character.shoja;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.character.Licence;
import com.kiokouwhite.crossfight.character.duo.Synergy;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.team.TeamSize;
import com.kiokouwhite.crossfight.util.Effects;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Licence SHOJA'S MEMORIES (groupe duo) : Hiro, Pinkou, Aida, Crystal et leurs six Synergies.
 */
public final class Shoja {

    private Shoja() {}

    public static Licence create() {
        Licence l = new Licence("shojas_memories", "SHOJA'S MEMORIES", TeamSize.DUO,
                "Synergie : second ultime commun a la paire, charge a 200 % de degats infliges par les deux.");
        l.mechanic(new Synergy("shojas_memories"));
        Hiro hiro = new Hiro();
        Pinkou pinkou = new Pinkou();
        Aida aida = new Aida();
        Crystal crystal = new Crystal();
        l.add(hiro); l.add(pinkou); l.add(aida); l.add(crystal);
        l.listener(hiro);
        l.listener(pinkou);
        l.listener(aida);
        l.listener(crystal);
        ShojaSynergies.register();
        return l;
    }

    static CrossfightPlugin plugin() { return CrossfightPlugin.get(); }

    static String characterId(Player p) {
        CfPlayer cf = plugin().players().get(p);
        return cf.character() == null ? "" : cf.character().id();
    }

    /** Le partenaire du duo (allie en vie et en ligne). */
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

    static List<Player> duo(Player p) {
        List<Player> out = new ArrayList<>();
        out.add(p);
        Player o = partner(p);
        if (o != null) out.add(o);
        return out;
    }

    /** Perte de vie directe (ignore l'armure), jamais mortelle. */
    static void selfLoss(Player p, double amount) {
        p.setHealth(Math.max(1.0, p.getHealth() - amount));
        p.damage(0.01);
        p.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, p.getLocation().add(0, 1.2, 0), (int) amount, 0.3, 0.3, 0.3, 0.1);
    }

    /** Explosion "de capacite" : particules, son, degats et feu dans un rayon, sans casser le terrain. */
    static void explosion(Player owner, Location c, double radius, double damage, boolean fire) {
        World w = c.getWorld();
        w.spawnParticle(Particle.EXPLOSION_EMITTER, c, Math.max(1, (int) (radius / 3)), radius * 0.4, 0.5, radius * 0.4, 0);
        w.spawnParticle(Particle.FLAME, c, (int) (radius * 15), radius * 0.6, 0.6, radius * 0.6, 0.05);
        w.spawnParticle(Particle.LAVA, c, (int) radius, radius * 0.5, 0.3, radius * 0.5, 0);
        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, Math.max(0.5f, 1.4f - (float) radius * 0.05f));
        for (LivingEntity e : Effects.within(c, radius, le -> Effects.isEnemy(owner, le))) {
            e.setNoDamageTicks(0);
            Effects.damage(e, damage, owner);
            if (fire) e.setFireTicks(Math.max(e.getFireTicks(), 100));
        }
    }
}
