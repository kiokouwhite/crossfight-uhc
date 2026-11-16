# Événement Omori

À la minute `omori` (60 par défaut), les quatre herses de l'arène se lèvent (on y entre par les galeries qui traversent les gradins) et une porte blanche apparaît à un endroit au hasard de la fosse ; ses coordonnées sont annoncées. En s'en approchant, un menu propose « Ouvrir la porte » ou « Ne rien faire » : l'ouvrir emmène dans l'Espace Blanc, un monde au sol blanc sans fin où attend le boss Omori.

Omori ne perd pas de vie : chaque coup qu'un joueur lui porte compte pour un (quatre par seconde au plus par joueur). Il se bat en cinq phases, à 30, 35, 40, 45 puis 50 coups, avec une barre en haut de l'écran. Il enchaîne les attaques de sa phase, une toutes les cinq secondes :

| Phase | Attaques, dans l'ordre |
|---|---|
| 1 | Attaque basique (2 cœurs au plus proche), Poignarder (3 cœurs, Wither et Lenteur V 5 s), Trancher (2 cœurs et Wither 2 s à tous) |
| 2 | Effacement (tous éjectés à 10 blocs, 3 cœurs), Poignarder, Attaque basique |
| 3 | Annihilation (pendant 10 s, une main noire tombe sur chaque joueur toutes les 2 s : 2 cœurs), Attaque basique, Poignarder |
| 4 | Traumatisme (le plus proche est projeté à 15 blocs de haut, Darkness 10 s), Poignarder, Attaque basique |
| 5 | Vision obstruée en permanence ; Suicide (tous éjectés à 10 blocs, Darkness, 5 cœurs), « Je ne peux pas le faire » (tous immobilisés 10 s) |

Vaincu, Omori laisse à chaque joueur présent un choix : se pardonner (une vie de plus) ou ne pas se pardonner (une vie de moins, mais Force III pour le reste de la partie) ; fermer le menu sans choisir, c'est se pardonner. Tout le monde revient alors à la porte, qui disparaît. Mourir dans l'Espace Blanc est une mort ordinaire. Omori n'existe que tant qu'un joueur est dans l'Espace Blanc : si tout le monde en sort, il reprend plus tard là où il en était. On n'y casse ni n'y pose aucun bloc.

Omori est un personnage articulé, couteau à la main, quand son skin est dans le pack (`python3 tools/add_skin.py <skin.png> omori --prive`, puis `tools/build_pack.py` ; `omori.skin` dans `config.yml` nomme le skin à utiliser) ; sans lui, c'est un zombie en tenue noire et blanche. `/cf test omori` pose la porte devant soi dans la zone de test, pour essayer le combat sans lancer de partie.

La porte est une image du pack : un dessin de porte blanche usée dans le pack public, l'image officielle dans le pack complet (jamais versionnée, à déposer dans `resourcepack-prive/glyphes-couleur/porte_blanche.png`).
