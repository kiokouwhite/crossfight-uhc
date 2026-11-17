package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Giant;
import org.bukkit.entity.Husk;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Les quetes des lieux. Chaque lieu a une quete, donnee par son personnage (game/Places.java) : un clic droit sur lui
 * la lance pour l'equipe du joueur. Une quete finie debloque le service du lieu pour toute l'equipe, jusqu'a la fin de
 * la partie. Chaque equipe fait la sienne : rien n'est partage entre equipes, mais rien n'empeche de venir gener.
 *
 * Quatre sortes de quetes :
 *   - ramasser : des objets lumineux apparaissent dans le lieu, il faut passer dessus (perles, modules, recolte, reliques) ;
 *   - porter : pareil, mais un seul objet a la fois, qui ralentit son porteur, a rapporter au personnage (pieces du vaisseau) ;
 *   - vaincre : des monstres a tuer (l'entrainement), ou un Titan qui ne craint que les coups dans le dos ou d'en haut ;
 *   - atteindre : rejoindre le point le plus haut du lieu (l'ascension, la pagode).
 * Hors partie, un admin peut tout essayer : /cf quete.
 */
public final class Quests implements Listener {

    public enum Kind { COLLECT, CARRY, KILL, TITAN, REACH }

    /** Une quete : son nom, qui la donne, ce qu'il dit, sa sorte, combien (objets, monstres), l'objet montre. */
    public record Quest(String title, String giver, String says, Kind kind, int count, Material icon, String thing) {}

    // Le personnage est partout le meme : Anna, la marchande, venue faire des affaires par la Fissure Temporelle.
    public static final Map<String, Quest> QUESTS = Map.ofEntries(
            Map.entry("garreg_mach", new Quest("Le cours du professeur", "Anna", "Bonjour, Héros ! Je suis Anna, et la Fissure Temporelle m'a déposée en plein monastère. Les élèves refusent de m'ouvrir leur bibliothèque tant que personne n'a fait ses preuves… Bats les cinq adversaires de la cour, et ses secrets seront à toi !", Kind.KILL, 5, Material.IRON_SWORD, "adversaires")),
            Map.entry("shiganshina", new Quest("Reboucher la breche", "Anna", "Bonjour, Héros ! Je suis Anna. J'allais rouvrir la forge du quartier, mais un Titan s'est installé devant la porte… Il ne craint que les coups à la nuque : frappe-le de dos, ou d'en haut. Débarrasse-m'en, et la forge est à toi !", Kind.TITAN, 1, Material.IRON_SWORD, "Titan")),
            Map.entry("pearl", new Quest("Les perles englouties", "Anna", "Bonjour, Héros ! Je suis Anna, et je profite de la Fissure Temporelle pour faire des affaires. Hélas, pendant ma téléportation dans ce monde, j'ai perdu mes précieuses perles… Retrouve-les toutes les cinq, et je te promets une belle récompense !", Kind.COLLECT, 5, Material.HEART_OF_THE_SEA, "perles")),
            Map.entry("central_town", new Quest("Les quatre modules", "Anna", "Bonjour, Héros ! Je suis Anna. Mon étal est prêt, mais la ville dort : il lui manque ses quatre modules, tombés aux quatre coins de la place. Rapporte-les, et ma boutique t'ouvre ses portes !", Kind.COLLECT, 4, Material.AMETHYST_SHARD, "modules")),
            Map.entry("arcadia", new Quest("La recolte", "Anna", "Bonjour, Héros ! Je suis Anna. Je comptais vendre les fruits de ces jardins, mais la Fissure les a éparpillés partout… Cueille-m'en huit, et tu ne manqueras plus jamais de vivres !", Kind.COLLECT, 8, Material.APPLE, "fruits")),
            Map.entry("terrace", new Quest("L'ascension", "Anna", "Bonjour, Héros ! Je suis Anna. On dit que, de là-haut, on apprend plus en un regard qu'en mille combats. Je n'ai pas le pied assez sûr… Atteins le sommet pour moi : un seul d'entre vous suffit.", Kind.REACH, 1, Material.FEATHER, "sommet")),
            Map.entry("beacon", new Quest("L'initiation", "Anna", "Bonjour, Héros ! Je suis Anna. L'académie cache quatre reliques, et sans elles ses caisses de ravitaillement restent fermées. Retrouve-les, et la prochaine livraison tombera ici même !", Kind.COLLECT, 4, Material.GOLDEN_HORSE_ARMOR, "reliques")),
            Map.entry("forest_of_hope", new Quest("Les pieces du vaisseau", "Anna", "Bonjour, Héros ! Je suis Anna. Mon vaisseau s'est disloqué en traversant la Fissure, et trois de ses pièces sont tombées dans la forêt. Elles sont lourdes : rapporte-les-moi une par une, et les trésors de ce bois seront à toi !", Kind.CARRY, 3, Material.NETHERITE_SCRAP, "pieces")),
            Map.entry("jouki", new Quest("Le sommet de la pagode", "Anna", "Bonjour, Héros ! Je suis Anna. Ici, je n'ai rien à vendre… mais on raconte que la vue, tout en haut de la pagode, vaut tous les trésors. Monte, et dis-moi si c'est vrai !", Kind.REACH, 1, Material.BELL, "sommet")));

    /** Ou en est une equipe dans la quete d'un lieu. */
    private static final class Run {
        final String team, place;
        int done;
        boolean finished;
        final List<Entity> things = new ArrayList<>();          // les monstres
        /** Les objets a ramasser (ou le repere du sommet) : ou ils sont, et ce qui les montre quand un joueur est pres. */
        final Map<Location, Entity> spots = new java.util.LinkedHashMap<>();
        Location target, home;
        UUID carrier;
        Location carriedFrom;
        Run(String team, String place) { this.team = team; this.place = place; }
    }

    private final CrossfightPlugin plugin;
    private final NamespacedKey mark;
    /** Par equipe (ou par joueur sans equipe) puis par lieu. */
    private final Map<String, Map<String, Run>> runs = new HashMap<>();

    public Quests(CrossfightPlugin plugin) {
        this.plugin = plugin;
        this.mark = new NamespacedKey(plugin, "quete_cible");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 5L);
    }

    public boolean on() { return plugin.getConfig().getBoolean("structures.quetes", true); }

    /** L'equipe d'un joueur, pour les quetes : son equipe, ou lui seul s'il n'en a pas (essais hors partie). */
    private String teamOf(Player p) {
        Team t = plugin.teams().teamOf(p);
        return t != null ? "e" + t.id() : p.getUniqueId().toString();
    }

    private List<Player> members(String team) {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) if (teamOf(p).equals(team)) out.add(p);
        return out;
    }

    private Run run(Player p, String place) {
        Map<String, Run> m = runs.get(teamOf(p));
        return m == null ? null : m.get(place);
    }

    /** Vrai si l'equipe de ce joueur peut se servir de ce lieu : pas de quete, quetes coupees, ou quete finie. */
    public boolean unlocked(Player p, String place) {
        if (!on() || !QUESTS.containsKey(place)) return true;
        Run r = run(p, place);
        return r != null && r.finished;
    }

    /** Vrai si une equipe au moins a fini la quete de ce lieu (pour ce qui profite a tous, comme la caisse garantie). */
    public boolean anyUnlocked(String place) {
        if (!on() || !QUESTS.containsKey(place)) return true;
        for (Map<String, Run> m : runs.values()) { Run r = m.get(place); if (r != null && r.finished) return true; }
        return false;
    }

    /** Pour le panneau de quete : le nom de la quete, ou elle en est, et la couleur de cet etat ; null s'il n'y a rien a montrer. */
    public String[] panel(Player p, String place) {
        Quest q = QUESTS.get(place);
        if (q == null || !on()) return null;
        Run r = run(p, place);
        if (r == null) return new String[]{q.title(), "parle au personnage", "#7a4a12"};
        if (r.finished) return new String[]{q.title(), "terminee", "#2e6b1f"};
        return new String[]{q.title(), q.kind() == Kind.REACH || q.kind() == Kind.TITAN ? "en cours" : r.done + " / " + q.count() + " " + q.thing(), "#7a4a12"};
    }

    /** Les lignes du tableau de bord pour un joueur dans ce lieu. */
    public List<String> lines(Player p, String place) {
        Quest q = QUESTS.get(place);
        if (q == null || !on()) return List.of();
        Run r = run(p, place);
        String state = r == null ? "<gray>parle au personnage" : r.finished ? "<green>terminee" : q.kind() == Kind.REACH || q.kind() == Kind.TITAN ? "<yellow>en cours" : "<yellow>" + r.done + " / " + q.count() + " " + q.thing();
        return List.of("<yellow><bold>!</bold> <white>Quete", "<gold>" + q.title(), state, "");
    }

    // ------------------------------------------------------------------ parler au personnage

    /** Un clic sur le personnage du lieu : il lance la quete, dit ou elle en est, ou recoit ce qu'on lui rapporte. */
    public void talk(Player p, String place, Location npc) {
        Quest q = QUESTS.get(place);
        if (q == null) return;
        if (!plugin.game().isRunning() && !p.hasPermission("crossfight.admin")) { Msg.send(p, "<yellow>" + q.giver() + " <gray>: <white>Revenez pendant la partie."); return; }
        Run r = run(p, place);
        if (r != null && r.finished) { Msg.send(p, "<yellow>" + q.giver() + " <gray>: <white>Vous avez fait vos preuves. Ce lieu est a vous."); return; }
        if (r == null) { start(p, place, npc); return; }
        if (q.kind() == Kind.CARRY && p.getUniqueId().equals(r.carrier)) { deliver(r, q, p); return; }
        Msg.send(p, "<yellow>" + q.giver() + " <gray>: <white>" + q.says());
    }

    /** Lance la quete d'un lieu pour l'equipe de ce joueur. */
    public boolean start(Player p, String place, Location npc) {
        Quest q = QUESTS.get(place);
        Structures.Placed box = placed(p.getWorld(), place);
        if (q == null || box == null) return false;
        Run r = new Run(teamOf(p), place);
        r.home = npc != null ? npc.clone() : p.getLocation();
        runs.computeIfAbsent(r.team, k -> new HashMap<>()).put(place, r);
        World w = p.getWorld();
        switch (q.kind()) {
            case COLLECT, CARRY -> {
                for (int i = 0; i < q.count(); i++) {
                    Location at = null;
                    // Les modules : un par quart du lieu. Le reste : au hasard.
                    for (int tries = 0; tries < 40 && at == null; tries++) {
                        Location c = plugin.places().questSpot(w, box);
                        if (c == null) continue;
                        boolean east = c.getX() >= r.home.getX(), south = c.getZ() >= r.home.getZ();
                        if (q.count() != 4 || tries > 30 || (east ? 1 : 0) + (south ? 2 : 0) == i) at = c;
                    }
                    if (at != null) r.spots.put(at, null);
                }
                if (r.spots.isEmpty()) { finish(r, q); return true; }
            }
            case KILL -> {
                for (int i = 0; i < q.count(); i++) {
                    double a = Math.PI * 2 * i / q.count();
                    Location at = r.home.clone().add(Math.cos(a) * 5, 0.2, Math.sin(a) * 5);
                    r.things.add(w.spawn(at, Husk.class, h -> {
                        h.setPersistent(false);
                        h.setRemoveWhenFarAway(false);
                        h.setAdult();
                        h.setGlowing(true);
                        h.setTarget(p);
                        h.getPersistentDataContainer().set(mark, PersistentDataType.STRING, r.team + "/" + place);
                        if (h.getEquipment() != null) { h.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET)); h.getEquipment().setItemInMainHand(new ItemStack(Material.STONE_SWORD)); }
                    }));
                }
            }
            case TITAN -> {
                Location at = r.home.clone().add(8, 0.2, 0);
                r.things.add(w.spawn(at, Giant.class, g -> {
                    g.setPersistent(false);
                    g.setRemoveWhenFarAway(false);
                    g.setGlowing(true);
                    g.customName(Msg.mm("<red>Titan"));
                    g.setCustomNameVisible(true);
                    var hp = g.getAttribute(Attribute.MAX_HEALTH);
                    if (hp != null) { hp.setBaseValue(120); g.setHealth(120); }
                    g.getPersistentDataContainer().set(mark, PersistentDataType.STRING, r.team + "/" + place);
                }));
            }
            case REACH -> {
                // Le sommet : le plus haut bloc du lieu, cherche de quatre en quatre.
                int best = Integer.MIN_VALUE, bx = 0, bz = 0;
                for (int x = box.x() + 2; x < box.x() + box.sx(); x += 4) for (int z = box.z() + 2; z < box.z() + box.sz(); z += 4) {
                    if (plugin.structures().at(new Location(w, x, 0, z)) == null) continue;
                    int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                    if (y > best) { best = y; bx = x; bz = z; }
                }
                r.target = new Location(w, bx + 0.5, best + 1, bz + 0.5);
                r.spots.put(r.target.clone(), null);
            }
        }
        for (Player m : members(r.team)) {
            Msg.send(m, "<yellow>" + q.giver() + " <gray>: <white>" + q.says());
            m.playSound(m.getLocation(), Sound.UI_TOAST_IN, 1f, 1f);
        }
        return true;
    }

    /** Un objet lumineux qui tourne, visible a travers les murs. */
    private Entity item(Location at, Material icon) {
        return at.getWorld().spawn(at.clone().add(0, 0.6, 0), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(new ItemStack(icon));
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setGlowing(true);
            d.setGlowColorOverride(org.bukkit.Color.YELLOW);
            d.setViewRange(8f);
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(1.1f, 1.1f, 1.1f), new Quaternionf()));
        });
    }

    private Structures.Placed placed(World w, String place) {
        for (Structures.Placed p : plugin.structures().finished(w)) if (p.name().equals(place)) return p;
        return null;
    }

    // ------------------------------------------------------------------ le deroulement

    private int beat;

    private void tick() {
        beat++;
        for (Map<String, Run> m : runs.values()) for (Run r : new ArrayList<>(m.values())) {
            if (r.finished) continue;
            Quest q = QUESTS.get(r.place);
            List<Player> team = members(r.team);
            switch (q.kind()) {
                case COLLECT, CARRY -> {
                    Player carrier = r.carrier == null ? null : Bukkit.getPlayer(r.carrier);
                    if (r.carrier != null && (carrier == null || carrier.isDead() || carrier.getGameMode() == GameMode.SPECTATOR)) {
                        // Le porteur est tombe : la piece retourne ou elle etait.
                        r.spots.put(r.carriedFrom, null);
                        r.carrier = null;
                        carrier = null;
                    }
                    if (carrier != null) {
                        carrier.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 1, true, false, true));
                        if (beat % 4 == 0) Msg.actionBar(carrier, "<gold>Tu portes une piece : rapporte-la au capitaine.");
                        if (carrier.getLocation().distanceSquared(r.home) < 16) deliver(r, q, carrier);
                        continue;
                    }
                    show(r, q);
                    for (Location at : new ArrayList<>(r.spots.keySet())) {
                        for (Player p : team) {
                            if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR || !p.getWorld().equals(at.getWorld()) || p.getLocation().distanceSquared(at) > 4) continue;
                            Entity e = r.spots.remove(at);
                            if (e != null) e.remove();
                            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);
                            if (q.kind() == Kind.CARRY) { r.carrier = p.getUniqueId(); r.carriedFrom = at; }
                            else { r.done++; tell(r, q); if (r.done >= q.count() || r.spots.isEmpty()) finish(r, q); }
                            break;
                        }
                        if (r.finished || r.carrier != null) break;
                    }
                }
                case KILL -> {
                    r.things.removeIf(e -> !e.isValid());
                    int now = q.count() - r.things.size();
                    if (now != r.done) { r.done = now; tell(r, q); }
                    if (r.things.isEmpty()) finish(r, q);
                }
                case TITAN -> {
                    r.things.removeIf(e -> !e.isValid());
                    if (r.things.isEmpty()) { finish(r, q); break; }
                    if (beat % 4 != 0) break;
                    Giant g = (Giant) r.things.get(0);
                    // Il se tourne lentement vers le joueur le plus proche, et ecrase ceux qui restent a ses pieds.
                    Player near = null;
                    for (Player p : g.getWorld().getPlayers()) {
                        if (p.isDead() || p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) continue;
                        if (near == null || p.getLocation().distanceSquared(g.getLocation()) < near.getLocation().distanceSquared(g.getLocation())) near = p;
                    }
                    if (near == null || near.getLocation().distanceSquared(g.getLocation()) > 40 * 40) break;
                    Vector to = near.getLocation().toVector().subtract(g.getLocation().toVector());
                    float want = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ())), turn = ((want - g.getLocation().getYaw()) % 360 + 540) % 360 - 180;
                    g.setRotation(g.getLocation().getYaw() + Math.max(-25, Math.min(25, turn)), 0);
                    if (beat % 12 == 0) for (Player p : g.getWorld().getPlayers()) {
                        if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE || p.getLocation().distanceSquared(g.getLocation()) > 25 || p.getLocation().getY() > g.getLocation().getY() + 3) continue;
                        p.damage(5.0, g);
                        p.setVelocity(p.getLocation().toVector().subtract(g.getLocation().toVector()).setY(0).normalize().multiply(1.1).setY(0.45));
                        g.getWorld().playSound(g.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.4f, 0.5f);
                    }
                }
                case REACH -> {
                    show(r, q);
                    if (beat % 8 == 0) r.target.getWorld().spawnParticle(Particle.END_ROD, r.target.clone().add(0, 1, 0), 6, 0.3, 1.2, 0.3, 0.01);
                    for (Player p : team) if (!p.isDead() && p.getGameMode() != GameMode.SPECTATOR && p.getWorld().equals(r.target.getWorld()) && p.getLocation().distanceSquared(r.target) < 16) { finish(r, q); break; }
                }
            }
        }
    }

    /** Les objets d'une quete ne sont montres que pres d'un joueur : ailleurs, le monde n'est pas charge et ils disparaitraient. */
    private void show(Run r, Quest q) {
        if (beat % 4 != 0) return;
        for (Map.Entry<Location, Entity> e : r.spots.entrySet()) {
            Location at = e.getKey();
            boolean near = false;
            if (at.getWorld().isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) for (Player p : at.getWorld().getPlayers()) if (p.getLocation().distanceSquared(at) < 96 * 96) { near = true; break; }
            Entity shown = e.getValue();
            if (shown != null && (!near || !shown.isValid())) { shown.remove(); e.setValue(null); shown = null; }
            if (near && shown == null) e.setValue(item(at, q.icon()));
            // Un tintement, toutes les deux secondes, pour qui est a portee d'oreille (32 blocs). Le son est joue tout
            // pres du joueur, dans la direction de l'objet : on l'entend fort, et l'on sait d'ou il vient ; plus on est
            // pres, plus il est fort.
            if (near && beat % 8 == 0) for (Player p : members(r.team)) {
                if (!p.getWorld().equals(at.getWorld())) continue;
                double far = p.getLocation().distance(at);
                if (far > 32) continue;
                Location from = far < 2 ? at : p.getEyeLocation().add(at.toVector().subtract(p.getEyeLocation().toVector()).normalize().multiply(2));
                float loud = (float) Math.max(0.45, 1.0 - far / 40.0);
                p.playSound(from, Sound.BLOCK_AMETHYST_BLOCK_CHIME, org.bukkit.SoundCategory.MASTER, loud, 1.4f);
                p.playSound(from, Sound.BLOCK_NOTE_BLOCK_BELL, org.bukkit.SoundCategory.MASTER, loud, 1.6f);
            }
        }
    }

    private void clear(Run r) {
        r.things.forEach(Entity::remove);
        r.things.clear();
        for (Entity e : r.spots.values()) if (e != null) e.remove();
        r.spots.clear();
    }

    private void deliver(Run r, Quest q, Player p) {
        r.carrier = null;
        r.done++;
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.4f);
        tell(r, q);
        if (r.done >= q.count() || r.spots.isEmpty()) finish(r, q);
    }

    private void tell(Run r, Quest q) {
        for (Player m : members(r.team)) Msg.actionBar(m, "<gold>" + q.title() + " <white>" + r.done + " / " + q.count());
    }

    private void finish(Run r, Quest q) {
        r.finished = true;
        clear(r);
        String use = plugin.places().label(r.place);
        for (Player m : members(r.team)) {
            m.showTitle(net.kyori.adventure.title.Title.title(Msg.mm("<green><bold>Quete terminee"), Msg.mm("<white>" + q.title())));
            m.playSound(m.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            Msg.send(m, use == null ? "<green>Quete terminee : <white>" + q.title() + "<green>." : "<green>Quete terminee : <white>" + q.title() + "<green>. <gold>" + use + " <green>est debloque pour ton equipe jusqu'a la fin de la partie.");
        }
    }

    // ------------------------------------------------------------------ les monstres des quetes

    /** Le Titan ne craint que les coups dans le dos, ou venus d'en haut. */
    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Giant g) || !g.getPersistentDataContainer().has(mark, PersistentDataType.STRING)) return;
        Entity by = e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity s ? s : e.getDamager();
        Vector to = by.getLocation().toVector().subtract(g.getLocation().toVector()).setY(0);
        boolean behind = to.lengthSquared() > 0.01 && to.normalize().dot(g.getLocation().getDirection().setY(0).normalize()) < -0.2;
        boolean above = by.getLocation().getY() > g.getLocation().getY() + 8;
        if (behind || above) { g.getWorld().spawnParticle(Particle.CRIT, g.getLocation().add(0, 10, 0), 20, 1, 1, 1, 0.2); return; }
        e.setCancelled(true);
        if (by instanceof Player p) Msg.actionBar(p, "<red>Il ne craint que la nuque : frappe-le de dos, ou d'en haut.");
    }

    /** Les monstres d'une quete ne laissent rien. */
    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity l = e.getEntity();
        if (!l.getPersistentDataContainer().has(mark, PersistentDataType.STRING)) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
    }

    // ------------------------------------------------------------------ les essais (/cf quete)

    /**
     * Montre a ce joueur ce qu'il reste a trouver dans la quete d'un lieu : les coordonnees dans le chat, et une colonne
     * de lumiere sur chaque objet pendant une minute. Rend le nombre d'objets, ou -1 si la quete n'est pas en cours.
     */
    public int reveal(Player p, String place) {
        Run r = run(p, place);
        if (r == null || r.finished) return -1;
        List<Location> at = new ArrayList<>();
        at.addAll(r.spots.keySet());
        for (Entity e : r.things) if (e.isValid()) at.add(e.getLocation());
        for (Location l : at) Msg.raw(p, "<gray>- <white>" + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + " <gray>(a " + (int) l.distance(p.getLocation()) + " blocs) <click:run_command:'/tp " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + "'><yellow>[y aller]</click>");
        new org.bukkit.scheduler.BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (n++ >= 120 || !p.isOnline()) { cancel(); return; }
                for (Location l : at) for (int y = 0; y < 40; y += 2) p.spawnParticle(Particle.END_ROD, l.clone().add(0, y, 0), 1, 0, 0, 0, 0, null, true);
            }
        }.runTaskTimer(plugin, 0L, 10L);
        return at.size();
    }

    /** Termine d'office la quete d'un lieu pour l'equipe de ce joueur. */
    public boolean force(Player p, String place) {
        Quest q = QUESTS.get(place);
        if (q == null) return false;
        Run r = run(p, place);
        if (r == null) { r = new Run(teamOf(p), place); r.home = p.getLocation(); runs.computeIfAbsent(r.team, k -> new HashMap<>()).put(place, r); }
        if (!r.finished) finish(r, q);
        return true;
    }

    /** Oublie la quete d'un lieu (ou toutes, si place est null) pour l'equipe de ce joueur. */
    public void forget(Player p, String place) {
        Map<String, Run> m = runs.get(teamOf(p));
        if (m == null) return;
        for (Run r : new ArrayList<>(m.values())) if (place == null || r.place.equals(place)) { clear(r); m.remove(r.place); }
    }

    /** Ou en est ce joueur, lieu par lieu. */
    public String state(Player p, String place) {
        Run r = run(p, place);
        return r == null ? "<gray>pas commencee" : r.finished ? "<green>terminee" : "<yellow>en cours (" + r.done + " / " + QUESTS.get(place).count() + ")";
    }

    /** Une partie commence, ou la map change : tout repart de zero. */
    public void reset() {
        for (Map<String, Run> m : runs.values()) for (Run r : m.values()) clear(r);
        runs.clear();
    }
}
