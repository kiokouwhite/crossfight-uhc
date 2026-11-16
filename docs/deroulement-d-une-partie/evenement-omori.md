# Événement Omori

À la minute `omori` (60 par défaut), les quatre herses de l'arène se lèvent (on y entre par les galeries qui traversent les gradins) et une porte blanche apparaît à un endroit au hasard de la fosse ; ses coordonnées sont annoncées. C'est une vraie porte, blanche, haute de deux blocs et incassable : un clic droit dessus l'ouvre et emmène dans l'Espace Blanc, un monde au sol blanc où attend le boss Omori. Tout y est blanc, même le ciel : quatre murs et un plafond de lumière blanche, sans ombre, ferment la vue à 30 blocs du centre (de grands panneaux de texte en blocs pleins, quatre couches décalées par face), il y est toujours midi, et l'on ne peut pas s'éloigner à plus de 25 blocs du centre.

Omori ne perd pas de vie : chaque coup qu'un joueur lui porte compte pour un (quatre par seconde au plus par joueur). Il se bat en cinq phases, à 38, 44, 50, 56 puis 63 coups (251 en tout), avec une barre en haut de l'écran. Il enchaîne les attaques de sa phase, une toutes les cinq secondes ; le nom de chacune s'annonce une seconde avant qu'elle parte, dans une seconde barre rouge sous la sienne, qui se vide pendant ce délai :

| Phase | Attaques, dans l'ordre |
|---|---|
| 1 | Attaque basique, Poignarder, Trancher |
| 2 | Effacement, Poignarder, Attaque basique |
| 3 | Annihilation, Attaque basique, Poignarder |
| 4 | Traumatisme, Poignarder, Attaque basique |
| 5 | Vision obstruée en permanence ; Suicide, « Je ne peux pas le faire » |

Chaque attaque est animée :

- **Attaque basique** (2 cœurs) : Omori regarde le joueur le plus proche, lève son couteau, lui fonce dessus et le tranche ; un croissant blanc, fin, s'abat en travers de la cible, puis il retourne à sa place.
- **Poignarder** (3 cœurs, Wither et Lenteur V 5 s) : il fonce sur sa cible, l'attrape (elle ne peut plus bouger), arme son couteau et l'enfonce lentement, pendant une seconde et demie.
- **Trancher** (2 cœurs et Wither 2 s à tous) : il tremble sur place, de plus en plus fort, puis lâche une rafale de coups ; les croissants tombent sur chaque joueur, en tous sens.
- **Effacement** (3 cœurs) : deux mains rouges sortent du sol de part et d'autre de chaque joueur, se referment sur lui, puis le jettent à 10 blocs dans les airs.
- **Annihilation** (2 cœurs par main) : pendant 10 s, toutes les 2 s, une main sanglante tombe sur chaque joueur. Une cible rouge se dessine au sol là où elle va tomber, avec des bips de plus en plus rapides et un « DANGER » à l'écran : il reste une seconde et demie pour s'écarter.
- **Traumatisme** : il charge le joueur le plus proche et l'éjecte à 15 blocs de haut en le percutant, avec Darkness 10 s.
- **Suicide** (5 cœurs, Darkness) : une surface rouge se referme autour de lui, puis éclate en fils qui partent en tous sens, transpercent chaque joueur et l'éjectent à 10 blocs.
- **« Je ne peux pas le faire »** : il fixe les joueurs. Pendant 10 s ils ne peuvent plus bouger, leur regard est ramené de force sur lui, et des mèches de cheveux noirs leur tombent devant les yeux jusqu'à couvrir l'écran.

Les bruitages sont ceux du jeu OMORI dans le pack complet (`python3 tools/build_omori_sons.py <dossier « Sound Effects » du jeu>` les range dans le pack privé, jamais versionné ; la table du script dit quel fichier sert à quoi) ; avec le pack public, ce sont des bruitages de Minecraft.

Vaincu, Omori laisse à chaque joueur présent un choix : se pardonner (une vie de plus) ou ne pas se pardonner (une vie de moins, mais Force III pour le reste de la partie) ; fermer le menu sans choisir, c'est se pardonner. Tout le monde revient alors à la porte, qui disparaît. Mourir dans l'Espace Blanc est une mort ordinaire. Omori n'existe que tant qu'un joueur est dans l'Espace Blanc : si tout le monde en sort, il reprend plus tard là où il en était. On n'y casse ni n'y pose aucun bloc.

Omori est un personnage articulé, couteau à la main, quand son skin est dans le pack (`python3 tools/add_skin.py <skin.png> omori --prive`, puis `tools/build_pack.py` ; `omori.skin` dans `config.yml` nomme le skin à utiliser) ; sans lui, c'est un zombie en tenue noire et blanche. `/cf test omori` pose la porte devant soi dans la zone de test, pour essayer le combat sans lancer de partie.

Une musique accompagne le combat pour qui est dans l'Espace Blanc, et s'arrête dès qu'on en sort : la piste `omori` du pack complet (jamais versionnée), une boucle de jeu vidéo. Chacun l'entend depuis son début en entrant : tout le morceau une fois (4 min 30), puis sa dernière section de 42 secondes, qui tourne sans fin, sans conclusion ni reprise audible. Elle se fabrique avec `python3 tools/boucle_musique.py <fichier> omori --debut 270.85 --longueur 1846977 --prive` (voir « Sons, voix et musiques »), puis `python3 tools/build_pack.py`. Sans elle, le combat se fait sans musique. `/cf musique omori` l'écoute hors combat, `/cf musique omori_boucle` fait entendre la boucle seule.
