package com.kiokouwhite.crossfight.util;

import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;

/**
 * Mondes vides utilises comme arenes (dimension de Byleth, Regna Ferox,
 * Match Found, antre de Judgement, Espace Blanc d'Omori).
 */
public final class ArenaWorlds {

    public static final int FLOOR_Y = 64;

    private ArenaWorlds() {}

    /** Generateur qui ne produit rien : un monde vide. */
    public static final class VoidGenerator extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
        @Override public Location getFixedSpawnLocation(World world, java.util.Random random) {
            return new Location(world, 0.5, FLOOR_Y + 1, 0.5);
        }
    }

    /** Charge ou cree un monde vide avec les regles d'arene. */
    public static World get(String name) {
        World w = Bukkit.getWorld(name);
        if (w != null) return w;
        WorldCreator wc = new WorldCreator(name).generator(new VoidGenerator()).environment(World.Environment.NORMAL);
        w = wc.createWorld();
        if (w == null) throw new IllegalStateException("Impossible de creer le monde " + name);
        w.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        w.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        w.setGameRule(GameRule.NATURAL_REGENERATION, false);
        w.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        w.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        w.setTime(6000);
        w.setStorm(false);
        w.setSpawnLocation(0, FLOOR_Y + 1, 0);
        return w;
    }

    /**
     * Construit une plateforme carree centree en 0,0 : sol, murs invisibles et un
     * eclairage minimal. Idempotent.
     */
    public static void buildPlatform(World w, int halfSize, Material floor, Material wall, int wallHeight) {
        for (int x = -halfSize; x <= halfSize; x++) {
            for (int z = -halfSize; z <= halfSize; z++) {
                w.getBlockAt(x, FLOOR_Y, z).setType(floor, false);
                w.getBlockAt(x, FLOOR_Y - 1, z).setType(Material.BEDROCK, false);
                boolean edge = Math.abs(x) == halfSize || Math.abs(z) == halfSize;
                if (edge) {
                    for (int y = 1; y <= wallHeight; y++) w.getBlockAt(x, FLOOR_Y + y, z).setType(wall, false);
                }
            }
        }
        // Quelques sources de lumiere pour eviter l'obscurite totale
        for (int x = -halfSize + 2; x < halfSize; x += 8) {
            for (int z = -halfSize + 2; z < halfSize; z += 8) {
                w.getBlockAt(x, FLOOR_Y, z).setType(Material.SEA_LANTERN, false);
            }
        }
    }

    public static Location center(World w) {
        return new Location(w, 0.5, FLOOR_Y + 1, 0.5);
    }
}
