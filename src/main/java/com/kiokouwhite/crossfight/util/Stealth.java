package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Invisibilite totale : le joueur est retire de l'ecran des spectateurs choisis
 * (armure comprise), contrairement a l'effet de potion qui laisse l'equipement visible.
 * Chaque "source" (une capacite) gere sa propre liste, et un joueur reste cache tant
 * qu'au moins une source le cache.
 */
public final class Stealth {

    /** cible -> source -> spectateurs qui ne la voient plus. */
    private static final Map<UUID, Map<String, Set<UUID>>> HIDDEN = new HashMap<>();

    private Stealth() {}

    public static void hide(Player target, Player viewer, String source) {
        if (target == viewer) return;
        Set<UUID> set = HIDDEN.computeIfAbsent(target.getUniqueId(), u -> new HashMap<>()).computeIfAbsent(source, s -> new HashSet<>());
        if (set.add(viewer.getUniqueId())) refresh(target, viewer);
    }

    public static void show(Player target, Player viewer, String source) {
        Map<String, Set<UUID>> bySource = HIDDEN.get(target.getUniqueId());
        if (bySource == null) return;
        Set<UUID> set = bySource.get(source);
        if (set != null && set.remove(viewer.getUniqueId())) refresh(target, viewer);
    }

    /** Cache la cible a tous les joueurs qui passent le filtre, la montre aux autres. */
    public static void hideFromAll(Player target, String source, java.util.function.Predicate<Player> viewers) {
        for (Player v : Bukkit.getOnlinePlayers()) {
            if (v == target) continue;
            if (viewers.test(v)) hide(target, v, source); else show(target, v, source);
        }
    }

    /** Retire une source : la cible redevient visible pour tout le monde (sauf autres sources). */
    public static void reveal(Player target, String source) {
        Map<String, Set<UUID>> bySource = HIDDEN.get(target.getUniqueId());
        if (bySource == null) return;
        Set<UUID> set = bySource.remove(source);
        if (set == null) return;
        for (UUID u : set) {
            Player v = Bukkit.getPlayer(u);
            if (v != null) refresh(target, v);
        }
        if (bySource.isEmpty()) HIDDEN.remove(target.getUniqueId());
    }

    /** Tout retirer (fin de partie, mort). */
    public static void revealAll(Player target) {
        showArmor(target);
        Map<String, Set<UUID>> bySource = HIDDEN.remove(target.getUniqueId());
        if (bySource == null) return;
        for (Set<UUID> set : bySource.values()) {
            for (UUID u : set) {
                Player v = Bukkit.getPlayer(u);
                if (v != null) showFor(v, target);
            }
        }
    }

    /**
     * Cache / montre la cible a un spectateur. Le joueur pilote d'un mannequin (troisieme personne)
     * n'est pas connu du serveur : c'est son mannequin qu'on cache.
     */
    private static void hideFor(Player viewer, Player target) {
        if (MannequinPlayer.isProxy(target)) viewer.hideEntity(CrossfightPlugin.get(), MannequinPlayer.real(target));
        else viewer.hidePlayer(CrossfightPlugin.get(), target);
    }

    private static void showFor(Player viewer, Player target) {
        if (MannequinPlayer.isProxy(target)) viewer.showEntity(CrossfightPlugin.get(), MannequinPlayer.real(target));
        else viewer.showPlayer(CrossfightPlugin.get(), target);
    }

    private static final Set<UUID> ARMOR_HIDDEN = new HashSet<>();

    /**
     * Cache l'armure du joueur aux yeux de tous les autres (les ennemis ne le voient de
     * toute facon pas, les allies le voient sans equipement). A rappeler chaque seconde.
     */
    public static void hideArmor(Player target) {
        ARMOR_HIDDEN.add(target.getUniqueId());
        org.bukkit.inventory.ItemStack air = new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR);
        for (Player v : Bukkit.getOnlinePlayers()) {
            if (v == target) continue;
            for (org.bukkit.inventory.EquipmentSlot slot : new org.bukkit.inventory.EquipmentSlot[]{
                    org.bukkit.inventory.EquipmentSlot.HEAD, org.bukkit.inventory.EquipmentSlot.CHEST,
                    org.bukkit.inventory.EquipmentSlot.LEGS, org.bukkit.inventory.EquipmentSlot.FEET,
                    org.bukkit.inventory.EquipmentSlot.HAND, org.bukkit.inventory.EquipmentSlot.OFF_HAND}) {
                v.sendEquipmentChange(MannequinPlayer.real((org.bukkit.entity.LivingEntity) target), slot, air);
            }
        }
    }

    /** Cache uniquement les pieces d'armure (casque, plastron, jambieres, bottes), pas les objets en main. */
    public static void hideArmorOnly(Player target) {
        ARMOR_HIDDEN.add(target.getUniqueId());
        org.bukkit.inventory.ItemStack air = new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR);
        for (Player v : Bukkit.getOnlinePlayers()) {
            if (v == target) continue;
            var body = MannequinPlayer.real((org.bukkit.entity.LivingEntity) target);
            v.sendEquipmentChange(body, org.bukkit.inventory.EquipmentSlot.HEAD, air);
            v.sendEquipmentChange(body, org.bukkit.inventory.EquipmentSlot.CHEST, air);
            v.sendEquipmentChange(body, org.bukkit.inventory.EquipmentSlot.LEGS, air);
            v.sendEquipmentChange(body, org.bukkit.inventory.EquipmentSlot.FEET, air);
        }
    }

    /** Renvoie l'equipement reel du joueur a tout le monde. */
    public static void showArmor(Player target) {
        if (!ARMOR_HIDDEN.remove(target.getUniqueId())) return;
        var eq = target.getEquipment();
        var body = MannequinPlayer.real((org.bukkit.entity.LivingEntity) target);
        for (Player v : Bukkit.getOnlinePlayers()) {
            if (v == target) continue;
            v.sendEquipmentChange(body, org.bukkit.inventory.EquipmentSlot.HEAD, eq.getHelmet());
            v.sendEquipmentChange(body, org.bukkit.inventory.EquipmentSlot.CHEST, eq.getChestplate());
            v.sendEquipmentChange(body, org.bukkit.inventory.EquipmentSlot.LEGS, eq.getLeggings());
            v.sendEquipmentChange(body, org.bukkit.inventory.EquipmentSlot.FEET, eq.getBoots());
            v.sendEquipmentChange(body, org.bukkit.inventory.EquipmentSlot.HAND, eq.getItemInMainHand());
            v.sendEquipmentChange(body, org.bukkit.inventory.EquipmentSlot.OFF_HAND, eq.getItemInOffHand());
        }
    }

    public static boolean isArmorHidden(Player target) {
        return ARMOR_HIDDEN.contains(target.getUniqueId());
    }

    public static boolean isHiddenFrom(Player target, Player viewer) {
        Map<String, Set<UUID>> bySource = HIDDEN.get(target.getUniqueId());
        if (bySource == null) return false;
        for (Set<UUID> set : bySource.values()) if (set.contains(viewer.getUniqueId())) return true;
        return false;
    }

    private static void refresh(Player target, Player viewer) {
        if (isHiddenFrom(target, viewer)) hideFor(viewer, target);
        else showFor(viewer, target);
    }
}
