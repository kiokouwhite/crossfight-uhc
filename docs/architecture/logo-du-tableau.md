# Logo du tableau

Le titre du tableau de droite est le logo Crossfight UHC, dessiné dans le gris du tableau (qui lui réserve quelques lignes vides), pour les joueurs qui ont chargé le pack envoyé par le serveur ; les autres voient le titre en lettres. `scoreboard.logo` dans `config.yml` : `auto` (selon le pack), `toujours` (pour tous, quand le pack est installé à la main), `jamais`.

Le logo est une image de police du pack (`assets/crossfight/font/logo.json`, police `crossfight:logo`). Pour le changer : remplacer `tools/logo/crossfight_uhc.svg`, puis `node tools/build_logo.js` (Playwright et son Chromium), `python3 tools/build_pack.py`, et recompiler. La taille à l'écran est la constante `HAUTEUR` du script ; les lignes réservées dans le tableau sont `LOGO_LINES` dans `ScoreboardManager`.
