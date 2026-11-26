# Commandes

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
| `/cf map` | La carte de la map, dans la main : le vrai terrain vu du ciel (forêts, eau, relief, lieux bâtis ; il se dessine en partant du centre la première fois), les lieux cernés à leur couleur avec leur nom (sans le pack, un point blanc au-dessus du nom), l'arène, la bordure, soi (pointeur blanc) et son équipe (marques bleues avec le nom). Avec le pack complet, le tour de la carte est le cadre du pack d'interface (`tools/build_selection.py`) et l'objet a une icône de parchemin dans l'inventaire (`tools/build_icones.py`). Pour un admin : clic droit avec la carte pour viser un endroit, clic pour s'y téléporter |
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
| `/cf scoreboard hide\|show` | Cacher ou remettre le tableau de droite, pour soi (`/cf tableau` marche aussi ; sans argument, bascule). Le choix est gardé d'une partie à l'autre (`HudPrefs`, ligne « Tableau de droite » du menu `/cf hud`) |
| `/vivre` | Réapparaître quand on attend la mort de son équipe |
| `/cf test` | Entrer ou sortir du mode test (monde plat, équipement fourni) |
| `/cf annonce` | Affiche tout de suite le bilan des vies dans le ciel (en mode test, avec des exemples s'il n'y a pas eu de mort) |
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
