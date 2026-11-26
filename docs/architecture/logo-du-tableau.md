# Logo du tableau

Le titre du tableau de droite est le logo Crossfight UHC, dessiné dans le gris du tableau (qui lui réserve quelques lignes vides), pour les joueurs qui ont chargé le pack envoyé par le serveur ; les autres voient le titre en lettres. `scoreboard.logo` dans `config.yml` : `auto` (selon le pack), `toujours` (pour tous, quand le pack est installé à la main), `jamais`.

Le logo est une image de police du pack (`assets/crossfight/font/logo.json`, police `crossfight:logo`). Pour le changer : remplacer `tools/logo/crossfight_uhc.svg`, puis `node tools/build_logo.js` (Playwright et son Chromium), `python3 tools/build_pack.py`, et recompiler. La taille à l'écran est la constante `HAUTEUR` du script ; les lignes réservées dans le tableau sont `LOGO_LINES` dans `ScoreboardManager`.

## Le logo animé

`python3 tools/logo_anime.py <dossier>` écrit un SVG animé du logo (fond transparent) : CROSSFIGHT s'écrit seul, lettre par lettre, puis la barre verticale le transperce, puis la barre horizontale (un éclair et une secousse à chaque impact), et UHC sort de dessous la barre horizontale ; un reflet passe enfin sur les lettres. Le dessin est celui de `tools/logo/crossfight_uhc.svg`, dont les pièces sont seulement repérées et animées. `node tools/logo_video.js <dossier>` en tire la vidéo : MP4 1920×1080 sur fond sombre, GIF en boucle et WebM à fond transparent (Playwright, Chromium et ffmpeg).
