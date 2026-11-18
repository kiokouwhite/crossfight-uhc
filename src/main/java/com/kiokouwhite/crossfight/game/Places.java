package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.util.Msg;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A quoi sert chaque lieu pose sur la map (game/Structures.java). Un lieu n'est pas qu'un decor : on y vient preparer
 * son combat, et l'on s'y croise. Au milieu de chaque lieu qui a un service, une borne (un objet qui flotte sous son
 * nom) : un clic droit dessus, pendant la partie, rend le service.
 *
 *   enchantement  Anna enchante une piece d'equipement : on la pose, on choisit le niveau (1, 2 ou 5 niveaux ; pas de borne)
 *   forge         l'objet tenu est repare, contre 2 niveaux
 *   rythme        le concert d'Anna : un jeu de rythme a quatre touches, des materiaux selon la note (game/Rhythm.java ; pas de borne)
 *   potions       une potion tiree au sort, toutes les 8 minutes
 *   marchand      des echanges (fer, or, diamants, charbon contre fleches, pomme doree, experience, perle, viande)
 *   nourriture    12 steaks, toutes les 8 minutes
 *   experience    pas de borne : etre dans le lieu donne de l'experience, 2 points par seconde
 *   lootbox       pas de borne : a l'evenement Lootbox, une lootbox de plus y tombe a coup sur
 *   chasse        pas de borne : cinq pieces sont cachees dans le lieu. En trouver une (passer dessus ou cliquer) donne
 *                 2 diamants et de l'experience ; a la troisieme, le contenu d'une lootbox. Une piece trouvee reparait
 *                 ailleurs six minutes plus tard. Elles scintillent quand on est a moins de douze blocs.
 *
 * Quel lieu rend quel service : structures.utilites dans config.yml (le nom du dossier du lieu, puis le service).
 */
public final class Places implements Listener {

    /** Par defaut : les lieux connus et leur service. */
    private static final Map<String, String> DEFAULTS = Map.of("garreg_mach", "enchantement", "shiganshina", "rythme", "pearl", "potions",
            "central_town", "marchand", "arcadia", "nourriture", "terrace", "experience", "beacon", "lootbox", "forest_of_hope", "chasse");

    private record Use(String title, Material icon, String hint) {}

    private static final Map<String, Use> USES = Map.ofEntries(Map.entry("chasse", new Use("Chasse aux pieces", Material.RECOVERY_COMPASS, "cinq pieces cachees a trouver")),
            Map.entry("enchantement", new Use("Enchantement", Material.ENCHANTING_TABLE, "Anna enchante ton équipement (1, 2 ou 5 niveaux)")),
            Map.entry("forge", new Use("Forge", Material.ANVIL, "repare l'objet tenu (2 niveaux)")),
            Map.entry("rythme", new Use("Concert", Material.NOTE_BLOCK, "neuf musiques à jouer chez Anna : du fer, de l'or et des diamants selon la note")),
            Map.entry("potions", new Use("Potions", Material.BREWING_STAND, "une potion au choix chez Anna, toutes les 10 minutes")),
            Map.entry("marchand", new Use("Marchand", Material.EMERALD, "echanges")),
            Map.entry("nourriture", new Use("Nourriture", Material.COOKED_BEEF, "12 steaks, toutes les 8 minutes")),
            Map.entry("experience", new Use("Experience", Material.EXPERIENCE_BOTTLE, "de l'experience tant qu'on y reste")),
            Map.entry("lootbox", new Use("Lootbox", Material.ENDER_CHEST, "une lootbox garantie a l'evenement")));

    /** Les echanges du marchand : {ce qu'on donne, combien, ce qu'on recoit, combien}. */
    private static final Object[][] TRADES = {
            {Material.IRON_INGOT, 8, Material.ARROW, 16}, {Material.GOLD_INGOT, 6, Material.GOLDEN_APPLE, 1}, {Material.DIAMOND, 2, Material.EXPERIENCE_BOTTLE, 8},
            {Material.DIAMOND, 4, Material.ENDER_PEARL, 1}, {Material.COAL, 16, Material.COOKED_BEEF, 6}};
    private static final PotionType[] POTIONS = {PotionType.SWIFTNESS, PotionType.SWIFTNESS, PotionType.FIRE_RESISTANCE, PotionType.FIRE_RESISTANCE, PotionType.LEAPING,
            PotionType.SLOW_FALLING, PotionType.NIGHT_VISION, PotionType.WATER_BREATHING, PotionType.STRENGTH};
    private static final long WAIT = 8 * 60 * 1000L;

    /**
     * Ou se tient Anna dans les lieux ou sa place est choisie : coordonnees dans la map d'origine du lieu (x, y, z), et
     * le sens ou elle regarde. Garreg Mach : sur le quai, devant le ponton de l'etang, face a l'eau.
     */
    private static final Map<String, double[]> GUIDES = Map.of("garreg_mach", new double[]{43.5, 41, 62.5, 180});

    private final CrossfightPlugin plugin;
    private final NamespacedKey mark, guide;
    /** Le lieu ou se trouve chaque joueur (son dossier), pour annoncer son nom quand il y entre. */
    private final Map<UUID, String> zone = new HashMap<>();
    /** Le personnage de quete de chaque lieu, tant que quelqu'un est pres. */
    private final Map<String, Entity> guides = new HashMap<>();
    /** La place d'Anna dans chaque lieu, cherchee une fois. */
    private final Map<String, Location> guideSpots = new HashMap<>();
    /** La bulle de dialogue au-dessus de chaque personnage de quete (le texte, et sa pointe). */
    private final Map<String, List<Entity>> bubbles = new HashMap<>();
    /** Les bornes : par lieu ("monde/nom"), ou elle est, et les entites qui la montrent tant qu'un joueur est pres. */
    private final Map<String, Location> spots = new HashMap<>();
    private final Map<String, List<Entity>> shown = new HashMap<>();
    /** La derniere fois que chacun a pris une potion, de la nourriture ("joueur/service"). */
    private final Map<String, Long> taken = new HashMap<>();

    public Places(CrossfightPlugin plugin) {
        this.plugin = plugin;
        this.mark = new NamespacedKey(plugin, "borne");
        this.guide = new NamespacedKey(plugin, "quete");
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 100L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::gaze, 100L, 2L);
    }

    /** Le service d'un lieu (son nom de dossier), ou null. */
    public String useOf(String place) {
        String use = plugin.getConfig().getString("structures.utilites." + place, DEFAULTS.get(place));
        // Shiganshina avait la forge par defaut : une configuration d'avant le concert le dit encore, sans que personne l'ait choisi.
        if ("shiganshina".equals(place) && "forge".equals(use) && !plugin.getConfig().getBoolean("structures.forge-a-shiganshina", false)) use = "rythme";
        return use != null && USES.containsKey(use) ? use : null;
    }

    /** Pour la carte : "Enchantement", ou null. */
    public String label(String place) {
        String use = useOf(place);
        return use == null ? null : USES.get(use).title();
    }

    /**
     * Place Anna la ou se tient ce joueur, pour le lieu ou il se trouve (ou la remet a sa place d'origine si {@code here}
     * est faux). Rend le nom du lieu, ou null si le joueur n'est dans aucun lieu.
     */
    public String moveGuide(Player pl, boolean here) { return moveGuide(pl.getLocation(), here); }

    /** Pareil, pour un endroit donne (ses coordonnees sur la map, et le sens ou Anna regarde). */
    public String moveGuide(Location l, boolean here) {
        Structures.Placed p = plugin.structures().at(l);
        if (p == null) return null;
        // (note dans la map d'origine du lieu : l'endroit vaut quelle que soit la map et la decoupe)
        double[] src = plugin.structures().toSource(p, l);
        plugin.getConfig().set("structures.anna-origine." + p.name(), here ? List.of(src[0], src[1], src[2], (double) l.getYaw()) : null);
        plugin.saveConfig();
        String key = l.getWorld().getName() + "/" + p.name();
        guideSpots.remove(key);
        Entity g = guides.remove(key);
        if (g != null) g.remove();
        List<Entity> b = bubbles.remove(key);
        if (b != null) b.forEach(Entity::remove);
        return p.name();
    }

    /** Dix fois par seconde : Anna tourne la tete vers le joueur le plus proche (a dix blocs), sinon reprend sa pose. */
    private void gaze() {
        for (Map.Entry<String, Entity> e : guides.entrySet()) {
            Entity g = e.getValue();
            if (!g.isValid()) continue;
            Location at = g.getLocation(), eyes = at.clone().add(0, 1.62, 0);
            Player near = null;
            double best = 10 * 10;
            for (Player pl : at.getWorld().getPlayers()) {
                if (pl.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
                double d = pl.getLocation().distanceSquared(at);
                if (d < best) { best = d; near = pl; }
            }
            float yaw, pitch = 0f;
            if (near != null) {
                org.bukkit.util.Vector to = near.getEyeLocation().toVector().subtract(eyes.toVector());
                yaw = (float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ()));
                pitch = (float) Math.max(-50, Math.min(50, -Math.toDegrees(Math.atan2(to.getY(), Math.hypot(to.getX(), to.getZ())))));
            } else {
                Location home = guideSpots.get(e.getKey());
                yaw = home != null ? home.getYaw() : at.getYaw();
            }
            // Elle tourne en douceur : un quart du chemin a chaque fois.
            float turn = ((yaw - at.getYaw()) % 360 + 540) % 360 - 180;
            if (Math.abs(turn) < 0.5f && Math.abs(pitch - at.getPitch()) < 0.5f) continue;
            float now = at.getYaw() + turn * 0.35f;
            g.setRotation(now, at.getPitch() + (pitch - at.getPitch()) * 0.35f);
            if (g instanceof org.bukkit.entity.LivingEntity l) l.setBodyYaw(now);
        }
    }

    /** Ou se tient Anna dans ce lieu : la place qu'un admin lui a donnee, sa place choisie, sinon pres de la borne. */
    private Location guideAt(World w, Structures.Placed p) {
        Location known0 = guideSpots.get(w.getName() + "/" + p.name());
        if (known0 != null) return known0.clone();
        Location spot = spot(w, p);
        if (spot == null) return null;
        // Pres de la borne, sur un sol dur a la meme hauteur ; a defaut, tout contre elle.
        Location at = spot.clone().add(0.45, 0, 0.45);
        for (int[] o : new int[][]{{3, 0}, {-3, 0}, {0, 3}, {0, -3}, {2, 2}, {-2, -2}, {2, 0}, {0, 2}}) {
            Block b = plugin.worldMap().standAt(w, spot.getBlockX() + o[0], spot.getBlockZ() + o[1]);
            if (b == null || b.isLiquid() || !b.getType().isSolid() || Math.abs(b.getY() + 1 - spot.getBlockY()) > 1) continue;
            at = new Location(w, b.getX() + 0.5, b.getY() + 1, b.getZ() + 0.5);
            break;
        }
        at.setYaw(90f);
        // Sauf si un admin l'a placee lui-meme (/cf quete placer) : l'endroit est note par rapport au coin et au
        // niveau du lieu, pour valoir aussi sur une autre map.
        List<Double> fixed = plugin.getConfig().getDoubleList("structures.anna-origine." + p.name());
        double[] known = fixed.size() == 4 ? new double[]{fixed.get(0), fixed.get(1), fixed.get(2), fixed.get(3)} : GUIDES.get(p.name());
        if (known != null) {
            Location to = plugin.structures().fromSource(w, p, known[0], known[1], known[2]);
            // (seulement si cet endroit du lieu a bien ete pose sur cette map)
            if (to.getX() >= p.x() && to.getX() < p.x() + p.sx() && to.getZ() >= p.z() && to.getZ() < p.z() + p.sz()) { to.setYaw((float) known[3]); at = to; }
        }
        guideSpots.put(w.getName() + "/" + p.name(), at.clone());
        return at;
    }

    /** Ou se tient le personnage de quete d'un lieu, pour s'y teleporter. */
    public Location guideOf(World w, String place) {
        Entity g = guides.get(w.getName() + "/" + place);
        if (g != null && g.isValid()) return g.getLocation();
        for (Structures.Placed p : plugin.structures().finished(w)) if (p.name().equals(place)) return guideAt(w, p);
        return null;
    }

    /** La quete du lieu ou se trouve ce joueur (son nom), ou null. Pour le tableau de bord. */
    /** Le panneau de quete du lieu ou se trouve ce joueur (nom, etat, couleur), ou null. */
    public String[] questPanel(Player p) {
        String place = zone.get(p.getUniqueId());
        return place == null ? null : plugin.quests().panel(p, place);
    }

    public List<String> questOf(Player p) {
        String place = zone.get(p.getUniqueId());
        return place == null ? List.of() : plugin.quests().lines(p, place);
    }

    /** Qui entre dans un lieu voit son nom au milieu de l'ecran ; pres du centre, le personnage de la quete apparait, en surbrillance. */
    private void zones(World w) {
        for (Player pl : Bukkit.getOnlinePlayers()) {
            Structures.Placed p = pl.getWorld().equals(w) ? plugin.structures().at(pl.getLocation()) : null;
            String now = p == null ? null : p.name(), before = now == null ? zone.remove(pl.getUniqueId()) : zone.put(pl.getUniqueId(), now);
            if (now == null || now.equals(before)) continue;
            Quests.Quest q = Quests.QUESTS.get(now);
            pl.showTitle(net.kyori.adventure.title.Title.title(Msg.mm("<gold><bold>" + plugin.structures().title(now)), Msg.mm(q == null ? "" : "<gray>Quête : <white>" + q.title()),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(400), java.time.Duration.ofMillis(2500), java.time.Duration.ofMillis(800))));
            pl.playSound(pl.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.7f);
        }
        for (Structures.Placed p : plugin.structures().finished(w)) {
            Quests.Quest q = Quests.QUESTS.get(p.name());
            if (q == null) continue;
            String key = w.getName() + "/" + p.name();
            // Elle n'apparait que quand un joueur est pres de SA place (pas de la borne) : ailleurs, le monde n'est pas
            // charge et elle disparaitrait aussitot.
            Location where = guideAt(w, p);
            boolean near = false;
            if (where != null) for (Player pl : w.getPlayers()) if (pl.getLocation().distanceSquared(where) < 72 * 72) { near = true; break; }
            // Qui a fini la quete voit la bulle de la recompense a la place de celle de l'accueil.
            List<Entity> said = bubbles.get(key);
            // Avec le pack, c'est la bulle dessinee (tools/build_bulle.py) ; sans lui, le texte sur fond blanc et sa pointe.
            if (said != null && said.size() == 5) for (Player pl : w.getPlayers()) {
                boolean done = plugin.quests().finished(pl, p.name());
                boolean drawn = com.kiokouwhite.crossfight.util.Sounds.has("font.bulle") && com.kiokouwhite.crossfight.util.Sounds.hasPackLoaded(pl);
                boolean[] seen = {!drawn && !done, !drawn, !drawn && done, drawn && !done, drawn && done};
                for (int k = 0; k < 5; k++) { if (seen[k]) pl.showEntity(plugin, said.get(k)); else pl.hideEntity(plugin, said.get(k)); }
            }
            Entity have = guides.get(key);
            if (have != null && (!near || !have.isValid())) { have.remove(); guides.remove(key); have = null; List<Entity> b = bubbles.remove(key); if (b != null) b.forEach(Entity::remove); }
            if (!near || have != null) continue;
            Location at = where;
            // Sa bulle : ce qu'elle a a dire, noir sur blanc, au-dessus de son nom ; on ne la voit que de pres (une
            // douzaine de blocs), pour ne pas couvrir le lieu de texte.
            final Location over = at.clone();
            List<Entity> bubble = new ArrayList<>();
            bubble.add(w.spawn(over.clone().add(0, 3.05, 0), TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setLineWidth(190);
                d.setBackgroundColor(org.bukkit.Color.fromARGB(255, 250, 250, 255));
                d.setShadowed(false);
                d.setViewRange(0.2f);
                d.text(Msg.mm("<black>" + q.says()));
            }));
            bubble.add(w.spawn(over.clone().add(0, 2.82, 0), TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                d.setShadowed(true);
                d.setViewRange(0.2f);
                d.text(Msg.mm("<white>▼"));
            }));
            // La meme bulle, pour qui a fini la quete : ce qu'il a gagne. Chaque joueur ne voit que la sienne.
            bubble.add(w.spawn(over.clone().add(0, 3.05, 0), TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setLineWidth(190);
                d.setBackgroundColor(org.bukkit.Color.fromARGB(255, 250, 250, 255));
                d.setShadowed(false);
                d.setViewRange(0.2f);
                d.text(Msg.mm("<black>" + q.thanks()));
            }));
            // Les deux memes, en bulle dessinee, pour qui a le pack.
            for (String words : new String[]{q.says(), q.thanks()}) bubble.add(w.spawn(over.clone().add(0, 3.25, 0), TextDisplay.class, d -> {
                d.setPersistent(false);
                d.setVisibleByDefault(false);
                d.setBillboard(Display.Billboard.CENTER);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setLineWidth(1000);                                       // les lignes sont deja coupees : speech()
                d.setBackgroundColor(org.bukkit.Color.fromARGB(0, 0, 0, 0));
                d.setShadowed(false);
                d.setViewRange(0.2f);
                d.text(speech(words));
            }));
            bubbles.put(key, bubble);
            // Un mannequin a l'image d'un joueur, au skin des personnages de quete ("pnj" dans skins.txt).
            guides.put(key, w.spawn(at, org.bukkit.entity.Mannequin.class, v -> {
                v.setPersistent(false);
                v.setImmovable(true);
                v.setInvulnerable(true);
                v.setSilent(true);
                v.setGlowing(true);
                v.setCollidable(false);
                var skin = com.kiokouwhite.crossfight.util.Skins.profile("pnj");
                if (skin != null) v.setProfile(skin);
                v.customName(Msg.mm("<yellow><bold>!</bold> <white>" + q.giver() + " <gray>- " + q.title()));
                v.setCustomNameVisible(true);
                v.setDescription(Msg.mm("<gray>clic droit"));
                v.getPersistentDataContainer().set(guide, PersistentDataType.STRING, p.name());
            }));
        }
    }

    private boolean on() {
        return plugin.getConfig().getBoolean("structures.services", true);
    }

    /** La borne d'un lieu : au milieu, sur son sol. Cherchee une fois, quand quelqu'un s'approche. */
    private Location spot(World w, Structures.Placed p) {
        return spots.computeIfAbsent(w.getName() + "/" + p.name(), k -> {
            // Au plus pres du milieu, mais a l'air libre : sur du dur, pas plus bas que le sol du lieu, et sous le ciel
            // (pas dans l'eau, ni dans un sous-sol, ni sous un plancher, ni perche tres haut).
            int cx = p.x() + p.sx() / 2, cz = p.z() + p.sz() / 2, level = plugin.structures().level(p);
            Block first = null;
            for (int r = 0; r <= 40; r += 2) for (int dx = -r; dx <= r; dx += 2) for (int dz = -r; dz <= r; dz += 2) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                Block b = plugin.worldMap().standAt(w, cx + dx, cz + dz);
                if (b == null) continue;
                if (first == null) first = b;
                if (!b.isLiquid() && b.getY() >= level - 1 && b.getY() <= level + 24 && b.getRelative(org.bukkit.block.BlockFace.UP).getLightFromSky() > 0 && plugin.structures().at(b.getLocation()) != null)
                    return new Location(w, b.getX() + 0.5, b.getY() + 1, b.getZ() + 0.5);
            }
            return first == null ? null : new Location(w, first.getX() + 0.5, first.getY() + 1, first.getZ() + 0.5);
        });
    }

    /** Chaque seconde : les bornes apparaissent pres des joueurs, l'experience tombe sur ceux qui sont dans son lieu. */
    private void tick() {
        World w = plugin.game().world();
        if (plugin.getConfig().getBoolean("structures.quetes", true)) zones(w);
        else { guides.values().forEach(Entity::remove); guides.clear(); bubbles.values().forEach(l -> l.forEach(Entity::remove)); bubbles.clear(); zone.clear(); }
        if (!on()) { for (List<Entity> l : shown.values()) l.forEach(Entity::remove); shown.clear(); return; }
        boolean running = plugin.game().isRunning();
        for (Structures.Placed p : plugin.structures().finished(w)) {
            String use = useOf(p.name());
            if (use == null) continue;
            String key = w.getName() + "/" + p.name();
            // (les potions n'ont pas de borne : c'est Anna qui tient boutique)
            boolean station = !use.equals("experience") && !use.equals("lootbox") && !use.equals("chasse") && !use.equals("potions") && !use.equals("enchantement") && !use.equals("rythme");
            if (use.equals("chasse")) hunt(w, p, running);
            boolean near = false;
            for (Player pl : w.getPlayers()) {
                Location at = pl.getLocation();
                boolean inside = at.getX() >= p.x() && at.getX() < p.x() + p.sx() && at.getZ() >= p.z() && at.getZ() < p.z() + p.sz();
                if (inside && running && use.equals("experience") && plugin.quests().unlocked(pl, p.name()) && plugin.players().get(pl).isAlive() && pl.getGameMode() == org.bukkit.GameMode.SURVIVAL) pl.giveExp(2);
                if (!station || !inside && at.distanceSquared(new Location(w, p.x() + p.sx() / 2.0, at.getY(), p.z() + p.sz() / 2.0)) > 96 * 96) continue;
                Location spot = spot(w, p);
                if (spot != null && at.distanceSquared(spot) < 64 * 64) near = true;
            }
            List<Entity> have = shown.get(key);
            if (have != null && (!near || have.stream().anyMatch(e -> !e.isValid()))) { have.forEach(Entity::remove); shown.remove(key); have = null; }
            if (near && have == null) shown.put(key, build(spot(w, p), p.name(), use));
        }
    }

    private List<Entity> build(Location at, String place, String use) {
        Use u = USES.get(use);
        List<Entity> out = new ArrayList<>();
        World w = at.getWorld();
        out.add(w.spawn(at.clone().add(0, 1.1, 0), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(new ItemStack(u.icon()));
            d.setBillboard(Display.Billboard.VERTICAL);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.9f, 0.9f, 0.9f), new Quaternionf()));
        }));
        out.add(w.spawn(at.clone().add(0, 2.0, 0), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(new Display.Brightness(15, 15));
            d.text(Msg.mm("<gold><bold>" + u.title() + "</bold>\n<gray>" + u.hint() + "\n<white>clic droit"));
        }));
        out.add(w.spawn(at.clone(), Interaction.class, d -> {
            d.setPersistent(false);
            d.setInteractionWidth(1.4f);
            d.setInteractionHeight(2.2f);
            d.getPersistentDataContainer().set(mark, PersistentDataType.STRING, use);
        }));
        return out;
    }

    /** La largeur d'un caractere dans la police du jeu, a peu pres (avance comprise) : de quoi couper un texte en lignes. */
    private static int charWidth(char c) {
        if ("i!.,:;|'’".indexOf(c) >= 0) return 2;
        if (c == 'l' || c == 'ì' || c == 'í') return 3;
        if ("tI[] îï".indexOf(c) >= 0) return 4;
        if ("fk<>\"()*{}".indexOf(c) >= 0) return 5;
        if (c == '@' || c == '~' || c == '…') return 7;
        return 6;
    }

    /** Le texte coupe en lignes de `max` pixels au plus, entre deux mots. */
    private static List<String> wrap(String text, int max) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        int width = 0;
        for (String word : text.trim().split("\\s+")) {
            int w = 0;
            for (char c : word.toCharArray()) w += charWidth(c);
            if (line.length() > 0 && width + 4 + w > max) { lines.add(line.toString()); line.setLength(0); width = 0; }
            if (line.length() > 0) { line.append(' '); width += 4; }
            line.append(word);
            width += w;
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }

    /**
     * Ce que dit un personnage, en bulle dessinee (tools/build_bulle.py) : sur la premiere ligne, l'image de la bulle qui
     * a la hauteur du texte ; dessous, le texte, centre. L'image est la ligne la plus large : tout se centre sur elle.
     */
    private static Component speech(String text) {
        List<String> lines = wrap(text, 176);
        while (lines.size() > 14) { lines.remove(lines.size() - 1); lines.set(13, lines.get(13) + "…"); }
        return Component.text(String.valueOf((char) (0xE300 + lines.size()))).font(net.kyori.adventure.key.Key.key(com.kiokouwhite.crossfight.util.Sounds.NS, "bulle"))
                .color(net.kyori.adventure.text.format.NamedTextColor.WHITE)
                .append(Component.text("\n" + String.join("\n", lines)).font(net.kyori.adventure.key.Key.key("minecraft", "default"))
                        .color(net.kyori.adventure.text.format.TextColor.fromHexString("#33283c")));
    }

    @EventHandler
    public void onClick(PlayerInteractEntityEvent e) {
        String asked = e.getRightClicked().getPersistentDataContainer().get(guide, PersistentDataType.STRING);
        if (asked != null) {
            // Le personnage de la quete : il dit ce qu'il attend.
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND) {
                plugin.quests().talk(e.getPlayer(), asked, e.getRightClicked().getLocation());
                e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            }
            return;
        }
        String use = e.getRightClicked().getPersistentDataContainer().get(mark, PersistentDataType.STRING);
        if (use == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        // Hors partie, seuls les admins s'en servent (pour essayer).
        if (!plugin.game().isRunning() && !p.hasPermission("crossfight.admin")) { Msg.actionBar(p, "<gray>Les lieux ne servent que pendant la partie."); return; }
        // Le service d'un lieu se merite : la quete du lieu d'abord.
        Structures.Placed here = plugin.structures().at(e.getRightClicked().getLocation());
        if (here != null && !plugin.quests().unlocked(p, here.name())) {
            Msg.actionBar(p, "<red>Verrouillé : termine d'abord la quête <white>" + Quests.QUESTS.get(here.name()).title() + "<red>.");
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 1f);
            return;
        }
        switch (use) {
            case "enchantement" -> enchant(p);
            case "forge" -> repair(p);
            case "potions" -> { if (ready(p, use)) { p.getInventory().addItem(potion()).values().forEach(i -> p.getWorld().dropItem(p.getLocation(), i)); done(p, Sound.BLOCK_BREWING_STAND_BREW, "<green>Une potion pour toi."); } }
            case "nourriture" -> { if (ready(p, use)) { p.getInventory().addItem(new ItemStack(Material.COOKED_BEEF, 12)).values().forEach(i -> p.getWorld().dropItem(p.getLocation(), i)); done(p, Sound.ENTITY_PLAYER_BURP, "<green>12 steaks."); } }
            case "marchand" -> p.openInventory(new Shop().inv);
            default -> { }
        }
    }

    private void done(Player p, Sound sound, String text) {
        p.playSound(p.getLocation(), sound, 1f, 1f);
        Msg.actionBar(p, text);
    }

    /** Vrai si le joueur peut reprendre de ce service (et le note) ; sinon lui dit dans combien de temps. */
    private boolean ready(Player p, String use) {
        long now = System.currentTimeMillis();
        Long last = taken.get(p.getUniqueId() + "/" + use);
        if (last != null && now - last < WAIT) { Msg.actionBar(p, "<red>Encore " + Msg.time((int) ((WAIT - (now - last)) / 1000)) + "."); return false; }
        taken.put(p.getUniqueId() + "/" + use, now);
        return true;
    }

    private ItemStack potion() {
        ItemStack it = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) it.getItemMeta();
        meta.setBasePotionType(POTIONS[ThreadLocalRandom.current().nextInt(POTIONS.length)]);
        it.setItemMeta(meta);
        return it;
    }

    /** Un objet ordinaire du jeu (pas une arme de personnage ni un objet du plugin), tenu en main. */
    private ItemStack plain(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (it.getType().isAir()) { Msg.actionBar(p, "<red>Tiens l'objet en main."); return null; }
        ItemMeta meta = it.getItemMeta();
        if (meta != null && (!meta.getPersistentDataContainer().isEmpty() || meta.isUnbreakable())) { Msg.actionBar(p, "<red>Cet objet ne passe pas ici."); return null; }
        return it;
    }

    private void enchant(Player p) {
        ItemStack it = plain(p);
        if (it == null) return;
        if (!it.getEnchantments().isEmpty() || it.getMaxStackSize() != 1 && it.getType() != Material.BOOK) { Msg.actionBar(p, "<red>Il faut une arme, un outil, une armure ou un livre sans enchantement."); return; }
        if (p.getLevel() < 5) { Msg.actionBar(p, "<red>Il faut 5 niveaux."); return; }
        ItemStack single = it.asOne();
        ItemStack made = Bukkit.getItemFactory().enchantWithLevels(single, 30, false, new java.util.Random());
        if (made.getEnchantments().isEmpty() && made.getType() != Material.ENCHANTED_BOOK) { Msg.actionBar(p, "<red>Cet objet ne s'enchante pas."); return; }
        p.setLevel(p.getLevel() - 5);
        if (it.getAmount() > 1) { it.setAmount(it.getAmount() - 1); p.getInventory().addItem(made).values().forEach(i -> p.getWorld().dropItem(p.getLocation(), i)); }
        else p.getInventory().setItemInMainHand(made);
        done(p, Sound.BLOCK_ENCHANTMENT_TABLE_USE, "<light_purple>Enchante.");
    }

    private void repair(Player p) {
        ItemStack it = plain(p);
        if (it == null) return;
        if (!(it.getItemMeta() instanceof Damageable d) || !d.hasDamage()) { Msg.actionBar(p, "<red>Cet objet n'est pas abime."); return; }
        if (p.getLevel() < 2) { Msg.actionBar(p, "<red>Il faut 2 niveaux."); return; }
        p.setLevel(p.getLevel() - 2);
        d.setDamage(0);
        it.setItemMeta(d);
        done(p, Sound.BLOCK_ANVIL_USE, "<green>Repare.");
    }

    // ------------------------------------------------------------------ les potions d'Anna

    /** Les potions qu'Anna offre : le nom, la potion, et si elle se lance (les mauvaises) ou se boit (les bonnes). */
    private static final Object[][] BREWS = {
            {"Force", PotionType.STRENGTH, false}, {"Vitesse", PotionType.SWIFTNESS, false},
            {"Poison", PotionType.POISON, true}, {"Faiblesse", PotionType.WEAKNESS, true}, {"Lenteur", PotionType.SLOWNESS, true}};
    private static final long BREW_WAIT = 10 * 60 * 1000L;

    private static ItemStack brew(int i) {
        ItemStack it = new ItemStack((boolean) BREWS[i][2] ? Material.SPLASH_POTION : Material.POTION);
        PotionMeta meta = (PotionMeta) it.getItemMeta();
        meta.setBasePotionType((PotionType) BREWS[i][1]);
        it.setItemMeta(meta);
        return it;
    }

    /** La case de chaque potion dans le menu : la colonne 5 du coffre, une rangee par potion (leur nom est ecrit a cote, sur l'image). */
    private static int brewSlot(int i) { return i * 9 + 5; }

    /** La boutique d'Anna : cinq potions, gratuites, une toutes les dix minutes par joueur. */
    private final class Brews implements InventoryHolder {
        final Inventory inv;

        Brews(Player p) {
            inv = Bukkit.createInventory(this, 54, dressed(p, '\uE002', "Anna - une potion offerte"));
            Long last = taken.get(p.getUniqueId() + "/potions");
            long left = last == null ? 0 : BREW_WAIT - (System.currentTimeMillis() - last);
            for (int i = 0; i < BREWS.length; i++) {
                ItemStack it = brew(i);
                ItemMeta meta = it.getItemMeta();
                meta.lore(List.of(Msg.mm(left > 0 ? "<red>Reviens dans " + Msg.time((int) (left / 1000)) : "<green>Gratuit <gray>- une toutes les 10 minutes").decoration(TextDecoration.ITALIC, false),
                        Msg.mm((boolean) BREWS[i][2] ? "<gray>A lancer sur un adversaire" : "<gray>A boire").decoration(TextDecoration.ITALIC, false)));
                it.setItemMeta(meta);
                inv.setItem(brewSlot(i), it);
            }
        }

        @Override public Inventory getInventory() { return inv; }
    }

    /** Ouvre la boutique de potions d'Anna a ce joueur. */
    public void openBrews(Player p) {
        p.openInventory(new Brews(p).inv);
    }

    @EventHandler
    public void onBrew(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Brews shop)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != shop.inv || e.getSlot() % 9 != 5 || e.getSlot() / 9 >= BREWS.length || !(e.getWhoClicked() instanceof Player p)) return;
        long now = System.currentTimeMillis();
        Long last = taken.get(p.getUniqueId() + "/potions");
        if (last != null && now - last < BREW_WAIT) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            Msg.actionBar(p, "<red>Anna n'a plus rien pour toi : reviens dans " + Msg.time((int) ((BREW_WAIT - (now - last)) / 1000)) + ".");
            p.closeInventory();
            return;
        }
        taken.put(p.getUniqueId() + "/potions", now);
        p.getInventory().addItem(brew(e.getSlot() / 9)).values().forEach(i -> p.getWorld().dropItem(p.getLocation(), i));
        p.closeInventory();
        done(p, Sound.BLOCK_BREWING_STAND_BREW, "<green>Potion de " + BREWS[e.getSlot() / 9][0] + " : cadeau d'Anna. La prochaine dans 10 minutes.");
    }

    // ------------------------------------------------------------------ les enchantements d'Anna

    /** Le prix, en niveaux, de chaque niveau d'enchantement : I, II, III. */
    private static final int[] ENCHANT_COST = {0, 1, 2, 5};

    /** Ce qu'Anna met sur cet objet : {enchantement, niveau le plus haut}, ou null s'il ne s'enchante pas chez elle. Diamant et netherite : un niveau de moins. */
    private static Object[] enchantFor(ItemStack it) {
        if (it == null || it.getType().isAir()) return null;
        ItemMeta meta = it.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().isEmpty() || meta.isUnbreakable()) return null;   // armes de personnage, objets du plugin
        String n = it.getType().name();
        boolean rich = n.startsWith("DIAMOND_") || n.startsWith("NETHERITE_");
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")) return new Object[]{org.bukkit.enchantments.Enchantment.PROTECTION, rich ? 2 : 3};
        if (n.endsWith("_SWORD") || n.endsWith("_AXE")) return new Object[]{org.bukkit.enchantments.Enchantment.SHARPNESS, rich ? 2 : 3};
        if (n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL")) return new Object[]{org.bukkit.enchantments.Enchantment.EFFICIENCY, 3};
        if (it.getType() == Material.BOW) return new Object[]{org.bukkit.enchantments.Enchantment.POWER, 2};
        return null;
    }

    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};

    private static String enchantName(org.bukkit.enchantments.Enchantment e) {
        return e == org.bukkit.enchantments.Enchantment.PROTECTION ? "Protection" : e == org.bukkit.enchantments.Enchantment.SHARPNESS ? "Tranchant"
                : e == org.bukkit.enchantments.Enchantment.EFFICIENCY ? "Efficacité" : "Puissance";
    }

    /** Le titre d'un menu d'Anna : avec le pack, l'image qui habille le coffre (tools/build_menu_anna.py) ; sinon, du texte. */
    private static Component dressed(Player p, char image, String plain) {
        boolean pack = com.kiokouwhite.crossfight.util.Sounds.has("font.menu") && com.kiokouwhite.crossfight.util.Sounds.hasPackLoaded(p);
        return pack ? Component.text("" + image).font(net.kyori.adventure.key.Key.key(com.kiokouwhite.crossfight.util.Sounds.NS, "menu")).color(net.kyori.adventure.text.format.NamedTextColor.WHITE)
                : Component.text(plain);
    }

    private static ItemStack named(ItemStack it, String name, String... lore) {
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.mm(name).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String l : lore) lines.add(Msg.mm(l).decoration(TextDecoration.ITALIC, false));
        meta.lore(lines);
        it.setItemMeta(meta);
        return it;
    }

    /** La case du premier menu ou l'objet se pose. */
    private static final int PLACE = 34;

    /**
     * Le premier menu d'Anna au monastere : une seule case, ou poser l'objet a enchanter (un clic sur l'objet, dans
     * son inventaire, l'y pose). Une piece d'armure portee doit d'abord etre retiree.
     */
    private final class Enchants implements InventoryHolder {
        final Inventory inv;

        Enchants(Player p) {
            inv = Bukkit.createInventory(this, 54, dressed(p, '', "Anna - clique l'objet à enchanter"));
        }

        @Override public Inventory getInventory() { return inv; }
    }

    /** Le second menu : l'objet pose, et les niveaux qu'Anna propose pour lui, chacun a son prix. */
    private final class Levels implements InventoryHolder {
        static final int ITEM = 13;
        static final int[] CHOICES = {0, 29, 31, 33};
        final Inventory inv;
        final int slot;                       // la case de l'inventaire du joueur ou se trouve l'objet
        final Material type;

        Levels(Player p, int slot) {
            this.slot = slot;
            ItemStack it = p.getInventory().getItem(slot);
            this.type = it.getType();
            Object[] e = enchantFor(it);
            org.bukkit.enchantments.Enchantment ench = (org.bukkit.enchantments.Enchantment) e[0];
            int max = (int) e[1], has = it.getEnchantmentLevel(ench);
            inv = Bukkit.createInventory(this, 54, dressed(p, '', "Anna - choisis le niveau"));
            inv.setItem(ITEM, named(it.clone(), "<white>" + enchantName(ench), "<gray>Clic : changer d'objet"));
            for (int level = 1; level <= 3; level++) {
                String title = enchantName(ench) + " " + ROMAN[level];
                if (level > max) inv.setItem(CHOICES[level], named(new ItemStack(Material.BARRIER), "<red>" + title, "<gray>Trop fort pour cette pièce."));
                else if (level <= has) inv.setItem(CHOICES[level], named(new ItemStack(Material.BOOK), "<gray>" + title, "<dark_gray>Cette pièce l'a déjà."));
                else inv.setItem(CHOICES[level], named(new ItemStack(Material.ENCHANTED_BOOK), "<light_purple>" + title,
                        (p.getLevel() >= ENCHANT_COST[level] ? "<green>" : "<red>") + "Prix : " + ENCHANT_COST[level] + (ENCHANT_COST[level] > 1 ? " niveaux" : " niveau") + " <gray>(tu en as " + p.getLevel() + ")"));
            }
        }

        @Override public Inventory getInventory() { return inv; }
    }

    /** Ouvre le menu d'enchantement d'Anna a ce joueur. */
    public void openEnchants(Player p) {
        p.openInventory(new Enchants(p).inv);
    }

    @EventHandler
    public void onEnchantDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Enchants || e.getInventory().getHolder() instanceof Levels) e.setCancelled(true);
    }

    @EventHandler
    public void onEnchant(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getInventory().getHolder() instanceof Enchants menu) {
            e.setCancelled(true);
            // L'objet a enchanter : un clic dessus, dans l'inventaire du joueur.
            if (e.getClickedInventory() != p.getInventory()) return;
            int slot = e.getSlot();
            ItemStack it = p.getInventory().getItem(slot);
            if (it == null || it.getType().isAir()) return;
            if (enchantFor(it) == null) { p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); Msg.actionBar(p, "<red>Anna n'enchante pas cet objet."); return; }
            // L'objet apparait dans la case, puis le choix du niveau s'ouvre.
            menu.inv.setItem(PLACE, it.clone());
            p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.8f, 1.2f);
            int chosen = slot;
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline() && p.getOpenInventory().getTopInventory() == menu.inv && enchantFor(p.getInventory().getItem(chosen)) != null) p.openInventory(new Levels(p, chosen).inv); }, 5L);
            return;
        }
        if (!(e.getInventory().getHolder() instanceof Levels menu)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != menu.inv) return;
        if (e.getSlot() == Levels.ITEM) { openEnchants(p); return; }
        int level = e.getSlot() == Levels.CHOICES[1] ? 1 : e.getSlot() == Levels.CHOICES[2] ? 2 : e.getSlot() == Levels.CHOICES[3] ? 3 : 0;
        if (level == 0) return;
        ItemStack it = p.getInventory().getItem(menu.slot);
        Object[] found = enchantFor(it);
        if (found == null || it.getType() != menu.type) { openEnchants(p); return; }          // l'objet n'est plus la
        org.bukkit.enchantments.Enchantment ench = (org.bukkit.enchantments.Enchantment) found[0];
        if (level > (int) found[1] || level <= it.getEnchantmentLevel(ench)) { p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
        int cost = ENCHANT_COST[level];
        if (p.getLevel() < cost) { p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); Msg.actionBar(p, "<red>Il faut " + cost + (cost > 1 ? " niveaux." : " niveau.")); return; }
        p.setLevel(p.getLevel() - cost);
        it.addUnsafeEnchantment(ench, level);
        p.getInventory().setItem(menu.slot, it);
        done(p, Sound.BLOCK_ENCHANTMENT_TABLE_USE, "<light_purple>" + enchantName(ench) + " " + ROMAN[level] + " <gray>(-" + cost + (cost > 1 ? " niveaux)" : " niveau)"));
        openEnchants(p);
    }

    /** Le menu du marchand : un objet par echange, son prix en description. */
    private final class Shop implements InventoryHolder {
        final Inventory inv = Bukkit.createInventory(this, 9, Component.text("Marchand"));

        Shop() {
            for (int i = 0; i < TRADES.length; i++) {
                ItemStack it = new ItemStack((Material) TRADES[i][2], (int) TRADES[i][3]);
                ItemMeta meta = it.getItemMeta();
                meta.lore(List.of(Msg.mm("<gray>Prix : <white>" + TRADES[i][1] + " x " + name((Material) TRADES[i][0])).decoration(TextDecoration.ITALIC, false)));
                it.setItemMeta(meta);
                inv.setItem(i * 2, it);
            }
        }

        @Override public Inventory getInventory() { return inv; }
    }

    private static String name(Material m) {
        return switch (m) { case IRON_INGOT -> "lingots de fer"; case GOLD_INGOT -> "lingots d'or"; case DIAMOND -> "diamants"; case COAL -> "charbons"; default -> m.name().toLowerCase(); };
    }

    @EventHandler
    public void onShop(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Shop shop)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != shop.inv || e.getSlot() % 2 != 0 || e.getSlot() / 2 >= TRADES.length || !(e.getWhoClicked() instanceof Player p)) return;
        Object[] t = TRADES[e.getSlot() / 2];
        ItemStack price = new ItemStack((Material) t[0], (int) t[1]);
        if (!p.getInventory().containsAtLeast(price, price.getAmount())) { p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f); return; }
        p.getInventory().removeItem(price);
        p.getInventory().addItem(new ItemStack((Material) t[2], (int) t[3])).values().forEach(i -> p.getWorld().dropItem(p.getLocation(), i));
        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Shop || e.getInventory().getHolder() instanceof Brews) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ la chasse aux pieces

    /** Une piece cachee : ou elle est, ce qui la montre quand un joueur est pres, et quand elle reparait une fois trouvee. */
    private static final class Piece {
        Location at;
        Entity shown;
        long back;
    }

    private static final Material[] PARTS = {Material.RECOVERY_COMPASS, Material.CLOCK, Material.SPYGLASS, Material.ECHO_SHARD, Material.HEART_OF_THE_SEA};
    private static final long REAPPEAR = 6 * 60 * 1000L;
    private final Map<String, Piece[]> pieces = new HashMap<>();
    /** Combien de pieces chacun a trouvees depuis le debut de la partie. */
    private final Map<UUID, Integer> found = new HashMap<>();

    /** Un endroit ou cacher une piece : une colonne tiree au sort dans le lieu, a l'ecart de son bord, sur son sol. */
    Location hide(World w, Structures.Placed p) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int n = 0; n < 40; n++) {
            int x = p.x() + 12 + r.nextInt(Math.max(1, p.sx() - 24)), z = p.z() + 12 + r.nextInt(Math.max(1, p.sz() - 24));
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;
            Block b = plugin.worldMap().standAt(w, x, z);
            if (b != null && !b.isLiquid() && b.getY() < p.y() + p.sy()) return new Location(w, x + 0.5, b.getY() + 1, z + 0.5);
        }
        return null;
    }

    /**
     * Un endroit ou poser un objet de quete : dans le contour du lieu, sur un sol dur, sous le ciel, a hauteur du sol du
     * lieu (ni dans l'eau, ni dans un sous-sol, ni sur un toit eleve). Charge le chunk s'il le faut.
     */
    Location questSpot(World w, Structures.Placed p) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int level = plugin.structures().level(p);
        // Au coeur du lieu : dans un rayon d'un cinquieme de son cote autour de sa borne, et jamais sur une vitre
        // (le fond vitre d'un bassin n'est pas un endroit ou chercher).
        Location heart = spot(w, p);
        int cx = heart != null ? heart.getBlockX() : p.x() + p.sx() / 2, cz = heart != null ? heart.getBlockZ() : p.z() + p.sz() / 2;
        int reach = Math.max(40, Math.min(p.sx(), p.sz()) / 5);
        for (int n = 0; n < 300; n++) {
            int x = cx - reach + r.nextInt(2 * reach + 1), z = cz - reach + r.nextInt(2 * reach + 1);
            if ((x - cx) * (x - cx) + (z - cz) * (z - cz) > reach * reach || plugin.structures().at(new Location(w, x, 0, z)) == null) continue;
            Block b = plugin.worldMap().standAt(w, x, z);
            if (b == null || b.isLiquid() || !b.getType().isSolid() || b.getType().name().contains("GLASS") || b.getY() < level - 1 || b.getY() > level + 24) continue;
            if (b.getRelative(org.bukkit.block.BlockFace.UP).getLightFromSky() == 0) continue;
            return new Location(w, x + 0.5, b.getY() + 1, z + 0.5);
        }
        return hide(w, p);
    }

    /** Chaque seconde, pour le lieu de la chasse : les pieces se cachent, se montrent a qui s'approche, et se ramassent. */
    private void hunt(World w, Structures.Placed p, boolean running) {
        Piece[] all = pieces.computeIfAbsent(w.getName() + "/" + p.name(), k -> { Piece[] a = new Piece[PARTS.length]; for (int i = 0; i < a.length; i++) a[i] = new Piece(); return a; });
        long now = System.currentTimeMillis();
        List<Player> here = new ArrayList<>();
        for (Player pl : w.getPlayers()) {
            Location at = pl.getLocation();
            if (at.getX() >= p.x() - 48 && at.getX() < p.x() + p.sx() + 48 && at.getZ() >= p.z() - 48 && at.getZ() < p.z() + p.sz() + 48) here.add(pl);
        }
        for (int i = 0; i < all.length; i++) {
            Piece piece = all[i];
            if (here.isEmpty()) { if (piece.shown != null) { piece.shown.remove(); piece.shown = null; } continue; }
            if (piece.at == null && now >= piece.back) piece.at = hide(w, p);
            if (piece.at == null) continue;
            boolean near = false;
            Player taker = null;
            for (Player pl : here) {
                double d = pl.getLocation().distanceSquared(piece.at);
                if (d < 56 * 56) near = true;
                if (d < 12 * 12) pl.spawnParticle(org.bukkit.Particle.END_ROD, piece.at.clone().add(0, 0.6, 0), 2, 0.25, 0.3, 0.25, 0.01);
                if (d < 1.6 * 1.6 && taker == null && pl.getGameMode() != org.bukkit.GameMode.SPECTATOR && (running ? plugin.players().get(pl).isAlive() : pl.hasPermission("crossfight.admin"))) taker = pl;
            }
            if (piece.shown != null && (!near || !piece.shown.isValid())) { piece.shown.remove(); piece.shown = null; }
            if (near && piece.shown == null) {
                final Material part = PARTS[i];
                piece.shown = w.spawn(piece.at.clone().add(0, 0.45, 0), ItemDisplay.class, d -> {
                    d.setPersistent(false);
                    d.setItemStack(new ItemStack(part));
                    d.setBillboard(Display.Billboard.VERTICAL);
                    d.setBrightness(new Display.Brightness(15, 15));
                    d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(0.55f, 0.55f, 0.55f), new Quaternionf()));
                });
            }
            if (taker != null && plugin.quests().unlocked(taker, p.name())) take(taker, piece, all, p);
        }
    }

    private void take(Player pl, Piece piece, Piece[] all, Structures.Placed p) {
        if (piece.shown != null) { piece.shown.remove(); piece.shown = null; }
        pl.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, piece.at.clone().add(0, 0.6, 0), 30, 0.3, 0.4, 0.3, 0.2);
        piece.at = null;
        piece.back = System.currentTimeMillis() + REAPPEAR;
        int left = 0;
        for (Piece other : all) if (other.at != null) left++;
        int count = found.merge(pl.getUniqueId(), 1, Integer::sum);
        pl.getInventory().addItem(new ItemStack(Material.DIAMOND, 2)).values().forEach(i -> pl.getWorld().dropItem(pl.getLocation(), i));
        pl.giveExp(30);
        pl.playSound(pl.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        Msg.broadcast("<green>" + pl.getName() + " <gray>a trouve une piece dans <white>" + plugin.structures().title(p.name()) + "<gray> (" + left + " encore cachee" + (left > 1 ? "s" : "") + ").");
        Msg.send(pl, "<green>Piece trouvee : <white>2 diamants<green> et de l'experience. <gray>(" + count + " trouvee" + (count > 1 ? "s" : "") + " ; a la troisieme, une lootbox)");
        if (count == 3) {
            plugin.lootbox().reward(pl, 2);
            Msg.send(pl, "<gold>Troisieme piece : le contenu d'une lootbox est a toi.");
        }
    }

    /** Ou tombe la lootbox garantie de l'evenement : au milieu du lieu "lootbox", s'il est pose. Sinon null. */
    public Location lootSpot() {
        if (!on()) return null;
        World w = plugin.game().world();
        for (Structures.Placed p : plugin.structures().finished(w)) if ("lootbox".equals(useOf(p.name())) && plugin.quests().anyUnlocked(p.name())) return spot(w, p);
        return null;
    }

    /** Une partie commence : les delais et les comptes de pieces repartent de zero. */
    public void newGame() {
        plugin.quests().reset();
        taken.clear();
        found.clear();
    }

    /** Nouvelle map, nouvelle partie : on oublie les bornes et les delais. */
    public void reset() {
        for (List<Entity> l : shown.values()) l.forEach(Entity::remove);
        shown.clear();
        guides.values().forEach(Entity::remove);
        guides.clear();
        bubbles.values().forEach(l -> l.forEach(Entity::remove));
        bubbles.clear();
        guideSpots.clear();
        zone.clear();
        plugin.quests().reset();
        spots.clear();
        taken.clear();
        for (Piece[] all : pieces.values()) for (Piece piece : all) if (piece.shown != null) piece.shown.remove();
        pieces.clear();
        found.clear();
    }
}
