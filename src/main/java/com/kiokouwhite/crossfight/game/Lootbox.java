package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Fx;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.TempBlocks;
import com.kiokouwhite.crossfight.util.Weapons;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * L'evenement Lootbox : a sa minute (20 par defaut), des coffres sous un petit parachute apparaissent au hasard
 * sur la map (le parachute a la couleur du tier, le coffre en bois, en fer ou en or selon le pack). Chacun a un
 * tier tire au sort (45 %, 35 %, 19 %, 1 %) et contient 2 a 8 diamants, 6 a 12 lingots d'or et un objet de son
 * tier : tomes, armes, lances et batons a nombre d'utilisations compte ("durabilite"), certains avec un temps de
 * recharge. Les objets, leurs effets et leurs chiffres sont ceux du document de conception.
 */
public final class Lootbox implements Listener {

    /** Comment un objet s'utilise. */
    enum Kind { SPELL, MELEE, BOW, SPEAR }

    /**
     * Un objet de lootbox : son tier, son nom, son icone, son nombre d'utilisations, son temps de recharge en
     * secondes (0 : aucun), ses degats (armes) et sa description.
     */
    record Loot(String id, int tier, Kind kind, String name, Material icon, int uses, int cooldown, double damage, String help) {}

    private static final Map<String, Loot> LOOTS = new LinkedHashMap<>();
    /** Chance de chaque tier, en pour cent. */
    private static final int[] TIER_CHANCE = {45, 35, 19, 1};
    private static final String[] TIER_COLOR = {"white", "aqua", "light_purple", "gold"};
    private static final Color[] TIER_BEAM = {Color.fromRGB(235, 235, 235), Color.fromRGB(90, 220, 255), Color.fromRGB(220, 110, 255), Color.fromRGB(255, 190, 40)};

    private static void add(String id, int tier, Kind kind, String name, Material icon, int uses, int cooldown, double damage, String help) {
        LOOTS.put(id, new Loot(id, tier, kind, name, icon, uses, cooldown, damage, help));
    }

    static {
        // Tier 1
        add("feu", 1, Kind.SPELL, "Feu", Material.BLAZE_POWDER, 5, 2, 0, "Allume une flamme la ou tu vises, comme un briquet (3 blocs).");
        add("foudre", 1, Kind.SPELL, "Foudre", Material.BOOK, 2, 10, 0, "Un eclair de 2 coeurs sur l'adversaire le plus proche (10 blocs).");
        add("vent", 1, Kind.SPELL, "Vent", Material.FEATHER, 3, 5, 0, "Repousse de 5 blocs tous les adversaires a 10 blocs.");
        add("flux", 1, Kind.SPELL, "Flux", Material.BOOK, 2, 10, 0, "Absorbe 2 coeurs de l'adversaire le plus proche (10 blocs).");
        add("arc_bronze", 1, Kind.BOW, "Arc en bronze", Material.BOW, 10, 0, 1, "Un arc tout ce qu'il y a de plus classique (Puissance I).");
        add("epee_verre", 1, Kind.MELEE, "Epee de Verre", Material.IRON_SWORD, 1, 0, 14, "Une epee extremement puissante, qui se brise au premier coup.");
        add("javelot", 1, Kind.SPEAR, "Javelot", Material.TRIDENT, 1, 0, 6, "Clic droit : il se jette en avant, 3 coeurs s'il touche.");
        add("hache_bronze", 1, Kind.MELEE, "Hache en bronze", Material.GOLDEN_AXE, 5, 0, 10, "Allonge de 2 blocs.");
        add("sauvetage", 1, Kind.SPELL, "Sauvetage", Material.STICK, 2, 5, 0, "Teleporte un allie sur toi (celui que tu vises, sinon le plus proche).");
        // Tier 2
        add("bolganone", 2, Kind.SPELL, "Bolganone", Material.FIRE_CHARGE, 2, 10, 0, "Une boule de feu sur 10 blocs : elle enflamme son passage et explose au contact (3 coeurs).");
        add("thoron", 2, Kind.SPELL, "Thoron", Material.ENCHANTED_BOOK, 2, 10, 0, "Un eclair de 3 coeurs sur tous les ennemis a 20 blocs.");
        add("forseti", 2, Kind.SPELL, "Forseti", Material.PHANTOM_MEMBRANE, 3, 5, 0, "Ejecte tous les adversaires a 10 blocs.");
        add("ruine", 2, Kind.SPELL, "Ruine", Material.ENCHANTED_BOOK, 5, 10, 0, "Vole 2 coeurs a tous les adversaires a 10 blocs.");
        add("parthia", 2, Kind.BOW, "Parthia", Material.BOW, 10, 0, 2, "Puissance II : la fleche part droit, exactement la ou tu vises.");
        add("epee_heros", 2, Kind.MELEE, "Epee Heros", Material.GOLDEN_SWORD, 6, 0, 8, "Une epee sans delai entre deux coups.");
        add("gae_bolg", 2, Kind.SPEAR, "Gae Bolg", Material.TRIDENT, 3, 10, 6, "Clic droit : la lance part, 3 coeurs a l'impact. Si elle ne touche rien, elle te revient.");
        add("marteau", 2, Kind.MELEE, "Marteau", Material.IRON_AXE, 6, 0, 12, "Allonge de 2 blocs.");
        add("soin", 2, Kind.SPELL, "Soin", Material.BLAZE_ROD, 5, 10, 0, "Rend 5 coeurs a un allie (celui que tu vises, sinon le plus proche).");
        // Tier 3
        add("valflame", 3, Kind.SPELL, "Valflame", Material.MAGMA_CREAM, 3, 10, 0, "Un lac de lave de 3x3 sous l'adversaire le plus proche (10 blocs).");
        add("mjolnir", 3, Kind.SPELL, "Mjolnir", Material.ENCHANTED_BOOK, 2, 10, 0, "Un eclair et une explosion sur tous les adversaires a 50 blocs : 3 coeurs.");
        add("excalibur", 3, Kind.SPELL, "Excalibur", Material.ENCHANTED_BOOK, 3, 10, 0, "L'adversaire le plus proche (10 blocs) est pris dans une tornade, ejecte a 10 blocs de haut.");
        add("goethe", 3, Kind.SPELL, "Goethe", Material.ENCHANTED_BOOK, 3, 30, 0, "Une zone de 5x5 la ou tu vises : pendant 10 secondes, vole un coeur par seconde a qui s'y trouve.");
        add("stellaire", 3, Kind.BOW, "Stellaire", Material.BOW, 5, 0, 3, "Puissance III : clic droit tire tout de suite, sans bander l'arc.");
        add("sol_katti", 3, Kind.MELEE, "Sol Katti", Material.DIAMOND_SWORD, 10, 0, 9, "Te rend la moitie des degats que tu infliges.");
        add("gravidus", 3, Kind.SPEAR, "Gravidus", Material.TRIDENT, 3, 0, 6, "Clic droit : la lance part, 3 coeurs a l'impact et te rend 3 coeurs. Si elle ne touche rien, elle te revient.");
        add("vengeance", 3, Kind.MELEE, "Vengeance", Material.NETHERITE_AXE, 5, 0, 10, "Allonge de 2 blocs. 10 degats ; 12 si un allie est mort ; 14 si deux le sont.");
        add("baton_deesse", 3, Kind.SPELL, "Baton de la deesse", Material.END_ROD, 1, 0, 0, "Soigne tous les allies de 10 coeurs et leur donne Regeneration 10 secondes.");
        // Tier 4
        add("expiration", 4, Kind.SPELL, "Expiration", Material.DEAD_BUSH, 1, 0, 0, "Un labyrinthe d'epines de 20x20 : les ennemis y perdent 1 coeur toutes les 2 secondes ; tu les vois et tu as Force.");
        add("livre_naga", 4, Kind.SPELL, "Livre de Naga", Material.ENCHANTED_BOOK, 1, 0, 0, "Tu retrouves toute ta vie ; les ennemis a 30 blocs perdent pour de bon la vie que tu avais.");
    }

    private static final NamespacedKey REACH_KEY = new NamespacedKey("crossfight", "loot_reach");
    private static final NamespacedKey FAST_KEY = new NamespacedKey("crossfight", "loot_speed");
    /** Combien de temps une lootbox reste sur la map, et combien dure le labyrinthe d'Expiration (ticks). */
    private static final int BOX_LIFE = 20 * 60 * 10, MAZE_LIFE = 20 * 30;

    private final CrossfightPlugin plugin;
    private final NamespacedKey itemKey, usesKey;
    /**
     * Une lootbox posee : son bloc (une barriere invisible, pour qu'on ne la traverse pas), son tier, son coffre en 3D
     * et, un bloc plus haut, son parachute a la couleur du tier (deux affichages d'objet), la zone qu'on clique pour
     * l'ouvrir (une interaction) et son contenu.
     */
    private static final class Box implements InventoryHolder {
        final Block block;
        final int tier;
        final Inventory inv;
        ItemDisplay look, chute;
        Interaction hit;
        /**
         * FERMEE jusqu'a la premiere ouverture, ROULETTE pendant la roulette (et la seconde ou l'on voit la ligne
         * gagnante), OUVERTE si le butin n'a pas pu partir dans l'inventaire de celui qui l'a ouverte (chacun s'y sert).
         */
        int etat = FERMEE;
        /** Le contenu (case, objet), devoile a la fin de la roulette. */
        final Map<Integer, ItemStack> prix = new LinkedHashMap<>();
        /** Le tier dont la fenetre a la couleur (pendant la roulette, elle passe par les quatre). */
        int couleur;
        /** Les blocs de son faisceau (le socle de fer, la balise, le verre teinte), vides si elle n'en a pas. */
        final List<Block> faisceau = new ArrayList<>();
        /** Celui qui l'a ouverte le premier : le butin va dans son inventaire a la fin de la roulette. */
        UUID ouvreur;

        Box(Block block, int tier) {
            this.block = block;
            this.tier = tier;
            this.couleur = tier;
            this.inv = Bukkit.createInventory(this, 27, Msg.mm("<" + TIER_COLOR[tier - 1] + ">Lootbox tier " + tier));
        }

        @Override public Inventory getInventory() { return inv; }
    }

    private static final int FERMEE = 0, ROULETTE = 1, OUVERTE = 2;
    /**
     * Les trois roulettes, verticales, comme une machine a sous : les diamants, l'objet et l'or, dans les colonnes 3, 5
     * et 7 du coffre. Chacune montre trois cases (une par rangee) ; ce qui s'arrete dans celle du milieu (cases 11, 13
     * et 15, la ligne gagnante) est gagne.
     */
    private static final int DIAMANTS = 11, CENTRE = 13, OR = 15;
    /**
     * Les crans de chaque roulette (diamants, objet, or) : celle des diamants s'arrete la premiere, puis celle de l'or,
     * celle de l'objet en dernier (4 s). L'attente entre deux crans monte de 0 a ATTENTE ticks.
     */
    private static final int CRANS_DIAMANTS = 14, CRANS_OR = 20, CRANS_OBJET = 28, ATTENTE = 7;
    /** La fenetre change de couleur au plus toutes les 6 ticks (pas plus de trois a quatre eclats par seconde). */
    private static final int ECLAT = 6;
    /** Le temps de voir la ligne gagnante (en ticks) avant que le butin ne parte dans l'inventaire. */
    private static final int RAMASSAGE = 20;
    /** La fenetre de chaque tier dans le pack (tools/build_menu_lootbox.py) : un espace qui recule, puis son image. */
    private static final char RECUL = '\uE2AF', FENETRE = '\uE2A0';
    /** Les couleurs des tiers en codes de texte, pour le titre sans le pack. */
    private static final char[] LEGACY = {'f', 'b', 'd', '6'};

    /** Le verre teinte au-dessus de la balise de chaque tier : il donne au faisceau la couleur du tier. */
    private static final Material[] VERRES = {Material.WHITE_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS, Material.PURPLE_STAINED_GLASS, Material.YELLOW_STAINED_GLASS};
    /** Les blocs des faisceaux : ce qu'il y avait avant, et combien de lootbox s'en servent (deux socles voisins se touchent). */
    private final Map<Block, org.bukkit.block.data.BlockData> avant = new HashMap<>();
    private final Map<Block, Integer> usages = new HashMap<>();

    /**
     * L'Embleme de Feu : quand Marth ouvre une Lootbox le premier, il a "embleme-de-feu" % de chances (10 par defaut) d'y
     * trouver en plus cet objet, pose au-dessus de l'objet gagne (case 4) a la fin des roulettes ; s'il n'a ni la
     * Sainte-Falchion ni deja un Embleme. Utilise par Marth, il change sa Falchion en Sainte-Falchion.
     */
    private static final String EMBLEME = "embleme";
    private static final int CASE_EMBLEME = 4;

    /** Les lootbox posees, par bloc. */
    private final Map<Block, Box> boxes = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

    public Lootbox(CrossfightPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "loot");
        this.usesKey = new NamespacedKey(plugin, "loot_uses");
        // L'Embleme de Feu arrive dans l'inventaire de Marth (ramasse, donne, sorti d'un coffre) : l'animation se lance.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> { for (Player p : Bukkit.getOnlinePlayers()) eveiller(p); }, 40L, 10L);
    }

    public static List<String> ids() { return new ArrayList<>(LOOTS.keySet()); }

    /** Vrai pour un objet de lootbox. */
    public static boolean isLoot(ItemStack it) {
        CrossfightPlugin plugin = CrossfightPlugin.get();
        return plugin != null && plugin.lootbox() != null && plugin.lootbox().lootOf(it) != null;
    }

    Loot lootOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
        return id == null ? null : LOOTS.get(id);
    }

    // ------------------------------------------------------------------ les objets

    /** L'objet tout neuf, par son nom. */
    public ItemStack item(String id) {
        Loot l = LOOTS.get(id.toLowerCase(java.util.Locale.ROOT));
        if (l == null) return null;
        String name = "<" + TIER_COLOR[l.tier() - 1] + "><bold>" + l.name();
        ItemStack it;
        switch (l.kind()) {
            case MELEE -> {
                it = Weapons.melee(l.icon(), l.damage(), name, null);
                ItemMeta meta = it.getItemMeta();
                // Haches et marteau : une allonge de 2 blocs (3 d'ordinaire). L'Epee Heros : aucun delai entre deux coups.
                if (l.icon().name().endsWith("_AXE")) meta.addAttributeModifier(Attribute.ENTITY_INTERACTION_RANGE,
                        new AttributeModifier(REACH_KEY, -1.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                if (l.id().equals("epee_heros")) {
                    meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
                    meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(FAST_KEY, 100.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
                }
                it.setItemMeta(meta);
            }
            case BOW -> it = Weapons.bow((int) l.damage(), true, name, null);
            case SPEAR -> {
                it = Weapons.melee(l.icon(), 4, name, null);
            }
            default -> it = Items.of(l.icon(), name, null);
        }
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, l.id());
        meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, l.uses());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
        meta.setMaxStackSize(1);
        it.setItemMeta(meta);
        describe(it, l, l.uses());
        return it;
    }

    private void describe(ItemStack it, Loot l, int uses) {
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Msg.mm("<!italic><dark_gray>Lootbox tier " + l.tier()));
        for (String line : Items.wrap(l.help())) lore.add(Msg.mm("<!italic><gray>" + line));
        if (l.kind() == Kind.MELEE) lore.add(Msg.mm("<!italic><gray>" + (int) l.damage() + " degats."));
        lore.add(Msg.mm("<!italic>"));
        lore.add(Msg.mm("<!italic><yellow>Utilisations : <white>" + uses + "<gray>/" + l.uses()));
        if (l.cooldown() > 0) lore.add(Msg.mm("<!italic><yellow>Recharge : <white>" + l.cooldown() + " s"));
        ItemMeta meta = it.getItemMeta();
        meta.lore(lore);
        it.setItemMeta(meta);
    }

    /** Une utilisation de moins sur l'objet en main ; a zero, il se brise. */
    private void spend(Player p, Loot l) {
        ItemStack it = p.getInventory().getItemInMainHand();
        if (lootOf(it) != l) return;
        int left = it.getItemMeta().getPersistentDataContainer().getOrDefault(usesKey, PersistentDataType.INTEGER, 1) - 1;
        if (left <= 0) {
            p.getInventory().setItemInMainHand(null);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            Msg.actionBar(p, "<gray>" + l.name() + " est use.");
            return;
        }
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, left);
        it.setItemMeta(meta);
        describe(it, l, left);
        p.getInventory().setItemInMainHand(it);
    }

    /** Secondes de recharge restantes pour cet objet (0 : pret). */
    private int waiting(Player p, Loot l) {
        long until = cooldowns.getOrDefault(p.getUniqueId(), Map.of()).getOrDefault(l.id(), 0L);
        return (int) Math.max(0, Math.ceil((until - System.currentTimeMillis()) / 1000.0));
    }

    private void startCooldown(Player p, Loot l) {
        // Le mode test n'a pas de temps de recharge.
        if (l.cooldown() <= 0 || plugin.game().state() == GameState.TEST) return;
        cooldowns.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(l.id(), System.currentTimeMillis() + l.cooldown() * 1000L);
        p.setCooldown(p.getInventory().getItemInMainHand(), l.cooldown() * 20);
    }

    // ------------------------------------------------------------------ utilisation

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK)) return;
        Loot l = lootOf(e.getItem());
        if (l == null || l.kind() == Kind.MELEE) return;
        Player p = e.getPlayer();
        // Les arcs se bandent comme des arcs, sauf Stellaire qui tire au clic.
        if (l.kind() == Kind.BOW && !l.id().equals("stellaire")) return;
        e.setCancelled(true);
        if (!plugin.game().isRunning()) { Msg.actionBar(p, "<red>Seulement pendant la partie."); return; }
        int wait = waiting(p, l);
        if (wait > 0) { Msg.actionBar(p, "<red>" + l.name() + " : encore " + wait + " s."); return; }
        if (!cast(p, l)) return;
        startCooldown(p, l);
        // Une lance lancee quitte la main : c'est son retour (ou non) qui compte l'utilisation.
        if (l.kind() != Kind.SPEAR) spend(p, l);
    }

    /** Lance l'objet. Rend false s'il n'a rien pu faire (pas de cible) : rien n'est alors depense. */
    private boolean cast(Player p, Loot l) {
        World w = p.getWorld();
        Location at = p.getLocation();
        switch (l.id()) {
            case "feu" -> {
                var hit = p.rayTraceBlocks(3);
                if (hit == null || hit.getHitBlock() == null || hit.getHitBlockFace() == null) return none(p, "Vise un bloc a moins de 3 blocs.");
                Block air = hit.getHitBlock().getRelative(hit.getHitBlockFace());
                if (!air.getType().isAir()) return none(p, "Pas de place pour une flamme ici.");
                air.setType(Material.FIRE);
                w.playSound(air.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 1f, 1f);
            }
            case "foudre" -> {
                LivingEntity t = Effects.nearestEnemy(p, 10);
                if (t == null) return none(p, "Aucun adversaire a 10 blocs.");
                Effects.lightning(t, 4, p);
            }
            case "thoron", "mjolnir" -> {
                boolean big = l.id().equals("mjolnir");
                List<LivingEntity> targets = Effects.enemiesWithin(p, big ? 50 : 20);
                if (targets.isEmpty()) return none(p, "Aucun adversaire a " + (big ? 50 : 20) + " blocs.");
                for (LivingEntity t : targets) {
                    Effects.lightning(t, 6, p);
                    if (big) {
                        w.spawnParticle(Particle.EXPLOSION_EMITTER, t.getLocation().add(0, 1, 0), 1);
                        w.playSound(t.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.9f);
                    }
                }
            }
            case "vent", "forseti" -> {
                boolean strong = l.id().equals("forseti");
                List<LivingEntity> targets = Effects.enemiesWithin(p, 10);
                if (targets.isEmpty()) return none(p, "Aucun adversaire a 10 blocs.");
                for (LivingEntity t : targets) {
                    Effects.knockbackFrom(t, at, strong ? 10 : 5);
                    if (strong) t.setVelocity(t.getVelocity().setY(0.9));
                    w.spawnParticle(Particle.CLOUD, t.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.1);
                }
                Fx.burst(at.clone().add(0, 0.2, 0), new Vector(0, 1, 0), "vent", Color.WHITE, 1f, strong ? 20f : 14f, 8, 90);
                w.playSound(at, Sound.ENTITY_BREEZE_WIND_BURST, 1f, strong ? 0.7f : 1.1f);
            }
            case "flux", "ruine" -> {
                List<LivingEntity> targets = new ArrayList<>();
                if (l.id().equals("ruine")) targets.addAll(Effects.enemiesWithin(p, 10));
                else { LivingEntity t = Effects.nearestEnemy(p, 10); if (t != null) targets.add(t); }
                if (targets.isEmpty()) return none(p, "Aucun adversaire a 10 blocs.");
                for (LivingEntity t : targets) {
                    Effects.damage(t, 4, p);
                    Effects.heal(p, 4);
                    Fx.line(t.getLocation().add(0, 1.2, 0), p.getLocation().add(0, 1.2, 0), 0.3, new Particle.DustOptions(Color.fromRGB(120, 20, 160), 1.1f));
                }
                w.playSound(at, Sound.ENTITY_WITHER_SHOOT, 0.6f, 1.6f);
            }
            case "sauvetage", "soin" -> {
                LivingEntity ally = pickAlly(p, l.id().equals("soin") ? 30 : 1000);
                if (ally == null) return none(p, "Aucun allie.");
                if (l.id().equals("soin")) {
                    Effects.heal(ally, 10);
                    w.spawnParticle(Particle.HEART, ally.getLocation().add(0, 2, 0), 6, 0.4, 0.3, 0.4);
                    w.playSound(ally.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
                } else {
                    w.spawnParticle(Particle.PORTAL, ally.getLocation().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.2);
                    ally.teleport(at);
                    w.spawnParticle(Particle.END_ROD, at.clone().add(0, 1, 0), 25, 0.3, 0.6, 0.3, 0.05);
                    w.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.3f);
                }
                if (ally instanceof Player ap) Msg.actionBar(ap, "<green>" + p.getName() + " : " + l.name());
            }
            case "bolganone" -> fireball(p);
            case "valflame" -> {
                LivingEntity t = Effects.nearestEnemy(p, 10);
                if (t == null) return none(p, "Aucun adversaire a 10 blocs.");
                TempBlocks lake = new TempBlocks();
                Block centre = t.getLocation().getBlock().getRelative(BlockFace.DOWN);
                for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) lake.replace(centre.getRelative(x, 0, z), Material.LAVA);
                lake.restoreAfter(20 * 20);
                w.playSound(t.getLocation(), Sound.ITEM_BUCKET_EMPTY_LAVA, 1f, 0.8f);
            }
            case "excalibur" -> {
                LivingEntity t = Effects.nearestEnemy(p, 10);
                if (t == null) return none(p, "Aucun adversaire a 10 blocs.");
                Effects.launchUp(t, 10);
                tornado(t);
            }
            case "goethe" -> {
                var hit = p.rayTraceBlocks(20);
                if (hit == null || hit.getHitBlock() == null) return none(p, "Vise le sol a moins de 20 blocs.");
                drainZone(p, hit.getHitBlock().getLocation().add(0.5, 1, 0.5));
            }
            case "stellaire" -> {
                Vector dir = p.getEyeLocation().getDirection().normalize();
                Arrow arrow = Effects.shootInstantArrow(p, dir, 3.0, 2.0 + 0.5 * 4);
                arrow.setGravity(false);
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (arrow.isValid() && !arrow.isInBlock()) arrow.remove(); }, 40L);
            }
            case "baton_deesse" -> {
                List<LivingEntity> allies = Effects.alliesWithin(p, 1000);
                if (!allies.contains(p)) allies.add(p);
                for (LivingEntity a : allies) {
                    Effects.heal(a, 20);
                    Effects.give(a, PotionEffectType.REGENERATION, 1, 200);
                    w.spawnParticle(Particle.HEART, a.getLocation().add(0, 2, 0), 8, 0.4, 0.3, 0.4);
                    w.spawnParticle(Particle.END_ROD, a.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.03);
                }
                w.playSound(at, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
            }
            case "expiration" -> maze(p);
            case "livre_naga" -> {
                double had = p.getHealth();
                AttributeInstance mine = p.getAttribute(Attribute.MAX_HEALTH);
                p.setHealth(mine == null ? 20 : mine.getValue());
                for (LivingEntity t : Effects.enemiesWithin(p, 30)) {
                    AttributeInstance max = t.getAttribute(Attribute.MAX_HEALTH);
                    if (max == null) continue;
                    // Ils perdent pour de bon la vie qu'avait le lanceur ; il leur reste au moins un coeur.
                    double lost = Math.min(had, max.getValue() - 2);
                    if (lost <= 0) continue;
                    max.addModifier(new AttributeModifier(new NamespacedKey("crossfight", "naga_" + Long.toString(System.nanoTime(), 36)), -lost, AttributeModifier.Operation.ADD_NUMBER));
                    if (t.getHealth() > max.getValue()) t.setHealth(max.getValue());
                    w.spawnParticle(Particle.DUST, t.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0, new Particle.DustOptions(Color.fromRGB(255, 240, 150), 1.3f));
                    if (t instanceof Player tp) Msg.send(tp, "<red>Le Livre de Naga t'a retire " + fmt(lost / 2) + " coeur(s) de vie maximale pour le reste de la partie.");
                }
                w.spawnParticle(Particle.FLASH, at.clone().add(0, 1, 0), 1, 0, 0, 0, 0, Color.fromRGB(255, 245, 190));
                w.playSound(at, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.7f);
                w.playSound(at, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5f, 1.6f);
            }
            case "javelot", "gae_bolg", "gravidus" -> spear(p, l);
            default -> { return false; }
        }
        return true;
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.format(java.util.Locale.US, "%.1f", d);
    }

    private boolean none(Player p, String why) {
        Msg.actionBar(p, "<red>" + why);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.7f);
        return false;
    }

    /** L'allie que le joueur regarde, sinon le plus proche. */
    private LivingEntity pickAlly(Player p, double radius) {
        Vector look = p.getEyeLocation().getDirection().normalize();
        LivingEntity best = null, nearest = null;
        double bestDot = 0.93, nearestDist = Double.MAX_VALUE;
        for (LivingEntity a : Effects.alliesWithin(p, radius)) {
            if (a == p) continue;
            Vector to = a.getLocation().add(0, 1, 0).toVector().subtract(p.getEyeLocation().toVector());
            double dist = to.length();
            if (dist < nearestDist) { nearestDist = dist; nearest = a; }
            if (dist > 0.5 && to.normalize().dot(look) > bestDot) { bestDot = to.dot(look); best = a; }
        }
        return best != null ? best : nearest;
    }

    // ------------------------------------------------------------------ sorts a effet dans le temps

    /** Bolganone : la boule de feu parcourt 10 blocs en enflammant le sol, et explose sur le premier adversaire. */
    private void fireball(Player p) {
        World w = p.getWorld();
        Vector dir = p.getEyeLocation().getDirection().normalize();
        Location pos = p.getEyeLocation().add(dir.clone().multiply(0.8));
        TempBlocks fire = new TempBlocks();
        fire.restoreAfter(20 * 6);
        w.playSound(pos, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.9f);
        new BukkitRunnable() {
            double travelled = 0;
            @Override public void run() {
                for (int step = 0; step < 2; step++) {
                    pos.add(dir.clone().multiply(0.5));
                    travelled += 0.5;
                    w.spawnParticle(Particle.FLAME, pos, 6, 0.15, 0.15, 0.15, 0.01);
                    w.spawnParticle(Particle.LAVA, pos, 1);
                    // Le sol sous son passage prend feu.
                    Block ground = pos.getBlock();
                    for (int d = 0; d < 4 && ground.getType().isAir(); d++) ground = ground.getRelative(BlockFace.DOWN);
                    // (pas sous les pieds du lanceur : le feu commence a trois blocs)
                    if (travelled >= 3 && !ground.getType().isAir()) fire.place(ground.getRelative(BlockFace.UP), Material.FIRE);
                    LivingEntity hit = null;
                    for (LivingEntity t : Effects.enemiesWithin(p, pos, 1.2)) { hit = t; break; }
                    if (hit != null || !pos.getBlock().isPassable() || travelled >= 10) {
                        if (hit != null) {
                            Effects.damage(hit, 6, p);
                            hit.setFireTicks(80);
                        }
                        w.spawnParticle(Particle.EXPLOSION_EMITTER, pos, 1);
                        w.playSound(pos, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.1f);
                        cancel();
                        return;
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Excalibur : la tornade qui emporte la cible. */
    private void tornado(LivingEntity t) {
        World w = t.getWorld();
        w.playSound(t.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 0.6f);
        new BukkitRunnable() {
            int n = 0;
            @Override public void run() {
                if (n++ > 24 || !t.isValid()) { cancel(); return; }
                Location c = t.getLocation();
                for (int i = 0; i < 10; i++) {
                    double h = i * 0.35, a = n * 0.7 + i * 0.8, r = 0.5 + h * 0.35;
                    w.spawnParticle(Particle.CLOUD, c.clone().add(Math.cos(a) * r, h - 1, Math.sin(a) * r), 1, 0, 0, 0, 0);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Goethe : une zone de 5x5 qui vole un coeur par seconde, pendant 10 secondes. */
    private void drainZone(Player p, Location centre) {
        World w = centre.getWorld();
        Particle.DustOptions dark = new Particle.DustOptions(Color.fromRGB(90, 20, 130), 1.2f);
        w.playSound(centre, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 0.7f);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                if (t++ >= 200 || !p.isOnline()) { cancel(); return; }
                if (t % 2 == 0) for (int i = 0; i < 10; i++) {
                    double x = ThreadLocalRandom.current().nextDouble(-2.5, 2.5), z = ThreadLocalRandom.current().nextDouble(-2.5, 2.5);
                    w.spawnParticle(Particle.DUST, centre.clone().add(x, 0.1 + Math.random() * 0.6, z), 1, 0, 0, 0, 0, dark);
                }
                for (int i = -5; i <= 5 && t % 4 == 0; i++) {
                    for (double[] edge : new double[][]{{i * 0.5, -2.5}, {i * 0.5, 2.5}, {-2.5, i * 0.5}, {2.5, i * 0.5}})
                        w.spawnParticle(Particle.DUST, centre.clone().add(edge[0], 0.1, edge[1]), 1, 0, 0, 0, 0, dark);
                }
                if (t % 20 != 0) return;
                for (LivingEntity e : Effects.enemiesWithin(p, centre, 4)) {
                    Location l = e.getLocation();
                    if (Math.abs(l.getX() - centre.getX()) > 2.5 || Math.abs(l.getZ() - centre.getZ()) > 2.5 || Math.abs(l.getY() - centre.getY()) > 3) continue;
                    Effects.damage(e, 2, p);
                    Effects.heal(p, 2);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Expiration : un labyrinthe d'epines de 20x20 autour du lanceur, pendant 30 secondes. */
    private void maze(Player p) {
        World w = p.getWorld();
        Location c = p.getLocation().getBlock().getLocation();
        TempBlocks walls = new TempBlocks().unbreakable();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) {
            // Des murs sur une grille de 4 blocs, perces de passages ; l'enceinte, elle, est pleine.
            boolean border = Math.abs(x) == 10 || Math.abs(z) == 10;
            boolean line = Math.floorMod(x + 2, 4) == 0 || Math.floorMod(z + 2, 4) == 0;
            if (!border && (!line || r.nextInt(100) < 38)) continue;
            if (Math.abs(x) <= 1 && Math.abs(z) <= 1) continue;
            int gy = Effects.groundAround(w, c.getBlockX() + x, c.getBlockZ() + z, c.getBlockY());
            if (gy == Integer.MIN_VALUE) continue;
            for (int h = 1; h <= 3; h++) walls.place(w.getBlockAt(c.getBlockX() + x, gy + h, c.getBlockZ() + z), h == 3 ? Material.DEAD_BUSH : Material.MANGROVE_ROOTS);
        }
        walls.restoreAfter(MAZE_LIFE);
        w.playSound(c, Sound.BLOCK_ROOTS_PLACE, 1f, 0.5f);
        w.playSound(c, Sound.ENTITY_WARDEN_EMERGE, 0.5f, 1.4f);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t += 10;
                if (t > MAZE_LIFE || !p.isOnline() || p.isDead()) { cancel(); return; }
                boolean inside = inSquare(p.getLocation(), c);
                if (inside) Effects.refresh(p, PotionEffectType.STRENGTH, 0, 30);
                for (LivingEntity e : Effects.enemiesWithin(p, c, 16)) {
                    if (!inSquare(e.getLocation(), c)) continue;
                    if (inside) Effects.refresh(e, PotionEffectType.GLOWING, 0, 30);
                    if (t % 40 == 0) Effects.damage(e, 2, p);
                }
            }
            private boolean inSquare(Location l, Location c) {
                return Math.abs(l.getX() - c.getX() - 0.5) <= 10.5 && Math.abs(l.getZ() - c.getZ() - 0.5) <= 10.5 && Math.abs(l.getY() - c.getY()) <= 8;
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    /**
     * Une lance lancee : elle quitte la main, file droit devant, blesse le premier adversaire touche. Le Javelot
     * est perdu ; Gae Bolg et Gravidus reviennent en main (une utilisation de moins s'ils ont touche).
     */
    private void spear(Player p, Loot l) {
        World w = p.getWorld();
        ItemStack thrown = p.getInventory().getItemInMainHand().clone();
        int slot = p.getInventory().getHeldItemSlot();
        p.getInventory().setItemInMainHand(null);
        Vector dir = p.getEyeLocation().getDirection().normalize();
        Location pos = p.getEyeLocation().add(dir.clone().multiply(0.6));
        ItemDisplay show = w.spawn(pos.clone().setDirection(dir), ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(new ItemStack(Material.TRIDENT));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            // Le modele du trident pointe vers le bas : on le couche dans le sens du vol.
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotationX((float) Math.toRadians(-90)), new Vector3f(1.2f, 1.2f, 1.2f), new Quaternionf()));
        });
        w.playSound(pos, Sound.ITEM_TRIDENT_THROW, 1f, 1f);
        boolean returns = !l.id().equals("javelot");
        new BukkitRunnable() {
            double travelled = 0;
            @Override public void run() {
                LivingEntity hit = null;
                boolean wall = false;
                for (int step = 0; step < 3 && hit == null && !wall; step++) {
                    pos.add(dir.clone().multiply(0.5));
                    travelled += 0.5;
                    w.spawnParticle(Particle.CRIT, pos, 1, 0, 0, 0, 0);
                    for (LivingEntity t : Effects.enemiesWithin(p, pos, 1.0)) { hit = t; break; }
                    wall = !pos.getBlock().isPassable();
                }
                show.teleport(pos.clone().setDirection(dir));
                if (hit == null && !wall && travelled < 30) return;
                cancel();
                show.remove();
                if (hit != null) {
                    Effects.damage(hit, l.damage(), p);
                    w.playSound(pos, Sound.ITEM_TRIDENT_HIT, 1f, 1f);
                    if (l.id().equals("gravidus")) { Effects.heal(p, 6); w.spawnParticle(Particle.HEART, p.getLocation().add(0, 2, 0), 4, 0.3, 0.2, 0.3); }
                } else w.playSound(pos, Sound.ITEM_TRIDENT_HIT_GROUND, 1f, 1f);
                if (!returns || !p.isOnline()) return;
                // Elle revient : dans la case d'ou elle est partie si elle est libre, sinon dans l'inventaire.
                int left = thrown.getItemMeta().getPersistentDataContainer().getOrDefault(usesKey, PersistentDataType.INTEGER, 1) - (hit != null ? 1 : 0);
                if (left <= 0) { p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f); Msg.actionBar(p, "<gray>" + l.name() + " est use."); return; }
                ItemMeta meta = thrown.getItemMeta();
                meta.getPersistentDataContainer().set(usesKey, PersistentDataType.INTEGER, left);
                thrown.setItemMeta(meta);
                describe(thrown, l, left);
                ItemStack there = p.getInventory().getItem(slot);
                if (there == null || there.getType().isAir()) p.getInventory().setItem(slot, thrown);
                else for (ItemStack rest : p.getInventory().addItem(thrown).values()) w.dropItemNaturally(p.getLocation(), rest);
                p.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1f, 1f);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------------ armes

    /** Un coup porte avec une arme de lootbox : ses effets, et une utilisation de moins. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || Effects.isAbilityDamage() || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        Loot l = lootOf(p.getInventory().getItemInMainHand());
        if (l == null || l.kind() != Kind.MELEE || !(e.getEntity() instanceof LivingEntity)) return;
        if (l.id().equals("vengeance")) {
            // 10 degats, 12 si un allie est mort, 14 si deux le sont.
            int dead = 0;
            var team = plugin.teams().teamOf(p);
            if (team != null) for (UUID u : team.members()) {
                CfPlayer cf = plugin.players().get(u);
                if (!u.equals(p.getUniqueId()) && cf != null && !cf.isAlive()) dead++;
            }
            e.setDamage(e.getDamage() + 2.0 * Math.min(2, dead));
        }
        double dealt = e.getFinalDamage();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline()) return;
            if (l.id().equals("sol_katti")) Effects.heal(p, dealt / 2);
            spend(p, l);
        });
    }

    /** Un tir a l'arc de lootbox : une utilisation de moins ; la fleche de Parthia part droit. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        Loot l = lootOf(e.getBow());
        if (l == null || l.kind() != Kind.BOW) return;
        if (l.id().equals("parthia") && e.getProjectile() instanceof Arrow arrow) {
            arrow.setGravity(false);
            arrow.setVelocity(p.getEyeLocation().getDirection().normalize().multiply(5.0));
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (arrow.isValid() && !arrow.isInBlock()) arrow.remove(); }, 40L);
        }
        Bukkit.getScheduler().runTask(plugin, () -> { if (p.isOnline()) spend(p, l); });
    }

    // ------------------------------------------------------------------ les coffres

    /** Les objets d'un tier (leurs noms). */
    public static List<String> idsOf(int tier) {
        List<String> out = new ArrayList<>();
        for (Loot l : LOOTS.values()) if (l.tier() == tier) out.add(l.id());
        return out;
    }

    /** Le tier d'un objet (1 a 4), ou 0 s'il n'existe pas. */
    public static int tierOf(String id) {
        Loot l = LOOTS.get(id);
        return l == null ? 0 : l.tier();
    }

    /** La couleur d'un tier, pour les textes. */
    public static String colorOf(int tier) { return TIER_COLOR[tier - 1]; }

    /** Tire un tier au hasard, aux chances des lootbox (45, 35, 19 et 1 %). */
    public int rollTier() {
        int roll = ThreadLocalRandom.current().nextInt(100), sum = 0;
        for (int i = 0; i < TIER_CHANCE.length; i++) { sum += TIER_CHANCE[i]; if (roll < sum) return i + 1; }
        return 1;
    }

    /** L'evenement : des lootbox apparaissent au hasard dans la bordure, et leurs positions sont annoncees. */
    public void wave() {
        World w = plugin.game().world();
        int count = plugin.game().rules().value("lootbox-nombre");
        int radius = (int) Math.max(20, w.getWorldBorder().getSize() / 2 * 0.9);
        List<String> where = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Location at = plugin.game().randomGroundLocation(radius);
            int tier = rollTier();
            if (place(at, tier)) where.add("<" + TIER_COLOR[tier - 1] + ">" + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + "</" + TIER_COLOR[tier - 1] + ">");
        }
        // Le lieu des lootbox (game/Places.java) en recoit une de plus, a coup sur.
        // (evenement des lieux : deux si ce lieu est renforce, aucune s'il est affaibli)
        Location first = plugin.places().lootSpot();
        int mood = plugin.placeEvents().mood("lootbox");
        for (int k = 0; first != null && k < (mood > 0 ? 2 : mood < 0 ? 0 : 1); k++) {
            Location sure = first.clone().add(2 * k, 0, 0);
            int tier = rollTier();
            if (place(sure, tier)) where.add("<" + TIER_COLOR[tier - 1] + ">" + sure.getBlockX() + " " + sure.getBlockY() + " " + sure.getBlockZ() + "</" + TIER_COLOR[tier - 1] + ">");
        }
        Msg.broadcast("<gold><bold>Evenement :</bold> " + where.size() + " Lootbox sont apparues sur la map ! <gray>" + String.join("<dark_gray>, ", where));
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 0.8f);
    }

    /**
     * Pose une lootbox de ce tier (au hasard si 0) a cet endroit, avec son contenu et son faisceau de balise. Son coffre
     * fait face a la direction opposee au regard de {@code at} (devant un joueur : tourne vers lui).
     */
    public boolean place(Location at, int tier) {
        return place(at, tier, false);
    }

    /** Pose une lootbox ; avec embleme, l'Embleme de Feu y est a coup sur (/cf demo embleme). */
    public boolean place(Location at, int tier, boolean embleme) {
        if (tier < 1 || tier > 4) tier = rollTier();
        Block b = at.getBlock();
        if (!b.getType().isAir() && !b.isReplaceable()) b = b.getRelative(BlockFace.UP);
        if (boxes.containsKey(b) || (!b.getType().isAir() && !b.isReplaceable())) return false;
        b.setType(Material.BARRIER);
        Box box = new Box(b, tier);
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Loot> pool = new ArrayList<>();
        for (Loot l : LOOTS.values()) if (l.tier() == tier) pool.add(l);
        Loot loot = pool.get(r.nextInt(pool.size()));
        box.prix.put(DIAMANTS, new ItemStack(Material.DIAMOND, r.nextInt(2, 9)));
        box.prix.put(CENTRE, item(loot.id()));
        box.prix.put(OR, new ItemStack(Material.GOLD_INGOT, r.nextInt(6, 13)));
        // Un arc a Infinite a besoin d'une fleche dans l'inventaire : a cote de lui.
        if (loot.kind() == Kind.BOW && !loot.id().equals("stellaire")) box.prix.put(14, new ItemStack(Material.ARROW));
        if (embleme) box.prix.put(CASE_EMBLEME, embleme());
        // Le coffre au centre du bloc (le modele, de 0 a 16 pixels, est centre sur l'affichage), tourne d'un quart de
        // tour exact. Le devant du coffre (le sud du modele, comme le coffre de Minecraft) se retrouve a l'oppose de la
        // direction ou regarde l'affichage : regarder comme le joueur le tourne vers lui.
        Location c = b.getLocation().add(0.5, 0.5, 0.5);
        c.setYaw(Math.round(at.getYaw() / 90f) * 90f);
        c.setPitch(0f);
        box.look = c.getWorld().spawn(c, ItemDisplay.class, d -> {
            d.setPersistent(false);
            d.setItemStack(look());
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setViewRange(2f);
            d.setShadowRadius(0.6f);
            d.setShadowStrength(0.8f);
        });
        // Le parachute, un bloc au-dessus (son modele descend jusqu'au dessus du coffre).
        ItemStack voile = parachute(tier);
        if (voile != null) {
            box.chute = c.getWorld().spawn(c.clone().add(0, 1, 0), ItemDisplay.class, d -> {
                d.setPersistent(false);
                d.setItemStack(voile);
                d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                d.setViewRange(2f);
            });
        }
        // La zone a cliquer depasse un peu du bloc : le viseur la touche avant la barriere.
        box.hit = c.getWorld().spawn(b.getLocation().add(0.5, -0.025, 0.5), Interaction.class, i -> {
            i.setPersistent(false);
            i.setInteractionWidth(1.05f);
            i.setInteractionHeight(1.05f);
            i.setResponsive(true);
        });
        // Les affichages ne sont pas sauvegardes : le tronçon reste charge tant que la lootbox est la.
        b.getChunk().addPluginChunkTicket(plugin);
        faisceau(box);
        boxes.put(b, box);
        beam(box);
        Bukkit.getScheduler().runTaskLater(plugin, () -> remove(box), BOX_LIFE);
        return true;
    }

    /** Le coffre en 3D (le coffre de Minecraft si le pack ne l'a pas). */
    private static ItemStack look() {
        ItemStack it = new ItemStack(Material.CHEST);
        if (!com.kiokouwhite.crossfight.util.Sounds.has("model.lootbox")) return it;
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey(com.kiokouwhite.crossfight.util.Sounds.NS, "lootbox"));
        it.setItemMeta(meta);
        return it;
    }

    /** Le parachute teint de la couleur du tier, ou null si le pack ne l'a pas. */
    private static ItemStack parachute(int tier) {
        if (!com.kiokouwhite.crossfight.util.Sounds.has("model.lootbox")) return null;
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey(com.kiokouwhite.crossfight.util.Sounds.NS, "lootbox_parachute"));
        var cmd = meta.getCustomModelDataComponent();
        cmd.setColors(List.of(TIER_BEAM[tier - 1]));
        meta.setCustomModelDataComponent(cmd);
        it.setItemMeta(meta);
        return it;
    }

    /** L'Embleme de Feu, avec son icone dans le pack complet (tools/build_icones.py : marth_embleme). */
    public ItemStack embleme() {
        // Sans le pack complet : une creme de magma, une pierre ronde couleur de feu.
        ItemStack it = new ItemStack(Material.MAGMA_CREAM);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Msg.mm("<!italic><gold><bold>Embleme de Feu"));
        meta.lore(List.of(Msg.mm("<!italic><gray>Utilise par Marth, il change sa Falchion"), Msg.mm("<!italic><gray>en Sainte-Falchion.")));
        if (com.kiokouwhite.crossfight.util.Sounds.has("icone.marth_embleme")) {
            meta.setItemModel(new NamespacedKey(com.kiokouwhite.crossfight.util.Sounds.NS, "icone/marth_embleme"));
        }
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, EMBLEME);
        it.setItemMeta(meta);
        return it;
    }

    private boolean estEmbleme(ItemStack it) {
        return it != null && it.hasItemMeta() && EMBLEME.equals(it.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING));
    }

    private boolean estMarth(Player p) {
        CfPlayer cf = plugin.players().get(p);
        return cf != null && cf.character() != null && "marth".equals(cf.character().id());
    }

    /** Un clic droit avec l'Embleme : chez Marth, l'animation (qui se lance d'elle-meme de toute facon) ; sinon, rien. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEmbleme(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (!estEmbleme(it)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!estMarth(p)) { Msg.send(p, "<gray>Seul Marth peut eveiller l'Embleme de Feu."); return; }
        if (com.kiokouwhite.crossfight.character.fireemblem.Marth.isHoly(p)) { Msg.send(p, "<gray>Ta Falchion est deja la Sainte-Falchion."); return; }
        eveiller(p);
    }

    /**
     * L'animation de l'Emblème de Feu (tools/build_embleme_anim.py : ANIM_IMAGES images, une par tick, dans la police
     * crossfight:embleme du pack complet) : en titre, la Falchion apparait, l'Embleme vient toucher sa garde, elle
     * brille ; a l'image ANIM_ECLAIR, un eclair blanc, et la Sainte-Falchion apparait dans un halo bleu. Le sous-titre
     * le dit. Sans le pack, seuls les textes.
     */
    private static final int ANIM_IMAGES = 72, ANIM_ECLAIR = 36;
    /** Les Marth dont l'animation est en cours. */
    private final Set<UUID> eveils = new HashSet<>();

    /**
     * Des que l'Embleme de Feu est dans l'inventaire de Marth (s'il n'a pas deja la Sainte-Falchion), l'animation se
     * lance ; a l'eclair, l'Embleme disparait et la Falchion devient la Sainte-Falchion. S'il se deconnecte ou meurt
     * avant, rien ne change et l'Embleme lui reste (l'animation repartira).
     */
    public void eveiller(Player p) {
        if (eveils.contains(p.getUniqueId()) || p.isDead() || !estMarth(p)
                || java.util.Arrays.stream(p.getInventory().getContents()).noneMatch(this::estEmbleme)
                || com.kiokouwhite.crossfight.character.fireemblem.Marth.isHoly(p)) return;
        final UUID id = p.getUniqueId();
        eveils.add(id);
        final boolean images = com.kiokouwhite.crossfight.util.Sounds.has("font.embleme") && com.kiokouwhite.crossfight.util.Sounds.hasPackLoaded(p);
        final net.kyori.adventure.key.Key police = net.kyori.adventure.key.Key.key("crossfight", "embleme");
        final Component avant = Msg.mm("<gold>Au contact de l'Emblème de Feu, la Falchion brille !");
        final Component apres = Msg.mm("<aqua><bold>La Falchion devient la Sainte-Falchion !");
        // Chaque image reste 1,5 s si la suivante tarde (serveur ou client a la peine) : l'image suivante la remplace
        // de toute facon, et l'animation ne clignote pas.
        final var tick = net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO, java.time.Duration.ofMillis(1500), java.time.Duration.ZERO);
        new BukkitRunnable() {
            int f = 0;

            @Override public void run() {
                if (!p.isOnline() || p.isDead()) {
                    eveils.remove(id);
                    if (p.isOnline()) p.clearTitle();
                    cancel();
                    return;
                }
                if (f == ANIM_ECLAIR) {
                    // L'eclair : l'Embleme se fond dans la Falchion. S'il n'est plus la (jete entre-temps), tout s'arrete.
                    ItemStack[] contenu = p.getInventory().getContents();
                    int k = -1;
                    for (int i = 0; i < contenu.length && k < 0; i++) if (estEmbleme(contenu[i])) k = i;
                    if (k < 0) { eveils.remove(id); p.clearTitle(); cancel(); return; }
                    contenu[k].setAmount(contenu[k].getAmount() - 1);
                    p.getInventory().setItem(k, contenu[k].getAmount() > 0 ? contenu[k] : null);
                    com.kiokouwhite.crossfight.character.fireemblem.Marth.upgrade(p);
                    p.getWorld().playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
                    p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.4);
                }
                // Les sons : l'Embleme s'approche, il touche la garde, la lame brille, la Sainte-Falchion.
                if (f == 8) p.playSound(p, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
                if (f == 19) p.playSound(p, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.4f);
                if (f == 20) p.playSound(p, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.5f);
                if (f == ANIM_ECLAIR + 6) p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.2f);
                Component sous = f < ANIM_ECLAIR ? avant : apres;
                if (images) {
                    // Une image par tick ; la derniere s'efface avec le sous-titre.
                    var temps = f == ANIM_IMAGES - 1 ? net.kyori.adventure.title.Title.Times.times(java.time.Duration.ZERO,
                            java.time.Duration.ofMillis(200), java.time.Duration.ofMillis(500)) : tick;
                    p.showTitle(net.kyori.adventure.title.Title.title(Component.text(String.valueOf((char) (0xE000 + f))).font(police)
                            .color(net.kyori.adventure.text.format.NamedTextColor.WHITE), sous, temps));
                } else if (f == 0 || f == ANIM_ECLAIR) {
                    p.showTitle(net.kyori.adventure.title.Title.title(Msg.mm(f == 0 ? "<gold><bold>Emblème de Feu" : "<aqua><bold>Sainte-Falchion"), sous,
                            net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(250), java.time.Duration.ofMillis(f == 0 ? 1800 : 1500), java.time.Duration.ofMillis(500))));
                }
                f++;
                if (f >= ANIM_IMAGES) { eveils.remove(id); cancel(); }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** Le contenu d'une lootbox de ce tier, donne directement a un joueur (recompense de Regna Ferox). */
    public void reward(Player p, int tier) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Loot> pool = new ArrayList<>();
        for (Loot l : LOOTS.values()) if (l.tier() == tier) pool.add(l);
        List<ItemStack> gifts = new ArrayList<>(List.of(new ItemStack(Material.DIAMOND, r.nextInt(2, 9)), new ItemStack(Material.GOLD_INGOT, r.nextInt(6, 13)), item(pool.get(r.nextInt(pool.size())).id())));
        for (ItemStack rest : p.getInventory().addItem(gifts.toArray(new ItemStack[0])).values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
    }

    /**
     * Le faisceau d'une lootbox, comme celui d'une balise : le bloc sous elle devient du verre teinte a la couleur de son
     * tier, avec dessous une balise et son socle de neuf blocs de fer. Le faisceau monte de la, a travers la lootbox,
     * jusqu'au ciel (s'il ne rencontre pas de bloc opaque). Ce qu'il y avait la est retenu et remis quand la lootbox
     * disparait ; ces blocs ne se cassent pas, ne sautent pas et ne se poussent pas, et la balise ne s'ouvre pas.
     * Rien n'est pose (faux, et la colonne de particules le remplace) si la place manque : le bas du monde, un coffre ou
     * un bloc incassable, ou le faisceau d'une autre lootbox qui voudrait un autre bloc au meme endroit.
     */
    private boolean faisceau(Box box) {
        Block sous = box.block.getRelative(BlockFace.DOWN);
        if (sous.getY() - 2 < sous.getWorld().getMinHeight()) return false;
        Map<Block, Material> plan = new LinkedHashMap<>();
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) plan.put(sous.getRelative(dx, -2, dz), Material.IRON_BLOCK);
        plan.put(sous.getRelative(BlockFace.DOWN), Material.BEACON);
        plan.put(sous, VERRES[box.tier - 1]);
        for (var e : plan.entrySet()) {
            Block k = e.getKey();
            if (usages.containsKey(k)) {
                if (k.getType() != e.getValue()) return false;
                continue;
            }
            if (boxes.containsKey(k) || k.getState() instanceof org.bukkit.block.TileState || k.getType().getHardness() < 0) return false;
        }
        for (var e : plan.entrySet()) {
            Block k = e.getKey();
            if (!usages.containsKey(k)) {
                avant.put(k, k.getBlockData());
                k.setType(e.getValue(), false);
            }
            usages.merge(k, 1, Integer::sum);
            box.faisceau.add(k);
        }
        return true;
    }

    /** Les blocs du faisceau remis comme avant (le verre d'abord, le socle ensuite), sauf ceux d'une autre lootbox. */
    private void eteindre(Box box) {
        for (int i = box.faisceau.size() - 1; i >= 0; i--) {
            Block k = box.faisceau.get(i);
            int n = usages.merge(k, -1, Integer::sum);
            if (n > 0) continue;
            usages.remove(k);
            var d = avant.remove(k);
            if (d != null) k.setBlockData(d, false);
        }
        box.faisceau.clear();
    }

    /**
     * La surveillance d'une lootbox ; et, si elle n'a pas de faisceau de balise, une colonne de particules a la couleur
     * de son tier, qui la signale de loin.
     */
    private void beam(Box box) {
        Particle.DustOptions dust = new Particle.DustOptions(TIER_BEAM[box.tier - 1], 2.2f);
        new BukkitRunnable() {
            @Override public void run() {
                if (boxes.get(box.block) != box) { cancel(); return; }
                // Son coffre ou sa barriere effaces par autre chose (le nettoyage du terrain de test) : elle disparait.
                if (box.block.getType() != Material.BARRIER || box.look == null || !box.look.isValid() || box.hit == null || !box.hit.isValid()
                        || (box.chute != null && !box.chute.isValid())) {
                    remove(box);
                    cancel();
                    return;
                }
                if (!box.faisceau.isEmpty()) return;
                // Au-dessus du parachute.
                Location base = box.block.getLocation().add(0.5, 3.0, 0.5);
                for (int y = 0; y < 40; y += 2) base.getWorld().spawnParticle(Particle.DUST, base.clone().add(0, y, 0), 1, 0.05, 0.3, 0.05, 0, dust, true);
            }
        }.runTaskTimer(plugin, 0L, 8L);
    }

    private void remove(Box box) {
        if (!boxes.remove(box.block, box)) return;
        for (var viewer : new ArrayList<>(box.inv.getViewers())) viewer.closeInventory();
        box.inv.clear();
        if (box.look != null) box.look.remove();
        if (box.chute != null) box.chute.remove();
        if (box.hit != null) box.hit.remove();
        if (box.block.getType() == Material.BARRIER) box.block.setType(Material.AIR);
        eteindre(box);
        // Le tronçon peut se decharger s'il n'a plus d'autre lootbox.
        if (boxes.keySet().stream().noneMatch(o -> o.getWorld() == box.block.getWorld() && (o.getX() >> 4) == (box.block.getX() >> 4) && (o.getZ() >> 4) == (box.block.getZ() >> 4)))
            box.block.getChunk().removePluginChunkTicket(plugin);
    }

    /** Fin de partie (ou arret du serveur) : plus de lootbox ni de temps de recharge. */
    public void clear() {
        for (Box box : new ArrayList<>(boxes.values())) remove(box);
        cooldowns.clear();
    }

    /** Un clic sur le coffre l'ouvre (la zone a cliquer, ou la barriere si le viseur la touche). */
    @EventHandler(ignoreCancelled = true)
    public void onClickBox(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Interaction i)) return;
        for (Box box : boxes.values()) {
            if (box.hit != null && box.hit.getUniqueId().equals(i.getUniqueId())) {
                e.setCancelled(true);
                e.getPlayer().openInventory(box.inv);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClickBarrier(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        // La balise d'un faisceau ne s'ouvre pas.
        if (usages.containsKey(e.getClickedBlock()) && e.getClickedBlock().getType() == Material.BEACON) { e.setCancelled(true); return; }
        if (e.getHand() != EquipmentSlot.HAND) return;
        Box box = boxes.get(e.getClickedBlock());
        if (box == null) return;
        e.setCancelled(true);
        e.getPlayer().openInventory(box.inv);
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getInventory().getHolder() instanceof Box box) || !(e.getPlayer() instanceof Player p)) return;
        e.titleOverride(titre(p, box, box.couleur));
        Location at = box.block.getLocation().add(0.5, 0.5, 0.5);
        p.getWorld().playSound(at, Sound.BLOCK_CHEST_OPEN, 0.6f, 1f);
        // La premiere ouverture est annoncee, et lance la roulette.
        if (box.etat != FERMEE) return;
        box.etat = ROULETTE;
        box.ouvreur = p.getUniqueId();
        Msg.broadcast("<gold>" + p.getName() + " ouvre une <" + TIER_COLOR[box.tier - 1] + ">Lootbox tier " + box.tier + "</" + TIER_COLOR[box.tier - 1] + "> !");
        // Marth, s'il n'a pas encore la Sainte-Falchion ni d'Embleme, peut y trouver l'Embleme de Feu.
        if (!box.prix.containsKey(CASE_EMBLEME) && estMarth(p) && !com.kiokouwhite.crossfight.character.fireemblem.Marth.isHoly(p)
                && java.util.Arrays.stream(p.getInventory().getContents()).noneMatch(this::estEmbleme)
                && ThreadLocalRandom.current().nextInt(100) < plugin.game().rules().value("embleme-de-feu")) {
            box.prix.put(CASE_EMBLEME, embleme());
        }
        Bukkit.getScheduler().runTask(plugin, () -> roulette(box));
    }

    /** Le titre du coffre : la fenetre du pack a la couleur de ce tier, ou du texte de cette couleur sans le pack. */
    private static boolean fenetres(Player p) {
        return com.kiokouwhite.crossfight.util.Sounds.has("font.lootbox") && com.kiokouwhite.crossfight.util.Sounds.hasPackLoaded(p);
    }

    private static Component titre(Player p, Box box, int couleur) {
        return fenetres(p) ? Component.text("" + RECUL + (char) (FENETRE + couleur - 1)).color(net.kyori.adventure.text.format.NamedTextColor.WHITE)
                : Msg.mm("<" + TIER_COLOR[couleur - 1] + ">Lootbox tier " + box.tier);
    }

    /** Le meme titre en texte simple, pour InventoryView.setTitle (la fenetre est dans la police par defaut). */
    @SuppressWarnings("deprecation")
    private static void retitrer(Player p, Box box, int couleur) {
        p.getOpenInventory().setTitle(fenetres(p) ? "\u00a7f" + RECUL + (char) (FENETRE + couleur - 1)
                : "\u00a7" + LEGACY[couleur - 1] + "Lootbox tier " + box.tier);
    }

    /**
     * Une roulette verticale : sa colonne (la case du milieu), sa bande d'objets (l'indice 0 en bas au depart ; ils
     * descendent d'une case a chaque cran), ses crans. Un objet de la bande est tire quand il apparait en haut ; celui
     * de l'indice crans + 1 est l'objet gagne, au milieu au dernier cran.
     */
    private static final class Rouleau {
        final int milieu, crans;
        final ItemStack gagne;
        final java.util.function.Supplier<ItemStack> tirage;
        final List<ItemStack> bande = new ArrayList<>();
        int cran, attente;
        boolean arrete;

        Rouleau(int milieu, int crans, ItemStack gagne, java.util.function.Supplier<ItemStack> tirage) {
            this.milieu = milieu;
            this.crans = crans;
            this.gagne = gagne;
            this.tirage = tirage;
        }

        ItemStack at(int i) {
            while (bande.size() <= i) bande.add(null);
            if (bande.get(i) == null) bande.set(i, i == crans + 1 ? gagne.clone() : tirage.get());
            return bande.get(i);
        }

        /** Les objets visibles (sauf le gagnant) sont tires a nouveau : la roulette de l'objet change de tier. */
        void retirer() {
            for (int i = cran; i <= cran + 2 && i < bande.size(); i++) if (i != crans + 1) bande.set(i, null);
        }

        void montrer(Inventory inv) {
            inv.setItem(milieu + 9, at(cran));
            inv.setItem(milieu, at(cran + 1));
            inv.setItem(milieu - 9, at(cran + 2));
        }

        /** Un tick : vrai si la roulette a avance d'un cran. */
        boolean avancer() {
            if (arrete) return false;
            if (attente > 0) { attente--; return false; }
            cran++;
            if (cran >= crans) arrete = true;
            else attente = (int) Math.round(ATTENTE * Math.pow(cran / (double) crans, 2.4));
            return true;
        }
    }

    /**
     * La roulette : trois roulettes verticales, comme une machine a sous. Les diamants (de 2 a 8), l'objet et l'or (de
     * 6 a 12) descendent, de plus en plus lentement, et s'arretent l'une apres l'autre : les diamants, l'or, puis
     * l'objet. Ce qui s'arrete sur la ligne du milieu est gagne. Pendant ce temps, la fenetre passe par les couleurs des
     * quatre tiers, et la roulette de l'objet montre les objets du tier dont la fenetre a la couleur ; peu avant que
     * l'objet gagne n'apparaisse, la fenetre prend la couleur du tier de la Lootbox et la garde. Ceux qui ont la
     * Lootbox ouverte la voient tous ; on ne peut rien prendre avant la fin.
     */
    private void roulette(Box box) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<List<String>> parTier = new ArrayList<>();
        for (int t = 1; t <= 4; t++) parTier.add(idsOf(t));
        Rouleau diamants = new Rouleau(DIAMANTS, CRANS_DIAMANTS, box.prix.get(DIAMANTS), () -> new ItemStack(Material.DIAMOND, r.nextInt(2, 9)));
        Rouleau or = new Rouleau(OR, CRANS_OR, box.prix.get(OR), () -> new ItemStack(Material.GOLD_INGOT, r.nextInt(6, 13)));
        Rouleau objet = new Rouleau(CENTRE, CRANS_OBJET, box.prix.get(CENTRE), () -> {
            List<String> ids = parTier.get(box.couleur - 1);
            return item(ids.get(r.nextInt(ids.size())));
        });
        Rouleau[] rouleaux = {diamants, objet, or};
        for (Rouleau ro : rouleaux) ro.montrer(box.inv);
        new BukkitRunnable() {
            int depuis = 0;
            boolean fixee = false;

            @Override public void run() {
                if (boxes.get(box.block) != box) { cancel(); return; }
                depuis++;
                // La couleur suivante, au plus tous les ECLAT ticks ; puis celle du tier, juste avant que l'objet gagne
                // n'entre par le haut. La roulette de l'objet suit la couleur de la fenetre.
                int couleur = box.couleur;
                if (!fixee && objet.cran >= objet.crans - 2) { couleur = box.tier; fixee = true; }
                else if (!fixee && depuis >= ECLAT) couleur = box.couleur % 4 + 1;
                if (couleur != box.couleur || depuis >= ECLAT) depuis = 0;
                if (couleur != box.couleur) {
                    box.couleur = couleur;
                    objet.retirer();
                    objet.montrer(box.inv);
                    for (var v : new ArrayList<>(box.inv.getViewers())) if (v instanceof Player pl) retitrer(pl, box, box.couleur);
                }
                boolean clic = false;
                for (int k = 0; k < rouleaux.length; k++) {
                    Rouleau ro = rouleaux[k];
                    if (!ro.avancer()) continue;
                    ro.montrer(box.inv);
                    clic = true;
                    if (ro.arrete) {
                        // La roulette s'arrete : une note, plus aigue pour la suivante.
                        for (var v : box.inv.getViewers()) if (v instanceof Player pl) pl.playSound(pl, Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, ro == objet ? 1.6f : ro == or ? 1.3f : 1.0f);
                    }
                }
                if (clic) for (var v : box.inv.getViewers()) if (v instanceof Player pl) pl.playSound(pl, Sound.UI_BUTTON_CLICK, 0.2f, 1.5f);
                if (diamants.arrete && objet.arrete && or.arrete) { cancel(); devoiler(box); }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    /**
     * La fin de la roulette : les roulettes se vident, il reste ce qui est sur la ligne gagnante (et la fleche d'un arc).
     * Une seconde plus tard (RAMASSAGE), le tout part dans l'inventaire de celui qui l'a ouverte (ramasser).
     */
    private void devoiler(Box box) {
        for (int col : new int[]{DIAMANTS, CENTRE, OR}) { box.inv.setItem(col - 9, null); box.inv.setItem(col + 9, null); }
        for (var e : box.prix.entrySet()) box.inv.setItem(e.getKey(), e.getValue());
        Bukkit.getScheduler().runTaskLater(plugin, () -> ramasser(box), RAMASSAGE);
        Location at = box.block.getLocation().add(0.5, 0.5, 0.5);
        at.getWorld().playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        boolean embleme = box.prix.containsKey(CASE_EMBLEME);
        for (var v : box.inv.getViewers()) {
            if (!(v instanceof Player pl)) continue;
            if (box.tier >= 3) pl.playSound(pl, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1f);
            if (box.tier == 4) pl.playSound(pl, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1f);
            if (embleme) {
                pl.playSound(pl, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.3f);
                Msg.send(pl, "<gold><bold>L'Embleme de Feu brille dans la Lootbox !");
            }
        }
    }

    /**
     * Le butin dans l'inventaire de celui qui a ouvert la Lootbox : l'objet d'abord (il prend la premiere case libre),
     * puis l'Embleme, la fleche, les diamants et l'or ; ce qui n'y tient pas tombe a ses pieds. La Lootbox, videe,
     * disparait. S'il n'est plus la (deconnecte, mort, spectateur, dans un autre monde), elle reste ouverte et chacun
     * peut s'y servir.
     */
    private void ramasser(Box box) {
        if (boxes.get(box.block) != box) return;
        Player p = box.ouvreur == null ? null : Bukkit.getPlayer(box.ouvreur);
        if (p == null || !p.isOnline() || p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR || p.getWorld() != box.block.getWorld()) {
            box.etat = OUVERTE;
            return;
        }
        List<ItemStack> butin = new ArrayList<>();
        List<Integer> cases = new ArrayList<>(List.of(CENTRE, CASE_EMBLEME, 14, DIAMANTS, OR));
        for (int i = 0; i < box.inv.getSize(); i++) if (!cases.contains(i)) cases.add(i);
        for (int i : cases) {
            ItemStack it = box.inv.getItem(i);
            if (it != null && !it.getType().isAir()) butin.add(it);
        }
        box.inv.clear();
        var reste = p.getInventory().addItem(butin.toArray(new ItemStack[0])).values();
        for (ItemStack it : reste) p.getWorld().dropItemNaturally(p.getLocation(), it);
        if (!reste.isEmpty()) Msg.send(p, "<gray>Inventaire plein : le reste du butin tombe a tes pieds.");
        p.playSound(p, Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.1f);
        remove(box);
        eveiller(p);
    }

    /** Pendant la roulette (et la seconde ou l'on voit la ligne gagnante), on ne prend ni ne pose rien. */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Box box && box.etat != OUVERTE) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Box box && box.etat != OUVERTE) e.setCancelled(true);
    }

    /** Videe, la lootbox disparait. */
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Box box) || boxes.get(box.block) != box) return;
        if (box.etat == OUVERTE && e.getInventory().isEmpty()) Bukkit.getScheduler().runTask(plugin, () -> remove(box));
        else box.block.getWorld().playSound(box.block.getLocation().add(0.5, 0.5, 0.5), Sound.BLOCK_CHEST_CLOSE, 0.6f, 1f);
    }

    /** La barriere cassee (en creatif) : la lootbox disparait avec. Les blocs d'un faisceau ne se cassent pas. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (usages.containsKey(e.getBlock())) { e.setCancelled(true); return; }
        Box box = boxes.get(e.getBlock());
        if (box != null) Bukkit.getScheduler().runTask(plugin, () -> remove(box));
    }

    /** Les blocs d'un faisceau ne sautent pas... */
    @EventHandler(ignoreCancelled = true)
    public void onExplode(org.bukkit.event.entity.EntityExplodeEvent e) {
        e.blockList().removeIf(usages::containsKey);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent e) {
        e.blockList().removeIf(usages::containsKey);
    }

    /** ... et ne se poussent ni ne se tirent. */
    @EventHandler(ignoreCancelled = true)
    public void onPistonPush(org.bukkit.event.block.BlockPistonExtendEvent e) {
        if (e.getBlocks().stream().anyMatch(usages::containsKey)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonPull(org.bukkit.event.block.BlockPistonRetractEvent e) {
        if (e.getBlocks().stream().anyMatch(usages::containsKey)) e.setCancelled(true);
    }
}
