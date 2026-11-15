package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.GameRule;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les regles d'une partie, reglables en jeu (/cf regle), et les administrateurs de partie (/cf admin) : des
 * joueurs a qui un operateur a confie le droit de les regler et de lancer ou arreter la partie, sans etre
 * operateurs eux-memes. Tout est garde dans config.yml.
 */
public final class Rules {

    /** Une regle : son nom en jeu, sa cle dans config.yml, sa valeur par defaut, ses bornes, et ce qu'elle regle. */
    public record Rule(String name, String path, int def, int min, int max, String unit, String help, boolean toggle) {}

    private static final Map<String, Rule> RULES = new LinkedHashMap<>();

    private static void add(String name, String path, int def, int min, int max, String unit, String help) {
        RULES.put(name, new Rule(name, path, def, min, max, unit, help, false));
    }

    private static void toggle(String name, String path, boolean def, String help) {
        RULES.put(name, new Rule(name, path, def ? 1 : 0, 0, 1, "", help, true));
    }

    static {
        add("bordure", "world.border-radius", 1000, 50, 10000, "blocs", "Rayon de la bordure au depart");
        add("bordure-finale", "game.border-final-radius", 100, 10, 10000, "blocs", "Rayon de la bordure une fois reduite");
        add("bordure-debut", "game.border-shrink-minute", 60, 0, 600, "min", "Minute ou la bordure commence a se reduire (0 : jamais)");
        add("bordure-duree", "game.border-shrink-minutes", 20, 1, 300, "min", "Temps que met la bordure a se reduire");
        add("pvp", "game.pvp-minute", 30, 0, 600, "min", "Minute ou le PvP s'active (0 : des le depart)");
        add("invincibilite", "game.invincibility-seconds", 30, 0, 600, "s", "Invincibilite au depart");
        add("vies", "game.lives", 3, 1, 10, "", "Vies par joueur");
        add("dispersion", "world.spread-radius", 800, 20, 10000, "blocs", "Rayon dans lequel les equipes sont dispersees au depart");
        add("reapparition", "lives.respawn-radius", 500, 20, 10000, "blocs", "Rayon dans lequel on reapparait apres une mort");
        toggle("regeneration", "game.natural-regeneration", false, "Regeneration naturelle de la vie");
        toggle("jour-permanent", "game.eternal-day", false, "Il fait toujours jour");
        add("lootbox", "game.events.lootbox", 20, 0, 600, "min", "Minute de l'evenement Lootbox");
        add("regna-ferox", "game.events.regna-ferox", 30, 0, 600, "min", "Minute de l'evenement Regna Ferox");
        add("match-found", "game.events.match-found", 40, 0, 600, "min", "Minute de l'evenement Match Found");
        add("judgement", "game.events.judgement", 50, 0, 600, "min", "Minute de l'evenement Judgement");
        add("omori", "game.events.omori", 60, 0, 600, "min", "Minute de l'evenement Omori");
    }

    private final CrossfightPlugin plugin;

    public Rules(CrossfightPlugin plugin) { this.plugin = plugin; }

    public static List<Rule> all() { return new ArrayList<>(RULES.values()); }

    public static Rule rule(String name) { return RULES.get(name.toLowerCase(java.util.Locale.ROOT)); }

    public int value(Rule r) {
        return r.toggle() ? (plugin.getConfig().getBoolean(r.path(), r.def() == 1) ? 1 : 0) : plugin.getConfig().getInt(r.path(), r.def());
    }

    public int value(String name) { return value(RULES.get(name)); }

    public boolean on(String name) { return value(name) == 1; }

    /** La valeur telle qu'on la lit : "oui", "non", "30 min", "1000 blocs"... */
    public String shown(Rule r) {
        int v = value(r);
        return r.toggle() ? (v == 1 ? "oui" : "non") : v + (r.unit().isEmpty() ? "" : " " + r.unit());
    }

    /**
     * Regle une valeur ("oui"/"non" pour un interrupteur, un nombre sinon). Rend null si c'est fait, sinon
     * ce qui ne va pas. Ce qui peut l'etre est applique tout de suite (bordure, regeneration, jour).
     */
    public String set(Rule r, String raw) {
        int v;
        if (r.toggle()) {
            String x = raw.toLowerCase(java.util.Locale.ROOT);
            if (List.of("oui", "on", "true", "1", "vrai").contains(x)) v = 1;
            else if (List.of("non", "off", "false", "0", "faux").contains(x)) v = 0;
            else return "oui ou non";
            plugin.getConfig().set(r.path(), v == 1);
        } else {
            try { v = Integer.parseInt(raw); } catch (NumberFormatException ex) { return "un nombre entre " + r.min() + " et " + r.max(); }
            if (v < r.min() || v > r.max()) return "un nombre entre " + r.min() + " et " + r.max();
            plugin.getConfig().set(r.path(), v);
        }
        plugin.saveConfig();
        apply(plugin.game().world());
        return null;
    }

    /** Applique au monde ce qui se regle hors partie comme en partie : la regeneration et le cycle du jour. */
    public void apply(World w) {
        w.setGameRule(GameRule.NATURAL_REGENERATION, on("regeneration"));
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, !on("jour-permanent"));
        if (on("jour-permanent")) w.setTime(6000);
        // La bordure suit son rayon de depart tant qu'elle n'a pas commence a se reduire.
        if (!plugin.game().isRunning() || !plugin.game().borderShrinking()) w.getWorldBorder().setSize(value("bordure") * 2.0);
    }

    // ------------------------------------------------------------ administrateurs de partie

    /** Vrai si ce joueur a recu les droits d'administrateur de partie. */
    public boolean isHost(UUID id) {
        return plugin.getConfig().isSet("admins." + id);
    }

    public Map<UUID, String> hosts() {
        Map<UUID, String> out = new LinkedHashMap<>();
        var section = plugin.getConfig().getConfigurationSection("admins");
        if (section != null) for (String k : section.getKeys(false)) {
            try { out.put(UUID.fromString(k), section.getString(k, "?")); } catch (IllegalArgumentException ignored) { }
        }
        return out;
    }

    public void setHost(UUID id, String name, boolean host) {
        plugin.getConfig().set("admins." + id, host ? name : null);
        plugin.saveConfig();
    }
}
