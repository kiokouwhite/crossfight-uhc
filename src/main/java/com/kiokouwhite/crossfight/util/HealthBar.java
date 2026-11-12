package com.kiokouwhite.crossfight.util;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * La barre de vie des combats de Fire Emblem (GBA) : le nombre de points de vie, puis un trait par
 * point, qui se vident un a un au-dessus de la cible apres le coup.
 */
public final class HealthBar {

    /** Largeur maximale de la barre, en traits. Au-dela, un trait vaut plusieurs points de vie. */
    private static final int MAX_SEGMENTS = 40;
    private static final int HOLD_BEFORE = 6, HOLD_AFTER = 34;
    private static final TextColor NUMBER = TextColor.color(0xFFF7C0), FULL = TextColor.color(0xF8D838),
            LOST = TextColor.color(0xE04828), EMPTY = TextColor.color(0x3A3024);

    private static final Map<UUID, TextDisplay> SHOWN = new HashMap<>();

    private HealthBar() {}

    /**
     * Affiche la barre au-dessus de la cible et la vide de sa vie d'avant le coup a sa vie actuelle.
     * A appeler juste apres avoir inflige les degats.
     */
    public static void drain(LivingEntity target, double before) {
        var attr = target.getAttribute(Attribute.MAX_HEALTH);
        double max = Math.max(1, attr != null ? attr.getValue() : before);
        double after = target.isDead() ? 0 : Math.max(0, target.getHealth());
        double from = Math.min(max, Math.max(before, after));
        int segments = (int) Math.min(MAX_SEGMENTS, Math.ceil(max));
        double perSegment = max / segments;
        // Rythme du jeu d'origine : environ un point toutes les tick et demi, entre une demi-seconde et 1,5 s.
        int drainTicks = (int) Math.max(10, Math.min(30, Math.round((from - after) * 1.5)));

        TextDisplay old = SHOWN.remove(target.getUniqueId());
        if (old != null) old.remove();
        TextDisplay bar = target.getWorld().spawn(anchor(target), TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(215, 16, 24, 72));
            d.setSeeThrough(false);
            d.setShadowed(false);
            d.setLineWidth(1000);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setTeleportDuration(1);
            d.setViewRange(1.2f);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(1.25f, 1.25f, 1.25f), new AxisAngle4f()));
            d.text(render(from, from, segments, perSegment));
        });
        SHOWN.put(target.getUniqueId(), bar);

        new BukkitRunnable() {
            int tick;
            int lastShown = -1;
            Component lastText;

            @Override
            public void run() {
                if (!bar.isValid()) { cancel(); return; }
                if (tick > HOLD_BEFORE + drainTicks + HOLD_AFTER) { end(); return; }
                if (target.isValid() && !target.isDead()) bar.teleport(anchor(target));
                double progress = Math.max(0, Math.min(1, (tick - HOLD_BEFORE) / (double) drainTicks));
                double shown = from - (from - after) * progress;
                int points = (int) Math.ceil(shown - 1e-6);
                Component text = render(shown, progress < 1 ? from : shown, segments, perSegment);
                if (!text.equals(lastText)) {
                    bar.text(text);
                    lastText = text;
                }
                // Le petit cliquetis de la barre qui se vide, a chaque point perdu.
                if (points != lastShown && lastShown != -1) bar.getWorld().playSound(bar.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, SoundCategory.PLAYERS, 0.35f, 1.9f);
                lastShown = points;
                tick++;
            }

            void end() {
                bar.remove();
                SHOWN.remove(target.getUniqueId(), bar);
                cancel();
            }
        }.runTaskTimer(CrossfightPlugin.get(), 0L, 1L);
    }

    /** Au-dessus de la tete, plus haut que le nom d'un joueur. */
    private static Location anchor(LivingEntity target) {
        Location l = target.getLocation().add(0, target.getHeight() + 0.75, 0);
        l.setYaw(0); l.setPitch(0);
        return l;
    }

    /** "25 ||||||||||||||||" : les points restants, ceux qui partent (rouge), ceux deja perdus. */
    private static Component render(double shown, double before, int segments, double perSegment) {
        int points = (int) Math.ceil(shown - 1e-6);
        int full = (int) Math.min(segments, Math.ceil(shown / perSegment - 1e-6));
        int leaving = (int) Math.min(segments, Math.ceil(before / perSegment - 1e-6)) - full;
        String number = String.valueOf(points);
        return Component.text(" " + " ".repeat(Math.max(0, 3 - number.length())) + number + " ", NUMBER, TextDecoration.BOLD)
                .append(Component.text("|".repeat(full), FULL).decoration(TextDecoration.BOLD, false))
                .append(Component.text("|".repeat(Math.max(0, leaving)), LOST).decoration(TextDecoration.BOLD, false))
                .append(Component.text("|".repeat(Math.max(0, segments - full - Math.max(0, leaving))) + " ", EMPTY).decoration(TextDecoration.BOLD, false));
    }
}
