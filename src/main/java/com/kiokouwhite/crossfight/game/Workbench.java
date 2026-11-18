package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * L'etabli : une enigme des piliers (la quete de Central Town). Un objet est montre ; a cote, une table de craft vide
 * (3 x 3) et, dessous, six ingredients (ceux de la recette et des intrus). On clique un ingredient pour le prendre, il
 * suit le viseur, et chaque clic sur une case de la table en pose un (on en a autant qu'on veut) ; un clic a vide sur
 * une case garnie la vide. L'enigme est resolue quand la table montre la recette de l'objet, a n'importe quel endroit
 * de la table, a l'endroit ou en miroir. Les recettes sont celles du jeu.
 */
final class Workbench extends PanelGame {

    /** Une recette : l'objet, ses rangees (une lettre par case, espace pour vide), et ce que vaut chaque lettre. */
    private record Recipe(Material result, String[] rows, Object... key) {
        Material at(int x, int y) {
            if (y >= rows.length || x >= rows[y].length()) return null;
            char c = rows[y].charAt(x);
            for (int i = 0; i < key.length; i += 2) if ((char) key[i] == c) return (Material) key[i + 1];
            return null;
        }
    }

    private static final Recipe[] RECIPES = {
            new Recipe(Material.IRON_PICKAXE, new String[]{"III", " S ", " S "}, 'I', Material.IRON_INGOT, 'S', Material.STICK),
            new Recipe(Material.DIAMOND_SWORD, new String[]{"D", "D", "S"}, 'D', Material.DIAMOND, 'S', Material.STICK),
            new Recipe(Material.IRON_AXE, new String[]{"II", "IS", " S"}, 'I', Material.IRON_INGOT, 'S', Material.STICK),
            new Recipe(Material.BOW, new String[]{" SW", "S W", " SW"}, 'S', Material.STICK, 'W', Material.STRING),
            new Recipe(Material.ARROW, new String[]{"F", "S", "P"}, 'F', Material.FLINT, 'S', Material.STICK, 'P', Material.FEATHER),
            new Recipe(Material.FISHING_ROD, new String[]{"  S", " SW", "S W"}, 'S', Material.STICK, 'W', Material.STRING),
            new Recipe(Material.BUCKET, new String[]{"I I", " I "}, 'I', Material.IRON_INGOT),
            new Recipe(Material.IRON_HELMET, new String[]{"III", "I I"}, 'I', Material.IRON_INGOT),
            new Recipe(Material.DIAMOND_CHESTPLATE, new String[]{"D D", "DDD", "DDD"}, 'D', Material.DIAMOND),
            new Recipe(Material.IRON_BOOTS, new String[]{"I I", "I I"}, 'I', Material.IRON_INGOT),
            new Recipe(Material.SHIELD, new String[]{"PIP", "PPP", " P "}, 'P', Material.OAK_PLANKS, 'I', Material.IRON_INGOT),
            new Recipe(Material.CHEST, new String[]{"PPP", "P P", "PPP"}, 'P', Material.OAK_PLANKS),
            new Recipe(Material.FURNACE, new String[]{"CCC", "C C", "CCC"}, 'C', Material.COBBLESTONE),
            new Recipe(Material.LADDER, new String[]{"S S", "SSS", "S S"}, 'S', Material.STICK),
            new Recipe(Material.TORCH, new String[]{"C", "S"}, 'C', Material.COAL, 'S', Material.STICK),
            new Recipe(Material.BOOKSHELF, new String[]{"PPP", "BBB", "PPP"}, 'P', Material.OAK_PLANKS, 'B', Material.BOOK),
            new Recipe(Material.ENCHANTING_TABLE, new String[]{" B ", "DOD", "OOO"}, 'B', Material.BOOK, 'D', Material.DIAMOND, 'O', Material.OBSIDIAN),
            new Recipe(Material.ANVIL, new String[]{"KKK", " I ", "III"}, 'K', Material.IRON_BLOCK, 'I', Material.IRON_INGOT),
            new Recipe(Material.GOLDEN_APPLE, new String[]{"GGG", "GAG", "GGG"}, 'G', Material.GOLD_INGOT, 'A', Material.APPLE),
            new Recipe(Material.COMPASS, new String[]{" I ", "IRI", " I "}, 'I', Material.IRON_INGOT, 'R', Material.REDSTONE),
            new Recipe(Material.CLOCK, new String[]{" G ", "GRG", " G "}, 'G', Material.GOLD_INGOT, 'R', Material.REDSTONE),
            new Recipe(Material.TNT, new String[]{"PSP", "SPS", "PSP"}, 'P', Material.GUNPOWDER, 'S', Material.SAND),
            new Recipe(Material.BREAD, new String[]{"WWW"}, 'W', Material.WHEAT),
            new Recipe(Material.CAKE, new String[]{"MMM", "SES", "WWW"}, 'M', Material.MILK_BUCKET, 'S', Material.SUGAR, 'E', Material.EGG, 'W', Material.WHEAT)};
    /** Les intrus possibles. */
    private static final Material[] EXTRAS = {Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.STICK, Material.STRING, Material.OAK_PLANKS, Material.COBBLESTONE,
            Material.COAL, Material.REDSTONE, Material.LEATHER, Material.FLINT, Material.FEATHER, Material.PAPER, Material.WHEAT, Material.BOOK, Material.SAND};

    private static final double CELL = 0.5, GRID_LEFT = -0.05, GRID_TOP = 1.2, TRAY_V = -0.95, TRAY_PITCH = 0.6;
    private static final Color BACK = Color.fromARGB(235, 12, 14, 36), SLOT = Color.fromARGB(255, 34, 40, 76), AIMED = Color.fromARGB(255, 70, 80, 130),
            WOOD = Color.fromARGB(255, 92, 64, 40), GOOD = Color.fromARGB(255, 60, 150, 80);

    final Recipe recipe;
    final List<Material> tray = new ArrayList<>();
    final Material[] grid = new Material[9];
    final TextDisplay[] cells = new TextDisplay[9], slots = new TextDisplay[6];
    final ItemDisplay[] placed = new ItemDisplay[9];
    ItemDisplay moving;
    Material held;
    int hover = -1;

    Workbench(CrossfightPlugin plugin, Player player, Location pillar, Random r, Consumer<Player> solved) {
        super(plugin, player, pillar, 4.4, 3.5, solved);
        recipe = RECIPES[r.nextInt(RECIPES.length)];
        for (int i = 1; i < recipe.key().length; i += 2) tray.add((Material) recipe.key()[i]);
        List<Material> extras = new ArrayList<>(List.of(EXTRAS));
        Collections.shuffle(extras, r);
        for (Material m : extras) if (tray.size() < 6 && !tray.contains(m)) tray.add(m);
        Collections.shuffle(tray, r);
        build();
    }

    double trayU(int i) { return (i - 2.5) * TRAY_PITCH; }

    void build() {
        backdrop();
        header(-1.3, 1.3, "<aqua>Refais cet objet", 0.6f);
        tile(-1.35, 0.55, 1.1, 1.1, 0.02, empty);
        item(-1.35, 0.55, 0.8, 0.04, new ItemStack(recipe.result()));
        label(-0.45, 0.42, "<white>←", 1.0f);
        label(GRID_LEFT + 1.5 * CELL, GRID_TOP + 0.1, "<white>Table de craft", 0.7f);
        if (drawn) plate(GRID_LEFT + 1.5 * CELL, GRID_TOP - 1.5 * CELL, 3 * CELL + 0.06, 3 * CELL + 0.06);
        else rect(GRID_LEFT + 1.5 * CELL, GRID_TOP - 1.5 * CELL, 3 * CELL + 0.12, 3 * CELL + 0.12, 0.015, WOOD);
        for (int c = 0; c < 9; c++) {
            double u = GRID_LEFT + (c % 3 + 0.5) * CELL, v = GRID_TOP - (c / 3 + 0.5) * CELL;
            cells[c] = tile(u, v, CELL - 0.05, CELL - 0.05, 0.02, empty);
            placed[c] = item(u, v, CELL * 0.72, 0.04, new ItemStack(Material.AIR));
        }
        label(0, TRAY_V + 0.34, "<gray>Ingrédients", 0.6f);
        for (int i = 0; i < 6; i++) {
            slots[i] = tile(trayU(i), TRAY_V, 0.5, 0.5, 0.02, empty);
            item(trayU(i), TRAY_V, 0.36, 0.04, new ItemStack(tray.get(i)));
        }
        label(0, -height / 2 + 0.06, "<yellow>Clic <gray>: prendre / poser / retirer   <yellow>Accroupi <gray>: fermer", 0.6f);
        moving = item(0, 0, 0.36, 0.07, new ItemStack(Material.AIR));
        moving.setTeleportDuration(2);
        boxes();
    }

    int cellAt() {
        if (!aimed || cu <= GRID_LEFT || cu >= GRID_LEFT + 3 * CELL || cv >= GRID_TOP || cv <= GRID_TOP - 3 * CELL) return -1;
        return (int) ((GRID_TOP - cv) / CELL) * 3 + (int) ((cu - GRID_LEFT) / CELL);
    }

    int trayAt() {
        if (!aimed || Math.abs(cv - TRAY_V) > 0.28) return -1;
        for (int i = 0; i < 6; i++) if (Math.abs(cu - trayU(i)) < 0.28) return i;
        return -1;
    }

    void hold(Material m) {
        held = m;
        moving.setItemStack(new ItemStack(m == null ? Material.AIR : m));
    }

    @Override void onClick() {
        int cell = cellAt(), pick = trayAt();
        if (pick >= 0) {
            hold(tray.get(pick) == held ? null : tray.get(pick));
            player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_INSERT, 0.8f, 1.2f);
        } else if (cell >= 0) {
            if (held != null && grid[cell] != held) { grid[cell] = held; player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.9f, 1.2f); }
            else if (grid[cell] != null) { grid[cell] = null; player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_REMOVE_ONE, 0.8f, 1f); }
            placed[cell].setItemStack(new ItemStack(grid[cell] == null ? Material.AIR : grid[cell]));
            if (matches(false) || matches(true)) {
                hold(null);
                for (TextDisplay c : cells) paint(c, GOOD);
                win(Sound.BLOCK_ANVIL_USE);
            }
        } else if (held != null) {
            hold(null);
            player.playSound(player.getLocation(), Sound.ITEM_BUNDLE_DROP_CONTENTS, 0.8f, 1f);
        }
    }

    /** Vrai si la table montre la recette : meme dessin une fois les bords vides otes, a l'endroit ou en miroir. */
    boolean matches(boolean mirror) {
        int x0 = 3, y0 = 3, x1 = -1, y1 = -1;
        for (int c = 0; c < 9; c++) if (grid[c] != null) { x0 = Math.min(x0, c % 3); x1 = Math.max(x1, c % 3); y0 = Math.min(y0, c / 3); y1 = Math.max(y1, c / 3); }
        if (x1 < 0) return false;
        int w = 0;
        for (String row : recipe.rows()) w = Math.max(w, row.length());
        int rx0 = w, rx1 = -1;
        for (int y = 0; y < recipe.rows().length; y++) for (int x = 0; x < w; x++) if (recipe.at(x, y) != null) { rx0 = Math.min(rx0, x); rx1 = Math.max(rx1, x); }
        if (x1 - x0 != rx1 - rx0 || y1 - y0 != recipe.rows().length - 1) return false;
        for (int y = 0; y <= y1 - y0; y++) for (int x = 0; x <= x1 - x0; x++)
            if (grid[(y0 + y) * 3 + x0 + x] != recipe.at(mirror ? rx1 - x : rx0 + x, y)) return false;
        return true;
    }

    @Override void onTick() {
        int over = cellAt() >= 0 ? cellAt() : trayAt() >= 0 ? 9 + trayAt() : -1;
        if (over != hover) {
            for (int c = 0; c < 9; c++) paint(cells[c], c == over ? pointed : empty);
            for (int i = 0; i < 6; i++) paint(slots[i], 9 + i == over ? pointed : empty);
            hover = over;
        }
        if (held != null && aimed) moving.teleport(spot(cu, cv, 0.07));
    }

    @Override String hint() { return "<aqua>Prends un ingrédient en bas, pose-le sur la table : refais la recette de l'objet montré."; }
}
