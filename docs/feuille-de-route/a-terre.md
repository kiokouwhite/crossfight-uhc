# À terre

Règle `a-terre` (oui par défaut ; `game.downs`), dans `game/Downs.java`, comme dans les battle royale :

- **Tomber** : un joueur en équipe qui prend un coup mortel ne meurt pas, il tombe au sol, la barre de vie de nouveau pleine (« À TERRE » à l'écran, son équipe est prévenue avec la distance). Seulement si un allié est encore debout dans le même monde : le dernier debout d'une équipe meurt directement. La chute dans le vide et `/kill` tuent toujours.
- **À terre** : il rampe (les autres le voient allongé, il avance lentement, ne saute pas, ne court pas). Il ne peut ni frapper, ni lancer de capacité, ni manger, ni poser ou casser de bloc, et rien ne le soigne.
- **Se vider** : il perd un cœur toutes les deux secondes (`downs.bleed-seconds`) ; la barre d'action lui dit le temps qu'il lui reste. Arrivé au bout, il meurt.
- **Être relevé** : un allié debout reste accroupi à moins de 2,5 blocs pendant cinq secondes (`downs.revive-seconds`), avec une barre de progression pour les deux. S'accroupir ailleurs ou s'éloigner remet le compte à zéro. Relevé, il a trois cœurs (`downs.revive-health`).
- **Être achevé** : un ennemi qui le frappe à terre lui fait perdre de la vie normalement et peut l'achever. S'il meurt à terre, de quelque façon que ce soit, le kill va à celui qui l'a mis à terre.
- **Plus personne debout** : si tous ses alliés tombent, meurent ou quittent, ceux qui sont à terre meurent.
- Le tableau des alliés montre un allié à terre en rouge, « À TERRE », avec sa vie restante.
