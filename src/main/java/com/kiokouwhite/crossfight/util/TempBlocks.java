package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Blocs poses temporairement par une capacite (patinoire, barriere, feu...).
 * Chaque instance garde l'etat d'origine et le remet en place a la demande ou apres un delai.
 */
public final class TempBlocks {

    private static final List<TempBlocks> ACTIVE = new ArrayList<>();

    private final Map<Block, BlockState> placed = new LinkedHashMap<>();
    private boolean unbreakable;

    public TempBlocks() {
        ACTIVE.add(this);
    }

    public TempBlocks unbreakable() {
        this.unbreakable = true;
        return this;
    }

    /** Pose un bloc si l'emplacement est libre (air ou vegetation). */
    public boolean place(Block b, Material m) {
        if (placed.containsKey(b)) return false;
        if (!(b.getType().isAir() || b.isReplaceable())) return false;
        placed.put(b, b.getState());
        b.setType(m, false);
        return true;
    }

    /** Remplace un bloc, meme solide (sauf les indestructibles). */
    public boolean replace(Block b, Material m) {
        if (placed.containsKey(b)) return false;
        Material t = b.getType();
        if (t == Material.BEDROCK || t == Material.BARRIER || t == Material.END_PORTAL_FRAME) return false;
        placed.put(b, b.getState());
        b.setType(m, false);
        return true;
    }

    public boolean contains(Block b) {
        return placed.containsKey(b);
    }

    public int size() {
        return placed.size();
    }

    public void restore() {
        List<Map.Entry<Block, BlockState>> entries = new ArrayList<>(placed.entrySet());
        for (int i = entries.size() - 1; i >= 0; i--) entries.get(i).getValue().update(true, false);
        placed.clear();
        ACTIVE.remove(this);
    }

    public void restoreAfter(long ticks) {
        Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), this::restore, ticks);
    }

    /** Un bloc pose par une capacite marquee incassable ? */
    public static boolean isProtected(Block b) {
        for (TempBlocks t : ACTIVE) if (t.unbreakable && t.placed.containsKey(b)) return true;
        return false;
    }

    /** Remet tout en place (fin de partie). */
    public static void restoreAll() {
        for (TempBlocks t : new ArrayList<>(ACTIVE)) t.restore();
    }
}
