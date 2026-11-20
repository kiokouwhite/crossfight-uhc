package com.kiokouwhite.crossfight.character;

import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Un personnage jouable : son arme, ses capacites et son ultime.
 * Les sous-classes concretes vivent dans character.&lt;licence&gt;.
 */
public abstract class Character {

    private final String id;
    private final String name;
    private final Voie voie;
    private final Material icon;
    private Licence licence;
    private final List<Ability> abilities = new ArrayList<>();
    private UltimateAbility ultimate;

    protected Character(String id, String name, Voie voie, Material icon) {
        this.id = id;
        this.name = name;
        this.voie = voie;
        this.icon = icon;
    }

    public String id() { return id; }
    public String name() { return name; }
    public Voie voie() { return voie; }
    public Material icon() { return icon; }
    public Licence licence() { return licence; }

    void setLicence(Licence licence) { this.licence = licence; }

    protected void addAbility(Ability a) { abilities.add(a); }
    protected void setUltimate(UltimateAbility u) { this.ultimate = u; }

    /** Capacites hors ultime, dans l'ordre des slots de la barre d'action. */
    public List<Ability> abilities() { return List.copyOf(abilities); }

    public UltimateAbility ultimate() { return ultimate; }

    /** Capacites + ultime. */
    public List<Ability> allAbilities() {
        List<Ability> all = new ArrayList<>(abilities);
        if (ultimate != null) all.add(ultimate);
        return all;
    }

    public Ability ability(String id) {
        for (Ability a : allAbilities()) if (a.id().equals(id)) return a;
        return null;
    }

    /** Capacites + ultime pour un joueur donne, plus celles qu'il a empruntees (Absorption de John, Vol Merite...). */
    public List<Ability> allAbilities(org.bukkit.entity.Player p) {
        List<Ability> all = allAbilities();
        for (com.kiokouwhite.crossfight.ability.Borrowed.Entry e : com.kiokouwhite.crossfight.ability.Borrowed.of(p)) all.add(e.ability);
        return all;
    }

    public Ability ability(org.bukkit.entity.Player p, String id) {
        for (Ability a : allAbilities(p)) if (a.id().equals(id)) return a;
        return null;
    }

    /** L'arme fournie au personnage, deja enchantee. Null si le personnage n'en a pas. */
    public abstract ItemStack createWeapon();

    /** La variante de la carte de l'arme en haut a gauche ("" par defaut ; Corrin : "l" ou "t" selon la voie de la Yato, en minuscules : une cle de ressource). */
    public String hudWeaponVariant(org.bukkit.entity.Player p) { return ""; }

    /**
     * La variante des textes du HUD de gauche ("" par defaut ; Weiss : "d0" a "d5", sa Dust en cours) : les cartes
     * ne montrent que ce qui la concerne. Les images portent ce suffixe quand le texte est affiche
     * (tools/build_passifs.py, TEXT_VARIANTS).
     */
    public String hudTextVariant(org.bukkit.entity.Player p) { return ""; }

    /**
     * Des lignes propres au personnage dans le tableau de droite du joueur, sous son personnage (Eren : les
     * pseudos de ses cibles). Du vrai texte : ce que les cartes du HUD, des images dessinees a l'avance, ne peuvent pas dire.
     */
    public List<String> sidebarLines(org.bukkit.entity.Player p) { return List.of(); }

    /** La variante de l'orbe de l'ultime, qui porte l'avancement de l'arme ("" par defaut ; Corrin : "yato_l3"). */
    public String hudOrbVariant(org.bukkit.entity.Player p) { return ""; }

    /** Description courte affichee dans le menu de selection. */
    public abstract List<String> summary();
}
