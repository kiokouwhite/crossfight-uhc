package com.kiokouwhite.crossfight.hud;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.ability.AbilityType;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Sounds;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Les passifs, en haut a gauche de l'ecran : l'icone de chacun, eteinte au repos, allumee quand il agit ;
 * au moment ou il s'allume, la fleche verte monte en s'animant (six images de 2 ticks) avec un son. Un passif agit soit le temps d'un declenchement ({@link #flash}), soit tant qu'une condition
 * tient ({@link #setActive}). /cf passif show ajoute a cote de chaque icone le texte qui rappelle ce qu'il fait.
 *
 * Comme l'orbe de l'ultime, c'est une image de plein ecran (camera_overlay) portee dans la case du casque,
 * que le joueur seul voit : une image par personnage, par combinaison de passifs allumes et avec ou sans texte
 * (tools/build_passifs.py, pack complet). Les passifs d'arme restent decrits sur l'arme.
 */
public final class PassiveHud {

    private PassiveHud() {}

    private static final long FLASH_MS = 2000L;
    private static final Map<UUID, Map<String, Long>> FLASH = new HashMap<>();
    private static final Map<UUID, Set<String>> ACTIVE = new HashMap<>();
    /** Les passifs allumes au dernier passage, et le debut de l'animation de la fleche (un passif vient de s'allumer). */
    private static final Map<UUID, Set<String>> WAS_LIT = new HashMap<>();
    private static final Map<UUID, Long> ANIM = new HashMap<>();
    /** Six etapes de 2 ticks (tools/build_passifs.py, FRAMES). */
    private static final int FRAMES = 6;
    private static final long FRAME_MS = 100L;
    private static NamespacedKey textKey;

    /** Les passifs affiches d'un personnage, dans l'ordre (les memes que l'outil des images). */
    public static List<Ability> shown(Character c) {
        List<Ability> out = new ArrayList<>();
        if (c == null) return out;
        for (Ability a : c.abilities()) if (a.type() == AbilityType.PASSIVE && !a.isWeaponBound()) out.add(a);
        return out;
    }

    /** Le passif vient d'agir : son icone s'allume deux secondes. */
    public static void flash(Player p, String id) {
        if (p == null) return;
        FLASH.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(id, System.currentTimeMillis() + FLASH_MS);
    }

    /** Le passif agit tant que {@code on} : son icone reste allumee. */
    public static void setActive(Player p, String id, boolean on) {
        if (p == null) return;
        Set<String> s = ACTIVE.computeIfAbsent(p.getUniqueId(), k -> new HashSet<>());
        if (on) s.add(id); else s.remove(id);
    }

    private static boolean lit(UUID u, String id) {
        Set<String> s = ACTIVE.get(u);
        if (s != null && s.contains(id)) return true;
        Map<String, Long> f = FLASH.get(u);
        return f != null && f.getOrDefault(id, 0L) > System.currentTimeMillis();
    }

    private static NamespacedKey key() {
        if (textKey == null) textKey = new NamespacedKey(CrossfightPlugin.get(), "passifs_texte");
        return textKey;
    }

    public static boolean showsText(Player p) {
        Byte b = p.getPersistentDataContainer().get(key(), PersistentDataType.BYTE);
        return b != null && b == 1;
    }

    public static void setShowsText(Player p, boolean on) {
        p.getPersistentDataContainer().set(key(), PersistentDataType.BYTE, (byte) (on ? 1 : 0));
    }

    /** L'image a montrer maintenant ("passif/<perso>_<allumes>_<texte>"), ou null s'il n'y en a pas. */
    public static String image(Player p) {
        CfPlayer cf = CrossfightPlugin.get().players().get(p);
        Character c = cf == null ? null : cf.character();
        if (c == null || !Sounds.has("passif." + c.id())) return null;
        List<Ability> list = shown(c);
        if (list.isEmpty()) return null;
        int mask = 0;
        Set<String> now = new HashSet<>();
        for (int i = 0; i < list.size(); i++) if (lit(p.getUniqueId(), list.get(i).id())) { mask |= 1 << i; now.add(list.get(i).id()); }
        // Un passif qui s'allume : la fleche monte en s'animant, avec un son.
        Set<String> before = WAS_LIT.put(p.getUniqueId(), now);
        if (before != null && !before.containsAll(now)) {
            ANIM.put(p.getUniqueId(), System.currentTimeMillis());
            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, org.bukkit.SoundCategory.PLAYERS, 0.6f, 1.6f);
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, org.bukkit.SoundCategory.PLAYERS, 0.35f, 1.4f);
        }
        String key = "passif/" + c.id() + "_" + mask + "_" + (showsText(p) ? 1 : 0);
        long since = System.currentTimeMillis() - ANIM.getOrDefault(p.getUniqueId(), 0L);
        if (mask != 0 && since < FRAMES * FRAME_MS) key += "_" + (1 + since / FRAME_MS);
        return key;
    }

    public static void forget(Player p) {
        FLASH.remove(p.getUniqueId());
        ACTIVE.remove(p.getUniqueId());
        WAS_LIT.remove(p.getUniqueId());
        ANIM.remove(p.getUniqueId());
    }
}
