package com.kiokouwhite.crossfight.character.solo;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Stealth;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Yuki - Voie de la Chasse. Plus elle souffre, plus elle est forte.
 */
public final class Yuki extends Character implements Listener, Solo.Burst {

    static final NamespacedKey MAXHP = new NamespacedKey("crossfight", "yuki_maxhp");
    static final Particle.DustOptions SNOW = new Particle.DustOptions(Color.fromRGB(235, 245, 255), 1.0f);

    public Yuki() {
        super("yuki", "Yuki", Voie.CHASSE, Material.SNOWBALL);
        addAbility(new BleedingSnow().bindToWeapon());
        addAbility(new Souffrante());
        addAbility(new Insatiable());
        addAbility(new Insaisissable());
        addAbility(new Desesperee());
        addAbility(Solo.ITEM);
        setUltimate(new ParfaiteImparfaite());
    }

    @Override public ItemStack createWeapon() { return Weapons.melee(Material.IRON_SWORD, 8.5, "<white><bold>Bleeding Snow", List.of("<gray>8.5 dmg.")); }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Bleeding Snow : -2 coeurs, Force, Vitesse et Resistance",
                "<white>Souffrante : bonus selon sa vie - Insatiable : survit a 0.5 coeur 15 s",
                "<white>Insaisissable : 3 dashs invisibles - Desesperee : malus selon sa vie",
                "<light_purple>Ultime : Obscurite et Lenteur II a tous, Yuki perd 7.5 coeurs 20 s",
                "<gold>Burst : Monstre, explosion de 50 blocs, -9 coeurs definitifs");
    }

    /** Modificateur de vie max (temporaire ou definitif), negatif. */
    static void maxHealthDelta(Player p, double delta, String key) {
        AttributeInstance mh = p.getAttribute(Attribute.MAX_HEALTH);
        if (mh == null) return;
        NamespacedKey k = new NamespacedKey("crossfight", key);
        double cur = 0;
        for (AttributeModifier m : mh.getModifiers()) if (m.getKey().equals(k)) cur = m.getAmount();
        mh.removeModifier(k);
        double v = cur + delta;
        if (Math.abs(v) > 1e-6) mh.addModifier(new AttributeModifier(k, v, AttributeModifier.Operation.ADD_NUMBER));
        if (p.getHealth() > mh.getValue()) p.setHealth(Math.max(1.0, mh.getValue()));
    }

    static final class BleedingSnow extends ActiveAbility {
        BleedingSnow() { super("yuki_snow", "Bleeding Snow", "Clic droit : Yuki perd 2 coeurs mais gagne Force, Vitesse et Resistance pendant 10 secondes.", Material.IRON_SWORD, 15); }
        @Override public List<String> weaponLore() { return List.of("<white>Clic droit : <gray>-2 coeurs, Force, Vitesse, Resistance 10 s"); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            p.setHealth(Math.max(1.0, p.getHealth() - 4.0));
            p.damage(0.01);
            Boosts.set(p, "yuki_snow", new Boosts.Boost(1, 1, 1, 0));
            Bukkit.getScheduler().runTaskLater(Solo.plugin(), () -> Boosts.remove(p, "yuki_snow"), 200L);
            p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0, new Particle.DustOptions(Color.fromRGB(200, 20, 40), 1.0f));
            p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 30, 0.5, 0.7, 0.5, 0.03);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_SNOW_BREAK, 1f, 0.6f);
            Msg.actionBar(p, "<white>Bleeding Snow : -2 coeurs, bonus 10 s");
            return true;
        }
    }

    static final class Souffrante extends PassiveAbility {
        Souffrante() { super("yuki_souffrante", "Souffrante", "Selon sa vie : moins de 7.5 coeurs, Vitesse ; moins de 5, Vitesse et Force ; moins de 3, Vitesse, Force et Resistance.", Material.SNOWBALL); }
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) {
            double hp = p.getHealth();
            Boosts.Boost b = hp < 6 ? new Boosts.Boost(1, 1, 1, 0) : hp < 10 ? new Boosts.Boost(1, 1, 0, 0) : hp < 15 ? new Boosts.Boost(0, 1, 0, 0) : null;
            Boosts.Boost cur = Boosts.get(p, "yuki_souffrante");
            if (b == null) { if (cur != null) Boosts.remove(p, "yuki_souffrante"); }
            else if (cur == null || cur.force != b.force || cur.speed != b.speed || cur.resistance != b.resistance) Boosts.set(p, "yuki_souffrante", b);
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            double hp = p.getHealth();
            return "<white>Souffrante <gray>[" + (hp < 6 ? "V+F+R" : hp < 10 ? "V+F" : hp < 15 ? "V" : "-") + "]";
        }
        @Override public void onUnequip(Player p, CfPlayer cf) { Boosts.remove(p, "yuki_souffrante"); Boosts.remove(p, "yuki_snow"); }
    }

    /** Le coup fatal la laisse a un demi-coeur pendant 15 secondes, sans regeneration. */
    static final class Insatiable extends ActiveAbility {
        Insatiable() { super("yuki_insatiable", "Insatiable", "Quand Yuki devrait mourir, elle reste a un demi-coeur pendant 15 secondes, sans pouvoir regenerer.", Material.TOTEM_OF_UNDYING, 300); }
        @Override protected List<String> extraLore() { return List.of("<gray>Passif", "<yellow>Temps de chargement : <white>5 minutes"); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { Msg.actionBar(p, "<gray>Insatiable est un passif."); return false; }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            if (System.currentTimeMillis() < st.get("until", 0L)) return "<red><bold>INSATIABLE</bold> <white>" + ((st.get("until", 0L) - System.currentTimeMillis()) / 1000 + 1) + "s";
            return st.onCooldown() ? "<gray>Insatiable <red>" + st.cooldownLeftSeconds() + "s" : "<green>Insatiable ✔";
        }
        @Override public void onDamaged(Player victim, EntityDamageEvent e, AbilityState st) {
            boolean active = System.currentTimeMillis() < st.get("until", 0L);
            if (e.getFinalDamage() < victim.getHealth()) return;
            if (active) { e.setDamage(0); victim.setHealth(Math.max(1.0, victim.getHealth())); return; }
            if (st.onCooldown() && !Solo.plugin().game().isTest()) return;
            e.setDamage(0);
            victim.setHealth(1.0);
            st.set("until", System.currentTimeMillis() + 15000L);
            com.kiokouwhite.crossfight.util.Sounds.ability(victim, "yuki_insatiable");
            st.startCooldown((int) Math.ceil(cooldownSeconds() * Solo.plugin().players().get(victim).cooldownFactor()));
            victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.1);
            victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.5f);
            Msg.send(victim, "<red><bold>Insatiable :</bold> <white>tu tiens a un demi-coeur pendant 15 secondes !");
        }
    }

    /** Pas de regeneration pendant Insatiable. */
    @EventHandler(ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        CfPlayer cf = Solo.plugin().players().get(p);
        if (cf.character() == null || !"yuki".equals(cf.character().id())) return;
        var a = cf.character().ability("yuki_insatiable");
        if (a != null && System.currentTimeMillis() < Solo.plugin().abilities().state(p, a).get("until", 0L)) e.setCancelled(true);
    }

    static final class Insaisissable extends ActiveAbility implements CustomUse {
        Insaisissable() { super("yuki_insaisissable", "Insaisissable", "Jusqu'a trois dashs de 4 blocs en 5 secondes. Yuki est invisible jusqu'au troisieme dash, ou jusqu'a ce qu'elle frappe.", Material.FEATHER, 15); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            long now = System.currentTimeMillis();
            boolean running = now < st.get("until", 0L) && st.get("dashes", 0) > 0;
            if (!running) {
                if (st.onCooldown()) { Msg.actionBar(p, "<red>Insaisissable : encore " + st.cooldownLeftSeconds() + "s"); return true; }
                st.set("until", now + 5000L); st.set("dashes", 3);
                st.startCooldown((int) Math.ceil(cooldownSeconds() * cf.cooldownFactor()));
                vanish(p, st, true);
            }
            dash(p);
            int left = st.get("dashes", 0) - 1;
            st.set("dashes", left);
            if (left <= 0) vanish(p, st, false);
            Msg.actionBar(p, "<white>Insaisissable : " + left + " dash(s) restant(s)");
            return true;
        }
        static void dash(Player p) {
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
            Effects.rush(p, p.getLocation().add(dir.normalize().multiply(4)), 2, Particle.SNOWFLAKE);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_SNOW_STEP, 1f, 1.4f);
        }
        static void vanish(Player p, AbilityState st, boolean on) {
            if (on) { Stealth.hideFromAll(p, "yuki", o -> Effects.isEnemy(p, o)); Stealth.hideArmor(p); st.set("invisible", true); }
            else if (st.get("invisible", false)) { Stealth.reveal(p, "yuki"); st.set("invisible", false); }
        }
        @Override public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e, AbilityState st) { vanish(attacker, st, false); }
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) { if (st.get("invisible", false) && System.currentTimeMillis() > st.get("until", 0L)) vanish(p, st, false); }
        @Override public void onUnequip(Player p, CfPlayer cf) { vanish(p, Solo.plugin().abilities().state(p, this), false); }
    }

    static final class Desesperee extends ActiveAbility {
        Desesperee() { super("yuki_desesperee", "Desesperee", "Tous les ennemis a 30 blocs subissent un effet selon la vie de Yuki : moins de 7.5 coeurs Lenteur, moins de 5 Cecite, moins de 2.5 Obscurite, moins de 1 coeur Obscurite, Lenteur III et Faiblesse III.", Material.WITHER_ROSE, 20); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            double hp = p.getHealth();
            if (hp >= 15) { Msg.actionBar(p, "<red>Desesperee : il faut avoir moins de 7.5 coeurs."); return false; }
            int n = 0;
            for (LivingEntity e : Effects.enemiesWithin(p, 30)) {
                if (hp < 2) { Effects.give(e, PotionEffectType.DARKNESS, 0, 200); Effects.give(e, PotionEffectType.SLOWNESS, 2, 200); Effects.give(e, PotionEffectType.WEAKNESS, 2, 200); }
                else if (hp < 5) Effects.give(e, PotionEffectType.DARKNESS, 0, 200);
                else if (hp < 10) Effects.give(e, PotionEffectType.BLINDNESS, 0, 200);
                else Effects.give(e, PotionEffectType.SLOWNESS, 0, 200);
                e.getWorld().spawnParticle(Particle.SNOWFLAKE, e.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.05);
                n++;
            }
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 1.4f);
            Msg.actionBar(p, "<white>Desesperee : " + n + " ennemi(s) touche(s).");
            return true;
        }
    }

    static final class ParfaiteImparfaite extends UltimateAbility {
        ParfaiteImparfaite() { super("yuki_ult", "Parfaite | Imparfaite", "Pendant 20 secondes, tous les ennemis de la carte ont Obscurite et Lenteur II ; Yuki perd temporairement 7.5 coeurs.", Material.SNOW_BLOCK); }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            for (Player o : Bukkit.getOnlinePlayers()) if (Effects.isEnemy(p, o)) { Effects.give(o, PotionEffectType.DARKNESS, 0, 400); Effects.give(o, PotionEffectType.SLOWNESS, 1, 400); Msg.send(o, "<white>Yuki : Obscurite et Lenteur II pendant 20 s !"); }
            for (LivingEntity e : Effects.enemiesWithin(p, 100)) if (!(e instanceof Player)) { Effects.give(e, PotionEffectType.DARKNESS, 0, 400); Effects.give(e, PotionEffectType.SLOWNESS, 1, 400); }
            maxHealthDelta(p, -15, "yuki_ult");
            Bukkit.getScheduler().runTaskLater(Solo.plugin(), () -> maxHealthDelta(p, 15, "yuki_ult"), 400L);
            World w = p.getWorld();
            w.spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 150, 3, 2, 3, 0.1);
            w.playSound(p.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.8f);
            Msg.actionBar(p, "<white>Parfaite | Imparfaite : 20 s");
            return true;
        }
    }

    @Override public String burstName() { return "Monstre"; }
    @Override public String burstDescription() { return "Yuki perd definitivement 7 coeurs : enorme explosion sur 50 blocs, les ennemis a 10 blocs perdent definitivement 9 coeurs."; }
    @Override public boolean burst(Player p, CfPlayer cf, AbilityState st) {
        maxHealthDelta(p, -14, "yuki_burst");
        World w = p.getWorld();
        Location c = p.getLocation();
        new BukkitRunnable() {
            int r = 0;
            @Override public void run() {
                r += 2;
                if (r > 50) { cancel(); return; }
                for (int i = 0; i < 24; i++) { double a = i * Math.PI / 12; w.spawnParticle(Particle.EXPLOSION, c.clone().add(Math.cos(a) * r, 1, Math.sin(a) * r), 1); w.spawnParticle(Particle.SNOWFLAKE, c.clone().add(Math.cos(a) * r, 1.5, Math.sin(a) * r), 3, 0.5, 0.8, 0.5, 0.05); }
                if (r % 10 == 0) w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.6f);
            }
        }.runTaskTimer(Solo.plugin(), 0L, 1L);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, c.clone().add(0, 1, 0), 5, 2, 1, 2, 0);
        int n = 0;
        for (LivingEntity e : Effects.enemiesWithin(p, 10)) {
            if (e instanceof Player ep) maxHealthDelta(ep, -18, "yuki_monstre");
            e.setNoDamageTicks(0);
            Effects.damage(e, 18.0, p);
            if (e instanceof Player ep) Msg.send(ep, "<white><bold>Monstre :</bold> <red>tu perds definitivement 9 coeurs !");
            n++;
        }
        Msg.actionBar(p, "<gold>Monstre : " + n + " ennemi(s), -7 coeurs definitifs pour Yuki.");
        return true;
    }

    /** Degats definitifs : on retire les modificateurs permanents a la nouvelle partie seulement. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onNothing(EntityDamageEvent e) { }
}
