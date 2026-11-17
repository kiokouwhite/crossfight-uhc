package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * La tablette : le casse-tete des piliers (la quete de Central Town). A gauche, la figure a obtenir ; a droite, une
 * grille vide ; en bas, des pieces. On choisit une piece, on la tourne si l'on veut, on clique dans la grille pour la
 * poser. La ou deux pieces se recouvrent, les cases s'annulent. La tablette est resolue quand toutes les pieces sont
 * posees et que la grille montre la figure. Chaque tablette est tiree au hasard, et toujours soluble : la figure est
 * faite en posant les pieces quelque part.
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
    private static final Material[] COLORS = {Material.ORANGE_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.LIME_CONCRETE};

    /** Une tablette ouverte chez un joueur. */
    private static final class Board implements InventoryHolder {
        final Inventory inv = Bukkit.createInventory(this, 54, Component.text("Tablette - reproduis la figure"));
        final boolean[] target = new boolean[SIZE * SIZE];
        final int[][][] pieces = new int[3][][];
        final int[] at = {-1, -1, -1};                        // la case (y * SIZE + x) du coin de chaque piece posee, -1 sinon
        int chosen = 0;
        final Consumer<Player> solved;

        Board(Random r, Consumer<Player> solved) {
            this.solved = solved;
            // La figure : trois pieces posees au hasard, tournees au hasard. On recommence tant qu'elle est trop maigre.
            int cells;
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
                cells = 0;
                for (boolean b : target) if (b) cells++;
            } while (cells < 4);
            draw();
        }

        boolean[] grid() {
            boolean[] g = new boolean[SIZE * SIZE];
            for (int i = 0; i < pieces.length; i++) if (at[i] >= 0) for (int[] c : pieces[i]) g[at[i] + c[1] * SIZE + c[0]] ^= true;
            return g;
        }

        boolean done() {
            for (int a : at) if (a < 0) return false;
            return java.util.Arrays.equals(grid(), target);
        }

        void draw() {
            boolean[] g = grid();
            for (int y = 0; y < 6; y++) for (int x = 0; x < 9; x++) inv.setItem(y * 9 + x, item(Material.BLACK_STAINED_GLASS_PANE, " "));
            for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
                inv.setItem(y * 9 + x, item(target[y * SIZE + x] ? Material.YELLOW_CONCRETE : Material.GRAY_STAINED_GLASS_PANE, target[y * SIZE + x] ? "<yellow>La figure à obtenir" : "<gray>La figure à obtenir"));
                // Dans la grille, chaque case pleine porte la couleur de la derniere piece posee dessus.
                Material m = Material.WHITE_STAINED_GLASS_PANE;
                if (g[y * SIZE + x]) for (int i = 0; i < pieces.length; i++) if (at[i] >= 0) for (int[] c : pieces[i]) if (at[i] + c[1] * SIZE + c[0] == y * SIZE + x) m = COLORS[i];
                inv.setItem(y * 9 + 5 + x, item(m, "<white>Clique pour poser la pièce choisie ici"));
            }
            for (int i = 0; i < pieces.length; i++) {
                List<String> lore = new ArrayList<>();
                for (int y = 0; y < height(pieces[i]); y++) {
                    StringBuilder row = new StringBuilder("<white>");
                    for (int x = 0; x < width(pieces[i]); x++) { boolean on = false; for (int[] c : pieces[i]) if (c[0] == x && c[1] == y) on = true; row.append(on ? "■" : "<dark_gray>□<white>"); }
                    lore.add(row.toString());
                }
                lore.add(at[i] >= 0 ? "<green>posée <gray>(clique dans la grille pour la déplacer)" : "<yellow>à poser");
                ItemStack it = item(COLORS[i], (i == chosen ? "<gold><bold>▶ " : "<white>") + "Pièce " + (i + 1) + (i == chosen ? " (choisie)" : ""), lore.toArray(new String[0]));
                if (i == chosen) { ItemMeta meta = it.getItemMeta(); meta.setEnchantmentGlintOverride(true); it.setItemMeta(meta); }
                inv.setItem(45 + i * 2, it);
            }
            inv.setItem(52, item(Material.COMPASS, "<aqua>Tourner la pièce choisie"));
            inv.setItem(53, item(Material.BARRIER, "<red>Tout retirer"));
            inv.setItem(40, item(Material.PAPER, "<gold>Comment faire", "<gray>Choisis une pièce en bas, tourne-la si besoin,", "<gray>puis clique dans la grille de droite pour la poser.", "<gray>Là où deux pièces se recouvrent, les cases s'annulent.", "<gray>Reproduis la figure de gauche avec les trois pièces."));
        }

        @Override public Inventory getInventory() { return inv; }
    }

    private static ItemStack item(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.mm(name).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(Msg.mm(l).decoration(TextDecoration.ITALIC, false));
        if (!lines.isEmpty()) meta.lore(lines);
        it.setItemMeta(meta);
        return it;
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

    /** Ouvre une tablette a ce joueur ; {@code solved} est appele quand il la resout. */
    public void open(Player p, Consumer<Player> solved) {
        p.openInventory(new Board(new Random(), solved).inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Board b)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != b.inv || !(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getSlot(), x = slot % 9, y = slot / 9;
        if (y == 5 && x % 2 == 0 && x / 2 < b.pieces.length) b.chosen = x / 2;
        else if (slot == 52) {
            // Tourner retire la piece de la grille : on la repose dans son nouveau sens.
            b.pieces[b.chosen] = turn(b.pieces[b.chosen]);
            b.at[b.chosen] = -1;
        } else if (slot == 53) java.util.Arrays.fill(b.at, -1);
        else if (y < SIZE && x >= 5) {
            int gx = x - 5;
            if (gx + width(b.pieces[b.chosen]) > SIZE || y + height(b.pieces[b.chosen]) > SIZE) { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f); return; }
            b.at[b.chosen] = y * SIZE + gx;
            // La suivante a poser est choisie d'office.
            for (int i = 1; i <= b.pieces.length; i++) if (b.at[(b.chosen + i) % b.pieces.length] < 0) { b.chosen = (b.chosen + i) % b.pieces.length; break; }
        } else return;
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.3f);
        b.draw();
        if (b.done()) {
            p.closeInventory();
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.2f);
            b.solved.accept(p);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Board) e.setCancelled(true);
    }
}
