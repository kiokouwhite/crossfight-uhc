package com.kiokouwhite.crossfight.character.valorant;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.util.Bullets;
import com.kiokouwhite.crossfight.util.ChoiceMenu;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Revived;
import com.kiokouwhite.crossfight.util.TempBlocks;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Sage - Path of Harmony.
 */
public final class Sage extends Character implements Listener {

    static final NamespacedKey SLOW_ORB = new NamespacedKey("crossfight", "sage_slow_orb");

    public Sage() {
        super("sage", "Sage", Voie.HARMONIE, Material.EMERALD);
        addAbility(new OrbeDeSoin());
        addAbility(new OrbeDeLenteur());
        addAbility(new OrbeBarriere());
        addAbility(Valorant.SHOP);
        addAbility(Guns.ABILITY);
        setUltimate(new Resurrection());
    }

    private static com.kiokouwhite.crossfight.CrossfightPlugin plugin() { return com.kiokouwhite.crossfight.CrossfightPlugin.get(); }

    @Override
    public ItemStack createWeapon() { return Valorant.meleeWeapon(0); }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Orbe de Soin : 3 coeurs a un allie en 3 s",
                "<white>Orbe de Lenteur : zone de Lenteur II 5 s",
                "<white>Orbe Barriere : mur d'emeraude 8x4x3",
                "<light_purple>Ultime : ressuscite un allie");
    }

    /** L'allie vise (a moins de 15 blocs), sinon le plus proche. */
    static LivingEntity targetAlly(Player p) {
        var hit = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), 15, 1.0, e -> e instanceof LivingEntity le && Effects.isAlly(p, le));
        if (hit != null && hit.getHitEntity() instanceof LivingEntity le) return le;
        return Effects.nearestAlly(p, 15);
    }

    static final class OrbeDeSoin extends ActiveAbility {
        OrbeDeSoin() {
            super("sage_soin", "Orbe de Soin", "Sage soigne de 3 coeurs l'allie vise (ou le plus proche), en 3 secondes.", Material.GLOW_INK_SAC, 20);
        }
        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            LivingEntity ally = targetAlly(p);
            if (ally == null) { Msg.actionBar(p, "<red>Orbe de Soin : aucun allie a moins de 15 blocs."); return false; }
            new BukkitRunnable() {
                int n = 0;
                @Override public void run() {
                    if (n++ >= 6 || !ally.isValid() || ally.isDead()) { cancel(); return; }
                    Effects.heal(ally, 1.0);
                    ally.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, ally.getLocation().add(0, 1, 0), 6, 0.4, 0.5, 0.4);
                }
            }.runTaskTimer(plugin(), 0L, 10L);
            p.getWorld().playSound(ally.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
            Msg.actionBar(p, "<green>Orbe de Soin sur " + (ally instanceof Player ap ? ap.getName() : "l'allie"));
            return true;
        }
    }

    static final class OrbeDeLenteur extends ActiveAbility {
        OrbeDeLenteur() {
            super("sage_lenteur", "Orbe de Lenteur", "Sage tire un orbe au sol : pendant 5 secondes, une zone de 5 blocs donne Lenteur II aux ennemis.", Material.SNOWBALL, 10);
        }
        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Arrow a = Bullets.shoot(p, p.getEyeLocation().getDirection(), 1.8, 0.5, Material.CYAN_CONCRETE, Color.fromRGB(100, 230, 220));
            a.getPersistentDataContainer().set(SLOW_ORB, PersistentDataType.BYTE, (byte) 1);
            return true;
        }
    }

    @EventHandler
    public void onSlowOrbLand(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow a) || !a.getPersistentDataContainer().has(SLOW_ORB)) return;
        if (!(a.getShooter() instanceof Player p)) return;
        Location c = a.getLocation();
        World w = c.getWorld();
        new BukkitRunnable() {
            int s = 0;
            @Override public void run() {
                if (s++ >= 5) { cancel(); return; }
                for (double ang = 0; ang < Math.PI * 2; ang += 0.25) w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(ang) * 2.5, 0.3, Math.sin(ang) * 2.5), 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(100, 230, 220), 1.4f));
                w.spawnParticle(Particle.SNOWFLAKE, c, 12, 1.5, 0.5, 1.5, 0.01);
                for (LivingEntity enemy : Effects.enemiesWithin(p, c, 2.5)) Effects.give(enemy, PotionEffectType.SLOWNESS, 1, 30);
            }
        }.runTaskTimer(plugin(), 0L, 20L);
        w.playSound(c, Sound.BLOCK_GLASS_BREAK, 1f, 0.8f);
    }

    static final class OrbeBarriere extends ActiveAbility {
        OrbeBarriere() {
            super("sage_barriere", "Orbe Barriere", "Un bloc devant Sage, un mur en blocs d'emeraude de 8 de long, 4 de haut et 3 de profondeur, pendant 30 secondes.", Material.EMERALD_BLOCK, 15);
        }
        @Override
        public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Vector fwd = Effects.towards(p, null);
            Vector side = new Vector(-fwd.getZ(), 0, fwd.getX());
            Location base = p.getLocation().add(fwd.clone().multiply(2));
            TempBlocks wall = new TempBlocks();
            for (int i = -4; i <= 3; i++) for (int d = 0; d < 3; d++) for (int y = 0; y < 4; y++) {
                wall.place(base.clone().add(side.clone().multiply(i)).add(fwd.clone().multiply(d)).add(0, y, 0).getBlock(), Material.EMERALD_BLOCK);
            }
            wall.restoreAfter(600L);
            p.getWorld().playSound(base, Sound.BLOCK_AMETHYST_BLOCK_PLACE, 1f, 0.8f);
            Msg.actionBar(p, "<green>Orbe Barriere : " + wall.size() + " blocs");
            return true;
        }
    }

    static final class Resurrection extends UltimateAbility {
        Resurrection() {
            super("sage_ult", "Resurrection", "Sage ressuscite l'allie de son choix : il revient avec une vie, Faiblesse permanente et sans pouvoir porter de plastron.", Material.TOTEM_OF_UNDYING);
        }
        @Override
        public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            Team team = cf.team();
            if (team == null) return false;
            List<ChoiceMenu.Option> options = new ArrayList<>();
            for (var u : team.members()) {
                Player o = Bukkit.getPlayer(u);
                if (o == null || o == p) continue;
                CfPlayer ocf = plugin().players().get(o);
                if (ocf.isAlive() && !ocf.isWaitingRespawn()) continue;
                if (Revived.is(o)) continue;
                options.add(new ChoiceMenu.Option(Material.PLAYER_HEAD, o.getName(), List.of("<gray>" + (ocf.character() == null ? "?" : ocf.character().name()),
                        ocf.isWaitingRespawn() ? "<yellow>En attente de reapparition" : "<red>Definitivement elimine"), () -> revive(p, cf, o)));
            }
            if (options.isEmpty()) { Msg.actionBar(p, "<red>Resurrection : aucun allie mort a ramener."); return false; }
            ChoiceMenu.open(p, "<dark_gray>Resurrection : qui ramener ?", options, pl -> cf.setUltimateCharge(100));
            return true;
        }

        private void revive(Player sage, CfPlayer scf, Player target) {
            if (!target.isOnline()) { scf.setUltimateCharge(100); return; }
            Revived.revive(sage, target, scf.team());
            Msg.broadcast("<light_purple>" + sage.getName() + " ressuscite <yellow>" + target.getName() + "</yellow> !");
        }
    }
}
