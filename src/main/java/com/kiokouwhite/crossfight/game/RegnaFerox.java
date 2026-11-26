package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * L'evenement Regna Ferox : a sa minute (30 par defaut), chaque joueur se voit proposer un duel d'equipes dans
 * l'arene de Ferox. Une equipe est inscrite quand tous ses membres en vie ont accepte ; les deux premieres
 * inscrites sont emmenees dans l'arene, qui se dresse sur la map des le debut de la partie. L'equipe qui met l'autre a terre gagne : chacun de ses joueurs recoit le
 * contenu d'une Lootbox de tier IV ; chaque joueur de l'equipe battue perd une vie. Tomber dans l'arene ne coute
 * rien d'autre : on y regarde la fin du combat, puis tout le monde revient la ou il etait.
 */
public final class RegnaFerox implements Listener {

    /** Secondes pour s'inscrire. Le combat, lui, n'a pas de limite de temps. */
    private static final int ASK_SECONDS = 60;
    /** L'arene : rayon de la fosse, rayon hors tout, hauteur du mur de la fosse, de sa paroi invisible, de l'enceinte, des piliers. */
    private static final int PIT = 56, OUT = 92, RING = 8, DOME = 20, WALL_OUT = 32, TOWER = 66;
    /** Rayon de la mosaique, distance au centre des grilles (sur chaque axe) et des braseros. */
    private static final int MOSAIC = 40, GRATE = 33, FIRES = 49;
    /** Hauteur des piliers de l'enceinte, qui depassent du toit (TOWER est le point le plus haut a degager). */
    private static final int PILLAR = 42;
    /** Demi-largeur des galeries d'entree et hauteur de leurs herses. */
    private static final int GALLERY = 3, GATE = 6;

    private enum Phase { IDLE, ASKING, FIGHT }

    private final CrossfightPlugin plugin;
    private Phase phase = Phase.IDLE;
    private final Set<UUID> accepted = new HashSet<>();
    /** Les equipes inscrites, dans l'ordre ou elles l'ont ete. */
    private final Set<Team> ready = new LinkedHashSet<>();
    private Team teamA, teamB;
    private final Set<UUID> fighters = new HashSet<>(), down = new HashSet<>();
    private final Map<UUID, Location> back = new HashMap<>();
    private final Map<UUID, GameMode> modes = new HashMap<>();
    private BukkitTask timer;

    public RegnaFerox(CrossfightPlugin plugin) { this.plugin = plugin; }

    /** Vrai pour un joueur en train de se battre dans l'arene (pas encore a terre). */
    public boolean fighting(Player p) {
        return phase == Phase.FIGHT && fighters.contains(p.getUniqueId()) && !down.contains(p.getUniqueId());
    }

    /** Les joueurs d'une equipe qui comptent : en ligne, en vie, pas en attente de reapparition. */
    private List<Player> active(Team t) {
        List<Player> out = new ArrayList<>();
        for (Player p : t.onlineMembers()) {
            CfPlayer cf = plugin.players().get(p);
            if (cf.isAlive() && !cf.isWaitingRespawn() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR) out.add(p);
        }
        return out;
    }

    // ------------------------------------------------------------------ l'inscription

    /** La proposition, dans le chat de tous les joueurs en vie. */
    public void ask() {
        if (phase != Phase.IDLE) return;
        if (plugin.teams().alive().size() < 2) { Msg.broadcast("<gold><bold>Evenement :</bold> Regna Ferox n'ouvre pas son arene : il faut au moins deux equipes."); return; }
        phase = Phase.ASKING;
        accepted.clear();
        ready.clear();
        Component offer = Msg.prefixed("<gold><bold>Evenement : Regna Ferox ouvre son arene !</bold>")
                .append(Component.newline())
                .append(Msg.mm("<gray>Un duel d'equipes : les vainqueurs gagnent chacun une <gold>Lootbox de tier IV</gold>, les vaincus perdent une vie. Les deux premieres equipes dont tous les membres acceptent y vont."))
                .append(Component.newline())
                .append(Msg.mm("  <green><bold>[Participer]</bold></green>").clickEvent(ClickEvent.runCommand("/cf ferox oui")).hoverEvent(Msg.mm("<green>Clique pour t'inscrire")))
                .append(Msg.mm("   <red><bold>[Refuser]</bold></red>").clickEvent(ClickEvent.runCommand("/cf ferox non")).hoverEvent(Msg.mm("<red>Clique pour refuser")))
                .append(Msg.mm("   <dark_gray>(" + ASK_SECONDS + " secondes)"));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.players().get(p).team() == null) continue;
            p.sendMessage(offer);
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 0.6f, 1.2f);
        }
        timer = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (phase != Phase.ASKING) return;
            phase = Phase.IDLE;
            Msg.broadcast("<gold>Regna Ferox referme son arene : il fallait deux equipes inscrites" + (ready.isEmpty() ? "." : ", une seule l'etait."));
        }, ASK_SECONDS * 20L);
    }

    /** La reponse d'un joueur a la proposition. */
    public void answer(Player p, boolean yes) {
        if (phase != Phase.ASKING) { Msg.send(p, "<red>Regna Ferox ne recrute pas en ce moment."); return; }
        Team team = plugin.players().get(p).team();
        if (team == null || !active(team).contains(p)) { Msg.send(p, "<red>Il faut etre en vie et dans une equipe pour participer."); return; }
        if (!yes) {
            accepted.remove(p.getUniqueId());
            if (ready.remove(team)) for (Player m : team.onlineMembers()) Msg.send(m, "<yellow>" + p.getName() + " se retire : ton equipe n'est plus inscrite a Regna Ferox.");
            else for (Player m : team.onlineMembers()) Msg.send(m, "<yellow>" + p.getName() + " refuse Regna Ferox.");
            return;
        }
        accepted.add(p.getUniqueId());
        List<Player> members = active(team);
        int in = 0;
        for (Player m : members) if (accepted.contains(m.getUniqueId())) in++;
        if (in < members.size()) {
            for (Player m : team.onlineMembers()) Msg.send(m, "<green>" + p.getName() + " veut aller a Regna Ferox <gray>(" + in + "/" + members.size() + " de ton equipe).");
            return;
        }
        if (!ready.add(team)) return;
        Msg.broadcast("<gold>" + team.coloredName() + " <gold>est inscrite a Regna Ferox <gray>(" + ready.size() + "/2).");
        if (ready.size() >= 2) {
            var it = ready.iterator();
            start(it.next(), it.next());
        }
    }

    // ------------------------------------------------------------------ le combat

    /**
     * L'arene est sur la map avant meme le lancement de la partie : une salle massive et fermee (voir raise), au
     * centre de la map (0, 0), posee sur le terrain. Elle est batie au demarrage du serveur et a chaque nouvelle
     * map ; sa hauteur est retenue par monde dans config.yml, pour la retrouver sans la rebatir.
     */
    public Location build() {
        World w = plugin.game().world();
        String key = "ferox.arene-ouverte." + w.getName();
        // L'arene n'est plus au centre de la map (un autre lieu l'occupe) : sur une map neuve, elle est batie a un
        // endroit tire de la graine, a 450-650 blocs du centre. Une map qui a deja la sienne la garde ou elle est.
        int cx, cz;
        if (plugin.getConfig().isSet(key + "-x")) { cx = plugin.getConfig().getInt(key + "-x"); cz = plugin.getConfig().getInt(key + "-z", 0); }
        else {
            java.util.Random r = new java.util.Random(w.getSeed() ^ 0xFE40L);
            int border = plugin.game().rules().value("bordure");
            double far = Math.max(0, Math.min(450 + r.nextInt(200), border - 220)), a = r.nextDouble() * Math.PI * 2;
            cx = far < 250 ? 0 : (int) Math.round(Math.cos(a) * far);
            cz = far < 250 ? 0 : (int) Math.round(Math.sin(a) * far);
        }
        int y = plugin.getConfig().getInt(key, Integer.MIN_VALUE);
        // Une arene d'une version precedente (sans galeries) : on la refait sur place, a la meme hauteur.
        int before = plugin.getConfig().getInt("ferox.arene-fermee." + w.getName(), Integer.MIN_VALUE);
        if (y == Integer.MIN_VALUE && before != Integer.MIN_VALUE && plugin.getConfig().getInt("ferox.arene-fermee." + w.getName() + "-x", cx) == cx) {
            y = before;
            raise(plugin, w, cx, y, cz);
            plugin.getConfig().set(key, y);
            plugin.getConfig().set(key + "-x", cx);
            plugin.saveConfig();
        }
        boolean there = y != Integer.MIN_VALUE && plugin.getConfig().getInt(key + "-x", cx) == cx && w.getBlockAt(cx, y, cz).getType() == Material.YELLOW_TERRACOTTA && w.getBlockAt(cx, y + DOME, cz).getType() == Material.BARRIER;
        if (!there) {
            // Le sol de la fosse a la hauteur mediane du terrain sous le colisee : ni perche sur une butte, ni enterre.
            List<Integer> heights = new ArrayList<>();
            for (int dx = -24; dx <= 24; dx += 24) for (int dz = -24; dz <= 24; dz += 24) heights.add(w.getHighestBlockYAt(cx + dx * 3, cz + dz * 3, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES));
            heights.sort(null);
            y = Math.max(w.getMinHeight() + 6, Math.min(w.getMaxHeight() - TOWER - 8, heights.get(heights.size() / 2)));
            raise(plugin, w, cx, y, cz);
            plugin.getConfig().set(key, y);
            plugin.getConfig().set(key + "-x", cx);
            plugin.getConfig().set(key + "-z", cz);
            plugin.saveConfig();
        }
        centre = new Location(w, cx + 0.5, y + 1, cz + 0.5);
        return centre;
    }

    /** Les pierres du sol pave, autour de la mosaique : des bruns melanges. */
    private static final Material[] COBBLES = {Material.BROWN_TERRACOTTA, Material.TERRACOTTA, Material.MUD_BRICKS, Material.GRANITE, Material.PACKED_MUD, Material.BROWN_TERRACOTTA, Material.MUD_BRICKS, Material.COARSE_DIRT};

    /**
     * La grande mosaique ronde du centre de la fosse, a la maniere de l'arene de Ferox, de 81 blocs de large : un
     * soleil clair, une etoile noire a seize branches, un cercle de rayons, une dent de scie noire et creme, un
     * bandeau brun, une frise de volutes dorees sur fond noir, un lisere d'or ; un filet dore separe les bandes.
     * {@code u} est la distance au centre ramenee sur 13,5 (le bord de la mosaique), l'angle est en degres.
     */
    private static Material mosaic(double u, double angle) {
        if (u < 0.5) return Material.YELLOW_TERRACOTTA;
        if (u < 1.2) return Material.SMOOTH_SANDSTONE;
        if (u < 1.5) return Material.YELLOW_TERRACOTTA;
        if (u < 3.5) return Math.abs((angle % 22.5) / 22.5 - 0.5) * 2 < 1 - (u - 1.5) / 2 ? Material.SANDSTONE : Material.BLACKSTONE;
        if (u < 3.8) return Material.YELLOW_TERRACOTTA;
        if (u < 4.5) return Material.CUT_SANDSTONE;
        if (u < 6.5) return ((int) (angle / 3.75) & 1) == 0 ? Material.WHITE_TERRACOTTA : Material.LIGHT_GRAY_TERRACOTTA;
        if (u < 6.8) return Material.YELLOW_TERRACOTTA;
        if (u < 7.5) return Material.POLISHED_BLACKSTONE;
        if (u < 9.5) return (angle % 10) / 10 < (u - 7.5) / 2 ? Material.SMOOTH_SANDSTONE : Material.BLACKSTONE;
        if (u < 9.8) return Material.YELLOW_TERRACOTTA;
        if (u < 10.5) return Material.BROWN_TERRACOTTA;
        if (u < 12.5) {
            // Les volutes : une onde doree, doublee d'une seconde plus fine, en opposition.
            double wave = Math.sin(Math.toRadians(angle * 16));
            if (Math.abs(u - 11.5 - 0.6 * wave) < 0.2) return Material.YELLOW_TERRACOTTA;
            if (Math.abs(u - 11.5 + 0.35 * wave) < 0.12) return Material.ORANGE_TERRACOTTA;
            return Material.BLACKSTONE;
        }
        return u < 13.2 ? Material.ORANGE_TERRACOTTA : Material.YELLOW_TERRACOTTA;
    }

    /** La hauteur du toit au-dessus du sol de la fosse, a cette distance du centre : un cone bas, ferme. */
    private static int roof(double d) {
        return WALL_OUT + 1 + (int) ((OUT - d) * 0.3);
    }

    /**
     * Batit l'arene de Ferox, massive et fermee : 185 blocs de large, un toit en cone bas qui la couvre toute.
     * Dedans, dans les bruns et les noirs : une fosse octogonale pavee de 113 blocs, sa grande mosaique ronde de
     * 81 blocs au centre, quatre grilles aux coins, douze grands braseros le long du mur ; un mur sombre a
     * tentures rouges et a herses ; des gradins de brique de terre en dix-neuf marches ; une enceinte noire
     * rythmee par seize piliers qui percent le toit, coiffes de feux ; quatre galeries la traversent du dehors
     * jusqu'aux herses de la fosse, fermees sauf pour l'evenement Omori. La salle est sombre, eclairee par les
     * braseros (une lumiere invisible et faible empeche seulement les monstres d'y apparaitre). Une paroi
     * invisible ferme la fosse : on n'y entre et on n'en sort que par l'evenement. La construction se fait par
     * tranches, quelques-unes par tick, pour ne pas figer le serveur. Construction originale, en blocs de
     * Minecraft, d'apres l'allure du lieu.
     */
    private static void raise(org.bukkit.plugin.Plugin plugin, World w, int cx, int y, int cz) {
        new org.bukkit.scheduler.BukkitRunnable() {
            int x = -OUT;
            @Override public void run() {
                for (int n = 0; n < 2 && x <= OUT; n++, x++) for (int z = -OUT; z <= OUT; z++) column(w, cx, y, cz, x, z);
                if (x > OUT) cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /** Demi-cote du coeur de l'arene bati tout de suite par {@link #raiseAt} (le reste suit par tranches). */
    private static final int CORE = 24;

    /**
     * L'arene de Ferox ailleurs que sur la map (le mode test, pour filmer) : sa fosse en (cx, y, cz). Le coeur de la
     * fosse est bati tout de suite, pour qu'un joueur qui arrive ait un sol sous les pieds ; le reste suit par
     * tranches. Rebatir une arene deja la la remet a neuf (chaque colonne est reecrite de bas en haut).
     */
    public static void raiseAt(org.bukkit.plugin.Plugin plugin, World w, int cx, int y, int cz) {
        for (int x = -CORE; x <= CORE; x++) for (int z = -CORE; z <= CORE; z++) column(w, cx, y, cz, x, z);
        raise(plugin, w, cx, y, cz);
    }

    /** Vrai si l'arene est deja batie a cet endroit (le centre de la mosaique et la paroi invisible au-dessus). */
    public static boolean arenaAt(World w, int cx, int y, int cz) {
        return w.getBlockAt(cx, y, cz).getType() == Material.YELLOW_TERRACOTTA && w.getBlockAt(cx, y + DOME, cz).getType() == Material.BARRIER;
    }

    private static void column(World w, int cx, int y, int cz, int x, int z) {
        double d = Math.hypot(x, z);
        if (d > OUT + 0.5) return;
        // La fosse est un octogone : sa "distance" au centre est la plus grande des distances a ses huit cotes.
        double o = Math.max(Math.max(Math.abs(x), Math.abs(z)), (Math.abs(x) + Math.abs(z)) * 0.7071);
        double angle = Math.toDegrees(Math.atan2(z, x)) + 180;
        // L'ecart, en blocs le long du cercle, a l'axe le plus proche parmi 16 (les piliers) et parmi 4 (les herses).
        double perDegree = Math.PI / 180 * d;
        double toPillar = Math.abs((angle + 11.25) % 22.5 - 11.25) * perDegree, toGate = Math.abs((angle + 45) % 90 - 45) * perDegree;
        int mix = Math.floorMod(x * 7349 + z * 9151 + (x * z) * 31, 8);
        final int bx = cx + x, bz = cz + z, top = roof(d);
        java.util.function.BiConsumer<Integer, Material> set = (h, m) -> { var b = w.getBlockAt(bx, y + h, bz); if (b.getType() != m) b.setType(m, false); };
        for (int h = -4; h < 0; h++) set.accept(h, Material.DEEPSLATE_BRICKS);
        for (int h = 1; h < top; h++) set.accept(h, Material.AIR);
        for (int h = top + 1; h <= TOWER + 3; h++) set.accept(h, Material.AIR);
        // Le toit : tuiles sombres, nervures noires dans l'axe des piliers.
        set.accept(top, toPillar < 1.6 ? Material.POLISHED_BLACKSTONE : ((int) d & 7) == 0 ? Material.DEEPSLATE_BRICKS : Material.DEEPSLATE_TILES);
        boolean lamp = Math.floorMod(x, 7) == 0 && Math.floorMod(z, 7) == 0;
        // Quatre galeries traversent les gradins dans l'axe des herses, du dehors jusqu'au mur de la fosse.
        boolean gallery = Math.min(Math.abs(x), Math.abs(z)) <= GALLERY && o > PIT + 0.5;
        if (gallery) {
            boolean bars = o <= PIT + 1.5;
            set.accept(0, Material.POLISHED_BLACKSTONE_BRICKS);
            for (int h = 1; h <= GATE; h++) set.accept(h, bars ? Material.IRON_BARS : Material.AIR);
            for (int h = GATE + 1; h < Math.min(top, o <= PIT + 3.5 ? RING + 1 : top); h++) set.accept(h, Material.DEEPSLATE_BRICKS);
            if (o <= PIT + 3.5) set.accept(RING, Material.CHISELED_DEEPSLATE);
            else if (d <= OUT - 3.5) {
                // Les gradins passent par-dessus la galerie.
                int row = Math.max(0, (int) ((d - PIT - 3.5) / 1.5)), step = Math.min(top - 4, RING + 1 + row);
                for (int h = step + 1; h < top; h++) set.accept(h, Material.AIR);
                set.accept(step, (row & 1) == 0 ? Material.MUD_BRICKS : Material.PACKED_MUD);
            }
            if (bars) for (int h = RING + 1; h <= DOME; h++) set.accept(h, Material.BARRIER);
            if (Math.floorMod(Math.max(Math.abs(x), Math.abs(z)), 8) == 0 && Math.min(Math.abs(x), Math.abs(z)) == GALLERY) set.accept(GATE, Material.SHROOMLIGHT);
            return;
        }
        if (o <= PIT + 0.5) {
            boolean grate = Math.abs(Math.abs(x) - GRATE) <= 3 && Math.abs(Math.abs(z) - GRATE) <= 3;
            set.accept(0, d < MOSAIC + 0.5 ? mosaic(d / (MOSAIC + 0.5) * 13.5, angle) : grate ? Material.WAXED_OXIDIZED_COPPER_GRATE : COBBLES[mix]);
            if (grate) set.accept(-1, Material.BLACK_CONCRETE);
            // Les braseros : douze colonnes noires de cinq blocs, un grand feu dessus, le long du mur.
            double fireAngle = Math.toRadians(Math.floor(angle / 30) * 30 + 15 - 180);
            double toFire = Math.hypot(x - FIRES * Math.cos(fireAngle), z - FIRES * Math.sin(fireAngle));
            if (toFire < 1.9) {
                for (int h = 1; h <= 5; h++) set.accept(h, h == 5 ? Material.NETHERRACK : toFire < 1.0 || h == 1 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.POLISHED_BLACKSTONE_BRICK_WALL);
                set.accept(6, Material.FIRE);
            } else if (lamp) light(w, bx, y + 4, bz);
            set.accept(DOME, Material.BARRIER);
        } else if (o <= PIT + 3.5) {
            // Le mur de la fosse : brique sombre, tentures rouges et or, herses aux quatre points cardinaux.
            boolean inner = o <= PIT + 1.5;
            for (int h = 0; h <= RING; h++) {
                Material m = h == RING ? Material.CHISELED_DEEPSLATE : h == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_BRICKS;
                if (inner && h >= 2 && h < RING && ((int) (angle / 5.625) & 3) == 1) m = h == RING - 1 || h == 2 ? Material.YELLOW_TERRACOTTA : Material.RED_WOOL;
                set.accept(h, m);
            }
            if (inner) for (int h = RING + 1; h <= DOME; h++) set.accept(h, Material.BARRIER);
        } else if (d <= OUT - 3.5) {
            // Les gradins : une marche tous les 1,5 bloc, bancs de brique de terre, escaliers noirs dans l'axe des piliers.
            int row = Math.max(0, (int) ((d - PIT - 3.5) / 1.5)), step = Math.min(top - 4, RING + 1 + row);
            for (int h = 0; h < step; h++) set.accept(h, Material.DEEPSLATE_BRICKS);
            set.accept(step, toPillar < 1.6 ? Material.POLISHED_BLACKSTONE : (row & 1) == 0 ? Material.MUD_BRICKS : Material.PACKED_MUD);
            if (lamp) light(w, bx, y + step + 3, bz);
        } else {
            // L'enceinte : pleine jusqu'au toit ; seize piliers noirs le percent, coiffes d'un feu ; bandeaux ciseles.
            boolean pillar = toPillar < 2.6;
            int high = pillar ? PILLAR : top;
            for (int h = 0; h <= high; h++) {
                if (h == top && !pillar) continue;
                set.accept(h, pillar ? (h == high ? Material.CHISELED_POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS)
                        : h == WALL_OUT - 4 || h == RING + 1 ? Material.CHISELED_DEEPSLATE : h > WALL_OUT - 4 && ((int) (angle / 5.625) & 3) == 1 ? Material.RED_WOOL : Material.DEEPSLATE_BRICKS);
            }
            if (pillar && toPillar < 1.3 && d > OUT - 2.5 && d <= OUT - 0.5) { set.accept(high + 1, Material.NETHERRACK); set.accept(high + 2, Material.FIRE); }
        }
    }

    /** Une lumiere invisible et faible : assez pour qu'aucun monstre n'apparaisse dans la salle, sans l'eclairer. */
    private static void light(World w, int x, int y, int z) {
        var b = w.getBlockAt(x, y, z);
        b.setType(Material.LIGHT, false);
        if (b.getBlockData() instanceof org.bukkit.block.data.type.Light l) { l.setLevel(8); b.setBlockData(l, false); }
    }

    /** Leve (ou rabaisse) les quatre herses de la fosse : l'arene s'ouvre pour l'evenement Omori. */
    public void gates(boolean open) {
        if (centre == null) return;
        World w = centre.getWorld();
        int cx = centre.getBlockX(), y = centre.getBlockY() - 1, cz = centre.getBlockZ();
        for (int side = 0; side < 4; side++) for (int k = -GALLERY; k <= GALLERY; k++) for (int h = 1; h <= GATE; h++) {
            int along = (side < 2 ? 1 : -1) * (PIT + 1);
            w.getBlockAt(cx + (side % 2 == 0 ? along : k), y + h, cz + (side % 2 == 0 ? k : along)).setType(open ? Material.AIR : Material.IRON_BARS, false);
        }
    }

    /** Un endroit au hasard sur le pave de la fosse, entre la mosaique et le mur, loin des braseros et des grilles. */
    public Location randomSpot() {
        if (centre == null) build();
        var r = java.util.concurrent.ThreadLocalRandom.current();
        for (int i = 0; i < 60; i++) {
            double angle = r.nextDouble(Math.PI * 2), d = r.nextDouble(MOSAIC + 3, PIT - 9);
            Location l = centre.clone().add(Math.cos(angle) * d, 0, Math.sin(angle) * d);
            Material floor = l.clone().subtract(0, 1, 0).getBlock().getType();
            if (floor != Material.WAXED_OXIDIZED_COPPER_GRATE && l.getBlock().getType().isAir() && l.clone().add(0, 2, 0).getBlock().getType().isAir()
                    && Math.abs(d - FIRES) > 4) return l;
        }
        return centre.clone().add(MOSAIC + 4, 0, 0);
    }

    /** Le centre du sol de la fosse. */
    public Location centre() { return centre != null ? centre.clone() : build().clone(); }

    /** Le centre du sol de l'arene (null tant qu'elle n'est pas batie). */
    private Location centre;

    private boolean inArena(Location l) {
        return centre != null && l.getWorld().equals(centre.getWorld()) && Math.hypot(l.getX() - centre.getX(), l.getZ() - centre.getZ()) <= OUT + 1
                && l.getY() >= centre.getY() - 5.5 && l.getY() <= centre.getY() + TOWER + 3;
    }

    /** L'enceinte ne se casse pas, ne se complete pas, n'explose pas. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(org.bukkit.event.block.BlockBreakEvent e) {
        if (inArena(e.getBlock().getLocation().add(0.5, 0.5, 0.5))) { e.setCancelled(true); Msg.actionBar(e.getPlayer(), "<red>L'arene de Regna Ferox est indestructible."); }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(org.bukkit.event.block.BlockPlaceEvent e) {
        if (inArena(e.getBlock().getLocation().add(0.5, 0.5, 0.5))) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(org.bukkit.event.entity.EntityExplodeEvent e) {
        e.blockList().removeIf(b -> inArena(b.getLocation().add(0.5, 0.5, 0.5)));
    }

    private void start(Team a, Team b) {
        if (timer != null) timer.cancel();
        phase = Phase.FIGHT;
        teamA = a;
        teamB = b;
        fighters.clear();
        down.clear();
        Location c = centre != null ? centre : build();
        World w = c.getWorld();
        Msg.broadcast("<gold><bold>Regna Ferox :</bold> " + a.coloredName() + " <gold>affronte " + b.coloredName() + " <gold>dans l'arene !");
        for (Team t : List.of(a, b)) {
            List<Player> members = active(t);
            for (int i = 0; i < members.size(); i++) {
                Player p = members.get(i);
                fighters.add(p.getUniqueId());
                back.put(p.getUniqueId(), p.getLocation());
                modes.put(p.getUniqueId(), p.getGameMode());
                double side = t == a ? -1 : 1;
                Location at = new Location(w, c.getX() + (i - (members.size() - 1) / 2.0) * 3, c.getY(), c.getZ() + side * (MOSAIC - 12), side < 0 ? 0 : 180, 0);
                p.teleport(at);
                p.setFallDistance(0);
                Effects.invincible(p, 3000);
                p.showTitle(Title.title(Msg.mm("<gold><bold>Regna Ferox"), Msg.mm("<gray>Mettez l'equipe adverse a terre"), Title.Times.times(Duration.ofMillis(200), Duration.ofSeconds(3), Duration.ofMillis(400))));
                p.playSound(at, Sound.EVENT_RAID_HORN, 1f, 0.9f);
            }
        }
    }

    /** Un coup qui tuerait dans l'arene ne tue pas : le joueur est a terre et regarde la fin du combat. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLethal(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !fighting(p)) return;
        if (e.getFinalDamage() < p.getHealth()) return;
        e.setCancelled(true);
        fall(p);
    }

    private void fall(Player p) {
        if (!down.add(p.getUniqueId())) return;
        var max = p.getAttribute(Attribute.MAX_HEALTH);
        if (p.isOnline() && !p.isDead()) {
            p.setHealth(max == null ? 20 : max.getValue());
            p.setFireTicks(0);
            p.setGameMode(GameMode.SPECTATOR);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_DEATH, 1f, 0.9f);
        }
        for (UUID u : fighters) { Player o = Bukkit.getPlayer(u); if (o != null) Msg.send(o, "<red>" + p.getName() + " <gray>est a terre."); }
        for (Team t : List.of(teamA, teamB)) {
            boolean standing = false;
            for (UUID u : t.members()) if (fighters.contains(u) && !down.contains(u)) standing = true;
            if (!standing) { finish(t == teamA ? teamB : teamA); return; }
        }
    }

    /** Fin du combat : les vainqueurs recompenses, les vaincus perdent une vie, chacun retourne d'ou il venait. */
    private void finish(Team winner) {
        if (phase != Phase.FIGHT) return;
        phase = Phase.IDLE;
        if (timer != null) timer.cancel();
        Team loser = winner == null ? null : winner == teamA ? teamB : teamA;
        if (winner != null) Msg.broadcast("<gold><bold>Regna Ferox :</bold> " + winner.coloredName() + " <gold>l'emporte sur " + loser.coloredName() + " <gold>!");
        for (UUID u : new ArrayList<>(fighters)) {
            Player p = Bukkit.getPlayer(u);
            if (p == null) continue;
            p.setGameMode(modes.getOrDefault(u, GameMode.SURVIVAL));
            Location home = back.remove(u);
            p.teleport(home != null ? home : plugin.game().randomGroundLocation(300));
            p.setFireTicks(0);
            p.setFallDistance(0);
            Effects.invincible(p, 3000);
            Team mine = plugin.players().get(p).team();
            if (winner != null && mine == winner) {
                plugin.lootbox().reward(p, 4);
                Msg.send(p, "<gold>Victoire ! Tu recois le contenu d'une Lootbox de tier IV.");
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            } else if (loser != null && mine == loser) {
                plugin.lives().loseLife(p, "Regna Ferox");
            }
        }
        fighters.clear();
        down.clear();
        modes.clear();
    }

    /** Fin de partie : l'arene se vide sans vainqueur ni vaincu. */
    public void cancel() {
        if (timer != null) timer.cancel();
        if (phase == Phase.FIGHT) {
            for (UUID u : fighters) {
                Player p = Bukkit.getPlayer(u);
                if (p != null) p.setGameMode(modes.getOrDefault(u, GameMode.SURVIVAL));
            }
        }
        phase = Phase.IDLE;
        fighters.clear();
        down.clear();
        back.clear();
        modes.clear();
        accepted.clear();
        ready.clear();
    }

    /** Parti en plein combat : il compte comme a terre. */
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (fighting(e.getPlayer())) fall(e.getPlayer());
    }

    /** Revenu apres la fin du combat alors qu'il etait reste dans l'arene : il retourne d'ou il venait. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Location home = back.get(p.getUniqueId());
        if (home == null || (phase == Phase.FIGHT && fighters.contains(p.getUniqueId()))) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            back.remove(p.getUniqueId());
            p.teleport(home);
            if (plugin.game().isRunning() && plugin.players().get(p).isAlive()) p.setGameMode(GameMode.SURVIVAL);
        });
    }
}
