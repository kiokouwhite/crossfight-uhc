package com.kiokouwhite.crossfight.character.earthdelta;

import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.SwitchAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.ChoiceMenu;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Mannequins;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Kura - Voie de la Chasse. Ses pouvoirs se debloquent avec ses kills.
 */
public final class Kura extends Character {

    public Kura() {
        super("kura", "Kura", Voie.CHASSE, Material.NETHERITE_SWORD);
        addAbility(new VoieDeLAssassin());
        addAbility(new RueeMortelle());
        addAbility(new MeurtreParfait());
        addAbility(new ReineDuCrime());
        addAbility(new RecompenseMeritee());
        setUltimate(new PluieDeCouteaux());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.NETHERITE_SWORD, 7.0, "<dark_red><bold>Mourning Knife", List.of("<gray>7 dmg, +0.5 par kill (max 11)."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Mourning Knife : +0.5 dmg par kill",
                "<white>0 kill : Voie de l'assassin - 2 : Ruee Mortelle - 4 : Meurtre Parfait",
                "<white>6 : Reine du Crime - 8 : Recompense meritee",
                "<light_purple>Ultime : Pluie de Couteaux (1 coeur par kill, 8 max)");
    }

    static int kills(Player p) { return EarthDelta.plugin().players().get(p).kills(); }
    static boolean unlocked(Player p, int needed) { return EarthDelta.plugin().game().isTest() || kills(p) >= needed; }

    static void refreshKnife(Player p) {
        double dmg = Math.min(11.0, 7.0 + 0.5 * kills(p));
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && "kura".equals(Items.tag(it, AbilityEngine.WEAPON_KEY)) && Weapons.damageOf(it) != dmg) Weapons.setDamage(it, dmg);
        }
    }

    /** Capacite debloquee a un nombre de kills. */
    abstract static class Unlockable extends ActiveAbility {
        final int needed;
        Unlockable(String id, String name, String desc, Material icon, int cd, int needed) { super(id, name, desc, icon, cd); this.needed = needed; }
        @Override protected List<String> extraLore() {
            List<String> l = new ArrayList<>(super.extraLore());
            l.add("<red>Debloquee a " + needed + " kill(s)");
            return l;
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            return unlocked(p, needed) ? null : "<dark_gray>" + name() + " [" + kills(p) + "/" + needed + "]";
        }
        boolean locked(Player p) {
            if (unlocked(p, needed)) return false;
            Msg.actionBar(p, "<red>" + name() + " : debloquee a " + needed + " kills (" + kills(p) + ").");
            return true;
        }
    }

    static final class VoieDeLAssassin extends Unlockable {
        VoieDeLAssassin() {
            super("kura_assassin", "Voie de l'assassin", "Kura se teleporte derriere l'adversaire le plus proche a moins de 10 blocs et gagne Speed pendant 10 secondes.", Material.ENDER_PEARL, 15, 0);
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            if (locked(p)) return false;
            LivingEntity target = Effects.nearestEnemy(p, 10);
            if (target == null) { Msg.actionBar(p, "<red>Aucun adversaire a moins de 10 blocs."); return false; }
            Vector facing = target.getLocation().getDirection().setY(0);
            if (facing.lengthSquared() < 0.01) facing = new Vector(1, 0, 0);
            Location to = target.getLocation().subtract(facing.normalize().multiply(1.2));
            if (!to.getBlock().isPassable()) to = target.getLocation();
            to.setDirection(target.getLocation().add(0, 1, 0).toVector().subtract(to.toVector().add(new Vector(0, 1.6, 0))));
            World w = p.getWorld();
            w.spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
            p.teleport(to);
            w.spawnParticle(Particle.SMOKE, to.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
            w.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.6f);
            Effects.give(p, PotionEffectType.SPEED, 0, 200);
            return true;
        }
    }

    static final class RueeMortelle extends Unlockable {
        RueeMortelle() {
            super("kura_ruee", "Ruee Mortelle", "Kura fait un dash de 5 blocs et inflige 3 coeurs a l'adversaire percute.", Material.FEATHER, 15, 2);
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            if (locked(p)) return false;
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
            dir.normalize();
            Location from = p.getLocation();
            World w = p.getWorld();
            java.util.Set<UUID> hit = new java.util.HashSet<>();
            final Vector d = dir;
            new BukkitRunnable() {
                int i = 0; Location current = from.clone();
                @Override public void run() {
                    i++;
                    if (!p.isOnline() || p.isDead() || i > 5) { cancel(); return; }
                    Location next = current.clone().add(d);
                    next.setDirection(p.getLocation().getDirection());
                    if (!next.getBlock().isPassable() || !next.clone().add(0, 1, 0).getBlock().isPassable()) { cancel(); return; }
                    w.spawnParticle(Particle.CRIT, next.clone().add(0, 1, 0), 6, 0.2, 0.3, 0.2, 0.05);
                    for (LivingEntity enemy : Effects.enemiesWithin(p, next.clone().add(0, 1, 0), 1.5)) {
                        if (hit.add(enemy.getUniqueId())) { Effects.damage(enemy, 6.0, p); w.playSound(next, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f); }
                    }
                    p.teleport(next);
                    current = next;
                }
            }.runTaskTimer(EarthDelta.plugin(), 0L, 1L);
            w.playSound(from, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.4f);
            return true;
        }
    }

    /** Intervertible : Kura cible un joueur qui garde Glowing et Faiblesse. */
    static final class MeurtreParfait extends SwitchAbility implements CustomUse {
        MeurtreParfait() {
            super("kura_meurtre", "Meurtre Parfait", "Kura cible un adversaire : il a Glowing et Faiblesse tant qu'il est cible. Debloquee a 4 kills.", Material.SPYGLASS, List.of("Cible"));
        }
        @Override public void onSwitch(Player p, CfPlayer cf, AbilityState st, int oldMode, int newMode) {}
        @Override protected List<String> extraLore() { return List.of("", "<yellow>Clic droit : choisir la cible", "<red>Debloquee a 4 kills"); }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            if (!unlocked(p, 4)) { Msg.actionBar(p, "<red>Meurtre Parfait : debloquee a 4 kills (" + kills(p) + ")."); return true; }
            List<LivingEntity> targets = new ArrayList<>();
            for (LivingEntity e : Effects.enemiesWithin(p, 100)) if (e instanceof Player || Mannequins.isMannequin(e)) targets.add(e);
            if (targets.isEmpty()) { Msg.actionBar(p, "<red>Aucun adversaire a moins de 100 blocs."); return true; }
            List<ChoiceMenu.Option> options = new ArrayList<>();
            for (LivingEntity t : targets) {
                String name = t instanceof Player tp ? tp.getName() : "Mannequin";
                options.add(new ChoiceMenu.Option(t instanceof Player ? Material.PLAYER_HEAD : Material.ZOMBIE_HEAD, name, List.of("<gray>A " + (int) t.getLocation().distance(p.getLocation()) + " blocs"), () -> {
                    st.set("target", t.getUniqueId().toString());
                    Msg.actionBar(p, "<dark_red>Meurtre Parfait : " + name + " est cible.");
                    p.playSound(p.getLocation(), Sound.ENTITY_WOLF_GROWL, 1f, 0.8f);
                }));
            }
            ChoiceMenu.open(p, "<dark_red><bold>Meurtre Parfait : qui ?", options);
            return true;
        }
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) {
            String id = st.get("target", null);
            if (id == null) return;
            org.bukkit.entity.Entity e = Bukkit.getEntity(UUID.fromString(id));
            if (!(e instanceof LivingEntity t) || t.isDead()) { st.remove("target"); return; }
            Effects.give(t, PotionEffectType.GLOWING, 0, 40);
            Effects.give(t, PotionEffectType.WEAKNESS, 0, 40);
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            if (!unlocked(p, 4)) return "<dark_gray>Meurtre Parfait [" + kills(p) + "/4]";
            String id = st.get("target", null);
            org.bukkit.entity.Entity e = id == null ? null : Bukkit.getEntity(UUID.fromString(id));
            return e == null ? "<gray>Meurtre <dark_gray>[aucune cible]" : "<dark_red>Meurtre <white>[" + (e instanceof Player tp ? tp.getName() : "mannequin") + "]";
        }
    }

    static final class ReineDuCrime extends PassiveAbility {
        ReineDuCrime() {
            super("kura_reine", "Reine du Crime", "Debloquee a 6 kills : Speed permanent. Quand aucun adversaire n'est a moins de 10 blocs, Speed II et les coordonnees d'un adversaire au hasard.", Material.GOLDEN_HELMET);
        }
        @Override public boolean showInBar(Player p, CfPlayer cf, AbilityState st) { return unlocked(p, 6); }
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) {
            refreshKnife(p);
            if (!unlocked(p, 6)) { if (Boosts.get(p, "kura_reine") != null) Boosts.remove(p, "kura_reine"); return; }
            boolean alone = Effects.nearestEnemy(p, 10) == null;
            Boosts.Boost b = Boosts.get(p, "kura_reine");
            double want = alone ? 2 : 1;
            if (b == null || b.speed != want) Boosts.set(p, "kura_reine", new Boosts.Boost(0, want, 0, 0));
            int t = st.get("t", 0) + 1; st.set("t", t);
            if (alone && t % 10 == 0) {
                List<Player> enemies = new ArrayList<>();
                for (Player o : Bukkit.getOnlinePlayers()) if (Effects.isEnemy(p, o)) enemies.add(o);
                if (!enemies.isEmpty()) {
                    Player e = enemies.get((int) (Math.random() * enemies.size()));
                    Location l = e.getLocation();
                    Msg.send(p, "<gray>Reine du Crime : <white>" + e.getName() + "</white> est en <white>" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + "</white>.");
                }
            }
        }
        @Override public void onUnequip(Player p, CfPlayer cf) { Boosts.remove(p, "kura_reine"); Boosts.remove(p, "kura_prime"); }
    }

    static final class RecompenseMeritee extends PassiveAbility {
        RecompenseMeritee() {
            super("kura_recompense", "Recompense meritee", "Debloquee a 8 kills : Kura recoit le nom d'un adversaire ; si elle le tue, elle gagne Force (Force IV max) et une nouvelle cible.", Material.GOLD_INGOT);
        }
        @Override public boolean showInBar(Player p, CfPlayer cf, AbilityState st) { return unlocked(p, 8); }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            String b = st.get("bounty", null);
            return b == null ? "<gray>Prime <dark_gray>[aucune]" : "<gold>Prime <white>[" + b + "]";
        }
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) {
            if (!unlocked(p, 8) || st.get("bounty", null) != null) return;
            List<Player> enemies = new ArrayList<>();
            for (Player o : Bukkit.getOnlinePlayers()) if (Effects.isEnemy(p, o)) enemies.add(o);
            if (enemies.isEmpty()) return;
            Player target = enemies.get((int) (Math.random() * enemies.size()));
            st.set("bounty", target.getName());
            Msg.send(p, "<gold>Recompense meritee : ta cible est <white>" + target.getName() + "</white>.");
        }
        @Override public void onKill(Player killer, Player victim, AbilityState st) {
            if (!victim.getName().equals(st.get("bounty", null))) return;
            st.remove("bounty");
            int force = Math.min(4, st.get("force", 0) + 1);
            st.set("force", force);
            Boosts.set(killer, "kura_prime", new Boosts.Boost(force, 0, 0, 0));
            Msg.send(killer, "<gold>Recompense meritee : cible abattue, Force " + force + " !");
            killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
        }
    }

    static final class PluieDeCouteaux extends UltimateAbility {
        PluieDeCouteaux() {
            super("kura_ult", "Pluie de Couteaux", "L'adversaire le plus proche (10 blocs) perd autant de coeurs que Kura a de kills (8 coeurs max, 1 minimum).", Material.IRON_SWORD);
        }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            LivingEntity target = Effects.nearestEnemy(p, 10);
            if (target == null) { Msg.actionBar(p, "<red>Aucun adversaire a moins de 10 blocs."); return false; }
            int hearts = Math.max(1, Math.min(8, kills(p)));
            World w = target.getWorld();
            List<ItemDisplay> knives = new ArrayList<>();
            for (int i = 0; i < 12; i++) {
                Location at = target.getLocation().add((Math.random() - 0.5) * 2.5, 6 + Math.random() * 3, (Math.random() - 0.5) * 2.5);
                knives.add(w.spawn(at, ItemDisplay.class, d -> {
                    d.setItemStack(new ItemStack(Material.IRON_SWORD));
                    d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationX((float) (Math.PI / 2 + (Math.random() - 0.5) * 0.6)).rotateY((float) (Math.random() * 6.28)), new Vector3f(0.8f, 0.8f, 0.8f), new Quaternionf()));
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setPersistent(false);
                }));
            }
            w.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    for (ItemDisplay k : knives) k.teleport(k.getLocation().subtract(0, 0.9, 0));
                    if (t >= 8) {
                        for (ItemDisplay k : knives) k.remove();
                        if (!target.isDead()) {
                            target.setNoDamageTicks(0);
                            Effects.damage(target, hearts * 2.0, p);
                            w.spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), 60, 0.6, 0.8, 0.6, 0.3);
                            w.spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 6, 0.6, 0.6, 0.6, 0);
                            w.playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.7f);
                            w.playSound(target.getLocation(), Sound.ITEM_TRIDENT_HIT, 1f, 0.8f);
                        }
                        Msg.actionBar(p, "<dark_red>Pluie de Couteaux : " + hearts + " coeur(s) !");
                        cancel();
                    }
                }
            }.runTaskTimer(EarthDelta.plugin(), 0L, 1L);
            return true;
        }
    }
}
