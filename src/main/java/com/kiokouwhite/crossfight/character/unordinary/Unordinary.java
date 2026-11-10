package com.kiokouwhite.crossfight.character.unordinary;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Licence;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.TeamSize;
import com.kiokouwhite.crossfight.util.Bullets;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Weapons;
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
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

/**
 * Licence UNORDINARY : pas d'arme attitree. Chacun craft une epee d'Aura dont les degats
 * et l'actif dependent du craft (une colonne : ingredient, ingredient, baton).
 */
public final class Unordinary {

    static final NamespacedKey AURA_KEY = new NamespacedKey("crossfight", "aura_type");
    static final NamespacedKey GLUE = new NamespacedKey("crossfight", "uno_glue");

    /** Les cinq epees d'Aura du doc : type, nom, degats, cooldown, ingredients (haut, milieu), description. */
    enum Aura {
        SOIN("soin", "Soin", 8.0, 30, Material.VINE, Material.AMETHYST_SHARD, Material.IRON_SWORD, "Clic droit : Regeneration II pendant 10 secondes."),
        IMMUNITE("immunite", "Immunite", 8.5, 15, Material.INK_SAC, Material.NAUTILUS_SHELL, Material.IRON_SWORD, "Clic droit : immunise a tous les degats pendant 5 secondes."),
        LASER("laser", "Laser", 9.0, 20, Material.REDSTONE, Material.COPPER_INGOT, Material.DIAMOND_SWORD, "Clic droit : projette un rayon de 20 blocs qui inflige 3 coeurs."),
        CHALEUR("chaleur", "Chaleur Augmentee", 9.0, 15, Material.LAVA_BUCKET, Material.NETHER_BRICK, Material.DIAMOND_SWORD, "Clic droit : pendant 5 secondes, une zone de chaleur de 4 blocs autour de toi inflige 1 coeur par seconde."),
        GLUE("glue", "Glue", 8.5, 15, Material.SLIME_BALL, Material.EMERALD, Material.IRON_SWORD, "Clic droit : jette un projectile qui immobilise l'adversaire touche pendant 5 secondes.");

        final String id, label, description; final double damage; final int cooldown; final Material top, middle, sword;
        Aura(String id, String label, double damage, int cooldown, Material top, Material middle, Material sword, String description) {
            this.id = id; this.label = label; this.damage = damage; this.cooldown = cooldown; this.top = top; this.middle = middle; this.sword = sword; this.description = description;
        }
        static Aura byId(String id) { for (Aura a : values()) if (a.id.equalsIgnoreCase(id)) return a; return null; }
        NamespacedKey recipeKey() { return new NamespacedKey("crossfight", "aura_" + id); }
    }

    private Unordinary() {}

    public static Licence create() {
        Licence l = new Licence("unordinary", "UNORDINARY", TeamSize.TRIO,
                "Pas d'arme attitree : chacun craft une epee d'Aura (ingredient, ingredient, baton en colonne) dont l'actif et les degats dependent du craft.");
        Arlo arlo = new Arlo();
        John john = new John();
        Remi remi = new Remi();
        Seraphina seraphina = new Seraphina();
        l.add(arlo); l.add(john); l.add(remi); l.add(seraphina);
        l.listener(new Events());
        l.listener(arlo);
        registerRecipes();
        return l;
    }

    static CrossfightPlugin plugin() { return CrossfightPlugin.get(); }

    /** Les recettes sont enregistrees cote serveur ; seuls les joueurs unOrdinary peuvent les utiliser. */
    static void registerRecipes() {
        for (Aura a : Aura.values()) {
            if (Bukkit.getRecipe(a.recipeKey()) != null) continue;
            ShapedRecipe r = new ShapedRecipe(a.recipeKey(), sword(a, null));
            r.shape(" T ", " M ", " S ");
            r.setIngredient('T', a.top);
            r.setIngredient('M', a.middle);
            r.setIngredient('S', Material.STICK);
            Bukkit.addRecipe(r);
        }
    }

    /** L'epee d'Aura d'un type, decoree pour un personnage (null : item generique de recette). */
    static ItemStack sword(Aura a, Character ch) {
        ItemStack it = Weapons.melee(a.sword, a.damage, "<light_purple><bold>Epee d'Aura - " + a.label,
                List.of("<gray>" + a.damage + " dmg.", "<gray>" + a.description, "", "<yellow>Temps de chargement : <white>" + Msg.time(a.cooldown)));
        Items.tag(it, AURA_KEY, a.id);
        if (ch != null) AbilityEngine.decorateWeapon(it, ch);
        return it;
    }

    static Aura auraOf(ItemStack it) {
        String id = Items.tag(it, AURA_KEY);
        return id == null ? null : Aura.byId(id);
    }

    static boolean isUnordinary(Player p) {
        CfPlayer cf = plugin().players().get(p);
        return cf.character() != null && cf.character().licence() != null && "unordinary".equals(cf.character().licence().id());
    }

    /** Mode test : donne directement une epee d'Aura. */
    public static boolean giveAura(Player p, String type) {
        Aura a = Aura.byId(type);
        CfPlayer cf = plugin().players().get(p);
        if (a == null || cf.character() == null) return false;
        var left = p.getInventory().addItem(sword(a, cf.character()));
        for (ItemStack rest : left.values()) p.getWorld().dropItem(p.getLocation(), rest);
        return true;
    }

    // --------------------------------------------------------------- l'actif

    /** L'actif de l'epee d'Aura : depend du type grave sur l'epee tenue. Partage par les quatre personnages. */
    static final class AuraSword extends ActiveAbility implements CustomUse {
        AuraSword() {
            super("uno_aura", "Epee d'Aura", "Clic droit avec l'epee d'Aura : son actif depend du craft (Soin, Immunite, Laser, Chaleur Augmentee, Glue).", Material.IRON_SWORD, 0);
            bindToWeapon();
        }
        @Override public List<String> weaponLore() { return List.of(); }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            Aura a = auraOf(p.getInventory().getItemInMainHand());
            if (a == null) { for (ItemStack it : p.getInventory().getContents()) { a = auraOf(it); if (a != null) break; } }
            if (a == null) return "<gray>Epee d'Aura <dark_gray>[a crafter]";
            return st.onCooldown() ? "<gray>Aura " + a.label + " <red>" + st.cooldownLeftSeconds() + "s" : "<green>Aura " + a.label + " ✔";
        }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }

        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            Aura a = auraOf(p.getInventory().getItemInMainHand());
            if (a == null) { Msg.actionBar(p, "<red>Tiens une epee d'Aura en main."); return true; }
            if (st.onCooldown()) {
                Msg.actionBar(p, "<red>Aura " + a.label + " : encore " + st.cooldownLeftSeconds() + "s");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
                return true;
            }
            boolean ok = switch (a) {
                case SOIN -> soin(p);
                case IMMUNITE -> immunite(p);
                case LASER -> laser(p);
                case CHALEUR -> chaleur(p);
                case GLUE -> glue(p);
            };
            if (ok) {
                st.startCooldown((int) Math.ceil(a.cooldown * cf.cooldownFactor()));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
            }
            return true;
        }

        static boolean soin(Player p) {
            Effects.give(p, PotionEffectType.REGENERATION, 1, 200);
            p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.5, 0), 8, 0.4, 0.4, 0.4);
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 20, 0.5, 0.6, 0.5);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
            Msg.actionBar(p, "<green>Aura de Soin : regeneration 10 s");
            return true;
        }

        static boolean immunite(Player p) {
            Effects.invincible(p, 5000L);
            World w = p.getWorld();
            w.playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
            Particle.DustOptions shell = new Particle.DustOptions(Color.fromRGB(40, 60, 120), 1.2f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t += 4;
                    if (!p.isOnline() || t > 100) { cancel(); return; }
                    w.spawnParticle(Particle.DUST, p.getLocation().add(0, 1.1, 0), 8, 0.5, 0.8, 0.5, 0, shell);
                }
            }.runTaskTimer(plugin(), 0L, 4L);
            Msg.actionBar(p, "<blue>Aura d'Immunite : 5 s sans degats");
            return true;
        }

        static boolean laser(Player p) {
            World w = p.getWorld();
            Location eye = p.getEyeLocation();
            Vector dir = eye.getDirection();
            Particle.DustOptions core = new Particle.DustOptions(Color.fromRGB(255, 60, 60), 1.4f);
            Particle.DustOptions glow = new Particle.DustOptions(Color.fromRGB(255, 180, 120), 0.8f);
            java.util.Set<UUID> hit = new java.util.HashSet<>();
            for (double d = 0.5; d <= 20; d += 0.4) {
                Location l = eye.clone().add(dir.clone().multiply(d));
                if (!l.getBlock().isPassable()) break;
                w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, core);
                w.spawnParticle(Particle.DUST, l, 1, 0.15, 0.15, 0.15, 0, glow);
                for (LivingEntity enemy : Effects.enemiesWithin(p, l, 1.0)) if (hit.add(enemy.getUniqueId())) Effects.damage(enemy, 6.0, p);
            }
            w.playSound(eye, Sound.ENTITY_GUARDIAN_ATTACK, 1f, 1.6f);
            w.playSound(eye, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.8f);
            Msg.actionBar(p, hit.isEmpty() ? "<gray>Laser : dans le vide." : "<red>Laser : " + hit.size() + " adversaire(s) touche(s)");
            return true;
        }

        static boolean chaleur(Player p) {
            World w = p.getWorld();
            w.playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (!p.isOnline() || p.isDead() || t > 5) { cancel(); return; }
                    Location c = p.getLocation();
                    w.spawnParticle(Particle.FLAME, c.clone().add(0, 0.5, 0), 60, 2.0, 0.6, 2.0, 0.02);
                    w.spawnParticle(Particle.LAVA, c.clone().add(0, 0.5, 0), 6, 2.0, 0.3, 2.0, 0);
                    w.playSound(c, Sound.BLOCK_FIRE_AMBIENT, 1f, 1f);
                    for (LivingEntity enemy : Effects.enemiesWithin(p, 4)) { enemy.setNoDamageTicks(0); Effects.damage(enemy, 2.0, p); }
                }
            }.runTaskTimer(plugin(), 0L, 20L);
            Msg.actionBar(p, "<gold>Chaleur Augmentee : 5 s");
            return true;
        }

        static boolean glue(Player p) {
            Arrow a = Bullets.shoot(p, p.getEyeLocation().getDirection(), 2.0, 0.0, Material.SLIME_BLOCK, Color.fromRGB(120, 220, 90));
            a.getPersistentDataContainer().set(GLUE, PersistentDataType.BYTE, (byte) 1);
            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_SLIME_BLOCK_PLACE, 1f, 0.8f);
            return true;
        }
    }

    static final AuraSword AURA = new AuraSword();

    // ---------------------------------------------------------------- events

    static final class Events implements Listener {

        /** Seuls les joueurs unOrdinary craftent les epees d'Aura ; le resultat est decore pour leur personnage. */
        @EventHandler
        public void onPrepareCraft(PrepareItemCraftEvent e) {
            if (!(e.getRecipe() instanceof org.bukkit.Keyed k) || !k.getKey().getNamespace().equals("crossfight") || !k.getKey().getKey().startsWith("aura_")) return;
            if (e.getViewers().isEmpty() || !(e.getViewers().get(0) instanceof Player p)) return;
            Aura a = Aura.byId(k.getKey().getKey().substring("aura_".length()));
            if (a == null) return;
            if (!isUnordinary(p) || !plugin().game().isRunning()) {
                e.getInventory().setResult(null);
                Msg.actionBar(p, "<red>Seuls les personnages unOrdinary craftent les epees d'Aura.");
                return;
            }
            e.getInventory().setResult(sword(a, plugin().players().get(p).character()));
        }

        @EventHandler
        public void onGlueHit(ProjectileHitEvent e) {
            if (!(e.getEntity() instanceof Arrow a) || !a.getPersistentDataContainer().has(GLUE) || !(a.getShooter() instanceof Player p)) return;
            Location c = a.getLocation();
            c.getWorld().spawnParticle(Particle.ITEM_SLIME, c, 25, 0.4, 0.4, 0.4, 0.05);
            c.getWorld().playSound(c, Sound.BLOCK_SLIME_BLOCK_FALL, 1f, 0.9f);
            if (e.getHitEntity() instanceof LivingEntity target && Effects.isEnemy(p, target)) {
                Effects.root(target, 100);
                Effects.say(target, "<green>Englue par " + p.getName() + " : immobilise 5 s !");
                Msg.actionBar(p, "<green>Glue : adversaire immobilise 5 s.");
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        t += 10;
                        if (t > 100 || target.isDead()) { cancel(); return; }
                        target.getWorld().spawnParticle(Particle.ITEM_SLIME, target.getLocation().add(0, 0.5, 0), 6, 0.3, 0.3, 0.3, 0.01);
                    }
                }.runTaskTimer(plugin(), 10L, 10L);
            }
        }
    }
}
