package com.kiokouwhite.crossfight.ability;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Coeur du systeme de capacites : donne les items, ecoute les clics, fait
 * avancer les temps de chargement et les barres, et affiche l'etat au joueur.
 */
public final class AbilityEngine implements Listener {

    public static final NamespacedKey ABILITY_KEY = new NamespacedKey("crossfight", "ability");
    public static final NamespacedKey WEAPON_KEY = new NamespacedKey("crossfight", "weapon");

    private final CrossfightPlugin plugin;
    /** Etat par joueur puis par identifiant de capacite. */
    private final Map<UUID, Map<String, AbilityState>> states = new HashMap<>();
    private BukkitTask tickTask;

    public AbilityEngine(CrossfightPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 20L, 20L);
    }

    public void stop() {
        if (tickTask != null) tickTask.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) unequip(p);
        states.clear();
    }

    // ------------------------------------------------------------------ etat

    public AbilityState state(Player p, Ability ability) {
        return states.computeIfAbsent(p.getUniqueId(), u -> new HashMap<>())
                .computeIfAbsent(ability.id(), k -> new AbilityState());
    }

    public void clearStates(Player p) {
        states.remove(p.getUniqueId());
    }

    // ------------------------------------------------------------ equipement

    /** Donne au joueur son arme et ses items de capacite, et declenche les passifs. */
    public void equip(Player p) {
        CfPlayer cf = plugin.players().get(p);
        Character ch = cf.character();
        if (ch == null) return;

        PlayerInventory inv = p.getInventory();
        ItemStack weapon = ch.createWeapon();
        if (weapon != null) {
            Items.tag(weapon, WEAPON_KEY, ch.id());
            inv.setItem(0, weapon);
        }
        int slot = 1;
        for (Ability a : ch.abilities()) {
            if (a.hasItem()) {
                inv.setItem(slot++, a.createItem());
            }
        }
        if (ch.ultimate() != null) {
            inv.setItem(8, ch.ultimate().createItem());
        }
        for (Ability a : ch.allAbilities()) {
            a.onEquip(p, cf);
        }
    }

    /** Retire les items de capacite et appelle les hooks de fin. */
    public void unequip(Player p) {
        CfPlayer cf = plugin.players().get(p);
        Character ch = cf.character();
        if (ch != null) {
            for (Ability a : ch.allAbilities()) {
                AbilityState st = state(p, a);
                if (a instanceof ToggleAbility t && st.isEnabled()) {
                    st.setEnabled(false);
                    t.onDisable(p, cf, st);
                }
                a.onUnequip(p, cf);
            }
        }
        removeAbilityItems(p.getInventory());
    }

    public static void removeAbilityItems(PlayerInventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (isAbilityItem(it) || isWeaponItem(it)) inv.setItem(i, null);
        }
    }

    public static boolean isAbilityItem(ItemStack it) {
        return Items.tag(it, ABILITY_KEY) != null;
    }

    public static boolean isWeaponItem(ItemStack it) {
        return Items.tag(it, WEAPON_KEY) != null;
    }

    // ------------------------------------------------------------------ tick

    private void tickAll() {
        if (!plugin.game().isRunning()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            CfPlayer cf = plugin.players().get(p);
            Character ch = cf.character();
            if (ch == null || cf.isWaitingRespawn() || !cf.isAlive()) continue;
            StringBuilder bar = new StringBuilder();
            for (Ability a : ch.allAbilities()) {
                AbilityState st = state(p, a);
                try {
                    a.tick(p, cf, st);
                    if (a instanceof ToggleAbility t) tickToggle(p, cf, t, st);
                } catch (Exception ex) {
                    plugin.getLogger().warning("Erreur dans la capacite " + a.id() + " : " + ex);
                }
                appendBar(bar, a, st, cf);
            }
            if (!bar.isEmpty()) Msg.actionBar(p, bar.toString());
        }
    }

    private void tickToggle(Player p, CfPlayer cf, ToggleAbility t, AbilityState st) {
        if (st.isEnabled()) {
            st.setGauge(st.gauge() - t.drainPerSecond());
            t.tickActive(p, cf, st);
            if (st.gauge() <= 0) {
                st.setEnabled(false);
                t.onDisable(p, cf, st);
                Msg.send(p, "<red>" + t.name() + " s'est desactivee, barre vide.");
            }
        } else {
            st.setGauge(st.gauge() + t.regenPerSecond());
        }
    }

    private void appendBar(StringBuilder bar, Ability a, AbilityState st, CfPlayer cf) {
        String piece;
        switch (a.type()) {
            case ACTIVE -> piece = st.onCooldown()
                    ? "<gray>" + a.name() + " <red>" + st.cooldownLeftSeconds() + "s"
                    : "<green>" + a.name() + " ✔";
            case TOGGLE -> piece = (st.isEnabled() ? "<aqua>" : "<gray>") + a.name() + " " + (int) st.gauge() + "%";
            case SWITCH -> piece = "<yellow>" + a.name() + " <white>[" + ((SwitchAbility) a).modeName(st.mode()) + "]";
            case ULTIMATE -> piece = cf.isUltimateReady()
                    ? "<light_purple><bold>ULTIME PRET</bold>"
                    : "<light_purple>Ultime " + (int) cf.ultimateCharge() + "%";
            default -> { return; }
        }
        if (!bar.isEmpty()) bar.append(" <dark_gray>| ");
        bar.append(piece);
    }

    // ----------------------------------------------------------- activation

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        String id = Items.tag(item, ABILITY_KEY);
        if (id == null) return;
        e.setCancelled(true);

        Player p = e.getPlayer();
        if (!plugin.game().isRunning()) {
            Msg.send(p, "<red>La partie n'a pas commence.");
            return;
        }
        CfPlayer cf = plugin.players().get(p);
        Character ch = cf.character();
        if (ch == null) return;
        Ability a = ch.ability(id);
        if (a == null) return;
        use(p, cf, a);
    }

    public void use(Player p, CfPlayer cf, Ability a) {
        AbilityState st = state(p, a);
        switch (a) {
            case ActiveAbility active -> {
                if (st.onCooldown()) {
                    Msg.actionBar(p, "<red>" + a.name() + " : encore " + st.cooldownLeftSeconds() + "s");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
                    return;
                }
                boolean ok = active.activate(p, cf, st);
                if (ok) {
                    st.startCooldown(active.cooldownSeconds());
                    p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
                }
            }
            case ToggleAbility toggle -> {
                if (st.isEnabled()) {
                    st.setEnabled(false);
                    toggle.onDisable(p, cf, st);
                    Msg.actionBar(p, "<gray>" + a.name() + " desactivee");
                } else {
                    if (st.gauge() < 5) {
                        Msg.actionBar(p, "<red>" + a.name() + " : barre trop faible");
                        return;
                    }
                    if (toggle.onEnable(p, cf, st)) {
                        st.setEnabled(true);
                        Msg.actionBar(p, "<aqua>" + a.name() + " activee");
                    }
                }
            }
            case SwitchAbility sw -> {
                int old = st.mode();
                int next = Math.floorMod(old + (p.isSneaking() ? -1 : 1), sw.modes().size());
                st.setMode(next);
                sw.onSwitch(p, cf, st, old, next);
                Msg.actionBar(p, "<yellow>" + a.name() + " : <white>" + sw.modeName(next));
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            }
            case UltimateAbility ult -> {
                if (!cf.isUltimateReady()) {
                    Msg.actionBar(p, "<light_purple>Ultime a " + (int) cf.ultimateCharge() + "%");
                    return;
                }
                if (ult.cast(p, cf, st)) {
                    cf.resetUltimate();
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.6f);
                    plugin.game().broadcastNear(p, "<light_purple>" + p.getName() + " utilise <bold>" + ult.name() + "</bold> !");
                }
            }
            default -> { }
        }
    }

    // --------------------------------------------------------- protections

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        ItemStack it = e.getItemDrop().getItemStack();
        if (isAbilityItem(it) || isWeaponItem(it)) e.setCancelled(true);
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (isAbilityItem(e.getOffHandItem())) e.setCancelled(true);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();
        boolean involved = isAbilityItem(current) || isAbilityItem(cursor) || isWeaponItem(current) || isWeaponItem(cursor);
        if (!involved) return;
        // Interdit de sortir un item de capacite de son propre inventaire (coffre, etc.)
        if (e.getClickedInventory() != null && e.getClickedInventory() != e.getWhoClicked().getInventory()) {
            e.setCancelled(true);
        } else if (e.isShiftClick() && e.getView().getTopInventory().getSize() > 5) {
            e.setCancelled(true);
        }
    }

    public List<Ability> abilitiesOf(Player p) {
        Character ch = plugin.players().get(p).character();
        return ch == null ? List.of() : ch.allAbilities();
    }
}
