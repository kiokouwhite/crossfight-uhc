const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(1) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
let sounds = []; bot._client.on('packet', (d, m) => { if (/sound_effect/.test(m.name)) { const x = JSON.stringify(d).match(/crossfight:[a-z0-9_.]+/); if (x) sounds.push(((Date.now() - t0) / 1000).toFixed(1) + ' ' + x[0].slice(11)); } });
const take = () => { const s = sounds.join(', ') || '(rien)'; sounds = []; return s; };
const use = async slot => { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); };
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest(); take();
  t0 = Date.now(); bot.chat('/cf change takumi'); await sleep(2500); log('selection ->', take());
  const names = [...Array(9).keys()].map(i => bot.inventory.slots[36 + i] ? bot.inventory.slots[36 + i].name : '-'); log('barre :', names.join(', '));
  for (let i = 1; i <= 3; i++) { if (names[i] === '-') continue; t0 = Date.now(); await use(i); await sleep(1800); log('case ' + (i + 1) + ' (' + names[i] + ') ->', take()); }
  bot.chat('/cf heal'); await sleep(300);
  t0 = Date.now(); await use(8); await sleep(6000); log('Ultime (charge puis tir) ->', take());
  bot.chat('/cf son voice.takumi_kill'); await sleep(600); log('kill ->', take());
  bot.chat('/kill @s'); await sleep(1500); log('mort ->', take());
  bot._client.write('client_command', { actionId: 0 }); await sleep(1000);
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
