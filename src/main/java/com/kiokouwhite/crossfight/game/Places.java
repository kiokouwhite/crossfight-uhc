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
 *   enchantement  l'objet tenu recoit un enchantement de niveau 30, contre 5 niveaux
 *   forge         l'objet tenu est repare, contre 2 niveaux
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
    private static final Map<String, String> DEFAULTS = Map.of("garreg_mach", "enchantement", "shiganshina", "forge", "pearl", "potions",
            "central_town", "marchand", "arcadia", "nourriture", "terrace", "experience", "beacon", "lootbox", "forest_of_hope", "chasse");

    private record Use(String title, Material icon, String hint) {}

    private static final Map<String, Use> USES = Map.ofEntries(Map.entry("chasse", new Use("Chasse aux pieces", Material.RECOVERY_COMPASS, "cinq pieces cachees a trouver")),
            Map.entry("enchantement", new Use("Enchantement", Material.ENCHANTING_TABLE, "enchante l'objet tenu (5 niveaux)")),
            Map.entry("forge", new Use("Forge", Material.ANVIL, "repare l'objet tenu (2 niveaux)")),
            Map.entry("potions", new Use("Potions", Material.BREWING_STAND, "une potion, toutes les 8 minutes")),
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

    private final CrossfightPlugin plugin;
    private final NamespacedKey mark, guide;
    /** Le lieu ou se trouve chaque joueur (son dossier), pour annoncer son nom quand il y entre. */
    private final Map<UUID, String> zone = new HashMap<>();
    /** Le personnage de quete de chaque lieu, tant que quelqu'un est pres. */
    private final Map<String, Entity> guides = new HashMap<>();
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
    }

    /** Le service d'un lieu (son nom de dossier), ou null. */
    public String useOf(String place) {
        String use = plugin.getConfig().getString("structures.utilites." + place, DEFAULTS.get(place));
        return use != null && USES.containsKey(use) ? use : null;
    }

    /** Pour la carte : "Enchantement", ou null. */
    public String label(String place) {
        String use = useOf(place);
        return use == null ? null : USES.get(use).title();
    }

    /** Ou se tient le personnage de quete d'un lieu (ou sa borne s'il n'est pas apparu), pour s'y teleporter. */
    public Location guideOf(World w, String place) {
        Entity g = guides.get(w.getName() + "/" + place);
        if (g != null && g.isValid()) return g.getLocation();
        for (Structures.Placed p : plugin.structures().finished(w)) if (p.name().equals(place)) return spot(w, p);
        return null;
    }

    /** La quete du lieu ou se trouve ce joueur (son nom), ou null. Pour le tableau de bord. */
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
            pl.showTitle(net.kyori.adventure.title.Title.title(Msg.mm("<gold><bold>" + plugin.structures().title(now)), Msg.mm(q == null ? "" : "<gray>Quete : <white>" + q.title()),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(400), java.time.Duration.ofMillis(2500), java.time.Duration.ofMillis(800))));
            pl.playSound(pl.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.7f);
        }
        for (Structures.Placed p : plugin.structures().finished(w)) {
            Quests.Quest q = Quests.QUESTS.get(p.name());
            if (q == null) continue;
            String key = w.getName() + "/" + p.name();
            Location spot = spot(w, p);
            boolean near = false;
            if (spot != null) for (Player pl : w.getPlayers()) if (pl.getLocation().distanceSquared(spot) < 72 * 72) { near = true; break; }
            Entity have = guides.get(key);
            if (have != null && (!near || !have.isValid())) { have.remove(); guides.remove(key); have = null; }
            if (!near || have != null) continue;
            // A trois blocs de la borne, sur le sol.
            Block b = plugin.worldMap().standAt(w, spot.getBlockX() + 3, spot.getBlockZ());
            Location at = b == null || Math.abs(b.getY() + 1 - spot.getBlockY()) > 3 ? spot.clone().add(1.5, 0, 0) : new Location(w, spot.getBlockX() + 3.5, b.getY() + 1, spot.getBlockZ() + 0.5);
            at.setYaw(90f);
            guides.put(key, w.spawn(at, org.bukkit.entity.Villager.class, v -> {
                v.setPersistent(false);
                v.setAI(false);
                v.setInvulnerable(true);
                v.setSilent(true);
                v.setGlowing(true);
                v.setCollidable(false);
                v.setRemoveWhenFarAway(false);
                v.customName(Msg.mm("<yellow><bold>!</bold> <white>" + q.giver() + " <gray>- " + q.title()));
                v.setCustomNameVisible(true);
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
            int x = p.x() + p.sx() / 2, z = p.z() + p.sz() / 2;
            Block b = plugin.worldMap().standAt(w, x, z);
            return b == null ? null : new Location(w, x + 0.5, b.getY() + 1, z + 0.5);
        });
    }

    /** Chaque seconde : les bornes apparaissent pres des joueurs, l'experience tombe sur ceux qui sont dans son lieu. */
    private void tick() {
        World w = plugin.game().world();
        if (plugin.getConfig().getBoolean("structures.quetes", true)) zones(w);
        else { guides.values().forEach(Entity::remove); guides.clear(); zone.clear(); }
        if (!on()) { for (List<Entity> l : shown.values()) l.forEach(Entity::remove); shown.clear(); return; }
        boolean running = plugin.game().isRunning();
        for (Structures.Placed p : plugin.structures().finished(w)) {
            String use = useOf(p.name());
            if (use == null) continue;
            String key = w.getName() + "/" + p.name();
            boolean station = !use.equals("experience") && !use.equals("lootbox") && !use.equals("chasse");
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

    @EventHandler
    public void onClick(PlayerInteractEntityEvent e) {
        String asked = e.getRightClicked().getPersistentDataContainer().get(guide, PersistentDataType.STRING);
        if (asked != null) {
            // Le personnage de la quete : il dit ce qu'il attend.
            e.setCancelled(true);
            if (e.getHand() == EquipmentSlot.HAND) {
                plugin.quests().talk(e.getPlayer(), asked, e.getRightClicked().getLocation());
                e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_AMBIENT, 1f, 1f);
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
            Msg.actionBar(p, "<red>Verrouille : termine d'abord la quete <white>" + Quests.QUESTS.get(here.name()).title() + "<red>.");
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
        if (e.getInventory().getHolder() instanceof Shop) e.setCancelled(true);
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
        zone.clear();
        plugin.quests().reset();
        spots.clear();
        taken.clear();
        for (Piece[] all : pieces.values()) for (Piece piece : all) if (piece.shown != null) piece.shown.remove();
        pieces.clear();
        found.clear();
    }
}
