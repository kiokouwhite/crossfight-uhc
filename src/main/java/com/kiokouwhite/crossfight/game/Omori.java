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

    /** Le geste d'un coup : le bras tendu, puis retour au repos. */
    private void strike() {
        boss.swingMainHand();
        if (puppet == null) return;
        puppet.pose("omori_coup", 3);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (puppet != null && boss != null && boss.isValid()) puppet.pose("debout", 5); }, 12L);
    }

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
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_WOODEN_DOOR_OPEN, 1f, 0.7f);
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
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.8f, 0.6f);
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
        w.playSound(boss.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 2f, 0.6f);
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

    /** Un trait d'encre d'Omori a sa cible : l'annonce d'un coup, une demi-seconde avant. */
    private void slash(Player target, Runnable then) {
        busy = true;
        Location from = boss.getLocation().add(0, 1.2, 0);
        strike();
        Fx.line(from, target.getLocation().add(0, 1, 0), 0.4, INK);
        boss.getWorld().playSound(from, Sound.ITEM_TRIDENT_THROW, 1f, 0.6f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (boss != null && boss.isValid() && target.isOnline() && !target.isDead() && target.getWorld().equals(boss.getWorld())) {
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
                target.getWorld().spawnParticle(Particle.SWEEP_ATTACK, target.getLocation().add(0, 1, 0), 2, 0.3, 0.3, 0.3, 0);
                then.run();
            }
            rest();
        }, 10L);
    }

    private void attack(String name) {
        Player target = nearest();
        if (target == null || boss == null) return;
        World w = boss.getWorld();
        switch (name) {
            case "basique" -> slash(target, () -> hurt(target, 2));
            case "poignarder" -> slash(target, () -> {
                hurt(target, 3);
                Effects.give(target, PotionEffectType.WITHER, 0, 100);
                Effects.give(target, PotionEffectType.SLOWNESS, 4, 100);
                Effects.say(target, "<dark_gray>Omori te poignarde.");
            });
            case "trancher" -> {
                busy = true;
                strike();
                w.playSound(boss.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.8f, 1.6f);
                for (Player p : fighters()) {
                    Fx.line(boss.getLocation().add(0, 1.2, 0), p.getLocation().add(0, 1, 0), 0.5, INK);
                    hurt(p, 2);
                    Effects.give(p, PotionEffectType.WITHER, 0, 40);
                }
                Bukkit.getScheduler().runTaskLater(plugin, this::rest, 10L);
            }
            case "effacement" -> {
                busy = true;
                w.playSound(boss.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
                Fx.burst(boss.getLocation().add(0, 0.2, 0), new Vector(0, 1, 0), "vent", Color.WHITE, 2f, 26f, 10, 60);
                for (Player p : fighters()) {
                    Effects.knockbackFrom(p, boss.getLocation(), 10);
                    hurt(p, 3);
                }
                Bukkit.getScheduler().runTaskLater(plugin, this::rest, 10L);
            }
            case "annihilation" -> hands();
            case "traumatisme" -> slash(target, () -> {
                Effects.launchUp(target, 15);
                Effects.give(target, PotionEffectType.DARKNESS, 0, 200);
                Effects.say(target, "<dark_gray>Traumatisme.");
            });
            case "suicide" -> {
                busy = true;
                w.playSound(boss.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.6f);
                w.spawnParticle(Particle.DUST, boss.getLocation().add(0, 1, 0), 120, 6, 2, 6, 0, RED);
                for (Player p : fighters()) {
                    Effects.knockbackFrom(p, boss.getLocation(), 10);
                    Effects.give(p, PotionEffectType.DARKNESS, 0, 200);
                    hurt(p, 5);
                }
                Bukkit.getScheduler().runTaskLater(plugin, this::rest, 10L);
            }
            case "peur" -> {
                busy = true;
                w.playSound(boss.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.2f, 0.5f);
                for (Player p : fighters()) {
                    Effects.root(p, 200);
                    Effects.say(p, "<dark_gray>Je ne peux pas le faire...");
                    p.sendMessage(Msg.prefixed("<dark_gray>Tu es effraye : immobilise 10 secondes."));
                }
                Bukkit.getScheduler().runTaskLater(plugin, this::rest, 200L);
            }
            default -> rest();
        }
    }

    /** Annihilation : pendant 10 secondes, toutes les 2 secondes, une main noire tombe sur chaque joueur. */
    private void hands() {
        busy = true;
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (boss == null || !boss.isValid() || n++ >= 5) { cancel(); rest(); return; }
                for (Player p : fighters()) {
                    Location mark = p.getLocation();
                    World w = mark.getWorld();
                    // L'ombre de la main au sol, puis la main qui s'abat une seconde plus tard.
                    for (int i = 0; i < 24; i++) w.spawnParticle(Particle.DUST, mark.clone().add(Math.cos(i * 0.262) * 2, 0.1, Math.sin(i * 0.262) * 2), 1, 0, 0, 0, 0, RED);
                    var hand = w.spawn(mark.clone().add(-0.9, 12, -0.9), org.bukkit.entity.BlockDisplay.class, d -> {
                        d.setPersistent(false);
                        d.setBlock(Material.BLACK_CONCRETE.createBlockData());
                        d.setBrightness(new Display.Brightness(15, 15));
                        d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.Quaternionf(), new org.joml.Vector3f(1.8f, 3.4f, 1.8f), new org.joml.Quaternionf()));
                        d.setTeleportDuration(18);
                    });
                    Bukkit.getScheduler().runTaskLater(plugin, () -> hand.teleport(mark.clone().add(-0.9, 0, -0.9)), 2L);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        hand.remove();
                        w.playSound(mark, Sound.ENTITY_IRON_GOLEM_ATTACK, 1f, 0.5f);
                        w.spawnParticle(Particle.DUST, mark.clone().add(0, 0.4, 0), 40, 1.2, 0.3, 1.2, 0, INK);
                        if (boss == null) return;
                        for (Player q : fighters()) if (q.getLocation().distanceSquared(mark) <= 2.2 * 2.2) hurt(q, 2);
                    }, 21L);
                }
            }
        }.runTaskTimer(plugin, 0L, 40L);
    }

    // ------------------------------------------------------------------ la fin

    /** Omori est vaincu : chacun choisit de se pardonner ou non, puis revient dans l'arene. */
    private void defeated() {
        List<Player> winners = fighters();
        World w = boss.getWorld();
        w.playSound(boss.getLocation(), Sound.ENTITY_WITHER_DEATH, 1f, 1.4f);
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
                    p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
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
