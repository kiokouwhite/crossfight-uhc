const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(1) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function ensureTest(b) { b.chat('/cf test'); await sleep(2000); if (!b.inventory.slots[43] || b.inventory.slots[43].name !== 'golden_apple') { b.chat('/cf test'); await sleep(2000); } }
let sounds = []; bot._client.on('packet', (d, m) => { if (/sound_effect/.test(m.name)) { const x = JSON.stringify(d).match(/crossfight:[a-z0-9_.]+/); if (x) sounds.push(((Date.now() - t0) / 1000).toFixed(1) + ' ' + x[0].slice(11)); } });
const take = () => { const s = sounds.filter(x => /voice|ability\.ruby/.test(x)).join(', ') || '(rien)'; sounds = []; return s; };
const use = async (slot, b = bot) => { b.setQuickBarSlot(slot); await sleep(150); b.activateItem(); await sleep(60); b.deactivateItem(); };
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest(bot); take();
  t0 = Date.now(); bot.chat('/cf change ruby'); await sleep(2500); log('selection ->', take());
  const names = [...Array(9).keys()].map(i => bot.inventory.slots[36 + i] ? bot.inventory.slots[36 + i].name : '-'); log('barre :', names.join(', '));
  for (let i = 0; i <= 3; i++) { if (names[i] === '-') continue; t0 = Date.now(); await use(i); await sleep(2200); log('case ' + (i + 1) + ' (' + names[i] + ') ->', take()); }
  t0 = Date.now(); await use(8); await sleep(1500); log('Ultime ->', take());
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(2500); bot.chat('/cf change ruby'); await sleep(2000); take();
  // Yeux d'Argent : passer sous 5 coeurs.
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf mannequin'); await sleep(800);
  t0 = Date.now(); bot.chat('/damage @s 12 minecraft:out_of_world'); await sleep(1200); log('Yeux d Argent (sous 5 coeurs) ->', take());
  bot.chat('/cf heal'); bot.chat('/cf son voice.ruby_kill'); await sleep(600); log('kill ->', take());
  // Ultime en duo avec une coequipiere : Weiss.
  const mate = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester2', version: '1.21.10' });
  await new Promise(r => mate.once('spawn', r)); await sleep(2500); if (mate.health <= 0) { mate._client.write('client_command', { actionId: 0 }); await sleep(800); }
  await ensureTest(mate); mate.chat('/cf change weiss'); await sleep(2000);
  bot.chat('/cf ally Tester2'); await sleep(500); bot.chat('/cf duo'); await sleep(800); bot.chat('/tp Tester2 Tester'); await sleep(800); take();
  const duo = [...Array(9).keys()].find(i => bot.inventory.slots[36 + i] && bot.inventory.slots[36 + i].name === 'amethyst_cluster');
  t0 = Date.now(); if (duo !== undefined) { await use(duo); await sleep(2500); } log('Ultime en duo avec Weiss (case ' + (duo + 1) + ') ->', take());
  bot.chat('/kill @s'); await sleep(1500); log('mort ->', take());
  bot._client.write('client_command', { actionId: 0 }); await sleep(1500);
  bot.chat('/cf mannequin clear'); await sleep(200); bot.chat('/cf clear'); mate.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); mate.chat('/cf test'); await sleep(800); mate.quit(); bot.quit(); process.exit(0);
});
