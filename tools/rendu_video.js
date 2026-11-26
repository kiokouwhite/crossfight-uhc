// Rend une page HTML animee (animations CSS) en video : chaque image est prise dans Chromium, les animations arretees
// au bon instant, puis ffmpeg assemble.
//
// Usage :  node tools/rendu_video.js <page.html> <sortie.mp4> <secondes> [images par seconde]
//
// La page fait 1920x1080. Les animations infinies sont arretees au meme instant que les autres. Si la page dessine une
// simulation (un canevas p5), elle met window.pret a false tant qu'elle se prepare et fournit window.avancer(t) : la
// simulation est avancee jusqu'a chaque instant, dans l'ordre, avant que l'image soit prise.
//
// IMAGES=0.5,1.2,3 : ne prend que ces instants (en secondes), en PNG a cote de la sortie, pour verifier vite.
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const [page, sortie, secondes, ips] = process.argv.slice(2);
const FPS = Number(ips || 60), DUREE = Number(secondes);
(async () => {
  const browser = await chromium.launch({ args: ['--allow-file-access-from-files'] });
  const p = await browser.newPage({ viewport: { width: 1920, height: 1080 }, deviceScaleFactor: 1 });
  p.on('pageerror', e => console.error('page :', e.message));
  await p.goto('file://' + path.resolve(page));
  await p.waitForFunction(() => window.pret !== false, null, { timeout: 60000 });
  await p.waitForTimeout(300);
  const instant = t => p.evaluate(t => {
    if (window.avancer) window.avancer(t / 1000);
    for (const a of document.getAnimations()) { a.pause(); a.currentTime = t; }
  }, t);
  if (process.env.IMAGES) {
    for (const s of process.env.IMAGES.split(',').map(Number).sort((a, b) => a - b)) {
      await instant(s * 1000);
      await p.screenshot({ path: path.resolve(sortie) + '.' + s.toFixed(2) + '.png' });
    }
    await browser.close();
    return;
  }
  const frames = path.resolve(sortie) + '.images';
  fs.rmSync(frames, { recursive: true, force: true });
  fs.mkdirSync(frames, { recursive: true });
  const n = Math.round(DUREE * FPS);
  for (let i = 0; i < n; i++) {
    await instant(i / FPS * 1000);
    await p.screenshot({ path: path.join(frames, String(i).padStart(4, '0') + '.png') });
  }
  await browser.close();
  execFileSync('ffmpeg', ['-hide_banner', '-loglevel', 'error', '-y', '-framerate', String(FPS), '-i', path.join(frames, '%04d.png'),
    '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '17', path.resolve(sortie)]);
  fs.rmSync(frames, { recursive: true, force: true });
})();
