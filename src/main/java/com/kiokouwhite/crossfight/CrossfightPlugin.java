package com.kiokouwhite.crossfight;

import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.character.CharacterRegistry;
import com.kiokouwhite.crossfight.command.CfCommand;
import com.kiokouwhite.crossfight.command.VivreCommand;
import com.kiokouwhite.crossfight.game.GameManager;
import com.kiokouwhite.crossfight.game.ScoreboardManager;
import com.kiokouwhite.crossfight.listener.ConnectionListener;
import com.kiokouwhite.crossfight.listener.DamageListener;
import com.kiokouwhite.crossfight.listener.DeathListener;
import com.kiokouwhite.crossfight.listener.RestrictionListener;
import com.kiokouwhite.crossfight.player.LivesManager;
import com.kiokouwhite.crossfight.player.PlayerManager;
import com.kiokouwhite.crossfight.team.TeamManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Point d'entree du plugin Crossfight UHC.
 */
public final class CrossfightPlugin extends JavaPlugin {

    private static CrossfightPlugin instance;

    private PlayerManager playerManager;
    private TeamManager teamManager;
    private CharacterRegistry characterRegistry;
    private AbilityEngine abilityEngine;
    private LivesManager livesManager;
    private GameManager gameManager;
    private ScoreboardManager scoreboardManager;

    public static CrossfightPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        playerManager = new PlayerManager();
        teamManager = new TeamManager(this);
        characterRegistry = new CharacterRegistry();
        abilityEngine = new AbilityEngine(this);
        livesManager = new LivesManager(this);
        gameManager = new GameManager(this);
        scoreboardManager = new ScoreboardManager(this);

        characterRegistry.registerDefaults();

        var pm = getServer().getPluginManager();
        pm.registerEvents(abilityEngine, this);
        pm.registerEvents(new ConnectionListener(this), this);
        pm.registerEvents(new DamageListener(this), this);
        pm.registerEvents(new DeathListener(this), this);
        pm.registerEvents(new RestrictionListener(this), this);
        pm.registerEvents(characterRegistry.selectMenu(this), this);

        var cf = getCommand("cf");
        var vivre = getCommand("vivre");
        if (cf != null) {
            var cmd = new CfCommand(this);
            cf.setExecutor(cmd);
            cf.setTabCompleter(cmd);
        }
        if (vivre != null) {
            vivre.setExecutor(new VivreCommand(this));
        }

        abilityEngine.start();
        scoreboardManager.start();
        getLogger().info("Crossfight UHC active. " + characterRegistry.characterCount()
                + " personnages dans " + characterRegistry.licences().size() + " licences.");
    }

    @Override
    public void onDisable() {
        if (gameManager != null) gameManager.shutdown();
        if (abilityEngine != null) abilityEngine.stop();
        if (scoreboardManager != null) scoreboardManager.stop();
        instance = null;
    }

    public PlayerManager players() { return playerManager; }
    public TeamManager teams() { return teamManager; }
    public CharacterRegistry characters() { return characterRegistry; }
    public AbilityEngine abilities() { return abilityEngine; }
    public LivesManager lives() { return livesManager; }
    public GameManager game() { return gameManager; }
    public ScoreboardManager scoreboard() { return scoreboardManager; }
}
