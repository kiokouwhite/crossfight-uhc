package com.kiokouwhite.crossfight.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Fabrique d'items avec nom, lore et tags persistants.
 */
public final class Items {

    private Items() {}

    public static ItemStack of(Material material, String miniName, List<String> miniLore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.mm("<!italic>" + miniName));
        if (miniLore != null && !miniLore.isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : miniLore) {
                lore.add(Msg.mm("<!italic><gray>" + line));
            }
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack tag(ItemStack item, NamespacedKey key, String value) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
        return item;
    }

    public static String tag(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public static ItemStack unbreakable(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    /** Lore utilitaire : coupe un texte long en lignes d'environ 40 caracteres. */
    public static List<String> wrap(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (line.length() + word.length() + 1 > 40) {
                out.add(line.toString());
                line = new StringBuilder();
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) out.add(line.toString());
        return out;
    }

    @SuppressWarnings("unused")
    private static Component plain(String s) {
        return Component.text(s).decoration(TextDecoration.ITALIC, false);
    }
}
