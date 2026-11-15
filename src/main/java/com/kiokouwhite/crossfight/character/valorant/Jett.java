package com.kiokouwhite.crossfight.character.valorant;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.ToggleAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.character.rwby.Ruby;
import com.kiokouwhite.crossfight.util.Bullets;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Fx;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Puppet;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Jett - Path of Destruction.
 */
public final class Jett extends Character {

    public Jett() {
        super("jett", "Jett", Voie.DESTRUCTION, Material.FEATHER);
        addAbility(new VentArriere());
        addAbility(new CourantAscendant());
        addAbility(new Flottement());
        addAbility(Valorant.SHOP);
        addAbility(Guns.ABILITY);
        setUltimate(new TempeteDeLames());
    }

    @Override
    public ItemStack createWeapon() { return Valorant.meleeWeapon(0); }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Vent Arriere : dash de 5 blocs",
                "<white>Courant Ascendant : saut de 5 blocs",
                "<white>Flottement : chute lente activable",
                "<light_purple>Ultime : 5 couteaux a lancer, 2 coeurs chacun");
    }

    static final class VentArriere extends ActiveAbility {
        VentArriere() {
            super("jett_vent", "Vent Arriere", "Jett fait un dash de 5 blocs dans la direction ou elle se deplace (ou regarde).", Material.WHITE_DYE, 5);
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Vector v = p.getVelocity().clone().setY(0);
            Vector dir = v.lengthSquared() > 0.01 ? v.normalize() : Effects.towards(p, null);
            dashLook(p, dir.clone());
            p.setVelocity(dir.multiply(1.6).setY(0.15));
            Ruby.noFallUntil(p, 2000);
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, 0.05);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 1.8f);
            return true;
        }

        /**
         * Comme dans Valorant : elle file couchee dans le sens du dash, les bras tires en arriere ; une bourrasque
         * s'ouvre derriere elle et deux filets de vent blanc la suivent.
         */
        static void dashLook(Player p, Vector dir) {
            Location start = p.getLocation();
            Fx.burst(start.clone().add(0, 1.0, 0), dir.clone().multiply(-1), "vent", Color.WHITE, 0.7f, 2.6f, 6, 140);
            Puppet pu = Puppet.show(p, "jett", null, "jett_vent");
            if (pu != null) {
                pu.face(start.clone().setDirection(dir).getYaw());
                pu.endAfter(8, 3);
            }
            Vector side = new Vector(-dir.getZ(), 0, dir.getX());
            new BukkitRunnable() {
                int t = 0;
                Location last = start.clone();
                @Override public void run() {
                    if (!p.isOnline() || t++ >= 8) { cancel(); return; }
                    Location now = p.getLocation();
                    for (int k = -1; k <= 1; k += 2) {
                        Fx.line(last.clone().add(0, 1.1, 0).add(side.clone().multiply(0.38 * k)), now.clone().add(0, 1.1, 0).add(side.clone().multiply(0.38 * k)), 0.3, WIND);
                    }
                    p.getWorld().spawnParticle(Particle.CLOUD, now.clone().add(0, 0.9, 0), 2, 0.15, 0.25, 0.15, 0.01);
                    last = now;
                }
            }.runTaskTimer(plugin(), 1L, 1L);
        }
    }

    static final Particle.DustOptions WIND = new Particle.DustOptions(Color.fromRGB(235, 248, 255), 0.8f);
    static final Color WIND_TINT = Color.fromRGB(215, 245, 255);

    static final class CourantAscendant extends ActiveAbility {
        CourantAscendant() {
            super("jett_courant", "Courant Ascendant", "Jett se propulse de 5 blocs en hauteur.", Material.LIGHT_GRAY_DYE, 3);
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Effects.launchUp(p, 5);
            Ruby.noFallUntil(p, 3000);
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 25, 0.4, 0.1, 0.4, 0.05);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 1.2f);
            riseLook(p);
            return true;
        }

        /**
         * Comme dans Valorant : elle jaillit vers le haut, les bras plaques vers le bas, un genou remonte ; la
         * bourrasque s'ouvre au sol sous ses pieds et une colonne de vent en vrille monte avec elle.
         */
        static void riseLook(Player p) {
            Location ground = p.getLocation().add(0, 0.08, 0);
            Fx.burst(ground, new Vector(0, 1, 0), "vent", Color.WHITE, 0.9f, 3.6f, 8, 220);
            Fx.burst(ground.clone().add(0, 0.05, 0), new Vector(0, 1, 0), "vent", WIND_TINT, 0.5f, 2.2f, 6, -160);
            Puppet pu = Puppet.show(p, "jett", null, "jett_courant");
            if (pu != null) pu.endAfter(12, 4);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    if (!p.isOnline() || t++ >= 12) { cancel(); return; }
                    Location feet = p.getLocation();
                    for (int k = 0; k < 3; k++) {
                        double a = t * 0.9 + k * Math.PI * 2 / 3, r = 0.55;
                        Location from = feet.clone().add(Math.cos(a) * r, -0.9, Math.sin(a) * r), to = feet.clone().add(Math.cos(a + 0.5) * r, 0.1, Math.sin(a + 0.5) * r);
                        Fx.line(from, to, 0.25, WIND);
                    }
                }
            }.runTaskTimer(plugin(), 1L, 1L);
        }
    }

    static final class Flottement extends ToggleAbility {
        Flottement() {
            super("jett_flottement", "Flottement", "Tant que c'est actif, Jett a Chute lente.", Material.FEATHER, 0.0, 0.0);
        }
        @Override public boolean onEnable(Player p, CfPlayer cf, AbilityState st) {
            Effects.refresh(p, PotionEffectType.SLOW_FALLING, 0, 45);
            glideLook(p, st);
            return true;
        }
        @Override public void tickActive(Player p, CfPlayer cf, AbilityState st) { Effects.refresh(p, PotionEffectType.SLOW_FALLING, 0, 45); }

        /**
         * Comme dans Valorant : tant qu'elle descend en planant, elle a les bras ouverts de chaque cote, penchee en
         * avant, et le vent file sous ses mains. Des qu'elle touche le sol (ou remonte), elle reprend son allure.
         */
        static void glideLook(Player p, AbilityState st) {
            new BukkitRunnable() {
                Puppet glide;
                int t = 0;
                @Override public void run() {
                    if (!p.isOnline() || p.isDead() || !st.isEnabled()) { cancel(); return; }
                    t++;
                    Location feet = p.getLocation();
                    boolean falling = p.getVelocity().getY() < -0.03 && feet.clone().subtract(0, 0.35, 0).getBlock().isPassable() && feet.clone().subtract(0, 1.1, 0).getBlock().isPassable();
                    Puppet current = Puppet.of(p);
                    if (falling && current == null) glide = Puppet.show(p, "jett", null, "jett_flotte");
                    if (glide == null || Puppet.of(p) != glide) return;
                    // Tant qu'elle plane, la pose est prolongee ; au sol, elle se termine d'elle-meme en quelques ticks.
                    if (falling) glide.endAfter(4, 3);
                    if (t % 2 == 0) {
                        for (boolean right : new boolean[]{true, false}) {
                            Location hand = glide.armPoint(right, 1.0);
                            p.getWorld().spawnParticle(Particle.DUST, hand, 1, 0.05, 0.05, 0.05, 0, WIND);
                            p.getWorld().spawnParticle(Particle.CLOUD, hand, 0, 0, 0.12, 0, 0.5);
                        }
                    }
                }
            }.runTaskTimer(plugin(), 1L, 1L);
        }
        @Override public void onDisable(Player p, CfPlayer cf, AbilityState st) { p.removePotionEffect(PotionEffectType.SLOW_FALLING); }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { return st.isEnabled() ? "<white>Flottement <aqua>actif" : "<gray>Flottement"; }
    }

    /** Cinq couteaux flottent en eventail derriere Jett ; chaque clic en lance un (2 coeurs). */
    static final class TempeteDeLames extends UltimateAbility implements CustomUse {
        TempeteDeLames() {
            super("jett_ult", "Tempete de Lames", "Jett invoque 5 couteaux autour d'elle. Chaque clic droit sur cet item en lance un, infligeant 2 coeurs.", Material.IRON_SWORD);
        }

        /** Un couteau : l'image du pack (tools/build_glyphs.py), ou une epee de fer a sa place. */
        static ItemStack knife() { return Fx.imageOr("kunai", Color.WHITE, Material.IRON_SWORD); }

        @Override
        public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            int knives = st.get("knives", 0);
            if (knives <= 0) return false; // lancement standard de l'ultime
            st.set("knives", knives - 1);
            Vector dir = p.getEyeLocation().getDirection();
            Bullets.shoot(p, dir, 3.0, 1.34, knife(), 0.55f, false, Fx.has("kunai"), Color.WHITE);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.6f);
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_THROW, 0.7f, 1.7f);
            List<ItemDisplay> displays = st.get("displays", null);
            if (displays != null && !displays.isEmpty()) displays.remove(displays.size() - 1).remove();
            // Comme dans Valorant : un geste sec du bras, le couteau part de sa main.
            Puppet current = Puppet.of(p);
            if (current == null || (current.currentPose() != null && current.currentPose().startsWith("jett_lames"))) {
                Puppet pu = Puppet.show(p, "jett", null, "jett_lames");
                if (pu != null) { pu.pose("jett_lames_lance", 2); pu.endAfter(6, 3); }
            }
            if (knives - 1 <= 0) Msg.actionBar(p, "<gray>Plus de couteaux.");
            return true;
        }

        @Override
        public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            st.set("knives", 5);
            List<ItemDisplay> displays = new ArrayList<>();
            Location spawn = p.getLocation();
            spawn.setYaw(0);
            spawn.setPitch(0);
            for (int i = 0; i < 5; i++) displays.add(Fx.show(spawn, knife(), 0.05f, false));
            st.set("displays", displays);
            // L'invocation : les bras s'ecartent, une bourrasque tourne a ses pieds, les couteaux sortent de son dos.
            Puppet pu = Puppet.show(p, "jett", null, "debout");
            if (pu != null) { pu.pose("jett_lames", 4); pu.endAfter(14, 4); }
            Fx.burst(p.getLocation().add(0, 0.08, 0), new Vector(0, 1, 0), "vent", WIND_TINT, 0.8f, 3.0f, 10, 260);
            new BukkitRunnable() {
                int ticks = 0;
                @Override public void run() {
                    ticks++;
                    List<ItemDisplay> list = st.get("displays", null);
                    if (!p.isOnline() || list == null || list.isEmpty() || ticks > 20 * 30) {
                        if (list != null) { for (ItemDisplay d : list) d.remove(); list.clear(); }
                        st.set("knives", 0);
                        cancel();
                        return;
                    }
                    // Un eventail derriere ses epaules, pointes vers le haut et l'exterieur, qui suit son regard et respire.
                    Location base = p.getLocation();
                    double yaw = Math.toRadians(base.getYaw());
                    Vector fwd = new Vector(-Math.sin(yaw), 0, Math.cos(yaw)), side = new Vector(-fwd.getZ(), 0, fwd.getX());
                    float grow = Math.min(1f, ticks / 8f);
                    for (int i = 0; i < list.size(); i++) {
                        double spread = (i - (list.size() - 1) / 2.0) * 0.52;          // angle dans l'eventail
                        double bob = Math.sin(ticks * 0.12 + i * 1.3) * 0.06;
                        Vector out = side.clone().multiply(Math.sin(spread)).add(new Vector(0, Math.cos(spread), 0));
                        Location l = base.clone().add(0, 1.25 + bob, 0).add(fwd.clone().multiply(-0.45)).add(out.clone().multiply(0.95 * grow));
                        l.setYaw(0);
                        l.setPitch(0);
                        ItemDisplay d = list.get(i);
                        d.teleport(l);
                        if (ticks <= 9 || ticks % 4 == 0) {
                            d.setInterpolationDelay(0);
                            d.setInterpolationDuration(2);
                            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), Fx.has("kunai") ? Fx.upright(out, fwd) : Fx.along(out, fwd),
                                    new org.joml.Vector3f(0.55f * grow + 0.05f), new org.joml.Quaternionf()));
                        }
                    }
                    if (ticks % 5 == 0 && !list.isEmpty()) {
                        Location tip = list.get((ticks / 5) % list.size()).getLocation();
                        p.getWorld().spawnParticle(Particle.DUST, tip, 1, 0.1, 0.1, 0.1, 0, WIND);
                    }
                }
            }.runTaskTimer(plugin(), 0L, 1L);
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1f, 1.5f);
            Msg.send(p, "<white>Tempete de Lames : 5 couteaux, clic droit sur l'item pour lancer.");
            return true;
        }

        /** Elle n'est plus Jett (changement de personnage, fin de partie) : les couteaux qui restaient disparaissent. */
        @Override
        public void onUnequip(Player p, CfPlayer cf) {
            AbilityState st = plugin().abilities().state(p, this);
            List<ItemDisplay> list = st.get("displays", null);
            if (list != null) { for (ItemDisplay d : list) d.remove(); list.clear(); }
            st.set("knives", 0);
        }

        @Override
        public String barText(Player p, CfPlayer cf, AbilityState st) {
            int k = st.get("knives", 0);
            return k > 0 ? "<white>Couteaux " + "🗡".repeat(k) : null;
        }
    }
}
