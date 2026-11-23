package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.player.CfPlayer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Les tetes des personnages, a ecrire a cote des noms : un glyphe par personnage dans la police "crossfight:tetes"
 * du pack complet (tools/build_tetes.py, "fonttete.<personnage>.<point de code>"). Seuls les joueurs qui ont charge
 * le pack les voient : pour les autres, rien n'est ecrit.
 */
public final class Heads {

    private Heads() {}

    private static final Map<String, Character> GLYPHS = new HashMap<>();
    private static boolean loaded;

    private static void load() {
        if (loaded) return;
        loaded = true;
        for (String ev : Sounds.events()) {
            if (!ev.startsWith("fonttete.")) continue;
            int dot = ev.lastIndexOf('.');
            GLYPHS.put(ev.substring("fonttete.".length(), dot), (char) Integer.parseInt(ev.substring(dot + 1), 16));
        }
    }

    /** Les personnages qui ont une tete dans le pack. */
    public static List<String> known() {
        load();
        List<String> out = new ArrayList<>(GLYPHS.keySet());
        java.util.Collections.sort(out);
        return out;
    }

    /** Vrai si {@code viewer} peut voir la tete de ce personnage (elle est dans le pack, et il l'a charge). */
    public static boolean shows(Player viewer, String character) {
        load();
        return character != null && GLYPHS.containsKey(character) && Sounds.hasPackLoaded(viewer);
    }

    /** La tete d'un personnage, en MiniMessage, suivie d'une espace ; vide si {@code viewer} ne peut pas la voir. */
    public static String tag(Player viewer, String character) {
        return shows(viewer, character) ? "<white><font:crossfight:tetes>" + GLYPHS.get(character) + "</font></white> " : "";
    }

    /** La tete du personnage que joue {@code who}, pour {@code viewer} ; vide s'il n'en a pas. */
    public static String tag(Player viewer, Player who) {
        CfPlayer cf = who == null ? null : CrossfightPlugin.get().players().get(who);
        return cf == null || cf.character() == null ? "" : tag(viewer, cf.character().id());
    }

    /** La meme tete en composant (le prefixe d'un pseudo), ou null. */
    public static Component prefix(Player viewer, String character) {
        if (!shows(viewer, character)) return null;
        // L'espace qui suit est ecrite dans la police normale : celle des tetes n'a que les tetes.
        return Component.text(String.valueOf(GLYPHS.get(character))).font(Key.key(Sounds.NS, "tetes")).color(NamedTextColor.WHITE)
                .append(Component.text(" ").font(Key.key("minecraft", "default")));
    }
}
