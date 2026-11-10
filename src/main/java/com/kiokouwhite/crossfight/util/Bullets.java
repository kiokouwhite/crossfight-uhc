package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * "Balles" : une fleche invisible (physique et degats d'une fleche) habillee d'une
 * petite bille lumineuse qui la suit, avec une trainee de la meme couleur.
 */
public final class Bullets implements Listener {

    public static Arrow shoot(LivingEntity shooter, Vector dir, double speed, double damage, Material bullet, Color trail) {
        Arrow arrow = Effects.shootInstantArrow(shooter, dir, speed, damage);
        arrow.setInvisible(true);
        BlockDisplay bead = arrow.getWorld().spawn(arrow.getLocation(), BlockDisplay.class, d -> {
            d.setBlock(bullet.createBlockData());
            float s = 0.18f;
            d.setTransformation(new org.bukkit.util.Transformation(new Vector3f(-s / 2, -s / 2, -s / 2), new Quaternionf(), new Vector3f(s, s, s), new Quaternionf()));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setGlowing(true);
            d.setGlowColorOverride(trail);
            d.setPersistent(false);
        });
        arrow.addPassenger(bead);
        Particle.DustOptions dust = new Particle.DustOptions(trail, 0.9f);
        new BukkitRunnable() {
            int ticks = 0;
            Location last = arrow.getLocation();
            @Override public void run() {
                ticks++;
                if (!arrow.isValid() || arrow.isOnGround() || arrow.isInBlock() || ticks > 60) {
                    bead.remove();
                    if (arrow.isValid()) arrow.remove();
                    cancel();
                    return;
                }
                Location now = arrow.getLocation();
                Vector step = now.toVector().subtract(last.toVector());
                double len = step.length();
                if (len > 0.01) {
                    step.normalize();
                    for (double d = 0; d < len; d += 0.4) arrow.getWorld().spawnParticle(Particle.DUST, last.clone().add(step.clone().multiply(d)), 1, 0, 0, 0, 0, dust);
                }
                last = now;
            }
        }.runTaskTimer(CrossfightPlugin.get(), 0L, 1L);
        return arrow;
    }

    @EventHandler
    public void onHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow a) || !a.isInvisible()) return;
        for (var passenger : a.getPassengers()) if (passenger instanceof BlockDisplay) passenger.remove();
        if (e.getHitEntity() != null) {
            a.getWorld().spawnParticle(Particle.CRIT, a.getLocation(), 8, 0.2, 0.2, 0.2, 0.1);
        }
        // Une balle ne reste pas plantee dans le decor.
        org.bukkit.Bukkit.getScheduler().runTask(CrossfightPlugin.get(), () -> { if (a.isValid()) a.remove(); });
    }
}
