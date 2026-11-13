package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Mannequins d'entrainement : des zombies immobiles de 50 coeurs qui affichent
 * les degats recus et se regenerent apres 5 secondes sans etre touches.
 * Les capacites les considerent comme des ennemis.
 */
public final class Mannequins implements Listener {

    public static final String TAG = "cf_mannequin";
    public static final String AGGRO_TAG = "cf_mannequin_aggro";
    public static final String ALLY_TAG = "cf_mannequin_ally";
    public static final String ADVERSARY_TAG = "cf_mannequin_adversary";
    public static final org.bukkit.NamespacedKey CHARACTER_KEY = new org.bukkit.NamespacedKey("crossfight", "mannequin_character");
    public static final org.bukkit.NamespacedKey ADVERSARY_ITEM_KEY = new org.bukkit.NamespacedKey("crossfight", "adversary_ability");
    public static final double MAX_HEALTH = 100.0;

    private static final Map<UUID, BukkitTask> REGEN = new HashMap<>();

    public static boolean isMannequin(Entity e) {
        return e != null && e.getScoreboardTags().contains(TAG);
    }

    public static boolean isAggressive(Entity e) {
        return e != null && e.getScoreboardTags().contains(AGGRO_TAG);
    }

    public static LivingEntity spawn(Location loc) {
        return spawn(loc, false);
    }

    /**
     * @param aggressive true pour un mannequin qui poursuit et frappe les joueurs
     *                   (2 coeurs par coup, vitesse d'un joueur qui marche).
     */
    public static boolean isAdversary(Entity e) {
        return e != null && e.getScoreboardTags().contains(ADVERSARY_TAG);
    }

    /** Identifiant du personnage incarne par un mannequin adversaire, ou null. */
    public static String characterOf(Entity e) {
        if (!isAdversary(e)) return null;
        return e.getPersistentDataContainer().get(CHARACTER_KEY, org.bukkit.persistence.PersistentDataType.STRING);
    }

    /**
     * Mannequin adversaire : il incarne un personnage et le joueur recoit des items pour
     * lui faire lancer ses capacites contre lui-meme.
     */
    public static LivingEntity spawnAdversary(Location loc, com.kiokouwhite.crossfight.character.Character character, Player tester) {
        LivingEntity m = spawn(loc, false, false);
        m.addScoreboardTag(ADVERSARY_TAG);
        m.getPersistentDataContainer().set(CHARACTER_KEY, org.bukkit.persistence.PersistentDataType.STRING, character.id());
        var weapon = character.createWeapon();
        if (weapon != null && m.getEquipment() != null) {
            m.getEquipment().setItemInMainHand(weapon);
            m.getEquipment().setItemInMainHandDropChance(0f);
        }
        updateName(m);
        giveAdversaryItems(tester, character);
        return m;
    }

    /** Items de commande : un par capacite simulable du personnage, sur la deuxieme ligne de l'inventaire. */
    public static int giveAdversaryItems(Player p, com.kiokouwhite.crossfight.character.Character character) {
        removeAdversaryItems(p, character.id());
        var inv = p.getInventory();
        int slot = 9, given = 0;
        for (var a : character.allAbilities()) {
            if (!(a instanceof com.kiokouwhite.crossfight.ability.EnemyCastable)) continue;
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add("<dark_gray>" + a.type().label() + " de " + character.name());
            lore.addAll(Items.wrap(a.description()));
            lore.add("");
            lore.add("<red>Clic droit : " + character.name() + " l'utilise contre toi.");
            org.bukkit.inventory.ItemStack it = Items.of(a.icon(), "<red>[" + character.name() + "] " + a.name(), lore);
            Items.tag(it, ADVERSARY_ITEM_KEY, character.id() + ":" + a.id());
            while (slot < 36 && inv.getItem(slot) != null) slot++;
            if (slot >= 36) break;
            inv.setItem(slot++, it);
            given++;
        }
        return given;
    }

    public static void removeAdversaryItems(Player p, String characterId) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            String tag = Items.tag(inv.getItem(i), ADVERSARY_ITEM_KEY);
            if (tag != null && (characterId == null || tag.startsWith(characterId + ":"))) inv.setItem(i, null);
        }
    }

    public static boolean isAdversaryItem(org.bukkit.inventory.ItemStack it) {
        return Items.tag(it, ADVERSARY_ITEM_KEY) != null;
    }

    /** Le mannequin adversaire le plus proche incarnant ce personnage. */
    public static LivingEntity nearestAdversary(Player p, String characterId, double radius) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : p.getNearbyEntities(radius, radius, radius)) {
            if (!(e instanceof LivingEntity le) || !characterId.equals(characterOf(e))) continue;
            double d = le.getLocation().distanceSquared(p.getLocation());
            if (d < bestD) { bestD = d; best = le; }
        }
        return best;
    }

    public static boolean isAlly(Entity e) {
        return e != null && e.getScoreboardTags().contains(ALLY_TAG);
    }

    /**
     * Mannequin pilote (mode test en troisieme personne) : le corps d'un joueur pilote, qui lance les
     * capacites de son personnage sur son testeur. Il a la vie d'un joueur, ne meurt jamais, et n'est
     * retire que par la fin de son mode.
     */
    public static final String PILOT_TAG = "cf_mannequin_pilote";

    public static boolean isPilot(Entity e) {
        return e != null && e.getScoreboardTags().contains(PILOT_TAG);
    }

    /** Vie maximale d'un mannequin (100 pour les cibles, celle d'un joueur pour le mannequin pilote). */
    public static double maxHealthOf(LivingEntity e) {
        var mh = e.getAttribute(Attribute.MAX_HEALTH);
        return mh == null ? MAX_HEALTH : mh.getValue();
    }

    /** Remet a jour le nom affiche (personnage, vie). */
    public static void refreshName(LivingEntity e) {
        updateName(e);
    }

    // ------------------------------------------------------------ mannequin duo

    /**
     * Mannequin duo (zone duo du mode test) : un allie a l'image d'un personnage, qui tient le role de
     * la partenaire pour les ultimes en duo. Il est dans le camp du testeur qui l'a fait apparaitre.
     */
    public static final String PARTNER_TAG = "cf_mannequin_partner";
    private static final org.bukkit.NamespacedKey PARTNER_CHARACTER_KEY = new org.bukkit.NamespacedKey("crossfight", "partner_character");
    private static final org.bukkit.NamespacedKey PARTNER_OWNER_KEY = new org.bukkit.NamespacedKey("crossfight", "partner_owner");

    public static boolean isPartner(Entity e) {
        return e != null && e.getScoreboardTags().contains(PARTNER_TAG);
    }

    /** Le personnage incarne par un mannequin duo, ou null. */
    public static String partnerCharacterOf(Entity e) {
        if (!isPartner(e)) return null;
        return e.getPersistentDataContainer().get(PARTNER_CHARACTER_KEY, org.bukkit.persistence.PersistentDataType.STRING);
    }

    /** Le testeur d'un mannequin duo (s'il est connecte), ou null. */
    public static Player partnerOwner(Entity e) {
        if (!isPartner(e)) return null;
        String s = e.getPersistentDataContainer().get(PARTNER_OWNER_KEY, org.bukkit.persistence.PersistentDataType.STRING);
        return s == null ? null : Bukkit.getPlayer(UUID.fromString(s));
    }

    /** Les mannequins duo d'un joueur, dans tous les mondes. */
    public static java.util.List<LivingEntity> partnersOf(Player owner) {
        java.util.List<LivingEntity> out = new java.util.ArrayList<>();
        String id = owner.getUniqueId().toString();
        for (World w : Bukkit.getWorlds()) {
            for (LivingEntity e : w.getLivingEntities()) {
                if (isPartner(e) && id.equals(e.getPersistentDataContainer().get(PARTNER_OWNER_KEY, org.bukkit.persistence.PersistentDataType.STRING))) out.add(e);
            }
        }
        return out;
    }

    public static void removePartners(Player owner) {
        for (LivingEntity e : partnersOf(owner)) e.remove();
    }

    /** Fait apparaitre le mannequin duo d'un joueur, a l'image d'un personnage. */
    public static LivingEntity spawnPartner(Location loc, com.kiokouwhite.crossfight.character.Character character, Player owner) {
        org.bukkit.entity.Mannequin m = (org.bukkit.entity.Mannequin) loc.getWorld().spawnEntity(loc, EntityType.MANNEQUIN);
        m.addScoreboardTag(TAG);
        m.addScoreboardTag(ALLY_TAG);
        m.addScoreboardTag(PARTNER_TAG);
        m.getPersistentDataContainer().set(PARTNER_OWNER_KEY, org.bukkit.persistence.PersistentDataType.STRING, owner.getUniqueId().toString());
        // Il peut etre projete (Yang lance Ruby...), mais ne se deplace jamais seul.
        m.setImmovable(false);
        m.setSilent(true);
        m.setPersistent(false);
        m.setCustomNameVisible(true);
        m.setDescription(Msg.mm("<gray>Clic droit : changer de personnage"));
        var mh = m.getAttribute(Attribute.MAX_HEALTH);
        if (mh != null) mh.setBaseValue(MAX_HEALTH);
        m.setHealth(mh != null ? MAX_HEALTH : m.getHealth());
        setPartnerCharacter(m, character);
        return m;
    }

    /** Change le personnage d'un mannequin duo : son skin (s'il en a un), son arme et son nom. */
    public static void setPartnerCharacter(LivingEntity e, com.kiokouwhite.crossfight.character.Character character) {
        e.getPersistentDataContainer().set(PARTNER_CHARACTER_KEY, org.bukkit.persistence.PersistentDataType.STRING, character.id());
        if (e instanceof org.bukkit.entity.Mannequin m) {
            var skin = Skins.profile(character.id());
            m.setProfile(skin != null ? skin : org.bukkit.entity.Mannequin.defaultProfile());
            var eq = m.getEquipment();
            if (eq != null) eq.setItemInMainHand(character.createWeapon());
        }
        for (var eff : e.getActivePotionEffects()) e.removePotionEffect(eff.getType());
        updateName(e);
    }

    public static LivingEntity spawn(Location loc, boolean aggressive) {
        return spawn(loc, aggressive, false);
    }

    /**
     * @param ally true pour un mannequin considere comme un allie par les capacites
     *             (soins, Tutorat, Charisme...). Il n'est jamais agressif.
     */
    public static LivingEntity spawn(Location loc, boolean aggressive, boolean ally) {
        World w = loc.getWorld();
        Zombie z = (Zombie) w.spawnEntity(loc, EntityType.ZOMBIE);
        z.addScoreboardTag(TAG);
        if (ally) { z.addScoreboardTag(ALLY_TAG); aggressive = false; }
        if (aggressive) z.addScoreboardTag(AGGRO_TAG);
        z.setAdult();
        // L'IA reste active pour que le mannequin subisse la gravite et les projections ;
        // un mannequin passif a simplement une vitesse nulle et ne cible personne.
        z.setAI(true);
        if (!aggressive) {
            var spd = z.getAttribute(Attribute.MOVEMENT_SPEED);
            if (spd != null) spd.setBaseValue(0.0);
        }
        if (aggressive) {
            var atk = z.getAttribute(Attribute.ATTACK_DAMAGE);
            if (atk != null) atk.setBaseValue(4.0);
            var spd = z.getAttribute(Attribute.MOVEMENT_SPEED);
            if (spd != null) spd.setBaseValue(0.28);
            var range = z.getAttribute(Attribute.FOLLOW_RANGE);
            if (range != null) range.setBaseValue(48.0);
        }
        z.setSilent(true);
        z.setShouldBurnInDay(false);
        z.setCanPickupItems(false);
        z.setPersistent(true);
        z.setRemoveWhenFarAway(false);
        z.setCustomNameVisible(true);
        var mh = z.getAttribute(Attribute.MAX_HEALTH);
        if (mh != null) mh.setBaseValue(MAX_HEALTH);
        var kb = z.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(0.0);
        var reinf = z.getAttribute(Attribute.SPAWN_REINFORCEMENTS);
        if (reinf != null) reinf.setBaseValue(0.0);
        z.setHealth(MAX_HEALTH);
        // Equipement standard de Crossfight : 2 pieces diamant Protection 2, 2 pieces fer Protection 3.
        var eq = z.getEquipment();
        eq.setHelmet(armor(org.bukkit.Material.IRON_HELMET, 3));
        eq.setChestplate(armor(org.bukkit.Material.DIAMOND_CHESTPLATE, 2));
        eq.setLeggings(armor(org.bukkit.Material.DIAMOND_LEGGINGS, 2));
        eq.setBoots(armor(org.bukkit.Material.IRON_BOOTS, 3));
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        updateName(z);
        return z;
    }

    public static int clearAll() {
        int n = 0;
        for (World w : Bukkit.getWorlds()) {
            for (LivingEntity e : w.getLivingEntities()) {
                if (isMannequin(e) && !isPilot(e)) { e.remove(); n++; }
            }
        }
        for (BukkitTask t : REGEN.values()) t.cancel();
        REGEN.clear();
        ADVERSARY_STATES.clear();
        for (Player p : Bukkit.getOnlinePlayers()) removeAdversaryItems(p, null);
        return n;
    }

    private static org.bukkit.inventory.ItemStack armor(org.bukkit.Material m, int protection) {
        org.bukkit.inventory.ItemStack it = new org.bukkit.inventory.ItemStack(m);
        var meta = it.getItemMeta();
        meta.addEnchant(org.bukkit.enchantments.Enchantment.PROTECTION, protection, true);
        meta.setUnbreakable(true);
        it.setItemMeta(meta);
        return it;
    }

    private static void updateName(LivingEntity e) {
        double hp = Math.max(0, e.getHealth());
        String label;
        if (isAdversary(e)) {
            var c = CrossfightPlugin.get().characters().character(characterOf(e));
            label = "<red>" + (c == null ? "Adversaire" : c.name()) + " <dark_red>(adversaire)";
        } else if (isPartner(e)) {
            var c = CrossfightPlugin.get().characters().character(partnerCharacterOf(e));
            label = "<green>" + (c == null ? "Partenaire" : c.name()) + " <dark_green>(mannequin duo)";
        } else if (isPilot(e)) {
            Player pilot = MannequinPlayer.of(e);
            var cf = pilot == null ? null : CrossfightPlugin.get().players().get(pilot);
            var c = cf == null ? null : cf.character();
            label = "<aqua>" + (c == null ? "Mannequin" : c.name()) + " <dark_aqua>(3e personne)";
        } else label = isAggressive(e) ? "<dark_red>Mannequin agressif" : isAlly(e) ? "<green>Mannequin allie" : "<gold>Mannequin";
        int pct = (int) Math.round(hp / maxHealthOf(e) * 100);
        e.customName(Msg.mm(label + " <red>❤ " + pct + "%"));
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((int) d) : String.format(Locale.US, "%.1f", d);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDamageLowest(EntityDamageEvent e) {
        if (!isMannequin(e.getEntity())) return;
        switch (e.getCause()) {
            case FIRE, FIRE_TICK, LAVA, DROWNING, SUFFOCATION, HOT_FLOOR, FREEZE -> e.setCancelled(true);
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageHighest(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof LivingEntity m) || !isMannequin(m)) return;
        double dmg = e.getFinalDamage();
        boolean lethal = dmg >= m.getHealth();
        if (lethal) {
            // Le mannequin ne meurt jamais : on encaisse puis on le remet a neuf.
            e.setDamage(0);
            m.setHealth(maxHealthOf(m));
            m.getWorld().playSound(m.getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_DEATH, 1f, 1.2f);
            m.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, m.getLocation().add(0, 1, 0), 1);
        }
        Bukkit.getScheduler().runTask(CrossfightPlugin.get(), () -> updateName(m));
        scheduleRegen(m);
    }

    private static void scheduleRegen(LivingEntity m) {
        BukkitTask old = REGEN.remove(m.getUniqueId());
        if (old != null) old.cancel();
        REGEN.put(m.getUniqueId(), Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> {
            if (m.isValid()) {
                m.setHealth(maxHealthOf(m));
                // Le mannequin pilote garde ses effets : ce sont ceux de ses capacites.
                if (!isPilot(m)) for (var eff : m.getActivePotionEffects()) m.removePotionEffect(eff.getType());
                m.setFireTicks(0);
                updateName(m);
            }
            REGEN.remove(m.getUniqueId());
        }, 100L));
    }

    /** Etats des capacites par mannequin adversaire. */
    private static final Map<UUID, Map<String, com.kiokouwhite.crossfight.ability.AbilityState>> ADVERSARY_STATES = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGH)
    public void onUseAdversaryItem(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        String tag = Items.tag(e.getItem(), ADVERSARY_ITEM_KEY);
        if (tag == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        String[] parts = tag.split(":", 2);
        var character = CrossfightPlugin.get().characters().character(parts[0]);
        var ability = character == null ? null : character.ability(parts[1]);
        if (!(ability instanceof com.kiokouwhite.crossfight.ability.EnemyCastable castable)) return;
        LivingEntity adversary = nearestAdversary(p, parts[0], 80);
        if (adversary == null) {
            Msg.actionBar(p, "<red>Aucun mannequin " + character.name() + " a proximite.");
            return;
        }
        var state = ADVERSARY_STATES.computeIfAbsent(adversary.getUniqueId(), u -> new HashMap<>())
                .computeIfAbsent(ability.id(), k -> new com.kiokouwhite.crossfight.ability.AbilityState());
        try {
            if (castable.castOn(adversary, p, state)) {
                Msg.actionBar(p, "<red>" + character.name() + " utilise " + ability.name() + " contre toi !");
            }
        } catch (Exception ex) {
            CrossfightPlugin.get().getLogger().warning("Capacite adverse " + ability.id() + " : " + ex);
            Msg.actionBar(p, "<red>Erreur dans la capacite adverse, voir la console.");
        }
    }

    @EventHandler
    public void onDropAdversaryItem(org.bukkit.event.player.PlayerDropItemEvent e) {
        if (isAdversaryItem(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onCombust(EntityCombustEvent e) {
        if (isMannequin(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        if (!isMannequin(e.getEntity())) return;
        // Les mannequins agressifs ne visent que des joueurs en survie.
        if (isAggressive(e.getEntity()) && e.getTarget() instanceof Player t && t.getGameMode() == org.bukkit.GameMode.SURVIVAL) return;
        e.setCancelled(true);
    }
}
