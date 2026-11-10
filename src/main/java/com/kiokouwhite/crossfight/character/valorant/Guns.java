package com.kiokouwhite.crossfight.character.valorant;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.AbilityType;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Bullets;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Les armes a feu de Valorant : definitions, item, tir, chargeur et rechargement.
 */
public final class Guns implements Listener {

    public static final NamespacedKey GUN_KEY = new NamespacedKey("crossfight", "valorant_gun");
    static final NamespacedKey HEADSHOT_KEY = new NamespacedKey("crossfight", "headshot_bullet");
    static final String ABILITY_ID = "valorant_gun";

    enum Mode { SINGLE, BURST, SHOTGUN, MACHINE }

    /** Une arme a feu : prix, puissance (niveau d'enchantement), chargeur, cadence, rechargement. */
    public record Gun(String id, String name, String category, int price, int power, int magazine,
                      double shotsPerSecond, int reloadSeconds, Mode mode, boolean headshot, Material icon, Color color) {

        public String display() { return "<gold>" + name + " <gray>(" + category + ")"; }

        /** Degats de base d'une fleche pour ce niveau de Puissance, a vitesse 3. */
        public double arrowDamage() {
            return power <= 0 ? 2.0 : 2.0 + 0.5 * power + 0.5;
        }
    }

    public static final List<Gun> ALL = List.of(
            new Gun("classic", "Classic", "Pistolet", 0, 1, 2, 0.5, 15, Mode.SINGLE, false, Material.WOODEN_HOE, Color.GRAY),
            new Gun("frenzy", "Frenzy", "Pistolet", 450, 1, 5, 2.0, 20, Mode.SINGLE, false, Material.WOODEN_HOE, Color.GRAY),
            new Gun("shorty", "Shorty", "Pistolet", 350, 1, 1, 1.0, 30, Mode.SHOTGUN, false, Material.WOODEN_HOE, Color.GRAY),
            new Gun("ghost", "Ghost", "Pistolet", 500, 2, 10, 0.5, 10, Mode.SINGLE, false, Material.STONE_HOE, Color.SILVER),
            new Gun("sheriff", "Sheriff", "Pistolet", 800, 2, 5, 1.0 / 3.0, 10, Mode.SINGLE, true, Material.STONE_HOE, Color.SILVER),
            new Gun("stinger", "Stinger", "SMG", 1100, 1, 8, 2.0, 10, Mode.SINGLE, false, Material.IRON_HOE, Color.YELLOW),
            new Gun("spectre", "Spectre", "SMG", 1600, 2, 10, 1.0, 15, Mode.SINGLE, false, Material.IRON_HOE, Color.YELLOW),
            new Gun("bucky", "Bucky", "Shotgun", 950, 1, 4, 0.5, 20, Mode.SHOTGUN, false, Material.GOLDEN_HOE, Color.ORANGE),
            new Gun("judge", "Judge", "Shotgun", 1850, 1, 6, 1.0, 20, Mode.SHOTGUN, false, Material.GOLDEN_HOE, Color.ORANGE),
            new Gun("bulldog", "Bulldog", "Fusil", 2050, 2, 6, 0.5, 10, Mode.BURST, false, Material.DIAMOND_HOE, Color.AQUA),
            new Gun("guardian", "Guardian", "Fusil", 2250, 4, 3, 1.0 / 3.0, 10, Mode.SINGLE, false, Material.DIAMOND_HOE, Color.AQUA),
            new Gun("phantom", "Phantom", "Fusil", 2900, 2, 10, 1.0, 10, Mode.SINGLE, false, Material.DIAMOND_HOE, Color.AQUA),
            new Gun("vandal", "Vandal", "Fusil", 2900, 3, 8, 0.5, 10, Mode.SINGLE, false, Material.DIAMOND_HOE, Color.AQUA),
            new Gun("marshal", "Marshal", "Sniper", 950, 2, 3, 1.0 / 3.0, 10, Mode.SINGLE, true, Material.NETHERITE_HOE, Color.PURPLE),
            new Gun("operator", "Operator", "Sniper", 4700, 5, 1, 1.0, 5, Mode.SINGLE, true, Material.NETHERITE_HOE, Color.PURPLE),
            new Gun("ares", "Ares", "Mitrailleuse", 1600, 0, 10, 1.0, 20, Mode.MACHINE, false, Material.IRON_SHOVEL, Color.RED),
            new Gun("odin", "Odin", "Mitrailleuse", 3200, 0, 20, 2.0, 20, Mode.MACHINE, false, Material.IRON_SHOVEL, Color.RED)
    );

    public static Gun byId(String id) {
        for (Gun g : ALL) if (g.id().equals(id)) return g;
        return null;
    }

    public static ItemStack createItem(Gun g) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>" + g.category() + " <dark_gray>- Puissance " + g.power());
        lore.add("<gray>Chargeur : <white>" + g.magazine() + " <gray>| Cadence : <white>" + cadence(g) + " <gray>| Rechargement : <white>" + g.reloadSeconds() + " s");
        switch (g.mode()) {
            case BURST -> lore.add("<yellow>Tire 3 balles a la suite");
            case SHOTGUN -> lore.add("<yellow>5 balles en gerbe, portee 5 blocs");
            case MACHINE -> lore.add("<yellow>Rafale complete sans bouger, puis Lenteur II 5 s");
            default -> { }
        }
        if (g.headshot()) lore.add("<yellow>Degats doubles a la tete");
        lore.add("");
        lore.add("<dark_gray>Clic droit : tirer. Accroupi + clic droit : recharger.");
        ItemStack it = Items.of(g.icon(), g.display(), lore);
        Items.tag(it, GUN_KEY, g.id());
        Items.tag(it, AbilityEngine.ABILITY_KEY, ABILITY_ID);
        return Items.unbreakable(it);
    }

    static String cadence(Gun g) {
        if (g.shotsPerSecond() >= 1) return (int) g.shotsPerSecond() + "/s";
        return "1 / " + Math.round(1 / g.shotsPerSecond()) + " s";
    }

    public static Gun gunInHand(Player p) {
        String id = Items.tag(p.getInventory().getItemInMainHand(), GUN_KEY);
        return id == null ? null : byId(id);
    }

    /** Retire l'arme a feu de l'inventaire (achat d'une nouvelle). */
    public static void removeGun(Player p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) if (Items.tag(inv.getItem(i), GUN_KEY) != null) inv.setItem(i, null);
    }

    /**
     * La capacite "arme a feu", partagee par tous les personnages Valorant : gere le tir
     * de l'arme tenue en main.
     */
    public static final class GunAbility extends Ability implements CustomUse {

        GunAbility() {
            super(ABILITY_ID, "Arme a feu", "Clic droit : tirer avec l'arme a feu en main. Accroupi + clic droit : recharger.", AbilityType.ACTIVE, Material.IRON_HOE);
        }

        @Override public boolean hasItem() { return false; }

        /** Etat propre a l'arme en main (chaque arme a son chargeur). */
        private AbilityState gunState(Player p, Gun g) {
            AbilityState st = plugin().abilities().state(p, this);
            String current = st.get("gun", "");
            if (!current.equals(g.id())) {
                st.set("gun", g.id());
                st.set("mag", g.magazine());
                st.set("reloadUntil", 0L);
                st.set("lastShot", 0L);
            }
            return st;
        }

        @Override
        public String barText(Player p, CfPlayer cf, AbilityState st) {
            Gun g = gunInHand(p);
            if (g == null) return null;
            AbilityState gs = gunState(p, g);
            long reload = gs.get("reloadUntil", 0L);
            if (System.currentTimeMillis() < reload) return "<gold>" + g.name() + " <red>recharge " + (int) Math.ceil((reload - System.currentTimeMillis()) / 1000.0) + "s";
            return "<gold>" + g.name() + " <white>" + (int) gs.get("mag", g.magazine()) + "/" + g.magazine();
        }

        @Override
        public boolean onUse(Player p, CfPlayer cf, AbilityState unused, boolean sneaking) {
            Gun g = gunInHand(p);
            if (g == null) return true;
            AbilityState st = gunState(p, g);
            long now = System.currentTimeMillis();
            if (now < (long) st.get("reloadUntil", 0L)) {
                Msg.actionBar(p, "<red>" + g.name() + " : rechargement...");
                return true;
            }
            int mag = st.get("mag", g.magazine());
            if (sneaking) {
                if (mag < g.magazine()) reload(p, g, st);
                return true;
            }
            if (mag <= 0) { reload(p, g, st); return true; }
            if (st.get("firing", false)) return true;
            long interval = (long) (1000.0 / g.shotsPerSecond());
            if (now - (long) st.get("lastShot", 0L) < interval) return true;
            st.set("lastShot", now);
            switch (g.mode()) {
                case SINGLE -> { fire(p, g); st.set("mag", mag - 1); }
                case SHOTGUN -> { shotgun(p, g); st.set("mag", mag - 1); }
                case BURST -> burst(p, g, st, Math.min(3, mag));
                case MACHINE -> machine(p, g, st);
            }
            if ((int) st.get("mag", 0) <= 0 && !(boolean) st.get("firing", false)) reload(p, g, st);
            return true;
        }

        void reload(Player p, Gun g, AbilityState st) {
            st.set("reloadUntil", System.currentTimeMillis() + g.reloadSeconds() * 1000L);
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_START, 1f, 1.3f);
            Msg.actionBar(p, "<gold>" + g.name() + " <gray>: rechargement " + g.reloadSeconds() + " s");
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                if (!p.isOnline()) return;
                st.set("mag", g.magazine());
                st.set("reloadUntil", 0L);
                p.getWorld().playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 1f, 1.3f);
            }, g.reloadSeconds() * 20L);
        }

        static Arrow fire(Player p, Gun g) {
            return fire(p, g, p.getEyeLocation().getDirection());
        }

        static Arrow fire(Player p, Gun g, Vector dir) {
            Arrow a = Bullets.shoot(p, dir, 3.0, g.arrowDamage(), Material.YELLOW_CONCRETE, g.color());
            if (g.headshot()) a.getPersistentDataContainer().set(HEADSHOT_KEY, PersistentDataType.BYTE, (byte) 1);
            p.getWorld().playSound(p.getLocation(), g.power() >= 4 ? Sound.ENTITY_GENERIC_EXPLODE : Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.6f, g.power() >= 4 ? 1.8f : 1.5f);
            return a;
        }

        /** 5 balles en gerbe de 3 blocs de large, portee 5 blocs (1 bloc par tick pendant 6 ticks). */
        static void shotgun(Player p, Gun g) {
            Vector base = p.getEyeLocation().getDirection();
            for (int i = 0; i < 5; i++) {
                Vector dir = base.clone().add(new Vector(Math.random() * 0.5 - 0.25, Math.random() * 0.3 - 0.15, Math.random() * 0.5 - 0.25)).normalize();
                Arrow a = Bullets.shoot(p, dir, 1.0, 3.0, Material.ORANGE_CONCRETE, g.color());
                a.setGravity(false);
                Bukkit.getScheduler().runTaskLater(plugin(), () -> { if (a.isValid()) a.remove(); }, 6L);
            }
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.6f);
        }

        /** 3 balles a la suite, une toute les 3 ticks. */
        void burst(Player p, Gun g, AbilityState st, int shots) {
            st.set("firing", true);
            new BukkitRunnable() {
                int n = 0;
                @Override public void run() {
                    if (!p.isOnline() || n >= shots) { st.set("firing", false); if ((int) st.get("mag", 0) <= 0) reload(p, g, st); cancel(); return; }
                    fire(p, g);
                    st.set("mag", (int) st.get("mag", 0) - 1);
                    n++;
                }
            }.runTaskTimer(plugin(), 0L, 3L);
        }

        /** Rafale complete : immobile pendant le tir, puis Lenteur II 5 secondes. */
        void machine(Player p, Gun g, AbilityState st) {
            st.set("firing", true);
            int mag = st.get("mag", 0);
            long period = Math.max(1, Math.round(20.0 / g.shotsPerSecond()));
            Effects.root(p, (int) (mag * period) + 5);
            new BukkitRunnable() {
                @Override public void run() {
                    int left = st.get("mag", 0);
                    if (!p.isOnline() || left <= 0) {
                        st.set("firing", false);
                        Effects.unroot(p);
                        Effects.give(p, PotionEffectType.SLOWNESS, 1, 100);
                        reload(p, g, st);
                        cancel();
                        return;
                    }
                    fire(p, g);
                    st.set("mag", left - 1);
                }
            }.runTaskTimer(plugin(), 0L, period);
        }
    }

    static final GunAbility ABILITY = new GunAbility();

    /** Tir a la tete : degats doubles pour les armes qui le permettent. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHeadshot(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Arrow a) || !a.getPersistentDataContainer().has(HEADSHOT_KEY)) return;
        if (!(e.getEntity() instanceof LivingEntity victim)) return;
        if (a.getLocation().getY() >= victim.getEyeLocation().getY() - 0.3) {
            e.setDamage(e.getDamage() * 2);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1.8f);
            if (a.getShooter() instanceof Player shooter) Msg.actionBar(shooter, "<red><bold>Tir a la tete !");
        }
    }
}
