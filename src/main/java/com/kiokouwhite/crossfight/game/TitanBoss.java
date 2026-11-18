package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Giant;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Le Titan de Shiganshina : un mini-boss qui tombe en cent coups, et qui lance au hasard une de ses cinq attaques.
 *
 *   Charge titanesque   il s'arrete, un couloir de danger apparait devant lui, puis il fonce : 2 coeurs a qui s'y trouve
 *   Lancer de pierres   il s'arrete et jette des debris en l'air ; trois fois de suite, un debris tombe pres de chaque
 *                       joueur, la ou un cercle de danger l'annonce : 2 coeurs
 *   Regard terrifiant   il fixe les joueurs ; qui le regarde encore une seconde plus tard est petrifie
 *   Cri strident        il crie : nausee et lenteur pendant dix secondes
 *   Course du Titan     pendant dix secondes il court apres un joueur, a sa vitesse ; chaque fois qu'il le rattrape, 1 coeur
 *
 * Il casse les blocs : ce qui lui barre la route quand il marche, charge ou court, et le sol ou tombe une pierre.
 * Il ne prend pas de degats : chaque coup d'un joueur de l'equipe compte pour un (un coup par demi-seconde et par
 * joueur au plus), et une barre en haut de l'ecran dit ce qu'il en reste. Quests le fait vivre, un tick apres l'autre.
 */
final class TitanBoss {
    static final int HITS = 100;
    private static final int STUN_SECONDS = 30;
    private static final int IDLE = 0, CHARGE = 1, STONES = 2, STARE = 3, SCREAM = 4, CHASE = 5;
    private static final String[] NAMES = {"", "Charge titanesque", "Lancer de pierres", "Regard terrifiant", "Cri strident", "Course du Titan"};

    final Giant giant;
    private final BossBar bar = BossBar.bossBar(Msg.mm("<red>Titan"), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
    private final Set<UUID> viewers = new HashSet<>();
    private final Map<UUID, Integer> lastHit = new HashMap<>(), lastBite = new HashMap<>();
    private final Set<UUID> struck = new HashSet<>();
    /** Les debris en train de tomber : ou, et dans combien de ticks. */
    private final List<Object[]> stones = new ArrayList<>();
    private int hits = HITS, state = IDLE, timer, last, age, lastCrash = -100;
    private Vector dash;
    private UUID prey;

    TitanBoss(Giant giant) {
        this.giant = giant;
        giant.setAI(false);
        title();
    }

    int hitsLeft() { return hits; }

    private void title() {
        bar.name(Msg.mm("<red><bold>Titan</bold> <gray>- <white>" + hits + " <gray>coups" + (state == IDLE ? "" : " <dark_gray>| <gold>" + NAMES[state])));
        bar.progress(Math.max(0f, Math.min(1f, hits / (float) HITS)));
    }

    /** Un coup porte par ce joueur : vrai si le Titan en meurt. */
    boolean hit(Player p) {
        Integer when = lastHit.get(p.getUniqueId());
        if (when != null && age - when < 10) return false;
        lastHit.put(p.getUniqueId(), age);
        hits--;
        title();
        giant.playHurtAnimation(0);
        giant.getWorld().playSound(giant.getLocation(), Sound.ENTITY_ZOMBIE_HURT, 1.6f, 0.5f);
        giant.getWorld().spawnParticle(Particle.CRIT, p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.5)), 8, 0.3, 0.3, 0.3, 0.2);
        return hits <= 0;
    }

    /** Un coup deja porte, a reprendre quand le Titan reparait (le monde s'etait decharge). */
    void spare() { hits--; title(); }

    /** Le Titan s'en va (vaincu, ou la quete s'arrete) : sa barre disparait. */
    void remove() {
        for (UUID id : viewers) { Player p = org.bukkit.Bukkit.getPlayer(id); if (p != null) p.hideBossBar(bar); }
        viewers.clear();
        if (giant.isValid()) {
            giant.getWorld().spawnParticle(Particle.EXPLOSION, giant.getLocation().add(0, 6, 0), 12, 1.5, 4, 1.5, 0);
            giant.getWorld().playSound(giant.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.2f, 0.6f);
            giant.remove();
        }
    }

    private static boolean fights(Player p) {
        return !p.isDead() && p.getGameMode() != GameMode.SPECTATOR;
    }

    private static double flat(Location a, Location b) {
        double dx = a.getX() - b.getX(), dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Le Titan casse les blocs autour de ce point, du niveau `from` jusqu'a `height` blocs plus haut : ce qu'il traverse
     * en marchant ou en chargeant, et le cratere d'une pierre. Il ne touche ni a l'eau ni a ce qui ne se casse pas.
     */
    private void smash(Location center, double radius, int from, int height) {
        World w = center.getWorld();
        int r = (int) Math.ceil(radius), broken = 0;
        Block sample = null;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dz * dz > radius * radius) continue;
            for (int dy = from; dy < from + height; dy++) {
                Block b = w.getBlockAt(center.getBlockX() + dx, center.getBlockY() + dy, center.getBlockZ() + dz);
                Material type = b.getType();
                if (type.isAir() || b.isLiquid() || type.getHardness() < 0 || type == Material.BARRIER) continue;
                if (sample == null) sample = b;
                if (broken < 6) w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 6, 0.3, 0.3, 0.3, 0, b.getBlockData());
                b.setType(Material.AIR, false);
                broken++;
            }
        }
        if (broken > 0 && age - lastCrash >= 4) { lastCrash = age; w.playSound(center, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.4f, 0.6f); }
    }

    /** Le Titan avance de (dx, dz), en suivant le sol, et se tourne dans ce sens. Ce qui lui barre la route est casse. */
    private void step(double dx, double dz) {
        Location at = giant.getLocation().add(dx, 0, dz);
        World w = at.getWorld();
        smash(at, 2.2, 0, 12);
        for (int dy = 0; dy >= -3; dy--) {
            Block feet = w.getBlockAt(at.getBlockX(), at.getBlockY() + dy, at.getBlockZ());
            if (feet.getRelative(0, -1, 0).getType().isSolid() && feet.isPassable()) { at.setY(feet.getY()); break; }
        }
        if (dx * dx + dz * dz > 1e-6) at.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        at.setPitch(0);
        giant.teleport(at);
    }

    private void face(Location target, float most) {
        Vector to = target.toVector().subtract(giant.getLocation().toVector());
        float want = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ())), turn = ((want - giant.getLocation().getYaw()) % 360 + 540) % 360 - 180;
        giant.setRotation(giant.getLocation().getYaw() + Math.max(-most, Math.min(most, turn)), 0);
    }

    private void hurt(Player p, double amount, Vector push) {
        if (p.getGameMode() == GameMode.CREATIVE) return;
        p.damage(amount, giant);
        if (push != null) p.setVelocity(push);
    }

    private void begin(int attack) {
        state = attack;
        timer = 0;
        last = attack;
        struck.clear();
        title();
    }

    /** Un tick de sa vie. `team` : ceux qui ont la quete (ils voient sa barre). */
    void tick(List<Player> team) {
        age++;
        World w = giant.getWorld();
        Location here = giant.getLocation();
        List<Player> near = new ArrayList<>();
        for (Player p : w.getPlayers()) if (fights(p) && p.getLocation().distanceSquared(here) < 45 * 45) near.add(p);
        // sa barre : pour l'equipe de la quete, tant qu'elle est a portee
        Set<UUID> now = new HashSet<>();
        for (Player p : team) if (p.getWorld().equals(w) && p.getLocation().distanceSquared(here) < 60 * 60) now.add(p.getUniqueId());
        for (UUID id : now) if (viewers.add(id)) { Player p = org.bukkit.Bukkit.getPlayer(id); if (p != null) p.showBossBar(bar); }
        for (var it = viewers.iterator(); it.hasNext(); ) { UUID id = it.next(); if (!now.contains(id)) { Player p = org.bukkit.Bukkit.getPlayer(id); if (p != null) p.hideBossBar(bar); it.remove(); } }
        falling(w, near);
        Player target = null;
        for (Player p : near) if (target == null || p.getLocation().distanceSquared(here) < target.getLocation().distanceSquared(here)) target = p;
        if (target == null) { if (state != IDLE) { state = IDLE; timer = 0; title(); } return; }
        var random = ThreadLocalRandom.current();
        switch (state) {
            case IDLE -> {
                // Entre deux attaques : il se tourne vers le plus proche et s'en approche a pas lents.
                face(target.getLocation(), 6);
                double far = flat(target.getLocation(), here);
                if (far > 7) { Vector to = target.getLocation().toVector().subtract(here.toVector()).setY(0).normalize().multiply(0.07); step(to.getX(), to.getZ()); }
                if (++timer >= 70) { int next; do { next = 1 + random.nextInt(5); } while (next == last); begin(next); }
            }
            case CHARGE -> {
                if (timer == 0) {
                    dash = target.getLocation().toVector().subtract(here.toVector()).setY(0);
                    if (dash.lengthSquared() < 0.01) dash = here.getDirection().setY(0);
                    dash.normalize();
                    w.playSound(here, Sound.ENTITY_RAVAGER_ROAR, 2f, 0.6f);
                }
                if (timer < 30) {
                    // le couloir de danger : seize blocs devant lui, cinq de large
                    face(here.clone().add(dash), 30);
                    if (timer % 3 == 0) {
                        Vector side = new Vector(-dash.getZ(), 0, dash.getX());
                        for (double d = 1; d <= 16; d += 1) for (double s = -2.5; s <= 2.5; s += (d == 1 || d == 16 ? 0.5 : 5)) {
                            Location at = here.clone().add(dash.clone().multiply(d)).add(side.clone().multiply(s));
                            w.spawnParticle(Particle.DUST, at.getX(), ground(at) + 0.2, at.getZ(), 2, 0.15, 0, 0.15, 0, new Particle.DustOptions(Color.RED, 1.6f));
                        }
                    }
                } else if (timer < 42) {
                    // la charge : seize blocs en douze ticks
                    step(dash.getX() * 16 / 12.0, dash.getZ() * 16 / 12.0);
                    if (timer % 3 == 0) w.playSound(giant.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 2f, 0.5f);
                    for (Player p : near) if (flat(p.getLocation(), giant.getLocation()) < 3.4 && Math.abs(p.getLocation().getY() - giant.getLocation().getY()) < 6 && struck.add(p.getUniqueId())) {
                        hurt(p, 4.0, dash.clone().multiply(1.3).setY(0.5));
                        w.playSound(p.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5f, 0.5f);
                    }
                } else { state = IDLE; timer = 0; title(); break; }
                timer++;
            }
            case STONES -> {
                if (timer == 0) w.playSound(here, Sound.ENTITY_IRON_GOLEM_DAMAGE, 2f, 0.5f);
                if (timer < 20 && timer % 2 == 0)             // il arrache des debris et les jette en l'air
                    w.spawnParticle(Particle.BLOCK, here.clone().add(0, 8 + timer * 0.6, 0), 14, 1.2, 0.6, 1.2, 0.1, Material.COBBLESTONE.createBlockData());
                if (timer == 20 || timer == 45 || timer == 70) for (Player p : near) {
                    double a = random.nextDouble(Math.PI * 2), d = random.nextDouble(1.5);
                    Location at = p.getLocation().add(Math.cos(a) * d, 0, Math.sin(a) * d);
                    at.setY(ground(at));
                    stones.add(new Object[]{at, 24});
                    w.playSound(at, Sound.ENTITY_ARROW_SHOOT, 1.2f, 0.4f);
                }
                if (++timer > 100) { state = IDLE; timer = 0; title(); }
            }
            case STARE -> {
                face(target.getLocation(), 30);
                if (timer == 0) {
                    for (Player p : near) {
                        p.showTitle(Title.title(Msg.mm("<dark_red><bold>Le Titan te fixe"), Msg.mm("<white>Détourne le regard !"), Title.Times.times(Duration.ZERO, Duration.ofMillis(1100), Duration.ofMillis(200))));
                        p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1.4f);
                    }
                    w.spawnParticle(Particle.DUST, here.clone().add(0, 10.5, 0).add(here.getDirection().multiply(1.2)), 30, 0.8, 0.3, 0.8, 0, new Particle.DustOptions(Color.fromRGB(255, 40, 40), 2.5f));
                }
                if (timer == 22) for (Player p : near) {
                    if (!watches(p)) continue;
                    if (p.getGameMode() != GameMode.CREATIVE) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, STUN_SECONDS * 20, 250, true, false, true));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, STUN_SECONDS * 20, 200, true, false, false));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, STUN_SECONDS * 20, 250, true, false, false));
                    }
                    p.showTitle(Title.title(Msg.mm("<dark_red><bold>Pétrifié"), Msg.mm("<gray>" + STUN_SECONDS + " secondes")));
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f);
                }
                if (++timer > 40) { state = IDLE; timer = 0; title(); }
            }
            case SCREAM -> {
                if (timer == 0) {
                    w.playSound(here, Sound.ENTITY_ENDER_DRAGON_GROWL, 3f, 0.6f);
                    w.spawnParticle(Particle.SONIC_BOOM, here.clone().add(0, 10, 0).add(here.getDirection().multiply(2)), 3, 1, 0.5, 1, 0);
                    for (Player p : near) {
                        if (p.getLocation().distanceSquared(here) > 30 * 30 || p.getGameMode() == GameMode.CREATIVE) continue;
                        p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0, true, false, true));
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 200, 1, true, false, true));
                        Msg.actionBar(p, "<red>Le cri du Titan t'étourdit.");
                    }
                }
                if (++timer > 30) { state = IDLE; timer = 0; title(); }
            }
            case CHASE -> {
                if (timer == 0) { prey = target.getUniqueId(); w.playSound(here, Sound.ENTITY_WARDEN_ROAR, 2f, 0.8f); Msg.actionBar(target, "<red>Le Titan te court après : fuis !"); }
                Player run = org.bukkit.Bukkit.getPlayer(prey);
                if (run == null || !near.contains(run)) run = target;
                Location to = run.getLocation();
                double far = flat(to, here);
                if (far > 1.5) { Vector v = to.toVector().subtract(here.toVector()).setY(0).normalize().multiply(Math.min(0.26, far)); step(v.getX(), v.getZ()); }
                if (timer % 6 == 0) w.playSound(giant.getLocation(), Sound.ENTITY_IRON_GOLEM_STEP, 2f, 0.5f);
                // chaque fois qu'il le rattrape : un coeur (une fois par seconde au plus)
                for (Player p : near) {
                    Integer when = lastBite.get(p.getUniqueId());
                    if (flat(p.getLocation(), giant.getLocation()) < 2.8 && Math.abs(p.getLocation().getY() - giant.getLocation().getY()) < 6 && (when == null || age - when >= 20)) {
                        lastBite.put(p.getUniqueId(), age);
                        hurt(p, 2.0, null);
                        w.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1f, 0.6f);
                    }
                }
                if (++timer > 200) { state = IDLE; timer = 0; title(); }
            }
            default -> state = IDLE;
        }
    }

    /** Le joueur regarde-t-il le Titan ? (a quarante degres pres, vers ses pieds, son torse ou sa tete, sans mur entre eux) */
    private boolean watches(Player p) {
        if (!p.hasLineOfSight(giant)) return false;
        Vector look = p.getEyeLocation().getDirection();
        for (double h : new double[]{1.5, 6, 10.5}) {
            Vector to = giant.getLocation().add(0, h, 0).toVector().subtract(p.getEyeLocation().toVector());
            if (to.lengthSquared() > 0.01 && look.angle(to) < Math.toRadians(40)) return true;
        }
        return false;
    }

    private static double ground(Location at) {
        World w = at.getWorld();
        for (int dy = 3; dy >= -6; dy--) {
            Block b = w.getBlockAt(at.getBlockX(), at.getBlockY() + dy, at.getBlockZ());
            if (b.getType().isSolid() && b.getRelative(0, 1, 0).isPassable()) return b.getY() + 1;
        }
        return at.getY();
    }

    /** Les debris qui tombent : le cercle de danger au sol, la pierre qui descend, puis le choc. */
    private void falling(World w, List<Player> near) {
        for (var it = stones.iterator(); it.hasNext(); ) {
            Object[] s = it.next();
            Location at = (Location) s[0];
            int fuse = (int) s[1] - 1;
            s[1] = fuse;
            if (fuse > 0) {
                if (fuse % 2 == 0) for (int k = 0; k < 16; k++) {
                    double a = Math.PI * 2 * k / 16;
                    w.spawnParticle(Particle.DUST, at.getX() + Math.cos(a) * 2.5, at.getY() + 0.2, at.getZ() + Math.sin(a) * 2.5, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.ORANGE, 1.5f));
                }
                w.spawnParticle(Particle.BLOCK, at.clone().add(0, fuse * 0.6, 0), 8, 0.4, 0.4, 0.4, 0, Material.COBBLESTONE.createBlockData());
                continue;
            }
            it.remove();
            w.spawnParticle(Particle.EXPLOSION, at.clone().add(0, 0.5, 0), 2, 0.5, 0.2, 0.5, 0);
            w.spawnParticle(Particle.BLOCK, at.clone().add(0, 0.5, 0), 40, 1.2, 0.4, 1.2, 0.1, Material.COBBLESTONE.createBlockData());
            w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);
            smash(at, 2.0, -1, 4);                                 // le cratere : un bloc de profondeur
            for (Player p : near) if (flat(p.getLocation(), at) < 2.5 && Math.abs(p.getLocation().getY() - at.getY()) < 3) hurt(p, 4.0, new Vector(0, 0.4, 0));
        }
    }
}
