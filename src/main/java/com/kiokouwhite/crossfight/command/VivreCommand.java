package com.kiokouwhite.crossfight.command;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class VivreCommand implements CommandExecutor {

    private final CrossfightPlugin plugin;

    public VivreCommand(CrossfightPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (!(sender instanceof Player p)) {
            Msg.send(sender, "<red>Commande reservee aux joueurs.");
            return true;
        }
        if (!plugin.lives().respawnNow(p)) {
            Msg.send(p, "<red>Tu n'as pas de reapparition en attente.");
        }
        return true;
    }
}
