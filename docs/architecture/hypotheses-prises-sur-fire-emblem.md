# Hypothèses prises sur Fire Emblem

- Le Soutien est commun à la paire : les dégâts des deux alliés font progresser le même niveau, et chaque niveau tire au sort Force, Vitesse ou Résistance 0.5 pour les deux.
- Takumi reçoit un arc Infinité et une flèche, sinon il ne peut pas tirer avant d'avoir des plumes.
- Yato Alpha : « 15 ❤ » est lu comme 15 cœurs de vie maximum.
- L'Épée du Créateur de Byleth fait 8 dégâts (non précisé dans le doc).
- Les dégâts d'une capacité (« inflige 5 cœurs », « perdent 5 cœurs ») ignorent l'armure et ses enchantements de protection : la cible perd le nombre de cœurs annoncé (moins Résistance et cœurs d'absorption). Les coups d'arme, les flèches et les bonus de dégâts sur un coup passent par l'armure comme un coup normal. `abilities.ignore-armor: false` dans `config.yml` fait de nouveau passer les capacités par l'armure.
- Sang Draconique de Corrin lui fait pousser des ailes de dragon pendant le vol : membrane translucide rose pâle à la racine et blanche vers les bouts, arêtes et griffes argentées, battement rapide en vol puis lent en descente, éclaboussures d'eau ; elles disparaissent à l'atterrissage.
- Le Contre de Marth reprend celui de Smash Bros Melee : pendant la seconde de pose il scintille en blanc et ne peut ni marcher ni sauter (il peut tourner la tête ; un contre réussi le libère aussitôt) ; au coup reçu il se tourne vers l'attaquant, un grand croissant d'épée balaye devant lui, les dégâts sont renvoyés et l'adversaire est projeté au loin (environ 10 blocs) avant d'être immobilisé 1 s.
- Le Coup Critique de Marth affiche au-dessus de chaque ennemi touché la barre de vie des combats de Fire Emblem (GBA) : un nombre qui descend de 100 à 0 et 40 traits qui se vident un à un en deux secondes, avec un cliquetis, avant de disparaître. C'est un effet purement visuel, sans rapport avec la vie réelle de la cible (`util/HealthBar.java`, qui sait aussi afficher la vraie perte de vie).
