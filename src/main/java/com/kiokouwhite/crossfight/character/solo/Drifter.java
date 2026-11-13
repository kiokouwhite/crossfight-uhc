package com.kiokouwhite.crossfight.character.solo;

import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.ArenaWorlds;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.Bullets;
import com.kiokouwhite.crossfight.util.Effects;
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
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Le Drifter - Voie de la Chasse. Six pistolets, un coeur mourant, et une arene finale.
 */
public final class Drifter extends Character implements Listener, Solo.Burst {

    static final NamespacedKey GRENADE = new NamespacedKey("crossfight", "drifter_grenade");
    static final String ARENA_WORLD = "crossfight_ruine";

    enum Gun {
        PISTOL("Pistol", 5.0, 6, 500, "Aucune particularite."),
        ZELISKA("Zeliska", 9.0, 4, 3000, "La balle met 2 secondes a partir ; immobile pendant la charge."),
        SHOTGUN("Diamond Shotgun", 6.0, 3, 2000, "Tir en Multishot (3 balles)."),
        BLUNDERBUSS("Blunderbuss", 5.0, 4, 2000, "5 balles eparpillees, puis immobile 1 seconde."),
        IMPACT_RAILGUN("Impact Railgun", 8.0, 2, 2000, "Les balles traversent les adversaires."),
        RAILGUN("Railgun", 6.0, 4, 2000, "Balles presque invisibles et instantanees.");
        final String label, special; final double damage; final int ammo; final long rateMs;
        Gun(String label, double damage, int ammo, long rateMs, String special) { this.label = label; this.damage = damage; this.ammo = ammo; this.rateMs = rateMs; this.special = special; }
    }

    public Drifter() {
        super("drifter", "Le Drifter", Voie.CHASSE, Material.IRON_HORSE_ARMOR);
        addAbility(new HardLight().bindToWeapon());
        addAbility(new Pistolets());
        addAbility(new ChainDash());
        addAbility(new SpecialGrenade());
        addAbility(new CoeurMourant());
        addAbility(Solo.ITEM);
        setUltimate(new AffronterSesDemons());
    }

    @Override public ItemStack createWeapon() { return Weapons.melee(Material.GOLDEN_SWORD, 7.0, "<yellow><bold>Hard Light", List.of("<gray>7 dmg. Renvoie les projectiles.")); }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Hard Light : renvoie les projectiles, attaque circulaire",
                "<white>Pistolets : 6 armes a munitions (accroupi + clic pour changer)",
                "<white>Chain Dash : un dash par seconde en rythme - Special Grenade",
                "<white>Coeur Mourant : barre de sante de 100 qui fond avec le temps",
                "<light_purple>Ultime : Judgement tombe, 0 a 9 coeurs selon la sante",
                "<gold>Burst : Monde en Ruine, l'arene finale");
    }

    static AbilityState sante(Player p) {
        CfPlayer cf = Solo.plugin().players().get(p);
        Ability a = cf.character() == null ? null : cf.character().ability("drifter_coeur");
        return a == null ? null : Solo.plugin().abilities().state(p, a);
    }
    static double santeValue(Player p) { AbilityState st = sante(p); return st == null ? 100 : st.get("sante", 100.0); }
    /** Test : fixe la sante (0-100). Faux si le joueur n'est pas le Drifter. */
    public static boolean setSante(Player p, double n) {
        AbilityState st = sante(p);
        if (st == null) return false;
        st.set("sante", Math.max(0, Math.min(100, n)));
        return true;
    }

    public static void addSante(Player p, double n, String why) {
        AbilityState st = sante(p);
        if (st == null) return;
        double v = Math.max(0, Math.min(100, st.get("sante", 100.0) + n));
        st.set("sante", v);
        if (n > 0) Msg.actionBar(p, "<green>Sante +" + (int) n + " <gray>(" + why + ") : " + (int) v);
    }
    static boolean inArena(Player p) { return p.getWorld().getName().equals(ARENA_WORLD); }

    // ------------------------------------------------------------------ arme

    static final class HardLight extends ActiveAbility {
        HardLight() { super("drifter_hardlight", "Hard Light", "Passif : les projectiles qui touchent le Drifter sont renvoyes a leur tireur. Clic droit : attaque circulaire de 2 coeurs a 3 blocs.", Material.GOLDEN_SWORD, 8); }
        @Override public List<String> weaponLore() { return List.of("<yellow>Passif : <gray>renvoie les projectiles", "<yellow>Clic droit : <gray>attaque circulaire, 2 coeurs a 3 blocs"); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            World w = p.getWorld();
            Location c = p.getLocation().add(0, 1, 0);
            for (int i = 0; i < 16; i++) { double a = i * Math.PI / 8; w.spawnParticle(Particle.SWEEP_ATTACK, c.clone().add(Math.cos(a) * 2, 0, Math.sin(a) * 2), 1); }
            w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
            int n = 0;
            for (LivingEntity e : Effects.enemiesWithin(p, 3)) { e.setNoDamageTicks(0); Effects.damage(e, 4.0, p); n++; }
            if (n == 0) Msg.actionBar(p, "<gray>Hard Light : dans le vide.");
            return true;
        }
    }

    /** Un projectile touche le Drifter : il repart vers son tireur. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectile(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player v) || !"drifter".equals(Solo.characterIdOf(v)) || !(e.getDamager() instanceof Projectile proj)) return;
        if (!(proj.getShooter() instanceof LivingEntity shooter) || shooter == v) return;
        e.setCancelled(true);
        Vector dir = shooter.getLocation().add(0, 1, 0).toVector().subtract(v.getEyeLocation().toVector()).normalize();
        Arrow back = Effects.shootInstantArrow(v, dir, 2.5, 6.0);
        back.setCritical(true);
        v.getWorld().spawnParticle(Particle.ENCHANTED_HIT, v.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.1);
        v.getWorld().playSound(v.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 1.6f);
        Bukkit.getScheduler().runTask(Solo.plugin(), proj::remove);
        Msg.actionBar(v, "<yellow>Hard Light : projectile renvoye !");
    }

    // ------------------------------------------------------------- pistolets

    static final class Pistolets extends ActiveAbility implements CustomUse {
        Pistolets() { super("drifter_guns", "Pistolets", "Accroupi + clic droit : changer de pistolet. Clic droit : tirer. Un pistolet vide est inutilisable ; quand tous sont vides, tous se rechargent.", Material.IRON_HORSE_ARMOR, 0); }
        @Override protected List<String> extraLore() {
            java.util.List<String> l = new java.util.ArrayList<>();
            for (Gun g : Gun.values()) l.add("<gray>" + g.label + " : " + g.ammo + " balles, " + g.special);
            l.add(""); l.add("<yellow>Accroupi + clic : changer  <gray>|  <yellow>Clic : tirer");
            return l;
        }
        static Gun current(AbilityState st) { return Gun.values()[st.get("gun", 0)]; }
        static int ammo(AbilityState st, Gun g) { return st.get("ammo_" + g.name(), g.ammo); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { Gun g = current(st); return "<yellow>" + g.label + " <white>" + ammo(st, g) + "/" + g.ammo; }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            if (sneaking) {
                int next = (st.get("gun", 0) + 1) % Gun.values().length;
                st.set("gun", next);
                Gun g = Gun.values()[next];
                Msg.actionBar(p, "<yellow>" + g.label + " <gray>(" + ammo(st, g) + "/" + g.ammo + ") - " + g.special);
                p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1.4f);
                return true;
            }
            Gun g = current(st);
            long now = System.currentTimeMillis();
            if (now < st.get("next", 0L)) return true;
            if (ammo(st, g) <= 0) { Msg.actionBar(p, "<red>" + g.label + " : vide."); p.playSound(p.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 1f, 1.2f); return true; }
            st.set("next", now + g.rateMs);
            st.set("ammo_" + g.name(), ammo(st, g) - 1);
            fire(p, g);
            // Tous vides : tout se recharge.
            boolean allEmpty = true;
            for (Gun x : Gun.values()) if (ammo(st, x) > 0) allEmpty = false;
            if (allEmpty) { for (Gun x : Gun.values()) st.remove("ammo_" + x.name()); Msg.actionBar(p, "<green>Tous les pistolets rechargent !"); p.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_LOADING_END, 1f, 1f); }
            return true;
        }
        static void fire(Player p, Gun g) {
            World w = p.getWorld();
            com.kiokouwhite.crossfight.util.Sounds.sfx(p.getLocation(), g == Gun.RAILGUN || g == Gun.IMPACT_RAILGUN ? "laser" : "gun");
            Vector dir = p.getEyeLocation().getDirection();
            Color c = Color.fromRGB(255, 230, 120);
            switch (g) {
                case PISTOL -> { Bullets.shoot(p, dir, 3.0, g.damage, Material.YELLOW_CONCRETE, c); w.playSound(p.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.6f, 1.8f); }
                case ZELISKA -> {
                    Effects.root(p, 40);
                    Msg.actionBar(p, "<yellow>Zeliska : charge...");
                    w.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.6f);
                    Bukkit.getScheduler().runTaskLater(Solo.plugin(), () -> { if (p.isOnline()) { Bullets.shoot(p, p.getEyeLocation().getDirection(), 4.0, g.damage, Material.ORANGE_CONCRETE, c); w.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.6f); } }, 40L);
                }
                case SHOTGUN -> { for (int k : new int[]{-8, 0, 8}) Bullets.shoot(p, dir.clone().rotateAroundY(Math.toRadians(k)), 3.0, g.damage, Material.LIGHT_BLUE_CONCRETE, c); w.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.8f); }
                case BLUNDERBUSS -> {
                    for (int k = 0; k < 5; k++) Bullets.shoot(p, dir.clone().rotateAroundY(Math.toRadians(-10 + k * 5)).add(new Vector(0, (Math.random() - 0.5) * 0.1, 0)), 2.5, g.damage, Material.GRAY_CONCRETE, c);
                    Effects.root(p, 20);
                    w.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
                }
                case IMPACT_RAILGUN -> { Arrow a = Bullets.shoot(p, dir, 4.0, g.damage, Material.PURPLE_CONCRETE, c); a.setPierceLevel(5); w.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 2f); }
                case RAILGUN -> {
                    // Hitscan : la premiere cible sur la ligne, 40 blocs, presque sans trace.
                    Location eye = p.getEyeLocation();
                    LivingEntity hit = null;
                    for (double d = 0.5; d <= 40 && hit == null; d += 0.5) {
                        Location l = eye.clone().add(dir.clone().multiply(d));
                        if (!l.getBlock().isPassable()) break;
                        if (d % 4 < 0.6) w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(200, 200, 255), 0.4f));
                        for (LivingEntity e : Effects.enemiesWithin(p, l, 0.8)) { hit = e; break; }
                    }
                    if (hit != null) { hit.setNoDamageTicks(0); Effects.damage(hit, g.damage, p); w.spawnParticle(Particle.CRIT, hit.getLocation().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0.1); }
                    w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.4f, 2f);
                }
            }
        }
    }

    // ---------------------------------------------------------------- pouvoirs

    /** Un dash par seconde tant que le rythme est tenu ; rater le rythme coute 15 s. Immunise aux projectiles pendant le dash. */
    static final class ChainDash extends ActiveAbility implements CustomUse {
        ChainDash() { super("drifter_chain", "Chain Dash", "Un dash de 5 blocs par seconde tant que le rythme est tenu (re-clic entre 0.7 et 1.3 s) ; rater le rythme bloque le dash 15 secondes. Immunise aux projectiles pendant le dash ; percuter un ennemi arrete le dash et lui inflige 2 coeurs.", Material.FEATHER, 0); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            long lock = st.get("lockUntil", 0L);
            if (System.currentTimeMillis() < lock) return "<gray>Chain Dash <red>" + ((lock - System.currentTimeMillis()) / 1000 + 1) + "s";
            return st.get("chain", 0) > 0 ? "<yellow>Chain Dash <white>x" + st.get("chain", 0) : "<green>Chain Dash ✔";
        }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            long now = System.currentTimeMillis();
            if (now < st.get("lockUntil", 0L)) { Msg.actionBar(p, "<red>Chain Dash : rythme rate, encore " + ((st.get("lockUntil", 0L) - now) / 1000 + 1) + "s"); return true; }
            long last = st.get("last", 0L);
            int chain = st.get("chain", 0);
            if (chain > 0 && now - last < 700) return true; // trop tot : ignore
            st.set("last", now); st.set("chain", chain + 1);
            st.set("dashUntil", now + 400L);
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
            dir.normalize();
            World w = p.getWorld();
            Location from = p.getLocation();
            final Vector d = dir;
            new BukkitRunnable() {
                int i = 0; Location cur = from.clone();
                @Override public void run() {
                    i++;
                    if (!p.isOnline() || p.isDead() || i > 5) { cancel(); return; }
                    Location next = cur.clone().add(d);
                    if (!next.getBlock().isPassable() || !next.clone().add(0, 1, 0).getBlock().isPassable()) { cancel(); return; }
                    w.spawnParticle(Particle.CLOUD, next.clone().add(0, 0.5, 0), 3, 0.2, 0.2, 0.2, 0.02);
                    for (LivingEntity e : Effects.enemiesWithin(p, next.clone().add(0, 1, 0), 1.5)) {
                        e.setNoDamageTicks(0); Effects.damage(e, 4.0, p);
                        w.playSound(next, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 1f);
                        cancel(); return;
                    }
                    next.setDirection(p.getLocation().getDirection());
                    p.teleport(next); cur = next;
                }
            }.runTaskTimer(Solo.plugin(), 0L, 1L);
            w.playSound(from, Sound.ENTITY_BREEZE_SHOOT, 0.8f, 1.5f);
            // Le rythme : 1.3 s pour re-cliquer, sinon la chaine casse.
            int myChain = chain + 1;
            Bukkit.getScheduler().runTaskLater(Solo.plugin(), () -> {
                if (st.get("chain", 0) == myChain && System.currentTimeMillis() - st.get("last", 0L) >= 1250) {
                    st.set("chain", 0);
                    st.set("lockUntil", System.currentTimeMillis() + 15000L);
                    Msg.actionBar(p, "<red>Chain Dash : rythme rate, 15 s.");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                }
            }, 26L);
            return true;
        }
        @Override public void onDamaged(Player victim, EntityDamageEvent e, AbilityState st) {
            if (System.currentTimeMillis() < st.get("dashUntil", 0L) && e instanceof EntityDamageByEntityEvent be && be.getDamager() instanceof Projectile) e.setCancelled(true);
        }
    }

    static final class SpecialGrenade extends ActiveAbility {
        SpecialGrenade() { super("drifter_grenade", "Special Grenade", "Une grenade jetee au sol explose une seconde plus tard : 3 coeurs aux adversaires a 3 blocs.", Material.FIRE_CHARGE, 10); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            Arrow a = Bullets.shoot(p, p.getEyeLocation().getDirection().add(new Vector(0, 0.25, 0)), 1.2, 0.0, Material.GRAY_CONCRETE, Color.fromRGB(120, 120, 120));
            a.getPersistentDataContainer().set(GRENADE, PersistentDataType.BYTE, (byte) 1);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_SNOWBALL_THROW, 1f, 0.8f);
            return true;
        }
    }

    @EventHandler
    public void onGrenadeLand(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow a) || !a.getPersistentDataContainer().has(GRENADE) || !(a.getShooter() instanceof Player p)) return;
        Location c = a.getLocation().clone();
        World w = c.getWorld();
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                if (t < 20) { w.spawnParticle(Particle.SMOKE, c, 2, 0.1, 0.1, 0.1, 0.01); if (t % 5 == 0) w.playSound(c, Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1.6f); return; }
                w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 1);
                w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
                for (LivingEntity x : Effects.within(c, 3, le -> Effects.isEnemy(p, le))) { x.setNoDamageTicks(0); Effects.damage(x, 6.0, p); }
                cancel();
            }
        }.runTaskTimer(Solo.plugin(), 0L, 1L);
    }

    /** La barre de sante : fond avec le temps de partie, bonus et malus par paliers. */
    static final class CoeurMourant extends PassiveAbility {
        CoeurMourant() { super("drifter_coeur", "Coeur Mourant", "Une barre de sante de 100 : -1 par minute apres 30 min, -3 apres 1 h, -10 apres 1 h 30. 90+ Force III, 80+ Force II, 70+ Force, 60+ Speed, 50+ normal ; en dessous, toux, hallucinations puis Judgement. Se regagne : kill +10, equipe eliminee +15, Pomme Doree +3, lootbox +30.", Material.POISONOUS_POTATO); }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { return "<red>Sante <white>" + (int) (double) st.get("sante", 100.0); }
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) {
            int sec = st.get("sec", 0) + 1; st.set("sec", sec);
            if (!Solo.plugin().game().isTest() && sec % 60 == 0) {
                int min = Solo.plugin().game().elapsedMinutes();
                double loss = min >= 90 ? 10 : min >= 60 ? 3 : min >= 30 ? 1 : 0;
                if (loss > 0) st.set("sante", Math.max(0, st.get("sante", 100.0) - loss));
            }
            double s = st.get("sante", 100.0);
            if (inArena(p)) { Boosts.remove(p, "drifter_sante"); return; }
            Boosts.Boost b = s >= 90 ? new Boosts.Boost(3, 0, 0, 0) : s >= 80 ? new Boosts.Boost(2, 0, 0, 0) : s >= 70 ? new Boosts.Boost(1, 0, 0, 0) : s >= 60 ? new Boosts.Boost(0, 1, 0, 0) : null;
            Boosts.Boost cur = Boosts.get(p, "drifter_sante");
            if (b == null) { if (cur != null) Boosts.remove(p, "drifter_sante"); }
            else if (cur == null || cur.force != b.force || cur.speed != b.speed) Boosts.set(p, "drifter_sante", b);
            World w = p.getWorld();
            if (s >= 50) return;
            if (s >= 40) { if (sec % 30 == 0) { Effects.give(p, PotionEffectType.SLOWNESS, 0, 40); w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BREATH, 1f, 0.6f); Msg.actionBar(p, "<gray>Tu toussotes..."); } }
            else if (s >= 30) { if (sec % 20 == 0) { Effects.give(p, PotionEffectType.SLOWNESS, 0, 40); cf.silence(2000L); w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_BREATH, 1f, 0.5f); Msg.actionBar(p, "<gray>Tu tousses : 2 s sans attaquer."); } }
            else if (s >= 20) { if (sec % 10 == 0) { Effects.give(p, PotionEffectType.SLOWNESS, 2, 60); cf.silence(3000L); p.setHealth(Math.max(1, p.getHealth() - 2)); w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_HURT, 1f, 0.6f); Msg.actionBar(p, "<red>Tu tousses fortement : -1 coeur, 3 s sans attaquer."); } }
            else if (s >= 10) { if (sec % 10 == 0) { Effects.root(p, 60); Effects.give(p, PotionEffectType.BLINDNESS, 0, 60); w.playSound(p.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.7f, 0.5f); Msg.actionBar(p, "<dark_purple>Tu hallucines Judgement..."); } }
            else { if (sec % 10 == 0) { judgement(p, p.getLocation(), 5.0, null); Msg.actionBar(p, "<dark_red>Judgement apparait : -5 coeurs !"); } }
        }
        @Override public void onKill(Player killer, Player victim, AbilityState st) { addSante(killer, 10, "kill"); }
    }

    /** Pendant les 3 secondes qui suivent un coup, le Drifter qui tousse ne peut pas attaquer. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCoughAttack(EntityDamageByEntityEvent e) {
        Player a = com.kiokouwhite.crossfight.listener.DamageListener.meleeAttackerOf(e.getDamager());
        if (a == null || !"drifter".equals(Solo.characterIdOf(a))) return;
        if (Solo.plugin().players().get(a).isSilenced() && santeValue(a) < 40 && !inArena(a)) { e.setCancelled(true); Msg.actionBar(a, "<gray>Tu tousses : impossible d'attaquer."); }
    }

    @EventHandler
    public void onGapple(PlayerItemConsumeEvent e) {
        Player p = e.getPlayer();
        if (!"drifter".equals(Solo.characterIdOf(p))) return;
        Material m = e.getItem().getType();
        if (m == Material.GOLDEN_APPLE || m == Material.ENCHANTED_GOLDEN_APPLE) addSante(p, 3, "Pomme Doree");
    }

    /** Judgement tombe du ciel sur un point : une enclume geante qui ecrase. */
    static void judgement(Player owner, Location at, double hearts, Player exclude) {
        World w = at.getWorld();
        ItemDisplay anvil = w.spawn(at.clone().add(0, 12, 0), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.ANVIL));
            d.setTransformation(new Transformation(new Vector3f(), new Quaternionf(), new Vector3f(3f, 3f, 3f), new Quaternionf()));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setPersistent(false);
        });
        w.playSound(at, Sound.ENTITY_WITHER_AMBIENT, 1f, 0.5f);
        new BukkitRunnable() {
            int t = 0;
            @Override public void run() {
                t++;
                anvil.teleport(at.clone().add(0, Math.max(0, 12 - t * 1.5), 0));
                w.spawnParticle(Particle.SMOKE, anvil.getLocation(), 5, 0.5, 0.5, 0.5, 0.02);
                if (t >= 8) {
                    anvil.remove();
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, at, 2, 1, 0.3, 1, 0);
                    w.spawnParticle(Particle.BLOCK, at, 60, 1.5, 0.5, 1.5, 0.1, Material.ANVIL.createBlockData());
                    w.playSound(at, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.5f);
                    w.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.7f);
                    if (hearts > 0) {
                        if (exclude == null) { owner.setNoDamageTicks(0); owner.setHealth(Math.max(1, owner.getHealth() - hearts * 2)); owner.damage(0.01); }
                        else for (LivingEntity e : Effects.within(at, 3, le -> Effects.isEnemy(owner, le))) { e.setNoDamageTicks(0); Effects.damage(e, hearts * 2, owner); }
                    }
                    cancel();
                }
            }
        }.runTaskTimer(Solo.plugin(), 0L, 1L);
    }

    static final class AffronterSesDemons extends UltimateAbility {
        AffronterSesDemons() { super("drifter_ult", "Affronter ses demons", "Judgement tombe sur l'adversaire le plus proche (10 blocs) : 0 a 9 coeurs selon la barre de sante (plus elle est basse, plus il frappe fort).", Material.ANVIL); }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            LivingEntity t = Effects.nearestEnemy(p, 10);
            if (t == null) { Msg.actionBar(p, "<red>Aucun adversaire a moins de 10 blocs."); return false; }
            double hearts = Math.round(9.0 * (100 - santeValue(p)) / 100.0);
            judgement(p, t.getLocation(), hearts, p);
            Msg.actionBar(p, "<dark_purple>Judgement tombe : " + (int) hearts + " coeur(s).");
            return true;
        }
    }

    // ------------------------------------------------------------------ burst

    static final Map<UUID, Location> RETURN = new HashMap<>();
    static UUID arenaOwner;

    static World arena() {
        World w = ArenaWorlds.get(ARENA_WORLD);
        ArenaWorlds.buildPlatform(w, 25, Material.POLISHED_DEEPSLATE, Material.BARRIER, 8);
        return w;
    }

    @Override public String burstName() { return "Monde en Ruine"; }
    @Override public String burstDescription() { return "Tous les adversaires a 40 blocs sont teleportes avec le Drifter dans l'arene finale : il y a Force III, Resistance II et Speed II, sans les effets de sa sante. Il en sort en tuant tous ses adversaires, ou en mourant sans perdre de vie."; }
    @Override public boolean burst(Player p, CfPlayer cf, AbilityState st) {
        List<LivingEntity> enemies = Effects.enemiesWithin(p, 40);
        if (enemies.isEmpty()) { Msg.actionBar(p, "<red>Aucun adversaire a moins de 40 blocs."); return false; }
        World arena = arena();
        Location center = ArenaWorlds.center(arena);
        RETURN.clear();
        RETURN.put(p.getUniqueId(), p.getLocation().clone());
        arenaOwner = p.getUniqueId();
        java.util.Random r = new java.util.Random();
        for (LivingEntity e : enemies) {
            RETURN.put(e.getUniqueId(), e.getLocation().clone());
            e.teleport(center.clone().add(r.nextInt(-15, 16), 0, r.nextInt(-15, 16)));
            if (e instanceof Player ep) Msg.send(ep, "<dark_red><bold>Monde en Ruine :</bold> <gray>le Drifter vous entraine dans l'arene finale !");
        }
        p.teleport(center);
        Boosts.set(p, "drifter_ruine", new Boosts.Boost(3, 2, 2, 0));
        arena.playSound(center, Sound.ENTITY_WITHER_SPAWN, 1f, 0.7f);
        Msg.send(p, "<gold><bold>Monde en Ruine :</bold> <gray>tue tous tes adversaires pour sortir. Mourir ici ne coute aucune vie.");
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline() || !p.getUniqueId().equals(arenaOwner)) { cancel(); return; }
                boolean any = false;
                for (LivingEntity e : arena.getLivingEntities()) if (e != p && !e.isDead() && Effects.isEnemy(p, e)) { any = true; break; }
                if (!any) { Msg.send(p, "<gold>Monde en Ruine : tous tes adversaires sont tombes."); leaveArena(p, true); cancel(); }
            }
        }.runTaskTimer(Solo.plugin(), 40L, 20L);
        return true;
    }

    static void leaveArena(Player drifter, boolean survived) {
        arenaOwner = null;
        Boosts.remove(drifter, "drifter_ruine");
        World arena = Bukkit.getWorld(ARENA_WORLD);
        if (arena != null) for (LivingEntity e : new java.util.ArrayList<>(arena.getLivingEntities())) {
            Location back = RETURN.get(e.getUniqueId());
            if (back != null && e != drifter) e.teleport(back);
        }
        Location back = RETURN.get(drifter.getUniqueId());
        if (survived && back != null && drifter.isOnline()) drifter.teleport(back);
        RETURN.clear();
    }

    /** Le Drifter meurt dans l'arene : tout le monde ressort, aucune vie perdue. */
    boolean onDeathInArena(Player p, CfPlayer cf) {
        if (!inArena(p) || !p.getUniqueId().equals(arenaOwner)) return false;
        Location back = RETURN.get(p.getUniqueId());
        leaveArena(p, false);
        Location target = back != null ? back : Solo.plugin().game().randomGroundLocation(300);
        Bukkit.getScheduler().runTaskLater(Solo.plugin(), () -> {
            if (!p.isOnline()) return;
            if (Solo.plugin().game().isTest()) { p.teleport(target); p.setGameMode(org.bukkit.GameMode.SURVIVAL); }
            else Solo.plugin().lives().respawnAt(p, target);
            Msg.send(p, "<green>Tu quittes l'arene finale : aucune vie perdue.");
        }, 3L);
        return true;
    }
}
