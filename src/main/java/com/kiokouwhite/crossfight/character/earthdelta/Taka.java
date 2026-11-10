package com.kiokouwhite.crossfight.character.earthdelta;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.listener.DamageListener;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Taka - Path of Hunt. Elle invoque des armes (epees, lances, haches) qui combattent seules.
 */
public final class Taka extends Character implements Listener {

    static final NamespacedKey OWNER = new NamespacedKey("crossfight", "taka_owner");
    static final String TAG = "cf_taka_weapon";

    enum Kind {
        SWORD("Epee Flottante", Material.IRON_SWORD, 2, 2.0),
        SPEAR("Lance Volante", Material.TRIDENT, 3, 3.0),
        AXE("Hache Tournoyante", Material.IRON_AXE, 4, 4.0);
        final String label; final Material item; final int hits; final double damage;
        Kind(String label, Material item, int hits, double damage) { this.label = label; this.item = item; this.hits = hits; this.damage = damage; }
    }

    /** Une arme invoquee : un porte-armure invisible qui tient l'arme et bouge selon son type. */
    static final class Summon {
        final Kind kind; final ArmorStand stand; final UUID owner; final boolean ult;
        final Location origin; final Vector dir; int hits; int age; double phase;
        final Map<UUID, Long> lastHit = new HashMap<>();
        Summon(Kind kind, ArmorStand stand, UUID owner, boolean ult, Location origin, Vector dir) {
            this.kind = kind; this.stand = stand; this.owner = owner; this.ult = ult; this.origin = origin; this.dir = dir;
        }
    }
    static final List<Summon> SUMMONS = new ArrayList<>();

    public Taka() {
        super("taka", "Taka", Voie.CHASSE, Material.IRON_AXE);
        addAbility(new MultiArme().bindToWeapon());
        addAbility(new Invocation(Kind.SWORD, 15, 3, 0));
        addAbility(new Invocation(Kind.SPEAR, 20, 2, 1));
        addAbility(new Invocation(Kind.AXE, 25, 1, 2));
        setUltimate(new MultiInvocation());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.IRON_SWORD, 9.0, "<gold><bold>Multi-Arme", List.of("<gray>9 dmg."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Multi-Arme : clic droit, toutes les armes invoquees foncent sur l'ennemi",
                "<white>Epee Flottante (x3) - Lance Volante (1 vie perdue, x2) - Hache Tournoyante (2 vies, x1)",
                "<light_purple>Ultime : 10 armes indestructibles a 50 blocs");
    }

    static int livesLost(Player p) {
        return Math.max(0, EarthDelta.initialLives() - EarthDelta.plugin().players().get(p).lives());
    }
    static boolean unlocked(Player p, int needed) { return EarthDelta.plugin().game().isTest() || livesLost(p) >= needed; }

    static List<Summon> summonsOf(Player p) {
        List<Summon> out = new ArrayList<>();
        for (Summon s : SUMMONS) if (s.owner.equals(p.getUniqueId()) && s.stand.isValid()) out.add(s);
        return out;
    }

    static Summon summonOf(org.bukkit.entity.Entity e) {
        for (Summon s : SUMMONS) if (s.stand == e) return s;
        return null;
    }

    /** Invoque une arme a un endroit, pour un proprietaire. */
    static Summon summon(Player owner, Kind kind, Location at, boolean ult) {
        World w = at.getWorld();
        Vector dir = owner.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
        dir.normalize();
        Location base = at.clone();
        base.setYaw(owner.getLocation().getYaw());
        ArmorStand stand = w.spawn(base, ArmorStand.class, s -> {
            s.setInvisible(true);
            s.setArms(true);
            s.setBasePlate(false);
            s.setGravity(false);
            s.setSmall(false);
            s.setPersistent(false);
            s.setCanPickupItems(false);
            s.setRemoveWhenFarAway(false);
            s.addScoreboardTag(TAG);
            s.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());
            s.getEquipment().setItemInMainHand(new ItemStack(kind.item));
            s.setRightArmPose(kind == Kind.SWORD ? new EulerAngle(Math.PI, 0, 0) : new EulerAngle(-Math.PI / 2, 0, 0));
            s.setGlowing(ult);
        });
        Summon s = new Summon(kind, stand, owner.getUniqueId(), ult, at.clone(), dir);
        SUMMONS.add(s);
        w.spawnParticle(Particle.CRIT, at.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
        w.playSound(at, Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 0.8f);
        new BukkitRunnable() {
            @Override public void run() {
                if (!stand.isValid() || !SUMMONS.contains(s)) { cancel(); return; }
                s.age++;
                if (!s.ult && s.age > 20 * 60) { destroy(s, "<gray>L'arme s'est dissipee."); cancel(); return; }
                Player o = Bukkit.getPlayer(s.owner);
                if (o == null) { destroy(s, null); cancel(); return; }
                try { tick(s, o); } catch (Exception ex) { destroy(s, null); cancel(); }
            }
        }.runTaskTimer(EarthDelta.plugin(), 1L, 1L);
        return s;
    }

    static void tick(Summon s, Player o) {
        World w = s.stand.getWorld();
        switch (s.kind) {
            case SWORD -> {
                // Plantee, elle tourne doucement et blesse ce qui s'approche a 2 blocs chaque seconde.
                Location l = s.stand.getLocation();
                l.setYaw(l.getYaw() + 3);
                s.stand.teleport(l);
                if (s.age % 20 == 0) {
                    for (LivingEntity e : Effects.within(l, 2.0, le -> Effects.isEnemy(o, le))) { e.setNoDamageTicks(0); Effects.damage(e, s.kind.damage, o); }
                    w.spawnParticle(Particle.SWEEP_ATTACK, l.clone().add(0, 1, 0), 1);
                }
            }
            case SPEAR -> {
                // Va-et-vient sur 5 blocs dans la direction d'invocation.
                s.phase += 0.08;
                double off = (Math.sin(s.phase) + 1) / 2 * 5.0;
                Location l = s.origin.clone().add(s.dir.clone().multiply(off));
                l.setDirection(Math.cos(s.phase) >= 0 ? s.dir : s.dir.clone().multiply(-1));
                s.stand.teleport(l);
                if (s.age % 3 == 0) w.spawnParticle(Particle.CRIT, l.clone().add(0, 1.2, 0), 1, 0.1, 0.1, 0.1, 0);
                strike(s, o, l.clone().add(0, 1, 0), 1.2);
            }
            case AXE -> {
                // Tournoie autour de Taka (ou de son point d'invocation pour les armes de l'ultime).
                s.phase += 0.25;
                Location center = s.ult ? s.origin : o.getLocation();
                Location l = center.clone().add(Math.cos(s.phase) * 2.5, 0.2, Math.sin(s.phase) * 2.5);
                l.setYaw((float) Math.toDegrees(s.phase) + 90);
                s.stand.teleport(l);
                if (s.age % 2 == 0) w.spawnParticle(Particle.SWEEP_ATTACK, l.clone().add(0, 1.2, 0), 1, 0, 0, 0, 0);
                strike(s, o, l.clone().add(0, 1, 0), 1.3);
            }
        }
    }

    static void strike(Summon s, Player o, Location at, double radius) {
        long now = System.currentTimeMillis();
        for (LivingEntity e : Effects.within(at, radius, le -> Effects.isEnemy(o, le))) {
            if (now - s.lastHit.getOrDefault(e.getUniqueId(), 0L) < 1000) continue;
            s.lastHit.put(e.getUniqueId(), now);
            e.setNoDamageTicks(0);
            Effects.damage(e, s.kind.damage, o);
            e.getWorld().playSound(e.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.2f);
        }
    }

    static void destroy(Summon s, String msg) {
        SUMMONS.remove(s);
        if (s.stand.isValid()) {
            s.stand.getWorld().spawnParticle(Particle.ITEM, s.stand.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05, new ItemStack(s.kind.item));
            s.stand.getWorld().playSound(s.stand.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 0.8f);
            s.stand.remove();
        }
        Player o = Bukkit.getPlayer(s.owner);
        if (o != null && msg != null) Msg.actionBar(o, msg);
    }

    static void destroyAll(Player p) { for (Summon s : summonsOf(p)) destroy(s, null); }

    // ------------------------------------------------------------------ arme

    /** Clic droit avec la Multi-Arme : toutes les armes invoquees foncent sur l'ennemi le plus proche (1 coeur chacune). */
    static final class MultiArme extends ActiveAbility implements CustomUse {
        MultiArme() {
            super("taka_multiarme", "Multi-Arme", "Clic droit : toutes les armes invoquees foncent sur l'adversaire le plus proche (10 blocs) et infligent 1 coeur chacune.", Material.IRON_SWORD, 0);
        }
        @Override public List<String> weaponLore() { return List.of("<gold>Multi-Arme : <gray>clic droit, les armes invoquees foncent sur l'ennemi"); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { return "<gold>Armes <white>" + summonsOf(p).stream().filter(s -> !s.ult).count(); }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            List<Summon> mine = new ArrayList<>();
            for (Summon s : summonsOf(p)) if (!s.ult) mine.add(s);
            if (mine.isEmpty()) { Msg.actionBar(p, "<red>Aucune arme invoquee."); return true; }
            LivingEntity target = Effects.nearestEnemy(p, 10);
            if (target == null) { Msg.actionBar(p, "<red>Aucun adversaire a moins de 10 blocs."); return true; }
            World w = p.getWorld();
            w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
            for (Summon s : mine) {
                SUMMONS.remove(s);
                Location from = s.stand.getLocation();
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        t++;
                        if (!s.stand.isValid() || target.isDead() || t > 12) { s.stand.remove(); cancel(); return; }
                        Location to = target.getLocation().add(0, 0.2, 0);
                        Location l = from.clone().add(to.toVector().subtract(from.toVector()).multiply(t / 10.0));
                        l.setDirection(to.toVector().subtract(l.toVector()));
                        s.stand.teleport(l);
                        w.spawnParticle(Particle.CRIT, l.clone().add(0, 1, 0), 2, 0.1, 0.1, 0.1, 0.02);
                        if (t >= 10) {
                            target.setNoDamageTicks(0);
                            Effects.damage(target, 2.0, p);
                            w.spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 1);
                            w.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
                            s.stand.remove();
                            cancel();
                        }
                    }
                }.runTaskTimer(EarthDelta.plugin(), 0L, 1L);
            }
            Msg.actionBar(p, "<gold>Multi-Arme : " + mine.size() + " arme(s) lancee(s) !");
            return true;
        }
    }

    // ---------------------------------------------------------------- pouvoirs

    static final class Invocation extends ActiveAbility {
        final Kind kind; final int max; final int livesNeeded;
        Invocation(Kind kind, int cd, int max, int livesNeeded) {
            super("taka_" + kind.name().toLowerCase(), kind.label, switch (kind) {
                case SWORD -> "Taka plante une epee au sol : 1 coeur par seconde a tout adversaire a 2 blocs. Detruite apres 2 coups ou 1 minute. 3 maximum.";
                case SPEAR -> "Debloquee apres 1 vie perdue. Une lance fait des allers-retours sur 5 blocs : 1.5 coeur par touche. Detruite apres 3 coups ou 1 minute. 2 maximum.";
                case AXE -> "Debloquee apres 2 vies perdues. Une hache tournoie autour de Taka : 2 coeurs par touche. Detruite apres 4 coups ou 1 minute. 1 maximum.";
            }, kind.item, cd);
            this.kind = kind; this.max = max; this.livesNeeded = livesNeeded;
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            if (!unlocked(p, livesNeeded)) return "<dark_gray>" + name() + " [" + livesLost(p) + "/" + livesNeeded + " vie(s)]";
            return null;
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            if (!unlocked(p, livesNeeded)) { Msg.actionBar(p, "<red>" + name() + " : debloquee apres " + livesNeeded + " vie(s) perdue(s)."); return false; }
            long n = summonsOf(p).stream().filter(s -> s.kind == kind && !s.ult).count();
            if (n >= max) { Msg.actionBar(p, "<red>" + name() + " : " + max + " maximum."); return false; }
            Location at = p.getLocation();
            if (kind == Kind.SWORD) {
                var hit = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 6);
                if (hit != null && hit.getHitBlock() != null) at = hit.getHitBlock().getLocation().add(0.5, 1, 0.5);
                else at = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(2));
            }
            summon(p, kind, at, false);
            return true;
        }
        @Override public void onUnequip(Player p, CfPlayer cf) { destroyAll(p); }
    }

    static final class MultiInvocation extends UltimateAbility {
        MultiInvocation() {
            super("taka_ult", "Multi-Invocation", "Taka invoque 10 armes au hasard a 50 blocs autour d'elle : indestructibles, elles ne peuvent pas etre lancees par la Multi-Arme.", Material.NETHERITE_AXE);
        }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            World w = p.getWorld();
            Location c = p.getLocation();
            int n = 0;
            for (int i = 0; i < 10; i++) {
                double a = Math.random() * Math.PI * 2, d = 3 + Math.random() * 47;
                int x = c.getBlockX() + (int) (Math.cos(a) * d), z = c.getBlockZ() + (int) (Math.sin(a) * d);
                int y = Effects.groundAround(w, x, z, c.getBlockY());
                if (y == Integer.MIN_VALUE) y = w.getHighestBlockYAt(x, z);
                Kind kind = Kind.values()[(int) (Math.random() * Kind.values().length)];
                summon(p, kind, new Location(w, x + 0.5, y + 1, z + 0.5), true);
                n++;
            }
            w.playSound(c, Sound.BLOCK_ANVIL_LAND, 1f, 0.6f);
            Msg.actionBar(p, "<gold>Multi-Invocation : " + n + " armes invoquees !");
            return true;
        }
    }

    // ---------------------------------------------------------------- events

    /** Frapper une arme invoquee compte un coup ; elle se brise au bout de son nombre de coups. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onHitWeapon(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof ArmorStand stand) || !stand.getScoreboardTags().contains(TAG)) return;
        e.setCancelled(true);
        Summon s = summonOf(stand);
        if (s == null) return;
        Player o = Bukkit.getPlayer(s.owner);
        LivingEntity attacker = DamageListener.livingAttackerOf(e.getDamager());
        if (o == null || attacker == null || !Effects.isEnemy(o, attacker)) return;
        if (s.ult) { stand.getWorld().playSound(stand.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.6f, 1.5f); return; }
        s.hits++;
        stand.getWorld().playSound(stand.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.6f, 1.4f);
        if (s.hits >= s.kind.hits) destroy(s, "<gray>" + s.kind.label + " detruite par l'adversaire.");
    }

    @EventHandler
    public void onAnyDamageWeapon(EntityDamageEvent e) {
        if (e.getEntity() instanceof ArmorStand stand && stand.getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }

    @EventHandler
    public void onManipulate(PlayerArmorStandManipulateEvent e) {
        if (e.getRightClicked().getScoreboardTags().contains(TAG)) e.setCancelled(true);
    }
}
