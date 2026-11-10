package com.kiokouwhite.crossfight.character.unordinary;

import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.ToggleAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.listener.DamageListener;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.TempBlocks;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Arlo - Path of Destruction. Une jauge de 100 (+3 par seconde) alimente barrieres et cages.
 */
public final class Arlo extends Character implements Listener {

    static final Particle.DustOptions GOLD = new Particle.DustOptions(Color.fromRGB(255, 200, 60), 1.2f);

    public Arlo() {
        super("arlo", "Arlo", Voie.DESTRUCTION, Material.GOLD_BLOCK);
        addAbility(Unordinary.AURA);
        addAbility(new AuraGauge());
        addAbility(new Barriere());
        addAbility(new BarriereUltime());
        addAbility(new Enfermement(false));
        addAbility(new Enfermement(true));
        setUltimate(new BulleGeante());
    }

    @Override public ItemStack createWeapon() { return null; }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Epee d'Aura a crafter (actif selon le craft)",
                "<white>Jauge d'Aura 100 (+3/s) : Barriere (-50% degats), Barriere Ultime (renvoi)",
                "<white>Enfermement / Etouffement : cages a distance",
                "<light_purple>Ultime : bulle de verre de 30 blocs, Force aux allies 1 min");
    }

    // ---------------------------------------------------------------- jauge

    static AbilityState gauge(Player p) {
        CfPlayer cf = Unordinary.plugin().players().get(p);
        if (cf.character() == null) return null;
        Ability a = cf.character().ability("arlo_jauge");
        return a == null ? null : Unordinary.plugin().abilities().state(p, a);
    }
    static double aura(Player p) { AbilityState g = gauge(p); return g == null ? 0 : g.gauge(); }
    static boolean spend(Player p, double amount) {
        AbilityState g = gauge(p);
        if (g == null || g.gauge() < amount) return false;
        g.setGauge(g.gauge() - amount);
        return true;
    }

    /** La jauge partagee : jamais "activee", elle ne fait que se remplir (+3/s) et s'afficher. */
    static final class AuraGauge extends ToggleAbility {
        AuraGauge() { super("arlo_jauge", "Aura", "La jauge d'Arlo : 100 points, +3 par seconde. Les barrieres et les cages la consomment.", Material.GOLD_NUGGET, 0, 3); }
        @Override public boolean hasItem() { return false; }
        @Override public boolean onEnable(Player p, CfPlayer cf, AbilityState st) { return false; }
        @Override public void onDisable(Player p, CfPlayer cf, AbilityState st) {}
    }

    static void switchOff(Player p, String id) {
        CfPlayer cf = Unordinary.plugin().players().get(p);
        Ability a = cf.character() == null ? null : cf.character().ability(id);
        if (!(a instanceof ToggleAbility t)) return;
        AbilityState st = Unordinary.plugin().abilities().state(p, a);
        if (st.isEnabled()) { st.setEnabled(false); t.onDisable(p, cf, st); }
    }

    // ------------------------------------------------------------- barrieres

    /** Barriere : 5 par seconde, moitie des degats. */
    static final class Barriere extends ToggleAbility {
        Barriere() { super("arlo_barriere", "Barriere", "Tant qu'elle est active (5 d'Aura par seconde), Arlo ne prend que la moitie des degats.", Material.GOLDEN_HORSE_ARMOR, 0, 0); }
        @Override protected List<String> extraLore() { return List.of("", "<yellow>Clic droit : activer / desactiver", "<gray>Consomme 5 d'Aura par seconde"); }
        @Override public boolean onEnable(Player p, CfPlayer cf, AbilityState st) {
            if (aura(p) < 5) { Msg.actionBar(p, "<red>Pas assez d'Aura."); return false; }
            switchOff(p, "arlo_barriere_ultime");
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
            return true;
        }
        @Override public void onDisable(Player p, CfPlayer cf, AbilityState st) { p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.4f); }
        @Override public void tickActive(Player p, CfPlayer cf, AbilityState st) {
            if (!spend(p, 5)) { st.setEnabled(false); onDisable(p, cf, st); Msg.actionBar(p, "<red>Barriere : plus d'Aura."); return; }
            p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1.1, 0), 10, 0.6, 0.9, 0.6, 0, GOLD);
        }
        @Override public void onDamaged(Player victim, EntityDamageEvent e, AbilityState st) {
            if (!st.isEnabled()) return;
            e.setDamage(e.getDamage() * 0.5);
            victim.getWorld().spawnParticle(Particle.DUST, victim.getLocation().add(0, 1, 0), 12, 0.4, 0.6, 0.4, 0, GOLD);
        }
    }

    /** Barriere Ultime : 10 par seconde, aucun degat, l'attaquant encaisse a la place d'Arlo. */
    static final class BarriereUltime extends ToggleAbility {
        BarriereUltime() { super("arlo_barriere_ultime", "Barriere Ultime", "Tant qu'elle est active (10 d'Aura par seconde), Arlo ne prend aucun degat : celui qui tente de le blesser les prend a sa place.", Material.GOLD_BLOCK, 0, 0); }
        @Override protected List<String> extraLore() { return List.of("", "<yellow>Clic droit : activer / desactiver", "<gray>Consomme 10 d'Aura par seconde"); }
        @Override public boolean onEnable(Player p, CfPlayer cf, AbilityState st) {
            if (aura(p) < 10) { Msg.actionBar(p, "<red>Pas assez d'Aura."); return false; }
            switchOff(p, "arlo_barriere");
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.8f);
            return true;
        }
        @Override public void onDisable(Player p, CfPlayer cf, AbilityState st) { p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 0.8f); }
        @Override public void tickActive(Player p, CfPlayer cf, AbilityState st) {
            if (!spend(p, 10)) { st.setEnabled(false); onDisable(p, cf, st); Msg.actionBar(p, "<red>Barriere Ultime : plus d'Aura."); return; }
            Location c = p.getLocation().add(0, 1, 0);
            for (int i = 0; i < 16; i++) { double a = i * Math.PI / 8; p.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 1.2, Math.sin(a * 3) * 0.6, Math.sin(a) * 1.2), 1, 0, 0, 0, 0, GOLD); }
        }
        @Override public void onDamaged(Player victim, EntityDamageEvent e, AbilityState st) {
            if (!st.isEnabled()) return;
            double dmg = e.getDamage();
            e.setCancelled(true);
            victim.getWorld().spawnParticle(Particle.ENCHANTED_HIT, victim.getLocation().add(0, 1, 0), 10, 0.4, 0.6, 0.4);
            victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5f, 2f);
            if (e instanceof EntityDamageByEntityEvent be) {
                LivingEntity attacker = DamageListener.livingAttackerOf(be.getDamager());
                if (attacker != null && attacker != victim && Effects.isEnemy(victim, attacker)) {
                    Bukkit.getScheduler().runTask(Unordinary.plugin(), () -> { attacker.setNoDamageTicks(0); Effects.damage(attacker, dmg, victim); });
                }
            }
        }
    }

    // ----------------------------------------------------------------- cages

    static final class Cage {
        final LivingEntity target; final TempBlocks blocks = new TempBlocks().unbreakable(); final boolean suffocate;
        Cage(LivingEntity target, boolean suffocate) { this.target = target; this.suffocate = suffocate; }
    }
    static final Map<UUID, List<Cage>> CAGES = new HashMap<>();

    static List<Cage> cagesOf(Player p) { return CAGES.computeIfAbsent(p.getUniqueId(), u -> new ArrayList<>()); }

    static void build(Cage cage) {
        Location base = cage.target.getLocation();
        World w = base.getWorld();
        Material glass = cage.suffocate ? Material.TINTED_GLASS : Material.GLASS;
        int bx = base.getBlockX(), by = base.getBlockY(), bz = base.getBlockZ();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = -1; dy <= 2; dy++) {
            boolean inside = dx == 0 && dz == 0 && dy >= 0 && dy <= 1;
            if (inside) continue;
            cage.blocks.place(w.getBlockAt(bx + dx, by + dy, bz + dz), glass);
        }
        cage.target.teleport(new Location(w, bx + 0.5, by, bz + 0.5, base.getYaw(), base.getPitch()));
        w.playSound(base, Sound.BLOCK_GLASS_PLACE, 1f, 0.8f);
        w.spawnParticle(Particle.DUST, base.clone().add(0, 1, 0), 30, 0.8, 1, 0.8, 0, GOLD);
    }

    static void release(Player arlo, Cage cage, String why) {
        cage.blocks.restore();
        Effects.unroot(cage.target);
        cagesOf(arlo).remove(cage);
        if (!cage.target.isDead()) {
            cage.target.getWorld().playSound(cage.target.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1f);
            Effects.say(cage.target, "<gray>Libere" + (why == null ? "" : " : " + why));
        }
    }

    static void releaseAll(Player arlo, String why) {
        for (Cage c : new ArrayList<>(cagesOf(arlo))) release(arlo, c, why);
    }

    /** Enfermement (5/s par cage) et Etouffement (10/s par cage, 1 coeur par seconde). */
    static final class Enfermement extends ActiveAbility implements CustomUse {
        final boolean suffocate;
        Enfermement(boolean suffocate) {
            super(suffocate ? "arlo_etouffement" : "arlo_enfermement", suffocate ? "Etouffement" : "Enfermement",
                    suffocate ? "Clic droit vers un adversaire : il est enferme dans une cage sombre et perd 1 coeur par seconde (10 d'Aura par seconde). Accroupi + clic droit sur lui : relache. Plusieurs cages possibles."
                              : "Clic droit vers un adversaire : il est enferme dans une cage de verre (5 d'Aura par seconde). Accroupi + clic droit sur lui : relache. Plusieurs cages possibles.",
                    suffocate ? Material.TINTED_GLASS : Material.GLASS, 0);
            this.suffocate = suffocate;
        }
        @Override protected List<String> extraLore() { return List.of("", "<yellow>Clic droit : enfermer  <gray>|  <yellow>Accroupi + clic : liberer"); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            long n = cagesOf(p).stream().filter(c -> c.suffocate == suffocate).count();
            return (suffocate ? "<dark_gray>Etouffement" : "<white>Enfermement") + " <gray>[" + n + "]";
        }

        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            World w = p.getWorld();
            // On vise l'entite directement (le verre d'une cage ne doit pas bloquer la visee).
            RayTraceResult hit = w.rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), 20, 1.0,
                    e -> e instanceof LivingEntity le && Effects.isEnemy(p, le));
            LivingEntity target = hit == null ? null : (LivingEntity) hit.getHitEntity();
            if (target == null) { Msg.actionBar(p, "<red>Vise un adversaire a moins de 20 blocs."); return true; }
            for (Cage c : new ArrayList<>(cagesOf(p))) {
                if (c.target == target) {
                    if (sneaking) { release(p, c, null); Msg.actionBar(p, "<gray>Adversaire relache."); }
                    else Msg.actionBar(p, "<red>Deja enferme.");
                    return true;
                }
            }
            if (sneaking) { Msg.actionBar(p, "<gray>Cet adversaire n'est pas enferme."); return true; }
            if (!Effects.canSee(p, target)) { Msg.actionBar(p, "<red>Un mur te separe de cet adversaire."); return true; }
            double cost = suffocate ? 10 : 5;
            if (aura(p) < cost) { Msg.actionBar(p, "<red>Pas assez d'Aura."); return true; }
            Cage cage = new Cage(target, suffocate);
            cagesOf(p).add(cage);
            build(cage);
            Effects.root(target, 30);
            Effects.say(target, suffocate ? "<dark_gray>Etouffe par " + p.getName() + " !" : "<white>Enferme par " + p.getName() + " !");
            Msg.actionBar(p, (suffocate ? "<dark_gray>Etouffement" : "<white>Enfermement") + " : adversaire enferme.");
            return true;
        }

        /** Chaque seconde : consomme l'Aura de chaque cage de ce type, etouffe, et libere si l'Aura manque. */
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) {
            for (Cage c : new ArrayList<>(cagesOf(p))) {
                if (c.suffocate != suffocate) continue;
                if (c.target.isDead() || !c.target.isValid() || c.target.getWorld() != p.getWorld()) { release(p, c, null); continue; }
                if (!spend(p, suffocate ? 10 : 5)) { release(p, c, "plus d'Aura"); continue; }
                Effects.root(c.target, 30);
                if (suffocate) {
                    c.target.setNoDamageTicks(0);
                    Effects.damage(c.target, 2.0, p);
                    c.target.getWorld().spawnParticle(Particle.SQUID_INK, c.target.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.02);
                }
            }
        }
        @Override public void onUnequip(Player p, CfPlayer cf) { releaseAll(p, null); }
    }

    // ---------------------------------------------------------------- ultime

    /** Bulle geante : sphere de verre indestructible de 30 blocs autour d'Arlo pendant 1 minute ; Force aux allies dedans. */
    static final class BulleGeante extends UltimateAbility {
        BulleGeante() {
            super("arlo_ult", "Bulle geante", "Pendant 1 minute, une cage de verre indestructible de 30 blocs de rayon apparait autour d'Arlo. Ses allies a l'interieur gagnent Force.", Material.GLASS);
        }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            World w = p.getWorld();
            Location c = p.getLocation().clone();
            final double R = 30.0;
            TempBlocks shell = new TempBlocks().unbreakable();
            // Les blocs de la coquille, par couches, poses sur quelques ticks.
            List<int[]> cells = new ArrayList<>();
            for (int dx = -30; dx <= 30; dx++) for (int dy = -30; dy <= 30; dy++) for (int dz = -30; dz <= 30; dz++) {
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d >= R - 0.5 && d < R + 0.5) cells.add(new int[]{dx, dy, dz});
            }
            w.playSound(c, Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.6f);
            w.playSound(c, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 0.8f);
            List<Player> boosted = new ArrayList<>();
            new BukkitRunnable() {
                int index = 0; int t = 0;
                @Override public void run() {
                    t++;
                    try {
                        if (index < cells.size()) {
                            int end = Math.min(cells.size(), index + 1500);
                            for (; index < end; index++) {
                                int[] cell = cells.get(index);
                                int y = c.getBlockY() + cell[1];
                                if (y < w.getMinHeight() || y >= w.getMaxHeight()) continue;
                                shell.place(w.getBlockAt(c.getBlockX() + cell[0], y, c.getBlockZ() + cell[2]), Material.GLASS);
                            }
                        }
                        if (t % 20 == 0) {
                            for (Player ally : boosted) if (ally.getWorld() != w || ally.getLocation().distance(c) > R) Boosts.remove(ally, "arlo_bulle");
                            boosted.clear();
                            List<Player> team = new ArrayList<>();
                            team.add(p);
                            for (LivingEntity le : Effects.within(c, R, le -> Effects.isAlly(p, le))) if (le instanceof Player ap) team.add(ap);
                            for (Player ally : team) {
                                if (ally.getWorld() != w || ally.getLocation().distance(c) > R) continue;
                                if (Boosts.get(ally, "arlo_bulle") == null) Boosts.set(ally, "arlo_bulle", new Boosts.Boost(1, 0, 0, 0));
                                boosted.add(ally);
                            }
                        }
                        if (t >= 20 * 60) {
                            shell.restore();
                            for (Player ally : boosted) Boosts.remove(ally, "arlo_bulle");
                            w.playSound(c, Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
                            cancel();
                        }
                    } catch (Exception ex) { shell.restore(); for (Player ally : boosted) Boosts.remove(ally, "arlo_bulle"); cancel(); }
                }
            }.runTaskTimer(Unordinary.plugin(), 0L, 1L);
            Msg.actionBar(p, "<gold>Bulle geante : 1 minute");
            return true;
        }
    }

    // ---------------------------------------------------------------- events

    /** Un adversaire enferme ne sort pas de sa cage en cassant le verre (TempBlocks incassables) ni en se faisant teleporter : on le ramene. */
    @EventHandler
    public void onTeleport(org.bukkit.event.entity.EntityTeleportEvent e) {
        for (List<Cage> list : CAGES.values()) for (Cage c : list) {
            if (c.target == e.getEntity() && e.getTo() != null && e.getTo().distance(c.target.getLocation()) > 2) e.setCancelled(true);
        }
    }
}
