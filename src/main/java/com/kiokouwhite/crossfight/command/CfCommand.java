package com.kiokouwhite.crossfight.command;

import com.kiokouwhite.crossfight.CrossfightPlugin;
import com.kiokouwhite.crossfight.character.Character;
import com.kiokouwhite.crossfight.character.Licence;
import com.kiokouwhite.crossfight.player.CfPlayer;
import com.kiokouwhite.crossfight.team.Team;
import com.kiokouwhite.crossfight.team.TeamSize;
import com.kiokouwhite.crossfight.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * /cf : commandes d'administration et d'information.
 */
public final class CfCommand implements TabExecutor {

    private final CrossfightPlugin plugin;

    public CfCommand(CrossfightPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "status" -> status(sender);
            case "licences", "licenses" -> licences(sender);
            case "perso", "character" -> character(sender, args);
            case "team" -> team(sender, args);
            default -> {
                if (!sender.hasPermission("crossfight.admin")) {
                    Msg.send(sender, "<red>Tu n'as pas la permission.");
                    return true;
                }
                switch (sub) {
                    case "start" -> {
                        if (plugin.game().start()) Msg.send(sender, "<green>Lancement de la partie.");
                        else Msg.send(sender, "<red>Impossible de lancer (deja en cours ou aucun joueur).");
                    }
                    case "stop" -> {
                        plugin.game().stop();
                        Msg.broadcast("<yellow>La partie a ete arretee, retour au lobby.");
                    }
                    case "setsize" -> {
                        TeamSize size = args.length > 1 ? TeamSize.parse(args[1]) : null;
                        if (size == null) { Msg.send(sender, "<red>Usage : /cf setsize <SOLO|DUO|TRIO>"); return true; }
                        if (!plugin.game().isLobby()) { Msg.send(sender, "<red>Impossible en cours de partie."); return true; }
                        plugin.teams().setTeamSize(size);
                        plugin.teams().clear();
                        Msg.broadcast("<yellow>Mode : <gold>" + size.label());
                    }
                    case "setborder" -> {
                        int radius;
                        try { radius = Integer.parseInt(args[1]); } catch (Exception ex) { Msg.send(sender, "<red>Usage : /cf setborder <rayon>"); return true; }
                        plugin.getConfig().set("world.border-radius", radius);
                        plugin.saveConfig();
                        plugin.game().world().getWorldBorder().setSize(radius * 2.0);
                        Msg.send(sender, "<green>Bordure : rayon " + radius + " blocs.");
                    }
                    case "setpvp" -> {
                        int minute;
                        try { minute = Integer.parseInt(args[1]); } catch (Exception ex) { Msg.send(sender, "<red>Usage : /cf setpvp <minute>"); return true; }
                        plugin.getConfig().set("game.pvp-minute", minute);
                        plugin.saveConfig();
                        Msg.send(sender, "<green>PvP a la minute " + minute + ".");
                    }
                    case "ult" -> {
                        // /cf ult [joueur] <pourcent> : regle la jauge d'ultime (test)
                        Player target = sender instanceof Player sp ? sp : null;
                        String value = args.length > 1 ? args[1] : null;
                        if (args.length > 2) { target = Bukkit.getPlayerExact(args[1]); value = args[2]; }
                        if (target == null || value == null) { Msg.send(sender, "<red>Usage : /cf ult [joueur] <pourcent>"); return true; }
                        try {
                            plugin.players().get(target).setUltimateCharge(Double.parseDouble(value));
                            Msg.send(sender, "<green>Ultime de " + target.getName() + " a " + (int) plugin.players().get(target).ultimateCharge() + "%.");
                        } catch (NumberFormatException ex) { Msg.send(sender, "<red>Pourcentage invalide."); }
                    }
                    case "test" -> {
                        if (!(sender instanceof Player sp)) return true;
                        if (plugin.testMode().isTester(sp)) plugin.testMode().leave(sp);
                        else plugin.testMode().enter(sp);
                    }
                    case "ally", "allie" -> {
                        // Mode test : rejoindre l'equipe d'un autre testeur pour essayer les capacites a deux.
                        if (!(sender instanceof Player sp)) return true;
                        if (!plugin.testMode().isTester(sp)) { Msg.send(sp, "<red>Disponible uniquement en mode test."); return true; }
                        Player other = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : null;
                        if (other == null || !plugin.testMode().isTester(other)) { Msg.send(sp, "<red>Usage : /cf ally <testeur>"); return true; }
                        Team t = plugin.teams().teamOf(other);
                        if (t == null) { Msg.send(sp, "<red>Ce joueur n'a pas d'equipe."); return true; }
                        plugin.teams().forceJoin(sp, t);
                        Msg.send(sp, "<green>Tu es maintenant dans l'equipe de " + other.getName() + ".");
                        Msg.send(other, "<green>" + sp.getName() + " rejoint ton equipe.");
                    }
                    case "duo" -> {
                        // Test : remplit les jauges d'Ultime en Duo du joueur (RWBY).
                        if (!(sender instanceof Player sp)) return true;
                        double pct = 100;
                        if (args.length > 1) { try { pct = Double.parseDouble(args[1]); } catch (NumberFormatException ignored) { } }
                        var lic = plugin.characters().licence("rwby");
                        if (lic != null && lic.mechanic() instanceof com.kiokouwhite.crossfight.character.rwby.RWBY.Duo duo) {
                            int n = duo.fill(sp, pct);
                            Msg.send(sp, "<green>Jauges de Duo a " + (int) pct + "% avec " + n + " allie(s).");
                        }
                    }
                    case "credits" -> {
                        if (!(sender instanceof Player sp)) return true;
                        int n = 5000;
                        if (args.length > 1) { try { n = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) { } }
                        var lic = plugin.characters().licence("valorant");
                        if (lic != null && lic.mechanic() instanceof com.kiokouwhite.crossfight.character.valorant.Valorant.Economy eco) {
                            eco.set(sp, n);
                            Msg.send(sp, "<gold>Credits : " + n);
                        }
                    }
                    case "titan" -> {
                        // Test : donne a Eren les pouvoirs d'un titan primordial (ou de tous).
                        if (!(sender instanceof Player sp)) return true;
                        if (!plugin.testMode().isTester(sp)) { Msg.send(sp, "<red>Disponible uniquement en mode test."); return true; }
                        String which = args.length > 1 ? args[1].toLowerCase() : "tous";
                        int n = com.kiokouwhite.crossfight.character.snk.Eren.grantTitan(sp, which);
                        if (n < 0) Msg.send(sp, "<red>Tu dois jouer Eren. Titans : " + String.join(", ", com.kiokouwhite.crossfight.character.snk.Eren.TITAN_IDS) + ", tous");
                        else Msg.send(sp, "<green>" + n + " pouvoir(s) de titan obtenu(s).");
                    }
                    case "clear" -> {
                        int n = plugin.testMode().clearTerrain();
                        Msg.send(sender, "<green>Terrain de test nettoye <gray>(" + n + " blocs remis en place, entites retirees).");
                    }
                    case "change" -> {
                        if (!(sender instanceof Player sp)) return true;
                        if (!plugin.testMode().isTester(sp)) { Msg.send(sp, "<red>Disponible uniquement en mode test (/cf test)."); return true; }
                        if (args.length < 2) { plugin.testMode().openMenu(sp); return true; }
                        Character c = plugin.characters().character(args[1]);
                        if (c == null) { Msg.send(sp, "<red>Personnage inconnu."); return true; }
                        plugin.testMode().change(sp, c);
                    }
                    case "mannequin", "mannequins" -> {
                        if (!(sender instanceof Player sp)) return true;
                        if (args.length > 1 && args[1].equalsIgnoreCase("clear")) {
                            int n = com.kiokouwhite.crossfight.util.Mannequins.clearAll();
                            Msg.send(sp, "<yellow>" + n + " mannequin(s) retire(s).");
                            return true;
                        }
                        if (args.length > 1 && (args[1].equalsIgnoreCase("adversaire") || args[1].equalsIgnoreCase("adversary"))) {
                            Character c = args.length > 2 ? plugin.characters().character(args[2]) : null;
                            if (c == null) { Msg.send(sp, "<red>Usage : /cf mannequin adversaire <perso>"); return true; }
                            var loc = sp.getLocation().add(sp.getLocation().getDirection().setY(0).normalize().multiply(6));
                            loc.setY(sp.getWorld().getHighestBlockYAt(loc) + 1);
                            loc.setYaw(sp.getLocation().getYaw() + 180);
                            com.kiokouwhite.crossfight.util.Mannequins.spawnAdversary(loc, c, sp);
                            int n = 0;
                            for (var a : c.allAbilities()) if (a instanceof com.kiokouwhite.crossfight.ability.EnemyCastable) n++;
                            Msg.send(sp, "<green>Mannequin <red>" + c.name() + "</red> invoque. <gray>" + n + " capacite(s) a lancer contre toi sur la 2e ligne de ton inventaire.");
                            return true;
                        }
                        boolean aggressive = false, ally = false;
                        int count = 1;
                        for (int i = 1; i < args.length; i++) {
                            if (args[i].equalsIgnoreCase("attaque") || args[i].equalsIgnoreCase("attack") || args[i].equalsIgnoreCase("agressif")) aggressive = true;
                            else if (args[i].equalsIgnoreCase("allie") || args[i].equalsIgnoreCase("ally")) ally = true;
                            else { try { count = Math.max(1, Math.min(10, Integer.parseInt(args[i]))); } catch (NumberFormatException ignored) { } }
                        }
                        var base = sp.getLocation().add(sp.getLocation().getDirection().setY(0).normalize().multiply(3));
                        base.setY(sp.getWorld().getHighestBlockYAt(base) + 1);
                        for (int i = 0; i < count; i++) {
                            var loc = base.clone().add((i % 5) * 1.5, 0, (i / 5) * 1.5);
                            loc.setY(sp.getWorld().getHighestBlockYAt(loc) + 1);
                            loc.setYaw(sp.getLocation().getYaw() + 180);
                            com.kiokouwhite.crossfight.util.Mannequins.spawn(loc, aggressive, ally);
                        }
                        Msg.send(sp, "<green>" + count + " mannequin(s)" + (aggressive ? " agressif(s)" : ally ? " allie(s)" : "") + " invoque(s). <gray>/cf mannequin clear pour les retirer.");
                    }
                    case "heal" -> {
                        if (!(sender instanceof Player sp)) return true;
                        var mh = sp.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                        sp.setHealth(mh == null ? 20 : mh.getValue());
                        sp.setFoodLevel(20);
                        Msg.send(sp, "<green>Soigne.");
                    }
                    case "give" -> {
                        if (args.length < 3) { Msg.send(sender, "<red>Usage : /cf give <joueur> <perso>"); return true; }
                        Player target = Bukkit.getPlayerExact(args[1]);
                        Character c = plugin.characters().character(args[2]);
                        if (target == null || c == null) { Msg.send(sender, "<red>Joueur ou personnage inconnu."); return true; }
                        CfPlayer cf = plugin.players().get(target);
                        plugin.abilities().unequip(target);
                        cf.setCharacter(c);
                        if (plugin.game().isRunning()) plugin.abilities().equip(target);
                        Msg.send(sender, "<green>" + target.getName() + " incarne " + c.name() + ".");
                    }
                    default -> help(sender);
                }
            }
        }
        return true;
    }

    private void help(CommandSender s) {
        Msg.raw(s, "<gold><bold>Crossfight UHC</bold> <gray>- commandes");
        Msg.raw(s, "<yellow>/cf status <gray>- etat de la partie");
        Msg.raw(s, "<yellow>/cf licences <gray>- liste des licences et personnages");
        Msg.raw(s, "<yellow>/cf perso [id] <gray>- ouvrir le menu ou choisir un personnage");
        Msg.raw(s, "<yellow>/cf team list|create|join <id>|leave <gray>- gestion des equipes");
        Msg.raw(s, "<yellow>/vivre <gray>- reapparaitre quand tu attends");
        if (s.hasPermission("crossfight.admin")) {
            Msg.raw(s, "<red>/cf start|stop <gray>- lancer ou arreter");
            Msg.raw(s, "<red>/cf setsize <SOLO|DUO|TRIO> <gray>- taille des equipes");
            Msg.raw(s, "<red>/cf setborder <rayon> <gray>- taille de la map");
            Msg.raw(s, "<red>/cf setpvp <minute> <gray>- minute du PvP");
            Msg.raw(s, "<red>/cf team add <joueur> <id> <gray>- forcer un joueur dans une equipe");
            Msg.raw(s, "<red>/cf give <joueur> <perso> <gray>- forcer un personnage");
            Msg.raw(s, "<red>/cf ult [joueur] <pourcent> <gray>- regler la jauge d'ultime (test)");
            Msg.raw(s, "<red>/cf heal <gray>- se soigner (test)");
            Msg.raw(s, "<red>/cf mannequin [nb] [attaque|allie|clear] <gray>- mannequins d'entrainement (test)");
            Msg.raw(s, "<red>/cf mannequin adversaire <perso> <gray>- un mannequin qui lance ses capacites contre toi");
            Msg.raw(s, "<red>/cf test <gray>- entrer ou sortir du mode test");
            Msg.raw(s, "<red>/cf change [perso] <gray>- changer de personnage en mode test");
            Msg.raw(s, "<red>/cf clear <gray>- remettre le terrain de test a neuf");
            Msg.raw(s, "<red>/cf ally <testeur> <gray>- rejoindre l'equipe d'un autre testeur (mode test)");
            Msg.raw(s, "<red>/cf duo [pourcent] <gray>- remplir les jauges d'Ultime en Duo (test)");
            Msg.raw(s, "<red>/cf credits [n] <gray>- regler ses credits Valorant (test)");
        }
    }

    private void status(CommandSender s) {
        var g = plugin.game();
        Msg.raw(s, "<gold>Etat : <white>" + g.state() + " <gray>| Temps : <white>" + Msg.time(g.elapsedSeconds())
                + " <gray>| PvP : <white>" + (g.isPvpEnabled() ? "oui" : "non"));
        Msg.raw(s, "<gold>Mode : <white>" + plugin.teams().teamSize().label() + " <gray>| Equipes : <white>"
                + plugin.teams().all().size() + " <gray>(en vie : " + plugin.teams().alive().size() + ")");
    }

    private void licences(CommandSender s) {
        for (Licence l : plugin.characters().licences()) {
            StringBuilder sb = new StringBuilder("<gold>" + l.name() + " <gray>[" + l.teamSize().label() + "] : ");
            if (l.characters().isEmpty()) sb.append("<dark_gray>aucun personnage");
            for (Character c : l.characters()) sb.append("<white>").append(c.name()).append(" <dark_gray>(").append(c.id()).append(") ");
            Msg.raw(s, sb.toString());
        }
    }

    private void character(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) { Msg.send(s, "<red>Commande reservee aux joueurs."); return; }
        if (!plugin.game().isSelecting()) { Msg.send(p, "<red>Le choix du personnage n'est pas ouvert."); return; }
        if (args.length < 2) { plugin.characters().selectMenu(plugin).open(p); return; }
        Character c = plugin.characters().character(args[1]);
        if (c == null || !plugin.characters().selectMenu(plugin).choose(p, c)) {
            Msg.send(p, "<red>Personnage inconnu ou indisponible.");
        }
    }

    private void team(CommandSender s, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase() : "list";
        switch (action) {
            case "list" -> {
                if (plugin.teams().all().isEmpty()) { Msg.send(s, "<gray>Aucune equipe. Elles seront creees au lancement."); return; }
                for (Team t : plugin.teams().all()) {
                    StringBuilder sb = new StringBuilder(t.coloredName() + " <gray>(" + t.size() + "/" + plugin.teams().teamSize().size() + ") : ");
                    for (var u : t.members()) {
                        CfPlayer cf = plugin.players().get(u);
                        sb.append("<white>").append(cf == null ? u.toString().substring(0, 6) : cf.name()).append(" ");
                    }
                    if (t.licence() != null) sb.append("<gray>- <gold>").append(t.licence().name());
                    Msg.raw(s, sb.toString());
                }
            }
            case "create" -> {
                if (!(s instanceof Player p)) return;
                if (!plugin.game().isLobby()) { Msg.send(p, "<red>Impossible en cours de partie."); return; }
                Team t = plugin.teams().create();
                plugin.teams().join(p, t);
                Msg.send(p, "<green>Tu as cree " + t.coloredName() + "<green>. Les autres rejoignent avec <white>/cf team join " + t.id());
            }
            case "join" -> {
                if (!(s instanceof Player p)) return;
                if (!plugin.game().isLobby()) { Msg.send(p, "<red>Impossible en cours de partie."); return; }
                Team t = null;
                try { t = plugin.teams().get(Integer.parseInt(args[2])); } catch (Exception ignored) { }
                if (t == null) { Msg.send(p, "<red>Equipe introuvable."); return; }
                if (plugin.teams().join(p, t)) Msg.send(p, "<green>Tu as rejoint " + t.coloredName());
                else Msg.send(p, "<red>Cette equipe est pleine.");
            }
            case "leave" -> {
                if (!(s instanceof Player p)) return;
                if (!plugin.game().isLobby()) { Msg.send(p, "<red>Impossible en cours de partie."); return; }
                plugin.teams().leave(p);
                Msg.send(p, "<yellow>Tu as quitte ton equipe.");
            }
            case "add" -> {
                if (!s.hasPermission("crossfight.admin")) { Msg.send(s, "<red>Permission refusee."); return; }
                if (args.length < 4) { Msg.send(s, "<red>Usage : /cf team add <joueur> <id>"); return; }
                Player target = Bukkit.getPlayerExact(args[2]);
                Team t = null;
                try { t = plugin.teams().get(Integer.parseInt(args[3])); } catch (Exception ignored) { }
                if (target == null || t == null) { Msg.send(s, "<red>Joueur ou equipe introuvable."); return; }
                if (plugin.teams().join(target, t)) Msg.send(s, "<green>" + target.getName() + " ajoute a " + t.coloredName());
                else Msg.send(s, "<red>Equipe pleine.");
            }
            default -> Msg.send(s, "<red>Usage : /cf team list|create|join <id>|leave");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("status", "licences", "perso", "team"));
            if (sender.hasPermission("crossfight.admin")) subs.addAll(List.of("start", "stop", "setsize", "setborder", "setpvp", "give", "ult", "heal", "mannequin", "test", "change", "clear", "ally", "duo", "credits", "titan"));
            for (String sub : subs) if (sub.startsWith(args[0].toLowerCase())) out.add(sub);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "setsize" -> { for (TeamSize t : TeamSize.values()) out.add(t.name()); }
                case "team" -> out.addAll(List.of("list", "create", "join", "leave", "add"));
                case "perso" -> { for (Licence l : plugin.characters().licences()) for (Character c : l.characters()) out.add(c.id()); }
                case "give" -> { for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName()); }
                case "mannequin" -> out.addAll(List.of("1", "3", "5", "attaque", "allie", "adversaire", "clear"));
                case "titan" -> { out.addAll(List.of(com.kiokouwhite.crossfight.character.snk.Eren.TITAN_IDS)); out.add("tous"); }
                case "change" -> { for (Licence l : plugin.characters().licences()) for (Character c : l.characters()) out.add(c.id()); }
                default -> { }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("mannequin") && args[1].equalsIgnoreCase("adversaire")) {
            for (Licence l : plugin.characters().licences()) for (Character c : l.characters()) out.add(c.id());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (Licence l : plugin.characters().licences()) for (Character c : l.characters()) out.add(c.id());
        }
        return out;
    }
}
