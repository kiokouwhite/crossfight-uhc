package com.kiokouwhite.crossfight.character.gunvolt;

import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.character.duo.Synergy;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Bullets;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Weapons;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kirin - Voie de la Destruction. Des talismans qui ciblent, un sabre qui teleporte.
 */
public final class Kirin extends Character implements Listener {

    static final NamespacedKey TALISMAN = new NamespacedKey("crossfight", "kirin_talisman");
    static final Particle.DustOptions RED = new Particle.DustOptions(Color.fromRGB(255, 70, 90), 1.0f);
    /** Cibles marquees par un talisman : par Kirin, cible -> expiration. */
    static final Map<UUID, Map<UUID, Long>> MARKS = new HashMap<>();

    public Kirin() {
        super("kirin", "Kirin", Voie.DESTRUCTION, Material.PAPER);
        addAbility(new ShadowYakumo().bindToWeapon());
        addAbility(new JetDeTalisman());
        addAbility(new ArcChain());
        addAbility(new Slash());
        addAbility(Synergy.ITEM);
        setUltimate(new DivineRuinArc());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.IRON_SWORD, 8.0, "<red><bold>Shadow Yakumo Sword", List.of("<gray>8 dmg."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Shadow Yakumo : teleportation sur la cible la plus proche",
                "<white>Jet de Talisman : 5 talismans qui ciblent 10 s",
                "<white>Arc Chain : blesse toutes les cibles (1 a 4 coeurs)",
                "<white>Slash : Tensho (en l'air), Ressen (accroupi), Issen (immobile)",
                "<light_purple>Ultime : 92nd Hit Rite, 5 coeurs sur 5 blocs et sur toutes les cibles");
    }

    // ---------------------------------------------------------------- marques

    static Map<UUID, Long> marks(Player kirin) {
        Map<UUID, Long> m = MARKS.computeIfAbsent(kirin.getUniqueId(), u -> new HashMap<>());
        m.entrySet().removeIf(en -> System.currentTimeMillis() >= en.getValue());
        return m;
    }
    static List<LivingEntity> markedEntities(Player kirin) {
        List<LivingEntity> out = new ArrayList<>();
        for (UUID id : new ArrayList<>(marks(kirin).keySet())) {
            var e = org.bukkit.Bukkit.getEntity(id);
            if (e instanceof LivingEntity le && !le.isDead()) out.add(le); else marks(kirin).remove(id);
        }
        return out;
    }
    static void mark(Player kirin, LivingEntity t, long millis) {
        marks(kirin).put(t.getUniqueId(), System.currentTimeMillis() + millis);
        Effects.give(t, PotionEffectType_GLOWING(), 0, (int) (millis / 50));
        t.getWorld().spawnParticle(Particle.DUST, t.getLocation().add(0, 1.2, 0), 15, 0.3, 0.5, 0.3, 0, RED);
        t.getWorld().playSound(t.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.6f);
        Effects.say(t, "<red>Cible par un talisman de " + kirin.getName() + " !");
    }
    private static org.bukkit.potion.PotionEffectType PotionEffectType_GLOWING() { return org.bukkit.potion.PotionEffectType.GLOWING; }

    static LivingEntity nearestMarked(Player kirin, double radius) {
        LivingEntity best = null; double bd = radius * radius;
        for (LivingEntity e : markedEntities(kirin)) {
            if (e.getWorld() != kirin.getWorld()) continue;
            double d = e.getLocation().distanceSquared(kirin.getLocation());
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    static void teleportOnto(Player p, LivingEntity t) {
        Vector back = t.getLocation().getDirection().setY(0);
        if (back.lengthSquared() < 0.01) back = new Vector(1, 0, 0);
        Location to = t.getLocation().subtract(back.normalize().multiply(1.2));
        if (!to.getBlock().isPassable()) to = t.getLocation();
        to.setDirection(t.getLocation().add(0, 1, 0).toVector().subtract(to.toVector().add(new Vector(0, 1.6, 0))));
        World w = p.getWorld();
        w.spawnParticle(Particle.DUST, p.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0, RED);
        p.teleport(to);
        w.spawnParticle(Particle.DUST, to.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0, RED);
        w.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.8f);
    }

    // ------------------------------------------------------------------ arme

    static final class ShadowYakumo extends ActiveAbility implements CustomUse {
        ShadowYakumo() { super("kirin_yakumo", "Shadow Yakumo Sword", "Clic droit : Kirin se teleporte sur la cible la plus proche (30 blocs) et lui retire son talisman.", Material.IRON_SWORD, 0); }
        @Override public List<String> weaponLore() { return List.of("<red>Clic droit : <gray>teleportation sur la cible la plus proche"); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { return "<red>Cibles <white>" + markedEntities(p).size(); }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            LivingEntity t = nearestMarked(p, 30);
            if (t == null) { Msg.actionBar(p, "<red>Aucune cible marquee a moins de 30 blocs."); return true; }
            teleportOnto(p, t);
            marks(p).remove(t.getUniqueId());
            st.set("arcUntil", System.currentTimeMillis() + 4000L);
            st.set("arcTarget", t.getUniqueId().toString());
            Msg.actionBar(p, "<red>Teleportation ! <gray>Arc Chain disponible 4 s.");
            return true;
        }
    }

    // ---------------------------------------------------------------- pouvoirs

    static final class JetDeTalisman extends ActiveAbility implements CustomUse {
        static final int MAX = 5; static final long RELOAD_MS = 10000L;
        JetDeTalisman() { super("kirin_talisman", "Jet de Talisman", "Kirin jette un talisman qui cible l'adversaire touche pendant 10 secondes. 5 talismans, un revient toutes les 10 secondes.", Material.PAPER, 0); }
        int count(AbilityState st) {
            int b = st.get("n", MAX); long last = st.get("reloadAt", 0L); long now = System.currentTimeMillis();
            if (b < MAX) {
                if (last == 0L) st.set("reloadAt", now);
                else while (b < MAX && now - last >= RELOAD_MS) { b++; last += RELOAD_MS; }
                st.set("n", b); st.set("reloadAt", b >= MAX ? 0L : last);
            }
            return b;
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) { int b = count(st); return "<red>Talismans " + "●".repeat(b) + "<dark_gray>" + "●".repeat(MAX - b); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            int b = count(st);
            if (b <= 0) { Msg.actionBar(p, "<red>Plus de talisman."); return true; }
            if (b == MAX) st.set("reloadAt", System.currentTimeMillis());
            st.set("n", b - 1);
            throwTalisman(p, 10000L, false);
            return true;
        }
    }

    /** Jette un talisman : marque la cible touchee ; en Synergie (Talisman Electrifiant), teleporte et immobilise. */
    static void throwTalisman(Player p, long markMillis, boolean electrified) {
        Arrow a = Bullets.shoot(p, p.getEyeLocation().getDirection(), 2.2, 0.5, Material.WHITE_CONCRETE, Color.fromRGB(255, 90, 110));
        a.getPersistentDataContainer().set(TALISMAN, PersistentDataType.STRING, (electrified ? "e" : "m") + markMillis);
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
    }

    @EventHandler
    public void onTalismanHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow a) || !(a.getShooter() instanceof Player p)) return;
        String tag = a.getPersistentDataContainer().get(TALISMAN, PersistentDataType.STRING);
        if (tag == null || !(e.getHitEntity() instanceof LivingEntity t) || !Effects.isEnemy(p, t)) return;
        long millis = Long.parseLong(tag.substring(1));
        if (tag.charAt(0) == 'e') {
            teleportOnto(p, t);
            Effects.root(t, 100);
            t.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, t.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.15);
            Effects.say(t, "<aqua>Talisman Electrifiant : immobilise 5 s !");
        } else mark(p, t, millis);
    }

    static final class ArcChain extends ActiveAbility {
        ArcChain() { super("kirin_arc", "Arc Chain", "Utilisable dans les 4 secondes apres une teleportation : toutes les cibles marquees perdent de la vie, 1 coeur pour une cible, jusqu'a 4 coeurs avec 4 cibles ou plus.", Material.IRON_CHAIN, 10); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            var yak = cf.character() == null ? null : cf.character().ability("kirin_yakumo");
            AbilityState ys = yak == null ? null : ABG.plugin().abilities().state(p, yak);
            if (ys == null || System.currentTimeMillis() > ys.get("arcUntil", 0L)) { Msg.actionBar(p, "<red>Arc Chain : seulement juste apres une teleportation."); return false; }
            List<LivingEntity> targets = markedEntities(p);
            // L'adversaire sur lequel Kirin vient de se teleporter compte aussi (son talisman vient d'etre retire).
            String last = ys.get("arcTarget", null);
            var le = last == null ? null : org.bukkit.Bukkit.getEntity(UUID.fromString(last));
            if (le instanceof LivingEntity lt && !lt.isDead() && targets.stream().noneMatch(x -> x.getUniqueId().equals(lt.getUniqueId()))) targets.add(lt);
            if (targets.isEmpty()) { Msg.actionBar(p, "<red>Aucune cible marquee."); return false; }
            double dmg = Math.min(8.0, 2.0 * targets.size());
            World w = p.getWorld();
            Location from = p.getLocation().add(0, 1, 0);
            for (LivingEntity t : targets) {
                ABG.arc(w, from, t.getLocation().add(0, 1, 0));
                t.setNoDamageTicks(0);
                Effects.damage(t, dmg, p);
                w.spawnParticle(Particle.ELECTRIC_SPARK, t.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
            }
            w.playSound(from, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 1.5f);
            Msg.actionBar(p, "<red>Arc Chain : " + targets.size() + " cible(s), " + (dmg / 2) + " coeur(s) chacune.");
            return true;
        }
    }

    /** Slash : Tensho en l'air, Ressen accroupi (saut puis chute), Issen immobile (dash). */
    static final class Slash extends ActiveAbility implements CustomUse {
        Slash() { super("kirin_slash", "Slash", "En l'air : Tensho Slash, coup a 3 blocs de 2 coeurs qui ejecte l'adversaire de 10 blocs. Accroupi : Ressen Slash, saut puis, au clic suivant, chute de 4 coeurs. Immobile : Issen Slash, dash de 5 blocs de 3 coeurs.", Material.NETHERITE_SWORD, 15); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) { return false; }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            World w = p.getWorld();
            // Deuxieme clic du Ressen : la chute.
            if (st.get("ressen", false)) {
                st.set("ressen", false);
                p.setVelocity(new Vector(0, -2.5, 0));
                Effects.noFallUntil(p, 4000);
                new BukkitRunnable() {
                    int t = 0;
                    @Override public void run() {
                        t++;
                        if (!p.isOnline() || t > 40) { cancel(); return; }
                        w.spawnParticle(Particle.SWEEP_ATTACK, p.getLocation().add(0, 0.5, 0), 1);
                        if (p.isOnGround() || t == 40) {
                            Location c = p.getLocation();
                            w.spawnParticle(Particle.EXPLOSION, c, 1);
                            w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.4f);
                            for (LivingEntity e : Effects.enemiesWithin(p, c, 2.5)) { e.setNoDamageTicks(0); Effects.damage(e, 8.0, p); }
                            cancel();
                        }
                    }
                }.runTaskTimer(ABG.plugin(), 1L, 1L);
                return true;
            }
            if (st.onCooldown()) { Msg.actionBar(p, "<red>Slash : encore " + st.cooldownLeftSeconds() + "s"); return true; }
            boolean ok;
            String which;
            if (!p.isOnGround()) {
                which = "Tensho Slash";
                Location eye = p.getEyeLocation();
                Vector dir = eye.getDirection().setY(0).normalize();
                Location c = eye.clone().add(dir.clone().multiply(1.5));
                for (int y = -2; y <= 2; y++) w.spawnParticle(Particle.SWEEP_ATTACK, c.clone().add(0, y, 0), 1);
                int n = 0;
                for (LivingEntity e : Effects.within(c, 3.0, le -> Effects.isEnemy(p, le))) { e.setNoDamageTicks(0); Effects.damage(e, 4.0, p); Effects.launchUp(e, 10); n++; }
                w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
                ok = true;
                if (n == 0) Msg.actionBar(p, "<gray>Tensho Slash : dans le vide.");
            } else if (sneaking) {
                which = "Ressen Slash";
                Effects.launchUp(p, 6);
                Effects.noFallUntil(p, 6000);
                st.set("ressen", true);
                w.playSound(p.getLocation(), Sound.ENTITY_BREEZE_JUMP, 1f, 1f);
                Msg.actionBar(p, "<red>Ressen Slash : clique a nouveau pour retomber !");
                ok = true;
            } else {
                which = "Issen Slash";
                Vector dir = p.getLocation().getDirection().setY(0);
                if (dir.lengthSquared() < 0.01) dir = new Vector(1, 0, 0);
                dir.normalize();
                Location from = p.getLocation();
                java.util.Set<UUID> hit = new java.util.HashSet<>();
                final Vector d = dir;
                new BukkitRunnable() {
                    int i = 0; Location cur = from.clone();
                    @Override public void run() {
                        i++;
                        if (!p.isOnline() || p.isDead() || i > 5) { cancel(); return; }
                        Location next = cur.clone().add(d);
                        if (!next.getBlock().isPassable() || !next.clone().add(0, 1, 0).getBlock().isPassable()) { cancel(); return; }
                        w.spawnParticle(Particle.SWEEP_ATTACK, next.clone().add(0, 1, 0), 1);
                        for (LivingEntity e : Effects.enemiesWithin(p, next.clone().add(0, 1, 0), 1.5)) if (hit.add(e.getUniqueId())) { e.setNoDamageTicks(0); Effects.damage(e, 6.0, p); }
                        next.setDirection(p.getLocation().getDirection());
                        p.teleport(next); cur = next;
                    }
                }.runTaskTimer(ABG.plugin(), 0L, 1L);
                w.playSound(from, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.3f);
                ok = true;
            }
            if (ok) {
                st.startCooldown((int) Math.ceil(cooldownSeconds() * cf.cooldownFactor()));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
                Msg.actionBar(p, "<red>" + which + " !");
            }
            return true;
        }
    }

    static final class DivineRuinArc extends UltimateAbility {
        DivineRuinArc() { super("kirin_ult", "92nd Hit Rite - Divine Ruin Arc", "Sur 5 blocs autour de Kirin, 5 coeurs de degats. Les cibles marquees prennent l'attaque ou qu'elles soient.", Material.NETHER_STAR); }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            World w = p.getWorld();
            Location c = p.getLocation();
            java.util.Set<UUID> done = new java.util.HashSet<>();
            List<LivingEntity> all = new ArrayList<>(Effects.enemiesWithin(p, 2.5));
            for (LivingEntity m : markedEntities(p)) if (m.getWorld() == w) all.add(m);
            new BukkitRunnable() {
                int t = 0;
                @Override public void run() {
                    t++;
                    if (t > 12) { cancel(); return; }
                    for (int i = 0; i < 8; i++) { double a = Math.random() * Math.PI * 2, r = Math.random() * 2.5; w.spawnParticle(Particle.SWEEP_ATTACK, c.clone().add(Math.cos(a) * r, 0.5 + Math.random() * 1.5, Math.sin(a) * r), 1); }
                    w.spawnParticle(Particle.DUST, c.clone().add(0, 1, 0), 10, 1.5, 1, 1.5, 0, RED);
                    if (t % 3 == 0) w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f + t * 0.05f);
                    if (t == 12) {
                        for (LivingEntity e : all) {
                            if (!done.add(e.getUniqueId()) || e.isDead()) continue;
                            e.setNoDamageTicks(0);
                            Effects.damage(e, 10.0, p);
                            ABG.arc(w, c.clone().add(0, 1, 0), e.getLocation().add(0, 1, 0));
                            w.spawnParticle(Particle.SWEEP_ATTACK, e.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0);
                        }
                        w.playSound(c, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.5f);
                        Msg.actionBar(p, "<red>92nd Hit Rite : " + done.size() + " adversaire(s) frappe(s) !");
                    }
                }
            }.runTaskTimer(ABG.plugin(), 0L, 1L);
            return true;
        }
    }
}
