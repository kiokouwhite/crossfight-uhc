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
import com.kiokouwhite.crossfight.util.Fx;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Puppet;
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
            if (ally == null && com.kiokouwhite.crossfight.util.MannequinPlayer.isProxy(p)) {
                // Mode d'essai a la troisieme personne, sans allie : le geste seul, l'orbe part dans le vide.
                healLookVoid(p);
                return true;
            }
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
            healLook(p, ally);
            return true;
        }

        /**
         * Comme dans Valorant : elle tend l'orbe vert vers son allie, a bout de bras ; l'orbe file jusqu'a lui et
         * une lueur verte tourne autour de lui tant que dure le soin.
         */
        /** Le meme geste sans personne a soigner : elle tend l'orbe a bout de bras, il file droit devant elle et s'eteint. */
        static void healLookVoid(Player p) {
            World w = p.getWorld();
            Puppet pu = Puppet.show(p, "sage", null, "debout");
            if (pu != null) {
                pu.face(p.getLocation().getYaw());
                pu.pose("sage_soin", 3);
                pu.endAfter(16, 4);
            }
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.01) dir = new Vector(0, 0, 1);
            final Vector ahead = dir.normalize().multiply(5);
            ItemDisplay orb = orb(palm(p, pu), HEAL, 0.38f);
            w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || t > 14) {
                        w.spawnParticle(Particle.DUST, orb.getLocation(), 14, 0.2, 0.2, 0.2, 0, HEAL_DUST);
                        orb.remove();
                        cancel();
                        return;
                    }
                    Location from = palm(p, pu);
                    if (t <= 6) { orb.teleport(from); return; }
                    orb.teleport(from.add(ahead.clone().multiply((t - 6) / 8.0)));
                    w.spawnParticle(Particle.DUST, orb.getLocation(), 2, 0.08, 0.08, 0.08, 0, HEAL_DUST);
                }
            }.runTaskTimer(plugin(), 1L, 1L);
        }

        static void healLook(Player p, LivingEntity ally) {
            World w = p.getWorld();
            Puppet pu = Puppet.show(p, "sage", null, "debout");
            if (pu != null) {
                pu.face(p.getLocation().setDirection(ally.getLocation().toVector().subtract(p.getLocation().toVector())).getYaw());
                pu.pose("sage_soin", 3);
                pu.endAfter(16, 4);
            }
            ItemDisplay orb = orb(palm(p, pu), HEAL, 0.38f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || !ally.isValid() || ally.isDead() || t > 66) { orb.remove(); cancel(); return; }
                    Location chest = ally.getLocation().add(0, 1.0, 0);
                    if (t <= 6) {
                        orb.teleport(palm(p, pu));
                    } else if (t <= 12) {
                        // L'orbe quitte sa main et rejoint son allie.
                        Location from = palm(p, pu);
                        orb.teleport(from.add(chest.toVector().subtract(from.toVector()).multiply((t - 6) / 6.0)));
                        w.spawnParticle(Particle.DUST, orb.getLocation(), 2, 0.08, 0.08, 0.08, 0, HEAL_DUST);
                    } else {
                        if (t == 13) {
                            orb.remove();
                            w.spawnParticle(Particle.HAPPY_VILLAGER, chest, 14, 0.4, 0.6, 0.4);
                            w.playSound(chest, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.3f);
                        }
                        // La lueur qui tourne autour de lui, des pieds a la tete.
                        if (t % 2 == 0) {
                            double a = t * 0.35, h = ((t - 13) % 26) / 26.0 * 1.9;
                            for (int k = 0; k < 2; k++) {
                                w.spawnParticle(Particle.DUST, ally.getLocation().add(Math.cos(a + k * Math.PI) * 0.6, 0.1 + h, Math.sin(a + k * Math.PI) * 0.6), 1, 0, 0, 0, 0, HEAL_DUST);
                            }
                        }
                    }
                }
            }.runTaskTimer(plugin(), 1L, 1L);
        }
    }

    // ------------------------------------------------------------ les orbes

    static final Color JADE = Color.fromRGB(110, 240, 225), HEAL = Color.fromRGB(120, 255, 160);
    static final Particle.DustOptions HEAL_DUST = new Particle.DustOptions(HEAL, 1.0f);

    /** Un orbe lumineux : l'image du pack teinte (tools/build_glyphs.py), ou un coeur de la mer a sa place. */
    static ItemDisplay orb(Location at, Color color, float size) {
        Location l = at.clone();
        l.setYaw(0);
        l.setPitch(0);
        return Fx.show(l, Fx.imageOr("orbe", color, Material.HEART_OF_THE_SEA), size, true);
    }

    /** La paume ou repose l'orbe : la main droite du pantin, ou devant le joueur a hauteur de poitrine. */
    static Location palm(Player p, Puppet pu) {
        if (pu != null && Puppet.of(p) == pu) return pu.hand().add(0, 0.16, 0);
        Location eye = p.getEyeLocation();
        return eye.clone().add(eye.getDirection().setY(0).normalize().multiply(0.7)).add(0, -0.45, 0);
    }

    static final class OrbeDeLenteur extends ActiveAbility {
        OrbeDeLenteur() {
            super("sage_lenteur", "Orbe de Lenteur", "Sage tire un orbe au sol qui eclate en un tapis de cristaux : pendant 5 secondes, une zone de 10 blocs de large donne Lenteur II aux ennemis.", Material.SNOWBALL, 10);
        }
        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            // Comme dans Valorant : elle arme le bras par en dessous et lance l'orbe, qui part de sa main au milieu du geste.
            Puppet pu = Puppet.show(p, "sage", null, "sage_lance_arme");
            if (pu == null) { release(p); return true; }
            pu.pose("sage_lance", 3);
            pu.endAfter(10, 3);
            ItemDisplay held = orb(palm(p, pu), JADE, 0.36f);
            Fx.removeLater(held, 4);
            Bukkit.getScheduler().runTaskLater(plugin(), () -> { if (p.isOnline() && !p.isDead()) release(p); }, 4L);
            return true;
        }

        static void release(Player p) {
            Vector dir = p.getEyeLocation().getDirection();
            Color trail = Color.fromRGB(100, 230, 220);
            Arrow a = Fx.has("orbe") ? Bullets.shoot(p, dir, 1.8, 0.5, Fx.image("orbe", JADE), 0.5f, true, trail)
                    : Bullets.shoot(p, dir, 1.8, 0.5, Material.CYAN_CONCRETE, trail);
            a.getPersistentDataContainer().set(SLOW_ORB, PersistentDataType.BYTE, (byte) 1);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 0.8f);
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
            World w = p.getWorld();
            // Comme dans Valorant : l'orbe repose dans sa paume, elle l'abat vers le sol devant elle, et le mur se leve
            // rangee par rangee dans un craquement de cristal.
            Puppet pu = Puppet.show(p, "sage", null, "sage_orbe");
            if (pu != null) pu.endAfter(RISE_AT + 10, 4);
            ItemDisplay orb = orb(palm(p, pu), JADE, 0.4f);
            Location foot = base.clone().add(fwd.clone().multiply(1)).add(0, 0.3, 0);
            w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.9f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (t < RISE_AT - 3) {
                        orb.teleport(palm(p, pu));
                    } else if (t < RISE_AT) {
                        if (t == RISE_AT - 3 && pu != null && Puppet.of(p) == pu) pu.pose("sage_barriere", 3);
                        // L'orbe tombe au pied du mur.
                        Location from = orb.getLocation();
                        orb.teleport(from.add(foot.toVector().subtract(from.toVector()).multiply(1.0 / (RISE_AT - t))));
                    } else {
                        int layer = (t - RISE_AT) / 2;
                        if (t == RISE_AT) {
                            orb.remove();
                            w.spawnParticle(Particle.FLASH, foot, 1, 0, 0, 0, 0, Color.fromRGB(170, 255, 235));
                            w.playSound(foot, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.5f);
                        }
                        if ((t - RISE_AT) % 2 == 0 && layer < 4) {
                            for (int i = -4; i <= 3; i++) for (int d = 0; d < 3; d++) {
                                Location at = base.clone().add(side.clone().multiply(i)).add(fwd.clone().multiply(d)).add(0, layer, 0);
                                wall.place(at.getBlock(), Material.EMERALD_BLOCK);
                                if (d != 1) w.spawnParticle(Particle.DUST, at.getBlock().getLocation().add(0.5, 1.05, 0.5), 1, 0.3, 0.05, 0.3, 0, ICE);
                            }
                            w.playSound(base, Sound.BLOCK_AMETHYST_BLOCK_PLACE, 1f, 0.7f + layer * 0.15f);
                            w.playSound(base, Sound.BLOCK_AMETHYST_CLUSTER_PLACE, 0.8f, 0.6f + layer * 0.1f);
                        }
                        if (layer >= 4) {
                            wall.restoreAfter(600L);
                            Msg.actionBar(p, "<green>Orbe Barriere : " + wall.size() + " blocs");
                            cancel();
                        }
                    }
                }
            }.runTaskTimer(plugin(), 1L, 1L);
            return true;
        }
        /** Le mur commence a se lever ce nombre de ticks apres le lancer (le temps du geste). */
        static final int RISE_AT = 8;
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
                Player o = com.kiokouwhite.crossfight.util.MannequinPlayer.getPlayer(u);
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
            resurrectionLook(sage, target.getLocation());
        }
    }

    static final Material[] COCOON = {Material.CYAN_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS, Material.EMERALD_BLOCK, Material.WHITE_STAINED_GLASS};

    /**
     * La Resurrection, comme dans Valorant : Sage, penchee, tend les deux mains vers son allie ; un cocon de
     * cristal de jade se leve autour de lui dans une colonne de lumiere, puis vole en eclats quand il se releve.
     * Appelee aussi par /cf effet resurrection (apercu en mode test).
     */
    public static void resurrectionLook(Player sage, Location at) {
        World w = at.getWorld();
        Puppet pu = Puppet.show(sage, "sage", null, "debout");
        if (pu != null) {
            pu.face(sage.getLocation().setDirection(at.toVector().subtract(sage.getLocation().toVector())).getYaw());
            pu.pose("sage_resurrection", 4);
            pu.endAfter(34, 5);
        }
        List<org.bukkit.entity.BlockDisplay> shards = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            double a = i * Math.PI * 2 / 7;
            Location l = at.clone().add(Math.cos(a) * 0.75, 0, Math.sin(a) * 0.75);
            l.setYaw((float) Math.toDegrees(a));
            l.setPitch(0);
            final int k = i;
            shards.add(w.spawn(l, org.bukkit.entity.BlockDisplay.class, d -> {
                d.setBlock(COCOON[k % COCOON.length].createBlockData());
                d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                d.setPersistent(false);
                d.setTransformation(shard(0.05f, k));
            }));
        }
        w.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.6f);
        w.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.6f);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                if (t == 2) for (int i = 0; i < shards.size(); i++) {
                    // Les cristaux poussent du sol et se referment au-dessus de lui.
                    var d = shards.get(i);
                    d.setInterpolationDelay(0);
                    d.setInterpolationDuration(10);
                    d.setTransformation(shard(1f, i));
                }
                if (t <= 26) {
                    // La colonne de lumiere, et la lueur qui passe des mains de Sage a son allie.
                    w.spawnParticle(Particle.END_ROD, at.clone().add(0, 0.2 + (t % 13) * 0.22, 0), 2, 0.12, 0.1, 0.12, 0.01);
                    w.spawnParticle(Particle.DUST, at.clone().add(0, 1.0, 0), 3, 0.35, 0.7, 0.35, 0, ICE);
                    if (pu != null && Puppet.of(sage) == pu && t % 2 == 0) {
                        Fx.line(pu.armPoint(t % 4 == 0, 1.0), at.clone().add(0, 1.0, 0), 0.35, HEAL_DUST);
                    }
                    if (t % 6 == 0) w.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 0.8f + t * 0.03f);
                }
                if (t == 27) {
                    // Le cocon eclate : il est revenu.
                    for (var d : shards) {
                        w.spawnParticle(Particle.BLOCK, d.getLocation().add(0, 1.0, 0), 8, 0.2, 0.6, 0.2, d.getBlock());
                        d.remove();
                    }
                    w.spawnParticle(Particle.FLASH, at.clone().add(0, 1.0, 0), 1, 0, 0, 0, 0, Color.fromRGB(190, 255, 240));
                    w.spawnParticle(Particle.TOTEM_OF_UNDYING, at.clone().add(0, 1.0, 0), 40, 0.3, 0.6, 0.3, 0.35);
                    w.playSound(at, Sound.BLOCK_GLASS_BREAK, 1f, 1.3f);
                    w.playSound(at, Sound.ITEM_TOTEM_USE, 0.6f, 1.4f);
                    cancel();
                }
            }
        }.runTaskTimer(plugin(), 1L, 1L);
    }

    /** Un eclat du cocon : une lame de cristal haute et fine, penchee vers l'interieur (grow : de 0 a 1). */
    private static org.bukkit.util.Transformation shard(float grow, int i) {
        float w = 0.22f + (i % 3) * 0.05f, h = (2.0f + (i % 2) * 0.35f) * grow;
        var lean = new org.joml.Quaternionf().rotationZ((float) Math.toRadians(16 + (i % 3) * 5));
        return new org.bukkit.util.Transformation(new org.joml.Vector3f(-w / 2, 0, -w / 2), lean, new org.joml.Vector3f(w, Math.max(0.05f, h), w), new org.joml.Quaternionf());
    }
}
