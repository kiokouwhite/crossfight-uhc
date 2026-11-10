package com.kiokouwhite.crossfight.character.solo;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.AbilityType;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.character.Licence;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.TeamSize;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Licence PERSONNAGE SOLITAIRE : chacun a un Burst, second ultime charge a 1000 % de degats infliges,
 * compteur conserve d'une mort a l'autre.
 */
public final class Solo {

    public static final double BURST_REQUIRED = 1000.0;

    private Solo() {}

    public static Licence create() {
        Licence l = new Licence("solitaire", "PERSONNAGE SOLITAIRE", TeamSize.SOLO,
                "Burst : second ultime charge a 1000 % de degats infliges, conserve entre les morts.");
        Yuki yuki = new Yuki();
        Drifter drifter = new Drifter();
        Shoja shoja = new Shoja();
        l.add(yuki); l.add(drifter); l.add(shoja);
        l.listener(yuki);
        l.listener(drifter);
        l.listener(shoja);
        l.listener(new ShojaTree.Tracker());
        l.mechanic(new Mechanic());
        return l;
    }

    static CrossfightPlugin plugin() { return CrossfightPlugin.get(); }

    /** Remise a zero des Bursts, de l'arbre de Shoja et des arenes a la nouvelle partie. */
    static final class Mechanic implements com.kiokouwhite.crossfight.character.LicenceMechanic {
        @Override public void reset() { Solo.reset(); ShojaTree.returnAllFromDimension(); ShojaTree.reset(); Drifter.RETURN.clear(); Drifter.arenaOwner = null; }
        @Override public void onUnequip(Player p) { ShojaTree.FROZEN.remove(p.getUniqueId()); }
    }

    static String characterIdOf(Player p) {
        CfPlayer cf = plugin().players().get(p);
        return cf.character() == null ? "" : cf.character().id();
    }

    /** Un Burst : le second ultime d'un personnage solitaire. */
    public interface Burst {
        String burstName();
        String burstDescription();
        /** Lance le Burst. Retourne false si rien n'a ete lance (pas de consommation). */
        boolean burst(Player p, CfPlayer cf, AbilityState st);
    }

    /** Base de charge : le pourcentage de degats au moment de la derniere utilisation. */
    private static final Map<UUID, Double> BASE = new HashMap<>();

    public static double charge(Player p) {
        CfPlayer cf = plugin().players().get(p);
        return Math.max(0, cf.damageDealtPercent() - BASE.getOrDefault(p.getUniqueId(), 0.0));
    }
    public static boolean ready(Player p) { return charge(p) >= BURST_REQUIRED; }
    public static void consume(Player p) { BASE.put(p.getUniqueId(), plugin().players().get(p).damageDealtPercent()); }
    /** Test : rend le Burst pret. */
    public static void fill(Player p) { BASE.put(p.getUniqueId(), plugin().players().get(p).damageDealtPercent() - BURST_REQUIRED); }
    public static void reset() { BASE.clear(); }

    public static final BurstItem ITEM = new BurstItem();

    /** L'item de Burst, commun aux trois solitaires. */
    public static final class BurstItem extends Ability implements CustomUse {
        BurstItem() {
            super("solo_burst", "Burst", "Second ultime des solitaires : se charge a 1000 % de degats infliges, compteur conserve entre les morts. L'effet depend du personnage.", AbilityType.ACTIVE, Material.NETHER_STAR);
        }
        @Override protected List<String> extraLore() { return List.of("", "<yellow>Clic droit : lancer le Burst"); }
        static Burst burstOf(CfPlayer cf) { return cf.character() instanceof Burst b ? b : null; }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            boolean free = plugin().game().isTest();
            return (free || ready(p)) ? "<gold><bold>BURST PRET</bold>" : "<gold>Burst <white>" + (int) charge(p) + "%";
        }
        @Override public boolean onDeath(Player p, CfPlayer cf, AbilityState st) {
            return cf.character() instanceof Drifter d && d.onDeathInArena(p, cf);
        }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            Burst b = burstOf(cf);
            if (b == null) return true;
            if (!plugin().game().isTest() && !ready(p)) { Msg.actionBar(p, "<red>Burst a " + (int) charge(p) + "% / 1000%"); return true; }
            if (!b.burst(p, cf, st)) return true;
            if (!plugin().game().isTest()) consume(p);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.7f, 1.3f);
            plugin().game().broadcastNear(p, "<gold>" + p.getName() + " lance son Burst : <bold>" + b.burstName() + "</bold> !");
            return true;
        }
    }
}
