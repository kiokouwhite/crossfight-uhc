package com.kiokouwhite.crossfight.listener;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class ConnectionListener implements Listener {

    private final CrossfightPlugin plugin;

    public ConnectionListener(CrossfightPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        CfPlayer cf = plugin.players().get(p);
        e.joinMessage(Msg.prefixed("<gray>" + p.getName() + " a rejoint."));
        if (plugin.testMode().resumeIfPending(p)) return;
        if (plugin.game().isLobby()) {
            p.setGameMode(GameMode.ADVENTURE);
            p.getInventory().clear();
            Msg.send(p, "<gray>Bienvenue sur <gold>Crossfight UHC</gold>. En attente du lancement.");
            return;
        }
        // Partie en cours : les joueurs sans equipe ou sans vie sont spectateurs.
        if (cf.team() == null || !cf.isAlive()) {
            p.setGameMode(GameMode.SPECTATOR);
            Msg.send(p, "<gray>Partie en cours, tu es spectateur.");
        } else if (cf.isWaitingRespawn()) {
            p.setGameMode(GameMode.SPECTATOR);
            Msg.send(p, "<yellow>Tu attends ta reapparition. Tape <white>/vivre</white> pour revenir.");
        } else if (plugin.game().isRunning() && cf.character() != null) {
            // Reconnexion en vie : on s'assure qu'il a bien ses items.
            boolean hasItems = false;
            for (var it : p.getInventory().getContents()) {
                if (com.kiokouwhite.crossfight.ability.AbilityEngine.isAbilityItem(it)) { hasItems = true; break; }
            }
            if (!hasItems) plugin.abilities().equip(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        e.quitMessage(Msg.prefixed("<gray>" + e.getPlayer().getName() + " est parti."));
        plugin.scoreboard().remove(e.getPlayer());
    }
}
