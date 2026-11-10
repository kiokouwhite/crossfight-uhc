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
import com.kiokouwhite.crossfight.util.Msg;
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
            p.setVelocity(dir.multiply(1.6).setY(0.15));
            Ruby.noFallUntil(p, 2000);
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, 0.05);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PHANTOM_FLAP, 1f, 1.8f);
            return true;
        }
    }

    static final class CourantAscendant extends ActiveAbility {
        CourantAscendant() {
            super("jett_courant", "Courant Ascendant", "Jett se propulse de 5 blocs en hauteur.", Material.LIGHT_GRAY_DYE, 3);
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Effects.launchUp(p, 5);
            Ruby.noFallUntil(p, 3000);
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 25, 0.4, 0.1, 0.4, 0.05);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 1.2f);
            return true;
        }
    }

    static final class Flottement extends ToggleAbility {
        Flottement() {
            super("jett_flottement", "Flottement", "Tant que c'est actif, Jett a Chute lente.", Material.FEATHER, 0.0, 0.0);
        }
        @Override public boolean onEnable(Player p, CfPlayer cf, AbilityState st) { return true; }
        @Override public void tickActive(Player p, CfPlayer cf, AbilityState st) { Effects.refresh(p, PotionEffectType.SLOW_FALLING, 0, 45); }
        @Override public void onDisable(Player p, CfPlayer cf, AbilityState st) { p.removePotionEffect(PotionEffectType.SLOW_FALLING); }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { return st.isEnabled() ? "<white>Flottement <aqua>actif" : "<gray>Flottement"; }
    }

    /** Cinq couteaux tournent autour de Jett ; chaque clic en lance un (2 coeurs). */
    static final class TempeteDeLames extends UltimateAbility implements CustomUse {
        TempeteDeLames() {
            super("jett_ult", "Tempete de Lames", "Jett invoque 5 couteaux autour d'elle. Chaque clic droit sur cet item en lance un, infligeant 2 coeurs.", Material.IRON_SWORD);
        }

        @Override
        public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            int knives = st.get("knives", 0);
            if (knives <= 0) return false; // lancement standard de l'ultime
            st.set("knives", knives - 1);
            Bullets.shoot(p, p.getEyeLocation().getDirection(), 3.0, 1.34, Material.WHITE_CONCRETE, Color.WHITE);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.6f);
            List<ItemDisplay> displays = st.get("displays", null);
            if (displays != null && !displays.isEmpty()) displays.remove(displays.size() - 1).remove();
            if (knives - 1 <= 0) Msg.actionBar(p, "<gray>Plus de couteaux.");
            return true;
        }

        @Override
        public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            st.set("knives", 5);
            List<ItemDisplay> displays = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                ItemDisplay d = p.getWorld().spawn(p.getLocation(), ItemDisplay.class, disp -> {
                    disp.setItemStack(new ItemStack(Material.IRON_SWORD));
                    disp.setPersistent(false);
                    disp.setTeleportDuration(1);
                });
                displays.add(d);
            }
            st.set("displays", displays);
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
                    for (int i = 0; i < list.size(); i++) {
                        double a = ticks * 0.15 + i * (Math.PI * 2 / list.size());
                        Location l = p.getLocation().add(Math.cos(a) * 1.3, 1.2 + Math.sin(ticks * 0.1 + i) * 0.2, Math.sin(a) * 1.3);
                        l.setYaw((float) Math.toDegrees(a) + 90);
                        l.setPitch(90);
                        list.get(i).teleport(l);
                    }
                }
            }.runTaskTimer(plugin(), 0L, 1L);
            p.getWorld().playSound(p.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1f, 1.5f);
            Msg.send(p, "<white>Tempete de Lames : 5 couteaux, clic droit sur l'item pour lancer.");
            return true;
        }

        @Override
        public String barText(Player p, CfPlayer cf, AbilityState st) {
            int k = st.get("knives", 0);
            return k > 0 ? "<white>Couteaux " + "🗡".repeat(k) : null;
        }
    }
}
