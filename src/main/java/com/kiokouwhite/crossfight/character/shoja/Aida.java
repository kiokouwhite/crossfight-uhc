package com.kiokouwhite.crossfight.character.shoja;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.character.duo.Synergy;
import com.kiokouwhite.crossfight.listener.DamageListener;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Weapons;
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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Aida - Voie de la Nihilite. L'eau : jets, bulles, clone, eponge.
 */
public final class Aida extends Character implements Listener {

    static final Particle.DustOptions WATER = new Particle.DustOptions(Color.fromRGB(80, 160, 255), 1.0f);
    static final Set<PotionEffectType> BAD = Set.of(PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA, PotionEffectType.MINING_FATIGUE, PotionEffectType.HUNGER, PotionEffectType.DARKNESS, PotionEffectType.LEVITATION, PotionEffectType.GLOWING);
    static final Set<PotionEffectType> GOOD = Set.of(PotionEffectType.SPEED, PotionEffectType.STRENGTH, PotionEffectType.REGENERATION, PotionEffectType.RESISTANCE, PotionEffectType.FIRE_RESISTANCE, PotionEffectType.ABSORPTION, PotionEffectType.HASTE, PotionEffectType.JUMP_BOOST, PotionEffectType.INVISIBILITY);
    /** Dernier attaquant d'Aida (pour renvoyer les effets). */
    static final Map<UUID, UUID> LAST_ATTACKER = new HashMap<>();

    /** Une bulle d'eau posee au sol. */
    static final class Bubble { final Location at; final UUID owner; int age; Bubble(Location at, UUID owner) { this.at = at; this.owner = owner; } }
    static final List<Bubble> BUBBLES = new ArrayList<>();

    public Aida() {
        super("aida", "Aida", Voie.NIHILITE, Material.WATER_BUCKET);
        addAbility(new LacerateursAqueux().bindToWeapon());
        addAbility(new BulleDEau());
        addAbility(new CloneDEau());
        addAbility(new EpongeCapacitive());
        addAbility(Synergy.ITEM);
        setUltimate(new PluieBouillante());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.IRON_SWORD, 7.0, "<aqua><bold>Lacerateurs Aqueux", List.of("<gray>7 dmg."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Lacerateurs Aqueux : jet d'eau de 5 blocs, Lenteur 5 s",
                "<white>Bulle d'eau : 3 bulles qui immobilisent 3 s",
                "<white>Clone d'eau : si on la frappe, elle passe derriere l'attaquant",
                "<white>Eponge Capacitive : renvoie les mauvais effets, vole les bons",
                "<light_purple>Ultime : Pluie bouillante, 1 coeur/s sous la pluie 10 s");
    }

    static void sphere(World w, Location c, double r, int n, double spin) {
        final double golden = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < n; i++) {
            double y = 1 - (i / (double) (n - 1)) * 2, rad = Math.sqrt(1 - y * y), th = golden * i + spin;
            w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(th) * rad * r, y * r, Math.sin(th) * rad * r), 1, 0, 0, 0, 0, WATER);
        }
    }

    // ------------------------------------------------------------------ arme

    static final class LacerateursAqueux extends ActiveAbility {
        LacerateursAqueux() { super("aida_jet", "Lacerateurs Aqueux", "Clic droit : un jet d'eau de 5 blocs devant Aida ; les adversaires touches ont Lenteur 5 secondes et perdent 1 coeur.", Material.IRON_SWORD, 15); }
        @Override public List<String> weaponLore() { return List.of("<aqua>Clic droit : <gray>jet d'eau de 5 blocs, Lenteur 5 s"); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            World w = p.getWorld();
            Location eye = p.getEyeLocation();
            Vector dir = eye.getDirection().normalize();
            java.util.Set<UUID> hit = new java.util.HashSet<>();
            for (double d = 0.5; d <= 5; d += 0.5) {
                Location l = eye.clone().add(dir.clone().multiply(d));
                if (!l.getBlock().isPassable()) break;
                w.spawnParticle(Particle.SPLASH, l, 6, 0.25, 0.25, 0.25, 0);
                w.spawnParticle(Particle.DUST, l, 2, 0.2, 0.2, 0.2, 0, WATER);
                for (LivingEntity e : Effects.enemiesWithin(p, l, 1.2)) if (hit.add(e.getUniqueId())) { Effects.give(e, PotionEffectType.SLOWNESS, 0, 100); Effects.damage(e, 2.0, p); }
            }
            w.playSound(eye, Sound.ENTITY_PLAYER_SPLASH, 1f, 1.4f);
            w.playSound(eye, Sound.ITEM_BUCKET_EMPTY, 0.8f, 1.6f);
            return true;
        }
    }

    // ---------------------------------------------------------------- pouvoirs

    static final class BulleDEau extends ActiveAbility implements CustomUse {
        static final int MAX = 3; static final long RELOAD_MS = 10000L;
        BulleDEau() { super("aida_bulle", "Bulle d'eau", "Aida pose une bulle d'eau devant elle : le premier adversaire qui la touche est immobilise 3 secondes. 3 bulles, une revient toutes les 10 secondes ; une bulle reste 30 secondes.", Material.HEART_OF_THE_SEA, 0); }
        int count(AbilityState st) {
            int b = st.get("n", MAX); long last = st.get("reloadAt", 0L); long now = System.currentTimeMillis();
            if (b < MAX) { if (last == 0L) st.set("reloadAt", now); else while (b < MAX && now - last >= RELOAD_MS) { b++; last += RELOAD_MS; } st.set("n", b); st.set("reloadAt", b >= MAX ? 0L : last); }
            return b;
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { int b = count(st); return "<aqua>Bulles " + "●".repeat(b) + "<dark_gray>" + "●".repeat(MAX - b); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            int b = count(st);
            if (b <= 0) { Msg.actionBar(p, "<red>Plus de bulle."); return true; }
            if (b == MAX) st.set("reloadAt", System.currentTimeMillis());
            st.set("n", b - 1);
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
            Location at = p.getLocation().add(dir.normalize().multiply(2)).add(0, 1, 0);
            Bubble bubble = new Bubble(at, p.getUniqueId());
            BUBBLES.add(bubble);
            World w = p.getWorld();
            w.playSound(at, Sound.ENTITY_BOAT_PADDLE_WATER, 1f, 1.5f);
            new BukkitRunnable() {
                @Override public void run() {
                    bubble.age++;
                    if (!BUBBLES.contains(bubble) || bubble.age > 600 || !p.isOnline()) { BUBBLES.remove(bubble); w.spawnParticle(Particle.SPLASH, at, 20, 0.5, 0.5, 0.5, 0); cancel(); return; }
                    sphere(w, at, 1.0, 24, bubble.age * 0.1);
                    for (LivingEntity e : Effects.within(at, 1.2, le -> Effects.isEnemy(p, le))) {
                        BUBBLES.remove(bubble);
                        Effects.root(e, 60);
                        w.spawnParticle(Particle.SPLASH, e.getLocation().add(0, 1, 0), 40, 0.5, 0.7, 0.5, 0);
                        w.playSound(e.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 1f, 1f);
                        Effects.say(e, "<aqua>Pris dans une bulle d'eau : immobilise 3 s !");
                        new BukkitRunnable() { int t = 0; @Override public void run() { t++; if (t > 60 || e.isDead()) { cancel(); return; } sphere(w, e.getLocation().add(0, 1, 0), 1.3, 30, t * 0.1); } }.runTaskTimer(Shoja.plugin(), 0L, 1L);
                        cancel();
                        return;
                    }
                }
            }.runTaskTimer(Shoja.plugin(), 0L, 1L);
            return true;
        }
    }

    static final class CloneDEau extends ActiveAbility {
        CloneDEau() { super("aida_clone", "Clone d'eau", "Aida s'entoure d'eau 5 secondes : si un adversaire la frappe, elle se teleporte derriere lui et lui inflige Faiblesse et Lenteur 5 secondes.", Material.GLASS_BOTTLE, 10); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            st.set("until", System.currentTimeMillis() + 5000L);
            World w = p.getWorld();
            w.playSound(p.getLocation(), Sound.ITEM_BUCKET_FILL, 1f, 1.2f);
            new BukkitRunnable() { int t = 0; @Override public void run() { t++; if (!p.isOnline() || t > 100 || System.currentTimeMillis() > st.get("until", 0L)) { cancel(); return; } sphere(w, p.getLocation().add(0, 1, 0), 1.4, 36, t * 0.08); } }.runTaskTimer(Shoja.plugin(), 0L, 2L);
            Msg.actionBar(p, "<aqua>Clone d'eau : 5 s");
            return true;
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { long left = st.get("until", 0L) - System.currentTimeMillis(); return left > 0 ? "<aqua>Clone d'eau <white>" + (left / 1000 + 1) + "s" : null; }
        @Override public void onDamaged(Player victim, EntityDamageEvent e, AbilityState st) {
            if (System.currentTimeMillis() > st.get("until", 0L) || !(e instanceof EntityDamageByEntityEvent be)) return;
            LivingEntity attacker = DamageListener.livingAttackerOf(be.getDamager());
            if (attacker == null || attacker == victim || !Effects.isEnemy(victim, attacker)) return;
            st.set("until", 0L);
            e.setCancelled(true);
            World w = victim.getWorld();
            w.spawnParticle(Particle.SPLASH, victim.getLocation().add(0, 1, 0), 40, 0.5, 0.7, 0.5, 0);
            Vector facing = attacker.getLocation().getDirection().setY(0);
            if (facing.lengthSquared() < 0.01) facing = new Vector(1, 0, 0);
            Location behind = attacker.getLocation().subtract(facing.normalize().multiply(1.5));
            if (!behind.getBlock().isPassable()) behind = attacker.getLocation();
            behind.setDirection(attacker.getLocation().add(0, 1, 0).toVector().subtract(behind.toVector().add(new Vector(0, 1.6, 0))));
            victim.teleport(behind);
            Effects.give(attacker, PotionEffectType.WEAKNESS, 0, 100);
            Effects.give(attacker, PotionEffectType.SLOWNESS, 0, 100);
            w.playSound(behind, Sound.ENTITY_PLAYER_SPLASH, 1f, 1.2f);
            Msg.actionBar(victim, "<aqua>Clone d'eau : derriere l'attaquant !");
        }
    }

    static final class EpongeCapacitive extends PassiveAbility {
        EpongeCapacitive() { super("aida_eponge", "Eponge Capacitive", "Les mauvais effets qu'Aida devrait subir sont renvoyes a son dernier attaquant ; les bons effets de l'adversaire qui la frappe lui sont copies.", Material.SPONGE); }
    }

    /** Memorise l'attaquant d'Aida et copie ses bons effets. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAidaHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player v) || !"aida".equals(Shoja.characterId(v))) return;
        LivingEntity attacker = DamageListener.livingAttackerOf(e.getDamager());
        if (attacker == null || attacker == v || !Effects.isEnemy(v, attacker)) return;
        LAST_ATTACKER.put(v.getUniqueId(), attacker.getUniqueId());
        for (PotionEffect pe : attacker.getActivePotionEffects()) {
            if (!GOOD.contains(pe.getType())) continue;
            Effects.give(v, pe.getType(), pe.getAmplifier(), Math.min(pe.getDuration(), 1200));
            v.getWorld().spawnParticle(Particle.DUST, v.getLocation().add(0, 1.5, 0), 8, 0.3, 0.3, 0.3, 0, WATER);
        }
    }

    /** Un mauvais effet sur Aida : annule et renvoye a son dernier attaquant proche. */
    @EventHandler(ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player v) || !"aida".equals(Shoja.characterId(v))) return;
        if (e.getAction() != EntityPotionEffectEvent.Action.ADDED && e.getAction() != EntityPotionEffectEvent.Action.CHANGED) return;
        PotionEffect ne = e.getNewEffect();
        if (ne == null || !BAD.contains(ne.getType())) return;
        UUID aid = LAST_ATTACKER.get(v.getUniqueId());
        var ent = aid == null ? null : Bukkit.getEntity(aid);
        if (!(ent instanceof LivingEntity attacker) || attacker.isDead() || attacker.getWorld() != v.getWorld() || attacker.getLocation().distance(v.getLocation()) > 30) return;
        e.setCancelled(true);
        Bukkit.getScheduler().runTask(Shoja.plugin(), () -> {
            Effects.give(attacker, ne.getType(), ne.getAmplifier(), ne.getDuration());
            attacker.getWorld().spawnParticle(Particle.SPLASH, attacker.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0);
        });
        Msg.actionBar(v, "<aqua>Eponge Capacitive : effet renvoye !");
    }

    // ----------------------------------------------------------------- ultime

    static final class PluieBouillante extends UltimateAbility {
        PluieBouillante() { super("aida_ult", "Pluie bouillante", "Aida fait tomber la pluie sur toute la carte pendant 10 secondes : les adversaires sous la pluie perdent 1 coeur par seconde.", Material.WATER_BUCKET); }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            World w = p.getWorld();
            boolean wasStorm = w.hasStorm();
            w.setStorm(true);
            w.setWeatherDuration(20 * 12);
            w.playSound(p.getLocation(), Sound.WEATHER_RAIN, 1f, 1f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (t > 10) { w.setStorm(wasStorm); cancel(); return; }
                    for (Player o : w.getPlayers()) {
                        if (!Effects.isEnemy(p, o)) continue;
                        Location l = o.getLocation();
                        if (w.getHighestBlockYAt(l) > l.getBlockY()) continue;
                        o.setNoDamageTicks(0);
                        Effects.damage(o, 2.0, p);
                        w.spawnParticle(Particle.FALLING_WATER, l.add(0, 2.5, 0), 20, 0.5, 0.3, 0.5, 0);
                        w.playSound(l, Sound.BLOCK_LAVA_EXTINGUISH, 0.6f, 1.6f);
                    }
                    for (LivingEntity e : Effects.enemiesWithin(p, 60)) if (!(e instanceof Player) && w.getHighestBlockYAt(e.getLocation()) <= e.getLocation().getBlockY()) { e.setNoDamageTicks(0); Effects.damage(e, 2.0, p); }
                }
            }.runTaskTimer(Shoja.plugin(), 0L, 20L);
            Msg.actionBar(p, "<aqua>Pluie bouillante : 10 s");
            return true;
        }
    }
}
