package com.kiokouwhite.crossfight.game;

public enum GameState {
    /** En attente dans le lobby, equipes modifiables. */
    LOBBY,
    /** Licences tirees, les joueurs choisissent leur personnage. */
    SELECTING,
    /** Compte a rebours avant teleportation. */
    STARTING,
    /** La partie est en cours. */
    RUNNING,
    /** La partie est terminee. */
    ENDED
}
