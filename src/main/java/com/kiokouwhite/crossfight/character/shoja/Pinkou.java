package com.kiokouwhite.crossfight.character.shoja;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.character.duo.Synergy;
import com.kiokouwhite.crossfight.listener.DamageListener;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.TempBlocks;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pinkou - Voie de l'Harmonie. Ses degats soignent le duo ; ronces, vignes et cocons.
 */
public final class Pinkou extends Character implements Listener {

    static final Particle.DustOptions PINK = new Particle.DustOptions(Color.fromRGB(255, 150, 200), 1.0f);
    /** Armure de Ronces : joueur -> expiration. Vigne : victimes empoisonnees -> Pinkou (soins). */
    static final Map<UUID, Long> THORNS = new HashMap<>();
    static final Map<UUID, UUID> VINE_POISON = new HashMap<>();

    public Pinkou() {
        super("pinkou", "Pinkou", Voie.HARMONIE, Material.PINK_TULIP);
        addAbility(new RapiereCurative().bindToWeapon());
        addAbility(new CoconMortel());
        addAbility(new ArmureDeRonces());
        addAbility(new VigneIncapacitante());
        addAbility(Synergy.ITEM);
        setUltimate(new NatureEternelle());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.IRON_SWORD, 7.0, "<light_purple><bold>Rapiere Curative", List.of("<gray>7 dmg."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Rapiere Curative : les degats infliges soignent Pinkou et son allie",
                "<white>Cocon Mortel : cocon de 5x5 avec l'ennemi le plus proche, soins doubles",
                "<white>Armure de Ronces : Thorns IV sur un allie 10 s",
                "<white>Vigne Incapacitante : zone de poison qui soigne le duo",
                "<light_purple>Ultime : Nature Eternelle, cocon et ronces geantes");
    }

    static void healDuo(Player p, double total) {
        List<Player> duo = Shoja.duo(p);
        double each = total / duo.size();
        for (Player m : duo) { Effects.heal(m, each); m.getWorld().spawnParticle(Particle.HEART, m.getLocation().add(0, 2, 0), 1, 0.2, 0.1, 0.2); }
    }

    // ------------------------------------------------------------------ arme

    static final class RapiereCurative extends PassiveAbility {
        RapiereCurative() { super("pinkou_rapiere", "Rapiere Curative", "Les coeurs infliges par Pinkou sont repartis en soins entre elle et son allie.", Material.IRON_SWORD); }
        @Override public List<String> weaponLore() { return List.of("<light_purple>Rapiere Curative : <gray>les degats soignent le duo"); }
        @Override public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) {
            double dmg = e.getFinalDamage();
            if (dmg <= 0) return;
            boolean cocoon = System.currentTimeMillis() < st.get("cocoonUntil", 0L);
            Bukkit.getScheduler().runTask(Shoja.plugin(), () -> healDuo(attacker, cocoon ? dmg * 2 : dmg));
        }
    }

    // ---------------------------------------------------------------- pouvoirs

    static final class CoconMortel extends ActiveAbility {
        CoconMortel() { super("pinkou_cocon", "Cocon Mortel", "Pinkou s'enferme 5 secondes dans un cocon de 5x5 avec l'adversaire le plus proche (10 blocs) : ses soins sont doubles pendant ce temps.", Material.MOSS_BLOCK, 20); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            LivingEntity target = Effects.nearestEnemy(p, 10);
            if (target == null) { Msg.actionBar(p, "<red>Aucun adversaire a moins de 10 blocs."); return false; }
            World w = p.getWorld();
            Location c = p.getLocation().clone();
            TempBlocks cocoon = new TempBlocks().unbreakable();
            for (int dx = -2; dx <= 2; dx++) for (int dy = -1; dy <= 3; dy++) for (int dz = -2; dz <= 2; dz++) {
                boolean shell = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == -1 || dy == 3;
                if (!shell) continue;
                cocoon.place(w.getBlockAt(c.getBlockX() + dx, c.getBlockY() + dy, c.getBlockZ() + dz), (dx + dz + dy) % 2 == 0 ? Material.MOSS_BLOCK : Material.MANGROVE_LEAVES);
            }
            Location inside = new Location(w, c.getBlockX() + 0.5, c.getBlockY(), c.getBlockZ() + 0.5);
            target.teleport(inside.clone().add(1, 0, 0));
            p.teleport(inside.clone().add(-1, 0, 0));
            var rap = cf.character().ability("pinkou_rapiere");
            if (rap != null) Shoja.plugin().abilities().state(p, rap).set("cocoonUntil", System.currentTimeMillis() + 5000L);
            cocoon.restoreAfter(100);
            w.playSound(c, Sound.BLOCK_MOSS_PLACE, 1f, 0.7f);
            w.spawnParticle(Particle.DUST, c.clone().add(0, 1, 0), 40, 2, 1.5, 2, 0, PINK);
            Effects.say(target, "<light_purple>Enferme dans le cocon de " + p.getName() + " !");
            return true;
        }
    }

    static final class ArmureDeRonces extends ActiveAbility {
        ArmureDeRonces() { super("pinkou_ronces", "Armure de Ronces", "Clic droit sur un allie (10 blocs) : une armure de ronces l'entoure pendant 10 secondes, Thorns IV (qui le frappe perd 2 coeurs).", Material.SWEET_BERRIES, 30); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            RayTraceResult hit = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), 10, 1.0, e -> e instanceof Player o && o != p && Effects.isAlly(p, o));
            Player ally = hit == null ? null : (Player) hit.getHitEntity();
            if (ally == null) ally = Shoja.partner(p);
            if (ally == null) ally = p;
            THORNS.put(ally.getUniqueId(), System.currentTimeMillis() + 10000L);
            Player a = ally;
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t += 4;
                    if (!a.isOnline() || t > 200) { cancel(); return; }
                    for (int i = 0; i < 4; i++) { double ang = t * 0.15 + i * Math.PI / 2; a.getWorld().spawnParticle(Particle.DUST, a.getLocation().add(Math.cos(ang) * 0.8, 0.3 + (t % 40) / 40.0 * 1.6, Math.sin(ang) * 0.8), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(60, 140, 40), 0.9f)); }
                }
            }.runTaskTimer(Shoja.plugin(), 0L, 4L);
            a.getWorld().playSound(a.getLocation(), Sound.BLOCK_SWEET_BERRY_BUSH_PLACE, 1f, 0.8f);
            Msg.send(a, "<green>Armure de Ronces : Thorns IV pendant 10 s !");
            return true;
        }
    }

    static final class VigneIncapacitante extends ActiveAbility {
        VigneIncapacitante() { super("pinkou_vigne", "Vigne Incapacitante", "Zone de vignes de 5x5 pendant 5 secondes : les ennemis dedans ont Poison 5 secondes, et les degats du poison soignent Pinkou et son allie.", Material.VINE, 15); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            World w = p.getWorld();
            Location c = p.getLocation().clone();
            TempBlocks vines = new TempBlocks().unbreakable();
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                int gy = Effects.groundAround(w, c.getBlockX() + dx, c.getBlockZ() + dz, c.getBlockY());
                if (gy != Integer.MIN_VALUE) vines.place(w.getBlockAt(c.getBlockX() + dx, gy + 1, c.getBlockZ() + dz), Material.MOSS_CARPET);
            }
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (t > 5) { vines.restore(); cancel(); return; }
                    w.spawnParticle(Particle.DUST, c.clone().add(0, 0.5, 0), 20, 2.5, 0.3, 2.5, 0, new Particle.DustOptions(Color.fromRGB(60, 160, 40), 1.0f));
                    for (LivingEntity e : Effects.within(c, 3.0, le -> Effects.isEnemy(p, le))) {
                        Effects.give(e, PotionEffectType.POISON, 0, 100);
                        VINE_POISON.put(e.getUniqueId(), p.getUniqueId());
                    }
                }
            }.runTaskTimer(Shoja.plugin(), 0L, 20L);
            w.playSound(c, Sound.BLOCK_VINE_PLACE, 1f, 0.8f);
            return true;
        }
    }

    static final class NatureEternelle extends UltimateAbility {
        NatureEternelle() { super("pinkou_ult", "Nature Eternelle", "Pinkou s'enferme dans un cocon enorme et couvre le sol de ronces sur 12 blocs pendant 15 secondes : les ennemis qui y marchent ont Poison 5 secondes, ses allies regenerent.", Material.FLOWERING_AZALEA); }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            World w = p.getWorld();
            Location c = p.getLocation().clone();
            TempBlocks blocks = new TempBlocks().unbreakable();
            for (int dx = -4; dx <= 4; dx++) for (int dy = -1; dy <= 5; dy++) for (int dz = -4; dz <= 4; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz + (dy - 2) * (dy - 2) * 0.8);
                if (d < 3.6 || d > 4.6) continue;
                blocks.place(w.getBlockAt(c.getBlockX() + dx, c.getBlockY() + dy, c.getBlockZ() + dz), (dx + dz + dy) % 2 == 0 ? Material.MOSS_BLOCK : Material.MANGROVE_LEAVES);
            }
            for (int dx = -12; dx <= 12; dx++) for (int dz = -12; dz <= 12; dz++) {
                if (dx * dx + dz * dz > 144 || Math.random() < 0.35) continue;
                int gy = Effects.groundAround(w, c.getBlockX() + dx, c.getBlockZ() + dz, c.getBlockY());
                if (gy != Integer.MIN_VALUE) blocks.place(w.getBlockAt(c.getBlockX() + dx, gy + 1, c.getBlockZ() + dz), Math.random() < 0.3 ? Material.SWEET_BERRY_BUSH : Material.MOSS_CARPET);
            }
            w.playSound(c, Sound.BLOCK_MOSS_PLACE, 1f, 0.5f);
            w.playSound(c, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 0.8f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (t > 15) { blocks.restore(); cancel(); return; }
                    w.spawnParticle(Particle.DUST, c.clone().add(0, 1, 0), 30, 8, 0.5, 8, 0, PINK);
                    for (LivingEntity e : Effects.within(c, 12, le -> Effects.isEnemy(p, le))) {
                        int gy = Effects.groundAround(w, e.getLocation().getBlockX(), e.getLocation().getBlockZ(), e.getLocation().getBlockY());
                        if (gy != Integer.MIN_VALUE && blocks.contains(w.getBlockAt(e.getLocation().getBlockX(), gy + 1, e.getLocation().getBlockZ()))) { Effects.give(e, PotionEffectType.POISON, 0, 100); VINE_POISON.put(e.getUniqueId(), p.getUniqueId()); }
                    }
                    for (Player m : Shoja.duo(p)) Effects.give(m, PotionEffectType.REGENERATION, 0, 30);
                }
            }.runTaskTimer(Shoja.plugin(), 0L, 20L);
            return true;
        }
    }

    // ---------------------------------------------------------------- events

    /** Thorns IV : celui qui frappe un joueur protege perd 2 coeurs. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onThorns(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player v)) return;
        Long until = THORNS.get(v.getUniqueId());
        if (until == null || System.currentTimeMillis() > until) return;
        LivingEntity attacker = DamageListener.livingAttackerOf(e.getDamager());
        if (attacker == null || attacker == v || !Effects.isEnemy(v, attacker)) return;
        Bukkit.getScheduler().runTask(Shoja.plugin(), () -> { attacker.setNoDamageTicks(0); Effects.damage(attacker, 4.0, v); });
        attacker.getWorld().spawnParticle(Particle.DUST, attacker.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0, new Particle.DustOptions(Color.fromRGB(60, 140, 40), 1.0f));
        attacker.getWorld().playSound(attacker.getLocation(), Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1f, 0.8f);
    }

    /** Tir Curatif : le tir touche un adversaire, la tireuse regagne 2 coeurs et l'adversaire a Lenteur. */
    @EventHandler
    public void onHealShot(org.bukkit.event.entity.ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof org.bukkit.entity.Arrow a) || !a.getPersistentDataContainer().has(ShojaSynergies.HEAL_SHOT) || !(a.getShooter() instanceof LivingEntity p)) return;
        if (!(e.getHitEntity() instanceof LivingEntity t) || !Effects.isEnemyOf(p, t)) return;
        Effects.give(t, PotionEffectType.SLOWNESS, 0, 100);
        Effects.heal(p, 4.0);
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 4, 0.3, 0.2, 0.3);
    }

    /** Pardon Temporaire : les coups du duo enflamment. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFireHits(EntityDamageByEntityEvent e) {
        Player a = DamageListener.attackerOf(e.getDamager());
        if (a == null || !(e.getEntity() instanceof LivingEntity t)) return;
        Long until = ShojaSynergies.FIRE_HITS.get(a.getUniqueId());
        if (until == null || System.currentTimeMillis() > until) return;
        t.setFireTicks(Math.max(t.getFireTicks(), 80));
    }

    /** Les degats de poison des vignes soignent Pinkou et son allie. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPoison(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.POISON) return;
        UUID pid = VINE_POISON.get(e.getEntity().getUniqueId());
        if (pid == null) return;
        Player p = Bukkit.getPlayer(pid);
        if (p == null || !"pinkou".equals(Shoja.characterId(p))) { VINE_POISON.remove(e.getEntity().getUniqueId()); return; }
        if (!(e.getEntity() instanceof LivingEntity le) || !le.hasPotionEffect(PotionEffectType.POISON)) { VINE_POISON.remove(e.getEntity().getUniqueId()); return; }
        healDuo(p, e.getFinalDamage());
    }
}
