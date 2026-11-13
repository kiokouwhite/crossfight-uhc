package com.kiokouwhite.crossfight.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.kiokouwhite.crossfight.CrossfightPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MainHand;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Un joueur "pilote" : un objet qui se fait passer pour un {@link Player} et qui est en realite un
 * mannequin (mode test en troisieme personne). Les capacites, ecrites pour un joueur, tournent ainsi
 * telles quelles sur le mannequin :
 * <ul>
 *   <li>tout ce qui concerne le corps (position, regard, vie, effets, vitesse, attributs, projectiles,
 *       teleportations...) va au mannequin ;</li>
 *   <li>tout ce qu'un joueur voit et entend seul (messages, barre d'action, titres, sons prives, menus,
 *       barres de boss, temps de recharge des objets, barre d'experience) va au testeur qui le pilote ;</li>
 *   <li>l'inventaire est un inventaire a part, dont la main et l'armure s'affichent sur le mannequin.</li>
 * </ul>
 * Le serveur, lui, ne connait pas cet objet : le passer a une de ses methodes qui attend une vraie entite
 * (degats avec une source, son attache a une entite, joueur cache...) le ferait planter. Ces endroits
 * passent par {@link #real(Entity)}.
 */
public final class MannequinPlayer implements InvocationHandler {

    private static final Map<UUID, MannequinPlayer> BY_BODY = new ConcurrentHashMap<>();

    /** Ce qu'un joueur est seul a voir ou entendre : envoye au testeur. */
    private static final Set<String> TO_CONTROLLER = Set.of(
            "sendMessage", "sendRichMessage", "sendPlainMessage", "sendRawMessage", "sendActionBar", "sendTitle", "showTitle",
            "clearTitle", "resetTitle", "sendTitlePart", "showBossBar", "hideBossBar", "playSound", "stopSound", "stopAllSounds",
            "playNote", "playEffect", "spawnParticle", "openInventory", "openWorkbench", "openEnchanting", "openMerchant", "openBook",
            "openSign", "closeInventory", "getOpenInventory", "updateInventory", "setCooldown", "getCooldown", "hasCooldown",
            "sendBlockChange", "sendBlockChanges", "sendMultiBlockChange", "sendBlockDamage", "sendEquipmentChange", "sendSignChange",
            "sendPotionEffectChange", "sendPotionEffectChangeRemove", "sendExperienceChange", "hideEntity", "showEntity",
            "hidePlayer", "showPlayer", "canSee", "setExp", "getExp", "setLevel", "getLevel", "giveExp", "giveExpLevels",
            "getTotalExperience", "setTotalExperience", "setExperienceLevelAndProgress", "getClientOption", "getProtocolVersion",
            "getClientBrandName", "sendHurtAnimation");

    private final Mannequin body;
    private final UUID controller;
    private final Player proxy;
    private final Inventory storage;
    private final PlayerInventory inventory;
    private Inventory enderChest;
    private int held;
    private boolean allowFlight, flying;
    private String name;

    private MannequinPlayer(Mannequin body, Player controller, String name) {
        this.body = body;
        this.controller = controller.getUniqueId();
        this.name = name;
        this.proxy = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, this);
        // Un inventaire de joueur : 36 cases, l'armure (36 a 39) et la main gauche (40).
        Inventory inv;
        try {
            inv = Bukkit.createInventory(null, InventoryType.PLAYER);
        } catch (Exception ex) {
            inv = Bukkit.createInventory(null, 45);
        }
        this.storage = inv;
        this.inventory = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(), new Class<?>[]{PlayerInventory.class}, new InventoryHandler());
    }

    /**
     * Fait d'un mannequin un joueur pilote par {@code controller}.
     * @param name le nom sous lequel il apparait (messages "X utilise Y")
     */
    public static Player create(Mannequin body, Player controller, String name) {
        // Les attributs d'un joueur que le mannequin n'a pas (degats, portee...) : certaines capacites les modifient.
        for (Attribute a : new Attribute[]{Attribute.ATTACK_DAMAGE, Attribute.ATTACK_SPEED, Attribute.ENTITY_INTERACTION_RANGE,
                Attribute.BLOCK_INTERACTION_RANGE, Attribute.LUCK, Attribute.BLOCK_BREAK_SPEED, Attribute.SNEAKING_SPEED,
                Attribute.MINING_EFFICIENCY, Attribute.SUBMERGED_MINING_SPEED, Attribute.SWEEPING_DAMAGE_RATIO}) {
            try {
                if (body.getAttribute(a) == null) body.registerAttribute(a);
            } catch (Exception ignored) { }
        }
        var dmg = body.getAttribute(Attribute.ATTACK_DAMAGE);
        if (dmg != null) dmg.setBaseValue(1.0);
        MannequinPlayer h = new MannequinPlayer(body, controller, name);
        BY_BODY.put(body.getUniqueId(), h);
        return h.proxy;
    }

    /** Le pilote n'existe plus (fin du mode troisieme personne). */
    public static void release(Player proxy) {
        MannequinPlayer h = handler(proxy);
        if (h != null) BY_BODY.remove(h.body.getUniqueId(), h);
    }

    // ------------------------------------------------------------ recherche

    private static MannequinPlayer handler(Object o) {
        if (o == null || !Proxy.isProxyClass(o.getClass())) return null;
        return Proxy.getInvocationHandler(o) instanceof MannequinPlayer h ? h : null;
    }

    /** Vrai pour un joueur pilote (un mannequin). */
    public static boolean isProxy(Object o) { return handler(o) != null; }

    /** La vraie entite derriere un joueur pilote : son mannequin. Toute autre entite est rendue telle quelle. */
    public static Entity real(Entity e) {
        MannequinPlayer h = handler(e);
        return h == null ? e : h.body;
    }

    public static LivingEntity real(LivingEntity e) {
        MannequinPlayer h = handler(e);
        return h == null ? e : h.body;
    }

    /** Le joueur pilote d'un mannequin (le corps), ou null si cette entite n'en est pas un. */
    public static Player of(Entity e) {
        if (e == null) return null;
        if (isProxy(e)) return (Player) e;
        MannequinPlayer h = BY_BODY.get(e.getUniqueId());
        return h != null && h.body == e ? h.proxy : null;
    }

    /** Vrai si ce mannequin est le corps d'un joueur pilote. */
    public static boolean isBody(Entity e) {
        return e != null && !isProxy(e) && BY_BODY.containsKey(e.getUniqueId());
    }

    /** Comme Bukkit.getPlayer, mais trouve aussi les joueurs pilotes. */
    public static Player getPlayer(UUID uuid) {
        if (uuid == null) return null;
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p;
        MannequinPlayer h = BY_BODY.get(uuid);
        return h == null ? null : h.proxy;
    }

    /** Les joueurs pilotes dont le mannequin existe encore. */
    public static List<Player> all() {
        List<Player> out = new ArrayList<>();
        for (MannequinPlayer h : BY_BODY.values()) if (h.body.isValid()) out.add(h.proxy);
        return out;
    }

    /** Le testeur qui pilote ce joueur (ou ce mannequin), ou null. */
    public static UUID controllerId(Entity e) {
        MannequinPlayer h = handler(e);
        if (h == null && e != null) h = BY_BODY.get(e.getUniqueId());
        return h == null ? null : h.controller;
    }

    public static Mannequin body(Player proxy) {
        MannequinPlayer h = handler(proxy);
        return h == null ? null : h.body;
    }

    /** Vrai si les deux designent la meme entite (un joueur pilote et son mannequin, par exemple). */
    public static boolean same(Entity a, Entity b) {
        return a != null && b != null && real(a) == real(b);
    }

    /** La case tenue en main par le pilote (celle que le testeur tient). */
    public static void setHeldSlot(Player proxy, int slot) {
        MannequinPlayer h = handler(proxy);
        if (h != null && slot >= 0 && slot < 9 && slot != h.held) { h.held = slot; h.syncEquipment(); }
    }

    public static void rename(Player proxy, String name) {
        MannequinPlayer h = handler(proxy);
        if (h != null) h.name = name;
    }

    /**
     * Le tireur d'un projectile. Pour un joueur pilote, le serveur garde son mannequin comme proprietaire
     * (la fleche ne le touche pas au depart), mais l'API renvoie le joueur pilote : les capacites qui
     * reconnaissent "leurs" fleches par getShooter() fonctionnent.
     */
    public static void setShooter(Projectile proj, ProjectileSource shooter) {
        MannequinPlayer h = handler(shooter);
        if (h == null) { proj.setShooter(shooter); return; }
        h.adopt(proj);
    }

    private void adopt(Projectile proj) {
        proj.setShooter(body);
        try {
            Object handle = proj.getClass().getMethod("getHandle").invoke(proj);
            java.lang.reflect.Field f = null;
            for (Class<?> c = handle.getClass(); c != null && f == null; c = c.getSuperclass()) {
                try { f = c.getDeclaredField("projectileSource"); } catch (NoSuchFieldException ignored) { }
            }
            if (f == null) throw new NoSuchFieldException("projectileSource");
            f.setAccessible(true);
            f.set(handle, proxy);
        } catch (Throwable t) {
            // Sans acces aux classes du serveur : le projectile n'a plus de proprietaire, mais il reste a nous.
            proj.setShooter(proxy);
        }
    }

    // --------------------------------------------------------------- appels

    private Player controller() {
        return Bukkit.getPlayer(controller);
    }

    @Override
    public Object invoke(Object self, Method m, Object[] args) throws Throwable {
        String n = m.getName();
        int argc = args == null ? 0 : args.length;
        switch (n) {
            case "equals" -> { if (argc == 1) return args[0] == self; }
            case "hashCode" -> { if (argc == 0) return System.identityHashCode(self); }
            case "toString" -> { if (argc == 0) return "MannequinPlayer[" + name + "]"; }
            default -> { }
        }
        Player tester = controller();
        if (TO_CONTROLLER.contains(n)) {
            if (tester == null) return defaultValue(m.getReturnType());
            if (n.equals("sendMessage") && argc == 1 && args[0] instanceof Component c) {
                tester.sendMessage(Msg.mm("<dark_aqua>[Mannequin]</dark_aqua> ").append(c));
                return null;
            }
            if (n.equals("sendMessage") && argc == 1 && args[0] instanceof String s) {
                tester.sendMessage(Msg.mm("<dark_aqua>[Mannequin]</dark_aqua> ").append(Component.text(s)));
                return null;
            }
            return call(tester, m, args);
        }
        switch (n) {
            case "getInventory" -> { if (argc == 0) return inventory; }
            case "getEnderChest" -> {
                if (enderChest == null) enderChest = Bukkit.createInventory(null, InventoryType.ENDER_CHEST);
                return enderChest;
            }
            case "getName", "getDisplayName", "getPlayerListName" -> { if (argc == 0) return name; }
            case "displayName", "playerListName", "teamDisplayName" -> { if (argc == 0) return Component.text(name); }
            case "isOnline", "isConnected" -> { return body.isValid() && tester != null; }
            case "getPlayer" -> { return self; }
            case "isSneaking" -> { return tester != null && tester.isSneaking(); }
            case "isSprinting", "isBlocking", "isHandRaised", "isSleeping", "isDeeplySleeping", "isOp", "isBanned", "isFlyingFallDamage",
                 "isWhitelisted", "isHealthScaled" -> { if (argc == 0) return false; }
            case "getGameMode" -> { return GameMode.SURVIVAL; }
            case "setGameMode" -> { return null; }
            case "getAllowFlight" -> { return allowFlight; }
            case "setAllowFlight" -> { allowFlight = (Boolean) args[0]; if (!allowFlight) setFlying(false); return null; }
            case "isFlying" -> { return flying; }
            case "setFlying" -> { setFlying((Boolean) args[0]); return null; }
            case "getFoodLevel" -> { return 20; }
            case "getSaturation" -> { return 20f; }
            case "getWalkSpeed" -> { return 0.2f; }
            case "getFlySpeed" -> { return 0.1f; }
            case "getAttackCooldown" -> { return 1f; }
            case "getMainHand" -> { return MainHand.RIGHT; }
            case "getHandRaised" -> { return EquipmentSlot.HAND; }
            case "getLocale" -> { return "fr_fr"; }
            case "locale" -> { return java.util.Locale.FRANCE; }
            case "getItemOnCursor" -> { return ItemStack.empty(); }
            case "getItemInHand" -> { if (argc == 0) return inventory.getItemInMainHand(); }
            case "hasPlayedBefore" -> { return true; }
            case "getPlayerProfile" -> { return profile(); }
            case "setPlayerProfile" -> { applyProfile((PlayerProfile) args[0]); return null; }
            case "getScoreboard" -> { return Bukkit.getScoreboardManager().getMainScoreboard(); }
            case "launchProjectile" -> {
                Object proj = call(body, m, args);
                if (proj instanceof Projectile p) adopt(p);
                return proj;
            }
            // Le corps du mannequin ne disparait pas et ne meurt pas sur ordre d'une capacite.
            case "remove", "kick", "chat", "performCommand", "setResourcePack", "addResourcePack", "setResourcePacks", "removeResourcePacks",
                 "setPlayerListName", "setPlayerListHeader", "setPlayerListFooter", "setPlayerListHeaderFooter", "setDisplayName",
                 "setFoodLevel", "setSaturation", "setExhaustion", "setWalkSpeed", "setFlySpeed", "setItemOnCursor", "setSpectatorTarget",
                 "setCompassTarget", "setBedSpawnLocation", "setRespawnLocation", "setOp", "setWhitelisted", "setHealthScaled",
                 "setHealthScale", "setPlayerTime", "resetPlayerTime", "setPlayerWeather", "resetPlayerWeather", "setStatistic",
                 "incrementStatistic", "decrementStatistic", "setSleepingIgnored", "setWorldBorder", "sendOpLevel", "transfer",
                 "saveData", "loadData", "updateCommands", "setSendViewDistance", "setViewDistance", "setSimulationDistance",
                 "lookAt", "showDemoScreen", "showWinScreen", "showElderGuardian" -> { return defaultValue(m.getReturnType()); }
            case "setHealth" -> {
                // Une capacite qui "tue" son lanceur laisse le mannequin a un souffle de vie.
                double v = (Double) args[0];
                var mh = body.getAttribute(Attribute.MAX_HEALTH);
                body.setHealth(Math.max(1.0, Math.min(mh == null ? 20 : mh.getValue(), v)));
                return null;
            }
            case "spigot" -> { return null; }
            default -> { }
        }
        if (m.getDeclaringClass().isInstance(body)) return call(body, m, args);
        // Une methode redeclaree par Player ou HumanEntity mais qui existe aussi pour une entite (getEquipment...).
        Method bm = bodyMethod(m);
        if (bm != null) return call(body, bm, args);
        return defaultValue(m.getReturnType());
    }

    private static final Map<Method, Optional<Method>> BODY_METHODS = new ConcurrentHashMap<>();

    private static Method bodyMethod(Method m) {
        return BODY_METHODS.computeIfAbsent(m, k -> {
            try {
                Method bm = Mannequin.class.getMethod(k.getName(), k.getParameterTypes());
                return k.getReturnType().isAssignableFrom(bm.getReturnType()) ? Optional.of(bm) : Optional.empty();
            } catch (NoSuchMethodException e) {
                return Optional.empty();
            }
        }).orElse(null);
    }

    /** Appelle une methode sur une vraie entite, en remplacant les joueurs pilotes passes en argument par leur mannequin. */
    private static Object call(Object target, Method m, Object[] args) throws Throwable {
        Object[] a = args;
        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                MannequinPlayer h = handler(args[i]);
                if (h != null) {
                    if (a == args) a = args.clone();
                    a[i] = h.body;
                }
            }
        }
        try {
            return m.invoke(target, a);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static Object defaultValue(Class<?> t) {
        if (t == void.class) return null;
        if (t == boolean.class) return false;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == double.class) return 0.0;
        if (t == float.class) return 0f;
        if (t == short.class) return (short) 0;
        if (t == byte.class) return (byte) 0;
        if (t == char.class) return (char) 0;
        if (t == Optional.class) return Optional.empty();
        if (t == String.class) return "";
        if (t == Component.class) return Component.empty();
        if (t == Set.class) return Set.of();
        if (t == List.class || t == Collection.class || t == Iterable.class) return List.of();
        if (t == Map.class) return Map.of();
        if (t.isArray()) return java.lang.reflect.Array.newInstance(t.getComponentType(), 0);
        return null;
    }

    private void setFlying(boolean f) {
        flying = f && allowFlight;
        body.setGravity(!flying);
        if (flying) body.setVelocity(new Vector(0, 0, 0));
    }

    private PlayerProfile profile() {
        String n = name.replaceAll("[^A-Za-z0-9_]", "");
        if (n.isEmpty()) n = "Mannequin";
        if (n.length() > 16) n = n.substring(0, 16);
        PlayerProfile pp = Bukkit.createProfile(body.getUniqueId(), n);
        try {
            for (ProfileProperty prop : body.getProfile().properties()) pp.setProperty(prop);
        } catch (Exception ignored) { }
        return pp;
    }

    private void applyProfile(PlayerProfile pp) {
        try {
            body.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile(pp));
        } catch (Exception ex) {
            CrossfightPlugin.get().getLogger().warning("Skin du mannequin pilote : " + ex);
        }
    }

    // ------------------------------------------------------------ inventaire

    /** Montre sur le mannequin ce que le pilote tient et porte. */
    private void syncEquipment() {
        var eq = body.getEquipment();
        if (eq == null) return;
        eq.setItemInMainHand(storage.getItem(held));
        eq.setItemInOffHand(storage.getItem(40));
        eq.setBoots(storage.getItem(36));
        eq.setLeggings(storage.getItem(37));
        eq.setChestplate(storage.getItem(38));
        eq.setHelmet(storage.getItem(39));
    }

    /** L'inventaire du pilote : un vrai inventaire de 41 cases, avec les notions propres a un joueur par-dessus. */
    private final class InventoryHandler implements InvocationHandler {
        @Override
        public Object invoke(Object self, Method m, Object[] args) throws Throwable {
            String n = m.getName();
            int argc = args == null ? 0 : args.length;
            switch (n) {
                case "equals" -> { if (argc == 1) return args[0] == self; }
                case "hashCode" -> { if (argc == 0) return System.identityHashCode(self); }
                case "toString" -> { if (argc == 0) return "MannequinInventory[" + name + "]"; }
                case "getHolder" -> { return proxy; }
                case "getHeldItemSlot" -> { return held; }
                case "setHeldItemSlot" -> { int s = (Integer) args[0]; if (s >= 0 && s < 9) { held = s; syncEquipment(); } return null; }
                case "getItemInMainHand", "getItemInHand" -> { return orAir(storage.getItem(held)); }
                case "setItemInMainHand", "setItemInHand" -> { storage.setItem(held, (ItemStack) args[0]); syncEquipment(); return null; }
                case "getItemInOffHand" -> { return orAir(storage.getItem(40)); }
                case "setItemInOffHand" -> { storage.setItem(40, (ItemStack) args[0]); syncEquipment(); return null; }
                case "getHelmet" -> { return storage.getItem(39); }
                case "getChestplate" -> { return storage.getItem(38); }
                case "getLeggings" -> { return storage.getItem(37); }
                case "getBoots" -> { return storage.getItem(36); }
                case "setHelmet" -> { storage.setItem(39, (ItemStack) args[0]); syncEquipment(); return null; }
                case "setChestplate" -> { storage.setItem(38, (ItemStack) args[0]); syncEquipment(); return null; }
                case "setLeggings" -> { storage.setItem(37, (ItemStack) args[0]); syncEquipment(); return null; }
                case "setBoots" -> { storage.setItem(36, (ItemStack) args[0]); syncEquipment(); return null; }
                case "getArmorContents" -> { return new ItemStack[]{storage.getItem(36), storage.getItem(37), storage.getItem(38), storage.getItem(39)}; }
                case "setArmorContents" -> {
                    ItemStack[] a = (ItemStack[]) args[0];
                    for (int i = 0; i < 4; i++) storage.setItem(36 + i, a != null && i < a.length ? a[i] : null);
                    syncEquipment();
                    return null;
                }
                case "getExtraContents" -> { return new ItemStack[]{storage.getItem(40)}; }
                case "setExtraContents" -> {
                    ItemStack[] a = (ItemStack[]) args[0];
                    storage.setItem(40, a != null && a.length > 0 ? a[0] : null);
                    syncEquipment();
                    return null;
                }
                case "getStorageContents" -> {
                    ItemStack[] out = new ItemStack[36];
                    for (int i = 0; i < 36; i++) out[i] = storage.getItem(i);
                    return out;
                }
                case "setStorageContents" -> {
                    ItemStack[] a = (ItemStack[]) args[0];
                    for (int i = 0; i < 36; i++) storage.setItem(i, a != null && i < a.length ? a[i] : null);
                    syncEquipment();
                    return null;
                }
                case "getItem" -> {
                    if (argc == 1 && args[0] instanceof EquipmentSlot s) return orAir(storage.getItem(slotOf(s)));
                }
                case "setItem" -> {
                    if (argc == 2 && args[0] instanceof EquipmentSlot s) { storage.setItem(slotOf(s), (ItemStack) args[1]); syncEquipment(); return null; }
                }
                default -> { }
            }
            Method target = m;
            if (!m.getDeclaringClass().isInstance(storage)) {
                // Une methode d'Inventory redeclaree par PlayerInventory (setItem, getItem...) : celle d'Inventory.
                try {
                    target = Inventory.class.getMethod(n, m.getParameterTypes());
                } catch (NoSuchMethodException ex) {
                    return defaultValue(m.getReturnType());
                }
            }
            Object out = call(storage, target, args);
            if (!n.startsWith("get") && !n.startsWith("contains") && !n.startsWith("first") && !n.startsWith("all")
                    && !n.equals("isEmpty") && !n.equals("iterator")) syncEquipment();
            return out;
        }

        private int slotOf(EquipmentSlot s) {
            return switch (s) {
                case HAND -> held;
                case OFF_HAND -> 40;
                case FEET -> 36;
                case LEGS -> 37;
                case CHEST -> 38;
                case HEAD -> 39;
                default -> 40;
            };
        }

        private ItemStack orAir(ItemStack it) {
            return it == null ? new ItemStack(Material.AIR) : it;
        }
    }

    /** Les objets du pilote, case par case (0 a 35), pour les recopier chez le testeur. */
    public static ItemStack[] contents(Player proxy) {
        MannequinPlayer h = handler(proxy);
        ItemStack[] out = new ItemStack[36];
        if (h != null) for (int i = 0; i < 36; i++) out[i] = h.storage.getItem(i);
        return out;
    }

    /** Vide l'inventaire du pilote. */
    public static void clearInventory(Player proxy) {
        MannequinPlayer h = handler(proxy);
        if (h == null) return;
        h.storage.clear();
        h.syncEquipment();
    }
}
