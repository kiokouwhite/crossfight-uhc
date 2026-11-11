const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let chat = []; bot.on('messagestr', m => chat.push(m));
let sounds = [];
bot._client.on('packet', (data, meta) => {
  if (/resource_pack/.test(meta.name)) log('PAQUET', meta.name, JSON.stringify(data).slice(0, 260));
  if (meta.name === 'sound_effect' || meta.name === 'entity_sound_effect') { const s = JSON.stringify(data); const m = s.match(/crossfight:[a-z0-9_.]+/); if (m) sounds.push(m[0] + (data.soundCategory !== undefined ? ' [cat ' + data.soundCategory + ']' : '')); }
});
const take = () => { const s = sounds; sounds = []; return s.length ? s.join(', ') : '(aucun son crossfight)'; };
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const hotbar = () => bot.inventory.slots.slice(36, 45).map((s, i) => i + ':' + (s ? s.name : '-')).join(' ');
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest(); take();
  bot.chat('/cf change marth'); await sleep(1500); log('selection marth ->', take(), '|', hotbar());
  await click(0, 800); log('falchion (clic arme) ->', take());
  await click(1, 800); log('contre ->', take());
  await click(8, 1200); log('ultime ->', take());
  bot.chat('/cf change remi'); await sleep(1500); take();
  await click(1, 900); log('remi slot 1 ->', take());
  await click(2, 900); log('remi slot 2 ->', take());
  bot.chat('/cf change seraphina'); await sleep(1500); take();
  await click(1, 900); log('seraphina slot 1 ->', take()); await click(3, 900); log('seraphina slot 3 ->', take());
  bot.chat('/cf change shoja'); await sleep(1500); take();
  bot.chat('/cf shoja inflammation'); await sleep(500); bot.chat('/cf shoja panne_dhorloge'); await sleep(600);
  const iF = bot.inventory.slots.slice(36, 45).findIndex(s => s && s.name === 'flint_and_steel'); const iT = bot.inventory.slots.slice(36, 45).findIndex(s => s && s.name === 'clock');
  await click(iF, 900); log('shoja inflammation ->', take()); await click(iT, 900); log('shoja panne ->', take());
  await click(2, 1200); log('shoja burst ->', take());
  bot.chat('/cf son sfx.big_explosion'); await sleep(500); log('/cf son ->', take(), '|', chat.filter(x => /Son /.test(x)).pop());
  bot.chat('/cf son'); await sleep(400); log(chat.filter(x => /sons dans le pack/.test(x)).pop());
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
