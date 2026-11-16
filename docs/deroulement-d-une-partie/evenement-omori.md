# Événement Omori

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
