package com.kiokouwhite.crossfight.hud;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.ClientSlots;
import com.kiokouwhite.crossfight.util.Effects;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ce que le joueur voit en plus sur son ecran : un son quand il recoit un nouveau bonus ou malus
 * (hud.effets: false le coupe), et l'orbe de son ultime en bas a droite.
 *
 * L'orbe est le "camera_overlay" d'un objet porte, que le jeu dessine en plein ecran (comme la vue a travers
 * une citrouille). Le joueur, et lui seul, voit un objet invisible dans sa case de bottes : ses vraies bottes
 * ne bougent pas et protegent toujours. Rien en mode creatif ou spectateur (le client y ecrit lui-meme son
 * inventaire).
 */
public final class EffectHud implements Listener {

    static final int HEAD = 5, FEET = 8;

    private final CrossfightPlugin plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private int tick;

    private static final class State {
        Set<String> bonus = Set.of(), malus = Set.of();
        String ultImage, passiveImage;
        boolean shown;
        final Map<String, Long> lastSound = new HashMap<>();
    }

    public EffectHud(CrossfightPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::update, 20L, 1L);
    }

    private boolean enabled() { return plugin.getConfig().getBoolean("hud.effets", true); }

    private void update() {
        tick++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            State st = states.computeIfAbsent(p.getUniqueId(), k -> new State());
            // Rien pendant le choix du personnage : l'ecran est aux cartes.
            boolean active = enabled() && plugin.game().isRunning() && !p.isDead() && !plugin.selection().isViewing(p)
                    && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE);
            Set<String> bonus = active ? bonuses(p) : Set.of();
            Set<String> malus = active ? maluses(p) : Set.of();
            // Un son pour chaque effet qui vient d'apparaitre (pas plus d'une fois toutes les 2 s par effet).
            long now = System.currentTimeMillis();
            boolean newBonus = false, newMalus = false;
            for (String b : bonus) if (!st.bonus.contains(b) && now - st.lastSound.getOrDefault(b, 0L) > 2000) { newBonus = true; st.lastSound.put(b, now); }
            for (String m : malus) if (!st.malus.contains(m) && now - st.lastSound.getOrDefault(m, 0L) > 2000) { newMalus = true; st.lastSound.put(m, now); }
            if (newBonus) {
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 0.7f, 1.7f);
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 1.5f);
            }
            if (newMalus) {
                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 0.8f, 1.3f);
                p.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_HURT, SoundCategory.PLAYERS, 0.35f, 1.6f);
            }
            st.bonus = bonus;
            st.malus = malus;
            st.ultImage = active ? ultimate(p) : null;
            st.passiveImage = active && com.kiokouwhite.crossfight.util.Sounds.hasPackLoaded(p) ? PassiveHud.image(p) : null;
            if (st.ultImage == null && st.passiveImage == null) {
                if (st.shown) { st.shown = false; p.updateInventory(); }
                continue;
            }
            // Renvoye a chaque tick : un clic dans l'inventaire ou une mise a jour du serveur efface l'objet.
            send(p, st);
        }
    }

    private void send(Player p, State st) {
        st.shown = ClientSlots.show(p, new int[]{HEAD, FEET}, new ItemStack[]{
                st.passiveImage == null ? p.getInventory().getHelmet() : overlayItem(EquipmentSlot.HEAD, st.passiveImage),
                st.ultImage == null ? p.getInventory().getBoots() : overlayItem(EquipmentSlot.FEET, st.ultImage)});
    }

    /** Ce que le joueur doit voir dans sa case de casque (5 : ses passifs) ou de bottes (8 : l'orbe de l'ultime), ou null (sa vraie piece). */
    public ItemStack shownIn(Player p, int slot) {
        State st = states.get(p.getUniqueId());
        if (st == null) return null;
        if (slot == FEET && st.ultImage != null) return overlayItem(EquipmentSlot.FEET, st.ultImage);
        if (slot == HEAD && st.passiveImage != null) return overlayItem(EquipmentSlot.HEAD, st.passiveImage);
        return null;
    }

    /**
     * L'orbe de l'ultime, en bas a droite de l'ecran (avec le pack d'interface : tools/build_ultime_franuka.py) : il se
     * remplit par vingtiemes avec la charge ; plein, il brille (deux images qui alternent). Porte par la case des bottes.
     * Null sans le pack, ou si le joueur n'a pas de personnage.
     */
    private String ultimate(Player p) {
        if (!showsUltimate(p)) return null;
        CfPlayer cf = plugin.players().get(p);
        if (cf == null || cf.character() == null) return null;
        if (cf.isUltimateReady()) return "overlay_ult/ult_pret_" + (tick / 10 % 2);
        return String.format("overlay_ult/ult_%02d", Math.max(0, Math.min(19, (int) (cf.ultimateCharge() / 5))));
    }

    /** Vrai si ce joueur a l'orbe de l'ultime a l'ecran (le pack d'interface charge, hud.ultime) : la charge n'a alors plus a etre ecrite ailleurs. */
    public boolean showsUltimate(Player p) {
        return plugin.getConfig().getBoolean("hud.ultime", true) && com.kiokouwhite.crossfight.util.Sounds.has("overlay.ultime") && com.kiokouwhite.crossfight.util.Sounds.hasPackLoaded(p);
    }

    private final Map<String, ItemStack> items = new HashMap<>();

    private ItemStack overlayItem(EquipmentSlot slot, String image) {
        return items.computeIfAbsent(slot + ":" + image, k -> {
            ItemStack it = ItemStack.of(Material.PAPER);
            it.setData(DataComponentTypes.ITEM_MODEL, Key.key("crossfight", "hidden"));
            it.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(slot).cameraOverlay(Key.key("crossfight", image)).build());
            it.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
            return it;
        }).clone();
    }

    // ------------------------------------------------------------ ce que le joueur subit

    private static boolean has(Player p, PotionEffectType type) {
        PotionEffect e = p.getPotionEffect(type);
        // Un effet sans icone est technique (l'invisibilite d'un pantin d'animation...) : on l'ignore.
        return e != null && e.hasIcon();
    }

    Set<String> bonuses(Player p) {
        Set<String> out = new LinkedHashSet<>();
        if (has(p, PotionEffectType.SPEED) || Boosts.totalSpeed(p) > 0) out.add("vitesse");
        if (has(p, PotionEffectType.STRENGTH) || Boosts.totalForce(p) > 0 || Boosts.totalWeapon(p) > 0) out.add("force");
        if (has(p, PotionEffectType.RESISTANCE) || has(p, PotionEffectType.FIRE_RESISTANCE) || Boosts.totalResistance(p) > 0) out.add("resistance");
        if (has(p, PotionEffectType.REGENERATION)) out.add("regeneration");
        if (has(p, PotionEffectType.INVISIBILITY)) out.add("invisibilite");
        if (has(p, PotionEffectType.ABSORPTION)) out.add("absorption");
        if (has(p, PotionEffectType.JUMP_BOOST) || has(p, PotionEffectType.SLOW_FALLING)) out.add("saut");
        return out;
    }

    Set<String> maluses(Player p) {
        Set<String> out = new LinkedHashSet<>();
        if (has(p, PotionEffectType.SLOWNESS) || Boosts.totalSpeed(p) < 0) out.add("lenteur");
        if (has(p, PotionEffectType.WEAKNESS) || has(p, PotionEffectType.MINING_FATIGUE) || Boosts.totalForce(p) < 0 || Boosts.totalResistance(p) < 0) out.add("faiblesse");
        if (has(p, PotionEffectType.POISON) || has(p, PotionEffectType.HUNGER) || has(p, PotionEffectType.NAUSEA)) out.add("poison");
        if (has(p, PotionEffectType.BLINDNESS) || has(p, PotionEffectType.DARKNESS)) out.add("cecite");
        if (has(p, PotionEffectType.WITHER)) out.add("wither");
        if (p.getFireTicks() > 0 && !p.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) out.add("feu");
        CfPlayer cf = CrossfightPlugin.get().players().get(p);
        if (Effects.isRooted(p) || (cf != null && cf.isSilenced()) || has(p, PotionEffectType.LEVITATION) || p.getFreezeTicks() > 0) out.add("entrave");
        return out;
    }

    /**
     * Les icones d'effet de Minecraft en haut a droite de l'ecran (hud.icones-minecraft) : cachees par defaut.
     * Tout effet recu avec son icone est remis au tick suivant, identique mais sans icone ; il reste liste dans
     * l'inventaire. (Pas dans l'evenement meme : il peut venir du tic des effets, qui parcourt la liste.)
     */
    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.MONITOR)
    public void onEffect(org.bukkit.event.entity.EntityPotionEffectEvent e) {
        if (!(e.getEntity() instanceof Player p) || plugin.getConfig().getBoolean("hud.icones-minecraft", false)) return;
        PotionEffect fx = e.getNewEffect();
        if (fx == null || !fx.hasIcon()) return;
        Bukkit.getScheduler().runTask(plugin, () -> hideIcon(p, fx.getType()));
    }

    /** Remet l'effet en cours de ce type sans son icone. */
    private static void hideIcon(Player p, PotionEffectType type) {
        if (!p.isOnline()) return;
        PotionEffect cur = p.getPotionEffect(type);
        if (cur == null || !cur.hasIcon()) return;
        p.addPotionEffect(new PotionEffect(type, cur.getDuration(), cur.getAmplifier(), cur.isAmbient(), cur.hasParticles(), false));
    }

    /** A l'arrivee, les effets deja portes perdent leur icone aussi. */
    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        if (plugin.getConfig().getBoolean("hud.icones-minecraft", false)) return;
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> { for (PotionEffect fx : p.getActivePotionEffects()) hideIcon(p, fx.getType()); });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        states.remove(e.getPlayer().getUniqueId());
        PassiveHud.forget(e.getPlayer());
    }
}
