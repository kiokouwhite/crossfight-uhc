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
 * Les bonus et malus d'un joueur, sur les bords de son ecran (comme dans un jeu de tir) : a gauche la bande
 * et les icones de ses bonus, a droite celles de ses malus (deux au plus de chaque cote), et un son quand il
 * en recoit un nouveau. Les icones montent en file le long du bord et s'effacent en haut : chaque image de
 * plein ecran est une etape de la boucle, que le plugin fait defiler.
 *
 * L'image est le "camera_overlay" d'un objet porte, que le jeu dessine en plein ecran (comme la vue a travers
 * une citrouille). Le joueur, et lui seul, voit un objet invisible dans sa case de casque (les bonus) et de
 * jambieres (les malus) : sa vraie armure ne bouge pas et protege toujours. Images : tools/build_overlays.py.
 * Rien en mode creatif ou spectateur (le client y ecrit lui-meme son inventaire). hud.effets: false coupe tout.
 */
public final class EffectHud implements Listener {

    /** Les familles d'effets, dans l'ordre d'affichage (le meme que tools/build_overlays.py). */
    static final List<String> BONUS = List.of("vitesse", "force", "resistance", "regeneration", "invisibilite", "absorption", "saut");
    static final List<String> MALUS = List.of("lenteur", "faiblesse", "poison", "cecite", "wither", "feu", "entrave");
    static final int HEAD = 5, LEGS = 7, FEET = 8;

    private final CrossfightPlugin plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private int tick;

    private static final class State {
        Set<String> bonus = Set.of(), malus = Set.of();
        String bonusImage, malusImage, ultImage;
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
            String bi = image("bonus", BONUS, bonus), mi = image("malus", MALUS, malus);
            st.bonusImage = bi;
            st.malusImage = mi;
            st.ultImage = active ? ultimate(p) : null;
            if (bi == null && mi == null && st.ultImage == null) {
                if (st.shown) { st.shown = false; p.updateInventory(); }
                continue;
            }
            // Les icones montent le long du bord : une image de la boucle a chaque tick (ce qui renvoie aussi
            // l'objet regulierement : un clic dans l'inventaire ou une mise a jour du serveur l'efface).
            send(p, st);
        }
    }

    private void send(Player p, State st) {
        st.shown = ClientSlots.show(p, new int[]{HEAD, LEGS, FEET}, new ItemStack[]{
                st.bonusImage == null ? p.getInventory().getHelmet() : overlayItem(EquipmentSlot.HEAD, folder() + st.bonusImage + "_" + frame()),
                st.malusImage == null ? p.getInventory().getLeggings() : overlayItem(EquipmentSlot.LEGS, folder() + st.malusImage + "_" + frame()),
                st.ultImage == null ? p.getInventory().getBoots() : overlayItem(EquipmentSlot.FEET, st.ultImage)});
    }

    /**
     * Ce que le joueur doit voir dans sa case de casque (5) ou de jambieres (7) : l'objet de l'image de ses
     * bonus ou malus, ou null s'il n'en a pas (sa vraie piece). Pour les animations qui masquent l'armure.
     */
    public ItemStack shownIn(Player p, int slot) {
        State st = states.get(p.getUniqueId());
        if (st == null) return null;
        if (slot == HEAD && st.bonusImage != null) return overlayItem(EquipmentSlot.HEAD, folder() + st.bonusImage + "_" + frame());
        if (slot == LEGS && st.malusImage != null) return overlayItem(EquipmentSlot.LEGS, folder() + st.malusImage + "_" + frame());
        if (slot == FEET && st.ultImage != null) return overlayItem(EquipmentSlot.FEET, st.ultImage);
        return null;
    }

    /** Le nom de l'image : "bonus_vitesse", "bonus_vitesse_force"... (les deux premiers dans l'ordre), ou null. */
    static String image(String kind, List<String> order, Set<String> present) {
        List<String> shown = new ArrayList<>();
        for (String k : order) if (present.contains(k) && shown.size() < 2) shown.add(k);
        return shown.isEmpty() ? null : kind + "_" + String.join("_", shown);
    }

    /**
     * Le dossier des images : celles aux vraies icones de Minecraft quand le pack complet les contient (un
     * dossier a part : le pack public envoye par le serveur ne les recouvre pas), sinon les dessins du pack public.
     */
    private static String folder() {
        return com.kiokouwhite.crossfight.util.Sounds.has("overlay.minecraft") ? "overlay_mc/" : "overlay/";
    }

    /**
     * L'orbe de l'ultime, en bas a gauche de l'ecran (avec le pack d'interface : tools/build_ultime_franuka.py) : il se
     * remplit par vingtiemes avec la charge ; plein, il brille (deux images qui alternent). Porte par la case des bottes.
     * Null sans le pack, ou si le joueur n'a pas de personnage.
     */
    private String ultimate(Player p) {
        if (!plugin.getConfig().getBoolean("hud.ultime", true) || !com.kiokouwhite.crossfight.util.Sounds.has("overlay.ultime") || !com.kiokouwhite.crossfight.util.Sounds.hasPackLoaded(p)) return null;
        CfPlayer cf = plugin.players().get(p);
        if (cf == null || cf.character() == null) return null;
        if (cf.isUltimateReady()) return "overlay_ult/ult_pret_" + (tick / 10 % 2);
        return String.format("overlay_ult/ult_%02d", Math.max(0, Math.min(19, (int) (cf.ultimateCharge() / 5))));
    }

    /** Images par boucle (tools/build_overlays.py : FRAMES). */
    static final int FRAMES = 16;

    /** L'image de la boucle a montrer maintenant. */
    private int frame() { return tick % FRAMES; }

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

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        states.remove(e.getPlayer().getUniqueId());
    }
}
