package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Le puzzle : une enigme des piliers (la quete de Central Town). Une image de 12 x 12 points est decoupee en neuf
 * pieces carrees, melangees a gauche ; a droite, le cadre vide, et dessous le modele en petit. On clique une piece pour
 * la prendre, elle suit le viseur (calee sur les cases au-dessus du cadre), on clique pour la poser ; une piece posee
 * sur une case occupee renvoie l'autre a sa place. Le puzzle est resolu quand le cadre montre l'image (deux pieces
 * identiques sont interchangeables). Les images sont dessinees ici, point par point.
 */
final class Jigsaw extends PanelGame {

    private static final int P = 3, K = 4, SIDE = P * K;
    /** Les images : une lettre par point ('.' : le ciel, en degrade). */
    private static final String[][] PICTURES = {
            {"............", "..RR....RR..", ".RRRR..RRRR.", ".RWRRRRRRRR.", ".RWRRRRRRRR.", ".RRRRRRRRRR.", "..RRRRRRRR..", "...RRRRRR...", "....RRRR....", ".....RR.....", "............", "MMMMMMMMMMMM"},
            {"..........WW", ".........WLW", "........WLW.", ".......WLW..", "......WLW...", ".....WLW....", ".T..WLW.....", ".TTWLW......", "..TTW.......", ".KTTT.......", "KK..TT......", "K..........."},
            {"....GGGG....", "...GGGGGG...", "..GGGDGGGG..", ".GGGGGGGDGG.", ".GDGGGGGGGG.", ".GGGGGDGGGG.", "..GGGGGGGG..", "...GGTTGG...", ".....TT.....", ".....TT.....", "....TTTT....", "MMMMMMMMMMMM"},
            {".....RR.....", "....RRRR....", "...RRRRRR...", "..RRRRRRRR..", ".RRRRRRRRRR.", "..YYYYYYYY..", "..YBBYYYYY..", "..YBBYYTTY..", "..YYYYYTTY..", "..YYYYYTTY..", "MMMMMMMMMMMM", "MMMMMMMMMMMM"}};

    private static final double PX = 0.14, PIECE = K * PX, FRAME_LEFT = 0.5, FRAME_TOP = 1.2, HOME_LEFT = -2.05, HOME_TOP = 1.1, HOME_PITCH = 0.56;
    private static final double MINI = 0.045, MINI_LEFT = FRAME_LEFT + (P * PIECE - SIDE * MINI) / 2, MINI_TOP = -0.78;
    private static final Color BACK = Color.fromARGB(235, 12, 14, 36), SLOT = Color.fromARGB(255, 34, 40, 76), AIMED = Color.fromARGB(255, 70, 80, 130);

    /** Les tableaux du jeu qui servent d'image, avec le pack (tools/build_puzzle_font.py, meme ordre) : 48 x 48 points, neuf pieces de 16. */
    private static final int PAINTINGS = 9;

    /** Avec le pack : l'image est un tableau du jeu (une image par piece). Sinon : une image dessinee ici, point par point. */
    final boolean painted;
    final int painting;
    final Color[] picture = new Color[SIDE * SIDE];
    final TextDisplay[][] pieces = new TextDisplay[P * P][];
    final TextDisplay[] frame = new TextDisplay[P * P];
    final int[] home = new int[P * P];                    // la place de chaque piece dans le tas de gauche
    final int[] slot = new int[P * P];                    // la case du cadre ou est chaque piece, -1 dans le tas
    int held = -1, hover = -1;

    Jigsaw(CrossfightPlugin plugin, Player player, Location pillar, Random r, Consumer<Player> solved) {
        super(plugin, player, pillar, 5.0, 3.7, solved);
        painted = com.kiokouwhite.crossfight.util.Sounds.has("font.puzzle") && com.kiokouwhite.crossfight.util.Sounds.hasPackLoaded(player);
        painting = r.nextInt(PAINTINGS);
        String[] rows = PICTURES[r.nextInt(PICTURES.length)];
        for (int y = 0; y < SIDE; y++) for (int x = 0; x < SIDE; x++) picture[y * SIDE + x] = color(rows[y].charAt(x), y);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < P * P; i++) { order.add(i); slot[i] = -1; }
        // melangees, et jamais chacune en face de sa case
        do Collections.shuffle(order, r); while (sorted(order));
        for (int i = 0; i < P * P; i++) home[order.get(i)] = i;
        build();
    }

    private static boolean sorted(List<Integer> order) {
        for (int i = 0; i < order.size(); i++) if (order.get(i) != i) return false;
        return true;
    }

    private static Color color(char c, int y) {
        return switch (c) {
            case 'R' -> Color.fromARGB(255, 222, 62, 72);
            case 'W' -> Color.fromARGB(255, 245, 245, 250);
            case 'L' -> Color.fromARGB(255, 150, 210, 240);
            case 'T' -> Color.fromARGB(255, 130, 86, 50);
            case 'K' -> Color.fromARGB(255, 70, 50, 40);
            case 'G' -> Color.fromARGB(255, 70, 170, 80);
            case 'D' -> Color.fromARGB(255, 40, 120, 60);
            case 'M' -> Color.fromARGB(255, 96, 150, 70);
            case 'Y' -> Color.fromARGB(255, 240, 220, 170);
            case 'B' -> Color.fromARGB(255, 90, 160, 230);
            // le ciel : du bleu en haut au rose en bas
            default -> Color.fromARGB(255, 90 + 12 * y, 150 + 4 * y, 230 - 5 * y);
        };
    }

    double frameU(int s) { return FRAME_LEFT + (s % P) * PIECE; }
    double frameV(int s) { return FRAME_TOP - (s / P) * PIECE; }
    double homeU(int h) { return HOME_LEFT + (h % P) * HOME_PITCH; }
    double homeV(int h) { return HOME_TOP - (h / P) * HOME_PITCH; }

    void build() {
        backdrop();
        // le cadre du tas : les neuf pieces, sa bordure, et de la marge
        double heap = 2 * HOME_PITCH + PIECE * HOME_SCALE + 0.5;
        plate(HOME_LEFT + (2 * HOME_PITCH + PIECE) / 2, HOME_TOP - (2 * HOME_PITCH + PIECE) / 2, heap, heap);
        label(HOME_LEFT + (2 * HOME_PITCH + PIECE) / 2, HOME_TOP + 0.2, "<gray>Les pièces", 0.6f);
        label(FRAME_LEFT + P * PIECE / 2, FRAME_TOP + 0.08, "<aqua>Reforme l'image", 0.7f);
        label(MINI_LEFT - 0.4, MINI_TOP - 0.35, "<gray>Modèle", 0.6f);
        label(-0.6, -height / 2 + 0.06, "<yellow>Clic <gray>: prendre / poser   <yellow>Accroupi <gray>: fermer", 0.55f);
        for (int s = 0; s < P * P; s++) frame[s] = tile(frameU(s) + PIECE / 2, frameV(s) - PIECE / 2, PIECE - 0.03, PIECE - 0.03, 0.02, empty);
        if (painted) {
            picture("puzzle", (char) (0xE800 + 16 * painting + 9), 48, 48, MINI_LEFT + SIDE * MINI / 2, MINI_TOP - SIDE * MINI / 2, SIDE * MINI, SIDE * MINI, 0.02, Color.WHITE);
            for (int i = 0; i < P * P; i++) {
                pieces[i] = new TextDisplay[]{picture("puzzle", (char) (0xE800 + 16 * painting + i), 16, 16, 0, 0, PIECE, PIECE, 0.035, Color.WHITE)};
                pieces[i][0].setTeleportDuration(2);
            }
        } else {
            for (int y = 0; y < SIDE; y++) for (int x = 0; x < SIDE; x++)
                rect(MINI_LEFT + (x + 0.5) * MINI, MINI_TOP - (y + 0.5) * MINI, MINI + 0.004, MINI + 0.004, 0.02, picture[y * SIDE + x]);
            for (int i = 0; i < P * P; i++) {
                pieces[i] = new TextDisplay[K * K];
                for (int k = 0; k < K * K; k++) {
                    int x = i % P * K + k % K, y = i / P * K + k / K;
                    pieces[i][k] = rect(0, 0, PX + 0.004, PX + 0.004, 0.035, picture[y * SIDE + x]);
                    pieces[i][k].setTeleportDuration(2);
                }
            }
        }
        for (int i = 0; i < P * P; i++) place(i);
        boxes();
    }

    /** Dans le tas et dans les cases du cadre, les pieces sont plus petites que leur place : elles n'en sortent pas. */
    private static final double HOME_SCALE = 0.8, SLOT_SCALE = 0.8;
    /** Le creux d'une case du pack est un demi-point plus bas et plus a droite que son milieu (son ombre est en haut a gauche). */
    private static final double NUDGE = 0.017;

    /** Pose une piece, son coin haut gauche en (u, v), a cette echelle (1 : sa taille dans le cadre). */
    void move(int i, double u, double v, double depth, double f) {
        if (painted) {
            resize(pieces[i][0], PIECE * f, PIECE * f);
            pieces[i][0].teleport(spot(u + PIECE * f / 2, v - PIECE * f, depth));
            return;
        }
        double px = PX * f;
        for (int k = 0; k < K * K; k++) {
            resize(pieces[i][k], px + 0.004, px + 0.004);
            pieces[i][k].teleport(spot(u + (k % K + 0.5) * px, v - (k / K + 0.5) * px - (px + 0.004) / 2, depth));
        }
    }

    void move(int i, double u, double v, double depth) { move(i, u, v, depth, 1); }

    /** Pose une piece au milieu d'une case du cadre. */
    void seat(int i, int s, double depth) {
        double in = PIECE * (1 - SLOT_SCALE) / 2, nudge = drawn ? NUDGE : 0;
        move(i, frameU(s) + in + nudge, frameV(s) - in - nudge, depth, SLOT_SCALE);
    }

    /** Remet une piece ou elle doit etre : sur sa case du cadre, ou a sa place dans le tas. */
    void place(int i) {
        if (slot[i] >= 0) seat(i, slot[i], 0.035);
        else move(i, homeU(home[i]) + PIECE * (1 - HOME_SCALE) / 2, homeV(home[i]) - PIECE * (1 - HOME_SCALE) / 2, 0.035, HOME_SCALE);
    }

    int slotAt() {
        if (!aimed || cu <= FRAME_LEFT || cu >= FRAME_LEFT + P * PIECE || cv >= FRAME_TOP || cv <= FRAME_TOP - P * PIECE) return -1;
        return (int) ((FRAME_TOP - cv) / PIECE) * P + (int) ((cu - FRAME_LEFT) / PIECE);
    }

    /** La piece que vise le joueur (dans le cadre ou dans le tas), ou -1. */
    int pieceAt() {
        if (!aimed) return -1;
        int s = slotAt();
        for (int i = 0; i < P * P; i++) {
            if (slot[i] >= 0) { if (slot[i] == s) return i; continue; }
            double u = homeU(home[i]), v = homeV(home[i]);
            if (cu > u && cu < u + PIECE && cv < v && cv > v - PIECE) return i;
        }
        return -1;
    }

    boolean same(int a, int b) {
        if (painted) return a == b;
        for (int k = 0; k < K * K; k++) if (!pieces[a][k].getBackgroundColor().equals(pieces[b][k].getBackgroundColor())) return false;
        return true;
    }

    boolean done() {
        int[] at = new int[P * P];
        java.util.Arrays.fill(at, -1);
        for (int i = 0; i < P * P; i++) if (slot[i] >= 0) at[slot[i]] = i;
        for (int s = 0; s < P * P; s++) if (at[s] < 0 || !same(at[s], s)) return false;
        return true;
    }

    @Override void onClick() {
        if (held >= 0) {
            int s = slotAt();
            if (s >= 0) {
                for (int i = 0; i < P * P; i++) if (slot[i] == s) { slot[i] = -1; place(i); }
                slot[held] = s;
                player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.9f, 1.3f);
            } else player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, 0.8f, 1f);
            int was = held;
            held = -1;
            place(was);
            if (done()) win(Sound.BLOCK_AMETHYST_BLOCK_CHIME);
            return;
        }
        int pick = pieceAt();
        if (pick < 0) return;
        slot[pick] = -1;
        held = pick;
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.4f);
    }

    @Override void onTick() {
        int over = held >= 0 ? slotAt() : -1;
        if (over != hover) {
            for (int s = 0; s < P * P; s++) paint(frame[s], s == over ? pointed : empty);
            hover = over;
        }
        if (held < 0 || !aimed) return;
        if (over >= 0) seat(held, over, 0.06);
        else move(held, cu - PIECE / 2, cv + PIECE / 2, 0.06);
    }

    @Override String hint() { return "<aqua>Prends une pièce à gauche et pose-la dans le cadre : reforme l'image du modèle."; }
}
