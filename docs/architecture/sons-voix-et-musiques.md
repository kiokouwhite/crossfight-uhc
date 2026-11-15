# Sons, voix et musiques

Le plugin envoie à chaque joueur un pack de ressources (`dist/CrossfightPack.zip`, adresse dans `config.yml`, section `resource-pack`). Tout y est rangé sous l'espace de noms `crossfight` :

- **Bruitages** : chaque capacité réussie joue `ability.<id>`. `resourcepack/abilities.txt` associe chaque capacité à une catégorie de la bibliothèque (`sounds/sfx/<catégorie>/`, 28 catégories de sons libres, crédits dans `resourcepack/CREDITS.md`). Un fichier `sounds/ability/<id>.ogg` remplace la catégorie pour cette capacité.
- **Voix** : `sounds/voice/<clé>.ogg`. Pour l'Ultime en Duo de RWBY, le lanceur dit `<perso>_duo_<partenaire>` (appel de partenaire de BlazBlue) et la partenaire répond `<partenaire>_nom_<perso>`. Les quatre membres de RWBY ont aussi leurs échanges de sélection (les entrées en duo de BlazBlue Cross Tag Battle), pour les six paires. Une réplique qui répond à une autre attend la fin de la première : `build_pack.py` écrit la durée de chaque réplique dans `durations.txt`, embarqué dans le plugin (`/cf dialogue <perso> <perso>` pour écouter un échange). La clé est l'id de la capacité, ou `<perso>_selection`, `<perso>_mort`, `<perso>_kill`, `<perso>_burst`, `syn_<a>_<b>_<perso>` (Synergie), `duo_<a>_<b>_<perso>` (échange des deux membres d'un duo après le choix des personnages). `<clé>_1.ogg`, `<clé>_2.ogg` sont des variantes tirées au hasard. Les voix sont entendues autour du personnage ; la réplique de sélection, par le joueur seul.
- **Musiques de combat** : `sounds/music/<piste>.ogg`, et `resourcepack/music.txt` dit quelles licences la déclenchent (`licence_a + licence_b = piste`). La musique démarre au premier coup entre deux équipes, repart sur elle-même tant qu'elles se battent (la reprise est lancée `music.loop-overlap-ms` avant la fin de la piste, et `add_audio.py` retire les silences de début et de fin), et s'arrête après `music.silence-seconds` (20 s) sans coup ou quand une équipe n'a plus de joueur en vie. Elle passe par le réglage « Juke-box / Blocs musicaux » du joueur.

Un son absent du pack n'est simplement pas joué : ajouter une voix ou une musique ne demande aucun changement de code.

```
python3 tools/add_audio.py music "ma piste.mp3"        # convertit et range une musique
python3 tools/add_audio.py voice netsu.wav netsu_ult   # une réplique
python3 tools/build_pack.py                            # reconstruit le pack, puis recompiler le plugin
```

`build_pack.py` écrit aussi dans le jar la liste des sons, l'empreinte du pack et la table des musiques : le jar et le pack publiés doivent donc venir de la même construction.

**Sons sous droits (voix officielles, musiques non libres)** : ils ne vont jamais sur ce dépôt. Ils se rangent dans `resourcepack-prive/` (même structure, ignoré par git) avec `add_audio.py --prive`. `build_pack.py` construit alors deux packs : `dist/CrossfightPack.zip` (public, sans ces sons) et `prive/CrossfightPackComplet.zip` (tout, ignoré par git). Seuls les noms des sons privés sont versionnés (`resourcepack/prive.txt`), pour que le plugin sache les jouer. Pour que les joueurs les entendent, deux possibilités :

- héberger `CrossfightPackComplet.zip` à une adresse de téléchargement direct non publique, puis mettre cette adresse dans `resource-pack.url` et l'empreinte affichée par `build_pack.py` dans `resource-pack.sha1` ;
- ou que chaque joueur dépose `CrossfightPackComplet.zip` dans son dossier `resourcepacks` et l'active (le pack public envoyé par le serveur peut rester en place).

Un son qui a une version privée n'est pas déclaré dans le pack public : Minecraft additionne les déclarations d'un même son venant de deux packs, et tirerait sinon au hasard entre le bruitage libre et le vrai. Avec le seul pack public, ces quelques capacités sont donc muettes.

Sur une machine qui n'a plus `resourcepack-prive/`, `python3 tools/build_pack.py --depuis CrossfightPackComplet.zip` le recrée avant d'ajouter de nouveaux sons.

```
python3 tools/add_audio.py --prive voice vc_marth_appeal01.wav marth_ult
```
