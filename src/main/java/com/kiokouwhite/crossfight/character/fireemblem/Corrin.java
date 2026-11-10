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

        VaguesBleues() {
            super("corrin_ult", "Les Vagues Bleues de l'Ocean Gris",
                    "Une vraie vague de 10 blocs de large se dresse et deferle sur 40 blocs (10 blocs/s). Les ennemis touches sont ejectes a 30 blocs de haut et ont Lenteur III pendant 10 secondes.",
                    Material.HEART_OF_THE_SEA);
        }

        @Override
        public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            perform(p, Effects.towards(p, null), Effects.enemiesOf(p, null));
            return true;
        }

        @Override
        public boolean castOn(org.bukkit.entity.LivingEntity caster, Player target, AbilityState st) {
            perform(caster, Effects.towards(caster, target), Effects.enemiesOf(caster, target));
            return true;
        }

        static void perform(org.bukkit.entity.LivingEntity p, Vector dir, java.util.function.Predicate<org.bukkit.entity.LivingEntity> isTarget) {
            // On avance sur l'axe dominant pour que la vague reste alignee sur la grille de blocs.
            final Vector axis = Math.abs(dir.getX()) >= Math.abs(dir.getZ())
                    ? new Vector(Math.signum(dir.getX()), 0, 0) : new Vector(0, 0, Math.signum(dir.getZ()));
            final Vector side = new Vector(-axis.getZ(), 0, axis.getX());
            final Location origin = p.getLocation().getBlock().getLocation();
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
                        build(front);
                        splash(front);
                        strike(front);
                    } catch (Exception ex) {
                        plugin().getLogger().warning("Vagues Bleues interrompues : " + ex);
                        restoreAll();
                        clearCubes();
                        cancel();
                    }
                }

                /** Pose les blocs de verre de la vague autour du front, colonne par colonne. */
                void build(int front) {
                    for (int lateral = -HALF_WIDTH + 1; lateral <= HALF_WIDTH; lateral++) {
                        int edge = Math.max(0, Math.abs(lateral - (lateral > 0 ? 1 : 0)) - (HALF_WIDTH - 3));
                        for (Cell c : PROFILE) {
                            int along = front - c.back();
                            if (along < 1) continue; // jamais sur Corrin
                            // Les deux dernieres colonnes de chaque bord sont plus basses, pour arrondir la vague.
                            int up = c.up() - edge;
                            if (up < 1) continue;
                            int x = origin.getBlockX() + axis.getBlockX() * along + side.getBlockX() * lateral;
                            int z = origin.getBlockZ() + axis.getBlockZ() * along + side.getBlockZ() * lateral;
                            int ground = groundY(x, z);
                            Block b = w.getBlockAt(x, ground + up, z);
                            if (!(b.getType().isAir() || b.isReplaceable()) || placed.containsKey(b)) continue;
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
