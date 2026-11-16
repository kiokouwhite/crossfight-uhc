// Le logo du tableau de droite : rend tools/logo/crossfight_uhc.svg en image de police pour le pack.
//
// Usage :  node tools/build_logo.js          (demande Playwright et son Chromium)
//
// Ecrit    resourcepack/assets/crossfight/textures/font/logo.png   le logo, fond transparent
//          resourcepack/assets/crossfight/font/logo.json           la police "crossfight:logo" : le logo s'ecrit U+E000 U+E002 U+E001
// Puis :   python3 tools/build_pack.py   (le plugin apprend que le pack a le logo : "font.logo")
//
// Un caractere d'image ne depasse pas 256 pixels de cote : le logo est coupe en deux moities (U+E000, U+E001), pour
// rester net aux grandes tailles d'interface ; U+E002 recule d'un pixel pour les recoller. HAUTEUR est sa taille a
// l'ecran (en pixels d'interface) et MONTEE ce qui depasse au-dessus de la ligne du titre : presque rien, le logo
// descend dans le gris du tableau, qui lui reserve des lignes vides (ScoreboardManager.LOGO_LINES).
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const LARGEUR = 448, HAUTEUR = 30, MONTEE = 5;
const root = path.join(__dirname, '..');
const svg = fs.readFileSync(path.join(__dirname, 'logo', 'crossfight_uhc.svg'), 'utf8');
const box = /viewBox="0 0 ([\d.]+) ([\d.]+)"/.exec(svg);
const hauteur = Math.round(LARGEUR * box[2] / box[1]);

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: LARGEUR, height: hauteur }, deviceScaleFactor: 1 });
  await page.setContent(`<html><body style="margin:0;background:transparent"><img src="data:image/svg+xml;base64,${Buffer.from(svg).toString('base64')}" style="width:${LARGEUR}px;height:${hauteur}px;display:block"></body></html>`);
  await page.waitForTimeout(300);
  const assets = path.join(root, 'resourcepack', 'assets', 'crossfight');
  fs.mkdirSync(path.join(assets, 'textures', 'font'), { recursive: true });
  fs.mkdirSync(path.join(assets, 'font'), { recursive: true });
  await page.screenshot({ path: path.join(assets, 'textures', 'font', 'logo.png'), omitBackground: true });
  await browser.close();
  const code = n => '\\u' + n.toString(16).toUpperCase();
  const json = `{
  "providers": [
    { "type": "space", "advances": { "${code(0xE002)}": -1 } },
    { "type": "bitmap", "file": "crossfight:font/logo.png", "height": ${HAUTEUR}, "ascent": ${MONTEE}, "chars": ["${code(0xE000)}${code(0xE001)}"] }
  ]
}
`;
  fs.writeFileSync(path.join(assets, 'font', 'logo.json'), json);
  console.log(`logo ${LARGEUR}x${hauteur} en deux moities -> ${HAUTEUR} px de haut a l'ecran`);
})();
