package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.Msg;

/**
 * Planification des evenements de la partie (lootbox, Regna Ferox, Match Found,
 * Judgement, Omori). Les Lootbox sont en place (game/Lootbox.java) ; les autres sont
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
            Msg.broadcast("<gold><bold>Evenement :</bold> Regna Ferox ouvre son arene ! <dark_gray>(a venir)");
        }
        if (minute == minuteOf("match-found", 40)) {
            Msg.broadcast("<gold><bold>Evenement :</bold> Match Found ! <dark_gray>(a venir)");
        }
        if (minute == minuteOf("judgement", 50)) {
            Msg.broadcast("<dark_red><bold>Evenement :</bold> l'antre de Judgement s'ouvre au centre de la map. <dark_gray>(a venir)");
        }
        if (minute == minuteOf("omori", 60)) {
            Msg.broadcast("<white><bold>Evenement :</bold> une porte vers l'Espace Blanc apparait... <dark_gray>(a venir)");
        }
    }
}
