package com.kiokouwhite.crossfight.character.fireemblem;

import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.ArenaWorlds;
import com.kiokouwhite.crossfight.util.Boosts;
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
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Byleth - Path of Harmony.
 */
public final class Byleth extends Character implements Listener {

    static final String DIMENSION_WORLD = "crossfight_byleth";
    static final String SWORD_ID = "byleth_sword";

    public Byleth() {
        super("byleth", "Byleth", Voie.HARMONIE, Material.DIAMOND_SWORD);
        addAbility(new EpeeDuCreateur());
        addAbility(new EmblemeDeFeu());
        addAbility(new Tutorat());
        addAbility(new DechainementDEpine());
        setUltimate(new Tenebres());
    }

    @Override
    public ItemStack createWeapon() {
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>Actif");
        lore.addAll(Items.wrap("Byleth se fige et son epee s'etend tout droit sur 10 blocs (8 blocs/s) pour ramener le joueur attrape."));
        lore.add("");
        lore.add("<yellow>Temps de chargement : <white>00:10");
        ItemStack sword = Weapons.melee(Material.DIAMOND_SWORD, 8, "<gold><bold>Epee du Createur", lore);
        Items.tag(sword, AbilityEngine.ABILITY_KEY, SWORD_ID);
        return sword;
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Epee du Createur (8) : fouet de 10 blocs",
                "<white>Embleme de Feu : vol de vie 25 %, allies 10 %",
                "<white>Tutorat : fait progresser un allie",
                "<white>Dechainement d'Epine : zone qui ejecte, 3 coeurs",
                "<light_purple>Ultime : emprisonne les ennemis dans une dimension");
    }

    public static World dimension() {
        World w = ArenaWorlds.get(DIMENSION_WORLD);
        if (w.getBlockAt(0, ArenaWorlds.FLOOR_Y, 0).getType() == Material.AIR) {
            ArenaWorlds.buildPlatform(w, 20, Material.OBSIDIAN, Material.BARRIER, 10);
            // Plafond pour empecher de construire vers le haut.
            for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++) w.getBlockAt(x, ArenaWorlds.FLOOR_Y + 11, z).setType(Material.BARRIER, false);
        }
        return w;
    }

    @EventHandler
    public void onConsume(PlayerItemConsumeEvent e) {
        Material m = e.getItem().getType();
        if (m != Material.GOLDEN_APPLE && m != Material.ENCHANTED_GOLDEN_APPLE) return;
        Player p = e.getPlayer();
        if (!p.getWorld().getName().equals(DIMENSION_WORLD)) return;
        CfPlayer cf = plugin().players().get(p);
        if (cf.character() != null && "byleth".equals(cf.character().id())) {
            e.setCancelled(true);
            Msg.actionBar(p, "<red>Impossible de manger une pomme doree ici.");
        }
    }

    private static com.kiokouwhite.crossfight.CrossfightPlugin plugin() {
        return com.kiokouwhite.crossfight.CrossfightPlugin.get();
    }

    // ------------------------------------------------------------- fouet d'os

    /** Chaine de segments d'os (BlockDisplay) alignee sur une direction. */
    static final class BoneWhip {
        static final double SEGMENT = 0.5;
        private final List<BlockDisplay> bones = new ArrayList<>();
        private final boolean glowing;

        BoneWhip(boolean glowing) { this.glowing = glowing; }

        void layout(Location origin, Vector dir, double len) {
            int needed = (int) Math.ceil(len / SEGMENT);
            while (bones.size() < needed) bones.add(spawn(origin));
            while (bones.size() > needed) bones.remove(bones.size() - 1).remove();
            var rot = rotationFor(dir);
            for (int i = 0; i < bones.size(); i++) {
                Location l = origin.clone().add(dir.clone().multiply(0.6 + i * SEGMENT)).subtract(0, 0.25, 0);
                BlockDisplay b = bones.get(i);
                b.teleport(l);
                b.setTransformation(transformFor(rot));
            }
        }

        /** Position d'un point du fouet a une distance donnee. */
        static Location pointAt(Location origin, Vector dir, double d) {
            return origin.clone().add(dir.clone().multiply(d)).subtract(0, 0.25, 0);
        }

        void clear() {
            for (BlockDisplay b : bones) b.remove();
            bones.clear();
        }

        private BlockDisplay spawn(Location at) {
            return at.getWorld().spawn(at, BlockDisplay.class, d -> {
                d.setBlock(Material.BONE_BLOCK.createBlockData());
                d.setTeleportDuration(1);
                d.setInterpolationDuration(1);
                d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
                d.setPersistent(false);
                if (glowing) {
                    d.setGlowing(true);
                    d.setGlowColorOverride(org.bukkit.Color.fromRGB(255, 40, 60));
                }
            });
        }

        /** Oriente l'axe Z local du bloc vers la direction donnee. */
        private static org.joml.Quaternionf rotationFor(Vector dir) {
            float yaw = (float) Math.atan2(dir.getX(), dir.getZ());
            float pitch = (float) -Math.asin(Math.max(-1, Math.min(1, dir.getY())));
            return new org.joml.Quaternionf().rotationY(yaw).rotateX(pitch);
        }

        private static org.bukkit.util.Transformation transformFor(org.joml.Quaternionf rot) {
            org.joml.Vector3f scale = new org.joml.Vector3f(0.22f, 0.22f, (float) SEGMENT + 0.05f);
            org.joml.Vector3f offset = new org.joml.Vector3f(-scale.x / 2, -scale.y / 2, -scale.z / 2);
            rot.transform(offset);
            return new org.bukkit.util.Transformation(offset, new org.joml.Quaternionf(rot), scale, new org.joml.Quaternionf());
        }
    }

    // ------------------------------------------------------------------ arme

    static final class EpeeDuCreateur extends ActiveAbility {
        EpeeDuCreateur() {
            super(SWORD_ID, "Epee du Createur", "Byleth se fige et son epee s'etend tout droit sur 10 blocs (8 blocs/s) pour ramener le joueur attrape.", Material.DIAMOND_SWORD, 10);
        }

        @Override public boolean hasItem() { return false; }

        /** Avancee par tick : 8 blocs par seconde. */
        private static final double SPEED_PER_TICK = 0.4;

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            p.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 1.5f);
            // Byleth est immobilisee tant que le fouet est sorti (au plus 3 secondes de securite).
            Effects.root(p, 60);
            BoneWhip whip = new BoneWhip(false);
            // Direction figee au lancement : Byleth est verrouillee, le fouet ne suit plus le regard.
            final Vector lockedDir = p.getEyeLocation().getDirection().normalize();
            new BukkitRunnable() {
                double length = 0;
                boolean retracting = false;

                @Override public void run() {
                    if (!p.isOnline() || p.isDead()) { clear(); cancel(); return; }
                    if (retracting) {
                        length -= 1.5;
                        if (length <= 0) { clear(); cancel(); return; }
                        layout(p, length);
                        return;
                    }
                    length += SPEED_PER_TICK;
                    Location eye = p.getEyeLocation();
                    Vector dir = lockedDir;
                    // Le fouet s'arrete sur un mur.
                    for (double d = 0.5; d <= length; d += 0.5) {
                        if (!eye.clone().add(dir.clone().multiply(d)).getBlock().isPassable()) {
                            length = Math.max(0.5, d - 0.5);
                            retracting = true;
                            break;
                        }
                    }
                    layout(p, length);
                    Location tip = eye.clone().add(dir.clone().multiply(length));
                    for (org.bukkit.entity.LivingEntity enemy : Effects.enemiesWithin(p, tip, 1.2)) {
                        Vector pull = p.getLocation().toVector().subtract(enemy.getLocation().toVector());
                        double dist = pull.length();
                        pull.normalize().multiply(Math.min(2.5, 0.4 + dist * 0.22)).setY(0.35);
                        enemy.setVelocity(pull);
                        enemy.getWorld().playSound(enemy.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.7f);
                        enemy.getWorld().spawnParticle(Particle.CRIT, enemy.getLocation().add(0, 1, 0), 15, 0.3, 0.4, 0.3, 0.1);
                        if (enemy instanceof Player ep) Msg.actionBar(ep, "<gold>Byleth t'attrape avec son epee !");
                        Msg.actionBar(p, "<gold>Attrape !");
                        retracting = true;
                        return;
                    }
                    if (length >= 10) retracting = true;
                }

                void layout(Player p, double len) {
                    whip.layout(p.getEyeLocation(), lockedDir, len);
                }

                void clear() {
                    whip.clear();
                    Effects.unroot(p);
                }
            }.runTaskTimer(plugin(), 0L, 1L);
            return true;
        }
    }

    // --------------------------------------------------------------- pouvoirs

    static final class EmblemeDeFeu extends PassiveAbility {
        EmblemeDeFeu() {
            super("byleth_embleme", "Embleme de Feu", "Byleth regenere 25 % des degats qu'elle inflige, ses allies a moins de 10 blocs 10 %.", Material.FIRE_CHARGE);
        }

        @Override
        public void onDealDamage(Player attacker, org.bukkit.entity.LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) {
            double dmg = e.getFinalDamage();
            if (dmg <= 0) return;
            Bukkit.getScheduler().runTask(plugin(), () -> {
                Effects.heal(attacker, dmg * 0.25);
                for (Player ally : Effects.alliesWithin(attacker, 10)) Effects.heal(ally, dmg * 0.10);
            });
        }
    }

    /**
     * Tutorat : Byleth choisit une categorie pour l'allie le plus proche, qui gagne
     * 50 a 90 XP. A 100 XP, un niveau (max 4 par categorie).
     *  - Maitrise d'arme : +0.2 degats par niveau
     *  - Entrainement physique : +0.2 Force, Resistance ou Vitesse par niveau
     *  - Amelioration de capacite : -5 % de temps de chargement par niveau
     */
    static final class Tutorat extends ActiveAbility {

        static final class Progress {
            final int[] level = new int[3];
            final double[] xp = new double[3];
            double force, speed, resistance, weapon;
        }

        private final Map<UUID, Progress> progress = new HashMap<>();
        private static final String[] CATEGORIES = {"Maitrise d'arme", "Entrainement physique", "Amelioration de capacite"};

        Tutorat() {
            super("byleth_tutorat", "Tutorat", "Fait progresser l'allie le plus proche dans une categorie : arme, physique ou capacites.",
                    Material.WRITABLE_BOOK, 15 * 60);
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Player ally = Effects.nearestAlly(p, 10);
            if (ally == null) {
                Msg.actionBar(p, "<red>Aucun allie a moins de 10 blocs.");
                return false;
            }
            Progress pr = progress.computeIfAbsent(ally.getUniqueId(), u -> new Progress());
            List<ChoiceMenu.Option> options = new ArrayList<>();
            Material[] icons = {Material.IRON_SWORD, Material.LEATHER_CHESTPLATE, Material.CLOCK};
            String[] desc = {"+0.2 degats par niveau", "+0.2 Force, Resistance ou Vitesse par niveau", "-5 % de temps de chargement par niveau"};
            for (int i = 0; i < 3; i++) {
                final int cat = i;
                options.add(new ChoiceMenu.Option(icons[i], CATEGORIES[i], List.of(
                        "<gray>" + desc[i],
                        "<white>Niveau " + pr.level[i] + "/4 <gray>(" + (int) pr.xp[i] + " XP)"),
                        () -> teach(p, ally, pr, cat, st)));
            }
            ChoiceMenu.open(p, "<dark_gray>Tutorat : " + ally.getName(), options,
                    pl -> { st.clearCooldown(); Msg.actionBar(pl, "<gray>Tutorat annule."); });
            // Le temps de chargement ne part que si une categorie est choisie.
            return true;
        }

        private void teach(Player byleth, Player ally, Progress pr, int cat, AbilityState st) {
            if (pr.level[cat] >= 4) {
                Msg.send(byleth, "<red>" + ally.getName() + " est deja au niveau maximum en " + CATEGORIES[cat] + ".");
                st.clearCooldown();
                return;
            }
            int gain = ThreadLocalRandom.current().nextInt(50, 91);
            pr.xp[cat] += gain;
            String bonusText = "";
            while (pr.xp[cat] >= 100 && pr.level[cat] < 4) {
                pr.xp[cat] -= 100;
                pr.level[cat]++;
                switch (cat) {
                    case 0 -> { pr.weapon += 0.2; bonusText = "<gold>+0.2 degats"; }
                    case 1 -> {
                        int roll = ThreadLocalRandom.current().nextInt(3);
                        if (roll == 0) { pr.force += 0.2; bonusText = "<red>+0.2 Force"; }
                        else if (roll == 1) { pr.resistance += 0.2; bonusText = "<gray>+0.2 Resistance"; }
                        else { pr.speed += 0.2; bonusText = "<aqua>+0.2 Vitesse"; }
                    }
                    default -> {
                        CfPlayer acf = plugin().players().get(ally);
                        acf.setCooldownFactor(acf.cooldownFactor() * 0.95);
                        bonusText = "<yellow>-5 % de temps de chargement";
                    }
                }
            }
            Boosts.set(ally, "tutorat", new Boosts.Boost(pr.force, pr.speed, pr.resistance, pr.weapon));
            Msg.send(byleth, "<gold>Tutorat : " + ally.getName() + " gagne " + gain + " XP en " + CATEGORIES[cat]
                    + " <gray>(niveau " + pr.level[cat] + ", " + (int) pr.xp[cat] + " XP)");
            Msg.send(ally, "<gold>Byleth t'enseigne " + CATEGORIES[cat] + " : +" + gain + " XP"
                    + (bonusText.isEmpty() ? "" : " <gray>- niveau " + pr.level[cat] + " ! " + bonusText));
            ally.playSound(ally.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        }

        @Override
        public void onEquip(Player p, CfPlayer cf) {
            // Les allies gardent leurs bonus de Tutorat apres une mort.
            for (Player ally : Bukkit.getOnlinePlayers()) {
                Progress pr = progress.get(ally.getUniqueId());
                if (pr != null) Boosts.set(ally, "tutorat", new Boosts.Boost(pr.force, pr.speed, pr.resistance, pr.weapon));
            }
        }
    }

    static final class DechainementDEpine extends ActiveAbility {
        DechainementDEpine() {
            super("byleth_epine", "Dechainement d'Epine", "Byleth balaie un grand arc avec son epee-fouet : les ennemis dans la zone de 5 blocs devant elle sont ejectes a 10 blocs et perdent 3 coeurs.",
                    Material.SWEET_BERRIES, 20);
        }

        // Chronologie (ticks) : preparation 0-7, ruee a 8, balayage 8-16, explosion a 17, fin a 24.
        private static final int WINDUP_END = 8;
        private static final int SWEEP_END = 16;
        private static final int BLAST_TICK = 17;
        private static final int TOTAL = 24;
        private static final double WHIP_LENGTH = 6.0;
        private static final double HALF_ARC = Math.toRadians(100);
        private static final Particle.DustOptions RED = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 30, 50), 1.8f);
        private static final Particle.DustOptions PINK = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 110, 160), 1.3f);
        private static final Particle.DustOptions MIST = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 150, 190), 3.5f);
        private static final Particle.DustTransition FLASH = new Particle.DustTransition(
                org.bukkit.Color.fromRGB(255, 235, 200), org.bukkit.Color.fromRGB(255, 50, 70), 2.6f);

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Vector forward = p.getLocation().getDirection().setY(0);
            if (forward.lengthSquared() < 0.01) forward = new Vector(1, 0, 0);
            forward.normalize();
            final Vector fwd = forward;
            final double baseAngle = Math.atan2(fwd.getZ(), fwd.getX());
            final Location center = p.getLocation().add(fwd.clone().multiply(3.5));
            final double groundY = p.getLocation().getY();
            final List<java.util.UUID> hit = new ArrayList<>();

            Effects.root(p, TOTAL + 5);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 0.8f, 1.4f);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.8f);
            BoneWhip whip = new BoneWhip(true);

            new BukkitRunnable() {
                int tick = 0;

                @Override public void run() {
                    if (!p.isOnline() || p.isDead() || tick > TOTAL) { whip.clear(); Effects.unroot(p); cancel(); return; }
                    int current = tick++;
                    try {
                        World w = p.getWorld();
                        if (current < WINDUP_END) windup(w, current);
                        else if (current <= SWEEP_END) sweep(w, current);
                        else if (current == BLAST_TICK) blast(w);
                        else aftermath(w, current);
                    } catch (Exception ex) {
                        plugin().getLogger().warning("Dechainement d'Epine interrompu : " + ex);
                        whip.clear();
                        Effects.unroot(p);
                        cancel();
                    }
                }

                /** Fouet court tenu bas devant elle, etincelles blanches a la pointe. */
                void windup(World w, int tick) {
                    Location origin = p.getEyeLocation().subtract(0, 0.6, 0);
                    Vector dir = fwd.clone().setY(-0.45).normalize();
                    whip.layout(origin, dir, 2.5 + tick * 0.15);
                    Location tip = BoneWhip.pointAt(origin, dir, 2.5 + tick * 0.15);
                    w.spawnParticle(Particle.END_ROD, tip, 4, 0.25, 0.25, 0.25, 0.03);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, tip, 6, 0.4, 0.3, 0.4, 0.1);
                    w.spawnParticle(Particle.DUST, tip, 3, 0.15, 0.15, 0.15, 0, PINK);
                    if (tick == WINDUP_END - 1) {
                        // Ruee en avant d'environ deux blocs.
                        p.setVelocity(fwd.clone().multiply(0.85).setY(0.12));
                        w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f);
                        w.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f);
                        w.spawnParticle(Particle.FLASH, p.getEyeLocation().add(fwd), 1, 0, 0, 0, 0, org.bukkit.Color.fromRGB(255, 200, 220));
                    }
                }

                /** Grand coup horizontal au ras du sol, vague rouge et poussiere blanche. */
                void sweep(World w, int tick) {
                    double t = (double) (tick - WINDUP_END) / (SWEEP_END - WINDUP_END);
                    double eased = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
                    double angle = baseAngle - HALF_ARC + eased * 2 * HALF_ARC;
                    Vector dir = new Vector(Math.cos(angle), -0.12, Math.sin(angle)).normalize();
                    Location origin = p.getLocation().add(0, 1.0, 0);
                    whip.layout(origin, dir, WHIP_LENGTH);

                    // La vague : tout l'arc deja balaye reste visible au sol, en rouge vers l'exterieur.
                    for (double a = baseAngle - HALF_ARC; a <= angle; a += Math.toRadians(9)) {
                        for (double r = 1.5; r <= WHIP_LENGTH; r += 1.5) {
                            Location l = new Location(w, p.getLocation().getX() + Math.cos(a) * r, groundY + 0.4 + Math.random() * 0.5,
                                    p.getLocation().getZ() + Math.sin(a) * r);
                            w.spawnParticle(Particle.DUST, l, 1, 0.1, 0.1, 0.1, 0, r > 3.5 ? RED : PINK);
                        }
                    }
                    // Brume rose qui monte et nuage de poussiere blanche sur le bord du fouet.
                    for (double r = 2.0; r <= WHIP_LENGTH; r += 1.0) {
                        Location l = BoneWhip.pointAt(origin, dir, r);
                        l.setY(groundY + 0.3);
                        w.spawnParticle(Particle.DUST, l, 2, 0.5, 0.4, 0.5, 0, MIST);
                        w.spawnParticle(Particle.CLOUD, l, 2, 0.3, 0.1, 0.3, 0.02);
                    }
                    Location tip = BoneWhip.pointAt(origin, dir, WHIP_LENGTH);
                    w.spawnParticle(Particle.SWEEP_ATTACK, tip, 1);
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, tip, 3, 0.2, 0.2, 0.2, 0, FLASH);
                    if ((tick - WINDUP_END) % 3 == 0) w.playSound(tip, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 0.7f);
                }

                /** Explosion rose-rouge au coeur de la zone : degats et projection. */
                void blast(World w) {
                    whip.clear();
                    Location c = center.clone().add(0, 1.0, 0);
                    w.spawnParticle(Particle.FLASH, c, 2, 0, 0, 0, 0, org.bukkit.Color.fromRGB(255, 90, 120));
                    w.spawnParticle(Particle.EXPLOSION, c, 2, 0.6, 0.3, 0.6, 0);
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, c, 120, 2.2, 1.0, 2.2, 0, FLASH);
                    w.spawnParticle(Particle.DUST, c, 60, 2.5, 1.2, 2.5, 0, MIST);
                    w.spawnParticle(Particle.CRIT, c, 50, 2.0, 1.0, 2.0, 0.3);
                    w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
                    w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
                    w.playSound(c, Sound.ITEM_TRIDENT_THUNDER, 0.6f, 1.6f);
                    for (org.bukkit.entity.LivingEntity enemy : Effects.enemiesWithin(p, center, 2.5)) {
                        if (hit.contains(enemy.getUniqueId())) continue;
                        hit.add(enemy.getUniqueId());
                        strike(p, enemy, center);
                    }
                    Msg.actionBar(p, hit.isEmpty() ? "<gray>Aucun ennemi dans la zone" : "<green>" + hit.size() + " ennemi(s) ejecte(s)");
                }

                /** Brume et poussiere qui retombent. */
                void aftermath(World w, int tick) {
                    Location c = center.clone().add(0, 0.5, 0);
                    w.spawnParticle(Particle.DUST, c, 8, 2.5, 0.8, 2.5, 0, MIST);
                    w.spawnParticle(Particle.CLOUD, c, 4, 2.5, 0.3, 2.5, 0.01);
                    if (tick == BLAST_TICK + 3) Effects.unroot(p);
                }
            }.runTaskTimer(plugin(), 0L, 1L);
            return true;
        }

        private static double normalize(double a) {
            while (a > Math.PI) a -= 2 * Math.PI;
            while (a < -Math.PI) a += 2 * Math.PI;
            return a;
        }

        private static void strike(Player byleth, org.bukkit.entity.LivingEntity enemy, Location center) {
            Effects.damage(enemy, 6.0, byleth);
            // Projection a 10 blocs en s'eloignant de Byleth, avec une bonne composante verticale comme dans le jeu.
            Vector dir = enemy.getLocation().toVector().subtract(byleth.getLocation().toVector()).setY(0);
            if (dir.lengthSquared() < 0.01) dir = byleth.getLocation().getDirection().setY(0);
            dir.normalize();
            enemy.setVelocity(dir.multiply(1.6).setY(0.9));
            Location at = enemy.getLocation().add(0, 1, 0);
            at.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 25, 0.5, 0.6, 0.5, 0, FLASH);
            at.getWorld().spawnParticle(Particle.CRIT, at, 20, 0.4, 0.5, 0.4, 0.2);
        }
    }

    // ----------------------------------------------------------------- ultime

    /**
     * Les Tenebres se consumeront elles-memes : les ennemis a 20 blocs sont
     * envoyes dans une dimension avec Byleth. Elle y a Force, Vitesse et
     * Resistance mais ne peut pas manger de pomme doree. Les ennemis ne sortent
     * que si Byleth meurt, et cette mort ne lui coute pas de vie.
     */
    static final class Tenebres extends UltimateAbility {

        /** Positions de retour, pour Byleth et ses prisonniers. */
        private final Map<UUID, Location> returnPoints = new HashMap<>();
        private UUID activeByleth;

        Tenebres() {
            super("byleth_ult", "Les Tenebres se consumeront elles-memes",
                    "Teleporte les ennemis a 20 blocs dans une autre dimension avec Byleth. Ils n'en sortent que si elle meurt, ce qui ne lui coute pas de vie.",
                    Material.ENDER_EYE);
        }

        @Override
        public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            if (activeByleth != null) {
                Msg.actionBar(p, "<red>La dimension est deja occupee.");
                return false;
            }
            List<org.bukkit.entity.LivingEntity> enemies = Effects.enemiesWithin(p, 20);
            if (enemies.isEmpty()) {
                Msg.actionBar(p, "<red>Aucun ennemi a moins de 20 blocs.");
                return false;
            }
            World dim = dimension();
            activeByleth = p.getUniqueId();
            returnPoints.clear();
            returnPoints.put(p.getUniqueId(), p.getLocation());
            p.teleport(ArenaWorlds.center(dim));
            Msg.send(p, "<dark_purple>Tu entraines " + enemies.size() + " ennemi(s) dans les Tenebres.");
            ThreadLocalRandom r = ThreadLocalRandom.current();
            for (org.bukkit.entity.LivingEntity enemy : enemies) {
                returnPoints.put(enemy.getUniqueId(), enemy.getLocation());
                enemy.teleport(ArenaWorlds.center(dim).add(r.nextInt(-12, 13), 0, r.nextInt(-12, 13)));
                if (enemy instanceof Player ep) {
                    Msg.send(ep, "<dark_purple>Byleth t'a entraine dans les Tenebres. Tu n'en sortiras que par sa mort.");
                    ep.playSound(ep.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
                }
            }
            dim.playSound(ArenaWorlds.center(dim), Sound.ENTITY_WITHER_SPAWN, 1f, 0.6f);
            return true;
        }

        @Override
        public void tick(Player p, CfPlayer cf, AbilityState st) {
            if (!p.getUniqueId().equals(activeByleth)) return;
            if (!p.getWorld().getName().equals(DIMENSION_WORLD)) return;
            Effects.refresh(p, PotionEffectType.STRENGTH, 0, 45);
            Effects.refresh(p, PotionEffectType.SPEED, 0, 45);
            Effects.refresh(p, PotionEffectType.RESISTANCE, 0, 45);
            // Plus aucun ennemi vivant dans la dimension : Byleth rentre.
            boolean enemyInside = false;
            for (org.bukkit.entity.LivingEntity o : p.getWorld().getLivingEntities()) if (Effects.isEnemy(p, o)) { enemyInside = true; break; }
            if (!enemyInside) {
                Location back = returnPoints.get(p.getUniqueId());
                if (back != null) p.teleport(back);
                Msg.send(p, "<dark_purple>Plus aucun ennemi dans les Tenebres, tu reviens.");
                finish();
            }
        }

        @Override
        public boolean onDeath(Player p, CfPlayer cf, AbilityState st) {
            if (!p.getUniqueId().equals(activeByleth) || !p.getWorld().getName().equals(DIMENSION_WORLD)) return false;
            // Byleth meurt dans sa dimension : tout le monde sort, aucune vie perdue.
            Msg.broadcast("<dark_purple>Byleth est tombee dans les Tenebres : ses prisonniers sont liberes.");
            World dim = p.getWorld();
            releaseAll(dim, p);
            Location back = returnPoints.get(p.getUniqueId());
            Location target = back != null ? back : plugin().game().randomGroundLocation(300);
            finish();
            Bukkit.getScheduler().runTask(plugin(), () -> plugin().lives().respawnAt(p, target));
            Msg.send(p, "<green>Ta mort dans les Tenebres ne te coute aucune vie.");
            return true;
        }

        /** Renvoie tous les prisonniers (joueurs et mannequins) a leur position d'origine. */
        private void releaseAll(World dim, Player byleth) {
            for (org.bukkit.entity.LivingEntity o : new ArrayList<>(dim.getLivingEntities())) {
                if (o == byleth || !returnPoints.containsKey(o.getUniqueId())) continue;
                Location back = returnPoints.get(o.getUniqueId());
                o.teleport(back != null ? back : plugin().game().randomGroundLocation(300));
                if (o instanceof Player op) Msg.send(op, "<dark_purple>Tu es libere des Tenebres.");
            }
        }

        private void finish() {
            activeByleth = null;
            returnPoints.clear();
        }

        @Override
        public void onUnequip(Player p, CfPlayer cf) {
            // Fin de partie ou mort hors dimension pendant qu'elle est active : on libere tout le monde.
            if (p.getUniqueId().equals(activeByleth) && !p.getWorld().getName().equals(DIMENSION_WORLD)) {
                World dim = Bukkit.getWorld(DIMENSION_WORLD);
                if (dim != null) releaseAll(dim, p);
                finish();
            }
        }
    }
}
