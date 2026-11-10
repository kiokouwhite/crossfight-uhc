package com.kiokouwhite.crossfight.ability;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Boosts;
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
            decorateWeapon(weapon, ch);
            inv.setItem(0, weapon);
        }
        int slot = 1;
        for (Ability a : ch.abilities()) {
            if (!a.hasItem()) continue;
            if (slot == 8) slot = 9; // le slot 8 est reserve a l'ultime
            inv.setItem(slot++, a.createItem());
        }
        if (ch.ultimate() != null) {
            inv.setItem(8, ch.ultimate().createItem());
        }
        for (Ability a : ch.allAbilities()) {
            a.onEquip(p, cf);
        }
        if (ch.licence() != null && ch.licence().mechanic() != null) ch.licence().mechanic().onEquip(p);
        Boosts.apply(p);
        plugin.legacyCombat().apply(p);
    }

    /** Tague l'arme et y ajoute la description des capacites qui lui sont liees. */
    public static ItemStack decorateWeapon(ItemStack weapon, Character ch) {
        Items.tag(weapon, WEAPON_KEY, ch.id());
        // La premiere capacite non passive portee par l'arme repond au clic droit sur celle-ci.
        for (Ability a : ch.allAbilities()) {
            if (a.isWeaponBound() && a.type() != AbilityType.PASSIVE) { Items.tag(weapon, ABILITY_KEY, a.id()); break; }
        }
        var meta = weapon.getItemMeta();
        List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(meta.lore());
        for (Ability a : ch.allAbilities()) {
            if (!a.isWeaponBound()) continue;
            for (String line : a.weaponLore()) lore.add(Msg.mm("<!italic>" + line));
        }
        meta.lore(lore);
        weapon.setItemMeta(meta);
        return weapon;
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
            if (ch.licence() != null && ch.licence().mechanic() != null) ch.licence().mechanic().onUnequip(p);
        }
        removeAbilityItems(p.getInventory());
        com.kiokouwhite.crossfight.util.Stealth.revealAll(p);
    }

    // ------------------------------------------------------------ dispatch

    /** Degats joueur contre joueur : previent les capacites de l'attaquant puis de la victime. */
    public void dispatchDamage(Player attacker, org.bukkit.entity.LivingEntity victim, org.bukkit.event.entity.EntityDamageByEntityEvent e) {
        CfPlayer acf = plugin.players().get(attacker);
        if (acf.character() != null) {
            for (Ability a : acf.character().allAbilities()) {
                try { a.onDealDamage(attacker, victim, e, state(attacker, a)); }
                catch (Exception ex) { plugin.getLogger().warning("onDealDamage " + a.id() + " : " + ex); }
            }
        }
    }

    /** Degats recus par un joueur, toute source. */
    public void dispatchDamaged(Player victim, org.bukkit.event.entity.EntityDamageEvent e) {
        CfPlayer vcf = plugin.players().get(victim);
        if (vcf.character() != null) {
            for (Ability a : vcf.character().allAbilities()) {
                try { a.onDamaged(victim, e, state(victim, a)); }
                catch (Exception ex) { plugin.getLogger().warning("onDamaged " + a.id() + " : " + ex); }
            }
        }
    }

    public void dispatchKill(Player killer, Player victim) {
        CfPlayer kcf = plugin.players().get(killer);
        if (kcf.character() != null) {
            for (Ability a : kcf.character().allAbilities()) {
                try { a.onKill(killer, victim, state(killer, a)); }
                catch (Exception ex) { plugin.getLogger().warning("onKill " + a.id() + " : " + ex); }
            }
            var lic = kcf.character().licence();
            if (lic != null && lic.mechanic() != null) lic.mechanic().onKill(killer, victim);
        }
    }

    /** Retourne true si une capacite prend la mort en charge (pas de perte de vie). */
    public boolean dispatchDeath(Player p) {
        CfPlayer cf = plugin.players().get(p);
        boolean handled = false;
        if (cf.character() != null) {
            for (Ability a : cf.character().allAbilities()) {
                try { if (a.onDeath(p, cf, state(p, a))) handled = true; }
                catch (Exception ex) { plugin.getLogger().warning("onDeath " + a.id() + " : " + ex); }
            }
            var lic = cf.character().licence();
            if (lic != null && lic.mechanic() != null) lic.mechanic().onDeath(p);
        }
        return handled;
    }

    /** Tick des particularites de licence, appele chaque seconde par la partie. */
    public void tickMechanics() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            CfPlayer cf = plugin.players().get(p);
            Character ch = cf.character();
            if (ch == null || ch.licence() == null || ch.licence().mechanic() == null) continue;
            if (cf.isWaitingRespawn() || !cf.isAlive()) continue;
            try { ch.licence().mechanic().tick(p); }
            catch (Exception ex) { plugin.getLogger().warning("tick licence " + ch.licence().id() + " : " + ex); }
        }
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
            if (!bar.isEmpty() && !Msg.actionBarBusy(p)) p.sendActionBar(Msg.mm(bar.toString()));
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
        Player owner = cf.bukkit();
        String custom = owner == null ? null : a.barText(owner, cf, st);
        if (custom != null) {
            if (!bar.isEmpty()) bar.append(" <dark_gray>| ");
            bar.append(custom);
            return;
        }
        switch (a.type()) {
            case ACTIVE -> piece = st.onCooldown()
                    ? "<gray>" + a.name() + " <red>" + st.cooldownLeftSeconds() + "s"
                    : "<green>" + a.name() + " ✔";
            case TOGGLE -> piece = (st.isEnabled() ? "<aqua>" : "<gray>") + a.name() + " " + (int) st.gauge() + "%";
            case SWITCH -> piece = "<yellow>" + a.name() + " <white>[" + ((SwitchAbility) a).modeName(st.mode()) + "]";
            case ULTIMATE -> piece = cf.isUltimateLocked()
                    ? "<dark_purple>Ultime en cours"
                    : cf.isUltimateReady()
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
        // Clic droit sur un coffre, une porte... : on laisse faire sauf si le joueur est accroupi.
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null
                && isInteractable(e.getClickedBlock().getType()) && !e.getPlayer().isSneaking()) {
            return;
        }
        // L'arme garde son usage normal (arc, etc.) : on n'annule que pour les items purs de capacite,
        // sauf si la capacite de l'arme gere elle-meme le clic.
        CfPlayer cfEarly = plugin.players().get(e.getPlayer());
        Ability early = cfEarly.character() == null ? null : cfEarly.character().ability(id);
        if (!isWeaponItem(item) || (early != null && early.type() != AbilityType.PASSIVE)) e.setCancelled(true);

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

    /** Blocs dont le clic droit a un usage (coffres, portes, tables...). */
    private static boolean isInteractable(org.bukkit.Material m) {
        String n = m.name();
        return n.contains("CHEST") || n.contains("DOOR") || n.contains("GATE") || n.contains("BUTTON") || n.contains("LEVER")
                || n.contains("FURNACE") || n.contains("TABLE") || n.contains("ANVIL") || n.contains("BARREL") || n.contains("SHULKER")
                || n.contains("BED") || n.contains("SIGN") || n.contains("TRAPDOOR") || n.contains("HOPPER") || n.contains("DISPENSER")
                || n.contains("DROPPER") || n.contains("REPEATER") || n.contains("COMPARATOR") || n.contains("GRINDSTONE")
                || n.contains("LOOM") || n.contains("STONECUTTER") || n.contains("LECTERN") || n.contains("BREWING")
                || n.contains("BEACON") || n.contains("CAMPFIRE") || n.contains("NOTE_BLOCK") || n.contains("JUKEBOX")
                || n.contains("CAULDRON") || n.contains("COMPOSTER") || n.contains("BELL") || n.contains("CRAFTER");
    }

    public void use(Player p, CfPlayer cf, Ability a) {
        AbilityState st = state(p, a);
        if (a instanceof CustomUse custom && custom.onUse(p, cf, st, p.isSneaking())) return;
        switch (a) {
            case ActiveAbility active -> {
                if (st.onCooldown()) {
                    Msg.actionBar(p, "<red>" + a.name() + " : encore " + st.cooldownLeftSeconds() + "s");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
                    return;
                }
                boolean ok = active.activate(p, cf, st);
                if (ok) {
                    st.startCooldown((int) Math.ceil(active.cooldownSeconds() * cf.cooldownFactor()));
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
            case PassiveAbility passive -> {
                Msg.actionBar(p, "<gray>" + a.name() + " est un passif : toujours actif.");
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
