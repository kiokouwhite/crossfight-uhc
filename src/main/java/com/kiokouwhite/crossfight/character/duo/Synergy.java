package com.kiokouwhite.crossfight.character.duo;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.AbilityType;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.character.LicenceMechanic;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.util.ChoiceMenu;
import com.kiokouwhite.crossfight.util.Effects;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Synergie des groupes duo : un second ultime commun a une paire de personnages, charge par les
 * degats que les deux infligent (200 % necessaires). Chaque licence duo enregistre ses paires.
 */
public final class Synergy implements LicenceMechanic {

    public static final double REQUIRED = 200.0;

    /** L'effet d'une paire de personnages. */
    public interface Effect {
        String name();
        String description();
        /** Lance la synergie pour les deux joueurs. Retourne false si rien n'a pu etre lance. */
        boolean cast(Player a, Player b);
        /** Clics suivants sur l'item de Synergie pendant que l'effet est actif (dashs, talismans...). */
        default boolean onUse(Player p, Player partner, AbilityState st, boolean sneaking) { return false; }
        /** Duree pendant laquelle l'item de Synergie reste "actif" pour ces clics suivants (ms). */
        default long activeMillis() { return 0L; }
    }

    private static final Map<String, Effect> EFFECTS = new HashMap<>();
    private static final Map<String, Synergy> BY_LICENCE = new HashMap<>();

    public static String pairKey(String charA, String charB) { return charA.compareTo(charB) < 0 ? charA + "|" + charB : charB + "|" + charA; }

    /** Declare la synergie de deux personnages. */
    public static void register(String charA, String charB, Effect effect) { EFFECTS.put(pairKey(charA, charB), effect); }

    public static Effect effectOf(Player a, Player b) {
        CfPlayer ca = plugin().players().get(a), cb = plugin().players().get(b);
        if (ca.character() == null || cb.character() == null) return null;
        return EFFECTS.get(pairKey(ca.character().id(), cb.character().id()));
    }

    static CrossfightPlugin plugin() { return CrossfightPlugin.get(); }

    // ---------------------------------------------------------------- jauges

    private final String licenceId;
    private final Map<String, double[]> gauges = new HashMap<>();

    public Synergy(String licenceId) { this.licenceId = licenceId; BY_LICENCE.put(licenceId, this); }

    public static Synergy of(Player p) {
        CfPlayer cf = plugin().players().get(p);
        if (cf.character() == null || cf.character().licence() == null) return null;
        return BY_LICENCE.get(cf.character().licence().id());
    }

    private static String key(UUID a, UUID b) { return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a; }

    double[] gauge(Player a, Player b) { return gauges.computeIfAbsent(key(a.getUniqueId(), b.getUniqueId()), k -> new double[1]); }

    boolean sameLicence(Player p) {
        CfPlayer cf = plugin().players().get(p);
        return cf.character() != null && cf.character().licence() != null && licenceId.equals(cf.character().licence().id());
    }

    /** Les allies de la licence (le partenaire du duo). */
    List<Player> partners(Player p) {
        List<Player> out = new ArrayList<>();
        for (Player ally : Bukkit.getOnlinePlayers()) if (ally != p && Effects.isAlly(p, ally) && sameLicence(ally)) out.add(ally);
        return out;
    }

    @Override public void reset() { gauges.clear(); }

    @Override
    public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e) {
        double percent = e.getFinalDamage() / 20.0 * 100.0;
        if (percent <= 0) return;
        for (Player ally : partners(attacker)) {
            double[] g = gauge(attacker, ally);
            boolean wasReady = g[0] >= REQUIRED;
            g[0] = Math.min(REQUIRED, g[0] + percent);
            if (!wasReady && g[0] >= REQUIRED) {
                Effect ef = effectOf(attacker, ally);
                for (Player p : new Player[]{attacker, ally}) {
                    Msg.send(p, "<aqua><bold>Synergie prete</bold> <aqua>avec " + (p == attacker ? ally : attacker).getName() + (ef == null ? "" : " : " + ef.name()) + " !");
                    p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
                }
            }
        }
    }

    List<Player> readyPartners(Player p) {
        List<Player> out = new ArrayList<>();
        for (Player ally : partners(p)) if (gauge(p, ally)[0] >= REQUIRED) out.add(ally);
        return out;
    }

    String summary(Player p) {
        StringBuilder sb = new StringBuilder();
        for (Player ally : partners(p)) sb.append(ally.getName()).append(' ').append((int) (gauge(p, ally)[0] / REQUIRED * 100)).append("% ");
        return sb.toString().trim();
    }

    /** Test : met les jauges du joueur avec ses partenaires a un pourcentage. */
    public int fill(Player p, double percent) {
        int n = 0;
        for (Player ally : partners(p)) { gauge(p, ally)[0] = REQUIRED * percent / 100.0; n++; }
        return n;
    }

    void consume(Player a, Player b) { gauge(a, b)[0] = 0; }

    // ------------------------------------------------------------------- item

    public static final SynergyItem ITEM = new SynergyItem();

    /** L'item de Synergie, commun a tous les personnages des groupes duo. */
    public static final class SynergyItem extends Ability implements CustomUse {
        SynergyItem() {
            super("duo_synergie", "Synergie", "Second ultime commun au duo : se charge avec les degats infliges par les deux partenaires (200 %). L'effet depend de la paire.", AbilityType.ACTIVE, Material.NETHERITE_SCRAP);
        }
        @Override protected List<String> extraLore() { return List.of("", "<yellow>Clic droit : lancer la Synergie"); }

        @Override public String barText(Player p, CfPlayer cf, AbilityState st) {
            if (System.currentTimeMillis() < st.get("activeUntil", 0L)) return "<aqua><bold>SYNERGIE</bold> <white>" + st.get("activeName", "") + " " + ((st.get("activeUntil", 0L) - System.currentTimeMillis()) / 1000 + 1) + "s";
            Synergy s = of(p);
            String txt = s == null ? "" : s.summary(p);
            return txt.isEmpty() ? "<aqua>Synergie <gray>sans partenaire" : "<aqua>Synergie " + txt;
        }

        @Override public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            Synergy s = of(p);
            if (s == null) return true;
            if (System.currentTimeMillis() < st.get("activeUntil", 0L)) {
                String pid = st.get("activePartner", null);
                Player partner = pid == null ? null : Bukkit.getPlayer(UUID.fromString(pid));
                Effect ef = partner == null ? null : effectOf(p, partner);
                if (ef != null && ef.onUse(p, partner, st, sneaking)) return true;
            }
            List<Player> ready = s.readyPartners(p);
            if (plugin().game().isTest() && ready.isEmpty()) ready = s.partners(p);
            if (ready.isEmpty()) { Msg.actionBar(p, "<red>Aucune Synergie prete. <gray>" + s.summary(p)); return true; }
            if (ready.size() == 1) { launch(p, ready.get(0), st); return true; }
            List<ChoiceMenu.Option> options = new ArrayList<>();
            for (Player ally : ready) {
                Effect ef = effectOf(p, ally);
                options.add(new ChoiceMenu.Option(Material.PLAYER_HEAD, "Avec " + ally.getName(), List.of("<gray>" + (ef == null ? "?" : ef.name())), () -> launch(p, ally, st)));
            }
            ChoiceMenu.open(p, "<aqua>Synergie : avec qui ?", options);
            return true;
        }

        private void launch(Player p, Player ally, AbilityState st) {
            Synergy s = of(p);
            if (s == null || !ally.isOnline() || !Effects.isAlly(p, ally)) { Msg.actionBar(p, "<red>Ton partenaire n'est plus disponible."); return; }
            Effect ef = effectOf(p, ally);
            if (ef == null) { Msg.actionBar(p, "<red>Pas de Synergie pour cette paire."); return; }
            if (!ef.cast(p, ally)) return;
            s.consume(p, ally);
            var cp = plugin().players().get(p).character(); var ca = plugin().players().get(ally).character();
            if (cp != null && ca != null) com.kiokouwhite.crossfight.util.Sounds.synergy(p, cp.id(), ally, ca.id());
            for (Player o : new Player[]{p, ally}) {
                o.playSound(o.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.6f);
                AbilityState os = plugin().abilities().state(o, ITEM);
                os.set("activeUntil", System.currentTimeMillis() + ef.activeMillis());
                os.set("activePartner", (o == p ? ally : p).getUniqueId().toString());
                os.set("activeName", ef.name());
            }
            plugin().game().broadcastNear(p, "<aqua>" + p.getName() + " et " + ally.getName() + " lancent <bold>" + ef.name() + "</bold> !");
        }
    }
}
