package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.Msg;

/**
 * Planification des evenements de la partie (lootbox, Regna Ferox, Match Found,
 * Judgement, Omori). Les Lootbox (game/Lootbox.java), Regna Ferox (game/RegnaFerox.java) et Omori (game/Omori.java) sont en place ; les autres sont
 * encore seulement annonces, leur logique sera branchee ici au fur et a mesure.
 */
public final class GameEvents {

    private final CrossfightPlugin plugin;

    public GameEvents(CrossfightPlugin plugin) {
        this.plugin = plugin;
    }

    private int minuteOf(String key, int def) {
        return plugin.getConfig().getInt("game.events." + key, def);
    }

    public void onMinute(int minute) {
        if (minute == minuteOf("lootbox", 20)) {
            plugin.lootbox().wave();
        }
        if (minute == minuteOf("regna-ferox", 30)) {
            plugin.ferox().ask();
        }
        if (minute == minuteOf("match-found", 40)) {
            // L'evenement aura lieu dans l'un des lieux prevus pour lui (game.events.match-found-lieux), s'il est sur la map.
            java.util.List<String> wanted = plugin.getConfig().getStringList("game.events.match-found-lieux");
            if (wanted.isEmpty()) wanted = java.util.List.of("glitch", "lotus");
            java.util.List<Structures.Placed> here = new java.util.ArrayList<>();
            for (Structures.Placed p : plugin.structures().finished(plugin.game().world())) if (wanted.contains(p.name())) here.add(p);
            String where = "";
            if (!here.isEmpty()) {
                Structures.Placed p = here.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(here.size()));
                where = " <white>a " + plugin.structures().title(p.name()) + " <gray>(" + (p.x() + p.sx() / 2) + ", " + (p.z() + p.sz() / 2) + ")";
            }
            Msg.broadcast("<gold><bold>Evenement :</bold> Match Found !" + where + " <dark_gray>(a venir)");
        }
        if (minute == minuteOf("judgement", 50)) {
            Msg.broadcast("<dark_red><bold>Evenement :</bold> l'antre de Judgement s'ouvre au centre de la map. <dark_gray>(a venir)");
        }
        if (minute == minuteOf("omori", 60)) {
            plugin.omori().open();
        }
    }
}
