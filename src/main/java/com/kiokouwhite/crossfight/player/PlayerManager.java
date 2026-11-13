package com.kiokouwhite.crossfight.player;

import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Registre des joueurs connus de la partie.
 */
public final class PlayerManager {

    private final Map<UUID, CfPlayer> players = new HashMap<>();

    public CfPlayer get(Player p) {
        return players.computeIfAbsent(p.getUniqueId(), u -> new CfPlayer(u, p.getName()));
    }

    public CfPlayer get(UUID uuid) {
        return players.get(uuid);
    }

    /** Oublie un joueur (le joueur pilote d'un mannequin, quand il disparait). */
    public void remove(UUID uuid) {
        players.remove(uuid);
    }

    public Collection<CfPlayer> all() {
        return players.values();
    }

    public void clear() {
        players.clear();
    }
}
