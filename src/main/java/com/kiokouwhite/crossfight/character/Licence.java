package com.kiokouwhite.crossfight.character;

import com.kiokouwhite.crossfight.team.TeamSize;
import org.bukkit.event.Listener;

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
    private LicenceMechanic mechanic;
    private final List<Listener> listeners = new ArrayList<>();

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

    public LicenceMechanic mechanic() { return mechanic; }

    public Licence mechanic(LicenceMechanic mechanic) {
        this.mechanic = mechanic;
        return this;
    }

    /** Ecouteurs d'evenements propres a la licence, enregistres au demarrage. */
    public Licence listener(Listener l) {
        listeners.add(l);
        return this;
    }

    public List<Listener> listeners() { return Collections.unmodifiableList(listeners); }

    public Character character(String id) {
        for (Character c : characters) if (c.id().equalsIgnoreCase(id)) return c;
        return null;
    }
}
