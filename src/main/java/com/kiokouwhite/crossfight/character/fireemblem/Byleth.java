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

    static final String DIMENSION_WORLD = "crossfight_tenebres";
    static final String SWORD_ID = "byleth_sword";

    public Byleth() {
        super("byleth", "Byleth", Voie.HARMONIE, Material.DIAMOND_SWORD);
        addAbility(new EpeeDuCreateur().bindToWeapon());
        addAbility(new EmblemeDeFeu());
        addAbility(new Tutorat());
        addAbility(new DechainementDEpine());
        setUltimate(new Tenebres());
    }

    @Override
    public ItemStack createWeapon() {
        ItemStack sword = Weapons.melee(Material.DIAMOND_SWORD, 8, "<gold><bold>Epee du Createur", List.of());
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

    /** Les Tenebres : un sol noir infini sous un ciel noir, sans aucune lumiere. */
    public static World dimension() {
        World w = ArenaWorlds.get(DIMENSION_WORLD,
                new ArenaWorlds.InfiniteFloorGenerator(Material.BLACK_CONCRETE), World.Environment.THE_END);
        w.setTime(18000);
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

    static final class EpeeDuCreateur extends ActiveAbility implements com.kiokouwhite.crossfight.ability.EnemyCastable {
        EpeeDuCreateur() {
            super(SWORD_ID, "Epee du Createur", "Clic droit : Byleth se fige et son epee s'etend tout droit sur 10 blocs (8 blocs/s) pour ramener le joueur attrape.", Material.DIAMOND_SWORD, 10);
        }

        /** Avancee par tick : 8 blocs par seconde. */
        private static final double SPEED_PER_TICK = 0.4;

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            perform(p, p.getEyeLocation().getDirection().normalize(), Effects.enemiesOf(p, null));
            return true;
        }

        @Override
        public boolean castOn(org.bukkit.entity.LivingEntity caster, Player target, AbilityState st) {
            Vector dir = target.getEyeLocation().toVector().subtract(caster.getEyeLocation().toVector());
            if (dir.lengthSquared() < 0.01) dir = caster.getLocation().getDirection();
            perform(caster, dir.normalize(), Effects.enemiesOf(caster, target));
            return true;
        }

        /** Le fouet d'os part tout droit depuis les yeux du lanceur et ramene la premiere cible attrapee. */
        static void perform(org.bukkit.entity.LivingEntity caster, Vector lockedDir, java.util.function.Predicate<org.bukkit.entity.LivingEntity> isTarget) {
            caster.getWorld().playSound(caster.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 1.5f);
            // Le lanceur est immobilise tant que le fouet est sorti (au plus 3 secondes de securite).
            Effects.root(caster, 60);
            // Byleth se fend et tend le bras de l'epee droit devant : la lame-fouet part de sa main.
            final var puppet = caster instanceof Player pl ? com.kiokouwhite.crossfight.util.Puppet.show(pl, "byleth", DechainementDEpine.heldSword(pl), "debout") : null;
            if (puppet != null) {
                Location facing = caster.getLocation().clone();
                facing.setDirection(lockedDir);
                puppet.face(facing.getYaw());
                puppet.pose("byleth_epee_tendue", 3);
                puppet.endAfter(60, 4);
            }
            BoneWhip whip = new BoneWhip(false);
            new BukkitRunnable() {
                double length = 0;
                boolean retracting = false;

                @Override public void run() {
                    if (!caster.isValid() || caster.isDead()) { clear(); cancel(); return; }
                    if (retracting) {
                        length -= 1.5;
                        if (length <= 0) { clear(); cancel(); return; }
                        whip.layout(start(), lockedDir, length);
                        return;
                    }
                    length += SPEED_PER_TICK;
                    Location eye = caster.getEyeLocation();
                    // Le fouet s'arrete sur un mur.
                    for (double d = 0.5; d <= length; d += 0.5) {
                        if (!eye.clone().add(lockedDir.clone().multiply(d)).getBlock().isPassable()) {
                            length = Math.max(0.5, d - 0.5);
                            retracting = true;
                            break;
                        }
                    }
                    whip.layout(start(), lockedDir, length);
                    Location tip = eye.clone().add(lockedDir.clone().multiply(length));
                    for (org.bukkit.entity.LivingEntity enemy : Effects.within(tip, 1.2, isTarget)) {
                        if (enemy == caster) continue;
                        Vector pull = caster.getLocation().toVector().subtract(enemy.getLocation().toVector());
                        double dist = pull.length();
                        pull.normalize().multiply(Math.min(2.5, 0.4 + dist * 0.22)).setY(0.35);
                        enemy.setVelocity(pull);
                        enemy.getWorld().playSound(enemy.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 0.7f);
                        enemy.getWorld().spawnParticle(Particle.CRIT, enemy.getLocation().add(0, 1, 0), 15, 0.3, 0.4, 0.3, 0.1);
                        Effects.say(enemy, "<gold>Byleth t'attrape avec son epee !");
                        Effects.say(caster, "<gold>Attrape !");
                        retracting = true;
                        return;
                    }
                    if (length >= 10) retracting = true;
                }

                /** Le fouet sort de la main tendue (des yeux, sans modele articule). */
                Location start() {
                    if (puppet != null && caster instanceof Player pl && com.kiokouwhite.crossfight.util.Puppet.of(pl) == puppet) return puppet.hand();
                    return caster.getEyeLocation();
                }

                void clear() {
                    whip.clear();
                    Effects.unroot(caster);
                    if (puppet != null && caster instanceof Player pl && com.kiokouwhite.crossfight.util.Puppet.of(pl) == puppet) puppet.endAfter(0, 4);
                }
            }.runTaskTimer(plugin(), 0L, 1L);
        }
    }

    // --------------------------------------------------------------- pouvoirs

    static final class EmblemeDeFeu extends PassiveAbility {
        EmblemeDeFeu() {
            super("byleth_embleme", "Embleme de Feu", "Byleth regenere 25 % des degats qu'elle inflige, ses allies a moins de 10 blocs 10 %.", Material.BLAZE_ROD);
        }

        @Override
        public void onDealDamage(Player attacker, org.bukkit.entity.LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) {
            double dmg = e.getFinalDamage();
            if (dmg <= 0) return;
            Bukkit.getScheduler().runTask(plugin(), () -> {
                Effects.heal(attacker, dmg * 0.25);
                for (var ally : Effects.alliesWithin(attacker, 10)) Effects.heal(ally, dmg * 0.10);
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
            super("byleth_tutorat", "Tutorat", "Fait progresser un allie, ou qu'il soit, dans une categorie : arme, physique ou capacites.",
                    Material.BOOK, 15 * 60);
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            // Mannequin en troisieme personne : pas d'eleve a former, juste la lecture du livre, pour la voir.
            if (com.kiokouwhite.crossfight.util.MannequinPlayer.isProxy(p)) {
                startReading(p);
                Bukkit.getScheduler().runTaskLater(plugin(), () -> stopReading(p, 0), 100L);
                Msg.actionBar(p, "<gold>Tutorat <gray>(troisieme personne : l'animation seule)");
                return true;
            }
            // Tous les allies vivants, quelle que soit la distance (meme dans un autre monde).
            List<org.bukkit.entity.LivingEntity> allies = new ArrayList<>();
            for (World world : Bukkit.getWorlds()) {
                for (org.bukkit.entity.LivingEntity e : world.getLivingEntities()) {
                    if (Effects.isAlly(p, e)) allies.add(e);
                }
            }
            if (allies.isEmpty()) {
                Msg.actionBar(p, "<red>Tutorat : aucun allie en vie.");
                return false;
            }
            // Etape 1 : choisir l'allie a former.
            List<ChoiceMenu.Option> options = new ArrayList<>();
            for (org.bukkit.entity.LivingEntity ally : allies) {
                Progress pr = progress.computeIfAbsent(ally.getUniqueId(), u -> new Progress());
                List<String> lore = new ArrayList<>();
                for (int i = 0; i < 3; i++) lore.add("<gray>" + CATEGORIES[i] + " : <white>" + pr.level[i] + "/4 <dark_gray>(" + (int) pr.xp[i] + " XP)");
                lore.add(ally.getWorld().equals(p.getWorld())
                        ? "<gray>Distance : " + (int) ally.getLocation().distance(p.getLocation()) + " blocs"
                        : "<gray>Dans une autre dimension");
                options.add(new ChoiceMenu.Option(ally instanceof Player ? Material.PLAYER_HEAD : Material.ZOMBIE_HEAD,
                        allyName(ally), lore, () -> chooseCategory(p, ally, pr, st)));
            }
            startReading(p);
            ChoiceMenu.open(p, "<dark_gray>Tutorat : quel allie former ?", options,
                    pl -> { st.clearCooldown(); Msg.actionBar(pl, "<gray>Tutorat annule."); stopReading(pl, 0); });
            // Le temps de chargement ne part que si une categorie est choisie.
            return true;
        }

        /** Etape 2 : choisir la categorie. */
        private void chooseCategory(Player p, org.bukkit.entity.LivingEntity ally, Progress pr, AbilityState st) {
            if (!ally.isValid() || ally.isDead()) {
                Msg.actionBar(p, "<red>Tutorat : " + allyName(ally) + " n'est plus disponible.");
                st.clearCooldown();
                stopReading(p, 0);
                return;
            }
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
            ChoiceMenu.open(p, "<dark_gray>Tutorat : " + allyName(ally), options,
                    pl -> { st.clearCooldown(); Msg.actionBar(pl, "<gray>Tutorat annule."); stopReading(pl, 0); });
        }

        private static String allyName(org.bukkit.entity.LivingEntity e) {
            return e instanceof Player pl ? pl.getName() : "Mannequin allie";
        }

        private void teach(Player byleth, org.bukkit.entity.LivingEntity ally, Progress pr, int cat, AbilityState st) {
            if (pr.level[cat] >= 4) {
                Msg.send(byleth, "<red>" + allyName(ally) + " est deja au niveau maximum en " + CATEGORIES[cat] + ".");
                st.clearCooldown();
                stopReading(byleth, 0);
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
                        if (ally instanceof Player ap) {
                            CfPlayer acf = plugin().players().get(ap);
                            acf.setCooldownFactor(acf.cooldownFactor() * 0.95);
                        }
                        bonusText = "<yellow>-5 % de temps de chargement";
                    }
                }
            }
            Boosts.set(ally, "tutorat", new Boosts.Boost(pr.force, pr.speed, pr.resistance, pr.weapon));
            Msg.send(byleth, "<gold>Tutorat : " + allyName(ally) + " gagne " + gain + " XP en " + CATEGORIES[cat]
                    + " <gray>(niveau " + pr.level[cat] + ", " + (int) pr.xp[cat] + " XP)"
                    + (bonusText.isEmpty() ? "" : " <gray>- " + bonusText));
            if (ally instanceof Player ap) {
                Msg.send(ap, "<gold>Byleth t'enseigne " + CATEGORIES[cat] + " : +" + gain + " XP"
                        + (bonusText.isEmpty() ? "" : " <gray>- niveau " + pr.level[cat] + " ! " + bonusText));
            }
            ally.getWorld().playSound(ally.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            // La lecon est donnee : elle lit encore un instant, puis referme son livre.
            stopReading(byleth, 30);
        }

        // ------------------------------------------------------------ la lecture

        /** Les pantins de celles qui sont en train de lire. */
        private static final Map<UUID, com.kiokouwhite.crossfight.util.Puppet> READING = new HashMap<>();

        /** Le livre ouvert du pack (items/book_open.json) ou l'une des trois images de la page qui tourne. */
        private static ItemStack book(String look) {
            ItemStack it = new ItemStack(Material.BOOK);
            var meta = it.getItemMeta();
            meta.setItemModel(new org.bukkit.NamespacedKey("crossfight", look));
            it.setItemMeta(meta);
            return it;
        }

        /** Byleth ouvre un livre devant elle et le lit, en tournant une page toutes les secondes et demie. */
        private static void startReading(Player p) {
            var puppet = com.kiokouwhite.crossfight.util.Puppet.show(p, "byleth", book("book_open"), "debout");
            if (puppet == null) return;
            puppet.pose("byleth_tutorat", 5);
            READING.put(p.getUniqueId(), puppet);
            final String[] flip = {"book_turn1", "book_turn2", "book_turn3", "book_open"};
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || READING.get(p.getUniqueId()) != puppet || com.kiokouwhite.crossfight.util.Puppet.of(p) != puppet) {
                        READING.remove(p.getUniqueId(), puppet);
                        cancel();
                        return;
                    }
                    int phase = (t - 16) % 30;   // premiere page au bout de 0,8 s, puis une toutes les 1,5 s
                    if (t >= 16 && phase < 8 && phase % 2 == 0) {
                        puppet.heldLook(book(flip[phase / 2]));
                        if (phase == 0) p.getWorld().playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1f);
                    }
                }
            }.runTaskTimer(plugin(), 1L, 1L);
        }

        /** Elle referme son livre dans {@code delay} ticks. */
        private static void stopReading(Player p, int delay) {
            var puppet = READING.remove(p.getUniqueId());
            if (puppet == null || com.kiokouwhite.crossfight.util.Puppet.of(p) != puppet) return;
            puppet.endAfter(delay, 4);
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

    static final class DechainementDEpine extends ActiveAbility implements com.kiokouwhite.crossfight.ability.EnemyCastable {
        DechainementDEpine() {
            super("byleth_epine", "Dechainement d'Epine", "Byleth balaie un grand arc avec son epee-fouet : les ennemis dans la zone de 5 blocs devant elle sont ejectes a 10 blocs et perdent 3 coeurs.",
                    Material.RED_DYE, 20);
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
            perform(p, Effects.towards(p, null), Effects.enemiesOf(p, null));
            return true;
        }

        @Override
        public boolean castOn(org.bukkit.entity.LivingEntity caster, Player target, AbilityState st) {
            perform(caster, Effects.towards(caster, target), Effects.enemiesOf(caster, target));
            return true;
        }

        static void perform(org.bukkit.entity.LivingEntity caster, Vector fwd, java.util.function.Predicate<org.bukkit.entity.LivingEntity> isTarget) {
            final double baseAngle = Math.atan2(fwd.getZ(), fwd.getX());
            final Location center = caster.getLocation().add(fwd.clone().multiply(3.5));
            final double groundY = caster.getLocation().getY();
            final List<java.util.UUID> hit = new ArrayList<>();
            final World w = caster.getWorld();

            Effects.root(caster, TOTAL + 5);
            w.playSound(caster.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 0.8f, 1.4f);
            w.playSound(caster.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.8f);
            BoneWhip whip = new BoneWhip(true);
            // Le modele de Byleth suit le coup : epee basse devant elle, bras arme a gauche, grand balayage
            // jusqu'a sa droite. Il garde la direction du coup, meme si le joueur tourne la tete.
            final var puppet = caster instanceof Player pl ? com.kiokouwhite.crossfight.util.Puppet.show(pl, "byleth", heldSword(pl), "debout") : null;
            if (puppet != null) {
                puppet.face((float) Math.toDegrees(Math.atan2(-fwd.getX(), fwd.getZ())));
                puppet.pose("byleth_epine_garde", 3);
                puppet.endAfter(TOTAL - 3, 4);
            }

            new BukkitRunnable() {
                int tick = 0;

                @Override public void run() {
                    if (!caster.isValid() || caster.isDead() || tick > TOTAL) { whip.clear(); Effects.unroot(caster); cancel(); return; }
                    int current = tick++;
                    try {
                        if (puppet != null && caster instanceof Player pl && com.kiokouwhite.crossfight.util.Puppet.of(pl) == puppet) {
                            // Elle arme a gauche juste avant le balayage, puis le bras traverse pendant qu'il dure.
                            if (current == WINDUP_END - 2) puppet.pose("byleth_epine_gauche", 2);
                            if (current == WINDUP_END) puppet.pose("byleth_epine_droite", SWEEP_END - WINDUP_END);
                        }
                        if (current < WINDUP_END) windup(current);
                        else if (current <= SWEEP_END) sweep(current);
                        else if (current == BLAST_TICK) blast();
                        else aftermath(current);
                    } catch (Exception ex) {
                        plugin().getLogger().warning("Dechainement d'Epine interrompu : " + ex);
                        whip.clear();
                        Effects.unroot(caster);
                        cancel();
                    }
                }

                /** Fouet court tenu bas devant, etincelles blanches a la pointe. */
                void windup(int tick) {
                    Location origin = caster.getEyeLocation().subtract(0, 0.6, 0);
                    Vector dir = fwd.clone().setY(-0.45).normalize();
                    whip.layout(origin, dir, 2.5 + tick * 0.15);
                    Location tip = BoneWhip.pointAt(origin, dir, 2.5 + tick * 0.15);
                    w.spawnParticle(Particle.END_ROD, tip, 4, 0.25, 0.25, 0.25, 0.03);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, tip, 6, 0.4, 0.3, 0.4, 0.1);
                    w.spawnParticle(Particle.DUST, tip, 3, 0.15, 0.15, 0.15, 0, PINK);
                    if (tick == WINDUP_END - 1) {
                        // Ruee en avant d'environ deux blocs.
                        caster.setVelocity(fwd.clone().multiply(0.85).setY(0.12));
                        w.playSound(caster.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f);
                        w.playSound(caster.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f);
                        w.spawnParticle(Particle.FLASH, caster.getEyeLocation().add(fwd), 1, 0, 0, 0, 0, org.bukkit.Color.fromRGB(255, 200, 220));
                    }
                }

                /** Grand coup horizontal au ras du sol, vague rouge et poussiere blanche. */
                void sweep(int tick) {
                    double t = (double) (tick - WINDUP_END) / (SWEEP_END - WINDUP_END);
                    double eased = t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
                    double angle = baseAngle - HALF_ARC + eased * 2 * HALF_ARC;
                    Vector dir = new Vector(Math.cos(angle), -0.12, Math.sin(angle)).normalize();
                    Location origin = caster.getLocation().add(0, 1.0, 0);
                    whip.layout(origin, dir, WHIP_LENGTH);
                    Location base = caster.getLocation();
                    for (double a = baseAngle - HALF_ARC; a <= angle; a += Math.toRadians(9)) {
                        for (double r = 1.5; r <= WHIP_LENGTH; r += 1.5) {
                            Location l = new Location(w, base.getX() + Math.cos(a) * r, groundY + 0.4 + Math.random() * 0.5, base.getZ() + Math.sin(a) * r);
                            w.spawnParticle(Particle.DUST, l, 1, 0.1, 0.1, 0.1, 0, r > 3.5 ? RED : PINK);
                        }
                    }
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
                void blast() {
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
                    for (org.bukkit.entity.LivingEntity enemy : Effects.within(center, 2.5, isTarget)) {
                        if (enemy == caster || hit.contains(enemy.getUniqueId())) continue;
                        hit.add(enemy.getUniqueId());
                        strike(caster, enemy);
                    }
                    Effects.say(caster, hit.isEmpty() ? "<gray>Aucun ennemi dans la zone" : "<green>" + hit.size() + " ennemi(s) ejecte(s)");
                }

                void aftermath(int tick) {
                    Location c = center.clone().add(0, 0.5, 0);
                    w.spawnParticle(Particle.DUST, c, 8, 2.5, 0.8, 2.5, 0, MIST);
                    w.spawnParticle(Particle.CLOUD, c, 4, 2.5, 0.3, 2.5, 0.01);
                    if (tick == BLAST_TICK + 3) Effects.unroot(caster);
                }
            }.runTaskTimer(plugin(), 0L, 1L);
        }

        /** L'Epee du Createur que porte Byleth, pour la montrer dans la main du modele. */
        private static ItemStack heldSword(Player p) {
            for (ItemStack it : p.getInventory().getContents()) {
                if (it != null && SWORD_ID.equals(com.kiokouwhite.crossfight.util.Items.tag(it, AbilityEngine.ABILITY_KEY))) return it;
            }
            return new ItemStack(Material.DIAMOND_SWORD);
        }

        private static void strike(org.bukkit.entity.LivingEntity caster, org.bukkit.entity.LivingEntity enemy) {
            Effects.damage(enemy, 6.0, caster);
            Vector dir = enemy.getLocation().toVector().subtract(caster.getLocation().toVector()).setY(0);
            if (dir.lengthSquared() < 0.01) dir = caster.getLocation().getDirection().setY(0);
            dir.normalize();
            enemy.setVelocity(dir.multiply(1.6).setY(0.9));
            Location at = enemy.getLocation().add(0, 1, 0);
            at.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, at, 25, 0.5, 0.6, 0.5, 0, FLASH);
            at.getWorld().spawnParticle(Particle.CRIT, at, 20, 0.4, 0.5, 0.4, 0.2);
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
        private long castAt;

        Tenebres() {
            super("byleth_ult", "Les Tenebres se consumeront elles-memes",
                    "Teleporte les ennemis a 20 blocs dans une autre dimension avec Byleth. Ils n'en sortent que si elle meurt, ce qui ne lui coute pas de vie.",
                    Material.ECHO_SHARD);
        }

        // Chronologie (ticks) : Byleth enveloppee 0-30, vague 30-70, zone emportee a 80.
        private static final int ENVELOP_END = 30;
        private static final int WAVE_END = 70;
        private static final int CARRY_TICK = 80;
        private static final double RANGE = 20.0;
        private static final Particle.DustOptions DEEP = new Particle.DustOptions(org.bukkit.Color.fromRGB(70, 10, 110), 2.6f);
        private static final Particle.DustOptions VIOLET = new Particle.DustOptions(org.bukkit.Color.fromRGB(130, 30, 190), 1.8f);
        private static final Particle.DustOptions BRIGHT = new Particle.DustOptions(org.bukkit.Color.fromRGB(200, 90, 255), 1.0f);

        @Override
        public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            if (activeByleth != null) {
                Msg.actionBar(p, "<red>La dimension est deja occupee.");
                return false;
            }
            List<org.bukkit.entity.LivingEntity> marked = Effects.enemiesWithin(p, RANGE);
            if (marked.isEmpty()) {
                Msg.actionBar(p, "<red>Aucun ennemi a moins de 20 blocs.");
                return false;
            }
            activeByleth = p.getUniqueId();
            castAt = System.currentTimeMillis();
            cf.setUltimateLocked(true);
            returnPoints.clear();
            // Byleth est figee et intouchable le temps que les Tenebres l'enveloppent.
            st.set("invulnUntil", System.currentTimeMillis() + (CARRY_TICK + 10) * 50L);
            Effects.root(p, CARRY_TICK + 10);
            final Location origin = p.getLocation();
            final World w = p.getWorld();
            final List<org.bukkit.entity.LivingEntity> caught = new ArrayList<>();
            w.playSound(origin, Sound.ENTITY_WITHER_AMBIENT, 1f, 0.4f);
            // Les Tenebres la font ployer, un genou a terre, puis la plaquent au sol, sur le dos.
            final var puppet = com.kiokouwhite.crossfight.util.Puppet.show(p, "byleth", null, "debout");
            if (puppet != null) {
                puppet.face(origin.getYaw());
                puppet.pose("byleth_ult_ploie", 6);
                Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                    if (com.kiokouwhite.crossfight.util.Puppet.of(p) == puppet) {
                        puppet.pose("byleth_ult_couchee", 8);
                        w.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.6f, 0.5f);
                    }
                }, 14L);
                puppet.endAfter(CARRY_TICK + 30, 6);
            }
            // Une seconde apres sa replique : son cri et le fracas du Final Smash.
            Bukkit.getScheduler().runTaskLater(plugin(), () -> { if (p.isOnline()) com.kiokouwhite.crossfight.util.Sounds.ability(p, "byleth_ult_cri"); }, 20L);
            w.playSound(origin, Sound.BLOCK_PORTAL_TRIGGER, 0.6f, 0.5f);

            new BukkitRunnable() {
                int tick = 0;
                @Override public void run() {
                    if (!p.isOnline() || p.isDead()) { Effects.unroot(p); for (var e : caught) Effects.unroot(e); finish(); cancel(); return; }
                    int t = tick++;
                    try {
                        if (t < ENVELOP_END) envelop(t);
                        else if (t < WAVE_END) wave(t);
                        else if (t < CARRY_TICK) climax(t);
                        else { carry(); cancel(); }
                    } catch (Exception ex) {
                        plugin().getLogger().warning("Ultime de Byleth interrompu : " + ex);
                        Effects.unroot(p);
                        for (var e : caught) Effects.unroot(e);
                        finish();
                        cancel();
                    }
                }

                /** La masse violette monte le long de Byleth et l'enveloppe entierement (au ras du sol une fois couchee). */
                void envelop(int t) {
                    double height = puppet != null && t >= 14 ? 0.8 : Math.min(2.2, 0.2 + t * 0.08);
                    Location base = p.getLocation();
                    for (int i = 0; i < 14; i++) {
                        double a = Math.random() * Math.PI * 2, r = 0.3 + Math.random() * 0.6, y = Math.random() * height;
                        Location l = base.clone().add(Math.cos(a) * r, y, Math.sin(a) * r);
                        w.spawnParticle(Particle.DUST, l, 1, 0.05, 0.05, 0.05, 0, y > height - 0.4 ? VIOLET : DEEP);
                    }
                    w.spawnParticle(Particle.WITCH, base.clone().add(0, height, 0), 3, 0.4, 0.2, 0.4, 0);
                    w.spawnParticle(Particle.SMOKE, base.clone().add(0, 0.2, 0), 2, 0.3, 0.1, 0.3, 0.01);
                    if (t % 10 == 0) w.playSound(base, Sound.ENTITY_ENDERMAN_AMBIENT, 0.6f, 0.5f);
                }

                /** La vague violette s'etend depuis Byleth et piege chaque ennemi qu'elle atteint. */
                void wave(int t) {
                    envelopAround(p.getLocation(), puppet != null ? 1.4 : 1.0, puppet != null ? 0.8 : 2.2, puppet != null ? 12 : 8);
                    double radius = (t - ENVELOP_END) / (double) (WAVE_END - ENVELOP_END) * RANGE;
                    for (double a = 0; a < Math.PI * 2; a += Math.toRadians(12)) {
                        Location l = origin.clone().add(Math.cos(a) * radius, 0.3 + Math.random() * 1.5, Math.sin(a) * radius);
                        w.spawnParticle(Particle.DUST, l, 1, 0.3, 0.3, 0.3, 0, VIOLET);
                        if (Math.random() < 0.3) w.spawnParticle(Particle.DUST, l, 1, 0.5, 0.8, 0.5, 0, DEEP);
                    }
                    for (org.bukkit.entity.LivingEntity e : marked) {
                        if (caught.contains(e) || e.isDead()) continue;
                        if (e.getLocation().distance(origin) <= radius + 1.0) {
                            caught.add(e);
                            Effects.root(e, CARRY_TICK - t + 10);
                            w.playSound(e.getLocation(), Sound.ENTITY_WITHER_HURT, 0.5f, 0.5f);
                            if (e instanceof Player ep) Msg.actionBar(ep, "<dark_purple>Les Tenebres t'enveloppent...");
                        }
                    }
                    for (org.bukkit.entity.LivingEntity e : caught) envelopAround(e.getLocation(), 0.8, 2.0, 6);
                    if (t % 8 == 0) w.playSound(origin, Sound.ENTITY_PHANTOM_AMBIENT, 0.5f, 0.4f);
                }

                /** Tout la zone se remplit de violet avant d'etre emportee. */
                void climax(int t) {
                    for (int i = 0; i < 40; i++) {
                        double a = Math.random() * Math.PI * 2, r = Math.random() * RANGE;
                        Location l = origin.clone().add(Math.cos(a) * r, Math.random() * 4, Math.sin(a) * r);
                        w.spawnParticle(Particle.DUST, l, 1, 0.5, 0.5, 0.5, 0, Math.random() < 0.7 ? DEEP : VIOLET);
                    }
                    w.spawnParticle(Particle.DRAGON_BREATH, origin.clone().add(0, 1, 0), 10, RANGE / 2, 1.5, RANGE / 2, 0.02, 1.0f);
                    for (org.bukkit.entity.LivingEntity e : caught) envelopAround(e.getLocation(), 1.0, 2.2, 10);
                    envelopAround(p.getLocation(), puppet != null ? 1.5 : 1.2, puppet != null ? 0.9 : 2.4, 14);
                    if (t == WAVE_END) {
                        w.playSound(origin, Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f);
                        Effects.give(p, PotionEffectType.DARKNESS, 0, 60);
                        for (org.bukkit.entity.LivingEntity e : caught) Effects.give(e, PotionEffectType.DARKNESS, 0, 60);
                    }
                }

                void envelopAround(Location base, double radius, double height, int count) {
                    for (int i = 0; i < count; i++) {
                        double a = Math.random() * Math.PI * 2, r = Math.random() * radius, y = Math.random() * height;
                        w.spawnParticle(Particle.DUST, base.clone().add(Math.cos(a) * r, y, Math.sin(a) * r), 1, 0, 0, 0, 0, Math.random() < 0.5 ? DEEP : VIOLET);
                    }
                    w.spawnParticle(Particle.DUST, base.clone().add(0, height / 2, 0), 1, radius, height / 2, radius, 0, BRIGHT);
                }

                /** Toute la zone est emportee dans les Tenebres. */
                void carry() {
                    World dim = dimension();
                    returnPoints.put(p.getUniqueId(), p.getLocation());
                    for (int i = 0; i < 80; i++) {
                        double a = Math.random() * Math.PI * 2, r = Math.random() * RANGE;
                        w.spawnParticle(Particle.DUST, origin.clone().add(Math.cos(a) * r, Math.random() * 3, Math.sin(a) * r), 1, 0, 0, 0, 0, DEEP);
                    }
                    w.spawnParticle(Particle.REVERSE_PORTAL, origin.clone().add(0, 1, 0), 200, RANGE / 2, 1.5, RANGE / 2, 0.5);
                    w.playSound(origin, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.4f);
                    Effects.unroot(p);
                    p.teleport(ArenaWorlds.center(dim));
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    int n = 0;
                    for (org.bukkit.entity.LivingEntity enemy : caught) {
                        if (enemy.isDead()) continue;
                        Effects.unroot(enemy);
                        returnPoints.put(enemy.getUniqueId(), enemy.getLocation());
                        enemy.teleport(ArenaWorlds.center(dim).add(r.nextInt(-12, 13), 0, r.nextInt(-12, 13)));
                        if (enemy instanceof Player ep) {
                            Msg.send(ep, "<dark_purple>Byleth t'a entraine dans les Tenebres. Tu n'en sortiras que par sa mort.");
                            ep.playSound(ep.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
                        }
                        n++;
                    }
                    Msg.send(p, "<dark_purple>Tu entraines " + n + " ennemi(s) dans les Tenebres.");
                    dim.playSound(ArenaWorlds.center(dim), Sound.ENTITY_WITHER_SPAWN, 1f, 0.6f);
                    if (n == 0) {
                        // Plus personne a emporter : Byleth ressort aussitot.
                        Location back = returnPoints.get(p.getUniqueId());
                        if (back != null) p.teleport(back);
                        finish();
                    }
                }
            }.runTaskTimer(plugin(), 0L, 1L);
            return true;
        }

        @Override
        public void onDamaged(Player victim, org.bukkit.event.entity.EntityDamageEvent e, AbilityState st) {
            if (System.currentTimeMillis() < (long) st.get("invulnUntil", 0L)) e.setCancelled(true);
        }

        @Override
        public void tick(Player p, CfPlayer cf, AbilityState st) {
            if (!p.getUniqueId().equals(activeByleth)) return;
            if (!p.getWorld().getName().equals(DIMENSION_WORLD)) return;
            Effects.refresh(p, PotionEffectType.STRENGTH, 0, 45);
            Effects.refresh(p, PotionEffectType.SPEED, 0, 45);
            Effects.refresh(p, PotionEffectType.RESISTANCE, 0, 45);
            // Plus aucun ennemi vivant dans la dimension : Byleth rentre. On laisse 3 secondes aux
            // teleportations inter-mondes pour se terminer avant de verifier.
            if (System.currentTimeMillis() - castAt < 3000) return;
            boolean enemyInside = false;
            for (UUID u : returnPoints.keySet()) {
                if (u.equals(p.getUniqueId())) continue;
                org.bukkit.entity.Entity e = Bukkit.getEntity(u);
                if (e instanceof org.bukkit.entity.LivingEntity le && le.isValid() && !le.isDead()
                        && le.getWorld().getName().equals(DIMENSION_WORLD) && Effects.isEnemy(p, le)) { enemyInside = true; break; }
            }
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
            // Deux ticks plus tard : apres la reapparition vanilla declenchee par DeathListener.
            Bukkit.getScheduler().runTaskLater(plugin(), () -> plugin().lives().respawnAt(p, target), 2L);
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
            if (activeByleth != null) {
                Player b = com.kiokouwhite.crossfight.util.MannequinPlayer.getPlayer(activeByleth);
                if (b != null) plugin().players().get(b).setUltimateLocked(false);
            }
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
