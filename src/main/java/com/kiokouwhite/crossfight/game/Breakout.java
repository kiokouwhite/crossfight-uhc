package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.Random;
import java.util.function.IntSupplier;

/**
 * Le casse-briques : la quete d'Arcadia. Un mur de briques en haut, une raquette en bas qui suit le viseur, une balle
 * qui rebondit. Un clic lance la balle. Certaines briques portent une pomme, qu'on voit dessus, et dessinent un motif
 * (un V, un losange, des colonnes...) : cassee, la brique laisse tomber sa pomme, et il faut la rattraper avec la
 * raquette. Chaque pomme rattrapee compte pour l'equipe ; a quinze, le lieu est debloque. Toutes les briques a pomme
 * cassees, le mur revient au motif suivant, la balle un peu plus vive. La balle perdue revient sur la raquette.
 */
final class Breakout extends PanelGame {

    private static final int COLS = 8, ROWS = 5, APPLES = 12;
    /** Le terrain, en blocs depuis le milieu du tableau. */
    private static final double LEFT = -1.6, RIGHT = 1.6, TOP = 1.15, FLOOR = -1.45, PADDLE_V = -1.2, PADDLE_W = 0.85, PADDLE_H = 0.1;
    private static final double BRICK_W = (RIGHT - LEFT) / COLS, BRICK_H = 0.17, BALL = 0.12, DROP = 0.03;
    private static final Color[] ROW = {Color.fromARGB(255, 222, 70, 80), Color.fromARGB(255, 240, 150, 60), Color.fromARGB(255, 246, 204, 70), Color.fromARGB(255, 110, 200, 110), Color.fromARGB(255, 90, 160, 230)};

    final IntSupplier total;
    final int goal;
    final Runnable caught;
    final Random random;
    final boolean[] alive = new boolean[COLS * ROWS], fruit = new boolean[COLS * ROWS];
    final TextDisplay[] bricks = new TextDisplay[COLS * ROWS];
    final ItemDisplay[] apples = new ItemDisplay[APPLES];
    final double[] appleU = new double[APPLES], appleV = new double[APPLES];
    final boolean[] falling = new boolean[APPLES];
    TextDisplay paddle, ball, count;
    double paddleU, ballU, ballV, velU, velV, speed = 0.1;
    boolean held = true;                                      // la balle attend sur la raquette
    int shown = -1;

    Breakout(CrossfightPlugin plugin, Player player, Location pillar, Random random, IntSupplier total, int goal, Runnable caught) {
        super(plugin, player, pillar, 4.2, 3.9, who -> { });
        this.random = random;
        this.total = total;
        this.goal = goal;
        this.caught = caught;
        build();
        wall();
    }

    double brickU(int k) { return LEFT + (k % COLS + 0.5) * BRICK_W; }
    double brickV(int k) { return TOP - (k / COLS + 0.5) * BRICK_H; }

    void build() {
        backdrop();
        count = label(0, TOP + 0.42, "", 0.7f);
        label(0, -height / 2 + 0.06, "<yellow>Vise <gray>: la raquette   <yellow>Clic <gray>: lancer   <yellow>Accroupi <gray>: fermer", 0.55f);
        // le cadre du terrain : plus grand que le mur, sa bordure reste hors des briques
        plate(0, (TOP + 0.2 + FLOOR - 0.12) / 2, RIGHT - LEFT + 0.5, TOP + 0.2 - (FLOOR - 0.12));
        for (int k = 0; k < bricks.length; k++) bricks[k] = tile(brickU(k), brickV(k), BRICK_W - 0.03, BRICK_H - 0.03, 0.03, NONE);
        paddle = rect(0, PADDLE_V, PADDLE_W, PADDLE_H, 0.04, drawn ? Color.fromARGB(255, 62, 66, 110) : Color.fromARGB(255, 230, 230, 240));
        paddle.setTeleportDuration(1);
        ball = drawn ? orb(2, 0, 0, BALL + 0.04, 0.05) : rect(0, 0, BALL, BALL, 0.05, Color.WHITE);
        ball.setTeleportDuration(1);
        for (int i = 0; i < APPLES; i++) {
            apples[i] = item(0, 0, 0.2, 0.06, new ItemStack(Material.AIR));
            apples[i].setTeleportDuration(1);
        }
        for (int i = 0; i < BADGES; i++) badges[i] = item(0, 0, 0.15, 0.045, new ItemStack(Material.AIR));
        boxes();
    }

    /** Les motifs : ou sont les briques a pomme (A) dans le mur de huit sur cinq. Ils se suivent dans cet ordre, puis recommencent. */
    private static final String[][] PATTERNS = {
            {"A......A", ".A....A.", "..A..A..", "...AA...", "........"},      // un V
            {"...AA...", "..A..A..", ".A....A.", "..A..A..", "...AA..."},      // un losange
            {".A....A.", ".A....A.", ".A....A.", ".A....A.", "........"},      // deux colonnes
            {"A.A.A.A.", "........", ".A.A.A.A", "........", "........"},      // un damier
            {"...AA...", ".AAAAAA.", "...AA...", "...AA...", "........"},      // une croix
            {"A.......", ".A......", "..A.....", "...A....", "....AAAA"}};     // un escalier
    private static final int BADGES = 12;
    final ItemDisplay[] badges = new ItemDisplay[BADGES];
    final int[] badgeOf = new int[COLS * ROWS];
    int pattern = -1;

    /** Un mur neuf, au motif suivant : toutes les briques reviennent, et celles du motif portent une pomme, qu'on voit dessus. */
    void wall() {
        pattern = (pattern + 1) % PATTERNS.length;
        int used = 0;
        for (int k = 0; k < bricks.length; k++) {
            alive[k] = true;
            fruit[k] = PATTERNS[pattern][k / COLS].charAt(k % COLS) == 'A';
            paint(bricks[k], ROW[k / COLS]);
            badgeOf[k] = -1;
            if (fruit[k] && used < BADGES) {
                badgeOf[k] = used;
                badges[used].setItemStack(new ItemStack(Material.APPLE));
                badges[used].teleport(spot(brickU(k), brickV(k), 0.045));
                used++;
            }
        }
        for (int i = used; i < BADGES; i++) badges[i].setItemStack(new ItemStack(Material.AIR));
    }

    @Override void onClick() {
        if (!held) return;
        held = false;
        double angle = Math.toRadians(random.nextInt(50) - 25);
        velU = Math.sin(angle) * speed;
        velV = Math.cos(angle) * speed;
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.4f);
    }

    /** Un pas de la balle : les murs, la raquette, les briques. */
    void step() {
        double fromV = ballV;
        ballU += velU / 2;
        ballV += velV / 2;
        if (ballU < LEFT + BALL / 2) { ballU = LEFT + BALL / 2; velU = Math.abs(velU); }
        if (ballU > RIGHT - BALL / 2) { ballU = RIGHT - BALL / 2; velU = -Math.abs(velU); }
        if (ballV > TOP - BALL / 2) { ballV = TOP - BALL / 2; velV = -Math.abs(velV); }
        if (velV < 0 && ballV <= PADDLE_V + PADDLE_H / 2 + BALL / 2 && fromV > PADDLE_V && Math.abs(ballU - paddleU) < PADDLE_W / 2 + BALL / 2) {
            // la raquette renvoie la balle d'autant plus de cote qu'elle la touche loin de son milieu
            double angle = Math.toRadians(Math.max(-1, Math.min(1, (ballU - paddleU) / (PADDLE_W / 2))) * 60);
            velU = Math.sin(angle) * speed;
            velV = Math.cos(angle) * speed;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 1.6f);
        }
        if (ballV < FLOOR) { held = true; player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 0.6f, 0.8f); return; }
        for (int k = 0; k < bricks.length; k++) {
            if (!alive[k] || Math.abs(ballU - brickU(k)) > BRICK_W / 2 + BALL / 2 || Math.abs(ballV - brickV(k)) > BRICK_H / 2 + BALL / 2) continue;
            alive[k] = false;
            paint(bricks[k], NONE);
            // venue du dessus ou du dessous, elle repart en hauteur ; sinon, de cote
            if (Math.abs(fromV - brickV(k)) > BRICK_H / 2 + BALL / 2) velV = -velV; else velU = -velU;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, 0.8f, 1f + 0.12f * (ROWS - k / COLS));
            if (fruit[k]) {
                if (badgeOf[k] >= 0) badges[badgeOf[k]].setItemStack(new ItemStack(Material.AIR));
                drop(brickU(k), brickV(k));
            }
            break;
        }
    }

    void drop(double u, double v) {
        for (int i = 0; i < APPLES; i++) if (!falling[i]) {
            falling[i] = true;
            appleU[i] = u;
            appleV[i] = v;
            apples[i].setItemStack(new ItemStack(Material.APPLE));
            apples[i].teleport(spot(u, v, 0.06));
            return;
        }
    }

    @Override void onTick() {
        if (aimed) paddleU = Math.max(LEFT + PADDLE_W / 2, Math.min(RIGHT - PADDLE_W / 2, cu));
        paddle.teleport(spot(paddleU, PADDLE_V - PADDLE_H / 2, 0.04));
        if (held) { ballU = paddleU; ballV = PADDLE_V + PADDLE_H / 2 + BALL / 2 + 0.01; }
        else { step(); if (!held) step(); }
        ball.teleport(spot(ballU, ballV - (drawn ? BALL + 0.04 : BALL) / 2, 0.05));
        for (int i = 0; i < APPLES; i++) {
            if (!falling[i]) continue;
            appleV[i] -= DROP;
            if (appleV[i] <= PADDLE_V + 0.12 && appleV[i] > PADDLE_V - 0.1 && Math.abs(appleU[i] - paddleU) < PADDLE_W / 2 + 0.1) {
                falling[i] = false;
                apples[i].setItemStack(new ItemStack(Material.AIR));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.9f, 1.2f);
                caught.run();
                continue;
            }
            if (appleV[i] < FLOOR) { falling[i] = false; apples[i].setItemStack(new ItemStack(Material.AIR)); continue; }
            apples[i].teleport(spot(appleU[i], appleV[i], 0.06));
        }
        // toutes les briques a pomme cassees : le motif suivant
        boolean empty = true;
        for (int k = 0; k < alive.length; k++) if (alive[k] && fruit[k]) { empty = false; break; }
        if (empty) {
            wall();
            held = true;
            speed = Math.min(0.14, speed + 0.01);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
        }
        int now = total.getAsInt();
        if (now != shown) {
            shown = now;
            net.kyori.adventure.text.Component text = Msg.mm((drawn ? "<#3e426e>" : "<white>") + "Pommes : " + (drawn ? "<#b02c3c>" : "<gold>") + Math.min(now, goal) + " / " + goal);
            count.text(lettered ? text.font(net.kyori.adventure.key.Key.key(com.kiokouwhite.crossfight.util.Sounds.NS, "quete_texte")) : text);
        }
    }

    @Override String hint() { return "<aqua>Vise pour bouger la raquette, clique pour lancer la balle : rattrape les pommes qui tombent des briques."; }
}
