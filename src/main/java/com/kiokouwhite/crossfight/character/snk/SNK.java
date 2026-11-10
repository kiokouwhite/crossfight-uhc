package com.kiokouwhite.crossfight.character.snk;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.ToggleAbility;
import com.kiokouwhite.crossfight.character.Licence;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.team.TeamSize;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Licence SHINGEKI NO KYOJIN : chaque personnage a un equipement tridimensionnel (un grappin)
 * avec une jauge de gaz de 100 qui baisse de 1 par seconde d'utilisation et remonte de 1 toutes les 2 secondes.
 */
public final class SNK {

    static final NamespacedKey HOOK = new NamespacedKey("crossfight", "snk_hook");
    static final Particle.DustOptions CABLE = new Particle.DustOptions(Color.fromRGB(190, 190, 200), 0.6f);
    static final Particle.DustOptions GAS = new Particle.DustOptions(Color.fromRGB(235, 235, 235), 1.2f);

    private SNK() {}

    public static Licence create() {
        Licence l = new Licence("snk", "SHINGEKI NO KYOJIN", TeamSize.TRIO,
                "Equipement tridimensionnel (grappin) avec une jauge de gaz de 100 : -1 par seconde d'utilisation, +1 toutes les 2 secondes.");
        Eren eren = new Eren();
        Mikasa mikasa = new Mikasa();
        Livai livai = new Livai();
        Armin armin = new Armin();
        l.add(eren); l.add(mikasa); l.add(livai); l.add(armin);
        l.listener(new Events());
        l.listener(eren);
        return l;
    }

    static CrossfightPlugin plugin() { return CrossfightPlugin.get(); }

    // ------------------------------------------------------------------ equipe

    /** Allies en vie et en ligne du joueur (sans lui). */
    static List<Player> allies(Player p) {
        List<Player> out = new ArrayList<>();
        Team t = plugin().teams().teamOf(p);
        if (t == null) return out;
        for (Player m : t.onlineMembers()) {
            if (m == p) continue;
            CfPlayer cf = plugin().players().get(m);
            if (cf.isAlive() && !cf.isWaitingRespawn()) out.add(m);
        }
        return out;
    }

    /** L'Eren de l'equipe du joueur, s'il est en vie. */
    static Player erenOf(Player p) {
        for (Player m : allies(p)) {
            CfPlayer cf = plugin().players().get(m);
            if (cf.character() != null && "eren".equals(cf.character().id())) return m;
        }
        return null;
    }

    /** Moins de 5 coeurs. */
    static boolean isLow(Player p) { return p != null && p.getHealth() < 10.0; }

    // ------------------------------------------------------------ invincibilite

    static void invincible(Player p, long millis) { Effects.invincible(p, millis); }
    static boolean isInvincible(Entity e) { return Effects.isInvincible(e); }

    // ----------------------------------------------------------------- ruees

    static void rush(Player p, Location to, int ticks, Particle particle) { Effects.rush(p, to, ticks, particle); }

    /** Degats directs a soi-meme (ignorent l'armure), sans jamais tuer. */
    static void selfDamage(Player p, double amount) {
        double hp = Math.max(1.0, p.getHealth() - amount);
        p.setHealth(hp);
        p.damage(0.01);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.9f);
    }

    // ------------------------------------------------------------ equipement 3D

    enum Style { HAINE, FORCE, TOUPIE, SOUTIEN }

    /**
     * L'equipement tridimensionnel : clic droit pour lancer un grappin ; la ou il s'accroche, le joueur
     * est tire a travers les airs. La jauge est le gaz. Chaque personnage y ajoute son style.
     */
    static class OdmGear extends ToggleAbility implements CustomUse {
        static final double RANGE = 30.0;
        final Style style;

        OdmGear(Style style, String extra) {
            super("snk_odm", "Equipement tridimensionnel",
                    "Clic droit : lance un grappin a 30 blocs ; la ou il s'accroche, tu es tire dans les airs. " + extra
                            + " Le gaz baisse de 1 par seconde de vol et remonte de 1 toutes les 2 secondes.",
                    Material.BOW, 1.0, 0.5);
            this.style = style;
        }

        @Override protected List<String> extraLore() { return List.of("", "<yellow>Clic droit : lancer le grappin"); }

        @Override public boolean onEnable(Player p, CfPlayer cf, AbilityState st) { return false; }
        @Override public void onDisable(Player p, CfPlayer cf, AbilityState st) { st.set("stop", true); }

        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            if (Eren.isTitan(p)) { Msg.actionBar(p, "<red>Un Titan n'utilise pas d'equipement tridimensionnel."); return true; }
            if (st.gauge() < 2) { Msg.actionBar(p, "<red>Plus de gaz !"); p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f); return true; }
            if (st.get("hooking", false) || st.isEnabled()) return true;
            fire(p, st);
            return true;
        }

        void fire(Player p, AbilityState st) {
            Vector dir = p.getEyeLocation().getDirection();
            Arrow a = Effects.shootInstantArrow(p, dir, 3.0, 0.0);
            a.setGravity(false);
            a.setInvisible(true);
            a.getPersistentDataContainer().set(HOOK, PersistentDataType.BYTE, (byte) 1);
            ItemDisplay hook = a.getWorld().spawn(a.getLocation(), ItemDisplay.class, d -> {
                d.setItemStack(new ItemStack(Material.TRIPWIRE_HOOK));
                d.setTransformation(new Transformation(new Vector3f(0, 0, 0), new Quaternionf(), new Vector3f(0.7f, 0.7f, 0.7f), new Quaternionf()));
                d.setBrightness(new Display.Brightness(15, 15));
                d.setPersistent(false);
            });
            a.addPassenger(hook);
            st.set("hooking", true);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_DISPENSER_LAUNCH, 1f, 1.4f);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1f, 1.6f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!a.isValid() || t > 12 || !p.isOnline()) {
                        hook.remove();
                        if (a.isValid()) a.remove();
                        st.set("hooking", false);
                        cancel();
                        return;
                    }
                    cable(p, a.getLocation());
                }
            }.runTaskTimer(plugin(), 1L, 1L);
        }

        /** Le grappin s'est accroche : sur une entite ou sur un bloc. */
        void onHit(Player p, CfPlayer cf, AbilityState st, ProjectileHitEvent e, Arrow a) {
            for (Entity pass : a.getPassengers()) pass.remove();
            st.set("hooking", false);
            Location where = a.getLocation();
            Entity hitEntity = e.getHitEntity();
            e.setCancelled(true);
            a.remove();
            if (hitEntity instanceof LivingEntity target && !target.isDead()) {
                p.getWorld().playSound(where, Sound.BLOCK_CHAIN_HIT, 1f, 1.2f);
                if (style == Style.HAINE && Effects.isEnemy(p, target) && Eren.haineReady(p, st)) { Eren.haineDash(p, st, target); return; }
                if (style == Style.SOUTIEN && Effects.isAlly(p, target) && Armin.soutienReady(p, st)
                        && target.getLocation().distance(p.getLocation()) <= 20) { Armin.soutienDash(p, st, target); return; }
                pull(p, st, () -> target.isDead() ? null : target.getLocation().add(0, 1, 0));
                return;
            }
            if (e.getHitBlock() == null) { st.set("stop", true); return; }
            p.getWorld().playSound(where, Sound.BLOCK_CHAIN_HIT, 1f, 1.0f);
            Location anchor = where.clone();
            pull(p, st, () -> anchor);
        }

        /** Le joueur est tire vers l'ancre tant qu'il n'y est pas (ou tant qu'il a du gaz). */
        void pull(Player p, AbilityState st, Supplier<Location> anchor) {
            st.set("stop", false);
            st.setEnabled(true);
            Effects.noFallUntil(p, 5000);
            World w = p.getWorld();
            w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.8f, 1.6f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    try {
                        Location target = anchor.get();
                        if (!p.isOnline() || p.isDead() || target == null || st.get("stop", false) || t > 60) { stop(); return; }
                        Vector diff = target.toVector().subtract(p.getLocation().add(0, 1, 0).toVector());
                        double dist = diff.length();
                        if (dist < 2.0) { stop(); return; }
                        Vector vel = diff.normalize().multiply(1.35).add(new Vector(0, 0.06, 0));
                        p.setVelocity(vel);
                        cable(p, target);
                        if (t % 3 == 0) w.spawnParticle(Particle.DUST, p.getLocation().add(0, 0.8, 0), 3, 0.3, 0.2, 0.3, 0, GAS);
                        if (t % 5 == 0) w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_IDLE_AIR, 0.5f, 1.8f);
                    } catch (Exception ex) { stop(); }
                }
                void stop() {
                    st.setEnabled(false);
                    st.set("stop", false);
                    if (p.isOnline()) {
                        Vector v = p.getVelocity();
                        p.setVelocity(v.multiply(0.4));
                        Effects.noFallUntil(p, 5000);
                    }
                    cancel();
                }
            }.runTaskTimer(plugin(), 0L, 1L);
        }

        /** Le cable entre la main du joueur et le grappin. */
        static void cable(Player p, Location to) {
            Location from = p.getEyeLocation().add(0, -0.4, 0);
            Vector d = to.toVector().subtract(from.toVector());
            double len = d.length();
            if (len < 0.1) return;
            d.normalize();
            for (double x = 0; x < len; x += 0.7) p.getWorld().spawnParticle(Particle.DUST, from.clone().add(d.clone().multiply(x)), 1, 0, 0, 0, 0, CABLE);
        }
    }

    // ---------------------------------------------------------------- events

    static final class Events implements Listener {

        @EventHandler
        public void onHookHit(ProjectileHitEvent e) {
            if (!(e.getEntity() instanceof Arrow a) || !a.getPersistentDataContainer().has(HOOK)) return;
            if (!(a.getShooter() instanceof Player p)) { a.remove(); return; }
            CfPlayer cf = plugin().players().get(p);
            if (cf.character() == null || !(cf.character().ability("snk_odm") instanceof OdmGear gear)) { a.remove(); return; }
            gear.onHit(p, cf, plugin().abilities().state(p, gear), e, a);
        }

    }

    /** Boost "Force" nomme, pose ou retire selon une condition. */
    static void boostIf(Player p, String source, boolean on, Boosts.Boost boost) {
        boolean has = Boosts.get(p, source) != null;
        if (on && !has) Boosts.set(p, source, boost);
        else if (!on && has) Boosts.remove(p, source);
    }
}
