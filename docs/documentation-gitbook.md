# Documentation GitBook

Le site GitBook a deux espaces, déclarés par `gitbook-docs.yaml` à la racine :

- **Guide du joueur** (`guide/`, l'espace par défaut) : les règles, le déroulement, les lieux et les quêtes, les événements, les personnages et leurs capacités, sans rien de technique. `python3 tools/build_guide.py` le refabrique : les pages générales sont écrites dans le script, les pages des personnages sont tirées du code (noms, capacités, descriptions, temps de recharge) et la table des objets de Lootbox de ce README. À relancer quand une règle, une quête ou un personnage change.
- **Documentation technique** (`docs/`) : ce README découpé en pages, avec son sommaire. `python3 tools/build_docs.py` la refabrique après chaque changement du README.

Le site est branché sur ce dépôt par Git Sync (branche `main`, sens GitHub → GitBook) : chaque envoi sur GitHub le met à jour. Ne rien modifier dans GitBook, ce serait écrasé.
