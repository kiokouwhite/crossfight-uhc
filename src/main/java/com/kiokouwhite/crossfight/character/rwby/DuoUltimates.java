package com.kiokouwhite.crossfight.character.rwby;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.Clones;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Flames;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Puppet;
import com.kiokouwhite.crossfight.util.Stealth;
import com.kiokouwhite.crossfight.util.TempBlocks;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les six Ultimes en Duo de RWBY. La partenaire du lanceur peut etre un joueur ou, dans la zone duo
 * du mode test, un mannequin duo a l'image du personnage : les roles sont des entites vivantes, et ce
 * qui n'a de sens que pour un joueur (furtivite, chute amortie) ne s'applique qu'aux joueurs.
 */
final class DuoUltimates {

    private DuoUltimates() {}

    /** Joueurs sous "Weiss et Blake" : leurs coups immobilisent 5 secondes. Valeur = fin en millisecondes. */
    static final Map<UUID, Long> FREEZING_HITS = new HashMap<>();

    private static LivingEntity of(LivingEntity a, LivingEntity b, String id) {
        if (RWBY.characterId(a).equals(id)) return a;
        if (RWBY.characterId(b).equals(id)) return b;
        return null;
    }

    static String name(LivingEntity a, LivingEntity b) { return name(RWBY.characterId(a), RWBY.characterId(b)); }

    /** Le nom de l'attaque d'equipe de la paire, comme dans RWBY (Ruby et Yang n'en ont pas dans la serie : celui de leur duo). */
    static String name(String x, String y) {
        java.util.Set<String> pair = java.util.Set.of(x, y);
        if (pair.equals(java.util.Set.of("ruby", "weiss"))) return "Ice Flower";
        if (pair.equals(java.util.Set.of("ruby", "yang"))) return "Strawberry Sunrise";
        if (pair.equals(java.util.Set.of("ruby", "blake"))) return "Ladybug";
        if (pair.equals(java.util.Set.of("weiss", "yang"))) return "Freezerburn";
        if (pair.equals(java.util.Set.of("weiss", "blake"))) return "Checkmate";
        if (pair.equals(java.util.Set.of("blake", "yang"))) return "Bumblebee";
        return "l'Ultime en Duo";
    }

    static String describe(LivingEntity a, LivingEntity b) { return describe(RWBY.characterId(a), RWBY.characterId(b)); }

    static String describe(String x, String y) {
        java.util.Set<String> pair = java.util.Set.of(x, y);
        if (pair.equals(java.util.Set.of("ruby", "weiss"))) return "Patinoire de 50 blocs, Vitesse III pour les allies";
        if (pair.equals(java.util.Set.of("ruby", "yang"))) return "Yang projette Ruby sur 10 blocs, 5 coeurs autour d'elle";
        if (pair.equals(java.util.Set.of("ruby", "blake"))) return "Invisibles et Vitesse III pendant 30 s";
        if (pair.equals(java.util.Set.of("weiss", "yang"))) return "Glyphe de glace et de feu : ralentit et enflamme 30 s";
        if (pair.equals(java.util.Set.of("weiss", "blake"))) return "Invisibles 10 s, chaque coup immobilise 5 s";
        if (pair.equals(java.util.Set.of("blake", "yang"))) return "3 clones de Yang qui explosent (3 coeurs)";
        return "";
    }

    /** Le texte du guide : ce que fait l'attaque d'equipe de la paire, en phrases completes. */
    static String guide(String x, String y) {
        java.util.Set<String> pair = java.util.Set.of(x, y);
        if (pair.equals(java.util.Set.of("ruby", "weiss"))) return "Weiss plante son epee dans le sol : une patinoire de 50 blocs de large s'etend autour d'elle pendant 30 secondes, et Ruby en fait le tour en semant des petales de rose. Les allies qui s'y trouvent ont Vitesse III.";
        if (pair.equals(java.util.Set.of("ruby", "yang"))) return "Yang arme son poing et projette Ruby dans une explosion, sur 10 blocs. Ruby file en toupie dans une couronne de petales de rose : elle detruit les blocs qu'elle traverse et inflige 5 coeurs aux adversaires a 3 blocs.";
        if (pair.equals(java.util.Set.of("ruby", "blake"))) return "Blake prend la main de Ruby et elles s'enfoncent ensemble dans une flaque noire : toutes deux sont invisibles et ont Vitesse III pendant 30 secondes.";
        if (pair.equals(java.util.Set.of("weiss", "yang"))) return "Weiss dessine un glyphe de 12 blocs de large au sol, sur l'adversaire le plus proche (10 blocs) ou devant elle. Yang bondit dessus et le frappe trois fois du poing : a chaque coup, un pic de glace en flammes jaillit. Pendant 30 secondes, les adversaires sur le glyphe sont ralentis et prennent feu.";
        if (pair.equals(java.util.Set.of("weiss", "blake"))) return "Weiss dessine un glyphe sous elle et Blake ; elles sautent dessus ensemble et une enorme flaque noire les engloutit. Toutes deux sont invisibles 10 secondes, et chaque adversaire qu'elles frappent est immobilise 5 secondes.";
        if (pair.equals(java.util.Set.of("blake", "yang"))) return "Blake pose la main sur le dos de Yang : trois clones de Yang sortent d'elle et foncent sur les trois adversaires les plus proches (40 blocs). Chaque clone explose en atteignant sa cible, quand on l'attaque, ou au bout de 20 secondes : 3 coeurs aux adversaires a 3 blocs, qui prennent feu.";
        return "";
    }

    static boolean cast(Player a, LivingEntity b) {
        LivingEntity ruby = of(a, b, "ruby"), weiss = of(a, b, "weiss"), yang = of(a, b, "yang"), blake = of(a, b, "blake");
        if (ruby != null && weiss != null) return patinoire(weiss, ruby);
        if (ruby != null && yang != null) return lancerDeRose(yang, ruby);
        if (ruby != null && blake != null) return ombreRouge(ruby, blake);
        if (weiss != null && yang != null) return piquesDeGlace(weiss, yang);
        if (weiss != null && blake != null) return gelSilencieux(weiss, blake);
        if (blake != null && yang != null) return leurresExplosifs(yang, blake);
        Msg.actionBar(a, "<red>Cette paire n'a pas d'Ultime en Duo.");
        return false;
    }

    // ------------------------------------------------------------ Ruby + Weiss

    /** Patinoire de 50 blocs de large : les allies dessus ont Vitesse III. 30 secondes. */
    /** Ice Flower : le temps du geste de Weiss avant que l'epee touche le sol, puis celui que met la glace a s'etendre. */
    static final int PLANT = 6, SPREAD = 30;
    static final Particle.DustOptions PETAL = new Particle.DustOptions(Color.fromRGB(205, 24, 44), 1.3f),
            PETAL_DARK = new Particle.DustOptions(Color.fromRGB(140, 10, 30), 1.0f);

    /**
     * Weiss leve son epee et la plante dans le sol : la glace part de la lame et s'etend jusqu'a 25 blocs. Ruby
     * file autour de la patinoire a mesure qu'elle grandit, a toute vitesse, en semant des petales de rose qui
     * restent en couronne sur son bord. Elle revient ensuite d'ou elle est partie.
     */
    static boolean patinoire(LivingEntity weiss, LivingEntity ruby) {
        World w = weiss.getWorld();
        Location c = weiss.getLocation();
        TempBlocks rink = new TempBlocks();
        int radius = 25;
        // Les blocs de la patinoire, du centre vers le bord.
        List<Block> blocks = new ArrayList<>();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius) continue;
                int bx = c.getBlockX() + x, bz = c.getBlockZ() + z;
                int y = Effects.groundAround(w, bx, bz, c.getBlockY());
                if (y == Integer.MIN_VALUE) continue;
                Block top = w.getBlockAt(bx, y, bz);
                if (top.getType() == Material.WATER || top.getType() == Material.LAVA || top.getType().isSolid()) blocks.add(top);
            }
        }
        blocks.sort(java.util.Comparator.comparingDouble(bl -> bl.getLocation().add(0.5, 0, 0.5).distanceSquared(new Location(w, c.getX(), bl.getY(), c.getZ()))));

        // Weiss : l'epee levee des deux mains, puis plantee, un genou a terre, tant que la glace s'etend.
        ItemStack sword = weiss instanceof Player wp ? Weiss.sword(wp) : null;
        com.kiokouwhite.crossfight.util.Puppet wpu = com.kiokouwhite.crossfight.util.Puppet.show(weiss, "weiss", sword != null ? sword : new ItemStack(Material.IRON_SWORD), "debout");
        if (wpu != null) { wpu.pose("weiss_plante_leve", 3); wpu.endAfter(PLANT + SPREAD + 6, 5); }
        Effects.root(weiss, PLANT + SPREAD);

        // Ruby : couchee dans sa course, elle tourne autour de la glace. Elle garde son regard et retrouve sa place a la fin.
        Location rubyStart = ruby.getLocation();
        com.kiokouwhite.crossfight.util.Puppet rpu = com.kiokouwhite.crossfight.util.Puppet.show(ruby, "ruby", null, "ruby_petales_vol");
        if (rpu != null) rpu.endAfter(PLANT + SPREAD + 4, 4);
        if (ruby instanceof Player rp) Ruby.noFallUntil(rp, (PLANT + SPREAD) * 50L + 4000);
        double startAngle = Math.atan2(rubyStart.getZ() - c.getZ(), rubyStart.getX() - c.getX());
        w.playSound(c, Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 0.7f);

        final Player side = Effects.sideOf(weiss);
        new BukkitRunnable() {
            int t = 0, placed = 0;
            Location last = rubyStart.clone();
            @Override public void run() {
                t++;
                if (t == 3 && wpu != null && com.kiokouwhite.crossfight.util.Puppet.of(weiss) == wpu) wpu.pose("weiss_plante", 3);
                if (t == PLANT) {
                    w.playSound(c, Sound.BLOCK_GLASS_PLACE, 1f, 0.6f);
                    w.playSound(c, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 0.8f);
                    w.playSound(c, Sound.ITEM_TRIDENT_HIT_GROUND, 1f, 0.6f);
                    w.spawnParticle(Particle.SNOWFLAKE, c.clone().add(0, 0.3, 0), 60, 0.6, 0.2, 0.6, 0.12);
                }
                if (t < PLANT) return;
                double k = Math.min(1.0, (t - PLANT) / (double) SPREAD), reach = radius * k;
                // La glace gagne du terrain a partir de la lame.
                while (placed < blocks.size()) {
                    Block bl = blocks.get(placed);
                    double dx = bl.getX() + 0.5 - c.getX(), dz = bl.getZ() + 0.5 - c.getZ();
                    if (dx * dx + dz * dz > reach * reach) break;
                    rink.replace(bl, Material.PACKED_ICE);
                    placed++;
                }
                if (t % 3 == 0 && k < 1) {
                    for (int i = 0; i < 24; i++) {
                        double a = i * Math.PI / 12 + t * 0.2;
                        w.spawnParticle(Particle.SNOWFLAKE, c.clone().add(Math.cos(a) * reach, 0.3, Math.sin(a) * reach), 1, 0.2, 0.1, 0.2, 0.01);
                    }
                    w.playSound(c, Sound.BLOCK_GLASS_STEP, 0.8f, 0.7f + (float) k * 0.8f);
                }
                // Ruby court sur le bord de la glace : pres de trois tours le temps qu'elle s'etende.
                if (k < 1 && ruby.isValid() && !ruby.isDead()) {
                    double r = Math.max(2.5, reach + 0.8), a = startAngle + (t - PLANT) * 0.6;
                    Location at = new Location(w, c.getX() + Math.cos(a) * r, c.getY(), c.getZ() + Math.sin(a) * r);
                    int gy = Effects.groundAround(w, at.getBlockX(), at.getBlockZ(), c.getBlockY());
                    at.setY(gy == Integer.MIN_VALUE ? c.getY() : gy + 1);
                    at.setYaw(ruby.getLocation().getYaw());
                    at.setPitch(ruby.getLocation().getPitch());
                    if (at.getBlock().isPassable()) {
                        ruby.teleport(at);
                        ruby.setFallDistance(0);
                        // Le pantin regarde ou elle court (la tangente du cercle).
                        if (rpu != null && com.kiokouwhite.crossfight.util.Puppet.of(ruby) == rpu) rpu.face((float) Math.toDegrees(Math.atan2(-(-Math.sin(a)), Math.cos(a))));
                        petals(last, at);
                        last = at.clone();
                    }
                    if (t % 4 == 0) w.playSound(at, Sound.ENTITY_PHANTOM_FLAP, 0.7f, 1.8f);
                }
                if (t == PLANT + SPREAD) {
                    // La patinoire est faite : Ruby revient a sa place dans un dernier tourbillon de petales.
                    if (ruby.isValid() && !ruby.isDead()) {
                        w.spawnParticle(Particle.DUST, ruby.getLocation().add(0, 1, 0), 30, 0.5, 0.7, 0.5, 0, PETAL);
                        ruby.teleport(rubyStart);
                        ruby.setFallDistance(0);
                        w.spawnParticle(Particle.DUST, rubyStart.clone().add(0, 1, 0), 30, 0.5, 0.7, 0.5, 0, PETAL);
                        w.spawnParticle(Particle.CHERRY_LEAVES, rubyStart.clone().add(0, 1.6, 0), 20, 0.6, 0.5, 0.6, 0);
                    }
                    w.playSound(c, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.7f);
                }
                if (t >= PLANT + SPREAD && (t - PLANT - SPREAD) % 20 == 0) {
                    int seconds = (t - PLANT - SPREAD) / 20;
                    if (seconds >= 30) { rink.restore(); cancel(); return; }
                    for (Player p : w.getPlayers()) {
                        if (side == null || !(p == side || Effects.isAlly(side, p))) continue;
                        Block under = p.getLocation().subtract(0, 0.1, 0).getBlock();
                        if (!rink.contains(under)) continue;
                        Effects.refresh(p, PotionEffectType.SPEED, 2, 45);
                        // La carte bleue des allies sur la patinoire (pas pour les deux qui l'ont lancee).
                        if (p != ruby && p != weiss) card(p, "duo_ruby_weiss", true, 40);
                    }
                    // La couronne de petales reste sur le bord de la patinoire.
                    for (int i = 0; i < 40; i++) {
                        double a = Math.random() * Math.PI * 2;
                        Location at = c.clone().add(Math.cos(a) * (radius + 0.5), 0.4 + Math.random() * 1.4, Math.sin(a) * (radius + 0.5));
                        w.spawnParticle(Particle.DUST, at, 1, 0.2, 0.2, 0.2, 0, i % 3 == 0 ? PETAL_DARK : PETAL);
                        if (i % 4 == 0) w.spawnParticle(Particle.CHERRY_LEAVES, at.clone().add(0, 0.8, 0), 1, 0.3, 0.2, 0.3, 0);
                    }
                }
            }
        }.runTaskTimer(CrossfightPlugin.get(), 1L, 1L);
        return true;
    }

    /** Les petales de rose que Ruby laisse derriere elle, d'un point de sa course au suivant. */
    private static void petals(Location from, Location to) {
        World w = to.getWorld();
        Vector step = to.toVector().subtract(from.toVector());
        double length = step.length();
        if (length < 0.1 || length > 40) return;
        int n = (int) Math.min(30, Math.ceil(length / 0.7));
        for (int i = 0; i < n; i++) {
            Location at = from.clone().add(step.clone().multiply((i + Math.random()) / n)).add(0, 0.3 + Math.random() * 1.3, 0);
            w.spawnParticle(Particle.DUST, at, 1, 0.15, 0.15, 0.15, 0, i % 3 == 0 ? PETAL_DARK : PETAL);
            if (i % 3 == 0) w.spawnParticle(Particle.CHERRY_LEAVES, at.clone().add(0, 0.5, 0), 1, 0.2, 0.2, 0.2, 0);
        }
    }

    // --------------------------------------------------------- gestes communs

    /** Le pantin d'une des quatre (joueuse, pilote de mannequin ou mannequin duo) ; {@code armed} : son arme a la main (Ruby, Weiss). */
    private static Puppet puppet(LivingEntity e, boolean armed) {
        String id = RWBY.characterId(e);
        if (id.isEmpty()) return null;
        ItemStack held = null;
        if (armed && id.equals("ruby")) held = e instanceof Player p ? Ruby.weapon(p) : new ItemStack(Material.DIAMOND_HOE);
        if (armed && id.equals("weiss")) held = e instanceof Player p ? Weiss.sword(p) : new ItemStack(Material.DIAMOND_SWORD);
        return Puppet.show(e, id, held, "debout");
    }

    /** La carte d'un Ultime en Duo en haut a droite de l'ecran de celui qu'il touche (adversaire gene, allie aide), pendant {@code ticks}. */
    private static void card(LivingEntity touched, String duo, boolean ally, int ticks) {
        if (touched instanceof Player p) com.kiokouwhite.crossfight.hud.InfluenceHud.threat(p, duo, ally, ticks);
    }

    /** Vrai tant que ce pantin est bien celui qui joue l'animation de {@code e}. */
    private static boolean shown(LivingEntity e, Puppet pu) { return pu != null && Puppet.of(e) == pu; }

    private static boolean standable(Location at) {
        return at.getBlock().isPassable() && at.clone().add(0, 1, 0).getBlock().isPassable() && !at.clone().add(0, -1, 0).getBlock().isPassable();
    }

    /**
     * Amene {@code mover} a la gauche de {@code anchor}, a {@code dist} blocs, dans un nuage de fumee : les deux se
     * tiennent alors cote a cote, tournees du meme cote (l'orientation rendue). S'il n'y a de place d'aucun cote,
     * elle reste ou elle est.
     */
    private static float sideBySide(LivingEntity anchor, LivingEntity mover, double dist) {
        Location al = anchor.getLocation();
        for (float yaw : new float[]{al.getYaw(), al.getYaw() + 180f, al.getYaw() + 90f, al.getYaw() - 90f}) {
            double r = Math.toRadians(yaw);
            // A sa gauche quand elle regarde vers yaw : (cos, sin).
            Location spot = al.clone().add(Math.cos(r) * dist, 0, Math.sin(r) * dist);
            if (!standable(spot)) continue;
            spot.setYaw(yaw);
            spot.setPitch(0);
            World w = anchor.getWorld();
            w.spawnParticle(Particle.SMOKE, mover.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
            mover.teleport(spot);
            w.spawnParticle(Particle.SMOKE, spot.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
            return yaw;
        }
        return al.getYaw();
    }

    private static float yawTo(Location from, Location to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        return dx * dx + dz * dz < 1e-4 ? from.getYaw() : (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    // ------------------------------------------------------------- Ruby + Yang

    /** Strawberry Sunrise : Yang lance au tick THROW ; la toupie de Ruby file pendant FLIGHT ticks, puis ralentit. */
    static final int THROW = 7, FLIGHT = 24;
    static final float TOP_PER_TICK = 72f;
    static final float[] TOP_LANDING = {60f, 48f, 36f, 24f, 12f};

    /**
     * Yang arme son bras, Ruby devant son poing, deja en toupie, la faux tendue. Le coup part : le gantelet explose
     * et projette Ruby sur 10 blocs. Elle file en tournant sur elle-meme dans une couronne de petales de rose,
     * detruit ce qu'elle traverse et inflige 5 coeurs autour d'elle.
     */
    static boolean lancerDeRose(LivingEntity yang, LivingEntity ruby) {
        Vector look = yang.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 0.01) look = new Vector(1, 0, 0);
        final Vector dir = look.normalize();
        final float yaw = (float) Math.toDegrees(Math.atan2(-dir.getX(), dir.getZ()));
        World w = yang.getWorld();
        ruby.teleport(yang.getLocation().add(dir.clone().multiply(0.9)).setDirection(dir));
        if (ruby instanceof Player rp) Ruby.noFallUntil(rp, 5000);
        final Puppet py = puppet(yang, false), pr = puppet(ruby, true);
        if (py != null) { py.face(yaw); py.pose("yang_lancer_arme", 3); py.endAfter(THROW + 10, 4); }
        if (pr != null) { pr.face(yaw); pr.pose("ruby_toupie", 3); pr.endAfter(THROW + FLIGHT + TOP_LANDING.length + 2, 4); }
        w.playSound(yang.getLocation(), Sound.ITEM_ARMOR_EQUIP_GOLD, 1f, 0.9f);
        List<UUID> hit = new ArrayList<>();
        new BukkitRunnable() {
            int t = 0;
            float angle = 0;
            @Override public void run() {
                t++;
                if (!ruby.isValid() || ruby.isDead()) { cancel(); return; }
                int flight = t - THROW;
                // La toupie : elle tourne deja dans la main de Yang, a pleine vitesse en vol, puis ralentit en retombant.
                if (t >= 3 && shown(ruby, pr)) {
                    angle += flight <= FLIGHT ? TOP_PER_TICK : TOP_LANDING[Math.min(TOP_LANDING.length - 1, flight - FLIGHT - 1)];
                    pr.spin(new Vector3f(0, 1, 0), angle, new Vector3f(0, 0, 0), 1);
                }
                Location at = ruby.getLocation();
                // La couronne de petales qui tourne avec elle.
                for (int k = 0; k < 6; k++) {
                    double a = Math.toRadians(angle) + k * Math.PI / 3, r = 1.7;
                    Location petal = at.clone().add(Math.cos(a) * r, 1.0 + 0.25 * Math.sin(a * 2), Math.sin(a) * r);
                    w.spawnParticle(Particle.DUST, petal, 2, 0.08, 0.08, 0.08, 0, k % 2 == 0 ? PETAL : PETAL_DARK);
                    if (k % 3 == 0) w.spawnParticle(Particle.CHERRY_LEAVES, petal, 1, 0.1, 0.1, 0.1, 0);
                }
                if (flight < 0) {
                    ruby.setVelocity(new Vector(0, 0, 0));
                    if (t == THROW - 2 && shown(yang, py)) py.pose("yang_direct_droit", 2);
                    return;
                }
                if (flight == 0) {
                    // Le coup part : l'explosion du gantelet, et Ruby s'envole.
                    Location fist = shown(yang, py) ? py.armPoint(true, 1.0) : yang.getEyeLocation().add(dir.clone().multiply(0.6)).add(0, -0.3, 0);
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, fist, 1);
                    w.spawnParticle(Particle.FLAME, fist, 90, 0.1, 0.1, 0.1, 0.2);
                    w.spawnParticle(Particle.LAVA, fist, 12, 0.3, 0.3, 0.3, 0);
                    w.playSound(fist, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.1f);
                    w.playSound(fist, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 0.7f);
                    RWBY.ejectShell(yang, fist);
                    ruby.setVelocity(dir.clone().multiply(2.4).setY(0.35));
                    return;
                }
                if (flight > FLIGHT) { if (flight > FLIGHT + TOP_LANDING.length) cancel(); return; }
                w.spawnParticle(Particle.CHERRY_LEAVES, at.clone().add(0, 1, 0), 10, 0.6, 0.6, 0.6, 0.05);
                w.spawnParticle(Particle.DUST, at.clone().add(0, 1, 0), 8, 0.5, 0.5, 0.5, 0, new Particle.DustOptions(Color.fromRGB(220, 20, 60), 1.5f));
                if (flight < 6) w.spawnParticle(Particle.FLAME, at.clone().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0.03);
                // Ruby detruit ce qu'elle traverse (sauf les blocs indestructibles ou proteges).
                for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 2; dy++) for (int dz = -1; dz <= 1; dz++) {
                    Block b = at.clone().add(dx, dy, dz).getBlock();
                    Material type = b.getType();
                    if (type.isAir() || type == Material.BEDROCK || type == Material.OBSIDIAN || type == Material.BARRIER || type == Material.WATER || type == Material.LAVA) continue;
                    if (TempBlocks.isProtected(b)) continue;
                    w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0, b.getBlockData());
                    b.setType(Material.AIR, false);
                }
                for (LivingEntity enemy : Effects.enemiesAround(ruby, 3.0)) {
                    if (hit.contains(enemy.getUniqueId())) continue;
                    hit.add(enemy.getUniqueId());
                    Effects.damage(enemy, 10.0, ruby);
                    card(enemy, "duo_ruby_yang", false, 60);
                    w.playSound(enemy.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
                }
                if (flight < 12) ruby.setVelocity(dir.clone().multiply(1.2).setY(ruby.getVelocity().getY()));
            }
        }.runTaskTimer(CrossfightPlugin.get(), 1L, 1L);
        return true;
    }

    // ------------------------------------------------------------ Ruby + Blake

    /** Ladybug : les mains se joignent a HANDS (la flaque s'ouvre), elles s'enfoncent de SINK a SUNK. */
    static final int HANDS = 8, SINK = 12, SUNK = 30;

    /**
     * Blake apparait a cote de Ruby et lui tend la main ; Ruby la prend. Une flaque noire s'ouvre sous elles et
     * elles s'y enfoncent ensemble, main dans la main. Invisibles avec Vitesse III pendant 30 secondes.
     */
    static boolean ombreRouge(LivingEntity ruby, LivingEntity blake) {
        // L'invisibilite d'abord : les pantins qui s'enfoncent ne l'enleveront pas en finissant.
        for (LivingEntity p : new LivingEntity[]{ruby, blake}) {
            Effects.give(p, PotionEffectType.SPEED, 2, 20 * 30);
            Effects.give(p, PotionEffectType.INVISIBILITY, 0, 20 * 30);
            if (!(p instanceof Player pl)) continue;
            Stealth.hideFromAll(pl, "duo_rb", v -> Effects.isEnemy(pl, v));
            Stealth.hideArmor(pl);
            Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> { Stealth.reveal(pl, "duo_rb"); Stealth.showArmor(pl); }, 20L * 30);
        }
        World w = ruby.getWorld();
        final float yaw = sideBySide(ruby, blake, 1.4);
        final Location rl = ruby.getLocation(), bl = blake.getLocation();
        final Location mid = Glyph.ground(rl.clone().add(bl).multiply(0.5));
        final Puppet pr = puppet(ruby, false), pb = puppet(blake, false);
        if (pb != null) { pb.face(yaw); pb.pin(bl); pb.rest("blake_duo_fond"); pb.pose("blake_main_tendue", 4); pb.endAfter(SUNK, 1); }
        if (pr != null) { pr.face(yaw); pr.pin(rl); pr.rest("ruby_duo_fond"); pr.endAfter(SUNK, 1); }
        w.playSound(rl, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 0.8f);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                if (t == 4 && shown(ruby, pr)) pr.pose("ruby_main_donnee", 3);
                if (t == HANDS) {
                    Glyph puddle = Glyph.spawn(mid, "flaque_noire", Color.WHITE, 4.2f, Glyph.FLAT, new Vector3f());
                    if (puddle != null) {
                        puddle.spin(0.6f);
                        Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), puddle::close, SUNK - HANDS + 6L);
                    }
                    w.playSound(mid, Sound.ITEM_BUCKET_EMPTY, 0.8f, 0.6f);
                    w.spawnParticle(Particle.SQUID_INK, mid.clone().add(0, 0.1, 0), 24, 1.0, 0.05, 1.0, 0.02);
                }
                if (t == SINK) {
                    if (shown(ruby, pr)) pr.pose("ruby_duo_fond", 16);
                    if (shown(blake, pb)) pb.pose("blake_duo_fond", 16);
                    w.playSound(mid, Sound.BLOCK_HONEY_BLOCK_SLIDE, 1f, 0.5f);
                }
                if (t > SINK && t % 3 == 0) w.spawnParticle(Particle.SQUID_INK, mid.clone().add(0, 0.15, 0), 6, 0.9, 0.05, 0.9, 0.01);
                if (t >= SUNK) {
                    w.spawnParticle(Particle.SMOKE, mid.clone().add(0, 0.3, 0), 30, 0.9, 0.2, 0.9, 0.02);
                    cancel();
                }
            }
        }.runTaskTimer(CrossfightPlugin.get(), 1L, 1L);
        return true;
    }

    // ------------------------------------------------------------ Weiss + Yang

    /**
     * Freezerburn : le glyphe apparait a FB_GLYPH, Yang saute a FB_JUMP et retombe dessus a FB_LAND ; ses trois
     * coups suivent, un tous les FB_EVERY ticks. La zone dure 30 secondes.
     */
    static final int FB_GLYPH = 7, FB_JUMP = 9, FB_LAND = 22, FB_FIRST = 25, FB_EVERY = 7, FB_ZONE = 20 * 30;
    static final double FB_RADIUS = 6.0;
    static final Color FB_ICE = Color.fromRGB(150, 225, 255);

    /**
     * Weiss leve son epee puis la pointe : un grand glyphe se dessine au sol, sur l'adversaire le plus proche
     * (10 blocs) ou 4 blocs devant elle. Yang bondit dessus et le frappe du poing, trois fois : a chaque coup un
     * pic de glace en flammes jaillit autour d'elle. Sur le glyphe, la glace et le feu tourbillonnent 30 secondes :
     * les adversaires y sont ralentis et enflammes.
     */
    static boolean piquesDeGlace(LivingEntity weiss, LivingEntity yang) {
        World w = weiss.getWorld();
        List<LivingEntity> enemies = Effects.enemiesAround(weiss, 10);
        enemies.sort(java.util.Comparator.comparingDouble(e -> e.getLocation().distanceSquared(weiss.getLocation())));
        Vector look = weiss.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 0.01) look = new Vector(1, 0, 0);
        Location aim = enemies.isEmpty() ? weiss.getLocation().add(look.normalize().multiply(4)) : enemies.get(0).getLocation();
        int gy = Effects.groundAround(w, aim.getBlockX(), aim.getBlockZ(), aim.getBlockY());
        final Location center = new Location(w, aim.getBlockX() + 0.5, (gy == Integer.MIN_VALUE ? aim.getBlockY() - 1 : gy) + 1, aim.getBlockZ() + 0.5);
        // Les trois pics, en triangle autour de Yang.
        final List<Location> spots = new ArrayList<>();
        double a0 = Math.random() * Math.PI * 2;
        for (int i = 0; i < 3; i++) {
            double a = a0 + i * Math.PI * 2 / 3;
            int x = (int) Math.floor(center.getX() + Math.cos(a) * 3.5), z = (int) Math.floor(center.getZ() + Math.sin(a) * 3.5);
            int y = Effects.groundAround(w, x, z, center.getBlockY());
            spots.add(new Location(w, x, (y == Integer.MIN_VALUE ? center.getBlockY() - 1 : y) + 1, z));
        }
        final TempBlocks spikes = new TempBlocks();
        final Puppet pw = puppet(weiss, true);
        if (pw != null) { pw.face(yawTo(weiss.getLocation(), center)); pw.pose("weiss_epee_levee", 3); pw.endAfter(FB_GLYPH + 12, 4); }
        w.playSound(weiss.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
        new BukkitRunnable() {
            int t = 0;
            Glyph glyph;
            Puppet py;
            Location from;
            final int[] erupted = {-1, -1, -1};
            void finish() {
                spikes.restore();
                if (glyph != null) glyph.close();
                cancel();
            }
            @Override public void run() {
                t++;
                if (t == FB_GLYPH - 3 && shown(weiss, pw)) pw.pose("weiss_estoc", 2);
                if (t == FB_GLYPH) {
                    glyph = Glyph.spawn(Glyph.ground(center), Glyph.CERCLE, FB_ICE, (float) (FB_RADIUS * 2), Glyph.FLAT, new Vector3f());
                    if (glyph != null) glyph.spin(1.5f);
                    w.playSound(center, Sound.BLOCK_GLASS_PLACE, 1f, 0.5f);
                    w.playSound(center, Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 0.6f);
                    for (int k = 0; k < 40; k++) {
                        double a = k * Math.PI / 20;
                        w.spawnParticle(Particle.SNOWFLAKE, center.clone().add(Math.cos(a) * FB_RADIUS, 0.2, Math.sin(a) * FB_RADIUS), 2, 0.1, 0.1, 0.1, 0.01);
                    }
                }
                boolean yangThere = yang.isValid() && !yang.isDead();
                if (t == FB_JUMP && yangThere) {
                    from = yang.getLocation();
                    py = puppet(yang, false);
                    if (py != null) { py.face(yawTo(from, center)); py.pose("yang_saut_frappe", 3); py.endAfter(FB_FIRST + 2 * FB_EVERY + 8 - FB_JUMP, 4); }
                    if (yang instanceof Player yp) Effects.noFallUntil(yp, 4000);
                    w.playSound(from, Sound.ENTITY_BREEZE_JUMP, 1f, 0.9f);
                    w.spawnParticle(Particle.FLAME, from.clone().add(0, 0.2, 0), 20, 0.3, 0.1, 0.3, 0.05);
                }
                if (t >= FB_JUMP && t < FB_LAND && yangThere) {
                    // Le bond : une cloche, qui l'amene au centre du glyphe en FB_LAND - FB_JUMP ticks.
                    int k = t - FB_JUMP, left = FB_LAND - t;
                    Vector to = center.toVector().subtract(yang.getLocation().toVector()).setY(0);
                    Vector v = to.multiply(1.0 / left);
                    if (v.length() > 1.4) v.normalize().multiply(1.4);
                    v.setY(0.5 - 0.077 * k + (center.getY() - from.getY()) / (FB_LAND - FB_JUMP));
                    yang.setVelocity(v);
                }
                if (t == FB_LAND && yangThere) {
                    if (yang.getLocation().distanceSquared(center) > 1.5 * 1.5) {
                        Location land = center.clone();
                        land.setYaw(yang.getLocation().getYaw());
                        land.setPitch(yang.getLocation().getPitch());
                        yang.teleport(land);
                    }
                    yang.setVelocity(new Vector(0, 0, 0));
                    if (shown(yang, py)) { py.pin(center); py.pose("yang_frappe_sol_d", 2); }
                }
                for (int i = 0; i < 3; i++) {
                    int at = FB_FIRST + i * FB_EVERY;
                    if (i > 0 && t == at - 3 && shown(yang, py)) py.pose(i % 2 == 0 ? "yang_frappe_sol_d" : "yang_frappe_sol_g", 2);
                    if (t != at) continue;
                    // Le coup : l'onde part du poing, le pic jaillit.
                    Location fist = shown(yang, py) ? py.armPoint(i % 2 == 0, 1.0) : center.clone().add(0, 0.2, 0);
                    w.spawnParticle(Particle.EXPLOSION, fist, 1);
                    w.spawnParticle(Particle.FLAME, fist, 40, 0.1, 0.05, 0.1, 0.18);
                    w.spawnParticle(Particle.SNOWFLAKE, fist, 40, 0.1, 0.05, 0.1, 0.2);
                    w.playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.3f + 0.15f * i);
                    w.playSound(center, Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
                    erupted[i] = t;
                    Location foot = spots.get(i).clone().add(0.5, 0, 0.5);
                    // Le pic en flammes : un feu au pied de chacune de ses faces, un autre a sa pointe.
                    int burn = FB_FIRST + FB_ZONE - t;
                    for (int[] side : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) Flames.pillar(foot.clone().add(side[0], 0, side[1]), 1.0f, 1.5f, burn);
                    Flames.pillar(foot.clone().add(0, 5, 0), 0.9f, 1.3f, burn);
                    w.spawnParticle(Particle.SNOWFLAKE, foot.clone().add(0, 2, 0), 40, 1, 2, 1, 0.05);
                    w.spawnParticle(Particle.FLAME, foot.clone().add(0, 2, 0), 40, 1, 2, 1, 0.05);
                    w.playSound(foot, Sound.BLOCK_GLASS_PLACE, 1f, 0.5f);
                }
                // Chaque pic monte d'un etage par tick : la base de 3x3, le coeur en croix, puis la pointe.
                for (int i = 0; i < 3; i++) {
                    if (erupted[i] < 0) continue;
                    int layer = t - erupted[i];
                    Location c = spots.get(i);
                    if (layer == 0 || layer == 1) {
                        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) spikes.place(c.clone().add(dx, layer, dz).getBlock(), Material.PACKED_ICE);
                    } else if (layer == 2) {
                        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx == 0 || dz == 0) spikes.place(c.clone().add(dx, 2, dz).getBlock(), Material.BLUE_ICE);
                    } else if (layer == 3) {
                        spikes.place(c.clone().add(0, 3, 0).getBlock(), Material.BLUE_ICE);
                    } else if (layer == 4) {
                        spikes.place(c.clone().add(0, 4, 0).getBlock(), Material.PACKED_ICE);
                    }
                }
                if (t < FB_FIRST) return;
                if (t >= FB_FIRST + FB_ZONE) { finish(); return; }
                // La zone : la glace et le feu tourbillonnent sur le glyphe ; les adversaires y sont ralentis et enflammes.
                if (t % 2 == 0) {
                    for (int k = 0; k < 5; k++) {
                        double a = Math.random() * Math.PI * 2, r = Math.sqrt(Math.random()) * FB_RADIUS;
                        Location at = center.clone().add(Math.cos(a) * r, 0.2 + Math.random() * 2.2, Math.sin(a) * r);
                        w.spawnParticle(k % 2 == 0 ? Particle.SNOWFLAKE : Particle.FLAME, at, 1, 0.1, 0.1, 0.1, 0.02);
                    }
                    double a = t * 0.12;
                    for (int k = 0; k < 2; k++) {
                        Location rim = center.clone().add(Math.cos(a + k * Math.PI) * FB_RADIUS, 0.3, Math.sin(a + k * Math.PI) * FB_RADIUS);
                        w.spawnParticle(k == 0 ? Particle.SNOWFLAKE : Particle.SMALL_FLAME, rim, 2, 0.1, 0.2, 0.1, 0.01);
                    }
                }
                if ((t - FB_FIRST) % 20 == 0) {
                    for (LivingEntity enemy : Effects.enemiesAround(weiss, center.clone().add(0, 1, 0), FB_RADIUS)) {
                        enemy.setFireTicks(Math.max(enemy.getFireTicks(), 100));
                        Effects.give(enemy, PotionEffectType.SLOWNESS, 0, 100);
                        // Sa carte rouge, tant qu'il reste sur le glyphe.
                        card(enemy, "duo_weiss_yang", false, 40);
                    }
                }
            }
        }.runTaskTimer(CrossfightPlugin.get(), 1L, 1L);
        return true;
    }

    // ----------------------------------------------------------- Weiss + Blake

    /** Checkmate : le glyphe a CM_GLYPH, le saut a CM_JUMP, la flaque quand elles retombent (CM_LAND), englouties a CM_SUNK. */
    static final int CM_GLYPH = 5, CM_JUMP = 10, CM_LAND = 17, CM_SINK = 19, CM_SUNK = 35;

    /**
     * Blake apparait a cote de Weiss, qui leve son epee : un glyphe se dessine sous elles deux. Elles sautent
     * dessus en meme temps ; quand elles retombent, une enorme flaque noire s'ouvre et les engloutit, puis se
     * referme avec le glyphe. Invisibles 10 secondes ; chaque adversaire touche est immobilise 5 secondes.
     */
    static boolean gelSilencieux(LivingEntity weiss, LivingEntity blake) {
        long until = System.currentTimeMillis() + 10_000L;
        // L'invisibilite d'abord : les pantins qui s'enfoncent ne l'enleveront pas en finissant.
        for (LivingEntity p : new LivingEntity[]{weiss, blake}) {
            FREEZING_HITS.put(p.getUniqueId(), until);
            Effects.give(p, PotionEffectType.INVISIBILITY, 0, 20 * 10);
            if (p instanceof Player pl) {
                Stealth.hideFromAll(pl, "duo_wb", v -> Effects.isEnemy(pl, v));
                Stealth.hideArmor(pl);
            }
            Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> {
                if (p instanceof Player pl) { Stealth.reveal(pl, "duo_wb"); Stealth.showArmor(pl); }
                FREEZING_HITS.remove(p.getUniqueId());
            }, 20L * 10);
        }
        World w = weiss.getWorld();
        final float yaw = sideBySide(weiss, blake, 1.4);
        final Location wl = weiss.getLocation(), bl = blake.getLocation();
        final Location mid = Glyph.ground(wl.clone().add(bl).multiply(0.5));
        final Puppet pw = puppet(weiss, true), pb = puppet(blake, false);
        if (pw != null) { pw.face(yaw); pw.pin(wl); pw.rest("blake_enfonce_fond"); pw.pose("weiss_epee_levee", 3); pw.endAfter(CM_SUNK, 1); }
        if (pb != null) { pb.face(yaw); pb.pin(bl); pb.rest("blake_enfonce_fond"); pb.endAfter(CM_SUNK, 1); }
        w.playSound(wl, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 0.6f);
        new BukkitRunnable() {
            int t = 0;
            Glyph glyph, puddle;
            void both(String pose, int ticks) {
                if (shown(weiss, pw)) pw.pose(pose, ticks);
                if (shown(blake, pb)) pb.pose(pose, ticks);
            }
            @Override public void run() {
                t++;
                if (t == CM_GLYPH) {
                    glyph = Glyph.spawn(mid, Glyph.CERCLE, Color.fromRGB(225, 240, 255), 5f, Glyph.FLAT, new Vector3f());
                    if (glyph != null) glyph.spin(2.5f);
                    w.playSound(mid, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.7f);
                    w.playSound(mid, Sound.BLOCK_GLASS_PLACE, 0.8f, 0.6f);
                    w.spawnParticle(Particle.SNOWFLAKE, mid.clone().add(0, 0.2, 0), 40, 1.6, 0.05, 1.6, 0.01);
                }
                if (t == CM_JUMP) { both("duo_saut", 4); w.playSound(mid, Sound.ENTITY_BREEZE_JUMP, 0.7f, 1.2f); }
                if (t == CM_JUMP + 4) both("duo_atterri", 3);
                if (t == CM_LAND) {
                    // Elles retombent : la flaque jaillit du glyphe et le recouvre.
                    puddle = Glyph.spawn(mid.clone().add(0, 0.02, 0), "flaque_noire", Color.WHITE, 9f, Glyph.FLAT, new Vector3f());
                    if (puddle != null) puddle.spin(0.6f);
                    w.playSound(mid, Sound.ITEM_BUCKET_EMPTY, 1f, 0.5f);
                    w.playSound(mid, Sound.ENTITY_GENERIC_SPLASH, 0.8f, 0.5f);
                    for (int k = 0; k < 24; k++) {
                        double a = k * Math.PI / 12;
                        w.spawnParticle(Particle.SQUID_INK, mid.clone().add(Math.cos(a) * 1.2, 0.15, Math.sin(a) * 1.2), 0, Math.cos(a), 0.15, Math.sin(a), 0.25);
                    }
                }
                if (t == CM_SINK) { both("blake_enfonce_fond", 14); w.playSound(mid, Sound.BLOCK_HONEY_BLOCK_SLIDE, 1f, 0.5f); }
                if (t > CM_SINK && t % 3 == 0) w.spawnParticle(Particle.SQUID_INK, mid.clone().add(0, 0.15, 0), 8, 2.0, 0.05, 2.0, 0.01);
                if (t >= CM_SUNK + 4) {
                    // Englouties : la flaque se referme, et le glyphe avec elle.
                    if (puddle != null) puddle.close();
                    if (glyph != null) glyph.close();
                    w.spawnParticle(Particle.SMOKE, mid.clone().add(0, 0.3, 0), 40, 2.0, 0.2, 2.0, 0.02);
                    w.playSound(mid, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 0.6f);
                    cancel();
                }
            }
        }.runTaskTimer(CrossfightPlugin.get(), 1L, 1L);
        return true;
    }

    /** Appele sur les degats : si l'attaquant est sous Gel Silencieux, la victime est immobilisee. */
    static void onFreezingHit(Player attacker, LivingEntity victim) {
        Long until = FREEZING_HITS.get(attacker.getUniqueId());
        if (until == null || System.currentTimeMillis() > until) return;
        Effects.root(victim, 100);
        card(victim, "duo_weiss_blake", false, 100);
        victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0), 25, 0.4, 0.6, 0.4, 0.02);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_HURT_FREEZE, 1f, 1f);
        Effects.say(victim, "<aqua>Gele sur place !");
    }

    // ------------------------------------------------------------ Blake + Yang

    /** Bumblebee : la main de Blake touche le dos de Yang a BB_TOUCH ; les clones sortent un par un, puis s'elancent a BB_RUN. */
    static final int BB_TOUCH = 6, BB_FIRST = 8, BB_EVERY = 4, BB_RUN = 22;
    static final Particle.DustOptions SHADOW = new Particle.DustOptions(Color.fromRGB(95, 40, 150), 1.4f);

    /**
     * Blake apparait derriere Yang et pose la main a plat sur son dos : son ombre la traverse, et trois clones de
     * Yang sortent d'elle, a sa gauche, a sa droite et devant. Ils foncent sur les trois ennemis les plus proches
     * et explosent dans tous les cas (3 coeurs a 3 blocs) : en l'atteignant, quand on les frappe, ou au bout de 20 secondes.
     */
    /** L'explosion d'un clone de Yang : 3 coeurs et le feu aux adversaires a 3 blocs, et a celui qui l'a frappe ou qu'il soit. */
    static void explode(LivingEntity yang, Location at, LivingEntity attacker) {
        World w = at.getWorld();
        w.spawnParticle(Particle.EXPLOSION, at, 2);
        w.spawnParticle(Particle.FLAME, at, 40, 0.6, 0.6, 0.6, 0.05);
        w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
        java.util.Set<LivingEntity> hit = new java.util.LinkedHashSet<>(Effects.enemiesAround(yang, at, 3.0));
        if (attacker != null) hit.add(attacker);
        for (LivingEntity e : hit) {
            if (Clones.isClone(e)) continue;
            Effects.damage(e, 6.0, yang);
            e.setFireTicks(Math.max(e.getFireTicks(), 60));
            card(e, "duo_blake_yang", false, 60);
        }
    }

    static boolean leurresExplosifs(LivingEntity yang, LivingEntity blake) {
        Player owner = Effects.sideOf(yang);
        if (owner == null) return false;
        List<LivingEntity> enemies = Effects.enemiesAround(yang, 40);
        enemies.sort(java.util.Comparator.comparingDouble(e -> e.getLocation().distanceSquared(yang.getLocation())));
        World w = yang.getWorld();
        final Location yl = yang.getLocation();
        final float yaw = yl.getYaw();
        double r = Math.toRadians(yaw);
        final Vector fwd = new Vector(-Math.sin(r), 0, Math.cos(r)), left = new Vector(Math.cos(r), 0, Math.sin(r));
        Location behind = yl.clone().subtract(fwd.clone().multiply(0.8));
        behind.setYaw(yaw);
        behind.setPitch(0);
        if (standable(behind)) {
            w.spawnParticle(Particle.SMOKE, blake.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
            blake.teleport(behind);
            w.spawnParticle(Particle.SMOKE, behind.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.02);
        }
        final Location bl = blake.getLocation();
        final Puppet py = puppet(yang, false), pb = puppet(blake, false);
        if (py != null) { py.face(yaw); py.pin(yl); py.pose("yang_garde", 3); py.endAfter(BB_RUN, 4); }
        if (pb != null) { pb.face(yawTo(bl, yl)); pb.pin(bl); pb.pose("blake_main_dos", 4); pb.endAfter(BB_RUN, 4); }
        final Location[] spots = {yl.clone().add(left.clone().multiply(1.4)), yl.clone().subtract(left.clone().multiply(1.4)), yl.clone().add(fwd.clone().multiply(1.4))};
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                if (!yang.isValid() || yang.isDead()) { cancel(); return; }
                Location back = yl.clone().add(0, 1.2, 0).subtract(fwd.clone().multiply(0.25));
                if (t == BB_TOUCH) {
                    // La main sur son dos : l'ombre de Blake la traverse.
                    w.spawnParticle(Particle.DUST, back, 30, 0.25, 0.4, 0.25, 0, SHADOW);
                    w.spawnParticle(Particle.SQUID_INK, back, 10, 0.2, 0.3, 0.2, 0.03);
                    w.spawnParticle(Particle.FLAME, yl.clone().add(0, 1, 0), 24, 0.35, 0.6, 0.35, 0.03);
                    w.playSound(yl, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.2f);
                    w.playSound(yl, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);
                }
                for (int i = 0; i < 3; i++) {
                    if (t != BB_FIRST + i * BB_EVERY) continue;
                    LivingEntity target = i < enemies.size() ? enemies.get(i) : (enemies.isEmpty() ? null : enemies.get(i % enemies.size()));
                    // Un clone explose dans tous les cas : frappe, arrive sur sa cible, ou au bout de 20 secondes.
                    LivingEntity clone = Clones.spawn(owner, yang, target, 20 * 20, (c, attacker) -> explode(yang, c.getLocation().add(0, 1, 0), attacker), BB_RUN - t);
                    Clones.onEnd(clone, c -> explode(yang, c.getLocation().add(0, 1, 0), null));
                    if (target == null && clone != null) {
                        // Personne a viser : il reste sur place, puis explose.
                        Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> {
                            if (!clone.isValid()) return;
                            explode(yang, clone.getLocation().add(0, 1, 0), null);
                            Clones.remove(clone);
                        }, BB_RUN - t + 60L);
                    }
                    // Le clone sort d'elle : une trainee d'ombre de son dos jusqu'a sa place.
                    Location spot = standable(spots[i]) ? spots[i].clone() : yl.clone();
                    spot.setYaw(yaw);
                    spot.setPitch(0);
                    if (clone != null) clone.teleport(spot);
                    Vector line = spot.toVector().subtract(yl.toVector());
                    for (double d = 0; d <= 1.0; d += 0.12) {
                        Location at = yl.clone().add(line.clone().multiply(d)).add(0, 1.0, 0);
                        w.spawnParticle(Particle.DUST, at, 3, 0.15, 0.4, 0.15, 0, SHADOW);
                    }
                    w.spawnParticle(Particle.LARGE_SMOKE, spot.clone().add(0, 1, 0), 12, 0.3, 0.6, 0.3, 0.01);
                    w.spawnParticle(Particle.FLAME, spot.clone().add(0, 1, 0), 10, 0.3, 0.6, 0.3, 0.02);
                    w.playSound(spot, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.8f, 1.4f + 0.2f * i);
                }
                if (t >= BB_RUN) cancel();
            }
        }.runTaskTimer(CrossfightPlugin.get(), 1L, 1L);
        return true;
    }
}
