package com.kiokouwhite.crossfight.character.earthdelta;

import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.ability.AbilityEngine;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.ActiveAbility;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.ability.PassiveAbility;
import com.kiokouwhite.crossfight.ability.SwitchAbility;
import com.kiokouwhite.crossfight.ability.UltimateAbility;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Voie;
import com.kiokouwhite.crossfight.listener.DamageListener;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.ChoiceMenu;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Items;
import com.kiokouwhite.crossfight.util.Msg;
import com.kiokouwhite.crossfight.util.Weapons;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Kishi - Voie de la Nihilite. Des cones de lumiere, gagnes quand ses allies encaissent.
 */
public final class Kishi extends Character implements Listener {

    static final Particle.DustOptions LIGHT = new Particle.DustOptions(Color.fromRGB(255, 255, 200), 1.0f);

    public Kishi() {
        super("kishi", "Kishi", Voie.NIHILITE, Material.END_ROD);
        addAbility(new ExplosionAveuglante());
        addAbility(new AuraLumineuse());
        addAbility(new LumiereEtincelante());
        setUltimate(new ParadisBlanc());
    }

    @Override
    public ItemStack createWeapon() {
        return Weapons.melee(Material.IRON_SWORD, 8.0, "<yellow><bold>Illumilame", List.of("<gray>8 dmg, +0.5 par cone de lumiere (max 10.5)."));
    }

    @Override
    public List<String> summary() {
        return List.of(
                "<white>Illumilame : +0.5 dmg par cone de lumiere",
                "<white>Explosion Aveuglante : tous les 7 coups encaisses par l'equipe, un cone",
                "<white>Aura Lumineuse : Force a un allie selon les cones",
                "<white>Lumiere Etincelante : aveugle l'ennemi le plus proche (1 cone)",
                "<light_purple>Ultime : 10 cones permanents");
    }

    // ----------------------------------------------------------------- cones

    static AbilityState conesState(Player p) {
        CfPlayer cf = EarthDelta.plugin().players().get(p);
        if (cf.character() == null) return null;
        Ability a = cf.character().ability("kishi_explosion");
        return a == null ? null : EarthDelta.plugin().abilities().state(p, a);
    }

    @SuppressWarnings("unchecked")
    static List<Long> cones(Player p) {
        AbilityState st = conesState(p);
        if (st == null) return new ArrayList<>();
        List<Long> l = st.get("cones", null);
        if (l == null) { l = new ArrayList<>(); st.set("cones", l); }
        l.removeIf(exp -> System.currentTimeMillis() >= exp);
        return l;
    }

    static int coneCount(Player p) { return cones(p).size(); }

    static void addCone(Player p, boolean permanent) {
        cones(p).add(permanent ? Long.MAX_VALUE : System.currentTimeMillis() + 30000L);
        if (!permanent) com.kiokouwhite.crossfight.util.Sounds.ability(p, "kishi_explosion");
        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 2.2, 0), 8, 0.3, 0.3, 0.3, 0.03);
        p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.8f);
        refreshBlade(p);
    }

    static boolean spendCone(Player p) {
        List<Long> l = cones(p);
        if (l.isEmpty()) return false;
        // On depense d'abord les cones temporaires.
        int idx = 0;
        for (int i = 0; i < l.size(); i++) if (l.get(i) != Long.MAX_VALUE) { idx = i; break; }
        l.remove(idx);
        refreshBlade(p);
        return true;
    }

    /** L'Illumilame : 8 dmg + 0.5 par cone, 10.5 max. */
    static void refreshBlade(Player p) {
        double dmg = Math.min(10.5, 8.0 + 0.5 * coneCount(p));
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && "kishi".equals(Items.tag(it, AbilityEngine.WEAPON_KEY)) && Weapons.damageOf(it) != dmg) Weapons.setDamage(it, dmg);
        }
    }

    static List<Player> kishisOf(Player teammate) {
        List<Player> out = new ArrayList<>();
        List<Player> team = new ArrayList<>(EarthDelta.allies(teammate));
        team.add(teammate);
        for (Player m : team) {
            CfPlayer cf = EarthDelta.plugin().players().get(m);
            if (cf.character() != null && "kishi".equals(cf.character().id())) out.add(m);
        }
        return out;
    }

    // ---------------------------------------------------------------- pouvoirs

    static final class ExplosionAveuglante extends PassiveAbility {
        ExplosionAveuglante() {
            super("kishi_explosion", "Explosion Aveuglante", "Tous les 7 coups qu'un membre de l'equipe encaisse, l'ennemi est aveugle 1 seconde et Kishi obtient un cone de lumiere (30 secondes).", Material.END_ROD);
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            int n = coneCount(p);
            return "<yellow>Cones <white>" + n + " <dark_gray>(" + st.get("hits", 0) + "/7)";
        }
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) {
            refreshBlade(p);
            int n = Math.min(10, coneCount(p));
            Location c = p.getLocation().add(0, 2.3, 0);
            for (int i = 0; i < n; i++) {
                double a = i * 2 * Math.PI / Math.max(1, n) + System.currentTimeMillis() / 400.0;
                p.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 0.6, 0, Math.sin(a) * 0.6), 1, 0, 0, 0, 0, LIGHT);
            }
            if (n > 0) p.getWorld().spawnParticle(Particle.END_ROD, c, 1, 0.2, 0.1, 0.2, 0.0);
        }
    }

    /** Un allie encaisse un coup : compteur de chaque Kishi de son equipe. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAllyHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim) || !EarthDelta.inGame(victim)) return;
        LivingEntity attacker = DamageListener.livingAttackerOf(e.getDamager());
        if (attacker == null || !Effects.isEnemy(victim, attacker)) return;
        for (Player kishi : kishisOf(victim)) {
            AbilityState st = conesState(kishi);
            if (st == null) continue;
            int hits = st.get("hits", 0) + 1;
            if (hits < 7) { st.set("hits", hits); continue; }
            st.set("hits", 0);
            Effects.give(attacker, PotionEffectType.BLINDNESS, 0, 20);
            attacker.getWorld().spawnParticle(Particle.FLASH, attacker.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0, Color.fromRGB(255, 255, 220));
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1.6f);
            addCone(kishi, false);
            Msg.actionBar(kishi, "<yellow>Explosion Aveuglante : +1 cone (" + coneCount(kishi) + ")");
        }
    }

    /** Intervertible : Kishi choisit un allie qui recoit Force selon le nombre de cones (IV max). */
    static final class AuraLumineuse extends SwitchAbility implements CustomUse {
        AuraLumineuse() {
            super("kishi_aura", "Aura Lumineuse", "Kishi choisit un allie : il recoit Force egale au nombre de cones de lumiere (Force IV max), tant qu'il reste choisi.", Material.GLOWSTONE_DUST, List.of("Allie"));
        }
        @Override public void onSwitch(Player p, CfPlayer cf, AbilityState st, int oldMode, int newMode) {}
        @Override protected List<String> extraLore() { return List.of("", "<yellow>Clic droit : choisir l'allie"); }
        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            List<Player> team = new ArrayList<>(EarthDelta.allies(p));
            team.add(0, p);
            List<ChoiceMenu.Option> options = new ArrayList<>();
            for (Player a : team) {
                boolean current = a.getUniqueId().toString().equals(st.get("ally", ""));
                options.add(new ChoiceMenu.Option(Material.PLAYER_HEAD, a == p ? "Moi-meme" : a.getName(), current ? List.of("<yellow>Actuellement choisi") : List.of(), () -> {
                    clear(st);
                    st.set("ally", a.getUniqueId().toString());
                    Msg.actionBar(p, "<yellow>Aura Lumineuse : " + (a == p ? "toi-meme" : a.getName()));
                    a.getWorld().spawnParticle(Particle.END_ROD, a.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.05);
                }));
            }
            ChoiceMenu.open(p, "<yellow><bold>Aura Lumineuse : qui ?", options);
            return true;
        }
        static void clear(AbilityState st) {
            String prev = st.get("ally", null);
            if (prev == null) return;
            Player old = Bukkit.getPlayer(UUID.fromString(prev));
            if (old != null) Boosts.remove(old, "kishi_aura");
            st.remove("ally");
        }
        @Override public void tick(Player p, CfPlayer cf, AbilityState st) {
            String id = st.get("ally", null);
            Player ally = id == null ? null : Bukkit.getPlayer(UUID.fromString(id));
            if (ally == null || ally.isDead()) return;
            int force = Math.min(4, coneCount(p));
            Boosts.Boost b = Boosts.get(ally, "kishi_aura");
            if (force <= 0) { if (b != null) Boosts.remove(ally, "kishi_aura"); }
            else if (b == null || b.force != force) Boosts.set(ally, "kishi_aura", new Boosts.Boost(force, 0, 0, 0));
        }
        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            String id = st.get("ally", null);
            Player ally = id == null ? null : Bukkit.getPlayer(UUID.fromString(id));
            return ally == null ? "<gray>Aura <dark_gray>[personne]" : "<yellow>Aura <white>[" + (ally == p ? "moi" : ally.getName()) + " : Force " + Math.min(4, coneCount(p)) + "]";
        }
        @Override public void onUnequip(Player p, CfPlayer cf) { clear(EarthDelta.plugin().abilities().state(p, this)); }
    }

    static final class LumiereEtincelante extends ActiveAbility {
        LumiereEtincelante() {
            super("kishi_lumiere", "Lumiere Etincelante", "L'ennemi le plus proche (15 blocs) est aveugle pendant 3 secondes. Cout : 1 cone de lumiere.", Material.GLOW_INK_SAC, 0);
        }
        @Override protected List<String> extraLore() { return List.of("", "<yellow>Cout : 1 cone de lumiere"); }
        @Override public boolean activate(Player p, CfPlayer cf, AbilityState st) {
            LivingEntity target = Effects.nearestEnemy(p, 15);
            if (target == null) { Msg.actionBar(p, "<red>Aucun ennemi a moins de 15 blocs."); return false; }
            if (coneCount(p) <= 0) { Msg.actionBar(p, "<red>Aucun cone de lumiere."); return false; }
            spendCone(p);
            Effects.give(target, PotionEffectType.BLINDNESS, 0, 60);
            World w = target.getWorld();
            w.spawnParticle(Particle.FLASH, target.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0, Color.fromRGB(255, 255, 220));
            w.spawnParticle(Particle.END_ROD, target.getLocation().add(0, 1, 0), 30, 0.5, 0.7, 0.5, 0.1);
            w.playSound(target.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1f, 1.2f);
            Effects.say(target, "<yellow>Aveugle par " + p.getName() + " !");
            return true;
        }
    }

    static final class ParadisBlanc extends UltimateAbility {
        ParadisBlanc() {
            super("kishi_ult", "Paradis blanc", "Kishi obtient 10 cones de lumiere qui ne disparaissent pas tant qu'ils ne sont pas utilises.", Material.NETHER_STAR);
        }
        @Override public boolean cast(Player p, CfPlayer cf, AbilityState st) {
            for (int i = 0; i < 10; i++) cones(p).add(Long.MAX_VALUE);
            refreshBlade(p);
            World w = p.getWorld();
            w.spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 120, 1.5, 1.5, 1.5, 0.1);
            w.spawnParticle(Particle.FLASH, p.getLocation().add(0, 1, 0), 1, 0, 0, 0, 0, Color.fromRGB(255, 255, 240));
            w.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.4f);
            Msg.actionBar(p, "<yellow>Paradis blanc : +10 cones permanents (" + coneCount(p) + ")");
            return true;
        }
    }
}
