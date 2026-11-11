package com.kiokouwhite.crossfight.character.solo;

import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.Borrowed;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;

/**
 * Shoja - Voie de la Destruction. Une epee qui copie, et un arbre de 59 capacites a debloquer.
 */
public final class Shoja extends Character implements Listener, Solo.Burst {

    public Shoja() {
        super("shoja", "Shoja", Voie.DESTRUCTION, Material.ECHO_SHARD);
        addAbility(new EpeeDeLEgo().bindToWeapon());
        addAbility(new Arbre());
        addAbility(Solo.ITEM);
        setUltimate(new CouronneAraignee());
    }

    @Override public ItemStack createWeapon() { return Weapons.melee(Material.IRON_SWORD, 8.5, "<dark_red><bold>Epee de l'Ego", List.of("<gray>8.5 dmg.", "<gray>Shoja peut crafter, enchanter et reparer des armes.")); }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Epee de l'Ego : copie les enchantements de l'arme de son premier tue",
                "<white>Arbre des capacites : 59 capacites en 6 elements, 3 equipees au plus",
                "<white>Debloquees par des objectifs (crafts, degats, immobilisations...)",
                "<light_purple>Ultime : Couronne Angelique (vol et regen) ou Araignee Malefique (Force IV) selon sa vie",
                "<gold>Burst : Dieu du monde (+1 vie) ou Dieu des enfers (Force IV definitive) selon sa vie");
    }

    // ----------------------------------------------------------- Epee de l'Ego

    static final class EpeeDeLEgo extends PassiveAbility {
        EpeeDeLEgo() { super("shoja_ego", "Epee de l'Ego", "Le premier ennemi tue par Shoja lui legue les enchantements de l'arme qu'il tenait : ils sont copies sur son epee.", Material.ECHO_SHARD); }
        @Override public List<String> weaponLore() { return List.of("<dark_red>Epee de l'Ego : <gray>copie les enchantements de l'arme du premier tue"); }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { return st.get("copied", false) ? "<dark_red>Ego <green>✔" : "<dark_red>Ego <gray>en attente"; }
    }

    /** Copie les enchantements de l'arme tenue par la victime sur l'epee de Shoja (une seule fois). */
    static void egoCopy(Player killer, LivingEntity victim) {
        CfPlayer cf = Solo.plugin().players().get(killer);
        if (cf.character() == null) return;
        var ego = cf.character().ability("shoja_ego");
        if (ego == null) return;
        AbilityState st = Solo.plugin().abilities().state(killer, ego);
        if (st.get("copied", false)) return;
        var eq = victim.getEquipment();
        ItemStack held = eq == null ? null : eq.getItemInMainHand();
        Map<Enchantment, Integer> ench = held == null ? Map.of() : held.getEnchantments();
        st.set("copied", true);
        if (ench.isEmpty()) { Msg.send(killer, "<dark_red>Epee de l'Ego : <gray>l'arme de " + ShojaTree.safeName(victim) + " n'avait aucun enchantement."); return; }
        var inv = killer.getInventory();
        int n = 0;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || !"shoja".equals(Items.tag(it, AbilityEngine.WEAPON_KEY))) continue;
            for (var en : ench.entrySet()) if (it.getEnchantmentLevel(en.getKey()) < en.getValue()) { it.addUnsafeEnchantment(en.getKey(), en.getValue()); n++; }
            inv.setItem(i, it);
        }
        killer.getWorld().spawnParticle(Particle.ENCHANT, killer.getLocation().add(0, 1.5, 0), 60, 0.5, 0.8, 0.5, 1);
        killer.playSound(killer.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 0.8f);
        com.kiokouwhite.crossfight.util.Sounds.voiceLater(killer, "shoja_ego", 30L);
        Msg.send(killer, "<dark_red><bold>Epee de l'Ego :</bold> <white>" + n + " enchantement(s) de l'arme de " + ShojaTree.safeName(victim) + " copie(s) sur ton epee.");
    }

    // --------------------------------------------------------------- l'arbre

    /** L'item de menu de l'arbre ; il tient aussi l'horloge de Shoja (historique de vie, mines, nage). */
    static final class Arbre extends ActiveAbility implements CustomUse {
        Arbre() { super("shoja_arbre", "Arbre des capacites", "Ouvre l'arbre : 59 capacites en six elements (Feu, Eau, Foudre, Vent, Espace, Temps), chacune debloquee par un objectif. Trois equipees au plus, d'elements differents.", Material.OAK_SAPLING, 0); }
        @Override protected List<String> extraLore() { return List.of("", "<yellow>Clic droit : ouvrir l'arbre"); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) { ShojaTree.openMenu(p); return true; }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            StringBuilder sb = new StringBuilder("<green>Arbre <gray>");
            List<String> eq = ShojaTree.equipped(p);
            if (eq.isEmpty()) sb.append("(vide)");
            for (String id : eq) { var n = ShojaTree.NODES.get(id); if (n != null) sb.append("<").append(n.element.color).append(">").append(n.element.label.charAt(0)).append(" "); }
            return sb.toString().trim();
        }
        @Override public void onEquip(Player p, CfPlayer cf) { Bukkit.getScheduler().runTask(Solo.plugin(), () -> { Borrowed.regive(p); ShojaTree.regive(p); }); }
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) { ShojaTree.tickShoja(p); }
        @Override public void onUnequip(Player p, CfPlayer cf) { Boosts.remove(p, "shoja_absorption"); Boosts.remove(p, "shoja_eventail"); Boosts.remove(p, "shoja_espace"); }
    }

    // ---------------------------------------------------------------- ultime

    static final class CouronneAraignee extends UltimateAbility {
        CouronneAraignee() { super("shoja_ult", "Couronne Angelique | Araignee Malefique", "Plus de 5 coeurs : Couronne Angelique, Shoja vole et regenere pendant 20 secondes. 5 coeurs ou moins : Araignee Malefique, Vitesse II et Force IV pendant 20 secondes, mais elle perd 1 coeur toutes les 2 secondes.", Material.ECHO_SHARD); }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            com.kiokouwhite.crossfight.util.Sounds.voice(p, p.getHealth() > 10.0 ? "shoja_ult_couronne" : "shoja_ult_araignee");
            if (p.getHealth() > 10.0) {
                p.setAllowFlight(true); p.setFlying(true);
                Effects.give(p, PotionEffectType.REGENERATION, 1, 400);
                Effects.noFallUntil(p, 25000);
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        t++;
                        if (t > 20 || !p.isOnline()) { if (p.isOnline() && p.getGameMode() == org.bukkit.GameMode.SURVIVAL) { p.setFlying(false); p.setAllowFlight(false); } cancel(); return; }
                        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 2.3, 0), 12, 0.4, 0.05, 0.4, 0);
                        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 10, 0.5, 0.8, 0.5, 0, new Particle.DustOptions(Color.fromRGB(255, 240, 180), 1.0f));
                    }
                }.runTaskTimer(Solo.plugin(), 0L, 20L);
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.8f);
                Msg.actionBar(p, "<gold>Couronne Angelique : vol et regeneration 20 s.");
            } else {
                Boosts.set(p, "shoja_araignee", new Boosts.Boost(4, 2, 0, 0));
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        t++;
                        if (t > 10 || !p.isOnline() || p.isDead()) { Boosts.remove(p, "shoja_araignee"); cancel(); return; }
                        if (p.getHealth() > 2.0) { p.setHealth(p.getHealth() - 2.0); p.damage(0.01); }
                        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 20, 0.5, 0.8, 0.5, 0, new Particle.DustOptions(Color.fromRGB(60, 0, 80), 1.3f));
                    }
                }.runTaskTimer(Solo.plugin(), 40L, 40L);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1.5f, 0.5f);
                Msg.actionBar(p, "<dark_purple>Araignee Malefique : Force IV et Vitesse II 20 s, -1 coeur / 2 s.");
            }
            return true;
        }
        @Override public void onUnequip(Player p, CfPlayer cf) { Boosts.remove(p, "shoja_araignee"); if (p.getGameMode() == org.bukkit.GameMode.SURVIVAL) { p.setFlying(false); p.setAllowFlight(false); } }
    }

    // ----------------------------------------------------------------- burst

    @Override public String burstName() { return "Dieu du monde | Dieu des enfers"; }
    @Override public String burstDescription() { return "Plus de 5 coeurs : Dieu du monde, Shoja reste immobile 10 secondes puis gagne une vie. 5 coeurs ou moins : Dieu des enfers, elle perd definitivement 4 coeurs et a Force IV jusqu'a sa mort."; }
    @Override public boolean burst(Player p, CfPlayer cf, AbilityState st) {
        if (p.getHealth() > 10.0) {
            Effects.root(p, 200);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || p.isDead()) { cancel(); return; }
                    if (t > 10) { cf.addLife(1); Msg.send(p, "<gold><bold>Dieu du monde :</bold> <white>tu gagnes une vie (" + cf.lives() + ")."); p.getWorld().playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1.2f); p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 80, 0.5, 1, 0.5, 0.3); cancel(); return; }
                    p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 15, 0.6, 1, 0.6, 0.02);
                    Msg.actionBar(p, "<gold>Dieu du monde : " + (10 - t + 1) + " s");
                }
            }.runTaskTimer(Solo.plugin(), 0L, 20L);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.8f);
        } else {
            Yuki.maxHealthDelta(p, -8, "shoja_enfers");
            Boosts.set(p, "shoja_enfers", new Boosts.Boost(4, 0, 0, 0));
            p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().add(0, 1, 0), 80, 0.6, 1, 0.6, 0.05);
            p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 60, 0.6, 1, 0.6, 0, new Particle.DustOptions(Color.fromRGB(120, 0, 0), 1.5f));
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f);
            Msg.send(p, "<dark_red><bold>Dieu des enfers :</bold> <white>-4 coeurs definitifs, Force IV jusqu'a ta mort.");
        }
        return true;
    }
}
