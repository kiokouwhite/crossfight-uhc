package com.kiokouwhite.crossfight.character.valorant;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.util.Bullets;
import com.kiokouwhite.crossfight.util.ChoiceMenu;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Revived;
import com.kiokouwhite.crossfight.util.TempBlocks;
import org.bukkit.Bukkit;
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
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Sage - Path of Harmony.
 */
public final class Sage extends Character implements Listener {

    static final NamespacedKey SLOW_ORB = new NamespacedKey("crossfight", "sage_slow_orb");

    public Sage() {
        super("sage", "Sage", Voie.HARMONIE, Material.EMERALD);
        addAbility(new OrbeDeSoin());
        addAbility(new OrbeDeLenteur());
        addAbility(new OrbeBarriere());
        addAbility(Valorant.SHOP);
        addAbility(Guns.ABILITY);
        setUltimate(new Resurrection());
    }

    private static com.kiokouwhite.crossfight.CrossfightPlugin plugin() { return com.kiokouwhite.crossfight.CrossfightPlugin.get(); }

    @Override
    public ItemStack createWeapon() { return Valorant.meleeWeapon(0); }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Orbe de Soin : 3 coeurs a un allie en 3 s",
                "<white>Orbe de Lenteur : zone de Lenteur II 5 s",
                "<white>Orbe Barriere : mur d'emeraude 8x4x3",
                "<light_purple>Ultime : ressuscite un allie");
    }

    /** L'allie vise (a moins de 15 blocs), sinon le plus proche. */
    static LivingEntity targetAlly(Player p) {
        var hit = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), 15, 1.0, e -> e instanceof LivingEntity le && Effects.isAlly(p, le));
        if (hit != null && hit.getHitEntity() instanceof LivingEntity le) return le;
        return Effects.nearestAlly(p, 15);
    }

    static final class OrbeDeSoin extends ActiveAbility {
        OrbeDeSoin() {
            super("sage_soin", "Orbe de Soin", "Sage soigne de 3 coeurs l'allie vise (ou le plus proche), en 3 secondes.", Material.GLOW_INK_SAC, 20);
        }
        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            LivingEntity ally = targetAlly(p);
            if (ally == null) { Msg.actionBar(p, "<red>Orbe de Soin : aucun allie a moins de 15 blocs."); return false; }
            new BukkitRunnable() {
                int n = 0;
                @Override public void run() {
                    if (n++ >= 6 || !ally.isValid() || ally.isDead()) { cancel(); return; }
                    Effects.heal(ally, 1.0);
                    ally.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, ally.getLocation().add(0, 1, 0), 6, 0.4, 0.5, 0.4);
                }
            }.runTaskTimer(plugin(), 0L, 10L);
            p.getWorld().playSound(ally.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
            Msg.actionBar(p, "<green>Orbe de Soin sur " + (ally instanceof Player ap ? ap.getName() : "l'allie"));
            return true;
        }
    }

    static final class OrbeDeLenteur extends ActiveAbility {
        OrbeDeLenteur() {
            super("sage_lenteur", "Orbe de Lenteur", "Sage tire un orbe au sol qui eclate en un tapis de cristaux : pendant 5 secondes, une zone de 10 blocs de large donne Lenteur II aux ennemis.", Material.SNOWBALL, 10);
        }
        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Arrow a = Bullets.shoot(p, p.getEyeLocation().getDirection(), 1.8, 0.5, Material.CYAN_CONCRETE, Color.fromRGB(100, 230, 220));
            a.getPersistentDataContainer().set(SLOW_ORB, PersistentDataType.BYTE, (byte) 1);
            return true;
        }
    }

    static final Particle.DustOptions ICE = new Particle.DustOptions(Color.fromRGB(110, 240, 225), 1.3f);
    static final Material[] CRYSTAL_FLOOR = {Material.CYAN_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS, Material.DIAMOND_BLOCK, Material.CYAN_STAINED_GLASS, Material.PRISMARINE_BRICKS};
    static final Material[] CRYSTAL_SHARD = {Material.CYAN_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS, Material.WHITE_STAINED_GLASS};
    static final double SLOW_RADIUS = 5.0;

    @EventHandler
    public void onSlowOrbLand(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow a) || !a.getPersistentDataContainer().has(SLOW_ORB)) return;
        if (!(a.getShooter() instanceof Player p)) return;
        Location c = a.getLocation();
        World w = c.getWorld();
        int gy = Effects.groundAround(w, c.getBlockX(), c.getBlockZ(), c.getBlockY());
        if (gy == Integer.MIN_VALUE) gy = c.getBlockY() - 1;
        final Location center = new Location(w, c.getBlockX() + 0.5, gy + 1, c.getBlockZ() + 0.5);
        // Eclatement de l'orbe.
        w.spawnParticle(Particle.FLASH, center.clone().add(0, 0.5, 0), 1, 0, 0, 0, 0, Color.fromRGB(150, 255, 240));
        w.spawnParticle(Particle.DUST, center.clone().add(0, 0.5, 0), 60, 1.0, 0.5, 1.0, 0, ICE);
        w.playSound(center, Sound.BLOCK_GLASS_BREAK, 1f, 1.4f);
        w.playSound(center, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 0.8f);

        TempBlocks floor = new TempBlocks();
        List<org.bukkit.entity.BlockDisplay> shards = new ArrayList<>();
        new BukkitRunnable() {
            int tick = 0;
            @Override public void run() {
                tick++;
                if (tick > 100) { floor.restore(); for (var d : shards) d.remove(); shards.clear(); cancel(); return; }
                // Le tapis de cristal s'etend du centre vers le bord pendant les 5 premiers ticks.
                if (tick <= 5) {
                    double rNow = SLOW_RADIUS * tick / 5.0, rPrev = SLOW_RADIUS * (tick - 1) / 5.0;
                    for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
                        double d = Math.sqrt(x * x + z * z);
                        if (d > rNow || d <= rPrev) continue;
                        int y = Effects.groundAround(w, center.getBlockX() + x, center.getBlockZ() + z, center.getBlockY());
                        if (y == Integer.MIN_VALUE) continue;
                        org.bukkit.block.Block top = w.getBlockAt(center.getBlockX() + x, y, center.getBlockZ() + z);
                        if (top.getType().isSolid()) floor.replace(top, CRYSTAL_FLOOR[(int) (Math.random() * CRYSTAL_FLOOR.length)]);
                        // Eclats de cristal plantes de travers dans le sol.
                        if (Math.random() < 0.4) {
                            Location at = new Location(w, center.getBlockX() + x + Math.random(), y + 1, center.getBlockZ() + z + Math.random());
                            float size = 0.3f + (float) Math.random() * 0.5f;
                            shards.add(w.spawn(at, org.bukkit.entity.BlockDisplay.class, disp -> {
                                disp.setBlock(CRYSTAL_SHARD[(int) (Math.random() * CRYSTAL_SHARD.length)].createBlockData());
                                var rot = new org.joml.Quaternionf().rotationXYZ((float) (Math.random() * 0.8 - 0.4), (float) (Math.random() * 6.28), (float) (Math.random() * 0.8 - 0.4));
                                disp.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(-size / 2, -0.15f, -size / 2), rot,
                                        new org.joml.Vector3f(size, size * 1.8f, size), new org.joml.Quaternionf()));
                                disp.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                                disp.setPersistent(false);
                            }));
                        }
                    }
                    w.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f + tick * 0.1f);
                }
                // Scintillement et effet de lenteur chaque seconde.
                if (tick % 4 == 0) {
                    double ang = Math.random() * Math.PI * 2, r = Math.random() * SLOW_RADIUS;
                    w.spawnParticle(Particle.DUST, center.clone().add(Math.cos(ang) * r, 0.3 + Math.random(), Math.sin(ang) * r), 2, 0.2, 0.2, 0.2, 0, ICE);
                    w.spawnParticle(Particle.SNOWFLAKE, center.clone().add(0, 0.5, 0), 2, SLOW_RADIUS * 0.6, 0.3, SLOW_RADIUS * 0.6, 0.01);
                }
                if (tick % 20 == 0) {
                    for (LivingEntity enemy : Effects.enemiesWithin(p, center, SLOW_RADIUS)) Effects.give(enemy, PotionEffectType.SLOWNESS, 1, 30);
                }
            }
        }.runTaskTimer(plugin(), 0L, 1L);
    }

    static final class OrbeBarriere extends ActiveAbility {
        OrbeBarriere() {
            super("sage_barriere", "Orbe Barriere", "Un bloc devant Sage, un mur en blocs d'emeraude de 8 de long, 4 de haut et 3 de profondeur, pendant 30 secondes.", Material.EMERALD_BLOCK, 15);
        }
        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Vector fwd = Effects.towards(p, null);
            Vector side = new Vector(-fwd.getZ(), 0, fwd.getX());
            Location base = p.getLocation().add(fwd.clone().multiply(2));
            TempBlocks wall = new TempBlocks();
            for (int i = -4; i <= 3; i++) for (int d = 0; d < 3; d++) for (int y = 0; y < 4; y++) {
                wall.place(base.clone().add(side.clone().multiply(i)).add(fwd.clone().multiply(d)).add(0, y, 0).getBlock(), Material.EMERALD_BLOCK);
            }
            wall.restoreAfter(600L);
            p.getWorld().playSound(base, Sound.BLOCK_AMETHYST_BLOCK_PLACE, 1f, 0.8f);
            Msg.actionBar(p, "<green>Orbe Barriere : " + wall.size() + " blocs");
            return true;
        }
    }

    static final class Resurrection extends UltimateAbility {
        Resurrection() {
            super("sage_ult", "Resurrection", "Sage ressuscite l'allie de son choix : il revient avec une vie, Faiblesse permanente et sans pouvoir porter de plastron.", Material.TOTEM_OF_UNDYING);
        }
        @Override
        public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            Team team = cf.team();
            if (team == null) return false;
            List<ChoiceMenu.Option> options = new ArrayList<>();
            for (var u : team.members()) {
                Player o = Bukkit.getPlayer(u);
                if (o == null || o == p) continue;
                CfPlayer ocf = plugin().players().get(o);
                if (ocf.isAlive() && !ocf.isWaitingRespawn()) continue;
                if (Revived.is(o)) continue;
                options.add(new ChoiceMenu.Option(Material.PLAYER_HEAD, o.getName(), List.of("<gray>" + (ocf.character() == null ? "?" : ocf.character().name()),
                        ocf.isWaitingRespawn() ? "<yellow>En attente de reapparition" : "<red>Definitivement elimine"), () -> revive(p, cf, o)));
            }
            if (options.isEmpty()) { Msg.actionBar(p, "<red>Resurrection : aucun allie mort a ramener."); return false; }
            ChoiceMenu.open(p, "<dark_gray>Resurrection : qui ramener ?", options, pl -> cf.setUltimateCharge(100));
            return true;
        }

        private void revive(Player sage, CfPlayer scf, Player target) {
            if (!target.isOnline()) { scf.setUltimateCharge(100); return; }
            Revived.revive(sage, target, scf.team());
            Msg.broadcast("<light_purple>" + sage.getName() + " ressuscite <yellow>" + target.getName() + "</yellow> !");
        }
    }
}
