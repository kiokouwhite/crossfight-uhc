package com.kiokouwhite.crossfight.character.gunvolt;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.ToggleAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.character.duo.Synergy;
import com.kiokouwhite.crossfight.listener.DamageListener;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Bullets;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gunvolt - Voie de la Destruction. Ses balles posent de l'Electricite Septimale.
 */
public final class Gunvolt extends Character implements Listener {

    static final NamespacedKey CERBERE = new NamespacedKey("crossfight", "gv_cerbere");
    /** Stacks d'Electricite Septimale par cible : dates d'expiration. */
    static final Map<UUID, List<Long>> STACKS = new HashMap<>();

    public Gunvolt() {
        super("gunvolt", "Gunvolt", Voie.DESTRUCTION, Material.LIGHTNING_ROD);
        addAbility(new Cerbere().bindToWeapon());
        addAbility(new ChampElectrique());
        addAbility(new ChaineDeTelesquive());
        addAbility(new DashAzur());
        addAbility(Synergy.ITEM);
        setUltimate(new Astrasphere());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.IRON_HOE, 1.0, "<aqua><bold>Cerbere", List.of("<gray>Pistolet : une balle toutes les 1.5 s."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Cerbere : balles qui posent l'Electricite Septimale (3 stacks)",
                "<white>Champ Electrique : jauge, degats autour et sur les cibles marquees",
                "<white>Chaine de Telesquive : 5 esquives, puis 30 s",
                "<white>Dash Azur : recule de 5 blocs",
                "<light_purple>Ultime : Astrasphere, champ de 10x10 pendant 5 s");
    }

    // ---------------------------------------------------------------- stacks

    static List<Long> stacks(LivingEntity e) {
        List<Long> l = STACKS.computeIfAbsent(e.getUniqueId(), u -> new ArrayList<>());
        l.removeIf(exp -> System.currentTimeMillis() >= exp);
        return l;
    }
    static int stackCount(LivingEntity e) { return stacks(e).size(); }
    static void addStack(LivingEntity e) {
        List<Long> l = stacks(e);
        if (l.size() >= 3) l.remove(0);
        l.add(System.currentTimeMillis() + 3000L);
        e.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, e.getLocation().add(0, 1, 0), 10 * l.size(), 0.3, 0.5, 0.3, 0.1);
        Effects.say(e, "<aqua>Electricite Septimale x" + l.size());
    }

    /** Toutes les entites marquees, a portee du joueur. */
    static List<LivingEntity> marked(Player p, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : Effects.enemiesWithin(p, radius)) if (stackCount(e) > 0) out.add(e);
        return out;
    }

    // ------------------------------------------------------------------ arme

    static final class Cerbere extends ActiveAbility implements CustomUse {
        Cerbere() {
            super("gv_cerbere", "Cerbere", "Clic droit : une balle toutes les 1.5 secondes (Puissance II). Chaque balle qui touche pose un stack d'Electricite Septimale (3 max, un stack dure 3 secondes).", Material.IRON_HOE, 0);
        }
        @Override public List<String> weaponLore() { return List.of("<aqua>Clic droit : <gray>tirer (1.5 s), pose l'Electricite Septimale"); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { return null; }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            long now = System.currentTimeMillis();
            if (now < st.get("nextShot", 0L)) return true;
            st.set("nextShot", now + 1500L);
            Arrow a = Bullets.shoot(p, p.getEyeLocation().getDirection(), 3.0, 7.0, Material.LIGHT_BLUE_CONCRETE, Color.fromRGB(100, 200, 255));
            a.getPersistentDataContainer().set(CERBERE, PersistentDataType.BYTE, (byte) 1);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.8f, 1.8f);
            return true;
        }
    }

    /** Synergie Lame Electrisante : chaque coup immobilise 10 s. */
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR, ignoreCancelled = true)
    public void onElectricBlade(org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        Player a = DamageListener.attackerOf(e.getDamager());
        if (a == null || !(e.getEntity() instanceof LivingEntity t) || t == a) return;
        Long until = Synergies.ELECTRIC_BLADE.get(a.getUniqueId());
        if (until == null || System.currentTimeMillis() > until || !Effects.isEnemy(a, t)) return;
        Effects.root(t, 200);
        t.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, t.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
        Effects.say(t, "<aqua>Lame Electrisante : immobilise 10 s !");
    }

    @EventHandler
    public void onBullet(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow a) || !a.getPersistentDataContainer().has(CERBERE) || !(a.getShooter() instanceof Player p)) return;
        if (e.getHitEntity() instanceof LivingEntity t && Effects.isEnemy(p, t)) addStack(t);
    }

    // ---------------------------------------------------------------- pouvoirs

    /** Jauge 100 : -5/s actif, +1/s inactif. Champ de 3x3 : 0.5 coeur/s ; les cibles marquees prennent les degats a distance. */
    static final class ChampElectrique extends ToggleAbility {
        ChampElectrique() {
            super("gv_champ", "Champ Electrique", "Champ d'electricite de 3x3 autour de Gunvolt : 0.5 coeur par seconde aux adversaires dedans. Les adversaires avec un stack d'Electricite Septimale prennent les degats meme hors du champ. 5 Puissance par seconde (100 max, +1 par seconde).", Material.LIGHTNING_ROD, 5, 1);
        }
        @Override public boolean onEnable(Player p, CfPlayer cf, AbilityState st) {
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.8f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || !st.isEnabled()) { cancel(); return; }
                    World w = p.getWorld();
                    Location c = p.getLocation();
                    for (int i = 0; i < 12; i++) { double a = i * Math.PI / 6 + t * 0.15; w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 1.5, 0.2 + Math.random() * 0.4, Math.sin(a) * 1.5), 1, 0, 0, 0, 0, ABG.CYAN); }
                    if (t % 4 == 0) ABG.sparks(w, c, 1.5, 1);
                    if (t % 10 == 0) for (LivingEntity m : marked(p, 40)) ABG.arc(w, c.clone().add(0, 1, 0), m.getLocation().add(0, 1, 0));
                }
            }.runTaskTimer(ABG.plugin(), 1L, 1L);
            return true;
        }
        @Override public void onDisable(Player p, CfPlayer cf, AbilityState st) { p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.8f); }
        @Override public void tickActive(Player p, CfPlayer cf, AbilityState st) {
            java.util.Set<UUID> done = new java.util.HashSet<>();
            for (LivingEntity e : Effects.enemiesWithin(p, 1.5)) { done.add(e.getUniqueId()); e.setNoDamageTicks(0); Effects.damage(e, 1.0, p); }
            for (LivingEntity e : marked(p, 40)) if (done.add(e.getUniqueId())) { e.setNoDamageTicks(0); Effects.damage(e, 1.0, p); e.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, e.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.1); }
        }
    }

    /** 5 esquives : chaque attaque subie en consomme une et ne fait rien ; a zero, 30 s pour les ravoir. */
    static final class ChaineDeTelesquive extends PassiveAbility {
        ChaineDeTelesquive() {
            super("gv_telesquive", "Chaine de Telesquive", "5 Telesquives : chaque attaque d'un ennemi en consomme une et n'inflige aucun degat. Quand elles sont toutes consommees, 30 secondes avant d'en ravoir 5.", Material.PHANTOM_MEMBRANE);
        }
        int dodges(AbilityState st) {
            int d = st.get("dodges", 5);
            long back = st.get("backAt", 0L);
            if (d <= 0 && back > 0 && System.currentTimeMillis() >= back) { d = 5; st.set("dodges", 5); st.set("backAt", 0L); }
            return d;
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            int d = dodges(st);
            if (d <= 0) return "<gray>Telesquive <red>" + Math.max(0, (st.get("backAt", 0L) - System.currentTimeMillis()) / 1000 + 1) + "s";
            return "<aqua>Telesquive <white>" + "●".repeat(d) + "<dark_gray>" + "●".repeat(5 - d);
        }
        @Override public void onDamaged(Player victim, EntityDamageEvent e, AbilityState st) {
            if (!(e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent be)) return;
            LivingEntity attacker = DamageListener.livingAttackerOf(be.getDamager());
            if (attacker == null || attacker == victim || !Effects.isEnemy(victim, attacker)) return;
            int d = dodges(st);
            if (d <= 0) return;
            d--;
            st.set("dodges", d);
            if (d <= 0) st.set("backAt", System.currentTimeMillis() + 30000L);
            e.setCancelled(true);
            World w = victim.getWorld();
            Location from = victim.getLocation();
            // La telesquive : un court deplacement de cote dans un eclair.
            Vector side = from.getDirection().setY(0).crossProduct(new Vector(0, 1, 0)).normalize().multiply(Math.random() < 0.5 ? 1.5 : -1.5);
            Location to = from.clone().add(side);
            if (to.getBlock().isPassable() && to.clone().add(0, 1, 0).getBlock().isPassable()) victim.teleport(to);
            w.spawnParticle(Particle.ELECTRIC_SPARK, from.add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.2);
            w.playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 0.6f, 2f);
            Msg.actionBar(victim, d > 0 ? "<aqua>Telesquive ! <white>" + d + " restante(s)" : "<red>Plus de Telesquive : 30 s");
        }
    }

    static final class DashAzur extends ActiveAbility {
        DashAzur() { super("gv_dash", "Dash Azur", "Gunvolt fait un dash en arriere de 5 blocs.", Material.FEATHER, 15); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Vector back = p.getLocation().getDirection().setY(0);
            if (back.lengthSquared() < 0.01) back = new Vector(1, 0, 0);
            back.normalize().multiply(-5);
            Effects.rush(p, p.getLocation().add(back), 3, Particle.ELECTRIC_SPARK);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1.6f);
            return true;
        }
    }

    static final class Astrasphere extends UltimateAbility {
        Astrasphere() {
            super("gv_ult", "Astrasphere", "Pendant 5 secondes, un champ d'electricite de 10x10 autour de Gunvolt : 1 coeur par seconde, +0.5 coeur par stack d'Electricite Septimale.", Material.BEACON);
        }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            World w = p.getWorld();
            w.strikeLightningEffect(p.getLocation());
            w.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.5f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || t > 100) { cancel(); return; }
                    Location c = p.getLocation();
                    for (int i = 0; i < 24; i++) { double a = i * Math.PI / 12 + t * 0.08; w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 5, 0.2 + Math.random() * 1.5, Math.sin(a) * 5), 1, 0, 0, 0, 0, ABG.BLUE); }
                    if (t % 2 == 0) ABG.sparks(w, c, 5, 3);
                    if (t % 20 == 0) {
                        w.playSound(c, Sound.BLOCK_BEACON_AMBIENT, 1f, 1.6f);
                        for (LivingEntity e : Effects.enemiesWithin(p, 5)) { e.setNoDamageTicks(0); Effects.damage(e, 2.0 + stackCount(e), p); }
                    }
                }
            }.runTaskTimer(ABG.plugin(), 0L, 1L);
            return true;
        }
    }
}
