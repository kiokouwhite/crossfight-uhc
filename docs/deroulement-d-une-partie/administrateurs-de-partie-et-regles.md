# Administrateurs de partie et règles

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
| `blocs-poses` | 15 s | Les blocs posés par les joueurs se cassent d'eux-mêmes après ce délai, en se fissurant, et lâchent leur objet (0 : jamais). Restent en place les blocs qu'on ouvre ou qu'on utilise (établi, four, coffre, enclume, lit…) et les sources de lumière (torches, lanternes) |
| `armures-incassables` | oui | Les pièces d'armure (casque, plastron, jambières, bottes, élytres) ne s'usent pas, d'où qu'elles viennent |
| `armure-depart` | oui | Les joueurs commencent avec une armure en fer complète, Protection I, déjà portée |
| `annonce-vies` | 20 min | Les morts et les vies perdues ne sont plus dites dans le chat (seuls la victime et le tueur le savent, la victime par un message, le tueur dans sa barre d'action). Toutes les N minutes, un hologramme s'allume dans le ciel, devant chaque joueur, pendant une quinzaine de secondes : une carte par joueur qui a perdu une vie depuis le dernier bilan (trois par rangée), avec son pseudo, le personnage qu'il incarne et sa tête, et une rangée de cœurs, ses vies avant le bilan. Les cœurs perdus grésillent, pâlissent, gonflent puis éclatent l'un après l'autre ; s'il n'en reste aucun, « ÉLIMINÉ » s'allume à leur place. Dessous, les équipes éliminées. L'hologramme se projette à partir d'une ligne, une ligne de lumière le balaie, il grésille de temps en temps, et il suit le regard (décalé vers la gauche, à cause du tableau de score). À l'air libre, chaque carte a son panneau sombre ; sous terre, l'hologramme se voit à travers les blocs, sans panneaux. `/cf annonce` (admins) l'affiche tout de suite ; en mode test, avec des exemples s'il n'y a pas eu de mort |
| `a-terre` | oui | En équipe, un coup mortel met à terre au lieu de tuer (détail plus bas, « À terre ») |
| `seau-de-lave` | non | Sur « non », un seau de lave ne se pose pas (on peut toujours en remplir un). Sur « oui », les seaux de lave peuvent être vidés |
| `briquet` | non | Sur « non », les briquets et les boules de feu n'allument rien (bloc, TNT, creeper). Sur « oui », ils allument un feu |
| `aura-fire-aspect` | non | Sur « non », ni la table d'enchantement ni l'enclume ne donnent Aura de feu (Fire Aspect) aux épées d'Aura (unOrdinary). Sur « oui », elles peuvent la recevoir |
| `roche-en-cobble` | oui | Dans le monde de jeu, toute roche minée qui n'est pas un minerai (pierre, ardoise des abîmes, granite, diorite, andésite, tuf, calcite, spéléothème, basalte, pierre noire) donne de la pierre taillée, qui s'empile par 99 (le maximum que Minecraft autorise) |
| `combat-1.7` | oui | Combat de la 1.7.10 : pas de délai d'attaque, 6 coups par seconde au plus, recul, coups critiques, armure et Tranchant d'époque, canne à pêche qui repousse (détail plus bas, « Combat façon 1.7.10 ») |
| `clic-maintenu` | non | Sur « oui », avec le combat 1.7 : après un clic sur un ennemi, les coups s'enchaînent seuls à 6 par seconde tant qu'il reste dans le viseur |
| `blocage-epee` | non | Sur « oui », avec le combat 1.7 : le clic droit maintenu à l'épée pare la moitié des dégâts (sauf l'épée dont le clic droit lance une capacité) |
| `lootbox`, `regna-ferox`, `match-found`, `judgement`, `omori` | 20 à 60 min | Minute de chaque événement |
| `lootbox-nombre` | 6 | Nombre de Lootbox qui apparaissent |

La bordure se réduit maintenant d'elle-même : à sa minute, elle passe en `bordure-duree` minutes de son rayon de départ à son rayon final, avec une annonce cinq minutes avant.
