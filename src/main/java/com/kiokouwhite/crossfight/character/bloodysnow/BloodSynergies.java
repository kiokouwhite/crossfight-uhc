package com.kiokouwhite.crossfight.character.bloodysnow;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.character.duo.Synergy;
import com.kiokouwhite.crossfight.listener.DamageListener;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Les six Synergies de Bloody Snow.
 */
final class BloodSynergies implements Listener {

    /** Force contre les adversaires marques (Vision Emettrice) : joueur -> expiration. */
    static final Map<UUID, Long> FORCE_VS_MARKED = new HashMap<>();
    /** Defense Aceree : joueur protege -> expiration. */
    static final Map<UUID, Long> SHARP_DEFENSE = new HashMap<>();

    /** Celle des deux qui incarne ce personnage (la partenaire peut etre un mannequin duo). */
    static LivingEntity byCharacter(Player a, LivingEntity b, String id) { return id.equals(Synergy.characterOf(a)) ? a : b; }

    /** La portee n'existe que pour un joueur. */
    static void reach(LivingEntity e, Double value) { if (e instanceof Player p) BloodySnow.setReach(p, "synergie", value); }

    static void register() {

        Synergy.register("netsu", "kaze", new Synergy.Effect() {
            public String name() { return "Complementarite fraternelle"; }
            public String description() { return "Pendant 30 secondes, Netsu gagne le Power Hit de Kaze et un Home Run (clic sur la Synergie en visant un ennemi), Kaze gagne une portee de 4 blocs."; }
            public long activeMillis() { return 30000L; }
            public boolean cast(Player a, LivingEntity b) {
                LivingEntity netsu = byCharacter(a, b, "netsu"), kaze = byCharacter(a, b, "kaze");
                Kaze.POWER_HIT.put(netsu.getUniqueId(), System.currentTimeMillis() + 30000L);
                reach(kaze, 4.0);
                Bukkit.getScheduler().runTaskLater(BloodySnow.plugin(), () -> reach(kaze, null), 600L);
                Msg.send(netsu, "<red>Complementarite fraternelle : Power Hit et Home Run (clic sur la Synergie) pendant 30 s.");
                Msg.send(kaze, "<red>Complementarite fraternelle : portee de 4 blocs pendant 30 s.");
                return true;
            }
            public boolean onUse(Player p, LivingEntity partner, AbilityState st, boolean sneaking) {
                if (!"netsu".equals(BloodySnow.characterId(p))) return false;
                RayTraceResult hit = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), 4, 0.8, e -> e instanceof LivingEntity le && Effects.isEnemy(p, le));
                if (hit == null || !(hit.getHitEntity() instanceof LivingEntity target)) { Msg.actionBar(p, "<red>Home Run : vise un ennemi a 4 blocs."); return true; }
                Kaze.homeRun(p, target);
                return true;
            }
        });

        Synergy.register("netsu", "hannah", new Synergy.Effect() {
            public String name() { return "Invincibles ensemble"; }
            public String description() { return "Pendant 30 secondes, tant qu'Hannah et Netsu sont a moins de 5 blocs, elles ont une portee de 4 blocs et Resistance IV."; }
            public boolean cast(Player a, LivingEntity b) {
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        t++;
                        boolean close = a.isOnline() && b.isValid() && a.getWorld() == b.getWorld() && a.getLocation().distance(b.getLocation()) <= 5;
                        if (t > 30 || !a.isOnline() || !b.isValid()) { for (LivingEntity p : new LivingEntity[]{a, b}) reach(p, null); cancel(); return; }
                        for (LivingEntity p : new LivingEntity[]{a, b}) {
                            reach(p, close ? 4.0 : null);
                            if (close) { Effects.give(p, PotionEffectType.RESISTANCE, 3, 30); p.getWorld().spawnParticle(Particle.ENCHANTED_HIT, p.getLocation().add(0, 1, 0), 4, 0.3, 0.5, 0.3, 0.02); }
                        }
                    }
                }.runTaskTimer(BloodySnow.plugin(), 0L, 20L);
                for (LivingEntity p : new LivingEntity[]{a, b}) Msg.send(p, "<gold>Invincibles ensemble : restez a 5 blocs l'une de l'autre (30 s).");
                return true;
            }
        });

        Synergy.register("netsu", "eirika", new Synergy.Effect() {
            public String name() { return "Vision Emettrice"; }
            public String description() { return "Pendant 30 secondes, tous les ennemis a 100 blocs recoivent 2 Marques de Sang et Glowing ; Netsu et Eirika ont Force contre les marques, et Netsu peut marquer."; }
            public boolean cast(Player a, LivingEntity b) {
                LivingEntity netsu = byCharacter(a, b, "netsu");
                long until = System.currentTimeMillis() + 30000L;
                int n = 0;
                for (LivingEntity e : Effects.enemiesWithin(a, 100)) { BloodySnow.addMark(a, e); BloodySnow.addMark(a, e); Effects.give(e, PotionEffectType.GLOWING, 0, 600); n++; }
                for (LivingEntity p : new LivingEntity[]{a, b}) { FORCE_VS_MARKED.put(p.getUniqueId(), until); Msg.send(p, "<dark_red>Vision Emettrice : " + n + " ennemi(s) marque(s), Force contre les marques 30 s."); }
                BloodySnow.MARKERS.put(netsu.getUniqueId(), until);
                return true;
            }
        });

        Synergy.register("kaze", "hannah", new Synergy.Effect() {
            public String name() { return "Defense et Attaque"; }
            public String description() { return "Pendant 30 secondes, les deux gagnent des coeurs d'absorption egaux a la barre de resistance d'Hannah et une Force selon les degats de Kaze de la derniere minute (10 coeurs = Force I, jusqu'a IV)."; }
            public boolean cast(Player a, LivingEntity b) {
                LivingEntity kaze = byCharacter(a, b, "kaze"), hannah = byCharacter(a, b, "hannah");
                // Un mannequin duo n'a ni barre de resistance ni historique de degats : sa part compte pour 0.
                int res = hannah instanceof Player hp ? Hannah.resistance(hp) : 0;
                int force = kaze instanceof Player kp ? Math.min(4, (int) (Kaze.damageLastMinute(kp) / 20.0)) : 0;
                for (LivingEntity p : new LivingEntity[]{a, b}) {
                    p.setAbsorptionAmount(Math.min(40, p.getAbsorptionAmount() + res * 2));
                    if (force > 0) { Boosts.set(p, "synergie_da", new Boosts.Boost(force, 0, 0, 0)); Bukkit.getScheduler().runTaskLater(BloodySnow.plugin(), () -> Boosts.remove(p, "synergie_da"), 600L); }
                    p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.1);
                    Msg.send(p, "<gold>Defense et Attaque : +" + res + " coeur(s) d'absorption, Force " + force + " (30 s).");
                }
                return true;
            }
        });

        Synergy.register("kaze", "eirika", new Synergy.Effect() {
            public String name() { return "Hemorragie Puissante"; }
            public String description() { return "Pendant 30 secondes, les deux ont une Force egale au plus grand nombre de Marques de Sang portees par un adversaire, et Kaze peut marquer."; }
            public boolean cast(Player a, LivingEntity b) {
                LivingEntity kaze = byCharacter(a, b, "kaze");
                long until = System.currentTimeMillis() + 30000L;
                BloodySnow.MARKERS.put(kaze.getUniqueId(), until);
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        t++;
                        if (t > 30) { for (LivingEntity p : new LivingEntity[]{a, b}) Boosts.remove(p, "synergie_hemo"); cancel(); return; }
                        int force = Math.min(5, BloodySnow.maxMarks(a));
                        for (LivingEntity p : new LivingEntity[]{a, b}) if (p.isValid()) { if (force > 0) Boosts.set(p, "synergie_hemo", new Boosts.Boost(force, 0, 0, 0)); else Boosts.remove(p, "synergie_hemo"); }
                    }
                }.runTaskTimer(BloodySnow.plugin(), 0L, 20L);
                for (LivingEntity p : new LivingEntity[]{a, b}) Msg.send(p, "<dark_red>Hemorragie Puissante : Force = marques maximales (30 s), Kaze peut marquer.");
                return true;
            }
        });

        Synergy.register("hannah", "eirika", new Synergy.Effect() {
            public String name() { return "Defense Aceree"; }
            public String description() { return "Pendant 30 secondes, chaque ennemi qui touche Hannah ou Eirika perd 1 coeur par Marque de Sang qu'il porte, et Hannah peut marquer."; }
            public boolean cast(Player a, LivingEntity b) {
                LivingEntity hannah = byCharacter(a, b, "hannah");
                long until = System.currentTimeMillis() + 30000L;
                BloodySnow.MARKERS.put(hannah.getUniqueId(), until);
                for (LivingEntity p : new LivingEntity[]{a, b}) { SHARP_DEFENSE.put(p.getUniqueId(), until); Msg.send(p, "<dark_red>Defense Aceree : qui vous frappe saigne selon ses marques (30 s)."); }
                return true;
            }
        });
    }

    /** Force contre les marques, et Defense Aceree. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity v)) return;
        Player a = DamageListener.attackerOf(e.getDamager());
        if (a != null) {
            Long until = FORCE_VS_MARKED.get(a.getUniqueId());
            if (until != null && System.currentTimeMillis() < until && BloodySnow.markCount(v) > 0) e.setDamage(e.getDamage() + 3.0);
        }
        if (v instanceof Player victim) {
            Long until = SHARP_DEFENSE.get(victim.getUniqueId());
            LivingEntity attacker = DamageListener.livingAttackerOf(e.getDamager());
            if (until != null && System.currentTimeMillis() < until && attacker != null && attacker != victim && Effects.isEnemy(victim, attacker)) {
                int marks = BloodySnow.markCount(attacker);
                if (marks > 0) Bukkit.getScheduler().runTask(BloodySnow.plugin(), () -> { attacker.setNoDamageTicks(0); Effects.damage(attacker, marks * 2.0, victim); attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_HURT_DROWN, 0.8f, 0.7f); });
            }
        }
    }
}
