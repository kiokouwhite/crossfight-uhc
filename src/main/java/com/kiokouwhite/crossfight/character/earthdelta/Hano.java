package com.kiokouwhite.crossfight.character.earthdelta;

import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.listener.DamageListener;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.TempBlocks;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Hano - Voie de la Nihilite. Un arc dont l'enchantement change le tir, et des tomes enchantes qui sont ses pouvoirs.
 */
public final class Hano extends Character implements Listener {

    static final NamespacedKey ARROW_ENCH = new NamespacedKey("crossfight", "hano_arrow");
    static final Map<UUID, Long> FRAGILE = new HashMap<>();

    enum BowEnchant {
        FLAME("Flame", "Fleches enflammees qui explosent au sol."),
        MULTISHOT("Multishot", "Trois fleches a la fois."),
        PIERCING("Piercing", "Les fleches traversent et clouent l'adversaire au sol 2 secondes."),
        POWER("Power", "Power IV au lieu de Power II."),
        PUNCH("Punch", "L'adversaire touche est propulse de 5 blocs."),
        QUICK_CHARGE("Quick Charge", "Les fleches arrivent instantanement a l'endroit vise.");
        final String label, description;
        BowEnchant(String label, String description) { this.label = label; this.description = description; }
        static BowEnchant byName(String s) { for (BowEnchant b : values()) if (b.name().equalsIgnoreCase(s) || b.label.equalsIgnoreCase(s)) return b; return null; }
    }

    public Hano() {
        super("hano", "Hano", Voie.NIHILITE, Material.BOW);
        addAbility(new Henkama().bindToWeapon());
        addAbility(new TomesPassive());
        for (Tomes.Tome t : Tomes.Tome.values()) addAbility(new TomeAbility(t));
        setUltimate(new MaitresseDeLaConnaissance());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.bow(2, true, "<dark_green><bold>Henkama", List.of("<gray>Arc Power II. Son enchantement change le tir."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Henkama : arc Power II avec un enchantement de tir (au hasard)",
                "<white>3 tomes enchantes au hasard parmi 28 pouvoirs",
                "<light_purple>Ultime : 5 tomes supplementaires a usage unique pendant 30 s");
    }

    // ---------------------------------------------------------------- helpers

    static AbilityState tomesState(Player p) {
        CfPlayer cf = EarthDelta.plugin().players().get(p);
        if (cf.character() == null) return null;
        Ability a = cf.character().ability("hano_tomes");
        return a == null ? null : EarthDelta.plugin().abilities().state(p, a);
    }

    @SuppressWarnings("unchecked")
    static List<String> list(AbilityState st, String key) {
        List<String> l = st.get(key, null);
        if (l == null) { l = new ArrayList<>(); st.set(key, l); }
        return l;
    }

    static boolean isHano(Player p) {
        CfPlayer cf = EarthDelta.plugin().players().get(p);
        return cf.character() != null && "hano".equals(cf.character().id());
    }

    static boolean owns(Player p, Tomes.Tome t) {
        AbilityState st = tomesState(p);
        if (st == null) return false;
        return list(st, "tomes").contains(t.id) || list(st, "ult").contains(t.id);
    }

    /** Un tome a usage unique vient d'etre utilise : on le retire. */
    static void consume(Player p, Tomes.Tome t) {
        AbilityState st = tomesState(p);
        if (st == null) return;
        boolean removed = list(st, "ult").remove(t.id) || list(st, "tomes").remove(t.id);
        if (!removed) return;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (("hano_tome_" + t.id).equals(Items.tag(inv.getItem(i), AbilityEngine.ABILITY_KEY))) inv.setItem(i, null);
        p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1.5, 0), 30, 0.4, 0.5, 0.4, 0.5);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.6f);
        Msg.actionBar(p, "<gray>Le tome " + t.label + " s'est consume.");
    }

    /** Donne les items des tomes possedes (barre puis inventaire). */
    static void giveTomeItems(Player p) {
        AbilityState st = tomesState(p);
        CfPlayer cf = EarthDelta.plugin().players().get(p);
        if (st == null || cf.character() == null) return;
        var inv = p.getInventory();
        List<String> all = new ArrayList<>(list(st, "tomes"));
        all.addAll(list(st, "ult"));
        for (String id : all) {
            String abilityId = "hano_tome_" + id;
            boolean present = false;
            for (ItemStack it : inv.getContents()) if (abilityId.equals(Items.tag(it, AbilityEngine.ABILITY_KEY))) present = true;
            if (present) continue;
            Ability a = cf.character().ability(abilityId);
            if (a == null) continue;
            ItemStack item = a.createItem();
            int slot = -1;
            for (int i = 2; i <= 7 && slot < 0; i++) if (inv.getItem(i) == null) slot = i;
            for (int i = 9; i < 36 && slot < 0; i++) if (inv.getItem(i) == null) slot = i;
            if (slot >= 0) inv.setItem(slot, item); else inv.addItem(item);
        }
    }

    static Tomes.Tome randomTome(List<String> exclude) {
        List<Tomes.Tome> pool = new ArrayList<>();
        for (Tomes.Tome t : Tomes.Tome.values()) if (!exclude.contains(t.id)) pool.add(t);
        return pool.isEmpty() ? null : pool.get((int) (Math.random() * pool.size()));
    }

    public static List<String> tomeIds() { List<String> l = new ArrayList<>(); for (Tomes.Tome t : Tomes.Tome.values()) l.add(t.id); return l; }

    /** Mode test : donne un tome (id), tous, un au hasard, ou change l'enchantement de l'arc ("arc <nom>"). */
    public static boolean grant(Player p, String[] args) {
        if (!isHano(p)) return false;
        AbilityState st = tomesState(p);
        if (st == null) return false;
        if (args.length >= 2 && args[0].equalsIgnoreCase("arc")) {
            BowEnchant b = BowEnchant.byName(args[1]);
            if (b == null) return false;
            Henkama.setEnchant(p, b);
            return true;
        }
        String which = args.length == 0 ? "aleatoire" : args[0].toLowerCase();
        List<String> owned = list(st, "tomes");
        if (which.equals("tous")) { for (Tomes.Tome t : Tomes.Tome.values()) if (!owned.contains(t.id)) owned.add(t.id); }
        else if (which.equals("aleatoire")) { Tomes.Tome t = randomTome(owned); if (t == null) return false; owned.add(t.id); Msg.send(p, "<green>Tome : <white>" + t.label + " <gray>(" + t.enchant + ")"); }
        else { Tomes.Tome t = Tomes.Tome.byId(which); if (t == null) return false; if (!owned.contains(t.id)) owned.add(t.id); }
        giveTomeItems(p);
        return true;
    }

    // -------------------------------------------------------------------- arme

    /** L'arc : un enchantement de tir tire au sort au debut de la partie. */
    static final class Henkama extends PassiveAbility {
        Henkama() { super("hano_henkama", "Henkama", "Un arc Power II dont l'enchantement, tire au sort au debut de la partie, change le tir.", Material.BOW); }

        static BowEnchant enchantOf(Player p) {
            CfPlayer cf = EarthDelta.plugin().players().get(p);
            Ability a = cf.character() == null ? null : cf.character().ability("hano_henkama");
            if (a == null) return BowEnchant.POWER;
            AbilityState st = EarthDelta.plugin().abilities().state(p, a);
            String id = st.get("ench", null);
            if (id == null) { BowEnchant b = BowEnchant.values()[(int) (Math.random() * BowEnchant.values().length)]; st.set("ench", b.name()); return b; }
            return BowEnchant.valueOf(id);
        }

        static void setEnchant(Player p, BowEnchant b) {
            CfPlayer cf = EarthDelta.plugin().players().get(p);
            Ability a = cf.character() == null ? null : cf.character().ability("hano_henkama");
            if (a == null) return;
            EarthDelta.plugin().abilities().state(p, a).set("ench", b.name());
            refreshBow(p);
            Msg.send(p, "<dark_green>Henkama : <white>" + b.label + "</white> <gray>- " + b.description);
        }

        /** Remet l'arc a jour : niveau de Power et description de l'enchantement. */
        static void refreshBow(Player p) {
            BowEnchant b = enchantOf(p);
            for (ItemStack it : p.getInventory().getContents()) {
                if (it == null || !"hano".equals(Items.tag(it, AbilityEngine.WEAPON_KEY))) continue;
                var meta = it.getItemMeta();
                meta.removeEnchant(Enchantment.POWER);
                meta.addEnchant(Enchantment.POWER, b == BowEnchant.POWER ? 4 : 2, true);
                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                lore.add(Msg.mm("<!italic><gray>Arc Power " + (b == BowEnchant.POWER ? "IV" : "II") + "."));
                lore.add(Msg.mm("<!italic><dark_green>" + b.label + " : <gray>" + b.description));
                meta.lore(lore);
                it.setItemMeta(meta);
            }
            boolean has = false;
            for (ItemStack it : p.getInventory().getContents()) if (it != null && it.getType() == Material.ARROW) { has = true; break; }
            if (!has) p.getInventory().addItem(new ItemStack(Material.ARROW));
        }

        @Override public void onEquip(Player p, CfPlayer cf) {
            Bukkit.getScheduler().runTask(EarthDelta.plugin(), () -> {
                refreshBow(p);
                Msg.send(p, "<dark_green>Henkama : <white>" + enchantOf(p).label + "</white> <gray>- " + enchantOf(p).description);
            });
        }
    }

    // ------------------------------------------------------------------- tomes

    /** Les tomes possedes : 3 au hasard au debut, plus ceux de l'ultime. */
    static final class TomesPassive extends PassiveAbility {
        TomesPassive() { super("hano_tomes", "Tomes enchantes", "Hano a trois tomes a enchanter au debut de la partie : selon l'enchantement recu, ses pouvoirs sont differents. Chaque tome possede est un item.", Material.ENCHANTED_BOOK); }
        @Override public void onEquip(Player p, CfPlayer cf) {
            AbilityState st = EarthDelta.plugin().abilities().state(p, this);
            List<String> owned = list(st, "tomes");
            if (owned.isEmpty()) {
                for (int i = 0; i < 3; i++) { Tomes.Tome t = randomTome(owned); if (t != null) owned.add(t.id); }
                List<String> names = new ArrayList<>();
                for (String id : owned) { Tomes.Tome t = Tomes.Tome.byId(id); if (t != null) names.add(t.label + " (" + t.enchant + ")"); }
                Msg.send(p, "<dark_green>Tes tomes : <white>" + String.join(", ", names));
            }
            Bukkit.getScheduler().runTask(EarthDelta.plugin(), () -> giveTomeItems(p));
        }
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) {
            long until = st.get("ultUntil", 0L);
            if (until > 0 && System.currentTimeMillis() > until) {
                st.set("ultUntil", 0L);
                for (String id : new ArrayList<>(list(st, "ult"))) { Tomes.Tome t = Tomes.Tome.byId(id); if (t != null) consume(p, t); }
            }
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            return "<dark_green>Tomes <white>" + list(st, "tomes").size() + (list(st, "ult").isEmpty() ? "" : " <light_purple>+" + list(st, "ult").size());
        }
    }

    /** Un tome : item individuel donne quand Hano le possede. */
    static final class TomeAbility extends ActiveAbility {
        final Tomes.Tome tome;
        TomeAbility(Tomes.Tome t) {
            super("hano_tome_" + t.id, t.label, t.description, t.icon, t.passive ? 0 : 30);
            this.tome = t;
        }
        @Override public boolean hasItem() { return false; }
        @Override public boolean showInBar(Player p, CfPlayer cf, AbilityState st) { return !tome.passive && owns(p, tome); }
        @Override protected List<String> extraLore() {
            List<String> l = new ArrayList<>();
            l.add("<dark_gray>Enchantement : " + tome.enchant);
            if (tome.passive) l.add("<gray>Passif");
            else l.add("<yellow>Temps de chargement : <white>30s");
            if (tome.uses > 0) l.add("<red>Usage unique");
            return l;
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            if (!owns(p, tome)) { Msg.actionBar(p, "<red>Tu n'as pas ce tome."); return false; }
            if (tome.passive) { Msg.actionBar(p, "<gray>" + tome.label + " est un passif : toujours actif."); return false; }
            AbilityState hs = tomesState(p);
            boolean ok = Tomes.use(p, cf, hs, tome);
            if (ok && (tome.uses > 0 || list(hs, "ult").contains(tome.id))) Bukkit.getScheduler().runTask(EarthDelta.plugin(), () -> consume(p, tome));
            return ok;
        }
    }

    // ------------------------------------------------------------------- ultime

    static final class MaitresseDeLaConnaissance extends UltimateAbility {
        MaitresseDeLaConnaissance() { super("hano_ult", "Maitresse de la Connaissance", "Pendant 30 secondes, Hano recoit 5 tomes supplementaires enchantes au hasard, a usage unique.", Material.KNOWLEDGE_BOOK); }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            AbilityState ts = tomesState(p);
            if (ts == null) return false;
            List<String> exclude = new ArrayList<>(list(ts, "tomes"));
            exclude.addAll(list(ts, "ult"));
            List<String> names = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                Tomes.Tome t = randomTome(exclude);
                if (t == null) break;
                exclude.add(t.id); list(ts, "ult").add(t.id); names.add(t.label);
            }
            ts.set("ultUntil", System.currentTimeMillis() + 30000L);
            giveTomeItems(p);
            p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1.5, 0), 120, 1, 1, 1, 1);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
            Msg.send(p, "<light_purple>Maitresse de la Connaissance : <white>" + String.join(", ", names) + "</white> (30 s, usage unique).");
            return true;
        }
    }

    // ------------------------------------------------------------------- events

    @EventHandler
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !isHano(p) || !(e.getProjectile() instanceof Arrow a)) return;
        if (e.getBow() == null || !"hano".equals(Items.tag(e.getBow(), AbilityEngine.WEAPON_KEY))) return;
        BowEnchant b = Henkama.enchantOf(p);
        a.getPersistentDataContainer().set(ARROW_ENCH, PersistentDataType.STRING, b.name());
        switch (b) {
            case FLAME -> a.setFireTicks(200);
            case PIERCING -> a.setPierceLevel(3);
            case MULTISHOT -> {
                for (int k : new int[]{-10, 10}) {
                    Vector d = a.getVelocity().clone().rotateAroundY(Math.toRadians(k));
                    Arrow extra = p.getWorld().spawnArrow(a.getLocation(), d.clone().normalize(), (float) d.length(), 1f);
                    extra.setShooter(p);
                    extra.setDamage(a.getDamage());
                    extra.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
                    extra.getPersistentDataContainer().set(ARROW_ENCH, PersistentDataType.STRING, b.name());
                }
            }
            case QUICK_CHARGE -> {
                e.setCancelled(true);
                Arrow fast = Effects.shootInstantArrow(p, p.getEyeLocation().getDirection(), 12.0, a.getDamage() * Math.max(0.3, e.getForce()));
                fast.getPersistentDataContainer().set(ARROW_ENCH, PersistentDataType.STRING, b.name());
            }
            default -> { }
        }
    }

    @EventHandler
    public void onArrowHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow a) || !(a.getShooter() instanceof Player p)) return;
        String spear = a.getPersistentDataContainer().get(Tomes.SPEAR, PersistentDataType.STRING);
        if (spear != null && e.getHitEntity() instanceof LivingEntity target && Effects.isEnemy(p, target)) {
            if (spear.equals("impaling")) { Effects.root(target, 200); Effects.say(target, "<gray>Empale : immobilise 10 s !"); }
            else { Effects.give(target, PotionEffectType.WEAKNESS, 3, 200); Effects.say(target, "<gray>Javelot Desarmant : Faiblesse IV !"); }
            target.getWorld().playSound(target.getLocation(), Sound.ITEM_TRIDENT_HIT, 1f, 1f);
            return;
        }
        String ench = a.getPersistentDataContainer().get(ARROW_ENCH, PersistentDataType.STRING);
        if (ench == null) return;
        World w = a.getWorld();
        Location c = a.getLocation();
        switch (BowEnchant.valueOf(ench)) {
            case FLAME -> {
                if (e.getHitBlock() == null) return;
                w.spawnParticle(Particle.EXPLOSION, c, 1);
                w.spawnParticle(Particle.FLAME, c, 40, 1.2, 0.5, 1.2, 0.05);
                w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.3f);
                for (LivingEntity t : Effects.enemiesWithin(p, c, 2.5)) { Effects.damage(t, 4.0, p); t.setFireTicks(60); }
                TempBlocks fire = new TempBlocks().unbreakable();
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    int y = Effects.groundAround(w, c.getBlockX() + dx, c.getBlockZ() + dz, c.getBlockY());
                    if (y != Integer.MIN_VALUE) fire.place(w.getBlockAt(c.getBlockX() + dx, y + 1, c.getBlockZ() + dz), Material.FIRE);
                }
                fire.restoreAfter(100);
                Bukkit.getScheduler().runTask(EarthDelta.plugin(), () -> { if (a.isValid()) a.remove(); });
            }
            case PIERCING -> { if (e.getHitEntity() instanceof LivingEntity t && Effects.isEnemy(p, t)) { Effects.root(t, 40); Effects.say(t, "<gray>Cloue au sol 2 s !"); } }
            case PUNCH -> { if (e.getHitEntity() instanceof LivingEntity t && Effects.isEnemy(p, t)) Bukkit.getScheduler().runTask(EarthDelta.plugin(), () -> Effects.knockbackFrom(t, p.getLocation(), 5)); }
            default -> { }
        }
    }

    /** Coups recus par Hano : Explosion Desarmante, Explosion d'Epines, Armure d'Hades, Armure d'Epines, Immortalite, chute. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHanoDamaged(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !isHano(p) || !EarthDelta.inGame(p)) return;
        AbilityState st = tomesState(p);
        if (st == null) return;
        World w = p.getWorld();
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && owns(p, Tomes.Tome.FEATHER_FALLING)) { e.setCancelled(true); return; }
        if (owns(p, Tomes.Tome.INFINITY) && e.getFinalDamage() >= p.getHealth()) {
            e.setCancelled(true);
            p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
            w.spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 80, 0.5, 0.8, 0.5, 0.3);
            w.playSound(p.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
            Msg.send(p, "<gold>Immortalite : le tome te rend toute ta vie !");
            consume(p, Tomes.Tome.INFINITY);
            return;
        }
        if (!(e instanceof EntityDamageByEntityEvent be)) return;
        LivingEntity attacker = DamageListener.livingAttackerOf(be.getDamager());
        if (attacker == null || attacker == p || !Effects.isEnemy(p, attacker)) return;
        if (owns(p, Tomes.Tome.THORNS)) Effects.give(attacker, PotionEffectType.POISON, 0, 40);
        if (System.currentTimeMillis() < st.get("hades_until", 0L)) attacker.setFireTicks(Math.max(attacker.getFireTicks(), 100));
        if (st.get("blast_armed", false)) {
            st.set("blast_armed", false);
            w.spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation().add(0, 1, 0), 2, 0.5, 0.5, 0.5, 0);
            w.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.9f);
            for (LivingEntity t : Effects.enemiesWithin(p, 4)) {
                Effects.damage(t, 2.0, p);
                var eq = t.getEquipment();
                if (eq != null && eq.getChestplate() != null && !eq.getChestplate().getType().isAir()) { w.dropItemNaturally(t.getLocation(), eq.getChestplate()); eq.setChestplate(null); Effects.say(t, "<gray>Ton plastron a saute !"); }
            }
        }
        if (st.get("arrows_armed", false)) {
            st.set("arrows_armed", false);
            w.playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 0.6f);
            for (int i = 0; i < 16; i++) {
                double ang = i * Math.PI / 8;
                Arrow ar = Effects.shootInstantArrow(p, new Vector(Math.cos(ang), 0.15, Math.sin(ang)), 2.5, 8.0);
                ar.setCritical(true);
            }
        }
    }

    /** Brise Glace : la cible marquee prend le double des degats. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFragile(EntityDamageEvent e) {
        Long until = FRAGILE.get(e.getEntity().getUniqueId());
        if (until == null) return;
        if (System.currentTimeMillis() > until) { FRAGILE.remove(e.getEntity().getUniqueId()); return; }
        e.setDamage(e.getDamage() * 2);
        e.getEntity().getWorld().spawnParticle(Particle.DUST, e.getEntity().getLocation().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0, new Particle.DustOptions(org.bukkit.Color.fromRGB(170, 230, 255), 1.0f));
    }

    /** Jackpot : minerais triples. */
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!isHano(p) || !owns(p, Tomes.Tome.FORTUNE)) return;
        if (!e.getBlock().getType().name().endsWith("_ORE") && e.getBlock().getType() != Material.ANCIENT_DEBRIS) return;
        for (ItemStack drop : e.getBlock().getDrops(p.getInventory().getItemInMainHand(), p)) {
            ItemStack extra = drop.clone(); extra.setAmount(drop.getAmount() * 2);
            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation().add(0.5, 0.5, 0.5), extra);
        }
    }

    /** Par ici le butin : les Pommes Dorees de la victime sont doublees. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onVictimDeath(PlayerDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer == null || !isHano(killer) || !owns(killer, Tomes.Tome.LOOTING)) return;
        List<ItemStack> extra = new ArrayList<>();
        for (ItemStack it : e.getDrops()) if (it != null && it.getType() == Material.GOLDEN_APPLE) extra.add(it.clone());
        e.getDrops().addAll(extra);
        if (!extra.isEmpty()) Msg.send(killer, "<gold>Par ici le butin : Pommes Dorees doublees !");
    }
}
