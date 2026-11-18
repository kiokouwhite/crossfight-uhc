package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Le circuit : le second casse-tete des piliers (la quete de Central Town). Une grille de tuiles portant des fils ; une
 * source (orange) et deux lampes (rouges). Un clic sur une tuile la fait pivoter d'un quart de tour. Le courant part de
 * la source et court le long des fils qui se touchent : on le voit avancer. Le circuit est resolu quand il atteint les
 * deux lampes. Chaque circuit est tire au hasard, et toujours soluble : les fils utiles sont traces d'abord (un chemin
 * de la source a chaque lampe), des fils inutiles sont ajoutes, puis toutes les tuiles sont tournees au hasard.
 */
final class Circuit extends PanelGame {

    private static final int N = 4;
    /** Les ouvertures d'une tuile : haut 1, droite 2, bas 4, gauche 8. */
    private static final int[] DX = {0, 1, 0, -1}, DY = {-1, 0, 1, 0};
    private static final double CELL = 0.6, LEFT = -N * CELL / 2, TOP = 1.05;
    private static final Color BACK = Color.fromARGB(235, 12, 14, 36), TILE = Color.fromARGB(255, 34, 40, 76), AIMED = Color.fromARGB(255, 70, 80, 130),
            WIRE = Color.fromARGB(255, 120, 130, 165), LIVE = Color.fromARGB(255, 255, 176, 56), SOURCE = Color.fromARGB(255, 255, 140, 30),
            LAMP_OFF = Color.fromARGB(255, 200, 50, 60), LAMP_ON = Color.fromARGB(255, 110, 240, 120);

    final int[] mask = new int[N * N];
    final int source;
    final List<Integer> lamps = new ArrayList<>();
    final TextDisplay[] tiles = new TextDisplay[N * N], hubs = new TextDisplay[N * N];
    final TextDisplay[][] arms = new TextDisplay[N * N][4];
    final int[] dist = new int[N * N];                    // la distance a la source le long des fils, -1 hors courant
    int flow, hover = -1, lit;

    Circuit(CrossfightPlugin plugin, Player player, Location pillar, Random r, Consumer<Player> solved) {
        super(plugin, player, pillar, 3.6, 3.6, solved);
        // un arbre au hasard sur la grille, depuis la source : il donne un chemin vers chaque case
        int[] parent = new int[N * N];
        java.util.Arrays.fill(parent, -2);
        source = r.nextInt(N * N);
        parent[source] = -1;
        ArrayDeque<Integer> stack = new ArrayDeque<>(List.of(source));
        int[] depth = new int[N * N];
        while (!stack.isEmpty()) {
            int c = stack.peek();
            List<Integer> free = new ArrayList<>();
            for (int d = 0; d < 4; d++) {
                int x = c % N + DX[d], y = c / N + DY[d];
                if (x >= 0 && x < N && y >= 0 && y < N && parent[y * N + x] == -2) free.add(y * N + x);
            }
            if (free.isEmpty()) { stack.pop(); continue; }
            int next = free.get(r.nextInt(free.size()));
            parent[next] = c;
            depth[next] = depth[c] + 1;
            stack.push(next);
        }
        // deux lampes, a trois cases de fil au moins de la source
        List<Integer> far = new ArrayList<>();
        for (int c = 0; c < N * N; c++) if (depth[c] >= 3) far.add(c);
        Collections.shuffle(far, r);
        for (int k = 0; k < 2 && k < far.size(); k++) lamps.add(far.get(k));
        for (int lamp : lamps) for (int c = lamp; parent[c] >= 0; c = parent[c]) {
            int p = parent[c];
            for (int d = 0; d < 4; d++) if (p % N + DX[d] == c % N && p / N + DY[d] == c / N) { mask[p] |= 1 << d; mask[c] |= 1 << (d + 2) % 4; }
        }
        // des fils inutiles sur une case vide sur deux, puis tout est tourne au hasard (et jamais deja resolu)
        int[] spare = {5, 10, 3, 6, 12, 9};
        for (int c = 0; c < N * N; c++) if (mask[c] == 0 && r.nextBoolean()) mask[c] = spare[r.nextInt(spare.length)];
        do {
            for (int c = 0; c < N * N; c++) for (int k = r.nextInt(4); k > 0; k--) mask[c] = turn(mask[c]);
            power();
        } while (won());
        build();
        draw();
    }

    double tu(int c) { return LEFT + (c % N + 0.5) * CELL; }
    double tv(int c) { return TOP - (c / N + 0.5) * CELL; }

    void build() {
        rect(0, 0, width, height, 0, BACK);
        label(0, TOP + 0.3, "<aqua>Amène le courant aux deux lampes", 0.7f);
        label(0, TOP + 0.1, "<gold>■ <gray>source   <red>■ <gray>lampe éteinte   <green>■ <gray>lampe allumée", 0.5f);
        label(0, -height / 2 + 0.06, "<yellow>Clic <gray>: tourner la tuile   <yellow>Accroupi <gray>: fermer", 0.6f);
        for (int c = 0; c < N * N; c++) {
            tiles[c] = rect(tu(c), tv(c), CELL - 0.04, CELL - 0.04, 0.02, TILE);
            boolean end = c == source || lamps.contains(c);
            hubs[c] = rect(tu(c), tv(c), end ? 0.26 : 0.1, end ? 0.26 : 0.1, 0.045, NONE);
            for (int d = 0; d < 4; d++) {
                boolean flat = d % 2 == 1;
                arms[c][d] = rect(tu(c) + DX[d] * CELL / 4, tv(c) - DY[d] * CELL / 4, flat ? CELL / 2 : 0.1, flat ? 0.1 : CELL / 2, 0.035, NONE);
            }
        }
        boxes();
    }

    /** D'ou vient le courant : la distance de chaque tuile a la source, le long des fils qui se touchent. */
    void power() {
        java.util.Arrays.fill(dist, -1);
        dist[source] = 0;
        ArrayDeque<Integer> queue = new ArrayDeque<>(List.of(source));
        while (!queue.isEmpty()) {
            int c = queue.poll();
            for (int d = 0; d < 4; d++) {
                if ((mask[c] & 1 << d) == 0) continue;
                int x = c % N + DX[d], y = c / N + DY[d];
                if (x < 0 || x >= N || y < 0 || y >= N) continue;
                int o = y * N + x;
                if (dist[o] >= 0 || (mask[o] & 1 << (d + 2) % 4) == 0) continue;
                dist[o] = dist[c] + 1;
                queue.add(o);
            }
        }
    }

    boolean won() {
        for (int lamp : lamps) if (dist[lamp] < 0) return false;
        return true;
    }

    /** Les couleurs, d'apres l'etat : un fil est allume quand le courant y est deja arrive (il avance d'une tuile par tick). */
    void draw() {
        int reached = 0;
        for (int c = 0; c < N * N; c++) {
            boolean live = dist[c] >= 0 && dist[c] <= flow;
            tiles[c].setBackgroundColor(c == hover && solvedAt < 0 ? AIMED : TILE);
            Color wire = live ? LIVE : WIRE;
            if (c == source) hubs[c].setBackgroundColor(SOURCE);
            else if (lamps.contains(c)) { hubs[c].setBackgroundColor(live ? LAMP_ON : LAMP_OFF); if (live) reached++; }
            else hubs[c].setBackgroundColor(mask[c] == 0 ? NONE : wire);
            for (int d = 0; d < 4; d++) arms[c][d].setBackgroundColor((mask[c] & 1 << d) != 0 ? wire : NONE);
        }
        if (reached > lit) player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.9f, 1f + 0.4f * reached);
        lit = reached;
    }

    int tileAt() {
        if (!aimed || cu <= LEFT || cu >= LEFT + N * CELL || cv >= TOP || cv <= TOP - N * CELL) return -1;
        return (int) ((TOP - cv) / CELL) * N + (int) ((cu - LEFT) / CELL);
    }

    @Override void onClick() {
        int c = tileAt();
        if (c < 0 || mask[c] == 0) return;
        mask[c] = turn(mask[c]);
        power();
        // le courant repart de la tuile tournee (ou de la source si elle n'en a plus)
        flow = Math.max(0, Math.min(flow, dist[c] < 0 ? flow : dist[c] - 1));
        player.playSound(player.getLocation(), Sound.BLOCK_COPPER_BULB_TURN_ON, 1f, 1.3f);
        draw();
    }

    @Override void onTick() {
        int over = tileAt();
        boolean redraw = over != hover;
        hover = over;
        int far = 0;
        for (int d : dist) far = Math.max(far, d);
        if (flow < far) { flow++; redraw = true; }
        if (redraw) draw();
        if (flow >= far && won()) { win(Sound.BLOCK_BEACON_POWER_SELECT); draw(); }
    }

    /** Une tuile tournee d'un quart de tour (sens des aiguilles). */
    private static int turn(int m) {
        return (m << 1 & 15) | m >> 3;
    }

    @Override String hint() { return "<aqua>Clique une tuile pour la tourner : le courant doit aller de la source aux deux lampes."; }
}
