package com.kiokouwhite.crossfight.ability;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Items;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Capacites empruntees par un joueur a un autre personnage (Absorption de John, Vol Merite de Hano...).
 * Elles s'ajoutent aux capacites de son personnage, avec leur item, pour un temps ou pour la partie.
 */
public final class Borrowed {

    public static final class Entry {
        public final Ability ability; public final String from; public final long expiresAt;
        public Entry(Ability ability, String from, long expiresAt) { this.ability = ability; this.from = from; this.expiresAt = expiresAt; }
        public boolean expired() { return System.currentTimeMillis() >= expiresAt; }
    }

    private static final Map<UUID, List<Entry>> BORROWED = new HashMap<>();

    private Borrowed() {}

    public static List<Entry> of(Player p) { return BORROWED.computeIfAbsent(p.getUniqueId(), u -> new ArrayList<>()); }

    public static boolean has(Player p, String abilityId) {
        for (Entry e : of(p)) if (e.ability.id().equals(abilityId)) return true;
        return false;
    }

    /** Ajoute la capacite, donne son item (barre puis inventaire) et l'equipe. */
    public static void add(Player p, Ability a, String from, long expiresAt) {
        of(p).add(new Entry(a, from, expiresAt));
        give(p, a);
    }

    public static void give(Player p, Ability a) {
        CfPlayer cf = CrossfightPlugin.get().players().get(p);
        if (a.hasItem()) {
            boolean present = false;
            for (ItemStack it : p.getInventory().getContents()) if (a.id().equals(Items.tag(it, AbilityEngine.ABILITY_KEY))) present = true;
            if (!present) {
                ItemStack item = a.createItem();
                var inv = p.getInventory();
                int slot = -1;
                for (int i = 1; i <= 7 && slot < 0; i++) if (inv.getItem(i) == null) slot = i;
                for (int i = 9; i < 36 && slot < 0; i++) if (inv.getItem(i) == null) slot = i;
                if (slot >= 0) inv.setItem(slot, item); else inv.addItem(item);
            }
        }
        try { a.onEquip(p, cf); } catch (Exception ex) { CrossfightPlugin.get().getLogger().warning("Borrowed onEquip " + a.id() + " : " + ex); }
    }

    /** Retire la capacite (desequipe, enleve son item). */
    public static void remove(Player p, Entry e) {
        of(p).remove(e);
        CfPlayer cf = CrossfightPlugin.get().players().get(p);
        try { e.ability.onUnequip(p, cf); } catch (Exception ex) { CrossfightPlugin.get().getLogger().warning("Borrowed onUnequip " + e.ability.id() + " : " + ex); }
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (e.ability.id().equals(Items.tag(inv.getItem(i), AbilityEngine.ABILITY_KEY))) inv.setItem(i, null);
    }

    /** Retire les capacites expirees ; retourne celles qui viennent d'expirer. */
    public static List<Entry> purge(Player p) {
        List<Entry> gone = new ArrayList<>();
        for (Entry e : new ArrayList<>(of(p))) if (e.expired()) { remove(p, e); gone.add(e); }
        return gone;
    }

    /** Redonne les items manquants (apres une reapparition). */
    public static void regive(Player p) {
        for (Entry e : of(p)) give(p, e.ability);
    }

    public static void clear(Player p) { BORROWED.remove(p.getUniqueId()); }
    public static void clearAll() { BORROWED.clear(); }
}
