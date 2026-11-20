# Crossfight UHC

Plugin Paper 1.21 pour le mode de jeu **Crossfight UHC** : un UHC en solo, duo ou trio
où chaque joueur incarne un personnage tiré d'une licence (Fire Emblem, RWBY, Valorant,
Shingeki no Kyojin, unOrdinary, Earth Delta, Armed Blue Gunvolt, Shoja's Memories,
Bloody Snow, personnages solitaires).

Règles complètes : https://celecias-organization.gitbook.io/crossfight-uhc/

## Prérequis

- Serveur **Paper 1.21.10** (ou plus récent en 1.21.x)
- **Java 21**

## Compiler

```bash
./gradlew build
```

Le jar à déposer dans `plugins/` est `build/libs/CrossfightUHC-<version>.jar`.

## Commandes

| Commande | Rôle |
|---|---|
| `/cf start` / `/cf stop` | Lancer ou arrêter la partie (admin) |
| `/cf lobby` | Téléporter tous les joueurs sur la plateforme du lobby (admin, hors partie ; les joueurs en mode test restent où ils sont) |
| `/cf regenere [graine]` | Créer une map toute neuve et y amener tout le monde (admin, hors partie) |
| `/cf scenario [nom] [oui\|non]` | Voir les scénarios (tout le monde), en cocher ou décocher un (admin) |
| `/cf ferox oui\|non` | Répondre à la proposition de Regna Ferox |
| `/cf ferox lancer` | Ouvrir Regna Ferox tout de suite (admin, en partie) |
| `/cf test omori` | Zone de test avec la porte d'Omori devant soi, pour essayer le boss (une seconde fois pour la retirer) |
| `/cf omori lancer\|fermer\|phase <1-5>\|coups <n>` | Essayer l'événement Omori : l'ouvrir, le refermer, changer de phase, compter des coups (test) |
| `/cf map` | La carte de la map, dans la main : les lieux et leur nom, l'arène, la bordure, soi et son équipe. Pour un admin : clic droit avec la carte pour viser un endroit, clic pour s'y téléporter |
| `/cf structure liste\|monde\|fiche\|placer\|disperser` | Les lieux posés au hasard sur la map : les lister (et la part que la bordure laisse à chacun), visiter le monde d'origine d'un lieu, écrire sa fiche, en poser un, poser ceux qui manquent (admin) |
| `/cf lootbox [1-4\|vague]` | Poser une Lootbox devant soi (tier au choix), ou lancer l'événement entier (test) |
| `/cf loot <objet\|tous>` | Recevoir un objet de Lootbox (test) |
| `/cf outils` | Remettre dans sa barre les outils d'administrateur (admin, hors partie) |
| `/cf admin [pseudo]` | Nommer (ou retirer) un administrateur de partie ; sans pseudo, la liste (opérateur) |
| `/cf regle [nom] [valeur]` | Voir toutes les règles de la partie, ou en changer une (admin) |
| `/cf setsize SOLO|DUO|TRIO` | Format des équipes tirées au sort (admin) |
| `/cf desactiver <nom>` / `/cf activer <nom>` | Désactiver ou réactiver un personnage, une licence ou un format (`solo`, `duo`, `trio`) ; sans nom : ce qui est désactivé (admin) |
| `/cf setborder <rayon>` | Taille de la map (admin) |
| `/cf setpvp <minute>` | Minute d'activation du PvP (admin) |
| `/cf team create [solo\|duo\|trio]\|join <id>\|leave\|list` | Choisir son équipe dans le lobby (comme la bannière « Choisir son équipe ») |
| `/cf team add <joueur> <id>` | Forcer un joueur dans une équipe (admin) |
| `/cf perso [id]` | Ouvrir le menu de choix ou choisir un personnage |
| `/cf give <joueur> <perso>` | Forcer un personnage (admin, pour tester) |
| `/cf licences` | Lister les licences et personnages |
| `/vivre` | Réapparaître quand on attend la mort de son équipe |
| `/cf test` | Entrer ou sortir du mode test (monde plat, équipement fourni) |
| `/cf test duo [perso]` | Zone duo : essayer les ultimes en duo seul, avec un mannequin duo (une seconde fois sans personnage pour revenir) |
| `/cf test selection [licence]` | Essayer l'écran de sélection en 3D d'une licence (une seconde fois sans licence pour revenir) ; choisir un personnage le fait incarner |
| `/cf test third-person [perso]` | Troisième personne : un mannequin incarne le personnage et lance ses capacités sur toi, avec tes objets (une seconde fois sans personnage pour revenir) |
| `/cf change [perso]` | Changer de personnage en mode test |
| `/cf clear` | Remettre le terrain de test à neuf (blocs, entités, mannequins) |
| `/cf mannequin adversaire <perso>` | Un mannequin qui incarne le personnage : ses capacités offensives sont données en items, et les activer les lance depuis le mannequin contre toi |
| `/cf mannequin [nb] [attaque\|allie]` | Mannequins d'entraînement (ennemis, agressifs ou alliés), `/cf mannequin clear` pour les retirer |
| `/cf ult [joueur] <pourcent>` | Régler la jauge d'Ultime (test) |
| `/cf heal` | Se soigner (test) |
| `/cf ally <testeur>` | Rejoindre l'équipe d'un autre testeur en mode test |
| `/cf duo [pourcent]` | Remplir les jauges d'Ultime en Duo (RWBY) et de Synergie (groupes duo) (test) |
| `/cf credits [n]` | Régler ses crédits Valorant (test) |
| `/cf titan [nom\|tous]` | Donner à Eren les pouvoirs d'un titan primordial (test) |
| `/cf test duo-third-person [perso]` | Troisième personne en duo : le mannequin du centre, ses coéquipières en mannequins duo, et ses ultimes en duo lancés sur le testeur (une seconde fois pour revenir) |
| `/cf test arbre` | Forêt de test : des épicéas de taïga pour essayer l'équipement tridimensionnel (une seconde fois pour revenir) |
| `/cf effet resurrection` | Aperçu d'un effet qu'on ne peut pas déclencher seul (test) |
| `/cf aura <type>` | Recevoir une épée d'Aura sans la crafter (test) |
| `/cf choose` | John : choisir ses pouvoirs absorbés |
| `/cf delta <effet\|aleatoire>` | Ajouter une mutation Earth Delta (test) |
| `/cf kills <n>` | Régler son nombre de kills (test) |
| `/cf tome <id\|tous\|aleatoire>` / `/cf tome arc <ench>` | Tomes et enchantement d'arc de Hano (test) |
| `/cf burst` | Rendre son Burst de solitaire prêt (test) |
| `/cf sante <0-100>` | Régler la santé du Cœur Mourant du Drifter (test) |
| `/cf shoja <liste\|id\|tous\|raz>` | Arbre de Shoja : lister, équiper une capacité, tout débloquer, tout retirer (test) |
| `/cf pack` | Redemander le pack de sons (ouvert à tous) |
| `/cf son [nom]` | Compter les sons du pack, ou en jouer un (test) |
| `/cf dialogue <perso> <perso> [joueur]` | Écouter l'échange de deux personnages après leur choix (test) |
| `/cf musique [piste\|stop]` | Lister les musiques du pack, en écouter une en boucle, l'arrêter (test) |

## Déroulement d'une partie

Avant le lancement, les joueurs attendent dans un lobby dans les airs : une plateforme de verre de 43 blocs de côté, à 250 blocs de haut, au-dessus du point d'apparition du monde. Pour patienter, chaque bloc de verre change de couleur (avec une petite note) quand un joueur marche dessus. Des murs et un plafond invisibles empêchent d'en tomber. On y arrive en se connectant, en quittant le mode test, après `/cf stop`, et avec `/cf lobby`, qui y envoie tous les joueurs ; elle disparaît du ciel quand la partie démarre et revient au retour au lobby. Réglages dans `config.yml` : `lobby.plateforme` (false : on reste au point d'apparition), `lobby.hauteur`, `lobby.rayon`.

1. **Lobby** : les joueurs choisissent leur équipe (solitaire, duo ou équipe de trois), ou de regarder ; la partie ne se lance que quand tous ont choisi. Avec la règle `equipes-aleatoires`, les équipes sont tirées au sort au lancement.
2. **Sélection** : une licence est tirée au sort par équipe, parmi celles de son format, chacun choisit un personnage (60 s, puis attribution automatique).
3. **Lancement** : téléportation par équipe, armes et items de capacités donnés.
4. **Partie** : PvP à 30 min, événements à 20 / 30 / 40 / 50 / 60 min, 3 vies par joueur.
5. **Fin** : dernière équipe en vie.

### Choisir son équipe

Dans le lobby, chaque joueur a une bannière « Choisir son équipe » dans sa barre (clic droit). Le menu propose de jouer en solitaire, de créer un duo ou de créer une équipe de trois, et montre les équipes déjà formées (un bloc de laine à leur couleur, avec leurs membres et leurs places libres) : un clic sur une équipe incomplète la rejoint, « Quitter mon équipe » la quitte. Une équipe que tout le monde a quittée disparaît, et un joueur qui se déconnecte du lobby libère sa place.

Les formats se mélangent dans une même partie : chaque équipe tire sa licence parmi celles de son format (Personnage Solitaire, Groupe Duo, Équipe de Trois). L'œil « Regarder la partie » du même menu fait du joueur un spectateur.

La partie ne se lance pas tant qu'un joueur n'a ni équipe ni choisi de regarder : `/cf start` répond qui manque, et ces joueurs sont prévenus. Une équipe incomplète (un trio à deux) peut jouer.

### Personnages, licences, formats, doublons

L'objet « Personnages et licences » de l'administrateur (ou les commandes) règle ce qui est jouable :

- **Formats** : désactiver les solitaires, les duos ou les équipes de trois (`/cf desactiver duo`). Le bouton correspondant se ferme dans le menu des équipes, et les équipes de ce format déjà formées sont dissoutes.
- **Licences** : clic gauche sur une licence pour la désactiver (`/cf desactiver rwby`) ; elle n'est plus tirée.
- **Personnages** : clic droit sur une licence pour ouvrir ses personnages, clic pour en désactiver un (`/cf desactiver weiss`) ; il n'apparaît plus à la sélection. Une licence à qui il ne reste pas assez de personnages pour une équipe de son format n'est plus tirée.
- **Doublons** (règle `doublons`, autorisés par défaut) : autorisés, deux équipes peuvent recevoir la même licence quand il n'y en a plus de libre, et deux solitaires le même personnage. Interdits, il y a une équipe par licence et un solitaire par personnage : le menu des équipes ferme un format quand il est plein, et la partie refuse de se lancer s'il y a trop d'équipes.
- **Équipes aléatoires** (règle `equipes-aleatoires`) : les joueurs ne choisissent plus, tout le monde est mélangé au lancement dans des équipes du format choisi (« Format du tirage », ou `/cf setsize`) ; ceux qui restent forment une équipe plus petite (un duo, un solitaire) si ce format est permis.

À la sortie du menu, une annonce résume les réglages. Le mode test ignore ces réglages : on y essaie tout.

### Administrateurs de partie et règles

`/cf admin <pseudo>` (réservé aux opérateurs du serveur) confie à un joueur les droits d'administrateur de partie : sans être opérateur, il peut lancer et arrêter la partie (`/cf start`, `/cf stop`), rappeler tout le monde au lobby (`/cf lobby`), créer une nouvelle map (`/cf regenere`), régler la taille des équipes (`/cf setsize`) et changer les règles (`/cf regle`). La même commande les lui retire ; `/cf admin` seul donne la liste. Le mode test et les commandes de triche restent aux opérateurs. Dans ce README, « (admin) » veut dire opérateur ou administrateur de partie pour ces commandes-là.

Pas besoin de commandes : dans le lobby, les opérateurs et les administrateurs de partie ont leurs outils dans la barre d'objets, à utiliser d'un clic droit, objet en main.

| Objet | Ce qu'il fait |
|---|---|
| Comparateur « Règles de la partie » | Ouvre le menu des règles : un objet par règle, avec sa valeur. Clic gauche : plus ; clic droit : moins ; Maj + clic : cinq crans d'un coup ; un clic bascule les règles oui / non et la taille des équipes. À la fermeture, une seule annonce dit à tous ce qui a changé. |
| Livre enchanté « Scénarios » | Ouvre le menu des scénarios : un clic coche ou décoche chacun ; une annonce à la fermeture |
| Porte-armure « Personnages et licences » | Ouvre le menu des formats, licences et personnages jouables, des doublons et des équipes aléatoires |
| Bloc d'herbe « Nouvelle map » | Crée une nouvelle map (deux clics en moins de cinq secondes) |
| Boussole « Rappeler au lobby » | Ramène tous les joueurs sur la plateforme |
| Teinture verte « Lancer la partie » | Lance la partie (deux clics en moins de cinq secondes) |

Les outils ne se jettent pas et ne se déplacent pas ; ils disparaissent au lancement et reviennent au retour au lobby. `/cf outils` les redonne (après un `/op`, par exemple). Les commandes restent disponibles :

`/cf regle` liste les règles et leur valeur ; `/cf regle <nom> <valeur>` en change une, l'annonce à tous et l'enregistre dans `config.yml` :

| Règle | Par défaut | Ce qu'elle règle |
|---|---|---|
| `bordure` | 1000 blocs | Rayon de la bordure au départ |
| `bordure-finale` | 100 blocs | Rayon de la bordure une fois réduite |
| `bordure-debut` | 60 min | Minute où la bordure commence à se réduire (0 : jamais) |
| `bordure-duree` | 20 min | Temps que met la bordure à se réduire |
| `pvp` | 30 min | Minute où le PvP s'active (0 : dès le départ) |
| `invincibilite` | 30 s | Invincibilité au départ |
| `vies` | 3 | Vies par joueur |
| `dispersion` | 800 blocs | Rayon dans lequel les équipes sont dispersées au départ |
| `reapparition` | 500 blocs | Rayon dans lequel on réapparaît après une mort |
| `force` | 1 pt | Points d'attaque par niveau de Force, potion comprise (3 : comme dans Minecraft) |
| `minerais` | 5 x | Minerais multipliés sous terre (1 : comme dans Minecraft) |
| `doublons` | oui | Deux équipes peuvent avoir la même licence (et deux solitaires le même personnage) |
| `equipes-aleatoires` | non | Les équipes sont tirées au sort au lancement |
| `regeneration` | non | Régénération naturelle de la vie |
| `jour-permanent` | non | Il fait toujours jour |
| `lootbox`, `regna-ferox`, `match-found`, `judgement`, `omori` | 20 à 60 min | Minute de chaque événement |
| `lootbox-nombre` | 6 | Nombre de Lootbox qui apparaissent |

La bordure se réduit maintenant d'elle-même : à sa minute, elle passe en `bordure-duree` minutes de son rayon de départ à son rayon final, avec une annonce cinq minutes avant.

### Événement Lootbox

À la minute `lootbox` (20 par défaut), `lootbox-nombre` coffres apparaissent au hasard dans la bordure. Leurs coordonnées sont annoncées à tous, à la couleur de leur tier, et une colonne de lumière de cette couleur les signale. Chaque Lootbox contient 2 à 8 diamants, 6 à 12 lingots d'or et un objet de son tier ; sa première ouverture est annoncée, elle disparaît une fois vidée, ou au bout de 10 minutes.

Le tier est tiré au sort : tier 1 (45 %), tier 2 (35 %), tier 3 (19 %), tier 4 (1 %). Les objets ont un nombre d'utilisations compté (leur « durabilité », affichée sur l'objet) et, pour certains, un temps de recharge ; ils s'utilisent au clic droit, les armes en frappant.

| Tier | Objets |
|---|---|
| 1 | Feu (flamme là où l'on vise, 3 blocs), Foudre (éclair de 2 cœurs sur l'adversaire le plus proche à 10 blocs), Vent (repousse de 5 blocs les adversaires à 10 blocs), Flux (absorbe 2 cœurs), Arc en bronze (Puissance I), Épée de Verre (14 dégâts, un seul coup), Javelot (lancé, 3 cœurs), Hache en bronze (10 dégâts, allonge de 2 blocs), Sauvetage (téléporte un allié sur soi) |
| 2 | Bolganone (boule de feu sur 10 blocs, enflamme son passage, 3 cœurs), Thoron (éclair de 3 cœurs sur les ennemis à 20 blocs), Forseti (éjecte les adversaires à 10 blocs), Ruine (vole 2 cœurs à chaque adversaire à 10 blocs), Parthia (Puissance II, flèche qui part droit), Épée Héros (8 dégâts, sans délai), Gae Bolg (lance qui revient si elle ne touche rien), Marteau (12 dégâts, allonge de 2 blocs), Soin (5 cœurs à un allié) |
| 3 | Valflame (lac de lave 3×3 sous l'adversaire le plus proche), Mjolnir (éclair et explosion sur les adversaires à 50 blocs, 3 cœurs), Excalibur (tornade qui éjecte à 10 blocs de haut), Goethe (zone 5×5 qui vole un cœur par seconde pendant 10 s), Stellaire (Puissance III, tire au clic), Sol Katti (9 dégâts, rend la moitié des dégâts infligés), Gravidus (lance qui rend 3 cœurs), Vengeance (10, 12 ou 14 dégâts selon les alliés morts), Bâton de la déesse (10 cœurs et Régénération à tous les alliés) |
| 4 | Expiration (labyrinthe d'épines de 20×20 pendant 30 s), Livre de Naga (toute sa vie ; les ennemis à 30 blocs perdent définitivement la vie qu'avait le lanceur) |

Choix faits là où le document ne tranche pas : « reach de 2 » est une allonge de 2 blocs (3 d'ordinaire) ; les lances infligent 3 cœurs ; Soin et Sauvetage visent l'allié regardé, sinon le plus proche ; Gae Bolg et Gravidus reviennent aussi après avoir touché, avec une utilisation de moins ; le lac de lave dure 20 s ; le labyrinthe laisse au moins un cœur de vie maximale aux victimes du Livre de Naga. Les haches de Lootbox blessent les joueurs, contrairement aux haches fabriquées.

### Événement Regna Ferox

L'arène de Ferox se dresse sur la map avant même le lancement de la partie (à 450-650 blocs du centre sur une map neuve ; en 0, 0 sur les maps créées avant) : elle est bâtie au démarrage du serveur et à chaque nouvelle map (`/cf regenere`), et elle est indestructible en permanence. Sa hauteur, qui dépend du relief, est rappelée au lancement. C'est une salle massive et fermée de 185 blocs de large, dans les bruns et les noirs de l'arène de Ferox : un toit en cône bas la couvre entièrement ; dedans, une fosse octogonale pavée de 113 blocs, avec au centre une grande mosaïque ronde de 81 blocs (soleil clair, étoile noire à seize branches, cercle de rayons, dents de scie, frise de volutes dorées), quatre grilles aux coins et douze grands braseros le long du mur ; un mur sombre à tentures rouges et à herses, des gradins de brique de terre en dix-neuf marches, une enceinte noire rythmée par seize piliers qui percent le toit, coiffés de feux. La salle est sombre, éclairée par les braseros. Quatre galeries traversent les gradins du dehors jusqu'aux herses de la fosse, qui restent fermées sauf pour l'événement Omori ; une paroi invisible ferme la fosse au-dessus de son mur. Elle se bâtit en quelques secondes, par tranches.

À la minute `regna-ferox` (30 par défaut), chaque joueur en vie reçoit dans le chat une proposition de duel d'équipes, avec deux boutons : Participer et Refuser (ou `/cf ferox oui`, `/cf ferox non`). Une équipe est inscrite quand tous ses membres en vie ont accepté ; les deux premières inscrites sont téléportées dans la fosse. Sans deux équipes inscrites au bout de 60 secondes, l'arène reste fermée.

Dans la fosse, un coup qui tuerait ne tue pas : le joueur est à terre et regarde la fin du combat en spectateur. L'équipe qui met toute l'autre à terre gagne : chacun de ses joueurs reçoit le contenu d'une Lootbox de tier IV (diamants, or et un objet de tier IV) ; chaque joueur de l'équipe battue perd une vie. Tout le monde revient ensuite là où il était, avec la vie qu'il avait (pleine pour ceux qui étaient à terre). Le combat n'a pas de limite de temps. On s'y bat même si le PvP n'est pas encore ouvert sur la map ; un joueur qui se déconnecte en plein combat compte comme à terre.

### Événement Omori

À la minute `omori` (60 par défaut), les quatre herses de l'arène se lèvent (on y entre par les galeries qui traversent les gradins) et une porte blanche apparaît à un endroit au hasard de la fosse ; ses coordonnées sont annoncées. C'est une vraie porte, blanche, haute de deux blocs et incassable : un clic droit dessus l'ouvre et emmène dans l'Espace Blanc, un monde au sol blanc où attend le boss Omori. Tout y est blanc, même le ciel : quatre murs et un plafond de lumière blanche, sans ombre, ferment la vue à 30 blocs du centre (de grands panneaux de texte en blocs pleins, quatre couches décalées par face), il y est toujours midi, et l'on ne peut pas s'éloigner à plus de 25 blocs du centre.

La première fois qu'un joueur franchit la porte, la vidéo d'entrée « Welcome to White Space » se joue plein écran, avec son son (30 secondes ; s'accroupir la passe) : il ne bouge pas, ne craint rien, et le combat ne commence pour lui qu'ensuite. Minecraft ne lit pas de vidéo : `python3 tools/build_intro.py <vidéo> espace_blanc --prive [--cadre x:y:l:h]` la découpe en images de 594 × 440, rangées dans une police du pack complet (jamais versionnée), que le plugin affiche en titre sur un fond noir. Une police n'accepte pas d'image de plus de 256 pixels : chaque image est donc coupée en six tuiles, écrites côte à côte (des caractères de largeur négative ramènent le curseur en arrière). Les images qui ne changent pas ne sont gardées qu'une fois ; une table (`intro.txt`, dix cases par seconde) dit laquelle montrer à chaque instant. Sans elle, on entre directement.

Omori ne perd pas de vie : chaque coup qu'un joueur lui porte compte pour un (quatre par seconde au plus par joueur). Pendant ses attaques, il est intouchable : du déclenchement d'une attaque à sa fin, aucun coup ne compte (sa barre l'indique, « intouchable ») ; on le frappe pendant ses cinq secondes de repos et pendant la seconde d'annonce de l'attaque suivante. Il se bat en cinq phases, à 38, 44, 50, 56 puis 63 coups (251 en tout), avec une barre en haut de l'écran. Il enchaîne les attaques de sa phase, une toutes les cinq secondes ; le nom de chacune s'annonce une seconde avant qu'elle parte, dans une seconde barre rouge sous la sienne, qui se vide pendant ce délai :

| Phase | Attaques, dans l'ordre |
|---|---|
| 1 | Attaque basique, Poignarder, Trancher |
| 2 | Effacement, Poignarder, Attaque basique |
| 3 | Annihilation, Attaque basique, Poignarder |
| 4 | Traumatisme, Poignarder, Attaque basique |
| 5 | Le sol devient noir (le noir part de sous Omori et gagne tout le sol en une seconde) ; vision obstruée en permanence ; Suicide, « Je ne peux pas le faire » |

Chaque attaque est animée :

- **Attaque basique** (2 cœurs) : Omori regarde le joueur le plus proche, lève son couteau, lui fonce dessus et le tranche ; un croissant blanc, fin, s'abat en travers de la cible, puis il retourne à sa place.
- **Poignarder** (3 cœurs, Wither et Lenteur V 5 s) : il fonce sur sa cible, l'attrape (elle ne peut plus bouger), arme son couteau et l'enfonce lentement, pendant une seconde et demie.
- **Trancher** (2 cœurs et Wither 2 s à tous) : il tremble sur place, de plus en plus fort, puis lâche une rafale de coups ; les croissants tombent sur chaque joueur, en tous sens.
- **Effacement** (3 cœurs) : deux mains rouges sortent du sol de part et d'autre de chaque joueur, se referment sur lui, puis le jettent à 10 blocs dans les airs.
- **Annihilation** (2 cœurs par main) : pendant 10 s, toutes les 2 s, une main sanglante tombe sur chaque joueur. Une cible rouge se dessine au sol là où elle va tomber, avec des bips de plus en plus rapides et un « DANGER » à l'écran : il reste une seconde et demie pour s'écarter.
- **Traumatisme** : il charge le joueur le plus proche et l'éjecte à 15 blocs de haut en le percutant, avec Darkness 10 s.
- **Suicide** (5 cœurs, Darkness) : une surface rouge se referme autour de lui, puis éclate en fils qui partent en tous sens, transpercent chaque joueur et l'éjectent à 10 blocs.
- **« Je ne peux pas le faire »** : il fixe les joueurs. Pendant 10 s ils ne peuvent plus bouger, leur regard est ramené de force sur lui, et des mèches de cheveux noirs leur tombent devant les yeux jusqu'à couvrir l'écran. Chaque seconde, ils perdent un cœur (10 en tout, sans recul).

À chaque changement de phase, le panneau du jeu s'écrit au-dessus de la barre d'objets de ceux qui combattent : « OMORI did not succumb. », lettre à lettre, avec le bip des dialogues, pendant trois secondes et demie. Ses images viennent d'une vidéo du jeu (`python3 tools/build_panneau.py <vidéo>` les range dans une police du pack complet, jamais versionnée ; seule la table du rythme, dans `intro.txt`, l'est) ; sans le pack complet, il n'y a que le message du chat.

Le temps du panneau, Omori est en négatif : son skin aux couleurs inversées (`skin.omori_negatif`, fait avec `tools/add_skin.py` sur l'image inversée, pack complet). Il retrouve ses couleurs quand le combat reprend.

Les bruitages sont ceux du jeu OMORI dans le pack complet (`python3 tools/build_omori_sons.py <dossier « Sound Effects » du jeu>` les range dans le pack privé, jamais versionné ; la table du script dit quel fichier sert à quoi) ; avec le pack public, ce sont des bruitages de Minecraft.

Au dernier coup, Omori meurt en six secondes et demie : il tombe à genoux, tête basse, puis part en poussière, partie par partie (les bras et son couteau, les jambes, le buste, la tête en dernier : chacune grisonne, s'effrite, et ses grains s'envolent dans son dos) ; la poussière revient ensuite, tourne en se resserrant là où il était, et il en sort une **porte noire**. La musique se tait, la porte blanche de l'arène s'efface (plus personne n'entre), et la vision des joueurs se dégage. Sans son modèle articulé, il rapetisse en s'effritant.

La porte noire est une vraie porte : un clic droit l'ouvre et fait sortir de l'Espace Blanc, à l'endroit où était la porte blanche. C'est une fois dehors que chaque joueur présent à sa mort reçoit le choix : se pardonner (une vie de plus) ou ne pas se pardonner (une vie de moins, mais Force III pour le reste de la partie) ; fermer le menu sans choisir, c'est se pardonner. Chacun sort quand il veut ; au bout de deux minutes (prévenu trente secondes avant), ceux qui restent sont reconduits dehors, avec le même choix. Quand le dernier est sorti, l'événement se referme et le sol redevient blanc.

Minecraft n'a pas de porte noire : c'est une porte en chêne noir dans l'état « alimentée » (sans redstone), auquel le pack public donne une texture noire dessinée par `python3 tools/build_porte.py` ; les portes en chêne noir du jeu ne changent pas, sauf le temps qu'un bouton ou une plaque les alimente. Sans le pack, la porte de sortie est une porte en chêne noir ordinaire.

Mourir dans l'Espace Blanc est une mort ordinaire. Omori n'existe que tant qu'un joueur est dans l'Espace Blanc : si tout le monde en sort, il reprend plus tard là où il en était. On n'y casse ni n'y pose aucun bloc.

Omori est un personnage articulé, couteau à la main, quand son skin est dans le pack (`python3 tools/add_skin.py <skin.png> omori --prive`, puis `tools/build_pack.py` ; `omori.skin` dans `config.yml` nomme le skin à utiliser) ; sans lui, c'est un zombie en tenue noire et blanche. `/cf test omori` pose la porte devant soi dans la zone de test, pour essayer le combat sans lancer de partie.

Une musique accompagne le combat pour qui est dans l'Espace Blanc, et s'arrête dès qu'on en sort (pack complet, jamais versionnée). Elle suit le combat : pendant les quatre premières phases, c'est le côté calme du morceau qui tourne (piste `omori_calme` : ses 47 premières secondes, en boucle) ; à la dernière phase, la piste `omori_violent` prend le relais en fondu (deux secondes) : elle repart de la montée du morceau, qui glisse d'elle-même vers son côté violent en une minute, puis sa dernière section de 42 secondes tourne sans fin. Elles se fabriquent avec `python3 tools/boucle_musique.py <fichier> omori_calme --debut 4 --longueur 1896300 --fondu 3500 --tours 6 --niveau -23 --prive` et, sur le morceau coupé à 47 secondes avec un fondu d'entrée, `... omori_violent --debut 223.85 --longueur 1846977 --tours 4 --prive` (voir « Sons, voix et musiques »), puis `python3 tools/build_pack.py`. Sans elles, le combat se fait sans musique. `/cf musique omori_calme` et `/cf musique omori_violent` les font entendre hors combat.

### Scénarios

Des scénarios UHC de confort, à cocher avant la partie avec l'objet « Scénarios » de l'administrateur ou `/cf scenario <nom>` ; `/cf scenario` seul en donne la liste à tout le monde, et ceux qui sont cochés sont rappelés au lancement. Tous sont décochés par défaut et gardés dans `config.yml` (`scenarios`).

| Scénario | Ce qu'il fait |
|---|---|
| `cutclean` CutClean | Les minerais de fer, d'or et de cuivre donnent des lingots (avec leur expérience), les animaux de la viande cuite |
| `hastyboys` Hasty Boys | Les outils fabriqués (pioche, hache, pelle, houe) ont Efficacité III et Solidité I |
| `timber` Timber | Casser une bûche fait tomber tout l'arbre (accroupi : seulement la bûche ; une construction en bûches sans feuillage ne tombe pas) |
| `fastsmelting` Fast Smelting | Les fours cuisent 4 fois plus vite |
| `vanillaplus` Vanilla+ | Le gravier donne toujours du silex ; tous les feuillages donnent des pommes (3 %) |
| `cateyes` Cat Eyes | Vision nocturne permanente pendant la partie |

### Armes fabriquées

Personne ne fabrique d'épée : elles viennent du personnage, ou d'une recette du plugin (les épées d'Aura). Les haches se fabriquent et s'enchantent comme des outils, mais un coup de hache fabriquée ne blesse pas un joueur ; la hache d'un personnage reste une arme. Arcs, arbalètes, tridents et masses ne se fabriquent pas non plus, sauf par Shoja.

### Force

La Force est réduite : chaque niveau donne +1 point d'attaque (un demi-cœur) au lieu des +3 de Minecraft, donc +1 en Force I, +2 en Force II, etc. Cela vaut pour la potion et l'effet de Force comme pour les bonus de Force des personnages (« Force 0.5 » donne +0,5). La règle `force` remet une autre valeur (3 : celle de Minecraft) ; elle s'applique tout de suite, même à ceux qui ont déjà l'effet.

### Minerais multipliés

Sous terre, il y a 5 fois plus de minerais (charbon, fer, cuivre, or, redstone, lapis, diamant, émeraude) : la première fois qu'une zone du monde de jeu est chargée, chacun de ses filons est recopié quatre fois ailleurs dans la même zone, à peu près à la même profondeur, dans la roche. Chaque zone n'est enrichie qu'une fois (elle en garde la marque), y compris dans un monde déjà exploré. La règle `minerais` change le multiplicateur (1 : rien) pour les zones pas encore enrichies ; pour l'appliquer partout, créer une nouvelle map.

### Nouvelle map

`/cf regenere` (hors partie) crée un nouveau monde de jeu, avec une graine au hasard ou celle donnée (`/cf regenere 12345`), y amène tous les joueurs sur son lobby et l'inscrit dans `config.yml` (`world.name`, un nom en `crossfight_map_…`). Le serveur se fige une dizaine de secondes pendant la création. L'ancienne map est effacée du disque si c'était déjà une map créée par cette commande ; le monde principal du serveur (`world`) n'est jamais effacé, il n'est simplement plus utilisé. Le Nether et l'End restent ceux du serveur (ils ne sont pas recréés) et leurs portails ramènent vers la map de jeu.

### Lieux posés sur la map

La map n'est pas un simple monde de Minecraft : elle rassemble des lieux venus de séries différentes (des maps de la communauté : une ville, une académie, un monastère...), posés à des endroits tirés au sort avant la partie, comme des bouts de monde arrachés et enfoncés là de force. Chaque lieu est un dossier de `plugins/CrossfightUHC/structures/` : son monde d'origine (`monde/` : la map telle qu'on la télécharge, de la version du serveur ou d'une version plus ancienne, que le serveur met à jour en la lisant) et sa fiche (`structure.yml`). Ces dossiers ne sont pas dans ce dépôt (ce sont les œuvres de leurs auteurs) et se déposent à la main sur le serveur.

- **Installer les lieux** : ils ne viennent pas avec le plugin. Sans eux, la map est un monde ordinaire et la carte (`/cf map`) ne montre que l'arène. Décompresser l'archive de chaque lieu dans `plugins/CrossfightUHC/structures/` : un dossier par lieu (`structures/jouki/structure.yml`, `structures/jouki/monde/`...). Un dossier de trop, comme en laisse une décompression « dans un dossier au nom de l'archive » (`structures/jouki/jouki/...`), est toléré. Puis redémarrer le serveur, ou `/cf structure disperser` : les lieux se posent l'un après l'autre (une minute environ chacun) et apparaissent sur la carte au fur et à mesure, que la partie soit lancée ou non. `/cf structure liste` dit ceux que le serveur a trouvés et où ils sont posés ; tant qu'une map n'a aucun lieu, `/cf map`, `liste` et `disperser` disent pourquoi à un admin (aucun dossier installé, lieux désactivés, pose pas encore faite).

- **Quand** : au démarrage du serveur et à chaque nouvelle map (`/cf regenere`), les lieux qui ne sont pas encore sur la map sont posés l'un après l'autre (`/cf structure disperser` le relance). L'endroit, la découpe et l'avancement sont retenus par monde dans `config.yml` : un lieu n'est posé qu'une fois par map, et une pose interrompue par un arrêt du serveur reprend au démarrage. Au lancement de la partie, les lieux et leurs coordonnées sont annoncés.
- **Le lieu du centre** : celui que nomme `structures.centre` (`jouki` par défaut) est toujours au milieu de la map, posé avant les autres. L'arène de Regna Ferox n'est donc plus au centre : sur une map neuve, elle est bâtie à un endroit tiré de la graine, à 450-650 blocs du centre (une map qui a déjà son arène la garde où elle est).
- **Les lieux s'adaptent à la bordure** : à la création de la map, la part de chaque lieu est comptée d'après le rayon de la bordure (règle `bordure`) et le nombre de lieux. Ils se partagent trois dixièmes de la surface de la map, à parts égales (un côté multiple de 16 : 384 blocs pour sept lieux et une bordure de 1 000, 800 pour 2 000, 1 216 pour 3 000). Un lieu plus grand que sa part est recadré autour de son cœur : petite bordure, des morceaux de monde ; grande bordure, des lieux entiers. `structures.taille-max` fixe ce côté à la main (0 : d'après la bordure), `/cf structure liste` le rappelle. Changer la bordure ne recoupe pas les lieux déjà posés : la découpe vaut pour la map, il faut `/cf regenere` pour une nouvelle.
- **Où** : dans la bordure (à `structures.marge` blocs du bord, 60), à plus de `structures.arene` blocs (160) de l'arène, sans chevaucher un autre lieu, de préférence hors des océans et des rivières (le biome est lu sans générer le terrain). Le tirage dépend de la graine de la map : la même map donne les mêmes emplacements.
- **Comment** : le coin du lieu est aligné sur les chunks, et son niveau du sol posé à la hauteur médiane du terrain. Chunk par chunk (chargés sans figer le serveur), le terrain est dégagé au-dessus du niveau du sol, les blocs du monde d'origine copiés tels quels, sans mise à jour (rien ne tombe ni ne se casse), puis le dessous comblé jusqu'au terrain (herbe à l'air libre, terre, puis pierre). Ce que le lieu laisse vide sous son niveau ne remplace rien ; ses sous-sols sont remplis par le terrain, sauf si sa fiche dit `sous-sol: creuse`. Les barrières invisibles de la map d'origine ne sont pas copiées (`/cf structure barrieres` les retire des lieux déjà posés). Les panneaux, bannières et têtes gardent ce qu'ils portent ; les coffres arrivent vides et les blocs de commande sans leur commande ; les cadres, tableaux, porte-armures et affichages sont copiés, pas les animaux ni les villageois. Un lieu de 384 × 384 blocs se pose en une minute environ.
- **Le socle : un lieu ne flotte pas.** Beaucoup de maps sont des coquilles : un sol d'un bloc bâti très au-dessus du sol plat d'un monde vide, une dalle posée sur rien (le monastère de Garreg Mach), une colline creuse (Shiganshina), un lac tenu par une vitre (Pearl). Posées telles quelles, elles flottent et l'on se promène dessous. Avant de poser un lieu, sa découpe est donc lue en entier (une quinzaine de secondes, sans figer le serveur), et le plugin en tire une décision, résumée dans le chat de l'admin (« Socle : … ») :
  - *Le sol plat résiduel.* Si le bas de la map est un sol plat sur lequel rien n'est posé (moins d'une colonne sur cinquante), c'est le reste d'un monde vide : il est retiré, le lieu est ce qu'il y a au-dessus, et les colonnes qui ne portaient que lui ne sont pas posées (le terrain de la map y reste).
  - *Le niveau.* La hauteur du lieu posée au niveau du terrain est celle de son vrai sol (le quart bas des hauteurs de sol, l'eau comptant comme un sol), et non plus celle de la fiche : Arcadia est posée 27 blocs plus bas qu'avant, Terrace 85, Pearl au niveau de son sol vitré.
  - *Le vide sous le sol.* Un espace couvert, sans rien au sol ni lumière, au sol de terre, est comblé (terre sous ce qu'il porte, puis roche) si c'est une poche fermée, un vide où l'on ne tient pas debout (vide sanitaire, double sol), une grande étendue basse (le dessous de la dalle de Garreg Mach), ou le dessous d'un sol naturel (une colline creuse). Jamais une pièce (fermée, avec portes ou fenêtres, ou qui touche un espace meublé), une halle haute, le dessous d'un toit de maison ni celui d'un arbre.
  - *Ce qui flotte.* Sous une partie du lieu plus haute que son niveau et sans rien dessous, on comble jusqu'au terrain si elle est massive et tient au corps du lieu (à 32 blocs du niveau au plus) ; un îlot détaché, une lanterne, une frondaison restent en l'air, comme leur auteur les a voulus.
  - *Les creux.* Là où le lieu descend sous son niveau à ciel ouvert (rivière, douve, cour basse, jusqu'à 12 blocs), le terrain est creusé jusqu'à son sol au lieu de l'enterrer.
  - Sous un sol de verre, la dernière case comblée reprend le sol d'origine (le sable de Pearl), pour qu'on le voie toujours à travers.
  La règle est la même pour tous les lieux, sans réglage par lieu ; elle vaut pour les lieux posés après la mise à jour (une nouvelle map : `/cf regenere`), pas pour ceux déjà posés.
- **Entrer dans un lieu, et sa quête** (`structures.quetes`, oui par défaut) : le nom du lieu s'affiche au milieu de l'écran, avec le nom de sa quête dessous ; tant qu'on y est, un panneau de quête (un parchemin, dessiné par `python3 tools/build_quete_panneau.py`) s'affiche au-dessus du tableau de bord, avec le nom de la quête et où elle en est ; sans le pack, ces lignes s'écrivent en tête du tableau. Dans le pack complet, le panneau est refait avec le [RPG UI pack de Franuka](https://franuka.itch.io/rpg-ui-pack) (`python3 tools/build_quete_franuka.py` : cadre pêche, bannière « QUÊTE », police de texte du pack, hors dépôt comme le menu d'Anna). Les deux lignes sont centrées dans le panneau : le plugin ne connaît pas la largeur des lettres, alors il part du milieu, recule de la moitié du texte (police `quete_demi`, où chaque lettre recule de la moitié de sa largeur), l'écrit, puis revient au milieu (`quete_recul`, `quete_demi2`). En bas du panneau, une jauge (un curseur du pack, police `quete_jauge` : onze images, de vide à pleine) dit où en est la quête, par dixièmes ; pleine quand elle est terminée, vide tant qu'on n'a pas parlé à Anna ou pour les quêtes sans compte. Les lettres n'ont plus à être de même largeur : pour revenir au début d'une ligne, le plugin réécrit le texte dans la police `quete_recul`, dont chaque caractère recule de sa propre largeur. Près du centre du lieu se tient le personnage qui donne la quête : un mannequin à l'image d'un joueur, au skin `pnj` de `skins.txt` (`python3 tools/sign_skins.py <dossier contenant pnj.png>` pour le changer), en surbrillance (on le voit à travers les murs). C'est partout Anna, la marchande venue par la Fissure Temporelle ; elle se tient près de la borne, sauf là où sa place est choisie (à Garreg Mach : sur le quai, devant le ponton de l'étang) ; de près (une douzaine de blocs), elle tourne la tête vers le joueur le plus proche (à dix blocs), et une bulle de dialogue au-dessus d'elle dit ce qu'elle attend ; la quête finie, la bulle explique la récompense (chaque joueur voit celle de son équipe), et Anna le redit dans le chat. **Le service d'un lieu est verrouillé tant que l'équipe n'a pas fini sa quête** ; une quête finie le débloque pour toute l'équipe jusqu'à la fin de la partie. Un clic droit sur le personnage lance la quête pour l'équipe ; chaque équipe fait la sienne, le progrès est commun à ses membres. La bulle d'Anna est dessinée (`python3 tools/build_bulle.py` : une image de police par nombre de lignes, que le plugin écrit sur la première ligne d'un texte flottant, le texte centré dessous ; dans le pack complet, `python3 tools/build_bulle_franuka.py` la compose avec le cadre clair du pack de Franuka) ; sans le pack, le texte reste sur fond blanc.
  - *Garreg Mach, La partie de pêche* : Anna donne une canne à chaque membre de l'équipe ; il faut prendre dix poissons à l'étang du monastère. Tant que la quête est en cours, le bord de l'étang (l'eau la plus proche d'Anna) scintille pour l'équipe, et pour elle seule. La prise se joue en plusieurs temps. Clic droit pour lancer la ligne : le bouchon part vraiment et doit tomber dans l'eau (sur la berge, il faut relancer ; un clic droit avant la touche remonte la ligne). Une à quatre secondes après son arrivée dans l'eau, le bouchon plonge et « ÇA MORD ! » s'affiche au milieu de l'écran : il faut faire clic droit le plus vite possible (en moins d'une demi-seconde, le ferrage éclair entame déjà le poisson ; passé deux secondes, il lâche l'appât et revient plus tard). Une barre de boss apparaît alors en haut de l'écran : la résistance du poisson (8 à 12 points). Vient le jeu des cercles, manche après manche : au milieu de l'écran, une cible à trois zones emboîtées (dessinée par `python3 tools/build_peche.py`) et un cercle qui se resserre en une seconde et demie ; un clic droit l'arrête. Dans la zone extérieure, « Bien » retire 2 points ; dans la deuxième, « Très bien » en retire 3 ; au cœur, « Parfait » en retire 4, et jusqu'à 6 quand les Parfaits s'enchaînent. Trop tôt (avant la cible) ou trop tard (le cercle s'est refermé seul), le poisson perd quand même 1 point : on ne rate jamais une prise, on la réussit seulement plus lentement. Quand la barre est vide, le poisson est pris. Sans le pack, le cercle est remplacé par deux crochets et le nom de la zone au-dessus de la barre d'objets. La ligne reste à l'eau pendant toute la prise et remonte quand le poisson est pris. Rien d'autre ne mord à cette canne (pas de pêche ordinaire), et elle est reprise à la fin de la quête.
  - *Shiganshina, La chasse au Titan* : un Titan apparaît près d'Anna ; c'est un mini-boss (`game/TitanBoss.java`). Son corps est un mannequin à l'image d'un joueur, grandi six fois et demie (près de douze blocs de haut), qui porte le skin `titan_quete` (signé dans `skins.txt` par `tools/sign_skins.py` ; l'image reste dans `resourcepack-prive/skins/`). Il ne prend pas de dégâts : il tombe en cent coups. Chaque coup d'un joueur de l'équipe compte pour un (un par demi-seconde et par joueur au plus ; les coups d'une autre équipe ne comptent pas), et une barre en haut de l'écran dit combien il en reste. Entre deux attaques, il se tourne vers le joueur le plus proche et s'en approche à pas lents ; toutes les trois secondes, il annonce au hasard une de ses cinq attaques (jamais deux fois la même de suite) : comme pour Omori, son nom s'affiche dans une seconde barre, sous la sienne, qui se vide pendant les deux secondes de l'annonce (le Titan s'arrête et se tourne vers sa cible), puis l'attaque part. *Charge titanesque* : il s'arrête, un couloir de danger rouge apparaît devant lui (16 blocs de long, 5 de large) pendant un peu plus de deux secondes, puis il fonce : 2 cœurs et une projection pour qui s'y trouve. *Lancer de pierres* : il se baisse, arrache des rochers au sol, se redresse et les lance d'un grand coup de bras (trois rochers montent dans le ciel) ; trois fois de suite, un rocher redescend sur chaque joueur, là où un cercle orange l'annonce près de deux secondes avant : 2 cœurs dans 2,5 blocs. *Regard terrifiant* : « Le Titan te fixe » s'affiche ; qui le regarde encore une seconde et demie plus tard (à 40 degrés près, sans mur entre eux) est pétrifié 10 secondes (immobile, sans saut, sans force). *Cri strident* : nausée et lenteur pendant 10 secondes, dans 20 blocs (l'annonce laisse le temps de s'éloigner). *Course du Titan* : pendant 10 secondes, il court après un joueur à la vitesse d'un joueur ; chaque fois qu'il le rattrape, 1 cœur (une fois par seconde au plus). Le Titan casse les blocs : ce qui lui barre la route quand il marche, charge ou court (sur 12 blocs de haut), et un cratère là où tombe une pierre ; l'eau et ce qui ne se casse pas restent. Ses dégâts sont bruts, comme ceux d'une capacité : l'armure et ses enchantements ne les réduisent pas (`abilities.ignore-armor`). Hors partie, où le plugin annule tous les dégâts, il ne blesse pas.
  - *Arcadia, La récolte* : un casse-briques (`game/Breakout.java`). Parler à Anna dresse devant elle un tableau que seul le joueur voit : un mur de quarante briques, une raquette qui suit le viseur, une balle qu'un clic lance. Certaines briques portent une pomme, qu'on voit dessus, et dessinent un motif (un V, un losange, deux colonnes, un damier, une croix, un escalier : huit à douze briques) : cassée, la brique laisse tomber sa pomme, et il faut la rattraper avec la raquette. Chaque pomme rattrapée compte pour l'équipe (chacun joue sur son tableau) ; à quinze, le lieu est débloqué. La balle perdue revient sur la raquette, sans autre perte. Toutes les briques à pomme cassées, le mur revient en entier avec le motif suivant, et la balle va un peu plus vite. S'accroupir ferme le tableau, reparler à Anna le rouvre. `/cf enigme briques` (admins) l'ouvre sans la quête.
  - *Central Town, Les quatre piliers* : quatre piliers éteints apparaissent autour d'Anna, chacun avec une énigme différente. Un clic droit sur un pilier dresse devant lui un tableau que seul le joueur voit (`game/Panel.java` : des affichages de texte et d'objets dans un plan tourné vers lui ; le viseur est le point où son regard coupe ce plan). Tant que l'épreuve est ouverte, ce joueur ne voit plus le pilier (ni sa lueur ni son étiquette), pour que rien ne gêne le tableau ; il reparaît à la fermeture. On y joue en visant et en cliquant (clic gauche ou droit) ; s'accroupir le ferme ; l'énigme résolue, le pilier se rallume. Toutes sont tirées au hasard et toujours solubles. `/cf enigme <tablette|circuit|etabli|puzzle>` (admins) en ouvre une devant soi, sans la quête.
    - **La tablette** (`game/Tablet.java`) : à gauche la figure à obtenir, à droite une grille de 4 x 4, en bas trois pièces dorées. On attrape une pièce, elle suit le viseur (calée sur les cases au-dessus de la grille), on clique pour la poser ; F la tourne ; on reprend une pièce posée en cliquant dessus. Là où deux pièces se recouvrent, les cases s'annulent (elles s'assombrissent). Résolue quand les trois pièces sont posées et que la grille montre la figure.
    - **Le circuit** (`game/Circuit.java`) : une grille de 4 x 4 tuiles portant des fils, une source (orange) et deux lampes (rouges). Un clic fait pivoter une tuile d'un quart de tour ; le courant part de la source et avance le long des fils qui se touchent. Résolu quand il atteint les deux lampes.
    - **L'établi** (`game/Workbench.java`) : un objet est montré, à côté d'une table de craft vide et de six ingrédients (ceux de la recette et des intrus). On prend un ingrédient, chaque clic sur une case en pose un ; un clic sur une case garnie la vide. Résolu quand la table montre la recette de l'objet (n'importe où sur la table, à l'endroit ou en miroir). Vingt-quatre recettes du jeu.
    - **Le puzzle** (`game/Jigsaw.java`) : une image de 12 x 12 points découpée en neuf pièces mélangées, le cadre vide et le modèle en petit. On prend une pièce, on la pose dans le cadre ; poser sur une case occupée renvoie l'autre pièce. Résolu quand le cadre montre l'image. Avec le pack, l'image est un tableau de Minecraft tiré parmi neuf (police `puzzle`, `python3 tools/build_puzzle_font.py` : elle désigne les textures de tableaux du jeu lui-même, découpées en neuf lettres, sans en copier aucune) ; sans le pack, une des quatre images dessinées dans le code (un cœur, une épée, un arbre, une maison).
    - `game/Puzzles.java` tient l'énigme ouverte de chaque joueur (circuit, établi, puzzle) et lui passe ses clics, la touche F et les ticks.
    - Habillage : avec le pack d'interface (pack complet, `python3 tools/build_enigmes_franuka.py` : police `enigme`), la fenêtre des tableaux est celle du pack, leurs cases sont les siennes (une image ramenée au blanc que le plugin teinte selon l'état : vide, visée, dorée, annulée), les zones de pose ont son cadre clair et les textes sont sombres, dans la police du panneau de quête (`quete_texte`) plutôt que celle du jeu. Le pack donne aussi une main qui suit le viseur sur le tableau, un bandeau sous le titre de chaque épreuve, et ses orbes pour la source et les lampes du circuit. Sans cette police, les tableaux sont en rectangles bleu nuit.
  - *Pearl, Les perles englouties* (5), *Beacon, L'initiation* (4 reliques) : des objets lumineux apparaissent au cœur du lieu (autour de sa borne, dans un rayon d'un cinquième de son côté, jamais sur un fond vitré), et tintent quand on est à moins de 32 blocs (de plus en plus fort en approchant, du côté où se trouve l'objet) ; il suffit de passer dessus.
  - *Forest of Hope, Les pièces du vaisseau* : trois pièces à rapporter au capitaine, une par une ; qui en porte une est ralenti, et s'il tombe la pièce retourne où elle était.
  - *Terrace, L'ascension* : une montée par cinq drapeaux, visibles à travers les murs. Le dernier est au point le plus haut du lieu ; les quatre autres sont plantés à hauteurs régulières entre Anna et lui, chacun sur le sol le plus proche de sa hauteur et de la ligne qui mène au sommet (à six blocs au moins les uns des autres). On passe par les quatre premiers dans l'ordre qu'on veut (à trois blocs), mais le drapeau du sommet ne compte qu'une fois tous les autres passés. Un seul membre de l'équipe suffit, et les drapeaux passés par l'un comptent pour tous.
  - *Le quartier général* (le lieu `jouki`, au centre de la map ; son nom se change par `structures.noms.jouki`) : pas de quête, une offre (`game/Headquarters.java`). Anna le cède à une équipe contre 5 diamants, 10 pommes dorées ou 20 niveaux d'expérience, au choix. Une seule équipe le tient à la fois : une autre qui paie le lui prend (l'ancienne est prévenue). Le panneau du lieu dit s'il est libre, occupé, ou à son équipe.
    - **La carte** : Anna donne la carte de la map (`/cf map`) à l'équipe. Tant qu'un de ses membres est dans le lieu, sa carte montre en rouge, avec leur nom, tous les joueurs en vie des autres équipes.
    - **La fiche d'un joueur** : un clic droit avec la carte ouvre la visée (une croix suit le regard, comme pour la téléportation des admins) ; un clic sur la marque d'un joueur ouvre sa fiche : son équipe, son personnage, ses vies, ses kills, sa position et sa distance, son armure et sa main gauche, tout son inventaire.
    - **La traque** : la fiche propose une boussole qui pointe vers ce joueur (mise à jour chaque seconde ; une par cible et par traqueur). Le joueur traqué le sait : un message, puis une barre rouge « Tu es traqué » tant qu'une boussole le suit. La boussole disparaît si son équipe perd le lieu ou si la cible est éliminée ; jetée, elle est détruite.
    - Admins : `/cf qg` (parler à Anna), `/cf qg prendre` (sans payer), `/cf qg liberer`, `/cf qg voir <joueur>` (sa fiche).
  - Les potions (Pearl) n'ont pas de borne : la quête finie, un clic droit sur Anna ouvre sa boutique, gratuite. On y choisit une potion parmi cinq (Force et Vitesse à boire ; Poison, Faiblesse et Lenteur à lancer), une toutes les 10 minutes par joueur. Avec le pack, ce menu est habillé comme celui des enchantements (troisième image de `tools/build_menu_anna.py`, et de `tools/build_menu_franuka.py` dans le pack complet) : la carte d'Anna à gauche, et à droite la liste des cinq potions, une case et un nom par ligne.
  - Ce qui est verrouillé : la borne de forge, la boutique de potions d'Anna, l'expérience de Terrace, la chasse aux pièces de Forest of Hope ; la caisse garantie de Beacon ne tombe que si une équipe au moins a fini sa quête.
  - **Les places d'un lieu** (`structures.equipes-par-lieu`, 2 par défaut ; 0 : pas de limite) : un lieu ne garde le privilège que de ce nombre d'équipes. En se lançant dans une quête, l'équipe apprend combien de places restent. Quand une équipe termine, tous les autres joueurs en sont avertis (qui, quel lieu, quel privilège, combien de places restent). Si le lieu est complet, l'équipe qui s'y lance est prévenue, et celles qui le tiennent reçoivent un avertissement (« Privilège menacé ») : c'est le moment de venir le défendre. Si elle termine quand même, un menu lui montre les équipes qui tiennent le lieu : elle choisit laquelle perd son privilège (tant qu'elle n'a pas choisi, le sien n'est pas ouvert ; reparler à Anna rouvre le menu). L'équipe écartée est prévenue, perd l'accès au privilège, et peut refaire la quête pour reprendre sa place. Un joueur sans équipe compte pour une équipe.
  - **Essayer** (admin, même hors partie) : `/cf quete` liste les quêtes et leur état ; `/cf quete tp <lieu>` (ou `/cf tp quete <lieu>`) amène devant le personnage, tourné vers lui ; `/cf quete lancer <lieu>` la lance ; `/cf quete parler <lieu>` parle à Anna d'où que l'on soit (son menu s'ouvre si la quête est faite) ; `/cf quete montrer <lieu>` (ou `/cf pearl glowing` pour Pearl) donne les coordonnées de ce qu'il reste à trouver, avec un « y aller » cliquable, et dresse une colonne de lumière sur chaque objet pendant une minute ; `/cf quete finir <lieu>` la termine d'office ; `/cf quete oublier [lieu]` la remet à zéro. `/cf quete placer` fait venir Anna là où se tient l'admin, tournée comme lui (ou `/cf quete placer <x> <y> <z> [regard]` à un endroit donné de la map) (l'endroit est noté dans la map d'origine du lieu, sous `structures.anna-origine` : il vaut sur toute map, quelle que soit la découpe du lieu) ; `/cf quete replacer` la renvoie à sa place d'origine. Sans nom de lieu, c'est celui où l'on se trouve.
- **À quoi sert chaque lieu** (`structures.services`, oui par défaut) : un lieu n'est pas qu'un décor, on y vient préparer son combat, et l'on s'y croise. Au milieu de chaque lieu qui rend un service, une borne (un objet qui flotte sous son nom) : un clic droit dessus, pendant la partie, rend le service (un admin peut s'en servir hors partie, pour essayer). `/cf map` rappelle le service de chaque lieu. Qui fait quoi se règle dans `structures.utilites` (`config.yml` : le dossier du lieu, puis le service) :

  | Service | Lieu par défaut | Ce qu'il fait |
  |---|---|---|
  | `enchantement` | Garreg Mach | pas de borne : la quête faite, parler à Anna ouvre un premier menu avec une seule case, « pose ton objet ici » : un clic sur l'objet dans son inventaire l'y pose (une pièce d'armure portée doit d'abord être retirée). Un second menu s'ouvre alors avec l'objet et trois niveaux au choix : I pour 1 niveau, II pour 2 niveaux, III pour 5 niveaux. Armure : Protection (III au plus, II en diamant ou netherite) ; épée et hache : Tranchant (mêmes plafonds) ; arc : Puissance (II au plus) ; pioche et pelle : Efficacité (III au plus). Un niveau trop fort pour la pièce, ou qu'elle a déjà, ne se choisit pas ; un clic sur l'objet revient au premier menu. Les armes de personnage et les objets du plugin n'y passent pas. Avec le pack, les deux coffres sont habillés par leur titre (`python3 tools/build_menu_anna.py` : une carte dorée dessinée ici ; dans le pack complet, `python3 tools/build_menu_franuka.py` les compose avec le [RPG UI pack de Franuka](https://franuka.itch.io/rpg-ui-pack), pack payant dont les fichiers restent dans `resourcepack-prive/ui-franuka/`, jamais sur le dépôt) |
  | `rythme` | Shiganshina | pas de borne : la quête faite, parler à Anna ouvre sa forge (`game/Rhythm.java`), où l'on forge ses matériaux en musique : un jeu de rythme à quatre touches, chaque note frappée étant un coup de marteau. Le menu est habillé comme ses autres menus (image `menu_anna4.png`, `tools/build_menu_anna.py` ; avec le pack d'interface, `tools/build_menu_franuka.py`) : la carte d'Anna à gauche, et à droite trois musiques sous chaque difficulté. Choisir une musique emmène le joueur dans une salle entièrement noire, où il n'y a qu'une enclume (un monde vide à part, `crossfight_forge`, une salle par joueur ; rien ne s'y casse et l'on n'y craint rien) : à chaque note frappée son bras droit part et des étincelles jaillissent de l'enclume. Il revient d'où il venait quand le panneau de résultat se ferme, ou dès qu'il s'accroupit. Neuf musiques, trois par difficulté (facile, moyen, difficile ; 38 à 40 secondes chacune). Un tableau haut de sept blocs se pose à sept blocs devant le joueur, que lui seul voit : quatre couloirs où les notes tombent de 5,6 blocs de haut (en 2 secondes au facile, 1,6 au moyen, 1,3 au difficile). Avec le pack, le tableau et les notes sont des images (`python3 tools/build_rythme.py` ; dans le pack complet, `python3 tools/build_rythme_franuka.py` les compose avec le pack de Franuka : sa fenêtre, ses touches, un de ses boutons recoloré pour les notes) ; sans lui, des rectangles de couleur. il appuie sur les touches 1, 2, 3 et 4 du clavier quand une note touche la ligne du bas (le plugin lit le changement de case de la barre d'objets et l'annule : la main ne bouge pas ; le retard du réseau est compensé). Chaque couloir est teinté à la couleur de ses notes. Une note frappée éclate (une étoile qui grossit et s'efface, des étincelles), son couloir s'illumine un instant comme si de la lumière montait de la ligne, un coup d'enclume et une cloche sonnent, et l'appréciation (« Parfait », « Bien », « Juste », « Raté ») s'écrit en haut de la piste. Le combo en cours (« x12 », avec le mot « combo » dessous) s'affiche à droite de la piste ; un combo d'au moins trois notes qui se brise s'entend (un bruit de verre). Ces textes sont dans la police du panneau de quête (`font/quete_texte.json`). Sous la ligne, une grande touche par couloir, marquée 1 à 4 : elle s'enfonce un instant quand le joueur appuie dessus, son chiffre à la couleur du couloir. Un accord (plusieurs notes au même instant) compte en entier dès qu'une de ses touches est frappée : quand deux touches de la barre d'objets sont enfoncées dans le même instant, le jeu n'en envoie qu'une au serveur. À deux ticks près, « Parfait » (300 points) ; à quatre, « Bien » (200) ; à six, « Juste » (100) ; au-delà, la note est manquée. Le score est la part du maximum : 55 % pour un C, 75 % pour un B, 90 % pour un A. Récompenses — facile : C 5 fer, B 5 fer et 5 or, A 5 fer, 10 or et 1 diamant ; moyen : C 10 fer et 10 or, B 10 fer, 20 or et 1 diamant, A 10 fer, 20 or et 2 diamants ; difficile : C 10 or et 1 diamant, B 10 or et 2 diamants, A 20 or et 3 diamants. Les récompenses s'additionnent : chaque note atteinte donne la sienne, un A donne donc celles du C, du B et du A (au difficile : 40 or et 6 diamants ; au moyen : 30 fer, 50 or et 3 diamants ; au facile : 15 fer, 15 or et 1 diamant). Chaque récompense ne se reçoit qu'une fois par musique, par joueur et par partie : rejouer ne rapporte que celles des notes pas encore atteintes. S'accroupir, ou s'éloigner de six blocs, arrête la musique sans rien compter. À la fin, un panneau de résultat prend la place du tableau pendant une dizaine de secondes : un anneau se remplit jusqu'au score (en deux secondes et demie), il change de couleur à chaque palier, la note en son centre monte de C à B puis A, et la ligne des matériaux de ce palier s'allume avec un coup d'enclume ; dessous, le détail des frappes et le plus long combo (les matériaux sont donnés dès la fin de la musique : le panneau ne fait que le montrer ; `/cf rythme resultat <score>` l'affiche pour essai, sans rien donner). Les musiques du plugin sont composées par lui (une mélodie tirée d'une graine, toujours la même, jouée avec les sons de blocs musicaux sur une basse et une batterie) : aucun fichier, rien de repris d'un jeu. On peut les remplacer par des cartes d'osu!mania à quatre touches : `python3 tools/import_osu.py <carte.osz> <facile|moyen|difficile>` écrit la musique dans le pack privé (`sounds/music/forge_<nom>.ogg`) et la carte dans `prive/musiques/<nom>.txt`, à déposer dans `plugins/CrossfightUHC/musiques/` ; après `python3 tools/build_pack.py` et `/cf rythme recharger`, les musiques importées prennent la place de celles du plugin, trois par difficulté au plus (une note tenue devient une note simple ; la musique et la carte appartiennent à leurs auteurs et restent hors dépôt). Essai : `/cf rythme` ouvre le menu, `/cf rythme <musique>` en joue une, `/cf rythme recharger` relit les musiques importées |
  | `forge` | (aucun lieu par défaut) | l'objet tenu est réparé, contre 2 niveaux. C'était le service de Shiganshina avant la forge en musique : une configuration qui dit encore `shiganshina: forge` est lue comme `rythme`, sauf si `structures.forge-a-shiganshina` vaut `true` |
  | `potions` | Pearl | une potion tirée au sort (rapidité, résistance au feu, saut, chute lente, vision nocturne, respiration, plus rarement force), toutes les 8 minutes |
  | `marchand` | Central Town | pas de borne : la quête faite, parler à Anna ouvre son étalage. Elle y vend quatre objets de lootbox différents, chacun tiré aux chances des lootbox (tier 1 : 45 %, tier 2 : 35 %, tier 3 : 19 %, tier 4 : 1 %), et l'étalage change toutes les dix minutes (le même pour tous ; une horloge dans le menu dit le temps restant). On paie en diamants, d'autant plus que l'objet est rare : 2, 4, 6 et 8 diamants du tier 1 au tier 4 (`structures.marchand.prix-par-tier`). Un seul exemplaire de chaque objet par joueur et par étalage. Le mot « Inventaire » que le jeu écrit au-dessus de l'inventaire du joueur est effacé par le pack dans toutes les langues (`python3 tools/build_lang.py` : un texte vide pour `container.inventory`), ce qui vaut pour tous les coffres et conteneurs tant que le pack est chargé. Le menu est habillé comme ses autres menus (image `menu_anna5.png`) : la carte d'Anna à gauche, les quatre objets et l'horloge à droite. `/cf marchand` (admins) ouvre l'étalage sans la quête, `/cf marchand nouveau` le refait tout de suite |
  | `nourriture` | Blossoming Arcadia | pas de borne : la quête faite, parler à Anna ouvre sa roulette, une fois toutes les dix minutes par joueur. Une rangée de piles de pommes dorées défile, de moins en moins vite, et s'arrête : la pile du milieu est le lot, de 3 à 10 pommes dorées au hasard. Fermer le menu avant l'arrêt donne le lot tout de suite. Le menu est habillé comme les autres menus d'Anna (image `menu_anna7.png`). `/cf roulette` (admins) l'ouvre sans la quête |
  | `experience` | Sun-Speckled Terrace | pas de borne : tant qu'on est dans le lieu, pendant la partie, on gagne 2 points d'expérience par seconde |
  | `lootbox` | Beacon Academy | pas de borne : à l'événement Lootbox, une lootbox de plus y tombe à coup sûr, annoncée avec les autres |
  | `chasse` | Forest of Hope | pas de borne : cinq pièces sont cachées dans le lieu, au sol, et scintillent quand on est à moins de douze blocs. En trouver une (passer dessus) donne 2 diamants et de l'expérience, et le serveur l'annonce ; à la troisième, le joueur reçoit le contenu d'une lootbox. Une pièce trouvée reparaît ailleurs six minutes plus tard |

  Les armes des personnages et les objets du plugin ne passent ni à l'enchantement ni à la forge. Le quartier général (au centre) et l'arène de Ferox gardent leur rôle. L'événement Match Found, pas encore conçu, se tiendra dans l'un des lieux de `game.events.match-found-lieux` (`glitch` et `lotus` par défaut), tiré au sort parmi ceux qui sont sur la map : son annonce le nomme déjà.
- **Pas un carré** (`structures.contour`, oui par défaut) : un lieu n'est pas posé en rectangle. Son contour est une tache arrondie inscrite dans sa boîte, dont le rayon change sans cesse le long du tour (de grandes ondulations, de plus petites par-dessus) : pour un lieu recadré, presque une ellipse, rentrant jusqu'à un tiers ; pour un lieu posé en entier, un rectangle aux coins très arrondis, au bord à peine rentré, pour garder presque tout ce qu'il contient. Hors du contour, le terrain de la map reste tel qu'il est, et la faille suit le contour. Chaque lieu a sa forme, tirée de la graine de la map. Le contour est décidé à la pose : les lieux déjà posés sur une map restent comme ils sont, il faut une nouvelle map (`/cf regenere`).
- **Monde déchiré** (`structures.dechirure`) : autour de chaque lieu posé, le monde est fendu, et par la déchirure on voit son cœur qui bat. Une faille massive (9 à 18 blocs de large) longe le contour du lieu et s'interrompt par endroits pour laisser passer. Son bord est une croûte d'obsidienne, qui pleure par endroits ; dedans, un lit de verre de toutes les couleurs, en creux vers le milieu, par taches de quelques blocs ; au milieu, par endroits, le verre manque : c'est le vide, un puits de cinquante blocs. Avec `structures.lueur` (oui par défaut), le verre est éclairé par-dessous (une lumière par lieu) et par une lumière invisible posée dessus : il brille de jour comme de nuit et la neige ne s'y pose pas. Près des joueurs, le cœur bat : le verre change de couleur par vagues (un changement montré au joueur seulement, les blocs ne bougent pas), des étincelles montent, un trait de lumière jaillit de temps en temps, et un battement sourd s'entend toutes les deux secondes. Le verre et la lumière des failles ne se ramassent pas. Sur une map déjà créée, les failles d'avant sont refaites au démarrage suivant du serveur, un lieu après l'autre (quelques minutes en tout).
- **La carte** : `/cf map` met dans la main une carte qui dit où est quoi. Toute la map y tient, d'un bord à l'autre de la bordure de départ, le nord en haut : chaque lieu posé est un rectangle à sa couleur, avec une bannière et son nom ; l'arène de Ferox a son icône ; le cadre rouge est la bordure du moment, qui se resserre. Le pointeur blanc est celui qui tient la carte (il tourne avec lui), les marques bleues sont ses coéquipiers ; les adversaires n'y sont jamais. La commande rappelle aussi dans le chat les coordonnées de chaque lieu, reprises dans la description de l'objet. C'est une seule carte de Minecraft pour tout le serveur (son numéro est gardé dans `config.yml`), dessinée pour chaque joueur.
- **Lieu complet** : le menu où l'équipe qui vient de finir la quête choisit laquelle perd son privilège est habillé comme les menus d'Anna (image `menu_anna6.png`), les équipes sur une rangée de sept cases.
- **Événement des lieux** (`game/PlaceEvents.java`) : toutes les 20 minutes de partie (`structures.evenements.intervalle` ; `structures.evenements.actifs: false` pour s'en passer), un lieu posé tiré au hasard est **renforcé** et un autre **affaibli**, jusqu'au tirage suivant ; c'est annoncé à tous (message, titre, cloche), et rappelé à qui se sert du lieu. Le premier tirage a lieu à la vingtième minute ; le même lieu n'est pas renforcé deux fois de suite. Admins : `/cf evenement` (voir), `/cf evenement tirer`, `/cf evenement <lieu renforcé> <lieu affaibli>` (`-` pour aucun), `/cf evenement arreter`.

  | Service | Renforcé | Affaibli |
  |---|---|---|
  | Potions (Pearl) | toutes les potions d'Anna, une de chaque | une seule potion toutes les 20 minutes |
  | Forge en musique (Shiganshina) | matériaux doublés ; un A en difficile donne en plus un lingot de netherite et son modèle d'amélioration | matériaux divisés par deux (arrondis au-dessus) |
  | Enchantement (Garreg Mach) | gratuit | deux fois plus cher |
  | Marchand | vend à moitié prix | vend au double |
  | Roulette (Arcadia) | de 6 à 10 pommes dorées | de 3 à 6 pommes dorées |
  | Expérience | doublée | divisée par deux |
  | Lootbox | deux lootbox garanties | plus de lootbox garantie |
  | Chasse aux pièces | 4 diamants par pièce | 1 diamant par pièce |
  | Forge (réparation) | gratuite | 4 niveaux |
- **Se téléporter depuis la carte (admins)** : Minecraft n'a pas de curseur sur une carte tenue en main ; c'est le regard qui en tient lieu. Un **clic droit** avec la carte ouvre la visée : le regard se baisse sur la carte, qui se lève en entier, et une **croix rouge** apparaît en son milieu. Tourner la tête à droite pousse la croix vers l'est, baisser les yeux la pousse vers le sud (toute la carte tient dans un petit mouvement de souris) ; la barre d'action dit les coordonnées pointées et, s'il y en a un, le nom du lieu. Un **clic** (droit ou gauche) téléporte là où est la croix ; **s'accroupir**, changer d'objet ou attendre une minute et demie annule, et le regard d'avant est rendu. On arrive au sol : en pleine nature sur le plus haut bloc, dans un lieu ou dans l'arène au niveau de leur sol (la rue, le rez-de-chaussée, la fosse) et non sur un toit ou un dôme, à la surface de l'eau s'il y en a, et quelques blocs à côté si l'endroit est une faille sans fond ou, pour un joueur en survie, de la lave ou du feu. Réservé à la permission `crossfight.admin` (les opérateurs) : pour les autres joueurs, et pour un hôte sans cette permission, la carte reste une carte. La visée marche depuis n'importe quel monde (le lobby compris) et mène toujours sur la map de jeu.
- **Protection** : avec `structures.protegees` (oui par défaut), on ne peut ni casser, ni poser, ni faire exploser ou brûler quoi que ce soit dans un lieu posé, ni y vider ou remplir un seau. Son décor ne se pille pas non plus : les cadres ne se décrochent pas, et l'on ne prend pas ce que portent les cadres et les porte-armures.
- **Préparer un lieu** : `python3 tools/lieu.py <dossier du monde> <nom> [--titre ...] [--auteur ...]` lit le monde téléchargé (toutes versions), trouve la construction (le plus grand ensemble de chunks « bâtis » qui se touchent), et écrit dans `prive/structures/<nom>/` la fiche (la boîte, la hauteur du sol, le cœur : là où la construction est la plus dense), le monde réduit aux fichiers que touche la boîte, et un aperçu à vérifier d'un coup d'œil. `--boite`, `--sol`, `--coeur`, `--profondeur` et `--hauteur` corrigent ce qu'il a trouvé. En jeu, `/cf structure monde <nom>` charge ce monde et y emmène (en spectateur), `/cf structure fiche <nom> <x1> <y1> <z1> <x2> <y2> <z2> <sol> [creuse]` réécrit la fiche (le cœur est là où l'on se tient), et `/cf structure placer <nom> [x z] [côté]` pose un lieu à un endroit précis, pour essayer.
- **Un `.schematic`** : certaines constructions ne sont pas distribuées en monde mais en ancien `.schematic` (MCEdit, WorldEdit d'avant la 1.13). `python3 tools/schematic_monde.py <fichier.schematic> <dossier>` en fait un monde vide où la construction est posée, son coin en (0, 0, 0), dans le format d'avant la 1.13 (le serveur le met à jour en le lisant) ; ce que portent les blocs est gardé, pas les créatures. Puis `tools/lieu.py` sur ce dossier, en donnant `--boite`, `--sol` et `--coeur` s'il ne trouve pas la construction (une forêt n'a presque que des blocs « naturels »), et en vérifiant dans la fiche que la boîte monte jusqu'au sommet.
- **Un monde plus récent que le serveur** : Paper arrête le serveur plutôt que de lire un chunk enregistré par une version de Minecraft plus récente que la sienne. Le plugin ne charge donc jamais un tel monde : il lit la version dans son `level.dat`, le refuse et dit pourquoi (dans le chat pour `/cf structure monde`, `placer` et `liste`, une fois dans la console au démarrage) ; un lieu dont le monde ne peut pas servir (trop récent, ou absent) est passé, et les autres sont posés normalement. `tools/lieu.py` sait le ramener à la version du serveur : quand le monde est plus récent que `--serveur` (le numéro de version des données du serveur ; 4556, celui de la 1.21.10, par défaut), les chunks gardés et `level.dat` sont recopiés avec ce numéro. Ce n'est pas une vraie conversion : cela ne vaut que si la map n'utilise aucun bloc que le serveur ne connaît pas (c'est le cas entre deux versions voisines), et l'outil rappelle de le vérifier en jeu.

## Architecture

```
com.kiokouwhite.crossfight
├── CrossfightPlugin        point d'entrée
├── game/                   machine à états, horloge, événements, scoreboard
├── team/                   équipes, tailles, attribution des licences
├── player/                 état des joueurs, vies et réapparition
├── ability/                moteur de capacités (passif, actif, activable, intervertible, ultime)
├── character/              licences, personnages, menu de sélection
│   ├── fireemblem/         Fire Emblem : Soutien, Marth, Corrin, Takumi, Byleth
│   ├── rwby/               RWBY : Ultimes en Duo, Ruby, Yang, Weiss, Blake
│   ├── valorant/           Valorant : crédits, armes à feu, Neon, Sage, Sova, Jett
│   ├── snk/                Shingeki no Kyojin : équipement 3D, Eren, Mikasa, Livaï, Armin
│   ├── unordinary/         unOrdinary : épées d'Aura, Arlo, John, Remi, Seraphina
│   ├── earthdelta/         Earth Delta : mutations, Kishi, Kura, Taka, Hano et ses 28 tomes
│   ├── duo/                Synergie : second ultime commun aux groupes duo (jauge à 200 %)
│   ├── gunvolt/            Armed Blue Gunvolt : Gunvolt, Acura, Kirin, Asimov, six Synergies
│   ├── shoja/              Shoja's Memories : Hiro, Pinkou, Aïda, Crystal, six Synergies
│   ├── bloodysnow/         Bloody Snow : Netsu, Kaze, Hannah, Eirika, Marques de Sang, six Synergies
│   └── solo/               Personnages solitaires : Burst (jauge à 1000 %), Yuki, le Drifter, Shoja et son arbre de 59 capacités
├── util/                   Sounds (bruitages et voix du pack), Music (musiques de combat), effets, menus…
├── listener/               PvP, charge d'ultime, mort, restrictions d'équipement
└── command/                /cf et /vivre
```

### Charge de l'Ultime

Une barre de vie pleine (10 cœurs) vaut 100 % de dégâts. Chaque 1 % infligé **ou** reçu
charge l'Ultime de 0,25 % (`ultimate.charge-per-percent-damage` dans `config.yml`).

### Bonus fractionnaires

Le doc parle de « Force 0.5 » ou « +0.2 Vitesse ». Les potions ne le permettent pas, donc
le plugin applique des modificateurs d'attributs : Force 1 = +1 point d'attaque (règle `force` ; 3 dans Minecraft),
Vitesse 1 = +20 % de vitesse, Résistance 1 = -20 % de dégâts reçus (plafond 80 %).

### Sons, voix et musiques

Le plugin envoie à chaque joueur un pack de ressources (`dist/CrossfightPack.zip`, adresse dans `config.yml`, section `resource-pack`). Tout y est rangé sous l'espace de noms `crossfight` :

- **Bruitages** : chaque capacité réussie joue `ability.<id>`. `resourcepack/abilities.txt` associe chaque capacité à une catégorie de la bibliothèque (`sounds/sfx/<catégorie>/`, 28 catégories de sons libres, crédits dans `resourcepack/CREDITS.md`). Un fichier `sounds/ability/<id>.ogg` remplace la catégorie pour cette capacité.
- **Voix** : `sounds/voice/<clé>.ogg`. Pour l'Ultime en Duo de RWBY, le lanceur dit `<perso>_duo_<partenaire>` (appel de partenaire de BlazBlue) et la partenaire répond `<partenaire>_nom_<perso>`. Les quatre membres de RWBY ont aussi leurs échanges de sélection (les entrées en duo de BlazBlue Cross Tag Battle), pour les six paires. Une réplique qui répond à une autre attend la fin de la première : `build_pack.py` écrit la durée de chaque réplique dans `durations.txt`, embarqué dans le plugin (`/cf dialogue <perso> <perso>` pour écouter un échange). La clé est l'id de la capacité, ou `<perso>_selection`, `<perso>_mort`, `<perso>_kill`, `<perso>_burst`, `syn_<a>_<b>_<perso>` (Synergie), `duo_<a>_<b>_<perso>` (échange des deux membres d'un duo après le choix des personnages). `<clé>_1.ogg`, `<clé>_2.ogg` sont des variantes tirées au hasard. Les voix sont entendues autour du personnage ; la réplique de sélection, par le joueur seul.
- **Musiques de combat** : `sounds/music/<piste>.ogg`, et `resourcepack/music.txt` dit quelles licences la déclenchent (`licence_a + licence_b = piste`). La musique démarre au premier coup entre deux équipes, repart sur elle-même tant qu'elles se battent (la reprise est lancée `music.loop-overlap-ms` avant la fin de la piste, et `add_audio.py` retire les silences de début et de fin), et s'arrête après `music.silence-seconds` (20 s) sans coup ou quand une équipe n'a plus de joueur en vie. Elle passe par le réglage « Juke-box / Blocs musicaux » du joueur.
- **Musiques à boucle** (comme dans un jeu vidéo : une introduction jouée une fois, puis une section qui tourne sans fin) : `tools/boucle_musique.py`.
  - `python3 tools/boucle_musique.py analyse <fichier>` cherche où la piste se répète. Sur un disque, la section qui boucle est en général jouée deux fois avant le fondu final : le second passage est le même enregistrement que le premier, et l'outil retrouve leur décalage à l'échantillon près, dit jusqu'où les deux passages se ressemblent (et où commence le fondu final), et propose des points de raccord calmes.
  - `python3 tools/boucle_musique.py <fichier> <piste> --debut <secondes> --longueur <échantillons> [--tours 8] [--suite 4] [--prive]` écrit la piste en deux fichiers : `<piste>.ogg` (l'introduction, puis 8 tours de boucle) et `<piste>_boucle.ogg` (4 tours, pour la suite). La boucle va de `--debut` à `--debut` + `--longueur` : ce qui suit sa fin est, sur le disque même, ce qui suit son début, et les 300 dernières millisecondes d'un tour glissent du second passage vers le premier (alignés à l'échantillon près). Le niveau est réglé par un gain fixe, le même pour les deux fichiers.
  - Minecraft ne sait pas boucler un son : le serveur relance un fichier, à un vingtième de seconde près. Dans un fichier, les tours s'enchaînent à l'échantillon près ; le premier contient donc déjà une dizaine de minutes de musique sans aucun raccord fait par le serveur. À l'instant écrit dans le fichier (étiquettes `CROSSFIGHT_RELAIS` et `CROSSFIGHT_SUITE`, lues par `build_pack.py` et reportées dans la table des musiques), sa suite prend le relais, puis se relaie elle-même ; les deux se recouvrent en fondu un quart de seconde, pour que ce relais ne laisse ni blanc ni claquement.
  - Une piste peut être tenue pour un joueur hors des combats d'équipes (`Music.hold` / `Music.release`) : c'est ainsi que joue la musique du boss Omori. `/cf musique <piste>` joue une piste en boucle jusqu'à `/cf musique stop`.

Un son absent du pack n'est simplement pas joué : ajouter une voix ou une musique ne demande aucun changement de code.

```
python3 tools/add_audio.py music "ma piste.mp3"        # convertit et range une musique
python3 tools/boucle_musique.py analyse "ma piste.mp3" # cherche le point de boucle d'une musique
python3 tools/add_audio.py voice netsu.wav netsu_ult   # une réplique
python3 tools/build_pack.py                            # reconstruit le pack, puis recompiler le plugin
```

`build_pack.py` écrit aussi dans le jar la liste des sons, l'empreinte du pack et la table des musiques : le jar et le pack publiés doivent donc venir de la même construction.

**Sons sous droits (voix officielles, musiques non libres)** : ils ne vont jamais sur ce dépôt. Ils se rangent dans `resourcepack-prive/` (même structure, ignoré par git) avec `add_audio.py --prive`. `build_pack.py` construit alors deux packs : `dist/CrossfightPack.zip` (public, sans ces sons) et `prive/CrossfightPackComplet.zip` (tout, ignoré par git). Seuls les noms des sons privés sont versionnés (`resourcepack/prive.txt`), pour que le plugin sache les jouer. Pour que les joueurs les entendent, deux possibilités :

- **le plus simple** : déposer `CrossfightPackComplet.zip` dans `plugins/CrossfightUHC/` et redémarrer. Le plugin le distribue alors lui-même (`util/PackHost.java` : un petit serveur web sur le port `resource-pack.port`, 8765 par défaut, qui ne sert que ce fichier), calcule son empreinte et l'envoie aux joueurs à la place du pack public ; `url` et `sha1` ne servent plus. Pour mettre le pack à jour, remplacer le fichier (l'empreinte est recalculée ; `/cf pack` le redemande). Le port doit être joignable par les joueurs, comme celui du jeu ; l'adresse proposée est celle par laquelle chaque joueur a joint le serveur, ou `resource-pack.adresse` si elle est remplie. Sans ce fichier, ou si le port ne s'ouvre pas, le plugin envoie le pack de `resource-pack.url` ; ou bien :
- héberger `CrossfightPackComplet.zip` à une adresse de téléchargement direct non publique, puis mettre cette adresse dans `resource-pack.url` et l'empreinte affichée par `build_pack.py` dans `resource-pack.sha1` ;
- ou que chaque joueur dépose `CrossfightPackComplet.zip` dans son dossier `resourcepacks` et l'active (le pack public envoyé par le serveur peut rester en place).

Un son qui a une version privée n'est pas déclaré dans le pack public : Minecraft additionne les déclarations d'un même son venant de deux packs, et tirerait sinon au hasard entre le bruitage libre et le vrai. Avec le seul pack public, ces quelques capacités sont donc muettes.

Sur une machine qui n'a plus `resourcepack-prive/`, `python3 tools/build_pack.py --depuis CrossfightPackComplet.zip` le recrée avant d'ajouter de nouveaux sons.

```
python3 tools/add_audio.py --prive voice vc_marth_appeal01.wav marth_ult
```

### Logo du tableau

Le titre du tableau de droite est le logo Crossfight UHC, dessiné dans le gris du tableau (qui lui réserve quelques lignes vides), pour les joueurs qui ont chargé le pack envoyé par le serveur ; les autres voient le titre en lettres. `scoreboard.logo` dans `config.yml` : `auto` (selon le pack), `toujours` (pour tous, quand le pack est installé à la main), `jamais`.

Le logo est une image de police du pack (`assets/crossfight/font/logo.json`, police `crossfight:logo`). Pour le changer : remplacer `tools/logo/crossfight_uhc.svg`, puis `node tools/build_logo.js` (Playwright et son Chromium), `python3 tools/build_pack.py`, et recompiler. La taille à l'écran est la constante `HAUTEUR` du script ; les lignes réservées dans le tableau sont `LOGO_LINES` dans `ScoreboardManager`.

### Skins des personnages

Le joueur porte le skin Minecraft du personnage qu'il incarne, dès qu'il le choisit (menu de sélection, `/cf change`), et retrouve le sien en quittant le mode test ou au retour au lobby. Les personnages sans skin (les originaux, pour l'instant) laissent au joueur son propre skin. Dans les menus de choix de personnage (mode test, sélection classique), chaque personnage est montré par la tête de son skin ; ceux qui n'ont pas de skin gardent leur objet. Une forme peut avoir son propre skin dans la table (`eren_titan`, porté pendant la forme de Titan d'Eren). `skins.enabled: false` dans `config.yml` coupe tout.

Minecraft n'affiche qu'un skin signé par Mojang : `python3 tools/sign_skins.py <dossier de <personnage>.png>` fait signer les images par MineSkin (en « non listé ») et inscrit les références dans `src/main/resources/skins.txt`. Ce fichier ne contient pas les images, seulement leur référence signée chez Mojang. Le changement de skin recharge un instant l'affichage du joueur.

### Armure invisible

L'armure portée n'est plus dessinée sur le corps : on voit le skin du personnage, pour le joueur comme pour les autres. Dès qu'un joueur enfile une pièce, le plugin remplace le modèle à dessiner (composant `equippable`, champ `asset_id`) par un modèle vide du pack (`equipment/hidden.json`) : elle protège autant, garde son icône et ses enchantements, mais ne se voit plus une fois portée. Le changement est inscrit dans l'objet : une pièce déjà portée reste invisible même si on la donne à quelqu'un d'autre. `armor.hidden: false` dans `config.yml` arrête de modifier les nouvelles pièces. Les élytres ne sont pas concernés.

### Guide du joueur

Le guide (`guide/`, publié sur GitBook) reprend la forme de l'ancien GitBook : une page par format, par licence (particularité, ce qui est commun aux personnages) et par personnage (voie, arme, pouvoirs, ultime). `python3 tools/build_guide.py` le fabrique à partir de `tools/guide_capacites.json`, l'export du plugin (`/cf tournage liste`, écrit dans `plugins/CrossfightUHC/capacites.json`), et rend leurs accents aux textes du jeu.

Le guide n'a plus de vidéos (retirées à la demande). L'outil de tournage reste, pour filmer les capacités dans un dossier local, jamais versionné (par exemple `prive/videos`) : `python3 tools/tournage.py tools/guide_capacites.json prive/videos [perso…]`, serveur et client de test lancés, la caméra en troisième personne (`/cf test third-person`), le son dans une sortie PulseAudio virtuelle `rec`. Le script fait lancer chaque capacité au mannequin (`/cf tournage <joueur> <capacité>`), et pour les armes et les passifs le fait frapper la caméra ; `perso <id>` change le personnage, `reset` et `reset-proche` remettent tout en place entre deux prises (plateau nettoyé, herbe remise, caméra insensible au recul). Pendant le tournage, les menus de choix prennent seuls leur première option. Une vidéo existante n'est pas refaite : la supprimer pour la refilmer.

### Icônes des capacités

Les objets des capacités peuvent porter une icône au lieu d'un objet de Minecraft. Les icônes viennent de packs d'icônes achetés, rangés dans `prive/icones/` (jamais versionnés) : `tools/build_icones.py` contient une recette par capacité, soit un dessin d'un pack dans une couleur (`Wizard 23 or`), soit une fusion d'un fond et d'un motif plus petit posé au centre (`Warrior 27 or + Acolyte 3 feu` : un bouclier et un cœur), avec quelques couleurs dérivées quand le pack ne les a pas (bleu foncé, rouge). `python3 tools/build_icones.py --apercu image.png [licence]` fait la planche d'une licence, à valider ; sans argument, l'outil écrit les icônes des licences validées (`VALIDATED`) dans le pack complet, et le plugin donne à l'objet de la capacité le modèle `icone/<identifiant>` quand le pack le déclare (`Ability.createItem`). Les armes n'ont pas d'icône. Validées : Fire Emblem, RWBY, Valorant, SNK, unOrdinary, Earth Delta, Bloody Snow, Shoja's Memories et, chez les solitaires, Yuki (sauf Hano, en attente : `PENDING`) ; Armed Blue Gunvolt est en pause. Une recette peut aussi prendre un objet de la grande collection (`Objets 342 violet` : ses bleus passent à la teinte donnée). Une recette peut aussi désigner une icône du pack d'animaux (`Animaux 14`), et quelques couleurs sont des dégradés à deux tons (`neon` : ombres bleues, lumières jaunes). Le Burst, objet commun aux solitaires, prend l'icône propre à chaque personnage (`icone/solo_burst_<perso>`, `Ability.createItem(personnage)`), ou à défaut l'icône commune. L'outil écrit aussi une police `crossfight:icones` avec un glyphe par icône (U+E100…, déclarés `fonticone.<id>.<hex>`) : le plugin le met devant le nom de l'objet de la capacité (`Ability.iconGlyph`), donc l'icône apparaît à côté du nom au-dessus de la barre d'action et dans l'infobulle.

### Passifs en haut à gauche de l'écran

Les passifs n'ont plus d'objet dans l'inventaire (`PassiveAbility.hasItem` rend faux) : chaque passif du personnage a sa case en haut à gauche de l'écran, avec son icône. En tête de la colonne, une carte montre l'arme, bordée de vert si sa capacité est passive (Yato) ou de jaune si elle est active (Falchion) (son objet du jeu, `weaponIcon` de l'export) et, avec le texte, ses capacités d'arme, passives comme actives (Falchion, Yato, Rapière Curative…) ; `/cf hud` permet de la masquer. Un passif qui agit aussi sur des alliés à ce moment-là (Charisme et Esprit du Leader de Marth, Emblème de Feu de Byleth : `ALLY_PASSIVES`, `PassiveAbility.allies`) montre une seconde flèche verte à gauche de la première ; le nom de l'image porte alors `a<bits>` après le masque des passifs allumés. Elle est éteinte au repos (bordure vert sombre) et s'allume, bordée de vert vif avec une flèche verte qui monte, quand le passif agit ; au repos, une croix rouge (icônes 3217 et 136 de la grande collection). Au moment où un passif s'allume, la flèche monte en s'animant (halo vert et éclats, six images de 2 ticks que le plugin fait défiler) et un son court l'accompagne (carillon et orbe d'expérience) . Quand il s'éteint, la croix rouge claque de la même façon (elle grossit, halo rouge, petite secousse) avec un son plus grave; un passif qui reste allumé ne refait ni l'animation ni le son : le temps d'un déclenchement (`signal`, deux secondes, et tout `Sounds.ability` d'un passif) ou tant qu'une condition tient (`active`, par exemple Dragon Gardien près d'un allié, Power Hit quand Kaze est seul face à un adversaire). `/cf passif show` ajoute à côté de chaque icône son nom et le texte qui rappelle ce qu'il fait, `/cf passif hide` le retire. `/cf hud` ouvre le menu des réglages d'affichage, gardés par joueur (`hud/HudPrefs.java`) : montrer ou non l'arme, les passifs, l'actif en cours et les effets des autres, et pour chacun son texte ; « Textes courts » remplace partout les textes par leur version télégraphique ; « Tout afficher », « Icônes seules » ; un clic bascule une ligne et le menu reste ouvert.

Sous les passifs, une carte à bordure jaune montre l'actif du joueur dont l'effet dure encore (un bonus qu'il s'est donné pour un moment, 2 s au moins : Bleeding Snow, Consumation Ardente… ; une capacité qui agit autrement que par un effet ou un bonus, comme le vol de Sang Draconique, le déclare avec `showFor(joueur, ticks)`), avec les secondes qui restent en bas à droite de l'icône (exactes jusqu'à 20 s, puis par paliers 25, 30, 40, 50, 60, 90, 120 ; `tools/build_actifs.py`, `actif/<perso>_<j>_<code>[_s<n>]`, le code comme à gauche ; passifs masqués, la carte garde sa place). Elle glisse depuis le bord gauche de l'écran en arrivant et y repart en s'en allant (trois puis deux images de 2 ticks). Elle occupe la case des jambières, partagée avec la seconde ligne de droite, qui s'efface pendant ce temps.

C'est la même technique que l'orbe de l'ultime : une image de plein écran portée dans la case du casque, que le joueur seul voit (`hud/PassiveHud.java`, `hud/EffectHud.java`). `python3 tools/build_passifs.py` (`--apercu image.png`) dessine une image par personnage, par combinaison de passifs allumés et avec ou sans texte (étapes des animations comprises : seul le passif qui vient de changer s'anime, `<perso>_<allumés>[a<alliés>]_<code>_<rang>_<étape>` pour sa flèche, `_x<rang>_<étape>` pour sa croix ; le code vaut mode de texte (0 aucun, 1 long, 2 court) + 3 × carte de l'arme ; `<perso>_arme_<mode>` quand les passifs sont masqués). Les textes de gauche sont dits au joueur (« Vous avez Vitesse. Vos alliés à moins de 10 blocs aussi. ») et ont une version courte (« Regen. 5 PV, immobilisat. 3 s ») : `tools/textes_hud.json`, champs `vous` et `court` par capacité, à défaut la description du jeu, avec la police du pack d'interface et les icônes des capacités, dans `passif/` du pack complet ; la liste et l'ordre des passifs viennent de `tools/guide_capacites.json`.

### Ce que les autres font sur toi, en haut à droite

En haut à droite, le pendant des passifs : la capacité d'un allié qui te donne un bonus (bordure bleue : Charisme de Marth, Esprit du Leader, Stratégie d'Armin, l'aura de Kishi…) ou celle d'un adversaire qui te donne un malus (bordure rouge : Lenteur, Poison, Faiblesse, immobilisation, Force en moins…), avec l'icône de la capacité et, avec `/cf passif show`, son nom, le personnage qui la lance et ce qu'elle te fait, dit pour toi (`FOR_YOU` dans `tools/build_influences.py`, sinon la description de la capacité, coupée à trois lignes). Deux lignes au plus, les plus récentes en haut, au-dessus du tableau de droite ; une carte arrive en glissant depuis le bord droit de l'écran et y repart.

L'origine d'un effet est retrouvée sans rien changer aux capacités (`hud/InfluenceHud.java`) : quand le plugin pose un effet de potion, un bonus de `Boosts` ou une immobilisation sur un joueur, la pile d'appels contient la classe de la capacité qui le pose ; le lanceur est le joueur le plus proche qui a ce personnage. Seuls comptent un allié qui aide et un adversaire qui gêne ; ses propres effets vont à gauche (la carte d'actif). Les images (`influence/<capacité>_<ligne>_<texte>_<a|e>`) sont portées par les cases du plastron et des jambières. `/cf hud demo` (admin) montre le tout pour essai.

### Sons d'effets et orbe de l'ultime

Un son prévient le joueur quand il reçoit un nouveau bonus (vitesse, force, résistance, régénération, invisibilité, absorption, saut) ou un nouveau malus (lenteur, faiblesse, poison, cécité, wither, feu, entrave), au plus une fois toutes les 2 secondes par effet ; `hud.effets: false` dans `config.yml` le coupe. Les icônes qui montaient sur les bords de l'écran ont été retirées. Les icônes d'effet de Minecraft, en haut à droite de l'écran, sont cachées aussi : tout effet reçu par un joueur est remis au tick suivant sans son icône (il reste listé dans l'inventaire, touche E) ; `hud.icones-minecraft: true` dans `config.yml` les rend.

Minecraft dessine en plein écran l'image `camera_overlay` d'un objet porté (comme la vue à travers une citrouille) : c'est la technique de l'orbe de l'ultime (`hud/EffectHud.java`, `util/ClientSlots.java`). Rien en mode créatif ou spectateur.

**L'orbe de l'ultime.** Avec le pack d'interface, un orbe au dragon (un cadre du pack) se tient en bas à droite de l'écran : il se remplit d'or avec la charge de l'ultime, par vingtièmes, et brille quand l'ultime est prêt. Une image de plein écran, vide sauf son coin, portée par un objet invisible que le joueur seul voit dans sa case de bottes (ses vraies bottes ne bougent pas). Images : `python3 tools/build_ultime_franuka.py` (`--apercu image.png`), dans `overlay_ult/` du pack complet. `hud.ultime: false` le coupe ; quand l'orbe est à l'écran, la charge n'est plus écrite ni au tableau de bord ni dans la barre des capacités ; sans le pack, pas d'orbe, et ces deux mentions restent.

### Animations (modèle articulé)

Minecraft ne permet pas de plier librement les membres d'un joueur. Pour une pose sur mesure, le joueur devient invisible un instant et un pantin à son image prend la pose à sa place : tête, torse, bras, jambes et objet tenu sont sept objets affichés (`ItemDisplay`), chacun centré sur son articulation, que `util/Puppet.java` fait tourner et suivre le joueur. Le joueur lui-même ne voit pas son pantin : à la première personne, les pièces seraient dans sa caméra et lui boucheraient la vue (le serveur ne sait pas s'il est en vue extérieure) ; il garde donc sa vue habituelle et ce qu'il tient en main, seule son armure lui est masquée. `animations.voir-son-pantin: true` dans `config.yml` le lui montre aussi, pour jouer en vue extérieure (F5).

- `python3 tools/add_skin.py skin.png <personnage> [--prive]` fabrique le modèle à partir d'un skin Minecraft 64×64 (bras fins détectés). Avec `--prive`, il reste hors du dépôt, comme les voix officielles.
- Les poses sont dans `src/main/resources/poses.txt` (angles de chaque partie, en degrés).
- `python3 tools/pose_preview.py <personnage> <pose> [image.png] [--prive]` dessine une pose sous cinq angles sans lancer le jeu.
- En jeu : `/cf pose <nom> [secondes]`, à regarder en vue extérieure (F5).
- `poses.enabled: false` dans `config.yml` coupe tout. Sans le modèle dans le pack, la capacité garde simplement ses effets habituels.

Animations de Marth :

- **La Falchion en 3D** : un modèle low-poly en pavés (style Blockbench), lame argentée à gouttière dorée, garde dorée en couronne avec un oeil au centre, poignée rouge, pommeau doré ; la Sainte-Falchion est plus claire, gouttière bleue lumineuse. Fait par `python3 tools/build_falchion.py` (`--apercu image.png` pour trois vues) dans le pack privé. Le plugin marque l'arme de Marth (`custom_model_data` « falchion » ou « falchion_sainte ») quand le pack contient le modèle (déclaré sous le nom `model.falchion`), et le pack privé redéfinit l'épée en fer et l'épée en diamant pour cette arme-là seulement : sans le pack privé, on voit l'épée normale.
  - **Retouche dans Blockbench** : `--blockbench fichier.bbmodel` écrit un projet Blockbench où l'épée est droite (pommeau en bas, pointe en haut), plus facile à modifier. Le projet retouché se dépose tel quel dans `resourcepack-prive/modeles/falchion.bbmodel` : le script reprend alors ses pavés, ses rotations (de -45 à 45°, sur un axe) et ses textures peintes, et en tire la Sainte-Falchion par recoloration. Un modèle retouché n'est jamais écrasé par celui du script (`--defaut` pour y revenir).
  - Le modèle reste droit dans le pack ; c'est à l'affichage qu'il penche d'un huitième de tour (réglages d'affichage en main, au sol et dans l'inventaire ; `util/Puppet.java` pour les animations). Il se pose ainsi comme l'image d'une épée de Minecraft et se place partout comme l'épée qu'il remplace.
- **Les autres armes, en brouillons** : `python3 tools/weapon_kit.py <definitions.py> [--apercu planche.png] [--compact] [--seul nom,...]` transforme une description courte de chaque arme (son profil : des pièces rectangulaires posées dans un plan, avec une épaisseur, des courbes en lignes brisées, des pavés libres) en projet Blockbench avec sa texture, et dessine une planche d'aperçu. Une image détourée vue de côté peut aussi être décalquée (`Arme.trace`) : c'est ainsi que sont faites les armes à feu. Les descriptions des armes sont privées (`resourcepack-prive/modeles/definitions/`, d'après les images des wikis ou, pour les licences originales, imaginées d'après leur nom), les projets sortent dans `resourcepack-prive/modeles/brouillons/`. Ce sont des premières versions à retoucher : elles ne sont pas encore dans le pack, seule la Falchion l'est.

- **Une arme retouchée, mise en jeu** : `python3 tools/build_armes.py [--apercu dossier]` prend le projet Blockbench retouché, déposé tel quel dans `resourcepack-prive/modeles/<nom>.bbmodel`, et en fait le modèle du pack privé, l'objet de Minecraft qui le montre (seulement à l'arme marquée `custom_model_data` « <nom> ») et la ligne de `src/main/resources/models.txt` qui dit au pantin des animations comment le tenir (`util/Models.java` : point de prise, inclinaison, taille). Première arme : le **Yumi Fujin** de Takumi.
  - Minecraft n'accepte qu'une rotation par pavé, sur un axe, entre -45 et 45° : un pavé tourné davantage dans Blockbench est d'abord tourné lui-même d'un quart de tour (ses faces et leurs textures suivent) et ne garde que le reste.
  - **La corde brille** : sa texture est animée (un éclat blanc remonte la corde, sur une lueur cyan qui respire), elle éclaire d'elle-même, même de nuit (`light_emission`), et un halo translucide l'entoure.
  - **L'arc bandé** : trois modèles de plus, choisis par le jeu pendant qu'on vise comme les trois images de l'arc de Minecraft ; la corde est tirée en deux brins vers l'archer, une flèche de lumière encochée dessus.
  - Le modèle reste droit dans le pack ; ses réglages d'affichage le posent exactement comme l'image de l'arc de Minecraft (poignée du modèle à la place de celle de l'image), en main à la première et à la troisième personne, au sol, dans l'inventaire. Un modèle peint se montre sous ses vraies couleurs : l'arme marquée perd son reflet d'enchantement.

- **Contre** : garde, épée tenue haut lame vers le bas, puis fente avant à la riposte.
- **Falchion** : l'épée plantée dans le sol devant lui, les deux mains sur le pommeau, tête baissée, pendant les 3 secondes du soin.
- **Coup Critique**, d'après le Final Smash de Smash Bros : moulinet, épée levée vers le ciel avec un éclat à la pointe (0,8 s, immobile, pendant lesquelles il choisit sa direction en regardant), il se ramasse, fonce à l'horizontale dans une traînée blanche et bleue, puis tient l'estoc bras tendu. Il est invulnérable de la levée d'épée à la fin de la charge. Bruitages d'origine dans le pack privé : `ability.marth_ult_eclat`, `ability.marth_ult_charge`, `ability.marth_ult_hit`.

Animation de Corrin : pendant son Ultime, elle se met à quatre pattes comme son dragon, les deux bras plantés dans le sol, l'eau jaillissant autour de ses mains et ses ailes de dragon déployées sur son dos ; elle ne peut pas bouger pendant cette pose (1,5 s).

Animation de Takumi : en lançant Contre Rapproché, il lève son arc bien haut et le jette violemment au sol (l'arc rebondit et reste par terre, visible de tous, tant que dure la lame). Au bout des 30 secondes, l'arc file jusqu'à ses pieds dans un souffle de vent, même s'il s'est éloigné, et il se baisse pour le ramasser. Le pantin tient un arc par le milieu de son bois (`Puppet.held` change l'objet tenu en cours d'animation). Avec le modèle 3D du Yumi Fujin dans le pack, c'est lui que le pantin tient et qui reste au sol, corde lumineuse comprise. Pour Vengeance, il tend les deux bras devant lui et ils s'embrasent, du poing à l'épaule (seulement les bras) ; ils restent en feu pendant les 5 secondes. Pour Furie, il lève le poing et frappe le sol, un genou à terre (immobile 0,7 s) : l'impact crache du feu, une onde de flammes s'élargit au ras du sol et une colonne de flammes monte en vrille et l'engloutit ; quelques flammes l'entourent ensuite tant que dure la Furie. Ce ne sont que des particules : rien ne brûle.

Pour le Rayon Démoniaque, d'après la scène de Fire Emblem Fates : Takumi se met de profil et bande son arc (tenu dans la main gauche, la droite ramenée sous le menton) pendant les 4 secondes de charge, enveloppé de flammes violettes en vrille et d'orbes noirs, les yeux rouges, pendant qu'un éclat blanc-rose pulse de plus en plus vite à la pointe de la flèche ; au tir, sa main droite part en arrière. La corde du Yumi Fujin 3D se tend en trois temps pendant la charge (`Models.look` : un état du modèle, ici l'arc bandé) et se détend au tir.

Le Rayon Démoniaque et les Vagues Bleues de Corrin partent dans la direction exacte du regard (à l'horizontale), et non plus sur l'un des quatre axes de la grille : droit, en diagonale ou entre les deux. Chaque case de la grille dont le centre tombe dans le rectangle du rayon ou de la vague en fait partie.

Animations de Byleth. Déchaînement d'Épine : le modèle suit le coup tel que la capacité le joue (préparation penchée en avant, épée basse devant elle ; bras armé loin sur sa gauche juste avant la ruée ; puis le bras traverse jusqu'à sa droite pendant les 8 ticks du balayage, l'épée dans le prolongement du fouet) et garde la direction du coup même si le joueur tourne la tête (`Puppet.face`). Tutorat : pendant tout le Tutorat (les deux menus, puis une seconde et demie après la leçon), elle tient un livre ouvert à deux mains devant elle, tête baissée, et tourne une page toutes les secondes et demie. Le livre est un modèle du pack public (`items/book_open.json` et trois images de la page qui tourne, `book_turn1` à `book_turn3`) : un objet du pack tenu en main est centré sur la main, sans la rotation des objets plats (`item_shift` le décale, `Puppet.heldLook` change son image).

Animations de RWBY :

- **Yang, Ember Celica** : garde de boxe, direct du droit puis direct du gauche dans le vide ; chaque coup de gantelet tire une décharge de feu (flamme au poing, douille éjectée) sur la cible : 1 cœur, puis 1 cœur et l'explosion qui l'enflamme et la repousse.
- **Ruby, Crescent Rose** : à chaque tir, un genou à terre, l'œil dans la lunette, l'arme tenue comme un fusil (les tirs enchaînés la gardent dans la position), flamme à la bouche du canon et douille éjectée.
- **Ruby, Explosion de Pétales (rapide)** : elle décolle et file à l'horizontale, la tête la première, en vrille sur elle-même (`Puppet.spin` : rotation de tout le pantin autour d'un axe), et finit sa vrille en retombant.
- **Ruby, Yeux d'Argent** : recroquevillée, les mains serrées sur la poitrine, puis bras grands ouverts et tête en arrière ; la lumière jaillit de ses yeux, l'écran de tous ceux qui la voient devient blanc un instant, et les 3 cœurs tombent avec l'éclair.
- **Weiss, Glyphs (Type 1)** : rouge, un grand coup en diagonale dans le vide qui embrase la lame ; bleu, la main gauche glisse le long de la lame et le givre s'y rassemble ; jaune et cyan, l'épée plantée dans le sol (genou à terre) fait naître la zone ou la barrière ; blanche, une pichenette sur la lame qui vibre ; violette, la lame tombe, lourde, et remonte en tremblant. Le pouvoir en attente (feu pendant 10 s, ou jusqu'au prochain coup) garde ses particules sur la lame et le flocon des Schnee dans son dos, à la couleur du Dust.
- **Weiss, Glyphs (Type 2)** : rouge, le signe apparaît devant elle, un estoc dedans et la flèche en sort ; bleu, pirouette sur un pied sur le signe pendant que la patinoire s'étend ; jaune, un genou à terre sur le signe, les éclairs, puis elle se relève ; cyan, un cercle de verre marqué du signe, cinq estocs rapides et une flèche à chacun ; blanche et violette, l'adversaire est retenu une seconde sur le signe (épée levée, ou les deux mains au sol) puis s'envole ou s'enfonce.

Les signes de Glyph sont deux images du pack privé (le flocon et le grand cercle des Schnee), préparées par `python3 tools/build_glyphs.py` depuis `resourcepack-prive/glyphes/` : blanches, elles sont teintes en jeu à la couleur du Dust et affichées à plat par un `ItemDisplay` (`character/rwby/Glyph.java`) qui grandit en apparaissant, peut tourner, suivre Weiss, puis s'efface. Le plugin les connaît sous les noms `glyph.schnee_flocon` et `glyph.schnee_cercle` ; sans elles, les Glyphs gardent leurs seules particules.

- **Ice Flower** (ultime en duo de Ruby et Weiss) : Weiss lève son épée des deux mains et la plante dans le sol, un genou à terre ; la glace part de la lame et s'étend en une seconde et demie jusqu'à 25 blocs. Ruby, couchée dans sa course, file sur le bord de la glace à mesure qu'elle grandit (près de trois tours), en semant des pétales de rose ; elle revient ensuite d'où elle est partie, et une couronne de pétales reste sur le bord de la patinoire tant qu'elle dure. Weiss ne bouge pas pendant le geste ; Ruby garde son regard pendant sa course.

Animations de Blake :

- **Ombre** : Blake et ses trois clones naissent en silhouettes violettes (le pantin peut être teint : `Puppet.tint`, avec un contour lumineux `Puppet.glow`), puis leur apparence revient partie par partie, des jambes à la tête, chaque fois dans un éclat de particules ; les clones ne partent qu'ensuite. Un pantin peut remplacer n'importe quelle entité, pas seulement un joueur (ici les clones).
- **Discrétion** : elle s'enfonce dans une flaque noire (image dessinée par `tools/build_glyphs.py`, pack public), les bras qui remontent pendant qu'elle disparaît ; le pantin reste sur place (`Puppet.pin`) même si elle s'en va, invisible.
- **Gambol Shroud** : en pistolet, à chaque tir elle le tient à deux mains, bras tendus (un petit modèle 3D du pack public, `items/blake_pistolet.json`) ; en katana, le clic droit joue le lancer : elle arme la lame des deux mains au-dessus de l'épaule, la jette devant elle (la lame part à ce moment-là, quatre ticks après le clic), la lame tournoie au bout de son ruban noir et se plante là où elle tombe ; Blake fonce jusqu'à elle dans l'élan de son lancer et la reprend d'une main. Le changement de mode, lui, ne lance plus rien.
- **Instinct Animal** : le bond est un saut de chat, corps allongé, bras tendus comme des pattes ; au lancement elle rugit, penchée en avant, les mains en griffes, et une onde violette part d'elle.

Animations d'Eren :

- **Charge tridimensionnelle** : il fonce tête la première, le corps couché vers l'avant, les deux bras rejetés en arrière, une lame dans chaque main (`Puppet.second` : un second objet tenu, dans l'autre main, orienté par `item2` dans la pose). Le pantin regarde dans le sens de la charge et suit sa pente quand la cible est plus haut ou plus bas. La ruée dure de 4 à 7 ticks selon la distance (deux blocs et demi par tick) pour qu'on le voie passer.
- **Titan Assaillant**, d'après la scène de l'anime : Eren porte la main à sa bouche et se mord (le sang gicle, une étincelle dorée court sur son poing), l'éclair tombe dans un éclat doré et une explosion de vapeur, puis le Titan se forme petit à petit, deux fois plus grand que lui : le squelette d'abord, os par os (colonne et côtes, crâne, bras, jambes), puis les muscles à vif qui poussent dessus dans le même ordre, enfin la peau du Titan Assaillant qui se referme d'un coup ; il se redresse et rugit. Eren ne bouge pas et ne craint rien pendant ces trois secondes et demie. Le pantin prend ici une taille (`Puppet.show(..., 2f)`) et change d'apparence partie par partie (`Puppet.look`).
  - Le squelette et les muscles sont deux modèles du pack public, dessinés par `python3 tools/build_titan.py` : de vrais os en pavés pour le premier, un skin de fibres rouges pour le second. La peau est le skin du Titan Assaillant, sous licence : `python3 tools/add_skin.py <skin.png> eren_titan --prive` pour le pantin, et `python3 tools/sign_skins.py <dossier contenant eren_titan.png>` pour que le joueur le porte ensuite (`Skins.wear`) pendant toute sa forme de Titan. Il retrouve le skin d'Eren à la mort du Titan.
  - Sans le modèle articulé dans le pack, la transformation se fait d'un coup, avec l'éclair, comme avant.

Animations de Valorant, d'après les lancers du jeu d'origine. Les images d'effets (un orbe teint en jeu, un anneau de vent, un couteau de lancer) sont dessinées par `tools/build_glyphs.py` dans le pack public et montrées par `util/Fx.java` ; sans elles, des particules ou un objet de Minecraft les remplacent.

- **Jett** : *Vent Arrière*, elle file couchée dans le sens du dash, les bras tirés en arrière, une bourrasque s'ouvre derrière elle et deux filets de vent la suivent ; *Courant Ascendant*, elle jaillit, les bras plaqués vers le bas, un genou remonté, la bourrasque s'ouvre au sol et une colonne de vent en vrille monte avec elle ; *Flottement*, tant qu'elle descend en planant elle a les bras ouverts, penchée en avant, le vent file sous ses mains, et elle reprend son allure en touchant le sol ; *Tempête de Lames*, elle écarte les bras et cinq couteaux se déploient en éventail derrière ses épaules, puis chaque lancer est un geste sec du bras et un vrai couteau qui part, pointe en avant.
- **Neon** : *Éclair Relais*, elle arme le bras et jette l'éclair (il part de sa main au milieu du geste, quatre ticks après le clic) ; *Voie Rapide*, les deux bras tendus, des éclairs partent de ses mains vers le pied des deux murs ; *Vitesse Supérieure*, elle se ramasse pour partir puis laisse un sillage d'électricité jaune et bleue au sol ; *Surmultipliée*, cambrée, les poings serrés, la foudre tombe sur elle, des éclairs la frappent de tous côtés et deux anneaux s'ouvrent à ses pieds.
- **Sage** : *Orbe de Soin*, elle tend l'orbe vert vers son allié, l'orbe file jusqu'à lui et une lueur tourne autour de lui pendant le soin ; *Orbe de Lenteur*, elle arme le bras par en dessous et lance l'orbe (quatre ticks après le clic) ; *Orbe Barrière*, l'orbe repose dans sa paume, elle l'abat vers le sol et le mur se lève rangée par rangée (il commence huit ticks après le clic) ; *Résurrection*, penchée sur son allié, les mains tendues, un cocon de cristal de jade se lève autour de lui dans une colonne de lumière puis vole en éclats. `/cf effet resurrection` en donne l'aperçu en mode test.
- **Sova** : *Drone Rapace*, il lève le bras et lâche le drone, qui s'élève de sa main ; *Électroflèche* et *Flèche de reconnaissance*, à la détente il est de profil, l'arc à bout de bras, la main de corde qui part en arrière ; *Fureur du Chasseur*, il lève son arc pendant que l'énergie converge dessus, puis chaque tir le montre de profil, arc bandé, avant que la corde claque.

Les modèles articulés acceptent une teinte (`custom_model_data`, blanc par défaut) : `python3 tools/add_skin.py --teinte` l'ajoute aux modèles fabriqués avant.

Pendant l'animation, les autres joueurs ne voient plus l'équipement du joueur (paquet d'équipement vide), et le joueur lui-même ne voit plus ni sa propre armure ni l'objet qu'il tient en vue extérieure : ses quatre cases d'armure lui sont envoyées vides, et la case qu'il tient reçoit un objet sans apparence (`items/hidden.json`), si bien que le clic droit part toujours. Son inventaire réel n'est pas touché et lui est renvoyé à la fin ; l'icône de l'objet tenu disparaît de sa barre le temps de l'animation. Les 25 personnages de licences existantes ont leur modèle dans le pack privé ; les personnages originaux attendent leur skin.

### Écran de sélection en 3D
Au début d'une partie, chaque équipe est emmenée dans un monde à part, devant une rangée de grandes cartes inclinées et presque collées, comme sur une affiche : une par personnage de sa licence, son illustration recadrée dans la carte, une bande à sa couleur avec son nom en capitales italiques, un bandeau sombre et l'icône de sa voie à cheval sur les deux ; derrière, un mur sombre à grands éclats, et le logo de la licence en haut à gauche. Le joueur vise une carte avec son viseur : elle grandit et avance, un halo à la couleur du personnage l'entoure, les autres s'assombrissent un peu, et la fiche du personnage s'affiche (voie et capacités) ; un clic (gauche ou droit) le choisit (la carte bondit dans un éclair blanc). Les coéquipiers voient le choix en direct (« CHOISI » et le pseudo sur la carte), un personnage déjà pris est refusé, et la barre en haut de l'écran décompte les 60 secondes. Chaque joueur a son propre mur de cartes, à plat et centré sur lui (il voit l'affiche bien en face, où qu'il soit dans la rangée de son équipe), et ses propres affichages : l'effet de survol suit son seul regard. La fiche du personnage visé s'affiche à côté de sa carte, par-dessus la voisine, pour ne pas le cacher. Les joueurs ne peuvent pas bouger mais peuvent tourner la tête : ils sont immobilisés sans que leur champ de vision change (vitesse de marche nulle, et non un malus de vitesse qui resserrerait la vue). Pendant le choix, le tableau latéral, les icônes de bonus/malus et la barre des capacités sont masqués ; au lancement, l'écran disparaît et la partie les téléporte. Un joueur qui se reconnecte pendant le choix retrouve l'écran de son équipe. `selection.scene-3d: false` dans `config.yml` remet l'ancien menu d'inventaire.

Les images sont des objets du pack affichés par des `ItemDisplay`, préparés par `tools/build_selection.py` :
- **privées** (jamais versionnées, comme les voix) : une carte entière par personnage (illustration, bande, nom, icône de voie), plus les icônes des voies et les logos. Les sources se déposent dans `resourcepack-prive/selection/art/<personnage>.png`, `selection/voie/<voie>.png` et `selection/logo/<licence>.png` (PNG ou WebP à fond transparent) ;
- **publiques** (versionnées, dessinées par le script) : la carte vide (sa bande blanche est teinte à la couleur du personnage), le halo et le mur à éclats.

Le nom et la couleur de chaque personnage sont dans `resourcepack/selection.txt` ; sa voie et le cadrage de son illustration (où est son visage, et à quel zoom) dans `resourcepack/selection_cartes.txt` : le visage est placé au milieu de la carte, à 20 % de sa hauteur. Pour une nouvelle licence : déposer ses images, compléter ces deux fichiers, lancer `python3 tools/build_selection.py` puis `python3 tools/build_pack.py`, recompiler. `--apercu fichier.png [licence] [perso_visé]` dessine l'écran vu de face (1920 × 1080) pour régler le cadrage. Les dimensions sont les mêmes dans le script et dans `game/SelectionStage.java`.

Un personnage sans illustration a la carte vide à sa couleur, son nom court en texte (ajusté à la largeur de la carte, sombre sur une bande claire) et son personnage debout sur la bande : son modèle articulé du pack, donc son vrai skin, agrandi et presque aplati sur la carte comme une image (`Puppet.statue`) ; à défaut un mannequin à son skin signé ; à défaut (personnage sans skin) une silhouette à sa couleur marquée d'un « ? » (modèle neutre du pack public, `python3 tools/build_silhouette.py`). Une licence sans logo a son nom en texte, ajusté pour tenir dans le coin.

Le cadrage se règle dans `config.yml` sans redémarrer (`/cf test selection` relit la configuration) : `selection.distance` (distance du mur, 4.9), `selection.hauteur` (hauteur du centre des cartes au-dessus des pieds, 1.74) et `selection.regard` (inclinaison du regard à l'ouverture, 0). Les valeurs par défaut remplissent l'écran pour le champ de vision normal (70).

### Items de capacité
Un item de capacité ne ressemble jamais à une arme ni à un outil, pour que personne ne frappe avec : les icônes en épée deviennent des éclats d'améthyste, les haches du fer brut, les arcs des flèches, les tridents des éclats de prismarine, les pioches, pelles et houes des matériaux bruts. Seuls les vrais arcs à bander (Sova) gardent leur apparence. L'arme du personnage reste en premier slot.

### Mode test

**Zone duo** (`/cf test duo`) : une plateforme à part pour essayer seul les ultimes en duo (l'Ultime en Duo de RWBY et les Synergies de Bloody Snow, Shoja's Memories et Armed Blue Gunvolt). Le testeur y a un **mannequin duo** : un allié à l'apparence de joueur, avec le skin du personnage qu'il incarne quand il en a un, qui tient le rôle de la partenaire. Un clic droit dessus ouvre le choix de son personnage (les autres personnes de la licence du testeur) et joue l'échange des deux personnages s'il existe. Trois cibles attendent devant le testeur, et ses ultimes en duo sont toujours prêts (les jauges avec un second testeur restent aussi à 100 %). `/cf test duo <perso>` choisit directement le personnage du mannequin. En arrivant sans personnage à ultime en duo, un menu ne propose que ceux-là. La mort fait réapparaître dans la zone ; `/cf test duo` ramène au terrain de test, `/cf test` quitte le mode test, `/cf clear` remet aussi la zone duo à neuf. Le testeur y a désormais un mannequin duo par autre personnage de sa licence, en rang à sa droite : tous ses ultimes en duo sont prêts en même temps (les trois de chaque membre de l'équipe RWBY, par exemple), et ils se refont quand il change de personnage.

**Troisième personne en duo** (`/cf test duo-third-person [perso]`) : la troisième personne, où le mannequin du centre a en plus un mannequin duo par autre personnage de sa licence, en rang à côté de lui. L'objet d'ultime en duo du testeur ouvre le menu du mannequin, dont tous les ultimes en duo sont prêts : il les lance avec sa coéquipière et c'est le testeur qui les reçoit, pour les voir de l'extérieur. `/cf change` change le personnage du centre et refait ses coéquipières ; `/cf clear` les remet en place ; la commande une seconde fois ramène au terrain de test.

**Troisième personne** (`/cf test third-person [perso]`) : une plateforme à part où un **mannequin pilote**, au centre, incarne le personnage (son skin, son arme) pendant que le testeur sert de cible, sept blocs devant lui. `/cf change` (et le menu) changent le personnage du mannequin, pas celui du testeur. Le testeur reçoit les objets de ce personnage, mais c'est le mannequin qui s'en sert, tourné vers lui en permanence :
- clic droit sur un objet de capacité : le mannequin la lance (accroupi : la variante accroupie, et le mannequin s'accroupit aussi) ; les menus de choix s'ouvrent chez le testeur ;
- accroupi + clic gauche dans le vide avec l'arme : le mannequin te frappe (à 4 blocs au plus) ou te tire dessus à l'arc, bandé à fond ;
- frapper le mannequin (avec n'importe quoi, arme comprise) déclenche ses capacités qui réagissent aux coups (Contre de Marth...) ; il ne recule pas sous tes coups, mais ses propres capacités le déplacent toujours.

Le mannequin a la vie d'un joueur mais ne meurt jamais, garde les effets de ses capacités, et revient à sa place s'il sort de la plateforme ou reste dix secondes immobile ailleurs. Ce que ses capacités rangent, retirent ou modifient dans son inventaire (flèches, arc lancé au sol...) se voit aussi dans celui du testeur ; sa barre de capacités, ses jauges, ses messages (préfixés `[Mannequin]`) et ses sons « privés » arrivent chez le testeur. Il ne vise que son testeur ; ses capacités qui visent un allié trouvent les mannequins alliés (`/cf mannequin allie`). Les commandes de test qui règlent un personnage (`/cf tome`, `/cf kills`, `/cf titan`, `/cf delta`, `/cf aura`, `/cf burst`, `/cf sante`, `/cf shoja`, `/cf credits`, `/cf pose`) s'appliquent au mannequin. La frappe du mannequin ignore la difficulté du serveur (elle compte comme celle d'un joueur). `/cf test third-person` (sans personnage) ramène au terrain de test en rendant le personnage au testeur, `/cf test` quitte le mode test ; la mort fait réapparaître face au mannequin, et un redémarrage du serveur remet le testeur en troisième personne.

Techniquement, le mannequin est piloté par un objet qui se fait passer pour un joueur (`util/MannequinPlayer`) : tout ce qui concerne le corps (position, regard, vie, effets, vitesse, attributs, projectiles, téléportations) va au mannequin, tout ce qu'un joueur voit et entend seul va au testeur, et son inventaire est un inventaire à part dont la main et l'armure s'affichent sur le mannequin. Les capacités, écrites pour un joueur, tournent donc telles quelles. Les seuls endroits qui passent par le vrai mannequin (`MannequinPlayer.real`) sont ceux où le serveur exige une vraie entité : dégâts avec une source (`Effects.damage`), tireur d'un projectile (`Effects.setShooter`), sons attachés, joueur caché (`Stealth`), équipement masqué (`Puppet`). Ce qui dépend des événements propres aux vrais joueurs (bander un arc à la main, se déplacer, manger) ne peut pas être piloté : le mannequin tire à pleine puissance et ne se déplace que par ses capacités.

Pour que la partenaire puisse être un mannequin, les 24 ultimes en duo prennent une entité vivante pour partenaire : ce qui n'a de sens que pour un joueur (vol, furtivité, dashs donnés par l'item de Synergie, portée, chute amortie) ne s'applique qu'aux joueurs, et un mannequin duo compte pour 0 là où l'effet dépend de son historique (barre de résistance d'Hannah, dégâts récents de Kaze). Un mannequin duo est dans le camp de son testeur (`Effects.sideOf`).


En mode test, aucune capacité n'a de temps de recharge et l'Ultime est toujours chargé. Le testeur a aussi deux seaux d'eau dans sa barre, qui se remplissent tout seuls (un seau vidé redevient un seau d'eau à la seconde suivante) ; `/cf clear` retire l'eau versée.
Les chargeurs, munitions et jauges fonctionnent normalement.

`/cf test arbre` envoie dans une forêt de test : le même terrain (90 blocs de côté), planté d'environ 80 épicéas de taïga, hauts et géants (troncs jusqu'à 28 blocs), avec une clairière au centre où l'on arrive. Elle sert à essayer l'équipement tridimensionnel de Shingeki no Kyojin : accrocher le grappin aux troncs, se balancer d'arbre en arbre. `/cf test arbre` une seconde fois ramène au terrain de test, `/cf clear` replante la forêt, `/cf mannequin` y pose des cibles comme ailleurs.

### Hypothèses prises sur Valorant

- Les armes blanches : le doc ne détaille pas encore les catégories et embranchements. En attendant, trois paliers (6, 7.5, 9 dégâts) atteints tous les 3 kills.
- Les armes à feu tirent des balles (flèches invisibles habillées) avec la puissance indiquée ; « Puissance 0 » vaut une flèche sans enchantement. Un tir à la tête double les dégâts pour Sheriff, Marshal et Operator. Le rechargement est automatique quand le chargeur est vide, ou accroupi + clic droit.
- L'Ares et l'Odin tirent tout leur chargeur d'un coup, immobile, puis Lenteur II 5 s.
- Les +100 crédits toutes les 10 minutes suivent l'horloge de la partie.
- Le drone de Sova est un allay qui suit Sova et tire une flèche de Faiblesse par seconde sur l'ennemi le plus proche à 20 blocs.
- L'Ultime de Neon donne une lame électrique de 10 dégâts le temps que la jauge de Vitesse Supérieure se vide.
- La Résurrection de Sage réutilise les règles de l'Invocation de Weiss : une vie, Faiblesse, pas de plastron.

### Hypothèses prises sur les groupes duo et Armed Blue Gunvolt

- La Synergie est un item commun (« Synergie ») : la jauge d'une paire monte avec les dégâts infligés par les deux partenaires, sans condition de distance, jusqu'à 200 % (40 cœurs cumulés). En mode test, elle se lance même sans jauge, et `/cf duo` la remplit. Les Synergies « à clics » (Dash Double Lame, Talisman Électrifiant) se réutilisent en recliquant sur l'item pendant leur durée.
- Gunvolt : Cerbère tire au clic droit (1,5 s entre deux balles, 7 dégâts). Le Champ Électrique a sa propre jauge (−5/s actif, +1/s) et frappe aussi, à 40 blocs, tout adversaire marqué par l'Électricité Septimale. La Télesquive fait un petit pas de côté dans un éclair et annule le coup. L'Astrasphère suit Gunvolt.
- Acura : le Pod tire tout seul toutes les 2 s sur la cible verrouillée par l'Avenger Dash si elle est à 10 blocs (clic droit : tir droit devant). Les Ex-Weapons partagent une jauge d'Énergie Mécanique (+1/s) : accroupi + clic pour choisir, clic pour utiliser ou activer. Anchor Nexus coûte 15 par percussion. Rising Cyclone reste là où elle a été lancée. Twin Shredder suit Acura (2 cœurs/s pendant 3 s, 4 sur la cible).
- Kirin : les talismans sont des projectiles ; la marque dure 10 s. Le sabre se téléporte sur la cible marquée la plus proche (30 blocs) et retire sa marque ; Arc Chain est utilisable 4 s après et touche aussi l'adversaire visé par la téléportation (1 cœur par cible, 4 max) avec 10 s de recharge. Slash : en l'air Tensho, accroupi Ressen (deuxième clic pour retomber), sinon Issen ; un seul temps de recharge de 15 s.
- Asimov : le fusil charge 3 s puis tire (9 dégâts, doublés à moins de 5 blocs). La tourelle tire ses salves à 5 s et 10 s. Les chaînes de l'ultime sont des segments tirés au hasard sur 30 blocs pendant 8 s.
- Vol Électrique et Prison de Chaîne donnent le vol créatif pendant leur durée ; Double Électrocution est centrée sur le lanceur.

### Hypothèses prises sur les personnages solitaires

- Le Burst est un item commun aux trois solitaires : il se charge à 1000 % de dégâts infligés, le compteur est conservé d'une mort à l'autre (il repart de la dernière utilisation, pas de la mort). En mode test il est toujours prêt ; `/cf burst` le remplit hors test.
- Yuki : Bleeding Snow coûte 2 cœurs et donne Force, Vitesse et Résistance 10 s (recharge 15 s). Souffrante s'applique en continu (moins de 7,5 / 5 / 3 cœurs). Insatiable est un passif à 5 minutes de recharge : le coup fatal la laisse à un demi-cœur 15 s sans régénération. Insaisissable : 3 dashs de 4 blocs en 5 s, invisible jusqu'au troisième ou jusqu'à ce qu'elle frappe. L'ultime retire 7,5 cœurs de vie max 20 s ; le Burst Monstre retire 7 cœurs définitifs à Yuki et 9 aux ennemis à 10 blocs (explosion visuelle sur 50 blocs). Les pertes définitives sont effacées à la nouvelle partie.
- Le Drifter : six pistolets sur un seul item (sneak + clic pour changer, munitions par arme, tout vide = rechargement), Chain Dash doit être relancé entre 0,7 et 1,3 s après le précédent sinon il est verrouillé 15 s. Le Cœur Mourant perd 1 % de santé par minute à partir de 30 min, 2 % à 60, 3 % à 90 ; les paliers donnent bonus puis toux, silence et hallucinations. Le Burst Monde en Ruine emmène les adversaires à 40 blocs dans une arène dédiée (monde `crossfight_ruine`) ; il en sort quand ils sont tous tombés, ou en mourant sans perdre de vie.
- Shoja : son épée est une épée de fer de 8,5 ; elle seule peut enchanter et réparer des armes, et crafter arcs, arbalètes, tridents et masses (pas d'épée, comme tout le monde) (les autres restrictions restent). L'Épée de l'Ego copie, une seule fois, les enchantements de l'arme tenue par son premier tué. L'arbre contient 59 capacités en six éléments (Feu, Eau, Foudre, Vent, Espace, Temps), chacune débloquée par un objectif suivi en jeu (crafts, cœurs infligés ou perdus, immobilisations, blocs cassés, enchantements posés, portail du Nether…) ; trois au plus équipées, d'éléments différents, via l'item « Arbre des capacités ». En mode test tout est débloqué. Les « gels » temporels immobilisent et empêchent de frapper ; les trous, murs et lacs de lave sont temporaires (20 s) ; les « dimensions » sont une arène d'End Stone (`crossfight_dimension`). L'ultime et le Burst dépendent de sa vie (plus ou moins de 5 cœurs) : Couronne Angélique (vol + Régénération II 20 s) / Araignée Maléfique (Force IV, Vitesse II, −1 cœur par 2 s) ; Dieu du monde (immobile 10 s puis +1 vie) / Dieu des enfers (−4 cœurs définitifs, Force IV jusqu'à sa mort).

### Hypothèses prises sur Bloody Snow

- La portée de frappe (« Reach ») passe par l'attribut de portée d'interaction de Minecraft, que le combat façon 1.8 respecte aussi : Crawnatus en mode défensif 3,5 blocs, Vision Trigonométrique et certaines Synergies 4 blocs, Goût du risque limité à 2 blocs.
- Marques de Sang : Eirika (et ceux que les Synergies y autorisent) en pose une tous les 5 coups ; 5 maximum par adversaire, 6 s chacune ; un adversaire marqué perd 0,5 cœur toutes les 2 s. L'Effroi sanglant et l'Exécution Atroce portent sur les adversaires à 30 blocs.
- Netsu : les coups qu'elle reçoit frappent son clone (4 cœurs de dégâts après armure) pendant 30 s ; quand il est détruit elle vole en papillons 10 s, invisible et intouchable, et un clic droit sur l'item la fait réapparaître où elle vise (30 blocs). L'ultime dure 30 s ; les distances sont des textes flottants visibles d'elle seule ; clic droit sur l'ultime en visant un ennemi (à 100 blocs) pour se téléporter à 4 blocs de lui.
- Kaze : Power Hit s'active quand au plus un adversaire est à 10 blocs. Home Run propulse Kaze et l'ennemi visé (4 blocs) dans son regard. Amour du danger : cœurs = 10 − distance (1 à 9).
- Hannah : Résistance II tant que l'Aldifae Blade est en main ; la barre monte d'un par coup reçu (10 max). Protection chevaleresque donne l'absorption à Hannah et à son partenaire ; les adversaires provoqués ne peuvent blesser qu'elle pendant 10 s ; Relâchement de Rage dure 30 s.
- Synergies : Complémentarité fraternelle donne à Netsu le Power Hit et un Home Run (clic sur la Synergie) ; Invincibles ensemble vérifie la distance chaque seconde ; Défense et Attaque convertit 10 cœurs de dégâts de Kaze sur la dernière minute en 1 Force (IV max) ; Hémorragie Puissante et Défense Acérée durent 30 s et s'appliquent aux deux partenaires.

### Hypothèses prises sur Shoja's Memories

- Hiro : clic droit sur Pyra-Mira lance ou arrête la charge de Puissance (+3/s, barre en haut), Fire Resistance permanente. Grenade et Consumation utilisent le plus haut palier que la Puissance permet et le consomment ; les explosions blessent et enflamment sans casser le terrain, sauf l'ultime qui creuse un vrai cratère de 50 blocs de large (bol peu profond) tapissé de magma, définitif en partie (`/cf clear` en test). Le Dash Explosif est un vol en ligne droite (clic pour arrêter).
- Pinkou : la Rapière soigne le duo de la totalité des dégâts infligés, répartis à parts égales (doublés dans le Cocon). L'Armure de Ronces inflige 2 cœurs à chaque attaquant pendant 10 s (clic sur un allié, sinon le partenaire, sinon elle-même). Les vignes sont un tapis de mousse ; les dégâts de poison soignent le duo tant que la cible est empoisonnée par Pinkou.
- Aïda : les bulles d'eau restent 30 s ; le Clone d'eau annule le premier coup reçu. L'Éponge renvoie les mauvais effets à son dernier attaquant (à 30 blocs) et copie les bons effets de l'adversaire qui la frappe. La Pluie bouillante déclenche la pluie du monde 10 s et blesse les adversaires à ciel ouvert.
- Crystal : ses pouvoirs coûtent de la vie directement (jamais mortel) ; la Lance Adamante est remplacée 5 s par une lance brisée. Œil pour Œil touche tous les joueurs ennemis en ligne.
- Synergies : Pardon Temporaire construit une cage en bois de 11×11 autour de Pinkou (duo et ennemis téléportés dedans, Régénération II et coups enflammés 20 s). Bulles Bouillantes : 10 bulles à 50 blocs pendant 1 minute. Tir Curatif tire tout seul toutes les 2 s (soin de 2 cœurs à la touche).

### Hypothèses prises sur Earth Delta

- Les mutations (une au hasard par mort, cumulables) : Régénération = 1 cœur toutes les 2 s ; Cœurs = +2 cœurs max ; Feu = 10 % par pile d'enflammer à chaque coup ; Vitesse = Speed (II max) après 8 s sans combat ; Explosion = 2 cœurs par pile à chaque membre vivant de l'équipe de la victime. En mode test les morts n'en donnent pas : `/cf delta`.
- Kishi : les cônes sont comptés sur les coups encaissés par toute son équipe (lui compris), durent 30 s (permanents avec l'ultime), et l'Illumilame suit leur nombre. Aura Lumineuse peut viser Kishi lui-même.
- Kura : paliers de kills 0/2/4/6/8 (tout est débloqué en mode test ; `/cf kills`). Voie de l'assassin et Ruée Mortelle ont 15 s de recharge. La Pluie de Couteaux fait au moins 1 cœur.
- Taka : les armes invoquées sont des porte-armures invisibles qui tiennent l'arme ; les adversaires les détruisent en les frappant. L'Épée Flottante est plantée dans le sol et jaillit pour frapper l'adversaire qui s'approche à 2 blocs (une fois par seconde par adversaire), puis se replante. Lance et Hache sont débloquées après 1 et 2 vies perdues (tout en test). Les armes de l'ultime sont indestructibles, durent 1 minute et agissent sur place (les haches tournent autour de leur point d'apparition). Lancées par la Multi-Arme, les armes sont consommées.
- Hano : l'enchantement de l'Henkama est tiré au sort au premier équipement (Flame, Multishot, Piercing, Power, Punch ou Quick Charge), les 3 tomes aussi, parmi 28 pouvoirs ; chaque tome est un item. Tous les actifs ont 30 s de recharge ; Immortalité et Vol Mérité sont à usage unique. L'ultime donne 5 tomes supplémentaires pour 30 s, consommés à l'usage. Le butin de Looting double les Pommes Dorées dans les drops de la victime (pas de coffre de mort). Les araignées et Husks sont des monstres temporaires de 20 s.

### Hypothèses prises sur unOrdinary

- Les épées d'Aura se craftent sur une table (ou la grille 2×2 ne suffit pas) avec une colonne : ingrédient du haut, ingrédient du milieu, bâton. Soin : liane + éclat d'améthyste. Immunité : poche d'encre + coquille de nautile. Laser : redstone + lingot de cuivre. Chaleur Augmentée : seau de lave + brique du Nether. Glue : boule de slime + émeraude. Seuls les personnages unOrdinary peuvent les crafter ; on peut en crafter autant qu'on a d'ingrédients.
- Le clic droit avec l'épée lance son actif (le temps de recharge est commun à toutes les épées du joueur). Soin donne Régénération II. Le Laser touche tout ce qui est sur une ligne de 20 blocs. La zone de Chaleur suit le joueur.
- Arlo : une seule jauge d'Aura (barre en haut) pour tout. La Barrière est un disque doré qui suit Arlo devant lui, la Barrière Ultime une bulle dorée autour de lui ; les deux s'excluent. Enfermement : bulle dorée autour de l'adversaire avec une paroi invisible (blocs barrière) qui bloque coups et projectiles ; Étouffement : toute petite bulle qui le plaque au sol. Chaque bulle coûte son prix par seconde (5 ou 10) et l'adversaire est immobilisé dedans ; quand l'Aura manque, les bulles s'ouvrent. La Bulle géante est une sphère de verre incassable de 30 blocs de rayon, posée en quelques ticks.
- John : frapper un personnage d'une Équipe de Trois (ou un mannequin adversaire) rend ses pouvoirs disponibles ; `/cf choose` (ou l'item Absorption) ouvre un menu en deux étapes (personnage, puis pouvoir) pour en prendre jusqu'à 3, chacun pendant 5 minutes. En mode test, tous les personnages du jeu sont disponibles, sans limite ni expiration. Les capacités liées à une arme, les ultimes et la liste du doc (Sova, Eren, ultimes de Mikasa et Livaï) ne se copient pas. L'ultime de John ouvre le menu des personnages en vie (hors lui) et lance aussitôt l'ultime choisi comme s'il était le sien ; les ultimes « à clics » (Fureur, Pro 3D, Titan…) lui restent 2 minutes.
- Remi : l'Électricité est un éclair en zigzag qui avance de 8 blocs par seconde sur 10 blocs et serpente vers l'adversaire le plus proche dans un cône de 60° devant elle (12 blocs) ; il s'arrête aux murs. Au contact, l'adversaire est électrisé (arcs autour du corps pendant les 2 s d'immobilisation). Le Dôme électrique est fixe à l'endroit du lancement (40 blocs de large) : arcs sur la coupole, éclairs qui tombent à l'intérieur, ennemis électrisés chaque seconde. Pendant Électrocution, Remi a une aura d'arcs bleus. Animations (avec son skin) : au Dash électrique elle fonce le buste en avant, un poing devant elle et l'autre bras rejeté en arrière, entre des traits de vitesse dorés et des arcs bleus, les yeux rouges laissant une traînée (la ruée dure 6 ticks au lieu de 4 pour qu'on la voie) ; pour l'Électricité elle tend la main droite vers sa cible, des arcs crépitant autour de ses doigts ; pour le Dôme électrique elle s'élève de cinq blocs, les poings levés, reste un instant suspendue, puis fond sur le sol et le frappe des deux poings : un éclair la touche à l'impact et le dôme part de là où elle a atterri.
- Seraphina : sa position est relevée à chaque tick ; Retour dans le temps rembobine son trajet des 5 dernières secondes à toute vitesse (en rose, avec des engrenages qui la suivent, brièvement invincible). Pendant l'Arrêt Temporel, le sol de la zone devient gris et une brume grise flotte 3 s ; ses coups sur les ennemis figés sont annulés. L'Explosion temporelle est une onde de choc qui grise le sol de 0 à 30 blocs en une seconde et fige 5 s chaque ennemi qu'elle atteint ; le sol redevient normal après 6 s.

### Hypothèses prises sur Shingeki no Kyojin

- L'équipement tridimensionnel est un grappin (clic droit sur l'arc) : la flèche s'accroche à un bloc ou une entité à 30 blocs et le joueur est tiré jusqu'à elle. Pendant le vol et les cinq secondes qui suivent, les dégâts de chute ne sont plus annulés mais réduits de 75 % ; il en va de même des ruées de style d'Eren et d'Armin, qui passent par l'équipement. La jauge de gaz (barre en haut) baisse de 1 par seconde de vol et remonte de 1 toutes les 2 secondes.
- Les styles : Eren fonce sur un adversaire attrapé (2 cœurs à l'impact, 10 s), Mikasa a Force en permanence, Livaï tournoie 5 s avec clic droit sur ses lames dans les airs, Armin fonce sur un allié attrapé à 20 blocs (10 s).
- La forme de Titan d'Eren dure jusqu'à la mort du Titan : Eren en sort alors là où il est tombé, sans perdre de vie. Le modèle est agrandi (×2), Force, Speed, 15 % de vol de vie, plus de Pommes Dorées, d'équipement ni de Charge. Clic droit sur l'ultime sous cette forme : régénération de 5 cœurs en 3 s (50 s).
- Les 4 cibles primordiales sont tirées au sort parmi les adversaires au début de la partie ; chaque kill d'une cible vole un titan au hasard. Sous forme de Titan, chaque pouvoir volé devient un item à part (dans la barre à la place de l'équipement et de la Charge, puis dans l'inventaire) ; l'item « Titans primordiaux » liste les titans volés. Le Colossal n'a pas de temps de recharge indiqué : 20 s. Les armes du Marteau d'Armes : épée nétherite 12 dmg (4 coups), arbalète à un seul carreau de 4 cœurs, lance (trident) 8 dmg avec frappe à 5 blocs au clic droit (5 coups).
- Mikasa sans Eren vivant dans l'équipe : l'ultime lui donne les bonus sans téléportation. « Eren a moins de 5 cœurs » se lit sur l'Eren de son équipe.
- Les dégâts que Livaï s'inflige ignorent l'armure et ne peuvent pas le tuer. « Allié mort » pour ses camarades tombés = allié éliminé (plus de vies).
- L'Annulation Tactique d'Armin rend l'adversaire incapable d'utiliser une capacité (hors passifs) pendant 5 s. Sa Stratégie et son Abnégation passent par des menus ; l'Abnégation s'applique aussi à Armin.

### Hypothèses prises sur RWBY

- Les Ultimes en Duo ont un item commun « Ultime en Duo » : un clic ouvre le menu de tous les Ultimes en Duo du personnage (un par coéquipière possible), avec pour chacun son nom, son effet et son état : prêt (clic pour le lancer), en charge avec son pourcentage, ou sans coéquipière dans l'équipe. Ils portent le nom de l'attaque d'équipe de la paire dans RWBY : Ice Flower (Ruby et Weiss), Ladybug (Ruby et Blake), Freezerburn (Weiss et Yang), Checkmate (Weiss et Blake), Bumblebee (Blake et Yang), et Strawberry Sunrise (Ruby et Yang, qui n'ont pas d'attaque nommée dans la série : c'est le nom de leur duo). Chaque paire a sa propre jauge, chargée par les dégâts de l'un ou l'autre quand ils sont à moins de 10 blocs.
- Ember Celica inflige 2 cœurs en plus de l'explosion et du feu (non précisé).
- L'invisibilité « totale » (Blake, Ruby, duos) retire vraiment le joueur de l'écran des ennemis, armure comprise ; les alliés le voient toujours.
- La patinoire d'Ice Flower dure 30 secondes ; les piques de glace de Freezerburn 30 secondes ; la barrière de verre de Weiss 10 secondes.
- Gambol Shroud : accroupi + clic droit change de mode, clic droit seul utilise le mode.
- L'Invocation de Weiss ramène un joueur ennemi définitivement éliminé dans son équipe avec une vie, Faiblesse permanente et interdiction de plastron.

### Hypothèses prises sur Fire Emblem

- Le Soutien est commun à la paire : les dégâts des deux alliés font progresser le même niveau, et chaque niveau tire au sort Force, Vitesse ou Résistance 0.5 pour les deux.
- Takumi reçoit un arc Infinité et une flèche, sinon il ne peut pas tirer avant d'avoir des plumes.
- Yato Alpha : « 15 ❤ » est lu comme 15 cœurs de vie maximum.
- L'Épée du Créateur de Byleth fait 8 dégâts (non précisé dans le doc).
- Les dégâts d'une capacité (« inflige 5 cœurs », « perdent 5 cœurs ») ignorent l'armure et ses enchantements de protection : la cible perd le nombre de cœurs annoncé (moins Résistance et cœurs d'absorption). Les coups d'arme, les flèches et les bonus de dégâts sur un coup passent par l'armure comme un coup normal. `abilities.ignore-armor: false` dans `config.yml` fait de nouveau passer les capacités par l'armure.
- Sang Draconique de Corrin lui fait pousser des ailes de dragon pendant le vol : membrane translucide rose pâle à la racine et blanche vers les bouts, arêtes et griffes argentées, battement rapide en vol puis lent en descente, éclaboussures d'eau ; elles disparaissent à l'atterrissage.
- Le Contre de Marth reprend celui de Smash Bros Melee : pendant la seconde de pose il scintille en blanc et ne peut ni marcher ni sauter (il peut tourner la tête ; un contre réussi le libère aussitôt) ; au coup reçu il se tourne vers l'attaquant, un grand croissant d'épée balaye devant lui, les dégâts sont renvoyés et l'adversaire est projeté au loin (environ 10 blocs) avant d'être immobilisé 1 s.
- Le Coup Critique de Marth affiche au-dessus de chaque ennemi touché la barre de vie des combats de Fire Emblem (GBA) : un nombre qui descend de 100 à 0 et 40 traits qui se vident un à un en deux secondes, avec un cliquetis, avant de disparaître. C'est un effet purement visuel, sans rapport avec la vie réelle de la cible (`util/HealthBar.java`, qui sait aussi afficher la vraie perte de vie).

## Feuille de route

Où en est le projet, ce qu'il reste, et dans quel ordre le prendre. Mise à jour à chaque étape.

### Ce qui est fait

- [x] Socle UHC : équipes, licences, vies, PvP, restrictions, tableau de bord, administrateurs de partie et règles
- [x] Moteur de capacités et Ultime ; les 39 personnages des dix licences (Fire Emblem, RWBY, Valorant, Shingeki no Kyojin, unOrdinary, Earth Delta, Armed Blue Gunvolt, Shoja's Memories, Bloody Snow, solitaires)
- [x] Sons : pack de ressources, bruitages par capacité, système de voix et de musiques de combat
- [x] Événements : Lootbox, Regna Ferox, Omori, événement des lieux (un lieu renforcé, un affaibli, toutes les 20 minutes)
- [x] Map : génération, minerais multipliés, lieux posés sur la map, monde déchiré autour d'eux, carte de la map
- [x] Lieux, quêtes et récompenses :

  | Lieu | Quête | Récompense | État |
  |---|---|---|---|
  | Garreg Mach | pêche (10 poissons) | enchantements d'Anna | refait |
  | Shiganshina | Titan en cent coups | forge en musique | refait |
  | Pearl | perles à ramasser | potions d'Anna | quête d'origine |
  | Central Town | quatre énigmes (tablette, circuit, établi, puzzle) | marchand d'objets de lootbox | refait |
  | Arcadia | casse-briques (15 pommes) | roulette de pommes dorées | refait |
  | Terrace | montée par cinq drapeaux | expérience dans le lieu | refait |
  | Quartier général | une offre à payer | carte des joueurs, fiches, traque | refait |
  | Beacon | quatre reliques à ramasser | lootbox garantie | quête d'origine |
  | Forest of Hope | trois pièces à rapporter | chasse aux pièces | quête d'origine |

- [x] Interface : menus d'Anna, panneau de quête et sa jauge, épreuves, orbe de l'ultime, avec le pack d'interface

### 1. Vérifier en vraie partie (le plus urgent)

Presque tout a été essayé avec des bots ou seul dans le client, jamais dans une partie à plusieurs. Avant d'ajouter du contenu, une partie d'essai complète dira ce qui tient.

- [ ] Une partie entière à plusieurs joueurs, du lancement à la victoire
- [ ] Durée des quêtes à la main : casse-briques, Titan, pêche, énigmes, montée aux drapeaux (accessibilité des drapeaux)
- [ ] Synchronisation des musiques de la forge avec leurs notes
- [ ] Valeurs à régler après essai : dégâts du Titan, prix du marchand, lots de la roulette, bonus et malus de l'événement des lieux, récompenses de la forge
- [ ] Limite de deux équipes par lieu et menu d'éviction, à plusieurs équipes
- [ ] Quartier général en partie : joueurs éliminés retirés de la carte, reprise du lieu

### 2. Finir les lieux

- [ ] Pearl, Beacon et Forest of Hope : les trois dernières quêtes d'origine, à refaire comme les autres (ou à garder)
- [ ] Récompenses de Beacon et de Forest of Hope à revoir dans le même esprit
- [ ] Lieux Lotus et Glitch : fichiers manquants
- [ ] Décider : tous les lieux à chaque partie, ou une partie tirée au sort

### 3. Événements à concevoir

- [ ] Match Found (prévu au quartier général)
- [ ] Judgement

### 4. Habillage et contenu

- [ ] Voix des personnages et musiques (fichiers à fournir), répliques des passifs à déclencheur
- [ ] Armes en 3D : intégrer en jeu les modèles une fois retouchés (seuls la Falchion et le Yumi Fujin le sont)
- [ ] Menus encore nus : l'offre et la fiche du quartier général
- [ ] Reste du pack d'interface : boutons, cases à cocher, grimoire, cases à pictogramme
- [ ] GitBook : images et vidéos par personnage et par capacité (commencé, en attente)

### 5. Avant d'ouvrir à d'autres joueurs

- [ ] Équilibrage des 39 personnages après plusieurs parties
- [ ] Guide du joueur relu de bout en bout
- [ ] Hébergement du pack complet et procédure d'installation du serveur

### Combat façon 1.8

Activé par `pvp.legacy-combat` dans `config.yml` : aucun temps de recharge d'attaque, pas
d'attaque balayante, et maintenir le clic gauche sur un ennemi à portée enchaîne les coups.

### Mannequins adversaires

Une capacité peut être « simulée » par un mannequin si elle implémente `EnemyCastable` : elle
est alors écrite pour un lanceur quelconque (position, direction, filtre de cibles) et non pour
un joueur. C'est le cas des capacités qui visent les ennemis : Coup Critique, Épée du Créateur,
Déchaînement d'Épine, Vagues Bleues, Rayon Démoniaque. Les passifs, les soins et les buffs
personnels n'ont pas d'item adverse.

## Documentation GitBook

Le site GitBook a deux espaces, déclarés par `gitbook-docs.yaml` à la racine :

- **Guide du joueur** (`guide/`, l'espace par défaut) : les règles, le déroulement, les lieux et les quêtes, les événements, les personnages et leurs capacités, sans rien de technique. `python3 tools/build_guide.py` le refabrique : les pages générales sont écrites dans le script, les pages des personnages sont tirées du code (noms, capacités, descriptions, temps de recharge) et la table des objets de Lootbox de ce README. À relancer quand une règle, une quête ou un personnage change.
- **Documentation technique** (`docs/`) : ce README découpé en pages, avec son sommaire. `python3 tools/build_docs.py` la refabrique après chaque changement du README.

Le site est branché sur ce dépôt par Git Sync (branche `main`, sens GitHub → GitBook) : chaque envoi sur GitHub le met à jour. Ne rien modifier dans GitBook, ce serait écrasé.

## Télécharger le plugin sans compiler

Le dernier jar compilé est dans le dossier `dist/` :
https://github.com/kiokouwhite/crossfight-uhc/raw/main/dist/CrossfightUHC.jar

Le pack de sons est téléchargé par le jeu lui-même à la connexion ; il n'y a rien à installer à la main.
