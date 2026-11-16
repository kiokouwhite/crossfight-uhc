# Déroulement d'une partie

Avant le lancement, les joueurs attendent dans un lobby dans les airs : une plateforme de verre de 43 blocs de côté, à 250 blocs de haut, au-dessus du point d'apparition du monde. Pour patienter, chaque bloc de verre change de couleur (avec une petite note) quand un joueur marche dessus. Des murs et un plafond invisibles empêchent d'en tomber. On y arrive en se connectant, en quittant le mode test, après `/cf stop`, et avec `/cf lobby`, qui y envoie tous les joueurs ; elle disparaît du ciel quand la partie démarre et revient au retour au lobby. Réglages dans `config.yml` : `lobby.plateforme` (false : on reste au point d'apparition), `lobby.hauteur`, `lobby.rayon`.

1. **Lobby** : les joueurs choisissent leur équipe (solitaire, duo ou équipe de trois), ou de regarder ; la partie ne se lance que quand tous ont choisi. Avec la règle `equipes-aleatoires`, les équipes sont tirées au sort au lancement.
2. **Sélection** : une licence est tirée au sort par équipe, parmi celles de son format, chacun choisit un personnage (60 s, puis attribution automatique).
3. **Lancement** : téléportation par équipe, armes et items de capacités donnés.
4. **Partie** : PvP à 30 min, événements à 20 / 30 / 40 / 50 / 60 min, 3 vies par joueur.
5. **Fin** : dernière équipe en vie.
