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
        Effects.softFallUntil(p, 5000);
        World w = p.getWorld();
        w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1.4f);
        com.kiokouwhite.crossfight.util.Puppet fly = SNK.flight(p);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                try {
                    if (!p.isOnline() || p.isDead() || ally.isDead() || t > 25 || gearSt.get("stop", false) || (t > 3 && p.isSneaking())) { stop(); return; }
                    Vector diff = ally.getLocation().add(0, 1, 0).toVector().subtract(p.getLocation().add(0, 1, 0).toVector());
                    if (diff.length() < 2.0) { p.setVelocity(p.getVelocity().multiply(0.2)); stop(); return; }
                    p.setVelocity(diff.normalize().multiply(2.2).add(new Vector(0, 0.04, 0)));
                    SNK.aim(fly, p, diff);
                    SNK.OdmGear.cable(p, ally.getLocation().add(0, 1, 0));
                    w.spawnParticle(Particle.DUST, p.getLocation().add(0, 0.8, 0), 3, 0.3, 0.2, 0.3, 0, SNK.GAS);
                } catch (Exception ex) { stop(); }
            }
            void stop() { gearSt.setEnabled(false); gearSt.set("stop", false); SNK.land(p, fly); if (p.isOnline()) Effects.softFallUntil(p, 5000); cancel(); }
        }.runTaskTimer(SNK.plugin(), 0L, 1L);
    }

    static final class StyleSoutien extends SNK.OdmGear {
        StyleSoutien() { super(SNK.Style.SOUTIEN, "Style Soutien : quand un allie passe sous 5 coeurs, Armin gagne Force ; quand son grappin attrape un allie (20 blocs), il fonce sur lui (10 s)."); }
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
            if (allies.isEmpty() && com.kiokouwhite.crossfight.util.MannequinPlayer.isProxy(p)) {
                // Mode d'essai a la troisieme personne, sans allie : le geste seul, personne n'est rappele.
                raiseFist(p);
                Msg.actionBar(p, "<blue>Regroupement : aucun allie a rappeler.");
                return true;
            }
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
        /** Le signal du regroupement : Armin leve le poing. */
        static void raiseFist(Player p) {
            com.kiokouwhite.crossfight.util.Puppet pu = com.kiokouwhite.crossfight.util.Puppet.show(p, "armin", null, "debout");
            if (pu == null) return;
            pu.pose("yang_poing_leve", 3);
            pu.endAfter(16, 4);
            p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 2.4, 0), 12, 0.25, 0.2, 0.25, 0, BLUE);
        }

        void bring(Player p, CfPlayer cf, AbilityState st, List<Player> who) {
            st.startCooldown((int) Math.ceil(cooldownSeconds() * cf.cooldownFactor()));
            // La capacite part au choix dans le menu, pas a son ouverture : son bruitage et sa replique aussi.
            com.kiokouwhite.crossfight.util.Sounds.ability(p, id());
            raiseFist(p);
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
        /** Les icones du pack complet pour ces trois effets (a la place des objets de Minecraft, quand le pack les a). */
        static final String[] MODELS = {"bonus_force", "bonus_resistance", "bonus_speed"};
        Strategie() {
            super("armin_strategie", "Strategie", "Menu : Armin donne a un allie Force, Resistance ou Speed. Il peut changer d'allie ou d'effet quand il le souhaite.", Material.MAP, List.of(EFFECTS));
        }
        @Override public void onSwitch(Player p, CfPlayer cf, AbilityState st, int oldMode, int newMode) {}
        @Override protected List<String> extraLore() { return List.of("", "<yellow>Clic droit : choisir l'allie et l'effet"); }
        /** Ouvrir le menu ne dit rien : le bruitage et la replique partent quand l'effet est donne. */
        @Override public String useSoundKey() { return "armin_strategie_menu"; }

        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            List<Player> allies = SNK.allies(p);
            // Mode d'essai a la troisieme personne, sans allie : on choisit l'effet, le geste part dans le vide.
            if (allies.isEmpty() && com.kiokouwhite.crossfight.util.MannequinPlayer.isProxy(p)) { chooseEffect(p, cf, st, null); return true; }
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
                options.add(new ChoiceMenu.Option(ICONS[i], EFFECTS[i], List.of(ally == null ? "<gray>Dans le vide (essai)" : "<gray>Pour " + ally.getName()), () -> apply(p, cf, st, ally, mode), MODELS[mode]));
            }
            ChoiceMenu.open(p, "<blue><bold>Strategie : quel effet ?", options);
        }
        /** La couleur de chaque effet : Force en rouge, Resistance en gris acier, Speed en bleu clair. */
        static final Particle.DustOptions[] TINT = {
                new Particle.DustOptions(org.bukkit.Color.fromRGB(225, 45, 45), 1.1f),
                new Particle.DustOptions(org.bukkit.Color.fromRGB(165, 175, 190), 1.1f),
                new Particle.DustOptions(org.bukkit.Color.fromRGB(110, 215, 255), 1.1f)};
        /** L'aura qui entoure l'allie renforce, par Armin : elle montre a tous qui porte l'effet, et lequel. */
        private static final java.util.Map<UUID, org.bukkit.scheduler.BukkitTask> AURA = new java.util.HashMap<>();
        /** Ticks que mettent les particules a aller de la main d'Armin a son allie. */
        static final int TRAVEL = 8;

        void apply(Player p, CfPlayer cf, AbilityState st, Player ally, int mode) {
            clear(st);
            stopAura(p);
            com.kiokouwhite.crossfight.util.Sounds.ability(p, id());
            if (ally == null) {
                // Essai sans allie : le bras jete droit devant, les particules partent dans le vide.
                Location front = p.getLocation().add(0, 1.1, 0).add(p.getLocation().getDirection().setY(0).normalize().multiply(8));
                send(p, front, mode, null);
                Msg.actionBar(p, "<blue>Strategie : " + EFFECTS[mode] + " (aucun allie)");
                return;
            }
            st.set("ally", ally.getUniqueId().toString());
            st.setMode(mode);
            Boosts.set(ally, "armin_strategie", mode == 0 ? new Boosts.Boost(1, 0, 0, 0) : mode == 1 ? new Boosts.Boost(0, 0, 1, 0) : new Boosts.Boost(0, 1, 0, 0));
            send(p, ally.getLocation().add(0, 1.1, 0), mode, () -> {
                if (!ally.isOnline() || ally.isDead()) return;
                ally.getWorld().spawnParticle(Particle.DUST, ally.getLocation().add(0, 1, 0), 24, 0.4, 0.6, 0.4, 0, TINT[mode]);
                ally.playSound(ally.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
            });
            aura(p, st, ally, mode);
            Msg.send(ally, "<blue>Strategie d'Armin : tu gagnes <white>" + EFFECTS[mode] + "</white>.");
            Msg.actionBar(p, "<blue>Strategie : " + EFFECTS[mode] + " pour " + ally.getName());
        }

        /** Armin jette son bras vers {@code to} ; les particules de l'effet quittent sa main et y vont, puis {@code arrived}. */
        static void send(Player p, Location to, int mode, Runnable arrived) {
            org.bukkit.util.Vector toward = to.toVector().subtract(p.getLocation().toVector());
            com.kiokouwhite.crossfight.util.Puppet pu = com.kiokouwhite.crossfight.util.Puppet.show(p, "armin", null, "debout");
            if (pu != null) {
                if (toward.clone().setY(0).lengthSquared() > 1e-4) pu.face(p.getLocation().setDirection(toward).getYaw());
                pu.pose("arlo_bras_arme", 2);
                pu.endAfter(TRAVEL + 8, 3);
            }
            World w = p.getWorld();
            new BukkitRunnable() {
                int t = 0;
                Location from;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || p.isDead()) { cancel(); return; }
                    if (t == 3) {
                        if (pu != null && com.kiokouwhite.crossfight.util.Puppet.of(p) == pu) pu.pose("arlo_bras_jete", 2);
                        from = pu != null && com.kiokouwhite.crossfight.util.Puppet.of(p) == pu ? pu.hand() : p.getEyeLocation().add(0, -0.3, 0);
                        w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.7f, 1.7f);
                    }
                    if (t < 3) return;
                    // Un paquet de particules qui avance de la main vers l'allie, avec une trainee derriere lui.
                    double f = (t - 2) / (double) TRAVEL;
                    for (int k = 0; k < 4; k++) {
                        double g = Math.max(0, f - k * 0.05);
                        Location at = from.clone().add(to.toVector().subtract(from.toVector()).multiply(g));
                        w.spawnParticle(Particle.DUST, at, k == 0 ? 6 : 2, 0.12, 0.12, 0.12, 0, TINT[mode]);
                    }
                    if (f >= 1) { cancel(); if (arrived != null) arrived.run(); }
                }
            }.runTaskTimer(SNK.plugin(), 0L, 1L);
        }

        /** Tant que l'allie garde l'effet, deux particules de sa couleur tournent autour de lui : ses adversaires voient ce qu'il porte. */
        static void aura(Player p, AbilityState st, Player ally, int mode) {
            String id = ally.getUniqueId().toString();
            AURA.put(p.getUniqueId(), new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t += 2;
                    if (!p.isOnline() || !ally.isOnline() || !id.equals(st.get("ally", "")) || st.mode() != mode) { cancel(); return; }
                    if (ally.isDead()) return;
                    Location c = ally.getLocation();
                    for (int k = 0; k < 2; k++) {
                        double a = t * 0.22 + k * Math.PI, y = 0.35 + 0.75 * (0.5 + 0.5 * Math.sin(t * 0.07 + k * Math.PI));
                        c.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 0.62, y, Math.sin(a) * 0.62), 1, 0, 0, 0, 0, TINT[mode]);
                    }
                }
            }.runTaskTimer(SNK.plugin(), TRAVEL + 2L, 2L));
        }

        static void stopAura(Player p) {
            org.bukkit.scheduler.BukkitTask old = AURA.remove(p.getUniqueId());
            if (old != null) old.cancel();
        }

        static void clear(AbilityState st) {
            String prev = st.get("ally", null);
            if (prev == null) return;
            Player old = com.kiokouwhite.crossfight.util.MannequinPlayer.getPlayer(UUID.fromString(prev));
            if (old != null) Boosts.remove(old, "armin_strategie");
            st.remove("ally");
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            String a = st.get("ally", null);
            Player ally = a == null ? null : com.kiokouwhite.crossfight.util.MannequinPlayer.getPlayer(UUID.fromString(a));
            return ally == null ? "<gray>Strategie <dark_gray>[aucune]" : "<yellow>Strategie <white>[" + EFFECTS[st.mode()] + " : " + ally.getName() + "]";
        }
        @Override public void onUnequip(Player p, CfPlayer cf) { clear(SNK.plugin().abilities().state(p, this)); stopAura(p); }
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
            signal(attacker);
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
                    new ChoiceMenu.Option(Material.SUGAR, "Speed II", List.of("<gray>Pour toute l'equipe, 1 minute"), () -> apply(p, new Boosts.Boost(0, 2, 0, 0), "Speed II", 2), "bonus_speed"),
                    new ChoiceMenu.Option(Material.SHIELD, "Resistance", List.of("<gray>Pour toute l'equipe, 1 minute"), () -> apply(p, new Boosts.Boost(0, 0, 1, 0), "Resistance", 1), "bonus_resistance"),
                    new ChoiceMenu.Option(Material.BLAZE_POWDER, "Force", List.of("<gray>Pour toute l'equipe, 1 minute"), () -> apply(p, new Boosts.Boost(1, 0, 0, 0), "Force", 0), "bonus_force"));
            ChoiceMenu.openWheel(p, "<gold><bold>Abnegation : quel bonus ?", options, null);
            return true;
        }
/** Ticks de la lumiere qui descend du ciel sur Armin, avant l'explosion lumineuse. */
        static final int BEAM = 14;

        /**
         * Un genou a terre, le poing sur le coeur : la lumiere descend du ciel sur lui, eclate, et les particules de
         * l'effet choisi partent vers chaque allie, qu'elles entourent jusqu'a la fin de l'ultime.
         */
        void apply(Player p, Boosts.Boost boost, String label, int mode) {
            List<Player> team = new ArrayList<>(SNK.allies(p));
            team.add(p);
            String source = "armin_abnegation_" + System.currentTimeMillis();
            showFor(p, 1200);
            World w = p.getWorld();
            com.kiokouwhite.crossfight.util.Puppet pu = com.kiokouwhite.crossfight.util.Puppet.show(p, "armin", null, "debout");
            if (pu != null) { pu.pose("armin_genou", 4); pu.endAfter(BEAM + 12, 4); }
            w.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.5f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || p.isDead()) { cancel(); return; }
                    Location c = p.getLocation();
                    if (t <= BEAM) {
                        // Le rayon : une colonne de lumiere dont le bas descend de 14 blocs jusqu'a lui.
                        double bottom = 14.0 * (1 - t / (double) BEAM);
                        for (double y = bottom; y <= 14; y += 0.45) w.spawnParticle(Particle.END_ROD, c.clone().add(0, y + 0.4, 0), 1, 0.12, 0, 0.12, 0);
                        if (t % 4 == 0) w.playSound(c, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.8f + t * 0.06f);
                        return;
                    }
                    // L'explosion lumineuse : il releve la tete, la lumiere eclate dans toutes les directions.
                    cancel();
                    if (pu != null && com.kiokouwhite.crossfight.util.Puppet.of(p) == pu) pu.pose("armin_genou_leve", 3);
                    w.spawnParticle(Particle.END_ROD, c.clone().add(0, 1, 0), 120, 0.2, 0.3, 0.2, 0.35);
                    w.spawnParticle(Particle.FIREWORK, c.clone().add(0, 1, 0), 60, 0.3, 0.4, 0.3, 0.25);
                    w.spawnParticle(Particle.EXPLOSION, c.clone().add(0, 1, 0), 1);
                    w.playSound(c, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1.3f);
                    w.playSound(c, Sound.ITEM_TOTEM_USE, 0.7f, 1.4f);
                    Location from = c.clone().add(0, 1.1, 0);
                    for (Player m : team) {
                        if (!m.isOnline() || m.isDead()) continue;
                        Boosts.set(m, source, boost);
                        Msg.send(m, "<gold>Abnegation d'Armin : <white>" + label + "</white> pendant 1 minute !");
                        if (m == p) { surround(m, mode, source); continue; }
                        fly(from, m, mode, () -> { m.playSound(m.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f); surround(m, mode, source); });
                    }
                }
            }.runTaskTimer(SNK.plugin(), 0L, 1L);
            Bukkit.getScheduler().runTaskLater(SNK.plugin(), () -> { for (Player m : team) Boosts.remove(m, source); }, BEAM + 20L * 60);
        }

        /** Les particules de l'effet vont du point de l'explosion jusqu'a un allie (elles le suivent s'il bouge). */
        static void fly(Location from, Player to, int mode, Runnable arrived) {
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!to.isOnline() || to.isDead() || !to.getWorld().equals(from.getWorld())) { cancel(); return; }
                    Location goal = to.getLocation().add(0, 1.1, 0);
                    double f = t / (double) Strategie.TRAVEL;
                    for (int k = 0; k < 4; k++) {
                        double g = Math.max(0, f - k * 0.05);
                        from.getWorld().spawnParticle(Particle.DUST, from.clone().add(goal.toVector().subtract(from.toVector()).multiply(g)), k == 0 ? 6 : 2, 0.12, 0.12, 0.12, 0, Strategie.TINT[mode]);
                    }
                    if (f >= 1) { cancel(); arrived.run(); }
                }
            }.runTaskTimer(SNK.plugin(), 0L, 1L);
        }

        /** L'aura de l'effet autour d'un membre de l'equipe, tant qu'il le garde (une minute). Les effets cumules tournent a des hauteurs differentes. */
        static void surround(Player m, int mode, String source) {
            m.getWorld().spawnParticle(Particle.DUST, m.getLocation().add(0, 1, 0), 24, 0.4, 0.6, 0.4, 0, Strategie.TINT[mode]);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t += 2;
                    if (!m.isOnline() || t > 1200 || Boosts.get(m, source) == null) { cancel(); return; }
                    if (m.isDead()) return;
                    Location c = m.getLocation();
                    for (int k = 0; k < 2; k++) {
                        double a = -t * 0.2 + k * Math.PI + mode * 2.1;
                        c.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 0.75, 0.3 + mode * 0.55, Math.sin(a) * 0.75), 1, 0, 0, 0, 0, Strategie.TINT[mode]);
                    }
                }
            }.runTaskTimer(SNK.plugin(), 2L, 2L);
        }
    }
}
