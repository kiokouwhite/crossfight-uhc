package com.kiokouwhite.crossfight.character.fireemblem;

import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.AbstractArrow;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Takumi - Path of Hunt.
 *
 * Hypothese : l'arc a Infinite et une fleche est fournie, sinon un archer sans
 * fleches est injouable en debut de partie.
 */
public final class Takumi extends Character implements Listener {

    static final NamespacedKey TEMP_SWORD = new NamespacedKey("crossfight", "takumi_sword");
    static final int BASE_POWER = 2;

    public Takumi() {
        super("takumi", "Takumi", Voie.CHASSE, Material.BOW);
        addAbility(new YumiFujin().bindToWeapon());
        addAbility(new ContreRapproche());
        addAbility(new Vengeance());
        addAbility(new Furie());
        setUltimate(new RayonDemoniaque());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.bow(BASE_POWER, true, "<gold><bold>Yumi Fujin", List.of());
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Yumi Fujin (Power II) : Lenteur 10 s",
                "<white>Contre Rapproche : l'arc devient une epee 9 dmg 30 s",
                "<white>Vengeance : +1 Power par 3 coeurs perdus (5 s)",
                "<white>Furie : +1 Power et Vitesse II contre 5 coeurs",
                "<light_purple>Ultime : rayon de verre 10x30, 5 coeurs");
    }

    /** Recalcule le niveau de Puissance de l'arc selon les bonus temporaires. */
    static void refreshPower(Player p) {
        var engine = plugin().abilities();
        var ch = plugin().characters().character("takumi");
        int bonus = 0;
        bonus += engine.state(p, ch.ability("takumi_vengeance")).get("bonus", 0);
        bonus += engine.state(p, ch.ability("takumi_furie")).get("bonus", 0);
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if ("takumi".equals(Items.tag(it, AbilityEngine.WEAPON_KEY))) Weapons.setPower(it, BASE_POWER + bonus);
        }
    }

    static boolean isBowLocked(Player p) {
        var st = plugin().abilities().state(p, plugin().characters().character("takumi").ability("takumi_contre"));
        return System.currentTimeMillis() < (long) st.get("lockedUntil", 0L);
    }

    @EventHandler
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        CfPlayer cf = plugin().players().get(p);
        if (cf.character() == null || !"takumi".equals(cf.character().id())) return;
        if (isBowLocked(p)) {
            e.setCancelled(true);
            Msg.actionBar(p, "<red>Ton arc est range pendant Contre Rapproche.");
        }
    }

    private static com.kiokouwhite.crossfight.CrossfightPlugin plugin() {
        return com.kiokouwhite.crossfight.CrossfightPlugin.get();
    }

    // ------------------------------------------------------------------ arme

    static final class YumiFujin extends PassiveAbility {
        YumiFujin() {
            super("takumi_yumi", "Yumi Fujin", "Les fleches infligent Lenteur pendant 10 secondes.", Material.ARROW);
        }

        @Override
        public void onDealDamage(Player attacker, org.bukkit.entity.LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) {
            if (e.getDamager() instanceof AbstractArrow) {
                Effects.give(victim, PotionEffectType.SLOWNESS, 0, 20 * 10);
            }
        }

        @Override
        public void onEquip(Player p, CfPlayer cf) {
            // Une fleche pour l'Infinite.
            boolean has = false;
            for (ItemStack it : p.getInventory().getContents()) if (it != null && it.getType() == Material.ARROW) { has = true; break; }
            if (!has) p.getInventory().addItem(new ItemStack(Material.ARROW));
        }
    }

    // --------------------------------------------------------------- pouvoirs

    static final class ContreRapproche extends ActiveAbility {
        ContreRapproche() {
            super("takumi_contre", "Contre Rapproche", "Takumi jette son arc au sol : le Yumi Fujin est remplace par une epee de 9 degats pendant 30 secondes, puis il le ramasse.",
                    Material.IRON_SWORD, 90);
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            var inv = p.getInventory();
            int bowSlot = -1;
            for (int i = 0; i < inv.getSize(); i++) {
                if ("takumi".equals(Items.tag(inv.getItem(i), AbilityEngine.WEAPON_KEY))) { bowSlot = i; break; }
            }
            if (bowSlot < 0) {
                Msg.actionBar(p, "<red>Ton arc est introuvable.");
                return false;
            }
            ItemStack sword = Weapons.melee(Material.IRON_SWORD, 9, "<red><bold>Lame de Takumi", Items.wrap("Le Yumi Fujin revient dans 30 secondes."));
            Items.tag(sword, TEMP_SWORD, "1");
            Items.tag(sword, AbilityEngine.WEAPON_KEY, "takumi_temp");
            st.set("bow", inv.getItem(bowSlot).clone());
            inv.setItem(bowSlot, sword);
            st.set("lockedUntil", System.currentTimeMillis() + 30_000L);
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 0.8f);
            Msg.actionBar(p, "<red>Lame en main ! Ton arc revient dans 30 s.");
            final ItemStack bowLook = ((ItemStack) st.get("bow", null)).clone();
            throwBow(p, bowLook);
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                boolean stillOut = st.get("bow", null) != null;
                restoreBow(p, st);
                // Fin normale des 30 secondes : l'arc revient a ses pieds et il le ramasse.
                if (stillOut && p.isOnline() && !p.isDead()) pickUpBow(p, bowLook);
                else removeGroundBow(p.getUniqueId());
            }, 20L * 30);
            return true;
        }

        // ------------------------------------------------ l'arc jete puis ramasse

        /** L'arc que chaque Takumi a jete au sol, le temps de la lame. */
        static final Map<UUID, org.bukkit.entity.ItemDisplay> GROUND_BOWS = new java.util.HashMap<>();

        static void removeGroundBow(UUID id) {
            var d = GROUND_BOWS.remove(id);
            if (d != null) d.remove();
        }

        /** L'image de l'arc, couchee sur le sol et tournee de {@code yaw} degres, a {@code y} blocs de haut. */
        private static org.bukkit.util.Transformation lying(float yaw, float tilt, float y) {
            var rot = new org.joml.Quaternionf().rotationY((float) Math.toRadians(yaw)).rotateX((float) Math.toRadians(90 - tilt));
            return new org.bukkit.util.Transformation(new org.joml.Vector3f(0, y, 0), rot, new org.joml.Vector3f(0.85f, 0.85f, 0.85f), new org.joml.Quaternionf());
        }

        /** Le dessus du premier bloc plein sous ce point (au plus 8 blocs plus bas). */
        private static Location ground(Location from) {
            Block b = from.getBlock();
            for (int i = 0; i < 8 && b.isPassable(); i++) b = b.getRelative(0, -1, 0);
            Location l = from.clone();
            l.setY(b.isPassable() ? from.getY() : b.getY() + 1);
            l.setYaw(0); l.setPitch(0);
            return l;
        }

        /** Devant le joueur, un peu sur sa droite : la ou tombe sa main quand il se baisse. */
        private static Location inFront(Player p, double forward, double right) {
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
            dir.normalize();
            Vector side = new Vector(-dir.getZ(), 0, dir.getX());
            return ground(p.getLocation().add(0, 0.5, 0).add(dir.multiply(forward)).add(side.multiply(right)));
        }

        /** Takumi leve son arc bien haut et le jette violemment au sol, ou il reste jusqu'a la fin de la lame. */
        private static void throwBow(Player p, ItemStack bowLook) {
            var puppet = com.kiokouwhite.crossfight.util.Puppet.show(p, "takumi", bowLook, "takumi_arc");
            if (puppet == null) return;
            puppet.pose("takumi_arc_leve", 4);
            puppet.endAfter(19, 4);
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                if (com.kiokouwhite.crossfight.util.Puppet.of(p) != puppet) return;
                puppet.pose("takumi_arc_jete", 2);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, 0.7f);
            }, 7L);
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                if (com.kiokouwhite.crossfight.util.Puppet.of(p) != puppet) return;
                // L'arc quitte sa main et va s'ecraser devant lui.
                Location hand = puppet.hand();
                puppet.held(null);
                Location spot = inFront(p, 1.0, 0.3);
                float yaw = (float) (Math.random() * 360), drop = (float) Math.max(0.1, hand.getY() - spot.getY());
                World w = p.getWorld();
                removeGroundBow(p.getUniqueId());
                var bow = w.spawn(spot, org.bukkit.entity.ItemDisplay.class, d -> {
                    d.setPersistent(false);
                    d.setItemStack(bowLook.clone());
                    d.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.NONE);
                    d.setTransformation(lying(yaw - 60, 70, drop));
                    d.setViewRange(1.5f);
                });
                GROUND_BOWS.put(p.getUniqueId(), bow);
                // Chute, impact, rebond, puis il retombe a plat.
                int[] when = {2, 4, 7};
                float[][] step = {{yaw, 10, 0.03f}, {yaw + 45, 35, 0.4f}, {yaw + 80, 0, 0.03f}};
                for (int i = 0; i < when.length; i++) {
                    final int k = i;
                    Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                        if (!bow.isValid()) return;
                        bow.setInterpolationDelay(0);
                        bow.setInterpolationDuration(k == 0 ? 2 : 3);
                        bow.setTransformation(lying(step[k][0], step[k][1], step[k][2]));
                    }, when[i]);
                }
                for (int hit : new int[]{4, 10}) {
                    final boolean first = hit == 4;
                    Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                        if (!bow.isValid()) return;
                        Location c = spot.clone().add(0, 0.1, 0);
                        w.playSound(c, Sound.BLOCK_WOOD_BREAK, first ? 1.2f : 0.5f, first ? 0.6f : 0.9f);
                        if (first) {
                            w.playSound(c, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 0.6f, 1.3f);
                            w.spawnParticle(Particle.CRIT, c, 14, 0.25, 0.1, 0.25, 0.25);
                            w.spawnParticle(Particle.CLOUD, c, 5, 0.2, 0.05, 0.2, 0.03);
                            Block under = spot.clone().add(0, -0.5, 0).getBlock();
                            if (!under.isPassable()) w.spawnParticle(Particle.BLOCK, c, 18, 0.25, 0.05, 0.25, under.getBlockData());
                        }
                    }, hit);
                }
            }, 8L);
        }

        /** L'arc glisse jusqu'a ses pieds dans un souffle de vent (c'est l'arc du dieu du vent), il se baisse et l'attrape. */
        private static void pickUpBow(Player p, ItemStack bowLook) {
            var puppet = com.kiokouwhite.crossfight.util.Puppet.show(p, "takumi", null, "debout");
            if (puppet == null) { removeGroundBow(p.getUniqueId()); return; }
            puppet.rest("takumi_arc");
            puppet.pose("takumi_arc_ramasse", 4);
            puppet.endAfter(20, 2);
            World w = p.getWorld();
            Location spot = inFront(p, 0.67, 0.29);
            org.bukkit.entity.ItemDisplay old = GROUND_BOWS.get(p.getUniqueId());
            final org.bukkit.entity.ItemDisplay bow;
            if (old != null && old.isValid() && old.getWorld().equals(w)) {
                bow = old;
                w.spawnParticle(Particle.CLOUD, bow.getLocation().add(0, 0.2, 0), 8, 0.3, 0.1, 0.3, 0.05);
            } else {
                removeGroundBow(p.getUniqueId());
                bow = w.spawn(spot, org.bukkit.entity.ItemDisplay.class, d -> {
                    d.setPersistent(false);
                    d.setItemStack(bowLook.clone());
                    d.setItemDisplayTransform(org.bukkit.entity.ItemDisplay.ItemDisplayTransform.NONE);
                    d.setTransformation(lying((float) (Math.random() * 360), 0, 0.03f));
                    d.setViewRange(1.5f);
                });
                GROUND_BOWS.put(p.getUniqueId(), bow);
            }
            bow.setTeleportDuration(3);
            w.playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 0.8f, 1.7f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || p.isDead() || !bow.isValid() || com.kiokouwhite.crossfight.util.Puppet.of(p) != puppet) {
                        removeGroundBow(p.getUniqueId());
                        cancel();
                        return;
                    }
                    if (t < 9) {
                        // L'arc file au ras du sol jusqu'a l'endroit ou sa main va se poser.
                        bow.teleport(inFront(p, 0.67, 0.29));
                        w.spawnParticle(Particle.CLOUD, bow.getLocation().add(0, 0.1, 0), 2, 0.15, 0.05, 0.15, 0.02);
                        return;
                    }
                    // Il le tient : l'arc quitte le sol pour sa main, et il se releve.
                    removeGroundBow(p.getUniqueId());
                    puppet.held(bowLook);
                    puppet.pose("takumi_arc", 4);
                    w.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1.1f);
                    cancel();
                }
            }.runTaskTimer(plugin(), 1L, 1L);
        }

        /** Remet le Yumi Fujin a la place de la lame. */
        static void restoreBow(Player p, AbilityState st) {
            ItemStack bow = st.get("bow", null);
            st.remove("bow");
            st.set("lockedUntil", 0L);
            if (bow == null) return;
            var inv = p.getInventory();
            boolean placed = false;
            for (int i = 0; i < inv.getSize(); i++) {
                if (Items.tag(inv.getItem(i), TEMP_SWORD) != null) {
                    inv.setItem(i, bow);
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                var left = inv.addItem(bow);
                if (!left.isEmpty()) inv.setItem(0, bow);
            }
            if (p.isOnline()) {
                refreshPower(p);
                Msg.actionBar(p, "<gray>Le Yumi Fujin est de retour.");
                p.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 1f, 1.2f);
            }
        }

        @Override
        public void onUnequip(Player p, CfPlayer cf) {
            // Mort ou fin de partie pendant la lame : on remet l'arc pour que le prochain equipement soit propre.
            restoreBow(p, plugin().abilities().state(p, this));
            removeGroundBow(p.getUniqueId());
        }
    }

    static final class Vengeance extends ActiveAbility {
        Vengeance() {
            super("takumi_vengeance", "Vengeance", "Takumi tend les bras et ils s'embrasent : un niveau de Puissance par tranche de 3 coeurs perdus, pendant 5 secondes.",
                    Material.GLOWSTONE_DUST, 30);
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            double max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue();
            int lostHearts = (int) Math.floor((max - p.getHealth()) / 2.0);
            int bonus = lostHearts / 3;
            if (bonus <= 0) {
                Msg.actionBar(p, "<red>Pas assez de coeurs perdus (3 minimum).");
                return false;
            }
            st.set("bonus", bonus);
            refreshPower(p);
            Msg.actionBar(p, "<red>Vengeance : Puissance +" + bonus + " pendant 5 s");
            p.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f);
            igniteArms(p);
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                st.set("bonus", 0);
                if (p.isOnline()) refreshPower(p);
            }, 100L);
            return true;
        }

        /** Les bras s'embrasent 6 ticks apres le lancement, la pose dure une seconde et demie. */
        private static final int IGNITE = 6, POSE = 30, DURATION = 100;

        /**
         * Takumi tend les bras devant lui et ils s'embrasent, du poing a l'epaule : seulement les bras.
         * Ils restent en feu tant que dure la Vengeance.
         */
        private static void igniteArms(Player p) {
            var puppet = com.kiokouwhite.crossfight.util.Puppet.show(p, "takumi", null, "debout");
            if (puppet != null) {
                puppet.pose("takumi_vengeance", 4);
                puppet.endAfter(POSE, 5);
            }
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    if (!p.isOnline() || p.isDead() || ++t > DURATION) { cancel(); return; }
                    World w = p.getWorld();
                    boolean posing = puppet != null && com.kiokouwhite.crossfight.util.Puppet.of(p) == puppet;
                    if (t == IGNITE) {
                        // Le feu prend aux poings.
                        w.playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.1f, 0.8f);
                        w.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.7f, 0.7f);
                    }
                    if (t < IGNITE) return;
                    if (t % 8 == 0) w.playSound(p.getLocation(), Sound.BLOCK_FIRE_AMBIENT, posing ? 1.0f : 0.5f, 1.0f);
                    for (boolean right : new boolean[]{true, false}) {
                        if (posing) {
                            // Pendant la pose : le feu remonte du poing vers l'epaule, puis tout le bras flambe.
                            double reach = Math.min(1.0, (t - IGNITE) / 8.0);
                            for (double f = 1.0; f >= 1.0 - reach * 0.9; f -= 0.12) {
                                Location at = puppet.armPoint(right, f);
                                w.spawnParticle(Particle.FLAME, at, 2, 0.05, 0.05, 0.05, 0.012);
                                if (Math.random() < 0.3) w.spawnParticle(Particle.SMALL_FLAME, at.clone().add(0, 0.12, 0), 1, 0.04, 0.04, 0.04, 0.01);
                            }
                            if (t == IGNITE) w.spawnParticle(Particle.LAVA, puppet.armPoint(right, 1.0), 3, 0.05, 0.05, 0.05);
                        } else if (t % 2 == 0) {
                            // Ensuite : ses bras, le long du corps, continuent de bruler.
                            Location feet = p.getLocation();
                            Vector dir = feet.getDirection().setY(0);
                            if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
                            dir.normalize();
                            Vector side = new Vector(-dir.getZ(), 0, dir.getX()).multiply(right ? 0.34 : -0.34);
                            for (double h = 0.75; h <= 1.3; h += 0.27) {
                                w.spawnParticle(Particle.FLAME, feet.clone().add(side).add(0, h, 0), 1, 0.04, 0.05, 0.04, 0.008);
                            }
                        }
                    }
                }
            }.runTaskTimer(plugin(), 1L, 1L);
        }
    }

    static final class Furie extends ActiveAbility {
        Furie() {
            super("takumi_furie", "Furie", "Takumi frappe le sol et s'embrase : un niveau de Puissance et Vitesse II pendant 10 secondes, mais il perd 5 coeurs.",
                    Material.BLAZE_POWDER, 10);
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            if (p.getHealth() <= 10.0) {
                Msg.actionBar(p, "<red>Pas assez de vie pour Furie.");
                return false;
            }
            p.setHealth(p.getHealth() - 10.0);
            st.set("bonus", 1);
            refreshPower(p);
            Effects.give(p, PotionEffectType.SPEED, 1, 20 * 10);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.7f);
            strikeGround(p);
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                st.set("bonus", 0);
                if (p.isOnline()) refreshPower(p);
            }, 200L);
            return true;
        }

        /** Le coup part 8 ticks apres le lancement ; Takumi reste sur place le temps de frapper. */
        private static final int IMPACT = 8, HELD = 14;

        /**
         * Takumi leve le poing et frappe le sol : des flammes jaillissent de l'impact et l'engloutissent,
         * puis quelques-unes continuent de l'entourer tant que dure la Furie.
         */
        private static void strikeGround(Player p) {
            Effects.root(p, HELD);
            var puppet = com.kiokouwhite.crossfight.util.Puppet.show(p, "takumi", null, "debout");
            if (puppet != null) {
                puppet.pose("takumi_furie_leve", 3);
                puppet.endAfter(24, 5);
            }
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    if (!p.isOnline() || p.isDead() || ++t > 200) { cancel(); return; }
                    World w = p.getWorld();
                    Location feet = p.getLocation();
                    if (t == IMPACT - 2 && com.kiokouwhite.crossfight.util.Puppet.of(p) == puppet && puppet != null) puppet.pose("takumi_furie_frappe", 2);
                    if (t == IMPACT) {
                        // L'impact : le sol tremble et crache du feu.
                        Vector dir = feet.getDirection().setY(0);
                        if (dir.lengthSquared() < 1e-4) dir = new Vector(0, 0, 1);
                        Location fist = feet.clone().add(dir.normalize().multiply(0.6)).add(0, 0.1, 0);
                        w.playSound(fist, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.6f);
                        w.playSound(fist, Sound.ITEM_FIRECHARGE_USE, 1.2f, 0.6f);
                        w.playSound(fist, Sound.ENTITY_BLAZE_SHOOT, 0.8f, 0.5f);
                        w.spawnParticle(Particle.EXPLOSION, fist, 1);
                        w.spawnParticle(Particle.LAVA, fist, 14, 0.4, 0.1, 0.4);
                        Block under = feet.clone().add(0, -0.5, 0).getBlock();
                        if (!under.isPassable()) w.spawnParticle(Particle.BLOCK, fist, 30, 0.6, 0.1, 0.6, under.getBlockData());
                    }
                    if (t >= IMPACT && t < IMPACT + 7) {
                        // L'onde de feu qui s'elargit au ras du sol.
                        double r = 0.5 + (t - IMPACT) * 0.4;
                        for (double a = 0; a < Math.PI * 2; a += Math.PI / 14) {
                            w.spawnParticle(Particle.FLAME, feet.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r), 1, 0.03, 0.03, 0.03, 0.01);
                        }
                    }
                    if (t >= IMPACT && t < IMPACT + 26) {
                        // La colonne de flammes qui monte en vrille et l'engloutit.
                        int age = t - IMPACT;
                        double top = Math.min(2.6, 0.4 + age * 0.25);
                        for (int k = 0; k < 14; k++) {
                            double h = Math.random() * top, a = age * 0.7 + k * (Math.PI * 2 / 14) + h * 1.5;
                            double r = 0.75 - h * 0.12;
                            w.spawnParticle(Particle.FLAME, feet.clone().add(Math.cos(a) * r, h, Math.sin(a) * r), 1, 0.02, 0.02, 0.02, 0.015);
                        }
                        w.spawnParticle(Particle.FLAME, feet.clone().add(0, top * 0.5, 0), 10, 0.3, top * 0.35, 0.3, 0.03);
                        w.spawnParticle(Particle.SMALL_FLAME, feet.clone().add(0, top, 0), 4, 0.25, 0.15, 0.25, 0.02);
                        if (age % 5 == 0) w.spawnParticle(Particle.LAVA, feet.clone().add(0, 0.5, 0), 2, 0.4, 0.2, 0.4);
                        if (age % 6 == 0) w.playSound(feet, Sound.BLOCK_FIRE_AMBIENT, 1.2f, 0.8f);
                    } else if (t >= IMPACT + 26 && t % 3 == 0) {
                        // Ensuite : quelques flammes autour de lui, tant que dure la Furie.
                        w.spawnParticle(Particle.FLAME, feet.clone().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.01);
                    }
                }
            }.runTaskTimer(plugin(), 1L, 1L);
        }
    }

    // ----------------------------------------------------------------- ultime

    static final class RayonDemoniaque extends UltimateAbility implements com.kiokouwhite.crossfight.ability.EnemyCastable {

        private static final int LENGTH = 30;
        private static final int HALF_WIDTH = 5;        // 10 blocs de large
        private static final int CHARGE_TICKS = 80;     // 4 secondes
        private static final int BLOCKS_PER_TICK = 3;   // le rayon jaillit en 10 ticks
        private static final int HOLD_TICKS = 16;
        private static final int FADE_PER_TICK = 4;
        private static final Particle.DustOptions PINK = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 120, 220), 1.6f);
        private static final Particle.DustOptions MAGENTA = new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 40, 200), 2.2f);
        private static final Particle.DustOptions WHITE = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 240, 255), 1.0f);

        RayonDemoniaque() {
            super("takumi_ult", "Rayon Demoniaque",
                    "Takumi charge 4 secondes puis un rayon de verre de 10 blocs de large jaillit sur 30 blocs, infligeant 5 coeurs a tous les ennemis visibles sur son passage.",
                    Material.AMETHYST_SHARD);
        }

        @Override
        public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            charge(p, null, Effects.enemiesOf(p, null));
            return true;
        }

        @Override
        public boolean castOn(org.bukkit.entity.LivingEntity caster, Player target, AbilityState st) {
            charge(caster, target, Effects.enemiesOf(caster, target));
            return true;
        }

        /** Charge de 4 secondes puis tir. Avec une cible, le rayon part vers elle (direction figee au tir). */
        static void charge(org.bukkit.entity.LivingEntity p, org.bukkit.entity.LivingEntity target, java.util.function.Predicate<org.bukkit.entity.LivingEntity> isTarget) {
            Effects.give(p, PotionEffectType.SLOWNESS, 2, CHARGE_TICKS);
            Effects.say(p, "<light_purple>Rayon Demoniaque en charge...");
            final World w = p.getWorld();
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    if (!p.isValid() || p.isDead()) { cancel(); return; }
                    ticks++;
                    // L'energie rose converge vers la pointe de l'arc.
                    Location tip = p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.2));
                    double r = 2.5 - ticks / (double) CHARGE_TICKS * 2.2;
                    for (int i = 0; i < 4; i++) {
                        double a = Math.random() * Math.PI * 2, b = Math.random() * Math.PI;
                        Location l = tip.clone().add(Math.sin(b) * Math.cos(a) * r, Math.cos(b) * r, Math.sin(b) * Math.sin(a) * r);
                        w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, ticks % 2 == 0 ? PINK : MAGENTA);
                    }
                    w.spawnParticle(Particle.DUST, tip, 2, 0.1, 0.1, 0.1, 0, WHITE);
                    if (ticks % 10 == 0) w.playSound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1f, 0.5f + ticks / 80f);
                    if (ticks >= CHARGE_TICKS) {
                        cancel();
                        fire(p, w, Effects.towards(p, target), isTarget);
                    }
                }
            }.runTaskTimer(plugin(), 0L, 1L);
        }

        private static void fire(org.bukkit.entity.LivingEntity p, World w, Vector dir, java.util.function.Predicate<org.bukkit.entity.LivingEntity> isTarget) {
            final Vector axis = Math.abs(dir.getX()) >= Math.abs(dir.getZ())
                    ? new Vector(Math.signum(dir.getX()), 0, 0) : new Vector(0, 0, Math.signum(dir.getZ()));
            final Vector side = new Vector(-axis.getZ(), 0, axis.getX());
            final Location origin = p.getEyeLocation().getBlock().getLocation(); // hauteur des yeux
            final Map<Block, BlockState> placed = new LinkedHashMap<>();
            final List<Block> order = new ArrayList<>();
            final List<UUID> hit = new ArrayList<>();
            Effects.root(p, 40);
            w.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 1f, 0.5f);
            // Le rayon part : sa seconde replique.
            if (p instanceof Player tp) com.kiokouwhite.crossfight.util.Sounds.voice(tp, "takumi_ult_tir");
            w.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.8f);

            new BukkitRunnable() {
                int tick = 0;
                int reach = 0;   // longueur deja construite
                int faded = 0;   // nombre de blocs deja retires

                @Override public void run() {
                    try {
                        tick++;
                        if (!p.isValid()) { restoreAll(); cancel(); return; }
                        if (reach < LENGTH) {
                            for (int i = 0; i < BLOCKS_PER_TICK && reach < LENGTH; i++) { reach++; buildSlice(reach); }
                            strike(reach);
                            Location front = origin.clone().add(0.5, 0.5, 0.5).add(axis.clone().multiply(reach));
                            w.spawnParticle(Particle.DUST, front, 30, Math.abs(side.getX()) * HALF_WIDTH + 0.5, 1.5, Math.abs(side.getZ()) * HALF_WIDTH + 0.5, 0, PINK);
                            w.spawnParticle(Particle.END_ROD, front, 6, Math.abs(side.getX()) * HALF_WIDTH, 1, Math.abs(side.getZ()) * HALF_WIDTH, 0.05);
                            if (reach % 6 == 0) w.playSound(front, Sound.BLOCK_GLASS_PLACE, 1f, 1.6f);
                        } else if (tick < LENGTH / BLOCKS_PER_TICK + HOLD_TICKS) {
                            // Le rayon reste allume : brume rose et etincelles le long du faisceau.
                            for (int i = 0; i < 6; i++) {
                                Location l = origin.clone().add(0.5, 0.5, 0.5).add(axis.clone().multiply(Math.random() * LENGTH))
                                        .add(side.clone().multiply(Math.random() * 2 * HALF_WIDTH - HALF_WIDTH)).add(0, Math.random() * 4 - 2, 0);
                                w.spawnParticle(Particle.DUST, l, 1, 0.3, 0.3, 0.3, 0, Math.random() < 0.5 ? PINK : MAGENTA);
                            }
                        } else {
                            // Dissolution depuis la base vers la pointe.
                            for (int i = 0; i < FADE_PER_TICK * HALF_WIDTH * 2 && faded < order.size(); i++) {
                                Block b = order.get(faded++);
                                BlockState state = placed.remove(b);
                                if (state != null) state.update(true, false);
                            }
                            if (faded >= order.size()) { restoreAll(); cancel(); }
                        }
                    } catch (Exception ex) {
                        plugin().getLogger().warning("Rayon Demoniaque interrompu : " + ex);
                        restoreAll();
                        cancel();
                    }
                }

                /** Une tranche du rayon : 10 de large, 3 de haut, coeur blanc, corps rose, bords magenta. */
                void buildSlice(int along) {
                    for (int lateral = -HALF_WIDTH + 1; lateral <= HALF_WIDTH; lateral++) {
                        int dist = Math.abs(lateral - (lateral > 0 ? 1 : 0)); // 0 au centre, 4 au bord
                        for (int up = -1; up <= 1; up++) {
                            boolean edge = dist == 4 || up != 0;
                            // Bords arrondis : pas de coins sur les couches haute et basse.
                            if (up != 0 && dist == 4) continue;
                            Material m = (dist <= 1 && up == 0) ? Material.WHITE_STAINED_GLASS
                                    : (dist <= 2 && up == 0) || (dist <= 1) ? Material.PINK_STAINED_GLASS
                                    : Material.MAGENTA_STAINED_GLASS;
                            int x = origin.getBlockX() + axis.getBlockX() * along + side.getBlockX() * lateral;
                            int y = origin.getBlockY() + up;
                            int z = origin.getBlockZ() + axis.getBlockZ() * along + side.getBlockZ() * lateral;
                            Block b = w.getBlockAt(x, y, z);
                            if (!(b.getType().isAir() || b.isReplaceable()) || placed.containsKey(b)) continue;
                            placed.put(b, b.getState());
                            order.add(b);
                            b.setType(m, false);
                        }
                    }
                }

                /** Les ennemis atteints par le front du rayon prennent 5 coeurs. */
                void strike(int reachNow) {
                    Location base = origin.clone().add(0.5, 0.5, 0.5);
                    for (org.bukkit.entity.LivingEntity enemy : Effects.within(base.clone().add(axis.clone().multiply(reachNow / 2.0)), reachNow / 2.0 + HALF_WIDTH + 2, isTarget)) {
                        if (enemy == p || hit.contains(enemy.getUniqueId())) continue;
                        Vector rel = enemy.getLocation().add(0, 1, 0).toVector().subtract(base.toVector());
                        double along = rel.dot(axis), lat = rel.dot(side);
                        if (along < 0.5 || along > reachNow + 0.5 || Math.abs(lat) > HALF_WIDTH + 0.5 || Math.abs(rel.getY()) > 4) continue;
                        if (!Effects.canSee(p, enemy)) continue;
                        hit.add(enemy.getUniqueId());
                        Effects.damage(enemy, 10.0, p);
                        enemy.getWorld().spawnParticle(Particle.DUST, enemy.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0, WHITE);
                        enemy.getWorld().playSound(enemy.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.7f);
                        Effects.say(enemy, "<light_purple>Frappe par le Rayon Demoniaque !");
                    }
                }

                void restoreAll() {
                    for (int i = order.size() - 1; i >= 0; i--) {
                        BlockState state = placed.remove(order.get(i));
                        if (state != null) state.update(true, false);
                    }
                    order.clear();
                    placed.clear();
                    Effects.unroot(p);
                }
            }.runTaskTimer(plugin(), 0L, 1L);
        }
    }
}
