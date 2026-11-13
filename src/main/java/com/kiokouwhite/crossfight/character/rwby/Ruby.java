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
import com.kiokouwhite.crossfight.util.Puppet;
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
import org.joml.Vector3f;

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

    /** Crescent Rose, telle que Ruby la porte (pour le pantin), ou une houe de diamant a defaut. */
    static ItemStack weapon(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && "ruby".equals(com.kiokouwhite.crossfight.util.Items.tag(it, AbilityEngine.WEAPON_KEY))) return it;
        }
        return new ItemStack(Material.DIAMOND_HOE);
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
            aim(p);
            return true;
        }

        /** Combien de temps Ruby reste a genou apres un tir (chaque tir relance le compte). */
        static final int AIM_TICKS = 16;

        /**
         * Le tir de Crescent Rose : Ruby met un genou a terre, l'oeil dans la lunette, l'arme tenue comme un fusil
         * (pose ruby_tir). Des tirs enchaines la gardent dans cette position ; une autre animation en cours
         * (ruee, Yeux d'Argent) n'est pas interrompue.
         */
        static void aim(Player p) {
            Puppet current = Puppet.of(p);
            if (current != null && "ruby_tir".equals(current.currentPose())) {
                current.endAfter(AIM_TICKS, 4);
            } else if (current == null) {
                Puppet puppet = Puppet.show(p, "ruby", p.getInventory().getItemInMainHand(), "debout");
                if (puppet != null) { puppet.pose("ruby_tir", 3); puppet.endAfter(AIM_TICKS, 4); }
            }
            // Le coup part : flamme a la bouche du canon, fumee, et la douille ejectee.
            Location eye = p.getEyeLocation();
            Vector dir = eye.getDirection();
            Location muzzle = eye.clone().add(dir.clone().multiply(1.6)).add(0, -0.22, 0);
            p.getWorld().spawnParticle(Particle.FLASH, muzzle, 1, 0, 0, 0, 0, Color.fromRGB(255, 170, 60));
            p.getWorld().spawnParticle(Particle.FLAME, muzzle, 8, 0.08, 0.08, 0.08, 0.06);
            p.getWorld().spawnParticle(Particle.SMOKE, muzzle, 10, 0.1, 0.1, 0.1, 0.03);
            p.getWorld().spawnParticle(Particle.SMOKE, eye.clone().add(dir.clone().multiply(0.4)).add(0, -0.2, 0), 4, 0.05, 0.05, 0.05, 0.01);
            RWBY.ejectShell(p, eye.clone().add(dir.clone().multiply(0.3)).add(0, -0.15, 0));
        }

        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }
    }

    // --------------------------------------------------------------- pouvoirs

    static final class PetalesRapide extends ActiveAbility implements EnemyCastable {
        PetalesRapide() {
            super("ruby_petales_rapide", "Explosion de Petales (rapide)", "Ruby decolle et fonce devant elle sur 8 blocs en tournant sur elle-meme, en laissant une trainee de roses. Si elle touche un adversaire elle s'arrete et lui inflige 2 coeurs.",
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

        /** La vrille : degres par tick pendant la ruee (pres de cinq tours par seconde), puis a l'arrivee, en ralentissant. */
        static final float SPIN_PER_TICK = 95f;
        static final float[] SPIN_LANDING = {80f, 65f, 50f, 35f, 20f};

        /**
         * La ruee : Ruby decolle du sol et file a l'horizontale, la tete la premiere, la faux tendue devant elle,
         * en tournant sur elle-meme comme une toupie (pantin ruby_petales_vol, vrille autour de l'axe de la ruee).
         */
        static void perform(LivingEntity caster, Vector dir, java.util.function.Predicate<LivingEntity> isTarget) {
            Location start = caster.getLocation();
            if (caster instanceof Player pl) noFallUntil(pl, 3000);
            caster.getWorld().playSound(start, Sound.ENTITY_PHANTOM_FLAP, 1f, 1.8f);
            caster.getWorld().spawnParticle(Particle.CHERRY_LEAVES, start.clone().add(0, 0.3, 0), 30, 0.6, 0.2, 0.6, 0.08);
            caster.getWorld().spawnParticle(Particle.DUST, start.clone().add(0, 0.3, 0), 20, 0.6, 0.2, 0.6, 0, ROSE);
            final Puppet puppet = caster instanceof Player pl ? Puppet.show(pl, "ruby", weapon(pl), "debout") : null;
            if (puppet != null) {
                puppet.face((float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ())));
                puppet.pose("ruby_petales_vol", 2);
                puppet.endAfter(40, 4);
            }
            new BukkitRunnable() {
                int ticks = 0;
                float angle = 0;
                void stop() {
                    caster.setVelocity(caster.getVelocity().multiply(0.2));
                    cancel();
                    if (puppet == null || !(caster instanceof Player pl) || Puppet.of(pl) != puppet) return;
                    // Elle finit sa vrille en retombant, puis se redresse.
                    new BukkitRunnable() {
                        int k = 0;
                        @Override public void run() {
                            if (Puppet.of(pl) != puppet) { cancel(); return; }
                            if (k < SPIN_LANDING.length) {
                                angle += SPIN_LANDING[k++];
                                puppet.spin(new Vector3f(0, 0, 1), angle, new Vector3f(0, 15, 0), 1);
                                return;
                            }
                            puppet.endAfter(1, 4);
                            cancel();
                        }
                    }.runTaskTimer(plugin(), 1L, 1L);
                }
                @Override public void run() {
                    ticks++;
                    double travelled = caster.getLocation().distance(start);
                    if (ticks > 12 || travelled >= 8 || !caster.isValid()) { stop(); return; }
                    // Elle decolle les premiers ticks, puis file droit.
                    caster.setVelocity(dir.clone().multiply(1.6).setY(ticks <= 3 ? 0.3 : 0.04));
                    if (puppet != null && ticks >= 2 && caster instanceof Player pl && Puppet.of(pl) == puppet) {
                        angle += SPIN_PER_TICK;
                        puppet.spin(new Vector3f(0, 0, 1), angle, new Vector3f(0, 15, 0), 1);
                    }
                    Location l = caster.getLocation().add(0, 0.8, 0);
                    caster.getWorld().spawnParticle(Particle.CHERRY_LEAVES, l, 12, 0.5, 0.5, 0.5, 0.03);
                    caster.getWorld().spawnParticle(Particle.DUST, l, 8, 0.4, 0.5, 0.4, 0, ROSE);
                    for (LivingEntity enemy : Effects.within(caster.getLocation(), 1.6, isTarget)) {
                        if (enemy == caster) continue;
                        Effects.damage(enemy, 4.0, caster);
                        enemy.getWorld().playSound(enemy.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.3f);
                        caster.setVelocity(new Vector(0, 0, 0));
                        stop();
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

        /** Le moment ou la lumiere jaillit (ticks apres le declenchement) et la duree du faisceau. */
        static final int FLASH = 12, BEAM = 30;
        static final Particle.DustOptions SILVER = new Particle.DustOptions(Color.fromRGB(235, 240, 255), 2.2f);

        @Override
        public void onDamaged(Player victim, EntityDamageEvent e, AbilityState st) {
            if (e.isCancelled()) return;
            Bukkit.getScheduler().runTask(plugin(), () -> {
                if (!victim.isOnline() || victim.isDead() || victim.getHealth() >= 10.0) return;
                if (st.onCooldown()) return;
                st.startCooldown(90);
                awaken(victim);
            });
        }

        /**
         * Comme dans la serie : Ruby se recroqueville, les mains serrees sur la poitrine, puis se redresse d'un
         * coup, bras grands ouverts et tete en arriere ; une lumiere blanche jaillit de ses yeux et aveugle
         * tout le monde un instant. Les degats (3 coeurs a 10 blocs) tombent avec l'eclair.
         */
        static void awaken(Player ruby) {
            com.kiokouwhite.crossfight.util.Sounds.ability(ruby, "ruby_yeux");
            final Puppet puppet = Puppet.show(ruby, "ruby", null, "debout");
            if (puppet != null) {
                puppet.pose("ruby_yeux_serre", 4);
                puppet.endAfter(FLASH + BEAM, 8);
            }
            ruby.getWorld().playSound(ruby.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.6f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!ruby.isOnline() || ruby.isDead()) { cancel(); return; }
                    boolean animated = puppet != null && Puppet.of(ruby) == puppet;
                    Location eye = ruby.getEyeLocation();
                    if (t < FLASH) {
                        // La lumiere se rassemble : des eclats argentes convergent vers ses yeux.
                        for (int i = 0; i < 3; i++) {
                            Vector off = new Vector(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5).normalize().multiply(2.2);
                            ruby.getWorld().spawnParticle(Particle.END_ROD, eye.clone().add(off), 0, -off.getX(), -off.getY(), -off.getZ(), 0.09);
                        }
                    }
                    if (t == FLASH - 3 && animated) puppet.pose("ruby_yeux_cri", 3);
                    if (t == FLASH) flash(ruby);
                    if (t == FLASH + 8 && animated) puppet.pose("ruby_yeux_lumiere", 10);
                    if (t > FLASH && t <= FLASH + BEAM) beam(ruby, t - FLASH);
                    if (t > FLASH + BEAM) cancel();
                }
            }.runTaskTimer(plugin(), 1L, 1L);
        }

        /** L'eclair : ecran blanc pour tous ceux qui la voient, 3 coeurs aux ennemis a 10 blocs, qui se figent un instant. */
        static void flash(Player ruby) {
            Location eye = ruby.getEyeLocation();
            var w = ruby.getWorld();
            w.spawnParticle(Particle.FLASH, eye, 3, 0.2, 0.2, 0.2, 0, Color.WHITE);
            w.spawnParticle(Particle.END_ROD, eye, 120, 0.2, 0.2, 0.2, 0.6);
            w.spawnParticle(Particle.DUST, eye, 60, 3, 1.5, 3, 0, SILVER);
            w.playSound(eye, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 2f);
            w.playSound(eye, Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.4f);
            w.playSound(eye, Sound.ITEM_TOTEM_USE, 0.6f, 1.8f);
            // Le blanc qui envahit l'ecran : un eclair juste devant les yeux de chacun, pendant quelques ticks.
            for (Player o : w.getPlayers()) {
                if (o.getLocation().distanceSquared(ruby.getLocation()) > 24 * 24) continue;
                if (o != ruby && !o.hasLineOfSight(com.kiokouwhite.crossfight.util.MannequinPlayer.real(ruby))) continue;
                for (int k = 0; k < 3; k++) {
                    Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                        if (!o.isOnline()) return;
                        Location oe = o.getEyeLocation();
                        o.spawnParticle(Particle.FLASH, oe.clone().add(oe.getDirection().multiply(0.7)), 2, 0.1, 0.1, 0.1, 0, Color.WHITE);
                    }, 2L * k);
                }
            }
            int n = 0;
            for (LivingEntity enemy : Effects.enemiesWithin(ruby, 10)) {
                Effects.damage(enemy, 6.0, ruby);
                Location at = enemy.getLocation().add(0, 1, 0);
                w.spawnParticle(Particle.BLOCK, at, 30, 0.35, 0.6, 0.35, 0, Material.STONE.createBlockData());
                w.spawnParticle(Particle.DUST, at, 20, 0.35, 0.6, 0.35, 0, new Particle.DustOptions(Color.fromRGB(150, 150, 155), 1.6f));
                n++;
            }
            Msg.send(ruby, "<white><bold>Yeux d'Argent !</bold> <gray>" + n + " ennemi(s) touche(s).");
        }

        /** Le faisceau : un cone de lumiere qui sort de ses yeux, devant elle et vers le haut. */
        static void beam(Player ruby, int t) {
            if (t % 2 != 0) return;
            Location eye = ruby.getEyeLocation();
            Vector fwd = eye.getDirection().setY(0);
            if (fwd.lengthSquared() < 1e-4) fwd = new Vector(0, 0, 1);
            fwd.normalize();
            Vector axis = fwd.clone().multiply(0.94).add(new Vector(0, 0.34, 0)).normalize();
            for (int i = 0; i < 10; i++) {
                Vector d = axis.clone().add(new Vector(Math.random() - 0.5, Math.random() - 0.5, Math.random() - 0.5).multiply(0.55)).normalize();
                ruby.getWorld().spawnParticle(Particle.END_ROD, eye, 0, d.getX(), d.getY(), d.getZ(), 0.7);
            }
            if (t % 6 == 0) ruby.getWorld().spawnParticle(Particle.FLASH, eye.clone().add(axis.clone().multiply(0.6)), 1, 0, 0, 0, 0, Color.WHITE);
            ruby.getWorld().spawnParticle(Particle.DUST, eye.clone().add(axis.clone().multiply(1.5)), 6, 0.6, 0.6, 0.6, 0, SILVER);
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
