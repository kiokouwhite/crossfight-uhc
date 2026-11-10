package com.kiokouwhite.crossfight.character.snk;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.SwitchAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.ChoiceMenu;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Armin Arlert - Path of Harmony.
 */
public final class Armin extends Character {

    static final Particle.DustOptions BLUE = new Particle.DustOptions(Color.fromRGB(90, 160, 255), 1.1f);

    public Armin() {
        super("armin", "Armin Arlert", Voie.HARMONIE, Material.IRON_SWORD);
        addAbility(new StyleSoutien());
        addAbility(new RegroupementMilitaire());
        addAbility(new Strategie());
        addAbility(new AnnulationTactique());
        setUltimate(new Abnegation());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.IRON_SWORD, 8.5, "<gold><bold>Lames (Style Soutien)", List.of("<gray>Equipement tridimensionnel d'Armin."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Grappin Style Soutien : fonce sur l'allie attrape, Force si un allie est bas",
                "<white>Regroupement Militaire : teleporte un ou tous les allies",
                "<white>Strategie : donne Force, Resistance ou Speed a un allie",
                "<white>Annulation Tactique : tous les 5 coups, annule les capacites 5 s",
                "<light_purple>Ultime : Speed II, Resistance ou Force pour toute l'equipe 1 min");
    }

    // -------------------------------------------------------- style Soutien

    static boolean soutienReady(Player p, AbilityState gearSt) { return Eren.cdReady(gearSt, "soutienAt"); }

    /** Armin fonce sur l'allie attrape a une vitesse ahurissante. */
    static void soutienDash(Player p, AbilityState gearSt, LivingEntity ally) {
        Eren.startCd(p, gearSt, "soutienAt", 10);
        gearSt.setEnabled(true);
        Effects.noFallUntil(p, 5000);
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1.4f);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                try {
                    if (!p.isOnline() || p.isDead() || ally.isDead() || t > 25 || gearSt.get("stop", false)) { stop(); return; }
                    Vector diff = ally.getLocation().add(0, 1, 0).toVector().subtract(p.getLocation().add(0, 1, 0).toVector());
                    if (diff.length() < 2.0) { p.setVelocity(p.getVelocity().multiply(0.2)); stop(); return; }
                    p.setVelocity(diff.normalize().multiply(2.2).add(new Vector(0, 0.04, 0)));
                    SNK.OdmGear.cable(p, ally.getLocation().add(0, 1, 0));
                    w.spawnParticle(Particle.DUST, p.getLocation().add(0, 0.8, 0), 3, 0.3, 0.2, 0.3, 0, SNK.GAS);
                } catch (Exception ex) { stop(); }
            }
            void stop() { gearSt.setEnabled(false); gearSt.set("stop", false); if (p.isOnline()) Effects.noFallUntil(p, 5000); cancel(); }
        }.runTaskTimer(SNK.plugin(), 0L, 1L);
    }

    static final class StyleSoutien extends SNK.OdmGear {
        StyleSoutien() { super(SNK.Style.SOUTIEN, "Style Soutien : si un allie a moins de 5 coeurs, Armin gagne Force ; s'il attrape un allie a 20 blocs, il fonce sur lui (10 s)."); }
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) {
            boolean low = false;
            for (Player a : SNK.allies(p)) if (SNK.isLow(a)) { low = true; break; }
            SNK.boostIf(p, "armin_force", low, new Boosts.Boost(1, 0, 0, 0));
        }
        @Override public void onUnequip(Player p, CfPlayer cf) { Boosts.remove(p, "armin_force"); }
    }

    // ---------------------------------------------------------------- pouvoirs

    static final class RegroupementMilitaire extends ActiveAbility {
        RegroupementMilitaire() {
            super("armin_regroupement", "Regroupement Militaire", "Menu : Armin teleporte un allie au choix, ou tous ses allies, a cote de lui.", Material.RECOVERY_COMPASS, 5);
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            List<Player> allies = SNK.allies(p);
            if (allies.isEmpty()) { Msg.actionBar(p, "<red>Aucun allie en vie."); return false; }
            List<ChoiceMenu.Option> options = new ArrayList<>();
            for (Player a : allies) {
                options.add(new ChoiceMenu.Option(Material.PLAYER_HEAD, a.getName(), List.of("<gray>A " + (int) (a.getWorld() == p.getWorld() ? a.getLocation().distance(p.getLocation()) : 0) + " blocs"),
                        () -> bring(p, cf, st, List.of(a))));
            }
            if (allies.size() > 1) options.add(new ChoiceMenu.Option(Material.BEACON, "Tous les allies", List.of("<gray>Teleporte toute l'equipe"), () -> bring(p, cf, st, allies)));
            ChoiceMenu.open(p, "<blue><bold>Regroupement Militaire", options);
            return false;
        }
        void bring(Player p, CfPlayer cf, AbilityState st, List<Player> who) {
            st.startCooldown((int) Math.ceil(cooldownSeconds() * cf.cooldownFactor()));
            World w = p.getWorld();
            int i = 0;
            for (Player a : who) {
                if (!a.isOnline() || a.isDead()) continue;
                double ang = i++ * 2.1;
                Location to = p.getLocation().clone().add(Math.cos(ang) * 1.5, 0, Math.sin(ang) * 1.5);
                if (!to.getBlock().isPassable()) to = p.getLocation().clone();
                to.setDirection(p.getLocation().getDirection());
                a.getWorld().spawnParticle(Particle.DUST, a.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0, BLUE);
                a.teleport(to);
                w.spawnParticle(Particle.DUST, to.clone().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0, BLUE);
                Msg.send(a, "<blue>Armin te regroupe a ses cotes.");
            }
            w.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
            Msg.actionBar(p, "<blue>Regroupement : " + i + " allie(s) teleporte(s).");
        }
    }

    /** Intervertible : Armin choisit un allie et lui donne Force, Resistance ou Speed ; il peut changer quand il veut. */
    static final class Strategie extends SwitchAbility implements CustomUse {
        static final String[] EFFECTS = {"Force", "Resistance", "Speed"};
        static final Material[] ICONS = {Material.BLAZE_POWDER, Material.SHIELD, Material.SUGAR};
        Strategie() {
            super("armin_strategie", "Strategie", "Menu : Armin donne a un allie Force, Resistance ou Speed. Il peut changer d'allie ou d'effet quand il le souhaite.", Material.MAP, List.of(EFFECTS));
        }
        @Override public void onSwitch(Player p, CfPlayer cf, AbilityState st, int oldMode, int newMode) {}
        @Override protected List<String> extraLore() { return List.of("", "<yellow>Clic droit : choisir l'allie et l'effet"); }

        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            List<Player> allies = SNK.allies(p);
            if (allies.isEmpty()) { Msg.actionBar(p, "<red>Aucun allie en vie."); return true; }
            List<ChoiceMenu.Option> options = new ArrayList<>();
            for (Player a : allies) {
                boolean current = a.getUniqueId().toString().equals(st.get("ally", ""));
                options.add(new ChoiceMenu.Option(Material.PLAYER_HEAD, a.getName(), current ? List.of("<yellow>Actuellement : " + EFFECTS[st.mode()]) : List.of(), () -> chooseEffect(p, cf, st, a)));
            }
            ChoiceMenu.open(p, "<blue><bold>Strategie : quel allie ?", options);
            return true;
        }
        void chooseEffect(Player p, CfPlayer cf, AbilityState st, Player ally) {
            List<ChoiceMenu.Option> options = new ArrayList<>();
            for (int i = 0; i < EFFECTS.length; i++) {
                final int mode = i;
                options.add(new ChoiceMenu.Option(ICONS[i], EFFECTS[i], List.of("<gray>Pour " + ally.getName()), () -> apply(p, cf, st, ally, mode)));
            }
            ChoiceMenu.open(p, "<blue><bold>Strategie : quel effet ?", options);
        }
        void apply(Player p, CfPlayer cf, AbilityState st, Player ally, int mode) {
            clear(st);
            st.set("ally", ally.getUniqueId().toString());
            st.setMode(mode);
            Boosts.set(ally, "armin_strategie", mode == 0 ? new Boosts.Boost(1, 0, 0, 0) : mode == 1 ? new Boosts.Boost(0, 0, 1, 0) : new Boosts.Boost(0, 1, 0, 0));
            ally.getWorld().spawnParticle(Particle.DUST, ally.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0, BLUE);
            ally.playSound(ally.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
            Msg.send(ally, "<blue>Strategie d'Armin : tu gagnes <white>" + EFFECTS[mode] + "</white>.");
            Msg.actionBar(p, "<blue>Strategie : " + EFFECTS[mode] + " pour " + ally.getName());
        }
        static void clear(AbilityState st) {
            String prev = st.get("ally", null);
            if (prev == null) return;
            Player old = Bukkit.getPlayer(UUID.fromString(prev));
            if (old != null) Boosts.remove(old, "armin_strategie");
            st.remove("ally");
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            String a = st.get("ally", null);
            Player ally = a == null ? null : Bukkit.getPlayer(UUID.fromString(a));
            return ally == null ? "<gray>Strategie <dark_gray>[aucune]" : "<yellow>Strategie <white>[" + EFFECTS[st.mode()] + " : " + ally.getName() + "]";
        }
        @Override public void onUnequip(Player p, CfPlayer cf) { clear(SNK.plugin().abilities().state(p, this)); }
    }

    static final class AnnulationTactique extends PassiveAbility {
        AnnulationTactique() {
            super("armin_annulation", "Annulation Tactique", "Tous les 5 coups infliges, Armin annule toutes les capacites de l'adversaire touche pendant 5 secondes.");
        }
        @Override public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) {
            if (e.getCause() != org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
            int hits = st.get("hits", 0) + 1;
            if (hits < 5) { st.set("hits", hits); return; }
            st.set("hits", 0);
            World w = victim.getWorld();
            w.spawnParticle(Particle.DUST, victim.getLocation().add(0, 1.2, 0), 30, 0.4, 0.6, 0.4, 0, BLUE);
            w.spawnParticle(Particle.ENCHANTED_HIT, victim.getLocation().add(0, 1.2, 0), 15, 0.3, 0.5, 0.3, 0.1);
            w.playSound(victim.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.8f);
            if (victim instanceof Player v) {
                SNK.plugin().players().get(v).silence(5000L);
                Msg.actionBar(v, "<red>Annulation Tactique : capacites annulees 5 s !");
            }
            Msg.actionBar(attacker, "<blue>Annulation Tactique : capacites de l'adversaire annulees 5 s.");
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            return "<blue>Annulation <white>" + st.get("hits", 0) + "/5";
        }
    }

    // ----------------------------------------------------------------- ultime

    static final class Abnegation extends UltimateAbility {
        Abnegation() {
            super("armin_ult", "Abnegation", "Armin renforce toute l'equipe pendant 1 minute : Speed II, Resistance ou Force, au choix (cumulable).", Material.GOLDEN_APPLE);
        }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            List<ChoiceMenu.Option> options = List.of(
                    new ChoiceMenu.Option(Material.SUGAR, "Speed II", List.of("<gray>Pour toute l'equipe, 1 minute"), () -> apply(p, new Boosts.Boost(0, 2, 0, 0), "Speed II")),
                    new ChoiceMenu.Option(Material.SHIELD, "Resistance", List.of("<gray>Pour toute l'equipe, 1 minute"), () -> apply(p, new Boosts.Boost(0, 0, 1, 0), "Resistance")),
                    new ChoiceMenu.Option(Material.BLAZE_POWDER, "Force", List.of("<gray>Pour toute l'equipe, 1 minute"), () -> apply(p, new Boosts.Boost(1, 0, 0, 0), "Force")));
            ChoiceMenu.open(p, "<gold><bold>Abnegation : quel bonus ?", options);
            return true;
        }
        void apply(Player p, Boosts.Boost boost, String label) {
            List<Player> team = new ArrayList<>(SNK.allies(p));
            team.add(p);
            String source = "armin_abnegation_" + System.currentTimeMillis();
            for (Player m : team) {
                Boosts.set(m, source, boost);
                m.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, m.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.2);
                m.playSound(m.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.4f);
                Msg.send(m, "<gold>Abnegation d'Armin : <white>" + label + "</white> pendant 1 minute !");
            }
            Bukkit.getScheduler().runTaskLater(SNK.plugin(), () -> { for (Player m : team) Boosts.remove(m, source); }, 20L * 60);
        }
    }
}
