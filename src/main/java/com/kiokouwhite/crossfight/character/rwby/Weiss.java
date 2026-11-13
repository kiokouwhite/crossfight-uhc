package com.kiokouwhite.crossfight.character.rwby;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.SwitchAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.ChoiceMenu;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Puppet;
import com.kiokouwhite.crossfight.util.TempBlocks;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Weiss Schnee - Path of Destruction.
 */
public final class Weiss extends Character implements Listener {

    static final String[] DUSTS = {"Rouge", "Bleu", "Jaune", "Cyan", "Blanche", "Violette"};
    static final String[] DUST_COLORS = {"red", "blue", "yellow", "aqua", "white", "dark_purple"};
    static final NamespacedKey FIRE_ARROW = new NamespacedKey("crossfight", "weiss_fire_arrow");

    public Weiss() {
        super("weiss", "Weiss Schnee", Voie.DESTRUCTION, Material.DIAMOND_SWORD);
        addAbility(new Myrtenaster().bindToWeapon());
        addAbility(new GlyphsType1());
        addAbility(new GlyphsType2());
        addAbility(new GlyphsPassif());
        addAbility(RWBY.DUO_ITEM);
        setUltimate(new Invocation());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.DIAMOND_SWORD, 8.5, "<aqua><bold>Myrtenaster", List.of("<gray>Dust : <red>Rouge"));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Myrtenaster (8.5) : 6 types de Dust",
                "<white>Glyphs (Type 1 et 2) : effets selon le Dust",
                "<white>Glyphs (passif) : bonus selon le Dust",
                "<light_purple>Ultime : invoque un ennemi elimine");
    }

    static int dust(Player p) {
        var ch = plugin().characters().character("weiss");
        return plugin().abilities().state(p, ch.ability("weiss_myrtenaster")).mode();
    }

    static String dustName(int mode) {
        return "<" + DUST_COLORS[mode] + ">" + DUSTS[mode];
    }

    /** Remplace le nom de l'epee pour afficher le Dust courant. */
    static void renameWeapon(Player p, int mode) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (!"weiss".equals(Items.tag(it, AbilityEngine.WEAPON_KEY))) continue;
            ItemStack sword = Weapons.melee(Material.DIAMOND_SWORD, 8.5, "<aqua><bold>Myrtenaster", List.of("<gray>Dust : " + dustName(mode)));
            AbilityEngine.decorateWeapon(sword, plugin().characters().character("weiss"));
            inv.setItem(i, sword);
            return;
        }
    }

    private static CrossfightPlugin plugin() { return CrossfightPlugin.get(); }

    /** Myrtenaster, telle que Weiss la porte (pour le pantin), ou une epee de diamant a defaut. */
    static ItemStack sword(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && "weiss".equals(Items.tag(it, AbilityEngine.WEAPON_KEY))) return it;
        }
        return new ItemStack(Material.DIAMOND_SWORD);
    }

    // ------------------------------------------------------------------ arme

    static final class Myrtenaster extends SwitchAbility {
        Myrtenaster() {
            super("weiss_myrtenaster", "Myrtenaster", "Clic droit : change le type de Dust (Rouge, Bleu, Jaune, Cyan, Blanche, Violette). Il modifie les trois Glyphs. Accroupi pour revenir en arriere.",
                    Material.DIAMOND_SWORD, List.of(DUSTS));
        }

        @Override
        public void onSwitch(Player p, CfPlayer cf, AbilityState st, int oldMode, int newMode) {
            renameWeapon(p, newMode);
            GlyphsPassif.applyBoosts(p, newMode);
            p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1.2, 0), 20, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(dustColor(newMode), 1.5f));
        }

        @Override
        public String barText(Player p, CfPlayer cf, AbilityState st) {
            return "<aqua>Dust " + dustName(st.mode());
        }
    }

    static Color dustColor(int mode) {
        return switch (mode) {
            case 0 -> Color.fromRGB(255, 40, 40);
            case 1 -> Color.fromRGB(60, 120, 255);
            case 2 -> Color.fromRGB(255, 230, 60);
            case 3 -> Color.fromRGB(80, 230, 255);
            case 4 -> Color.WHITE;
            default -> Color.fromRGB(170, 60, 220);
        };
    }

    // --------------------------------------------------------- animations et signes communs

    /** Le pantin de Weiss, son epee en main, et les etapes de l'animation tick par tick (0 = tout de suite). */
    static void play(Player p, int end, java.util.function.BiConsumer<Puppet, Integer> step) {
        Puppet shown = Puppet.show(p, "weiss", sword(p), "debout");
        if (shown != null) shown.endAfter(end, 4);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!p.isOnline() || p.isDead()) { cancel(); return; }
                // Une autre animation a pris la place : on garde les effets, plus les poses.
                step.accept(shown != null && Puppet.of(p) == shown ? shown : null, t);
                if (++t > end) cancel();
            }
        }.runTaskTimer(plugin(), 0L, 1L);
    }

    /** Ferme un signe au plus tard dans {@code ticks}, meme si l'animation qui le gerait s'est arretee avant. */
    static void closeLater(Glyph g, long ticks) {
        if (g != null) Bukkit.getScheduler().runTaskLater(plugin(), g::close, ticks);
    }

    /** Le grand signe des Schnee a plat au sol sous ce point, qui tourne. */
    static Glyph groundGlyph(Location at, int mode, float size, float spin) {
        World w = at.getWorld();
        Location l = at.clone();
        int y = Effects.groundAround(w, at.getBlockX(), at.getBlockZ(), at.getBlockY());
        if (y != Integer.MIN_VALUE) {
            var box = w.getBlockAt(at.getBlockX(), y, at.getBlockZ()).getBoundingBox();
            l.setY((box.getVolume() > 0 ? box.getMaxY() : y + 1) + 0.03);
        } else {
            l.setY(at.getY() + 0.03);
        }
        l.setYaw(0);
        l.setPitch(0);
        Glyph g = Glyph.spawn(l, Glyph.CERCLE, dustColor(mode), size, Glyph.FLAT, new Vector3f());
        if (g != null) g.spin(spin);
        return g;
    }

    /** Le signe cyan au milieu d'une barriere de verre posee a {@code base}, du cote de Weiss. */
    static Glyph wallGlyph(Location base, Vector fwd) {
        Location center = base.clone().add(0, 2, 0).getBlock().getLocation().add(0.5, 0.5, 0.5).subtract(fwd.clone().multiply(0.76));
        center.setDirection(fwd);
        return Glyph.spawn(center, Glyph.CERCLE, dustColor(3), 4.8f, Glyph.UPRIGHT, new Vector3f());
    }

    /** Les pouvoirs en attente (particules sur la lame et signe dans le dos) : un seul par pouvoir et par joueur. */
    static final java.util.Map<String, BukkitRunnable> AURAS = new java.util.HashMap<>();

    /**
     * Tant que {@code active} est vrai : des particules sur la lame ({@code spawn}) et le flocon des Schnee
     * dans son dos, a la couleur du Dust {@code mode}.
     */
    static void aura(Player p, String kind, int mode, java.util.function.BooleanSupplier active, java.util.function.BiConsumer<Location, Integer> spawn) {
        String key = p.getUniqueId() + ":" + kind;
        BukkitRunnable old = AURAS.remove(key);
        if (old != null) old.cancel();
        long others = AURAS.keySet().stream().filter(k -> k.startsWith(p.getUniqueId() + ":")).count();
        Glyph sign = Glyph.spawn(p.getLocation(), Glyph.FLOCON, dustColor(mode), 1.7f, Glyph.UPRIGHT, new Vector3f(0, 1.25f, -0.55f - 0.08f * others));
        if (sign != null) sign.follow(p).spin(1.5f);
        BukkitRunnable task = new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                if (!p.isOnline() || p.isDead() || !active.getAsBoolean() || !isWeiss(p) || t > 20 * 600) { cancel(); AURAS.remove(key, this); return; }
                if (t % 2 != 0) return;
                spawn.accept(bladePoint(p, Math.random()), t / 2);
            }

            @Override public synchronized void cancel() {
                super.cancel();
                if (sign != null) sign.close();
            }
        };
        AURAS.put(key, task);
        task.runTaskTimer(plugin(), 1L, 1L);
    }

    static boolean isWeiss(Player p) {
        CfPlayer cf = plugin().players().get(p);
        return cf != null && cf.character() != null && "weiss".equals(cf.character().id());
    }

    /**
     * Un point de la lame de l'epee que Weiss tient en main droite, vue de l'exterieur : 0 a la garde, 1 a la pointe.
     * (Une approximation de la position de l'objet tenu par le modele du joueur, quand il ne bouge pas le bras.)
     */
    static Location bladePoint(Player p, double along) {
        Location l = p.getLocation();
        double yaw = Math.toRadians(p.getBodyYaw());
        Vector fwd = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        Vector right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
        double y = p.isSneaking() ? 0.45 : 0.72;
        return l.add(right.multiply(0.36)).add(fwd.multiply(0.2 + 0.55 * along)).add(0, y + 0.1 + 0.5 * along, 0);
    }

    // --------------------------------------------------------- Glyphs Type 1

    static final class GlyphsType1 extends ActiveAbility {
        GlyphsType1() {
            super("weiss_glyphs1", "Glyphs (Type 1)",
                    "Rouge : Fire Aspect 10 s. Bleu : le prochain ennemi touche est emprisonne dans une cage de glace a casser. Jaune : zone de Vitesse II pour l'equipe (10 s). Cyan : barriere de verre 5x5 incassable (10 s). Blanche : prochain coup Recul III. Violette : prochain coup Lenteur II 10 s.",
                    Material.AMETHYST_SHARD, 15);
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            int d = dust(p);
            World w = p.getWorld();
            switch (d) {
                case 0 -> {
                    st.set("fireUntil", System.currentTimeMillis() + 10_000L);
                    Msg.actionBar(p, "<red>Glyphs : Fire Aspect pendant 10 s");
                    fireSlash(p, st);
                }
                case 1 -> {
                    st.set("nextIce", true);
                    Msg.actionBar(p, "<blue>Glyphs : le prochain coup emprisonne dans la glace");
                    iceBlade(p, st);
                }
                case 2 -> {
                    Location c = p.getLocation();
                    Msg.actionBar(p, "<yellow>Glyphs : zone de Vitesse II pendant 10 s");
                    plantSword(p, Color.YELLOW, () -> speedZone(p, c));
                }
                case 3 -> {
                    Vector fwd = Effects.towards(p, null);
                    Vector side = new Vector(-fwd.getZ(), 0, fwd.getX());
                    Location base = p.getLocation().add(fwd.clone().multiply(2));
                    Msg.actionBar(p, "<aqua>Glyphs : barriere de verre pendant 10 s");
                    // L'epee plantee fait surgir la barriere d'un coup, le signe des Schnee dessus tant qu'elle dure.
                    plantSword(p, Color.fromRGB(80, 230, 255), () -> {
                        TempBlocks wall = new TempBlocks().unbreakable();
                        for (int i = -2; i <= 2; i++) for (int y = 0; y < 5; y++) wall.place(base.clone().add(side.clone().multiply(i)).add(0, y, 0).getBlock(), Material.CYAN_STAINED_GLASS);
                        wall.restoreAfter(200L);
                        w.playSound(base, Sound.BLOCK_GLASS_PLACE, 1f, 0.8f);
                        w.playSound(base, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 1f, 1.4f);
                        for (int i = -2; i <= 2; i++) w.spawnParticle(Particle.DUST, base.clone().add(side.clone().multiply(i)).add(0.5, 2.5, 0.5), 12, 0.3, 1.4, 0.3, 0, new Particle.DustOptions(dustColor(3), 1.4f));
                        closeLater(wallGlyph(base, fwd), 196);
                    });
                }
                case 4 -> {
                    st.set("nextKnock", true);
                    Msg.actionBar(p, "<white>Glyphs : le prochain coup a Recul III");
                    tapBlade(p, st);
                }
                default -> {
                    st.set("nextSlow", true);
                    Msg.actionBar(p, "<dark_purple>Glyphs : le prochain coup met Lenteur II");
                    heavyBlade(p, st);
                }
            }
            w.spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 30, 0.6, 0.6, 0.6, 0, new Particle.DustOptions(dustColor(d), 1.6f));
            w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
            return true;
        }

        // ------------------------------------------------ animations des Glyphs Type 1

        /** Rouge : un grand coup en diagonale dans le vide, la lame s'embrase ; le feu reste sur elle 10 s. */
        static void fireSlash(Player p, AbilityState st) {
            play(p, 16, (pu, t) -> {
                if (t == 0 && pu != null) pu.pose("weiss_feu_arme", 3);
                if (t == 4) {
                    if (pu != null) pu.pose("weiss_feu_tranche", 3);
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.9f);
                    p.getWorld().playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 1.1f);
                }
                if (t >= 4 && t < 8) flameArc(p, (t - 4) / 4.0, (t - 3) / 4.0);
                if (t == 8) aura(p, "feu", 0, () -> System.currentTimeMillis() < (long) st.get("fireUntil", 0L), (at, k) -> {
                    p.getWorld().spawnParticle(Particle.FLAME, at, 1, 0.03, 0.03, 0.03, 0.01);
                    if (k % 2 == 0) p.getWorld().spawnParticle(Particle.SMALL_FLAME, bladePoint(p, Math.random()), 1, 0.02, 0.02, 0.02, 0.005);
                    // ...et quelques flammeches sur elle.
                    if (k % 3 == 0) p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 0.2 + Math.random() * 1.5, 0), 1, 0.3, 0.1, 0.3, 0.01);
                });
            });
        }

        /** L'arc de feu laisse par la pointe, de en haut a droite (0) a en bas a gauche (1), en passant devant elle. */
        static void flameArc(Player p, double from, double to) {
            Location l = p.getLocation();
            double yaw = Math.toRadians(l.getYaw());
            Vector fwd = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
            Vector right = new Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
            Vector u = right.clone().add(new Vector(0, 1, 0)).normalize();
            Location shoulder = l.clone().add(0, 1.35, 0).add(right.clone().multiply(0.25));
            for (double a = from; a <= to; a += 0.03) {
                double psi = Math.PI * a;
                Location tip = shoulder.clone().add(u.clone().multiply(Math.cos(psi) * 1.5)).add(fwd.clone().multiply(Math.sin(psi) * 1.5));
                p.getWorld().spawnParticle(Particle.FLAME, tip, 2, 0.04, 0.04, 0.04, 0.02);
                if (Math.random() < 0.4) p.getWorld().spawnParticle(Particle.LAVA, tip, 1, 0, 0, 0, 0);
            }
        }

        /** Bleu : la main gauche glisse le long de la lame, la glace s'y rassemble et y reste jusqu'au coup. */
        static void iceBlade(Player p, AbilityState st) {
            Particle.DustOptions frost = new Particle.DustOptions(Color.fromRGB(160, 225, 255), 1.0f);
            play(p, 22, (pu, t) -> {
                if (t == 0 && pu != null) pu.pose("weiss_glace_debut", 4);
                if (t == 6 && pu != null) pu.pose("weiss_glace_fin", 8);
                Location l = p.getLocation();
                double yaw = Math.toRadians(l.getYaw());
                Vector fwd = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
                Vector left = new Vector(Math.cos(yaw), 0, Math.sin(yaw));
                Location guard = l.clone().add(0, 1.05, 0).add(fwd.clone().multiply(0.42)).add(left.clone().multiply(-0.12));
                if (t < 14) {
                    // Des eclats de glace arrivent de partout vers la lame.
                    Location mid = guard.clone().add(left.clone().multiply(0.45));
                    for (int i = 0; i < 4; i++) {
                        Vector off = new Vector(Math.random() - 0.5, Math.random() - 0.3, Math.random() - 0.5).normalize().multiply(2.0);
                        p.getWorld().spawnParticle(Particle.SNOWFLAKE, mid.clone().add(off), 0, -off.getX(), -off.getY(), -off.getZ(), 0.11);
                    }
                }
                if (t >= 6 && t <= 14) {
                    // La main passe sur la lame : le givre la suit.
                    Location hand = guard.clone().add(left.clone().multiply(0.9 * (t - 6) / 8.0));
                    p.getWorld().spawnParticle(Particle.SNOWFLAKE, hand, 2, 0.05, 0.05, 0.05, 0.005);
                    p.getWorld().spawnParticle(Particle.DUST, hand, 3, 0.06, 0.04, 0.06, 0, frost);
                }
                if (t == 14) {
                    p.getWorld().playSound(l, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1.4f);
                    p.getWorld().playSound(l, Sound.BLOCK_GLASS_PLACE, 0.8f, 1.6f);
                    aura(p, "glace", 1, () -> st.get("nextIce", false), (at, k) -> {
                        p.getWorld().spawnParticle(Particle.SNOWFLAKE, at, 1, 0.03, 0.03, 0.03, 0.002);
                        p.getWorld().spawnParticle(Particle.DUST, bladePoint(p, Math.random()), 1, 0.02, 0.02, 0.02, 0, frost);
                    });
                }
            });
        }

        /** Jaune et cyan : elle plante son epee dans le sol, un genou a terre ; {@code effect} part a l'impact. */
        static void plantSword(Player p, Color color, Runnable effect) {
            play(p, 26, (pu, t) -> {
                if (t == 0 && pu != null) pu.pose("weiss_plante_leve", 3);
                if (t == 3 && pu != null) pu.pose("weiss_plante", 3);
                if (t == 6) {
                    Location l = p.getLocation();
                    double yaw = Math.toRadians(l.getYaw());
                    Location point = l.clone().add(-Math.sin(yaw) * 0.55, 0.05, Math.cos(yaw) * 0.55);
                    Block ground = point.clone().subtract(0, 0.2, 0).getBlock();
                    if (ground.getType().isSolid()) p.getWorld().spawnParticle(Particle.BLOCK, point, 25, 0.25, 0.05, 0.25, 0, ground.getBlockData());
                    p.getWorld().playSound(point, Sound.ITEM_TRIDENT_HIT_GROUND, 1f, 0.8f);
                    p.getWorld().playSound(point, Sound.BLOCK_ANVIL_LAND, 0.35f, 1.7f);
                    // L'onde du Glyph part de la pointe plantee.
                    for (int k = 0; k < 5; k++) {
                        final double r = 0.8 + k * 0.8;
                        Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                            for (double a = 0; a < Math.PI * 2; a += 0.45 / r) p.getWorld().spawnParticle(Particle.DUST, point.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r), 1, 0, 0, 0, 0, new Particle.DustOptions(color, 1.3f));
                        }, k);
                    }
                    effect.run();
                }
            });
        }

        /** La zone de Vitesse II du Glyph jaune : 10 s, rayon 4, le signe des Schnee tourne au sol tant qu'elle dure. */
        static void speedZone(Player p, Location c) {
            World w = c.getWorld();
            Glyph sign = groundGlyph(c, 2, 8.4f, 4f);
            closeLater(sign, 200);
            Particle.DustOptions yellow = new Particle.DustOptions(Color.YELLOW, 1.5f);
            new BukkitRunnable() {
                int s = 0;
                @Override public void run() {
                    if (s++ >= 20) { cancel(); return; }
                    if (sign == null) {
                        // Sans le pack prive : le Glyph dessine en particules.
                        for (double a = 0; a < Math.PI * 2; a += 0.3) w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 4, 0.2, Math.sin(a) * 4), 1, 0, 0, 0, 0, yellow);
                        if (s % 2 == 0) {
                            for (double a = 0; a < Math.PI * 2; a += 0.45) w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 2.6, 0.2, Math.sin(a) * 2.6), 1, 0, 0, 0, 0, yellow);
                            for (int k = 0; k < 6; k++) {
                                double a = Math.PI / 3 * k + s * 0.05;
                                for (double r = 0.4; r <= 4; r += 0.6) w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 1, 0, 0, 0, 0, yellow);
                            }
                        }
                    } else if (s % 2 == 0) {
                        for (int k = 0; k < 8; k++) {
                            double a = Math.random() * Math.PI * 2, r = Math.random() * 4;
                            w.spawnParticle(Particle.WAX_ON, c.clone().add(Math.cos(a) * r, 0.3, Math.sin(a) * r), 1, 0, 0.1, 0, 0);
                        }
                    }
                    for (Player o : w.getPlayers()) {
                        if ((o == p || Effects.isAlly(p, o)) && o.getLocation().distanceSquared(c) <= 16) Effects.refresh(o, PotionEffectType.SPEED, 1, 45);
                    }
                }
            }.runTaskTimer(plugin(), 0L, 10L);
        }

        /** Blanche : une pichenette sur la lame, qui vibre et rebondit ; une lueur blanche y reste jusqu'au coup. */
        static void tapBlade(Player p, AbilityState st) {
            Particle.DustOptions white = new Particle.DustOptions(Color.WHITE, 1.0f);
            String[] rebounds = {"weiss_blanc_rebond1", "weiss_blanc_rebond2", "weiss_blanc_rebond3", "weiss_blanc_rebond4", "weiss_blanc_tenue"};
            play(p, 24, (pu, t) -> {
                if (t == 0 && pu != null) pu.pose("weiss_blanc_tenue", 4);
                if (t == 5 && pu != null) pu.pose("weiss_blanc_tape", 2);
                Location l = p.getLocation();
                double yaw = Math.toRadians(l.getYaw());
                Location blade = l.clone().add(-Math.sin(yaw) * 0.45, 1.55, Math.cos(yaw) * 0.45);
                if (t >= 7 && t <= 15 && (t - 7) % 2 == 0) {
                    int k = (t - 7) / 2;
                    if (pu != null) pu.pose(rebounds[k], 2);
                    float vol = 1f - k * 0.2f;
                    if (k == 0) {
                        p.getWorld().playSound(l, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 1.9f);
                        p.getWorld().playSound(l, Sound.BLOCK_ANVIL_LAND, 0.25f, 2f);
                        p.getWorld().spawnParticle(Particle.END_ROD, blade, 10, 0.05, 0.25, 0.05, 0.04);
                    } else if (k < 4) {
                        p.getWorld().playSound(l, Sound.BLOCK_NOTE_BLOCK_CHIME, vol, 1.9f);
                    }
                    p.getWorld().spawnParticle(Particle.DUST, blade, 8, 0.06, 0.3, 0.06, 0, white);
                }
                if (t == 15) aura(p, "blanc", 4, () -> st.get("nextKnock", false), (at, k) -> {
                    p.getWorld().spawnParticle(Particle.DUST, at, 1, 0.02, 0.02, 0.02, 0, white);
                    if (k % 4 == 0) p.getWorld().spawnParticle(Particle.END_ROD, bladePoint(p, Math.random()), 1, 0.01, 0.01, 0.01, 0.003);
                });
            });
        }

        /** Violette : la lame tombe vers le sol, lourde, et elle la remonte en tremblant ; la brume violette reste jusqu'au coup. */
        static void heavyBlade(Player p, AbilityState st) {
            Particle.DustOptions purple = new Particle.DustOptions(dustColor(5), 1.1f);
            String[] rise = {"weiss_violet_monte1a", "weiss_violet_monte1b", "weiss_violet_monte2a", "weiss_violet_monte2b", "weiss_violet_monte3a", "weiss_violet_monte3b"};
            play(p, 30, (pu, t) -> {
                if (t == 0 && pu != null) pu.pose("weiss_violet_garde", 3);
                Location l = p.getLocation();
                double yaw = Math.toRadians(l.getYaw());
                Vector fwd = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
                if (t == 4) {
                    if (pu != null) pu.pose("weiss_violet_bas", 3);
                    p.getWorld().playSound(l, Sound.BLOCK_ANVIL_LAND, 0.5f, 0.6f);
                    p.getWorld().playSound(l, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 1.2f);
                }
                if (t >= 9 && t < 21 && (t - 9) % 2 == 0 && pu != null) pu.pose(rise[(t - 9) / 2], 2);
                if (t == 21 && pu != null) pu.pose("weiss_violet_garde", 3);
                if (t >= 4 && t < 21) {
                    // La lame : penchee vers le sol au plus bas, puis qui remonte peu a peu.
                    double down = t < 9 ? 1.0 : 1.0 - (t - 9) / 12.0;
                    for (double a = 0.2; a <= 1.0; a += 0.2) {
                        Location at = l.clone().add(0, 0.95 - down * 0.75 * a + (1 - down) * 0.15 * a, 0).add(fwd.clone().multiply(0.35 + 0.75 * a));
                        p.getWorld().spawnParticle(Particle.DUST, at, 1, 0.03, 0.03, 0.03, 0, purple);
                    }
                    if (t % 3 == 0) p.getWorld().spawnParticle(Particle.WITCH, l.clone().add(fwd.clone().multiply(0.8)).add(0, 0.5, 0), 2, 0.2, 0.2, 0.2, 0);
                }
                if (t == 21) aura(p, "violet", 5, () -> st.get("nextSlow", false), (at, k) -> {
                    p.getWorld().spawnParticle(Particle.DUST, at, 1, 0.02, 0.02, 0.02, 0, purple);
                    if (k % 4 == 0) p.getWorld().spawnParticle(Particle.WITCH, bladePoint(p, Math.random()), 1, 0.02, 0.02, 0.02, 0);
                });
            });
        }

        @Override
        public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) {
            if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
            if (System.currentTimeMillis() < (long) st.get("fireUntil", 0L)) victim.setFireTicks(Math.max(victim.getFireTicks(), 80));
            if (st.get("nextIce", false)) {
                st.remove("nextIce");
                icePrison(victim);
            }
            if (st.get("nextKnock", false)) {
                st.remove("nextKnock");
                Effects.knockbackFrom(victim, attacker.getLocation(), 8);
                victim.getWorld().spawnParticle(Particle.DUST, victim.getLocation().add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0, new Particle.DustOptions(Color.WHITE, 1.4f));
                victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.6f);
            }
            if (st.get("nextSlow", false)) {
                st.remove("nextSlow");
                Effects.give(victim, PotionEffectType.SLOWNESS, 1, 200);
                victim.getWorld().spawnParticle(Particle.DUST, victim.getLocation().add(0, 1, 0), 25, 0.4, 0.5, 0.4, 0, new Particle.DustOptions(dustColor(5), 1.4f));
                victim.getWorld().spawnParticle(Particle.WITCH, victim.getLocation().add(0, 1, 0), 10, 0.4, 0.5, 0.4, 0);
            }
        }

        /** Cage de glace de 3x3x3 autour de la victime. Elle ne disparait pas : il faut la casser. */
        static void icePrison(LivingEntity victim) {
            Location c = victim.getLocation().getBlock().getLocation();
            TempBlocks cage = new TempBlocks();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 3; dy++) for (int dz = -1; dz <= 1; dz++) {
                boolean shell = Math.abs(dx) == 1 || Math.abs(dz) == 1 || dy == -1 || dy == 3;
                if (!shell || (dx == 0 && dz == 0 && (dy == 0 || dy == 1 || dy == 2))) continue;
                cage.place(c.clone().add(dx, dy, dz).getBlock(), Material.PACKED_ICE);
            }
            victim.getWorld().playSound(c, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 0.8f);
            victim.getWorld().spawnParticle(Particle.SNOWFLAKE, c.clone().add(0.5, 1, 0.5), 40, 1, 1, 1, 0.02);
            Effects.say(victim, "<blue>Emprisonne dans la glace !");
        }
    }

    // --------------------------------------------------------- Glyphs Type 2

    static final class GlyphsType2 extends ActiveAbility {
        GlyphsType2() {
            super("weiss_glyphs2", "Glyphs (Type 2)",
                    "Rouge : fleche qui enflamme une zone de 5x5. Bleu : patinoire 10x10 avec Vitesse (10 s). Jaune : eclair sur tous les ennemis a 10 blocs. Cyan : cercle de verre qui tire 5 fleches a effet. Blanche : l'ennemi le plus proche est retenu un instant puis s'envole de 10 blocs. Violette : l'ennemi le plus proche est retenu un instant puis le sol s'ouvre sous lui, il tombe 10 blocs plus bas.",
                    Material.ECHO_SHARD, 15);
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            int d = dust(p);
            World w = p.getWorld();
            switch (d) {
                case 0 -> {
                    fireArrow(p);
                    Msg.actionBar(p, "<red>Glyphs : fleche incendiaire");
                }
                case 1 -> {
                    pirouetteRink(p);
                    Msg.actionBar(p, "<blue>Glyphs : patinoire pendant 10 s");
                }
                case 2 -> {
                    if (Effects.enemiesWithin(p, 10).isEmpty()) { Msg.actionBar(p, "<red>Glyphs : aucun ennemi a 10 blocs"); return false; }
                    kneelLightning(p);
                }
                case 3 -> {
                    glassCircle(p);
                    Msg.actionBar(p, "<aqua>Glyphs : cercle de verre et 5 fleches a effet");
                }
                case 4 -> {
                    LivingEntity t = Effects.nearestEnemy(p, 15);
                    if (t == null) { Msg.actionBar(p, "<red>Glyphs : aucun ennemi proche."); return false; }
                    holdThen(p, t, 4, "weiss_epee_levee", () -> {
                        Effects.launchUp(t, 10);
                        t.getWorld().spawnParticle(Particle.CLOUD, t.getLocation(), 30, 0.5, 0.2, 0.5, 0.05);
                        t.getWorld().playSound(t.getLocation(), Sound.ENTITY_WIND_CHARGE_WIND_BURST, 1f, 1f);
                    });
                    Msg.actionBar(p, "<white>Glyphs : " + (t instanceof Player tp ? tp.getName() : "la cible") + " est retenu, puis s'envole");
                }
                default -> {
                    LivingEntity t = Effects.nearestEnemy(p, 15);
                    if (t == null) { Msg.actionBar(p, "<red>Glyphs : aucun ennemi proche."); return false; }
                    holdThen(p, t, 5, "weiss_mains_sol", () -> sink(t, 10));
                    Msg.actionBar(p, "<dark_purple>Glyphs : " + (t instanceof Player tp ? tp.getName() : "la cible") + " est retenu, puis s'enfonce dans le sol");
                }
            }
            w.spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 30, 0.6, 0.6, 0.6, 0, new Particle.DustOptions(dustColor(d), 1.6f));
            w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.7f);
            return true;
        }

        /** Rouge : le signe rouge apparait devant elle, elle donne un coup d'estoc dedans et la fleche en sort. */
        static void fireArrow(Player p) {
            Location eye = p.getEyeLocation();
            Vector dir = eye.getDirection();
            Location at = eye.clone().add(dir.clone().multiply(1.4));
            Glyph sign = Glyph.spawn(at, Glyph.CERCLE, dustColor(0), 1.5f, Glyph.UPRIGHT, new Vector3f());
            if (sign != null) sign.spin(-6f);
            closeLater(sign, 20);
            p.getWorld().playSound(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.4f);
            play(p, 18, (pu, t) -> {
                if (t == 0 && pu != null) { pu.face(eye.getYaw()); pu.pose("weiss_estoc_arme", 3); }
                if (t == 5) {
                    if (pu != null) pu.pose("weiss_estoc", 2);
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.6f);
                }
                if (t == 7) {
                    Arrow a = Effects.shootInstantArrow(p, at, dir, 2.2, 1.0);
                    a.getPersistentDataContainer().set(FIRE_ARROW, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                    a.setFireTicks(200);
                    p.getWorld().spawnParticle(Particle.FLAME, at, 25, 0.25, 0.25, 0.25, 0.06);
                    p.getWorld().spawnParticle(Particle.DUST, at, 20, 0.4, 0.4, 0.4, 0, new Particle.DustOptions(dustColor(0), 1.4f));
                    p.getWorld().playSound(at, Sound.ITEM_FIRECHARGE_USE, 1f, 1f);
                }
                if (t == 12 && sign != null) sign.close();
            });
        }

        /** Bleu : le signe bleu au sol, elle tourne sur elle-meme sur un pied et la glace s'etend autour d'elle. */
        static void pirouetteRink(Player p) {
            Location c = p.getLocation();
            World w = c.getWorld();
            Glyph sign = groundGlyph(c, 1, 11.5f, 5f);
            closeLater(sign, 30);
            TempBlocks rink = new TempBlocks();
            rink.restoreAfter(214L);
            w.playSound(c, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.5f);
            play(p, 24, (pu, t) -> {
                if (t == 0 && pu != null) pu.pose("weiss_pirouette", 3);
                if (pu != null && t >= 3 && t <= 19) pu.spin(new Vector3f(0, 1, 0), (t - 3) * 45f, new Vector3f(), 1);
                if (t >= 3 && t <= 13 && (t - 3) % 2 == 0) {
                    // La patinoire s'etend en anneaux depuis elle, jusqu'a 11 x 11.
                    int r = (t - 3) / 2;
                    for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                        if (Math.max(Math.abs(x), Math.abs(z)) != r) continue;
                        int y = Effects.groundAround(w, c.getBlockX() + x, c.getBlockZ() + z, c.getBlockY());
                        if (y == Integer.MIN_VALUE) continue;
                        Block b = w.getBlockAt(c.getBlockX() + x, y, c.getBlockZ() + z);
                        rink.replace(b, Material.PACKED_ICE);
                        if (Math.random() < 0.4) w.spawnParticle(Particle.SNOWFLAKE, b.getLocation().add(0.5, 1.1, 0.5), 2, 0.3, 0.05, 0.3, 0.01);
                    }
                    w.playSound(c, Sound.BLOCK_GLASS_PLACE, 0.8f, 0.6f + r * 0.15f);
                }
                if (t == 13) {
                    new BukkitRunnable() {
                        int s = 0;
                        @Override public void run() {
                            if (s++ >= 10 || !p.isOnline()) { cancel(); return; }
                            if (rink.contains(p.getLocation().subtract(0, 0.1, 0).getBlock())) Effects.refresh(p, PotionEffectType.SPEED, 0, 45);
                        }
                    }.runTaskTimer(plugin(), 0L, 20L);
                }
            });
        }

        /** Jaune : un genou a terre sur le signe jaune, puis les eclairs tombent, puis elle se releve. */
        static void kneelLightning(Player p) {
            Location c = p.getLocation();
            Glyph sign = groundGlyph(c, 2, 3.2f, 6f);
            closeLater(sign, 30);
            play(p, 22, (pu, t) -> {
                if (t == 0 && pu != null) pu.pose("weiss_genou", 4);
                if (t == 4) {
                    p.getWorld().playSound(c, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.6f);
                    p.getWorld().spawnParticle(Particle.DUST, c.clone().add(0, 0.3, 0), 25, 1.2, 0.1, 1.2, 0, new Particle.DustOptions(Color.YELLOW, 1.4f));
                    p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, c.clone().add(0, 0.4, 0), 20, 1.0, 0.2, 1.0, 0.05);
                }
                if (t == 10) {
                    int n = 0;
                    for (LivingEntity enemy : Effects.enemiesWithin(p, 10)) { Effects.lightning(enemy, 4.0, p); n++; }
                    Msg.actionBar(p, n > 0 ? "<yellow>Glyphs : " + n + " ennemi(s) foudroye(s)" : "<red>Glyphs : plus aucun ennemi a 10 blocs");
                }
                if (t == 16 && pu != null) pu.pose("debout", 5);
                if (t == 18 && sign != null) sign.close();
            });
        }

        /** Cyan : un cercle de verre avec le signe cyan ; elle y donne cinq estocs rapides, une fleche a effet sort a chacun. */
        static void glassCircle(Player p) {
            World w = p.getWorld();
            Vector fwd = Effects.towards(p, null);
            Vector side = new Vector(-fwd.getZ(), 0, fwd.getX());
            Location base = p.getLocation().add(fwd.clone().multiply(2));
            TempBlocks wall = new TempBlocks().unbreakable();
            for (int y = 0; y < 5; y++) {
                int half = y == 0 || y == 4 ? 1 : 2;
                for (int i = -half; i <= half; i++) wall.place(base.clone().add(side.clone().multiply(i)).add(0, y, 0).getBlock(), Material.CYAN_STAINED_GLASS);
            }
            wall.restoreAfter(200L);
            w.playSound(base, Sound.BLOCK_GLASS_PLACE, 1f, 0.8f);
            closeLater(wallGlyph(base, fwd), 196);
            PotionType[] types = {PotionType.POISON, PotionType.SLOWNESS, PotionType.WEAKNESS, PotionType.HARMING, PotionType.STRONG_POISON};
            Location from = base.getBlock().getLocation().add(0.5, 1.5, 0.5).add(fwd);
            Location hit = base.getBlock().getLocation().add(0.5, 1.4, 0.5).subtract(fwd.clone().multiply(0.62));
            float yaw = (float) Math.toDegrees(Math.atan2(-fwd.getX(), fwd.getZ()));
            play(p, 26, (pu, t) -> {
                if (t == 0 && pu != null) { pu.face(yaw); pu.pose("weiss_estoc_arme", 3); }
                // Les fentes a t = 4, 8, 12, 16, 20 ; la fleche sort de l'autre cote du verre 2 ticks plus tard.
                if (t >= 4 && t <= 20 && t % 4 == 0 && pu != null) pu.pose("weiss_estoc", 2);
                if (t >= 6 && t <= 22 && t % 4 == 2) {
                    if (pu != null && t < 22) pu.pose("weiss_estoc_arme", 2);
                    w.spawnParticle(Particle.DUST, hit, 8, 0.15, 0.15, 0.15, 0, new Particle.DustOptions(dustColor(3), 1.2f));
                    w.playSound(hit, Sound.BLOCK_AMETHYST_BLOCK_HIT, 1f, 1.6f);
                    Arrow a = w.spawnArrow(from, fwd.clone().add(new Vector(Math.random() * 0.2 - 0.1, 0.05, Math.random() * 0.2 - 0.1)), 2.5f, 2f);
                    com.kiokouwhite.crossfight.util.Effects.setShooter(a, p);
                    a.setBasePotionType(types[(int) (Math.random() * types.length)]);
                    a.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
                    w.playSound(from, Sound.ENTITY_ARROW_SHOOT, 1f, 1f);
                }
            });
        }

        /**
         * Blanche et violette : Weiss prend sa pose ({@code pose}), la cible est retenue sur le signe sous ses pieds,
         * puis {@code then} part une seconde plus tard (elle s'envole, ou s'enfonce dans le sol).
         */
        static void holdThen(Player p, LivingEntity t, int mode, String pose, Runnable then) {
            Glyph sign = groundGlyph(t.getLocation(), mode, 2.6f, mode == 4 ? 8f : -8f);
            closeLater(sign, 30);
            Effects.freeze(t);
            Particle.DustOptions dust = new Particle.DustOptions(dustColor(mode), 1.2f);
            t.getWorld().playSound(t.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, mode == 4 ? 1.8f : 0.7f);
            float yaw = RWBY.yawTowards(p, t);
            play(p, 26, (pu, k) -> {
                if (k == 0 && pu != null) { pu.face(yaw); pu.pose(pose, 4); }
                if (k < 20 && k % 2 == 0 && t.isValid()) {
                    Location at = t.getLocation();
                    for (int i = 0; i < 4; i++) {
                        double a = Math.random() * Math.PI * 2;
                        w(t).spawnParticle(Particle.DUST, at.clone().add(Math.cos(a) * 0.9, 0.1 + Math.random() * (mode == 4 ? 1.8 : 0.4), Math.sin(a) * 0.9), 1, 0, 0, 0, 0, dust);
                    }
                    if (mode == 5) w(t).spawnParticle(Particle.WITCH, at.clone().add(0, 0.3, 0), 2, 0.4, 0.1, 0.4, 0);
                }
            });
            // Liberee quoi qu'il arrive a Weiss entre-temps.
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                Effects.unroot(t);
                if (sign != null) sign.close();
                if (t.isValid() && !t.isDead()) then.run();
            }, 21L);
        }

        private static World w(LivingEntity e) { return e.getWorld(); }

        /** Enfonce la cible de N blocs dans le sol : la colonne au-dessus d'elle est detruite, elle tombe au fond du puits. */
        static void sink(LivingEntity t, int depth) {
            Location base = t.getLocation();
            World w = base.getWorld();
            int x = base.getBlockX(), z = base.getBlockZ();
            int targetY = Math.max(w.getMinHeight() + 2, base.getBlockY() - depth);
            // Creuse le puits de 1x1 depuis les pieds de la cible jusqu'au fond.
            for (int y = base.getBlockY() + 1; y >= targetY; y--) {
                Block b = w.getBlockAt(x, y, z);
                Material m = b.getType();
                if (m.isAir() || m == Material.BEDROCK || m == Material.BARRIER || TempBlocks.isProtected(b)) continue;
                w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 12, 0.3, 0.3, 0.3, 0, b.getBlockData());
                b.setType(Material.AIR, false);
            }
            Block floor = w.getBlockAt(x, targetY - 1, z);
            if (!floor.getType().isSolid()) floor.setType(Material.STONE, false);
            w.playSound(base, Sound.BLOCK_GRAVEL_BREAK, 1f, 0.6f);
            w.playSound(base, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.8f);
            Location bottom = new Location(w, x + 0.5, targetY, z + 0.5);
            bottom.setDirection(t.getLocation().getDirection());
            t.teleport(bottom);
            if (t instanceof Player tp) Ruby.noFallUntil(tp, 2000);
            Effects.say(t, "<dark_purple>Les Glyphs de Weiss t'enfoncent dans le sol !");
        }
    }

    @EventHandler
    public void onFireArrowLand(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow a) || !a.getPersistentDataContainer().has(FIRE_ARROW)) return;
        Location c = a.getLocation();
        World w = c.getWorld();
        TempBlocks fire = new TempBlocks();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            int y = Effects.groundAround(w, c.getBlockX() + x, c.getBlockZ() + z, c.getBlockY());
            if (y == Integer.MIN_VALUE) continue;
            fire.place(w.getBlockAt(c.getBlockX() + x, y + 1, c.getBlockZ() + z), Material.FIRE);
        }
        fire.restoreAfter(160L);
        w.playSound(c, Sound.ITEM_FIRECHARGE_USE, 1f, 1f);
        a.remove();
    }

    // --------------------------------------------------------- Glyphs passif

    static final class GlyphsPassif extends PassiveAbility {
        GlyphsPassif() {
            super("weiss_glyphs_passif", "Glyphs (passif)",
                    "Rouge : 25 % de chance d'enflammer. Bleu : Vitesse 0.5. Jaune : 10 % de chance de foudroyer qui la touche. Cyan : Resistance 0.5. Blanche : Recul I. Violette : 25 % de chance de ralentir.",
                    Material.NETHER_STAR);
        }

        static void applyBoosts(Player p, int mode) {
            Boosts.set(p, "weiss_dust", new Boosts.Boost(0, mode == 1 ? 0.5 : 0, mode == 3 ? 0.5 : 0, 0));
        }

        @Override
        public void onEquip(Player p, CfPlayer cf) {
            applyBoosts(p, dust(p));
            renameWeapon(p, dust(p));
        }

        @Override
        public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) {
            if (e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
            switch (dust(attacker)) {
                case 0 -> { if (Math.random() < 0.25) victim.setFireTicks(Math.max(victim.getFireTicks(), 80)); }
                case 4 -> Bukkit.getScheduler().runTask(plugin(), () -> victim.setVelocity(victim.getVelocity().add(Effects.towards(attacker, victim).multiply(0.45).setY(0.1))));
                case 5 -> { if (Math.random() < 0.25) Effects.give(victim, PotionEffectType.SLOWNESS, 0, 100); }
                default -> { }
            }
        }

        @Override
        public void onDamaged(Player victim, EntityDamageEvent e, AbilityState st) {
            if (dust(victim) != 2 || !(e instanceof EntityDamageByEntityEvent ede)) return;
            LivingEntity attacker = com.kiokouwhite.crossfight.listener.DamageListener.livingAttackerOf(ede.getDamager());
            if (attacker != null && attacker != victim && Math.random() < 0.10) {
                Bukkit.getScheduler().runTask(plugin(), () -> Effects.lightning(attacker, 4.0, victim));
            }
        }
    }

    // ----------------------------------------------------------------- ultime

    /** Invoque un ennemi definitivement vaincu dans l'equipe de Weiss, sans plastron et avec Faiblesse. */
    static final class Invocation extends UltimateAbility {
        Invocation() {
            super("weiss_ult", "Invocation", "Weiss rappelle un ennemi que son equipe a definitivement vaincu : il rejoint son equipe avec une vie, sans pouvoir porter de plastron et avec Faiblesse.", Material.SOUL_LANTERN);
        }

        @Override
        public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            Team team = cf.team();
            if (team == null) return false;
            List<ChoiceMenu.Option> options = new ArrayList<>();
            for (Player o : Bukkit.getOnlinePlayers()) {
                CfPlayer ocf = plugin().players().get(o);
                if (ocf.team() == null || ocf.team() == team || ocf.lives() > 0 || com.kiokouwhite.crossfight.util.Revived.is(o)) continue;
                if (ocf.character() == null) continue;
                options.add(new ChoiceMenu.Option(Material.PLAYER_HEAD, o.getName(), List.of("<gray>" + ocf.character().name() + " <dark_gray>(" + ocf.team().name() + ")"),
                        () -> summon(p, cf, o)));
            }
            if (options.isEmpty()) {
                Msg.actionBar(p, "<red>Invocation : aucun ennemi definitivement vaincu a rappeler.");
                return false;
            }
            ChoiceMenu.open(p, "<dark_gray>Invocation : qui rappeler ?", options, pl -> cf.setUltimateCharge(100));
            return true;
        }

        private void summon(Player weiss, CfPlayer wcf, Player target) {
            CfPlayer tcf = plugin().players().get(target);
            if (tcf.lives() > 0 || !target.isOnline()) { Msg.actionBar(weiss, "<red>Ce joueur n'est plus invocable."); wcf.setUltimateCharge(100); return; }
            Team team = wcf.team();
            com.kiokouwhite.crossfight.util.Revived.revive(weiss, target, team);
            // L'invocation elle-meme : son cri d'invocation (le chevalier blanc de BlazBlue).
            com.kiokouwhite.crossfight.util.Sounds.ability(weiss, "weiss_ult_invoque");
            Msg.broadcast("<light_purple>" + weiss.getName() + " invoque <yellow>" + target.getName() + "</yellow> dans " + team.coloredName() + " <light_purple>!");
        }
    }
}
