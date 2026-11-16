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

    /** Reponse du joueur au pack de sons : on lui dit ou il en est, et comment le redemander. */
    @EventHandler
    public void onPackStatus(org.bukkit.event.player.PlayerResourcePackStatusEvent e) {
        Player p = e.getPlayer();
        com.kiokouwhite.crossfight.util.Sounds.packLoaded(p, e.getStatus() == org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
        if (e.getStatus() == org.bukkit.event.player.PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED) plugin.scoreboard().refresh(p);
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> { Msg.send(p, "<green>Sons et voix charges."); com.kiokouwhite.crossfight.util.Sounds.packSettled(p); }
            case DECLINED -> { Msg.send(p, "<yellow>Pack de sons refuse : tu joueras sans bruitages ni voix. <gray>/cf pack pour le redemander."); com.kiokouwhite.crossfight.util.Sounds.packSettled(p); }
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD, DISCARDED -> {
                Msg.send(p, "<red>Le pack de sons n'a pas pu etre charge. <gray>/cf pack pour reessayer.");
                com.kiokouwhite.crossfight.util.Sounds.packSettled(p);
            }
            default -> { }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        CfPlayer cf = plugin.players().get(p);
        e.joinMessage(Msg.prefixed("<gray>" + p.getName() + " a rejoint."));
        // Deconnecte pendant l'ecran de selection (vitesse de marche a zero, gardee avec le joueur) : il la retrouve.
        if (p.getWalkSpeed() == 0f) p.setWalkSpeed(0.2f);
        com.kiokouwhite.crossfight.util.Boosts.apply(p);
        // Le pack est propose une fois le joueur installe : envoye pendant le chargement du monde,
        // l'ecran de confirmation est referme aussitot par le jeu. Les menus (choix du personnage,
        // mode test) attendent sa reponse pour ne pas le recouvrir.
        com.kiokouwhite.crossfight.util.Sounds.expectPack(p);
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) com.kiokouwhite.crossfight.util.Sounds.sendPack(p); }, 40L);
        // Un joueur qui revient en cours de partie retrouve le skin de son personnage.
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && cf.character() != null) com.kiokouwhite.crossfight.util.Skins.apply(p, cf.character());
        }, 10L);
        if (plugin.testMode().resumeIfPending(p)) return;
        // Retour pendant le choix des personnages : il retrouve l'ecran de selection de son equipe.
        var state = plugin.game().state();
        if ((state == com.kiokouwhite.crossfight.game.GameState.SELECTING || state == com.kiokouwhite.crossfight.game.GameState.STARTING) && cf.team() != null) {
            plugin.selection().open(p);
            return;
        }
        if (plugin.game().isLobby()) {
            p.setGameMode(GameMode.ADVENTURE);
            p.getInventory().clear();
            // En attendant le lancement : la plateforme de verre dans les airs.
            p.teleport(plugin.game().lobbySpot());
            p.setFallDistance(0);
            Msg.send(p, "<gray>Bienvenue sur <gold>Crossfight UHC</gold>. En attente du lancement.");
            // Operateurs et administrateurs de partie : leurs outils de reglage en main.
            plugin.hostTools().refresh(p);
            Msg.send(p, plugin.hostTools().canHost(p) ? "<gray>Dans ta barre : tes outils d'administrateur (regles, scenarios, nouvelle map, lancement...) et la banniere pour choisir ton equipe."
                    : "<gray>La banniere dans ta barre sert a choisir ton equipe : solitaire, duo ou equipe de trois.");
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

    /**
     * Quand la map de jeu n'est pas le monde principal du serveur (/cf regenere), le Nether et l'End ramenent
     * quand meme vers elle.
     */
    @EventHandler(ignoreCancelled = true)
    public void onPortal(org.bukkit.event.player.PlayerPortalEvent e) {
        var main = org.bukkit.Bukkit.getWorlds().get(0);
        var game = plugin.game().world();
        var to = e.getTo();
        if (game.equals(main) || to.getWorld() == null || !to.getWorld().equals(main) || e.getFrom().getWorld().equals(game)) return;
        if (e.getFrom().getWorld().getEnvironment() == org.bukkit.World.Environment.THE_END) e.setTo(game.getSpawnLocation().add(0.5, 0, 0.5));
        else { to.setWorld(game); e.setTo(to); }
    }

    /** Dans le lobby, le verre change de couleur sous les pas. */
    @EventHandler(ignoreCancelled = true)
    public void onLobbyStep(org.bukkit.event.player.PlayerMoveEvent e) {
        var from = e.getFrom();
        var to = e.getTo();
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ() && from.getBlockY() == to.getBlockY()) return;
        plugin.game().stepOnLobby(e.getPlayer(), to.clone().subtract(0, 0.2, 0).getBlock());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        com.kiokouwhite.crossfight.util.Sounds.forget(e.getPlayer());
        com.kiokouwhite.crossfight.util.Skins.forget(e.getPlayer());
        e.quitMessage(Msg.prefixed("<gray>" + e.getPlayer().getName() + " est parti."));
        plugin.scoreboard().remove(e.getPlayer());
        plugin.abilities().clearGaugeBars(e.getPlayer());
        // Parti du lobby : sa place dans son equipe se libere.
        if (plugin.game().isLobby()) {
            plugin.teams().leave(e.getPlayer());
            plugin.teams().prune();
            plugin.hostTools().refreshTeamMenus();
        }
    }
}
