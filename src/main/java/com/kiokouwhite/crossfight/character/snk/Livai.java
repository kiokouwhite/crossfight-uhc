package com.kiokouwhite.crossfight.character.snk;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

/**
 * Livai Ackerman - Path of Hunt.
 */
public final class Livai extends Character {

    public Livai() {
        super("livai", "Livai Ackerman", Voie.CHASSE, Material.DIAMOND_SWORD);
        addAbility(new SNK.OdmGear(SNK.Style.TOUPIE, "Style Toupie : clic droit avec les lames dans les airs pour une toupie de 5 secondes."));
        addAbility(new Toupie().bindToWeapon());
        addAbility(new ChargeDestructrice());
        addAbility(new AssautImpetueux());
        addAbility(new CamaradesTombes());
        setUltimate(new ProDeLEquipement());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.DIAMOND_SWORD, 9.5, "<gold><bold>Lames (Style Toupie)", List.of("<gray>Equipement tridimensionnel de Livai."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Lames Style Toupie : toupie de 5 s dans les airs",
                "<white>Charge Destructrice : 10 blocs, 5 coeurs (3 pour lui)",
                "<white>Assaut Impetueux : saut puis frappe en retombant",
                "<white>Mes camarades tombes : plus fort a chaque allie perdu",
                "<light_purple>Ultime : 30 s de deplacements instantanes");
    }

    static boolean ultActive(Player p) {
        CfPlayer cf = SNK.plugin().players().get(p);
        if (cf.character() == null || !"livai".equals(cf.character().id())) return false;
        var ult = cf.character().ability("livai_ult");
        return ult != null && System.currentTimeMillis() < SNK.plugin().abilities().state(p, ult).get("proUntil", 0L);
    }

    // -------------------------------------------------------------- arme

    /** Clic droit avec les lames dans les airs : 5 secondes de toupie, 1 coeur toutes les demi-secondes autour de Livai. */
    static final class Toupie extends ActiveAbility {
        Toupie() {
            super("livai_toupie", "Toupie", "Clic droit dans les airs : Livai tournoie pendant 5 secondes en infligeant 1 coeur toutes les demi-secondes aux adversaires a 2.5 blocs.", Material.DIAMOND_SWORD, 30);
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            if (p.isOnGround()) { Msg.actionBar(p, "<red>Toupie : seulement dans les airs."); return false; }
            Effects.give(p, PotionEffectType.SLOW_FALLING, 0, 110);
            Effects.noFallUntil(p, 7000);
            World w = p.getWorld();
            w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.5f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || p.isDead() || t > 100) { cancel(); return; }
                    double ang = t * 0.9;
                    Location c = p.getLocation().add(0, 1, 0);
                    for (int k = 0; k < 2; k++) {
                        double a = ang + k * Math.PI;
                        w.spawnParticle(Particle.SWEEP_ATTACK, c.clone().add(Math.cos(a) * 1.8, Math.sin(t * 0.5) * 0.4, Math.sin(a) * 1.8), 1);
                    }
                    w.spawnParticle(Particle.DUST, c.clone().add(0, 0.3, 0), 2, 0.3, 0.2, 0.3, 0, SNK.GAS);
                    if (t % 10 == 0) {
                        w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.2f + (float) Math.random() * 0.4f);
                        var enemies = Effects.enemiesWithin(p, 2.5);
                        for (LivingEntity enemy : enemies) { enemy.setNoDamageTicks(0); Effects.damage(enemy, 2.0, p); }
                    }
                }
            }.runTaskTimer(SNK.plugin(), 0L, 1L);
            return true;
        }
    }

    // ---------------------------------------------------------------- pouvoirs

    static final class ChargeDestructrice extends ActiveAbility {
        ChargeDestructrice() {
            super("livai_charge", "Charge Destructrice", "Livai fonce en avant sur 10 blocs en s'infligeant 3 coeurs. Chaque adversaire touche perd 5 coeurs.", Material.BLAZE_POWDER, 10);
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
            dir.normalize();
            Location from = p.getLocation();
            World w = p.getWorld();
            SNK.selfDamage(p, 6.0);
            w.playSound(from, Sound.ENTITY_BREEZE_SHOOT, 1f, 1.1f);
            java.util.Set<UUID> hit = new java.util.HashSet<>();
            final Vector d = dir;
            new BukkitRunnable() {
                int i = 0;
                Location current = from.clone();
                @Override public void run() {
                    i++;
                    if (!p.isOnline() || p.isDead() || i > 5) { cancel(); return; }
                    Location next = current.clone().add(d.clone().multiply(2));
                    next.setDirection(p.getLocation().getDirection());
                    if (!next.getBlock().isPassable() || !next.clone().add(0, 1, 0).getBlock().isPassable()) { cancel(); return; }
                    for (double k = 0; k < 2; k += 0.5) {
                        Location l = current.clone().add(d.clone().multiply(k)).add(0, 1, 0);
                        w.spawnParticle(Particle.SWEEP_ATTACK, l, 1);
                        w.spawnParticle(Particle.DUST, l, 2, 0.2, 0.3, 0.2, 0, SNK.GAS);
                        for (LivingEntity enemy : Effects.enemiesWithin(p, l, 1.5)) {
                            if (hit.add(enemy.getUniqueId())) { Effects.damage(enemy, 10.0, p); w.playSound(l, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f); }
                        }
                    }
                    p.teleport(next);
                    current = next;
                }
            }.runTaskTimer(SNK.plugin(), 0L, 1L);
            return true;
        }
    }

    static final class AssautImpetueux extends ActiveAbility {
        AssautImpetueux() {
            super("livai_assaut", "Assaut Impetueux", "Livai se propulse a 5 blocs dans les airs puis retombe : 3 coeurs aux adversaires a 2 blocs de l'impact. S'il rate, il perd 3 coeurs.", Material.RABBIT_FOOT, 15);
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Effects.launchUp(p, 5);
            Effects.noFallUntil(p, 6000);
            World w = p.getWorld();
            w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 1.2f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || p.isDead() || t > 80) { cancel(); return; }
                    w.spawnParticle(Particle.CLOUD, p.getLocation(), 2, 0.2, 0.1, 0.2, 0.01);
                    if (t > 6 && p.isOnGround()) {
                        Location c = p.getLocation();
                        w.spawnParticle(Particle.EXPLOSION, c, 1);
                        w.spawnParticle(Particle.SWEEP_ATTACK, c.clone().add(0, 0.5, 0), 4, 0.8, 0.2, 0.8, 0);
                        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.5f);
                        int n = 0;
                        for (LivingEntity enemy : Effects.enemiesWithin(p, c, 2.0)) { Effects.damage(enemy, 6.0, p); n++; }
                        if (n == 0) { SNK.selfDamage(p, 6.0); Msg.actionBar(p, "<red>Assaut rate : -3 coeurs."); }
                        else Msg.actionBar(p, "<green>Assaut : " + n + " adversaire(s) touche(s).");
                        cancel();
                    }
                }
            }.runTaskTimer(SNK.plugin(), 1L, 1L);
            return true;
        }
    }

    static final class CamaradesTombes extends PassiveAbility {
        CamaradesTombes() {
            super("livai_camarades", "Mes camarades tombes au combat", "Plus Livai perd d'allies, plus il se renforce : 1 allie elimine = Speed ; 2 = Force et Speed ; 3 = Speed, Force et Resistance.");
        }
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) {
            Team t = SNK.plugin().teams().teamOf(p);
            int dead = 0;
            if (t != null) for (UUID u : t.members()) {
                if (u.equals(p.getUniqueId())) continue;
                CfPlayer m = SNK.plugin().players().get(u);
                if (m == null || !m.isAlive()) dead++;
            }
            Boosts.Boost b = dead <= 0 ? null : dead == 1 ? new Boosts.Boost(0, 1, 0, 0) : dead == 2 ? new Boosts.Boost(1, 1, 0, 0) : new Boosts.Boost(1, 1, 1, 0);
            if (b == null) { if (Boosts.get(p, "livai_camarades") != null) Boosts.remove(p, "livai_camarades"); }
            else if (st.get("dead", 0) != dead) Boosts.set(p, "livai_camarades", b);
            st.set("dead", dead);
        }
        @Override public void onUnequip(Player p, CfPlayer cf) { Boosts.remove(p, "livai_camarades"); Boosts.remove(p, "livai_air"); }
    }

    // ----------------------------------------------------------------- ultime

    /** 30 secondes : clic droit pour se deplacer quasi instantanement la ou Livai vise, attraper les ennemis ; Force dans les airs. */
    static final class ProDeLEquipement extends UltimateAbility implements CustomUse {
        ProDeLEquipement() {
            super("livai_ult", "Pro de l'equipement tridimensionnel", "Pendant 30 secondes : clic droit pour foncer quasi instantanement la ou Livai vise (30 blocs). S'il vise un ennemi, il l'attrape et le frappe (2 coeurs). Dans les airs il gagne Force. Pas de gaz consomme.", Material.ELYTRA);
        }
        static boolean active(AbilityState st) { return System.currentTimeMillis() < st.get("proUntil", 0L); }

        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            if (!active(st)) return false;
            World w = p.getWorld();
            Location eye = p.getEyeLocation();
            Vector dir = eye.getDirection();
            RayTraceResult hit = w.rayTrace(eye, dir, 30, FluidCollisionMode.NEVER, true, 0.8, e -> e instanceof LivingEntity le && Effects.isEnemy(p, le));
            if (hit != null && hit.getHitEntity() instanceof LivingEntity target) {
                Vector back = p.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
                if (back.lengthSquared() < 0.01) back = new Vector(1, 0, 0);
                Location to = target.getLocation().add(back.normalize().multiply(1.0));
                to.setDirection(target.getLocation().add(0, 1, 0).toVector().subtract(to.toVector().add(new Vector(0, 1.6, 0))));
                SNK.rush(p, to, 2, Particle.SWEEP_ATTACK);
                Bukkit.getScheduler().runTaskLater(SNK.plugin(), () -> {
                    if (target.isDead()) return;
                    Effects.damage(target, 4.0, p);
                    target.setVelocity(new Vector(0, 0.5, 0));
                    w.spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 2, 0.3, 0.3, 0.3, 0);
                    w.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.2f);
                }, 3L);
                return true;
            }
            Location to = hit != null && hit.getHitBlock() != null
                    ? hit.getHitPosition().toLocation(w).add(hit.getHitBlockFace().getDirection().multiply(0.6))
                    : eye.clone().add(dir.clone().multiply(30));
            to.setDirection(dir);
            SNK.rush(p, to, 2, Particle.CLOUD);
            w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1.5f);
            return true;
        }

        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            st.set("proUntil", System.currentTimeMillis() + 30000L);
            World w = p.getWorld();
            w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_CHARGE, 1f, 1.2f);
            w.spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 40, 0.6, 0.8, 0.6, 0.05);
            Msg.send(p, "<aqua><bold>Pro de l'equipement :</bold> <gray>clic droit sur l'ultime pour foncer ou tu vises.");
            new BukkitRunnable() {
                @Override public void run() {
                    if (!p.isOnline() || !active(st)) { Boosts.remove(p, "livai_air"); cancel(); return; }
                    SNK.boostIf(p, "livai_air", !p.isOnGround(), new Boosts.Boost(1, 0, 0, 0));
                }
            }.runTaskTimer(SNK.plugin(), 1L, 2L);
            return true;
        }

        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            if (!active(st)) return null;
            return "<aqua><bold>PRO 3D</bold> <white>" + ((st.get("proUntil", 0L) - System.currentTimeMillis()) / 1000) + "s";
        }
    }
}
