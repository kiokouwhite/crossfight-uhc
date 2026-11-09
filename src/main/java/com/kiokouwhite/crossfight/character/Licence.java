package com.kiokouwhite.crossfight.character;

import com.kiokouwhite.crossfight.team.TeamSize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Une licence (Fire Emblem, RWBY...) et ses personnages. Chaque licence est
 * reservee a une taille d'equipe.
 */
public class Licence {

    private final String id;
    private final String name;
    private final TeamSize teamSize;
    private final String particularite;
    private final List<Character> characters = new ArrayList<>();

    public Licence(String id, String name, TeamSize teamSize, String particularite) {
        this.id = id;
        this.name = name;
        this.teamSize = teamSize;
        this.particularite = particularite;
    }

    public String id() { return id; }
    public String name() { return name; }
    public TeamSize teamSize() { return teamSize; }
    public String particularite() { return particularite; }

    public List<Character> characters() { return Collections.unmodifiableList(characters); }

    public Licence add(Character c) {
        c.setLicence(this);
        characters.add(c);
        return this;
    }

    public Character character(String id) {
        for (Character c : characters) if (c.id().equalsIgnoreCase(id)) return c;
        return null;
    }
}
