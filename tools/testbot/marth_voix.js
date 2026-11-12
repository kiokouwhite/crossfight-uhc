const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(1) + 's', ...a);
let sounds = [];
bot._client.on('packet', (data, meta) => {
  if (meta.name === 'sound_effect' || meta.name === 'entity_sound_effect') { const m = JSON.stringify(data).match(/crossfight:[a-z0-9_.]+/); if (m) sounds.push(m[0].replace('crossfight:', '')); }
  if (meta.name === 'add_resource_pack') log('pack propose :', data.url, data.hash);
});
const take = () => { const s = {}; for (const x of sounds) s[x] = (s[x] || 0) + 1; sounds = []; const k = Object.keys(s); return k.length ? k.map(x => x + (s[x] > 1 ? ' x' + s[x] : '')).join(', ') : '(rien)'; };
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(120); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest(); take();
  bot.chat('/cf change marth'); await sleep(1500); log('selection ->', take());
  for (let i = 0; i < 9; i++) log('slot', i, bot.inventory.slots[36 + i] ? bot.inventory.slots[36 + i].name : '-');
  bot.chat('/cf heal'); bot.chat('/cf mannequin clear'); await sleep(250); bot.chat('/cf mannequin attaque'); await sleep(700);
  const z = zomb(); bot.chat('/tp @s ' + (z.position.x + 1.5) + ' ' + z.position.y + ' ' + z.position.z); await sleep(350); await bot.lookAt(z.position.offset(0, 1.2, 0), true); take();
  for (let s = 1; s <= 3; s++) { await click(s, 2500); log('slot ' + s + ' ->', take()); }
  await click(8, 1500); log('ultime ->', take());
  bot.chat('/cf son voice.marth_kill'); await sleep(800); log('/cf son voice.marth_kill ->', take());
  bot.chat('/cf mannequin clear'); await sleep(300);
  bot.chat('/kill @s'); await sleep(1500); log('mort ->', take());
  bot._client.write('client_command', { actionId: 0 }); await sleep(1000);
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
