# Le socle des lieux : la référence

`game/Socle.java` (dans le plugin) décide, avant de poser un lieu, à quel niveau le poser, quel vide combler dessous, quelles
colonnes ne pas poser et où creuser. Ce dossier contient la même règle écrite à part, en Python, et de quoi comparer les deux.

- `socle.py` : la règle de référence (`decide(v, inside)`), commentée étape par étape.
- `socle_lib.py`, `materiaux.txt` : lecture d'un volume et propriétés des blocs (relevées sur le serveur Paper 1.21.10).
- `extrait.py` : tire un volume (`.npz`) d'un lieu rangé dans `structures/<nom>` : `python3 -I extrait.py <dossier structures> <lieu> <sortie>`.
- `banc2.py` : simule la pose d'après la décision, liste ce qui reste de suspect, dessine vues de dessus et coupes
  (`SOCLE_DONNEES=<dossier des .npz> python3 -I banc2.py <lieu>`).
- `compare2.py` : compare, case par case, la décision du plugin à celle de la référence. Lancer le serveur avec
  `-Dcrossfight.socle=<dossier>`, poser les lieux, puis `python3 -I compare2.py <dossier>`.

Toute retouche de la règle se fait des deux côtés, et `compare2.py` doit rendre zéro différence sur tous les lieux.
Dépendances : numpy, scipy, Pillow. Les maps elles-mêmes ne sont pas dans le dépôt.
