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
import org.bukkit.block.BlockState;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.World;
import java.util.LinkedHashMap;
import java.util.Map;
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
        addAbility(new Yato().bindToWeapon());
        addAbility(new DragonGardien());
        addAbility(new SangDraconique());
        addAbility(new DragonDeLEau());
        setUltimate(new VaguesBleues());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.GOLDEN_SWORD, 8.5, "<gold><bold>Yato", List.of());
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Yato (8.5) : evolue avec les kills",
                "<white>Dragon Gardien : Resistance pres d'un allie",
                "<white>Sang Draconique : vol 5 s, eau a l'atterrissage",
                "<white>Dragon de l'eau : Agilite aquatique IV, Vitesse IV dans l'eau",
                "<light_purple>Ultime : vague de 40 blocs, ejecte a 30 blocs");
    }

    /** Remplace l'arme de Corrin dans son inventaire. */
    static void setYato(Player p, double damage, String name, String desc) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if ("corrin".equals(Items.tag(it, AbilityEngine.WEAPON_KEY))) {
                ItemStack sword = Weapons.melee(Material.GOLDEN_SWORD, damage, name, Items.wrap("<yellow>" + desc));
                AbilityEngine.decorateWeapon(sword, com.kiokouwhite.crossfight.CrossfightPlugin.get().characters().character("corrin"));
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

        /** Les ailes de dragon de chaque joueur en vol. */
        static final Map<UUID, com.kiokouwhite.crossfight.util.DragonWings> WINGS = new java.util.HashMap<>();

        static void shedWings(Player p) {
            var w = WINGS.remove(p.getUniqueId());
            if (w != null) w.remove();
        }

        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            p.setAllowFlight(true);
            p.setFlying(true);
            st.set("noFallUntil", System.currentTimeMillis() + 15000L);
            shedWings(p);
            WINGS.put(p.getUniqueId(), com.kiokouwhite.crossfight.util.DragonWings.grow(p));
            World w = p.getWorld();
            w.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1f, 1.2f);
            w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_2, 1f, 1.1f);
            w.spawnParticle(Particle.SPLASH, p.getLocation().add(0, 1.2, 0), 120, 1.5, 1.0, 1.5, 0.2);
            w.spawnParticle(Particle.BUBBLE_POP, p.getLocation().add(0, 1.2, 0), 40, 1.2, 0.8, 1.2, 0.1);
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                if (!p.isOnline()) { shedWings(p); return; }
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
                    if (!p.isOnline() || p.isDead() || ticks > 200) { shedWings(p); cancel(); return; }
                    Location feet = p.getLocation();
                    boolean grounded = feet.clone().subtract(0, 0.1, 0).getBlock().getType().isSolid() || p.isInWater();
                    if (grounded) {
                        cancel();
                        st.set("noFallUntil", 0L);
                        shedWings(p);
                        spawnWater(p.getLocation());
                    }
                }
            }.runTaskTimer(plugin(), 1L, 1L);
        }

        @Override public void onUnequip(Player p, CfPlayer cf) { shedWings(p); }

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
            com.kiokouwhite.crossfight.util.Sounds.at(loc.clone().add(0.5, 0.5, 0.5), "ability.corrin_sang_eau");
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
            super("corrin_eau", "Dragon de l'eau", "Corrin a Agilite aquatique IV, respire sous l'eau et gagne Vitesse IV tant qu'elle est dans l'eau.", Material.PRISMARINE_CRYSTALS);
        }

        @Override
        public void tick(Player p, CfPlayer cf, AbilityState st) {
            Effects.refresh(p, PotionEffectType.DOLPHINS_GRACE, 3, 45);
            Effects.refresh(p, PotionEffectType.WATER_BREATHING, 0, 45);
            if (p.isInWater()) Effects.refresh(p, PotionEffectType.SPEED, 3, 45);
        }
    }

    // ----------------------------------------------------------------- ultime

    static final class VaguesBleues extends UltimateAbility implements com.kiokouwhite.crossfight.ability.EnemyCastable {

        /** Un bloc de la vague : decalage derriere le front, hauteur au-dessus du sol, matiere. */
        private record Cell(int back, int up, Material material) {}

        /** Profil de la vague vu de cote : corps bleu, crete bleu clair, ecume blanche qui s'enroule vers l'avant. */
        private static final List<Cell> PROFILE = List.of(
                new Cell(0, 1, Material.LIGHT_BLUE_STAINED_GLASS),
                new Cell(1, 1, Material.BLUE_STAINED_GLASS), new Cell(1, 2, Material.LIGHT_BLUE_STAINED_GLASS),
                new Cell(2, 1, Material.BLUE_STAINED_GLASS), new Cell(2, 2, Material.BLUE_STAINED_GLASS), new Cell(2, 3, Material.LIGHT_BLUE_STAINED_GLASS),
                new Cell(3, 1, Material.BLUE_STAINED_GLASS), new Cell(3, 2, Material.BLUE_STAINED_GLASS), new Cell(3, 3, Material.BLUE_STAINED_GLASS), new Cell(3, 4, Material.LIGHT_BLUE_STAINED_GLASS),
                new Cell(4, 1, Material.BLUE_STAINED_GLASS), new Cell(4, 2, Material.BLUE_STAINED_GLASS), new Cell(4, 3, Material.BLUE_STAINED_GLASS), new Cell(4, 4, Material.LIGHT_BLUE_STAINED_GLASS),
                // La crete s'enroule vers l'avant, au-dessus du front
                new Cell(2, 4, Material.WHITE_STAINED_GLASS), new Cell(1, 4, Material.WHITE_STAINED_GLASS), new Cell(1, 5, Material.WHITE_STAINED_GLASS), new Cell(0, 5, Material.WHITE_STAINED_GLASS),
                // La queue redescend
                new Cell(5, 1, Material.BLUE_STAINED_GLASS), new Cell(5, 2, Material.BLUE_STAINED_GLASS), new Cell(5, 3, Material.LIGHT_BLUE_STAINED_GLASS),
                new Cell(6, 1, Material.BLUE_STAINED_GLASS), new Cell(6, 2, Material.LIGHT_BLUE_STAINED_GLASS),
                new Cell(7, 1, Material.LIGHT_BLUE_STAINED_GLASS));

        private static final int STEPS = 40;
        private static final int TICKS_PER_STEP = 2; // 10 blocs par seconde
        private static final int HALF_WIDTH = 5;     // 10 blocs de large
        /** Duree de la pose, bras plantes dans le sol : Corrin ne peut pas bouger (1,5 s). */
        private static final int PLANT_TICKS = 30;

        VaguesBleues() {
            super("corrin_ult", "Les Vagues Bleues de l'Ocean Gris",
                    "Corrin plante ses bras dans le sol, immobile 1,5 s : une vraie vague de 10 blocs de large se dresse et deferle sur 40 blocs (10 blocs/s) dans la direction ou elle regarde. Les ennemis touches sont ejectes a 30 blocs de haut et ont Lenteur III pendant 10 secondes.",
                    Material.HEART_OF_THE_SEA);
        }

        @Override
        public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            perform(p, Effects.towards(p, null), Effects.enemiesOf(p, null));
            // Comme son dragon dans Smash Bros : a quatre pattes, les deux bras plantes dans le sol,
            // immobile pendant que la vague se dresse.
            Effects.root(p, PLANT_TICKS);
            var puppet = com.kiokouwhite.crossfight.util.Puppet.show(p, "corrin", null, "debout");
            if (puppet != null) { puppet.pose("corrin_ult", 4); puppet.endAfter(PLANT_TICKS, 5); }
            // Ses ailes de dragon se deploient sur son dos le temps de la pose (sauf si elle les a deja, en plein vol).
            if (puppet != null && !SangDraconique.WINGS.containsKey(p.getUniqueId())) {
                Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                    if (!p.isOnline() || p.isDead() || com.kiokouwhite.crossfight.util.Puppet.of(p) == null) return;
                    var wings = com.kiokouwhite.crossfight.util.DragonWings.grow(p, 0.45, 0.6);
                    Bukkit.getScheduler().runTaskLater(plugin(), wings::remove, PLANT_TICKS - 3L);
                }, 4L);
            }
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    if (!p.isOnline() || p.isDead() || (t += 2) > PLANT_TICKS) { cancel(); return; }
                    // L'eau jaillit autour de ses mains.
                    Location feet = p.getLocation();
                    Vector fwd = feet.getDirection().setY(0);
                    if (fwd.lengthSquared() < 1e-4) fwd = new Vector(0, 0, 1);
                    fwd.normalize();
                    Vector side = new Vector(-fwd.getZ(), 0, fwd.getX());
                    for (int k = -1; k <= 1; k += 2) {
                        Location hand = feet.clone().add(fwd.clone().multiply(0.55)).add(side.clone().multiply(0.32 * k)).add(0, 0.1, 0);
                        p.getWorld().spawnParticle(Particle.SPLASH, hand, 10, 0.15, 0.1, 0.15, 0.1);
                        p.getWorld().spawnParticle(Particle.CLOUD, hand, 1, 0.1, 0.05, 0.1, 0.02);
                    }
                }
            }.runTaskTimer(plugin(), 4L, 2L);
            // Apres sa replique, le rugissement du dragon.
            Bukkit.getScheduler().runTaskLater(plugin(), () -> { if (p.isOnline()) com.kiokouwhite.crossfight.util.Sounds.ability(p, "corrin_ult_dragon"); }, 14L);
            return true;
        }

        @Override
        public boolean castOn(org.bukkit.entity.LivingEntity caster, Player target, AbilityState st) {
            perform(caster, Effects.towards(caster, target), Effects.enemiesOf(caster, target));
            return true;
        }

        static void perform(org.bukkit.entity.LivingEntity p, Vector dir, java.util.function.Predicate<org.bukkit.entity.LivingEntity> isTarget) {
            // La vague part exactement la ou Corrin regarde (a l'horizontale) : droit, en diagonale, ou entre les deux.
            final Vector axis = dir.clone().setY(0).normalize();
            final Vector side = new Vector(-axis.getZ(), 0, axis.getX());
            final Location origin = p.getLocation().getBlock().getLocation();
            // Toutes les cases de la grille que la vague balaiera : {x, z, distance le long de l'axe en
            // dixiemes, ecart au centre en dixiemes}. Le demi-bloc de decalage garde les 10 blocs de large.
            final List<int[]> swept = new ArrayList<>();
            {
                int ox = origin.getBlockX(), oz = origin.getBlockZ(), span = STEPS + HALF_WIDTH + 4;
                for (int dx = -span; dx <= span; dx++) {
                    for (int dz = -span; dz <= span; dz++) {
                        double along = dx * axis.getX() + dz * axis.getZ(), lat = Math.abs(dx * side.getX() + dz * side.getZ() - 0.5);
                        if (along < 0.5 || along > STEPS + 2.5 || lat >= HALF_WIDTH) continue; // jamais sur Corrin
                        swept.add(new int[]{ox + dx, oz + dz, (int) Math.round(along * 10), (int) (lat * 10)});
                    }
                }
            }
            final World w = p.getWorld();
            final Map<Block, BlockState> placed = new LinkedHashMap<>();
            final List<BlockDisplay> cubes = new ArrayList<>();
            final List<UUID> hit = new ArrayList<>();

            w.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.6f);
            w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, 1f, 0.7f);

            new BukkitRunnable() {
                int step = 0;

                @Override public void run() {
                    try {
                        if (step >= STEPS || !p.isValid()) { restoreAll(); clearCubes(); cancel(); return; }
                        step++;
                        int front = step + 1; // la vague part deux blocs devant Corrin
                        restoreAll();
                        // D'abord les ennemis atteints : ils sont ejectes avant que le verre de la vague ne soit pose,
                        // et le verre evite ensuite leur colonne (sinon la crete, au-dessus d'eux, bloque l'ejection).
                        strike(front);
                        build(front);
                        splash(front);
                    } catch (Exception ex) {
                        plugin().getLogger().warning("Vagues Bleues interrompues : " + ex);
                        restoreAll();
                        clearCubes();
                        cancel();
                    }
                }

                /**
                 * Les places ou la vague ne pose pas de verre : le corps de chaque etre vivant qu'elle traverse, et
                 * toute la colonne au-dessus de ceux qu'elle vient d'ejecter (leur envol reste libre).
                 */
                List<org.bukkit.util.BoundingBox> keepClear(int front) {
                    List<org.bukkit.util.BoundingBox> out = new ArrayList<>();
                    Location c = origin.clone().add(0.5, 2, 0.5).add(axis.clone().multiply(front - 3));
                    for (org.bukkit.entity.Entity e : w.getNearbyEntities(c, HALF_WIDTH + 9, 12, HALF_WIDTH + 9)) {
                        if (!(e instanceof org.bukkit.entity.LivingEntity le) || le.isDead()) continue;
                        org.bukkit.util.BoundingBox box = le.getBoundingBox().clone().expand(0.3, 0.0, 0.3);
                        if (hit.contains(le.getUniqueId())) box.expand(0, 0, 0, 0, 40, 0);
                        out.add(box);
                    }
                    return out;
                }

                /** Pose les blocs de verre de la vague autour du front, colonne par colonne. */
                void build(int front) {
                    List<org.bukkit.util.BoundingBox> clear = keepClear(front);
                    for (int[] cell : swept) {
                        // A combien de blocs derriere le front se trouve cette colonne.
                        int back = (int) Math.round(front - cell[2] / 10.0);
                        if (back < 0) continue;
                        // Les deux dernieres colonnes de chaque bord sont plus basses, pour arrondir la vague.
                        int edge = Math.max(0, cell[3] / 10 - (HALF_WIDTH - 3));
                        int ground = Integer.MIN_VALUE;
                        for (Cell c : PROFILE) {
                            if (c.back() != back) continue;
                            int up = c.up() - edge;
                            if (up < 1) continue;
                            if (ground == Integer.MIN_VALUE) ground = groundY(cell[0], cell[1]);
                            Block b = w.getBlockAt(cell[0], ground + up, cell[1]);
                            if (!(b.getType().isAir() || b.isReplaceable()) || placed.containsKey(b)) continue;
                            org.bukkit.util.BoundingBox bb = org.bukkit.util.BoundingBox.of(b);
                            boolean free = true;
                            for (org.bukkit.util.BoundingBox k : clear) if (k.overlaps(bb)) { free = false; break; }
                            if (!free) continue;
                            placed.put(b, b.getState());
                            b.setType(c.material(), false);
                        }
                    }
                }

                /** Hauteur du sol sous la vague, en suivant le relief autour de Corrin. */
                int groundY(int x, int z) {
                    int y = origin.getBlockY() - 1;
                    // Monte si le sol est plus haut, descend s'il est plus bas (au plus 3 blocs).
                    for (int i = 0; i < 8 && w.getBlockAt(x, y + 1, z).getType().isSolid(); i++) y++;
                    for (int i = 0; i < 8 && !w.getBlockAt(x, y, z).getType().isSolid(); i++) y--;
                    return y;
                }

                /** Eclaboussures, cubes de verre qui volent et bruit de vague. */
                void splash(int front) {
                    Location crest = origin.clone().add(0.5, 3.5, 0.5).add(axis.clone().multiply(front - 1));
                    double sx = Math.abs(side.getX()) * HALF_WIDTH + Math.abs(axis.getX()) * 1.5;
                    double sz = Math.abs(side.getZ()) * HALF_WIDTH + Math.abs(axis.getZ()) * 1.5;
                    w.spawnParticle(Particle.SPLASH, crest, 80, sx, 1.0, sz, 0);
                    w.spawnParticle(Particle.BUBBLE_POP, crest, 20, sx, 1.0, sz, 0);
                    w.spawnParticle(Particle.CLOUD, crest.clone().add(0, 1, 0), 8, sx, 0.3, sz, 0.01);
                    if (front % 2 == 0) {
                        w.playSound(crest, Sound.ENTITY_GENERIC_SPLASH, 1f, 0.6f);
                        w.playSound(crest, Sound.BLOCK_GLASS_BREAK, 0.4f, 1.4f);
                    }
                    clearCubes();
                    for (int i = 0; i < 8; i++) {
                        Location l = crest.clone().add(axis.clone().multiply(Math.random() * 3)).add(side.clone().multiply(Math.random() * 2 * HALF_WIDTH - HALF_WIDTH)).add(0, Math.random() * 2.5, 0);
                        float size = 0.25f + (float) Math.random() * 0.45f;
                        BlockDisplay d = w.spawn(l, BlockDisplay.class, disp -> {
                            disp.setBlock((Math.random() < 0.5 ? Material.LIGHT_BLUE_STAINED_GLASS : Material.WHITE_STAINED_GLASS).createBlockData());
                            var rot = new org.joml.Quaternionf().rotationXYZ((float) (Math.random() * 6.28), (float) (Math.random() * 6.28), 0);
                            disp.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(-size / 2, -size / 2, -size / 2), rot,
                                    new org.joml.Vector3f(size, size, size), new org.joml.Quaternionf()));
                            disp.setPersistent(false);
                        });
                        cubes.add(d);
                    }
                }

                /** Les ennemis au front de la vague sont emportes. */
                void strike(int front) {
                    Location check = origin.clone().add(0.5, 1, 0.5).add(axis.clone().multiply(front - 1));
                    for (org.bukkit.entity.LivingEntity enemy : Effects.within(check, HALF_WIDTH + 2.0, isTarget)) {
                        if (enemy == p || hit.contains(enemy.getUniqueId())) continue;
                        // Zone rectangulaire : 2 blocs devant/derriere le front, toute la largeur.
                        Vector rel = enemy.getLocation().toVector().subtract(check.toVector());
                        if (Math.abs(rel.dot(axis)) > 2.5 || Math.abs(rel.dot(side)) > HALF_WIDTH + 0.5) continue;
                        hit.add(enemy.getUniqueId());
                        Effects.launchUp(enemy, 30);
                        Effects.give(enemy, PotionEffectType.SLOWNESS, 2, 20 * 10);
                        enemy.getWorld().spawnParticle(Particle.SPLASH, enemy.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0);
                        Effects.say(enemy, "<aqua>Emporte par la vague de Corrin !");
                    }
                }

                void restoreAll() {
                    List<Map.Entry<Block, BlockState>> entries = new ArrayList<>(placed.entrySet());
                    java.util.Collections.reverse(entries);
                    for (var e : entries) e.getValue().update(true, false);
                    placed.clear();
                }

                void clearCubes() {
                    for (BlockDisplay d : cubes) d.remove();
                    cubes.clear();
                }
            }.runTaskTimer(plugin(), 0L, TICKS_PER_STEP);
        }
    }
}
