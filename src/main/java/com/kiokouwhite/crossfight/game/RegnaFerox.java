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
    /** Le colisee : rayon de la fosse, rayon hors tout, hauteur du mur de la fosse, du dome, de l'enceinte, des piliers. */
    private static final int PIT = 18, OUT = 32, RING = 5, DOME = 15, WALL_OUT = 17, TOWER = 23;

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
     * L'arene est sur la map des le debut de la partie : un colisee de pierre (voir raise), pose sur le terrain
     * a 30 % du rayon de la bordure, a l'est du centre (le centre est garde pour d'autres evenements). Sa hauteur
     * est retenue par monde dans config.yml, pour la retrouver (et ne pas la rebatir) a la partie suivante.
     */
    public Location build() {
        World w = plugin.game().world();
        int cx = (int) Math.round(plugin.game().rules().value("bordure") * 0.3), cz = 0;
        String key = "ferox.arene." + w.getName();
        int y = plugin.getConfig().getInt(key, Integer.MIN_VALUE);
        boolean there = y != Integer.MIN_VALUE && plugin.getConfig().getInt(key + "-x", cx) == cx && w.getBlockAt(cx, y, cz).getType() == Material.SMOOTH_SANDSTONE;
        if (!there) {
            // Le sol de la fosse a la hauteur mediane du terrain sous le colisee : ni perche sur une butte, ni enterre.
            List<Integer> heights = new ArrayList<>();
            for (int dx = -24; dx <= 24; dx += 24) for (int dz = -24; dz <= 24; dz += 24) heights.add(w.getHighestBlockYAt(cx + dx, cz + dz, org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES));
            heights.sort(null);
            y = Math.max(w.getMinHeight() + 6, Math.min(w.getMaxHeight() - TOWER - 8, heights.get(heights.size() / 2)));
            raise(w, cx, y, cz);
            plugin.getConfig().set(key, y);
            plugin.getConfig().set(key + "-x", cx);
            plugin.saveConfig();
        }
        centre = new Location(w, cx + 0.5, y + 1, cz + 0.5);
        return centre;
    }

    /** Les pierres du sol pave, autour de la mosaique : des bruns melanges. */
    private static final Material[] COBBLES = {Material.BROWN_TERRACOTTA, Material.TERRACOTTA, Material.MUD_BRICKS, Material.GRANITE, Material.PACKED_MUD, Material.BROWN_TERRACOTTA, Material.MUD_BRICKS, Material.COARSE_DIRT};

    /**
     * La grande mosaique ronde du centre de la fosse, a la maniere de l'arene de Ferox : un soleil clair, une
     * etoile noire, un cercle de rayons, une dent de scie noire et creme, un bandeau brun, une frise de volutes
     * dorees sur fond noir, un lisere d'or.
     */
    private static Material mosaic(double d, double angle) {
        if (d < 1.5) return Material.SMOOTH_SANDSTONE;
        if (d < 3.5) return ((int) (angle / 22.5) & 1) == 0 ? Material.BLACKSTONE : Material.SANDSTONE;
        if (d < 4.5) return Material.CUT_SANDSTONE;
        if (d < 6.5) return ((int) (angle / 11.25) & 1) == 0 ? Material.WHITE_TERRACOTTA : Material.LIGHT_GRAY_TERRACOTTA;
        if (d < 7.5) return Material.POLISHED_BLACKSTONE;
        if (d < 9.5) return (angle % 15) / 15 < (d - 7.5) / 2 ? Material.SMOOTH_SANDSTONE : Material.BLACKSTONE;
        if (d < 10.5) return Material.BROWN_TERRACOTTA;
        if (d < 12.5) return Math.abs(d - 11.5 - 0.75 * Math.sin(Math.toRadians(angle * 12))) < 0.45 ? Material.YELLOW_TERRACOTTA : Material.BLACKSTONE;
        return Material.ORANGE_TERRACOTTA;
    }

    /**
     * Batit le colisee de Ferox, de 65 blocs de large, dans les bruns et les noirs de l'arene de Ferox : une fosse
     * octogonale pavee, sa grande mosaique ronde au centre, quatre grilles aux coins, douze braseros sur leur
     * colonne le long du mur ; un mur sombre a tentures rouges et a herses ; des gradins de brique de terre en dix
     * marches ; une enceinte crenelee de 18 blocs, rythmee par seize piliers noirs coiffes de feux. Un dome
     * invisible ferme la fosse : on n'y entre et on n'en sort que par l'evenement. Construction originale, faite
     * de blocs de Minecraft d'apres l'allure du lieu.
     */
    private void raise(World w, int cx, int y, int cz) {
        for (int x = -OUT; x <= OUT; x++) for (int z = -OUT; z <= OUT; z++) {
            double d = Math.hypot(x, z);
            if (d > OUT + 0.5) continue;
            // La fosse est un octogone : sa "distance" au centre est la plus grande des distances a ses huit cotes.
            double o = Math.max(Math.max(Math.abs(x), Math.abs(z)), (Math.abs(x) + Math.abs(z)) * 0.7071);
            double angle = Math.toDegrees(Math.atan2(z, x)) + 180;
            // L'ecart a l'axe le plus proche parmi 16 (les piliers), 4 (les herses) et 12 (les braseros), en degres.
            double toPillar = Math.abs((angle + 11.25) % 22.5 - 11.25), toGate = Math.abs((angle + 45) % 90 - 45), toFire = Math.abs(angle % 30 - 15);
            int mix = Math.floorMod(x * 7349 + z * 9151 + (x * z) * 31, 8);
            final int bx = cx + x, bz = cz + z;
            java.util.function.BiConsumer<Integer, Material> set = (h, m) -> w.getBlockAt(bx, y + h, bz).setType(m, false);
            for (int h = -4; h < 0; h++) set.accept(h, Material.DEEPSLATE_BRICKS);
            for (int h = 1; h <= TOWER + 3; h++) set.accept(h, Material.AIR);
            if (o <= PIT + 0.5) {
                boolean grate = Math.abs(Math.abs(x) - 11) <= 1 && Math.abs(Math.abs(z) - 11) <= 1;
                set.accept(0, d < 13.5 ? mosaic(d, angle) : grate ? Material.WAXED_OXIDIZED_COPPER_GRATE : COBBLES[mix]);
                if (grate) set.accept(-1, Material.BLACK_CONCRETE);
                // Les braseros : une colonne noire de trois blocs, un feu dessus, le long du mur.
                if (d >= 15.5 && d < 16.5 && toFire < 2.2 && o <= PIT - 1.5) {
                    for (int h = 1; h <= 3; h++) set.accept(h, h == 3 ? Material.NETHERRACK : Material.POLISHED_BLACKSTONE_BRICKS);
                    set.accept(4, Material.FIRE);
                }
                set.accept(DOME, Material.BARRIER);
            } else if (o <= PIT + 2.5) {
                // Le mur de la fosse : brique sombre, tentures rouges et or entre les axes, herses aux quatre points cardinaux.
                boolean inner = o <= PIT + 1.5;
                for (int h = 0; h <= RING; h++) {
                    Material m = h == RING ? Material.CHISELED_DEEPSLATE : h == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_BRICKS;
                    if (inner && h >= 1 && h <= 4 && toGate < 6) m = Material.IRON_BARS;
                    else if (!inner && h <= 4 && toGate < 6) m = Material.BLACK_CONCRETE;
                    else if (inner && h >= 2 && h < RING && toPillar > 8.5) m = (h == RING - 1 || toPillar > 10.3) ? Material.YELLOW_TERRACOTTA : Material.RED_WOOL;
                    set.accept(h, m);
                }
                if (inner) for (int h = RING + 1; h <= DOME; h++) set.accept(h, Material.BARRIER);
            } else if (d <= OUT - 2.5) {
                // Les gradins : une marche tous les 1,1 bloc, bancs de brique de terre, escaliers noirs dans l'axe des piliers.
                int row = Math.max(0, (int) ((d - PIT - 2.5) / 1.1)), step = RING + 1 + row;
                for (int h = 0; h < step; h++) set.accept(h, Material.DEEPSLATE_BRICKS);
                set.accept(step, toPillar < 2.6 ? Material.POLISHED_BLACKSTONE : (row & 1) == 0 ? Material.MUD_BRICKS : Material.PACKED_MUD);
                if (toPillar < 1.2 && ((int) d) % 4 == 0) set.accept(step, Material.SHROOMLIGHT);
            } else {
                // L'enceinte : seize piliers noirs a brasero, des creneaux entre eux, des bandeaux ciseles, des tentures rouges.
                boolean pillar = toPillar < 3.4;
                int top = pillar ? TOWER : WALL_OUT + (((int) (angle / 3.75)) & 1);
                for (int h = 0; h <= top; h++) {
                    Material m = pillar ? (h == top ? Material.CHISELED_POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS)
                            : h == WALL_OUT - 3 || h == RING + 1 ? Material.CHISELED_DEEPSLATE : h > WALL_OUT - 3 && toPillar > 9 ? Material.RED_WOOL : Material.DEEPSLATE_BRICKS;
                    set.accept(h, m);
                }
                if (pillar && toPillar < 1.4 && d > OUT - 1.5 && d <= OUT - 0.5) { set.accept(top + 1, Material.NETHERRACK); set.accept(top + 2, Material.FIRE); }
            }
        }
    }

    /** Le centre du sol de l'arene (null hors partie). */
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
                Location at = new Location(w, c.getX() + (i - (members.size() - 1) / 2.0) * 3, c.getY(), c.getZ() + side * (PIT - 6), side < 0 ? 0 : 180, 0);
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
        centre = null;
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
