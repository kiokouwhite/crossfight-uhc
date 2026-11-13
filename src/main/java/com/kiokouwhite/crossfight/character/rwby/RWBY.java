package com.kiokouwhite.crossfight.character.rwby;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.ability.Ability;
import com.kiokouwhite.crossfight.ability.AbilityState;
import com.kiokouwhite.crossfight.ability.AbilityType;
import com.kiokouwhite.crossfight.ability.CustomUse;
import com.kiokouwhite.crossfight.character.Licence;
import com.kiokouwhite.crossfight.character.LicenceMechanic;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.TeamSize;
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
 * Licence RWBY et sa particularite : les Ultimes en Duo.
 *
 * Chaque paire de personnages a un ultime commun, charge par les degats que les deux
 * infligent quand ils sont a moins de 10 blocs l'un de l'autre. 300 % de degats
 * (30 coeurs cumules) sont necessaires.
 */
public final class RWBY {

    public static final double DUO_RANGE = 10.0;
    public static final double DUO_REQUIRED = 300.0;

    private RWBY() {}

    public static Licence create() {
        Licence l = new Licence("rwby", "RWBY", TeamSize.TRIO,
                "Ultimes en duo, charges par les degats de deux allies a moins de 10 blocs (300 %).");
        Duo duo = new Duo();
        l.mechanic(duo);
        Blake blake = new Blake();
        Weiss weiss = new Weiss();
        l.add(new Ruby());
        l.add(new Yang());
        l.add(weiss);
        l.add(blake);
        l.listener(blake);
        l.listener(weiss);
        return l;
    }

    /** Jauge d'une paire. */
    static final class PairGauge {
        double progress;
    }

    public static final class Duo implements LicenceMechanic {

        private final Map<String, PairGauge> gauges = new HashMap<>();

        private static String key(UUID a, UUID b) {
            return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
        }

        PairGauge gauge(Player a, Player b) {
            return gauges.computeIfAbsent(key(a.getUniqueId(), b.getUniqueId()), k -> new PairGauge());
        }

        @Override public void reset() { gauges.clear(); }

        @Override
        public void onDealDamage(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e) {
            DuoUltimates.onFreezingHit(attacker, victim);
            double percent = e.getFinalDamage() / 20.0 * 100.0;
            if (percent <= 0) return;
            for (Player ally : Effects.allyPlayersWithin(attacker, DUO_RANGE)) {
                if (!isRwby(ally)) continue;
                PairGauge g = gauge(attacker, ally);
                boolean wasReady = g.progress >= DUO_REQUIRED;
                g.progress = Math.min(DUO_REQUIRED, g.progress + percent);
                if (!wasReady && g.progress >= DUO_REQUIRED) {
                    for (Player p : new Player[]{attacker, ally}) {
                        Msg.send(p, "<light_purple><bold>Ultime en Duo pret</bold> <light_purple>avec " + (p == attacker ? ally : attacker).getName() + " !");
                        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
                    }
                }
            }
        }

        /** Les allies RWBY de ce joueur dont la jauge commune est pleine. */
        List<Player> readyPartners(Player p) {
            List<Player> out = new ArrayList<>();
            for (Player ally : Bukkit.getOnlinePlayers()) {
                if (ally == p || !Effects.isAlly(p, ally) || !isRwby(ally)) continue;
                PairGauge g = gauges.get(key(p.getUniqueId(), ally.getUniqueId()));
                if (g != null && g.progress >= DUO_REQUIRED) out.add(ally);
            }
            return out;
        }

        String summary(Player p) {
            StringBuilder sb = new StringBuilder();
            for (Player ally : Bukkit.getOnlinePlayers()) {
                if (ally == p || !Effects.isAlly(p, ally) || !isRwby(ally)) continue;
                PairGauge g = gauges.get(key(p.getUniqueId(), ally.getUniqueId()));
                int pct = g == null ? 0 : (int) (g.progress / DUO_REQUIRED * 100);
                sb.append(ally.getName()).append(' ').append(pct).append("% ");
            }
            // Les mannequins duo (zone duo du mode test) sont toujours prets.
            for (LivingEntity m : partnerMannequins(p)) sb.append(partnerName(m)).append(" 100% ");
            return sb.toString().trim();
        }

        /**
         * Les partenaires avec qui ce joueur peut lancer un Ultime en Duo : ses allies RWBY dont la jauge
         * commune est pleine (toutes, en mode test), et ses mannequins duo RWBY.
         */
        List<LivingEntity> choices(Player p) {
            List<LivingEntity> out = new ArrayList<>();
            boolean test = CrossfightPlugin.get().game().isTest();
            for (Player ally : Bukkit.getOnlinePlayers()) {
                if (ally == p || !Effects.isAlly(p, ally) || !isRwby(ally)) continue;
                PairGauge g = gauges.get(key(p.getUniqueId(), ally.getUniqueId()));
                if (test || (g != null && g.progress >= DUO_REQUIRED)) out.add(ally);
            }
            out.addAll(partnerMannequins(p));
            return out;
        }

        /** Test : met toutes les jauges du joueur avec ses allies RWBY a un pourcentage. */
        public int fill(Player p, double percent) {
            int n = 0;
            for (Player ally : Bukkit.getOnlinePlayers()) {
                if (ally == p || !Effects.isAlly(p, ally) || !isRwby(ally)) continue;
                gauge(p, ally).progress = DUO_REQUIRED * percent / 100.0;
                n++;
            }
            return n;
        }

        void consume(Player a, Player b) {
            PairGauge g = gauges.get(key(a.getUniqueId(), b.getUniqueId()));
            if (g != null) g.progress = 0;
        }
    }

    static boolean isRwby(Player p) {
        CfPlayer cf = CrossfightPlugin.get().players().get(p);
        return cf.character() != null && cf.character().licence() != null && "rwby".equals(cf.character().licence().id());
    }

    /** Le personnage d'un joueur, ou celui qu'incarne un mannequin duo. */
    static String characterId(LivingEntity e) {
        if (e instanceof Player p) {
            CfPlayer cf = CrossfightPlugin.get().players().get(p);
            return cf.character() == null ? "" : cf.character().id();
        }
        String c = com.kiokouwhite.crossfight.util.Mannequins.partnerCharacterOf(e);
        return c == null ? "" : c;
    }

    /** Les mannequins duo de ce joueur qui incarnent un personnage RWBY. */
    static List<LivingEntity> partnerMannequins(Player p) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity m : com.kiokouwhite.crossfight.util.Mannequins.partnersOf(p)) {
            var c = CrossfightPlugin.get().characters().character(characterId(m));
            if (c != null && c.licence() != null && "rwby".equals(c.licence().id())) out.add(m);
        }
        return out;
    }

    /** Le nom d'une partenaire : le joueur, ou "mannequin" et son personnage. */
    static String partnerName(LivingEntity e) {
        if (e instanceof Player p) return p.getName();
        var c = CrossfightPlugin.get().characters().character(characterId(e));
        return "mannequin " + (c == null ? "duo" : c.name());
    }

    static Duo duo() {
        return (Duo) CrossfightPlugin.get().characters().licence("rwby").mechanic();
    }

    /**
     * L'item "Ultime en Duo", commun a tous les personnages RWBY. Un clic droit lance
     * l'ultime de la paire prete (ou propose un choix s'il y en a plusieurs).
     */
    public static final class DuoUltimate extends Ability implements CustomUse {

        DuoUltimate() {
            super("rwby_duo", "Ultime en Duo", "Se charge avec les degats infliges par deux allies RWBY a moins de 10 blocs (300 %). L'effet depend de la paire.",
                    AbilityType.ACTIVE, Material.AMETHYST_CLUSTER);
        }

        @Override
        public String barText(Player p, CfPlayer cf, AbilityState st) {
            String s = duo().summary(p);
            return s.isEmpty() ? "<light_purple>Duo : <gray>aucun allie RWBY" : "<light_purple>Duo " + s;
        }

        @Override
        public boolean onUse(Player p, CfPlayer cf, AbilityState st, boolean sneaking) {
            List<LivingEntity> ready = duo().choices(p);
            if (ready.isEmpty()) {
                Msg.actionBar(p, "<red>Aucun Ultime en Duo pret. <gray>" + duo().summary(p));
                return true;
            }
            if (ready.size() == 1) {
                launch(p, ready.get(0));
                return true;
            }
            List<ChoiceMenu.Option> options = new ArrayList<>();
            for (LivingEntity ally : ready) {
                options.add(new ChoiceMenu.Option(Material.PLAYER_HEAD, "Avec " + partnerName(ally), List.of("<gray>" + DuoUltimates.describe(p, ally)), () -> launch(p, ally)));
            }
            ChoiceMenu.open(p, "<dark_gray>Ultime en Duo : avec qui ?", options);
            return true;
        }

        private void launch(Player p, LivingEntity ally) {
            boolean available = ally instanceof Player ap ? ap.isOnline() && Effects.isAlly(p, ap) : ally.isValid();
            if (!available) { Msg.actionBar(p, "<red>Ton allie n'est plus disponible."); return; }
            if (DuoUltimates.cast(p, ally)) {
                if (ally instanceof Player ap) duo().consume(p, ap);
                String name = DuoUltimates.name(p, ally);
                for (LivingEntity o : new LivingEntity[]{p, ally}) {
                    o.getWorld().playSound(o.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.6f);
                }
                // Comme les appels de partenaire de BlazBlue : le lanceur appelle sa coequipiere, qui repond par son nom.
                String a = characterId(p), b = characterId(ally);
                if (!a.isEmpty() && !b.isEmpty()) {
                    com.kiokouwhite.crossfight.util.Sounds.voice(p, a + "_duo_" + b);
                    com.kiokouwhite.crossfight.util.Sounds.voiceLater(ally, b + "_nom_" + a, com.kiokouwhite.crossfight.util.Sounds.after(a + "_duo_" + b, 25L));
                }
                CrossfightPlugin.get().game().broadcastNear(p, "<light_purple>" + p.getName() + " et " + partnerName(ally) + " lancent <bold>" + name + "</bold> !");
            }
        }
    }

    static final DuoUltimate DUO_ITEM = new DuoUltimate();

    /** L'orientation (yaw) qui regarde de {@code from} vers {@code to}. */
    static float yawTowards(LivingEntity from, LivingEntity to) {
        org.bukkit.util.Vector d = to.getLocation().toVector().subtract(from.getLocation().toVector());
        if (d.getX() * d.getX() + d.getZ() * d.getZ() < 1e-4) return from.getLocation().getYaw();
        return (float) Math.toDegrees(Math.atan2(-d.getX(), d.getZ()));
    }

    /** Une douille vide qui saute de l'arme apres un tir (Ember Celica, Crescent Rose) : decorative, ramassable par personne. */
    static void ejectShell(LivingEntity shooter, org.bukkit.Location from) {
        org.bukkit.Location l = shooter.getLocation();
        double yaw = Math.toRadians(l.getYaw());
        org.bukkit.util.Vector right = new org.bukkit.util.Vector(-Math.cos(yaw), 0, -Math.sin(yaw));
        org.bukkit.util.Vector back = new org.bukkit.util.Vector(Math.sin(yaw), 0, -Math.cos(yaw));
        org.bukkit.entity.Item shell = shooter.getWorld().dropItem(from, new org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLD_NUGGET), it -> {
            it.setPickupDelay(Short.MAX_VALUE);
            it.setCanMobPickup(false);
            it.setPersistent(false);
            it.setInvulnerable(true);
        });
        shell.setVelocity(right.multiply(0.12).add(back.multiply(0.08)).add(new org.bukkit.util.Vector(0, 0.28, 0)));
        org.bukkit.Bukkit.getScheduler().runTaskLater(com.kiokouwhite.crossfight.CrossfightPlugin.get(), shell::remove, 25L);
    }
}
