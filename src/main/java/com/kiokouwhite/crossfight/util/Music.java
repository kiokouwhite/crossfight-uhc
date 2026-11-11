package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.listener.DamageListener;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Musiques de combat : quand deux equipes s'affrontent, la musique associee a leurs deux licences
 * demarre pour tous leurs membres. Elle s'arrete quand elles ne se sont plus touchees depuis un
 * moment (music.silence-seconds) ou quand l'une des deux n'a plus de joueur en vie.
 * La correspondance licences -> piste et la duree des pistes viennent de music.txt, ecrit par
 * tools/build_pack.py a partir de resourcepack/music.txt.
 */
public final class Music implements Listener {

    /** "licenceA|licenceB" (triees) -> piste. */
    private static final Map<String, String> PAIR_TRACK = new TreeMap<>();
    /** Piste -> duree en secondes, pour la rejouer en boucle. */
    private static final Map<String, Integer> DURATION = new TreeMap<>();

    private static final class Fight {
        final int teamA, teamB; final String track; long lastHit;
        Fight(int teamA, int teamB, String track) { this.teamA = teamA; this.teamB = teamB; this.track = track; }
        boolean involves(int team) { return team == teamA || team == teamB; }
    }

    private static final class Playing {
        final String track; long startedAt;
        Playing(String track) { this.track = track; this.startedAt = System.currentTimeMillis(); }
    }

    private final CrossfightPlugin plugin;
    private final Map<String, Fight> fights = new HashMap<>();
    private final Map<UUID, Playing> playing = new HashMap<>();

    public Music(CrossfightPlugin plugin) {
        this.plugin = plugin;
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void load() {
        PAIR_TRACK.clear(); DURATION.clear();
        try (InputStream in = plugin.getResource("music.txt")) {
            if (in == null) return;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                String[] p = line.trim().split("\\s+");
                if (p.length == 3 && p[0].equals("track")) DURATION.put(p[1], Integer.parseInt(p[2]));
                else if (p.length == 4 && p[0].equals("pair")) PAIR_TRACK.put(pairKey(p[1], p[2]), p[3]);
            }
        } catch (Exception ex) { plugin.getLogger().warning("music.txt illisible : " + ex); }
        plugin.getLogger().info(DURATION.size() + " musiques de combat, " + PAIR_TRACK.size() + " affrontements de licences couverts.");
    }

    private static String pairKey(String a, String b) { return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a; }

    public static Map<String, String> pairs() { return PAIR_TRACK; }
    public static Map<String, Integer> tracks() { return DURATION; }
    public static String trackFor(String licenceA, String licenceB) { return PAIR_TRACK.get(pairKey(licenceA, licenceB)); }

    private boolean enabled() { return plugin.getConfig().getBoolean("music.enabled", true); }
    private long silenceMillis() { return plugin.getConfig().getInt("music.silence-seconds", 20) * 1000L; }
    private float volume() { return (float) plugin.getConfig().getDouble("music.volume", 0.6); }

    private String licenceOf(Player p) {
        CfPlayer cf = plugin.players().get(p);
        return cf.character() == null || cf.character().licence() == null ? null : cf.character().licence().id();
    }

    /** Un coup entre deux equipes ouvre (ou prolonge) leur combat. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!enabled() || DURATION.isEmpty() || !(e.getEntity() instanceof Player victim)) return;
        Player attacker = DamageListener.attackerOf(e.getDamager());
        if (attacker == null || attacker == victim) return;
        Team ta = plugin.teams().teamOf(attacker), tv = plugin.teams().teamOf(victim);
        if (ta == null || tv == null || ta.id() == tv.id()) return;
        String la = licenceOf(attacker), lv = licenceOf(victim);
        if (la == null || lv == null) return;
        String track = trackFor(la, lv);
        if (track == null || !DURATION.containsKey(track)) return;
        int a = Math.min(ta.id(), tv.id()), b = Math.max(ta.id(), tv.id());
        Fight f = fights.computeIfAbsent(a + "|" + b, k -> new Fight(a, b, track));
        f.lastHit = System.currentTimeMillis();
        for (Team t : new Team[]{ta, tv}) for (Player m : t.onlineMembers()) refresh(m);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { playing.remove(e.getPlayer().getUniqueId()); }

    private boolean alive(int teamId) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Team t = plugin.teams().teamOf(p);
            if (t != null && t.id() == teamId && plugin.players().get(p).isAlive()) return true;
        }
        return false;
    }

    /** La piste que ce joueur devrait entendre : celle de son combat le plus recent. */
    private String wanted(Player p) {
        Team t = plugin.teams().teamOf(p);
        if (t == null) return null;
        Fight best = null;
        for (Fight f : fights.values()) if (f.involves(t.id()) && (best == null || f.lastHit > best.lastHit)) best = f;
        return best == null ? null : best.track;
    }

    private void refresh(Player p) {
        String want = wanted(p);
        Playing now = playing.get(p.getUniqueId());
        if (want == null) { if (now != null) stop(p); return; }
        if (now != null && now.track.equals(want)) {
            // Fin de la piste : on la relance tant que le combat dure.
            if (System.currentTimeMillis() - now.startedAt >= DURATION.getOrDefault(want, 180) * 1000L) { now.startedAt = System.currentTimeMillis(); play(p, want); }
            return;
        }
        if (now != null) stop(p);
        playing.put(p.getUniqueId(), new Playing(want));
        p.stopSound(SoundCategory.MUSIC);
        play(p, want);
    }

    private void play(Player p, String track) { p.playSound(p, Sounds.NS + ":music." + track, SoundCategory.RECORDS, volume(), 1f); }

    private void stop(Player p) {
        Playing now = playing.remove(p.getUniqueId());
        if (now != null) p.stopSound(Sounds.NS + ":music." + now.track, SoundCategory.RECORDS);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        long silence = silenceMillis();
        fights.values().removeIf(f -> now - f.lastHit > silence || !alive(f.teamA) || !alive(f.teamB) || !plugin.game().isRunning());
        List<Player> listeners = new ArrayList<>();
        for (UUID id : playing.keySet()) { Player p = Bukkit.getPlayer(id); if (p != null) listeners.add(p); }
        for (Player p : listeners) refresh(p);
    }

    /** Coupe toutes les musiques (fin de partie). */
    public void stopAll() {
        fights.clear();
        for (UUID id : new ArrayList<>(playing.keySet())) { Player p = Bukkit.getPlayer(id); if (p != null) stop(p); }
        playing.clear();
    }

    /** Test : joue une piste a un joueur, hors combat (null pour l'arreter). */
    public void preview(Player p, String track) {
        stop(p);
        fights.remove("preview|" + p.getUniqueId());
        if (track == null) return;
        playing.put(p.getUniqueId(), new Playing(track));
        // Un combat fictif de l'equipe contre elle-meme tient la piste jusqu'a sa fin.
        Team t = plugin.teams().teamOf(p);
        if (t != null) { Fight f = new Fight(t.id(), t.id(), track); f.lastHit = System.currentTimeMillis() + DURATION.getOrDefault(track, 180) * 1000L; fights.put("preview|" + p.getUniqueId(), f); }
        p.stopSound(SoundCategory.MUSIC);
        play(p, track);
    }
}
