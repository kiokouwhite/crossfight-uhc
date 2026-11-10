const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
function make(name) {
  const b = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.10' });
  b.on('windowOpen', w => b.closeWindow(w));
  b.on('message', m => { const s = m.toString(); if (/lancent|Duo|Erreur|rejoint ton|Jauges/.test(s)) log(name, 'CHAT', s); });
  b._client.on('action_bar', p => { const t = JSON.stringify(p).replace(/[^A-Za-z0-9%:.,()/ !]/g, ''); if (/Duo|Aucun Ultime/.test(t)) b.lastBar = t.replace(/.*value:/, '').slice(0, 100); });
  return b;
}
const A = make('Tester'), B = make('Tester2');
const ready = b => new Promise(r => b.once('spawn', r));
(async () => {
  await Promise.all([ready(A), ready(B)]); await sleep(1500);
  A.chat('/cf test'); B.chat('/cf test'); await sleep(2500);
  A.chat('/cf change ruby'); B.chat('/cf change yang'); await sleep(2000);
  B.chat('/cf ally Tester'); await sleep(800);
  B.chat('/tp @s 2 65 0'); await sleep(500);
  A.chat('/cf mannequin'); await sleep(800);
  A.setQuickBarSlot(4); await sleep(200); A.activateItem(); await sleep(600); log('A sans jauge :', A.lastBar);
  A.chat('/cf duo 100'); await sleep(800);
  A.activateItem(); await sleep(2000); log('A Lancer de Rose : pos', A.entity.position.toString());
  // Weiss + Yang : piques de glace
  A.chat('/cf change weiss'); await sleep(1500); A.chat('/cf duo 100'); await sleep(500);
  A.setQuickBarSlot(4); await sleep(200); A.activateItem(); await sleep(1500);
  const ice = ['packed_ice', 'blue_ice', 'ice'];
  let count = 0; for (let x = -12; x <= 12; x++) for (let z = -12; z <= 12; z++) for (let y = 65; y <= 70; y++) { const bl = A.blockAt(new (require('vec3').Vec3)(x, y, z)); if (bl && ice.includes(bl.name)) count++; }
  log('Piques de glace : blocs de glace visibles', count);
  // Blake + Yang : leurres
  A.chat('/cf change blake'); await sleep(1500); A.chat('/cf duo 100'); await sleep(500);
  A.setQuickBarSlot(4); await sleep(200); A.activateItem(); await sleep(1500);
  log('Leurres : zombies', Object.values(A.entities).filter(e => e.name === 'zombie').length, '(1 mannequin + clones)');
  // Ruby + Weiss : patinoire
  A.chat('/cf change ruby'); B.chat('/cf change weiss'); await sleep(1500); A.chat('/cf duo 100'); await sleep(500);
  A.setQuickBarSlot(4); await sleep(200); A.activateItem(); await sleep(2000);
  let rink = 0; for (let x = -10; x <= 10; x++) for (let z = -10; z <= 10; z++) { const bl = A.blockAt(new (require('vec3').Vec3)(x, 64, z)); if (bl && bl.name === 'packed_ice') rink++; }
  log('Patinoire : blocs de glace au sol (sur 441 sondes)', rink);
  A.chat('/cf mannequin clear'); A.chat('/cf clear'); await sleep(1500);
  A.chat('/cf test'); B.chat('/cf test'); await sleep(1000); A.quit(); B.quit(); process.exit(0);
})();
