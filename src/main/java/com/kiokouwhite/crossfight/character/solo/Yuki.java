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
            Boosts.set(p, "yuki_snow", new Boosts.Boost(1, 1, 1, 0));
            Bukkit.getScheduler().runTaskLater(Solo.plugin(), () -> Boosts.remove(p, "yuki_snow"), 200L);
            aspirer(p);
            Msg.actionBar(p, "<white>Bleeding Snow : -2 coeurs, bonus 10 s");
            return true;
        }

        static final Color SANG = Color.fromRGB(140, 0, 16), SANG_VIF = Color.fromRGB(215, 18, 38), NEIGE = Color.fromRGB(235, 245, 255);
        static final int POULS = 4, ECART = 5, FIN = 20, DUREE = 26;   // en ticks : un coeur arrache tous les 5 ticks

        /**
         * L'epee boit la vie de Yuki : quatre pulsations (un demi-coeur chacune, le corps secoue, un battement de coeur),
         * a chacune du sang qui gicle de sa poitrine ; des filets de sang arraches de tout son corps tournoient jusqu'a la
         * lame, de plus en plus vite, et y blanchissent en neige. A la fin, la lame eclate en neige rouge et blanche.
         * Pendant ce temps, un voile rouge bat sur l'ecran de Yuki ; elle ne voit pas le sang qui gicle de sa poitrine
         * (de si pres, il lui cacherait l'ecran), seulement celui qui arrive dans sa lame.
         */
        static void aspirer(Player p) {
            java.util.Random rnd = java.util.concurrent.ThreadLocalRandom.current();
            // Une goutte : son depart (droite, haut, avant, autour du corps), sa place sur la lame, sa progression, sa
            // vitesse, l'angle de sa spirale.
            List<double[]> gouttes = new java.util.ArrayList<>();
            new BukkitRunnable() {
                int t = 0;
                double rouge = 0;
                @Override public void run() {
                    if (!p.isValid()) { vignette(p, 0); cancel(); return; }
                    World w = p.getWorld();
                    Location base = p.getLocation();
                    Vector avant = base.getDirection().setY(0);
                    if (avant.lengthSquared() < 1e-4) avant = new Vector(0, 0, 1);
                    avant.normalize();
                    Vector droite = new Vector(-avant.getZ(), 0, avant.getX());
                    Location main = local(base, droite, avant, 0.37, 0.8, 0.25);
                    Location poitrine = local(base, droite, avant, 0, 1.2, 0.12);

                    // Les pulsations : un demi-coeur arrache, le corps secoue, le sang gicle.
                    if (t % ECART == 0 && t / ECART < POULS) {
                        int i = t / ECART;
                        p.setHealth(Math.max(1.0, p.getHealth() - 1.0));
                        if (i == 0) p.damage(0.01); else p.playHurtAnimation(0);
                        autres(p, Particle.DUST, poitrine, 28, 0.25, 0.35, 0.25, 0, new Particle.DustOptions(SANG_VIF, 1.7f));
                        autres(p, Particle.BLOCK, poitrine, 16, 0.2, 0.3, 0.2, 0.25, Material.REDSTONE_BLOCK.createBlockData());
                        autres(p, Particle.DAMAGE_INDICATOR, poitrine, 3, 0.2, 0.2, 0.2, 0.15, null);
                        // Yuki voit le sang gicler de son dos : derriere ses yeux en premiere personne, devant la camera
                        // en troisieme.
                        Location dos = local(base, droite, avant, 0, 1.2, -0.4);
                        p.spawnParticle(Particle.DUST, dos, 18, 0.28, 0.32, 0.06, 0, new Particle.DustOptions(SANG_VIF, 1.5f));
                        p.spawnParticle(Particle.BLOCK, dos.clone().add(avant.clone().multiply(-0.1)), 10, 0.25, 0.3, 0.05, 0.05, Material.REDSTONE_BLOCK.createBlockData());
                        w.playSound(base, Sound.ENTITY_WARDEN_HEARTBEAT, 1.6f, 0.6f + 0.12f * i);
                        w.playSound(base, Sound.ENTITY_PLAYER_HURT, 0.7f, 0.6f);
                        rouge = 0.95;
                    }
                    if (t % ECART == 2 && t / ECART < POULS) w.playSound(base, Sound.ENTITY_GENERIC_DRINK, 1f, 0.5f);

                    // Les filets de sang : arraches du corps, de plus en plus vite, en spirale jusqu'a la lame.
                    if (t < FIN - 2) {
                        for (int k = 0; k < 5; k++) {
                            double a = rnd.nextDouble() * Math.PI * 2, r = 0.35 + rnd.nextDouble() * 0.3;
                            gouttes.add(new double[]{Math.cos(a) * r, 0.3 + rnd.nextDouble() * 1.3, Math.sin(a) * r,
                                    0.15 + rnd.nextDouble() * 1.05, 0, 0.06 + rnd.nextDouble() * 0.04, rnd.nextDouble() * Math.PI * 2});
                        }
                    }
                    for (var it = gouttes.iterator(); it.hasNext(); ) {
                        double[] g = it.next();
                        g[5] *= 1.3;
                        g[4] = Math.min(1, g[4] + g[5]);
                        double e = g[4] * g[4];
                        Location depart = local(base, droite, avant, g[0], g[1], g[2]);
                        Location cible = lame(main, avant, g[3]);
                        double s = Math.sin(e * Math.PI) * 0.35, ang = g[6] + e * 6;
                        Location at = depart.clone().add(cible.toVector().subtract(depart.toVector()).multiply(e))
                                .add(droite.clone().multiply(Math.cos(ang) * s)).add(0, Math.sin(ang) * s, 0);
                        // Yuki ne voit que la fin de chaque filet, devant elle, pres de la lame.
                        Particle.DustOptions goutte = new Particle.DustOptions(e > 0.5 ? SANG_VIF : SANG, 1.2f);
                        if (e > 0.45) w.spawnParticle(Particle.DUST, at, 1, 0, 0, 0, 0, goutte);
                        else autres(p, Particle.DUST, at, 1, 0, 0, 0, 0, goutte);
                        if (g[4] >= 1) {
                            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, cible, 3, 0.05, 0.05, 0.05, 0, new Particle.DustTransition(SANG_VIF, NEIGE, 1.0f));
                            it.remove();
                        }
                    }

                    // La lame se gorge de sang, qui y blanchit en neige.
                    if (t >= 2 && t <= FIN) {
                        for (double d = 0.15; d <= 1.3; d += 0.2) {
                            w.spawnParticle(Particle.DUST_COLOR_TRANSITION, lame(main, avant, d), 1, 0.04, 0.04, 0.04, 0,
                                    new Particle.DustTransition(SANG_VIF, NEIGE, 0.6f + t / 30f));
                        }
                    }
                    // La fin : la lame eclate en neige rouge et blanche, une onde de sang au sol.
                    if (t == FIN) {
                        Location milieu = lame(main, avant, 0.7);
                        autres(p, Particle.DUST_COLOR_TRANSITION, milieu, 40, 0.35, 0.15, 0.35, 0, new Particle.DustTransition(SANG_VIF, NEIGE, 1.4f));
                        autres(p, Particle.SNOWFLAKE, milieu, 45, 0.2, 0.2, 0.2, 0.18, null);
                        p.spawnParticle(Particle.SNOWFLAKE, milieu, 15, 0.2, 0.1, 0.2, 0.08);
                        w.playSound(base, Sound.BLOCK_SNOW_BREAK, 1f, 0.6f);
                        w.playSound(base, Sound.ENTITY_EVOKER_CAST_SPELL, 0.9f, 0.6f);
                        w.playSound(base, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 0.7f);
                    }
                    if (t > FIN && t <= FIN + 5) {
                        double r = (t - FIN) * 0.4;
                        for (int k = 0; k < 20; k++) {
                            double a = k * Math.PI / 10;
                            Location l = base.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                            w.spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, new Particle.DustOptions(k % 2 == 0 ? SANG : NEIGE, 1.1f));
                        }
                    }

                    vignette(p, rouge);
                    rouge *= 0.82;
                    if (++t > DUREE) { vignette(p, 0); cancel(); }
                }
            }.runTaskTimer(Solo.plugin(), 0L, 1L);
        }

        /** Des particules vues par tous sauf le joueur : de si pres, elles lui cacheraient l'ecran. */
        static <T> void autres(Player p, Particle type, Location at, int n, double dx, double dy, double dz, double v, T data) {
            for (Player o : at.getWorld().getPlayers()) {
                if (o.getUniqueId().equals(p.getUniqueId()) || o.getLocation().distanceSquared(at) > 64 * 64) continue;
                o.spawnParticle(type, at, n, dx, dy, dz, v, data);
            }
        }

        /** Un point pres du joueur : x vers sa droite, y vers le haut, z devant lui. */
        static Location local(Location base, Vector droite, Vector avant, double x, double y, double z) {
            return base.clone().add(droite.clone().multiply(x)).add(0, y, 0).add(avant.clone().multiply(z));
        }

        /** Un point de la lame, a d blocs de la main (tenue devant, la pointe un peu relevee). */
        static Location lame(Location main, Vector avant, double d) {
            return main.clone().add(avant.clone().multiply(d)).add(0, d * 0.15, 0);
        }

        /**
         * Un voile rouge sur l'ecran du joueur seul, de force 0 a 1 : sa bordure du monde (la vraie, copiee) l'avertit
         * comme s'il en etait tout pres. 0 lui rend la vraie bordure.
         */
        static void vignette(Player p, double force) {
            if (force < 0.03) {
                if (p.getWorldBorder() != null) p.setWorldBorder(null);
                return;
            }
            org.bukkit.WorldBorder vraie = p.getWorld().getWorldBorder();
            org.bukkit.WorldBorder b = Bukkit.createWorldBorder();
            b.setCenter(vraie.getCenter());
            b.setSize(vraie.getSize());
            Location l = p.getLocation();
            double demi = vraie.getSize() / 2;
            double d = Math.max(1, Math.min(demi - Math.abs(l.getX() - vraie.getCenter().getX()), demi - Math.abs(l.getZ() - vraie.getCenter().getZ())));
            b.setWarningDistance((int) Math.min(Integer.MAX_VALUE, d / (1 - Math.min(0.97, force))));
            b.setWarningTime(0);
            p.setWorldBorder(b);
        }
    }

    static final class Souffrante extends PassiveAbility {
        Souffrante() { super("yuki_souffrante", "Souffrante", "Selon sa vie : moins de 7.5 coeurs, Vitesse ; moins de 5, Vitesse et Force ; moins de 3, Vitesse, Force et Resistance.", Material.SNOWBALL); }
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) {
            double hp = p.getHealth();
            active(p, hp < 15);
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

    /** Passif : le coup fatal la laisse a un demi-coeur pendant 15 secondes, sans regeneration (une fois toutes les 5 minutes). */
    static final class Insatiable extends PassiveAbility {
        static final int COOLDOWN = 300;
        Insatiable() { super("yuki_insatiable", "Insatiable", "Quand Yuki devrait mourir, elle reste a un demi-coeur pendant 15 secondes, sans pouvoir regenerer. Une fois toutes les 5 minutes.", Material.TOTEM_OF_UNDYING); }
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) { active(p, System.currentTimeMillis() < st.get("until", 0L)); }
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
            st.startCooldown((int) Math.ceil(COOLDOWN * Solo.plugin().players().get(victim).cooldownFactor()));
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
                showFor(p, 100);
                st.startCooldown((int) Math.ceil(cooldownSeconds() * cf.cooldownFactor()));
                vanish(p, st, true);
            }
            dash(p);
            int left = st.get("dashes", 0) - 1;
            st.set("dashes", left);
            if (left <= 0) { vanish(p, st, false); hideFor(p); }
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
            showFor(p, 400);
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
