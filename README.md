# Crossfight UHC

Plugin Paper 1.21 pour le mode de jeu **Crossfight UHC** : un UHC en solo, duo ou trio
où chaque joueur incarne un personnage tiré d'une licence (Fire Emblem, RWBY, Valorant,
Shingeki no Kyojin, unOrdinary, Earth Delta, Armed Blue Gunvolt, Shoja's Memories,
Bloody Snow, personnages solitaires).

Règles complètes : https://kiokouwhite.gitbook.io/crossfight-uhc

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
| `/cf setsize SOLO|DUO|TRIO` | Taille des équipes (admin) |
| `/cf setborder <rayon>` | Taille de la map (admin) |
| `/cf setpvp <minute>` | Minute d'activation du PvP (admin) |
| `/cf team create|join <id>|leave|list` | Gestion des équipes dans le lobby |
| `/cf team add <joueur> <id>` | Forcer un joueur dans une équipe (admin) |
| `/cf perso [id]` | Ouvrir le menu de choix ou choisir un personnage |
| `/cf give <joueur> <perso>` | Forcer un personnage (admin, pour tester) |
| `/cf licences` | Lister les licences et personnages |
| `/vivre` | Réapparaître quand on attend la mort de son équipe |
| `/cf test` | Entrer ou sortir du mode test (monde plat, équipement fourni) |
| `/cf change [perso]` | Changer de personnage en mode test |
| `/cf clear` | Remettre le terrain de test à neuf (blocs, entités, mannequins) |
| `/cf mannequin adversaire <perso>` | Un mannequin qui incarne le personnage : ses capacités offensives sont données en items, et les activer les lance depuis le mannequin contre toi |
| `/cf mannequin [nb] [attaque\|allie]` | Mannequins d'entraînement (ennemis, agressifs ou alliés), `/cf mannequin clear` pour les retirer |
| `/cf ult [joueur] <pourcent>` | Régler la jauge d'Ultime (test) |
| `/cf heal` | Se soigner (test) |
| `/cf ally <testeur>` | Rejoindre l'équipe d'un autre testeur en mode test |
| `/cf duo [pourcent]` | Remplir les jauges d'Ultime en Duo (RWBY, test) |
| `/cf credits [n]` | Régler ses crédits Valorant (test) |
| `/cf titan [nom\|tous]` | Donner à Eren les pouvoirs d'un titan primordial (test) |
| `/cf aura <type>` | Recevoir une épée d'Aura sans la crafter (test) |
| `/cf choose` | John : choisir ses pouvoirs absorbés |

## Déroulement d'une partie

1. **Lobby** : les joueurs forment leurs équipes (ou sont répartis automatiquement au lancement).
2. **Sélection** : une licence est tirée au sort par équipe, chacun choisit un personnage (60 s, puis attribution automatique).
3. **Lancement** : téléportation par équipe, armes et items de capacités donnés.
4. **Partie** : PvP à 30 min, événements à 20 / 30 / 40 / 50 / 60 min, 3 vies par joueur.
5. **Fin** : dernière équipe en vie.

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
│   └── unordinary/         unOrdinary : épées d'Aura, Arlo, John, Remi, Seraphina
├── listener/               PvP, charge d'ultime, mort, restrictions d'équipement
└── command/                /cf et /vivre
```

### Charge de l'Ultime

Une barre de vie pleine (10 cœurs) vaut 100 % de dégâts. Chaque 1 % infligé **ou** reçu
charge l'Ultime de 0,25 % (`ultimate.charge-per-percent-damage` dans `config.yml`).

### Bonus fractionnaires

Le doc parle de « Force 0.5 » ou « +0.2 Vitesse ». Les potions ne le permettent pas, donc
le plugin applique des modificateurs d'attributs : Force 1 = +3 dégâts de mêlée,
Vitesse 1 = +20 % de vitesse, Résistance 1 = -20 % de dégâts reçus (plafond 80 %).

### Mode test

En mode test, aucune capacité n'a de temps de recharge et l'Ultime est toujours chargé.
Les chargeurs, munitions et jauges fonctionnent normalement.

### Hypothèses prises sur Valorant

- Les armes blanches : le doc ne détaille pas encore les catégories et embranchements. En attendant, trois paliers (6, 7.5, 9 dégâts) atteints tous les 3 kills.
- Les armes à feu tirent des balles (flèches invisibles habillées) avec la puissance indiquée ; « Puissance 0 » vaut une flèche sans enchantement. Un tir à la tête double les dégâts pour Sheriff, Marshal et Operator. Le rechargement est automatique quand le chargeur est vide, ou accroupi + clic droit.
- L'Ares et l'Odin tirent tout leur chargeur d'un coup, immobile, puis Lenteur II 5 s.
- Les +100 crédits toutes les 10 minutes suivent l'horloge de la partie.
- Le drone de Sova est un allay qui suit Sova et tire une flèche de Faiblesse par seconde sur l'ennemi le plus proche à 20 blocs.
- L'Ultime de Neon donne une lame électrique de 10 dégâts le temps que la jauge de Vitesse Supérieure se vide.
- La Résurrection de Sage réutilise les règles de l'Invocation de Weiss : une vie, Faiblesse, pas de plastron.

### Hypothèses prises sur unOrdinary

- Les épées d'Aura se craftent sur une table (ou la grille 2×2 ne suffit pas) avec une colonne : ingrédient du haut, ingrédient du milieu, bâton. Soin : liane + éclat d'améthyste. Immunité : poche d'encre + coquille de nautile. Laser : redstone + lingot de cuivre. Chaleur Augmentée : seau de lave + brique du Nether. Glue : boule de slime + émeraude. Seuls les personnages unOrdinary peuvent les crafter ; on peut en crafter autant qu'on a d'ingrédients.
- Le clic droit avec l'épée lance son actif (le temps de recharge est commun à toutes les épées du joueur). Soin donne Régénération II. Le Laser touche tout ce qui est sur une ligne de 20 blocs. La zone de Chaleur suit le joueur.
- Arlo : une seule jauge d'Aura (barre en haut) pour tout. La Barrière est un disque doré qui suit Arlo devant lui, la Barrière Ultime une bulle dorée autour de lui ; les deux s'excluent. Enfermement : bulle dorée autour de l'adversaire avec une paroi invisible (blocs barrière) qui bloque coups et projectiles ; Étouffement : toute petite bulle qui le plaque au sol. Chaque bulle coûte son prix par seconde (5 ou 10) et l'adversaire est immobilisé dedans ; quand l'Aura manque, les bulles s'ouvrent. La Bulle géante est une sphère de verre incassable de 30 blocs de rayon, posée en quelques ticks.
- John : frapper un personnage d'une Équipe de Trois (ou un mannequin adversaire) rend ses pouvoirs disponibles ; `/cf choose` (ou l'item Absorption) ouvre un menu en deux étapes (personnage, puis pouvoir) pour en prendre jusqu'à 3, chacun pendant 5 minutes. En mode test, tous les personnages du jeu sont disponibles, sans limite ni expiration. Les capacités liées à une arme, les ultimes et la liste du doc (Sova, Eren, ultimes de Mikasa et Livaï) ne se copient pas. L'ultime de John ouvre le menu des personnages en vie (hors lui) et lance aussitôt l'ultime choisi comme s'il était le sien ; les ultimes « à clics » (Fureur, Pro 3D, Titan…) lui restent 2 minutes.
- Remi : l'Électricité est un éclair en zigzag qui avance de 8 blocs par seconde sur 10 blocs et serpente vers l'adversaire le plus proche dans un cône de 60° devant elle (12 blocs) ; il s'arrête aux murs. Au contact, l'adversaire est électrisé (arcs autour du corps pendant les 2 s d'immobilisation). Le Dôme électrique est fixe à l'endroit du lancement (40 blocs de large) : arcs sur la coupole, éclairs qui tombent à l'intérieur, ennemis électrisés chaque seconde. Pendant Électrocution, Remi a une aura d'arcs bleus.
- Seraphina : sa position est relevée à chaque tick ; Retour dans le temps rembobine son trajet des 5 dernières secondes à toute vitesse (en rose, avec des engrenages qui la suivent, brièvement invincible). Pendant l'Arrêt Temporel, le sol de la zone devient gris et une brume grise flotte 3 s ; ses coups sur les ennemis figés sont annulés. L'Explosion temporelle est une onde de choc qui grise le sol de 0 à 30 blocs en une seconde et fige 5 s chaque ennemi qu'elle atteint ; le sol redevient normal après 6 s.

### Hypothèses prises sur Shingeki no Kyojin

- L'équipement tridimensionnel est un grappin (clic droit sur l'arc) : la flèche s'accroche à un bloc ou une entité à 30 blocs et le joueur est tiré jusqu'à elle, sans dégâts de chute. La jauge de gaz (barre en haut) baisse de 1 par seconde de vol et remonte de 1 toutes les 2 secondes.
- Les styles : Eren fonce sur un adversaire attrapé (2 cœurs à l'impact, 10 s), Mikasa a Force en permanence, Livaï tournoie 5 s avec clic droit sur ses lames dans les airs, Armin fonce sur un allié attrapé à 20 blocs (10 s).
- La forme de Titan d'Eren dure jusqu'à la mort du Titan : Eren en sort alors là où il est tombé, sans perdre de vie. Le modèle est agrandi (×2), Force, Speed, 15 % de vol de vie, plus de Pommes Dorées, d'équipement ni de Charge. Clic droit sur l'ultime sous cette forme : régénération de 5 cœurs en 3 s (50 s).
- Les 4 cibles primordiales sont tirées au sort parmi les adversaires au début de la partie ; chaque kill d'une cible vole un titan au hasard. Sous forme de Titan, chaque pouvoir volé devient un item à part (dans la barre à la place de l'équipement et de la Charge, puis dans l'inventaire) ; l'item « Titans primordiaux » liste les titans volés. Le Colossal n'a pas de temps de recharge indiqué : 20 s. Les armes du Marteau d'Armes : épée nétherite 12 dmg (4 coups), arbalète à un seul carreau de 4 cœurs, lance (trident) 8 dmg avec frappe à 5 blocs au clic droit (5 coups).
- Mikasa sans Eren vivant dans l'équipe : l'ultime lui donne les bonus sans téléportation. « Eren a moins de 5 cœurs » se lit sur l'Eren de son équipe.
- Les dégâts que Livaï s'inflige ignorent l'armure et ne peuvent pas le tuer. « Allié mort » pour ses camarades tombés = allié éliminé (plus de vies).
- L'Annulation Tactique d'Armin rend l'adversaire incapable d'utiliser une capacité (hors passifs) pendant 5 s. Sa Stratégie et son Abnégation passent par des menus ; l'Abnégation s'applique aussi à Armin.

### Hypothèses prises sur RWBY

- Les Ultimes en Duo ont un item commun « Ultime en Duo » : un clic lance l'ultime de la paire prête, ou ouvre un choix s'il y en a plusieurs. Chaque paire a sa propre jauge, chargée par les dégâts de l'un ou l'autre quand ils sont à moins de 10 blocs.
- Ember Celica inflige 2 cœurs en plus de l'explosion et du feu (non précisé).
- L'invisibilité « totale » (Blake, Ruby, duos) retire vraiment le joueur de l'écran des ennemis, armure comprise ; les alliés le voient toujours.
- La Patinoire dure 30 secondes ; les piques de glace 30 secondes ; la barrière de verre de Weiss 10 secondes.
- Gambol Shroud : accroupi + clic droit change de mode, clic droit seul utilise le mode.
- L'Invocation de Weiss ramène un joueur ennemi définitivement éliminé dans son équipe avec une vie, Faiblesse permanente et interdiction de plastron.

### Hypothèses prises sur Fire Emblem

- Le Soutien est commun à la paire : les dégâts des deux alliés font progresser le même niveau, et chaque niveau tire au sort Force, Vitesse ou Résistance 0.5 pour les deux.
- Takumi reçoit un arc Infinité et une flèche, sinon il ne peut pas tirer avant d'avoir des plumes.
- Yato Alpha : « 15 ❤ » est lu comme 15 cœurs de vie maximum.
- L'Épée du Créateur de Byleth fait 8 dégâts (non précisé dans le doc).
- Le Soutien et les dégâts des capacités passent par l'armure comme un coup normal.

## Feuille de route

- [x] Socle UHC : équipes, licences, vies, PvP, restrictions, scoreboard
- [x] Moteur de capacités et Ultime
- [x] Fire Emblem : Soutien C à S, Marth, Corrin, Takumi, Byleth
- [x] RWBY : Ultimes en Duo, Ruby, Yang, Weiss, Blake
- [x] Valorant : crédits et boutique de 17 armes à feu, paliers d'armes blanches, Neon, Sage, Sova, Jett
- [x] Shingeki no Kyojin : équipement tridimensionnel et gaz, Eren (et ses 7 titans), Mikasa, Livaï, Armin
- [x] unOrdinary : épées d'Aura à crafter, Arlo, John (absorption), Remi, Seraphina
- [ ] Autres systèmes de licence (gaz, crédits, épées d'Aura, Synergie, Burst...)
- [ ] 16 personnages restants
- [ ] Lootbox, Regna Ferox, Match Found, Judgement, Omori

### Combat façon 1.8

Activé par `pvp.legacy-combat` dans `config.yml` : aucun temps de recharge d'attaque, pas
d'attaque balayante, et maintenir le clic gauche sur un ennemi à portée enchaîne les coups.

### Mannequins adversaires

Une capacité peut être « simulée » par un mannequin si elle implémente `EnemyCastable` : elle
est alors écrite pour un lanceur quelconque (position, direction, filtre de cibles) et non pour
un joueur. C'est le cas des capacités qui visent les ennemis : Coup Critique, Épée du Créateur,
Déchaînement d'Épine, Vagues Bleues, Rayon Démoniaque. Les passifs, les soins et les buffs
personnels n'ont pas d'item adverse.

## Télécharger le plugin sans compiler

Le dernier jar compilé est dans le dossier `dist/` :
https://github.com/kiokouwhite/crossfight-uhc/raw/main/dist/CrossfightUHC.jar
