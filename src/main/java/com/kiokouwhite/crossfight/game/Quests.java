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

    public enum Kind { COLLECT, CARRY, KILL, TITAN, REACH, PUZZLE, FISH }

    /** Une quete : son nom, qui la donne, ce qu'il dit, sa sorte, combien (objets, monstres), l'objet montre. */
    public record Quest(String title, String giver, String says, Kind kind, int count, Material icon, String thing, String thanks) {}

    // Le personnage est partout le meme : Anna, la marchande, venue faire des affaires par la Fissure Temporelle.
    public static final Map<String, Quest> QUESTS = Map.ofEntries(
            Map.entry("garreg_mach", new Quest("La partie de pêche", "Anna", "Bonjour, Héros ! Je suis Anna, et la Fissure Temporelle m'a déposée en plein monastère. Les élèves ne m'ouvriront leur bibliothèque que contre un bon repas… Prends cette canne, vise l'eau de l'étang et pêche-moi dix poissons : quand ça mord, ferre vite, puis arrête le cercle au cœur de la cible !", Kind.FISH, 10, Material.COD, "poissons", "Quelle pêche, Héros ! Les élèves se régalent et m'ont ouvert leur bibliothèque : reviens me parler, pose l'objet de ton choix, et je l'enchante au niveau que tu veux : 1, 2 ou 5 niveaux. Protection pour l'armure, Tranchant pour les lames… C'est pour ton équipe, jusqu'à la fin de la partie !")),
            Map.entry("shiganshina", new Quest("La chasse au Titan", "Anna", "Bonjour, Héros ! Je suis Anna. J'allais rouvrir la forge du quartier, mais un Titan s'est installé devant la porte… Il faudra cent coups pour l'abattre, et il ne se laissera pas faire : esquive sa charge et ses pierres, détourne les yeux quand il te fixe, et fuis quand il te court après. Débarrasse-m'en, et la forge est à toi !", Kind.TITAN, 100, Material.IRON_SWORD, "coups", "Tu l'as terrassé ! La forge du quartier rouvre, et ici on forge en musique : reviens me parler, choisis un air et frappe le métal en rythme. Neuf airs, du plus facile au plus difficile ; mieux tu frappes, plus tu forges de fer, d'or et de diamants. De quoi t'équiper sans descendre à la mine !")),
            Map.entry("pearl", new Quest("Les perles englouties", "Anna", "Bonjour, Héros ! Je suis Anna, et je profite de la Fissure Temporelle pour faire des affaires. Hélas, pendant ma téléportation dans ce monde, j'ai perdu mes précieuses perles… Retrouve-les toutes les cinq, et je te promets une belle récompense !", Kind.COLLECT, 5, Material.HEART_OF_THE_SEA, "perles", "Mes perles ! Merci, Héros ! Comme promis, voici ta récompense : fais un clic droit sur moi et choisis une potion, c'est offert. Force, Vitesse, Poison, Faiblesse ou Lenteur : une toutes les 10 minutes, jusqu'à la fin de la partie !")),
            Map.entry("central_town", new Quest("Les quatre piliers", "Anna", "Bonjour, Héros ! Je suis Anna. Mon étal est prêt, mais la ville dort : ses quatre piliers se sont éteints autour de moi. Chacun cache une énigme différente : une tablette de pièces dorées, un circuit à rebrancher, un objet à refaire sur une table de craft, et un puzzle à reformer. Résous-la, et il se rallumera. Rallume-les tous les quatre, et ma boutique t'ouvre ses portes !", Kind.PUZZLE, 4, Material.AMETHYST_SHARD, "piliers", "Les quatre piliers brillent, la ville se réveille ! Ma boutique est ouverte : reparle-moi, et tu trouveras quatre objets de lootbox à acheter en diamants. L'étalage change toutes les dix minutes, jusqu'à la fin de la partie !")),
            Map.entry("arcadia", new Quest("La récolte", "Anna", "Bonjour, Héros ! Je suis Anna. Je comptais vendre les fruits de ces jardins, mais la Fissure les a éparpillés partout… Cueille-m'en huit, et tu ne manqueras plus jamais de vivres !", Kind.COLLECT, 8, Material.APPLE, "fruits", "Quelle belle récolte ! Tu ne manqueras plus de rien : la borne, juste à côté, t'offre 12 steaks toutes les 8 minutes, jusqu'à la fin de la partie !")),
            Map.entry("terrace", new Quest("L'ascension", "Anna", "Bonjour, Héros ! Je suis Anna. On dit que, de là-haut, on apprend plus en un regard qu'en mille combats. Je n'ai pas le pied assez sûr… Atteins le sommet pour moi : un seul d'entre vous suffit.", Kind.REACH, 1, Material.FEATHER, "sommet", "Tu l'as fait ! Désormais, ton équipe gagne de l'expérience rien qu'en restant ici, sur la terrasse, jusqu'à la fin de la partie !")),
            Map.entry("beacon", new Quest("L'initiation", "Anna", "Bonjour, Héros ! Je suis Anna. L'académie cache quatre reliques, et sans elles ses caisses de ravitaillement restent fermées. Retrouve-les, et la prochaine livraison tombera ici même !", Kind.COLLECT, 4, Material.GOLDEN_HORSE_ARMOR, "reliques", "Les quatre reliques, bravo ! Les caisses de ravitaillement sont rouvertes : à chaque vague, une caisse tombera ici même, à l'académie !")),
            Map.entry("forest_of_hope", new Quest("Les pièces du vaisseau", "Anna", "Bonjour, Héros ! Je suis Anna. Mon vaisseau s'est disloqué en traversant la Fissure, et trois de ses pièces sont tombées dans la forêt. Elles sont lourdes : rapporte-les-moi une par une, et les trésors de ce bois seront à toi !", Kind.CARRY, 3, Material.NETHERITE_SCRAP, "pièces", "Mon vaisseau est sauvé, merci ! En échange, les trésors de ce bois sont à ton équipe : cinq pièces y sont cachées, trouve-les pour gagner tes récompenses !")),
            Map.entry("jouki", new Quest("Le sommet de la pagode", "Anna", "Bonjour, Héros ! Je suis Anna. Ici, je n'ai rien à vendre… mais on raconte que la vue, tout en haut de la pagode, vaut tous les trésors. Monte, et dis-moi si c'est vrai !", Kind.REACH, 1, Material.BELL, "sommet", "Alors, cette vue ? Je te l'avais dit : elle vaut tous les trésors… et c'est bien le seul que j'aie à t'offrir ici !")));

    /** Ou en est une equipe dans la quete d'un lieu. */
    private static final class Run {
        final String team, place;
        int done;
        boolean finished;
        /** La quete est faite, mais le lieu est complet : l'equipe doit encore choisir qui en perd le privilege. */
        boolean pending;
        final List<Entity> things = new ArrayList<>();          // les monstres
        TitanBoss boss;                                          // le Titan de Shiganshina
        /** Les objets a ramasser (ou le repere du sommet) : ou ils sont, et ce qui les montre quand un joueur est pres. */
        final Map<Location, Entity> spots = new java.util.LinkedHashMap<>();
        Location target, home;
        UUID carrier;
        Location carriedFrom;
        /** Le bord de l'etang (peche) : les cases d'eau qui touchent la berge. Cherche une fois, pres d'Anna. */
        List<Location> shore;
        Run(String team, String place) { this.team = team; this.place = place; }
    }

    private final CrossfightPlugin plugin;
    private final NamespacedKey mark, pillar, rod;
    /** Les pecheurs : ou en est la prise de chacun. */
    private final Map<UUID, Catch> catches = new HashMap<>();
    private final Tablet tablet;
    private final Puzzles puzzles;
    /** Le pilier que chaque joueur ne voit plus, le temps de son epreuve. */
    private final Map<UUID, List<Entity>> hiddenPillars = new HashMap<>();
    /** Par equipe (ou par joueur sans equipe) puis par lieu. */
    private final Map<String, Map<String, Run>> runs = new HashMap<>();

    public Quests(CrossfightPlugin plugin) {
        this.plugin = plugin;
        this.mark = new NamespacedKey(plugin, "quete_cible");
        this.pillar = new NamespacedKey(plugin, "quete_pilier");
        this.tablet = new Tablet(plugin);
        this.puzzles = new Puzzles(plugin);
        Bukkit.getPluginManager().registerEvents(puzzles, plugin);
        this.rod = new NamespacedKey(plugin, "quete_canne");
        Bukkit.getScheduler().runTaskTimer(plugin, this::fish, 100L, 1L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::bosses, 100L, 1L);
        Bukkit.getPluginManager().registerEvents(tablet, plugin);
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
        return r != null && r.finished && !r.pending;
    }

    /** Vrai si une equipe au moins a fini la quete de ce lieu (pour ce qui profite a tous, comme la caisse garantie). */
    public boolean anyUnlocked(String place) {
        if (!on() || !QUESTS.containsKey(place)) return true;
        for (Map<String, Run> m : runs.values()) { Run r = m.get(place); if (r != null && r.finished && !r.pending) return true; }
        return false;
    }

    /** Pour le panneau de quete : le nom de la quete, ou elle en est, et la couleur de cet etat ; null s'il n'y a rien a montrer. */
    public String[] panel(Player p, String place) {
        Quest q = QUESTS.get(place);
        if (q == null || !on()) return null;
        Run r = run(p, place);
        if (r == null) return new String[]{q.title(), "parle à Anna", "#7a4a12"};
        if (r.finished && r.pending) return new String[]{q.title(), "choisis qui part", "#a02020"};
        if (r.finished) return new String[]{q.title(), "terminée", "#2e6b1f"};
        return new String[]{q.title(), q.kind() == Kind.REACH ? "en cours" : r.done + " / " + q.count() + " " + q.thing(), "#7a4a12"};
    }

    /** Vrai si l'equipe de ce joueur a fini la quete de ce lieu. */
    public boolean finished(Player p, String place) {
        Run r = run(p, place);
        return r != null && r.finished;
    }

    /** Les lignes du tableau de bord pour un joueur dans ce lieu. */
    public List<String> lines(Player p, String place) {
        Quest q = QUESTS.get(place);
        if (q == null || !on()) return List.of();
        Run r = run(p, place);
        String state = r == null ? "<gray>parle au personnage" : r.finished ? "<green>terminée" : q.kind() == Kind.REACH ? "<yellow>en cours" : "<yellow>" + r.done + " / " + q.count() + " " + q.thing();
        return List.of("<yellow><bold>!</bold> <white>Quête", "<gold>" + q.title(), state, "");
    }

    // ------------------------------------------------------------------ parler au personnage

    /** Un clic sur le personnage du lieu : il lance la quete, dit ou elle en est, ou recoit ce qu'on lui rapporte. */
    public void talk(Player p, String place, Location npc) {
        Quest q = QUESTS.get(place);
        if (q == null) return;
        if (!plugin.game().isRunning() && !p.hasPermission("crossfight.admin")) { Msg.send(p, "<yellow>" + q.giver() + " <gray>: <white>Revenez pendant la partie."); return; }
        Run r = run(p, place);
        if (r != null && r.finished && r.pending) { openEject(p, r); return; }
        if (r != null && r.finished) {
            // La quete faite, Anna tient boutique la ou le lieu offre des potions ou des enchantements.
            if ("potions".equals(plugin.places().useOf(place))) plugin.places().openBrews(p);
            else if ("enchantement".equals(plugin.places().useOf(place))) plugin.places().openEnchants(p);
            else if ("rythme".equals(plugin.places().useOf(place))) plugin.rhythm().open(p);
            else if ("marchand".equals(plugin.places().useOf(place))) plugin.places().openShop(p);
            else Msg.send(p, "<yellow>" + q.giver() + " <gray>: <white>" + q.thanks());
            return;
        }
        if (r == null) { start(p, place, npc); return; }
        if (q.kind() == Kind.CARRY && p.getUniqueId().equals(r.carrier)) { deliver(r, q, p); return; }
        if (q.kind() == Kind.FISH) giveRod(p);
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
        contest(r, q);
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
                r.boss = new TitanBoss(titan(at, r));
                r.things.add(r.boss.giant);
            }
            case FISH -> { for (Player m : members(r.team)) giveRod(m); }
            case PUZZLE -> {
                // Quatre piliers eteints autour du personnage : chacun ouvre une tablette a qui clique dessus.
                int[][] around = {{5, 0}, {-5, 0}, {0, 5}, {0, -5}};
                for (int i = 0; i < q.count(); i++) {
                    org.bukkit.block.Block b = plugin.worldMap().standAt(w, r.home.getBlockX() + around[i][0], r.home.getBlockZ() + around[i][1]);
                    Location at = b == null || Math.abs(b.getY() + 1 - r.home.getBlockY()) > 4 ? r.home.clone().add(around[i][0], 0, around[i][1]) : new Location(w, b.getX() + 0.5, b.getY() + 1, b.getZ() + 0.5);
                    final String tag = r.team + "/" + place + "/" + i;
                    r.things.add(w.spawn(at, org.bukkit.entity.BlockDisplay.class, d -> {
                        d.setPersistent(false);
                        d.setBlock(Material.CRYING_OBSIDIAN.createBlockData());
                        d.setGlowing(true);
                        d.setGlowColorOverride(org.bukkit.Color.AQUA);
                        d.setBrightness(new Display.Brightness(15, 15));
                        d.setTransformation(new Transformation(new Vector3f(-0.35f, 0f, -0.35f), new Quaternionf(), new Vector3f(0.7f, 2.4f, 0.7f), new Quaternionf()));
                    }));
                    r.things.add(w.spawn(at, org.bukkit.entity.Interaction.class, d -> {
                        d.setPersistent(false);
                        d.setInteractionWidth(1.1f);
                        d.setInteractionHeight(2.5f);
                        d.getPersistentDataContainer().set(pillar, PersistentDataType.STRING, tag);
                    }));
                    r.things.add(w.spawn(at.clone().add(0, 2.8, 0), org.bukkit.entity.TextDisplay.class, d -> {
                        d.setPersistent(false);
                        d.setBillboard(Display.Billboard.CENTER);
                        d.setBrightness(new Display.Brightness(15, 15));
                        d.setViewRange(0.4f);
                        d.text(Msg.mm("<aqua>Pilier éteint\n<white>clic droit"));
                    }));
                }
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
        // une epreuve fermee (resolue ou non) : son pilier reparait
        hiddenPillars.entrySet().removeIf(e -> {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null && (tablet.isOpen(p) || puzzles.isOpen(p))) return false;
            if (p != null) for (Entity x : e.getValue()) if (x.isValid()) p.showEntity(plugin, x);
            return true;
        });
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
                    // Le Titan vit tick par tick (bosses()) ; ici, on ne fait que le remettre s'il a disparu (monde decharge).
                    if (r.boss != null && !r.boss.giant.isValid()) {
                        boolean near = false;
                        for (Player p : team) if (p.getWorld().equals(r.home.getWorld()) && p.getLocation().distanceSquared(r.home) < 60 * 60) { near = true; break; }
                        if (near) {
                            int left = r.boss.hitsLeft();
                            r.boss.remove();
                            r.things.clear();
                            r.boss = new TitanBoss(titan(r.home.clone().add(8, 0.2, 0), r));
                            r.things.add(r.boss.giant);
                            for (int k = TitanBoss.HITS; k > left; k--) r.boss.spare();
                        }
                    }
                }
                case PUZZLE -> { }
                case FISH -> shore(r, team);
                case REACH -> {
                    show(r, q);
                    if (beat % 8 == 0) r.target.getWorld().spawnParticle(Particle.END_ROD, r.target.clone().add(0, 1, 0), 6, 0.3, 1.2, 0.3, 0.01);
                    for (Player p : team) if (!p.isDead() && p.getGameMode() != GameMode.SPECTATOR && p.getWorld().equals(r.target.getWorld()) && p.getLocation().distanceSquared(r.target) < 16) { finish(r, q); break; }
                }
            }
        }
    }

    /**
     * Le bord de l'etang brille pour l'equipe qui a la quete de peche : des etincelles le long de la berge, visibles
     * d'elle seule. L'etang est l'eau la plus proche d'Anna (a 48 blocs au plus), suivie de proche en proche.
     */
    private void shore(Run r, List<Player> team) {
        World w = r.home.getWorld();
        if (r.shore == null) {
            boolean near = false;
            for (Player p : team) if (p.getWorld().equals(w) && p.getLocation().distanceSquared(r.home) < 48 * 48) { near = true; break; }
            if (!near) return;
            r.shore = new ArrayList<>();
            // L'eau a ciel ouvert la plus proche d'Anna.
            org.bukkit.block.Block start = null;
            int hx = r.home.getBlockX(), hy = r.home.getBlockY(), hz = r.home.getBlockZ();
            double best = Double.MAX_VALUE;
            for (int dx = -48; dx <= 48; dx++) for (int dz = -48; dz <= 48; dz++) {
                if (dx * dx + dz * dz >= best || !w.isChunkLoaded((hx + dx) >> 4, (hz + dz) >> 4)) continue;
                for (int dy = 6; dy >= -12; dy--) {
                    org.bukkit.block.Block b = w.getBlockAt(hx + dx, hy + dy, hz + dz);
                    if (b.getType() == Material.WATER && b.getRelative(0, 1, 0).getType().isAir()) { start = b; best = dx * dx + dz * dz; break; }
                }
            }
            if (start == null) return;
            // De proche en proche, a la surface : tout l'etang, et son bord.
            java.util.ArrayDeque<org.bukkit.block.Block> todo = new java.util.ArrayDeque<>(List.of(start));
            java.util.Set<Long> seen = new java.util.HashSet<>(List.of(((long) start.getX() << 32) ^ (start.getZ() & 0xffffffffL)));
            int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            while (!todo.isEmpty() && seen.size() < 6000) {
                org.bukkit.block.Block b = todo.poll();
                boolean edge = false;
                for (int[] d : sides) {
                    int x = b.getX() + d[0], z = b.getZ() + d[1];
                    if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
                    org.bukkit.block.Block n = w.getBlockAt(x, b.getY(), z);
                    if (n.getType() != Material.WATER) { edge = true; continue; }
                    if (n.getRelative(0, 1, 0).getType().isAir() && seen.add(((long) x << 32) ^ (z & 0xffffffffL))) todo.add(n);
                }
                if (edge) r.shore.add(b.getLocation().add(0.5, 1.15, 0.5));
            }
        }
        if (beat % 2 != 0 || r.shore.isEmpty()) return;
        var random = java.util.concurrent.ThreadLocalRandom.current();
        for (Player p : team) {
            if (!p.getWorld().equals(w)) continue;
            for (Location at : r.shore) if (random.nextInt(3) == 0 && p.getLocation().distanceSquared(at) < 64 * 64)
                p.spawnParticle(Particle.END_ROD, at.getX() + random.nextDouble(-0.4, 0.4), at.getY(), at.getZ() + random.nextDouble(-0.4, 0.4), 1, 0, 0, 0, 0.005);
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
        if (r.boss != null) { r.boss.remove(); r.boss = null; }
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
        if (q.kind() == Kind.FISH) for (Player m : members(r.team)) { takeRod(m); Catch c = catches.remove(m.getUniqueId()); if (c != null) drop(m, c); }
        // Le lieu est-il complet ? Alors l'equipe doit d'abord choisir qui en perd le privilege.
        int cap = cap();
        r.pending = true;                                  // le temps de compter : holders() ne compte pas une equipe en attente
        r.pending = cap > 0 && holders(r.place).size() >= cap;
        for (Player m : members(r.team)) {
            m.showTitle(net.kyori.adventure.title.Title.title(Msg.mm("<green><bold>Quête terminée"), Msg.mm("<white>" + q.title())));
            m.playSound(m.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        if (r.pending) {
            for (Player m : members(r.team)) {
                Msg.send(m, "<gold>" + placeName(r.place) + " <yellow>est complet : choisis l'équipe qui perd son privilège. <gray>(reparle à " + q.giver() + " si tu fermes le menu)");
                openEject(m, r);
            }
            return;
        }
        grant(r, q, null);
    }

    // ------------------------------------------------------------------ les places d'un lieu

    /** Combien d'equipes peuvent garder le privilege d'un meme lieu (0 : autant qu'on veut). */
    private int cap() { return Math.max(0, plugin.getConfig().getInt("structures.equipes-par-lieu", 2)); }

    /** Les equipes qui ont le privilege de ce lieu. */
    private List<String> holders(String place) {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, Run>> e : runs.entrySet()) { Run r = e.getValue().get(place); if (r != null && r.finished && !r.pending) out.add(e.getKey()); }
        return out;
    }

    /** Le nom d'une equipe, pour les annonces : "Equipe 3" en couleur, ou le nom du joueur seul. */
    private String teamName(String team) {
        if (team.startsWith("e")) {
            try { Team t = plugin.teams().get(Integer.parseInt(team.substring(1))); if (t != null) return t.coloredName(); } catch (NumberFormatException ignored) { }
        }
        try { String name = Bukkit.getOfflinePlayer(UUID.fromString(team)).getName(); if (name != null) return "<white>" + name; } catch (IllegalArgumentException ignored) { }
        return "<white>une équipe";
    }

    private String placeName(String place) {
        String title = plugin.structures().title(place);
        return title == null || title.isBlank() ? place : title;
    }

    /** Ce qu'on dit des places d'un lieu : "1 place libre sur 2", "complet (2 équipes)". */
    private String seats(String place) {
        int cap = cap(), taken = holders(place).size();
        if (cap == 0) return "<gray>" + taken + (taken > 1 ? " équipes ont" : " équipe a") + " ce privilège";
        int left = Math.max(0, cap - taken);
        return left == 0 ? "<red>complet <gray>(" + cap + (cap > 1 ? " équipes)" : " équipe)") : "<green>" + left + (left > 1 ? " places libres" : " place libre") + " <gray>sur " + cap;
    }

    /** Une equipe se lance dans la quete d'un lieu : on lui dit ce qu'il reste de places ; s'il est complet, ceux qui le tiennent sont prevenus. */
    private void contest(Run r, Quest q) {
        int cap = cap();
        if (cap == 0) return;
        List<String> held = holders(r.place);
        for (Player m : members(r.team)) Msg.send(m, "<gold>" + placeName(r.place) + " <gray>: " + seats(r.place) + "<gray>.");
        if (held.size() < cap) return;
        for (Player m : members(r.team)) {
            Msg.send(m, "<red>Ce lieu est complet. <yellow>Les équipes qui le tiennent sont averties de ta quête : si tu la termines, tu choisiras laquelle perd son privilège.");
            m.playSound(m.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.7f);
        }
        for (String team : held) for (Player m : members(team)) {
            m.showTitle(net.kyori.adventure.title.Title.title(Msg.mm("<red><bold>Privilège menacé"), Msg.mm("<white>" + placeName(r.place))));
            Msg.send(m, teamName(r.team) + " <red>tente la quête de <gold>" + placeName(r.place) + "<red> : si elle la termine, ton équipe peut perdre son privilège. Défends-le !");
            m.playSound(m.getLocation(), Sound.BLOCK_BELL_USE, 1f, 0.7f);
        }
    }

    /** L'equipe gagne le privilege du lieu (apres avoir ecarte `ejected`, s'il etait complet), et tout le monde l'apprend. */
    private void grant(Run r, Quest q, String ejected) {
        r.pending = false;
        String use = plugin.places().label(r.place);
        for (Player m : members(r.team)) {
            Msg.send(m, "<yellow>" + q.giver() + " <gray>: <white>" + q.thanks());
            Msg.send(m, use == null ? "<green>Quête terminée : <white>" + q.title() + "<green>." : "<green>Quête terminée : <white>" + q.title() + "<green>. <gold>" + use + " <green>est débloqué pour ton équipe jusqu'à la fin de la partie.");
        }
        // L'annonce a tous les autres.
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (teamOf(other).equals(r.team)) continue;
            Msg.send(other, teamName(r.team) + " <yellow>a terminé la quête de <gold>" + placeName(r.place) + (use == null ? "" : " <gray>(" + use + ")")
                    + (ejected == null ? "" : " <yellow>et en a écarté " + teamName(ejected)) + "<yellow>. " + seats(r.place) + "<gray>.");
            other.playSound(other.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
        }
        if (cap() > 0 && holders(r.place).size() >= cap()) for (Player m : members(r.team))
            Msg.send(m, "<gray>Ce lieu est maintenant complet : une équipe qui terminera sa quête pourra écarter la tienne. Tu seras averti si quelqu'un s'y lance.");
    }

    /** Le menu qui demande quelle equipe perd son privilege : une tete par equipe qui tient le lieu. */
    private final class Eject implements org.bukkit.inventory.InventoryHolder {
        final org.bukkit.inventory.Inventory inv;
        final Run run;
        final List<String> teams = new ArrayList<>();

        Eject(Run run, Player p) {
            this.run = run;
            inv = Bukkit.createInventory(this, 54, Places.dressed(p, '\uE005', "Quelle équipe perd son privilège ?"));
            for (String team : holders(run.place)) {
                if (teams.size() >= 7) break;
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                UUID first = null;
                if (team.startsWith("e")) {
                    try {
                        Team t = plugin.teams().get(Integer.parseInt(team.substring(1)));
                        if (t != null) for (UUID id : t.members()) {
                            if (first == null) first = id;
                            String n = Bukkit.getOfflinePlayer(id).getName();
                            lore.add(Msg.mm("<gray>" + (n == null ? "?" : n)).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                        }
                    } catch (NumberFormatException ignored) { }
                } else {
                    try { first = UUID.fromString(team); } catch (IllegalArgumentException ignored) { }
                }
                if (first != null) meta.setOwningPlayer(Bukkit.getOfflinePlayer(first));
                meta.displayName(Msg.mm(teamName(team)).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                lore.add(Msg.mm("<red>Clic : elle perd le privilège de ce lieu").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                meta.lore(lore);
                head.setItemMeta(meta);
                inv.setItem(19 + teams.size(), head);
                teams.add(team);
            }
        }

        @Override public org.bukkit.inventory.Inventory getInventory() { return inv; }
    }

    private void closeEject(String team) {
        for (Player m : members(team)) if (m.getOpenInventory().getTopInventory().getHolder() instanceof Eject) m.closeInventory();
    }

    private void openEject(Player p, Run r) {
        // Entre-temps, une place a pu se liberer : alors il n'y a plus personne a ecarter.
        if (cap() == 0 || holders(r.place).size() < cap()) { closeEject(r.team); grant(r, QUESTS.get(r.place), null); return; }
        p.openInventory(new Eject(r, p).inv);
    }

    @EventHandler
    public void onEject(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Eject menu)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != menu.inv || e.getSlot() < 19 || e.getSlot() - 19 >= menu.teams.size() || !(e.getWhoClicked() instanceof Player p)) return;
        Run r = menu.run;
        if (!r.pending) { p.closeInventory(); return; }                    // un equipier a deja choisi
        String out = menu.teams.get(e.getSlot() - 19);
        Map<String, Run> theirs = runs.get(out);
        Run lost = theirs == null ? null : theirs.get(r.place);
        boolean ejected = lost != null && lost.finished && !lost.pending;
        if (ejected) {
            // L'equipe ecartee repart de zero : elle peut refaire la quete pour reprendre sa place.
            clear(lost);
            theirs.remove(r.place);
            String use = plugin.places().label(r.place);
            for (Player m : members(out)) {
                m.closeInventory();                                         // le menu d'Anna, s'il etait ouvert
                m.showTitle(net.kyori.adventure.title.Title.title(Msg.mm("<red><bold>Privilège perdu"), Msg.mm("<white>" + placeName(r.place))));
                Msg.send(m, teamName(r.team) + " <red>a terminé la quête de <gold>" + placeName(r.place) + " <red>et a écarté ton équipe" + (use == null ? "" : " : <gold>" + use + " <red>ne t'est plus ouvert") + ". <yellow>Refais la quête pour reprendre ta place.");
                m.playSound(m.getLocation(), Sound.ENTITY_WITHER_HURT, 0.8f, 0.8f);
            }
        }
        closeEject(r.team);
        grant(r, QUESTS.get(r.place), ejected ? out : null);
    }

    // ------------------------------------------------------------------ les monstres des quetes

    // ------------------------------------------------------------------ la peche (Garreg Mach)

    /**
     * Une prise en cours. Quatre temps : la ligne est a l'eau (WAIT) ; ca mord, il faut ferrer vite (BITE) ; le jeu des
     * cercles, manche apres manche (RING), avec une courte pause entre deux (PAUSE). On ne rate jamais un poisson : mal
     * jouer le fatigue seulement moins vite.
     */
    private static final class Catch {
        static final int WAIT = 0, BITE = 1, RING = 2, PAUSE = 3;
        final String place;
        Location water;
        org.bukkit.entity.FishHook hook;
        int air;
        int state = WAIT, timer, stamina, max, combo;
        String fish;
        net.kyori.adventure.bossbar.BossBar bar;
        Catch(String place) { this.place = place; }
    }

    private static final String[] FISHES = {"une carpe", "une truite", "un gardon", "une perche", "un brochet", "une anguille", "un silure"};
    /** Le cercle se resserre en vingt-huit images, une par tick ; son rayon a l'image i : 62 - 2 i. */
    private static final int FRAMES = 28;
    /** Le rayon exterieur de chaque zone de la cible (tools/build_peche.py a les memes). */
    private static final int GOOD = 40, GREAT = 27, PERFECT = 16;

    private boolean isRod(ItemStack it) {
        return it != null && it.getType() == Material.FISHING_ROD && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(rod, PersistentDataType.BYTE);
    }

    private void giveRod(Player p) {
        for (ItemStack it : p.getInventory().getContents()) if (isRod(it)) return;
        ItemStack it = new ItemStack(Material.FISHING_ROD);
        var meta = it.getItemMeta();
        meta.displayName(Msg.mm("<aqua>Canne d'Anna").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(List.of(Msg.mm("<gray>Clic droit en visant l'eau : lancer la ligne.").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
                Msg.mm("<gray>Quand ça mord, clic droit ; puis arrête le cercle au cœur de la cible.").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(rod, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        p.getInventory().addItem(it).values().forEach(x -> p.getWorld().dropItem(p.getLocation(), x));
    }

    private void takeRod(Player p) {
        for (ItemStack it : p.getInventory().getContents()) if (isRod(it)) p.getInventory().remove(it);
    }

    private static boolean packed(Player p) {
        return com.kiokouwhite.crossfight.util.Sounds.has("font.peche") && com.kiokouwhite.crossfight.util.Sounds.hasPackLoaded(p);
    }

    private static void title(Player p, net.kyori.adventure.text.Component text, int ticks) {
        p.showTitle(net.kyori.adventure.title.Title.title(text, net.kyori.adventure.text.Component.empty(),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(ticks * 50L), java.time.Duration.ofMillis(100))));
    }

    /** La prise s'arrete (poisson pris, joueur parti...) : sa barre disparait. */
    private void drop(Player p, Catch c) {
        if (p != null && c.bar != null) p.hideBossBar(c.bar);
        if (p != null) p.clearTitle();
        if (c.hook != null && c.hook.isValid()) c.hook.remove();
    }

    /**
     * La canne d'Anna lance une vraie ligne : le bouchon part, tombe a l'eau et y reste. Mais ce n'est pas la peche du jeu :
     * rien ne mord tout seul, et tant qu'une prise est en cours, le clic droit qui remonterait la ligne sert a ferrer ou
     * a arreter le cercle.
     */
    @EventHandler
    public void onCast(org.bukkit.event.player.PlayerFishEvent e) {
        Player p = e.getPlayer();
        if (!isRod(p.getInventory().getItemInMainHand()) && !isRod(p.getInventory().getItemInOffHand())) return;
        Catch c = catches.get(p.getUniqueId());
        if (e.getState() == org.bukkit.event.player.PlayerFishEvent.State.FISHING) {
            // Lancer.
            Structures.Placed here = plugin.structures().at(p.getLocation());
            Run r = here == null ? null : run(p, here.name());
            if (r == null || r.finished || QUESTS.get(r.place).kind() != Kind.FISH) { e.setCancelled(true); Msg.actionBar(p, "<gray>Cette canne ne sert que pour la quête d'Anna, au monastère."); return; }
            if (c != null) { catches.remove(p.getUniqueId()); c.hook = null; drop(p, c); }
            var random = java.util.concurrent.ThreadLocalRandom.current();
            c = new Catch(r.place);
            c.hook = e.getHook();
            c.hook.setMaxWaitTime(2_000_000);
            c.hook.setMinWaitTime(1_000_000);
            c.water = c.hook.getLocation();
            c.timer = 30 + random.nextInt(50);
            c.max = c.stamina = 8 + random.nextInt(5);
            c.fish = FISHES[random.nextInt(FISHES.length)];
            catches.put(p.getUniqueId(), c);
            return;
        }
        if (c == null) return;                       // une ligne sans prise (reste d'avant) : elle se remonte normalement
        if (e.getState() == org.bukkit.event.player.PlayerFishEvent.State.BITE || e.getState() == org.bukkit.event.player.PlayerFishEvent.State.LURED) { e.setCancelled(true); return; }
        if (c.state == Catch.WAIT) {
            // Rien n'a encore mordu : le joueur remonte sa ligne, pour la relancer ailleurs.
            catches.remove(p.getUniqueId());
            c.hook = null;
            drop(p, c);
            return;
        }
        e.setCancelled(true);                        // la ligne reste a l'eau : ce clic est un coup du jeu
        Run r = run(p, c.place);
        if (r == null || r.finished) return;
        switch (c.state) {
            case Catch.BITE -> {
                // Ferre : le combat commence. Un ferrage eclair (moins d'une demi-seconde) entame deja le poisson.
                boolean quick = c.timer > 30;
                c.bar = net.kyori.adventure.bossbar.BossBar.bossBar(Msg.mm("<aqua>" + java.lang.Character.toUpperCase(c.fish.charAt(c.fish.indexOf(' ') + 1)) + c.fish.substring(c.fish.indexOf(' ') + 2) + " <gray>- sa résistance"),
                        1f, net.kyori.adventure.bossbar.BossBar.Color.BLUE, net.kyori.adventure.bossbar.BossBar.Overlay.NOTCHED_10);
                p.showBossBar(c.bar);
                p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 1.2f);
                if (quick) hit(p, r, c, 2, "<aqua><bold>FERRAGE ÉCLAIR !", 1.6f);
                else { c.state = Catch.PAUSE; c.timer = 10; title(p, Msg.mm("<aqua>Ferré !"), 10); }
            }
            case Catch.RING -> {
                // Arreter le cercle : plus il est pres du centre, plus le poisson fatigue.
                int radius = 62 - 2 * Math.min(FRAMES - 1, c.timer);
                if (radius <= PERFECT) { c.combo++; hit(p, r, c, 4 + Math.min(2, c.combo / 2), "<gold><bold>PARFAIT !", 1.9f); }
                else if (radius <= GREAT) { c.combo = 0; hit(p, r, c, 3, "<yellow><bold>TRÈS BIEN !", 1.5f); }
                else if (radius <= GOOD) { c.combo = 0; hit(p, r, c, 2, "<green><bold>BIEN", 1.2f); }
                else { c.combo = 0; hit(p, r, c, 1, "<gray>Trop tôt…", 0.7f); }
            }
            default -> { }
        }
    }

    /** Un coup porte au poisson : sa barre baisse, la note s'affiche, et le poisson est pris quand elle est vide. */
    private void hit(Player p, Run r, Catch c, int strength, String note, float pitch) {
        c.stamina -= strength;
        if (c.bar != null) c.bar.progress(Math.max(0f, c.stamina / (float) c.max));
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, pitch);
        c.water.getWorld().spawnParticle(Particle.SPLASH, c.water, 10 + strength * 6, 0.3, 0.1, 0.3, 0.1);
        if (c.stamina > 0) {
            title(p, Msg.mm(note), 12);
            c.state = Catch.PAUSE;
            c.timer = 14;
            return;
        }
        catches.remove(p.getUniqueId());
        drop(p, c);
        r.done++;
        Quest q = QUESTS.get(r.place);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 0.7f, 1.4f);
        title(p, Msg.mm(note), 14);
        Msg.send(p, "<aqua>Tu as pris " + c.fish + " ! <gray>(" + r.done + " / " + q.count() + ")");
        tell(r, q);
        if (r.done >= q.count()) finish(r, q);
    }

    /** A chaque tick : l'attente de la touche, la touche, le cercle qui se resserre. */
    private void fish() {
        var random = java.util.concurrent.ThreadLocalRandom.current();
        for (var e : new ArrayList<>(catches.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            Catch c = e.getValue();
            if (p == null || p.isDead() || c.hook == null || !c.hook.isValid()) { drop(p, c); catches.remove(e.getKey()); continue; }
            c.water = c.hook.getLocation();
            Run r = run(p, c.place);
            if (r == null || r.finished) { drop(p, c); catches.remove(e.getKey()); continue; }
            switch (c.state) {
                case Catch.WAIT -> {
                    if (c.hook.getState() != org.bukkit.entity.FishHook.HookState.BOBBING) {
                        // Le bouchon vole encore, ou il est tombe ailleurs que dans l'eau.
                        if (++c.air > 60 || c.hook.isOnGround()) { Msg.actionBar(p, "<red>Lance ta ligne dans l'eau de l'étang."); drop(p, c); catches.remove(e.getKey()); }
                        break;
                    }
                    if (--c.timer > 0) { if (c.timer % 20 == 0) Msg.actionBar(p, "<gray>La ligne est à l'eau…"); break; }
                    c.hook.setVelocity(new org.bukkit.util.Vector(0, -0.25, 0));
                    c.state = Catch.BITE;
                    c.timer = 40;
                    p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 1f, 1f);
                    c.water.getWorld().spawnParticle(Particle.SPLASH, c.water, 25, 0.3, 0.1, 0.3, 0.1);
                    title(p, Msg.mm("<yellow><bold>ÇA MORD !"), 40);
                    Msg.actionBar(p, "<yellow>Clic droit, vite !");
                }
                case Catch.BITE -> {
                    if (--c.timer > 0) break;
                    // Pas ferre a temps : il lache l'appat, et reviendra.
                    c.state = Catch.WAIT;
                    c.timer = 20 + random.nextInt(40);
                    p.clearTitle();
                    Msg.actionBar(p, "<gray>Il a lâché l'appât… il va revenir.");
                }
                case Catch.PAUSE -> { if (--c.timer <= 0) { c.state = Catch.RING; c.timer = 0; } }
                case Catch.RING -> {
                    int frame = c.timer;
                    if (frame >= FRAMES) { c.combo = 0; hit(p, r, c, 1, "<gray>Trop tard…", 0.7f); break; }
                    int radius = 62 - 2 * frame;
                    if (packed(p)) {
                        // Avec le pack : la cible et le cercle qui se resserre, au milieu de l'ecran (tools/build_peche.py).
                        title(p, net.kyori.adventure.text.Component.text(String.valueOf((char) (0xE200 + frame))).font(net.kyori.adventure.key.Key.key(com.kiokouwhite.crossfight.util.Sounds.NS, "peche"))
                                .color(net.kyori.adventure.text.format.NamedTextColor.WHITE), 5);
                        if (c.timer == 0) Msg.actionBar(p, "<white>Clic droit quand le cercle est au centre de la cible.");
                    } else {
                        String zone = radius <= PERFECT ? "<gold><bold>PARFAIT" : radius <= GREAT ? "<yellow>TRÈS BIEN" : radius <= GOOD ? "<green>BIEN" : "<gray>attends…";
                        Msg.actionBar(p, "<white>❯" + " ".repeat(radius / 5) + "◉" + " ".repeat(radius / 5) + "❮  " + zone);
                    }
                    c.timer++;
                }
                default -> { }
            }
        }
    }

    /** Pour les essais (/cf tablette) : une tablette a trois blocs et demi devant le joueur, sans quete. */
    public void tryTablet(Player p) {
        Vector look = p.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 0.01) look = new Vector(0, 0, 1);
        tablet.open(p, p.getLocation().add(look.normalize().multiply(4.2)), who -> Msg.send(who, "<green>Tablette résolue."));
    }

    /** Pour les essais (/cf enigme <nom>) : une enigme a trois blocs et demi devant le joueur, sans quete. */
    public void tryPuzzle(Player p, String kind) {
        tablet.close(p);
        if (kind.equals("tablette")) { puzzles.close(p); tryTablet(p); return; }
        Vector look = p.getLocation().getDirection().setY(0);
        if (look.lengthSquared() < 0.01) look = new Vector(0, 0, 1);
        puzzles.open(p, kind, p.getLocation().add(look.normalize().multiply(4.2)), who -> Msg.send(who, "<green>Énigme résolue."));
    }

    /** Un clic sur un pilier : la tablette se dresse devant lui, pour un membre de l'equipe a qui il appartient. */
    @EventHandler
    public void onPillar(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        String tag = e.getRightClicked().getPersistentDataContainer().get(pillar, PersistentDataType.STRING);
        if (tag == null) return;
        e.setCancelled(true);
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (tablet.isOpen(p) || puzzles.isOpen(p)) return; // son casse-tete est ouvert : le clic est pour lui
        String[] t = tag.split("/");
        Run r = run(p, t[1]);
        if (r == null || r.finished || !r.team.equals(t[0])) { Msg.actionBar(p, "<gray>Ce pilier attend une autre équipe : parle à Anna pour avoir les tiens."); return; }
        final Entity clicked = e.getRightClicked();
        // chaque pilier a son enigme : la tablette, le circuit, l'etabli, le puzzle
        int index = t.length > 2 && t[2].matches("\\d+") ? Integer.parseInt(t[2]) % 4 : 0;
        java.util.function.BiConsumer<Location, java.util.function.Consumer<Player>> puzzle = index == 0 ? (at, done) -> tablet.open(p, at, done) : (at, done) -> puzzles.open(p, Puzzles.KINDS.get(index - 1), at, done);
        // tant que son epreuve est ouverte, le joueur ne voit plus ce pilier (ni sa lueur, ni son etiquette) : rien ne gene le tableau
        List<Entity> gone = new ArrayList<>();
        for (Entity x : r.things) if (x.getLocation().getBlockX() == clicked.getLocation().getBlockX() && x.getLocation().getBlockZ() == clicked.getLocation().getBlockZ()) { p.hideEntity(plugin, x); gone.add(x); }
        hiddenPillars.put(p.getUniqueId(), gone);
        puzzle.accept(clicked.getLocation(), who -> {
            if (r.finished || !clicked.isValid()) return;
            // Le pilier se rallume : il n'a plus rien a demander.
            Location at = clicked.getLocation();
            for (Entity x : new ArrayList<>(r.things)) if (x.getLocation().getBlockX() == at.getBlockX() && x.getLocation().getBlockZ() == at.getBlockZ()) {
                if (x instanceof org.bukkit.entity.BlockDisplay d) { d.setBlock(Material.SEA_LANTERN.createBlockData()); d.setGlowColorOverride(org.bukkit.Color.YELLOW); r.spots.put(at.clone().add(0, 0.01 * r.done, 0), d); r.things.remove(x); }
                else { r.things.remove(x); x.remove(); }
            }
            r.done++;
            at.getWorld().playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.3f);
            tell(r, QUESTS.get(r.place));
            if (r.done >= QUESTS.get(r.place).count()) finish(r, QUESTS.get(r.place));
        });
    }

    /** Le corps du Titan : un mannequin a l'image d'un joueur, au skin "titan_quete" (skins.txt), six fois et demie plus grand. */
    private LivingEntity titan(Location at, Run r) {
        return at.getWorld().spawn(at, org.bukkit.entity.Mannequin.class, v -> {
            v.setPersistent(false);
            v.setImmovable(true);
            v.setSilent(true);
            v.setGlowing(true);
            v.setCollidable(false);
            var skin = com.kiokouwhite.crossfight.util.Skins.profile("titan_quete");
            if (skin != null) v.setProfile(skin);
            var scale = v.getAttribute(Attribute.SCALE);
            if (scale == null) { v.registerAttribute(Attribute.SCALE); scale = v.getAttribute(Attribute.SCALE); }
            if (scale != null) scale.setBaseValue(6.5);
            v.customName(Msg.mm("<red>Titan"));
            v.setCustomNameVisible(true);
            v.setDescription(net.kyori.adventure.text.Component.empty());
            v.getPersistentDataContainer().set(mark, PersistentDataType.STRING, r.team + "/" + r.place);
        });
    }

    /** Chaque tick : la vie des Titans (leurs attaques, leur barre). */
    private void bosses() {
        for (Map<String, Run> m : runs.values()) for (Run r : m.values())
            if (r.boss != null && !r.finished && r.boss.giant.isValid()) r.boss.tick(members(r.team));
    }

    /** Le Titan ne prend pas de degats : chaque coup d'un joueur de l'equipe qui a la quete compte pour un, et le centieme le tue. */
    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof org.bukkit.entity.Mannequin g) || !g.getPersistentDataContainer().has(mark, PersistentDataType.STRING)) return;
        e.setCancelled(true);
        Entity by = e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Entity s ? s : e.getDamager();
        if (!(by instanceof Player p)) return;
        String[] owner = g.getPersistentDataContainer().get(mark, PersistentDataType.STRING).split("/", 2);
        Map<String, Run> m = runs.get(owner[0]);
        Run r = m == null || owner.length < 2 ? null : m.get(owner[1]);
        if (r == null || r.boss == null || r.finished) return;
        if (!teamOf(p).equals(r.team)) { Msg.actionBar(p, "<red>Ce Titan est la quête d'une autre équipe."); return; }
        boolean dead = r.boss.hit(p);
        r.done = TitanBoss.HITS - r.boss.hitsLeft();
        if (dead) finish(r, QUESTS.get(r.place));
    }

    /** Aucun autre degat ne l'atteint (chute, feu, explosion...). */
    @EventHandler(ignoreCancelled = true)
    public void onTitanHurt(org.bukkit.event.entity.EntityDamageEvent e) {
        if (e instanceof EntityDamageByEntityEvent) return;
        if (e.getEntity() instanceof org.bukkit.entity.Mannequin g && g.getPersistentDataContainer().has(mark, PersistentDataType.STRING)) e.setCancelled(true);
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
        for (Run r : new ArrayList<>(m.values())) if (place == null || r.place.equals(place)) { clear(r); m.remove(r.place); if (QUESTS.get(r.place).kind() == Kind.FISH) { takeRod(p); Catch c = catches.remove(p.getUniqueId()); if (c != null) drop(p, c); } }
    }

    /** Ou en est ce joueur, lieu par lieu. */
    public String state(Player p, String place) {
        Run r = run(p, place);
        return r == null ? "<gray>pas commencée" : r.finished && r.pending ? "<red>terminée, une équipe à écarter" : r.finished ? "<green>terminée" : "<yellow>en cours (" + r.done + " / " + QUESTS.get(place).count() + ")";
    }

    /** Une partie commence, ou la map change : tout repart de zero. */
    public void reset() {
        plugin.rhythm().reset();
        tablet.reset();
        puzzles.reset();
        for (Map<String, Run> m : runs.values()) for (Run r : m.values()) clear(r);
        runs.clear();
        for (var e : catches.entrySet()) drop(Bukkit.getPlayer(e.getKey()), e.getValue());
        catches.clear();
        for (Player p : Bukkit.getOnlinePlayers()) takeRod(p);
    }
}
