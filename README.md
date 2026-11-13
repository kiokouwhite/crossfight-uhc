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
| `/cf musique [piste\|stop]` | Lister les musiques de combat, en écouter une, l'arrêter (test) |

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
le plugin applique des modificateurs d'attributs : Force 1 = +3 dégâts de mêlée,
Vitesse 1 = +20 % de vitesse, Résistance 1 = -20 % de dégâts reçus (plafond 80 %).

### Sons, voix et musiques

Le plugin envoie à chaque joueur un pack de ressources (`dist/CrossfightPack.zip`, adresse dans `config.yml`, section `resource-pack`). Tout y est rangé sous l'espace de noms `crossfight` :

- **Bruitages** : chaque capacité réussie joue `ability.<id>`. `resourcepack/abilities.txt` associe chaque capacité à une catégorie de la bibliothèque (`sounds/sfx/<catégorie>/`, 28 catégories de sons libres, crédits dans `resourcepack/CREDITS.md`). Un fichier `sounds/ability/<id>.ogg` remplace la catégorie pour cette capacité.
- **Voix** : `sounds/voice/<clé>.ogg`. Pour l'Ultime en Duo de RWBY, le lanceur dit `<perso>_duo_<partenaire>` (appel de partenaire de BlazBlue) et la partenaire répond `<partenaire>_nom_<perso>`. Les quatre membres de RWBY ont aussi leurs échanges de sélection (les entrées en duo de BlazBlue Cross Tag Battle), pour les six paires. Une réplique qui répond à une autre attend la fin de la première : `build_pack.py` écrit la durée de chaque réplique dans `durations.txt`, embarqué dans le plugin (`/cf dialogue <perso> <perso>` pour écouter un échange). La clé est l'id de la capacité, ou `<perso>_selection`, `<perso>_mort`, `<perso>_kill`, `<perso>_burst`, `syn_<a>_<b>_<perso>` (Synergie), `duo_<a>_<b>_<perso>` (échange des deux membres d'un duo après le choix des personnages). `<clé>_1.ogg`, `<clé>_2.ogg` sont des variantes tirées au hasard. Les voix sont entendues autour du personnage ; la réplique de sélection, par le joueur seul.
- **Musiques de combat** : `sounds/music/<piste>.ogg`, et `resourcepack/music.txt` dit quelles licences la déclenchent (`licence_a + licence_b = piste`). La musique démarre au premier coup entre deux équipes, repart sur elle-même tant qu'elles se battent (la reprise est lancée `music.loop-overlap-ms` avant la fin de la piste, et `add_audio.py` retire les silences de début et de fin), et s'arrête après `music.silence-seconds` (20 s) sans coup ou quand une équipe n'a plus de joueur en vie. Elle passe par le réglage « Juke-box / Blocs musicaux » du joueur.

Un son absent du pack n'est simplement pas joué : ajouter une voix ou une musique ne demande aucun changement de code.

```
python3 tools/add_audio.py music "ma piste.mp3"        # convertit et range une musique
python3 tools/add_audio.py voice netsu.wav netsu_ult   # une réplique
python3 tools/build_pack.py                            # reconstruit le pack, puis recompiler le plugin
```

`build_pack.py` écrit aussi dans le jar la liste des sons, l'empreinte du pack et la table des musiques : le jar et le pack publiés doivent donc venir de la même construction.

**Sons sous droits (voix officielles, musiques non libres)** : ils ne vont jamais sur ce dépôt. Ils se rangent dans `resourcepack-prive/` (même structure, ignoré par git) avec `add_audio.py --prive`. `build_pack.py` construit alors deux packs : `dist/CrossfightPack.zip` (public, sans ces sons) et `prive/CrossfightPackComplet.zip` (tout, ignoré par git). Seuls les noms des sons privés sont versionnés (`resourcepack/prive.txt`), pour que le plugin sache les jouer. Pour que les joueurs les entendent, deux possibilités :

- héberger `CrossfightPackComplet.zip` à une adresse de téléchargement direct non publique, puis mettre cette adresse dans `resource-pack.url` et l'empreinte affichée par `build_pack.py` dans `resource-pack.sha1` ;
- ou que chaque joueur dépose `CrossfightPackComplet.zip` dans son dossier `resourcepacks` et l'active (le pack public envoyé par le serveur peut rester en place).

Un son qui a une version privée n'est pas déclaré dans le pack public : Minecraft additionne les déclarations d'un même son venant de deux packs, et tirerait sinon au hasard entre le bruitage libre et le vrai. Avec le seul pack public, ces quelques capacités sont donc muettes.

Sur une machine qui n'a plus `resourcepack-prive/`, `python3 tools/build_pack.py --depuis CrossfightPackComplet.zip` le recrée avant d'ajouter de nouveaux sons.

```
python3 tools/add_audio.py --prive voice vc_marth_appeal01.wav marth_ult
```

### Skins des personnages

Le joueur porte le skin Minecraft du personnage qu'il incarne, dès qu'il le choisit (menu de sélection, `/cf change`), et retrouve le sien en quittant le mode test ou au retour au lobby. Les personnages sans skin (les originaux, pour l'instant) laissent au joueur son propre skin. `skins.enabled: false` dans `config.yml` coupe tout.

Minecraft n'affiche qu'un skin signé par Mojang : `python3 tools/sign_skins.py <dossier de <personnage>.png>` fait signer les images par MineSkin (en « non listé ») et inscrit les références dans `src/main/resources/skins.txt`. Ce fichier ne contient pas les images, seulement leur référence signée chez Mojang. Le changement de skin recharge un instant l'affichage du joueur.

### Armure invisible

L'armure portée n'est plus dessinée sur le corps : on voit le skin du personnage, pour le joueur comme pour les autres. Dès qu'un joueur enfile une pièce, le plugin remplace le modèle à dessiner (composant `equippable`, champ `asset_id`) par un modèle vide du pack (`equipment/hidden.json`) : elle protège autant, garde son icône et ses enchantements, mais ne se voit plus une fois portée. Le changement est inscrit dans l'objet : une pièce déjà portée reste invisible même si on la donne à quelqu'un d'autre. `armor.hidden: false` dans `config.yml` arrête de modifier les nouvelles pièces. Les élytres ne sont pas concernés.

### Animations (modèle articulé)

Minecraft ne permet pas de plier librement les membres d'un joueur. Pour une pose sur mesure, le joueur devient invisible un instant et un pantin à son image prend la pose à sa place : tête, torse, bras, jambes et objet tenu sont sept objets affichés (`ItemDisplay`), chacun centré sur son articulation, que `util/Puppet.java` fait tourner et suivre le joueur.

- `python3 tools/add_skin.py skin.png <personnage> [--prive]` fabrique le modèle à partir d'un skin Minecraft 64×64 (bras fins détectés). Avec `--prive`, il reste hors du dépôt, comme les voix officielles.
- Les poses sont dans `src/main/resources/poses.txt` (angles de chaque partie, en degrés).
- `python3 tools/pose_preview.py <personnage> <pose> [image.png] [--prive]` dessine une pose sous cinq angles sans lancer le jeu.
- En jeu : `/cf pose <nom> [secondes]`, à regarder en vue extérieure (F5).
- `poses.enabled: false` dans `config.yml` coupe tout. Sans le modèle dans le pack, la capacité garde simplement ses effets habituels.

Animations de Marth :

- **Contre** : garde, épée tenue haut lame vers le bas, puis fente avant à la riposte.
- **Falchion** : l'épée plantée dans le sol devant lui, les deux mains sur le pommeau, tête baissée, pendant les 3 secondes du soin.
- **Coup Critique**, d'après le Final Smash de Smash Bros : moulinet, épée levée vers le ciel avec un éclat à la pointe (0,8 s, immobile, pendant lesquelles il choisit sa direction en regardant), il se ramasse, fonce à l'horizontale dans une traînée blanche et bleue, puis tient l'estoc bras tendu. Il est invulnérable de la levée d'épée à la fin de la charge. Bruitages d'origine dans le pack privé : `ability.marth_ult_eclat`, `ability.marth_ult_charge`, `ability.marth_ult_hit`.

Animation de Corrin : pendant son Ultime, elle se met à quatre pattes comme son dragon, les deux bras plantés dans le sol, l'eau jaillissant autour de ses mains et ses ailes de dragon déployées sur son dos ; elle ne peut pas bouger pendant cette pose (1,5 s).

Animation de Takumi : en lançant Contre Rapproché, il lève son arc bien haut et le jette violemment au sol (l'arc rebondit et reste par terre, visible de tous, tant que dure la lame). Au bout des 30 secondes, l'arc file jusqu'à ses pieds dans un souffle de vent, même s'il s'est éloigné, et il se baisse pour le ramasser. Le pantin tient un arc par le milieu de son bois (`Puppet.held` change l'objet tenu en cours d'animation). Pour Vengeance, il tend les deux bras devant lui et ils s'embrasent, du poing à l'épaule (seulement les bras) ; ils restent en feu pendant les 5 secondes. Pour Furie, il lève le poing et frappe le sol, un genou à terre (immobile 0,7 s) : l'impact crache du feu, une onde de flammes s'élargit au ras du sol et une colonne de flammes monte en vrille et l'engloutit ; quelques flammes l'entourent ensuite tant que dure la Furie. Ce ne sont que des particules : rien ne brûle.

Pour le Rayon Démoniaque, d'après la scène de Fire Emblem Fates : Takumi se met de profil et bande son arc (tenu dans la main gauche, la droite ramenée sous le menton) pendant les 4 secondes de charge, enveloppé de flammes violettes en vrille et d'orbes noirs, les yeux rouges, pendant qu'un éclat blanc-rose pulse de plus en plus vite à la pointe de la flèche ; au tir, sa main droite part en arrière.

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

Pendant l'animation, les autres joueurs ne voient plus l'équipement du joueur (paquet d'équipement vide), et le joueur lui-même ne voit plus ni sa propre armure ni l'objet qu'il tient en vue extérieure : ses quatre cases d'armure lui sont envoyées vides, et la case qu'il tient reçoit un objet sans apparence (`items/hidden.json`), si bien que le clic droit part toujours. Son inventaire réel n'est pas touché et lui est renvoyé à la fin ; l'icône de l'objet tenu disparaît de sa barre le temps de l'animation. Les 25 personnages de licences existantes ont leur modèle dans le pack privé ; les personnages originaux attendent leur skin.

### Écran de sélection en 3D
Au début d'une partie, chaque équipe est emmenée dans un monde à part, de nuit, devant un mur de panneaux inclinés : un par personnage de sa licence, avec son illustration, son nom sur un bandeau à sa couleur et l'icône de sa voie, le logo de la licence au-dessus. Le joueur vise un panneau avec son viseur : il s'illumine (halo et contour à la couleur du personnage, l'illustration grandit et avance) et la fiche du personnage s'affiche (voie et capacités) ; un clic (gauche ou droit) le choisit. Les coéquipiers voient le choix en direct (« CHOISI » et le pseudo sur le panneau), un personnage déjà pris est refusé, et la barre en haut de l'écran décompte les 60 secondes. Chaque joueur a ses propres affichages : l'effet de survol suit son seul regard. Les joueurs ne peuvent pas bouger mais peuvent tourner la tête ; au lancement, l'écran disparaît et la partie les téléporte. Un joueur qui se reconnecte pendant le choix retrouve l'écran de son équipe. `selection.scene-3d: false` dans `config.yml` remet l'ancien menu d'inventaire.

Les images sont des objets du pack affichés par des `ItemDisplay`, préparés par `tools/build_selection.py` :
- **publiques** (versionnées) : le fond incliné des panneaux, le halo, et un bandeau par personnage (couleur et nom de `resourcepack/selection.txt`, en Inter Black Italic) ;
- **privées** (jamais versionnées, comme les voix) : les illustrations, les icônes des voies et les logos, à déposer dans `resourcepack-prive/selection/art/<personnage>.png`, `selection/voie/<voie>.png` et `selection/logo/<licence>.png` (PNG ou WebP à fond transparent).

Pour une nouvelle licence : déposer ses images, ajouter ses personnages (nom, couleur) à `resourcepack/selection.txt`, lancer `python3 tools/build_selection.py` puis `python3 tools/build_pack.py`, recompiler. `--apercu fichier.png [licence] [perso]` dessine une vue de face de l'écran pour vérifier la composition. Sans illustration dans le pack, le panneau montre le personnage avec son skin (et sans bandeau, son nom en texte). Les dimensions sont les mêmes dans le script et dans `game/SelectionStage.java`.

### Items de capacité
Un item de capacité ne ressemble jamais à une arme ni à un outil, pour que personne ne frappe avec : les icônes en épée deviennent des éclats d'améthyste, les haches du fer brut, les arcs des flèches, les tridents des éclats de prismarine, les pioches, pelles et houes des matériaux bruts. Seuls les vrais arcs à bander (Sova) gardent leur apparence. L'arme du personnage reste en premier slot.

### Mode test

**Zone duo** (`/cf test duo`) : une plateforme à part pour essayer seul les ultimes en duo (l'Ultime en Duo de RWBY et les Synergies de Bloody Snow, Shoja's Memories et Armed Blue Gunvolt). Le testeur y a un **mannequin duo** : un allié à l'apparence de joueur, avec le skin du personnage qu'il incarne quand il en a un, qui tient le rôle de la partenaire. Un clic droit dessus ouvre le choix de son personnage (les autres personnes de la licence du testeur) et joue l'échange des deux personnages s'il existe. Trois cibles attendent devant le testeur, et ses ultimes en duo sont toujours prêts (les jauges avec un second testeur restent aussi à 100 %). `/cf test duo <perso>` choisit directement le personnage du mannequin. En arrivant sans personnage à ultime en duo, un menu ne propose que ceux-là. La mort fait réapparaître dans la zone ; `/cf test duo` ramène au terrain de test, `/cf test` quitte le mode test, `/cf clear` remet aussi la zone duo à neuf.

**Troisième personne** (`/cf test third-person [perso]`) : une plateforme à part où un **mannequin pilote**, au centre, incarne le personnage (son skin, son arme) pendant que le testeur sert de cible, sept blocs devant lui. `/cf change` (et le menu) changent le personnage du mannequin, pas celui du testeur. Le testeur reçoit les objets de ce personnage, mais c'est le mannequin qui s'en sert, tourné vers lui en permanence :
- clic droit sur un objet de capacité : le mannequin la lance (accroupi : la variante accroupie, et le mannequin s'accroupit aussi) ; les menus de choix s'ouvrent chez le testeur ;
- accroupi + clic gauche dans le vide avec l'arme : le mannequin te frappe (à 4 blocs au plus) ou te tire dessus à l'arc, bandé à fond ;
- frapper le mannequin (avec n'importe quoi, arme comprise) déclenche ses capacités qui réagissent aux coups (Contre de Marth...) ; il ne recule pas sous tes coups, mais ses propres capacités le déplacent toujours.

Le mannequin a la vie d'un joueur mais ne meurt jamais, garde les effets de ses capacités, et revient à sa place s'il sort de la plateforme ou reste dix secondes immobile ailleurs. Ce que ses capacités rangent, retirent ou modifient dans son inventaire (flèches, arc lancé au sol...) se voit aussi dans celui du testeur ; sa barre de capacités, ses jauges, ses messages (préfixés `[Mannequin]`) et ses sons « privés » arrivent chez le testeur. Il ne vise que son testeur ; ses capacités qui visent un allié trouvent les mannequins alliés (`/cf mannequin allie`). Les commandes de test qui règlent un personnage (`/cf tome`, `/cf kills`, `/cf titan`, `/cf delta`, `/cf aura`, `/cf burst`, `/cf sante`, `/cf shoja`, `/cf credits`, `/cf pose`) s'appliquent au mannequin. La frappe du mannequin ignore la difficulté du serveur (elle compte comme celle d'un joueur). `/cf test third-person` (sans personnage) ramène au terrain de test en rendant le personnage au testeur, `/cf test` quitte le mode test ; la mort fait réapparaître face au mannequin, et un redémarrage du serveur remet le testeur en troisième personne.

Techniquement, le mannequin est piloté par un objet qui se fait passer pour un joueur (`util/MannequinPlayer`) : tout ce qui concerne le corps (position, regard, vie, effets, vitesse, attributs, projectiles, téléportations) va au mannequin, tout ce qu'un joueur voit et entend seul va au testeur, et son inventaire est un inventaire à part dont la main et l'armure s'affichent sur le mannequin. Les capacités, écrites pour un joueur, tournent donc telles quelles. Les seuls endroits qui passent par le vrai mannequin (`MannequinPlayer.real`) sont ceux où le serveur exige une vraie entité : dégâts avec une source (`Effects.damage`), tireur d'un projectile (`Effects.setShooter`), sons attachés, joueur caché (`Stealth`), équipement masqué (`Puppet`). Ce qui dépend des événements propres aux vrais joueurs (bander un arc à la main, se déplacer, manger) ne peut pas être piloté : le mannequin tire à pleine puissance et ne se déplace que par ses capacités.

Pour que la partenaire puisse être un mannequin, les 24 ultimes en duo prennent une entité vivante pour partenaire : ce qui n'a de sens que pour un joueur (vol, furtivité, dashs donnés par l'item de Synergie, portée, chute amortie) ne s'applique qu'aux joueurs, et un mannequin duo compte pour 0 là où l'effet dépend de son historique (barre de résistance d'Hannah, dégâts récents de Kaze). Un mannequin duo est dans le camp de son testeur (`Effects.sideOf`).


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
- Shoja : son épée est une épée de fer de 8,5 ; elle seule peut crafter, enchanter et réparer des armes (les autres restrictions restent). L'Épée de l'Ego copie, une seule fois, les enchantements de l'arme tenue par son premier tué. L'arbre contient 59 capacités en six éléments (Feu, Eau, Foudre, Vent, Espace, Temps), chacune débloquée par un objectif suivi en jeu (crafts, cœurs infligés ou perdus, immobilisations, blocs cassés, enchantements posés, portail du Nether…) ; trois au plus équipées, d'éléments différents, via l'item « Arbre des capacités ». En mode test tout est débloqué. Les « gels » temporels immobilisent et empêchent de frapper ; les trous, murs et lacs de lave sont temporaires (20 s) ; les « dimensions » sont une arène d'End Stone (`crossfight_dimension`). L'ultime et le Burst dépendent de sa vie (plus ou moins de 5 cœurs) : Couronne Angélique (vol + Régénération II 20 s) / Araignée Maléfique (Force IV, Vitesse II, −1 cœur par 2 s) ; Dieu du monde (immobile 10 s puis +1 vie) / Dieu des enfers (−4 cœurs définitifs, Force IV jusqu'à sa mort).

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
- Les dégâts d'une capacité (« inflige 5 cœurs », « perdent 5 cœurs ») ignorent l'armure et ses enchantements de protection : la cible perd le nombre de cœurs annoncé (moins Résistance et cœurs d'absorption). Les coups d'arme, les flèches et les bonus de dégâts sur un coup passent par l'armure comme un coup normal. `abilities.ignore-armor: false` dans `config.yml` fait de nouveau passer les capacités par l'armure.
- Sang Draconique de Corrin lui fait pousser des ailes de dragon pendant le vol : membrane translucide rose pâle à la racine et blanche vers les bouts, arêtes et griffes argentées, battement rapide en vol puis lent en descente, éclaboussures d'eau ; elles disparaissent à l'atterrissage.
- Le Contre de Marth reprend celui de Smash Bros Melee : pendant la seconde de pose il scintille en blanc et ne peut ni marcher ni sauter (il peut tourner la tête ; un contre réussi le libère aussitôt) ; au coup reçu il se tourne vers l'attaquant, un grand croissant d'épée balaye devant lui, les dégâts sont renvoyés et l'adversaire est projeté au loin (environ 10 blocs) avant d'être immobilisé 1 s.
- Le Coup Critique de Marth affiche au-dessus de chaque ennemi touché la barre de vie des combats de Fire Emblem (GBA) : un nombre qui descend de 100 à 0 et 40 traits qui se vident un à un en deux secondes, avec un cliquetis, avant de disparaître. C'est un effet purement visuel, sans rapport avec la vie réelle de la cible (`util/HealthBar.java`, qui sait aussi afficher la vraie perte de vie).

## Feuille de route

- [x] Socle UHC : équipes, licences, vies, PvP, restrictions, scoreboard
- [x] Moteur de capacités et Ultime
- [x] Fire Emblem : Soutien C à S, Marth, Corrin, Takumi, Byleth
- [x] RWBY : Ultimes en Duo, Ruby, Yang, Weiss, Blake
- [x] Valorant : crédits et boutique de 17 armes à feu, paliers d'armes blanches, Neon, Sage, Sova, Jett
- [x] Shingeki no Kyojin : équipement tridimensionnel et gaz, Eren (et ses 7 titans), Mikasa, Livaï, Armin
- [x] unOrdinary : épées d'Aura à crafter, Arlo, John (absorption), Remi, Seraphina
- [x] Earth Delta : mutations à chaque mort, Kishi, Kura, Taka, Hano (28 tomes)
- [x] Synergies des groupes duo ; Armed Blue Gunvolt : Gunvolt, Acura, Kirin, Asimov
- [x] Shoja's Memories : Hiro, Pinkou, Aïda, Crystal
- [x] Bloody Snow : Netsu, Kaze, Hannah, Eirika
- [x] Personnages solitaires et Burst : Yuki, le Drifter, Shoja (arbre de 59 capacités)
- [x] Sons : pack de ressources, bruitages libres par capacité, système de voix et de musiques de combat
- [ ] Voix des personnages et musiques (fichiers à fournir), répliques des passifs à déclencheur
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

Le pack de sons est téléchargé par le jeu lui-même à la connexion ; il n'y a rien à installer à la main.
