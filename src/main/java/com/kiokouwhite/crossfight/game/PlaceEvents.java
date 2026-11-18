package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Les evenements des lieux : toutes les vingt minutes de partie (structures.evenements.intervalle), un lieu pose au
 * hasard est renforce et un autre affaibli, jusqu'au tirage suivant. Chaque service a son bonus et son malus (EFFECTS) ;
 * les services eux-memes (Places, Rhythm, Lootbox) demandent ici ou ils en sont, par mood(service).
 *
 * Le premier tirage a lieu a la vingtieme minute. On ne renforce pas deux fois de suite le meme lieu quand un autre
 * est possible. /cf evenement (admins) : voir, tirer, choisir, arreter.
 */
public final class PlaceEvents {

    /** Par service : ce que donne le bonus, ce que coute le malus (les phrases des annonces). */
    private static final Map<String, String[]> EFFECTS = Map.of(
            "potions", new String[]{"toutes les potions d'Anna : une de chaque", "une seule potion toutes les 20 minutes"},
            "rythme", new String[]{"matériaux doublés, et de la netherite pour un A en difficile", "matériaux divisés par deux"},
            "enchantement", new String[]{"enchantements gratuits", "enchantements deux fois plus chers"},
            "forge", new String[]{"réparations gratuites", "réparations deux fois plus chères"},
            "marchand", new String[]{"le marchand vend à moitié prix", "le marchand vend au double"},
            "nourriture", new String[]{"24 steaks et une pomme dorée", "6 steaks seulement"},
            "experience", new String[]{"expérience doublée", "expérience divisée par deux"},
            "lootbox", new String[]{"deux lootbox garanties au lieu d'une", "plus de lootbox garantie"},
            "chasse", new String[]{"4 diamants par pièce trouvée", "1 seul diamant par pièce trouvée"});

    private final CrossfightPlugin plugin;
    private String boosted, nerfed;
    private int slot;

    public PlaceEvents(CrossfightPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 20L);
    }

    private int interval() {
        return Math.max(1, plugin.getConfig().getInt("structures.evenements.intervalle", 20));
    }

    private void tick() {
        if (!plugin.getConfig().getBoolean("structures.evenements.actifs", true) || !plugin.game().isRunning()) return;
        int now = plugin.game().elapsedSeconds() / (interval() * 60);
        if (now < slot) slot = now;                     // le temps est reparti de zero
        if (now > slot) { slot = now; roll(); }
    }

    /** Les lieux poses qui rendent un service connu. */
    private List<String> candidates() {
        List<String> out = new ArrayList<>();
        World w = plugin.game().world();
        if (w == null) return out;
        for (Structures.Placed p : plugin.structures().finished(w)) {
            String use = plugin.places().useOf(p.name());
            if (use != null && EFFECTS.containsKey(use) && !out.contains(p.name())) out.add(p.name());
        }
        return out;
    }

    /** Tire un lieu renforce et un lieu affaibli, et l'annonce. Faux s'il n'y a pas deux lieux poses. */
    public boolean roll() {
        List<String> all = candidates();
        if (all.size() < 2) return false;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<String> forBoost = new ArrayList<>(all);
        if (forBoost.size() > 2) forBoost.remove(boosted);       // pas deux fois de suite le meme
        String up = forBoost.get(r.nextInt(forBoost.size()));
        List<String> forNerf = new ArrayList<>(all);
        forNerf.remove(up);
        if (forNerf.size() > 1) forNerf.remove(nerfed);
        set(up, forNerf.get(r.nextInt(forNerf.size())));
        return true;
    }

    /** Impose le lieu renforce et le lieu affaibli (l'un ou l'autre peut etre null), et l'annonce. */
    public void set(String up, String down) {
        boosted = up;
        nerfed = down;
        Msg.broadcast("<gold><bold>Événement des lieux</bold> <gray>(pour " + interval() + " minutes)");
        if (up != null) Msg.broadcast("<green>▲ " + name(up) + " <gray>: <white>" + effect(up, 0) + "<gray>.");
        if (down != null) Msg.broadcast("<red>▼ " + name(down) + " <gray>: <white>" + effect(down, 1) + "<gray>.");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(net.kyori.adventure.title.Title.title(Msg.mm("<gold>Événement des lieux"),
                    Msg.mm((up != null ? "<green>▲ " + name(up) : "") + (up != null && down != null ? "  " : "") + (down != null ? "<red>▼ " + name(down) : ""))));
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1f, 1f);
        }
    }

    /** Plus aucun lieu renforce ni affaibli (sans annonce). */
    public void clear() {
        boosted = null;
        nerfed = null;
        slot = 0;
    }

    private String name(String place) {
        String title = plugin.structures().title(place);
        return title == null || title.isBlank() ? place : title;
    }

    private String effect(String place, int which) {
        String[] e = EFFECTS.get(plugin.places().useOf(place));
        return e == null ? "" : e[which];
    }

    /** Ou en est ce service : 1 si son lieu est renforce, -1 s'il est affaibli, 0 sinon. */
    public int mood(String use) {
        if (use == null) return 0;
        if (boosted != null && use.equals(plugin.places().useOf(boosted))) return 1;
        if (nerfed != null && use.equals(plugin.places().useOf(nerfed))) return -1;
        return 0;
    }

    /** Une phrase a dire au joueur qui se sert d'un service renforce ou affaibli, ou null. */
    public String notice(String use) {
        int m = mood(use);
        String[] e = EFFECTS.get(use);
        if (m == 0 || e == null) return null;
        return m > 0 ? "<green>▲ Lieu renforcé : <white>" + e[0] + "<green>." : "<red>▼ Lieu affaibli : <white>" + e[1] + "<red>.";
    }

    /** Pour /cf evenement : ce qui est en cours. */
    public String status() {
        if (boosted == null && nerfed == null) return "<gray>Aucun événement des lieux en cours.";
        int left = interval() * 60 - plugin.game().elapsedSeconds() % (interval() * 60);
        return (boosted != null ? "<green>▲ " + name(boosted) + " <gray>(" + effect(boosted, 0) + ")  " : "") + (nerfed != null ? "<red>▼ " + name(nerfed) + " <gray>(" + effect(nerfed, 1) + ")  " : "")
                + (plugin.game().isRunning() ? "<dark_gray>prochain tirage dans " + Msg.time(left) : "");
    }

    public List<String> places() { return candidates(); }
}
