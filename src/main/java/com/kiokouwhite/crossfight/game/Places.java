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
 *
 * Quel lieu rend quel service : structures.utilites dans config.yml (le nom du dossier du lieu, puis le service).
 */
public final class Places implements Listener {

    /** Par defaut : les lieux connus et leur service. */
    private static final Map<String, String> DEFAULTS = Map.of("garreg_mach", "enchantement", "shiganshina", "forge", "pearl", "potions",
            "central_town", "marchand", "arcadia", "nourriture", "terrace", "experience", "beacon", "lootbox");

    private record Use(String title, Material icon, String hint) {}

    private static final Map<String, Use> USES = Map.of(
            "enchantement", new Use("Enchantement", Material.ENCHANTING_TABLE, "enchante l'objet tenu (5 niveaux)"),
            "forge", new Use("Forge", Material.ANVIL, "repare l'objet tenu (2 niveaux)"),
            "potions", new Use("Potions", Material.BREWING_STAND, "une potion, toutes les 8 minutes"),
            "marchand", new Use("Marchand", Material.EMERALD, "echanges"),
            "nourriture", new Use("Nourriture", Material.COOKED_BEEF, "12 steaks, toutes les 8 minutes"),
            "experience", new Use("Experience", Material.EXPERIENCE_BOTTLE, "de l'experience tant qu'on y reste"),
            "lootbox", new Use("Lootbox", Material.ENDER_CHEST, "une lootbox garantie a l'evenement"));

    /** Les echanges du marchand : {ce qu'on donne, combien, ce qu'on recoit, combien}. */
    private static final Object[][] TRADES = {
            {Material.IRON_INGOT, 8, Material.ARROW, 16}, {Material.GOLD_INGOT, 6, Material.GOLDEN_APPLE, 1}, {Material.DIAMOND, 2, Material.EXPERIENCE_BOTTLE, 8},
            {Material.DIAMOND, 4, Material.ENDER_PEARL, 1}, {Material.COAL, 16, Material.COOKED_BEEF, 6}};
    private static final PotionType[] POTIONS = {PotionType.SWIFTNESS, PotionType.SWIFTNESS, PotionType.FIRE_RESISTANCE, PotionType.FIRE_RESISTANCE, PotionType.LEAPING,
            PotionType.SLOW_FALLING, PotionType.NIGHT_VISION, PotionType.WATER_BREATHING, PotionType.STRENGTH};
    private static final long WAIT = 8 * 60 * 1000L;

    private final CrossfightPlugin plugin;
    private final NamespacedKey mark;
    /** Les bornes : par lieu ("monde/nom"), ou elle est, et les entites qui la montrent tant qu'un joueur est pres. */
    private final Map<String, Location> spots = new HashMap<>();
    private final Map<String, List<Entity>> shown = new HashMap<>();
    /** La derniere fois que chacun a pris une potion, de la nourriture ("joueur/service"). */
    private final Map<String, Long> taken = new HashMap<>();

    public Places(CrossfightPlugin plugin) {
        this.plugin = plugin;
        this.mark = new NamespacedKey(plugin, "borne");
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
        if (!on()) { for (List<Entity> l : shown.values()) l.forEach(Entity::remove); shown.clear(); return; }
        World w = plugin.game().world();
        boolean running = plugin.game().isRunning();
        for (Structures.Placed p : plugin.structures().finished(w)) {
            String use = useOf(p.name());
            if (use == null) continue;
            String key = w.getName() + "/" + p.name();
            boolean station = !use.equals("experience") && !use.equals("lootbox");
            boolean near = false;
            for (Player pl : w.getPlayers()) {
                Location at = pl.getLocation();
                boolean inside = at.getX() >= p.x() && at.getX() < p.x() + p.sx() && at.getZ() >= p.z() && at.getZ() < p.z() + p.sz();
                if (inside && running && use.equals("experience") && plugin.players().get(pl).isAlive() && pl.getGameMode() == org.bukkit.GameMode.SURVIVAL) pl.giveExp(2);
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
        String use = e.getRightClicked().getPersistentDataContainer().get(mark, PersistentDataType.STRING);
        if (use == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        // Hors partie, seuls les admins s'en servent (pour essayer).
        if (!plugin.game().isRunning() && !p.hasPermission("crossfight.admin")) { Msg.actionBar(p, "<gray>Les lieux ne servent que pendant la partie."); return; }
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

    /** Ou tombe la lootbox garantie de l'evenement : au milieu du lieu "lootbox", s'il est pose. Sinon null. */
    public Location lootSpot() {
        if (!on()) return null;
        World w = plugin.game().world();
        for (Structures.Placed p : plugin.structures().finished(w)) if ("lootbox".equals(useOf(p.name()))) return spot(w, p);
        return null;
    }

    /** Nouvelle map, nouvelle partie : on oublie les bornes et les delais. */
    public void reset() {
        for (List<Entity> l : shown.values()) l.forEach(Entity::remove);
        shown.clear();
        spots.clear();
        taken.clear();
    }
}
