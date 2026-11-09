package com.kiokouwhite.crossfight.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/**
 * Messages du plugin, ecrits en MiniMessage.
 */
public final class Msg {

    public static final String PREFIX = "<dark_gray>[<gradient:#ff4d4d:#ffb84d>Crossfight</gradient>]</dark_gray> ";
    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Msg() {}

    public static Component mm(String text) {
        return MM.deserialize(text);
    }

    public static Component prefixed(String text) {
        return MM.deserialize(PREFIX + text);
    }

    public static void send(CommandSender to, String text) {
        to.sendMessage(prefixed(text));
    }

    public static void raw(CommandSender to, String text) {
        to.sendMessage(mm(text));
    }

    public static void broadcast(String text) {
        Bukkit.broadcast(prefixed(text));
    }

    public static void actionBar(org.bukkit.entity.Player p, String text) {
        p.sendActionBar(mm(text));
    }

    /** Formate des secondes en mm:ss ou h:mm:ss. */
    public static String time(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }
}
