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

    /** Capacites + ultime pour un joueur donne (John y ajoute ses pouvoirs absorbes). */
    public List<Ability> allAbilities(org.bukkit.entity.Player p) { return allAbilities(); }

    public Ability ability(org.bukkit.entity.Player p, String id) {
        for (Ability a : allAbilities(p)) if (a.id().equals(id)) return a;
        return null;
    }

    /** L'arme fournie au personnage, deja enchantee. Null si le personnage n'en a pas. */
    public abstract ItemStack createWeapon();

    /** Description courte affichee dans le menu de selection. */
    public abstract List<String> summary();
}
