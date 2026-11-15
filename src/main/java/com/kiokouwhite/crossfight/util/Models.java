package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Les modeles 3D d'armes du pack (tools/build_armes.py, tools/build_falchion.py).
 *
 * Le pack ne montre un modele qu'a l'arme marquee de son nom (custom_model_data) : {@link #mark} pose cette
 * marque quand le pack contient le modele, et l'arme reste l'objet habituel sinon. models.txt dit comment le
 * pantin des animations tient chaque modele : ou est sa poignee, de combien il penche pour se poser comme
 * l'image de l'objet qu'il remplace, et sa taille.
 */
public final class Models {

    /** @param grip la poignee, en pixels depuis le milieu du modele, vue par un affichage d'objet */
    public record Model(String name, Vector3f grip, Quaternionf tilt, float scale) {}

    private static final Map<String, Model> MODELS = new HashMap<>();

    private Models() {}

    public static void load(CrossfightPlugin plugin) {
        MODELS.clear();
        try (InputStream in = plugin.getResource("models.txt")) {
            if (in == null) return;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                String[] t = line.trim().split("\\s+");
                if (t.length != 9 || t[0].startsWith("#")) continue;
                float[] v = new float[8];
                for (int i = 0; i < 8; i++) v[i] = Float.parseFloat(t[i + 1]);
                MODELS.put(t[0], new Model(t[0], new Vector3f(v[0], v[1], v[2]), new Quaternionf(v[3], v[4], v[5], v[6]).normalize(), v[7]));
            }
        } catch (Exception ex) { plugin.getLogger().warning("models.txt illisible : " + ex); }
    }

    /** Vrai si le pack contient ce modele. */
    public static boolean has(String name) { return Sounds.has("model." + name); }

    /** Marque l'objet pour que le pack le montre avec ce modele ; sans le modele dans le pack, l'objet reste tel quel. */
    public static ItemStack mark(ItemStack item, String name) {
        if (item == null || !has(name)) return item;
        // Un modele peint se montre sous ses vraies couleurs : pas de reflet d'enchantement par-dessus.
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(false);
        item.setItemMeta(meta);
        return look(item, name);
    }

    /** Le meme objet sous une autre apparence du pack (un etat du modele : "yumi_fujin_tire_2", l'arc bande). */
    public static ItemStack look(ItemStack item, String state) {
        ItemMeta meta = item.getItemMeta();
        var cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(List.of(state));
        meta.setCustomModelDataComponent(cmd);
        item.setItemMeta(meta);
        return item;
    }

    /** Le modele 3D que montre cet objet (d'apres sa marque, ou celle d'un de ses etats), ou null. */
    public static Model of(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasCustomModelDataComponent()) return null;
        for (String s : meta.getCustomModelDataComponent().getStrings()) {
            Model m = MODELS.get(s);
            // Un etat ("<nom>_tire_1") se tient comme son modele.
            for (int cut = s.lastIndexOf('_'); m == null && cut > 0; cut = s.lastIndexOf('_', cut - 1)) m = MODELS.get(s.substring(0, cut));
            if (m != null) return m;
        }
        return null;
    }
}
