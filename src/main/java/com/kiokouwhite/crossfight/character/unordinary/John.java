package com.kiokouwhite.crossfight.character.unordinary;

import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.AbilityType;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Licence;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.TeamSize;
import com.kiokouwhite.crossfight.util.ChoiceMenu;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Mannequins;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * John - Path of Destruction. Il absorbe les pouvoirs de ceux qu'il frappe.
 */
public final class John extends Character {

    /** Capacites interdites a l'absorption (doc). */
    static final Set<String> FORBIDDEN = Set.of("sova_electro", "sova_reco", "sova_ult", "eren_titans", "eren_ult", "mikasa_ult", "livai_ult");

    static final class Borrowed {
        final Ability ability; final String from; final long expiresAt;
        Borrowed(Ability ability, String from, long expiresAt) { this.ability = ability; this.from = from; this.expiresAt = expiresAt; }
    }

    /** Par joueur : pouvoirs absorbes (3 max) et personnages frappes (le "pool"). */
    static final Map<UUID, List<Borrowed>> BORROWED = new HashMap<>();
    static final Map<UUID, Set<String>> POOL = new HashMap<>();

    public John() {
        super("john", "John", Voie.DESTRUCTION, Material.BLAZE_POWDER);
        addAbility(Unordinary.AURA);
        addAbility(new Absorption());
        setUltimate(new AllezVousFaireFoutre());
    }

    @Override public ItemStack createWeapon() { return null; }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Epee d'Aura a crafter (actif selon le craft)",
                "<white>Absorption d'aura : vole les pouvoirs des personnages frappes (3 max, 5 min)",
                "<white>/cf choose pour choisir ses pouvoirs",
                "<light_purple>Ultime : lance l'ultime d'un personnage en vie au choix");
    }

    static List<Borrowed> borrowed(Player p) { return BORROWED.computeIfAbsent(p.getUniqueId(), u -> new ArrayList<>()); }
    static Set<String> pool(Player p) { return POOL.computeIfAbsent(p.getUniqueId(), u -> new LinkedHashSet<>()); }

    @Override
    public List<Ability> allAbilities(Player p) {
        List<Ability> all = new ArrayList<>(allAbilities());
        for (Borrowed b : borrowed(p)) all.add(b.ability);
        return all;
    }

    static boolean copyable(Ability a) {
        return a.type() != AbilityType.ULTIMATE && !a.isWeaponBound() && !FORBIDDEN.contains(a.id()) && !a.id().startsWith("eren_t_");
    }

    static boolean isJohn(Player p) {
        CfPlayer cf = Unordinary.plugin().players().get(p);
        return cf.character() != null && "john".equals(cf.character().id());
    }

    /** Donne a John l'item du pouvoir (barre puis inventaire) et l'equipe. */
    static void give(Player p, Ability a) {
        CfPlayer cf = Unordinary.plugin().players().get(p);
        if (a.hasItem()) {
            ItemStack item = a.createItem();
            var inv = p.getInventory();
            int slot = -1;
            for (int i = 1; i <= 7 && slot < 0; i++) if (inv.getItem(i) == null) slot = i;
            for (int i = 9; i < 36 && slot < 0; i++) if (inv.getItem(i) == null) slot = i;
            if (slot >= 0) inv.setItem(slot, item); else inv.addItem(item);
        }
        try { a.onEquip(p, cf); } catch (Exception ex) { Unordinary.plugin().getLogger().warning("John onEquip " + a.id() + " : " + ex); }
    }

    static void take(Player p, Ability a) {
        CfPlayer cf = Unordinary.plugin().players().get(p);
        try { a.onUnequip(p, cf); } catch (Exception ex) { Unordinary.plugin().getLogger().warning("John onUnequip " + a.id() + " : " + ex); }
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (a.id().equals(Items.tag(inv.getItem(i), AbilityEngine.ABILITY_KEY))) inv.setItem(i, null);
    }

    static boolean test() { return Unordinary.plugin().game().isTest(); }

    /** Personnages dont John peut prendre les pouvoirs : ceux qu'il a frappes ; en mode test, tous. */
    static List<Character> available(Player p) {
        List<Character> out = new ArrayList<>();
        if (test()) {
            for (Licence l : Unordinary.plugin().characters().licences()) for (Character c : l.characters()) if (!"john".equals(c.id())) out.add(c);
        } else {
            for (String id : pool(p)) { Character c = Unordinary.plugin().characters().character(id); if (c != null) out.add(c); }
        }
        return out;
    }

    /** /cf choose : etape 1, retirer un pouvoir absorbe ou choisir un personnage ; etape 2, choisir son pouvoir. */
    public static boolean openChoose(Player p) {
        if (!isJohn(p)) return false;
        List<Borrowed> mine = borrowed(p);
        List<ChoiceMenu.Option> options = new ArrayList<>();
        for (Borrowed b : mine) {
            long left = Math.max(0, b.expiresAt - System.currentTimeMillis()) / 1000;
            options.add(new ChoiceMenu.Option(b.ability.icon(), b.ability.name() + " <red>(retirer)",
                    List.of("<gray>De " + b.from, test() ? "<gray>Mode test : sans limite" : "<gray>Expire dans " + left + " s", "<red>Clique pour le retirer"), () -> {
                        mine.remove(b); take(p, b.ability);
                        Msg.actionBar(p, "<gray>Pouvoir retire : " + b.ability.name());
                    }));
        }
        for (Character ch : available(p)) {
            int n = 0;
            for (Ability a : ch.abilities()) if (copyable(a)) n++;
            if (n == 0) continue;
            options.add(new ChoiceMenu.Option(ch.icon(), ch.name(), List.of("<gray>" + (ch.licence() == null ? "" : ch.licence().name()), "<gray>" + n + " pouvoir(s) absorbable(s)"), () -> openCharacter(p, ch)));
        }
        if (options.isEmpty()) { Msg.send(p, "<red>Aucun pouvoir a absorber : frappe d'abord un personnage d'une Equipe de Trois."); return true; }
        ChoiceMenu.open(p, "<dark_red><bold>Absorption d'aura <gray>(" + mine.size() + (test() ? "" : "/3") + ")", options);
        return true;
    }

    static void openCharacter(Player p, Character ch) {
        List<Borrowed> mine = borrowed(p);
        List<ChoiceMenu.Option> options = new ArrayList<>();
        for (Ability a : ch.abilities()) {
            if (!copyable(a)) continue;
            boolean has = false;
            for (Borrowed b : mine) if (b.ability.id().equals(a.id())) has = true;
            if (has) continue;
            List<String> lore = new ArrayList<>();
            lore.add("<gray>De " + ch.name() + " <dark_gray>(" + a.type().label() + ")");
            lore.addAll(Items.wrap(a.description()));
            options.add(new ChoiceMenu.Option(a.icon(), a.name(), lore, () -> choose(p, a, ch.name())));
        }
        if (options.isEmpty()) { Msg.actionBar(p, "<gray>Tu as deja tous les pouvoirs de " + ch.name() + "."); return; }
        ChoiceMenu.open(p, "<dark_red><bold>Pouvoirs de " + ch.name(), options, pl -> openChoose(pl));
    }

    static void choose(Player p, Ability a, String from) {
        List<Borrowed> mine = borrowed(p);
        if (!test() && mine.size() >= 3) { Msg.send(p, "<red>Tu as deja 3 pouvoirs : retire-en un dans /cf choose."); return; }
        mine.add(new Borrowed(a, from, test() ? Long.MAX_VALUE : System.currentTimeMillis() + 5 * 60 * 1000L));
        give(p, a);
        p.getWorld().spawnParticle(Particle.WITCH, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.1);
        p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 1f, 1.4f);
        Msg.send(p, "<dark_red>Pouvoir absorbe : <white>" + a.name() + "</white> (de " + from + (test() ? ")." : ", 5 minutes)."));
    }

    static final class Absorption extends PassiveAbility implements com.kiokouwhite.crossfight.ability.CustomUse {
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState state, boolean sneaking) { return openChoose(p); }
        Absorption() {
            super("john_absorption", "Absorption d'aura", "Quand John frappe un personnage d'une Equipe de Trois, ses pouvoirs deviennent disponibles. John en garde 3 au maximum, chacun pendant 5 minutes. /cf choose pour choisir.", Material.BLAZE_POWDER);
        }
        @Override protected List<String> extraLore() { return List.of("", "<yellow>Clic droit ou /cf choose : choisir ses pouvoirs"); }

        @Override public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) {
            String charId = null;
            if (victim instanceof Player v) {
                CfPlayer vcf = Unordinary.plugin().players().get(v);
                if (vcf.character() != null) charId = vcf.character().id();
            } else if (Mannequins.isMannequin(victim)) {
                charId = Mannequins.characterOf(victim);
            }
            if (charId == null || charId.equals("john")) return;
            Character ch = Unordinary.plugin().characters().character(charId);
            if (ch == null || ch.licence() == null || ch.licence().teamSize() != TeamSize.TRIO) return;
            if (pool(attacker).add(charId)) {
                Msg.send(attacker, "<dark_red>Absorption : les pouvoirs de <white>" + ch.name() + "</white> sont disponibles (/cf choose).");
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.8f, 1.6f);
            }
        }

        /** Les pouvoirs expirent au bout de 5 minutes. */
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) {
            List<Borrowed> mine = borrowed(p);
            for (Borrowed b : new ArrayList<>(mine)) {
                if (System.currentTimeMillis() < b.expiresAt) continue;
                mine.remove(b);
                take(p, b.ability);
                Msg.send(p, "<gray>Le pouvoir " + b.ability.name() + " s'est dissipe.");
            }
        }

        /** A la reapparition, les items des pouvoirs absorbes sont redonnes. */
        @Override public void onEquip(Player p, CfPlayer cf) {
            Bukkit.getScheduler().runTask(Unordinary.plugin(), () -> {
                for (Borrowed b : borrowed(p)) {
                    boolean present = false;
                    for (ItemStack it : p.getInventory().getContents()) if (b.ability.id().equals(Items.tag(it, AbilityEngine.ABILITY_KEY))) present = true;
                    if (!present && b.ability.hasItem()) give(p, b.ability);
                }
            });
        }
    }

    // ---------------------------------------------------------------- ultime

    /** John lance l'ultime d'un personnage en vie, au choix. */
    static final class AllezVousFaireFoutre extends UltimateAbility {
        AllezVousFaireFoutre() {
            super("john_ult", "Allez vous faire f*utre", "John recupere l'ultime d'un personnage en vie au choix et le lance.", Material.DRAGON_BREATH);
        }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            List<ChoiceMenu.Option> options = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            for (Player o : Bukkit.getOnlinePlayers()) {
                CfPlayer ocf = Unordinary.plugin().players().get(o);
                if (o == p || ocf.character() == null || ocf.character().ultimate() == null || !ocf.isAlive()) continue;
                if (!seen.add(ocf.character().id())) continue;
                UltimateAbility u = ocf.character().ultimate();
                List<String> lore = new ArrayList<>();
                lore.add("<gray>" + o.getName() + " <dark_gray>(" + ocf.character().name() + ")");
                lore.addAll(Items.wrap(u.description()));
                options.add(new ChoiceMenu.Option(u.icon(), u.name(), lore, () -> steal(p, cf, ocf.character(), u)));
            }
            if (options.isEmpty()) { Msg.actionBar(p, "<red>Aucun autre personnage en vie."); return false; }
            ChoiceMenu.open(p, "<dark_red><bold>Quel ultime voler ?", options);
            return true;
        }
        static void steal(Player p, CfPlayer cf, Character from, UltimateAbility u) {
            p.getWorld().spawnParticle(Particle.WITCH, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.1);
            p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 1f, 1.2f);
            Unordinary.plugin().game().broadcastNear(p, "<dark_red>" + p.getName() + " vole l'ultime de " + from.name() + " : <bold>" + u.name() + "</bold> !");
            // L'ultime reste a John 2 minutes (clics ulterieurs, item) puis se dissipe.
            List<Borrowed> mine = borrowed(p);
            mine.removeIf(b -> b.ability.type() == AbilityType.ULTIMATE);
            mine.add(new Borrowed(u, from.name(), System.currentTimeMillis() + 2 * 60 * 1000L));
            try {
                boolean ok = u.cast(p, cf, Unordinary.plugin().abilities().state(p, u));
                if (ok && u instanceof com.kiokouwhite.crossfight.ability.CustomUse) give(p, u);
            } catch (Exception ex) {
                Unordinary.plugin().getLogger().warning("Ultime vole " + u.id() + " : " + ex);
                Msg.send(p, "<red>Cet ultime n'a pas pu etre lance.");
            }
        }
    }
}
