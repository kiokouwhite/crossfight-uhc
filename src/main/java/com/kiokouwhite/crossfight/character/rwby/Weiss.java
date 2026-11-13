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
                case 0 -> { st.set("fireUntil", System.currentTimeMillis() + 10_000L); Msg.actionBar(p, "<red>Glyphs : Fire Aspect pendant 10 s"); }
                case 1 -> { st.set("nextIce", true); Msg.actionBar(p, "<blue>Glyphs : le prochain coup emprisonne dans la glace"); }
                case 2 -> {
                    Location c = p.getLocation();
                    Msg.actionBar(p, "<yellow>Glyphs : zone de Vitesse II pendant 10 s");
                    new BukkitRunnable() {
                        int s = 0;
                        @Override public void run() {
                            if (s++ >= 10) { cancel(); return; }
                            for (double a = 0; a < Math.PI * 2; a += 0.3) w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 4, 0.2, Math.sin(a) * 4), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.YELLOW, 1.5f));
                            for (Player o : w.getPlayers()) {
                                if ((o == p || Effects.isAlly(p, o)) && o.getLocation().distanceSquared(c) <= 16) Effects.refresh(o, PotionEffectType.SPEED, 1, 45);
                            }
                        }
                    }.runTaskTimer(plugin(), 0L, 20L);
                }
                case 3 -> {
                    Vector fwd = Effects.towards(p, null);
                    Vector side = new Vector(-fwd.getZ(), 0, fwd.getX());
                    Location base = p.getLocation().add(fwd.clone().multiply(2));
                    TempBlocks wall = new TempBlocks().unbreakable();
                    for (int i = -2; i <= 2; i++) for (int y = 0; y < 5; y++) wall.place(base.clone().add(side.clone().multiply(i)).add(0, y, 0).getBlock(), Material.CYAN_STAINED_GLASS);
                    wall.restoreAfter(200L);
                    w.playSound(base, Sound.BLOCK_GLASS_PLACE, 1f, 0.8f);
                    Msg.actionBar(p, "<aqua>Glyphs : barriere de verre pendant 10 s");
                }
                case 4 -> { st.set("nextKnock", true); Msg.actionBar(p, "<white>Glyphs : le prochain coup a Recul III"); }
                default -> { st.set("nextSlow", true); Msg.actionBar(p, "<dark_purple>Glyphs : le prochain coup met Lenteur II"); }
            }
            w.spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 30, 0.6, 0.6, 0.6, 0, new Particle.DustOptions(dustColor(d), 1.6f));
            w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f);
            return true;
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
            }
            if (st.get("nextSlow", false)) {
                st.remove("nextSlow");
                Effects.give(victim, PotionEffectType.SLOWNESS, 1, 200);
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
                    "Rouge : fleche qui enflamme une zone de 5x5. Bleu : patinoire 10x10 avec Vitesse (10 s). Jaune : eclair sur tous les ennemis a 10 blocs. Cyan : barriere 3x3 qui tire 5 fleches a effet. Blanche : l'ennemi le plus proche s'envole de 10 blocs. Violette : le sol s'ouvre sous l'ennemi le plus proche, qui tombe 10 blocs plus bas.",
                    Material.ECHO_SHARD, 15);
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            int d = dust(p);
            World w = p.getWorld();
            switch (d) {
                case 0 -> {
                    Arrow a = Effects.shootInstantArrow(p, p.getEyeLocation().getDirection(), 2.2, 1.0);
                    a.getPersistentDataContainer().set(FIRE_ARROW, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                    a.setFireTicks(200);
                    Msg.actionBar(p, "<red>Glyphs : fleche incendiaire");
                }
                case 1 -> {
                    TempBlocks rink = new TempBlocks();
                    Location c = p.getLocation();
                    for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
                        int y = Effects.groundAround(w, c.getBlockX() + x, c.getBlockZ() + z, c.getBlockY());
                        if (y != Integer.MIN_VALUE) rink.replace(w.getBlockAt(c.getBlockX() + x, y, c.getBlockZ() + z), Material.PACKED_ICE);
                    }
                    rink.restoreAfter(200L);
                    w.playSound(c, Sound.BLOCK_GLASS_PLACE, 1f, 0.6f);
                    new BukkitRunnable() {
                        int s = 0;
                        @Override public void run() {
                            if (s++ >= 10 || !p.isOnline()) { cancel(); return; }
                            if (rink.contains(p.getLocation().subtract(0, 0.1, 0).getBlock())) Effects.refresh(p, PotionEffectType.SPEED, 0, 45);
                        }
                    }.runTaskTimer(plugin(), 0L, 20L);
                    Msg.actionBar(p, "<blue>Glyphs : patinoire pendant 10 s");
                }
                case 2 -> {
                    int n = 0;
                    for (LivingEntity enemy : Effects.enemiesWithin(p, 10)) { Effects.lightning(enemy, 4.0, p); n++; }
                    Msg.actionBar(p, n > 0 ? "<yellow>Glyphs : " + n + " ennemi(s) foudroye(s)" : "<red>Glyphs : aucun ennemi a 10 blocs");
                    if (n == 0) return false;
                }
                case 3 -> {
                    Vector fwd = Effects.towards(p, null);
                    Vector side = new Vector(-fwd.getZ(), 0, fwd.getX());
                    Location base = p.getLocation().add(fwd.clone().multiply(2));
                    TempBlocks wall = new TempBlocks().unbreakable();
                    for (int i = -1; i <= 1; i++) for (int y = 0; y < 3; y++) wall.place(base.clone().add(side.clone().multiply(i)).add(0, y, 0).getBlock(), Material.CYAN_STAINED_GLASS);
                    wall.restoreAfter(200L);
                    PotionType[] types = {PotionType.POISON, PotionType.SLOWNESS, PotionType.WEAKNESS, PotionType.HARMING, PotionType.STRONG_POISON};
                    for (int i = 0; i < 5; i++) {
                        final int k = i;
                        Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                            Location from = base.clone().add(fwd.clone().multiply(1)).add(0.5, 1.5, 0.5);
                            Arrow a = w.spawnArrow(from, fwd.clone().add(new Vector(Math.random() * 0.2 - 0.1, 0.05, Math.random() * 0.2 - 0.1)), 2.5f, 2f);
                            com.kiokouwhite.crossfight.util.Effects.setShooter(a, p);
                            a.setBasePotionType(types[(int) (Math.random() * types.length)]);
                            a.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
                            w.playSound(from, Sound.ENTITY_ARROW_SHOOT, 1f, 1f);
                        }, 5L * k);
                    }
                    Msg.actionBar(p, "<aqua>Glyphs : barriere et 5 fleches a effet");
                }
                case 4 -> {
                    LivingEntity t = Effects.nearestEnemy(p, 15);
                    if (t == null) { Msg.actionBar(p, "<red>Glyphs : aucun ennemi proche."); return false; }
                    Effects.launchUp(t, 10);
                    w.spawnParticle(Particle.CLOUD, t.getLocation(), 30, 0.5, 0.2, 0.5, 0.05);
                    Msg.actionBar(p, "<white>Glyphs : " + (t instanceof Player tp ? tp.getName() : "la cible") + " s'envole");
                }
                default -> {
                    LivingEntity t = Effects.nearestEnemy(p, 15);
                    if (t == null) { Msg.actionBar(p, "<red>Glyphs : aucun ennemi proche."); return false; }
                    sink(t, 10);
                    Msg.actionBar(p, "<dark_purple>Glyphs : " + (t instanceof Player tp ? tp.getName() : "la cible") + " s'enfonce dans le sol");
                }
            }
            w.spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 30, 0.6, 0.6, 0.6, 0, new Particle.DustOptions(dustColor(d), 1.6f));
            w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.7f);
            return true;
        }

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
