# Outils de test automatisés

Ces scripts permettent de vérifier les capacités sans joueur humain, avec un serveur Paper
local en mode hors ligne et un bot `mineflayer` qui se connecte, entre en mode test, prend un
personnage, invoque un mannequin et déclenche les capacités en traçant positions et messages.

## Serveur de test

1. Télécharger le jar Paper 1.21.10 (`paper.jar`) dans `tools/testserver/`.
2. Créer `eula.txt` avec `eula=true`, copier le plugin dans `plugins/`.
3. Lancer `./start.sh` : la console est accessible par le tube `console.in`
   (`echo "op Tester" > console.in`).

## Bots

```bash
cd tools/testbot
npm install
node epine.js            # Déchaînement d'Épine de Byleth sur un mannequin
node ultime-byleth.js    # Ultime de Byleth : dimension, mort, libération
node combat.js           # Combat 1.8 : clic maintenu et spam clic
node sol-tenebres.js     # Vérifie le sol noir de la dimension
```

Le bot s'appelle `Tester` et doit être opérateur (`op Tester` dans la console).

## Vérifier les particules

Dans cette version de Paper, certaines particules exigent un paramètre (`FLASH` une couleur,
`DRAGON_BREATH` un nombre). En oublier un lève une exception à chaque tick et peut figer une
animation. Avant d'utiliser une nouvelle particule, vérifier `Particle.X.getDataType()`.
