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
| `armure-depart` | oui | Les joueurs commencent avec une armure en fer complète, Protection I, déjà portée |
| `lootbox`, `regna-ferox`, `match-found`, `judgement`, `omori` | 20 à 60 min | Minute de chaque événement |
| `lootbox-nombre` | 6 | Nombre de Lootbox qui apparaissent |

La bordure se réduit maintenant d'elle-même : à sa minute, elle passe en `bordure-duree` minutes de son rayon de départ à son rayon final, avec une annonce cinq minutes avant.
