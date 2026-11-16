# Personnages, licences, formats, doublons

L'objet « Personnages et licences » de l'administrateur (ou les commandes) règle ce qui est jouable :

- **Formats** : désactiver les solitaires, les duos ou les équipes de trois (`/cf desactiver duo`). Le bouton correspondant se ferme dans le menu des équipes, et les équipes de ce format déjà formées sont dissoutes.
- **Licences** : clic gauche sur une licence pour la désactiver (`/cf desactiver rwby`) ; elle n'est plus tirée.
- **Personnages** : clic droit sur une licence pour ouvrir ses personnages, clic pour en désactiver un (`/cf desactiver weiss`) ; il n'apparaît plus à la sélection. Une licence à qui il ne reste pas assez de personnages pour une équipe de son format n'est plus tirée.
- **Doublons** (règle `doublons`, autorisés par défaut) : autorisés, deux équipes peuvent recevoir la même licence quand il n'y en a plus de libre, et deux solitaires le même personnage. Interdits, il y a une équipe par licence et un solitaire par personnage : le menu des équipes ferme un format quand il est plein, et la partie refuse de se lancer s'il y a trop d'équipes.
- **Équipes aléatoires** (règle `equipes-aleatoires`) : les joueurs ne choisissent plus, tout le monde est mélangé au lancement dans des équipes du format choisi (« Format du tirage », ou `/cf setsize`) ; ceux qui restent forment une équipe plus petite (un duo, un solitaire) si ce format est permis.

À la sortie du menu, une annonce résume les réglages. Le mode test ignore ces réglages : on y essaie tout.
