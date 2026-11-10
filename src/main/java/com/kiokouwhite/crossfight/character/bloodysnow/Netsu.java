package com.kiokouwhite.crossfight.character.bloodysnow;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.SwitchAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.character.duo.Synergy;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Stealth;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Netsu Lewis - Voie de la Destruction. Papillons, dashs et lancer d'arme.
 */
public final class Netsu extends Character implements Listener {

    static final Particle.DustOptions PINK = new Particle.DustOptions(Color.fromRGB(255, 170, 220), 0.8f);

    public Netsu() {
        super("netsu", "Netsu Lewis", Voie.DESTRUCTION, Material.PINK_PETALS);
        addAbility(new Crawnatus().bindToWeapon());
        addAbility(new NueeDePapillons());
        addAbility(new AilesEphemeres());
        addAbility(new JeteeEclatante());
        addAbility(Synergy.ITEM);
        setUltimate(new VisionTrigonometrique());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.DIAMOND_SWORD, 9.0, "<light_purple><bold>Crawnatus", List.of("<gray>9 dmg."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Crawnatus : mode offensif (Speed II) ou defensif (portee 3.5)",
                "<white>Nuee de Papillons : un clone de 4 coeurs, puis vol en papillons",
                "<white>Ailes Ephemeres : 3 dashs de 4 blocs",
                "<white>Jetee Eclatante : lance Crawnatus, Cecite 3 s",
                "<light_purple>Ultime : Vision Trigonometrique, distances et teleportation sur les ennemis");
    }

    static void butterflies(World w, Location c, int n) {
        w.spawnParticle(Particle.CHERRY_LEAVES, c, n, 0.5, 0.6, 0.5, 0.02);
        w.spawnParticle(Particle.DUST, c, n / 2, 0.5, 0.6, 0.5, 0, PINK);
    }

    // ------------------------------------------------------------------ arme

    /** Intervertible : offensif = Speed II, defensif = portee de 3.5 blocs. */
    static final class Crawnatus extends SwitchAbility {
        Crawnatus() { super("netsu_crawnatus", "Crawnatus", "Clic droit : alterne entre le mode offensif (Speed II) et le mode defensif (portee de frappe de 3.5 blocs).", Material.DIAMOND_SWORD, List.of("Offensif", "Defensif")); }
        @Override public List<String> weaponLore() { return List.of("<light_purple>Clic droit : <gray>mode offensif (Speed II) / defensif (portee 3.5)"); }
        @Override public void onEquip(Player p, CfPlayer cf) { apply(p, BloodySnow.plugin().abilities().state(p, this).mode()); }
        @Override public void onUnequip(Player p, CfPlayer cf) { Boosts.remove(p, "netsu_mode"); BloodySnow.setReach(p, "netsu_mode", null); }
        @Override public void onSwitch(Player p, CfPlayer cf, AbilityState st, int oldMode, int newMode) { apply(p, newMode); }
        static void apply(Player p, int mode) {
            if (mode == 0) { Boosts.set(p, "netsu_mode", new Boosts.Boost(0, 2, 0, 0)); BloodySnow.setReach(p, "netsu_mode", null); }
            else { Boosts.remove(p, "netsu_mode"); BloodySnow.setReach(p, "netsu_mode", 3.5); }
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_CHAIN, 1f, mode == 0 ? 1.6f : 0.8f);
        }
    }

    // ---------------------------------------------------------------- pouvoirs

    /**
     * Netsu incarne un clone de 4 coeurs : les degats qu'elle recoit vont au clone. Quand il est tue,
     * elle s'envole en nuee de papillons (invisible, intouchable, en vol) et re-clique pour reapparaitre la ou elle vise.
     */
    static final class NueeDePapillons extends ActiveAbility implements CustomUse {
        NueeDePapillons() { super("netsu_papillons", "Nuee de Papillons", "Netsu incarne un clone de 4 coeurs pendant 30 secondes : les coups qu'elle recoit frappent le clone. Quand il est tue, elle s'envole en nuee de papillons (10 s) et un clic droit la fait reapparaitre la ou elle vise.", Material.PINK_PETALS, 60); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            st.set("cloneHp", 8.0);
            st.set("cloneUntil", System.currentTimeMillis() + 30000L);
            st.set("swarm", false);
            World w = p.getWorld();
            butterflies(w, p.getLocation().add(0, 1, 0), 40);
            w.playSound(p.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 1f, 1.5f);
            Msg.actionBar(p, "<light_purple>Nuee de Papillons : clone de 4 coeurs (30 s)");
            return true;
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            if (st.get("swarm", false)) return "<light_purple>Papillons <white>clic : reapparaitre";
            if (System.currentTimeMillis() < st.get("cloneUntil", 0L)) return "<light_purple>Clone <white>" + String.format(java.util.Locale.US, "%.1f", st.get("cloneHp", 0.0) / 2) + " coeur(s)";
            return null;
        }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            if (!st.get("swarm", false)) return false;
            RayTraceResult hit = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 30, FluidCollisionMode.NEVER, true);
            Location to = hit != null && hit.getHitBlock() != null ? hit.getHitPosition().toLocation(p.getWorld()).add(hit.getHitBlockFace().getDirection().multiply(0.6)) : p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(30));
            to.setDirection(p.getLocation().getDirection());
            land(p, st, to);
            return true;
        }
        @Override public void onDamaged(Player victim, EntityDamageEvent e, AbilityState st) {
            if (st.get("swarm", false)) { e.setCancelled(true); return; }
            if (System.currentTimeMillis() > st.get("cloneUntil", 0L)) return;
            double hp = st.get("cloneHp", 0.0) - e.getFinalDamage();
            e.setCancelled(true);
            butterflies(victim.getWorld(), victim.getLocation().add(0, 1, 0), 6);
            if (hp > 0) { st.set("cloneHp", hp); return; }
            // Le clone meurt : Netsu s'envole.
            st.set("cloneUntil", 0L);
            st.set("swarm", true);
            st.set("swarmUntil", System.currentTimeMillis() + 10000L);
            Effects.give(victim, PotionEffectType.INVISIBILITY, 0, 220);
            Stealth.hideArmorOnly(victim);
            victim.setAllowFlight(true); victim.setFlying(true);
            Effects.noFallUntil(victim, 15000);
            World w = victim.getWorld();
            w.playSound(victim.getLocation(), Sound.ENTITY_ALLAY_DEATH, 1f, 1.4f);
            butterflies(w, victim.getLocation().add(0, 1, 0), 80);
            Msg.actionBar(victim, "<light_purple>Le clone est tombe : tu voles en papillons ! Clic droit pour reapparaitre.");
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!victim.isOnline() || !st.get("swarm", false)) { cancel(); return; }
                    butterflies(w, victim.getLocation().add(0, 1, 0), 4);
                    if (t >= 200) { land(victim, st, victim.getLocation()); cancel(); }
                }
            }.runTaskTimer(BloodySnow.plugin(), 1L, 1L);
        }
        static void land(Player p, AbilityState st, Location to) {
            st.set("swarm", false);
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
            if (p.getGameMode() == org.bukkit.GameMode.SURVIVAL || p.getGameMode() == org.bukkit.GameMode.ADVENTURE) { p.setFlying(false); p.setAllowFlight(false); }
            Effects.noFallUntil(p, 5000);
            p.teleport(to);
            butterflies(p.getWorld(), to.clone().add(0, 1, 0), 50);
            p.getWorld().playSound(to, Sound.ENTITY_ALLAY_ITEM_GIVEN, 1f, 1.2f);
            Msg.actionBar(p, "<light_purple>Netsu reapparait !");
        }
        @Override public void onUnequip(Player p, CfPlayer cf) {
            AbilityState st = BloodySnow.plugin().abilities().state(p, this);
            if (st.get("swarm", false)) { st.set("swarm", false); p.removePotionEffect(PotionEffectType.INVISIBILITY); if (p.getGameMode() == org.bukkit.GameMode.SURVIVAL) { p.setFlying(false); p.setAllowFlight(false); } }
            st.set("cloneUntil", 0L);
        }
    }

    static final class AilesEphemeres extends ActiveAbility implements CustomUse {
        static final int MAX = 3; static final long RELOAD_MS = 5000L;
        AilesEphemeres() { super("netsu_ailes", "Ailes Ephemeres", "3 dashs de 4 blocs, un dash revient toutes les 5 secondes.", Material.FEATHER, 0); }
        int count(AbilityState st) {
            int b = st.get("n", MAX); long last = st.get("reloadAt", 0L); long now = System.currentTimeMillis();
            if (b < MAX) { if (last == 0L) st.set("reloadAt", now); else while (b < MAX && now - last >= RELOAD_MS) { b++; last += RELOAD_MS; } st.set("n", b); st.set("reloadAt", b >= MAX ? 0L : last); }
            return b;
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { int b = count(st); return "<light_purple>Ailes " + "●".repeat(b) + "<dark_gray>" + "●".repeat(MAX - b); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            int b = count(st);
            if (b <= 0) { Msg.actionBar(p, "<red>Plus de dash."); return true; }
            if (b == MAX) st.set("reloadAt", System.currentTimeMillis());
            st.set("n", b - 1);
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
            Effects.rush(p, p.getLocation().add(dir.normalize().multiply(4)), 2, Particle.CHERRY_LEAVES);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 0.8f, 1.6f);
            return true;
        }
    }

    static final class JeteeEclatante extends ActiveAbility {
        JeteeEclatante() { super("netsu_jetee", "Jetee Eclatante", "Netsu lance Crawnatus en avant (10 blocs) : l'adversaire touche subit les degats de l'arme et Cecite 3 secondes, et la recharge tombe a 5 secondes.", Material.DIAMOND_SWORD, 15); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            World w = p.getWorld();
            Location start = p.getEyeLocation().add(0, -0.3, 0);
            Vector dir = start.getDirection().normalize();
            ItemDisplay sword = w.spawn(start, ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(Material.DIAMOND_SWORD));
                d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.1f, 1.1f, 1.1f), new Quaternionf()));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setPersistent(false);
            });
            w.playSound(start, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.4f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || t > 40) { sword.remove(); cancel(); return; }
                    double d = t <= 20 ? t * 0.5 : (40 - t) * 0.5;
                    Location l = (t <= 20 ? start : p.getEyeLocation().add(0, -0.3, 0)).clone().add(dir.clone().multiply(d));
                    if (!l.getBlock().isPassable()) { sword.remove(); cancel(); return; }
                    l.setDirection(dir);
                    l.setYaw(l.getYaw() + t * 40);
                    sword.teleport(l);
                    w.spawnParticle(Particle.DUST, l, 2, 0.1, 0.1, 0.1, 0, PINK);
                    for (LivingEntity e : Effects.enemiesWithin(p, l, 1.3)) {
                        e.setNoDamageTicks(0);
                        Effects.damage(e, 9.0, p);
                        Effects.give(e, PotionEffectType.BLINDNESS, 0, 60);
                        st.startCooldown(5);
                        w.spawnParticle(Particle.SWEEP_ATTACK, e.getLocation().add(0, 1, 0), 2, 0.3, 0.3, 0.3, 0);
                        w.playSound(e.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
                        Msg.actionBar(p, "<light_purple>Jetee Eclatante : touche ! Recharge 5 s.");
                        sword.remove(); cancel(); return;
                    }
                }
            }.runTaskTimer(BloodySnow.plugin(), 0L, 1L);
            return true;
        }
    }

    // ----------------------------------------------------------------- ultime

    /** 30 s : portee 4 et Speed II ; distances affichees au-dessus des ennemis a 100 blocs ; clic sur eux pour se teleporter a 4 blocs. */
    static final class VisionTrigonometrique extends UltimateAbility implements CustomUse {
        static final Map<UUID, Map<UUID, TextDisplay>> LABELS = new HashMap<>();
        VisionTrigonometrique() { super("netsu_ult", "Vision Trigonometrique", "Pendant 30 secondes : portee 4 et Speed II, la distance de chaque ennemi a 100 blocs s'affiche au-dessus de lui, et un clic droit en visant un ennemi teleporte Netsu a 4 blocs de lui.", Material.SPYGLASS); }
        static boolean active(AbilityState st) { return System.currentTimeMillis() < st.get("until", 0L); }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            if (!active(st)) return false;
            Vector dir = p.getEyeLocation().getDirection().normalize();
            LivingEntity best = null; double bestDot = 0.95;
            for (LivingEntity e : Effects.enemiesWithin(p, 100)) {
                Vector to = e.getLocation().add(0, 1, 0).toVector().subtract(p.getEyeLocation().toVector());
                double dot = to.normalize().dot(dir);
                if (dot > bestDot) { bestDot = dot; best = e; }
            }
            if (best == null) { Msg.actionBar(p, "<red>Vise un ennemi."); return true; }
            Vector back = p.getLocation().toVector().subtract(best.getLocation().toVector()).setY(0);
            if (back.lengthSquared() < 0.01) back = new Vector(1, 0, 0);
            Location to = best.getLocation().add(back.normalize().multiply(4));
            to.setDirection(best.getLocation().add(0, 1, 0).toVector().subtract(to.toVector().add(new Vector(0, 1.6, 0))));
            butterflies(p.getWorld(), p.getLocation().add(0, 1, 0), 30);
            p.teleport(to);
            butterflies(p.getWorld(), to.clone().add(0, 1, 0), 30);
            p.getWorld().playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.6f);
            return true;
        }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            st.set("until", System.currentTimeMillis() + 30000L);
            Boosts.set(p, "netsu_vision", new Boosts.Boost(0, 2, 0, 0));
            BloodySnow.setReach(p, "netsu_vision", 4.0);
            Map<UUID, TextDisplay> labels = LABELS.computeIfAbsent(p.getUniqueId(), u -> new HashMap<>());
            World w = p.getWorld();
            w.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.8f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || !active(st) || t > 600) {
                        for (TextDisplay d : labels.values()) d.remove();
                        labels.clear();
                        Boosts.remove(p, "netsu_vision"); BloodySnow.setReach(p, "netsu_vision", null);
                        cancel(); return;
                    }
                    Set<UUID> seen = new HashSet<>();
                    for (LivingEntity e : Effects.enemiesWithin(p, 100)) {
                        seen.add(e.getUniqueId());
                        int dist = (int) e.getLocation().distance(p.getLocation());
                        TextDisplay d = labels.get(e.getUniqueId());
                        if (d == null || !d.isValid()) {
                            d = w.spawn(e.getLocation().add(0, 2.6, 0), TextDisplay.class, td -> {
                                td.setBillboard(Display.Billboard.CENTER);
                                td.setSeeThrough(true);
                                td.setBrightness(new Display.Brightness(15, 15));
                                td.setPersistent(false);
                            });
                            labels.put(e.getUniqueId(), d);
                            for (Player o : Bukkit.getOnlinePlayers()) if (o != p) o.hideEntity(BloodySnow.plugin(), d);
                        }
                        d.text(Msg.mm("<light_purple>" + dist + " blocs"));
                        d.teleport(e.getLocation().add(0, 2.6, 0));
                    }
                    for (UUID id : new HashSet<>(labels.keySet())) if (!seen.contains(id)) { labels.remove(id).remove(); }
                }
            }.runTaskTimer(BloodySnow.plugin(), 0L, 2L);
            Msg.send(p, "<light_purple><bold>Vision Trigonometrique :</bold> <gray>clic droit sur l'ultime en visant un ennemi pour te teleporter a 4 blocs de lui.");
            return true;
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { return active(st) ? "<light_purple><bold>VISION</bold> <white>" + ((st.get("until", 0L) - System.currentTimeMillis()) / 1000 + 1) + "s" : null; }
    }
}
