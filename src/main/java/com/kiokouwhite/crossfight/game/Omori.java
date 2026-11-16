package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.ArenaWorlds;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.ChoiceMenu;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Fx;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * L'evenement Omori : a sa minute (60 par defaut), une porte blanche apparait a un endroit au hasard de la fosse
 * de l'arene, dont les herses se levent. Qui l'ouvre arrive dans l'Espace Blanc, ou attend le boss Omori. Il se
 * bat en cinq phases (30, 35, 40, 45 puis 50 coups a lui porter), chacune avec son roulement d'attaques, une
 * toutes les cinq secondes. Vaincu, il laisse a chaque joueur present un choix : se pardonner (une vie de plus)
 * ou non (une vie de moins, mais Force III). Les attaques et leurs chiffres sont ceux du document de conception.
 */
public final class Omori implements Listener {

    public static final String WORLD = "crossfight_espace_blanc";
    /** Coups a porter pour vaincre chaque phase. */
    private static final int[] HITS = {30, 35, 40, 45, 50};
    /** Le roulement d'attaques de chaque phase. */
    private static final String[][] ATTACKS = {
            {"basique", "poignarder", "trancher"},
            {"effacement", "poignarder", "basique"},
            {"annihilation", "basique", "poignarder"},
            {"traumatisme", "poignarder", "basique"},
            {"suicide", "peur"}};
    private static final Particle.DustOptions INK = new Particle.DustOptions(Color.fromRGB(12, 12, 16), 1.6f), RED = new Particle.DustOptions(Color.fromRGB(200, 20, 30), 1.4f);

    private final CrossfightPlugin plugin;
    private Location door;
    private ItemDisplay doorShow;
    private Zombie boss;
    private BossBar bar;
    private BukkitTask loop;
    private int phase, hits, turn, wait;
    private boolean busy;
    private final Map<UUID, Long> lastHit = new HashMap<>(), asked = new HashMap<>();

    public Omori(CrossfightPlugin plugin) { this.plugin = plugin; }

    public boolean active() { return door != null; }

    /**
     * Un bruitage de l'evenement : celui du jeu OMORI si le pack l'a ("ability.omori_<cle>", pack complet,
     * tools/build_omori_sons.py), sinon ce bruitage de Minecraft.
     */
    private static void sfx(Location at, String key, Sound fallback, float volume, float pitch) {
        if (com.kiokouwhite.crossfight.util.Sounds.has("ability.omori_" + key)) com.kiokouwhite.crossfight.util.Sounds.at(at, "ability.omori_" + key);
        else at.getWorld().playSound(at, fallback, volume, pitch);
    }

    private World space() {
        return ArenaWorlds.get(WORLD, new ArenaWorlds.InfiniteFloorGenerator(Material.WHITE_CONCRETE), World.Environment.NORMAL);
    }

    /** Ceux qui se battent dans l'Espace Blanc. */
    private List<Player> fighters() {
        List<Player> out = new ArrayList<>();
        World w = Bukkit.getWorld(WORLD);
        if (w != null) for (Player p : w.getPlayers()) if (!p.isDead() && p.getGameMode() != GameMode.SPECTATOR) out.add(p);
        return out;
    }

    private Player nearest() {
        Player best = null;
        for (Player p : fighters()) if (best == null || p.getLocation().distanceSquared(boss.getLocation()) < best.getLocation().distanceSquared(boss.getLocation())) best = p;
        return best;
    }

    // ------------------------------------------------------------------ la porte

    /** L'evenement commence : les herses se levent, la porte blanche apparait dans la fosse, Omori attend. */
    public void open() {
        if (active()) return;
        plugin.ferox().gates(true);
        // La porte, debout, tournee vers le centre de la fosse.
        place(plugin.ferox().randomSpot(), plugin.ferox().centre());
        Msg.broadcast("<white><bold>Evenement :</bold> une porte blanche est apparue dans l'arene, en <gray>" + door.getBlockX() + " " + door.getBlockY() + " " + door.getBlockZ()
                + "<white>. Les herses sont levees. Derriere la porte : l'Espace Blanc.");
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 1f, 0.6f);
        loop = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    /** Pose la porte a cet endroit, tournee vers ce point, et prepare Omori. */
    private void place(Location spot, Location facing) {
        door = spot;
        Location at = door.clone().add(0, 1.6, 0);
        at.setDirection(facing.toVector().subtract(door.toVector()).setY(0));
        doorShow = Fx.show(at, Fx.imageOr("porte_blanche", Color.WHITE, Material.PALE_OAK_DOOR), 3.2f, false);
        doorShow.setViewRange(6f);
        spawnBoss();
    }

    /** /cf test omori : la porte devant le testeur, dans la zone de test, sans annonce ni arene. */
    public void openForTest(Player p) {
        if (active()) close();
        Vector ahead = p.getLocation().getDirection().setY(0);
        if (ahead.lengthSquared() < 0.01) ahead = new Vector(0, 0, 1);
        Location spot = p.getLocation().add(ahead.normalize().multiply(5));
        spot.setY(p.getLocation().getY());
        place(spot, p.getLocation());
        loop = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    private void spawnBoss() {
        space();
        phase = 0;
        hits = 0;
        turn = 0;
        wait = 20;
        busy = false;
        bar = Bukkit.createBossBar("OMORI", BarColor.WHITE, BarStyle.SEGMENTED_10);
        updateBar();
    }

    /**
     * Omori lui-meme n'existe que tant qu'un joueur est dans l'Espace Blanc (un monde vide ne garde pas ses
     * entites) : il reparait la ou il en etait, phase et coups compris.
     */
    private void summon() {
        World w = space();
        Location c = ArenaWorlds.center(w);
        busy = false;
        wait = 60;
        boss = w.spawn(c, Zombie.class, z -> {
            z.setAdult();
            z.setAI(false);
            z.setSilent(true);
            z.setPersistent(false);
            z.setRemoveWhenFarAway(false);
            z.setCanPickupItems(false);
            z.customName(Msg.mm("<white><bold>OMORI"));
            z.setCustomNameVisible(true);
            var eq = z.getEquipment();
            // Tout en noir et blanc, un couteau a la main.
            eq.setHelmet(dyed(Material.LEATHER_HELMET, Color.fromRGB(20, 20, 24)));
            eq.setChestplate(dyed(Material.LEATHER_CHESTPLATE, Color.WHITE));
            eq.setLeggings(dyed(Material.LEATHER_LEGGINGS, Color.fromRGB(20, 20, 24)));
            eq.setBoots(dyed(Material.LEATHER_BOOTS, Color.WHITE));
            eq.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        });
        // Avec son skin dans le pack ("skin.omori", ou celui que nomme omori.skin dans config.yml), Omori est un
        // personnage articule, son couteau a la main ; sinon il garde cette tenue noire et blanche.
        puppet = com.kiokouwhite.crossfight.util.Puppet.show(boss, plugin.getConfig().getString("omori.skin", "omori"), new ItemStack(Material.IRON_SWORD), "debout");
        if (puppet != null) boss.getEquipment().clear();
    }

    private com.kiokouwhite.crossfight.util.Puppet puppet;

    private static ItemStack dyed(Material m, Color c) {
        ItemStack it = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        meta.setColor(c);
        it.setItemMeta(meta);
        return it;
    }

    private void updateBar() {
        if (bar == null) return;
        bar.setTitle("OMORI - phase " + (phase + 1) + "/" + HITS.length + " - " + (HITS[phase] - hits) + " coups");
        bar.setProgress(Math.max(0, Math.min(1, 1 - hits / (double) HITS[phase])));
        bar.setColor(phase == HITS.length - 1 ? BarColor.RED : BarColor.WHITE);
    }

    /** Toutes les 5 ticks : la porte propose d'entrer, la barre suit les joueurs, Omori enchaine ses attaques. */
    private void tick() {
        if (!active()) return;
        long now = System.currentTimeMillis();
        door.getWorld().spawnParticle(Particle.END_ROD, door.clone().add(0, 1.6, 0), 1, 0.5, 1.2, 0.5, 0.005);
        for (Player p : door.getWorld().getPlayers()) {
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.getLocation().distanceSquared(door) > 2.6 * 2.6) continue;
            if (now - asked.getOrDefault(p.getUniqueId(), 0L) < 6000 || p.getOpenInventory().getTopInventory().getType() != org.bukkit.event.inventory.InventoryType.CRAFTING) continue;
            asked.put(p.getUniqueId(), now);
            ChoiceMenu.open(p, "<dark_gray>Une porte blanche projette une ombre legere.", List.of(
                    new ChoiceMenu.Option(Material.PALE_OAK_DOOR, "Ouvrir la porte", List.of("Entrer dans l'Espace Blanc"), () -> enter(p)),
                    new ChoiceMenu.Option(Material.BARRIER, "Ne rien faire", List.of(), () -> { })));
        }
        List<Player> in = fighters();
        if (in.isEmpty()) { if (boss != null) { if (puppet != null) puppet.remove(); puppet = null; boss.remove(); boss = null; } return; }
        if (boss == null || !boss.isValid()) summon();
        for (Player p : new ArrayList<>(bar.getPlayers())) if (!in.contains(p)) bar.removePlayer(p);
        for (Player p : in) {
            bar.addPlayer(p);
            // Cinquieme phase : la vision de tous est obstruee.
            if (phase == HITS.length - 1) Effects.refresh(p, PotionEffectType.DARKNESS, 0, 60);
        }
        Player target = nearest();
        if (target == null) return;
        Location look = boss.getLocation();
        look.setDirection(target.getLocation().toVector().subtract(look.toVector()).setY(0));
        if (!busy) boss.setRotation(look.getYaw(), 0);
        if (busy || (wait -= 5) > 0) return;
        String[] cycle = ATTACKS[phase];
        attack(cycle[turn++ % cycle.length]);
    }

    private void enter(Player p) {
        if (!active() || p.isDead()) return;
        World w = space();
        Location c = ArenaWorlds.center(w);
        double a = Math.random() * Math.PI * 2;
        Location at = c.clone().add(Math.cos(a) * 12, 0, Math.sin(a) * 12);
        at.setDirection(c.toVector().subtract(at.toVector()));
        sfx(p.getLocation(), "porte", Sound.BLOCK_WOODEN_DOOR_OPEN, 1f, 0.7f);
        p.teleport(at);
        p.setFallDistance(0);
        Effects.invincible(p, 2000);
        Msg.send(p, "<white>Tu es dans l'Espace Blanc. <gray>Omori attend.");
        p.playSound(at, Sound.AMBIENT_CAVE, 1f, 0.6f);
    }

    // ------------------------------------------------------------------ les coups portes a Omori

    /** Omori ne perd pas de vie : chaque coup qu'un joueur lui porte compte pour un (quatre par seconde au plus). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(EntityDamageEvent e) {
        if (boss == null || !e.getEntity().equals(boss)) return;
        e.setCancelled(true);
        if (!(e instanceof EntityDamageByEntityEvent by)) return;
        Entity from = by.getDamager();
        Player p = from instanceof Player pl ? pl : from instanceof Projectile pr && pr.getShooter() instanceof Player sh ? sh : null;
        if (p == null || !p.getWorld().equals(boss.getWorld())) return;
        long now = System.currentTimeMillis();
        if (now - lastHit.getOrDefault(p.getUniqueId(), 0L) < 250) return;
        lastHit.put(p.getUniqueId(), now);
        hit(1);
    }

    /** Compte des coups (aussi pour /cf omori coups). */
    public void hit(int n) {
        if (!active() || boss == null || !boss.isValid()) return;
        hits += n;
        boss.playHurtAnimation(0);
        sfx(boss.getLocation(), "coup", Sound.ENTITY_PLAYER_HURT, 0.8f, 0.6f);
        boss.getWorld().spawnParticle(Particle.DUST, boss.getLocation().add(0, 1.1, 0), 8, 0.3, 0.5, 0.3, 0, INK);
        while (boss != null && hits >= HITS[phase]) {
            hits -= HITS[phase];
            if (phase == HITS.length - 1) { defeated(); return; }
            setPhase(phase + 1);
        }
        updateBar();
    }

    public void setPhase(int n) {
        if (boss == null) return;
        phase = Math.max(0, Math.min(HITS.length - 1, n));
        hits = Math.min(hits, HITS[phase] - 1);
        turn = 0;
        wait = 60;
        World w = boss.getWorld();
        sfx(boss.getLocation(), "phase", Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.6f);
        w.spawnParticle(Particle.DUST, boss.getLocation().add(0, 1, 0), 60, 1.2, 1.2, 1.2, 0, phase == HITS.length - 1 ? RED : INK);
        for (Player p : fighters()) Msg.send(p, phase == HITS.length - 1 ? "<dark_red><bold>Omori : cinquieme phase.</bold> <gray>Tout devient noir." : "<white><bold>Omori : phase " + (phase + 1) + ".");
        updateBar();
    }

    // ------------------------------------------------------------------ les attaques

    /** Apres une attaque, cinq secondes avant la suivante. */
    private void rest() {
        busy = false;
        wait = 100;
    }

    private void hurt(Player p, double hearts) {
        Effects.damage(p, hearts * 2, boss);
    }

    private static final Particle.DustOptions BLADE = new Particle.DustOptions(Color.fromRGB(250, 250, 255), 0.55f), BLADE_EDGE = new Particle.DustOptions(Color.fromRGB(20, 20, 24), 0.45f),
            BLADE_RED = new Particle.DustOptions(Color.fromRGB(215, 25, 35), 0.6f);

    /** Change la pose d'Omori (quand il a son modele articule). */
    private void pose(String name, int ticks) {
        if (puppet != null && boss != null && boss.isValid()) puppet.pose(name, ticks);
    }

    private boolean present(Player p) {
        return boss != null && boss.isValid() && p.isOnline() && !p.isDead() && p.getWorld().equals(boss.getWorld());
    }

    /**
     * Le croissant d'un coup de couteau : un trait fin et blanc, borde de noir, qui s'abat en travers de la cible
     * en quatre ticks, du haut a droite vers le bas a gauche (rouge pour un coup de poignard).
     */
    private void crescent(Location at, Vector facing, boolean red) { crescent(at, facing, red, false, false); }

    /** Le meme croissant ; {@code flip} le retourne (du haut a gauche vers le bas a droite), {@code quiet} le rend muet : pour une rafale. */
    private void crescent(Location at, Vector facing, boolean red, boolean flip, boolean quiet) {
        World w = at.getWorld();
        Vector flat = facing.clone().setY(0);
        final Vector f = (flat.lengthSquared() < 0.01 ? new Vector(0, 0, 1) : flat).normalize();
        Vector side = f.clone().crossProduct(new Vector(0, 1, 0)).normalize().multiply(flip ? -1 : 1);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t >= 4) { cancel(); return; }
                for (int i = 0; i < 12; i++) {
                    // Un arc de cercle pose de biais devant la cible ; il se dessine par quarts.
                    double u = (t + i / 12.0) / 4, a = Math.toRadians(-70 + 150 * u), thick = Math.sin(u * Math.PI);
                    Vector p = side.clone().multiply(Math.cos(a) * 1.5 - 0.5).add(new Vector(0, -Math.sin(a) * 1.5 + 0.3, 0));
                    Location l = at.clone().add(p).subtract(f.clone().multiply(0.4));
                    w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, red ? BLADE_RED : BLADE);
                    if (thick > 0.4) w.spawnParticle(Particle.DUST, l.clone().add(side.clone().multiply(0.09)).add(0, 0.07, 0), 1, 0, 0, 0, 0, BLADE_EDGE);
                }
                t++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
        if (!quiet) sfx(at, "basique", Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, red ? 0.6f : 1.5f);
    }

    /**
     * Omori fonce : il file vers sa cible (qu'il suit si elle bouge), ou vers un point, a deux blocs par tick, le
     * buste couche, dans une trainee d'encre ; puis {@code then}.
     */
    private void dash(Player target, Location point, Runnable then) {
        pose("omori_fonce", 2);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.9f, 1.4f);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (boss == null || !boss.isValid() || (target != null && !present(target))) { cancel(); if (boss != null && boss.isValid()) then.run(); return; }
                Location from = boss.getLocation();
                Location goal = target != null ? target.getLocation() : point;
                Vector to = goal.toVector().subtract(from.toVector());
                double stop = target != null ? 1.5 : 0.1, dist = to.length();
                if (dist <= stop + 0.05 || t++ > 16) { cancel(); then.run(); return; }
                Vector step = to.clone().normalize().multiply(Math.min(2.0, dist - stop));
                Location next = from.clone().add(step);
                next.setDirection(to.clone().setY(0).lengthSquared() < 0.01 ? from.getDirection() : to.clone().setY(0));
                next.setPitch(0);
                boss.teleport(next);
                Fx.line(from.clone().add(0, 1, 0), next.clone().add(0, 1, 0), 0.35, INK);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Retour au centre de l'Espace Blanc, puis repos. */
    private void home() {
        if (boss == null || !boss.isValid()) { rest(); return; }
        dash(null, ArenaWorlds.center(boss.getWorld()), () -> { pose("debout", 4); rest(); });
    }

    /**
     * Un coup au corps a corps : Omori regarde sa cible, leve son couteau, lui fonce dessus et frappe (le croissant
     * du coup, puis {@code then}), avant de revenir a sa place.
     */
    private void lunge(Player target, String strike, boolean red, Runnable then) {
        busy = true;
        pose("omori_garde", 4);
        boss.getWorld().playSound(boss.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 0.5f, 1.8f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!present(target)) { home(); return; }
            dash(target, null, () -> {
                if (present(target) && target.getLocation().distanceSquared(boss.getLocation()) < 16) {
                    pose(strike, 2);
                    boss.swingMainHand();
                    crescent(target.getLocation().add(0, 1, 0), target.getLocation().toVector().subtract(boss.getLocation().toVector()), red);
                    then.run();
                }
                Bukkit.getScheduler().runTaskLater(plugin, this::home, 10L);
            });
        }, 9L);
    }

    /**
     * Poignarder : Omori fonce sur sa cible et l'attrape de la main gauche (elle ne peut plus bouger), arme son
     * couteau, puis l'enfonce lentement, pendant une seconde et demie ; alors seulement viennent les 3 coeurs, le
     * Wither et la Lenteur. Il la relache et retourne a sa place.
     */
    private void stab(Player target) {
        busy = true;
        pose("omori_garde", 4);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!present(target)) { home(); return; }
            dash(target, null, () -> {
                if (!present(target) || target.getLocation().distanceSquared(boss.getLocation()) > 16) { home(); return; }
                World w = boss.getWorld();
                // La prise : la victime est figee, tournee de force vers lui.
                Effects.root(target, 46);
                pose("omori_attrape", 4);
                sfx(target.getLocation(), "attrape", Sound.ENTITY_WARDEN_ATTACK_IMPACT, 0.7f, 1.6f);
                Effects.say(target, "<dark_gray>Omori t'a attrape.");
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        if (!present(target)) { cancel(); home(); return; }
                        t++;
                        // Il reste colle a elle.
                        Location hold = boss.getLocation();
                        hold.setDirection(target.getLocation().toVector().subtract(hold.toVector()).setY(0));
                        boss.setRotation(hold.getYaw(), 0);
                        if (t == 12) {
                            // Le couteau s'enfonce lentement : la pose se fait sur trente ticks.
                            pose("omori_coup", 30);
                            w.playSound(boss.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 0.6f);
                        }
                        if (t >= 12 && t < 42 && t % 3 == 0) {
                            // La lame avance vers elle, peu a peu, en rouge.
                            Location from = boss.getLocation().add(0, 1.15, 0), to = target.getLocation().add(0, 1.05, 0);
                            Vector path = to.toVector().subtract(from.toVector());
                            Fx.line(from, from.clone().add(path.multiply((t - 12) / 30.0)), 0.12, BLADE_RED);
                            if (t % 9 == 0) w.playSound(target.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.7f + t * 0.01f);
                        }
                        if (t < 42) return;
                        cancel();
                        hurt(target, 3);
                        Effects.give(target, PotionEffectType.WITHER, 0, 100);
                        Effects.give(target, PotionEffectType.SLOWNESS, 4, 100);
                        Effects.say(target, "<dark_gray>Omori te poignarde.");
                        sfx(target.getLocation(), "poignarde", Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1f, 0.5f);
                        w.spawnParticle(Particle.DUST, target.getLocation().add(0, 1.05, 0), 26, 0.25, 0.3, 0.25, 0, RED);
                        Effects.unroot(target);
                        Bukkit.getScheduler().runTaskLater(plugin, Omori.this::home, 12L);
                    }
                }.runTaskTimer(plugin, 1L, 1L);
            });
        }, 9L);
    }

    /**
     * Trancher : Omori tremble sur place, de plus en plus fort, puis frappe en rafale, le couteau qui va et vient ;
     * les croissants de ses coups s'abattent sur chaque joueur, en tous sens. 2 coeurs et Wither 2 secondes a tous.
     */
    private void hack() {
        busy = true;
        pose("omori_garde", 4);
        World w = boss.getWorld();
        Location stand = boss.getLocation();
        boolean gameSound = com.kiokouwhite.crossfight.util.Sounds.has("ability.omori_rafale");
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (boss == null || !boss.isValid()) { cancel(); rest(); return; }
                t++;
                if (t <= 20) {
                    // Le tremblement : de petites secousses, de plus en plus larges, un battement qui s'accelere.
                    double shake = 0.03 + t * 0.006;
                    Location l = stand.clone().add((Math.random() - 0.5) * 2 * shake, 0, (Math.random() - 0.5) * 2 * shake);
                    l.setYaw(stand.getYaw() + (float) ((Math.random() - 0.5) * 10));
                    boss.teleport(l);
                    if (t % Math.max(2, 8 - t / 3) == 0) w.playSound(stand, Sound.ENTITY_WARDEN_HEARTBEAT, 1.6f, 0.8f + t * 0.04f);
                    return;
                }
                if (t == 21) {
                    boss.teleport(stand);
                    if (gameSound) com.kiokouwhite.crossfight.util.Sounds.at(stand, "ability.omori_rafale");
                }
                if (t <= 45) {
                    // La rafale : un coup toutes les deux ticks, le bras qui remonte et retombe.
                    if (t % 2 == 1) {
                        int n = (t - 21) / 2;
                        pose(n % 2 == 0 ? "omori_tranche" : "omori_garde", 2);
                        boss.swingMainHand();
                        if (!gameSound) w.playSound(stand, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, 1.3f + (n % 3) * 0.25f);
                        for (Player p : fighters()) {
                            Vector toward = p.getLocation().toVector().subtract(stand.toVector());
                            // Chaque coup tombe un peu a cote du precedent, d'un cote puis de l'autre.
                            Location at = p.getLocation().add((Math.random() - 0.5) * 0.9, 0.7 + Math.random() * 0.7, (Math.random() - 0.5) * 0.9);
                            crescent(at, toward, false, n % 2 == 1, true);
                        }
                    }
                    if (t == 33) for (Player p : fighters()) {
                        hurt(p, 2);
                        Effects.give(p, PotionEffectType.WITHER, 0, 40);
                    }
                    return;
                }
                cancel();
                pose("debout", 6);
                rest();
            }
        }.runTaskTimer(plugin, 6L, 1L);
    }

    /**
     * Une main faite de blocs : paume, quatre doigts, pouce et poignet, a cette taille, tournee de {@code turn}.
     * Tous ses morceaux sont au meme endroit (leur decalage est dans leur transformation) : on la deplace d'un bloc.
     */
    private List<org.bukkit.entity.BlockDisplay> hand(Location at, float size, org.joml.Quaternionf turn, Material skin, Material tips, int glide) {
        // x, y, z du coin, puis largeur, hauteur, epaisseur ; les doigts pointent vers +Y.
        float[][] boxes = {{-1.2f, 0, -0.35f, 2.4f, 2.2f, 0.7f}, {-0.8f, -1.8f, -0.3f, 1.6f, 1.8f, 0.6f},
                {-1.15f, 2.2f, -0.28f, 0.5f, 1.5f, 0.56f}, {-0.55f, 2.2f, -0.28f, 0.5f, 2.0f, 0.56f}, {0.05f, 2.2f, -0.28f, 0.5f, 1.9f, 0.56f}, {0.65f, 2.2f, -0.28f, 0.5f, 1.4f, 0.56f},
                {1.2f, 0.5f, -0.28f, 0.6f, 1.5f, 0.56f}};
        List<org.bukkit.entity.BlockDisplay> out = new ArrayList<>();
        for (int i = 0; i < boxes.length; i++) {
            float[] b = boxes[i];
            boolean tip = i >= 2;
            org.joml.Vector3f offset = turn.transform(new org.joml.Vector3f(b[0], b[1], b[2]).mul(size));
            out.add(at.getWorld().spawn(at, org.bukkit.entity.BlockDisplay.class, d -> {
                d.setPersistent(false);
                d.setBlock((tip ? tips : skin).createBlockData());
                d.setBrightness(new Display.Brightness(15, 15));
                d.setViewRange(4f);
                d.setTeleportDuration(glide);
                d.setTransformation(new org.bukkit.util.Transformation(offset, new org.joml.Quaternionf(turn), new org.joml.Vector3f(b[3], b[4], b[5]).mul(size), new org.joml.Quaternionf()));
            }));
        }
        return out;
    }

    private static void move(List<org.bukkit.entity.BlockDisplay> hand, Location to) {
        for (var d : hand) if (d.isValid()) d.teleport(to);
    }

    /**
     * Effacement : deux mains rouges sortent du sol de part et d'autre de chaque joueur, se referment sur lui (il
     * ne peut plus bouger), puis le jettent dans les airs, a 10 blocs : 3 coeurs.
     */
    private void grab() {
        busy = true;
        pose("omori_croix", 5);
        World w = boss.getWorld();
        w.playSound(boss.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1f, 1.4f);
        List<Player> caught = fighters();
        Map<Player, List<List<org.bukkit.entity.BlockDisplay>>> hands = new HashMap<>();
        for (Player p : caught) {
            Location l = p.getLocation();
            Effects.root(p, 34);
            Effects.say(p, "<red>Des mains rouges t'attrapent.");
            sfx(l, "mains", Sound.ENTITY_WARDEN_EMERGE, 0.6f, 1.6f);
            List<List<org.bukkit.entity.BlockDisplay>> pair = new ArrayList<>();
            // Une main de chaque cote, doigts vers le haut, la paume tournee vers le joueur ; elles partent de sous le sol.
            pair.add(hand(l.clone().add(1.5, -3.2, 0), 0.5f, new org.joml.Quaternionf().rotationY((float) Math.toRadians(90)), Material.RED_CONCRETE, Material.RED_NETHER_BRICKS, 10));
            pair.add(hand(l.clone().add(-1.5, -3.2, 0), 0.5f, new org.joml.Quaternionf().rotationY((float) Math.toRadians(-90)), Material.RED_CONCRETE, Material.RED_NETHER_BRICKS, 10));
            hands.put(p, pair);
        }
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                for (var e : hands.entrySet()) {
                    Player p = e.getKey();
                    if (!p.isOnline()) continue;
                    Location l = p.getLocation();
                    if (t == 2) { move(e.getValue().get(0), l.clone().add(1.5, 0.4, 0)); move(e.getValue().get(1), l.clone().add(-1.5, 0.4, 0)); }
                    // Elles se referment.
                    if (t == 14) { move(e.getValue().get(0), l.clone().add(0.45, 0.4, 0)); move(e.getValue().get(1), l.clone().add(-0.45, 0.4, 0)); w.playSound(l, Sound.ENTITY_WARDEN_ATTACK_IMPACT, 0.8f, 1.2f); }
                    if (t >= 14 && t < 26 && t % 3 == 0) w.spawnParticle(Particle.DUST, l.clone().add(0, 1, 0), 6, 0.4, 0.5, 0.4, 0, RED);
                    // Elles le jettent.
                    if (t == 26) {
                        Effects.unroot(p);
                        if (present(p)) { Effects.launchUp(p, 10); hurt(p, 3); }
                        move(e.getValue().get(0), l.clone().add(0.45, 4.5, 0));
                        move(e.getValue().get(1), l.clone().add(-0.45, 4.5, 0));
                        sfx(l, "jette", Sound.ENTITY_BREEZE_WIND_BURST, 1f, 0.7f);
                    }
                }
                if (t < 40) return;
                cancel();
                for (var pair : hands.values()) for (var h : pair) for (var d : h) d.remove();
                pose("debout", 5);
                rest();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * Suicide : une surface rouge se referme autour d'Omori, de plus en plus dense, puis eclate en fils rouges
     * qui partent en tous sens et transpercent chaque joueur : ejectes a 10 blocs, Darkness, 5 coeurs.
     */
    private void burst() {
        busy = true;
        pose("omori_contre_lui", 8);
        World w = boss.getWorld();
        sfx(boss.getLocation(), "enveloppe", Sound.ENTITY_WARDEN_SONIC_CHARGE, 1f, 0.6f);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (boss == null || !boss.isValid()) { cancel(); rest(); return; }
                t++;
                Location c = boss.getLocation().add(0, 1, 0);
                if (t <= 30) {
                    // La surface : une sphere de points rouges, qui se resserre et se remplit.
                    double radius = 2.6 - t * 0.03;
                    for (int i = 0; i < 10 + t * 2; i++) {
                        double az = Math.random() * Math.PI * 2, el = Math.acos(2 * Math.random() - 1);
                        w.spawnParticle(Particle.DUST, c.clone().add(Math.sin(el) * Math.cos(az) * radius, Math.cos(el) * radius, Math.sin(el) * Math.sin(az) * radius), 1, 0, 0, 0, 0, RED);
                    }
                    if (t % Math.max(2, 9 - t / 4) == 0) w.playSound(c, Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.6f + t * 0.03f);
                    return;
                }
                cancel();
                // L'eclatement : des fils dans toutes les directions, et un vers chaque joueur.
                sfx(c, "eclate", Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.6f);
                w.playSound(c, Sound.BLOCK_GLASS_BREAK, 1.2f, 0.5f);
                for (int i = 0; i < 46; i++) {
                    double az = Math.random() * Math.PI * 2, el = Math.acos(2 * Math.random() - 1), len = 8 + Math.random() * 14;
                    Fx.line(c, c.clone().add(Math.sin(el) * Math.cos(az) * len, Math.abs(Math.cos(el)) * len * 0.5, Math.sin(el) * Math.sin(az) * len), 0.3, BLADE_RED);
                }
                for (Player p : fighters()) {
                    Fx.line(c, p.getLocation().add(0, 1, 0), 0.15, BLADE_RED);
                    Effects.knockbackFrom(p, boss.getLocation(), 10);
                    Effects.give(p, PotionEffectType.DARKNESS, 0, 200);
                    hurt(p, 5);
                    w.spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0, RED);
                }
                Bukkit.getScheduler().runTaskLater(plugin, () -> { pose("debout", 6); rest(); }, 20L);
            }
        }.runTaskTimer(plugin, 6L, 1L);
    }

    private static final Particle.DustOptions HAIR = new Particle.DustOptions(Color.fromRGB(6, 6, 9), 0.5f);

    /**
     * "Je ne peux pas le faire" : Omori reste immobile et fixe les joueurs. Pendant 10 secondes ils ne peuvent
     * plus bouger et leur regard est ramene de force sur lui ; des meches de cheveux noirs leur tombent devant les
     * yeux, de plus en plus longues, jusqu'a couvrir l'ecran (chacun ne voit que les siennes).
     */
    private void stare() {
        busy = true;
        pose("debout", 6);
        World w = boss.getWorld();
        sfx(boss.getLocation(), "fixe", Sound.ENTITY_WARDEN_ROAR, 1.2f, 0.5f);
        List<Player> held = fighters();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (boss != null && boss.isValid()) sfx(boss.getLocation(), "cheveux", Sound.BLOCK_WOOL_PLACE, 1f, 0.5f); }, 12L);
        // Les meches de chacun : leur place en travers de l'ecran, et leur longueur finale.
        double[][] strands = new double[22][];
        for (int i = 0; i < strands.length; i++) strands[i] = new double[]{-0.75 + 1.5 * i / (strands.length - 1.0) + (Math.random() - 0.5) * 0.05, 0.55 + Math.random() * 0.65, Math.random() * 12};
        for (Player p : held) {
            Effects.root(p, 200);
            Effects.say(p, "<dark_gray>Je ne peux pas le faire...");
            p.sendMessage(Msg.prefixed("<dark_gray>Omori te fixe : immobilise 10 secondes."));
        }
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (boss == null || !boss.isValid() || t++ >= 200) {
                    cancel();
                    for (Player p : held) if (p.isOnline()) Effects.unroot(p);
                    if (boss != null && boss.isValid()) { pose("debout", 6); rest(); } else rest();
                    return;
                }
                Location eyes = boss.getLocation().add(0, 1.62, 0);
                if (t % 20 == 1) w.playSound(eyes, Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.5f);
                for (Player p : held) {
                    if (!present(p)) continue;
                    // Le regard est ramene sur lui, a chaque tick.
                    p.lookAt(eyes.getX(), eyes.getY(), eyes.getZ(), io.papermc.paper.entity.LookAnchor.EYES);
                    if (t % 2 != 0) continue;
                    Location eye = p.getEyeLocation();
                    Vector look = eyes.toVector().subtract(eye.toVector()).normalize();
                    Vector side = look.clone().crossProduct(new Vector(0, 1, 0));
                    if (side.lengthSquared() < 0.01) side = new Vector(1, 0, 0);
                    side.normalize();
                    Vector up = side.clone().crossProduct(look).normalize();
                    Location plane = eye.clone().add(look.clone().multiply(0.62));
                    for (double[] st : strands) {
                        // Chaque meche descend du haut de l'ecran, a son rythme, en ondulant un peu.
                        double grown = Math.max(0, Math.min(1, (t - st[2]) / 60.0)) * st[1];
                        for (double y = 0; y < grown; y += 0.07) {
                            double sway = Math.sin(y * 7 + st[0] * 9 + t * 0.05) * 0.02;
                            p.spawnParticle(Particle.DUST, plane.clone().add(side.clone().multiply(st[0] + sway)).add(up.clone().multiply(0.48 - y)), 1, 0, 0, 0, 0, HAIR);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void attack(String name) {
        Player target = nearest();
        if (target == null || boss == null) return;
        World w = boss.getWorld();
        switch (name) {
            // Attaque basique : il regarde le plus proche, lui fonce dessus et le tranche.
            case "basique" -> lunge(target, "omori_tranche", false, () -> hurt(target, 2));
            // Poignarder : il vient sur sa cible, l'attrape, puis enfonce lentement son couteau.
            case "poignarder" -> stab(target);
            // Trancher : il tremble, puis lache une rafale de coups qui tranchent tout le monde.
            case "trancher" -> hack();
            // Effacement : des mains rouges sortent du sol, attrapent chaque joueur et le jettent dans les airs.
            case "effacement" -> grab();
            case "annihilation" -> { pose("omori_leve", 6); hands(); }
            // Traumatisme : il charge le plus proche et l'ejecte en le percutant.
            case "traumatisme" -> {
                busy = true;
                pose("omori_garde", 3);
                w.playSound(boss.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.7f, 1.6f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!present(target)) { home(); return; }
                    dash(target, null, () -> {
                        if (present(target) && target.getLocation().distanceSquared(boss.getLocation()) < 16) {
                            pose("omori_haut", 2);
                            Effects.launchUp(target, 15);
                            Effects.give(target, PotionEffectType.DARKNESS, 0, 200);
                            Effects.say(target, "<dark_gray>Traumatisme.");
                            sfx(target.getLocation(), "charge", Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.6f);
                            w.spawnParticle(Particle.DUST, target.getLocation().add(0, 1, 0), 40, 0.5, 0.6, 0.5, 0, INK);
                            Fx.line(target.getLocation(), target.getLocation().add(0, 5, 0), 0.25, INK);
                        }
                        Bukkit.getScheduler().runTaskLater(plugin, Omori.this::home, 12L);
                    });
                }, 7L);
            }
            // Suicide : une surface rouge l'enveloppe, puis eclate en fils qui blessent tout le monde.
            case "suicide" -> burst();
            // "Je ne peux pas le faire" : il te fixe ; immobile, force de le regarder, des cheveux te tombent devant les yeux.
            case "peur" -> stare();
            default -> rest();
        }
    }

    /**
     * Annihilation : pendant 10 secondes, toutes les 2 secondes, une main sanglante tombe sur chaque joueur. Une
     * cible rouge se dessine au sol la ou elle va tomber, avec des bips de plus en plus rapides et aigus : il
     * reste une seconde et demie pour s'ecarter. 2 coeurs a qui est encore dessous.
     */
    private void hands() {
        busy = true;
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (boss == null || !boss.isValid() || n++ >= 5) { cancel(); pose("debout", 6); rest(); return; }
                for (Player p : fighters()) fall(p, p.getLocation());
            }
        }.runTaskTimer(plugin, 0L, 40L);
    }

    private void fall(Player warned, Location mark) {
        World w = mark.getWorld();
        Location ground = mark.clone();
        ground.setY(Math.floor(mark.getY()) + 0.08);
        // La main, a plat, paume vers le bas, doigts vers l'avant : elle attend en l'air puis s'abat.
        sfx(ground, "mains", Sound.ENTITY_WARDEN_EMERGE, 0.5f, 1.8f);
        var falling = hand(ground.clone().add(0, 16, -1.4), 0.9f, new org.joml.Quaternionf().rotationX((float) Math.toRadians(90)), Material.NETHER_WART_BLOCK, Material.RED_NETHER_BRICKS, 7);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                if (t <= 30) {
                    // La cible : deux cercles et une croix, qui se resserrent ; le bip s'accelere.
                    if (t % 2 == 0) {
                        double r = 2.4 - t * 0.012;
                        for (int i = 0; i < 28; i++) {
                            double a = i * Math.PI / 14;
                            w.spawnParticle(Particle.DUST, ground.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r), 1, 0, 0, 0, 0, RED);
                            if (i % 2 == 0) w.spawnParticle(Particle.DUST, ground.clone().add(Math.cos(a) * r * 0.5, 0, Math.sin(a) * r * 0.5), 1, 0, 0, 0, 0, RED);
                        }
                        for (double k = -r; k <= r; k += 0.4) {
                            w.spawnParticle(Particle.DUST, ground.clone().add(k, 0, 0), 1, 0, 0, 0, 0, BLADE_RED);
                            w.spawnParticle(Particle.DUST, ground.clone().add(0, 0, k), 1, 0, 0, 0, 0, BLADE_RED);
                        }
                    }
                    int every = t < 12 ? 6 : t < 22 ? 3 : 2;
                    if (t % every == 0) {
                        w.playSound(ground, Sound.BLOCK_NOTE_BLOCK_PLING, 1.4f, 0.8f + t * 0.04f);
                        if (warned.isOnline() && warned.getLocation().distanceSquared(ground) <= 2.4 * 2.4) warned.sendActionBar(Msg.mm("<red><bold>! DANGER !</bold>"));
                    }
                    if (t == 24) move(falling, ground.clone().add(0, 0, -1.4));
                    return;
                }
                cancel();
                sfx(ground, "ecrase", Sound.ENTITY_IRON_GOLEM_ATTACK, 1.2f, 0.5f);
                w.playSound(ground, Sound.BLOCK_HONEY_BLOCK_BREAK, 1.2f, 0.5f);
                w.spawnParticle(Particle.DUST, ground.clone().add(0, 0.4, 0), 60, 1.3, 0.3, 1.3, 0, RED);
                w.spawnParticle(Particle.BLOCK, ground.clone().add(0, 0.3, 0), 40, 1.2, 0.2, 1.2, 0, Material.NETHER_WART_BLOCK.createBlockData());
                if (boss != null) for (Player q : fighters()) if (q.getLocation().distanceSquared(ground) <= 2.4 * 2.4) hurt(q, 2);
                Bukkit.getScheduler().runTaskLater(plugin, () -> { for (var d : falling) d.remove(); }, 12L);
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // ------------------------------------------------------------------ la fin

    /** Omori est vaincu : chacun choisit de se pardonner ou non, puis revient dans l'arene. */
    private void defeated() {
        List<Player> winners = fighters();
        World w = boss.getWorld();
        sfx(boss.getLocation(), "vaincu", Sound.ENTITY_WITHER_DEATH, 1f, 1.4f);
        w.spawnParticle(Particle.DUST, boss.getLocation().add(0, 1, 0), 200, 1.5, 2, 1.5, 0, INK);
        Location home = door.clone();
        close();
        List<String> names = new ArrayList<>();
        for (Player p : winners) names.add(p.getName());
        Msg.broadcast("<white><bold>Omori est vaincu</bold> <gray>par " + String.join(", ", names) + ".");
        for (Player p : winners) {
            p.teleport(home);
            p.setFallDistance(0);
            Effects.unroot(p);
            p.removePotionEffect(PotionEffectType.DARKNESS);
            Bukkit.getScheduler().runTaskLater(plugin, () -> forgive(p), 20L);
        }
    }

    private void forgive(Player p) {
        if (!p.isOnline()) return;
        var cf = plugin.players().get(p);
        ChoiceMenu.open(p, "<dark_gray>Te pardonnes-tu ?", List.of(
                new ChoiceMenu.Option(Material.WHITE_TULIP, "Me pardonner", List.of("Tu gagnes une vie supplementaire."), () -> {
                    cf.addLife(1);
                    Msg.broadcast("<white>" + p.getName() + " se pardonne <gray>et gagne une vie (" + cf.lives() + ").");
                    sfx(p.getLocation(), "pardon", Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
                }),
                new ChoiceMenu.Option(Material.IRON_SWORD, "Ne pas me pardonner", List.of("Tu perds une vie, mais tu gagnes Force III", "pour le reste de la partie.", cf.lives() <= 1 ? "<red>C'est ta derniere vie : tu serais elimine." : ""), () -> {
                    Boosts.set(p, "omori", new Boosts.Boost(3, 0, 0, 0));
                    Msg.broadcast("<dark_gray>" + p.getName() + " ne se pardonne pas : <red>Force III<dark_gray>, une vie de moins.");
                    plugin.lives().loseLife(p, "Omori");
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.6f, 0.7f);
                })),
                // Fermer le menu sans choisir : on se pardonne.
                q -> { cf.addLife(1); Msg.send(q, "<white>Sans reponse, tu te pardonnes : une vie de plus (" + cf.lives() + ")."); });
    }

    /** L'evenement se referme : plus de porte ni de boss ; ceux qui sont encore dans l'Espace Blanc en sortent. */
    public void close() {
        if (loop != null) loop.cancel();
        Location home = door;
        if (doorShow != null) doorShow.remove();
        if (puppet != null) puppet.remove();
        puppet = null;
        if (boss != null) boss.remove();
        if (bar != null) bar.removeAll();
        boss = null;
        bar = null;
        door = null;
        doorShow = null;
        World w = Bukkit.getWorld(WORLD);
        if (w != null) for (Player p : w.getPlayers()) {
            Effects.unroot(p);
            p.teleport(home != null ? home : plugin.game().lobbySpot());
        }
    }

    /** Dans l'Espace Blanc, on ne casse ni ne pose rien, et Omori ne brule pas au soleil. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getWorld().getName().equals(WORLD)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlock().getWorld().getName().equals(WORLD)) e.setCancelled(true);
    }

    @EventHandler
    public void onBurn(EntityCombustEvent e) {
        if (e.getEntity().equals(boss)) e.setCancelled(true);
    }
}
