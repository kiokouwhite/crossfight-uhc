package com.kiokouwhite.crossfight.game;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.FurnaceStartSmeltEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Les scenarios UHC de confort, a cocher avant la partie (objet "Scenarios" de l'administrateur, ou
 * /cf scenario) : ils raccourcissent la recolte sans changer le combat. Chacun est garde dans config.yml
 * (scenarios.<nom>).
 */
public final class Scenarios implements Listener {

    public enum Scenario {
        CUTCLEAN("cutclean", "CutClean", Material.IRON_INGOT, "Les minerais donnent des lingots et les animaux de la viande cuite"),
        HASTY_BOYS("hastyboys", "Hasty Boys", Material.GOLDEN_PICKAXE, "Les outils fabriques ont Efficacite III et Solidite I"),
        TIMBER("timber", "Timber", Material.OAK_LOG, "Casser une buche fait tomber tout l'arbre"),
        FAST_SMELTING("fastsmelting", "Fast Smelting", Material.FURNACE, "Les fours cuisent 4 fois plus vite"),
        VANILLA_PLUS("vanillaplus", "Vanilla+", Material.FLINT, "Le gravier donne toujours du silex, tous les feuillages donnent des pommes"),
        CAT_EYES("cateyes", "Cat Eyes", Material.ENDER_EYE, "Vision nocturne permanente pendant la partie");

        private final String id, label, help;
        private final Material icon;

        Scenario(String id, String label, Material icon, String help) {
            this.id = id; this.label = label; this.icon = icon; this.help = help;
        }

        public String id() { return id; }
        public String label() { return label; }
        public String help() { return help; }
        public Material icon() { return icon; }

        /** Par son nom, quelle que soit sa graphie ("Hasty Boys", "hasty-boys", "hastyboys"). */
        public static Scenario parse(String raw) {
            String x = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z+]", "").replace("+", "plus");
            for (Scenario s : values()) if (s.id.equals(x)) return s;
            return null;
        }
    }

    /** Ce que le CutClean cuit : minerais bruts et viandes. */
    private static final Map<Material, Material> COOKED = new EnumMap<>(Material.class);
    /** Chance qu'un feuillage casse ou fane donne une pomme, avec Vanilla+. */
    private static final double APPLE_CHANCE = 0.03;
    private static final int TIMBER_MAX = 160, SMELT_SPEED = 4;

    static {
        COOKED.put(Material.RAW_IRON, Material.IRON_INGOT);
        COOKED.put(Material.RAW_GOLD, Material.GOLD_INGOT);
        COOKED.put(Material.RAW_COPPER, Material.COPPER_INGOT);
        COOKED.put(Material.BEEF, Material.COOKED_BEEF);
        COOKED.put(Material.PORKCHOP, Material.COOKED_PORKCHOP);
        COOKED.put(Material.CHICKEN, Material.COOKED_CHICKEN);
        COOKED.put(Material.MUTTON, Material.COOKED_MUTTON);
        COOKED.put(Material.RABBIT, Material.COOKED_RABBIT);
        COOKED.put(Material.COD, Material.COOKED_COD);
        COOKED.put(Material.SALMON, Material.COOKED_SALMON);
    }

    private final CrossfightPlugin plugin;
    /** Ceux a qui Cat Eyes a donne la vision nocturne (pour ne retirer que la sienne). */
    private final Set<UUID> catEyes = new HashSet<>();
    /** Vrai pendant qu'un arbre tombe : ses buches ne relancent pas Timber. */
    private boolean felling;

    public Scenarios(CrossfightPlugin plugin) { this.plugin = plugin; }

    public boolean on(Scenario s) { return plugin.getConfig().getBoolean("scenarios." + s.id(), false); }

    public void set(Scenario s, boolean on) {
        plugin.getConfig().set("scenarios." + s.id(), on);
        plugin.saveConfig();
        if (s == Scenario.CAT_EYES) tickCatEyes();
    }

    /** Les scenarios coches, par leur nom. */
    public List<String> active() {
        List<String> out = new ArrayList<>();
        for (Scenario s : Scenario.values()) if (on(s)) out.add(s.label());
        return out;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickCatEyes, 40L, 60L);
    }

    // ------------------------------------------------------------------ CutClean

    @EventHandler(ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent e) {
        boolean cut = on(Scenario.CUTCLEAN), plus = on(Scenario.VANILLA_PLUS);
        if (!cut && !plus) return;
        for (Item drop : e.getItems()) {
            ItemStack it = drop.getItemStack();
            Material to = cut ? COOKED.get(it.getType()) : null;
            // Vanilla+ : le gravier casse rend toujours du silex.
            if (to == null && plus && it.getType() == Material.GRAVEL && e.getBlockState().getType() == Material.GRAVEL) to = Material.FLINT;
            if (to != null) drop.setItemStack(it.withType(to));
        }
    }

    /** Le lingot sort tout cuit : il rapporte l'experience que le four aurait donnee. */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onOreBreak(BlockBreakEvent e) {
        if (!on(Scenario.CUTCLEAN) || !e.isDropItems() || e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        String n = e.getBlock().getType().name();
        if (!n.endsWith("IRON_ORE") && !n.endsWith("GOLD_ORE") && !n.endsWith("COPPER_ORE")) return;
        if (n.startsWith("NETHER_") || e.getPlayer().getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) return;
        e.setExpToDrop(e.getExpToDrop() + 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onAnimalDeath(EntityDeathEvent e) {
        if (!on(Scenario.CUTCLEAN) || e.getEntity() instanceof Player) return;
        e.getDrops().replaceAll(it -> COOKED.containsKey(it.getType()) ? it.withType(COOKED.get(it.getType())) : it);
    }

    // ------------------------------------------------------------------ Hasty Boys

    /** Apres le controle des armes interdites : l'outil fabrique sort deja enchante. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCraftTool(PrepareItemCraftEvent e) {
        if (!on(Scenario.HASTY_BOYS)) return;
        ItemStack result = e.getInventory().getResult();
        if (result == null) return;
        String n = result.getType().name();
        if (!n.endsWith("_PICKAXE") && !n.endsWith("_AXE") && !n.endsWith("_SHOVEL") && !n.endsWith("_HOE")) return;
        ItemStack tool = result.clone();
        tool.addUnsafeEnchantment(Enchantment.EFFICIENCY, 3);
        tool.addUnsafeEnchantment(Enchantment.UNBREAKING, 1);
        e.getInventory().setResult(tool);
    }

    // ------------------------------------------------------------------ Timber

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLogBreak(BlockBreakEvent e) {
        if (felling || !on(Scenario.TIMBER) || !Tag.LOGS.isTagged(e.getBlock().getType())) return;
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL || p.isSneaking()) return;
        // Toutes les buches qui tiennent a celle-ci, a sa hauteur ou au-dessus.
        Block start = e.getBlock();
        List<Block> logs = new ArrayList<>();
        Set<Block> seen = new HashSet<>();
        ArrayDeque<Block> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start);
        boolean tree = false;
        while (!queue.isEmpty() && logs.size() < TIMBER_MAX) {
            Block b = queue.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                Block o = b.getRelative(dx, dy, dz);
                if (!seen.add(o)) continue;
                if (Tag.LOGS.isTagged(o.getType())) { logs.add(o); queue.add(o); }
                // Un arbre, pas une cabane : il porte du feuillage qui a pousse la.
                else if (o.getBlockData() instanceof Leaves leaves && !leaves.isPersistent()) tree = true;
            }
        }
        if (!tree || logs.isEmpty()) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        felling = true;
        try {
            for (Block log : logs) log.breakNaturally(tool, true);
        } finally {
            felling = false;
        }
    }

    // ------------------------------------------------------------------ Fast Smelting

    @EventHandler(ignoreCancelled = true)
    public void onSmelt(FurnaceStartSmeltEvent e) {
        if (on(Scenario.FAST_SMELTING)) e.setTotalCookTime(Math.max(1, e.getTotalCookTime() / SMELT_SPEED));
    }

    // ------------------------------------------------------------------ Vanilla+

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLeavesBreak(BlockBreakEvent e) {
        if (e.getPlayer().getGameMode() == GameMode.SURVIVAL) apple(e.getBlock());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLeavesDecay(LeavesDecayEvent e) {
        apple(e.getBlock());
    }

    private void apple(Block b) {
        if (!on(Scenario.VANILLA_PLUS) || !(b.getBlockData() instanceof Leaves leaves) || leaves.isPersistent()) return;
        if (ThreadLocalRandom.current().nextDouble() < APPLE_CHANCE) b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), new ItemStack(Material.APPLE));
    }

    // ------------------------------------------------------------------ Cat Eyes

    /** Pendant la partie, chaque joueur en vie voit dans le noir ; hors partie ou scenario decoche, on la retire. */
    private void tickCatEyes() {
        boolean wanted = on(Scenario.CAT_EYES) && plugin.game().isRunning();
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean give = wanted && p.getGameMode() != GameMode.SPECTATOR && !p.isDead();
            if (give) {
                if (!p.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, true, false, false));
                    catEyes.add(p.getUniqueId());
                }
            } else if (catEyes.remove(p.getUniqueId())) p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
    }
}
