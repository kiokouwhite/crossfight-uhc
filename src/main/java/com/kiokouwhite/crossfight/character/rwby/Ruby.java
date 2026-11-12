package com.kiokouwhite.crossfight.character.rwby;

import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.EnemyCastable;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.ToggleAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Stealth;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ruby Rose - Path of Hunt.
 */
public final class Ruby extends Character {

    static final Particle.DustOptions ROSE = new Particle.DustOptions(Color.fromRGB(220, 20, 60), 1.4f);

    public Ruby() {
        super("ruby", "Ruby Rose", Voie.CHASSE, Material.DIAMOND_HOE);
        addAbility(new CrescentRose().bindToWeapon());
        addAbility(new PetalesRapide());
        addAbility(new PetalesLent());
        addAbility(new YeuxDArgent());
        addAbility(RWBY.DUO_ITEM);
        setUltimate(new RushDuChaperon());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.DIAMOND_HOE, 9, "<red><bold>Crescent Rose", List.of());
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Crescent Rose (9) : 3 balles Puissance III",
                "<white>Petales (rapide) : ruee de 8 blocs, 2 coeurs",
                "<white>Petales (lent) : Vitesse II + invisibilite",
                "<white>Yeux d'Argent : 3 coeurs autour sous 5 coeurs",
                "<light_purple>Ultime : 15 s Vitesse III invisible, sans s'arreter");
    }

    /** Protege un joueur des degats de chute pendant un moment (lancers, ruees). */
    public static void noFallUntil(Player p, long millis) {
        Effects.noFallUntil(p, millis);
    }

    static void trail(Player p) {
        Location l = p.getLocation().add(0, 0.8, 0);
        p.getWorld().spawnParticle(Particle.CHERRY_LEAVES, l, 6, 0.4, 0.5, 0.4, 0.02);
        p.getWorld().spawnParticle(Particle.DUST, l, 4, 0.3, 0.4, 0.3, 0, ROSE);
    }

    // ------------------------------------------------------------------ arme

    /** Crescent Rose : 3 balles, une balle revient toutes les 7 secondes. Clic droit : tir instantane Puissance III. */
    static final class CrescentRose extends ActiveAbility implements CustomUse {
        static final int MAX = 3;
        static final long RELOAD_MS = 7000L;

        CrescentRose() {
            super("ruby_crescent", "Crescent Rose", "Clic droit : Ruby tire instantanement une balle rouge (puissance d'une fleche Puissance III). 3 balles, une balle revient toutes les 7 secondes.", Material.DIAMOND_HOE, 0);
        }

        int bullets(AbilityState st) {
            int b = st.get("bullets", MAX);
            long last = st.get("reloadAt", 0L);
            long now = System.currentTimeMillis();
            if (b < MAX) {
                if (last == 0L) { st.set("reloadAt", now); }
                else while (b < MAX && now - last >= RELOAD_MS) { b++; last += RELOAD_MS; }
                st.set("bullets", b);
                st.set("reloadAt", b >= MAX ? 0L : last);
            }
            return b;
        }

        @Override
        public String barText(Player p, CfPlayer cf, AbilityState st) {
            int b = bullets(st);
            long last = st.get("reloadAt", 0L);
            String reload = b >= MAX ? "" : " <gray>(" + (int) Math.ceil((RELOAD_MS - (System.currentTimeMillis() - last)) / 1000.0) + "s)";
            return "<red>Crescent " + "●".repeat(b) + "<dark_gray>" + "●".repeat(MAX - b) + reload;
        }

        @Override
        public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            int b = bullets(st);
            if (b <= 0) {
                Msg.actionBar(p, "<red>Crescent Rose : plus de balles.");
                return true;
            }
            if (b == MAX) st.set("reloadAt", System.currentTimeMillis());
            st.set("bullets", b - 1);
            com.kiokouwhite.crossfight.util.Bullets.shoot(p, p.getEyeLocation().getDirection(), 4.0, 2.75, Material.RED_CONCRETE, Color.fromRGB(255, 30, 40));
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1.6f);
            return true;
        }

        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }
    }

    // --------------------------------------------------------------- pouvoirs

    static final class PetalesRapide extends ActiveAbility implements EnemyCastable {
        PetalesRapide() {
            super("ruby_petales_rapide", "Explosion de Petales (rapide)", "Ruby fonce devant elle sur 8 blocs en laissant une trainee de roses. Si elle touche un adversaire elle s'arrete et lui inflige 2 coeurs.",
                    Material.POPPY, 10);
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            perform(p, Effects.towards(p, null), Effects.enemiesOf(p, null));
            return true;
        }

        @Override
        public boolean castOn(LivingEntity caster, Player target, AbilityState st) {
            perform(caster, Effects.towards(caster, target), Effects.enemiesOf(caster, target));
            return true;
        }

        static void perform(LivingEntity caster, Vector dir, java.util.function.Predicate<LivingEntity> isTarget) {
            Location start = caster.getLocation();
            if (caster instanceof Player pl) noFallUntil(pl, 3000);
            caster.getWorld().playSound(start, Sound.ENTITY_PHANTOM_FLAP, 1f, 1.8f);
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    ticks++;
                    double travelled = caster.getLocation().distance(start);
                    if (ticks > 12 || travelled >= 8 || !caster.isValid()) { caster.setVelocity(caster.getVelocity().multiply(0.2)); cancel(); return; }
                    caster.setVelocity(dir.clone().multiply(1.6).setY(0.05));
                    Location l = caster.getLocation().add(0, 0.8, 0);
                    caster.getWorld().spawnParticle(Particle.CHERRY_LEAVES, l, 12, 0.5, 0.5, 0.5, 0.03);
                    caster.getWorld().spawnParticle(Particle.DUST, l, 8, 0.4, 0.5, 0.4, 0, ROSE);
                    for (LivingEntity enemy : Effects.within(caster.getLocation(), 1.6, isTarget)) {
                        if (enemy == caster) continue;
                        Effects.damage(enemy, 4.0, caster);
                        caster.setVelocity(new Vector(0, 0, 0));
                        enemy.getWorld().playSound(enemy.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.3f);
                        cancel();
                        return;
                    }
                }
            }.runTaskTimer(plugin(), 0L, 1L);
        }
    }

    /** Vitesse II + invisibilite. Jauge de 5 secondes (100 = 5 s), une seconde revient toutes les 3 secondes. */
    static final class PetalesLent extends ToggleAbility {
        PetalesLent() {
            super("ruby_petales_lent", "Explosion de Petales (lent)", "Ruby gagne Vitesse II et devient invisible. Jauge de 5 secondes, une seconde revient toutes les 3 secondes.",
                    Material.PINK_PETALS, 20.0, 100.0 / 15.0);
        }

        @Override
        public boolean onEnable(Player p, CfPlayer cf, AbilityState st) {
            Stealth.hideFromAll(p, "ruby_lent", v -> Effects.isEnemy(p, v));
            return true;
        }

        @Override
        public void tickActive(Player p, CfPlayer cf, AbilityState st) {
            Effects.refresh(p, PotionEffectType.SPEED, 1, 45);
            Effects.refresh(p, PotionEffectType.INVISIBILITY, 0, 45);
            Stealth.hideArmor(p);
            trail(p);
        }

        @Override
        public void onDisable(Player p, CfPlayer cf, AbilityState st) {
            Stealth.reveal(p, "ruby_lent");
            Stealth.showArmor(p);
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }

    static final class YeuxDArgent extends PassiveAbility {
        YeuxDArgent() {
            super("ruby_yeux", "Yeux d'Argent", "Quand Ruby passe sous 5 coeurs, elle inflige 3 coeurs a tous les ennemis a moins de 10 blocs. Une fois par minute et demie.", Material.IRON_NUGGET);
        }

        @Override
        public void onDamaged(Player victim, EntityDamageEvent e, AbilityState st) {
            if (e.isCancelled()) return;
            Bukkit.getScheduler().runTask(plugin(), () -> {
                if (!victim.isOnline() || victim.isDead() || victim.getHealth() >= 10.0) return;
                if (st.onCooldown()) return;
                st.startCooldown(90);
                int n = 0;
                for (LivingEntity enemy : Effects.enemiesWithin(victim, 10)) { Effects.damage(enemy, 6.0, victim); n++; }
                victim.getWorld().spawnParticle(Particle.FLASH, victim.getEyeLocation(), 2, 0, 0, 0, 0, Color.SILVER);
                victim.getWorld().spawnParticle(Particle.END_ROD, victim.getEyeLocation(), 80, 5, 1, 5, 0.1);
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 2f);
                com.kiokouwhite.crossfight.util.Sounds.ability(victim, "ruby_yeux");
                Msg.send(victim, "<white><bold>Yeux d'Argent !</bold> <gray>" + n + " ennemi(s) touche(s).");
            });
        }

        @Override
        public String barText(Player p, CfPlayer cf, AbilityState st) {
            return st.onCooldown() ? "<gray>Yeux d'Argent <red>" + st.cooldownLeftSeconds() + "s" : "<white>Yeux d'Argent ✔";
        }
    }

    // ----------------------------------------------------------------- ultime

    /** 15 secondes de Vitesse III et d'invisibilite, trainee de roses, impossible de s'arreter. */
    static final class RushDuChaperon extends UltimateAbility {
        RushDuChaperon() {
            super("ruby_ult", "Rush du Chaperon Rouge", "Pendant 15 secondes Ruby a Vitesse III et est invisible, laisse une trainee de roses et ne peut pas s'arreter.", Material.RED_TULIP);
        }

        @Override
        public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            Effects.give(p, PotionEffectType.SPEED, 2, 20 * 15);
            Effects.give(p, PotionEffectType.INVISIBILITY, 0, 20 * 15);
            Stealth.hideFromAll(p, "ruby_ult", v -> Effects.isEnemy(p, v));
            noFallUntil(p, 16000);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 1.5f);
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    ticks++;
                    if (ticks > 20 * 15 || !p.isOnline() || p.isDead()) { Stealth.reveal(p, "ruby_ult"); Stealth.showArmor(p); cancel(); return; }
                    if (ticks % 20 == 1) Stealth.hideArmor(p);
                    Vector dir = p.getLocation().getDirection().setY(0);
                    if (dir.lengthSquared() > 0.01) {
                        dir.normalize().multiply(0.55);
                        p.setVelocity(new Vector(dir.getX(), p.getVelocity().getY(), dir.getZ()));
                    }
                    if (ticks % 2 == 0) trail(p);
                }
            }.runTaskTimer(plugin(), 0L, 1L);
            return true;
        }
    }

}
