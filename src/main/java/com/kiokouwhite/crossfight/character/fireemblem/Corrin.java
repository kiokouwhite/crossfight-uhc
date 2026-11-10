package com.kiokouwhite.crossfight.character.fireemblem;

import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.util.ChoiceMenu;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Corrin - Path of Destruction.
 */
public final class Corrin extends Character {

    public Corrin() {
        super("corrin", "Corrin", Voie.DESTRUCTION, Material.GOLDEN_SWORD);
        addAbility(new Yato());
        addAbility(new DragonGardien());
        addAbility(new SangDraconique());
        addAbility(new DragonDeLEau());
        setUltimate(new VaguesBleues());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.GOLDEN_SWORD, 8.5, "<gold><bold>Yato",
                Items.wrap("Evolue selon la voie choisie et les eliminations."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Yato (8.5) : evolue avec les kills",
                "<white>Dragon Gardien : Resistance pres d'un allie",
                "<white>Sang Draconique : vol 5 s, eau a l'atterrissage",
                "<white>Dragon de l'eau : Agilite aquatique IV",
                "<light_purple>Ultime : vague qui ejecte a 15 blocs");
    }

    /** Remplace l'arme de Corrin dans son inventaire. */
    static void setYato(Player p, double damage, String name, String desc) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if ("corrin".equals(Items.tag(it, AbilityEngine.WEAPON_KEY))) {
                ItemStack sword = Weapons.melee(Material.GOLDEN_SWORD, damage, name, Items.wrap(desc));
                Items.tag(sword, AbilityEngine.WEAPON_KEY, "corrin");
                inv.setItem(i, sword);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ arme

    /**
     * Yato : au debut de la partie Corrin choisit la Lumiere ou les Tenebres.
     * Lumiere : 2 kills d'equipe -> Yato Eclatante (8.5, Vitesse + Resistance) ;
     *           5 kills d'equipe -> Yato Alpha (9, Vitesse II + Resistance + 15 coeurs).
     * Tenebres : 1 kill de Corrin -> Yato de l'Ombre (9.5) ; 5 kills -> Yato Omega (10.5).
     */
    static final class Yato extends PassiveAbility {
        Yato() {
            super("corrin_yato", "Yato", "Choisis ta voie en debut de partie ; la Yato evolue avec les eliminations.", Material.BOOK);
        }

        @Override
        public void onEquip(Player p, CfPlayer cf) {
            AbilityState st = plugin().abilities().state(p, this);
            if (st.get("path", null) == null) {
                Bukkit.getScheduler().runTaskLater(plugin(), () -> openChoice(p, st), 40L);
            } else {
                refresh(p, cf, st);
            }
        }

        private void openChoice(Player p, AbilityState st) {
            if (!p.isOnline() || st.get("path", null) != null) return;
            ChoiceMenu.open(p, "<dark_gray>Yato : choisis ta voie", List.of(
                    new ChoiceMenu.Option(Material.GLOWSTONE_DUST, "Voie de la Lumiere", List.of(
                            "<white>2 kills d'equipe : Yato Eclatante (8.5)",
                            "<gray>  Vitesse et Resistance",
                            "<white>5 kills d'equipe : Yato Alpha (9)",
                            "<gray>  Vitesse II, Resistance et 15 coeurs"),
                            () -> choose(p, st, "light")),
                    new ChoiceMenu.Option(Material.COAL, "Voie des Tenebres", List.of(
                            "<white>1 kill de Corrin : Yato de l'Ombre (9.5)",
                            "<white>5 kills de Corrin : Yato Omega (10.5)"),
                            () -> choose(p, st, "dark"))
            ), pl -> Bukkit.getScheduler().runTaskLater(plugin(), () -> openChoice(pl, st), 40L));
        }

        private void choose(Player p, AbilityState st, String path) {
            st.set("path", path);
            Msg.send(p, "<gold>Yato : <white>" + ("light".equals(path) ? "Voie de la Lumiere" : "Voie des Tenebres") + "</white> choisie.");
            refresh(p, plugin().players().get(p), st);
        }

        @Override
        public void onKill(Player killer, Player victim, AbilityState st) {
            refresh(killer, plugin().players().get(killer), st);
        }

        @Override
        public void tick(Player p, CfPlayer cf, AbilityState st) {
            String path = st.get("path", null);
            if (path == null) return;
            int stage = stage(cf, path);
            // Les kills d'equipe peuvent evoluer sans que Corrin tue : on verifie chaque seconde.
            if (stage != (int) st.get("stage", 0)) refresh(p, cf, st);
            if ("light".equals(path)) {
                if (stage >= 1) {
                    Effects.refresh(p, PotionEffectType.SPEED, stage >= 2 ? 1 : 0, 45);
                    Effects.refresh(p, PotionEffectType.RESISTANCE, 0, 45);
                }
            }
        }

        private int stage(CfPlayer cf, String path) {
            if ("light".equals(path)) {
                int teamKills = 0;
                Team t = cf.team();
                if (t != null) for (UUID u : t.members()) {
                    CfPlayer m = plugin().players().get(u);
                    if (m != null) teamKills += m.kills();
                }
                return teamKills >= 5 ? 2 : teamKills >= 2 ? 1 : 0;
            }
            return cf.kills() >= 5 ? 2 : cf.kills() >= 1 ? 1 : 0;
        }

        private void refresh(Player p, CfPlayer cf, AbilityState st) {
            String path = st.get("path", null);
            if (path == null) return;
            int stage = stage(cf, path);
            int old = st.get("stage", 0);
            st.set("stage", stage);
            if ("light".equals(path)) {
                switch (stage) {
                    case 0 -> setYato(p, 8.5, "<gold><bold>Yato", "Voie de la Lumiere. 2 kills d'equipe pour evoluer.");
                    case 1 -> setYato(p, 8.5, "<yellow><bold>Yato Eclatante", "Vitesse et Resistance. 5 kills d'equipe pour evoluer.");
                    default -> {
                        setYato(p, 9, "<white><bold>Yato Alpha", "Vitesse II, Resistance et 15 coeurs.");
                        var mh = p.getAttribute(Attribute.MAX_HEALTH);
                        if (mh != null && mh.getBaseValue() < 30) mh.setBaseValue(30.0);
                    }
                }
            } else {
                switch (stage) {
                    case 0 -> setYato(p, 8.5, "<gold><bold>Yato", "Voie des Tenebres. 1 kill pour evoluer.");
                    case 1 -> setYato(p, 9.5, "<dark_purple><bold>Yato de l'Ombre", "5 kills pour evoluer.");
                    default -> setYato(p, 10.5, "<dark_red><bold>Yato Omega", "La lame ultime des Tenebres.");
                }
            }
            if (stage > old) {
                Msg.send(p, "<gold><bold>Ta Yato evolue !");
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.5f);
            }
        }
    }

    // --------------------------------------------------------------- pouvoirs

    static final class DragonGardien extends PassiveAbility {
        DragonGardien() {
            super("corrin_gardien", "Dragon Gardien", "A moins de 10 blocs d'un allie, Corrin a Resistance.", Material.IRON_INGOT);
        }

        @Override
        public void tick(Player p, CfPlayer cf, AbilityState st) {
            if (!Effects.alliesWithin(p, 10).isEmpty()) Effects.refresh(p, PotionEffectType.RESISTANCE, 0, 45);
        }
    }

    static final class SangDraconique extends ActiveAbility {
        SangDraconique() {
            super("corrin_sang", "Sang Draconique", "Corrin vole pendant 5 secondes. Un carre d'eau de 4x4 apparait la ou elle atterrit.",
                    Material.FEATHER, 20);
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            p.setAllowFlight(true);
            p.setFlying(true);
            st.set("noFallUntil", System.currentTimeMillis() + 15000L);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.2f);
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                if (!p.isOnline()) return;
                p.setFlying(false);
                p.setAllowFlight(false);
                waitLanding(p, st);
            }, 100L);
            return true;
        }

        private void waitLanding(Player p, AbilityState st) {
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    ticks++;
                    if (!p.isOnline() || p.isDead() || ticks > 200) { cancel(); return; }
                    Location feet = p.getLocation();
                    boolean grounded = feet.clone().subtract(0, 0.1, 0).getBlock().getType().isSolid() || p.isInWater();
                    if (grounded) {
                        cancel();
                        st.set("noFallUntil", 0L);
                        spawnWater(p.getLocation());
                    }
                }
            }.runTaskTimer(plugin(), 1L, 1L);
        }

        private void spawnWater(Location loc) {
            List<Block> placed = new ArrayList<>();
            int bx = loc.getBlockX(), by = loc.getBlockY(), bz = loc.getBlockZ();
            for (int x = -1; x <= 2; x++) {
                for (int z = -1; z <= 2; z++) {
                    Block b = loc.getWorld().getBlockAt(bx + x, by, bz + z);
                    Block below = b.getRelative(0, -1, 0);
                    if ((b.getType().isAir() || b.isReplaceable()) && below.getType().isSolid()) {
                        b.setType(Material.WATER, false);
                        placed.add(b);
                    }
                }
            }
            loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_SPLASH, 1f, 1f);
            loc.getWorld().spawnParticle(Particle.SPLASH, loc.clone().add(0.5, 0.5, 0.5), 60, 1.5, 0.3, 1.5);
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                for (Block b : placed) if (b.getType() == Material.WATER) b.setType(Material.AIR, false);
            }, 20L * 15);
        }

        @Override
        public void onDamaged(Player victim, EntityDamageEvent e, AbilityState st) {
            if (e.getCause() == EntityDamageEvent.DamageCause.FALL && System.currentTimeMillis() < (long) st.get("noFallUntil", 0L)) {
                e.setCancelled(true);
            }
        }
    }

    static final class DragonDeLEau extends PassiveAbility {
        DragonDeLEau() {
            super("corrin_eau", "Dragon de l'eau", "Corrin a Agilite aquatique IV et respire sous l'eau.", Material.PRISMARINE_CRYSTALS);
        }

        @Override
        public void tick(Player p, CfPlayer cf, AbilityState st) {
            Effects.refresh(p, PotionEffectType.DOLPHINS_GRACE, 3, 45);
            Effects.refresh(p, PotionEffectType.WATER_BREATHING, 0, 45);
        }
    }

    // ----------------------------------------------------------------- ultime

    static final class VaguesBleues extends UltimateAbility {
        VaguesBleues() {
            super("corrin_ult", "Les Vagues Bleues de l'Ocean Gris",
                    "Une vague de 3 blocs de large avance sur 10 blocs (1 bloc/s). Les ennemis touches sont ejectes a 15 blocs de haut et ont Lenteur 10 secondes.",
                    Material.HEART_OF_THE_SEA);
        }

        @Override
        public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
            dir.normalize();
            Vector side = new Vector(-dir.getZ(), 0, dir.getX());
            Location origin = p.getLocation();
            List<UUID> hit = new ArrayList<>();
            final Vector fDir = dir;
            new BukkitRunnable() {
                int step = 0;
                @Override public void run() {
                    step++;
                    if (step > 10 || !p.isOnline()) { cancel(); return; }
                    Location front = origin.clone().add(fDir.clone().multiply(step));
                    front.setY(front.getWorld().getHighestBlockYAt(front) + 1);
                    for (double s = -1; s <= 1; s += 0.5) {
                        Location l = front.clone().add(side.clone().multiply(s));
                        l.getWorld().spawnParticle(Particle.SPLASH, l, 25, 0.3, 0.6, 0.3, 0);
                        l.getWorld().spawnParticle(Particle.BUBBLE_POP, l.clone().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0);
                    }
                    front.getWorld().playSound(front, Sound.ENTITY_GENERIC_SPLASH, 0.8f, 0.7f);
                    for (org.bukkit.entity.LivingEntity enemy : Effects.enemiesWithin(p, front, 2.2)) {
                        if (hit.contains(enemy.getUniqueId())) continue;
                        hit.add(enemy.getUniqueId());
                        Effects.launchUp(enemy, 15);
                        Effects.give(enemy, PotionEffectType.SLOWNESS, 0, 20 * 10);
                        if (enemy instanceof Player ep) Msg.actionBar(ep, "<aqua>Emporte par la vague de Corrin !");
                    }
                }
            }.runTaskTimer(plugin(), 0L, 20L);
            return true;
        }
    }
}
