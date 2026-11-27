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
        // /cf tp quete <lieu> : la meme chose que /cf quete tp <lieu>
        if (args.length >= 2 && args[0].equalsIgnoreCase("tp") && (args[1].equalsIgnoreCase("quete") || args[1].equalsIgnoreCase("quetes")))
            args = args.length > 2 ? new String[]{"quete", "tp", args[2]} : new String[]{"quete", "tp"};
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "status" -> status(sender);
            case "licences", "licenses" -> licences(sender);
            case "perso", "character" -> character(sender, args);
            case "team" -> team(sender, args);
            case "map", "carte" -> {
                // La carte de la map, dans la main : les lieux, l'arene, la bordure, soi et son equipe.
                if (sender instanceof Player sp) plugin.worldMap().give(sp);
            }
            case "ferox" -> {
                // /cf ferox oui|non : repondre a la proposition de Regna Ferox ; /cf ferox lancer : l'ouvrir tout de suite (admin).
                if (!(sender instanceof Player sp)) return true;
                String what = args.length > 1 ? args[1].toLowerCase() : "";
                if (what.equals("lancer")) {
                    if (!sender.hasPermission("crossfight.admin") && !isHost(sender)) { Msg.send(sender, "<red>Tu n'as pas la permission."); return true; }
                    if (plugin.game().state() != com.kiokouwhite.crossfight.game.GameState.RUNNING) { Msg.send(sender, "<red>Seulement pendant une partie."); return true; }
                    plugin.ferox().ask();
                } else if (what.equals("oui") || what.equals("non")) plugin.ferox().answer(sp, what.equals("oui"));
                else Msg.send(sp, "<yellow>/cf ferox oui|non");
            }
            case "demo" -> {
                // /cf demo contre|falchion|embleme (admin) : un adversaire au skin aleatoire, pour filmer une capacite (game/DemoAdversaire) :
                // contre, il frappe des que Marth lance son Contre ; falchion, il attaque des qu'on le frappe et s'arrete a 5 coeurs.
                // La meme commande le retire.
                if (!(sender instanceof Player sp)) return true;
                if (!sender.hasPermission("crossfight.admin")) { Msg.send(sender, "<red>Tu n'as pas la permission."); return true; }
                String what = args.length > 1 ? args[1].toLowerCase() : "";
                var cfp = plugin.players().get(sp);
                // /cf demo embleme [tier] : une Lootbox devant soi, l'Embleme de Feu dedans a coup sur ; un Marth qui a
                // deja la Sainte-Falchion retrouve sa Falchion, pour rejouer la transformation.
                if (List.of("embleme", "emblem", "fire-emblem", "fireemblem", "fire_emblem").contains(what)) {
                    int tier = 0;
                    try { if (args.length > 2) tier = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) { }
                    boolean marth = cfp != null && cfp.character() != null && cfp.character().id().equals("marth");
                    boolean reprise = marth && com.kiokouwhite.crossfight.character.fireemblem.Marth.isHoly(sp);
                    if (reprise) com.kiokouwhite.crossfight.character.fireemblem.Marth.downgrade(sp);
                    var at = sp.getLocation().add(sp.getLocation().getDirection().setY(0).normalize().multiply(2));
                    if (!plugin.lootbox().place(at, tier, true)) { Msg.send(sp, "<red>Impossible de poser une lootbox ici."); return true; }
                    Msg.send(sp, "<green>Une Lootbox avec l'Embleme de Feu est posee devant toi : ouvre-la, prends l'Embleme"
                            + (marth ? " et utilise-le." : ". <gray>(Seul Marth peut l'utiliser : /cf change marth.)")
                            + (reprise ? " <gray>Ta Falchion est redevenue la Falchion." : ""));
                    return true;
                }
                if (!com.kiokouwhite.crossfight.game.DemoAdversaire.MODES.contains(what)) { Msg.send(sp, "<yellow>/cf demo contre|falchion|embleme"); return true; }
                if (what.equals("contre") && (cfp == null || cfp.character() == null || !cfp.character().id().equals("marth"))) {
                    Msg.send(sp, "<red>Incarne Marth d'abord (en mode test : /cf change marth).");
                    return true;
                }
                if (com.kiokouwhite.crossfight.game.DemoAdversaire.toggle(sp, what))
                    Msg.send(sp, what.equals("contre")
                            ? "<green>Un adversaire t'attend : lance le Contre, il frappera aussitot. <gray>/cf demo contre pour le retirer."
                            : "<green>Un adversaire t'attend : frappe-le et il t'attaquera jusqu'a ce que tu n'aies plus que 5 coeurs. <gray>/cf demo falchion pour le retirer.");
                else Msg.send(sp, "<gray>Adversaire de demonstration retire.");
            }
            case "hud" -> {
                // /cf hud : le menu des reglages d'affichage (passifs, actif, effets des autres, leurs textes).
                // /cf hud demo (admin) : montre les cartes de droite (allie, adversaire) et l'actif de gauche, pour essai.
                if (!(sender instanceof Player sp)) return true;
                if (args.length > 1 && args[1].equalsIgnoreCase("demo")) {
                    if (!sender.hasPermission("crossfight.admin")) { Msg.send(sender, "<red>Tu n'as pas la permission."); return true; }
                    com.kiokouwhite.crossfight.hud.InfluenceHud.demo(sp);
                    Msg.send(sp, "<gray>HUD : demonstration lancee (allie, adversaire, actif).");
                    return true;
                }
                com.kiokouwhite.crossfight.hud.HudPrefs.open(sp);
            }
            case "passif", "passifs" -> {
                // /cf passif show|hide : le texte qui rappelle ce que fait chaque passif, a cote de son icone (en haut a gauche).
                if (!(sender instanceof Player sp)) return true;
                String what = args.length > 1 ? args[1].toLowerCase() : "";
                boolean on = what.equals("show") ? true : what.equals("hide") ? false : !com.kiokouwhite.crossfight.hud.PassiveHud.showsText(sp);
                com.kiokouwhite.crossfight.hud.PassiveHud.setShowsText(sp, on);
                Msg.send(sp, on ? "<green>Passifs : texte affiche a cote des icones. <gray>/cf passif hide pour le masquer."
                                : "<gray>Passifs : texte masque. /cf passif show pour l'afficher.");
            }
            case "scoreboard", "tableau" -> {
                // /cf scoreboard show|hide : afficher ou cacher le tableau de droite (pour soi ; garde d'une partie a l'autre).
                if (!(sender instanceof Player sp)) return true;
                String what = args.length > 1 ? args[1].toLowerCase() : "";
                var pref = com.kiokouwhite.crossfight.hud.HudPrefs.Pref.TABLEAU;
                boolean on = what.equals("show") ? true : what.equals("hide") ? false : !pref.of(sp);
                pref.set(sp, on);
                plugin.scoreboard().refresh(sp);
                Msg.send(sp, on ? "<green>Tableau affiche. <gray>/cf scoreboard hide pour le cacher."
                                : "<gray>Tableau cache. /cf scoreboard show pour le remettre.");
            }
            case "choose" -> {
                // John : choisir ses pouvoirs absorbes.
                if (!(sender instanceof Player sp)) return true;
                if (!com.kiokouwhite.crossfight.character.unordinary.John.openChoose(sp)) Msg.send(sp, "<red>Seul John peut choisir ses pouvoirs absorbes.");
            }
            case "pack" -> {
                // Redemande le pack de sons (ecran de confirmation ferme trop vite, pack refuse...).
                if (!(sender instanceof Player sp)) return true;
                com.kiokouwhite.crossfight.util.Sounds.sendPack(sp);
                Msg.send(sp, "<gray>Pack de sons propose a nouveau. S'il ne s'affiche pas, regle \"Packs de ressources du serveur\" sur Demander dans la liste des serveurs.");
            }
            default -> {
                // Un administrateur de partie (/cf admin) a les commandes qui reglent, lancent et arretent la partie ;
                // le reste (mode test, triche de test) demande d'etre operateur.
                boolean op = sender.hasPermission("crossfight.admin");
                // /cf scenario, sans rien : la liste, pour tout le monde.
                if ((sub.equals("scenario") || sub.equals("scenarios")) && args.length < 2) {
                    Msg.raw(sender, "<light_purple><bold>Scenarios</bold> <gray>(/cf scenario <nom> [oui|non])");
                    for (var s : com.kiokouwhite.crossfight.game.Scenarios.Scenario.values()) {
                        boolean on = plugin.scenarios().on(s);
                        Msg.raw(sender, (on ? "<green>✔ " : "<dark_gray>✘ ") + (on ? "<white>" : "<gray>") + s.label() + " <dark_gray>- <gray>" + s.help());
                    }
                    return true;
                }
                if (!op && !(isHost(sender) && HOST_COMMANDS.contains(sub))) {
                    Msg.send(sender, isHost(sender) ? "<red>Cette commande est reservee aux operateurs du serveur." : "<red>Tu n'as pas la permission.");
                    return true;
                }
                switch (sub) {
                    case "admin", "admins" -> {
                        // /cf admin <pseudo> : confie (ou retire) a un joueur le droit de regler et de lancer la partie.
                        if (!op) { Msg.send(sender, "<red>Seul un operateur du serveur nomme les administrateurs de partie."); return true; }
                        var rules = plugin.game().rules();
                        if (args.length < 2) {
                            var hosts = rules.hosts();
                            Msg.send(sender, hosts.isEmpty() ? "<gray>Aucun administrateur de partie. <white>/cf admin <pseudo></white> pour en nommer un."
                                    : "<gold>Administrateurs de partie : <white>" + String.join(", ", hosts.values()) + " <gray>(/cf admin <pseudo> pour ajouter ou retirer)");
                            return true;
                        }
                        Player target = Bukkit.getPlayerExact(args[1]);
                        java.util.UUID id = target != null ? target.getUniqueId() : null;
                        String name = target != null ? target.getName() : args[1];
                        // Un administrateur deconnecte se retire par son nom.
                        if (id == null) for (var en : rules.hosts().entrySet()) if (en.getValue().equalsIgnoreCase(args[1])) { id = en.getKey(); name = en.getValue(); }
                        if (id == null) { Msg.send(sender, "<red>" + args[1] + " n'est pas connecte."); return true; }
                        boolean now = !rules.isHost(id);
                        rules.setHost(id, name, now);
                        Msg.send(sender, now ? "<green>" + name + " est administrateur de partie : il a les outils de reglage en main dans le lobby (ou <white>/cf regle</white>), et peut lancer et arreter la partie."
                                : "<yellow>" + name + " n'est plus administrateur de partie.");
                        if (target != null && target != sender) Msg.send(target, now ? "<green>Tu es administrateur de partie. <gray>Dans le lobby, tes outils sont dans ta barre : regles, equipes, nouvelle map, rappel, lancement. Les commandes marchent aussi (<white>/cf regle</white>, <white>/cf start</white>, <white>/cf stop</white>...)."
                                : "<yellow>Tu n'es plus administrateur de partie.");
                        if (target != null) plugin.hostTools().refresh(target);
                    }
                    case "scenario", "scenarios" -> {
                        // /cf scenario <nom> [oui|non] : coche ou decoche un scenario.
                        var s = com.kiokouwhite.crossfight.game.Scenarios.Scenario.parse(args[1]);
                        if (s == null) { Msg.send(sender, "<red>Scenario inconnu : " + args[1] + ". <gray>/cf scenario pour la liste."); return true; }
                        boolean on = args.length < 3 ? !plugin.scenarios().on(s) : List.of("oui", "on", "true", "1", "vrai").contains(args[2].toLowerCase(java.util.Locale.ROOT));
                        plugin.scenarios().set(s, on);
                        Msg.broadcast("<light_purple>Scenario <white>" + s.label() + (on ? " <green>active" : " <red>desactive") + " <gray>(" + sender.getName() + ")");
                    }
                    case "desactiver", "desactive", "activer", "active" -> {
                        // /cf desactiver <personnage|licence|solo|duo|trio> (et /cf activer) ; sans rien : ce qui est desactive.
                        var roster = plugin.roster();
                        if (args.length < 2) { Msg.send(sender, roster.summary()); return true; }
                        boolean on = sub.startsWith("activ");
                        String id = args[1];
                        TeamSize kind = TeamSize.parse(id);
                        var licence = plugin.characters().licence(id.toLowerCase(java.util.Locale.ROOT));
                        var character = plugin.characters().character(id);
                        String what;
                        if (kind != null) { plugin.hostTools().setFormat(kind, on); what = "format " + kind.label(); }
                        else if (licence != null) { roster.set(licence, on); what = "licence " + licence.name(); }
                        else if (character != null) { roster.set(character, on); what = character.name(); }
                        else { Msg.send(sender, "<red>Ni un personnage, ni une licence, ni un format (solo, duo, trio) : " + id); return true; }
                        plugin.hostTools().refreshTeamMenus();
                        Msg.broadcast("<aqua>" + what + (on ? " <green>active" : " <red>desactive") + " <gray>(" + sender.getName() + ")");
                    }
                    case "pearl" -> {
                        // Raccourci : /cf pearl glowing = /cf quete montrer pearl
                        if (sender instanceof Player) return onCommand(sender, cmd, label, new String[]{"quete", "montrer", "pearl"});
                    }
                    case "enigme", "enigmes", "circuit", "etabli", "puzzle", "briques" -> {
                        // Essayer une enigme des piliers de Central Town, sans la quete : /cf enigme <tablette|circuit|etabli|puzzle>
                        if (!(sender instanceof Player sp)) return true;
                        String kind = args[0].toLowerCase().startsWith("enigme") ? (args.length > 1 ? args[1].toLowerCase() : "") : args[0].toLowerCase();
                        if (!kind.equals("tablette") && !kind.equals("briques") && !com.kiokouwhite.crossfight.game.Puzzles.KINDS.contains(kind)) { Msg.send(sender, "<yellow>/cf enigme <tablette|circuit|etabli|puzzle|briques>"); return true; }
                        plugin.quests().tryPuzzle(sp, kind);
                        return true;
                    }
                    case "tablette" -> {
                        // Essayer le casse-tete des piliers de Central Town, sans la quete
                        if (sender instanceof Player sp) plugin.quests().tryTablet(sp);
                        return true;
                    }
                    case "roulette" -> {
                        // Essayer la roulette d'Anna (Arcadia), sans la quete
                        if (sender instanceof Player sp) plugin.places().openWheel(sp);
                        return true;
                    }
                    case "qg" -> {
                        // Le quartier general : /cf qg (parler a Anna), prendre (sans payer), liberer, voir <joueur> (sa fiche)
                        if (!(sender instanceof Player sp)) return true;
                        var hq = plugin.headquarters();
                        if (args.length < 2) { hq.talk(sp); return true; }
                        switch (args[1].toLowerCase()) {
                            case "prendre" -> hq.grant(sp);
                            case "liberer" -> { hq.reset(); Msg.send(sender, "<gray>Le quartier général est libre."); }
                            case "voir" -> {
                                Player t = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                                if (t == null) Msg.send(sender, "<red>Usage : /cf qg voir <joueur>"); else hq.open(sp, t);
                            }
                            default -> Msg.send(sender, "<yellow>/cf qg [prendre|liberer|voir <joueur>]");
                        }
                        return true;
                    }
                    case "marchand" -> {
                        // Essayer le marchand de Central Town : /cf marchand (son etalage), /cf marchand nouveau (un nouvel etalage tout de suite)
                        if (args.length > 1 && args[1].equalsIgnoreCase("nouveau")) { plugin.places().restock(); Msg.send(sender, "<gray>Nouvel étalage."); }
                        if (sender instanceof Player sp) plugin.places().openShop(sp);
                        return true;
                    }
                    case "evenement", "evenements" -> {
                        // L'evenement des lieux : /cf evenement (voir), tirer, arreter, ou <lieu renforce> <lieu affaibli>
                        var ev = plugin.placeEvents();
                        if (args.length < 2) { Msg.send(sender, ev.status()); return true; }
                        if (args[1].equalsIgnoreCase("tirer")) { if (!ev.roll()) Msg.send(sender, "<red>Il faut au moins deux lieux posés."); return true; }
                        if (args[1].equalsIgnoreCase("arreter")) { ev.clear(); Msg.send(sender, "<gray>Plus aucun lieu renforcé ni affaibli."); return true; }
                        java.util.List<String> known = ev.places();
                        String up = args[1].equals("-") ? null : args[1], down = args.length > 2 && !args[2].equals("-") ? args[2] : null;
                        if (up != null && !known.contains(up) || down != null && !known.contains(down)) { Msg.send(sender, "<yellow>Lieux posés : <white>" + String.join(", ", known) + " <gray>(- pour aucun)"); return true; }
                        ev.set(up, down);
                        return true;
                    }
                    case "rythme", "concert", "forge" -> {
                        // Essayer le concert d'Anna : /cf rythme (le menu) ou /cf rythme <musique> (la jouer tout de suite)
                        if (!(sender instanceof Player sp)) return true;
                        if (args.length < 2) { plugin.rhythm().open(sp); return true; }
                        if (args[1].equalsIgnoreCase("resultat")) {
                            int percent = 92;
                            try { if (args.length > 2) percent = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) { }
                            plugin.rhythm().preview(sp, percent);
                            return true;
                        }
                        if (args[1].equalsIgnoreCase("recharger")) { Msg.send(sender, "<green>Musiques importées : <white>" + plugin.rhythm().reload()); return true; }
                        var song = plugin.rhythm().find(args[1]);
                        if (song != null) { plugin.rhythm().start(sp, song); return true; }
                        Msg.send(sender, "<yellow>Musiques : <white>" + String.join(", ", plugin.rhythm().ids()) + " <gray>(ou recharger)");
                        return true;
                    }
                    case "quete", "quetes", "quest" -> {
                        // Essayer les quetes des lieux : /cf quete [liste | tp | lancer | finir | parler | oublier] [lieu]
                        if (!(sender instanceof Player sp)) return true;
                        var qs = plugin.quests();
                        String what = args.length > 1 ? args[1].toLowerCase() : "liste";
                        String place = args.length > 2 ? args[2].toLowerCase() : null;
                        if (place == null) { var here = plugin.structures().at(sp.getLocation()); if (here != null) place = here.name(); }
                        if (what.equals("liste")) {
                            Msg.send(sender, "<gold>Quetes des lieux <gray>(/cf quete tp|lancer|finir|parler|oublier <lieu>)");
                            for (var pl : plugin.structures().finished(plugin.game().world())) {
                                var q = com.kiokouwhite.crossfight.game.Quests.QUESTS.get(pl.name());
                                if (q != null) Msg.raw(sender, "<white>" + pl.name() + " <gray>: <gold>" + q.title() + " <gray>- " + qs.state(sp, pl.name()));
                            }
                            return true;
                        }
                        if (what.equals("placer") || what.equals("replacer")) {
                            // Anna vient la ou se tient l'admin (ou retourne a sa place d'origine).
                            // Avec des coordonnees (x y z, puis le sens du regard) : a cet endroit de la map plutot qu'aux pieds de l'admin.
                            String moved;
                            if (what.equals("placer") && args.length >= 5) {
                                try {
                                    moved = plugin.places().moveGuide(new org.bukkit.Location(plugin.game().world(), Double.parseDouble(args[2]), Double.parseDouble(args[3]), Double.parseDouble(args[4]),
                                            args.length >= 6 ? Float.parseFloat(args[5]) : sp.getLocation().getYaw(), 0f), true);
                                } catch (NumberFormatException ex) { Msg.send(sender, "<yellow>/cf quete placer [x y z [regard]]"); return true; }
                            } else moved = plugin.places().moveGuide(sp, what.equals("placer"));
                            Msg.send(sender, moved == null ? "<red>Cet endroit n'est dans aucun lieu." : what.equals("placer") ? "<green>Anna se tiendra ici pour <white>" + moved + "<green>, sur cette map et les suivantes. <gray>/cf quete replacer pour annuler." : "<green>Anna retourne a sa place d'origine pour <white>" + moved + "<green>.");
                            return true;
                        }
                        if (what.equals("oublier") && place == null) { qs.forget(sp, null); Msg.send(sender, "<green>Toutes tes quetes sont oubliees."); return true; }
                        if (place == null || !com.kiokouwhite.crossfight.game.Quests.QUESTS.containsKey(place)) { Msg.send(sender, "<yellow>/cf quete " + what + " <lieu> <gray>(ou place-toi dans un lieu)"); return true; }
                        org.bukkit.Location npc = plugin.places().guideOf(plugin.game().world(), place);
                        switch (what) {
                            case "tp" -> { if (npc == null) Msg.send(sender, "<red>Ce lieu n'est pas pose sur cette map."); else { sp.teleport(facing(npc)); Msg.send(sender, "<green>Te voila devant le personnage de <white>" + place + "<green> : clic droit sur lui."); } }
                            case "lancer" -> { qs.forget(sp, place); Msg.send(sender, npc != null && qs.start(sp, place, npc) ? "<green>Quete lancee." : "<red>Ce lieu n'est pas pose sur cette map."); }
                            case "finir" -> { qs.force(sp, place); }
                            case "parler" -> { qs.talk(sp, place, npc); }
                            case "montrer", "glowing", "briller" -> {
                                Msg.send(sender, "<gold>Ce qu'il reste a trouver a <white>" + place + " <gray>(colonnes de lumiere pendant une minute) :");
                                int n = qs.reveal(sp, place);
                                if (n < 0) Msg.send(sender, "<red>Cette quete n'est pas en cours : parle au personnage, ou /cf quete lancer " + place + ".");
                                else if (n == 0) Msg.send(sender, "<gray>Rien a montrer pour cette quete.");
                            }
                            case "oublier" -> { qs.forget(sp, place); Msg.send(sender, "<green>Quete oubliee : <white>" + place); }
                            default -> Msg.send(sender, "<yellow>/cf quete liste | tp | lancer | montrer | finir | oublier [lieu]");
                        }
                    }
                    case "structure", "structures", "lieu", "lieux" -> {
                        // Les lieux poses au hasard sur la map : les lister, visiter leur monde d'origine, ecrire leur fiche, en poser.
                        var st = plugin.structures();
                        String what = args.length > 1 ? args[1].toLowerCase() : "liste";
                        switch (what) {
                            case "liste" -> {
                                var sheets = st.sheets();
                                int share = st.share(plugin.game().world());
                                Msg.send(sender, "<gold>" + sheets.size() + " lieu(x) dans plugins/CrossfightUHC/structures/ <gray>; avec cette bordure, chacun occupe au plus <white>" + share + " x " + share + "</white> blocs" + (st.working() ? " <yellow>(une pose est en cours)" : ""));
                                String advice = st.advice(plugin.game().world());
                                if (advice != null) Msg.send(sender, "<yellow>Cette map n'a aucun lieu : <gray>" + advice);
                                for (var s : sheets) {
                                    String no = st.unusable(s);
                                    String where = no != null ? "<red>monde inutilisable, " + no : "<gray>pas encore pose";
                                    for (var p : st.placed(plugin.game().world())) if (p.name().equals(s.name())) where = "<green>en " + (p.x() + p.sx() / 2) + " " + (p.z() + p.sz() / 2) + " (" + p.sx() + " x " + p.sz() + ")";
                                    Msg.send(sender, "<white>" + s.name() + " <gray>" + s.sx() + " x " + s.sy() + " x " + s.sz() + (s.author().isEmpty() ? "" : ", par " + s.author()) + " : " + where);
                                }
                                Msg.send(sender, "<gray>/cf structure monde <nom>, fiche <nom> <x1> <y1> <z1> <x2> <y2> <z2> <sol> [creuse], placer <nom> [x z] [cote], disperser");
                            }
                            case "monde" -> {
                                // Charge le monde d'origine d'un lieu (structures/<nom>/monde) et y emmene, en spectateur : pour reperer sa boite.
                                if (!(sender instanceof Player sp) || args.length < 3) { Msg.send(sender, "<yellow>/cf structure monde <nom>"); return true; }
                                String name = args[2].toLowerCase();
                                java.io.File dir = new java.io.File(st.home(name), "monde");
                                if (!name.matches("[a-z0-9_]+") || !new java.io.File(dir, "level.dat").isFile()) { Msg.send(sender, "<red>Aucun monde dans plugins/CrossfightUHC/structures/" + name + "/monde."); return true; }
                                String no = st.refusal(dir);
                                if (no != null) { Msg.send(sender, "<red>Ce monde n'est pas charge : " + no + "."); return true; }
                                var sheet = st.sheet(name);
                                org.bukkit.World w;
                                if (sheet != null) w = st.source(sheet);
                                else {
                                    String rel = Bukkit.getWorldContainer().getAbsoluteFile().toPath().normalize().relativize(dir.getAbsoluteFile().toPath().normalize()).toString().replace('\\', '/');
                                    w = Bukkit.getWorld(rel);
                                    if (w == null) w = new org.bukkit.WorldCreator(rel).generator(new com.kiokouwhite.crossfight.util.ArenaWorlds.VoidGenerator()).createWorld();
                                }
                                if (w == null) { Msg.send(sender, "<red>Ce monde n'a pas pu etre charge."); return true; }
                                sp.setGameMode(org.bukkit.GameMode.SPECTATOR);
                                sp.teleport(sheet != null ? new org.bukkit.Location(w, sheet.heartX() + 0.5, sheet.ground() + 20, sheet.heartZ() + 0.5) : w.getSpawnLocation());
                                Msg.send(sender, "<green>Monde de " + name + " charge : tu y es, en spectateur" + (sheet != null ? ", au-dessus de son coeur." : ", a son point d'apparition."));
                            }
                            case "fiche" -> {
                                // Ecrit la fiche d'un lieu : sa boite et la hauteur de son sol, dans son monde ; son coeur est la ou l'on se tient.
                                if (!(sender instanceof Player sp)) return true;
                                if (args.length < 10) { Msg.send(sender, "<yellow>/cf structure fiche <nom> <x1> <y1> <z1> <x2> <y2> <z2> <sol> [creuse]"); return true; }
                                try {
                                    int[] v = new int[7];
                                    for (int i = 0; i < 7; i++) v[i] = Integer.parseInt(args[3 + i]);
                                    boolean carve = args.length > 10 && args[10].equalsIgnoreCase("creuse");
                                    boolean ok = st.define(args[2].toLowerCase(), v[0], v[1], v[2], v[3], v[4], v[5], v[6], sp.getLocation().getBlockX(), sp.getLocation().getBlockZ(), carve, "");
                                    Msg.send(sender, ok ? "<green>Fiche de " + args[2].toLowerCase() + " ecrite (coeur : ta position)." : "<red>Aucun monde dans plugins/CrossfightUHC/structures/" + args[2].toLowerCase() + "/monde.");
                                } catch (NumberFormatException ex) { Msg.send(sender, "<red>Les coordonnees et le niveau du sol sont des nombres entiers."); }
                            }
                            case "placer" -> {
                                var s = args.length > 2 ? st.sheet(args[2].toLowerCase()) : null;
                                if (s == null) { Msg.send(sender, "<red>Lieu inconnu. <gray>/cf structure liste"); return true; }
                                try {
                                    org.bukkit.World w = sender instanceof Player sp && sp.getWorld().equals(plugin.game().world()) ? sp.getWorld() : plugin.game().world();
                                    int x = args.length > 4 ? Integer.parseInt(args[3]) : sender instanceof Player sp ? sp.getLocation().getBlockX() : 0;
                                    int z = args.length > 4 ? Integer.parseInt(args[4]) : sender instanceof Player sp ? sp.getLocation().getBlockZ() : 0;
                                    int side = args.length > 5 ? Integer.parseInt(args[5]) : st.share(w);
                                    st.place(sender, s, w, x, z, side, null);
                                } catch (NumberFormatException ex) { Msg.send(sender, "<red>x, z et le cote sont des nombres entiers."); }
                            }
                            case "barrieres", "barriere", "barrier" -> st.unbar(sender, plugin.game().world());
                            case "disperser" -> {
                                if (st.working()) { Msg.send(sender, "<red>Une pose est deja en cours."); return true; }
                                if (st.sheets().isEmpty()) { Msg.send(sender, "<red>Rien a poser : <gray>" + st.advice(plugin.game().world())); return true; }
                                st.retry();
                                st.scatter(plugin.game().world());
                                Msg.send(sender, st.working() ? "<yellow>Les lieux qui manquent a cette map sont poses l'un apres l'autre (suivi dans la console, et sur /cf map)."
                                        : "<gray>Aucun lieu a poser : ils sont tous sur cette map, ou leur monde est inutilisable (/cf structure liste).");
                            }
                            default -> Msg.send(sender, "<yellow>/cf structure liste|monde|fiche|placer|disperser");
                        }
                    }
                    case "omori" -> {
                        // /cf omori lancer|fermer|entrer|phase <n>|coups <n> : essayer l'evenement Omori (test).
                        String what = args.length > 1 ? args[1].toLowerCase() : "";
                        int n = 1;
                        try { if (args.length > 2) n = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) { }
                        switch (what) {
                            case "lancer" -> { if (plugin.game().isRunning()) plugin.omori().open(); else Msg.send(sender, "<red>Seulement pendant une partie ou un mode test."); }
                            case "fermer" -> { plugin.omori().close(); plugin.ferox().gates(false); Msg.send(sender, "<yellow>Evenement Omori referme."); }
                            case "phase" -> plugin.omori().setPhase(n - 1);
                            case "coups" -> plugin.omori().hit(n);
                            default -> Msg.send(sender, "<yellow>/cf omori lancer|fermer|phase <1-5>|coups <n>");
                        }
                    }
                    case "lootbox" -> {
                        // /cf lootbox [tier] : une lootbox devant soi (test) ; /cf lootbox vague : l'evenement entier.
                        if (!(sender instanceof Player p)) return true;
                        if (args.length > 1 && args[1].equalsIgnoreCase("vague")) { plugin.lootbox().wave(); return true; }
                        int tier = 0;
                        try { if (args.length > 1) tier = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) { }
                        var at = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(2));
                        Msg.send(p, plugin.lootbox().place(at, tier) ? "<green>Lootbox posee devant toi." : "<red>Impossible de poser une lootbox ici.");
                    }
                    case "loot" -> {
                        // /cf loot <objet|tous> : recevoir un objet de lootbox (test).
                        if (!(sender instanceof Player p)) return true;
                        if (args.length < 2) { Msg.send(p, "<yellow>/cf loot <objet|tous|embleme> : " + String.join(", ", com.kiokouwhite.crossfight.game.Lootbox.ids())); return true; }
                        for (String id : args[1].equalsIgnoreCase("tous") ? com.kiokouwhite.crossfight.game.Lootbox.ids() : List.of(args[1])) {
                            // L'Embleme de Feu de Marth (/cf loot embleme).
                            org.bukkit.inventory.ItemStack it = id.equalsIgnoreCase("embleme") ? plugin.lootbox().embleme() : plugin.lootbox().item(id);
                            if (it == null) { Msg.send(p, "<red>Objet inconnu : " + id); continue; }
                            for (org.bukkit.inventory.ItemStack rest : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
                        }
                    }
                    case "outils", "outil", "tools" -> {
                        // Redonne les outils d'administrateur (apres un /op, un inventaire vide...).
                        if (!(sender instanceof Player p)) { Msg.send(sender, "<red>En jeu seulement."); return true; }
                        plugin.hostTools().refresh(p);
                        Msg.send(p, plugin.game().isLobby() || plugin.game().state() == com.kiokouwhite.crossfight.game.GameState.TEST
                                ? "<green>Outils d'administrateur remis dans ta barre." : "<red>Les outils ne sont donnes que hors partie.");
                    }
                    case "regle", "regles", "rule", "rules", "config" -> {
                        // /cf regle : toutes les regles et leur valeur ; /cf regle <nom> <valeur> : en changer une.
                        var rules = plugin.game().rules();
                        if (args.length < 2) {
                            Msg.raw(sender, "<gold><bold>Regles de la partie</bold> <gray>(/cf regle <nom> <valeur>)");
                            Msg.raw(sender, "<yellow>equipes <white>" + plugin.teams().teamSize().label() + " <dark_gray>- <gray>Format des equipes tirees au sort (/cf setsize)");
                            for (var r : com.kiokouwhite.crossfight.game.Rules.all()) Msg.raw(sender, "<yellow>" + r.name() + " <white>" + rules.shown(r) + " <dark_gray>- <gray>" + r.help());
                            return true;
                        }
                        var r = com.kiokouwhite.crossfight.game.Rules.rule(args[1]);
                        if (r == null) { Msg.send(sender, "<red>Regle inconnue : " + args[1] + ". <gray>/cf regle pour la liste."); return true; }
                        if (args.length < 3) { Msg.send(sender, "<yellow>" + r.name() + " : <white>" + rules.shown(r) + " <gray>- " + r.help()); return true; }
                        String wrong = rules.set(r, args[2]);
                        if (wrong != null) { Msg.send(sender, "<red>" + r.name() + " attend " + wrong + "."); return true; }
                        Msg.broadcast("<yellow>Regle : <gold>" + r.help().toLowerCase(java.util.Locale.ROOT) + " <white>" + rules.shown(r) + " <gray>(" + sender.getName() + ")");
                    }
                    case "start" -> {
                        String why = plugin.game().startProblem();
                        if (why == null && plugin.game().start()) { Msg.send(sender, "<green>Lancement de la partie."); return true; }
                        Msg.send(sender, "<red>Impossible de lancer : " + (why == null ? "aucune equipe" : why) + ".");
                        // Ceux qui n'ont pas choisi sont prevenus.
                        for (Player late : plugin.teams().undecided()) {
                            Msg.send(late, "<yellow>La partie attend que tu choisisses ton equipe : la banniere dans ta barre <gray>(solitaire, duo, equipe de trois, ou regarder).");
                            late.playSound(late.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 0.8f);
                        }
                    }
                    case "stop" -> {
                        plugin.game().stop();
                        Msg.broadcast("<yellow>La partie a ete arretee, retour au lobby.");
                    }
                    case "regenere", "regenerer", "regen", "newmap" -> {
                        // Une map toute neuve (graine au hasard, ou celle donnee), hors partie.
                        var game = plugin.game();
                        if (!game.isLobby() && game.state() != com.kiokouwhite.crossfight.game.GameState.TEST) {
                            Msg.send(sender, "<red>Une partie est en cours : <white>/cf stop</white> d'abord.");
                            return true;
                        }
                        Long seed = null;
                        if (args.length >= 2) {
                            try { seed = Long.parseLong(args[1]); } catch (NumberFormatException ex) { seed = (long) args[1].hashCode(); }
                        }
                        Msg.broadcast("<yellow>Creation d'une nouvelle map... <gray>(le serveur se fige quelques secondes)");
                        Long chosen = seed;
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            var w = game.regenerate(chosen);
                            if (w == null) Msg.send(sender, "<red>La nouvelle map n'a pas pu etre creee (voir la console).");
                            else Msg.broadcast("<green>Nouvelle map prete <gray>(graine " + w.getSeed() + ")<green> : tout le monde est au lobby.");
                        }, 2L);
                    }
                    case "lobby" -> {
                        // Tout le monde sur la plateforme du lobby (hors partie ; les testeurs restent dans leur mode test).
                        var game = plugin.game();
                        if (!game.isLobby() && game.state() != com.kiokouwhite.crossfight.game.GameState.TEST) {
                            Msg.send(sender, "<red>Une partie est en cours : <white>/cf stop</white> pour l'arreter et revenir au lobby.");
                            return true;
                        }
                        int moved = 0, testers = 0;
                        for (Player o : Bukkit.getOnlinePlayers()) {
                            if (plugin.testMode().isTester(o)) { testers++; continue; }
                            if (o.isDead()) continue;
                            o.teleport(game.lobbySpot());
                            o.setFallDistance(0);
                            o.setGameMode(org.bukkit.GameMode.ADVENTURE);
                            moved++;
                        }
                        Msg.send(sender, "<green>" + moved + " joueur(s) envoye(s) au lobby." + (testers > 0 ? " <gray>" + testers + " en mode test, laisse(s) ou ils sont (/cf test pour en sortir)." : ""));
                    }
                    case "setsize" -> {
                        TeamSize size = args.length > 1 ? TeamSize.parse(args[1]) : null;
                        if (size == null) { Msg.send(sender, "<red>Usage : /cf setsize <SOLO|DUO|TRIO>"); return true; }
                        if (!plugin.game().isLobby()) { Msg.send(sender, "<red>Impossible en cours de partie."); return true; }
                        plugin.teams().setTeamSize(size);
                        Msg.broadcast("<yellow>Format des equipes tirees au sort : <gold>" + size.label());
                        plugin.hostTools().refreshAll();
                    }
                    case "setborder" -> {
                        int radius;
                        try { radius = Integer.parseInt(args[1]); } catch (Exception ex) { Msg.send(sender, "<red>Usage : /cf setborder <rayon>"); return true; }
                        String wrong = plugin.game().rules().set(com.kiokouwhite.crossfight.game.Rules.rule("bordure"), String.valueOf(radius));
                        Msg.send(sender, wrong == null ? "<green>Bordure : rayon " + radius + " blocs." : "<red>Le rayon doit etre " + wrong + ".");
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
                        if (args.length > 1 && args[1].equalsIgnoreCase("duo")) {
                            // Zone duo : /cf test duo [perso du mannequin duo] ; sans personnage, une seconde fois pour en sortir.
                            Character partner = args.length > 2 ? plugin.characters().character(args[2].toLowerCase()) : null;
                            if (args.length > 2 && partner == null) { Msg.send(sp, "<red>Personnage inconnu : " + args[2]); return true; }
                            if (plugin.testMode().isInDuoZone(sp) && partner == null) plugin.testMode().leaveDuo(sp);
                            else plugin.testMode().enterDuo(sp, partner);
                            return true;
                        }
                        if (args.length > 1 && (args[1].equalsIgnoreCase("arbre") || args[1].equalsIgnoreCase("arbres") || args[1].equalsIgnoreCase("foret"))) {
                            // Foret de test (epiceas de taiga, pour l'equipement tridimensionnel) ; une seconde fois pour revenir.
                            if (plugin.testMode().isInForest(sp)) plugin.testMode().leaveForest(sp);
                            else plugin.testMode().enterForest(sp);
                            return true;
                        }
                        if (args.length > 1 && args[1].equalsIgnoreCase("omori")) {
                            // La porte d'Omori devant soi, dans la zone de test ; une seconde fois pour la retirer.
                            if (plugin.omori().active() && plugin.testMode().isTester(sp)) {
                                plugin.omori().close();
                                Msg.send(sp, "<yellow>La porte d'Omori est retiree. <gray>/cf test omori pour la remettre, /cf test pour sortir du mode test.");
                                return true;
                            }
                            if (!plugin.testMode().isTester(sp) && !plugin.testMode().enter(sp)) return true;
                            plugin.omori().openForTest(sp);
                            Msg.send(sp, "<white>La porte blanche est devant toi : clic droit dessus pour l'ouvrir. <gray>Pour aller plus vite : <white>/cf omori phase <1-5></white>, <white>/cf omori coups <n></white>. <white>/cf test omori</white> la retire.");
                            return true;
                        }
                        if (args.length > 1 && args[1].equalsIgnoreCase("selection")) {
                            // Essai de l'ecran de selection en 3D : /cf test selection [licence] ; sans licence, une seconde fois pour revenir.
                            var sel = plugin.selection();
                            if (sel.isPreviewing(sp) && args.length == 2) { sel.close(sp, true); return true; }
                            Licence lic = args.length > 2 ? plugin.characters().licence(args[2].toLowerCase()) : null;
                            if (args.length > 2 && lic == null) { Msg.send(sp, "<red>Licence inconnue : " + args[2]); return true; }
                            if (lic == null) {
                                Character mine = plugin.players().get(sp).character();
                                if (mine == null) mine = plugin.testMode().thirdPerson().characterOf(sp);
                                lic = mine != null ? mine.licence() : plugin.characters().licence("fire_emblem");
                            }
                            if (!plugin.testMode().isTester(sp) && !plugin.testMode().enter(sp)) return true;
                            if (plugin.testMode().thirdPerson().isActive(sp)) plugin.testMode().thirdPerson().leave(sp);
                            // La config est relue : le cadrage (selection.distance, hauteur, regard) se regle sans redemarrer.
                            plugin.reloadConfig();
                            sel.openPreview(sp, lic);
                            return true;
                        }
                        if (args.length > 1 && (args[1].equalsIgnoreCase("duo-third-person") || args[1].equalsIgnoreCase("duo-3p") || args[1].equalsIgnoreCase("duo-troisieme-personne"))) {
                            // Troisieme personne en duo : le mannequin du centre et ses coequipieres ; sans personnage, une seconde fois pour en sortir.
                            Character c = args.length > 2 ? plugin.characters().character(args[2].toLowerCase()) : null;
                            if (args.length > 2 && c == null) { Msg.send(sp, "<red>Personnage inconnu : " + args[2]); return true; }
                            var tp = plugin.testMode().thirdPerson();
                            if (tp.isActive(sp) && tp.isDuo(sp) && c == null) tp.leave(sp);
                            else tp.enterDuo(sp, c);
                            return true;
                        }
                        if (args.length > 1 && isThirdPersonArg(args[1])) {
                            // Troisieme personne : /cf test third-person [perso du mannequin] ; sans personnage, une seconde fois pour en sortir.
                            Character c = args.length > 2 ? plugin.characters().character(args[2].toLowerCase()) : null;
                            if (args.length > 2 && c == null) { Msg.send(sp, "<red>Personnage inconnu : " + args[2]); return true; }
                            var tp = plugin.testMode().thirdPerson();
                            if (tp.isActive(sp) && c == null) tp.leave(sp);
                            else if (tp.isActive(sp)) tp.setCharacter(sp, c);
                            else tp.enter(sp, c);
                            return true;
                        }
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
                            if (n > 0) Msg.send(sp, "<green>Jauges de Duo a " + (int) pct + "% avec " + n + " allie(s).");
                        }
                        var syn = com.kiokouwhite.crossfight.character.duo.Synergy.of(sp);
                        if (syn != null) {
                            int n = syn.fill(sp, pct);
                            Msg.send(sp, "<green>Synergie a " + (int) pct + "% avec " + n + " partenaire(s).");
                        }
                    }
                    case "credits" -> {
                        if (!(sender instanceof Player sp)) return true;
                        int n = 5000;
                        if (args.length > 1) { try { n = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) { } }
                        var lic = plugin.characters().licence("valorant");
                        if (lic != null && lic.mechanic() instanceof com.kiokouwhite.crossfight.character.valorant.Valorant.Economy eco) {
                            eco.set(plugin.testMode().thirdPerson().actor(sp), n);
                            Msg.send(sp, "<gold>Credits : " + n);
                        }
                    }
                    case "aura" -> {
                        // Test : une epee d'Aura directement, sans craft.
                        if (!(sender instanceof Player sp)) return true;
                        if (!plugin.testMode().isTester(sp)) { Msg.send(sp, "<red>Disponible uniquement en mode test."); return true; }
                        String type = args.length > 1 ? args[1].toLowerCase() : "";
                        if (!com.kiokouwhite.crossfight.character.unordinary.Unordinary.giveAura(plugin.testMode().thirdPerson().actor(sp), type)) Msg.send(sp, "<red>Usage : /cf aura <soin|immunite|laser|chaleur|glue>");
                        else Msg.send(sp, "<green>Epee d'Aura - " + type + " donnee.");
                    }
                    case "delta" -> {
                        // Test : une mutation Earth Delta (regen, coeurs, feu, vitesse, explosion, aleatoire).
                        if (!(sender instanceof Player sp)) return true;
                        if (!plugin.testMode().isTester(sp)) { Msg.send(sp, "<red>Disponible uniquement en mode test."); return true; }
                        if (!com.kiokouwhite.crossfight.character.earthdelta.EarthDelta.grant(plugin.testMode().thirdPerson().actor(sp), args.length > 1 ? args[1] : null)) Msg.send(sp, "<red>Usage (Earth Delta) : /cf delta <regen|coeurs|feu|vitesse|explosion|aleatoire>");
                    }
                    case "kills" -> {
                        // Test : regle son nombre de kills (Kura, Valorant...).
                        if (!(sender instanceof Player sp)) return true;
                        if (!plugin.testMode().isTester(sp)) { Msg.send(sp, "<red>Disponible uniquement en mode test."); return true; }
                        int n = 0;
                        if (args.length > 1) { try { n = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) { } }
                        plugin.players().get(plugin.testMode().thirdPerson().actor(sp)).setKills(n);
                        Msg.send(sp, "<green>Kills : " + n);
                    }
                    case "tome" -> {
                        // Test : tomes de Hano (id, tous, aleatoire) ou enchantement de l'arc (arc <nom>).
                        if (!(sender instanceof Player sp)) return true;
                        if (!plugin.testMode().isTester(sp)) { Msg.send(sp, "<red>Disponible uniquement en mode test."); return true; }
                        String[] rest = args.length > 1 ? java.util.Arrays.copyOfRange(args, 1, args.length) : new String[0];
                        if (!com.kiokouwhite.crossfight.character.earthdelta.Hano.grant(plugin.testMode().thirdPerson().actor(sp), rest)) Msg.send(sp, "<red>Usage (Hano) : /cf tome <id|tous|aleatoire> ou /cf tome arc <flame|multishot|piercing|power|punch|quick_charge>");
                        else Msg.send(sp, "<green>Tomes mis a jour.");
                    }
                    case "son", "sons" -> {
                        // Test : joue un son du pack (/cf son voice.netsu_ult) ou liste ceux qui existent.
                        if (!(sender instanceof Player sp)) return true;
                        if (args.length < 2) { Msg.send(sp, "<gold>" + com.kiokouwhite.crossfight.util.Sounds.events().size() + " sons dans le pack. <gray>/cf son <nom> pour en jouer un (touche Tab pour la liste)."); return true; }
                        String ev = args[1].toLowerCase();
                        if (!com.kiokouwhite.crossfight.util.Sounds.has(ev)) { Msg.send(sp, "<red>Son inconnu : " + ev); return true; }
                        sp.playSound(sp.getLocation(), "crossfight:" + ev, org.bukkit.SoundCategory.MASTER, 1f, 1f);
                        Msg.send(sp, "<green>Son joue : <white>" + ev);
                    }
                    case "dialogue" -> {
                        // Test : l'echange de deux personnages apres leur choix (/cf dialogue ruby weiss [joueur]).
                        if (!(sender instanceof Player sp)) return true;
                        if (args.length < 3) { Msg.send(sp, "<gray>/cf dialogue <perso> <perso> [autre joueur]"); return true; }
                        Player other = args.length > 3 ? Bukkit.getPlayerExact(args[3]) : null;
                        com.kiokouwhite.crossfight.util.Sounds.duoDialogue(sp, args[1].toLowerCase(), other != null ? other : sp, args[2].toLowerCase());
                        Msg.send(sp, "<green>Dialogue " + args[1] + " / " + args[2] + " (rien ne se passe si la paire n'en a pas).");
                    }
                    case "annonce", "bilan" -> {
                        // Le bilan des vies dans le ciel, tout de suite (en mode test, avec des exemples s'il n'y a pas eu de mort).
                        plugin.lifeReport().announce();
                        Msg.send(sender, "<green>Bilan des vies affiche dans le ciel.");
                    }
                    case "pose" -> {
                        // Test : prend une pose du modele articule (/cf pose marth_contre 5), a regarder en vue exterieure (F5).
                        if (!(sender instanceof Player sp)) return true;
                        var poses = com.kiokouwhite.crossfight.util.Puppet.poses();
                        if (args.length < 2) { Msg.send(sp, "<gold>Poses : <white>" + String.join(", ", poses) + " <gray>(/cf pose <nom> [secondes], a regarder en F5)."); return true; }
                        // En troisieme personne, c'est le mannequin qui prend la pose.
                        Player actor = plugin.testMode() == null ? sp : plugin.testMode().thirdPerson().actor(sp);
                        CfPlayer pcf = plugin.players().get(actor);
                        String who = pcf != null && pcf.character() != null ? pcf.character().id() : "";
                        if (!com.kiokouwhite.crossfight.util.Puppet.available(who)) { Msg.send(sp, "<red>Pas de modele articule pour ce personnage dans le pack."); return true; }
                        String pose = args[1].toLowerCase();
                        if (!poses.contains(pose)) { Msg.send(sp, "<red>Pose inconnue : " + pose); return true; }
                        int seconds = 5;
                        if (args.length > 2) { try { seconds = Math.max(1, Math.min(25, Integer.parseInt(args[2]))); } catch (NumberFormatException ignored) { } }
                        var pu = com.kiokouwhite.crossfight.util.Puppet.show(actor, who, actor.getInventory().getItem(0), "debout");
                        if (pu == null) return true;
                        pu.pose(pose, 4);
                        pu.endAfter(seconds * 20, 4);
                        Msg.send(sp, "<green>Pose <white>" + pose + "<green> pendant " + seconds + " s (vue exterieure : F5).");
                    }
                    case "effet", "fx" -> {
                        // Test : joue un effet qu'on ne peut pas declencher seul (/cf effet resurrection : celle de Sage, sur soi).
                        if (!(sender instanceof Player sp)) return true;
                        if (args.length < 2 || !args[1].equalsIgnoreCase("resurrection")) { Msg.send(sp, "<gold>Effets : <white>resurrection <gray>(/cf effet <nom>, a regarder en F5)."); return true; }
                        Player actor = plugin.testMode() == null ? sp : plugin.testMode().thirdPerson().actor(sp);
                        org.bukkit.util.Vector ahead = actor.getLocation().getDirection().setY(0);
                        if (ahead.lengthSquared() < 1e-4) ahead = new org.bukkit.util.Vector(0, 0, 1);
                        com.kiokouwhite.crossfight.character.valorant.Sage.resurrectionLook(actor, actor.getLocation().add(ahead.normalize().multiply(2)));
                    }
                    case "musique", "music" -> {
                        // Test : liste les musiques de combat, en joue une (/cf musique <piste>) ou l'arrete (/cf musique stop).
                        if (!(sender instanceof Player sp)) return true;
                        var tracks = com.kiokouwhite.crossfight.util.Music.tracks();
                        if (args.length < 2) {
                            Msg.send(sp, "<gold>" + tracks.size() + " musique(s) dans le pack, " + com.kiokouwhite.crossfight.util.Music.pairs().size() + " affrontement(s) couverts.");
                            Msg.send(sp, "<gray>/cf musique <piste> pour ecouter, /cf musique stop pour arreter.");
                            return true;
                        }
                        String tr = args[1].toLowerCase();
                        if (tr.equals("stop")) { plugin.music().preview(sp, null); Msg.send(sp, "<gray>Musique arretee."); return true; }
                        if (!tracks.containsKey(tr)) { Msg.send(sp, "<red>Piste inconnue : " + tr); return true; }
                        plugin.music().preview(sp, tr);
                        Msg.send(sp, "<green>Musique : <white>" + tr);
                    }
                    case "burst" -> {
                        // Test : rend le Burst d'un personnage solitaire pret (ou a un pourcentage).
                        if (!(sender instanceof Player sp)) return true;
                        if (!plugin.testMode().isTester(sp)) { Msg.send(sp, "<red>Disponible uniquement en mode test."); return true; }
                        com.kiokouwhite.crossfight.character.solo.Solo.fill(plugin.testMode().thirdPerson().actor(sp));
                        Msg.send(sp, "<gold>Burst pret.");
                    }
                    case "sante" -> {
                        // Test : regle la sante du Coeur Mourant du Drifter (0-100).
                        if (!(sender instanceof Player sp)) return true;
                        if (!plugin.testMode().isTester(sp)) { Msg.send(sp, "<red>Disponible uniquement en mode test."); return true; }
                        double n = 100;
                        if (args.length > 1) { try { n = Double.parseDouble(args[1]); } catch (NumberFormatException ignored) { } }
                        if (!com.kiokouwhite.crossfight.character.solo.Drifter.setSante(plugin.testMode().thirdPerson().actor(sp), n)) Msg.send(sp, "<red>Tu dois jouer le Drifter.");
                        else Msg.send(sp, "<green>Sante du Drifter : " + (int) n + "%");
                    }
                    case "shoja" -> {
                        // Test : arbre des capacites de Shoja (liste, <id>, tous, raz).
                        if (!(sender instanceof Player sp)) return true;
                        if (!plugin.testMode().isTester(sp)) { Msg.send(sp, "<red>Disponible uniquement en mode test."); return true; }
                        String[] rest = args.length > 1 ? java.util.Arrays.copyOfRange(args, 1, args.length) : new String[0];
                        com.kiokouwhite.crossfight.character.solo.ShojaTree.command(plugin.testMode().thirdPerson().actor(sp), rest);
                    }
                    case "titan" -> {
                        // Test : donne a Eren les pouvoirs d'un titan primordial (ou de tous).
                        if (!(sender instanceof Player sp)) return true;
                        if (!plugin.testMode().isTester(sp)) { Msg.send(sp, "<red>Disponible uniquement en mode test."); return true; }
                        String which = args.length > 1 ? args[1].toLowerCase() : "tous";
                        int n = com.kiokouwhite.crossfight.character.snk.Eren.grantTitan(plugin.testMode().thirdPerson().actor(sp), which);
                        if (n < 0) Msg.send(sp, "<red>Tu dois jouer Eren. Titans : " + String.join(", ", com.kiokouwhite.crossfight.character.snk.Eren.TITAN_IDS) + ", tous");
                        else Msg.send(sp, "<green>" + n + " pouvoir(s) de titan obtenu(s).");
                    }
                    case "clear" -> {
                        int n = plugin.testMode().clearTerrain();
                        Msg.send(sender, "<green>Terrain de test nettoye <gray>(" + n + " blocs remis en place, entites retirees).");
                    }
                    case "tournage" -> {
                        // /cf tournage liste : les capacites en JSON (guide) ; /cf tournage <joueur> <id|frappe> : le mannequin la lance.
                        if (args.length > 1 && args[1].equalsIgnoreCase("liste")) { Msg.send(sender, "<green>" + com.kiokouwhite.crossfight.game.GuideExport.write(plugin)); return true; }
                        Player who = args.length > 2 ? Bukkit.getPlayerExact(args[1]) : null;
                        if (who == null) { Msg.send(sender, "<red>Usage : /cf tournage liste | /cf tournage <joueur> <capacite|frappe>"); return true; }
                        com.kiokouwhite.crossfight.util.ChoiceMenu.autoPick = true;
                        if (args[2].equalsIgnoreCase("perso") && args.length > 3) {
                            Character c = plugin.characters().character(args[3]);
                            if (c == null) { Msg.send(sender, "<red>Personnage inconnu."); return true; }
                            who.closeInventory();
                            plugin.testMode().change(who, c);
                            Msg.send(sender, "<gray>Tournage : perso " + c.id());
                            return true;
                        }
                        Msg.send(sender, "<gray>Tournage : " + plugin.testMode().thirdPerson().castFor(who, args[2]));
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
                            var loc = groundNear(sp, sp.getLocation().add(sp.getLocation().getDirection().setY(0).normalize().multiply(6)));
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
                        for (int i = 0; i < count; i++) {
                            var loc = groundNear(sp, base.clone().add((i % 5) * 1.5, 0, (i / 5) * 1.5));
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

    /** Le sol le plus proche de la hauteur du joueur (pas le bloc le plus haut : un toit ou une bulle fausserait tout). */
    private static org.bukkit.Location groundNear(Player p, org.bukkit.Location around) {
        int y = com.kiokouwhite.crossfight.util.Effects.groundAround(p.getWorld(), around.getBlockX(), around.getBlockZ(), p.getLocation().getBlockY());
        org.bukkit.Location loc = around.clone();
        loc.setY(y == Integer.MIN_VALUE ? p.getLocation().getY() : y + 1);
        return loc;
    }

    /** Ce qu'un administrateur de partie (/cf admin) peut faire sans etre operateur. */
    private static final java.util.Set<String> HOST_COMMANDS = java.util.Set.of("start", "stop", "lobby", "setsize", "setborder", "setpvp",
            "regenere", "regenerer", "regen", "newmap", "outils", "outil", "tools", "desactiver", "desactive", "activer", "active", "scenario", "scenarios", "regle", "regles", "rule", "rules", "config");

    private boolean isHost(CommandSender s) {
        return s instanceof Player p && plugin.game().rules().isHost(p.getUniqueId());
    }

    private void help(CommandSender s) {
        Msg.raw(s, "<gold><bold>Crossfight UHC</bold> <gray>- commandes");
        Msg.raw(s, "<yellow>/cf status <gray>- etat de la partie");
        Msg.raw(s, "<yellow>/cf licences <gray>- liste des licences et personnages");
        Msg.raw(s, "<yellow>/cf perso [id] <gray>- ouvrir le menu ou choisir un personnage");
        Msg.raw(s, "<yellow>/cf team list|create|join <id>|leave <gray>- gestion des equipes");
        Msg.raw(s, "<yellow>/vivre <gray>- reapparaitre quand tu attends");
        if (isHost(s) && !s.hasPermission("crossfight.admin")) {
            Msg.raw(s, "<red>/cf start|stop|lobby <gray>- lancer, arreter, rappeler tout le monde au lobby");
            Msg.raw(s, "<red>/cf regenere [graine] <gray>- une map toute neuve (hors partie)");
            Msg.raw(s, "<red>/cf scenario [nom] <gray>- les scenarios (CutClean, Hasty Boys, Timber...)");
            Msg.raw(s, "<red>/cf regle [nom] [valeur] <gray>- voir et changer les regles (bordure, PvP, vies...)");
            Msg.raw(s, "<red>/cf setsize <SOLO|DUO|TRIO> <gray>- taille des equipes");
        }
        if (s.hasPermission("crossfight.admin")) {
            Msg.raw(s, "<red>/cf admin [pseudo] <gray>- nommer (ou retirer) un administrateur de partie");
            Msg.raw(s, "<red>/cf regle [nom] [valeur] <gray>- voir et changer les regles (bordure, PvP, vies...)");
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
                    StringBuilder sb = new StringBuilder(t.coloredName() + " <gray>(" + t.kind().shortLabel() + " " + t.size() + "/" + t.capacity() + ") : ");
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
                // /cf team create [solo|duo|trio] : sans format, celui par defaut.
                TeamSize kind = args.length > 2 ? TeamSize.parse(args[2]) : plugin.teams().teamSize();
                if (kind == null) { Msg.send(p, "<red>Usage : /cf team create [solo|duo|trio]"); return; }
                plugin.teams().leave(p);
                plugin.teams().prune();
                Team t = plugin.teams().create(kind);
                plugin.teams().join(p, t);
                Msg.send(p, kind == TeamSize.SOLO ? "<green>Tu joues en solitaire <gray>(" + t.coloredName() + "<gray>)."
                        : "<green>Tu as cree " + t.coloredName() + " <gray>(" + kind.shortLabel() + ")<green>. Les autres rejoignent avec l'objet <white>Choisir son equipe</white> ou <white>/cf team join " + t.id());
                plugin.hostTools().refreshTeamMenus();
            }
            case "join" -> {
                if (!(s instanceof Player p)) return;
                if (!plugin.game().isLobby()) { Msg.send(p, "<red>Impossible en cours de partie."); return; }
                Team t = null;
                try { t = plugin.teams().get(Integer.parseInt(args[2])); } catch (Exception ignored) { }
                if (t == null) { Msg.send(p, "<red>Equipe introuvable."); return; }
                if (plugin.teams().join(p, t)) { plugin.teams().prune(); Msg.send(p, "<green>Tu as rejoint " + t.coloredName()); }
                else Msg.send(p, "<red>Cette equipe est pleine.");
                plugin.hostTools().refreshTeamMenus();
            }
            case "leave" -> {
                if (!(s instanceof Player p)) return;
                if (!plugin.game().isLobby()) { Msg.send(p, "<red>Impossible en cours de partie."); return; }
                plugin.teams().leave(p);
                plugin.teams().prune();
                Msg.send(p, "<yellow>Tu as quitte ton equipe.");
                plugin.hostTools().refreshTeamMenus();
            }
            case "add" -> {
                if (!s.hasPermission("crossfight.admin") && !isHost(s)) { Msg.send(s, "<red>Permission refusee."); return; }
                if (args.length < 4) { Msg.send(s, "<red>Usage : /cf team add <joueur> <id>"); return; }
                Player target = Bukkit.getPlayerExact(args[2]);
                Team t = null;
                try { t = plugin.teams().get(Integer.parseInt(args[3])); } catch (Exception ignored) { }
                if (target == null || t == null) { Msg.send(s, "<red>Joueur ou equipe introuvable."); return; }
                if (plugin.teams().join(target, t)) Msg.send(s, "<green>" + target.getName() + " ajoute a " + t.coloredName());
                else Msg.send(s, "<red>Equipe pleine.");
            }
            default -> Msg.send(s, "<red>Usage : /cf team list|create [solo|duo|trio]|join <id>|leave");
        }
    }

    /**
     * Ou se tenir pour etre devant ce personnage, tourne vers lui : a deux blocs et demi dans la direction ou il regarde,
     * si l'on peut s'y tenir (deux blocs libres, du sol dessous) ; sinon tout pres de lui, comme avant.
     */
    private static org.bukkit.Location facing(org.bukkit.Location npc) {
        double rad = Math.toRadians(npc.getYaw());
        for (double far : new double[]{2.5, 1.5}) {
            org.bukkit.Location at = npc.clone().add(-Math.sin(rad) * far, 0, Math.cos(rad) * far);
            for (int dy : new int[]{0, 1, -1}) {
                org.bukkit.block.Block feet = at.clone().add(0, dy, 0).getBlock();
                if (feet.isPassable() && !feet.isLiquid() && feet.getRelative(0, 1, 0).isPassable() && feet.getRelative(0, -1, 0).getType().isSolid()) {
                    org.bukkit.Location stand = new org.bukkit.Location(at.getWorld(), at.getX(), feet.getY(), at.getZ());
                    stand.setYaw(npc.getYaw() + 180);
                    stand.setPitch(0);
                    return stand;
                }
            }
        }
        org.bukkit.Location near = npc.clone().add(1.5, 0, 1.5);
        near.setDirection(npc.toVector().subtract(near.toVector()));
        return near;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("status", "licences", "perso", "team", "map", "choose", "pack", "scenario", "ferox", "passif", "scoreboard", "hud"));
            if (!sender.hasPermission("crossfight.admin") && isHost(sender)) subs.addAll(List.of("start", "stop", "lobby", "regenere", "setsize", "regle", "outils", "desactiver", "activer"));
            if (sender.hasPermission("crossfight.admin")) subs.addAll(List.of("admin", "structure", "quete", "tp", "rythme", "pearl", "omori", "lootbox", "loot", "outils", "desactiver", "activer", "regle", "start", "stop", "lobby", "regenere", "setsize", "setborder", "setpvp", "give", "ult", "heal", "mannequin", "test", "annonce", "change", "clear", "ally", "duo", "credits", "titan", "aura", "delta", "kills", "tome", "burst", "sante", "shoja", "son", "musique", "pose", "effet", "dialogue", "demo"));
            for (String sub : subs) if (sub.startsWith(args[0].toLowerCase())) out.add(sub);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "setsize" -> { for (TeamSize t : TeamSize.values()) out.add(t.name()); }
                case "team" -> out.addAll(List.of("list", "create", "join", "leave", "add"));
                case "perso" -> { for (Licence l : plugin.characters().licences()) for (Character c : l.characters()) out.add(c.id()); }
                case "give" -> { for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName()); }
                case "mannequin" -> out.addAll(List.of("1", "3", "5", "attaque", "allie", "adversaire", "clear"));
                case "titan" -> { out.addAll(List.of(com.kiokouwhite.crossfight.character.snk.Eren.TITAN_IDS)); out.add("tous"); }
                case "aura" -> out.addAll(List.of("soin", "immunite", "laser", "chaleur", "glue"));
                case "delta" -> out.addAll(List.of("regen", "coeurs", "feu", "vitesse", "explosion", "aleatoire"));
                case "kills" -> out.addAll(List.of("0", "2", "4", "6", "8"));
                case "sante" -> out.addAll(List.of("100", "75", "50", "25", "10"));
                case "son", "sons" -> { for (String ev : com.kiokouwhite.crossfight.util.Sounds.events()) if (!ev.startsWith("skin.")) out.add(ev); }
                case "pose" -> out.addAll(com.kiokouwhite.crossfight.util.Puppet.poses());
                case "effet", "fx" -> out.add("resurrection");
                case "regle", "regles", "rule", "rules", "config" -> { for (var r : com.kiokouwhite.crossfight.game.Rules.all()) out.add(r.name()); }
                case "admin", "admins" -> { for (Player o : Bukkit.getOnlinePlayers()) out.add(o.getName()); }
                case "scenario", "scenarios" -> { for (var s : com.kiokouwhite.crossfight.game.Scenarios.Scenario.values()) out.add(s.id()); }
                case "loot" -> { out.add("tous"); out.add("embleme"); out.addAll(com.kiokouwhite.crossfight.game.Lootbox.ids()); }
                case "lootbox" -> out.addAll(List.of("1", "2", "3", "4", "vague"));
                case "ferox" -> out.addAll(List.of("oui", "non", "lancer"));
                case "omori" -> out.addAll(List.of("lancer", "fermer", "phase", "coups"));
                case "quete", "quetes" -> out.addAll(List.of("liste", "tp", "lancer", "montrer", "finir", "parler", "oublier", "placer", "replacer"));
                case "tp" -> out.add("quete");
                case "rythme", "concert", "forge" -> { out.addAll(plugin.rhythm().ids()); out.add("recharger"); out.add("resultat"); }
                case "pearl" -> out.add("glowing");
                case "enigme", "enigmes" -> out.addAll(List.of("tablette", "circuit", "etabli", "puzzle", "briques"));
                case "marchand" -> out.add("nouveau");
                case "qg" -> out.addAll(List.of("prendre", "liberer", "voir"));
                case "evenement", "evenements" -> { out.add("tirer"); out.add("arreter"); out.addAll(plugin.placeEvents().places()); }
                case "structure", "structures", "lieu", "lieux" -> out.addAll(List.of("liste", "monde", "fiche", "placer", "disperser", "barrieres"));
                case "desactiver", "activer" -> {
                    out.addAll(List.of("solo", "duo", "trio"));
                    for (Licence l : plugin.characters().licences()) { out.add(l.id()); for (Character c : l.characters()) out.add(c.id()); }
                }
                case "dialogue" -> { for (Licence l : plugin.characters().licences()) for (Character c : l.characters()) out.add(c.id()); }
                case "test" -> out.addAll(List.of("duo", "third-person", "duo-third-person", "selection", "arbre", "omori"));
                case "passif", "passifs", "scoreboard", "tableau" -> out.addAll(List.of("show", "hide"));
                case "hud" -> { if (sender.hasPermission("crossfight.admin")) out.add("demo"); }
                case "demo" -> { if (sender.hasPermission("crossfight.admin")) { out.addAll(com.kiokouwhite.crossfight.game.DemoAdversaire.MODES); out.add("embleme"); } }
                case "musique", "music" -> { out.add("stop"); out.addAll(com.kiokouwhite.crossfight.util.Music.tracks().keySet()); }
                case "shoja" -> { out.addAll(List.of("liste", "tous", "raz")); out.addAll(com.kiokouwhite.crossfight.character.solo.ShojaTree.ids()); }
                case "tome" -> { out.addAll(List.of("tous", "aleatoire", "arc")); for (var t : com.kiokouwhite.crossfight.character.earthdelta.Hano.tomeIds()) out.add(t); }
                case "change" -> { for (Licence l : plugin.characters().licences()) for (Character c : l.characters()) out.add(c.id()); }
                default -> { }
            }
        } else if (args.length == 3 && (args[0].equalsIgnoreCase("quete") || args[0].equalsIgnoreCase("quetes") || args[0].equalsIgnoreCase("tp") && args[1].equalsIgnoreCase("quete"))) {
            out.addAll(com.kiokouwhite.crossfight.game.Quests.QUESTS.keySet());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("test") && args[1].equalsIgnoreCase("selection")) {
            for (Licence l : plugin.characters().licences()) out.add(l.id());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("test") && isThirdPersonArg(args[1])) {
            for (Licence l : plugin.characters().licences()) for (Character c : l.characters()) out.add(c.id());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("test") && (args[1].equalsIgnoreCase("duo") || args[1].equalsIgnoreCase("duo-third-person"))) {
            for (Licence l : plugin.characters().licences()) {
                if (com.kiokouwhite.crossfight.game.TestMode.DUO_LICENCES.contains(l.id())) for (Character c : l.characters()) out.add(c.id());
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("mannequin") && args[1].equalsIgnoreCase("adversaire")) {
            for (Licence l : plugin.characters().licences()) for (Character c : l.characters()) out.add(c.id());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (Licence l : plugin.characters().licences()) for (Character c : l.characters()) out.add(c.id());
        }
        return out;
    }

    /** "/cf test third-person" (et ses variantes francaises). */
    private static boolean isThirdPersonArg(String a) {
        String x = a.toLowerCase(java.util.Locale.ROOT);
        return x.equals("third-person") || x.equals("thirdperson") || x.equals("3p") || x.equals("troisieme-personne") || x.equals("3e-personne");
    }
}
