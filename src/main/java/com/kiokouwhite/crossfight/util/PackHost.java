package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.MessageDigest;

/**
 * Le plugin distribue lui-meme le pack complet : il suffit de deposer CrossfightPackComplet.zip dans le dossier du plugin.
 * Un petit serveur web l'offre sur un port a part (resource-pack.port), l'empreinte est calculee toute seule, et elle est
 * recalculee quand le fichier change : remplacer le zip suffit, sans rien ecrire dans la configuration. Sans ce fichier,
 * rien ne demarre et le plugin envoie le pack de resource-pack.url, comme avant.
 */
public final class PackHost {
    private static HttpServer server;
    private static File file;
    private static long stamp = -1, size = -1;
    private static String sha1 = "";
    private static int port;

    private PackHost() { }

    public static void start(CrossfightPlugin plugin) {
        stop();
        String name = plugin.getConfig().getString("resource-pack.fichier", "CrossfightPackComplet.zip");
        file = new File(plugin.getDataFolder(), name == null || name.isBlank() ? "CrossfightPackComplet.zip" : name);
        if (!plugin.getConfig().getBoolean("resource-pack.enabled", true) || !file.isFile()) { file = null; return; }
        port = plugin.getConfig().getInt("resource-pack.port", 8765);
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", exchange -> {
                File f = file;
                try (exchange) {
                    if (f == null || !f.isFile() || !exchange.getRequestURI().getPath().endsWith(".zip")) { exchange.sendResponseHeaders(404, -1); return; }
                    exchange.getResponseHeaders().set("Content-Type", "application/zip");
                    exchange.sendResponseHeaders(200, f.length());
                    try (InputStream in = Files.newInputStream(f.toPath()); OutputStream out = exchange.getResponseBody()) { in.transferTo(out); }
                } catch (Exception ignored) { }
            });
            server.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(4, r -> { Thread t = new Thread(r, "Crossfight-pack"); t.setDaemon(true); return t; }));
            server.start();
            plugin.getLogger().info("Pack complet distribue par le plugin : " + file.getName() + " (" + file.length() / 1024 + " Ko), port " + port + ", empreinte " + sha1());
        } catch (Exception ex) {
            plugin.getLogger().warning("Le pack complet n'a pas pu etre distribue sur le port " + port + " (" + ex.getMessage() + ") : le pack de resource-pack.url est envoye a la place.");
            server = null;
            file = null;
        }
    }

    public static void stop() {
        if (server != null) server.stop(0);
        server = null;
    }

    /** Vrai si le plugin distribue lui-meme le pack complet. */
    public static boolean active() { return server != null && file != null && file.isFile(); }

    /** L'empreinte du fichier, recalculee s'il a change. */
    public static synchronized String sha1() {
        if (file == null || !file.isFile()) return "";
        if (file.lastModified() == stamp && file.length() == size) return sha1;
        try (InputStream in = Files.newInputStream(file.toPath())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            byte[] buffer = new byte[1 << 16];
            for (int n; (n = in.read(buffer)) > 0; ) digest.update(buffer, 0, n);
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) hex.append(String.format("%02x", b));
            sha1 = hex.toString();
            stamp = file.lastModified();
            size = file.length();
        } catch (Exception ex) { sha1 = ""; }
        return sha1;
    }

    /**
     * L'adresse du pack pour ce joueur : celle de la configuration (resource-pack.adresse), sinon le nom par lequel il a
     * joint le serveur, qui est par construction une adresse qu'il sait atteindre.
     */
    public static String url(Player p) {
        String host = CrossfightPlugin.get().getConfig().getString("resource-pack.adresse", "");
        if (host == null || host.isBlank()) {
            InetSocketAddress joined = p.getVirtualHost();
            host = joined == null ? "127.0.0.1" : joined.getHostString();
        }
        String hash = sha1();
        return "http://" + host.trim() + ":" + port + "/" + (hash.length() >= 8 ? hash.substring(0, 8) : "pack") + ".zip";
    }
}
