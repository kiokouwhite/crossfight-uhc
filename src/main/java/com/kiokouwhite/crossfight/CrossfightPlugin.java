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
    private com.kiokouwhite.crossfight.game.TestMode testMode;
    private com.kiokouwhite.crossfight.listener.LegacyCombatListener legacyCombat;

    public static CrossfightPlugin get() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        com.kiokouwhite.crossfight.util.Sounds.load(this);
        com.kiokouwhite.crossfight.util.PackHost.start(this);
        com.kiokouwhite.crossfight.util.Puppet.load(this);
        com.kiokouwhite.crossfight.util.Models.load(this);
        com.kiokouwhite.crossfight.util.Skins.load(this);

        playerManager = new PlayerManager();
        teamManager = new TeamManager(this);
        characterRegistry = new CharacterRegistry();
        abilityEngine = new AbilityEngine(this);
        livesManager = new LivesManager(this);
        gameManager = new GameManager(this);
        roster = new com.kiokouwhite.crossfight.game.Roster(this);
        gameManager.loadWorld();
        scoreboardManager = new ScoreboardManager(this);
        testMode = new com.kiokouwhite.crossfight.game.TestMode(this);
        testMode.load();

        characterRegistry.registerDefaults();

        var pm = getServer().getPluginManager();
        pm.registerEvents(abilityEngine, this);
        pm.registerEvents(new ConnectionListener(this), this);
        pm.registerEvents(new com.kiokouwhite.crossfight.listener.ArmorHider(this), this);
        effectHud = new com.kiokouwhite.crossfight.hud.EffectHud(this);
        pm.registerEvents(effectHud, this);
        effectHud.start();
        music = new com.kiokouwhite.crossfight.util.Music(this);
        pm.registerEvents(music, this);
        pm.registerEvents(new DamageListener(this), this);
        scenarios = new com.kiokouwhite.crossfight.game.Scenarios(this);
        pm.registerEvents(scenarios, this);
        scenarios.start();
        lootbox = new com.kiokouwhite.crossfight.game.Lootbox(this);
        pm.registerEvents(lootbox, this);
        ferox = new com.kiokouwhite.crossfight.game.RegnaFerox(this);
        pm.registerEvents(ferox, this);
        omori = new com.kiokouwhite.crossfight.game.Omori(this);
        pm.registerEvents(omori, this);
        // L'arene de Ferox est sur la map avant meme la partie : on la batit (ou on la retrouve) une fois le serveur pret.
        structures = new com.kiokouwhite.crossfight.game.Structures(this);
        pm.registerEvents(structures, this);
        // Puis les structures (plugins/CrossfightUHC/structures/) qui ne sont pas encore sur cette map, a des endroits tires au sort.
        worldMap = new com.kiokouwhite.crossfight.game.WorldMap(this);
        pm.registerEvents(worldMap, this);
        quests = new com.kiokouwhite.crossfight.game.Quests(this);
        getServer().getPluginManager().registerEvents(quests, this);
        rhythm = new com.kiokouwhite.crossfight.game.Rhythm(this);
        getServer().getPluginManager().registerEvents(rhythm, this);
        places = new com.kiokouwhite.crossfight.game.Places(this);
        headquarters = new com.kiokouwhite.crossfight.game.Headquarters(this);
        getServer().getPluginManager().registerEvents(headquarters, this);
        placeEvents = new com.kiokouwhite.crossfight.game.PlaceEvents(this);
        pm.registerEvents(places, this);
        getServer().getScheduler().runTask(this, () -> { ferox.build(); structures.scatter(gameManager.world()); structures.shine(gameManager.world()); worldMap.attach(); });
        getServer().getScheduler().runTaskTimer(this, structures::sparkle, 100L, 5L);
        hostTools = new com.kiokouwhite.crossfight.game.HostTools(this);
        pm.registerEvents(hostTools, this);
        ores = new com.kiokouwhite.crossfight.game.Ores(this);
        pm.registerEvents(ores, this);
        ores.boostLoaded();
        pm.registerEvents(new DeathListener(this), this);
        pm.registerEvents(new RestrictionListener(this), this);
        pm.registerEvents(new com.kiokouwhite.crossfight.listener.PlacedBlocks(this), this);
        pm.registerEvents(characterRegistry.selectMenu(this), this);
        pm.registerEvents(new com.kiokouwhite.crossfight.util.ChoiceMenu(), this);
        pm.registerEvents(new com.kiokouwhite.crossfight.util.Mannequins(), this);
        pm.registerEvents(new com.kiokouwhite.crossfight.util.Clones(), this);
        pm.registerEvents(new com.kiokouwhite.crossfight.util.Bullets(), this);
        pm.registerEvents(testMode, this);
        selectionStage = new com.kiokouwhite.crossfight.game.SelectionStage(this);
        pm.registerEvents(selectionStage, this);
        pm.registerEvents(testMode.thirdPerson(), this);
        legacyCombat = new com.kiokouwhite.crossfight.listener.LegacyCombatListener(this);
        pm.registerEvents(legacyCombat, this);
        for (var p : getServer().getOnlinePlayers()) legacyCombat.apply(p);
        hostTools.refreshAll();
        for (var licence : characterRegistry.licences()) {
            for (var l : licence.listeners()) pm.registerEvents(l, this);
        }

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

        // Pre-creation des mondes d'arene pour eviter un lag en pleine partie.
        try {
            com.kiokouwhite.crossfight.character.fireemblem.Byleth.dimension();
            com.kiokouwhite.crossfight.game.TestMode.world();
            com.kiokouwhite.crossfight.game.SelectionStage.world();
        } catch (Exception ex) {
            getLogger().warning("Impossible de preparer la dimension de Byleth : " + ex.getMessage());
        }

        abilityEngine.start();
        scoreboardManager.start();
        getLogger().info("Crossfight UHC active. " + characterRegistry.characterCount()
                + " personnages dans " + characterRegistry.licences().size() + " licences.");
    }

    @Override
    public void onDisable() {
        com.kiokouwhite.crossfight.util.PackHost.stop();
        if (rhythm != null) rhythm.reset();
        com.kiokouwhite.crossfight.util.Puppet.removeAll();
        if (testMode != null) { testMode.save(); testMode.thirdPerson().stop(); }
        if (selectionStage != null) selectionStage.closeAll(true);
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
    private com.kiokouwhite.crossfight.util.Music music;
    public com.kiokouwhite.crossfight.util.Music music() { return music; }
    public GameManager game() { return gameManager; }
    public ScoreboardManager scoreboard() { return scoreboardManager; }
    public com.kiokouwhite.crossfight.game.TestMode testMode() { return testMode; }
    private com.kiokouwhite.crossfight.game.Ores ores;
    private com.kiokouwhite.crossfight.game.HostTools hostTools;
    private com.kiokouwhite.crossfight.game.Lootbox lootbox;
    private com.kiokouwhite.crossfight.game.RegnaFerox ferox;
    private com.kiokouwhite.crossfight.game.Omori omori;
    public com.kiokouwhite.crossfight.game.Omori omori() { return omori; }
    public com.kiokouwhite.crossfight.game.RegnaFerox ferox() { return ferox; }
    private com.kiokouwhite.crossfight.game.Structures structures;
    public com.kiokouwhite.crossfight.game.Structures structures() { return structures; }
    private com.kiokouwhite.crossfight.game.WorldMap worldMap;
    public com.kiokouwhite.crossfight.game.WorldMap worldMap() { return worldMap; }
    private com.kiokouwhite.crossfight.game.Places places;
    private com.kiokouwhite.crossfight.game.Headquarters headquarters;
    private com.kiokouwhite.crossfight.game.PlaceEvents placeEvents;
    public com.kiokouwhite.crossfight.game.Places places() { return places; }
    public com.kiokouwhite.crossfight.game.Headquarters headquarters() { return headquarters; }
    public com.kiokouwhite.crossfight.game.PlaceEvents placeEvents() { return placeEvents; }
    private com.kiokouwhite.crossfight.game.Rhythm rhythm;
    public com.kiokouwhite.crossfight.game.Rhythm rhythm() { return rhythm; }
    private com.kiokouwhite.crossfight.game.Quests quests;
    public com.kiokouwhite.crossfight.game.Quests quests() { return quests; }
    public com.kiokouwhite.crossfight.game.Lootbox lootbox() { return lootbox; }
    private com.kiokouwhite.crossfight.game.Roster roster;
    public com.kiokouwhite.crossfight.game.Roster roster() { return roster; }
    private com.kiokouwhite.crossfight.game.Scenarios scenarios;
    public com.kiokouwhite.crossfight.game.Scenarios scenarios() { return scenarios; }
    public com.kiokouwhite.crossfight.game.HostTools hostTools() { return hostTools; }
    public com.kiokouwhite.crossfight.game.Ores ores() { return ores; }
    private com.kiokouwhite.crossfight.game.SelectionStage selectionStage;
    private com.kiokouwhite.crossfight.hud.EffectHud effectHud;

    /** Les bonus et malus sur les bords de l'ecran. */
    public com.kiokouwhite.crossfight.hud.EffectHud effectHud() { return effectHud; }
    /** L'ecran de choix du personnage en 3D. */
    public com.kiokouwhite.crossfight.game.SelectionStage selection() { return selectionStage; }
    public com.kiokouwhite.crossfight.listener.LegacyCombatListener legacyCombat() { return legacyCombat; }
}
