package com.kiokouwhite.crossfight.player;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.util.Msg;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.UUID;

/**
 * Systeme de vies : 3 vies par joueur, reapparition aleatoire ou attente de
 * la mort de l'equipe, commande /vivre.
 */
public final class LivesManager {

    private final CrossfightPlugin plugin;

    public LivesManager(CrossfightPlugin plugin) {
        this.plugin = plugin;
    }

    /** Appele a la mort d'un joueur en partie. */
    public void onDeath(Player p) {
        if (plugin.game().isTest()) {
            plugin.testMode().onDeath(p);
            return;
        }
        CfPlayer cf = plugin.players().get(p);
        Team team = cf.team();
        if (plugin.abilities().dispatchDeath(p)) {
            // Une capacite prend la mort en charge (ex : Byleth dans sa dimension).
            return;
        }
        cf.addLife(-1);
        plugin.abilities().unequip(p);

        if (cf.lives() <= 0) {
            Msg.broadcast("<red>" + p.getName() + " <gray>a perdu sa derniere vie !");
            p.setGameMode(GameMode.SPECTATOR);
            cf.setWaitingRespawn(false);
            if (team != null && plugin.teams().checkElimination(team)) {
                Msg.broadcast(team.coloredName() + " <gray>est eliminee !");
            }
            plugin.game().checkWin();
            return;
        }

        Msg.broadcast("<red>" + p.getName() + " <gray>est mort. Il lui reste <red>" + cf.lives() + "</red> vie(s).");
        p.setGameMode(GameMode.SPECTATOR);
        cf.setWaitingRespawn(true);

        Component choice = Msg.prefixed("<yellow>Que veux-tu faire ?")
                .append(Component.newline())
                .append(Msg.mm("  <green><bold>[Reapparaitre maintenant]</bold></green> <gray>ailleurs sur la map")
                        .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/vivre"))
                        .hoverEvent(Msg.mm("<green>Clique pour reapparaitre")))
                .append(Component.newline())
                .append(Msg.mm("  <aqua><bold>[Attendre]</bold></aqua> <gray>tu reapparaitras avec ton equipe, ou en tapant <white>/vivre"));
        p.sendMessage(choice);

        // Si toute l'equipe est morte, tout le monde reapparait ensemble.
        if (team != null && allDownOrWaiting(team)) {
            Msg.send(p, "<gray>Toute ton equipe est tombee : reapparition collective.");
            Bukkit.getScheduler().runTaskLater(plugin, () -> respawnTeam(team), 60L);
        }
    }

    private boolean allDownOrWaiting(Team team) {
        for (UUID u : team.members()) {
            CfPlayer cf = plugin.players().get(u);
            if (cf == null) continue;
            if (cf.isAlive() && !cf.isWaitingRespawn() && cf.isOnline()) return false;
        }
        return true;
    }

    private void respawnTeam(Team team) {
        if (!plugin.game().isRunning()) return;
        Location loc = plugin.game().randomGroundLocation(plugin.getConfig().getInt("lives.respawn-radius", 500));
        for (Player p : team.onlineMembers()) {
            CfPlayer cf = plugin.players().get(p);
            if (cf.isWaitingRespawn() && cf.isAlive()) respawnAt(p, loc);
        }
    }

    /** /vivre : reapparition volontaire. */
    public boolean respawnNow(Player p) {
        CfPlayer cf = plugin.players().get(p);
        if (!plugin.game().isRunning() || !cf.isWaitingRespawn() || !cf.isAlive()) return false;
        Location loc = plugin.game().randomGroundLocation(plugin.getConfig().getInt("lives.respawn-radius", 500));
        respawnAt(p, loc);
        return true;
    }

    public void respawnAt(Player p, Location loc) {
        CfPlayer cf = plugin.players().get(p);
        cf.setWaitingRespawn(false);
        for (PotionEffect e : p.getActivePotionEffects()) p.removePotionEffect(e.getType());
        p.teleport(loc);
        p.setGameMode(GameMode.SURVIVAL);
        p.setHealth(Math.min(20.0, p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()));
        p.setFoodLevel(20);
        p.setSaturation(10);
        p.setFireTicks(0);
        p.addPotionEffect(new PotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE, 20 * 10, 4, false, false));
        plugin.abilities().equip(p);
        p.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
        Msg.send(p, "<green>Tu reapparais ! Il te reste <red>" + cf.lives() + "</red> vie(s).");
    }
}
