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
 * Une piste peut aussi etre tenue pour un joueur hors de ces combats (un boss, une scene) : {@link #hold}.
 *
 * Une piste simple repart sur elle-meme a sa fin. Une piste a boucle (tools/boucle_musique.py) n'a pas de fin :
 * son premier fichier joue l'introduction puis plusieurs tours de boucle, enchaines a l'echantillon pres dans le
 * fichier ; a l'instant qu'il indique, un second fichier (d'autres tours) prend le relais, puis se relaie lui-meme.
 * Les deux fichiers se recouvrent en fondu un quart de seconde : le relais, lance au tick pres, ne s'entend pas.
 */
public final class Music implements Listener {

    /** "licenceA|licenceB" (triees) -> piste. */
    private static final Map<String, String> PAIR_TRACK = new TreeMap<>();
    /** Piste -> duree en millisecondes, pour l'enchainer sur elle-meme sans blanc. */
    private static final Map<String, Long> DURATION = new TreeMap<>();
    /** Fichier d'une piste a boucle -> l'instant (ms) ou sa suite prend le relais, et le nom de cette suite. */
    private static final Map<String, Long> RELAY = new HashMap<>();
    private static final Map<String, String> NEXT = new HashMap<>();

    private static final class Fight {
        final int teamA, teamB; final String track; long lastHit;
        Fight(int teamA, int teamB, String track) { this.teamA = teamA; this.teamB = teamB; this.track = track; }
        boolean involves(int team) { return team == teamA || team == teamB; }
    }

    private static final class Playing {
        /** La piste demandee ; le fichier en cours (elle-meme, ou sa suite) ; celui d'avant, qui finit de s'eteindre. */
        final String track; String file, before; long startedAt;
        Playing(String track) { this.track = track; this.file = track; this.startedAt = System.currentTimeMillis(); }
    }

    private final CrossfightPlugin plugin;
    private final Map<String, Fight> fights = new HashMap<>();
    private final Map<UUID, Playing> playing = new HashMap<>();
    /** Joueur -> la piste tenue pour lui hors des combats d'equipes : elle passe avant tout. */
    private final Map<UUID, String> held = new HashMap<>();

    public Music(CrossfightPlugin plugin) {
        this.plugin = plugin;
        load();
        // A chaque tick : la boucle doit repartir au tick exact ou la piste se termine.
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void load() {
        PAIR_TRACK.clear(); DURATION.clear(); RELAY.clear(); NEXT.clear();
        try (InputStream in = plugin.getResource("music.txt")) {
            if (in == null) return;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                String[] p = line.trim().split("\\s+");
                if (p.length >= 3 && p[0].equals("track")) {
                    DURATION.put(p[1], Long.parseLong(p[2]));
                    if (p.length >= 5) { RELAY.put(p[1], Long.parseLong(p[3])); NEXT.put(p[1], p[4]); }
                }
                else if (p.length == 4 && p[0].equals("pair")) PAIR_TRACK.put(pairKey(p[1], p[2]), p[3]);
            }
        } catch (Exception ex) { plugin.getLogger().warning("music.txt illisible : " + ex); }
        plugin.getLogger().info(DURATION.size() + " musiques de combat, " + PAIR_TRACK.size() + " affrontements de licences couverts.");
    }

    private static String pairKey(String a, String b) { return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a; }

    public static Map<String, String> pairs() { return PAIR_TRACK; }
    public static Map<String, Long> tracks() { return DURATION; }
    public static String trackFor(String licenceA, String licenceB) { return PAIR_TRACK.get(pairKey(licenceA, licenceB)); }

    private boolean enabled() { return plugin.getConfig().getBoolean("music.enabled", true); }
    private long silenceMillis() { return plugin.getConfig().getInt("music.silence-seconds", 20) * 1000L; }
    /** La reprise part un peu avant la fin : un leger recouvrement s'entend moins qu'un blanc. */
    private long overlapMillis() { return plugin.getConfig().getInt("music.loop-overlap-ms", 50); }
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
    public void onQuit(PlayerQuitEvent e) { playing.remove(e.getPlayer().getUniqueId()); held.remove(e.getPlayer().getUniqueId()); }

    /**
     * Tient une piste pour un joueur, en boucle, jusqu'a {@link #release} : la musique d'un boss, d'une scene.
     * Elle passe avant la musique de son combat d'equipes. Rend faux si la piste n'est pas dans le pack.
     */
    public boolean hold(Player p, String track) {
        if (!enabled() || !DURATION.containsKey(track)) return false;
        if (!track.equals(held.put(p.getUniqueId(), track))) refresh(p);
        return true;
    }

    /** Les joueurs dont le prochain changement de piste se fait en fondu : pendant combien de ticks l'ancienne joue encore. */
    private final Map<UUID, Integer> blends = new java.util.HashMap<>();

    /** Comme {@link #hold}, mais l'ancienne piste tenue joue encore {@code ticks} ticks sous la nouvelle. */
    public boolean blend(Player p, String track, int ticks) {
        if (!enabled() || !DURATION.containsKey(track)) return false;
        if (!track.equals(held.get(p.getUniqueId()))) blends.put(p.getUniqueId(), ticks);
        return hold(p, track);
    }

    /** Relache la piste tenue pour ce joueur : sa musique s'arrete (ou celle de son combat reprend). */
    public void release(Player p) {
        if (held.remove(p.getUniqueId()) != null) refresh(p);
    }

    private boolean alive(int teamId) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Team t = plugin.teams().teamOf(p);
            if (t != null && t.id() == teamId && plugin.players().get(p).isAlive()) return true;
        }
        return false;
    }

    /** La piste que ce joueur devrait entendre : celle de son combat le plus recent. */
    private String wanted(Player p) {
        String own = held.get(p.getUniqueId());
        if (own != null) return own;
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
            // Une piste a boucle passe le relais a sa suite a l'instant dit par le fichier, au tick le plus proche ;
            // une piste simple repart sur elle-meme un peu avant sa fin. Dans les deux cas, tant qu'on la veut.
            Long relay = RELAY.get(now.file);
            long due = relay != null ? relay - 25 : DURATION.getOrDefault(now.file, 180000L) - overlapMillis();
            if (System.currentTimeMillis() - now.startedAt >= due) {
                String next = relay != null && DURATION.containsKey(NEXT.get(now.file)) ? NEXT.get(now.file) : now.file;
                now.before = now.file;
                now.file = next;
                now.startedAt = System.currentTimeMillis();
                play(p, next);
            }
            return;
        }
        // D'une piste tenue a une autre, en fondu (blend) : la nouvelle part tout de suite, l'ancienne ne se tait
        // qu'un peu apres, le temps que la nouvelle (qui commence en fondu entrant) ait pris sa place.
        Integer lap = blends.remove(p.getUniqueId());
        if (now != null && lap != null) {
            final String old = now.file, older = now.before;
            playing.remove(p.getUniqueId());
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                p.stopSound(Sounds.NS + ":music." + old, SoundCategory.RECORDS);
                if (older != null) p.stopSound(Sounds.NS + ":music." + older, SoundCategory.RECORDS);
            }, lap);
        } else if (now != null) stop(p);
        playing.put(p.getUniqueId(), new Playing(want));
        p.stopSound(SoundCategory.MUSIC);
        play(p, want);
    }

    private void play(Player p, String track) { p.playSound(p, Sounds.NS + ":music." + track, SoundCategory.RECORDS, volume(), 1f); }

    private void stop(Player p) {
        Playing now = playing.remove(p.getUniqueId());
        if (now == null) return;
        p.stopSound(Sounds.NS + ":music." + now.file, SoundCategory.RECORDS);
        // Juste apres un relais, le fichier d'avant joue encore sa derniere fraction de seconde.
        if (now.before != null && !now.before.equals(now.file)) p.stopSound(Sounds.NS + ":music." + now.before, SoundCategory.RECORDS);
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
        held.clear();
        for (UUID id : new ArrayList<>(playing.keySet())) { Player p = Bukkit.getPlayer(id); if (p != null) stop(p); }
        playing.clear();
    }

    /** Test : joue une piste a un joueur, hors combat, en boucle jusqu'a ce qu'on l'arrete (null pour l'arreter). */
    public void preview(Player p, String track) {
        stop(p);
        held.remove(p.getUniqueId());
        if (track == null || !DURATION.containsKey(track)) { refresh(p); return; }
        held.put(p.getUniqueId(), track);
        refresh(p);
    }
}
