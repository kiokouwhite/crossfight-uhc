package com.kiokouwhite.crossfight.character.gunvolt;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.ToggleAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.character.duo.Synergy;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Bullets;
import com.kiokouwhite.crossfight.util.ChoiceMenu;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Acura - Voie de la Destruction. Un pod qui tire tout seul sur sa cible, et des Ex-Weapons alimentees par l'Energie Mecanique.
 */
public final class Acura extends Character implements Listener {

    static final NamespacedKey PHOTON = new NamespacedKey("crossfight", "acura_photon");
    static final NamespacedKey SHROUD = new NamespacedKey("crossfight", "acura_shroud");
    static final NamespacedKey POD = new NamespacedKey("crossfight", "acura_pod");

    enum Ex {
        STELLAR("Stellar Spark", Material.LIGHTNING_ROD, 10, true, "Activable (10 Energie/s) : zone d'electricite de 5x5 autour d'Acura, Lenteur et 1 coeur par seconde."),
        ANCHOR("Anchor Nexus", Material.IRON_CHAIN, 15, false, "Passif (15 Energie par coup) : l'Avenger Dash inflige 3 coeurs a l'adversaire percute."),
        PHOTON_DET("Photon Detonator", Material.FIRE_CHARGE, 20, false, "Actif (20 Energie) : un projectile de 10 blocs qui explose sur 5x5 (2 coeurs), des qu'il touche un adversaire."),
        ORBITAL("Orbital Edge", Material.IRON_TRAPDOOR, 10, false, "Actif (10 Energie) : deux scies circulaires tournent a 5 blocs pendant 8 secondes, 1 coeur par touche."),
        SHROUD_SPEAR("Shroud Spear", Material.TRIDENT, 15, false, "Actif (15 Energie) : un projectile de 10 blocs qui aveugle et immobilise 5 secondes l'adversaire touche."),
        MUSE("Muse's Kiss", Material.BOW, 5, true, "Activable (5 Energie/s) : une fleche Power I par seconde file toute seule sur l'adversaire le plus proche."),
        CYCLONE("Rising Cyclone", Material.WIND_CHARGE, 30, true, "Activable (30 Energie/s) : une tornade de 5x5, 1 coeur par seconde dedans ; les adversaires a 10 blocs sont aspires.");
        final String label, description; final Material icon; final int cost; final boolean toggle;
        Ex(String label, Material icon, int cost, boolean toggle, String description) { this.label = label; this.icon = icon; this.cost = cost; this.toggle = toggle; this.description = description; }
    }

    public Acura() {
        super("acura", "Acura", Voie.DESTRUCTION, Material.IRON_HOE);
        addAbility(new LolaCombatPod().bindToWeapon());
        addAbility(new AvengerDash());
        addAbility(new EnvoleeLumineuse());
        addAbility(new ExWeapon());
        addAbility(Synergy.ITEM);
        setUltimate(new TwinShredder());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.GOLDEN_HOE, 1.0, "<yellow><bold>Lola Combat Pod", List.of("<gray>Pod : une balle toutes les 2 s, qui suit la cible."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Lola Combat Pod : balles automatiques sur la cible (10 blocs)",
                "<white>Avenger Dash : 10 blocs, cible l'adversaire percute",
                "<white>Envolee Lumineuse : 10 blocs dans les airs, sans chute",
                "<white>Ex Weapon : 7 armes alimentees par l'Energie Mecanique (accroupi + clic : choisir)",
                "<light_purple>Ultime : Twin Shredder, 3 coeurs sur 10 blocs en 3 s (double sur la cible)");
    }

    // ----------------------------------------------------------------- cible

    static AbilityState podState(Player p) {
        CfPlayer cf = ABG.plugin().players().get(p);
        var a = cf.character() == null ? null : cf.character().ability("acura_pod");
        return a == null ? null : ABG.plugin().abilities().state(p, a);
    }
    static LivingEntity target(Player p) {
        AbilityState st = podState(p);
        if (st == null) return null;
        String id = st.get("target", null);
        if (id == null) return null;
        var e = Bukkit.getEntity(UUID.fromString(id));
        if (!(e instanceof LivingEntity le) || le.isDead() || !Effects.isEnemy(p, le)) { st.remove("target"); return null; }
        return le;
    }
    static void setTarget(Player p, LivingEntity t) {
        AbilityState st = podState(p);
        if (st == null) return;
        st.set("target", t.getUniqueId().toString());
        Effects.give(t, PotionEffectType.GLOWING, 0, 200);
        Msg.actionBar(p, "<yellow>Cible verrouillee : " + (t instanceof Player tp ? tp.getName() : "mannequin"));
    }

    /** Le pod : une balle toutes les 2 s. Sur la cible a 10 blocs, la balle est guidee ; sinon clic droit tire droit devant. */
    static final class LolaCombatPod extends ActiveAbility implements CustomUse {
        LolaCombatPod() {
            super("acura_pod", "Lola Combat Pod", "Une balle toutes les 2 secondes (Puissance I). Si l'adversaire cible est a moins de 10 blocs, les balles partent toutes seules sur lui.", Material.GOLDEN_HOE, 0);
        }
        @Override public List<String> weaponLore() { return List.of("<yellow>Pod : <gray>tir automatique sur la cible, clic droit pour tirer droit devant"); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            LivingEntity t = target(p);
            return t == null ? "<gray>Pod <dark_gray>[pas de cible]" : "<yellow>Pod <white>[" + (t instanceof Player tp ? tp.getName() : "mannequin") + "]";
        }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            long now = System.currentTimeMillis();
            if (now < st.get("nextShot", 0L)) return true;
            st.set("nextShot", now + 2000L);
            fire(p, p.getEyeLocation().getDirection());
            return true;
        }
        static void fire(Player p, Vector dir) {
            Arrow a = Bullets.shoot(p, dir, 3.0, 5.0, Material.YELLOW_CONCRETE, Color.fromRGB(255, 220, 80));
            a.getPersistentDataContainer().set(POD, PersistentDataType.BYTE, (byte) 1);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.6f, 2f);
        }
        /** Tir automatique sur la cible, toutes les 2 secondes (hook tick : une fois par seconde). */
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) {
            LivingEntity t = target(p);
            if (t == null || t.getWorld() != p.getWorld() || t.getLocation().distance(p.getLocation()) > 10) return;
            long now = System.currentTimeMillis();
            if (now < st.get("nextShot", 0L)) return;
            st.set("nextShot", now + 2000L);
            Vector dir = t.getLocation().add(0, 1, 0).toVector().subtract(p.getEyeLocation().toVector()).normalize();
            fire(p, dir);
        }
    }

    // ---------------------------------------------------------------- pouvoirs

    static final class AvengerDash extends ActiveAbility {
        AvengerDash() {
            super("acura_dash", "Avenger Dash", "Acura se projette 10 blocs en avant. Si elle percute un adversaire, elle recule de 4 blocs et le cible.", Material.FEATHER, 5);
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
            dir.normalize();
            World w = p.getWorld();
            Location from = p.getLocation();
            final Vector d = dir;
            w.playSound(from, Sound.ENTITY_BREEZE_SHOOT, 1f, 1.4f);
            new BukkitRunnable() {
                int i = 0; Location cur = from.clone();
                @Override public void run() {
                    i++;
                    if (!p.isOnline() || p.isDead() || i > 5) { cancel(); return; }
                    Location next = cur.clone().add(d.clone().multiply(2));
                    if (!next.getBlock().isPassable() || !next.clone().add(0, 1, 0).getBlock().isPassable()) { cancel(); return; }
                    for (double k = 0; k < 2; k += 0.5) {
                        Location l = cur.clone().add(d.clone().multiply(k)).add(0, 1, 0);
                        w.spawnParticle(Particle.DUST, l, 2, 0.2, 0.3, 0.2, 0, new Particle.DustOptions(Color.fromRGB(255, 220, 80), 1.0f));
                        for (LivingEntity e : Effects.enemiesWithin(p, l, 1.5)) {
                            // Percute : on cible, Anchor Nexus frappe, et Acura recule de 4 blocs.
                            setTarget(p, e);
                            if (ExWeapon.selected(p) == Ex.ANCHOR && ExWeapon.spend(p, Ex.ANCHOR.cost)) {
                                e.setNoDamageTicks(0); Effects.damage(e, 6.0, p);
                                w.spawnParticle(Particle.CRIT, e.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.2);
                                w.playSound(e.getLocation(), Sound.BLOCK_CHAIN_BREAK, 1f, 0.8f);
                            }
                            Location back = p.getLocation().add(d.clone().multiply(-4));
                            Effects.rush(p, back, 2, Particle.CRIT);
                            cancel();
                            return;
                        }
                    }
                    next.setDirection(p.getLocation().getDirection());
                    p.teleport(next); cur = next;
                }
            }.runTaskTimer(ABG.plugin(), 0L, 1L);
            return true;
        }
    }

    static final class EnvoleeLumineuse extends ActiveAbility {
        EnvoleeLumineuse() { super("acura_envolee", "Envolee Lumineuse", "Acura s'envole de 10 blocs dans les airs, sans degats de chute jusqu'a ce qu'elle retombe.", Material.WHITE_DYE, 5); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Effects.launchUp(p, 10);
            Effects.noFallUntil(p, 15000);
            p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation(), 30, 0.4, 0.2, 0.4, 0.05);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 1.3f);
            return true;
        }
    }

    /** Jauge d'Energie Mecanique (100, +1/s). Accroupi + clic : choisir l'Ex-Weapon ; clic : l'utiliser (ou l'activer / la couper). */
    static final class ExWeapon extends ToggleAbility implements CustomUse {
        ExWeapon() { super("acura_ex", "Ex Weapon", "100 Energie Mecanique (+1 par seconde) pour sept Ex-Weapons. Accroupi + clic droit : choisir l'arme. Clic droit : l'utiliser, ou l'activer et la couper.", Material.REDSTONE, 0, 1); }
        @Override protected List<String> extraLore() { return List.of("", "<yellow>Accroupi + clic : choisir  <gray>|  <yellow>Clic : utiliser"); }

        static AbilityState state(Player p) {
            CfPlayer cf = ABG.plugin().players().get(p);
            var a = cf.character() == null ? null : cf.character().ability("acura_ex");
            return a == null ? null : ABG.plugin().abilities().state(p, a);
        }
        static Ex selected(Player p) { AbilityState st = state(p); return st == null ? Ex.STELLAR : Ex.valueOf(st.get("ex", Ex.STELLAR.name())); }
        static boolean spend(Player p, double amount) {
            AbilityState st = state(p);
            if (st == null || st.gauge() < amount) return false;
            st.setGauge(st.gauge() - amount);
            return true;
        }

        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            if (sneaking) {
                List<ChoiceMenu.Option> options = new ArrayList<>();
                for (Ex ex : Ex.values()) {
                    List<String> lore = new ArrayList<>(com.kiokouwhite.crossfight.util.Items.wrap(ex.description));
                    if (ex == selected(p)) lore.add("<yellow>Selectionnee");
                    options.add(new ChoiceMenu.Option(ex.icon, ex.label, lore, () -> {
                        if (st.isEnabled()) { st.setEnabled(false); onDisable(p, cf, st); }
                        st.set("ex", ex.name());
                        Msg.actionBar(p, "<yellow>Ex Weapon : " + ex.label);
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.4f);
                    }));
                }
                ChoiceMenu.open(p, "<yellow><bold>Ex Weapon", options);
                return true;
            }
            Ex ex = selected(p);
            if (ex.toggle) return false; // le moteur active / coupe
            if (ex == Ex.ANCHOR) { Msg.actionBar(p, "<gray>Anchor Nexus est passif : il frappe pendant l'Avenger Dash."); return true; }
            if (st.gauge() < ex.cost) { Msg.actionBar(p, "<red>" + ex.label + " : " + ex.cost + " Energie necessaire."); return true; }
            boolean ok = switch (ex) {
                case PHOTON_DET -> photon(p);
                case ORBITAL -> orbital(p);
                case SHROUD_SPEAR -> shroud(p);
                default -> false;
            };
            if (ok) st.setGauge(st.gauge() - ex.cost);
            return true;
        }

        @Override public boolean onEnable(Player p, CfPlayer cf, AbilityState st) {
            Ex ex = selected(p);
            if (!ex.toggle) return false;
            if (st.gauge() < ex.cost) { Msg.actionBar(p, "<red>" + ex.label + " : pas assez d'Energie."); return false; }
            if (ex == Ex.CYCLONE) st.set("cyclone", p.getLocation().clone());
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
            startVisual(p, st, ex);
            return true;
        }
        @Override public void onDisable(Player p, CfPlayer cf, AbilityState st) { p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.6f); }

        @Override public void tickActive(Player p, CfPlayer cf, AbilityState st) {
            Ex ex = selected(p);
            if (!ex.toggle || st.gauge() < ex.cost) { st.setEnabled(false); onDisable(p, cf, st); Msg.actionBar(p, "<red>" + ex.label + " : plus d'Energie."); return; }
            st.setGauge(st.gauge() - ex.cost);
            World w = p.getWorld();
            switch (ex) {
                case STELLAR -> { for (LivingEntity e : Effects.enemiesWithin(p, 2.5)) { e.setNoDamageTicks(0); Effects.damage(e, 2.0, p); Effects.give(e, PotionEffectType.SLOWNESS, 0, 30); } }
                case MUSE -> {
                    LivingEntity t = Effects.nearestEnemy(p, 20);
                    if (t != null) {
                        Vector dir = t.getLocation().add(0, 1, 0).toVector().subtract(p.getEyeLocation().toVector()).normalize();
                        Arrow a = Effects.shootInstantArrow(p, dir, 3.0, 4.0);
                        a.setCritical(false);
                    }
                }
                case CYCLONE -> {
                    Location c = st.get("cyclone", p.getLocation());
                    for (LivingEntity e : Effects.within(c, 2.5, le -> Effects.isEnemy(p, le))) { e.setNoDamageTicks(0); Effects.damage(e, 2.0, p); }
                    for (LivingEntity e : Effects.within(c, 10, le -> Effects.isEnemy(p, le))) {
                        Vector pull = c.toVector().subtract(e.getLocation().toVector()).setY(0);
                        if (pull.length() > 2) e.setVelocity(pull.normalize().multiply(0.8).setY(0.2));
                    }
                }
                default -> { }
            }
        }

        static void startVisual(Player p, AbilityState st, Ex ex) {
            World w = p.getWorld();
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || !st.isEnabled() || selected(p) != ex) { cancel(); return; }
                    switch (ex) {
                        case STELLAR -> { Location c = p.getLocation(); for (int i = 0; i < 10; i++) { double a = i * Math.PI / 5 + t * 0.2; w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 2.5, 0.3, Math.sin(a) * 2.5), 1, 0, 0, 0, 0, ABG.CYAN); } if (t % 3 == 0) ABG.sparks(w, c, 2.5, 2); }
                        case MUSE -> w.spawnParticle(Particle.DUST, p.getLocation().add(0, 2.2, 0), 2, 0.3, 0.1, 0.3, 0, new Particle.DustOptions(Color.fromRGB(255, 150, 200), 0.8f));
                        case CYCLONE -> {
                            Location c = st.get("cyclone", p.getLocation());
                            for (int i = 0; i < 6; i++) { double h = i * 0.9 + (t % 10) * 0.09, r = 0.6 + h * 0.45, a = t * 0.35 + i * 1.1; w.spawnParticle(Particle.CLOUD, c.clone().add(Math.cos(a) * r, h, Math.sin(a) * r), 1, 0, 0, 0, 0.01); w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a + 3.14) * r, h, Math.sin(a + 3.14) * r), 1, 0, 0, 0, 0, ABG.CYAN); }
                            if (t % 10 == 0) w.playSound(c, Sound.ITEM_ELYTRA_FLYING, 0.4f, 1.6f);
                        }
                        default -> { }
                    }
                }
            }.runTaskTimer(ABG.plugin(), 1L, 1L);
        }

        static boolean photon(Player p) {
            Arrow a = Bullets.shoot(p, p.getEyeLocation().getDirection(), 1.2, 0.0, Material.ORANGE_CONCRETE, Color.fromRGB(255, 160, 40));
            a.setGravity(false);
            a.getPersistentDataContainer().set(PHOTON, PersistentDataType.BYTE, (byte) 1);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1.2f);
            Location start = p.getEyeLocation();
            Bukkit.getScheduler().runTaskLater(ABG.plugin(), () -> { if (a.isValid()) { explode(p, a.getLocation()); a.remove(); } }, (long) (10 / 1.2) + 1);
            return true;
        }
        static void explode(Player p, Location c) {
            World w = c.getWorld();
            w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 1);
            w.spawnParticle(Particle.FLAME, c, 40, 1.5, 0.8, 1.5, 0.05);
            w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.1f);
            for (LivingEntity e : Effects.within(c, 2.5, le -> Effects.isEnemy(p, le))) { e.setNoDamageTicks(0); Effects.damage(e, 4.0, p); }
        }
        static boolean orbital(Player p) {
            World w = p.getWorld();
            List<ItemDisplay> saws = new ArrayList<>();
            for (int k = 0; k < 2; k++) saws.add(w.spawn(p.getLocation(), ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(Material.IRON_TRAPDOOR));
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationX((float) Math.PI / 2), new Vector3f(1.2f, 1.2f, 1.2f), new Quaternionf()));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setPersistent(false);
            }));
            Map<UUID, Long> last = new HashMap<>();
            w.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.5f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || t > 160) { for (ItemDisplay s : saws) s.remove(); cancel(); return; }
                    for (int k = 0; k < 2; k++) {
                        double a = t * 0.2 + k * Math.PI;
                        Location l = p.getLocation().add(Math.cos(a) * 5, 1.0, Math.sin(a) * 5);
                        l.setYaw((float) (t * 40 % 360));
                        saws.get(k).teleport(l);
                        if (t % 2 == 0) w.spawnParticle(Particle.CRIT, l, 1, 0.1, 0.1, 0.1, 0.02);
                        for (LivingEntity e : Effects.enemiesWithin(p, l, 1.3)) {
                            long now = System.currentTimeMillis();
                            if (now - last.getOrDefault(e.getUniqueId(), 0L) < 1000) continue;
                            last.put(e.getUniqueId(), now);
                            e.setNoDamageTicks(0); Effects.damage(e, 2.0, p);
                            w.playSound(l, Sound.BLOCK_GRINDSTONE_USE, 0.8f, 2f);
                        }
                    }
                    if (t % 20 == 0) w.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.3f, 1.8f);
                }
            }.runTaskTimer(ABG.plugin(), 0L, 1L);
            return true;
        }
        static boolean shroud(Player p) {
            Arrow a = Bullets.shoot(p, p.getEyeLocation().getDirection(), 2.5, 1.0, Material.BLACK_CONCRETE, Color.fromRGB(60, 20, 90));
            a.getPersistentDataContainer().set(SHROUD, PersistentDataType.BYTE, (byte) 1);
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_THROW, 1f, 0.8f);
            Bukkit.getScheduler().runTaskLater(ABG.plugin(), () -> { if (a.isValid()) a.remove(); }, 6L);
            return true;
        }
    }

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow a) || !(a.getShooter() instanceof Player p)) return;
        var pdc = a.getPersistentDataContainer();
        if (pdc.has(PHOTON)) { ExWeapon.explode(p, a.getLocation()); Bukkit.getScheduler().runTask(ABG.plugin(), () -> { if (a.isValid()) a.remove(); }); }
        else if (pdc.has(SHROUD) && e.getHitEntity() instanceof LivingEntity t && Effects.isEnemy(p, t)) {
            Effects.root(t, 100);
            Effects.give(t, PotionEffectType.BLINDNESS, 0, 100);
            t.getWorld().spawnParticle(Particle.SQUID_INK, t.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.05);
            Effects.say(t, "<dark_purple>Shroud Spear : aveugle et immobilise 5 s !");
        }
    }

    // ----------------------------------------------------------------- ultime

    static final class TwinShredder extends UltimateAbility {
        TwinShredder() { super("acura_ult", "Twin Shredder", "Sur 10 blocs autour d'Acura, 3 coeurs de degats repartis sur 3 secondes ; la cible verrouillee prend le double.", Material.SHEARS); }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            World w = p.getWorld();
            w.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 0.6f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || t > 60) { cancel(); return; }
                    Location c = p.getLocation();
                    for (int i = 0; i < 16; i++) { double a = i * Math.PI / 8 + t * 0.3; double r = 5 * (0.3 + 0.7 * ((t + i) % 10) / 10.0); w.spawnParticle(Particle.SWEEP_ATTACK, c.clone().add(Math.cos(a) * r, 0.5 + Math.random() * 1.5, Math.sin(a) * r), 1, 0, 0, 0, 0); }
                    if (t % 20 == 0) {
                        LivingEntity target = target(p);
                        for (LivingEntity e : Effects.enemiesWithin(p, 5)) { e.setNoDamageTicks(0); Effects.damage(e, e == target ? 4.0 : 2.0, p); }
                        w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
                    }
                }
            }.runTaskTimer(ABG.plugin(), 0L, 1L);
            return true;
        }
    }
}
