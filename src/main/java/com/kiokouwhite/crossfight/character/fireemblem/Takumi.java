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
            super("takumi_contre", "Contre Rapproche", "Le Yumi Fujin est remplace par une epee de 9 degats pendant 30 secondes.",
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
            Bukkit.getScheduler().runTaskLater(plugin(), () -> restoreBow(p, st), 20L * 30);
            return true;
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
        }
    }

    static final class Vengeance extends ActiveAbility {
        Vengeance() {
            super("takumi_vengeance", "Vengeance", "Takumi gagne un niveau de Puissance par tranche de 3 coeurs perdus, pendant 5 secondes.",
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
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                st.set("bonus", 0);
                if (p.isOnline()) refreshPower(p);
            }, 100L);
            return true;
        }
    }

    static final class Furie extends ActiveAbility {
        Furie() {
            super("takumi_furie", "Furie", "Takumi gagne un niveau de Puissance et Vitesse II pendant 10 secondes, mais perd 5 coeurs.",
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
            p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.02);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.7f);
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                st.set("bonus", 0);
                if (p.isOnline()) refreshPower(p);
            }, 200L);
            return true;
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
