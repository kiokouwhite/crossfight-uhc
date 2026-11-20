package com.kiokouwhite.crossfight.hud;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Licence;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.Boosts;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Sounds;
import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * En haut a droite de l'ecran, ce que les autres font sur toi : le passif ou la capacite d'un allie qui te donne
 * un bonus (Charisme de Marth : Vitesse), ou celle d'un adversaire qui te donne un malus (lenteur, poison,
 * immobilisation, Force en moins...). Comme les passifs a gauche : l'icone de la capacite, bordee de vert (allie)
 * ou de rouge (adversaire), et avec /cf passif show le texte qui dit ce qu'elle te fait.
 *
 * L'origine d'un effet est retrouvee sans rien changer aux capacites : au moment ou le plugin pose l'effet
 * (potion, bonus de Boosts, immobilisation), la pile d'appels contient la classe de la capacite qui le pose.
 * Le lanceur est le joueur qui a le personnage de cette capacite. Deux lignes au plus (les cases du plastron et
 * des jambieres : comme les autres images du HUD, des camera_overlay que le joueur seul voit), les plus recentes.
 */
public final class InfluenceHud {

    private InfluenceHud() {}

    /** Ce qu'une capacite d'un autre joueur fait en ce moment sur un joueur. */
    private static final class Influence {
        final String ability;
        final boolean ally;
        /** Ce qui la fait durer : "effet:<type>" -> fin, "boost:<source>" -> tant que le bonus existe, "entrave" -> fin. */
        final Map<String, Long> keys = new HashMap<>();
        long touched;
        Influence(String ability, boolean ally) { this.ability = ability; this.ally = ally; }
    }

    private static final Map<UUID, LinkedHashMap<String, Influence>> BY = new HashMap<>();
    /** Les effets que le joueur s'est donnes avec ses propres actifs (montres a gauche, sous ses passifs). */
    private static final Map<UUID, LinkedHashMap<String, Influence>> SELF = new HashMap<>();
    /** Un effet plus court n'est pas montre (l'elan d'un dash...). */
    private static final long MIN_SELF_MS = 2000L;
    private static Map<Class<?>, Ability> classes;
    private static Map<Ability, Character> owners;

    private static final Set<PotionEffectType> BAD = Set.of(PotionEffectType.SLOWNESS, PotionEffectType.WEAKNESS, PotionEffectType.POISON,
            PotionEffectType.WITHER, PotionEffectType.BLINDNESS, PotionEffectType.DARKNESS, PotionEffectType.NAUSEA, PotionEffectType.HUNGER,
            PotionEffectType.MINING_FATIGUE, PotionEffectType.LEVITATION, PotionEffectType.GLOWING, PotionEffectType.UNLUCK,
            PotionEffectType.INSTANT_DAMAGE, PotionEffectType.BAD_OMEN, PotionEffectType.INFESTED, PotionEffectType.OOZING,
            PotionEffectType.WEAVING, PotionEffectType.WIND_CHARGED);

    // ------------------------------------------------------------------ ce que posent les capacites

    /** Un effet de potion pose par le plugin sur un joueur (sans icone : technique, comme l'invisibilite d'un pantin). */
    public static void potion(Player target, PotionEffect fx) {
        if (fx == null || !fx.hasIcon()) return;
        long ms = fx.getDuration() < 0 ? 3_600_000L : fx.getDuration() * 50L;
        record(target, "effet:" + fx.getType().getKey().getKey(), System.currentTimeMillis() + ms, BAD.contains(fx.getType()));
    }

    /** L'effet de ce type a pris fin (expire, retire) : ce qu'il faisait durer s'arrete. */
    public static void potionGone(Player target, PotionEffectType type) {
        drop(target, "effet:" + type.getKey().getKey());
    }

    /** Un bonus (ou malus) de Force, Vitesse, Resistance pose par Boosts. */
    public static void boost(LivingEntity target, String source, Boosts.Boost b) {
        if (!(target instanceof Player p) || b == null) return;
        boolean bad = b.force < 0 || b.speed < 0 || b.resistance < 0 || b.weapon < 0;
        record(p, "boost:" + source, Long.MAX_VALUE, bad);
    }

    public static void boostGone(LivingEntity target, String source) {
        if (target instanceof Player p) drop(p, "boost:" + source);
    }

    /** Une immobilisation. */
    public static void root(LivingEntity target, int ticks) {
        if (target instanceof Player p) record(p, "entrave", System.currentTimeMillis() + ticks * 50L, true);
    }

    /**
     * Un actif qui agit sur son lanceur autrement que par un effet de potion ou un bonus (le vol de Sang
     * Draconique...) le dit lui-meme ({@code Ability.showFor}) : sa carte a gauche, avec le compte a rebours.
     */
    public static void self(Player p, Ability a, int ticks) {
        if (p == null || a == null) return;
        long now = System.currentTimeMillis();
        Influence inf = SELF.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashMap<>()).computeIfAbsent(a.id(), k -> new Influence(a.id(), true));
        inf.keys.merge("duree", now + ticks * 50L, Math::max);
        inf.touched = now;
    }

    private static void record(Player target, String key, long until, boolean bad) {
        Ability a = caller();
        if (a == null) return;
        Character ch = owners().get(a);
        if (ch == null) return;
        CfPlayer self = CrossfightPlugin.get().players().get(target);
        if (self != null && self.character() != null && self.character().id().equals(ch.id())) {
            // Son propre actif qui lui donne un bonus pour un moment : montre a gauche, bordure jaune.
            if (a.type() == com.kiokouwhite.crossfight.ability.AbilityType.PASSIVE || bad || until - System.currentTimeMillis() < MIN_SELF_MS) return;
            Influence inf = SELF.computeIfAbsent(target.getUniqueId(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(a.id(), k -> new Influence(a.id(), true));
            inf.keys.merge(key, until, Math::max);
            inf.touched = System.currentTimeMillis();
            return;
        }
        Player caster = caster(ch, target);
        if (caster == null) return;
        boolean ally = Effects.isAlly(caster, target);
        // Un allie qui aide, un adversaire qui gene : le reste (un malus d'un allie, un bonus d'un ennemi) n'est pas montre.
        if (ally == bad) return;
        Influence inf = BY.computeIfAbsent(target.getUniqueId(), k -> new LinkedHashMap<>())
                .computeIfAbsent(a.id(), k -> new Influence(a.id(), ally));
        inf.keys.merge(key, until, Math::max);
        inf.touched = System.currentTimeMillis();
    }

    private static void drop(Player target, String key) {
        for (var all : List.of(BY, SELF)) {
            var map = all.get(target.getUniqueId());
            if (map != null) for (Influence inf : map.values()) inf.keys.remove(key);
        }
    }

    private static List<Influence> live(Map<UUID, LinkedHashMap<String, Influence>> all, Player p) {
        var map = all.get(p.getUniqueId());
        if (map == null) return List.of();
        long now = System.currentTimeMillis();
        map.values().removeIf(inf -> {
            inf.keys.entrySet().removeIf(e -> e.getKey().startsWith("boost:")
                    ? Boosts.get(p, e.getKey().substring(6)) == null : e.getValue() < now);
            return inf.keys.isEmpty();
        });
        List<Influence> live = new ArrayList<>(map.values());
        live.sort((x, y) -> Long.compare(y.touched, x.touched));
        return live;
    }

    /** Les comptes a rebours dessines (tools/build_actifs.py, SECONDS) : le palier au-dessus du temps restant. */
    private static final int[] SECONDS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 25, 30, 40, 50, 60, 90, 120};
    private static final Map<UUID, String> SELF_SHOWN = new HashMap<>();
    private static final Map<UUID, Long> SELF_IN = new HashMap<>();
    private static final Map<UUID, Object[]> SELF_OUT = new HashMap<>();

    /**
     * La carte de l'actif du joueur dont l'effet dure (le plus recent), sous ses passifs a gauche :
     * "actif/<perso>_<j>_<texte>", qui glisse en arrivant (_i<etape>) et en partant (_o<etape>), avec les
     * secondes qui restent (_s<n>). Null s'il n'y en a pas.
     */
    public static String selfImage(Player p) {
        CfPlayer cf = CrossfightPlugin.get().players().get(p);
        Character c = cf == null ? null : cf.character();
        if (c == null || !Sounds.has("actif." + c.id()) || !HudPrefs.Pref.ACTIFS.of(p)) return null;
        UUID u = p.getUniqueId();
        long now = System.currentTimeMillis();
        List<Influence> live = live(SELF, p);
        Influence inf = live.isEmpty() ? null : live.get(0);
        int j = -1;
        if (inf != null) { List<Ability> acts = PassiveHud.actives(c); for (int k = 0; k < acts.size(); k++) if (acts.get(k).id().equals(inf.ability)) j = k; }
        String id = j < 0 ? null : c.id() + "_" + j + "_" + PassiveHud.code(p, c, HudPrefs.Pref.ACTIFS_TEXTE);
        String before = SELF_SHOWN.get(u);
        if (id != null && !id.equals(before)) SELF_IN.put(u, now);
        if (before != null && !before.equals(id)) SELF_OUT.put(u, new Object[]{before, now});
        SELF_SHOWN.put(u, id);
        if (id == null) {
            Object[] gone = SELF_OUT.get(u);
            if (gone != null && now - (long) gone[1] < SLIDE_OUT * STEP_MS) return "actif/" + gone[0] + "_o" + (1 + (now - (long) gone[1]) / STEP_MS);
            return null;
        }
        long since = now - SELF_IN.getOrDefault(u, 0L);
        if (since < SLIDE_IN * STEP_MS) return "actif/" + id + "_i" + (1 + since / STEP_MS);
        // Le temps qui reste : la plus lointaine des fins connues (les bonus sans fin n'en ont pas).
        long until = 0;
        for (long v : inf.keys.values()) if (v != Long.MAX_VALUE) until = Math.max(until, v);
        if (until > now && Sounds.has("actif." + c.id() + "." + j + ".s")) {
            int left = (int) Math.ceil((until - now) / 1000.0);
            for (int step : SECONDS) if (step >= left) return "actif/" + id + "_s" + step;
        }
        return "actif/" + id;
    }

    /** La capacite dont une classe apparait dans la pile d'appels (la plus proche), ou null. */
    private static Ability caller() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
                .map(f -> ability(f.getDeclaringClass())).filter(java.util.Objects::nonNull).findFirst().orElse(null));
    }

    private static Ability ability(Class<?> c) {
        while (c != null) {
            Ability a = classes().get(c);
            if (a != null) return a;
            c = c.getEnclosingClass();      // une tache planifiee ou un objet anonyme dans la capacite
        }
        return null;
    }

    private static Map<Class<?>, Ability> classes() {
        if (classes == null) {
            classes = new HashMap<>();
            owners = new HashMap<>();
            for (Licence l : CrossfightPlugin.get().characters().licences())
                for (Character c : l.characters())
                    for (Ability a : c.allAbilities()) { classes.putIfAbsent(a.getClass(), a); owners.putIfAbsent(a, c); }
        }
        return classes;
    }

    private static Map<Ability, Character> owners() { classes(); return owners; }

    /** Le joueur qui a ce personnage, le plus proche de la cible (autre qu'elle). */
    private static Player caster(Character ch, Player target) {
        Player best = null;
        double bestD = Double.MAX_VALUE;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.equals(target)) continue;
            CfPlayer cf = CrossfightPlugin.get().players().get(p);
            if (cf == null || cf.character() == null || !cf.character().id().equals(ch.id())) continue;
            double d = p.getWorld().equals(target.getWorld()) ? p.getLocation().distanceSquared(target.getLocation()) : 1e12;
            if (d < bestD) { bestD = d; best = p; }
        }
        return best;
    }

    // ------------------------------------------------------------------ ce qui est montre

    /** Les images a montrer (au plus deux, les plus recentes d'abord) : "influence/<capacite>_<ligne>_<texte>_<a|e>". */
    /** Ce qui etait montre au dernier passage ("<capacite>_<camp>" par ligne), et les glissements en cours. */
    private static final Map<UUID, List<String>> SHOWN = new HashMap<>();
    private static final Map<UUID, Long> IN_AT = new HashMap<>();
    private static final Map<UUID, String[]> OUT = new HashMap<>();
    private static final Map<UUID, long[]> OUT_AT = new HashMap<>();
    /** Etapes de glissement (tools/build_passifs.py : SLIDE_IN, SLIDE_OUT), 2 ticks chacune. */
    static final int SLIDE_IN = 3, SLIDE_OUT = 2;
    static final long STEP_MS = 100L;

    /**
     * Les images a montrer (au plus deux, les plus recentes en haut) : "influence/<capacite>_<ligne>_<texte>_<a|e>".
     * Une carte qui arrive glisse depuis le bord droit de l'ecran (_i<etape>, toujours en haut) ; une carte qui s'en
     * va y repart (_o<etape>), si sa ligne n'est pas reprise.
     */
    public static List<String> images(Player p) {
        UUID u = p.getUniqueId();
        long now = System.currentTimeMillis();
        List<String> cur = new ArrayList<>();
        if (HudPrefs.Pref.AUTRES.of(p)) for (Influence inf : live(BY, p)) {
            if (cur.size() == 2) break;
            String side = inf.ally ? "a" : "e";
            if (Sounds.has("influence." + inf.ability + "." + side)) cur.add(inf.ability + "_" + side);
        }
        List<String> before = SHOWN.getOrDefault(u, List.of());
        if (!cur.isEmpty() && !before.contains(cur.get(0))) IN_AT.put(u, now);
        String[] out = OUT.computeIfAbsent(u, k -> new String[2]);
        long[] outAt = OUT_AT.computeIfAbsent(u, k -> new long[2]);
        for (int r = 0; r < before.size(); r++)
            if (!cur.contains(before.get(r))) { out[r] = before.get(r); outAt[r] = now; }
        SHOWN.put(u, cur);

        int text = PassiveHud.textMode(p, HudPrefs.Pref.AUTRES_TEXTE);
        List<String> images = new ArrayList<>();
        for (int r = 0; r < 2; r++) {
            if (r < cur.size()) {
                String[] id = cur.get(r).split("_(?=[ae]$)");
                String key = "influence/" + id[0] + "_" + r + "_" + text + "_" + id[1];
                long since = now - IN_AT.getOrDefault(u, 0L);
                if (r == 0 && since < SLIDE_IN * STEP_MS) key += "_i" + (1 + since / STEP_MS);
                images.add(key);
            } else if (out[r] != null && now - outAt[r] < SLIDE_OUT * STEP_MS) {
                String[] id = out[r].split("_(?=[ae]$)");
                images.add("influence/" + id[0] + "_" + r + "_" + text + "_" + id[1] + "_o" + (1 + (now - outAt[r]) / STEP_MS));
            } else {
                images.add(null);
            }
        }
        return images;
    }

    /**
     * Demonstration (/cf hud demo) : un passif d'allie (Charisme, 12 s), un malus d'adversaire (Orbe de Lenteur,
     * 7 s, arrive 2 s plus tard) et, a gauche, le premier actif du personnage du joueur (5 s).
     */
    public static void demo(Player p) {
        long now = System.currentTimeMillis();
        BY.remove(p.getUniqueId());
        Influence ally = new Influence("marth_charisme", true);
        ally.keys.put("demo", now + 12_000L); ally.touched = now;
        BY.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashMap<>()).put(ally.ability, ally);
        Bukkit.getScheduler().runTaskLater(CrossfightPlugin.get(), () -> {
            Influence enemy = new Influence("sage_lenteur", false);
            enemy.keys.put("demo", System.currentTimeMillis() + 7_000L); enemy.touched = System.currentTimeMillis();
            BY.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashMap<>()).put(enemy.ability, enemy);
        }, 40L);
        CfPlayer cf = CrossfightPlugin.get().players().get(p);
        List<Ability> acts = PassiveHud.actives(cf == null ? null : cf.character());
        int pick = 0;      // de preference un actif qui a son compte a rebours
        for (int k = 0; k < acts.size(); k++) if (Sounds.has("actif." + cf.character().id() + "." + k + ".s")) { pick = k; break; }
        if (!acts.isEmpty()) {
            Influence self = new Influence(acts.get(pick).id(), true);
            self.keys.put("demo", now + 5_000L); self.touched = now;
            SELF.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashMap<>()).put(self.ability, self);
        }
    }

    public static void forget(Player p) {
        UUID u = p.getUniqueId();
        BY.remove(u); SELF.remove(u); SHOWN.remove(u); IN_AT.remove(u); OUT.remove(u); OUT_AT.remove(u);
        SELF_SHOWN.remove(u); SELF_IN.remove(u); SELF_OUT.remove(u);
    }

    public static void clearAll() { BY.clear(); SELF.clear(); }
}
