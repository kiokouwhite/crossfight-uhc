package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.function.Consumer;

/** Une enigme jouee sur un tableau (Panel) : elle recoit les clics, la touche F, les ticks, et dit quand elle est resolue. */
abstract class PanelGame extends Panel {

    final Consumer<Player> solved;
    int lastClick = Bukkit.getCurrentTick() + 5;                  // le clic qui l'ouvre ne compte pas
    int solvedAt = -1;

    PanelGame(CrossfightPlugin plugin, Player player, Location pillar, double width, double height, Consumer<Player> solved) {
        super(plugin, player, pillar, width, height);
        this.solved = solved;
    }

    /** Un clic (gauche ou droit) : le viseur (cu, cv, aimed) est a jour. */
    abstract void onClick();

    /** La touche F. */
    void onRotate() { }

    /** Chaque tick, tant que l'enigme n'est pas resolue : le viseur est a jour. */
    abstract void onTick();

    /** Ce qu'on dit au joueur a l'ouverture. */
    abstract String hint();

    final void click() {
        int now = Bukkit.getCurrentTick();
        if (now - lastClick < 3 || solvedAt >= 0) return;
        lastClick = now;
        aim();
        onClick();
    }

    final void rotate() {
        if (solvedAt >= 0) return;
        aim();
        onRotate();
    }

    final void tick() {
        if (solvedAt >= 0) return;
        aim();
        point();
        onTick();
    }

    /** L'enigme est resolue : un son, puis le tableau se ferme et la suite est appelee (Puzzles). */
    void win(Sound sound) {
        if (solvedAt >= 0) return;
        solvedAt = Bukkit.getCurrentTick();
        player.playSound(player.getLocation(), sound, 1f, 1.2f);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
    }
}
