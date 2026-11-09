package com.kiokouwhite.crossfight.util;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Armes fournies aux personnages, avec des degats fixes.
 */
public final class Weapons {

    private static final NamespacedKey DAMAGE_KEY = new NamespacedKey("crossfight", "weapon_damage");
    private static final NamespacedKey SPEED_KEY = new NamespacedKey("crossfight", "weapon_speed");

    private Weapons() {}

    /**
     * Cree une arme de melee dont les degats totaux (main nue incluse) valent {@code damage}.
     * La vitesse d'attaque est celle d'une epee (1.6) sauf pour les haches (1.0).
     */
    public static ItemStack melee(Material material, double damage, String miniName, List<String> lore) {
        ItemStack item = Items.of(material, miniName, lore);
        setDamage(item, damage);
        return Items.unbreakable(item);
    }

    /** Remplace les degats d'une arme de melee existante. */
    public static void setDamage(ItemStack item, double damage) {
        ItemMeta meta = item.getItemMeta();
        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE,
                new AttributeModifier(DAMAGE_KEY, damage - 1.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        double speed = item.getType().name().endsWith("_AXE") ? 1.0 : 1.6;
        meta.addAttributeModifier(Attribute.ATTACK_SPEED,
                new AttributeModifier(SPEED_KEY, speed - 4.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        item.setItemMeta(meta);
    }

    public static double damageOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 1.0;
        var mods = item.getItemMeta().getAttributeModifiers(Attribute.ATTACK_DAMAGE);
        if (mods == null) return 1.0;
        for (AttributeModifier m : mods) if (m.getKey().equals(DAMAGE_KEY)) return m.getAmount() + 1.0;
        return 1.0;
    }

    /** Arc avec un niveau de Puissance donne. */
    public static ItemStack bow(int power, boolean infinity, String miniName, List<String> lore) {
        ItemStack item = Items.of(Material.BOW, miniName, lore);
        ItemMeta meta = item.getItemMeta();
        if (power > 0) meta.addEnchant(Enchantment.POWER, power, true);
        if (infinity) meta.addEnchant(Enchantment.INFINITY, 1, true);
        item.setItemMeta(meta);
        return Items.unbreakable(item);
    }

    public static void setPower(ItemStack bow, int power) {
        if (bow == null || bow.getType() != Material.BOW) return;
        ItemMeta meta = bow.getItemMeta();
        meta.removeEnchant(Enchantment.POWER);
        if (power > 0) meta.addEnchant(Enchantment.POWER, power, true);
        bow.setItemMeta(meta);
    }
}
