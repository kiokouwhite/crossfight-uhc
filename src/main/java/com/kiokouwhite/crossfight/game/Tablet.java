package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
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
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * La tablette : le casse-tete des piliers (la quete de Central Town). Un tableau se dresse devant le pilier, que seul
 * le joueur voit : a gauche la figure a obtenir, a droite une grille, en bas trois pieces dorees. On attrape une piece
 * en la visant et en cliquant, elle suit le viseur, on clique encore pour la poser sur la grille ; F la tourne. La ou
 * deux pieces se recouvrent, les cases s'annulent (elles s'assombrissent). La tablette est resolue quand les trois
 * pieces sont posees et que la grille montre la figure. Chaque tablette est tiree au hasard, et toujours soluble : la
 * figure est faite en posant les pieces quelque part.
 *
 * Le tableau est fait d'affichages de texte (des rectangles de couleur) dans un plan tourne vers le joueur ; le viseur
 * est le point ou son regard coupe ce plan.
 */
public final class Tablet implements Listener {

    private static final int SIZE = 4;
    /** Les pieces possibles : leurs cases (x, y). */
    private static final int[][][] SHAPES = {
            {{0, 0}, {1, 0}},                               // domino
            {{0, 0}, {1, 0}, {2, 0}},                       // barre de trois
            {{0, 0}, {0, 1}, {1, 1}},                       // equerre
            {{0, 0}, {1, 0}, {0, 1}, {1, 1}},               // carre
            {{0, 0}, {1, 0}, {2, 0}, {1, 1}},               // te
            {{0, 0}, {1, 0}, {1, 1}, {2, 1}}};              // marche

    /** Le fond d'un texte d'une espace, a l'echelle 1 : sa largeur et sa hauteur en blocs, et son debord vers la droite. */
    private static final double W0 = 0.125, H0 = 0.25, OFF = 0.0125;
    /** La disposition, en blocs depuis le milieu du tableau (u vers la droite, v vers le haut). */
    private static final double CELL = 0.5, GAP = 0.05, GRID_LEFT = 0.15, GRID_TOP = 1.1;
    private static final double GOAL = 0.3, GOAL_LEFT = -2.15, GOAL_TOP = 1.1;
    private static final double SMALL = 0.15, TRAY_V = -0.85;
    private static final double[] TRAY_U = {-2.0, -1.3, -0.6};
    private static final double WIDTH = 5.0, HEIGHT = 3.2;

    private static final Color BACK = Color.fromARGB(235, 12, 14, 36), EMPTY = Color.fromARGB(255, 34, 40, 76), GOLD = Color.fromARGB(255, 246, 190, 60),
            FADED = Color.fromARGB(255, 110, 92, 58), HELD = Color.fromARGB(200, 255, 140, 40), SLOT = Color.fromARGB(255, 24, 28, 58), NONE = Color.fromARGB(0, 0, 0, 0);

    /** Une tablette ouverte chez un joueur. */
    private final class Board extends Panel {
        final boolean[] target = new boolean[SIZE * SIZE];
        final int[][][] pieces = new int[3][][];
        final int[] at = {-1, -1, -1};                        // la case (y * SIZE + x) du coin de chaque piece posee, -1 sinon
        final List<Integer> order = new ArrayList<>();        // les pieces posees, la derniere en dernier
        final TextDisplay[] cells = new TextDisplay[SIZE * SIZE];
        final TextDisplay[] moving = new TextDisplay[4];
        final TextDisplay[][] tray = new TextDisplay[3][4];
        final Consumer<Player> solved;
        int held = -1, lastClick, solvedAt = -1;

        Board(Player player, Location pillar, Random r, Consumer<Player> solved) {
            super(Tablet.this.plugin, player, pillar, WIDTH, HEIGHT);
            this.solved = solved;
            // La figure : trois pieces posees au hasard, tournees au hasard. On recommence tant qu'elle est trop maigre.
            int count;
            do {
                java.util.Arrays.fill(target, false);
                for (int i = 0; i < pieces.length; i++) {
                    int[][] s = SHAPES[r.nextInt(SHAPES.length)];
                    for (int k = r.nextInt(4); k > 0; k--) s = turn(s);
                    int w = width(s), h = height(s), x = r.nextInt(SIZE - w + 1), y = r.nextInt(SIZE - h + 1);
                    for (int[] c : s) target[(y + c[1]) * SIZE + x + c[0]] ^= true;
                    // La piece est donnee au joueur dans un autre sens.
                    for (int k = r.nextInt(4); k > 0; k--) s = turn(s);
                    pieces[i] = s;
                }
                count = 0;
                for (boolean b : target) if (b) count++;
            } while (count < 4);
            lastClick = Bukkit.getCurrentTick() + 5;         // le clic qui l'ouvre ne compte pas
            build();
            draw();
        }

        void build() {
            backdrop();
            plate(GRID_LEFT + SIZE * CELL / 2, GRID_TOP - SIZE * CELL / 2, SIZE * CELL + 0.06, SIZE * CELL + 0.06);
            header(GOAL_LEFT + SIZE * GOAL / 2, GOAL_TOP + 0.1, "<aqua>Figure à obtenir", 0.6f);
            label(GRID_LEFT + SIZE * CELL / 2, GRID_TOP + 0.08, "<white>Pose les pièces ici", 0.7f);
            label(-1.3, TRAY_V + 0.42, "<gray>Tes pièces", 0.6f);
            label(0, -HEIGHT / 2 + 0.06, "<yellow>Clic <gray>: prendre / poser   <yellow>F <gray>: tourner   <yellow>Accroupi <gray>: fermer", 0.6f);
            for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                tile(GOAL_LEFT + (x + 0.5) * GOAL, GOAL_TOP - (y + 0.5) * GOAL, GOAL - 0.03, GOAL - 0.03, 0.02, target[y * SIZE + x] ? gold : empty);
                cells[y * SIZE + x] = tile(GRID_LEFT + (x + 0.5) * CELL, GRID_TOP - (y + 0.5) * CELL, CELL - GAP, CELL - GAP, 0.02, empty);
            }
            for (int i = 0; i < 3; i++) {
                tile(TRAY_U[i], TRAY_V, 0.64, 0.64, 0.015, drawn ? empty : SLOT);
                for (int k = 0; k < 4; k++) tray[i][k] = tile(TRAY_U[i], TRAY_V, SMALL - 0.02, SMALL - 0.02, 0.03, NONE);
            }
            for (int k = 0; k < 4; k++) {
                moving[k] = tile(0, 0, CELL - GAP, CELL - GAP, 0.05, NONE);
                moving[k].setTeleportDuration(2);
            }
            boxes();
        }

        int[] coverage() {
            int[] g = new int[SIZE * SIZE];
            for (int i = 0; i < pieces.length; i++) if (at[i] >= 0) for (int[] c : pieces[i]) g[at[i] + c[1] * SIZE + c[0]]++;
            return g;
        }

        boolean done() {
            for (int a : at) if (a < 0) return false;
            int[] g = coverage();
            for (int k = 0; k < g.length; k++) if ((g[k] % 2 == 1) != target[k]) return false;
            return true;
        }

        /** Les couleurs de la grille et des pieces en attente, d'apres l'etat. */
        void draw() {
            int[] g = coverage();
            for (int k = 0; k < g.length; k++) paint(cells[k], g[k] == 0 ? empty : g[k] % 2 == 1 ? gold : faded);
            for (int i = 0; i < 3; i++) {
                int w = width(pieces[i]), h = height(pieces[i]);
                for (int k = 0; k < 4; k++) {
                    boolean shown = at[i] < 0 && held != i && k < pieces[i].length;
                    paint(tray[i][k], shown ? gold : NONE);
                    if (!shown) continue;
                    int[] c = pieces[i][k];
                    double u = TRAY_U[i] - w * SMALL / 2 + (c[0] + 0.5) * SMALL, v = TRAY_V + h * SMALL / 2 - (c[1] + 0.5) * SMALL;
                    tray[i][k].teleport(spot(u, v - (SMALL - 0.02) / 2, 0.03));
                }
            }
            for (int k = 0; k < 4; k++) paint(moving[k], held >= 0 && k < pieces[held].length ? carried : NONE);
            follow();
        }

        boolean overGrid() {
            return aimed && cu > GRID_LEFT - 0.3 && cu < GRID_LEFT + SIZE * CELL + 0.3 && cv < GRID_TOP + 0.3 && cv > GRID_TOP - SIZE * CELL - 0.3;
        }

        /** Le coin ou tomberait la piece tenue : la case de la grille la plus proche du viseur, la piece restant dedans. */
        int[] corner() {
            int w = width(pieces[held]), h = height(pieces[held]);
            int x = (int) Math.round((cu - GRID_LEFT) / CELL - w / 2.0), y = (int) Math.round((GRID_TOP - cv) / CELL - h / 2.0);
            return new int[]{Math.max(0, Math.min(SIZE - w, x)), Math.max(0, Math.min(SIZE - h, y))};
        }

        /** La piece tenue suit le viseur : sur la grille, elle se cale sur les cases. */
        void follow() {
            if (held < 0 || !aimed) return;
            int w = width(pieces[held]), h = height(pieces[held]);
            double left, top;
            if (overGrid()) { int[] c = corner(); left = GRID_LEFT + c[0] * CELL; top = GRID_TOP - c[1] * CELL; }
            else { left = cu - w * CELL / 2; top = cv + h * CELL / 2; }
            for (int k = 0; k < pieces[held].length; k++) {
                int[] c = pieces[held][k];
                moving[k].teleport(spot(left + (c[0] + 0.5) * CELL, top - (c[1] + 0.5) * CELL - (CELL - GAP) / 2, 0.05));
            }
        }

        /** La piece en attente que vise le joueur, ou -1. */
        int trayAt() {
            if (!aimed) return -1;
            for (int i = 0; i < 3; i++) if (at[i] < 0 && Math.abs(cu - TRAY_U[i]) < 0.34 && Math.abs(cv - TRAY_V) < 0.34) return i;
            return -1;
        }

        void click() {
            int now = Bukkit.getCurrentTick();
            if (now - lastClick < 3 || solvedAt >= 0) return;
            lastClick = now;
            aim();
            if (!aimed) return;
            if (held >= 0) {
                if (overGrid()) {
                    int[] c = corner();
                    at[held] = c[1] * SIZE + c[0];
                    order.add(held);
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_PLACE, 1f, 1.1f);
                } else player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, 0.8f, 1f);
                held = -1;
                draw();
                if (done()) win();
                return;
            }
            int pick = trayAt();
            if (pick < 0 && cu > GRID_LEFT && cu < GRID_LEFT + SIZE * CELL && cv < GRID_TOP && cv > GRID_TOP - SIZE * CELL) {
                // sur la grille : la derniere piece posee qui couvre cette case
                int x = (int) ((cu - GRID_LEFT) / CELL), y = (int) ((GRID_TOP - cv) / CELL);
                for (int k = order.size() - 1; k >= 0 && pick < 0; k--) {
                    int i = order.get(k);
                    for (int[] c : pieces[i]) if (at[i] % SIZE + c[0] == x && at[i] / SIZE + c[1] == y) pick = i;
                }
            }
            if (pick < 0) return;
            at[pick] = -1;
            order.remove((Integer) pick);
            held = pick;
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.4f);
            draw();
        }

        void rotate() {
            aim();
            int i = held >= 0 ? held : trayAt();
            if (i < 0 || solvedAt >= 0) return;
            pieces[i] = turn(pieces[i]);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.6f);
            draw();
        }

        void win() {
            solvedAt = Bukkit.getCurrentTick();
            for (int k = 0; k < cells.length; k++) if (target[k]) paint(cells[k], Color.fromARGB(255, 255, 240, 170));
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        }

    }

    private static int width(int[][] s) { int w = 0; for (int[] c : s) w = Math.max(w, c[0] + 1); return w; }
    private static int height(int[][] s) { int h = 0; for (int[] c : s) h = Math.max(h, c[1] + 1); return h; }

    /** La piece tournee d'un quart de tour, ramenee contre le coin. */
    private static int[][] turn(int[][] s) {
        int h = height(s);
        int[][] out = new int[s.length][];
        for (int i = 0; i < s.length; i++) out[i] = new int[]{h - 1 - s[i][1], s[i][0]};
        return out;
    }

    private final CrossfightPlugin plugin;
    private final Map<UUID, Board> boards = new HashMap<>();

    public Tablet(CrossfightPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 1L);
    }

    /** Ouvre une tablette a ce joueur, devant ce pilier ; {@code solved} est appele quand il la resout. */
    public void open(Player p, Location pillar, Consumer<Player> solved) {
        close(p);
        boards.put(p.getUniqueId(), new Board(p, pillar, new Random(), solved));
        Msg.actionBar(p, "<aqua>Vise une pièce et clique pour la prendre, clique sur la grille pour la poser. <white>F <aqua>la tourne.");
    }

    public boolean isOpen(Player p) {
        return boards.containsKey(p.getUniqueId());
    }

    public void close(Player p) {
        Board b = boards.remove(p.getUniqueId());
        if (b != null) b.remove();
    }

    /** Tout fermer (nouvelle partie, arret du plugin). */
    public void reset() {
        for (Board b : boards.values()) b.remove();
        boards.clear();
    }

    private void tick() {
        for (Board b : new ArrayList<>(boards.values())) {
            Player p = b.player;
            if (b.lost()) { close(p); continue; }
            if (b.solvedAt >= 0) {
                if (Bukkit.getCurrentTick() - b.solvedAt > 25) { close(p); b.solved.accept(p); }
                continue;
            }
            b.aim();
            b.point();
            b.follow();
        }
    }

    @EventHandler
    public void onSwing(PlayerAnimationEvent e) {
        Board b = boards.get(e.getPlayer().getUniqueId());
        if (b != null) b.click();
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        Board b = boards.get(e.getPlayer().getUniqueId());
        if (b == null) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) b.click();
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onUseEntity(PlayerInteractEntityEvent e) {
        Board b = boards.get(e.getPlayer().getUniqueId());
        if (b == null) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND) b.click();
    }

    @EventHandler
    public void onAttack(io.papermc.paper.event.player.PrePlayerAttackEntityEvent e) {
        // le coup de bras qui suit fait le clic
        if (boards.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Board b = boards.get(e.getPlayer().getUniqueId());
        if (b == null) return;
        e.setCancelled(true);
        b.rotate();
    }

    @EventHandler
    public void onSneak(PlayerToggleSneakEvent e) {
        if (e.isSneaking() && boards.containsKey(e.getPlayer().getUniqueId())) {
            close(e.getPlayer());
            Msg.actionBar(e.getPlayer(), "<gray>Tablette fermée : reclique sur le pilier pour réessayer.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        close(e.getPlayer());
    }
}
