package com.kiokouwhite.crossfight.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.character.Character;
import org.bukkit.entity.Player;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Le skin du personnage, porte par le joueur qui l'incarne.
 *
 * Minecraft n'affiche un skin que s'il est signe par Mojang : skins.txt contient, pour chaque
 * personnage, la reference signee (obtenue une fois pour toutes aupres de MineSkin) que le serveur
 * place dans le profil du joueur. Le skin d'origine est remis quand le joueur n'incarne plus personne.
 */
public final class Skins {

    private static final Map<String, ProfileProperty> SKINS = new HashMap<>();
    /** Le skin d'origine de chaque joueur deguise (vide : il n'en avait pas). */
    private static final Map<UUID, Optional<ProfileProperty>> ORIGINAL = new HashMap<>();

    private Skins() {}

    public static void load(CrossfightPlugin plugin) {
        SKINS.clear();
        try (InputStream in = plugin.getResource("skins.txt")) {
            if (in == null) return;
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                String[] t = line.trim().split("\\s+");
                if (t.length == 3 && !t[0].startsWith("#")) SKINS.put(t[0], new ProfileProperty("textures", t[1], t[2]));
            }
        } catch (Exception ex) { plugin.getLogger().warning("skins.txt illisible : " + ex); }
        plugin.getLogger().info(SKINS.size() + " skins de personnages.");
    }

    public static Collection<String> characters() { return SKINS.keySet(); }

    private static boolean enabled() { return CrossfightPlugin.get().getConfig().getBoolean("skins.enabled", true); }

    /** Met au joueur le skin de ce personnage ; null, ou un personnage sans skin : son skin d'origine. */
    public static void apply(Player p, Character character) {
        ProfileProperty skin = character == null || !enabled() ? null : SKINS.get(character.id());
        if (skin == null) { restore(p); return; }
        PlayerProfile profile = p.getPlayerProfile();
        ProfileProperty current = textures(profile);
        if (current != null && current.getValue().equals(skin.getValue())) return;
        ORIGINAL.putIfAbsent(p.getUniqueId(), Optional.ofNullable(current));
        profile.setProperty(skin);
        p.setPlayerProfile(profile);
    }

    /** Rend au joueur le skin qu'il avait en arrivant. */
    public static void restore(Player p) {
        Optional<ProfileProperty> original = ORIGINAL.remove(p.getUniqueId());
        if (original == null) return;
        PlayerProfile profile = p.getPlayerProfile();
        if (original.isPresent()) profile.setProperty(original.get());
        else profile.removeProperty("textures");
        p.setPlayerProfile(profile);
    }

    /** A la deconnexion : le profil repart de zero a la prochaine connexion. */
    public static void forget(Player p) { ORIGINAL.remove(p.getUniqueId()); }

    private static ProfileProperty textures(PlayerProfile profile) {
        for (ProfileProperty prop : profile.getProperties()) if (prop.getName().equals("textures")) return prop;
        return null;
    }
}
