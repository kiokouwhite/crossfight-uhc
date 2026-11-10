package com.kiokouwhite.crossfight.character.valorant;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.AbilityType;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.character.Licence;
import com.kiokouwhite.crossfight.character.LicenceMechanic;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.team.TeamSize;
import com.kiokouwhite.crossfight.util.ChoiceMenu;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Licence VALORANT : pas d'arme attitree. Armes blanches par paliers de kills,
 * armes a feu achetees avec des credits.
 */
public final class Valorant {

    private Valorant() {}

    public static Licence create() {
        Licence l = new Licence("valorant", "VALORANT", TeamSize.TRIO,
                "Pas d'arme attitree : armes blanches par paliers (tous les 3 kills) et armes a feu achetees avec des credits.");
        l.mechanic(new Economy());
        Neon neon = new Neon();
        Sage sage = new Sage();
        Sova sova = new Sova();
        Jett jett = new Jett();
        l.add(neon); l.add(sage); l.add(sova); l.add(jett);
        l.listener(new Guns());
        l.listener(neon);
        l.listener(sage);
        l.listener(sova);
        return l;
    }

    // ------------------------------------------------------------ armes blanches

    /**
     * Paliers d'armes blanches. Le doc ne detaille pas encore les categories et leurs
     * embranchements : on monte d'un palier tous les 3 kills, en attendant.
     */
    static final double[] MELEE_DAMAGE = {6.0, 7.5, 9.0};
    static final Material[] MELEE_MATERIAL = {Material.IRON_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD};
    static final String[] MELEE_NAME = {"Couteau tactique", "Lame d'agent", "Lame d'elite"};

    static int tierFor(int kills) {
        return Math.min(MELEE_DAMAGE.length - 1, kills / 3);
    }

    static ItemStack meleeWeapon(int tier) {
        return Weapons.melee(MELEE_MATERIAL[tier], MELEE_DAMAGE[tier], "<red><bold>" + MELEE_NAME[tier],
                List.of("<gray>Palier " + (tier + 1) + "/" + MELEE_DAMAGE.length + " <dark_gray>- un palier tous les 3 kills"));
    }

    static void refreshMelee(Player p) {
        CfPlayer cf = CrossfightPlugin.get().players().get(p);
        if (cf.character() == null) return;
        int tier = tierFor(cf.kills());
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (cf.character().id().equals(Items.tag(it, AbilityEngine.WEAPON_KEY)) && Items.tag(it, Guns.GUN_KEY) == null) {
                int current = it.getType() == MELEE_MATERIAL[0] ? 0 : it.getType() == MELEE_MATERIAL[1] ? 1 : 2;
                if (current != tier) {
                    ItemStack w = meleeWeapon(tier);
                    AbilityEngine.decorateWeapon(w, cf.character());
                    inv.setItem(i, w);
                    Msg.send(p, "<red>Ton arme blanche passe au palier " + (tier + 1) + " : <white>" + MELEE_NAME[tier]);
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.5f);
                }
                return;
            }
        }
    }

    // ------------------------------------------------------------------ credits

    /** Economie de la licence : credits, kills, equipes eliminees. */
    public static final class Economy implements LicenceMechanic {

        static final int BASE = 500;
        static final int PER_10_MIN = 100;
        static final int PER_KILL = 100, KILL_CAP = 400;
        static final int PER_TEAM = 400, TEAM_CAP = 900;

        private final Map<UUID, Integer> credits = new HashMap<>();
        private final Map<UUID, Integer> killCredits = new HashMap<>();
        private final Map<UUID, Integer> teamCredits = new HashMap<>();
        private int lastPaidMinute = 0;

        public int credits(Player p) { return credits.computeIfAbsent(p.getUniqueId(), u -> BASE); }

        public void add(Player p, int amount) { credits.put(p.getUniqueId(), credits(p) + amount); }

        public boolean spend(Player p, int amount) {
            if (CrossfightPlugin.get().game().isTest()) return true; // credits infinis en mode test
            if (credits(p) < amount) return false;
            credits.put(p.getUniqueId(), credits(p) - amount);
            return true;
        }

        public void set(Player p, int amount) { credits.put(p.getUniqueId(), Math.max(0, amount)); }

        @Override
        public void reset() {
            credits.clear(); killCredits.clear(); teamCredits.clear(); lastPaidMinute = 0;
        }

        @Override
        public void onEquip(Player p) {
            refreshMelee(p);
        }

        @Override
        public void tick(Player p) {
            int minute = CrossfightPlugin.get().game().elapsedMinutes();
            // +100 credits toutes les 10 minutes, verses une fois par palier a chaque joueur Valorant.
            int paid = CrossfightPlugin.get().abilities().state(p, SHOP).get("paidMinute", 0);
            if (minute / 10 > paid / 10) {
                CrossfightPlugin.get().abilities().state(p, SHOP).set("paidMinute", minute);
                if (minute >= 10) { add(p, PER_10_MIN); Msg.send(p, "<gold>+" + PER_10_MIN + " credits <gray>(10 minutes ecoulees)"); }
            }
        }

        @Override
        public void onKill(Player killer, Player victim) {
            refreshMelee(killer);
            int got = killCredits.getOrDefault(killer.getUniqueId(), 0);
            if (got < KILL_CAP) {
                killCredits.put(killer.getUniqueId(), got + PER_KILL);
                add(killer, PER_KILL);
                Msg.send(killer, "<gold>+" + PER_KILL + " credits <gray>(elimination)");
            }
        }

        @Override
        public void onTeamEliminated(Team team) {
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                CfPlayer cf = CrossfightPlugin.get().players().get(p);
                if (cf.character() == null || cf.character().licence() == null || !"valorant".equals(cf.character().licence().id())) continue;
                if (cf.team() == null || cf.team() == team) continue;
                int got = teamCredits.getOrDefault(p.getUniqueId(), 0);
                if (got >= TEAM_CAP) continue;
                int gain = Math.min(PER_TEAM, TEAM_CAP - got);
                teamCredits.put(p.getUniqueId(), got + gain);
                add(p, gain);
                Msg.send(p, "<gold>+" + gain + " credits <gray>(equipe eliminee)");
            }
        }
    }

    static Economy economy() {
        return (Economy) CrossfightPlugin.get().characters().licence("valorant").mechanic();
    }

    // ------------------------------------------------------------------ boutique

    /** L'item "Boutique" : achete une arme a feu avec ses credits. */
    public static final class Shop extends Ability implements CustomUse {
        Shop() {
            super("valorant_shop", "Boutique d'armes", "Achete une arme a feu avec tes credits : 500 au depart, +100 toutes les 10 minutes, +100 par kill (max 400), +400 par equipe eliminee (max 900).",
                    AbilityType.ACTIVE, Material.GOLD_INGOT);
        }

        @Override
        public String barText(Player p, CfPlayer cf, AbilityState st) {
            if (CrossfightPlugin.get().game().isTest()) return "<gold>∞ cr";
            return "<gold>" + economy().credits(p) + " cr";
        }

        @Override
        public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            boolean free = CrossfightPlugin.get().game().isTest();
            int money = free ? Integer.MAX_VALUE : economy().credits(p);
            List<ChoiceMenu.Option> options = new ArrayList<>();
            for (Guns.Gun g : Guns.ALL) {
                boolean ok = money >= g.price();
                List<String> lore = new ArrayList<>();
                lore.add((ok ? "<green>" : "<red>") + g.price() + " credits");
                lore.add("<gray>" + g.category() + " - Puissance " + g.power() + " - chargeur " + g.magazine());
                lore.add("<gray>Cadence " + Guns.cadence(g) + ", rechargement " + g.reloadSeconds() + " s");
                if (g.headshot()) lore.add("<yellow>Degats doubles a la tete");
                options.add(new ChoiceMenu.Option(g.icon(), g.name(), lore, () -> buy(p, g)));
            }
            ChoiceMenu.open(p, "<dark_gray>Boutique <gold>" + (free ? "credits infinis" : money + " cr"), options);
            return true;
        }

        private void buy(Player p, Guns.Gun g) {
            if (!economy().spend(p, g.price())) {
                Msg.actionBar(p, "<red>Pas assez de credits (" + g.price() + " requis, " + economy().credits(p) + " disponibles).");
                return;
            }
            Guns.removeGun(p);
            ItemStack gun = Guns.createItem(g);
            var inv = p.getInventory();
            int slot = -1;
            for (int i = 1; i < 8; i++) if (inv.getItem(i) == null) { slot = i; break; }
            if (slot >= 0) inv.setItem(slot, gun); else inv.addItem(gun);
            Msg.send(p, "<gold>Achat : <white>" + g.name() + " <gray>(" + g.price() + " cr, reste " + (CrossfightPlugin.get().game().isTest() ? "infini" : String.valueOf(economy().credits(p))) + ")");
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
        }
    }

    static final Shop SHOP = new Shop();
}
