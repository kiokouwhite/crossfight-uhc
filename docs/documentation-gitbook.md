# Documentation GitBook

`docs/` est la même documentation découpée en pages pour GitBook (une page par partie de ce README), avec son sommaire (`docs/SUMMARY.md`) ; `.gitbook.yaml` dit à GitBook de lire ce dossier. `python3 tools/build_docs.py` la refabrique après chaque changement du README. Pour brancher un espace GitBook dessus : dans l'espace, *Configure* → *Git Sync* → GitHub, dépôt `kiokouwhite/crossfight-uhc`, branche `main`, sens « GitHub → GitBook ». Chaque envoi sur GitHub met alors le GitBook à jour.
