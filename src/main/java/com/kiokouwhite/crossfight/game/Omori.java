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
import org.bukkit.attribute.Attribute;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private static final int[] HITS = {38, 44, 50, 56, 63};
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
    private Zombie boss;
    private BossBar bar;
    private BukkitTask loop;
    private int phase, hits, turn, wait;
    private boolean busy;
    /** Vrai pendant une attaque (de son declenchement a sa fin, annonce non comprise) : Omori est alors intouchable. */
    private boolean attacking;
    private final Map<UUID, Long> lastHit = new HashMap<>(), asked = new HashMap<>();
    /** Vrai du dernier coup a la fermeture : Omori tombe en poussiere, puis la porte noire attend les joueurs. */
    private boolean over;
    /** La porte noire, dans l'Espace Blanc : elle apparait la ou Omori est tombe, et fait sortir. */
    private Location exit;
    private long exitSince;
    private boolean exitWarned;
    private BukkitTask dying;
    /** Ceux qui etaient la quand il est tombe : la question du pardon les attend a la sortie. */
    private final Set<UUID> winners = new HashSet<>();
    /** Le sol du combat est noir (derniere phase) ; la tache qui le noircit de proche en proche. */
    private boolean dark;
    private BukkitTask spreading;
    /** Secondes laissees pour sortir par la porte noire, avant d'etre reconduit dehors. */
    private static final int EXIT_SECONDS = 120;

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

    /** Demi-cote de l'Espace Blanc visible : au-dela, tout est blanc, et l'on ne va pas plus loin. */
    private static final int EDGE = 30;
    private final List<org.bukkit.entity.TextDisplay> veils = new ArrayList<>();

    /**
     * Tout est blanc, meme le ciel : quatre murs et un plafond de lumiere blanche, sans ombre, ferment la vue
     * autour du combat. Ce sont de grands panneaux de texte au fond blanc, tous poses au centre et deportes par
     * leur transformation, pour rester charges et visibles de partout. Le temps y est fige a midi.
     */
    private void veil() {
        World w = Bukkit.getWorld(WORLD);
        if (w == null) return;
        Location c = ArenaWorlds.center(w);
        // Personne ne sort du blanc.
        for (Player p : w.getPlayers()) {
            Location l = p.getLocation();
            double dx = l.getX() - c.getX(), dz = l.getZ() - c.getZ();
            if (Math.max(Math.abs(dx), Math.abs(dz)) <= EDGE - 4) continue;
            l.setX(c.getX() + Math.max(-(EDGE - 5), Math.min(EDGE - 5, dx)));
            l.setZ(c.getZ() + Math.max(-(EDGE - 5), Math.min(EDGE - 5, dz)));
            p.teleport(l);
        }
        veils.removeIf(v -> !v.isValid());
        if (!veils.isEmpty()) return;
        w.setGameRule(org.bukkit.GameRule.DO_DAYLIGHT_CYCLE, false);
        w.setGameRule(org.bukkit.GameRule.DO_WEATHER_CYCLE, false);
        w.setTime(6000);
        w.setStorm(false);
        // Un pave de blocs pleins, blancs sur blanc : 4 blocs de cote avant mise a l'echelle.
        String row = "\u2588".repeat(18);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 16; i++) text.append(i == 0 ? "" : "\n").append(row);
        float[][] faces = { { 0, 0, EDGE }, { 90, 0, EDGE }, { 180, 0, EDGE }, { 270, 0, EDGE }, { 0, 90, 40 } };
        // Les blocs pleins de la police ne se touchent pas : quatre couches decalees d'une demi-case bouchent les joints.
        for (float[] f : faces) for (int layer = 0; layer < 4; layer++) {
            final float sx = (layer & 1) * 6.75f, sy = (layer >> 1) * 7.5f;
            Location at = c.clone().add(0, 1, 0);
            at.setYaw(f[0]);
            at.setPitch(f[1]);
            veils.add(w.spawn(at, org.bukkit.entity.TextDisplay.class, d -> {
                d.text(net.kyori.adventure.text.Component.text(text.toString(), net.kyori.adventure.text.format.NamedTextColor.WHITE));
                d.setLineWidth(2000);
                d.setBackgroundColor(Color.fromARGB(255, 255, 255, 254));
                d.setShadowed(false);
                d.setSeeThrough(false);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setBillboard(Display.Billboard.FIXED);
                d.setViewRange(12f);
                d.setDisplayWidth(400f);
                d.setDisplayHeight(400f);
                d.setPersistent(false);
                // Le panneau part de son pied : on le centre (murs : de 40 sous le sol a 200 au-dessus), et on le recule.
                d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(sx, (f[1] == 0 ? -40 : -120) + sy, -f[2]),
                        new org.joml.Quaternionf(), new org.joml.Vector3f(60, 60, 1), new org.joml.Quaternionf()));
            }));
        }
    }

    private void unveil() {
        for (var v : veils) v.remove();
        veils.clear();
    }

    /** Ceux qui se battent dans l'Espace Blanc. */
    private List<Player> fighters() {
        List<Player> out = new ArrayList<>();
        World w = Bukkit.getWorld(WORLD);
        if (w != null) for (Player p : w.getPlayers()) if (!p.isDead() && p.getGameMode() != GameMode.SPECTATOR && !watching.contains(p.getUniqueId())) out.add(p);
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
        door = spot.getBlock().getLocation().add(0.5, 0, 0.5);
        // Une vraie porte, blanche, de deux blocs : on l'ouvre d'un clic droit.
        build(door, facing, Material.PALE_OAK_DOOR, false);
        spawnBoss();
    }

    /**
     * Pose une vraie porte de deux blocs, tournee vers ce point. "Alimentee" (sans redstone : l'etat seul), une
     * porte en chene noir prend dans le pack la texture de la porte noire (tools/build_porte.py).
     */
    private static void build(Location at, Location facing, Material type, boolean powered) {
        Vector toward = facing.toVector().subtract(at.toVector());
        BlockFace face = Math.abs(toward.getX()) > Math.abs(toward.getZ())
                ? (toward.getX() > 0 ? BlockFace.EAST : BlockFace.WEST)
                : (toward.getZ() > 0 ? BlockFace.SOUTH : BlockFace.NORTH);
        for (int h = 0; h < 2; h++) {
            var b = at.getBlock().getRelative(0, h, 0);
            b.setType(type, false);
            Door data = (Door) b.getBlockData();
            data.setHalf(h == 0 ? Bisected.Half.BOTTOM : Bisected.Half.TOP);
            data.setFacing(face);
            data.setPowered(powered);
            b.setBlockData(data, false);
        }
    }

    /** Retire la porte posee a cet endroit (si elle y est encore). */
    private static void unbuild(Location at) {
        if (at == null) return;
        for (int h = 1; h >= 0; h--) {
            var b = at.getBlock().getRelative(0, h, 0);
            if (org.bukkit.Tag.DOORS.isTagged(b.getType())) b.setType(Material.AIR, false);
        }
    }

    private static boolean isDoor(org.bukkit.block.Block clicked, Location at) {
        return at != null && (clicked.equals(at.getBlock()) || clicked.equals(at.getBlock().getRelative(0, 1, 0)));
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
        over = false;
        exit = null;
        winners.clear();
        welcomed.clear();
        floor(Material.WHITE_CONCRETE);
        phase = 0;
        hits = 0;
        turn = 0;
        wait = 20;
        busy = false;
        attacking = false;
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
        attacking = false;
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
        dress();
    }

    /** Habille Omori de son modele articule, pour de bon (un pantin ordinaire s'efface au bout de 30 secondes). */
    private void dress() {
        puppet = com.kiokouwhite.crossfight.util.Puppet.show(boss, plugin.getConfig().getString("omori.skin", "omori"), new ItemStack(Material.IRON_SWORD), "debout");
        if (puppet != null) { puppet.keep(); boss.getEquipment().clear(); }
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
        bar.setTitle("OMORI - phase " + (phase + 1) + "/" + HITS.length + " - " + (HITS[phase] - hits) + " coups" + (attacking ? " - intouchable" : ""));
        bar.setProgress(Math.max(0, Math.min(1, 1 - hits / (double) HITS[phase])));
        bar.setColor(phase == HITS.length - 1 ? BarColor.RED : BarColor.WHITE);
    }

    /** Toutes les 5 ticks : la porte rappelle comment entrer, la musique tourne, la barre suit les joueurs, Omori enchaine ses attaques. */
    private void tick() {
        if (!active()) return;
        long now = System.currentTimeMillis();
        if (over) { aftermath(now); return; }
        door.getWorld().spawnParticle(Particle.END_ROD, door.clone().add(0, 1.2, 0), 1, 0.5, 1.0, 0.5, 0.005);
        for (Player p : door.getWorld().getPlayers()) {
            // Tout pres de la porte : un rappel de ce qu'il faut faire.
            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || p.getLocation().distanceSquared(door) > 3.5 * 3.5) continue;
            if (now - asked.getOrDefault(p.getUniqueId(), 0L) < 4000) continue;
            asked.put(p.getUniqueId(), now);
            Msg.actionBar(p, "<white>Une porte blanche projette une ombre legere. <gray>Clic droit pour l'ouvrir.");
        }
        music();
        List<Player> in = fighters();
        if (in.isEmpty()) { if (boss != null) { if (puppet != null) puppet.remove(); puppet = null; boss.remove(); boss = null; } unveil(); return; }
        veil();
        if (boss == null || !boss.isValid()) summon();
        else if (puppet != null && !puppet.shown()) dress();
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
        announce(cycle[turn++ % cycle.length]);
    }

    /** Le nom de chaque attaque, tel qu'il s'annonce sous la barre d'Omori. */
    private static final Map<String, String> NAMES = Map.of("basique", "Attaque basique", "poignarder", "Poignarder", "trancher", "Trancher",
            "effacement", "Effacement", "annihilation", "Annihilation", "traumatisme", "Traumatisme", "suicide", "Suicide", "peur", "Je ne peux pas le faire");
    /** Ticks entre l'annonce d'une attaque et son declenchement. */
    private static final int WARNING = 20;
    private BossBar nameBar;

    /**
     * L'attaque qui vient s'annonce un instant avant, par son nom, dans une seconde barre sous celle d'Omori ;
     * cette barre se vide pendant le delai, et reste (vide) jusqu'a la fin de l'attaque.
     */
    private void announce(String name) {
        busy = true;
        if (nameBar == null) nameBar = Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID);
        nameBar.setTitle(NAMES.getOrDefault(name, name));
        nameBar.setProgress(1);
        for (Player p : fighters()) nameBar.addPlayer(p);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (boss == null || !boss.isValid() || nameBar == null || over) { cancel(); rest(); return; }
                t += 2;
                nameBar.setProgress(Math.max(0, 1 - t / (double) WARNING));
                if (t < WARNING) return;
                cancel();
                // L'attaque part : jusqu'a sa fin, aucun coup ne compte.
                attacking = true;
                updateBar();
                attack(name);
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    /**
     * La musique du combat, pour qui est dans l'Espace Blanc : la piste "omori" du pack complet, une boucle de jeu
     * video (tools/boucle_musique.py) : tout le morceau une fois, puis sa derniere section, sans fin. Chacun
     * l'entend depuis son debut a son entree ; elle s'arrete des qu'il sort. Sans elle, le combat est sans musique.
     */
    private void music() {
        if (over) return;
        List<Player> in = fighters();
        for (Player p : in) if (listeners.add(p.getUniqueId())) plugin.music().hold(p, "omori");
        listeners.removeIf(id -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null && in.contains(p)) return false;
            if (p != null) plugin.music().release(p);
            return true;
        });
    }

    private final java.util.Set<UUID> listeners = new java.util.HashSet<>();

    /** Un clic droit sur la porte blanche : elle s'ouvre, et l'on passe dans l'Espace Blanc. */
    @EventHandler
    public void onDoor(org.bukkit.event.player.PlayerInteractEvent e) {
        if (!active() || e.getClickedBlock() == null || e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        var clicked = e.getClickedBlock();
        // La porte noire, apres le combat : elle s'ouvre, et l'on sort de l'Espace Blanc.
        final boolean out = isDoor(clicked, exit);
        if (!out && (over || !isDoor(clicked, door))) return;
        e.setCancelled(true);
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) return;
        final Location through = out ? exit : door;
        swing(through, true);
        sfx(through, "porte", Sound.BLOCK_WOODEN_DOOR_OPEN, 1f, out ? 0.5f : 0.7f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!active()) return;
            if (p.isOnline() && p.getWorld().equals(through.getWorld()) && p.getLocation().distanceSquared(through) < 36) {
                if (out) leave(p); else if (!over) enter(p);
            }
            swing(through, false);
        }, 8L);
    }

    /** Ouvre ou referme les deux blocs d'une porte. */
    private static void swing(Location at, boolean open) {
        if (at == null) return;
        for (int h = 0; h < 2; h++) {
            var b = at.getBlock().getRelative(0, h, 0);
            if (b.getBlockData() instanceof Door d) { d.setOpen(open); b.setBlockData(d, false); }
        }
    }

    private void enter(Player p) {
        if (!active() || p.isDead()) return;
        World w = space();
        Location c = ArenaWorlds.center(w);
        double a = Math.random() * Math.PI * 2;
        Location at = c.clone().add(Math.cos(a) * 12, 0, Math.sin(a) * 12);
        at.setDirection(c.toVector().subtract(at.toVector()));
        p.teleport(at);
        p.setFallDistance(0);
        Effects.invincible(p, 2000);
        if (welcome(p)) return;
        Msg.send(p, "<white>Tu es dans l'Espace Blanc. <gray>Omori attend.");
        p.playSound(at, Sound.AMBIENT_CAVE, 1f, 0.6f);
    }

    /** Ceux qui regardent la video d'entree : le combat ne commence pour eux qu'apres. */
    private final Set<UUID> watching = new HashSet<>(), welcomed = new HashSet<>();
    /** La video d'entree : quelle image montrer a chaque dixieme de seconde (intro.txt, ecrit par tools/build_intro.py). */
    private int[] welcomeTable;

    private int[] welcomeTable() {
        if (welcomeTable != null) return welcomeTable;
        welcomeTable = new int[0];
        try (var in = plugin.getResource("intro.txt")) {
            if (in != null) for (String line : new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).split("\n")) {
                String[] p = line.trim().split("\\s+");
                if (p.length < 2 || !p[0].equals("espace_blanc")) continue;
                welcomeTable = new int[p.length - 1];
                for (int i = 1; i < p.length; i++) welcomeTable[i - 1] = Integer.parseInt(p[i]);
            }
        } catch (Exception ex) { plugin.getLogger().warning("intro.txt illisible : " + ex); }
        return welcomeTable;
    }

    /** Une image de la video : ses six tuiles sur une ligne (trois, retour en arriere, trois), un pixel repris entre deux tuiles. */
    private static String welcomeImage(int image) {
        StringBuilder b = new StringBuilder();
        for (int t = 0; t < 6; t++) {
            if (t == 3) b.append('\ue101');
            else if (t > 0) b.append('\ue100');
            b.append((char) (0xE200 + image * 6 + t));
        }
        return b.toString();
    }

    /**
     * "Welcome to White Space" : la premiere fois qu'un joueur franchit la porte, la video d'entree se joue plein
     * ecran, avec son son (pack complet : police "espace_blanc", six tuiles par image, sur un fond noir). Il ne
     * bouge pas, ne craint rien, et Omori ne s'occupe pas de lui pendant ce temps ; s'accroupir la passe. Rend faux
     * si le pack n'a pas la video : on entre alors directement.
     */
    private boolean welcome(Player p) {
        final int[] table = welcomeTable();
        if (table.length == 0 || !com.kiokouwhite.crossfight.util.Sounds.has("font.espace_blanc") || !welcomed.add(p.getUniqueId())) return false;
        final UUID id = p.getUniqueId();
        watching.add(id);
        Effects.root(p, table.length * 2 + 20);
        Effects.invincible(p, table.length * 100L + 2000);
        final String sound = com.kiokouwhite.crossfight.util.Sounds.NS + ":ability.espace_blanc";
        p.playSound(p, sound, org.bukkit.SoundCategory.MASTER, 1f, 1f);
        final net.kyori.adventure.key.Key font = net.kyori.adventure.key.Key.key("crossfight", "espace_blanc");
        final net.kyori.adventure.text.Component black = net.kyori.adventure.text.Component.text("\ue000").font(font);
        final var times = net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(500), java.time.Duration.ZERO);
        new BukkitRunnable() {
            int frame = 0;
            @Override public void run() {
                boolean skip = frame > 10 && p.isSneaking();
                if (!p.isOnline() || p.isDead() || !active() || !p.getWorld().getName().equals(WORLD) || frame >= table.length || skip) {
                    cancel();
                    watching.remove(id);
                    if (!p.isOnline()) return;
                    p.clearTitle();
                    if (skip) p.stopSound(sound, org.bukkit.SoundCategory.MASTER);
                    Effects.unroot(p);
                    Effects.invincible(p, 2000);
                    if (active() && p.getWorld().getName().equals(WORLD)) Msg.send(p, "<white>Tu es dans l'Espace Blanc. <gray>Omori attend.");
                    return;
                }
                p.showTitle(net.kyori.adventure.title.Title.title(black, net.kyori.adventure.text.Component.text(welcomeImage(table[frame])).font(font), times));
                if (frame == 0) Msg.actionBar(p, "<dark_gray>S'accroupir pour passer");
                frame++;
            }
        }.runTaskTimer(plugin, 1L, 2L);
        return true;
    }

    // ------------------------------------------------------------------ les coups portes a Omori

    /** Omori ne perd pas de vie : chaque coup qu'un joueur lui porte compte pour un (quatre par seconde au plus). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onHit(EntityDamageEvent e) {
        if (boss == null || !e.getEntity().equals(boss)) return;
        e.setCancelled(true);
        if (over || !(e instanceof EntityDamageByEntityEvent by)) return;
        Entity from = by.getDamager();
        Player p = from instanceof Player pl ? pl : from instanceof Projectile pr && pr.getShooter() instanceof Player sh ? sh : null;
        if (p == null || !p.getWorld().equals(boss.getWorld())) return;
        long now = System.currentTimeMillis();
        if (now - lastHit.getOrDefault(p.getUniqueId(), 0L) < 250) return;
        lastHit.put(p.getUniqueId(), now);
        if (attacking) {
            // Pendant ses attaques, Omori est intouchable : le coup glisse sur lui et ne compte pas.
            boss.getWorld().playSound(boss.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.5f, 1.7f);
            boss.getWorld().spawnParticle(Particle.DUST, boss.getLocation().add(0, 1.1, 0), 6, 0.3, 0.5, 0.3, 0, BLADE);
            Msg.actionBar(p, "<gray>Omori est intouchable pendant ses attaques.");
            return;
        }
        hit(1);
    }

    /** Compte des coups (aussi pour /cf omori coups). */
    public void hit(int n) {
        if (!active() || over || boss == null || !boss.isValid()) return;
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
        if (boss == null || over) return;
        phase = Math.max(0, Math.min(HITS.length - 1, n));
        // Derniere phase : le sol devient noir, a partir de lui ; il redevient blanc si l'on revient en arriere (test).
        if (phase == HITS.length - 1) darken(boss.getLocation());
        else if (dark) floor(Material.WHITE_CONCRETE);
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
        attacking = false;
        wait = 100;
        if (nameBar != null) nameBar.removeAll();
        updateBar();
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
     * yeux, de plus en plus longues, jusqu'a couvrir l'ecran (chacun ne voit que les siennes). Chaque seconde, ils
     * perdent un coeur (sans recul : ils restent cloues sur place).
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
            p.sendMessage(Msg.prefixed("<dark_gray>Omori te fixe : immobilise 10 secondes, un coeur perdu par seconde."));
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
                    // Un coeur par seconde, dix fois ; sans attaquant, donc sans recul.
                    if (t % 20 == 0) {
                        Effects.damage(p, 2, null);
                        p.spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0, INK);
                    }
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
        if (target == null || boss == null) { rest(); return; }
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

    /**
     * Omori est vaincu. Il tombe a genoux et part en poussiere ; la ou il est tombe, une porte noire apparait : on
     * sort par elle, et c'est dehors que chacun choisit de se pardonner ou non.
     */
    private void defeated() {
        over = true;
        busy = true;
        attacking = false;
        List<String> names = new ArrayList<>();
        for (Player p : fighters()) {
            winners.add(p.getUniqueId());
            names.add(p.getName());
            Effects.unroot(p);
            p.removePotionEffect(PotionEffectType.DARKNESS);
        }
        if (nameBar != null) nameBar.removeAll();
        if (bar != null) bar.removeAll();
        // Plus personne n'entre : la porte blanche s'efface (on reviendra a sa place en sortant).
        unbuild(door);
        // La musique se tait.
        for (UUID id : listeners) { Player p = Bukkit.getPlayer(id); if (p != null) plugin.music().release(p); }
        listeners.clear();
        Msg.broadcast("<white><bold>Omori est vaincu</bold> <gray>par " + String.join(", ", names) + ".");
        crumble();
    }

    /** Un grain de la poussiere d'Omori : il derive, prend de la vitesse, puis s'eteint. */
    private static final class Mote {
        final Vector pos, vel;
        final Particle.DustOptions look;
        int life;
        Mote(Vector pos, Vector vel, Particle.DustOptions look, int life) { this.pos = pos; this.vel = vel; this.look = look; this.life = life; }
    }

    /** Sa poussiere : du noir surtout (elle se voit sur le blanc), un peu de gris. */
    private static final Particle.DustOptions[] ASH = {
            new Particle.DustOptions(Color.fromRGB(14, 14, 18), 0.9f), new Particle.DustOptions(Color.fromRGB(14, 14, 18), 0.7f),
            new Particle.DustOptions(Color.fromRGB(64, 64, 72), 0.8f), new Particle.DustOptions(Color.fromRGB(150, 150, 158), 0.6f)};
    /** L'ordre dans lequel il se defait : les bras, les jambes, le buste, la tete en dernier. */
    private static final String[] ORDER = {"arm_r", "arm_l", "leg_r", "leg_l", "body", "head"};
    /** En ticks : la premiere partie s'effrite, l'ecart entre deux parties, plus rien de lui, la poussiere revient, la porte. */
    private static final int KNEEL = 34, GAP = 8, GONE = KNEEL + ORDER.length * GAP + 6, GATHER = GONE + 12, DOOR = GATHER + 30;

    /** Des grains qui quittent un volume (son milieu, ses demi-dimensions) et partent dans le vent, en montant. */
    private static void shed(List<Mote> motes, Location middle, double sx, double sy, double sz, int count, Vector wind) {
        var r = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < count; i++) {
            Vector pos = middle.toVector().add(new Vector((r.nextDouble() * 2 - 1) * sx, (r.nextDouble() * 2 - 1) * sy, (r.nextDouble() * 2 - 1) * sz));
            Vector vel = wind.clone().multiply(0.05 + r.nextDouble() * 0.09)
                    .add(new Vector((r.nextDouble() - 0.5) * 0.05, 0.02 + r.nextDouble() * 0.06, (r.nextDouble() - 0.5) * 0.05));
            motes.add(new Mote(pos, vel, ASH[r.nextInt(ASH.length)], 22 + r.nextInt(26)));
        }
    }

    /**
     * Sa mort, en six secondes et demie : il tombe a genoux, tete basse ; puis il part en poussiere, partie par
     * partie (chacune grisonne, s'effrite, et ses grains s'envolent dans son dos) ; enfin la poussiere revient,
     * tourne en se resserrant la ou il etait, et il en sort une porte noire. Sans son modele articule, il rapetisse
     * en s'effritant.
     */
    private void crumble() {
        final World w = boss.getWorld();
        final Location fell = boss.getLocation();
        boss.setCustomNameVisible(false);
        sfx(fell, "vaincu", Sound.ENTITY_WITHER_DEATH, 1f, 1.4f);
        pose("omori_vaincu", 14);
        Vector back = fell.getDirection().setY(0);
        final Vector wind = back.lengthSquared() < 1e-4 ? new Vector(1, 0, 0) : back.normalize().multiply(-1);
        // La porte : la ou il est tombe, sans sortir de la zone du combat.
        Location c = ArenaWorlds.center(w);
        int reach = EDGE - 10;
        final Location spot = new Location(w, Math.max(c.getBlockX() - reach, Math.min(c.getBlockX() + reach, fell.getBlockX())) + 0.5, c.getBlockY(),
                Math.max(c.getBlockZ() - reach, Math.min(c.getBlockZ() + reach, fell.getBlockZ())) + 0.5);
        final List<Mote> motes = new ArrayList<>();
        dying = new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (!over || door == null) { cancel(); return; }
                t++;
                var r = java.util.concurrent.ThreadLocalRandom.current();
                if (t < GONE && boss != null && boss.isValid()) {
                    if (puppet != null) {
                        // A genoux : un peu de poussiere s'echappe deja de lui.
                        if (t >= 14 && t < KNEEL && t % 2 == 0) shed(motes, puppet.center("body"), 0.25, 0.35, 0.15, 2, wind);
                        for (int i = 0; i < ORDER.length; i++) {
                            int at = KNEEL + i * GAP;
                            if (t == at - 4) puppet.tint(ORDER[i], Color.fromRGB(86, 86, 94));
                            if (t != at) continue;
                            boolean head = ORDER[i].equals("head"), limb = !head && !ORDER[i].equals("body");
                            Location part = puppet.center(ORDER[i]);
                            shed(motes, part, limb ? 0.11 : 0.22, head ? 0.22 : 0.33, limb ? 0.11 : head ? 0.22 : 0.12, head ? 48 : 34, wind);
                            // Le couteau part avec le bras qui le tenait.
                            if (i == 0) puppet.held(null);
                            puppet.look(ORDER[i], null);
                            w.playSound(part, Sound.BLOCK_SAND_BREAK, 0.9f, 0.55f + i * 0.05f);
                        }
                    } else if (t >= KNEEL) {
                        double k = (t - KNEEL) / (double) (GONE - KNEEL);
                        var scale = boss.getAttribute(Attribute.SCALE);
                        if (scale != null) scale.setBaseValue(Math.max(0.1, 1 - 0.9 * k));
                        shed(motes, boss.getLocation().add(0, 1 - 0.8 * k, 0), 0.3, 0.8 * (1 - k) + 0.1, 0.3, 5, wind);
                        if (t % GAP == 0) w.playSound(boss.getLocation(), Sound.BLOCK_SAND_BREAK, 0.9f, 0.6f);
                    }
                }
                if (t == GONE) {
                    if (puppet != null) puppet.remove();
                    puppet = null;
                    if (boss != null) boss.remove();
                    boss = null;
                }
                if (t >= GATHER && t < DOOR) {
                    // La poussiere revient : six filets tournent en se resserrant, et la porte se dessine de bas en haut.
                    double k = (t - GATHER) / (double) (DOOR - GATHER), radius = 3.2 * (1 - k) + 0.3;
                    for (int j = 0; j < 6; j++) {
                        double a = t * 0.45 + j * Math.PI / 3;
                        Vector inward = new Vector(-Math.cos(a), 0, -Math.sin(a)), around = new Vector(-Math.sin(a), 0, Math.cos(a));
                        motes.add(new Mote(spot.toVector().add(new Vector(Math.cos(a) * radius, 0.1 + r.nextDouble() * 2.0, Math.sin(a) * radius)),
                                inward.multiply(0.09).add(around.multiply(0.06)), ASH[r.nextInt(2)], 9));
                    }
                    if (t % 2 == 0) w.spawnParticle(Particle.DUST, spot.clone().add(0, 0.2 + k * 1.8, 0), 6, 0.3, 0.12, 0.3, 0, ASH[0]);
                    if (t == GATHER) w.playSound(spot, Sound.BLOCK_SAND_FALL, 1.5f, 0.5f);
                }
                if (t == DOOR) appear(spot);
                for (Iterator<Mote> it = motes.iterator(); it.hasNext(); ) {
                    Mote m = it.next();
                    if (--m.life <= 0) { it.remove(); continue; }
                    m.vel.multiply(1.025);
                    m.pos.add(m.vel);
                    if (m.life % 2 == 0) w.spawnParticle(Particle.DUST, m.pos.getX(), m.pos.getY(), m.pos.getZ(), 1, 0, 0, 0, 0, m.look);
                }
                if (t > DOOR && motes.isEmpty()) { cancel(); dying = null; }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** La porte noire se dresse la ou il est tombe, tournee vers le joueur le plus proche. */
    private void appear(Location spot) {
        World w = spot.getWorld();
        List<Player> in = fighters();
        Player near = null;
        for (Player p : in) if (near == null || p.getLocation().distanceSquared(spot) < near.getLocation().distanceSquared(spot)) near = p;
        exit = spot.clone();
        build(exit, near != null ? near.getLocation() : spot.clone().add(0, 0, 1), Material.DARK_OAK_DOOR, true);
        exitSince = System.currentTimeMillis();
        exitWarned = false;
        w.spawnParticle(Particle.DUST, exit.clone().add(0, 1, 0), 80, 0.4, 1.0, 0.4, 0, INK);
        sfx(exit, "porte", Sound.BLOCK_WOODEN_DOOR_CLOSE, 1f, 0.5f);
        for (Player p : in) Msg.send(p, "<dark_gray><bold>Une porte noire est apparue.</bold> <gray>Clic droit dessus pour sortir de l'Espace Blanc.");
    }

    /** Apres sa chute : la porte noire rappelle comment sortir ; au dernier sorti, ou le temps ecoule, tout se referme. */
    private void aftermath(long now) {
        List<Player> in = fighters();
        if (in.isEmpty()) { close(); return; }
        veil();
        if (exit == null) return;
        exit.getWorld().spawnParticle(Particle.DUST, exit.clone().add(0, 1.1, 0), 2, 0.4, 0.9, 0.4, 0, ASH[1]);
        for (Player p : in) {
            if (p.getLocation().distanceSquared(exit) > 3.5 * 3.5 || now - asked.getOrDefault(p.getUniqueId(), 0L) < 4000) continue;
            asked.put(p.getUniqueId(), now);
            Msg.actionBar(p, "<dark_gray>Une porte noire. <gray>Clic droit pour sortir.");
        }
        long left = EXIT_SECONDS * 1000L - (now - exitSince);
        if (left <= 30000 && !exitWarned) {
            exitWarned = true;
            for (Player p : in) Msg.send(p, "<gray>La porte noire se refermera dans 30 secondes.");
        }
        if (left <= 0) {
            for (Player p : in) leave(p);
            close();
        }
    }

    /** Sortir par la porte noire : on revient la ou etait la porte blanche, et la question du pardon attend dehors. */
    private void leave(Player p) {
        Location home = door != null ? door.clone() : plugin.game().lobbySpot();
        Effects.unroot(p);
        p.removePotionEffect(PotionEffectType.DARKNESS);
        p.teleport(home);
        p.setFallDistance(0);
        sfx(home, "porte", Sound.BLOCK_WOODEN_DOOR_CLOSE, 1f, 0.6f);
        if (winners.remove(p.getUniqueId())) Bukkit.getScheduler().runTaskLater(plugin, () -> forgive(p), 20L);
    }

    // ------------------------------------------------------------------ le sol

    /** Repeint d'un coup tout le sol du combat : blanc au depart et a la fin, noir a la derniere phase. */
    private void floor(Material m) {
        if (spreading != null) { spreading.cancel(); spreading = null; }
        dark = m == Material.BLACK_CONCRETE;
        World w = Bukkit.getWorld(WORLD);
        if (w == null) return;
        Location c = ArenaWorlds.center(w);
        int y = c.getBlockY() - 1;
        for (int dx = -EDGE - 2; dx <= EDGE + 2; dx++) for (int dz = -EDGE - 2; dz <= EDGE + 2; dz++) {
            var b = w.getBlockAt(c.getBlockX() + dx, y, c.getBlockZ() + dz);
            if (b.getType() != m) b.setType(m, false);
        }
    }

    /** Derniere phase : le noir part de sous Omori et gagne tout le sol, de proche en proche (deux blocs par tick). */
    private void darken(Location from) {
        if (dark) return;
        dark = true;
        final World w = from.getWorld();
        Location c = ArenaWorlds.center(w);
        final int y = c.getBlockY() - 1, ox = from.getBlockX(), oz = from.getBlockZ();
        final List<int[]> cells = new ArrayList<>();
        for (int dx = -EDGE - 2; dx <= EDGE + 2; dx++) for (int dz = -EDGE - 2; dz <= EDGE + 2; dz++) {
            int x = c.getBlockX() + dx, z = c.getBlockZ() + dz;
            cells.add(new int[]{x, z, (x - ox) * (x - ox) + (z - oz) * (z - oz)});
        }
        cells.sort(java.util.Comparator.comparingInt(cell -> cell[2]));
        w.playSound(from, Sound.BLOCK_SCULK_SPREAD, 2f, 0.5f);
        spreading = new BukkitRunnable() {
            int i = 0;
            double reach = 0;
            @Override public void run() {
                reach += 2;
                while (i < cells.size() && cells.get(i)[2] <= reach * reach) {
                    w.getBlockAt(cells.get(i)[0], y, cells.get(i)[1]).setType(Material.BLACK_CONCRETE, false);
                    i++;
                }
                if (i >= cells.size()) { cancel(); spreading = null; }
            }
        }.runTaskTimer(plugin, 0L, 1L);
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

    /** L'evenement se referme : plus de portes ni de boss, le sol redevient blanc ; ceux qui sont encore dans l'Espace Blanc en sortent. */
    public void close() {
        if (loop != null) loop.cancel();
        if (dying != null) { dying.cancel(); dying = null; }
        Location home = door;
        unbuild(door);
        unbuild(exit);
        exit = null;
        over = false;
        exitWarned = false;
        winners.clear();
        if (dark || spreading != null) floor(Material.WHITE_CONCRETE);
        if (nameBar != null) nameBar.removeAll();
        nameBar = null;
        unveil();
        for (UUID id : listeners) { Player p = Bukkit.getPlayer(id); if (p != null) plugin.music().release(p); }
        listeners.clear();
        if (puppet != null) puppet.remove();
        puppet = null;
        if (boss != null) boss.remove();
        if (bar != null) bar.removeAll();
        boss = null;
        bar = null;
        door = null;
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
        // La porte blanche ne se casse pas.
        if (active() && e.getBlock().getWorld().equals(door.getWorld()) && e.getBlock().getX() == door.getBlockX() && e.getBlock().getZ() == door.getBlockZ()
                && e.getBlock().getY() >= door.getBlockY() && e.getBlock().getY() <= door.getBlockY() + 1) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (e.getBlock().getWorld().getName().equals(WORLD)) e.setCancelled(true);
    }

    @EventHandler
    public void onBurn(EntityCombustEvent e) {
        if (e.getEntity().equals(boss)) e.setCancelled(true);
    }

    /** Qui se reconnecte dans l'Espace Blanc alors que l'evenement est referme n'y reste pas. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || active() || !p.getWorld().getName().equals(WORLD)) return;
            p.teleport(plugin.game().isLobby() ? plugin.game().lobbySpot() : plugin.ferox().centre());
            p.setFallDistance(0);
        }, 2L);
    }
}
