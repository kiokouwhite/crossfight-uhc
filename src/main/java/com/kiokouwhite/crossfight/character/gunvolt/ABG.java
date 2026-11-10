package com.kiokouwhite.crossfight.character.gunvolt;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.character.Licence;
import com.kiokouwhite.crossfight.character.duo.Synergy;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.TeamSize;
import com.kiokouwhite.crossfight.util.Effects;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.function.Supplier;

/**
 * Licence ARMED BLUE GUNVOLT (groupe duo) : Gunvolt, Acura, Kirin, Asimov et leurs six Synergies.
 */
public final class ABG {

    static final Particle.DustOptions BLUE = new Particle.DustOptions(Color.fromRGB(80, 170, 255), 1.0f);
    static final Particle.DustOptions CYAN = new Particle.DustOptions(Color.fromRGB(160, 240, 255), 0.7f);

    private ABG() {}

    public static Licence create() {
        Licence l = new Licence("gunvolt", "ARMED BLUE GUNVOLT", TeamSize.DUO,
                "Synergie : second ultime commun a la paire, charge a 200 % de degats infliges par les deux.");
        l.mechanic(new Synergy("gunvolt"));
        Gunvolt gunvolt = new Gunvolt();
        Acura acura = new Acura();
        Kirin kirin = new Kirin();
        Asimov asimov = new Asimov();
        l.add(gunvolt); l.add(acura); l.add(kirin); l.add(asimov);
        l.listener(gunvolt);
        l.listener(acura);
        l.listener(kirin);
        l.listener(asimov);
        Synergies.register();
        return l;
    }

    static CrossfightPlugin plugin() { return CrossfightPlugin.get(); }

    static String characterId(Player p) {
        CfPlayer cf = plugin().players().get(p);
        return cf.character() == null ? "" : cf.character().id();
    }

    /** Un trait d'eclair entre deux points. */
    static void arc(World w, Location a, Location b) {
        Vector d = b.toVector().subtract(a.toVector());
        double len = d.length();
        if (len < 0.05) return;
        d.normalize();
        for (double x = 0; x <= len; x += 0.25) w.spawnParticle(Particle.DUST, a.clone().add(d.clone().multiply(x)), 1, 0, 0, 0, 0, BLUE);
    }

    /** Petits eclairs en zigzag au hasard dans une zone. */
    static void sparks(World w, Location c, double radius, int count) {
        for (int k = 0; k < count; k++) {
            double a = Math.random() * Math.PI * 2, r = Math.random() * radius;
            Location cur = c.clone().add(Math.cos(a) * r, 0.2 + Math.random() * 0.6, Math.sin(a) * r);
            for (int seg = 0; seg < 3; seg++) {
                Location next = cur.clone().add((Math.random() - 0.5) * 1.0, (Math.random() - 0.3) * 0.8, (Math.random() - 0.5) * 1.0);
                arc(w, cur, next);
                cur = next;
            }
        }
        w.spawnParticle(Particle.ELECTRIC_SPARK, c.clone().add(0, 0.5, 0), count * 2, radius * 0.6, 0.4, radius * 0.6, 0.05);
    }

    /**
     * Zone electrique generique : bordure d'etincelles, eclairs dedans, degats et Lenteur chaque seconde.
     * Le centre peut suivre un joueur (fourni par le supplier).
     */
    static void zone(Player owner, Supplier<Location> center, double radius, int seconds, double damagePerSec, int slowAmp) {
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                Location c = center.get();
                if (!owner.isOnline() || c == null || t > seconds * 20) { cancel(); return; }
                World w = c.getWorld();
                int pts = Math.max(12, (int) (2 * Math.PI * radius));
                for (int i = 0; i < pts; i += 2) {
                    double a = i * 2 * Math.PI / pts + t * 0.1;
                    w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * radius, 0.2 + Math.random() * 0.4, Math.sin(a) * radius), 1, 0, 0, 0, 0, CYAN);
                }
                if (t % 3 == 0) sparks(w, c, radius, Math.max(1, (int) (radius / 2)));
                if (t % 20 == 0) {
                    w.playSound(c, Sound.BLOCK_BEACON_AMBIENT, 0.6f, 2f);
                    for (LivingEntity e : Effects.within(c, radius, le -> Effects.isEnemy(owner, le))) {
                        e.setNoDamageTicks(0);
                        Effects.damage(e, damagePerSec, owner);
                        if (slowAmp >= 0) Effects.give(e, PotionEffectType.SLOWNESS, slowAmp, 30);
                        w.spawnParticle(Particle.ELECTRIC_SPARK, e.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.1);
                    }
                }
            }
        }.runTaskTimer(plugin(), 0L, 1L);
    }
}
