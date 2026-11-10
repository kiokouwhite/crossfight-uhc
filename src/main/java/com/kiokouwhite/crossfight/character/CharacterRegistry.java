package com.kiokouwhite.crossfight.character;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.team.TeamSize;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registre de toutes les licences et de tous les personnages.
 */
public final class CharacterRegistry {

    private final Map<String, Licence> licences = new LinkedHashMap<>();
    private CharacterSelectMenu selectMenu;

    public void register(Licence licence) {
        licences.put(licence.id(), licence);
    }

    /** Declare les licences du doc. Les personnages sont ajoutes licence par licence. */
    public void registerDefaults() {
        // Equipe de trois
        register(com.kiokouwhite.crossfight.character.fireemblem.FireEmblem.create());
        register(com.kiokouwhite.crossfight.character.rwby.RWBY.create());
        register(com.kiokouwhite.crossfight.character.valorant.Valorant.create());
        register(com.kiokouwhite.crossfight.character.snk.SNK.create());
        register(com.kiokouwhite.crossfight.character.unordinary.Unordinary.create());
        register(com.kiokouwhite.crossfight.character.earthdelta.EarthDelta.create());
        // Groupe duo
        register(com.kiokouwhite.crossfight.character.gunvolt.ABG.create());
        register(com.kiokouwhite.crossfight.character.shoja.Shoja.create());
        register(com.kiokouwhite.crossfight.character.bloodysnow.BloodySnow.create());
        // Solitaire
        register(com.kiokouwhite.crossfight.character.solo.Solo.create());
    }

    public Collection<Licence> licences() { return licences.values(); }

    public Licence licence(String id) { return licences.get(id); }

    /**
     * Licences jouables pour une taille d'equipe. Si aucune n'a encore de
     * personnage, on renvoie toutes celles de la taille pour pouvoir tester.
     */
    public List<Licence> licencesFor(TeamSize size) {
        List<Licence> withChars = new ArrayList<>();
        List<Licence> all = new ArrayList<>();
        for (Licence l : licences.values()) {
            if (l.teamSize() != size) continue;
            all.add(l);
            if (!l.characters().isEmpty()) withChars.add(l);
        }
        return withChars.isEmpty() ? all : withChars;
    }

    public Character character(String id) {
        for (Licence l : licences.values()) {
            Character c = l.character(id);
            if (c != null) return c;
        }
        return null;
    }

    public int characterCount() {
        int n = 0;
        for (Licence l : licences.values()) n += l.characters().size();
        return n;
    }

    public CharacterSelectMenu selectMenu(CrossfightPlugin plugin) {
        if (selectMenu == null) selectMenu = new CharacterSelectMenu(plugin);
        return selectMenu;
    }
}
