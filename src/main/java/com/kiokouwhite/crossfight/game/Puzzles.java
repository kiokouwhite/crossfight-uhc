package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Les enigmes des piliers de Central Town qui se jouent sur un tableau dresse dans le monde (PanelGame) : le circuit
 * (des fils a tourner), l'etabli (refaire un objet sur une table de craft), le puzzle (les pieces d'une image a
 * remettre en place). Cette classe tient l'enigme ouverte de chaque joueur et lui passe ses clics, la touche F et les
 * ticks. La tablette (Tablet) a sa propre classe.
 */
public final class Puzzles implements Listener {

    /** Les enigmes, par nom (celui de /cf enigme). */
    public static final List<String> KINDS = List.of("circuit", "etabli", "puzzle");

    private final CrossfightPlugin plugin;
    private final Map<UUID, PanelGame> games = new HashMap<>();

    public Puzzles(CrossfightPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 1L);
    }

    /** Ouvre cette enigme a ce joueur, devant ce pilier ; {@code solved} est appele quand il la resout. */
    public void open(Player p, String kind, Location pillar, Consumer<Player> solved) {
        close(p);
        Random r = new Random();
        PanelGame g = switch (kind) {
            case "etabli" -> new Workbench(plugin, p, pillar, r, solved);
            case "puzzle" -> new Jigsaw(plugin, p, pillar, r, solved);
            default -> new Circuit(plugin, p, pillar, r, solved);
        };
        games.put(p.getUniqueId(), g);
        Msg.actionBar(p, g.hint());
    }

    /** Ouvre le casse-briques d'Arcadia a ce joueur : {@code total} dit ou en est son equipe, {@code caught} est appele a chaque pomme rattrapee. */
    public void openBreakout(Player p, Location pillar, java.util.function.IntSupplier total, int goal, Runnable caught) {
        close(p);
        PanelGame g = new Breakout(plugin, p, pillar, new Random(), total, goal, caught);
        games.put(p.getUniqueId(), g);
        Msg.actionBar(p, g.hint());
    }

    public boolean isOpen(Player p) {
        return games.containsKey(p.getUniqueId());
    }

    public void close(Player p) {
        PanelGame g = games.remove(p.getUniqueId());
        if (g != null) g.remove();
    }

    public void reset() {
        for (PanelGame g : games.values()) g.remove();
        games.clear();
    }

    private void tick() {
        for (PanelGame g : new ArrayList<>(games.values())) {
            Player p = g.player;
            if (g.lost()) { close(p); continue; }
            if (g.solvedAt >= 0) {
                if (Bukkit.getCurrentTick() - g.solvedAt > 25) { close(p); g.solved.accept(p); }
                continue;
            }
            g.tick();
        }
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        PanelGame g = games.get(e.getPlayer().getUniqueId());
        if (g != null) g.click();
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        PanelGame g = games.get(e.getPlayer().getUniqueId());
        if (g == null) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) g.click();
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onUseEntity(PlayerInteractEntityEvent e) {
        PanelGame g = games.get(e.getPlayer().getUniqueId());
        if (g == null) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) g.click();
    }

    @EventHandler
    public void onAttack(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        // le coup de bras qui suit fait le clic
        if (games.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        PanelGame g = games.get(e.getPlayer().getUniqueId());
        if (g == null) return;
        e.setCancelled(true);
        g.rotate();
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (e.isSneaking() && games.containsKey(e.getPlayer().getUniqueId())) {
            close(e.getPlayer());
            Msg.actionBar(e.getPlayer(), "<gray>Énigme fermée : reclique sur le pilier pour réessayer.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        close(e.getPlayer());
    }
}
