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
import java.util.HashSet;
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
        /** La variante de texte du lanceur au moment du lancer (Weiss : sa Dust) : la carte garde ce texte. */
        String variant = "";
        /** Les cles posees par un effet entretenu (repose sans cesse) : pas de compte a rebours tant qu'elles sont la. */
        final Set<String> loops = new HashSet<>();
        /** Tant que c'est vrai, la carte reste (un coup arme, sans duree) : la cle "tenu". */
        java.util.function.BooleanSupplier held;
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
        String key = "effet:" + fx.getType().getKey().getKey();
        // Un effet entretenu (Effects.refresh : sans particules) est repose sans cesse pour quelques secondes : sa duree
        // ne dit pas quand il s'arretera. La carte qu'il fait durer n'a donc pas de compte a rebours.
        LOOPING.set(!fx.hasParticles());
        try { record(target, key, System.currentTimeMillis() + ms, BAD.contains(fx.getType())); } finally { LOOPING.set(false); }
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
        Influence inf = own(p, a);
        inf.keys.merge("duree", now + ticks * 50L, Math::max);
        inf.touched = now;
    }

    /**
     * Un actif qui reste arme sans duree (le prochain coup de Weiss...) : sa carte a gauche, sans compte a rebours,
     * tant que {@code armed} est vrai.
     */
    public static void selfWhile(Player p, Ability a, java.util.function.BooleanSupplier armed) {
        if (p == null || a == null) return;
        Influence inf = own(p, a);
        inf.keys.put("tenu", Long.MAX_VALUE);
        inf.held = armed;
        inf.touched = System.currentTimeMillis();
    }

    /**
     * La carte d'un actif du joueur, dans la variante de texte du moment : relancer la meme capacite avec une
     * autre variante (Weiss, une autre Dust) ouvre une seconde carte au lieu de changer le texte de la premiere.
     */
    private static Influence own(Player p, Ability a) {
        CfPlayer cf = CrossfightPlugin.get().players().get(p);
        String variant = cf == null || cf.character() == null ? "" : cf.character().hudActiveVariant(p, a.id());
        Influence inf = SELF.computeIfAbsent(p.getUniqueId(), k -> new LinkedHashMap<>()).computeIfAbsent(a.id() + "#" + variant, k -> new Influence(a.id(), true));
        inf.variant = variant;
        return inf;
    }

    /** L'actif annonce par {@link #self} s'arrete plus tot que prevu : sa carte s'en va. */
    public static void selfEnd(Player p, Ability a) {
        if (p == null || a == null) return;
        var map = SELF.get(p.getUniqueId());
        if (map != null) for (Influence inf : map.values()) if (inf.ability.equals(a.id())) { inf.keys.remove("duree"); inf.keys.remove("tenu"); }
    }

    /**
     * Une capacite d'un autre joueur qui te concerne sans te poser d'effet (le Contre de Marth : ne le frappe pas) :
     * sa carte a droite, en adversaire ou en allie, pendant {@code ticks}.
     */
    public static void threat(Player target, Ability a, boolean ally, int ticks) {
        if (a != null) threat(target, a.id(), ally, ticks);
    }

    /**
     * La meme carte, par son identifiant : ce qui n'est pas une capacite du plugin mais a ses images
     * (un Ultime en Duo de RWBY, "duo_weiss_yang" : tools/build_influences.py, DUO_SIDES).
     */
    public static void threat(Player target, String id, boolean ally, int ticks) {
        if (target == null || id == null) return;
        long now = System.currentTimeMillis();
        Influence inf = BY.computeIfAbsent(target.getUniqueId(), k -> new LinkedHashMap<>()).computeIfAbsent(id, k -> new Influence(id, ally));
        inf.keys.merge("avert", now + ticks * 50L, Math::max);
        inf.touched = now;
    }

    private static final ThreadLocal<Boolean> LOOPING = ThreadLocal.withInitial(() -> false);

    /** Le temps qui reste a une carte : la plus lointaine de ses fins connues ; 0 (pas de compte a rebours) si elle est tenue ou entretenue sans fin. */
    private static long endOf(Influence inf) {
        if (inf.keys.containsKey("tenu")) return 0;
        long until = 0;
        for (var e : inf.keys.entrySet()) {
            if (inf.loops.contains(e.getKey())) return 0;
            if (e.getValue() != Long.MAX_VALUE) until = Math.max(until, e.getValue());
        }
        return until;
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
            // La carte deja ouverte pour cette capacite (la plus recente) ; sinon une nouvelle, dans la variante du moment.
            Influence inf = null;
            var mine = SELF.get(target.getUniqueId());
            if (mine != null) for (Influence x : mine.values()) if (x.ability.equals(a.id()) && (inf == null || x.touched > inf.touched)) inf = x;
            if (inf == null) inf = own(target, a);
            inf.keys.merge(key, until, Math::max);
            if (LOOPING.get()) inf.loops.add(key); else inf.loops.remove(key);
            inf.touched = System.currentTimeMillis();
            return;
        }
        // L'objet de Synergie est commun a toutes les paires : chez les autres, c'est la carte de la Synergie lancee
        // qui parle (Synergy.card), pas la sienne.
        if (a.id().equals("duo_synergie")) return;
        Player caster = caster(ch, target);
        if (caster == null) return;
        boolean ally = Effects.isAlly(caster, target);
        // Un allie qui aide, un adversaire qui gene : le reste (un malus d'un allie, un bonus d'un ennemi) n'est pas montre.
        if (ally == bad) return;
        Influence inf = BY.computeIfAbsent(target.getUniqueId(), k -> new LinkedHashMap<>())
                .computeIfAbsent(a.id(), k -> new Influence(a.id(), ally));
        inf.keys.merge(key, until, Math::max);
        if (LOOPING.get()) inf.loops.add(key); else inf.loops.remove(key);
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
            if (inf.held != null && !inf.held.getAsBoolean()) { inf.held = null; inf.keys.remove("tenu"); }
            inf.keys.entrySet().removeIf(e -> e.getKey().startsWith("boost:")
                    ? Boosts.get(p, e.getKey().substring(6)) == null : e.getValue() < now);
            return inf.keys.isEmpty();
        });
        List<Influence> live = new ArrayList<>(map.values());
        live.sort((x, y) -> Long.compare(y.touched, x.touched));
        return live;
    }

    /** Les actifs en cours du joueur, du plus recent au plus ancien, sans ceux qui se taisent pour l'instant ({@code Ability.hudShown}). */
    private static List<Influence> liveSelf(Player p, Character c) {
        // Une capacite a bascule allumee (Flottement, Barriere, Champ Electrique...) : sa carte reste tant qu'elle l'est.
        var mine = SELF.get(p.getUniqueId());
        for (Ability a : c.allAbilities()) {
            if (!(a instanceof com.kiokouwhite.crossfight.ability.ToggleAbility)) continue;
            var st = CrossfightPlugin.get().abilities().state(p, a);
            if (!st.isEnabled()) continue;
            boolean held = false;
            if (mine != null) for (Influence x : mine.values()) if (x.ability.equals(a.id()) && x.held != null) held = true;
            if (!held) selfWhile(p, a, st::isEnabled);
        }
        List<Influence> out = new ArrayList<>(live(SELF, p));
        out.removeIf(inf -> { Ability a = c.ability(inf.ability); return a != null && !a.hudShown(p); });
        return out;
    }

    /** Les comptes a rebours dessines (tools/build_actifs.py, SECONDS) : le palier au-dessus du temps restant. */
    private static final int[] SECONDS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 25, 30, 40, 50, 60, 90, 120};
    /** Les paliers des cartes de droite (tools/build_influences.py, SECONDS). */
    private static final int[] RIGHT_SECONDS = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 15, 20, 30};
    private static final Map<UUID, String> SELF_SHOWN = new HashMap<>();
    private static final Map<UUID, Long> SELF_IN = new HashMap<>();
    private static final Map<UUID, Object[]> SELF_OUT = new HashMap<>();

    /**
     * Le second actif du joueur dont l'effet dure (celui d'avant le plus recent) : une petite case, icone et
     * secondes, a droite de la carte du premier ("actif2/<perso>_<j>_<code>[_s<n>]"). Null s'il n'y en a pas.
     */
    public static String selfImage2(Player p) {
        CfPlayer cf = CrossfightPlugin.get().players().get(p);
        Character c = cf == null ? null : cf.character();
        if (c == null || !HudPrefs.Pref.ACTIFS.of(p)) return null;
        List<Influence> live = liveSelf(p, c);
        if (live.size() < 2) return null;
        Influence inf = live.get(1);
        List<Ability> acts = PassiveHud.actives(c);
        int j = -1;
        for (int k = 0; k < acts.size(); k++) if (acts.get(k).id().equals(inf.ability)) j = k;
        if (j < 0 || !Sounds.has("actif2." + c.id() + "." + j)) return null;
        String id = "actif2/" + c.id() + "_" + j + "_" + PassiveHud.code(p, c, HudPrefs.Pref.ACTIFS_TEXTE);
        long now = System.currentTimeMillis(), until = 0;
        until = endOf(inf);
        if (until > now) {
            int left = (int) Math.ceil((until - now) / 1000.0);
            for (int step : SECONDS) if (step >= left) return id + "_s" + step;
        }
        return id;
    }

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
        List<Influence> live = liveSelf(p, c);
        Influence inf = live.isEmpty() ? null : live.get(0);
        int j = -1;
        if (inf != null) { List<Ability> acts = PassiveHud.actives(c); for (int k = 0; k < acts.size(); k++) if (acts.get(k).id().equals(inf.ability)) j = k; }
        String id = j < 0 ? null : c.id() + "_" + j + "_" + PassiveHud.code(p, c, HudPrefs.Pref.ACTIFS_TEXTE)
                + PassiveHud.textVariant(p, c, HudPrefs.Pref.ACTIFS_TEXTE, inf.variant);
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
        until = endOf(inf);
        if (until > now && Sounds.has("actif." + c.id() + "." + j + ".s")) {
            int left = (int) Math.ceil((until - now) / 1000.0);
            for (int step : SECONDS) if (step >= left) return "actif/" + id + "_s" + step;
        }
        return "actif/" + id;
    }

    /** La capacite que le moteur est en train de lancer (AbilityEngine.use), ou null. */
    private static final ThreadLocal<Ability> USING = new ThreadLocal<>();
    /** Les classes que plusieurs capacites partagent (Enfermement et Etouffement d'Arlo, les pouvoirs de titans, les tomes). */
    private static final Set<Class<?>> SHARED = new HashSet<>();

    public static void using(Ability a) { USING.set(a); }

    /**
     * La capacite dont une classe apparait dans la pile d'appels (la plus proche), ou null. Quand cette classe sert
     * a plusieurs capacites, c'est celle que le moteur lance en ce moment si elle en est ; sinon on ne sait pas
     * laquelle, et la capacite doit se declarer elle-meme ({@link #threat}).
     */
    private static Ability caller() {
        Ability found = StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
                .map(f -> ability(f.getDeclaringClass())).filter(java.util.Objects::nonNull).findFirst().orElse(null));
        if (found == null || !SHARED.contains(found.getClass())) return found;
        Ability now = USING.get();
        return now != null && now.getClass() == found.getClass() ? now : null;
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
                    for (Ability a : c.allAbilities()) {
                        Ability first = classes.putIfAbsent(a.getClass(), a);
                        if (first != null && first != a) SHARED.add(a.getClass());
                        owners.putIfAbsent(a, c);
                    }
        }
        return classes;
    }

    private static Map<Ability, Character> owners() { classes(); return owners; }

    /** Le joueur qui a ce personnage, le plus proche de la cible (autre qu'elle). */
    private static Player caster(Character ch, Player target) {
        Player best = null;
        double bestD = Double.MAX_VALUE;
        // Les joueurs en ligne, et les mannequins pilotes du mode test en troisieme personne (ils lancent comme des joueurs).
        List<Player> all = new ArrayList<>(Bukkit.getOnlinePlayers());
        all.addAll(com.kiokouwhite.crossfight.util.MannequinPlayer.all());
        for (Player p : all) {
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
        Map<String, Integer> left = new HashMap<>();     // le temps qui reste a chaque carte, en secondes (0 : inconnu)
        if (HudPrefs.Pref.AUTRES.of(p)) for (Influence inf : live(BY, p)) {
            if (cur.size() == 2) break;
            String side = inf.ally ? "a" : "e";
            if (!Sounds.has("influence." + inf.ability + "." + side)) continue;
            cur.add(inf.ability + "_" + side);
            long until = 0;
            until = endOf(inf);
            left.put(inf.ability + "_" + side, until > now ? (int) Math.ceil((until - now) / 1000.0) : 0);
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
                else {
                    // Le compte a rebours de l'effet, si la carte en a les images (tools/build_influences.py, SECONDS).
                    int rest = left.getOrDefault(cur.get(r), 0);
                    if (rest > 0 && Sounds.has("influence." + id[0] + "." + id[1] + ".s"))
                        for (int step : RIGHT_SECONDS) if (step >= rest) { key += "_s" + step; break; }
                }
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
