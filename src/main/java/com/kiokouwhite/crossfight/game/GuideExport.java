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
               .append(",\"team\":").append(q(l.teamSize().name())).append(",\"particularite\":").append(q(l.particularite())).append(",\"characters\":[");
            boolean firstChar = true;
            for (Character c : l.characters()) {
                if (!firstChar) out.append(',');
                firstChar = false;
                String weapon = null;
                try {
                    ItemStack w = c.createWeapon();
                    if (w != null && w.getItemMeta() != null && w.getItemMeta().displayName() != null)
                        weapon = PlainTextComponentSerializer.plainText().serialize(w.getItemMeta().displayName());
                } catch (Exception ignored) { }
                out.append("\n {\"id\":").append(q(c.id())).append(",\"name\":").append(q(c.name()))
                   .append(",\"voie\":").append(q(c.voie().label())).append(",\"weapon\":").append(q(weapon)).append(",\"abilities\":[");
                boolean firstAbility = true;
                for (Ability a : c.allAbilities()) {
                    if (!firstAbility) out.append(',');
                    firstAbility = false;
                    int cd = a instanceof ActiveAbility act ? act.cooldownSeconds() : 0;
                    out.append("\n  {\"id\":").append(q(a.id())).append(",\"name\":").append(q(a.name()))
                       .append(",\"type\":").append(q(a.type().label())).append(",\"description\":").append(q(a.description()))
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
