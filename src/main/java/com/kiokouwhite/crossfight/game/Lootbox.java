package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Fx;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.TempBlocks;
import com.kiokouwhite.crossfight.util.Weapons;
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
import org.bukkit.block.Chest;
import org.bukkit.entity.Arrow;
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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * L'evenement Lootbox : a sa minute (20 par defaut), des coffres apparaissent au hasard sur la map. Chacun a un
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
    /** Les lootbox posees : leur bloc, leur tier. */
    private final Map<Block, Integer> boxes = new HashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final java.util.Set<Block> opened = new java.util.HashSet<>();

    public Lootbox(CrossfightPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "loot");
        this.usesKey = new NamespacedKey(plugin, "loot_uses");
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

    private int rollTier() {
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

    /** Pose une lootbox de ce tier (au hasard si 0) a cet endroit, avec son contenu et sa colonne de lumiere. */
    public boolean place(Location at, int tier) {
        if (tier < 1 || tier > 4) tier = rollTier();
        Block b = at.getBlock();
        if (!b.getType().isAir() && !b.isReplaceable()) b = b.getRelative(BlockFace.UP);
        b.setType(Material.CHEST);
        if (!(b.getState() instanceof Chest chest)) return false;
        chest.customName(Msg.mm("<" + TIER_COLOR[tier - 1] + ">Lootbox tier " + tier));
        chest.update();
        var inv = ((Chest) b.getState()).getBlockInventory();
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Loot> pool = new ArrayList<>();
        for (Loot l : LOOTS.values()) if (l.tier() == tier) pool.add(l);
        Loot loot = pool.get(r.nextInt(pool.size()));
        inv.setItem(11, new ItemStack(Material.DIAMOND, r.nextInt(2, 9)));
        inv.setItem(13, item(loot.id()));
        inv.setItem(15, new ItemStack(Material.GOLD_INGOT, r.nextInt(6, 13)));
        // Un arc a Infinite a besoin d'une fleche dans l'inventaire.
        if (loot.kind() == Kind.BOW && !loot.id().equals("stellaire")) inv.setItem(22, new ItemStack(Material.ARROW));
        boxes.put(b, tier);
        beam(b, tier);
        final Block box = b;
        Bukkit.getScheduler().runTaskLater(plugin, () -> remove(box), BOX_LIFE);
        return true;
    }

    /** Le contenu d'une lootbox de ce tier, donne directement a un joueur (recompense de Regna Ferox). */
    public void reward(Player p, int tier) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<Loot> pool = new ArrayList<>();
        for (Loot l : LOOTS.values()) if (l.tier() == tier) pool.add(l);
        List<ItemStack> gifts = new ArrayList<>(List.of(new ItemStack(Material.DIAMOND, r.nextInt(2, 9)), new ItemStack(Material.GOLD_INGOT, r.nextInt(6, 13)), item(pool.get(r.nextInt(pool.size())).id())));
        for (ItemStack rest : p.getInventory().addItem(gifts.toArray(new ItemStack[0])).values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
    }

    /** La colonne de lumiere qui signale une lootbox de loin, a la couleur de son tier. */
    private void beam(Block b, int tier) {
        Particle.DustOptions dust = new Particle.DustOptions(TIER_BEAM[tier - 1], 2.2f);
        new BukkitRunnable() {
            @Override public void run() {
                if (!boxes.containsKey(b) || b.getType() != Material.CHEST) { boxes.remove(b); cancel(); return; }
                Location base = b.getLocation().add(0.5, 1, 0.5);
                for (int y = 0; y < 40; y += 2) b.getWorld().spawnParticle(Particle.DUST, base.clone().add(0, y, 0), 1, 0.05, 0.3, 0.05, 0, dust, true);
            }
        }.runTaskTimer(plugin, 0L, 8L);
    }

    private void remove(Block b) {
        opened.remove(b);
        if (boxes.remove(b) == null || b.getType() != Material.CHEST) return;
        if (b.getState() instanceof Chest chest) chest.getBlockInventory().clear();
        b.setType(Material.AIR);
    }

    /** Fin de partie : plus de lootbox ni de temps de recharge. */
    public void clear() {
        for (Block b : new ArrayList<>(boxes.keySet())) remove(b);
        cooldowns.clear();
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getInventory().getHolder() instanceof Chest chest) || !(e.getPlayer() instanceof Player p)) return;
        Integer tier = boxes.get(chest.getBlock());
        if (tier == null) return;
        // La premiere ouverture est annoncee.
        if (!opened.add(chest.getBlock())) return;
        Msg.broadcast("<gold>" + p.getName() + " ouvre une <" + TIER_COLOR[tier - 1] + ">Lootbox tier " + tier + "</" + TIER_COLOR[tier - 1] + "> !");
        p.getWorld().playSound(chest.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    /** Videe, la lootbox disparait. */
    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Chest chest) || !boxes.containsKey(chest.getBlock())) return;
        if (e.getInventory().isEmpty()) Bukkit.getScheduler().runTask(plugin, () -> remove(chest.getBlock()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        boxes.remove(e.getBlock());
    }
}
