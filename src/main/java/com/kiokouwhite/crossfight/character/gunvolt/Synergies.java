package com.kiokouwhite.crossfight.character.gunvolt;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.character.duo.Synergy;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Les six Synergies d'Armed Blue Gunvolt.
 */
final class Synergies {

    private Synergies() {}

    /** Coups qui immobilisent (Lame Electrisante) : joueur -> expiration. */
    static final Map<UUID, Long> ELECTRIC_BLADE = new HashMap<>();

    static void register() {
        Synergy.register("gunvolt", "acura", new Synergy.Effect() {
            public String name() { return "Vol Electrique"; }
            public String description() { return "Pendant 15 secondes, les deux volent et portent une barriere electrique de 3x3 (1 coeur par seconde)."; }
            public boolean cast(Player a, LivingEntity b) {
                for (LivingEntity e : new LivingEntity[]{a, b}) {
                    // La barriere suit chacune des deux ; seul un joueur peut voler (un mannequin duo garde les pieds au sol).
                    Player side = Effects.sideOf(e);
                    if (side != null) ABG.zone(side, () -> e.isValid() ? e.getLocation() : null, 1.5, 15, 2.0, -1);
                    if (!(e instanceof Player p)) continue;
                    p.setAllowFlight(true);
                    p.setFlying(true);
                    Effects.noFallUntil(p, 20000);
                    Msg.send(p, "<aqua>Vol Electrique : tu voles pendant 15 secondes !");
                    new BukkitRunnable() {
                        @Override public void run() {
                            if (!p.isOnline()) return;
                            if (p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE) { p.setFlying(false); p.setAllowFlight(false); }
                            Effects.noFallUntil(p, 6000);
                            Msg.actionBar(p, "<gray>Vol Electrique termine.");
                        }
                    }.runTaskLater(ABG.plugin(), 20L * 15);
                }
                return true;
            }
        });

        Synergy.register("gunvolt", "kirin", new Synergy.Effect() {
            public String name() { return "Lame Electrisante"; }
            public String description() { return "Pendant 10 secondes, chacun de vos coups immobilise l'adversaire pendant 10 secondes."; }
            public boolean cast(Player a, LivingEntity b) {
                for (LivingEntity p : new LivingEntity[]{a, b}) {
                    ELECTRIC_BLADE.put(p.getUniqueId(), System.currentTimeMillis() + 10000L);
                    p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 40, 0.5, 0.7, 0.5, 0.15);
                    Msg.send(p, "<aqua>Lame Electrisante : tes coups immobilisent 10 s pendant 10 s !");
                }
                return true;
            }
        });

        Synergy.register("gunvolt", "asimov", new Synergy.Effect() {
            public String name() { return "Double Electrocution"; }
            public String description() { return "Pendant 10 secondes, une zone electrique de 20x20 : 1 coeur par seconde."; }
            public boolean cast(Player a, LivingEntity b) {
                Location c = a.getLocation().clone();
                a.getWorld().strikeLightningEffect(c);
                ABG.zone(a, () -> c, 10, 10, 2.0, 0);
                return true;
            }
        });

        Synergy.register("acura", "kirin", new Synergy.Effect() {
            public String name() { return "Dash Double Lame"; }
            public String description() { return "Chacun a trois dashs de 10 blocs qui infligent 3 coeurs (clic droit sur la Synergie, 30 secondes)."; }
            public long activeMillis() { return 30000L; }
            public boolean cast(Player a, LivingEntity b) {
                // Les dashs se lancent avec l'item de Synergie : seuls les joueurs en recoivent.
                for (LivingEntity e : new LivingEntity[]{a, b}) {
                    if (!(e instanceof Player p)) continue;
                    ABG.plugin().abilities().state(p, Synergy.ITEM).set("dashes", 3);
                    Msg.send(p, "<aqua>Dash Double Lame : 3 dashs, clic droit sur la Synergie !");
                }
                return true;
            }
            public boolean onUse(Player p, LivingEntity partner, AbilityState st, boolean sneaking) {
                int left = st.get("dashes", 0);
                if (left <= 0) { Msg.actionBar(p, "<red>Plus de dash."); return true; }
                st.set("dashes", left - 1);
                Vector dir = p.getLocation().getDirection().setY(0);
                if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
                dir.normalize();
                World w = p.getWorld();
                Location from = p.getLocation();
                Set<UUID> hit = new HashSet<>();
                final Vector d = dir;
                new BukkitRunnable() {
                    int i = 0; Location cur = from.clone();
                    @Override public void run() {
                        i++;
                        if (!p.isOnline() || p.isDead() || i > 5) { cancel(); return; }
                        Location next = cur.clone().add(d.clone().multiply(2));
                        if (!next.getBlock().isPassable() || !next.clone().add(0, 1, 0).getBlock().isPassable()) { cancel(); return; }
                        for (double k = 0; k < 2; k += 0.5) {
                            Location l = cur.clone().add(d.clone().multiply(k)).add(0, 1, 0);
                            w.spawnParticle(Particle.SWEEP_ATTACK, l, 1);
                            for (LivingEntity e : Effects.enemiesWithin(p, l, 1.5)) if (hit.add(e.getUniqueId())) { e.setNoDamageTicks(0); Effects.damage(e, 6.0, p); }
                        }
                        next.setDirection(p.getLocation().getDirection());
                        p.teleport(next); cur = next;
                    }
                }.runTaskTimer(ABG.plugin(), 0L, 1L);
                w.playSound(from, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.2f);
                Msg.actionBar(p, "<aqua>Dash Double Lame : " + (left - 1) + " restant(s)");
                return true;
            }
        });

        Synergy.register("acura", "asimov", new Synergy.Effect() {
            public String name() { return "Prison de Chaine"; }
            public String description() { return "Pendant 10 secondes, tous les ennemis a 30 blocs sont emprisonnes dans des chaines (1 coeur toutes les 2 secondes) ; les deux volent."; }
            public boolean cast(Player a, LivingEntity b) {
                List<LivingEntity> prisoners = new ArrayList<>(Effects.enemiesWithin(a, 30));
                World w = a.getWorld();
                Particle.DustOptions chain = new Particle.DustOptions(Color.fromRGB(190, 190, 200), 0.9f);
                for (LivingEntity e : new LivingEntity[]{a, b}) {
                    if (!(e instanceof Player p)) continue;
                    p.setAllowFlight(true); p.setFlying(true); Effects.noFallUntil(p, 15000);
                    Msg.send(p, "<aqua>Prison de Chaine : " + prisoners.size() + " ennemi(s) enchaine(s), tu voles 10 s.");
                }
                w.playSound(a.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 0.5f);
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        t++;
                        if (t > 200) {
                            for (LivingEntity e : new LivingEntity[]{a, b}) if (e instanceof Player p && p.isOnline() && (p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE)) { p.setFlying(false); p.setAllowFlight(false); Effects.noFallUntil(p, 6000); }
                            for (LivingEntity e : prisoners) Effects.unroot(e);
                            cancel(); return;
                        }
                        for (LivingEntity e : prisoners) {
                            if (e.isDead()) continue;
                            if (t % 10 == 0) Effects.root(e, 15);
                            Location c = e.getLocation().add(0, 1, 0);
                            for (int i = 0; i < 4; i++) { double ang = t * 0.2 + i * Math.PI / 2; w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(ang) * 0.8, Math.sin(t * 0.15 + i) * 0.6, Math.sin(ang) * 0.8), 1, 0, 0, 0, 0, chain); }
                            if (t % 40 == 0) { e.setNoDamageTicks(0); Effects.damage(e, 2.0, a); w.playSound(c, Sound.BLOCK_CHAIN_HIT, 0.8f, 1.2f); }
                        }
                    }
                }.runTaskTimer(ABG.plugin(), 0L, 1L);
                return true;
            }
        });

        Synergy.register("kirin", "asimov", new Synergy.Effect() {
            public String name() { return "Talisman Electrifiant"; }
            public String description() { return "Pendant 10 secondes, les deux jettent des talismans (clic droit sur la Synergie) : toucher quelqu'un vous teleporte sur lui et l'immobilise 5 secondes."; }
            public long activeMillis() { return 10000L; }
            public boolean cast(Player a, LivingEntity b) {
                for (LivingEntity p : new LivingEntity[]{a, b}) Msg.send(p, "<aqua>Talisman Electrifiant : clic droit sur la Synergie pour jeter un talisman (10 s).");
                return true;
            }
            public boolean onUse(Player p, LivingEntity partner, AbilityState st, boolean sneaking) {
                Kirin.throwTalisman(p, 0L, true);
                return true;
            }
        });
    }
}
