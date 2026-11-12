const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(1) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function ensureTest(b) { b.chat('/cf test'); await sleep(2000); if (!b.inventory.slots[43] || b.inventory.slots[43].name !== 'golden_apple') { b.chat('/cf test'); await sleep(2000); } }
let sounds = []; bot._client.on('packet', (d, m) => { if (/sound_effect/.test(m.name)) { const x = JSON.stringify(d).match(/crossfight:[a-z0-9_.]+/); if (x) sounds.push(((Date.now() - t0) / 1000).toFixed(1) + ' ' + x[0].slice(11)); } });
const take = () => { const s = sounds.join(', ') || '(rien)'; sounds = []; return s; };
let msgs = []; bot.on('message', m => msgs.push(m.toString()));
const bar = b => [...Array(9).keys()].map(i => b.inventory.slots[36 + i] ? b.inventory.slots[36 + i].name : '-').join(',');
const use = async (slot, b = bot) => { b.setQuickBarSlot(slot); await sleep(150); b.activateItem(); await sleep(60); b.deactivateItem(); };
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest(bot);
  bot.chat('/cf change ruby'); await sleep(3000); take(); msgs = [];
  // 1. Yeux d'Argent : un vrai coup, d'un mannequin qui attaque.
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf mannequin attaque'); await sleep(800);
  const z = Object.values(bot.entities).find(e => e.name === 'zombie'); bot.chat('/tp @s ' + (z.position.x + 1.2) + ' ' + z.position.y + ' ' + z.position.z); await sleep(300);
  bot.chat('/damage @s 9'); await sleep(300);
  t0 = Date.now(); log('vie', bot.health.toFixed(1), '- on attend un coup du mannequin');
  for (let i = 0; i < 10 && !/ruby_yeux/.test(sounds.join()); i++) await sleep(500);
  log('Yeux d Argent -> vie', bot.health.toFixed(1), '|', take(), '|', msgs.filter(x => /Yeux/.test(x)).join(' ')); msgs = [];
  bot.chat('/cf mannequin clear'); bot.chat('/cf heal'); await sleep(500);
  // 2. Ultime en duo.
  const mate = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester2', version: '1.21.10' });
  await new Promise(r => mate.once('spawn', r)); await sleep(2500); if (mate.health <= 0) { mate._client.write('client_command', { actionId: 0 }); await sleep(800); }
  await ensureTest(mate); mate.chat('/cf change weiss'); await sleep(2000);
  bot.chat('/tp Tester2 Tester'); await sleep(500); msgs = [];
  bot.chat('/cf ally Tester2'); await sleep(800); bot.chat('/cf duo'); await sleep(800);
  log('messages ally/duo :', msgs.join(' | ').slice(0, 300)); log('barre de Ruby :', bar(bot)); take();
  const duo = [...Array(9).keys()].find(i => bot.inventory.slots[36 + i] && bot.inventory.slots[36 + i].name === 'amethyst_cluster');
  t0 = Date.now(); msgs = []; if (duo !== undefined) { await use(duo); await sleep(2500); }
  log('Ultime en duo ->', take(), '|', msgs.join(' | ').slice(0, 200));
  // 3. Mort.
  t0 = Date.now(); bot.chat('/kill @s'); await sleep(1800); log('mort ->', take());
  bot._client.write('client_command', { actionId: 0 }); await sleep(1500);
  bot.chat('/cf clear'); mate.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); mate.chat('/cf test'); await sleep(800); mate.quit(); bot.quit(); process.exit(0);
});
