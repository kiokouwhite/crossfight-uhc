package com.kiokouwhite.crossfight.character.rwby;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.AbilityType;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.ToggleAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Clones;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Puppet;
import com.kiokouwhite.crossfight.util.Stealth;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Blake Belladonna - Path of Nihility.
 */
public final class Blake extends Character implements Listener {

    static final NamespacedKey KATANA_THROW = new NamespacedKey("crossfight", "blake_katana");
    static final String[] MODES = {"Couperet", "Katana", "Pistolet"};

    public Blake() {
        super("blake", "Blake Belladonna", Voie.NIHILITE, Material.NETHERITE_SWORD);
        addAbility(new GambolShroud().bindToWeapon());
        addAbility(new Ombre());
        addAbility(new Discretion());
        addAbility(new Effacement());
        addAbility(RWBY.DUO_ITEM);
        setUltimate(new InstinctAnimal());
    }

    @Override
    public ItemStack createWeapon() {
        return weaponFor(0);
    }

    /** L'arme selon le mode : couperet (9.5), katana (8) ou pistolet (arc Puissance II). */
    static ItemStack weaponFor(int mode) {
        return switch (mode) {
            case 1 -> Weapons.melee(Material.IRON_SWORD, 8, "<dark_gray><bold>Gambol Shroud <gray>- Katana", List.of("<gray>Clic droit : lance la lame et te teleporte ou elle tombe."));
            case 2 -> Weapons.bow(2, false, "<dark_gray><bold>Gambol Shroud <gray>- Pistolet", List.of("<gray>Clic droit : une balle par seconde, 10 balles, 1 balle revient toutes les 3 s."));
            default -> Weapons.melee(Material.NETHERITE_SWORD, 9.5, "<dark_gray><bold>Gambol Shroud <gray>- Couperet", List.of("<gray>Resistance tant que ce mode est en main."));
        };
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Gambol Shroud : couperet, katana ou pistolet",
                "<white>Ombre : 3 clones, Cecite si on les frappe",
                "<white>Discretion : invisibilite totale a jauge",
                "<white>Effacement : invisible au-dela de 20 blocs",
                "<light_purple>Ultime : 30 s Vitesse II et bonds de 2 blocs");
    }

    private static CrossfightPlugin plugin() { return CrossfightPlugin.get(); }

    static int mode(Player p) {
        return plugin().abilities().state(p, plugin().characters().character("blake").ability("blake_gambol")).mode();
    }

    // ------------------------------------------------------------------ arme

    static final class GambolShroud extends Ability implements CustomUse {
        static final int MAX_BULLETS = 10;
        static final long RELOAD_MS = 3000L;

        GambolShroud() {
            super("blake_gambol", "Gambol Shroud",
                    "Accroupi + clic droit : change de mode. Couperet (9.5) : Resistance. Katana (8) : clic droit lance la lame et fonce quasi instantanement la ou elle tombe (10 s). Pistolet (Puissance II) : clic droit tire une balle par seconde, 10 balles, une revient toutes les 3 s.",
                    AbilityType.SWITCH, Material.NETHERITE_SWORD);
        }

        @Override
        public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            if (sneaking) {
                int next = (st.mode() + 1) % MODES.length;
                st.setMode(next);
                swapWeapon(p, next);
                if (next == 1) katanaToss(p);
                Msg.actionBar(p, "<dark_gray>Gambol Shroud : <white>" + MODES[next]);
                p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1.4f);
                return true;
            }
            switch (st.mode()) {
                case 1 -> throwKatana(p, st);
                case 2 -> shoot(p, st);
                // Rien a faire en mode Couperet : message rouge, donc ni bruitage ni replique.
                case 0 -> Msg.actionBar(p, "<red>Couperet : accroupi + clic droit pour changer de mode.");
                default -> { }
            }
            return true;
        }

        static void swapWeapon(Player p, int mode) {
            var inv = p.getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                if ("blake".equals(Items.tag(inv.getItem(i), AbilityEngine.WEAPON_KEY))) {
                    ItemStack w = weaponFor(mode);
                    AbilityEngine.decorateWeapon(w, plugin().characters().character("blake"));
                    inv.setItem(i, w);
                    return;
                }
            }
        }

        void throwKatana(Player p, AbilityState st) {
            if (st.onCooldown()) { Msg.actionBar(p, "<red>Katana : encore " + st.cooldownLeftSeconds() + "s"); return; }
            st.startCooldown(10);
            Snowball s = p.launchProjectile(Snowball.class, p.getEyeLocation().getDirection().multiply(1.8));
            s.getPersistentDataContainer().set(KATANA_THROW, PersistentDataType.BYTE, (byte) 1);
            s.setItem(new ItemStack(Material.IRON_SWORD));
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.5f);
        }

        int bullets(AbilityState st) {
            int b = st.get("bullets", MAX_BULLETS);
            long last = st.get("reloadAt", 0L);
            long now = System.currentTimeMillis();
            if (b < MAX_BULLETS) {
                if (last == 0L) st.set("reloadAt", now);
                else while (b < MAX_BULLETS && now - last >= RELOAD_MS) { b++; last += RELOAD_MS; }
                st.set("bullets", b);
                st.set("reloadAt", b >= MAX_BULLETS ? 0L : last);
            }
            return b;
        }

        void shoot(Player p, AbilityState st) {
            long now = System.currentTimeMillis();
            if (now - (long) st.get("lastShot", 0L) < 1000L) return;
            int b = bullets(st);
            if (b <= 0) { Msg.actionBar(p, "<red>Pistolet : plus de balles."); return; }
            if (b == MAX_BULLETS) st.set("reloadAt", now);
            st.set("bullets", b - 1);
            st.set("lastShot", now);
            com.kiokouwhite.crossfight.util.Bullets.shoot(p, p.getEyeLocation().getDirection(), 4.0, 2.2, Material.BLACK_CONCRETE, Color.fromRGB(40, 0, 60));
            aimPistol(p);
        }

        @Override
        public String barText(Player p, CfPlayer cf, AbilityState st) {
            return switch (st.mode()) {
                case 1 -> st.onCooldown() ? "<dark_gray>Katana <red>" + st.cooldownLeftSeconds() + "s" : "<dark_gray>Katana ✔";
                case 2 -> "<dark_gray>Pistolet <white>" + bullets(st) + "/" + MAX_BULLETS;
                default -> "<dark_gray>Couperet";
            };
        }

        @Override
        public void tick(Player p, CfPlayer cf, AbilityState st) {
            if (st.mode() == 0) Effects.refresh(p, PotionEffectType.RESISTANCE, 0, 45);
        }

        @Override
        public void onEquip(Player p, CfPlayer cf) {
            AbilityState st = plugin().abilities().state(p, this);
            if (st.mode() != 0) Bukkit.getScheduler().runTask(plugin(), () -> swapWeapon(p, st.mode()));
        }
    }

    @EventHandler
    public void onKatanaLand(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball s) || !s.getPersistentDataContainer().has(KATANA_THROW)) return;
        if (!(s.getShooter() instanceof Player p)) return;
        Location to = s.getLocation();
        if (e.getHitBlock() != null && e.getHitBlockFace() != null) to = e.getHitBlock().getRelative(e.getHitBlockFace()).getLocation().add(0.5, 0, 0.5);
        dash(p, to);
    }

    /** Ruee quasi instantanee vers un point : Blake traverse la distance en quelques ticks, en ligne droite. */
    static void dash(Player p, Location to) {
        Location from = p.getLocation();
        Vector path = to.toVector().subtract(from.toVector());
        double dist = path.length();
        if (dist < 0.5) return;
        int steps = (int) Math.max(2, Math.min(8, Math.ceil(dist / 4.0)));
        Vector step = path.clone().multiply(1.0 / steps);
        Ruby.noFallUntil(p, 3000);
        p.getWorld().playSound(from, Sound.ENTITY_PHANTOM_FLAP, 1f, 2f);
        new org.bukkit.scheduler.BukkitRunnable() {
            int i = 0;
            Location current = from.clone();
            @Override public void run() {
                i++;
                if (!p.isOnline() || p.isDead() || i > steps) { cancel(); return; }
                Location next = current.clone().add(step);
                next.setDirection(p.getLocation().getDirection());
                // Un mur sur le chemin arrete la ruee juste devant.
                if (!next.getBlock().isPassable() || !next.clone().add(0, 1, 0).getBlock().isPassable()) { cancel(); return; }
                for (double d = 0; d < step.length(); d += 0.5) {
                    Location l = current.clone().add(step.clone().normalize().multiply(d)).add(0, 1, 0);
                    p.getWorld().spawnParticle(Particle.SMOKE, l, 2, 0.1, 0.2, 0.1, 0.01);
                    p.getWorld().spawnParticle(Particle.DUST, l, 1, 0.1, 0.2, 0.1, 0, new Particle.DustOptions(Color.fromRGB(60, 0, 90), 1.2f));
                }
                p.teleport(next);
                current = next;
                if (i == steps) p.getWorld().playSound(next, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.6f);
            }
        }.runTaskTimer(plugin(), 0L, 1L);
    }

    // ------------------------------------------------------------ animations

    /** La silhouette violette de l'Ombre (teinte des parties du pantin et contour lumineux). */
    static final Color SHADOW = Color.fromRGB(120, 45, 215);
    static final Particle.DustOptions SHADOW_DUST = new Particle.DustOptions(Color.fromRGB(125, 50, 220), 1.3f);
    /** Duree de la revelation de l'Ombre, en ticks. */
    static final int REVEAL = 18;
    static final int AIM_TICKS = 16;

    /** Gambol Shroud en pistolet : un petit modele du pack public, tenu par le pantin. */
    static ItemStack pistolModel() {
        ItemStack it = new ItemStack(Material.PAPER);
        var meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("crossfight", "blake_pistolet"));
        it.setItemMeta(meta);
        return it;
    }

    /** Le tir au pistolet : tenu a deux mains, bras tendus devant elle ; des tirs enchaines la gardent en position. */
    static void aimPistol(Player p) {
        Puppet current = Puppet.of(p);
        if (current != null && "blake_pistolet".equals(current.currentPose())) {
            current.endAfter(AIM_TICKS, 4);
        } else if (current == null) {
            Puppet pu = Puppet.show(p, "blake", pistolModel(), "debout");
            if (pu != null) { pu.pose("blake_pistolet", 2); pu.endAfter(AIM_TICKS, 4); }
        }
        Location eye = p.getEyeLocation();
        Location muzzle = eye.clone().add(eye.getDirection().multiply(1.0)).add(0, -0.22, 0);
        p.getWorld().spawnParticle(Particle.SMOKE, muzzle, 5, 0.05, 0.05, 0.05, 0.02);
        p.getWorld().spawnParticle(Particle.DUST, muzzle, 4, 0.05, 0.05, 0.05, 0, SHADOW_DUST);
    }

    /** Passage en mode katana : elle lance l'epee en l'air des deux mains, l'epee tournoie, elle la rattrape. */
    static void katanaToss(Player p) {
        ItemStack katana = new ItemStack(Material.IRON_SWORD);
        Puppet pu = Puppet.show(p, "blake", katana, "debout");
        if (pu == null) return;
        pu.pose("blake_katana_tient", 3);
        pu.endAfter(30, 4);
        new org.bukkit.scheduler.BukkitRunnable() {
            int t = 0;
            org.bukkit.entity.ItemDisplay flying;
            @Override public void run() {
                t++;
                if (Puppet.of(p) != pu || !p.isOnline() || p.isDead()) { if (flying != null) flying.remove(); cancel(); return; }
                if (t == 5) pu.pose("blake_katana_lance", 2);
                if (t == 7) {
                    pu.held(null);
                    Location from = p.getLocation().add(0, 2.4, 0);
                    from.setPitch(0);
                    flying = p.getWorld().spawn(from, org.bukkit.entity.ItemDisplay.class, d -> {
                        d.setPersistent(false);
                        d.setItemStack(katana);
                        d.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.NONE);
                        d.setTeleportDuration(1);
                        d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.Quaternionf(), new org.joml.Vector3f(0.85f, 0.85f, 0.85f), new org.joml.Quaternionf()));
                    });
                    p.getWorld().playSound(from, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.8f);
                }
                if (flying != null && t > 7 && t <= 20) {
                    // Monte de deux blocs puis redescend vers ses mains, en tournant sur elle-meme.
                    double k = (t - 7) / 13.0;
                    Location at = p.getLocation().add(0, 2.4 + 2.2 * 4 * k * (1 - k), 0);
                    at.setPitch(0);
                    flying.teleport(at);
                    flying.setInterpolationDelay(0);
                    flying.setInterpolationDuration(1);
                    flying.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(),
                            new org.joml.Quaternionf().rotationZ((float) (k * Math.PI * 4)), new org.joml.Vector3f(0.85f, 0.85f, 0.85f), new org.joml.Quaternionf()));
                }
                if (t == 17) pu.pose("blake_katana_attrape", 3);
                if (t == 20) {
                    if (flying != null) { flying.remove(); flying = null; }
                    pu.held(katana);
                    p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1.5f);
                }
                if (t >= 30) cancel();
            }
        }.runTaskTimer(plugin(), 1L, 1L);
    }

    /**
     * L'Ombre : Blake et ses clones deviennent des silhouettes violettes (pantins teints, contour violet),
     * puis leur apparence revient partie par partie, des jambes a la tete, chaque fois dans un eclat de particules.
     */
    static void shadowReveal(List<LivingEntity> bodies) {
        List<Puppet> puppets = new java.util.ArrayList<>();
        for (LivingEntity e : bodies) {
            ItemStack held = e instanceof Player pl ? pl.getInventory().getItemInMainHand()
                    : e.getEquipment() == null ? null : e.getEquipment().getItemInMainHand();
            Puppet pu = Puppet.show(e, "blake", held, "debout");
            if (pu != null) {
                pu.pose("blake_ombre", 3);
                pu.tint(null, SHADOW);
                pu.glow(null, SHADOW);
                pu.endAfter(REVEAL, 4);
            }
            puppets.add(pu);
        }
        String[][] order = {{"leg_r", "leg_l"}, {"body"}, {"arm_r", "arm_l"}, {"head"}};
        double[] heights = {0.4, 1.0, 1.25, 1.65};
        new org.bukkit.scheduler.BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                for (int i = 0; i < bodies.size(); i++) {
                    LivingEntity e = bodies.get(i);
                    Puppet pu = puppets.get(i);
                    if (!e.isValid() || e.isDead()) continue;
                    Location at = e.getLocation();
                    if (t < 6) e.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 0.9, 0), 6, 0.25, 0.5, 0.25, 0, SHADOW_DUST);
                    int step = (t - 6) / 3;
                    if (t >= 6 && (t - 6) % 3 == 0 && step < order.length) {
                        if (pu != null && Puppet.of(e) == pu) for (String part : order[step]) { pu.tint(part, null); pu.glow(part, null); }
                        Location burst = at.clone().add(0, heights[step], 0);
                        e.getWorld().spawnParticle(Particle.DUST, burst, 14, 0.25, 0.2, 0.25, 0, SHADOW_DUST);
                        e.getWorld().spawnParticle(Particle.WITCH, burst, 6, 0.2, 0.15, 0.2, 0.02);
                        e.getWorld().spawnParticle(Particle.REVERSE_PORTAL, burst, 10, 0.1, 0.1, 0.1, 0.15);
                    }
                    if (t == 16) {
                        e.getWorld().spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 40, 0.6, 0.8, 0.6, 0, SHADOW_DUST);
                        e.getWorld().spawnParticle(Particle.REVERSE_PORTAL, at.clone().add(0, 1, 0), 30, 0.2, 0.4, 0.2, 0.3);
                    }
                }
                if (t == 16 && !bodies.isEmpty() && bodies.get(0).isValid()) {
                    Location l = bodies.get(0).getLocation();
                    l.getWorld().playSound(l, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 0.8f);
                    l.getWorld().playSound(l, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.3f);
                }
                if (t >= REVEAL) cancel();
            }
        }.runTaskTimer(plugin(), 1L, 1L);
    }

    /** Discretion : elle s'enfonce dans une flaque noire et disparait (le pantin reste sur place pendant qu'elle s'en va). */
    static void sinkIntoShadow(Player p) {
        Location at = p.getLocation();
        Glyph puddle = Glyph.spawn(Glyph.ground(at), "flaque_noire", Color.WHITE, 1.9f, Glyph.FLAT, new org.joml.Vector3f());
        if (puddle != null) {
            puddle.spin(0.6f);
            Bukkit.getScheduler().runTaskLater(plugin(), puddle::close, 26L);
        }
        p.getWorld().playSound(at, Sound.ITEM_BUCKET_EMPTY, 0.8f, 0.6f);
        p.getWorld().playSound(at, Sound.BLOCK_HONEY_BLOCK_SLIDE, 1f, 0.5f);
        p.getWorld().spawnParticle(Particle.SQUID_INK, at.clone().add(0, 0.1, 0), 12, 0.4, 0.05, 0.4, 0.02);
        Puppet pu = Puppet.show(p, "blake", null, "debout");
        if (pu == null) return;
        pu.pin(at);
        pu.rest("blake_enfonce_fond");
        pu.pose("blake_enfonce", 3);
        pu.endAfter(19, 1);
        Bukkit.getScheduler().runTaskLater(plugin(), () -> { if (Puppet.of(p) == pu) pu.pose("blake_enfonce_fond", 14); }, 4L);
    }

    /** Instinct Animal : le bond du chat, le corps allonge vers l'avant, les bras tendus comme des pattes. */
    static void pounce(Player p) {
        Puppet current = Puppet.of(p);
        Puppet pu = current != null && "blake_bond_chat".equals(current.currentPose()) ? current
                : current == null ? Puppet.show(p, "blake", null, "debout") : null;
        if (pu == null) return;
        pu.pose("blake_bond_chat", 2);
        pu.endAfter(11, 3);
    }

    /** Instinct Animal : son rugissement, penchee en avant, les mains en griffes, et l'onde qui part d'elle. */
    static void roar(Player p) {
        Location l = p.getLocation();
        p.getWorld().playSound(l, Sound.ENTITY_RAVAGER_ROAR, 1f, 1.45f);
        p.getWorld().playSound(l, Sound.ENTITY_CAT_HISS, 1f, 0.6f);
        Puppet pu = Puppet.show(p, "blake", null, "debout");
        if (pu != null) { pu.pose("blake_rugit", 3); pu.endAfter(24, 5); }
        for (int k = 0; k < 6; k++) {
            final double r = 0.8 + k * 0.7;
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                if (!p.isOnline()) return;
                Location c = p.getLocation().add(0, 1.0, 0);
                for (double a = 0; a < Math.PI * 2; a += 0.35 / r) {
                    p.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), 1, 0, 0.05, 0, 0, SHADOW_DUST);
                }
            }, 3L + k);
        }
    }

    // --------------------------------------------------------------- pouvoirs

    static final class Ombre extends ActiveAbility {
        Ombre() {
            super("blake_ombre", "Ombre", "Blake cree 3 copies d'elle-meme qui courent vers l'adversaire sans attaquer. S'il frappe une copie, il est aveugle 4 secondes ; si une copie l'atteint, elle se dissipe.", Material.BLACK_DYE, 20);
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            LivingEntity target = Effects.nearestEnemy(p, 30);
            if (target == null) { Msg.actionBar(p, "<red>Ombre : aucun adversaire a moins de 30 blocs."); return false; }
            // Blake et ses trois clones naissent en silhouettes violettes, puis se revelent (les clones partent ensuite).
            List<LivingEntity> bodies = new java.util.ArrayList<>();
            bodies.add(p);
            for (int i = 0; i < 3; i++) {
                bodies.add(Clones.spawn(p, p, target, 20 * 15, (clone, attacker) -> {
                    Effects.give(attacker, PotionEffectType.BLINDNESS, 0, 80);
                    Msg.actionBar(attacker, "<dark_gray>C'etait un clone de Blake !");
                }, REVEAL + 4));
            }
            shadowReveal(bodies);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1.2f);
            return true;
        }
    }

    /** Invisibilite totale. Jauge de 100 : -5 par seconde active, +1 par seconde inactive. Revele des qu'elle frappe. */
    static final class Discretion extends ToggleAbility {
        Discretion() {
            super("blake_discretion", "Discretion", "Blake devient totalement invisible. Des qu'elle frappe quelqu'un elle redevient visible. Jauge de 100 : -5 par seconde active, +1 par seconde au repos.",
                    Material.PHANTOM_MEMBRANE, 5.0, 1.0);
        }

        @Override
        public boolean onEnable(Player p, CfPlayer cf, AbilityState st) {
            // L'invisibilite d'abord : le pantin qui s'enfonce ne l'enlevera pas en finissant.
            Effects.refresh(p, PotionEffectType.INVISIBILITY, 0, 45);
            Stealth.hideFromAll(p, "blake_discretion", v -> Effects.isEnemy(p, v));
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.6f, 1.6f);
            sinkIntoShadow(p);
            return true;
        }

        @Override
        public void tickActive(Player p, CfPlayer cf, AbilityState st) {
            Effects.refresh(p, PotionEffectType.INVISIBILITY, 0, 45);
            Stealth.hideFromAll(p, "blake_discretion", v -> Effects.isEnemy(p, v));
            Stealth.hideArmor(p);
        }

        @Override
        public void onDisable(Player p, CfPlayer cf, AbilityState st) {
            Stealth.reveal(p, "blake_discretion");
            Stealth.showArmor(p);
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
        }

        @Override
        public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) {
            if (st.isEnabled()) {
                st.setEnabled(false);
                onDisable(attacker, plugin().players().get(attacker), st);
                Msg.actionBar(attacker, "<gray>Discretion rompue.");
            }
        }
    }

    /** Au-dela de 20 blocs, Blake est invisible aux yeux des ennemis. */
    static final class Effacement extends PassiveAbility {
        Effacement() {
            super("blake_effacement", "Effacement", "A plus de 20 blocs d'un ennemi, Blake est invisible a ses yeux.", Material.INK_SAC);
        }

        @Override
        public void tick(Player p, CfPlayer cf, AbilityState st) {
            Stealth.hideFromAll(p, "blake_effacement", v -> Effects.isEnemy(p, v)
                    && (!v.getWorld().equals(p.getWorld()) || v.getLocation().distanceSquared(p.getLocation()) > 400));
        }

        @Override
        public void onUnequip(Player p, CfPlayer cf) {
            Stealth.reveal(p, "blake_effacement");
        }
    }

    // ----------------------------------------------------------------- ultime

    /** 30 secondes de Vitesse II ; pendant ce temps, clic droit sur l'ultime = bond de 2 blocs. */
    static final class InstinctAnimal extends UltimateAbility implements CustomUse {
        InstinctAnimal() {
            super("blake_ult", "Instinct Animal", "Pendant 30 secondes Blake a Vitesse II et peut bondir de 2 blocs en avant en faisant clic droit sur cet item.", Material.RABBIT_FOOT);
        }

        @Override
        public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            if (System.currentTimeMillis() > (long) st.get("until", 0L)) return false; // lancement standard
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.01) return true;
            p.setVelocity(dir.normalize().multiply(0.75).setY(0.3));
            Ruby.noFallUntil(p, 1500);
            pounce(p);
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 8, 0.2, 0.1, 0.2, 0.02);
            p.playSound(p.getLocation(), Sound.ENTITY_RABBIT_JUMP, 1f, 0.8f);
            return true;
        }

        /** Un bond pendant l'ultime n'est pas son lancement : il a son propre son, plus court. */
        @Override
        public String useSoundKey() { return "blake_ult_bond"; }

        @Override
        public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            st.set("until", System.currentTimeMillis() + 30_000L);
            Effects.give(p, PotionEffectType.SPEED, 1, 20 * 30);
            roar(p);
            Msg.send(p, "<light_purple>Instinct Animal : clic droit sur l'item pour bondir pendant 30 s.");
            return true;
        }

        @Override
        public String barText(Player p, CfPlayer cf, AbilityState st) {
            long left = (long) st.get("until", 0L) - System.currentTimeMillis();
            return left > 0 ? "<light_purple>Instinct <white>" + (int) Math.ceil(left / 1000.0) + "s <gray>(clic = bond)" : null;
        }
    }
}
