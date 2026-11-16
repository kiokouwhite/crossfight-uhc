package com.kiokouwhite.crossfight.team;

import com.kiokouwhite.crossfight.character.Licence;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Une equipe : ses membres, sa couleur, son format (solitaire, duo ou trio : chaque equipe a le sien) et la
 * licence qui lui a ete attribuee.
 */
public final class Team {

    private final int id;
    private final String color; // tag MiniMessage, ex: "red"
    private final List<UUID> members = new ArrayList<>();
    private Licence licence;
    private boolean eliminated;

    private final TeamSize kind;

    public Team(int id, String color, TeamSize kind) {
        this.id = id;
        this.color = color;
        this.kind = kind;
    }

    /** Son format : il fixe le nombre de places et la famille de licences dans laquelle la sienne est tiree. */
    public TeamSize kind() { return kind; }
    public int capacity() { return kind.size(); }
    public boolean isFull() { return members.size() >= kind.size(); }

    public int id() { return id; }
    public String color() { return color; }
    public String name() { return "Equipe " + id; }
    public String coloredName() { return "<" + color + ">" + name() + "</" + color + ">"; }

    public List<UUID> members() { return Collections.unmodifiableList(members); }
    public boolean contains(UUID uuid) { return members.contains(uuid); }
    public int size() { return members.size(); }

    void add(UUID uuid) { if (!members.contains(uuid)) members.add(uuid); }
    void remove(UUID uuid) { members.remove(uuid); }

    public Licence licence() { return licence; }
    public void setLicence(Licence licence) { this.licence = licence; }

    public boolean isEliminated() { return eliminated; }
    public void setEliminated(boolean eliminated) { this.eliminated = eliminated; }

    public List<Player> onlineMembers() {
        List<Player> out = new ArrayList<>();
        for (UUID u : members) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) out.add(p);
        }
        return out;
    }
}
