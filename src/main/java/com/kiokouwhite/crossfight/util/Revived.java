package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Joueurs ramenes a la vie par une capacite (Invocation de Weiss, Resurrection de Sage) :
 * une vie, Faiblesse permanente et interdiction de porter un plastron.
 */
public final class Revived {

    private static final Set<UUID> REVIVED = new HashSet<>();

    private Revived() {}

    public static boolean is(Player p) {
        return REVIVED.contains(p.getUniqueId());
    }

    public static void clear() {
        REVIVED.clear();
    }

    /** Ramene un joueur dans une equipe, a cote du lanceur. */
    public static void revive(Player caster, Player target, Team team) {
        var plugin = CrossfightPlugin.get();
        CfPlayer tcf = plugin.players().get(target);
        plugin.teams().forceJoin(target, team);
        if (team != null) team.setEliminated(false);
        tcf.setLives(1);
        tcf.setWaitingRespawn(false);
        REVIVED.add(target.getUniqueId());
        plugin.abilities().clearStates(target);
        target.teleport(caster.getLocation().add(Effects.towards(caster, null).multiply(2)));
        target.setGameMode(GameMode.SURVIVAL);
        var mh = target.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        target.setHealth(mh == null ? 20 : mh.getValue());
        target.setFoodLevel(20);
        target.getInventory().setChestplate(null);
        plugin.abilities().equip(target);
        target.getWorld().playSound(target.getLocation(), org.bukkit.Sound.ENTITY_EVOKER_CAST_SPELL, 1f, 0.8f);
        target.getWorld().spawnParticle(org.bukkit.Particle.SOUL, target.getLocation().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.05);
        Msg.send(target, "<light_purple>Tu es ramene a la vie : une vie, Faiblesse permanente, pas de plastron.");
    }

    /** A appeler chaque seconde : Faiblesse et retrait du plastron. */
    public static void tick(Player p) {
        if (!is(p)) return;
        Effects.refresh(p, PotionEffectType.WEAKNESS, 0, 45);
        ItemStack chest = p.getInventory().getChestplate();
        if (chest != null) {
            p.getInventory().setChestplate(null);
            var left = p.getInventory().addItem(chest);
            for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
            Msg.actionBar(p, "<red>Les ressuscites ne peuvent pas porter de plastron.");
        }
    }
}
