package com.kiokouwhite.crossfight.character.solo;

import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.Borrowed;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.listener.DamageListener;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.ArenaWorlds;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.ChoiceMenu;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Mannequins;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.TempBlocks;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * L'arbre des capacites de Shoja : 59 capacites en six elements, chacune debloquee par un objectif
 * (crafts, degats, immobilisations...), trois equipees au plus, d'elements differents.
 */
public final class ShojaTree {

    private ShojaTree() {}

    static final String DIM_WORLD = "crossfight_dimension";
    static final NamespacedKey ORB = new NamespacedKey("crossfight", "shoja_orb");
    static final Particle.DustOptions GREY = new Particle.DustOptions(Color.fromRGB(140, 140, 150), 1.2f);

    // ------------------------------------------------------------- elements

    enum Element {
        FEU("Feu", "red", Material.BLAZE_POWDER),
        EAU("Eau", "aqua", Material.WATER_BUCKET),
        FOUDRE("Foudre", "yellow", Material.LIGHTNING_ROD),
        VENT("Vent", "white", Material.FEATHER),
        ESPACE("Espace", "dark_purple", Material.ENDER_EYE),
        TEMPS("Temps", "light_purple", Material.CLOCK);
        final String label, color; final Material icon;
        Element(String label, String color, Material icon) { this.label = label; this.color = color; this.icon = icon; }
    }

    /** Un objectif : un compteur et la valeur a atteindre. */
    record Need(String key, double amount, String label) {}

    /** Effet d'une capacite active. */
    interface Act { boolean act(Player p, CfPlayer cf, AbilityState st); }

    /** Une capacite de l'arbre. */
    static final class Node {
        final String id, name, desc; final Element element; final Material icon; final int cd; final List<Need> needs;
        Ability ability;
        Node(String id, String name, Element element, Material icon, int cd, String desc, Need... needs) {
            this.id = id; this.name = name; this.element = element; this.icon = icon; this.cd = cd; this.desc = desc; this.needs = List.of(needs);
        }
        String abilityId() { return "shoja_" + id; }
    }

    static final Map<String, Node> NODES = new LinkedHashMap<>();

    static Node node(String id, String name, Element el, Material icon, int cd, String desc, Need... needs) {
        Node n = new Node(id, name, el, icon, cd, desc, needs);
        NODES.put(id, n);
        return n;
    }
    static Need need(String key, double amount, String label) { return new Need(key, amount, label); }
    static Need uses(String id, double amount) { return new Need(id + "_uses", amount, "utiliser " + NODES.get(id).name + " " + (int) amount + " fois"); }

    // ------------------------------------------------------------ progression

    static final Map<UUID, Map<String, Double>> PROGRESS = new HashMap<>();
    static final Map<UUID, Set<String>> FORCED = new HashMap<>();
    static final Map<UUID, List<String>> EQUIPPED = new HashMap<>();

    static Map<String, Double> progress(Player p) { return PROGRESS.computeIfAbsent(p.getUniqueId(), u -> new HashMap<>()); }
    static double get(Player p, String key) { return progress(p).getOrDefault(key, 0.0); }

    static boolean isShoja(Player p) { return "shoja".equals(Solo.characterIdOf(p)); }

    /** Fait avancer un compteur et previent le joueur quand une capacite se debloque. */
    static void add(Player p, String key, double n) {
        if (p == null || !isShoja(p)) return;
        Map<String, Double> m = progress(p);
        Set<String> before = new HashSet<>();
        for (Node nd : NODES.values()) if (unlocked(p, nd)) before.add(nd.id);
        m.merge(key, n, Double::sum);
        for (Node nd : NODES.values()) if (!before.contains(nd.id) && unlocked(p, nd)) {
            Msg.send(p, "<" + nd.element.color + "><bold>Arbre :</bold> <white>" + nd.name + " <gray>(" + nd.element.label + ") est debloquee !");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.3f);
        }
    }

    static boolean unlocked(Player p, Node n) {
        if (Solo.plugin().game().isTest()) return true;
        if (FORCED.getOrDefault(p.getUniqueId(), Set.of()).contains(n.id)) return true;
        for (Need nd : n.needs) if (get(p, nd.key) < nd.amount) return false;
        return true;
    }

    static List<String> equipped(Player p) { return EQUIPPED.computeIfAbsent(p.getUniqueId(), u -> new ArrayList<>()); }
    static boolean isEquipped(Player p, String id) { return equipped(p).contains(id); }

    /** Equipe (ou retire) une capacite : trois au plus, une par element. */
    static boolean equip(Player p, Node n, boolean quiet) {
        if (isEquipped(p, n.id)) { unequip(p, n); if (!quiet) Msg.send(p, "<gray>" + n.name + " retiree."); return true; }
        if (!unlocked(p, n)) { if (!quiet) Msg.send(p, "<red>" + n.name + " n'est pas encore debloquee."); return false; }
        for (String id : new ArrayList<>(equipped(p))) { Node o = NODES.get(id); if (o != null && o.element == n.element) unequip(p, o); }
        if (equipped(p).size() >= 3) { if (!quiet) Msg.send(p, "<red>Trois capacites au plus : retire-en une d'abord."); return false; }
        equipped(p).add(n.id);
        if (!Borrowed.has(p, n.abilityId())) Borrowed.add(p, n.ability, "arbre", Long.MAX_VALUE);
        if (!quiet) Msg.send(p, "<" + n.element.color + ">" + n.name + " <white>equipee <gray>(" + equipped(p).size() + "/3).");
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.4f);
        // Un passif n'a pas de lancement : sa replique est dite quand elle l'equipe.
        if (n.cd == 0 && !quiet) com.kiokouwhite.crossfight.util.Sounds.voice(p, n.abilityId());
        return true;
    }

    static void unequip(Player p, Node n) {
        equipped(p).remove(n.id);
        for (Borrowed.Entry e : new ArrayList<>(Borrowed.of(p))) if (e.ability.id().equals(n.abilityId())) Borrowed.remove(p, e);
    }

    /** Apres une reapparition ou un changement, redonne les capacites equipees. */
    static void regive(Player p) {
        for (String id : equipped(p)) { Node n = NODES.get(id); if (n != null && !Borrowed.has(p, n.abilityId())) Borrowed.add(p, n.ability, "arbre", Long.MAX_VALUE); }
    }

    static void clear(Player p) {
        for (String id : new ArrayList<>(equipped(p))) { Node n = NODES.get(id); if (n != null) unequip(p, n); }
        EQUIPPED.remove(p.getUniqueId());
    }

    static void reset() {
        PROGRESS.clear(); FORCED.clear(); EQUIPPED.clear(); MINES.clear(); FROZEN.clear(); UNEXTINGUISHABLE.clear();
        BOURRASQUE.clear(); HEALTH_HISTORY.clear(); DIM_RETURN.clear(); WIND_CHARGES.clear();
    }

    /** Commande de test : liste, debloque ou equipe. */
    public static boolean command(Player p, String[] args) {
        if (!isShoja(p)) { Msg.send(p, "<red>Tu dois jouer Shoja (solitaire)."); return true; }
        String a = args.length > 0 ? args[0].toLowerCase() : "liste";
        switch (a) {
            case "liste" -> {
                for (Element el : Element.values()) {
                    StringBuilder sb = new StringBuilder("<" + el.color + "><bold>" + el.label + "</bold> <gray>: ");
                    for (Node n : NODES.values()) if (n.element == el) sb.append(isEquipped(p, n.id) ? "<green>" : unlocked(p, n) ? "<white>" : "<dark_gray>").append(n.id).append("<gray>, ");
                    Msg.send(p, sb.toString());
                }
                Msg.send(p, "<gray>/cf shoja <id> equipe ou retire ; /cf shoja tous debloque tout ; /cf shoja raz retire tout.");
            }
            case "tous" -> { Set<String> f = FORCED.computeIfAbsent(p.getUniqueId(), u -> new HashSet<>()); f.addAll(NODES.keySet()); Msg.send(p, "<green>Toutes les capacites de l'arbre sont debloquees."); }
            case "raz" -> { clear(p); Msg.send(p, "<gray>Capacites de l'arbre retirees."); }
            default -> {
                Node n = NODES.get(a);
                if (n == null) { Msg.send(p, "<red>Capacite inconnue : " + a + " (/cf shoja liste)"); return true; }
                if (!unlocked(p, n)) FORCED.computeIfAbsent(p.getUniqueId(), u -> new HashSet<>()).add(n.id);
                equip(p, n, false);
            }
        }
        return true;
    }

    public static List<String> ids() { return new ArrayList<>(NODES.keySet()); }

    // ------------------------------------------------------------------ menu

    static void openMenu(Player p) {
        List<ChoiceMenu.Option> opts = new ArrayList<>();
        for (Element el : Element.values()) {
            int unl = 0, tot = 0;
            for (Node n : NODES.values()) if (n.element == el) { tot++; if (unlocked(p, n)) unl++; }
            String eq = "";
            for (String id : equipped(p)) { Node n = NODES.get(id); if (n != null && n.element == el) eq = n.name; }
            opts.add(new ChoiceMenu.Option(el.icon, el.label, List.of("<gray>" + unl + "/" + tot + " debloquee(s)", eq.isEmpty() ? "<dark_gray>Aucune equipee" : "<green>Equipee : " + eq), () -> openElement(p, el)));
        }
        ChoiceMenu.open(p, "<dark_red>Arbre des capacites <gray>(" + equipped(p).size() + "/3)", opts);
    }

    static void openElement(Player p, Element el) {
        List<ChoiceMenu.Option> opts = new ArrayList<>();
        for (Node n : NODES.values()) {
            if (n.element != el) continue;
            boolean unl = unlocked(p, n), eq = isEquipped(p, n.id);
            List<String> lore = new ArrayList<>(Items.wrap(n.desc));
            lore.add(n.cd > 0 ? "<yellow>Temps de chargement : <white>" + Msg.time(n.cd) : "<dark_gray>Passif");
            lore.add("");
            if (n.needs.isEmpty()) lore.add("<gray>Condition : aucune");
            for (Need nd : n.needs) lore.add((get(p, nd.key) >= nd.amount ? "<green>✔ " : "<red>✘ ") + "<gray>" + nd.label + " <dark_gray>(" + (int) Math.min(get(p, nd.key), nd.amount) + "/" + (int) nd.amount + ")");
            lore.add("");
            lore.add(eq ? "<green><bold>EQUIPEE</bold> <gray>(clic : retirer)" : unl ? "<white>Debloquee <gray>(clic : equiper)" : "<dark_gray>Verrouillee");
            opts.add(new ChoiceMenu.Option(unl ? n.icon : Material.BARRIER, (eq ? "<green>" : unl ? "<white>" : "<dark_gray>") + n.name, lore, () -> { equip(p, n, false); Bukkit.getScheduler().runTask(Solo.plugin(), () -> openElement(p, el)); }));
        }
        ChoiceMenu.open(p, "<" + el.color + ">" + el.label + " <gray>(" + equipped(p).size() + "/3)", opts, pl -> {});
    }

    // ------------------------------------------------------------- generiques

    static class Active extends ActiveAbility {
        final Node node; final Act act;
        Active(Node node, Act act) { super(node.abilityId(), node.name, node.desc, node.icon, node.cd); this.node = node; this.act = act; }
        @Override protected List<String> extraLore() { return List.of("<" + node.element.color + ">Element : " + node.element.label, "", "<yellow>Temps de chargement : <white>" + Msg.time(node.cd)); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            boolean ok = act.act(p, cf, st);
            if (ok) add(p, node.id + "_uses", 1);
            return ok;
        }
    }

    interface OnHit { void hit(Player shoja, LivingEntity other, EntityDamageByEntityEvent e, AbilityState st); }

    static class Passive extends PassiveAbility {
        final Node node; OnHit onDeal, onTaken;
        Passive(Node node) { super(node.abilityId(), node.name, node.desc, node.icon); this.node = node; }
        Passive deal(OnHit h) { onDeal = h; return this; }
        Passive taken(OnHit h) { onTaken = h; return this; }
        @Override protected List<String> extraLore() { return List.of("<" + node.element.color + ">Element : " + node.element.label); }
        @Override public void onDealDamage(Player a, LivingEntity v, EntityDamageByEntityEvent e, AbilityState st) { if (onDeal != null && Effects.isEnemy(a, v)) onDeal.hit(a, v, e, st); }
        @Override public void onDamaged(Player v, EntityDamageEvent e, AbilityState st) {
            if (onTaken == null || !(e instanceof EntityDamageByEntityEvent be)) return;
            LivingEntity a = DamageListener.livingAttackerOf(be.getDamager());
            if (a != null && a != v && Effects.isEnemy(v, a)) onTaken.hit(v, a, be, st);
        }
    }

    // --------------------------------------------------------------- helpers

    static Location ahead(Player p, double dist) {
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
        Location l = p.getLocation().add(dir.normalize().multiply(dist));
        int gy = Effects.groundAround(p.getWorld(), l.getBlockX(), l.getBlockZ(), p.getLocation().getBlockY());
        if (gy != Integer.MIN_VALUE) l.setY(gy + 1);
        return l;
    }

    static LivingEntity nearest(Player p, double r) { return Effects.nearestEnemy(p, r); }

    /** Nom sans codes de couleur (les mannequins en ont). */
    static String safeName(LivingEntity e) { return e instanceof Player pl ? pl.getName() : e.getName().replaceAll("§.", ""); }

    static void ignite(Player by, LivingEntity e, int ticks, String key) {
        boolean fresh = e.getFireTicks() <= 0;
        e.setFireTicks(Math.max(e.getFireTicks(), ticks));
        if (fresh) { add(by, "ign_total", 1); if (key != null) add(by, key, 1); }
    }

    static void slow(Player by, LivingEntity e, int amp, int ticks) {
        Effects.give(e, PotionEffectType.SLOWNESS, amp, ticks);
        add(by, "slow_inflicted", 1); add(by, "negative_effects", 1);
        e.getWorld().spawnParticle(Particle.SPLASH, e.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
    }

    static void negative(Player by, LivingEntity e, PotionEffectType t, int amp, int ticks) {
        Effects.give(e, t, amp, ticks);
        add(by, "negative_effects", 1);
    }

    static void immobilize(Player by, LivingEntity e, int ticks) {
        Effects.root(e, ticks);
        add(by, "immobilized", 1);
        Msg.actionBar(by, "<yellow>" + safeName(e) + " immobilise " + (ticks / 20.0) + " s.");
        e.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, e.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0.1);
        e.getWorld().playSound(e.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.8f);
        Effects.say(e, "<yellow>Immobilise !");
    }

    static final Map<UUID, Long> FROZEN = new HashMap<>();

    /** Gel temporel : immobile, sans pouvoir frapper, en gris. */
    static void freeze(Player by, LivingEntity e, int ticks, boolean count) {
        Effects.root(e, ticks);
        FROZEN.put(e.getUniqueId(), System.currentTimeMillis() + ticks * 50L);
        if (count) add(by, "immobilized", 1);
        Effects.say(e, "<gray>Gele dans le temps !");
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t += 4;
                if (t > ticks || e.isDead()) { cancel(); return; }
                e.getWorld().spawnParticle(Particle.DUST, e.getLocation().add(0, 1, 0), 6, 0.4, 0.6, 0.4, 0, GREY);
            }
        }.runTaskTimer(Solo.plugin(), 0L, 4L);
    }
    static boolean isFrozen(LivingEntity e) { Long u = FROZEN.get(e.getUniqueId()); return u != null && System.currentTimeMillis() < u; }

    /** Zone de feu carree sur le sol, pendant quelques secondes. */
    static void fireZone(Player p, Location c, int half, int seconds, String key) {
        World w = c.getWorld();
        TempBlocks tb = new TempBlocks();
        for (int x = -half; x <= half; x++) for (int z = -half; z <= half; z++) {
            int gy = Effects.groundAround(w, c.getBlockX() + x, c.getBlockZ() + z, c.getBlockY());
            if (gy == Integer.MIN_VALUE) continue;
            Block b = w.getBlockAt(c.getBlockX() + x, gy + 1, c.getBlockZ() + z);
            if (b.getType().isAir()) tb.place(b, Material.FIRE);
        }
        tb.restoreAfter(seconds * 20L);
        w.playSound(c, Sound.ITEM_FIRECHARGE_USE, 1f, 0.9f);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t += 10;
                if (t > seconds * 20) { cancel(); return; }
                for (LivingEntity e : Effects.within(c, half + 1.0, le -> Effects.isEnemy(p, le))) {
                    if (Math.abs(e.getLocation().getX() - c.getX()) <= half + 0.8 && Math.abs(e.getLocation().getZ() - c.getZ()) <= half + 0.8 && Math.abs(e.getLocation().getY() - c.getY()) <= 2.5) ignite(p, e, 80, key);
                }
                w.spawnParticle(Particle.FLAME, c.clone().add(0, 0.5, 0), 6 * (half + 1), half * 0.6, 0.3, half * 0.6, 0.01);
            }
        }.runTaskTimer(Solo.plugin(), 0L, 10L);
    }

    /** Orbe : une boule de neige taguee, suivie d'une trainee ; l'impact est traite par le Tracker. */
    static void orb(Player p, String nodeId, Particle trail, Object trailData, double speed) {
        Vector dir = p.getEyeLocation().getDirection().normalize();
        Snowball s = p.launchProjectile(Snowball.class, dir.multiply(speed));
        s.getPersistentDataContainer().set(ORB, PersistentDataType.STRING, nodeId);
        s.setGravity(false);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                if (!s.isValid() || t > 60) { if (s.isValid()) s.remove(); cancel(); return; }
                if (trailData != null) s.getWorld().spawnParticle(trail, s.getLocation(), 3, 0.1, 0.1, 0.1, 0, trailData);
                else s.getWorld().spawnParticle(trail, s.getLocation(), 3, 0.1, 0.1, 0.1, 0.02);
            }
        }.runTaskTimer(Solo.plugin(), 0L, 1L);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 0.8f);
    }

    /** Rayon instantane : le premier ennemi vise a {@code range} blocs. */
    static LivingEntity rayTarget(Player p, double range, double width) {
        RayTraceResult hit = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), range, width, e -> e instanceof LivingEntity le && Effects.isEnemy(p, le));
        return hit != null && hit.getHitEntity() instanceof LivingEntity le ? le : null;
    }

    /** Tous les ennemis dans un couloir devant le joueur. */
    static List<LivingEntity> corridor(Player p, double length, double halfWidth) {
        Vector dir = p.getEyeLocation().getDirection().setY(0).normalize();
        Location o = p.getLocation();
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : Effects.enemiesWithin(p, length + 2)) {
            Vector rel = e.getLocation().toVector().subtract(o.toVector()).setY(0);
            double along = rel.dot(dir);
            if (along < 0 || along > length) continue;
            double side = rel.clone().subtract(dir.clone().multiply(along)).length();
            if (side <= halfWidth + 0.5) out.add(e);
        }
        return out;
    }

    static void line(Player p, double length, Particle particle, Object data) {
        Vector dir = p.getEyeLocation().getDirection().normalize();
        Location l = p.getEyeLocation();
        for (double d = 0.5; d < length; d += 0.5) {
            Location at = l.clone().add(dir.clone().multiply(d));
            if (at.getBlock().getType().isOccluding()) break;
            if (data != null) p.getWorld().spawnParticle(particle, at, 1, 0, 0, 0, 0, data); else p.getWorld().spawnParticle(particle, at, 1, 0, 0, 0, 0);
        }
    }

    /** Mines : position, capacite, expiration. */
    record Mine(UUID owner, Location at, String nodeId, long until) {}
    static final List<Mine> MINES = new ArrayList<>();

    static void mine(Player p, String nodeId, int lifeSeconds) {
        Location at = ahead(p, 2);
        MINES.add(new Mine(p.getUniqueId(), at, nodeId, System.currentTimeMillis() + lifeSeconds * 1000L));
        p.getWorld().playSound(at, Sound.BLOCK_BUBBLE_COLUMN_UPWARDS_INSIDE, 1f, 0.6f);
        Msg.actionBar(p, "<aqua>Mine posee (" + lifeSeconds + " s).");
    }

    /** Verifie les mines : appele chaque seconde par Shoja, puis a chaque tick pendant qu'il y en a. */
    static void tickMines() {
        long now = System.currentTimeMillis();
        for (Mine m : new ArrayList<>(MINES)) {
            Player owner = com.kiokouwhite.crossfight.util.MannequinPlayer.getPlayer(m.owner);
            if (now > m.until || owner == null || !owner.isOnline()) { MINES.remove(m); continue; }
            owner.spawnParticle(Particle.BUBBLE_POP, m.at.clone().add(0, 0.3, 0), 2, 0.3, 0.1, 0.3, 0);
            for (LivingEntity e : Effects.within(m.at, 1.3, le -> Effects.isEnemy(owner, le))) {
                MINES.remove(m);
                add(owner, "mines_exploded", 1);
                World w = m.at.getWorld();
                w.spawnParticle(Particle.SPLASH, m.at.clone().add(0, 0.5, 0), 80, 0.8, 0.5, 0.8, 0.2);
                w.spawnParticle(Particle.EXPLOSION, m.at.clone().add(0, 0.5, 0), 1);
                w.playSound(m.at, Sound.ENTITY_GENERIC_SPLASH, 1.2f, 0.7f);
                e.setNoDamageTicks(0);
                switch (m.nodeId) {
                    case "mine_sousmarine" -> { Effects.damage(e, 4.0, owner); slow(owner, e, 0, 200); }
                    case "piege_meduse" -> { slow(owner, e, 3, 200); negative(owner, e, PotionEffectType.WEAKNESS, 3, 200); }
                    case "bombe_poisson_globe" -> { Effects.damage(e, 8.0, owner); negative(owner, e, PotionEffectType.NAUSEA, 0, 200); }
                    default -> { }
                }
                break;
            }
        }
    }

    /** Creuse un trou (temporaire) : colonnes de {@code depth} blocs sous le sol. */
    static void hole(Player p, Location c, int halfX, int halfZ, Vector dir, int depth, int seconds) {
        World w = c.getWorld();
        TempBlocks tb = new TempBlocks();
        // Le trou s'etend dans la direction du regard : halfX de cote, halfZ devant/derriere (dans le repere du joueur).
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        for (int i = -halfX; i <= halfX; i++) for (int j = -halfZ; j <= halfZ; j++) {
            Location at = c.clone().add(side.clone().multiply(i)).add(dir.clone().multiply(j));
            int gy = Effects.groundAround(w, at.getBlockX(), at.getBlockZ(), c.getBlockY());
            if (gy == Integer.MIN_VALUE) continue;
            for (int k = 0; k < depth; k++) {
                Block b = w.getBlockAt(at.getBlockX(), gy - k, at.getBlockZ());
                if (b.getType() == Material.BEDROCK) break;
                if (b.getType().isAir()) continue;
                tb.replace(b, Material.AIR);
            }
        }
        tb.restoreAfter(seconds * 20L);
        w.spawnParticle(Particle.BLOCK, c.clone().add(0, 0.5, 0), 120, halfX + 1, 0.5, halfZ + 1, 0, Material.DIRT.createBlockData());
        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
        w.playSound(c, Sound.BLOCK_GRAVEL_BREAK, 1.5f, 0.6f);
        Msg.actionBar(p, "<dark_purple>Le sol s'ouvre pendant " + seconds + " s.");
    }

    /** Mur de terre devant le joueur. */
    static void wall(Player p, int length, int height, int thick, int seconds) {
        World w = p.getWorld();
        Vector dir = p.getLocation().getDirection().setY(0).normalize();
        Vector side = new Vector(-dir.getZ(), 0, dir.getX());
        Location base = p.getLocation().add(dir.clone().multiply(3));
        TempBlocks tb = new TempBlocks();
        for (int i = -length / 2; i <= length / 2; i++) for (int t = 0; t < thick; t++) {
            Location at = base.clone().add(side.clone().multiply(i)).add(dir.clone().multiply(t));
            int gy = Effects.groundAround(w, at.getBlockX(), at.getBlockZ(), base.getBlockY());
            if (gy == Integer.MIN_VALUE) continue;
            for (int y = 1; y <= height; y++) {
                Block b = w.getBlockAt(at.getBlockX(), gy + y, at.getBlockZ());
                if (!b.getType().isAir() && !b.isReplaceable()) continue;
                tb.place(b, y == height ? Material.GRASS_BLOCK : Material.DIRT);
            }
        }
        tb.restoreAfter(seconds * 20L);
        w.spawnParticle(Particle.BLOCK, base.clone().add(0, 2, 0), 200, length / 2.0, 3, 1, 0, Material.DIRT.createBlockData());
        w.playSound(base, Sound.BLOCK_ROOTED_DIRT_PLACE, 2f, 0.5f);
    }

    /** La "nouvelle dimension" : une arene de pierre de l'End. */
    static final Map<UUID, Location> DIM_RETURN = new HashMap<>();

    static World dimension() {
        World w = ArenaWorlds.get(DIM_WORLD);
        ArenaWorlds.buildPlatform(w, 15, Material.END_STONE, Material.BARRIER, 6);
        return w;
    }

    static void sendToDimension(Player shoja, List<LivingEntity> who, int seconds) {
        World dim = dimension();
        Location c = ArenaWorlds.center(dim);
        java.util.Random r = new java.util.Random();
        for (LivingEntity e : who) {
            if (e.getWorld().getName().equals(DIM_WORLD)) continue;
            DIM_RETURN.put(e.getUniqueId(), e.getLocation().clone());
            e.getWorld().spawnParticle(Particle.PORTAL, e.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.5);
            e.teleport(c.clone().add(r.nextInt(-8, 9), 0, r.nextInt(-8, 9)));
            e.getWorld().playSound(e.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);
            if (e instanceof Player ep) Msg.send(ep, "<dark_purple><bold>Nouvelle Dimension :</bold> <gray>Shoja vous envoie ailleurs pendant " + seconds + " s.");
        }
        Bukkit.getScheduler().runTaskLater(Solo.plugin(), () -> { for (LivingEntity e : who) returnFromDimension(e); }, seconds * 20L);
    }

    static void returnFromDimension(LivingEntity e) {
        Location back = DIM_RETURN.remove(e.getUniqueId());
        if (back == null || e.isDead() || !e.isValid() || !e.getWorld().getName().equals(DIM_WORLD)) return;
        e.teleport(back);
        back.getWorld().spawnParticle(Particle.PORTAL, back.clone().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.5);
    }

    static void returnAllFromDimension() {
        World dim = Bukkit.getWorld(DIM_WORLD);
        if (dim != null) for (LivingEntity e : new ArrayList<>(dim.getLivingEntities())) returnFromDimension(e);
        DIM_RETURN.clear();
    }

    /** Fait tomber un equipement au sol. */
    static boolean strip(LivingEntity e, org.bukkit.inventory.EquipmentSlot slot) {
        var eq = e.getEquipment();
        if (eq == null) return false;
        ItemStack it = eq.getItem(slot);
        if (it == null || it.getType().isAir()) return false;
        if (e instanceof Player ep) {
            var left = ep.getInventory().addItem(it.clone());
            for (ItemStack rest : left.values()) e.getWorld().dropItemNaturally(e.getLocation(), rest);
        } else if (!Mannequins.isMannequin(e)) e.getWorld().dropItemNaturally(e.getLocation(), it.clone());
        eq.setItem(slot, null);
        e.getWorld().spawnParticle(Particle.ITEM, e.getLocation().add(0, 1.2, 0), 15, 0.3, 0.3, 0.3, 0.1, it.clone());
        return true;
    }

    static final Map<UUID, Deque<Double>> HEALTH_HISTORY = new HashMap<>();
    static final Map<UUID, Long> BOURRASQUE = new HashMap<>();
    static final Map<UUID, Long> UNEXTINGUISHABLE = new HashMap<>();
    static final Map<UUID, Integer> WIND_CHARGES = new HashMap<>();

    /** Appele chaque seconde par Shoja : historique de vie, nage, feu inextinguible, mines. */
    static void tickShoja(Player p) {
        Deque<Double> h = HEALTH_HISTORY.computeIfAbsent(p.getUniqueId(), u -> new ArrayDeque<>());
        h.addLast(p.getHealth());
        while (h.size() > 6) h.removeFirst();
        if (p.isInWater()) add(p, "swim_s", 1);
        if (p.isInLava()) add(p, "lava_s", 1);
        long now = System.currentTimeMillis();
        for (var it = UNEXTINGUISHABLE.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            if (now > en.getValue()) { it.remove(); continue; }
            var e = Bukkit.getEntity(en.getKey());
            if (e instanceof LivingEntity le && !le.isDead()) le.setFireTicks(Math.max(le.getFireTicks(), 40));
        }
        if (!MINES.isEmpty()) tickMines();
    }

    static double healthAgo(Player p) {
        Deque<Double> h = HEALTH_HISTORY.get(p.getUniqueId());
        return h == null || h.isEmpty() ? p.getHealth() : h.peekFirst();
    }

    static void rewind(Player p, double bonus) {
        double target = Math.min(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(), healthAgo(p) + bonus);
        p.setHealth(Math.max(1.0, target));
        p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0, new Particle.DustOptions(Color.fromRGB(255, 120, 220), 1.2f));
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.6f);
    }

    static void pull(Player p, LivingEntity e, double strength) {
        Vector v = p.getLocation().toVector().subtract(e.getLocation().toVector());
        double d = v.length();
        if (d < 1.5) return;
        e.setVelocity(v.normalize().multiply(Math.min(3.0, strength)).setY(0.3));
        e.getWorld().spawnParticle(Particle.CLOUD, e.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);
        e.getWorld().playSound(e.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1.2f);
    }

    // ------------------------------------------------------------- les noeuds

    static {
        // ---------------------------------------------------------------- FEU
        node("inflammation", "Inflammation", Element.FEU, Material.FLINT_AND_STEEL, 5, "Enflamme une zone de 3x3 devant Shoja pendant 5 secondes.", need("craft_flint_steel", 1, "crafter un briquet"));
        node("incendie", "Incendie", Element.FEU, Material.FIRE_CHARGE, 3, "Enflamme une zone de 5x5 devant Shoja pendant 5 secondes.", need("ign_inflammation", 10, "enflammer 10 ennemis avec Inflammation"));
        node("explosion_infernale", "Explosion Infernale", Element.FEU, Material.TNT, 35, "Lance une boule de feu : a l'impact, explosion de 4 coeurs et zone enflammee de 10x10.", need("craft_tnt", 10, "crafter 10 TNT"), need("ign_total", 10, "enflammer 10 ennemis"));
        node("mer_de_magma", "Mer de Magma", Element.FEU, Material.LAVA_BUCKET, 20, "Un lac de lave de 10x10 devant Shoja pendant 10 secondes ; Shoja a Resistance au feu 15 secondes.", need("lava_s", 60, "nager 1 minute dans la lave"), need("ign_incendie", 10, "enflammer 10 ennemis avec Incendie"));
        node("epee_enflammee", "Epee Enflammee", Element.FEU, Material.BLAZE_ROD, 0, "Passif : un coup sur trois enflamme l'ennemi 4 secondes.", need("sword_fire_aspect", 1, "enchanter une epee avec Aura de feu"));
        node("lame_ardente", "Lame Ardente", Element.FEU, Material.BLAZE_POWDER, 0, "Passif : chaque coup enflamme l'ennemi 3 secondes.", need("ign_total", 50, "enflammer 50 ennemis"));
        node("absorption_ardente", "Absorption Ardente", Element.FEU, Material.MAGMA_CREAM, 15, "Pendant 5 secondes, tous les coups de Shoja enflamment 5 secondes et elle a Force II.", need("sword_fire_aspect2", 2, "enchanter 2 epees avec Aura de feu II"), need("ign_total", 30, "enflammer 30 ennemis"));
        node("fer_brulant", "Fer Brulant", Element.FEU, Material.IRON_CHESTPLATE, 0, "Passif : tous les 5 coups recus, l'attaquant s'enflamme 5 secondes.", need("chest_fire_prot", 1, "enchanter un plastron avec Protection contre le feu"));
        node("armure_bouillante", "Armure Bouillante", Element.FEU, Material.MAGMA_BLOCK, 0, "Passif : chaque attaquant qui touche Shoja s'enflamme 2 secondes.", need("hits_taken", 100, "recevoir 100 coups"));
        node("brulure_instantanee", "Brulure Instantanee", Element.FEU, Material.CAMPFIRE, 10, "Pendant la seconde qui suit, l'ennemi qui frappe Shoja brule 10 secondes sans pouvoir s'eteindre.", need("hits_taken", 50, "recevoir 50 coups"), need("ign_total", 20, "enflammer 20 ennemis"));

        // ---------------------------------------------------------------- EAU
        node("pistolet_a_eau", "Pistolet a Eau", Element.EAU, Material.GLASS_BOTTLE, 15, "Projectile d'eau : Lenteur pendant 5 secondes.", need("craft_bucket", 5, "crafter 5 seaux"));
        node("fusil_a_eau", "Fusil a Eau", Element.EAU, Material.POTION, 15, "Projectile d'eau : Lenteur II pendant 10 secondes.", need("craft_bucket", 30, "crafter 30 seaux"));
        node("sniper_a_eau", "Sniper a Eau", Element.EAU, Material.SPYGLASS, 10, "Tir instantane a 40 blocs : Lenteur II 10 secondes et 2 coeurs de degats.", uses("fusil_a_eau", 50));
        node("pompe_a_eau", "Pompe a Eau", Element.EAU, Material.PRISMARINE_SHARD, 15, "Trois jets d'eau de 5 blocs devant Shoja, 3.5 coeurs au total.", need("fusil_close_hits", 5, "toucher 5 ennemis a moins de 3 blocs avec le Fusil a Eau"));
        node("mine_sousmarine", "Mine sous-marine", Element.EAU, Material.HEART_OF_THE_SEA, 10, "Pose une mine invisible (30 s) : 2 coeurs et Lenteur 10 secondes.", need("swim_s", 300, "nager 5 minutes"));
        node("piege_meduse", "Piege Meduse", Element.EAU, Material.SLIME_BALL, 5, "Pose une mine (1 min) : Lenteur IV et Faiblesse IV pendant 10 secondes.", need("mines_exploded", 50, "faire exploser 50 mines"));
        node("bombe_poisson_globe", "Bombe Poisson-Globe", Element.EAU, Material.PUFFERFISH, 15, "Pose une mine (1 min) : 4 coeurs et Nausee 10 secondes.", need("mines_exploded", 30, "faire exploser 30 mines"), need("pufferfish_eaten", 2, "manger 2 poissons-globes"));
        node("protection_aqueuse", "Protection Aqueuse", Element.EAU, Material.TURTLE_HELMET, 0, "Passif : chaque attaquant recoit Lenteur 3 secondes.", need("drown_dmg", 9, "perdre 9 coeurs en se noyant"));
        node("plastron_noyade", "Plastron Noyade", Element.EAU, Material.PRISMARINE_CRYSTALS, 0, "Passif : chaque attaquant recoit Lenteur II et Faiblesse II 5 secondes.", need("slow_inflicted", 50, "infliger Lenteur 50 fois"));
        node("epine_de_mer", "Epine de Mer", Element.EAU, Material.SEA_PICKLE, 0, "Passif : chaque attaquant recoit Lenteur 5 secondes et perd 1 coeur.", need("hits_on_slowed", 70, "frapper 70 fois un ennemi ralenti"));

        // ------------------------------------------------------------- FOUDRE
        node("boule_electrifiante", "Boule Electrifiante", Element.FOUDRE, Material.GLOWSTONE_DUST, 10, "Boule d'energie (10 blocs) : l'ennemi touche est immobilise 2 secondes.", need("immobilized", 20, "immobiliser 20 fois"));
        node("rayon_electrique", "Rayon Electrique", Element.FOUDRE, Material.END_ROD, 15, "Rayon de 10 blocs : immobilise 2 secondes et inflige 3 coeurs.", need("boule_hits", 30, "toucher 30 fois avec Boule Electrifiante"));
        node("scie_circulaire", "Scie Circulaire", Element.FOUDRE, Material.HEAVY_CORE, 30, "Lance une scie electrique : 4 coeurs et 1 seconde d'immobilisation.", need("immobilized", 10, "immobiliser 10 fois"), need("dmg_dealt", 30, "infliger 30 coeurs"));
        node("sol_electrifiant", "Sol Electrifiant", Element.FOUDRE, Material.COPPER_BLOCK, 30, "Pendant 5 secondes, les ennemis a 10 blocs de Shoja sont immobilises.", uses("boule_electrifiante", 50));
        node("foudroiement_circulaire", "Foudroiement Circulaire", Element.FOUDRE, Material.LIGHTNING_ROD, 20, "Un eclair frappe a 3 blocs devant Shoja : les ennemis a 3 blocs sont immobilises 3 secondes et perdent 2 coeurs.", need("creeper_kills", 1, "tuer un Creeper"));
        node("foudre_incontrolable", "Foudre Incontrolable", Element.FOUDRE, Material.TRIDENT, 15, "Shoja perd 3 coeurs et est projetee en arriere : une onde de 3 blocs de large part devant elle et inflige 5 coeurs.", need("dmg_taken", 100, "perdre 100 coeurs"));
        node("explosion_denergie", "Explosion d'Energie", Element.FOUDRE, Material.NETHER_STAR, 10, "Shoja est immobile 5 secondes : les ennemis a 10 blocs perdent 1 coeur chaque demi-seconde.", need("dmg_dealt", 100, "infliger 100 coeurs"));
        node("armure_electrique", "Armure Electrique", Element.FOUDRE, Material.CHAINMAIL_CHESTPLATE, 0, "Passif : tous les 5 coups recus, l'attaquant est immobilise 3 secondes.", need("explosions_taken", 10, "subir 10 explosions"));
        node("electrocution_imminente", "Electrocution Imminente", Element.FOUDRE, Material.REDSTONE, 0, "Passif : chaque attaquant recoit Lenteur 10 secondes.", need("immobilized", 50, "immobiliser 50 fois"));

        // --------------------------------------------------------------- VENT
        node("rafale_de_vent", "Rafale de Vent", Element.VENT, Material.FEATHER, 10, "Repousse l'ennemi le plus proche (10 blocs) de 5 blocs.", need("sword_knockback", 1, "enchanter une epee avec Recul"));
        node("tornade_repoussante", "Tornade Repoussante", Element.VENT, Material.WIND_CHARGE, 15, "Repousse tous les ennemis a 10 blocs de 10 blocs.", need("sword_knockback2", 3, "enchanter 3 epees avec Recul II"), uses("rafale_de_vent", 5));
        node("oeil_du_cyclone", "Oeil du Cyclone", Element.VENT, Material.BREEZE_ROD, 15, "Projette tous les ennemis a 10 blocs 30 blocs en l'air.", uses("tornade_repoussante", 50));
        node("bourrasque_attirante", "Bourrasque Attirante", Element.VENT, Material.STRING, 10, "Attire l'ennemi le plus proche (10 blocs) vers Shoja.", need("rafale_close_hits", 10, "repousser 10 ennemis a moins de 5 blocs avec Rafale de Vent"));
        node("cyclone_amenant", "Cyclone Amenant", Element.VENT, Material.LEAD, 5, "Attire violemment l'ennemi le plus proche (30 blocs) vers Shoja.", need("bourrasque_hits", 50, "attirer 50 ennemis avec Bourrasque Attirante"));
        node("attraction_lacerante", "Attraction Lacerante", Element.VENT, Material.FISHING_ROD, 15, "Agrippe l'ennemi le plus proche (10 blocs) : attire d'1 bloc par seconde et inflige 1 coeur par seconde pendant 5 secondes.", need("dmg_on_bourrasque", 100, "infliger 100 coeurs a des ennemis attires"));
        node("vent_tranchant", "Vent Tranchant", Element.VENT, Material.GOLDEN_SWORD, 10, "Lame de vent de 10 blocs devant Shoja : 2 coeurs.", need("sword_sharp2", 1, "enchanter une epee avec Tranchant II"), uses("rafale_de_vent", 5));
        node("tornade_destructrice", "Tornade Destructrice", Element.VENT, Material.DIAMOND_SWORD, 10, "Lame de vent de 10 blocs devant Shoja : 4 coeurs.", uses("vent_tranchant", 70));
        node("epee_eventail", "Epee Eventail", Element.VENT, Material.NETHERITE_SWORD, 15, "Force IV pendant 5 secondes.", need("sword_sharp5", 1, "enchanter une epee avec Tranchant V"), uses("tornade_destructrice", 50));
        node("armure_de_vent", "Armure de Vent", Element.VENT, Material.ELYTRA, 0, "Passif : 5 charges annulent chacune un coup recu ; une charge revient toutes les 3 secondes.", need("armor_projprot4", 1, "enchanter une armure avec Protection contre les projectiles IV"), need("hits_taken", 200, "recevoir 200 coups"));

        // ------------------------------------------------------------- ESPACE
        node("division_du_territoire", "Division du Territoire", Element.ESPACE, Material.IRON_SHOVEL, 20, "Ouvre un trou de 3x3 sur 10 de profondeur devant Shoja (20 s).", need("blocks_broken", 100, "casser 100 blocs"));
        node("crevasse", "Crevasse", Element.ESPACE, Material.DIAMOND_PICKAXE, 30, "Ouvre une crevasse de 10 blocs de long, 4 de large et 10 de profondeur (20 s).", uses("division_du_territoire", 30));
        node("puits_sans_fond", "Puits sans Fond", Element.ESPACE, Material.BLACK_CONCRETE, 15, "Ouvre un puits de 5x5 sur 30 de profondeur devant Shoja (20 s).", need("fall_dmg", 10, "perdre 10 coeurs en tombant"), uses("crevasse", 50));
        node("soulevement_terrestre", "Soulevement Terrestre", Element.ESPACE, Material.DIRT, 10, "Un mur de terre de 30 blocs de long et 10 de haut se dresse devant Shoja (20 s).", need("blocks_broken", 300, "casser 300 blocs"), uses("crevasse", 50));
        node("nouvelle_dimension", "Nouvelle Dimension", Element.ESPACE, Material.END_STONE, 60, "Envoie l'ennemi le plus proche (10 blocs) dans une autre dimension pendant 30 secondes.", need("blocks_broken", 300, "casser 300 blocs"), need("nether_portal", 1, "passer un portail du Nether"), uses("division_du_territoire", 30));
        node("espace_incapacitant", "Espace Incapacitant", Element.ESPACE, Material.END_CRYSTAL, 60, "Shoja et l'ennemi le plus proche (10 blocs) partent 30 secondes dans une autre dimension : l'ennemi a Faiblesse II, Shoja Force II.", uses("nouvelle_dimension", 30), need("negative_effects", 10, "infliger 10 effets negatifs"));
        node("vide_interstellaire", "Vide Interstellaire", Element.ESPACE, Material.DRAGON_EGG, 90, "Envoie l'ennemi le plus proche (10 blocs) dans une autre dimension pendant 1 minute.", uses("nouvelle_dimension", 50));
        node("legere_suppression", "Legere Suppression", Element.ESPACE, Material.LEATHER_HELMET, 30, "Rayon de 3 blocs de large et 5 de long : Lenteur II 5 secondes et l'ennemi perd son casque.", need("negative_effects", 10, "infliger 10 effets negatifs"), uses("division_du_territoire", 30));
        node("souffle_destructeur", "Souffle Destructeur", Element.ESPACE, Material.ARMOR_STAND, 40, "L'ennemi le plus proche (10 blocs) perd tout son equipement.", uses("legere_suppression", 50), need("armor_broken", 1, "casser une piece de sa propre armure"));
        node("incapacitation", "Incapacitation", Element.ESPACE, Material.STRUCTURE_VOID, 60, "L'ennemi le plus proche (10 blocs) ne peut plus utiliser de capacites pendant 1 minute.", need("abilities_taken", 50, "subir 50 capacites ou ultimes"));

        // -------------------------------------------------------------- TEMPS
        node("panne_dhorloge", "Panne d'Horloge", Element.TEMPS, Material.CLOCK, 30, "Gele les ennemis a 3 blocs pendant 3 secondes.", need("craft_clock", 1, "crafter une horloge"));
        node("boom_temporel", "Boom Temporel", Element.TEMPS, Material.REPEATER, 30, "Gele les ennemis a 10 blocs pendant 5 secondes.", need("dmg_taken", 30, "perdre 30 coeurs"), need("craft_clock", 10, "crafter 10 horloges"));
        node("explosion_du_temps", "Explosion du Temps", Element.TEMPS, Material.COMPARATOR, 30, "Gele les ennemis a 30 blocs pendant 7 secondes.", uses("boom_temporel", 70));
        node("le_temps_est_incontrolable", "Le temps est incontrolable", Element.TEMPS, Material.DAYLIGHT_DETECTOR, 20, "Gele les ennemis a 10 blocs pendant 5 secondes : ils perdent 1 coeur par seconde.", uses("panne_dhorloge", 30));
        node("lorem_ipsum", "Lorem Ipsum", Element.TEMPS, Material.WRITABLE_BOOK, 20, "Gele les ennemis a 30 blocs pendant 10 secondes : ils perdent 1 coeur par seconde.", uses("le_temps_est_incontrolable", 70));
        node("cogito_ergo_sum", "Cogito Ergo Sum", Element.TEMPS, Material.SCULK_SENSOR, 20, "Gele les ennemis a 10 blocs pendant 4 secondes : 3 coeurs toutes les 2 secondes ; Shoja perd 4 coeurs.", need("dmg_taken", 50, "perdre 50 coeurs"), uses("le_temps_est_incontrolable", 50));
        node("back_to_back", "Back to Back", Element.TEMPS, Material.RECOVERY_COMPASS, 45, "La vie de Shoja revient a celle d'il y a 5 secondes.", need("regen", 40, "regenerer 40 coeurs"));
        node("tic_tac", "Tic Tac", Element.TEMPS, Material.GOLDEN_APPLE, 30, "La vie de Shoja revient a celle d'il y a 5 secondes, plus 5 coeurs.", need("regen", 80, "regenerer 80 coeurs"), uses("back_to_back", 50));
        node("ding_dong", "Ding Dong", Element.TEMPS, Material.BELL, 30, "La vie de Shoja revient a celle d'il y a 5 secondes, et les ennemis a 10 blocs perdent 5 coeurs.", need("dmg_taken", 80, "perdre 80 coeurs"), uses("back_to_back", 50));
        node("aura_de_lephemere", "Aura de l'Ephemere", Element.TEMPS, Material.AMETHYST_SHARD, 0, "Passif : tous les 5 coups recus, l'attaquant est gele 2 secondes.", need("dmg_taken", 50, "perdre 50 coeurs"), uses("boom_temporel", 50));

        bind();
    }

    static Particle.DustOptions dust(int r, int g, int b) { return new Particle.DustOptions(Color.fromRGB(r, g, b), 1.0f); }

    /** Relie chaque noeud a son effet. */
    static void bind() {
        // FEU
        act("inflammation", (p, cf, st) -> { fireZone(p, ahead(p, 4), 1, 5, "ign_inflammation"); return true; });
        act("incendie", (p, cf, st) -> { fireZone(p, ahead(p, 5), 2, 5, "ign_incendie"); return true; });
        act("explosion_infernale", (p, cf, st) -> {
            Fireball f = p.launchProjectile(Fireball.class, p.getEyeLocation().getDirection().multiply(1.5));
            f.setIsIncendiary(false); f.setYield(0f);
            f.getPersistentDataContainer().set(ORB, PersistentDataType.STRING, "explosion_infernale");
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 0.6f);
            return true;
        });
        act("mer_de_magma", (p, cf, st) -> {
            Location c = ahead(p, 7);
            World w = c.getWorld();
            TempBlocks tb = new TempBlocks();
            for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
                int gy = Effects.groundAround(w, c.getBlockX() + x, c.getBlockZ() + z, c.getBlockY());
                if (gy == Integer.MIN_VALUE) continue;
                tb.replace(w.getBlockAt(c.getBlockX() + x, gy, c.getBlockZ() + z), Material.LAVA);
            }
            tb.restoreAfter(200L);
            Effects.give(p, PotionEffectType.FIRE_RESISTANCE, 0, 300);
            w.playSound(c, Sound.ITEM_BUCKET_EMPTY_LAVA, 2f, 0.6f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (t > 10) { cancel(); return; }
                    for (LivingEntity e : Effects.within(c, 8, le -> Effects.isEnemy(p, le))) if (e.getLocation().getBlock().getType() == Material.LAVA || e.isInLava()) ignite(p, e, 100, "ign_magma");
                }
            }.runTaskTimer(Solo.plugin(), 10L, 20L);
            Msg.actionBar(p, "<red>Mer de Magma : Resistance au feu 15 s.");
            return true;
        });
        passive("epee_enflammee").deal((s, v, e, st) -> { int n = st.get("n", 0) + 1; st.set("n", n % 3); if (n % 3 == 0) ignite(s, v, 80, "ign_epee"); });
        passive("lame_ardente").deal((s, v, e, st) -> ignite(s, v, 60, "ign_lame"));
        act("absorption_ardente", (p, cf, st) -> {
            st.set("until", System.currentTimeMillis() + 5000L);
            Boosts.set(p, "shoja_absorption", new Boosts.Boost(2, 0, 0, 0));
            Bukkit.getScheduler().runTaskLater(Solo.plugin(), () -> Boosts.remove(p, "shoja_absorption"), 100L);
            p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.05);
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 0.7f);
            return true;
        });
        NODES.get("absorption_ardente").ability = new Active(NODES.get("absorption_ardente"), ((Active) NODES.get("absorption_ardente").ability).act) {
            @Override public void onDealDamage(Player a, LivingEntity v, EntityDamageByEntityEvent e, AbilityState st) { if (System.currentTimeMillis() < st.get("until", 0L) && Effects.isEnemy(a, v)) ignite(a, v, 100, "ign_absorption"); }
        };
        passive("fer_brulant").taken((s, a, e, st) -> { int n = st.get("n", 0) + 1; st.set("n", n % 5); if (n % 5 == 0) ignite(s, a, 100, "ign_fer"); });
        passive("armure_bouillante").taken((s, a, e, st) -> ignite(s, a, 40, "ign_armure"));
        act("brulure_instantanee", (p, cf, st) -> { st.set("until", System.currentTimeMillis() + 1000L); p.getWorld().spawnParticle(Particle.LAVA, p.getLocation().add(0, 1, 0), 15, 0.4, 0.5, 0.4, 0); Msg.actionBar(p, "<red>Brulure Instantanee : 1 seconde !"); return true; });
        NODES.get("brulure_instantanee").ability = new Active(NODES.get("brulure_instantanee"), ((Active) NODES.get("brulure_instantanee").ability).act) {
            @Override public void onDamaged(Player v, EntityDamageEvent e, AbilityState st) {
                if (System.currentTimeMillis() >= st.get("until", 0L) || !(e instanceof EntityDamageByEntityEvent be)) return;
                LivingEntity a = DamageListener.livingAttackerOf(be.getDamager());
                if (a == null || a == v || !Effects.isEnemy(v, a)) return;
                ignite(v, a, 200, "ign_brulure");
                UNEXTINGUISHABLE.put(a.getUniqueId(), System.currentTimeMillis() + 10000L);
                Effects.say(a, "<red>Brulure Instantanee : 10 s de feu inextinguible !");
            }
        };

        // EAU
        act("pistolet_a_eau", (p, cf, st) -> { orb(p, "pistolet_a_eau", Particle.SPLASH, null, 1.6); return true; });
        act("fusil_a_eau", (p, cf, st) -> { orb(p, "fusil_a_eau", Particle.SPLASH, null, 2.0); return true; });
        act("sniper_a_eau", (p, cf, st) -> {
            LivingEntity t = rayTarget(p, 40, 0.6);
            line(p, 40, Particle.BUBBLE_POP, null);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_DROWNED_SHOOT, 1f, 1.5f);
            if (t == null) { Msg.actionBar(p, "<red>Sniper a Eau : aucun ennemi vise."); return false; }
            t.setNoDamageTicks(0); Effects.damage(t, 4.0, p); slow(p, t, 1, 200);
            return true;
        });
        act("pompe_a_eau", (p, cf, st) -> {
            for (int i = 0; i < 3; i++) Bukkit.getScheduler().runTaskLater(Solo.plugin(), () -> {
                line(p, 5, Particle.SPLASH, null);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_SPLASH, 1f, 1.2f);
                for (LivingEntity e : corridor(p, 5, 1.0)) { e.setNoDamageTicks(0); Effects.damage(e, 2.4, p); Effects.knockbackFrom(e, p.getLocation(), 2); }
            }, i * 6L);
            return true;
        });
        act("mine_sousmarine", (p, cf, st) -> { mine(p, "mine_sousmarine", 30); return true; });
        act("piege_meduse", (p, cf, st) -> { mine(p, "piege_meduse", 60); return true; });
        act("bombe_poisson_globe", (p, cf, st) -> { mine(p, "bombe_poisson_globe", 60); return true; });
        passive("protection_aqueuse").taken((s, a, e, st) -> slow(s, a, 0, 60));
        passive("plastron_noyade").taken((s, a, e, st) -> { slow(s, a, 1, 100); negative(s, a, PotionEffectType.WEAKNESS, 1, 100); });
        passive("epine_de_mer").taken((s, a, e, st) -> { slow(s, a, 0, 100); Bukkit.getScheduler().runTask(Solo.plugin(), () -> { a.setNoDamageTicks(0); Effects.damage(a, 2.0, s); }); });

        // FOUDRE
        act("boule_electrifiante", (p, cf, st) -> { orb(p, "boule_electrifiante", Particle.ELECTRIC_SPARK, null, 1.4); return true; });
        act("rayon_electrique", (p, cf, st) -> {
            LivingEntity t = rayTarget(p, 10, 0.8);
            line(p, 10, Particle.ELECTRIC_SPARK, null);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 1.8f);
            if (t == null) { Msg.actionBar(p, "<red>Rayon Electrique : aucun ennemi a 10 blocs."); return false; }
            t.setNoDamageTicks(0); Effects.damage(t, 6.0, p); immobilize(p, t, 40);
            return true;
        });
        act("scie_circulaire", (p, cf, st) -> { orb(p, "scie_circulaire", Particle.CRIT, null, 1.0); p.getWorld().playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1f, 1.5f); return true; });
        act("sol_electrifiant", (p, cf, st) -> {
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t += 10;
                    if (t > 100 || !p.isOnline()) { cancel(); return; }
                    for (int i = 0; i < 20; i++) { double a = Math.random() * Math.PI * 2, r = Math.random() * 10; p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 1, 0, 0.2, 0, 0.05); }
                    for (LivingEntity e : Effects.enemiesWithin(p, 10)) { if (!Effects.isRooted(e)) immobilize(p, e, 15); else Effects.root(e, 15); }
                }
            }.runTaskTimer(Solo.plugin(), 0L, 10L);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
            return true;
        });
        act("foudroiement_circulaire", (p, cf, st) -> {
            Location c = ahead(p, 3);
            c.getWorld().strikeLightningEffect(c);
            for (LivingEntity e : Effects.within(c, 3, le -> Effects.isEnemy(p, le))) { e.setNoDamageTicks(0); Effects.damage(e, 4.0, p); immobilize(p, e, 60); }
            return true;
        });
        act("foudre_incontrolable", (p, cf, st) -> {
            p.setHealth(Math.max(1.0, p.getHealth() - 6.0)); p.damage(0.01);
            Vector back = p.getLocation().getDirection().setY(0).normalize().multiply(-1);
            p.setVelocity(back.multiply(1.8).setY(0.5));
            Effects.noFallUntil(p, 3000);
            Vector dir = p.getLocation().getDirection().setY(0).normalize();
            Location o = p.getLocation().add(0, 1, 0);
            for (double d = 0; d < 3; d += 0.5) for (double s = -1.5; s <= 1.5; s += 0.5) p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, o.clone().add(dir.clone().multiply(d)).add(-dir.getZ() * s, 0, dir.getX() * s), 2, 0.1, 0.3, 0.1, 0.05);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.5f);
            for (LivingEntity e : corridor(p, 4, 1.5)) { e.setNoDamageTicks(0); Effects.damage(e, 10.0, p); }
            return true;
        });
        act("explosion_denergie", (p, cf, st) -> {
            Effects.root(p, 100);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t += 10;
                    if (t > 100 || !p.isOnline() || p.isDead()) { cancel(); return; }
                    for (int i = 0; i < 24; i++) { double a = i * Math.PI / 12; p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(Math.cos(a) * 10, 1, Math.sin(a) * 10), 2, 0.2, 0.5, 0.2, 0.05); }
                    p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1, 0), 30, 3, 1.5, 3, 0.1);
                    for (LivingEntity e : Effects.enemiesWithin(p, 10)) { e.setNoDamageTicks(0); Effects.damage(e, 2.0, p); }
                    if (t % 20 == 0) p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1f, 2f);
                }
            }.runTaskTimer(Solo.plugin(), 0L, 10L);
            Msg.actionBar(p, "<yellow>Explosion d'Energie : immobile 5 s.");
            return true;
        });
        passive("armure_electrique").taken((s, a, e, st) -> { int n = st.get("n", 0) + 1; st.set("n", n % 5); if (n % 5 == 0) immobilize(s, a, 60); });
        passive("electrocution_imminente").taken((s, a, e, st) -> slow(s, a, 0, 200));

        // VENT
        act("rafale_de_vent", (p, cf, st) -> {
            LivingEntity t = nearest(p, 10);
            if (t == null) { Msg.actionBar(p, "<red>Rafale de Vent : aucun ennemi a 10 blocs."); return false; }
            if (t.getLocation().distance(p.getLocation()) < 5) add(p, "rafale_close_hits", 1);
            Effects.knockbackFrom(t, p.getLocation(), 5);
            t.getWorld().spawnParticle(Particle.GUST, t.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1f, 1f);
            return true;
        });
        act("tornade_repoussante", (p, cf, st) -> {
            for (LivingEntity t : Effects.enemiesWithin(p, 10)) { Effects.knockbackFrom(t, p.getLocation(), 10); t.getWorld().spawnParticle(Particle.GUST, t.getLocation().add(0, 1, 0), 3, 0.3, 0.3, 0.3, 0); }
            p.getWorld().spawnParticle(Particle.GUST_EMITTER_LARGE, p.getLocation().add(0, 1, 0), 1);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 0.8f);
            return true;
        });
        act("oeil_du_cyclone", (p, cf, st) -> {
            for (LivingEntity t : Effects.enemiesWithin(p, 10)) { Effects.launchUp(t, 30); t.getWorld().spawnParticle(Particle.CLOUD, t.getLocation(), 30, 0.4, 0.2, 0.4, 0.1); }
            p.getWorld().spawnParticle(Particle.GUST_EMITTER_LARGE, p.getLocation().add(0, 1, 0), 1);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1f, 0.5f);
            return true;
        });
        act("bourrasque_attirante", (p, cf, st) -> {
            LivingEntity t = nearest(p, 10);
            if (t == null) { Msg.actionBar(p, "<red>Bourrasque Attirante : aucun ennemi a 10 blocs."); return false; }
            pull(p, t, 1.4); add(p, "bourrasque_hits", 1); BOURRASQUE.put(t.getUniqueId(), System.currentTimeMillis() + 5000L);
            return true;
        });
        act("cyclone_amenant", (p, cf, st) -> {
            LivingEntity t = nearest(p, 30);
            if (t == null) { Msg.actionBar(p, "<red>Cyclone Amenant : aucun ennemi a 30 blocs."); return false; }
            pull(p, t, Math.min(3.0, 0.6 + t.getLocation().distance(p.getLocation()) * 0.12)); BOURRASQUE.put(t.getUniqueId(), System.currentTimeMillis() + 5000L);
            return true;
        });
        act("attraction_lacerante", (p, cf, st) -> {
            LivingEntity t = nearest(p, 10);
            if (t == null) { Msg.actionBar(p, "<red>Attraction Lacerante : aucun ennemi a 10 blocs."); return false; }
            BOURRASQUE.put(t.getUniqueId(), System.currentTimeMillis() + 6000L);
            new BukkitRunnable() {
                int n = 0;
                @Override public void run() {
                    n++;
                    if (n > 5 || t.isDead() || !p.isOnline() || t.getWorld() != p.getWorld()) { cancel(); return; }
                    Vector v = p.getLocation().toVector().subtract(t.getLocation().toVector());
                    if (v.length() > 1.5) t.setVelocity(v.normalize().multiply(0.35).setY(0.15));
                    t.setNoDamageTicks(0); Effects.damage(t, 2.0, p);
                    Location a = p.getLocation().add(0, 1, 0), b = t.getLocation().add(0, 1, 0);
                    Vector d = b.toVector().subtract(a.toVector()); double len = d.length(); d.normalize();
                    for (double k = 0; k < len; k += 0.5) p.getWorld().spawnParticle(Particle.CLOUD, a.clone().add(d.clone().multiply(k)), 1, 0, 0, 0, 0);
                }
            }.runTaskTimer(Solo.plugin(), 0L, 20L);
            return true;
        });
        act("vent_tranchant", (p, cf, st) -> { slash(p, 4.0); return true; });
        act("tornade_destructrice", (p, cf, st) -> { slash(p, 8.0); return true; });
        act("epee_eventail", (p, cf, st) -> {
            Boosts.set(p, "shoja_eventail", new Boosts.Boost(4, 0, 0, 0));
            Bukkit.getScheduler().runTaskLater(Solo.plugin(), () -> Boosts.remove(p, "shoja_eventail"), 100L);
            p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 1, 0), 8, 0.6, 0.4, 0.6, 0);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_CHARGE, 1f, 1.2f);
            Msg.actionBar(p, "<white>Epee Eventail : Force IV 5 s.");
            return true;
        });
        NODES.get("armure_de_vent").ability = new Passive(NODES.get("armure_de_vent")) {
            @Override public void onEquip(Player p, CfPlayer cf) { WIND_CHARGES.put(p.getUniqueId(), 5); }
            @Override public void tick(Player p, CfPlayer cf, AbilityState st) {
                int t = st.get("t", 0) + 1; st.set("t", t);
                if (t % 3 == 0) WIND_CHARGES.merge(p.getUniqueId(), 1, (a, b) -> Math.min(5, a + b));
            }
            @Override public String barText(Player p, CfPlayer cf, AbilityState st) { return "<white>Armure de Vent <gray>" + WIND_CHARGES.getOrDefault(p.getUniqueId(), 0) + "/5"; }
            @Override public void onDamaged(Player v, EntityDamageEvent e, AbilityState st) {
                if (!(e instanceof EntityDamageByEntityEvent)) return;
                int c = WIND_CHARGES.getOrDefault(v.getUniqueId(), 0);
                if (c <= 0) return;
                WIND_CHARGES.put(v.getUniqueId(), c - 1);
                e.setCancelled(true);
                v.getWorld().spawnParticle(Particle.GUST, v.getLocation().add(0, 1, 0), 2, 0.3, 0.3, 0.3, 0);
                v.getWorld().playSound(v.getLocation(), Sound.ENTITY_BREEZE_DEFLECT, 1f, 1.2f);
            }
        };

        // ESPACE
        act("division_du_territoire", (p, cf, st) -> { hole(p, ahead(p, 3), 1, 1, dir(p), 10, 20); return true; });
        act("crevasse", (p, cf, st) -> { hole(p, ahead(p, 7), 2, 5, dir(p), 10, 20); return true; });
        act("puits_sans_fond", (p, cf, st) -> { hole(p, ahead(p, 4), 2, 2, dir(p), 30, 20); return true; });
        act("soulevement_terrestre", (p, cf, st) -> { wall(p, 30, 10, 2, 20); return true; });
        act("nouvelle_dimension", (p, cf, st) -> {
            LivingEntity t = nearest(p, 10);
            if (t == null) { Msg.actionBar(p, "<red>Nouvelle Dimension : aucun ennemi a 10 blocs."); return false; }
            sendToDimension(p, List.of(t), 30);
            return true;
        });
        act("espace_incapacitant", (p, cf, st) -> {
            LivingEntity t = nearest(p, 10);
            if (t == null) { Msg.actionBar(p, "<red>Espace Incapacitant : aucun ennemi a 10 blocs."); return false; }
            sendToDimension(p, List.of(t, p), 30);
            negative(p, t, PotionEffectType.WEAKNESS, 1, 600);
            Boosts.set(p, "shoja_espace", new Boosts.Boost(2, 0, 0, 0));
            Bukkit.getScheduler().runTaskLater(Solo.plugin(), () -> Boosts.remove(p, "shoja_espace"), 600L);
            return true;
        });
        act("vide_interstellaire", (p, cf, st) -> {
            LivingEntity t = nearest(p, 10);
            if (t == null) { Msg.actionBar(p, "<red>Vide Interstellaire : aucun ennemi a 10 blocs."); return false; }
            sendToDimension(p, List.of(t), 60);
            return true;
        });
        act("legere_suppression", (p, cf, st) -> {
            List<LivingEntity> hit = corridor(p, 5, 1.5);
            line(p, 5, Particle.DUST, dust(120, 40, 200));
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.5f, 1.8f);
            for (LivingEntity e : hit) { slow(p, e, 1, 100); strip(e, org.bukkit.inventory.EquipmentSlot.HEAD); }
            return true;
        });
        act("souffle_destructeur", (p, cf, st) -> {
            LivingEntity t = nearest(p, 10);
            if (t == null) { Msg.actionBar(p, "<red>Souffle Destructeur : aucun ennemi a 10 blocs."); return false; }
            int n = 0;
            for (var s : new org.bukkit.inventory.EquipmentSlot[]{org.bukkit.inventory.EquipmentSlot.HEAD, org.bukkit.inventory.EquipmentSlot.CHEST, org.bukkit.inventory.EquipmentSlot.LEGS, org.bukkit.inventory.EquipmentSlot.FEET}) if (strip(t, s)) n++;
            t.getWorld().playSound(t.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.2f);
            t.getWorld().spawnParticle(Particle.SONIC_BOOM, t.getLocation().add(0, 1, 0), 1);
            Effects.say(t, "<dark_purple>Souffle Destructeur : ton equipement tombe !");
            Msg.actionBar(p, "<dark_purple>Souffle Destructeur : " + n + " piece(s) retiree(s).");
            return true;
        });
        act("incapacitation", (p, cf, st) -> {
            LivingEntity t = nearest(p, 10);
            if (t == null) { Msg.actionBar(p, "<red>Incapacitation : aucun ennemi a 10 blocs."); return false; }
            if (t instanceof Player tp) { Solo.plugin().players().get(tp).silence(60000L); Msg.send(tp, "<dark_purple><bold>Incapacitation :</bold> <gray>plus de capacites pendant 1 minute !"); }
            t.getWorld().spawnParticle(Particle.WITCH, t.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.1);
            t.getWorld().playSound(t.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.8f, 1.5f);
            return true;
        });

        // TEMPS
        act("panne_dhorloge", (p, cf, st) -> freezeAround(p, 3, 60, 0, 0));
        act("boom_temporel", (p, cf, st) -> freezeAround(p, 10, 100, 0, 0));
        act("explosion_du_temps", (p, cf, st) -> freezeAround(p, 30, 140, 0, 0));
        act("le_temps_est_incontrolable", (p, cf, st) -> freezeAround(p, 10, 100, 2.0, 20));
        act("lorem_ipsum", (p, cf, st) -> freezeAround(p, 30, 200, 2.0, 20));
        act("cogito_ergo_sum", (p, cf, st) -> { p.setHealth(Math.max(1.0, p.getHealth() - 8.0)); p.damage(0.01); return freezeAround(p, 10, 80, 6.0, 40); });
        act("back_to_back", (p, cf, st) -> { rewind(p, 0); return true; });
        act("tic_tac", (p, cf, st) -> { rewind(p, 10); return true; });
        act("ding_dong", (p, cf, st) -> { rewind(p, 0); for (LivingEntity e : Effects.enemiesWithin(p, 10)) { e.setNoDamageTicks(0); Effects.damage(e, 10.0, p); } p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 1.5f, 0.6f); return true; });
        passive("aura_de_lephemere").taken((s, a, e, st) -> { int n = st.get("n", 0) + 1; st.set("n", n % 5); if (n % 5 == 0) freeze(s, a, 40, true); });
    }

    static Vector dir(Player p) { Vector d = p.getLocation().getDirection().setY(0); return d.lengthSquared() < 0.01 ? new Vector(1, 0, 0) : d.normalize(); }

    static void act(String id, Act a) { Node n = NODES.get(id); n.ability = new Active(n, a); }
    static Passive passive(String id) { Node n = NODES.get(id); Passive p = new Passive(n); n.ability = p; return p; }

    static void slash(Player p, double dmg) {
        Vector dir = p.getEyeLocation().getDirection().setY(0).normalize();
        Location o = p.getLocation().add(0, 1, 0);
        new BukkitRunnable() {
            double d = 0;
            @Override public void run() {
                d += 2;
                if (d > 10) { cancel(); return; }
                Location at = o.clone().add(dir.clone().multiply(d));
                for (double s = -1.5; s <= 1.5; s += 0.5) p.getWorld().spawnParticle(Particle.SWEEP_ATTACK, at.clone().add(-dir.getZ() * s, 0, dir.getX() * s), 1, 0, 0, 0, 0);
                p.getWorld().spawnParticle(Particle.CLOUD, at, 6, 0.6, 0.3, 0.6, 0.02);
            }
        }.runTaskTimer(Solo.plugin(), 0L, 1L);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.7f);
        for (LivingEntity e : corridor(p, 10, 1.5)) { e.setNoDamageTicks(0); Effects.damage(e, dmg, p); }
    }

    static boolean freezeAround(Player p, double radius, int ticks, double dmgPerTick, int every) {
        List<LivingEntity> targets = Effects.enemiesWithin(p, radius);
        World w = p.getWorld();
        for (int i = 0; i < 36; i++) { double a = i * Math.PI / 18; w.spawnParticle(Particle.DUST, p.getLocation().add(Math.cos(a) * radius, 0.5, Math.sin(a) * radius), 2, 0.1, 0.5, 0.1, 0, GREY); }
        w.spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 80, radius / 2, 1, radius / 2, 0, GREY);
        w.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.5f);
        w.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 0.5f);
        for (LivingEntity e : targets) freeze(p, e, ticks, true);
        if (dmgPerTick > 0 && !targets.isEmpty()) new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t += every;
                if (t > ticks) { cancel(); return; }
                for (LivingEntity e : targets) if (!e.isDead()) { e.setNoDamageTicks(0); Effects.damage(e, dmgPerTick, p); }
            }
        }.runTaskTimer(Solo.plugin(), every, every);
        Msg.actionBar(p, "<light_purple>" + targets.size() + " ennemi(s) gele(s) " + (ticks / 20) + " s.");
        return true;
    }

    // ---------------------------------------------------------------- Tracker

    /** Compte les objectifs de l'arbre et gere les impacts des orbes, les geles, l'Epee de l'Ego. */
    public static final class Tracker implements Listener {

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onDamage(EntityDamageEvent e) {
            if (!(e.getEntity() instanceof Player v) || !isShoja(v)) return;
            double hearts = e.getFinalDamage() / 2.0;
            add(v, "dmg_taken", hearts);
            switch (e.getCause()) {
                case FALL -> add(v, "fall_dmg", hearts);
                case DROWNING -> add(v, "drown_dmg", hearts);
                case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> add(v, "explosions_taken", 1);
                default -> { }
            }
        }

        @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
        public void onFrozenAttack(EntityDamageByEntityEvent e) {
            LivingEntity a = DamageListener.livingAttackerOf(e.getDamager());
            if (a != null && isFrozen(a)) e.setCancelled(true);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onHit(EntityDamageByEntityEvent e) {
            if (!(e.getEntity() instanceof LivingEntity v)) return;
            Player a = DamageListener.attackerOf(e.getDamager());
            if (a != null && isShoja(a) && Effects.isEnemy(a, v)) {
                add(a, "hits_dealt", 1);
                add(a, "dmg_dealt", e.getFinalDamage() / 2.0);
                if (v.hasPotionEffect(PotionEffectType.SLOWNESS)) add(a, "hits_on_slowed", 1);
                Long b = BOURRASQUE.get(v.getUniqueId());
                if (b != null && System.currentTimeMillis() < b) add(a, "dmg_on_bourrasque", e.getFinalDamage() / 2.0);
            }
            if (v instanceof Player vp && isShoja(vp)) {
                LivingEntity att = DamageListener.livingAttackerOf(e.getDamager());
                if (att != null && att != vp && Effects.isEnemy(vp, att)) {
                    if (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) add(vp, "hits_taken", 1);
                    else add(vp, "abilities_taken", 1);
                }
            }
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onDeath(EntityDeathEvent e) {
            Player k = e.getEntity().getKiller();
            if (k == null || !isShoja(k)) return;
            if (e.getEntity() instanceof Creeper) add(k, "creeper_kills", 1);
            if (e.getEntity() instanceof Player || Mannequins.isMannequin(e.getEntity())) Shoja.egoCopy(k, e.getEntity());
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onBreak(BlockBreakEvent e) { if (isShoja(e.getPlayer())) add(e.getPlayer(), "blocks_broken", 1); }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onCraft(CraftItemEvent e) {
            if (!(e.getWhoClicked() instanceof Player p) || !isShoja(p)) return;
            ItemStack r = e.getRecipe().getResult();
            int n = e.isShiftClick() ? Math.max(1, r.getAmount()) : r.getAmount();
            switch (r.getType()) {
                case FLINT_AND_STEEL -> add(p, "craft_flint_steel", 1);
                case BUCKET -> add(p, "craft_bucket", n);
                case CLOCK -> add(p, "craft_clock", 1);
                case TNT -> add(p, "craft_tnt", n);
                default -> { }
            }
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onEnchant(EnchantItemEvent e) {
            if (!isShoja(e.getEnchanter())) return;
            ItemStack copy = e.getItem().clone();
            for (var en : e.getEnchantsToAdd().entrySet()) copy.addUnsafeEnchantment(en.getKey(), en.getValue());
            forged(e.getEnchanter(), copy);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onAnvil(InventoryClickEvent e) {
            if (!(e.getWhoClicked() instanceof Player p) || !isShoja(p)) return;
            if (e.getInventory().getType() != InventoryType.ANVIL || e.getRawSlot() != 2 || e.getCurrentItem() == null) return;
            forged(p, e.getCurrentItem());
        }

        static void forged(Player p, ItemStack it) {
            if (it == null || it.getType().isAir()) return;
            String n = it.getType().name();
            boolean sword = Tag.ITEMS_SWORDS.isTagged(it.getType());
            if (sword) {
                int fa = it.getEnchantmentLevel(Enchantment.FIRE_ASPECT), kb = it.getEnchantmentLevel(Enchantment.KNOCKBACK), sh = it.getEnchantmentLevel(Enchantment.SHARPNESS);
                if (fa >= 1) add(p, "sword_fire_aspect", 1);
                if (fa >= 2) add(p, "sword_fire_aspect2", 1);
                if (kb >= 1) add(p, "sword_knockback", 1);
                if (kb >= 2) add(p, "sword_knockback2", 1);
                if (sh >= 2) add(p, "sword_sharp2", 1);
                if (sh >= 5) add(p, "sword_sharp5", 1);
            }
            if (n.endsWith("_CHESTPLATE") && it.getEnchantmentLevel(Enchantment.FIRE_PROTECTION) >= 1) add(p, "chest_fire_prot", 1);
            if ((n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")) && it.getEnchantmentLevel(Enchantment.PROJECTILE_PROTECTION) >= 4) add(p, "armor_projprot4", 1);
        }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onPortal(PlayerPortalEvent e) { if (isShoja(e.getPlayer())) add(e.getPlayer(), "nether_portal", 1); }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onEat(PlayerItemConsumeEvent e) { if (e.getItem().getType() == Material.PUFFERFISH && isShoja(e.getPlayer())) add(e.getPlayer(), "pufferfish_eaten", 1); }

        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onRegen(EntityRegainHealthEvent e) { if (e.getEntity() instanceof Player p && isShoja(p)) add(p, "regen", e.getAmount() / 2.0); }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onItemBreak(PlayerItemBreakEvent e) {
            String n = e.getBrokenItem().getType().name();
            if (isShoja(e.getPlayer()) && (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS"))) add(e.getPlayer(), "armor_broken", 1);
        }

        /** Impacts des orbes et de la boule de feu. */
        @EventHandler
        public void onProjectileHit(ProjectileHitEvent e) {
            String id = e.getEntity().getPersistentDataContainer().get(ORB, PersistentDataType.STRING);
            if (id == null) return;
            if (!(e.getEntity().getShooter() instanceof Player p)) return;
            Location at = e.getEntity().getLocation();
            World w = at.getWorld();
            if (id.equals("explosion_infernale")) {
                e.setCancelled(true);
                e.getEntity().remove();
                w.spawnParticle(Particle.EXPLOSION_EMITTER, at, 3, 1, 1, 1, 0);
                w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
                for (LivingEntity le : Effects.within(at, 5, l -> Effects.isEnemy(p, l))) { le.setNoDamageTicks(0); Effects.damage(le, 8.0, p); Effects.knockbackFrom(le, at, 4); ignite(p, le, 100, "ign_explosion"); }
                fireZone(p, at, 5, 6, "ign_explosion");
                return;
            }
            LivingEntity t = e.getHitEntity() instanceof LivingEntity le && Effects.isEnemy(p, le) ? le : null;
            if (t == null) return;
            t.setNoDamageTicks(0);
            switch (id) {
                case "pistolet_a_eau" -> slow(p, t, 0, 100);
                case "fusil_a_eau" -> { slow(p, t, 1, 200); if (t.getLocation().distance(p.getLocation()) < 3) add(p, "fusil_close_hits", 1); }
                case "boule_electrifiante" -> { immobilize(p, t, 40); add(p, "boule_hits", 1); }
                case "scie_circulaire" -> { Effects.damage(t, 8.0, p); immobilize(p, t, 20); }
                default -> { }
            }
        }
    }
}
