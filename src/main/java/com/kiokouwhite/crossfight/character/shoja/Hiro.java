package com.kiokouwhite.crossfight.character.shoja;

import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.ToggleAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.character.duo.Synergy;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.Bullets;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.TempBlocks;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Hiro - Voie de la Destruction. Une barre de Puissance qu'il charge et depense en explosions.
 */
public final class Hiro extends Character implements Listener {

    static final NamespacedKey GRENADE = new NamespacedKey("crossfight", "hiro_grenade");

    public Hiro() {
        super("hiro", "Hiro", Voie.DESTRUCTION, Material.FIRE_CHARGE);
        addAbility(new PyraMira().bindToWeapon());
        addAbility(new DashExplosif());
        addAbility(new GrenadeEnflammee());
        addAbility(new ConsumationArdente());
        addAbility(Synergy.ITEM);
        setUltimate(new DetonationImminente());
    }

    @Override
    public ItemStack createWeapon() {
        ItemStack it = Weapons.melee(Material.GOLDEN_SWORD, 7.0, "<gold><bold>Pyra-Mira", List.of("<gray>7 dmg, Fire Aspect."));
        var meta = it.getItemMeta();
        meta.addEnchant(Enchantment.FIRE_ASPECT, 1, true);
        it.setItemMeta(meta);
        return it;
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Pyra-Mira : Fire Aspect, Fire Resistance, clic droit pour charger la Puissance (+3/s)",
                "<white>Dash Explosif : vol en avant (3 Puissance/s), explosion apres 3 s",
                "<white>Grenade Enflammee et Consumation Ardente : paliers 15 / 40 / 70 / 100",
                "<light_purple>Ultime : Detonation Imminente, cratere de 50 blocs en magma");
    }

    // -------------------------------------------------------------- puissance

    static AbilityState power(Player p) {
        CfPlayer cf = Shoja.plugin().players().get(p);
        Ability a = cf.character() == null ? null : cf.character().ability("hiro_pyra");
        return a == null ? null : Shoja.plugin().abilities().state(p, a);
    }
    static double puissance(Player p) { AbilityState st = power(p); return st == null ? 0 : st.gauge(); }
    static boolean spend(Player p, double amount) {
        AbilityState st = power(p);
        if (st == null || st.gauge() < amount) return false;
        st.setGauge(st.gauge() - amount);
        return true;
    }
    /** Le palier le plus haut que la Puissance permet : 100, 70, 40, 15, ou 0. */
    static int tier(Player p) {
        double pw = puissance(p);
        return pw >= 100 ? 100 : pw >= 70 ? 70 : pw >= 40 ? 40 : pw >= 15 ? 15 : 0;
    }

    /** L'arme : Fire Aspect et Fire Resistance ; clic droit pour charger (ou arreter de charger) la Puissance. */
    static final class PyraMira extends ToggleAbility implements CustomUse {
        PyraMira() { super("hiro_pyra", "Puissance", "Pyra-Mira donne Fire Aspect et Fire Resistance. Clic droit : Hiro charge sa Puissance (+3 par seconde, 100 max) ; re-clic pour arreter. La Puissance alimente ses pouvoirs.", Material.GOLDEN_SWORD, 0, 0); }
        @Override public boolean hasGauge() { return true; }
        @Override public List<String> weaponLore() { return List.of("<gold>Clic droit : <gray>charger la Puissance (+3/s)"); }
        @Override public void onEquip(Player p, CfPlayer cf) {
            AbilityState st = Shoja.plugin().abilities().state(p, this);
            if (!st.get("init", false)) { st.setGauge(0); st.set("init", true); }
            Effects.give(p, PotionEffectType.FIRE_RESISTANCE, 0, Integer.MAX_VALUE);
        }
        @Override public void onUnequip(Player p, CfPlayer cf) { p.removePotionEffect(PotionEffectType.FIRE_RESISTANCE); }
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) { Effects.give(p, PotionEffectType.FIRE_RESISTANCE, 0, 400); }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            if (st.isEnabled()) { st.setEnabled(false); onDisable(p, cf, st); Msg.actionBar(p, "<gold>Charge arretee : Puissance " + (int) st.gauge()); }
            else { st.setEnabled(true); onEnable(p, cf, st); Msg.actionBar(p, "<gold>Hiro charge sa Puissance..."); }
            return true;
        }
        @Override public boolean onEnable(Player p, CfPlayer cf, AbilityState st) {
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.8f, 0.8f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || !st.isEnabled()) { cancel(); return; }
                    double f = st.gauge() / 100.0;
                    p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 2 + (int) (f * 6), 0.4, 0.6, 0.4, 0.02);
                    if (t % 20 == 0) p.getWorld().playSound(p.getLocation(), Sound.BLOCK_FIRE_AMBIENT, 0.5f, 0.8f + (float) f * 0.8f);
                }
            }.runTaskTimer(Shoja.plugin(), 1L, 1L);
            return true;
        }
        @Override public void onDisable(Player p, CfPlayer cf, AbilityState st) { p.getWorld().playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.2f); }
        @Override public void tickActive(Player p, CfPlayer cf, AbilityState st) {
            st.setGauge(Math.min(100, st.gauge() + 3));
            if (st.gauge() >= 100) { st.setEnabled(false); onDisable(p, cf, st); Msg.actionBar(p, "<gold>Puissance maximale !"); }
        }
    }

    // ---------------------------------------------------------------- pouvoirs

    /** Vol en avant a 3 blocs par seconde (3 Puissance/s) ; apres 3 s de vol, explosion a l'arret. */
    static final class DashExplosif extends ToggleAbility implements CustomUse {
        DashExplosif() { super("hiro_dash", "Dash Explosif", "Activable : Hiro vole en avant a 3 blocs par seconde en depensant 3 Puissance par seconde. S'il a vole plus de 3 secondes, une explosion eclate quand il s'arrete.", Material.FEATHER, 0, 0); }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            if (st.isEnabled()) { st.setEnabled(false); onDisable(p, cf, st); return true; }
            if (puissance(p) < 3) { Msg.actionBar(p, "<red>Dash Explosif : pas assez de Puissance."); return true; }
            st.setEnabled(true); onEnable(p, cf, st);
            return true;
        }
        @Override public boolean onEnable(Player p, CfPlayer cf, AbilityState st) {
            st.set("start", System.currentTimeMillis());
            Effects.noFallUntil(p, 60000);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);
            new BukkitRunnable() {
                @Override public void run() {
                    if (!p.isOnline() || !st.isEnabled()) { cancel(); return; }
                    Vector dir = p.getLocation().getDirection().normalize().multiply(0.15);
                    p.setVelocity(dir.add(new Vector(0, 0.02, 0)));
                    p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 0.5, 0), 4, 0.2, 0.2, 0.2, 0.02);
                    p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 0.3, 0), 2, 0.2, 0.1, 0.2, 0.01);
                }
            }.runTaskTimer(Shoja.plugin(), 0L, 1L);
            Msg.actionBar(p, "<gold>Dash Explosif : en vol !");
            return true;
        }
        @Override public void tickActive(Player p, CfPlayer cf, AbilityState st) {
            if (!spend(p, 3)) { st.setEnabled(false); onDisable(p, cf, st); Msg.actionBar(p, "<red>Dash Explosif : plus de Puissance."); }
        }
        @Override public void onDisable(Player p, CfPlayer cf, AbilityState st) {
            Effects.noFallUntil(p, 5000);
            long flown = System.currentTimeMillis() - st.get("start", System.currentTimeMillis());
            if (flown > 3000) {
                Shoja.explosion(p, p.getLocation(), 3.5, 6.0, true);
                Msg.actionBar(p, "<gold>Dash Explosif : explosion !");
            } else p.getWorld().playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.2f);
        }
    }

    /** Grenade selon la Puissance : 15 / 40 / 70 / 100. */
    static final class GrenadeEnflammee extends ActiveAbility {
        GrenadeEnflammee() { super("hiro_grenade", "Grenade Enflammee", "15 Puissance : grenade a 5 blocs qui enflamme son chemin, explosion de 4 blocs. 40 : grenade a 10 blocs, explosion de 10. 70 : explosion de 12 avec de la lave. 100 : une boule qui explose tous les 2 blocs sur 20 blocs, puis une explosion de 20.", Material.FIRE_CHARGE, 3); }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { return "<gold>Grenade <white>[" + tier(p) + "]"; }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            int t = tier(p);
            if (t == 0) { Msg.actionBar(p, "<red>Grenade : 15 Puissance minimum."); return false; }
            spend(p, t);
            World w = p.getWorld();
            Vector dir = p.getEyeLocation().getDirection().normalize();
            double range = t == 15 ? 5 : t == 100 ? 20 : 10;
            double radius = t == 15 ? 4 : t == 40 ? 10 : t == 70 ? 12 : 20;
            double dmg = t == 15 ? 4.0 : t == 40 ? 6.0 : t == 70 ? 8.0 : 10.0;
            w.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f);
            Location start = p.getEyeLocation();
            TempBlocks fire = new TempBlocks().unbreakable();
            new BukkitRunnable() {
                double d = 0; double lastBurst = 0;
                @Override public void run() {
                    d += 1.0;
                    Location l = start.clone().add(dir.clone().multiply(d));
                    boolean blocked = !l.getBlock().isPassable();
                    if (!blocked) {
                        w.spawnParticle(Particle.FLAME, l, 6, 0.2, 0.2, 0.2, 0.02);
                        w.spawnParticle(Particle.LAVA, l, 1, 0.1, 0.1, 0.1, 0);
                        if (t == 15) { int gy = Effects.groundAround(w, l.getBlockX(), l.getBlockZ(), l.getBlockY()); if (gy != Integer.MIN_VALUE) fire.place(w.getBlockAt(l.getBlockX(), gy + 1, l.getBlockZ()), Material.FIRE); }
                        if (t == 100 && d - lastBurst >= 2) { lastBurst = d; Shoja.explosion(p, l, 5, 4.0, true); }
                    }
                    if (blocked || d >= range) {
                        Location c = blocked ? start.clone().add(dir.clone().multiply(Math.max(0, d - 1))) : l;
                        Shoja.explosion(p, c, radius, dmg, true);
                        if (t == 70) {
                            TempBlocks lava = new TempBlocks().unbreakable();
                            for (int k = 0; k < 14; k++) {
                                double a = Math.random() * Math.PI * 2, r = Math.random() * 5;
                                int x = c.getBlockX() + (int) (Math.cos(a) * r), z = c.getBlockZ() + (int) (Math.sin(a) * r);
                                int gy = Effects.groundAround(w, x, z, c.getBlockY());
                                if (gy != Integer.MIN_VALUE) lava.replace(w.getBlockAt(x, gy, z), Material.LAVA);
                            }
                            lava.restoreAfter(200);
                        }
                        fire.restoreAfter(100);
                        cancel();
                    }
                }
            }.runTaskTimer(Shoja.plugin(), 0L, 1L);
            return true;
        }
    }

    static final class ConsumationArdente extends ActiveAbility {
        ConsumationArdente() { super("hiro_consumation", "Consumation Ardente", "15 Puissance : Force 10 s. 40 : Force II et Speed 20 s. 70 : Force III et Speed II 30 s. 100 : Force IV, Speed III et Resistance II pendant 1 minute.", Material.BLAZE_POWDER, 3); }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { return "<gold>Consumation <white>[" + tier(p) + "]"; }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            int t = tier(p);
            if (t == 0) { Msg.actionBar(p, "<red>Consumation : 15 Puissance minimum."); return false; }
            spend(p, t);
            Boosts.Boost b = t == 15 ? new Boosts.Boost(1, 0, 0, 0) : t == 40 ? new Boosts.Boost(2, 1, 0, 0) : t == 70 ? new Boosts.Boost(3, 2, 0, 0) : new Boosts.Boost(4, 3, 2, 0);
            int seconds = t == 15 ? 10 : t == 40 ? 20 : t == 70 ? 30 : 60;
            String source = "hiro_consumation_" + System.currentTimeMillis();
            Boosts.set(p, source, b);
            Bukkit.getScheduler().runTaskLater(Shoja.plugin(), () -> Boosts.remove(p, source), 20L * seconds);
            p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 40 + t, 0.5, 0.8, 0.5, 0.08);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 1f, 0.7f);
            Msg.actionBar(p, "<gold>Consumation Ardente [" + t + "] : " + seconds + " s");
            return true;
        }
    }

    /** Explosion sur 50 blocs ; le cratere (un bol peu profond) est rempli de blocs de magma. */
    static final class DetonationImminente extends UltimateAbility {
        DetonationImminente() { super("hiro_ult", "Detonation Imminente", "Hiro provoque une explosion de 50 blocs de large : le cratere est rempli de blocs de magma.", Material.MAGMA_BLOCK); }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            World w = p.getWorld();
            Location c = p.getLocation().clone();
            final int R = 25;
            w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.4f);
            w.playSound(c, Sound.ENTITY_WITHER_SPAWN, 0.6f, 0.6f);
            Effects.noFallUntil(p, 8000);
            Effects.launchUp(p, 6);
            // Le cratere se creuse en anneaux successifs, un anneau par tick.
            new BukkitRunnable() {
                int ring = 0;
                @Override public void run() {
                    if (ring > R) { cancel(); return; }
                    for (int x = -ring; x <= ring; x++) for (int z = -ring; z <= ring; z++) {
                        double d = Math.sqrt(x * x + z * z);
                        if (d > ring || d <= ring - 1) continue;
                        int depth = (int) Math.round(4.0 * (1 - d / R));
                        int gy = Effects.groundAround(w, c.getBlockX() + x, c.getBlockZ() + z, c.getBlockY());
                        if (gy == Integer.MIN_VALUE) continue;
                        for (int k = 0; k < depth; k++) {
                            var b = w.getBlockAt(c.getBlockX() + x, gy - k, c.getBlockZ() + z);
                            if (b.getType() == Material.BEDROCK) break;
                            b.setType(k == depth - 1 ? Material.MAGMA_BLOCK : Material.AIR, false);
                        }
                        if (depth == 0 && Math.random() < 0.15) { var b = w.getBlockAt(c.getBlockX() + x, gy, c.getBlockZ() + z); if (b.getType() != Material.BEDROCK) b.setType(Material.MAGMA_BLOCK, false); }
                    }
                    for (int i = 0; i < 12; i++) { double a = i * Math.PI / 6; w.spawnParticle(Particle.EXPLOSION, c.clone().add(Math.cos(a) * ring, 1, Math.sin(a) * ring), 1); }
                    if (ring % 5 == 0) w.playSound(c.clone().add(ring, 0, 0), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
                    for (org.bukkit.entity.LivingEntity e : Effects.within(c, ring, le -> Effects.isEnemy(p, le))) {
                        if (e.getLocation().distance(c) > ring - 1) { e.setNoDamageTicks(0); Effects.damage(e, 10.0, p); e.setFireTicks(140); }
                    }
                    ring++;
                }
            }.runTaskTimer(Shoja.plugin(), 10L, 1L);
            Msg.actionBar(p, "<gold>Detonation Imminente !");
            return true;
        }
    }

    @EventHandler
    public void onGrenadeHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow a) || !a.getPersistentDataContainer().has(GRENADE)) return;
        a.remove();
    }
}
