// La video de l'animation du logo (tools/logo_anime.py) : chaque image est prise dans Chromium, l'animation arretee
// au bon instant, puis ffmpeg assemble.
//
// Usage :  node tools/logo_video.js <dossier>     (le dossier ou logo_anime.py a ecrit crossfight_uhc_anime.svg)
//
// Ecrit    crossfight_uhc_anime.mp4    1920x1080, fond sombre, 60 images par seconde
//          crossfight_uhc_anime.gif    960 de large, fond sombre, en boucle (le logo disparait avant de recommencer)
//          crossfight_uhc_anime.webm   1920x1080, fond transparent (VP9 avec transparence), a poser sur une video
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const dir = path.resolve(process.argv[2]);
const svg = fs.readFileSync(path.join(dir, 'crossfight_uhc_anime.svg'), 'utf8');
// La duree de l'animation est ecrite dans le SVG (data-duree) ; TENUE : quelques images vides a la fin.
const DUREE = Number((svg.match(/data-duree="([0-9.]+)"/) || [0, 4.2])[1]);
const W = 1920, H = 1080, LOGO = 1500, FPS = 60, TENUE = 0.25, FOND = '#14161f';

(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: W, height: H }, deviceScaleFactor: 1 });
  await page.setContent(`<html><body style="margin:0;width:${W}px;height:${H}px;display:flex;align-items:center;justify-content:center;background:transparent">
    <div style="width:${LOGO}px">${svg.replace('<svg ', '<svg style="width:100%;height:auto;display:block" ')}</div></body></html>`);
  await page.waitForTimeout(300);
  for (const [fond, sous] of [[FOND, 'opaque'], [null, 'transparent']]) {
    const frames = path.join(dir, 'images_' + sous);
    fs.rmSync(frames, { recursive: true, force: true });
    fs.mkdirSync(frames, { recursive: true });
    await page.evaluate(f => { document.body.style.background = f || 'transparent'; }, fond);
    const n = Math.round((DUREE + TENUE) * FPS);
    for (let i = 0; i < n; i++) {
      const ms = Math.min(i / FPS, DUREE) * 1000;
      await page.evaluate(t => { for (const a of document.getAnimations()) { a.pause(); a.currentTime = t; } }, ms);
      await page.screenshot({ path: path.join(frames, String(i).padStart(4, '0') + '.png'), omitBackground: !fond });
    }
  }
  await browser.close();
  const ff = (...a) => execFileSync('ffmpeg', ['-hide_banner', '-loglevel', 'error', '-y', ...a]);
  ff('-framerate', String(FPS), '-i', path.join(dir, 'images_opaque', '%04d.png'), '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '16', path.join(dir, 'crossfight_uhc_anime.mp4'));
  ff('-framerate', String(FPS), '-i', path.join(dir, 'images_transparent', '%04d.png'), '-c:v', 'libvpx-vp9', '-pix_fmt', 'yuva420p', '-b:v', '0', '-crf', '24', '-auto-alt-ref', '0', path.join(dir, 'crossfight_uhc_anime.webm'));
  // Le GIF : 25 images par seconde ; le logo disparait de lui-meme a la fin, la boucle repart sans saut.
  ff('-framerate', String(FPS), '-i', path.join(dir, 'images_opaque', '%04d.png'), '-vf',
    `fps=25,scale=960:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=128:stats_mode=full[p];[b][p]paletteuse=dither=sierra2_4a`,
    path.join(dir, 'crossfight_uhc_anime.gif'));
  for (const s of ['opaque', 'transparent']) fs.rmSync(path.join(dir, 'images_' + s), { recursive: true, force: true });
  console.log('ok');
})();
