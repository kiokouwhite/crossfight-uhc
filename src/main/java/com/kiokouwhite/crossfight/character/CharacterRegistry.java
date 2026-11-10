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
        register(new Licence("snk", "SHINGEKI NO KYOJIN", TeamSize.TRIO,
                "Equipement tridimensionnel avec une jauge de gaz de 100."));
        register(new Licence("unordinary", "UNORDINARY", TeamSize.TRIO,
                "Chacun craft une epee d'Aura dont l'actif depend du craft."));
        register(new Licence("earth_delta", "EARTH DELTA", TeamSize.TRIO,
                "Un effet passif aleatoire cumulable a chaque mort."));
        // Groupe duo
        register(new Licence("gunvolt", "ARMED BLUE GUNVOLT", TeamSize.DUO,
                "Synergie : second ultime commun, charge a 200 % de degats a deux."));
        register(new Licence("shojas_memories", "SHOJA'S MEMORIES", TeamSize.DUO,
                "Synergie : second ultime commun, charge a 200 % de degats a deux."));
        register(new Licence("bloody_snow", "BLOODY SNOW", TeamSize.DUO,
                "Synergie : second ultime commun, charge a 200 % de degats a deux."));
        // Solitaire
        register(new Licence("solitaire", "PERSONNAGE SOLITAIRE", TeamSize.SOLO,
                "Burst : second ultime charge a 1000 % de degats, conserve entre les morts."));
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
