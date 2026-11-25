# Combat façon 1.7.10

Règle `combat-1.7` (oui par défaut ; `pvp.legacy-combat` dans `config.yml`), dans `listener/LegacyCombatListener.java` :

- **Pas de délai d'attaque**, pas d'attaque balayante ; maintenir le clic gauche sur un ennemi à portée enchaîne les coups.
- **Recul d'époque** : la vitesse de la victime est divisée par deux, puis 0,4 à l'horizontale (à l'opposé de l'attaquant) et 0,4 à la verticale (0,4 au plus). Un coup porté en courant, ou avec Recul, ajoute 0,5 par niveau dans le regard de l'attaquant et 0,1 vers le haut. C'est le calcul de la 1.7, envoyé à la place du recul du jeu.
- **Coups critiques d'époque** : en tombant, un bonus tiré au hasard (de 0 à la moitié des dégâts plus un) au lieu de +50 %, et permis en courant.
- **Tranchant** : +1,25 par niveau (au lieu de +0,5 par niveau, +0,5).
- **Armure** : 4 % de dégâts en moins par point d'armure (80 % au plus), quelle que soit la force du coup ; Résistance, Protection et absorption suivent. Les dégâts des capacités, qui ignorent l'armure, ne changent pas.
- **Parade à l'épée** (règle `blocage-epee`, oui par défaut ; `pvp.sword-blocking`) : clic droit maintenu, la moitié des dégâts, de tous les côtés, sans délai. Une épée dont le clic droit lance une capacité ne pare pas (ce clic est pris).
- **Canne à pêche, boules de neige, œufs** : ils repoussent les joueurs (un coup sans dégâts).
- **Perles** : pas de délai entre deux lancers. **Pomme dorée enchantée** : Régénération V 30 s, Absorption 2 min, Résistance et Résistance au feu 5 min.
- **Les joueurs ne se poussent pas** (côté serveur).

Ce qui reste d'aujourd'hui, parce que c'est le jeu du joueur qui le décide : la portée et la détection des coups, les animations, les sons d'attaque.
