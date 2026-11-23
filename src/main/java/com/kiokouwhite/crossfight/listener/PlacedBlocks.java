package com.kiokouwhite.crossfight.listener;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Les blocs poses par les joueurs pendant la partie se cassent d'eux-memes apres un delai (regle blocs-poses,
 * 15 s par defaut, 0 : jamais) : une tour ou un mur ne tient pas. Le bloc se fissure a vue d'oeil puis casse
 * comme s'il etait mine (il lache son objet). Ce qui sert a s'equiper reste en place : les blocs qu'on ouvre ou
 * qu'on utilise (etabli, four, coffre, enclume, lit...) et les sources de lumiere (torches, lanternes).
 */
public final class PlacedBlocks implements Listener {

    /** Un bloc pose : ce qu'il est, quand il a ete pose, quand il casse (millisecondes). */
    private record Placed(Material type, long at, long until, int crackId) {}

    private final CrossfightPlugin plugin;
    private final Map<Location, Placed> blocks = new LinkedHashMap<>();
    private int nextId = 1_000_000;

    public PlacedBlocks(CrossfightPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    /** Vrai pour un bloc qui reste en place : on l'ouvre, on l'utilise, ou il eclaire. */
    private static boolean kept(Block b) {
        return b.getType().isInteractable() || b.getState() instanceof TileState || b.getBlockData().getLightEmission() > 0;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        int seconds = plugin.game().rules().value("blocs-poses");
        if (seconds <= 0 || !plugin.game().isRunning() || e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        Block b = e.getBlockPlaced();
        if (kept(b)) return;
        long now = System.currentTimeMillis();
        blocks.put(b.getLocation(), new Placed(b.getType(), now, now + seconds * 1000L, nextId++));
    }

    /** Casse a la main avant l'heure : plus rien a faire pour ce bloc. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { blocks.remove(e.getBlock().getLocation()); }

    private void tick() {
        if (blocks.isEmpty()) return;
        if (!plugin.game().isRunning()) { blocks.clear(); return; }
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<Location, Placed>> it = blocks.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Location, Placed> en = it.next();
            Location at = en.getKey();
            Placed p = en.getValue();
            if (at.getWorld() == null || !at.isChunkLoaded()) { if (now >= p.until()) it.remove(); continue; }
            Block b = at.getBlock();
            // Deja casse, remplace ou tombe (sable) : ce n'est plus ce bloc-la.
            if (b.getType() != p.type()) { it.remove(); continue; }
            if (now >= p.until()) {
                it.remove();
                crack(at, p.crackId(), 0f);
                b.breakNaturally(true);
                continue;
            }
            crack(at, p.crackId(), Math.max(0.01f, Math.min(1f, (now - p.at()) / (float) (p.until() - p.at()))));
        }
    }

    /** Les fissures du bloc, pour les joueurs a portee de vue (0 : les efface). */
    private static void crack(Location at, int id, float progress) {
        for (Player viewer : at.getWorld().getPlayers())
            if (viewer.getLocation().distanceSquared(at) < 48 * 48) viewer.sendBlockDamage(at, progress, id);
    }
}
