package com.kiokouwhite.crossfight.listener;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

public final class DeathListener implements Listener {

    private final CrossfightPlugin plugin;

    public DeathListener(CrossfightPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        if (!plugin.game().isRunning()) {
            e.setCancelled(true);
            p.setHealth(20);
            return;
        }
        // Les items de capacite et l'arme ne tombent jamais au sol.
        e.getDrops().removeIf(it -> AbilityEngine.isAbilityItem(it) || AbilityEngine.isWeaponItem(it));
        if (plugin.game().isTest()) e.getDrops().clear();
        e.setKeepLevel(false);

        CfPlayer cf = plugin.players().get(p);
        Player killer = p.getKiller();
        String msg = "<red>" + p.getName() + "</red> <gray>est tombe";
        if (cf.character() != null) com.kiokouwhite.crossfight.util.Sounds.voice(p, cf.character().id() + "_mort");
        if (killer != null && killer != p) {
            var kc = plugin.players().get(killer).character();
            if (kc != null) com.kiokouwhite.crossfight.util.Sounds.voiceLater(killer, kc.id() + "_kill", 20L);
            plugin.players().get(killer).addKill();
            plugin.abilities().dispatchKill(killer, p);
            msg += " face a <red>" + killer.getName();
        }
        e.deathMessage(Msg.prefixed(msg + "."));
        if (cf.team() == null) return;

        // Les capacites qui gerent leur propre mort (dimension de Byleth) doivent voir le joueur
        // la ou il est mort : on les previent maintenant, avant la reapparition.
        boolean handled = plugin.abilities().dispatchDeath(p);

        // On laisse la mort se produire (drops), puis on gere les vies au tick suivant.
        Bukkit.getScheduler().runTask(plugin, () -> {
            p.spigot().respawn();
            Bukkit.getScheduler().runTask(plugin, () -> plugin.lives().onDeath(p, handled));
        });
    }

    @SuppressWarnings("unused")
    private static boolean isEmpty(ItemStack it) {
        return it == null || it.getType().isAir();
    }
}
