package com.kiokouwhite.crossfight.player;

import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.team.Team;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Etat d'un joueur dans la partie : equipe, personnage, vies, jauges.
 */
public final class CfPlayer {

    private final UUID uuid;
    private final String name;
    private Team team;
    private Character character;
    private int lives;
    private boolean waitingRespawn;
    private boolean spectatorOnly;

    /** Jauge d'ultime, de 0 a 100. */
    private double ultimateCharge;
    /** Degats infliges cumules, en pourcentage de barre de vie (10 coeurs = 100). Utilise pour Synergie/Burst. */
    private double damageDealtPercent;
    private double damageTakenPercent;
    private int kills;
    /** Multiplicateur des temps de chargement (1.0 = normal, 0.95 = -5 %). */
    private double cooldownFactor = 1.0;

    public CfPlayer(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
    }

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public Player bukkit() { return Bukkit.getPlayer(uuid); }
    public boolean isOnline() { return bukkit() != null; }

    public Team team() { return team; }
    public void setTeam(Team team) { this.team = team; }

    public Character character() { return character; }
    public void setCharacter(Character character) { this.character = character; }

    public int lives() { return lives; }
    public void setLives(int lives) { this.lives = Math.max(0, lives); }
    public void addLife(int n) { this.lives = Math.max(0, this.lives + n); }
    public boolean isAlive() { return lives > 0; }

    public boolean isWaitingRespawn() { return waitingRespawn; }
    public void setWaitingRespawn(boolean waitingRespawn) { this.waitingRespawn = waitingRespawn; }

    public boolean isSpectatorOnly() { return spectatorOnly; }
    public void setSpectatorOnly(boolean spectatorOnly) { this.spectatorOnly = spectatorOnly; }

    public double ultimateCharge() { return ultimateCharge; }
    public boolean isUltimateReady() { return ultimateCharge >= 100.0; }
    public void addUltimateCharge(double percent) { ultimateCharge = Math.min(100.0, Math.max(0.0, ultimateCharge + percent)); }
    public void setUltimateCharge(double v) { ultimateCharge = Math.min(100.0, Math.max(0.0, v)); }
    public void resetUltimate() { ultimateCharge = 0.0; }

    public double damageDealtPercent() { return damageDealtPercent; }
    public double damageTakenPercent() { return damageTakenPercent; }
    public void addDamageDealt(double percent) { damageDealtPercent += percent; }
    public void addDamageTaken(double percent) { damageTakenPercent += percent; }

    public double cooldownFactor() { return cooldownFactor; }
    public void setCooldownFactor(double f) { this.cooldownFactor = Math.max(0.1, f); }

    public int kills() { return kills; }
    public void addKill() { kills++; }

    /** Remet a zero l'etat de partie (pas l'equipe). */
    public void resetForNewGame(int lives) {
        this.lives = lives;
        this.waitingRespawn = false;
        this.ultimateCharge = 0;
        this.damageDealtPercent = 0;
        this.damageTakenPercent = 0;
        this.kills = 0;
        this.character = null;
        this.cooldownFactor = 1.0;
    }
}
