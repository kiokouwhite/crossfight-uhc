package com.kiokouwhite.crossfight.hud;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.ChoiceMenu;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Les reglages d'affichage du HUD, par joueur (gardes dans ses donnees) : montrer ou non les passifs et la carte
 * d'actif a gauche, les effets des autres a droite, et pour chacun le texte a cote de l'icone. /cf hud ouvre le
 * menu : un clic sur une ligne la bascule, le menu reste ouvert.
 */
public final class HudPrefs {

    private HudPrefs() {}

    public enum Pref {
        ARME("arme", "Arme (en tête, à gauche)", Material.IRON_SWORD, true),
        PASSIFS("passifs", "Passifs (en haut à gauche)", Material.TOTEM_OF_UNDYING, true),
        PASSIFS_TEXTE("passifs_texte", "Texte des passifs", Material.WRITABLE_BOOK, false),
        ACTIFS("actifs", "Actif en cours (sous les passifs)", Material.CLOCK, true),
        ACTIFS_TEXTE("actifs_texte", "Texte de l'actif", Material.WRITABLE_BOOK, false),
        AUTRES("autres", "Effets des autres (en haut à droite)", Material.SHIELD, true),
        AUTRES_TEXTE("autres_texte", "Texte des effets des autres", Material.WRITABLE_BOOK, false),
        TEXTE_COURT("texte_court", "Textes courts (ex. : Regen. 5 PV, immobilisat. 3 s)", Material.PAPER, false);

        final String key, label;
        final Material icon;
        final boolean fallback;
        Pref(String key, String label, Material icon, boolean fallback) { this.key = key; this.label = label; this.icon = icon; this.fallback = fallback; }

        private NamespacedKey nsKey() { return new NamespacedKey(CrossfightPlugin.get(), "hud_" + key); }

        public boolean of(Player p) {
            Byte b = p.getPersistentDataContainer().get(nsKey(), PersistentDataType.BYTE);
            return b == null ? fallback : b == 1;
        }

        public void set(Player p, boolean on) {
            p.getPersistentDataContainer().set(nsKey(), PersistentDataType.BYTE, (byte) (on ? 1 : 0));
        }
    }

    /** Le menu de /cf hud : chaque ligne montre son etat et se bascule d'un clic ; le menu se rouvre apres. */
    public static void open(Player p) {
        List<ChoiceMenu.Option> options = new ArrayList<>();
        for (Pref pref : Pref.values()) {
            boolean on = pref.of(p);
            options.add(new ChoiceMenu.Option(on ? pref.icon : Material.GRAY_DYE,
                    (on ? "<green>✔ " : "<red>✘ ") + "<white>" + pref.label,
                    List.of(on ? "<green>Activé" : "<red>Désactivé", "", "<gray>Clique pour " + (on ? "désactiver" : "activer")),
                    () -> { pref.set(p, !on); open(p); }));
        }
        options.add(new ChoiceMenu.Option(Material.LIME_DYE, "<white>Tout afficher", List.of("<gray>Icônes et textes partout"), () -> { for (Pref x : Pref.values()) if (x != Pref.TEXTE_COURT) x.set(p, true); open(p); }));
        options.add(new ChoiceMenu.Option(Material.BARRIER, "<white>Icônes seules", List.of("<gray>Les icônes, sans aucun texte"), () -> { for (Pref x : Pref.values()) x.set(p, !x.name().endsWith("_TEXTE")); open(p); }));
        options.add(new ChoiceMenu.Option(Material.ARROW, "<white>Fermer", List.of(), () -> Msg.send(p, "<gray>HUD : réglages gardés. <white>/cf hud</white> pour les changer.")));
        ChoiceMenu.open(p, "<dark_gray>Affichage du HUD", options);
    }
}
