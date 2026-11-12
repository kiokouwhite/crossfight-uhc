package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Sons personnalises du pack de ressources (espace de noms "crossfight") :
 * <ul>
 *   <li>{@code ability.<id>} : le bruitage d'une capacite ;</li>
 *   <li>{@code voice.<cle>} : une replique (capacite, selection, mort, Synergie, dialogue de duo...) ;</li>
 *   <li>{@code sfx.<categorie>} : la bibliotheque de bruitages.</li>
 * </ul>
 * La liste des sons presents dans le pack est embarquee dans le jar (sounds.txt, ecrit par
 * tools/build_pack.py) : un son absent n'est simplement pas joue. Ajouter une voix revient donc a
 * deposer un fichier dans le pack et a le reconstruire, sans toucher au code.
 */
public final class Sounds {

    public static final String NS = "crossfight";
    /** Adresse du pack quand la configuration (anterieure aux sons) n'en donne pas. */
    public static final String DEFAULT_PACK_URL = "https://github.com/kiokouwhite/crossfight-uhc/raw/main/dist/CrossfightPack.zip";

    private static final TreeSet<String> INDEX = new TreeSet<>();
    private static String packSha1 = "";
    /** Derniere replique de chaque joueur, pour ne pas en superposer deux. */
    private static final Map<UUID, Long> LAST_VOICE = new HashMap<>();
    /** Derniere fois que chaque joueur a dit chaque replique. */
    private static final Map<String, Long> LAST_LINE = new HashMap<>();

    /** Les duos : personnage A, personnage B (ordre des noms de fichiers), et qui parle en premier dans le dialogue. */
    private static final String[][] DUOS = {
            {"netsu", "kaze", "kaze"}, {"netsu", "hannah", "hannah"}, {"netsu", "eirika", "netsu"},
            {"kaze", "hannah", "hannah"}, {"kaze", "eirika", "kaze"}, {"hannah", "eirika", "hannah"},
            {"hiro", "pinkou", "hiro"}, {"hiro", "aida", "aida"}, {"hiro", "crystal", "crystal"},
            {"pinkou", "aida", "pinkou"}, {"pinkou", "crystal", "pinkou"}, {"aida", "crystal", "aida"},
    };

    private Sounds() {}

    public static void load(CrossfightPlugin plugin) {
        INDEX.clear();
        try (InputStream in = plugin.getResource("sounds.txt")) {
            if (in != null) {
                BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line; (line = r.readLine()) != null; ) { line = line.trim(); if (!line.isEmpty() && !line.startsWith("#")) INDEX.add(line); }
            }
        } catch (Exception ex) { plugin.getLogger().warning("sounds.txt illisible : " + ex); }
        packSha1 = "";
        try (InputStream in = plugin.getResource("pack.sha1")) {
            if (in != null) packSha1 = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception ex) { plugin.getLogger().warning("pack.sha1 illisible : " + ex); }
        plugin.getLogger().info(INDEX.size() + " sons personnalises declares.");
    }

    public static boolean has(String event) { return INDEX.contains(event); }
    public static Collection<String> events() { return INDEX; }

    private static void playAt(Location at, String event, SoundCategory category, float volume) {
        if (at == null || at.getWorld() == null || !INDEX.contains(event)) return;
        at.getWorld().playSound(at, NS + ":" + event, category, volume, 1f);
    }

    /** Un bruitage de la bibliotheque, a un endroit. */
    public static void sfx(Location at, String category) { playAt(at, "sfx." + category, SoundCategory.PLAYERS, 1f); }

    /** Une capacite vient de reussir : son bruitage et sa replique. */
    public static void ability(Player p, String abilityId) {
        playAt(p.getLocation(), "ability." + abilityId, SoundCategory.PLAYERS, 1f);
        voice(p, abilityId);
    }

    /** Une replique, entendue autour du joueur. Deux repliques du meme joueur ne se chevauchent pas. */
    public static boolean voice(Player p, String key) {
        String event = "voice." + resolve(key);
        if (!INDEX.contains(event)) return false;
        long now = System.currentTimeMillis();
        Long last = LAST_VOICE.get(p.getUniqueId());
        if (last != null && now - last < 900) return false;
        // Une capacite utilisee en rafale (dashs, tirs) ne redit pas sa replique a chaque fois.
        String lineKey = p.getUniqueId() + ":" + event;
        Long lastLine = LAST_LINE.get(lineKey);
        if (lastLine != null && now - lastLine < 6000) return false;
        LAST_LINE.put(lineKey, now);
        LAST_VOICE.put(p.getUniqueId(), now);
        playAt(p.getLocation(), event, SoundCategory.VOICE, 1.5f);
        return true;
    }

    public static void voiceLater(Player p, String key, long ticks) {
        Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> { if (p.isOnline()) voice(p, key); }, ticks);
    }

    /** Une replique que seul ce joueur entend (menu de selection). */
    public static void voiceTo(Player listener, String key) {
        String event = "voice." + resolve(key);
        if (INDEX.contains(event)) listener.playSound(listener.getLocation(), NS + ":" + event, SoundCategory.VOICE, 1f, 1f);
    }

    /** Les capacites qui partagent une replique (les 28 tomes de Hano tirent parmi trois). */
    private static String resolve(String key) {
        if (key.startsWith("hano_tome_") && !INDEX.contains("voice." + key)) return "hano_tome";
        return key;
    }

    private static String[] duo(String idA, String idB) {
        for (String[] d : DUOS) if ((d[0].equals(idA) && d[1].equals(idB)) || (d[0].equals(idB) && d[1].equals(idA))) return d;
        return null;
    }

    /** Le personnage vient d'etre choisi : sa replique de selection, pour lui seul. */
    public static void selection(Player p, String characterId) {
        if (INDEX.contains("sfx.select")) p.playSound(p.getLocation(), NS + ":sfx.select", SoundCategory.PLAYERS, 0.8f, 1f);
        voiceTo(p, characterId + "_selection");
    }

    /** Les deux membres d'un duo ont choisi : leur court echange, entendu des deux. */
    public static void duoDialogue(Player a, String idA, Player b, String idB) {
        String[] d = duo(idA, idB);
        if (d == null) return;
        String base = "duo_" + d[0] + "_" + d[1] + "_";
        String first = d[2], second = d[2].equals(d[0]) ? d[1] : d[0];
        for (Player listener : new Player[]{a, b}) {
            Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> { if (listener.isOnline()) voiceTo(listener, base + first); }, 50L);
            Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> { if (listener.isOnline()) voiceTo(listener, base + second); }, 110L);
        }
    }

    /** Une Synergie est lancee : le lanceur dit sa replique, son partenaire repond. */
    public static void synergy(Player caster, String idCaster, Player partner, String idPartner) {
        playAt(caster.getLocation(), "sfx.ultimate", SoundCategory.PLAYERS, 1f);
        String[] d = duo(idCaster, idPartner);
        if (d == null) return;
        String base = "syn_" + d[0] + "_" + d[1] + "_";
        voice(caster, base + idCaster);
        voiceLater(partner, base + idPartner, 45L);
    }

    /** Joueurs a qui le pack vient d'etre propose et qui n'ont pas encore repondu. */
    private static final java.util.Set<UUID> PACK_PENDING = new java.util.HashSet<>();
    /** Ce qui attend leur reponse (ouverture des menus). */
    private static final Map<UUID, java.util.List<Runnable>> AFTER_PACK = new HashMap<>();

    /** Vrai si le pack est configure et sera propose a la connexion. */
    private static boolean packConfigured() {
        CrossfightPlugin plugin = CrossfightPlugin.get();
        String url = plugin.getConfig().getString("resource-pack.url", DEFAULT_PACK_URL);
        return plugin.getConfig().getBoolean("resource-pack.enabled", true) && url != null && !url.isBlank() && sha1().length() == 40;
    }

    /**
     * L'empreinte du pack envoye : celle de la configuration (pack complet heberge ailleurs, avec les
     * voix officielles et les musiques), sinon celle du pack public embarquee dans le jar.
     */
    private static String sha1() {
        String own = CrossfightPlugin.get().getConfig().getString("resource-pack.sha1", "");
        own = own == null ? "" : own.trim().toLowerCase(java.util.Locale.ROOT);
        return own.matches("[0-9a-f]{40}") ? own : packSha1;
    }

    /** A la connexion : le joueur va recevoir la demande du pack, les menus attendront sa reponse. */
    public static void expectPack(Player p) {
        if (packConfigured()) PACK_PENDING.add(p.getUniqueId());
    }

    /**
     * Execute l'action tout de suite, ou apres la reponse du joueur a la demande du pack :
     * un menu ouvert par-dessus l'ecran de confirmation le refermerait.
     */
    public static void afterPack(Player p, Runnable action) {
        if (!PACK_PENDING.contains(p.getUniqueId())) { action.run(); return; }
        AFTER_PACK.computeIfAbsent(p.getUniqueId(), u -> new java.util.ArrayList<>()).add(action);
    }

    /** Le joueur a repondu (pack charge, refuse ou en echec), ou n'a pas repondu a temps. */
    public static void packSettled(Player p) {
        PACK_PENDING.remove(p.getUniqueId());
        java.util.List<Runnable> waiting = AFTER_PACK.remove(p.getUniqueId());
        if (waiting == null) return;
        Bukkit.getScheduler().runTask(CrossfightPlugin.get(), () -> { if (p.isOnline()) for (Runnable r : waiting) r.run(); });
    }

    public static boolean packPending(Player p) { return PACK_PENDING.contains(p.getUniqueId()); }

    public static void forget(Player p) { PACK_PENDING.remove(p.getUniqueId()); AFTER_PACK.remove(p.getUniqueId()); }

    /** Propose (ou impose) le pack de ressources au joueur qui se connecte. */
    public static void sendPack(Player p) {
        CrossfightPlugin plugin = CrossfightPlugin.get();
        if (!plugin.getConfig().getBoolean("resource-pack.enabled", true)) return;
        String url = plugin.getConfig().getString("resource-pack.url", DEFAULT_PACK_URL);
        String hash = sha1();
        if (url == null || url.isBlank() || hash.length() != 40) return;
        boolean required = plugin.getConfig().getBoolean("resource-pack.required", false);
        try {
            p.closeInventory();
            PACK_PENDING.add(p.getUniqueId());
            p.setResourcePack(url, hash, required, Msg.mm("<gold>Crossfight UHC</gold> <gray>: sons et voix des personnages."));
            // Sans reponse au bout d'une minute, on n'attend plus.
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (PACK_PENDING.contains(p.getUniqueId())) packSettled(p); }, 20L * 60);
        } catch (Exception ex) { plugin.getLogger().warning("Pack de ressources non envoye : " + ex); packSettled(p); }
    }
}
