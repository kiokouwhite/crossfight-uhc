package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Licence;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Exporte les licences, personnages et capacites en JSON (plugins/CrossfightUHC/capacites.json) : la source
 * du guide (tools/build_guide.py) et de la liste des videos a tourner. /cf tournage liste.
 */
public final class GuideExport {
    private GuideExport() {}

    private static String q(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c == '\n') b.append("\\n");
            else if (c < 0x20) b.append(' ');
            else b.append(c);
        }
        return b.append('"').toString();
    }

    public static String write(CrossfightPlugin plugin) {
        StringBuilder out = new StringBuilder("[\n");
        boolean firstLicence = true;
        for (Licence l : plugin.characters().licences()) {
            if (!firstLicence) out.append(",\n");
            firstLicence = false;
            out.append("{\"id\":").append(q(l.id())).append(",\"name\":").append(q(l.name()))
               .append(",\"team\":").append(q(l.teamSize().name())).append(",\"particularite\":").append(q(l.particularite()));
            // RWBY : les six Ultimes en Duo, un par paire.
            if (l.id().equals("rwby")) {
                out.append(",\"duos\":[");
                boolean firstDuo = true;
                for (String[] d : com.kiokouwhite.crossfight.character.rwby.RWBY.duos()) {
                    if (!firstDuo) out.append(',');
                    firstDuo = false;
                    out.append("\n {\"id\":").append(q(d[0])).append(",\"name\":").append(q(d[1])).append(",\"a\":").append(q(d[2]))
                       .append(",\"b\":").append(q(d[3])).append(",\"description\":").append(q(d[4])).append('}');
                }
                out.append("]");
            }
            // Groupes duo : la Synergie de chaque paire de personnages de la licence.
            if (!l.id().equals("rwby")) {
                StringBuilder duos = new StringBuilder();
                var people = l.characters();
                for (int i = 0; i < people.size(); i++) for (int j = i + 1; j < people.size(); j++) {
                    String a = people.get(i).id(), b = people.get(j).id();
                    var ef = com.kiokouwhite.crossfight.character.duo.Synergy.effectOf(a, b);
                    if (ef == null) continue;
                    if (duos.length() > 0) duos.append(',');
                    duos.append("\n {\"id\":").append(q("syn_" + com.kiokouwhite.crossfight.character.duo.Synergy.pairKey(a, b).replace('|', '_')))
                        .append(",\"name\":").append(q(ef.name())).append(",\"a\":").append(q(a)).append(",\"b\":").append(q(b))
                        .append(",\"description\":").append(q(ef.description())).append('}');
                }
                if (duos.length() > 0) out.append(",\"duos\":[").append(duos).append("]");
            }
            out.append(",\"characters\":[");
            boolean firstChar = true;
            for (Character c : l.characters()) {
                if (!firstChar) out.append(',');
                firstChar = false;
                String weapon = null, weaponIcon = "";
                try {
                    ItemStack w = c.createWeapon();
                    if (w != null) weaponIcon = w.getType().getKey().getKey();
                    if (w != null && w.getItemMeta() != null && w.getItemMeta().displayName() != null)
                        weapon = PlainTextComponentSerializer.plainText().serialize(w.getItemMeta().displayName());
                } catch (Exception ignored) { }
                out.append("\n {\"id\":").append(q(c.id())).append(",\"name\":").append(q(c.name()))
                   .append(",\"voie\":").append(q(c.voie().label())).append(",\"weapon\":").append(q(weapon))
                   .append(",\"weaponIcon\":").append(q(weaponIcon)).append(",\"abilities\":[");
                boolean firstAbility = true;
                for (Ability a : c.allAbilities()) {
                    if (!firstAbility) out.append(',');
                    firstAbility = false;
                    int cd = a instanceof ActiveAbility act ? act.cooldownSeconds() : 0;
                    String name = a.name(), text = a.description();
                    // Le Burst est un objet commun aux solitaires, mais chacun a le sien : son nom et son effet.
                    if (a == com.kiokouwhite.crossfight.character.solo.Solo.ITEM && c instanceof com.kiokouwhite.crossfight.character.solo.Solo.Burst b) {
                        name = "Burst : " + b.burstName();
                        text = b.burstDescription() + " Se charge a 1000 % de degats infliges, compteur conserve entre les morts.";
                    }
                    out.append("\n  {\"id\":").append(q(a.id())).append(",\"name\":").append(q(name))
                       .append(",\"type\":").append(q(a.type().label())).append(",\"description\":").append(q(text))
                       .append(",\"cooldown\":").append(cd).append(",\"weapon\":").append(a.isWeaponBound())
                       .append(",\"item\":").append(a.hasItem()).append(",\"icon\":").append(q(a.icon().getKey().getKey())).append('}');
                }
                out.append("]}");
            }
            out.append("]}");
        }
        out.append("\n]\n");
        File f = new File(plugin.getDataFolder(), "capacites.json");
        try {
            Files.writeString(f.toPath(), out.toString(), StandardCharsets.UTF_8);
            return "Ecrit : " + f.getPath();
        } catch (Exception e) {
            return "Echec : " + e;
        }
    }
}
