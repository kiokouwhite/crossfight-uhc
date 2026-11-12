const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const displays = () => Object.values(bot.entities).filter(e => e.name === 'item_display').length;
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change marth'); await sleep(6000); bot.chat('/damage @s 8'); await sleep(600);
  let sounds = []; bot._client.on('packet', (d, m) => { if (/sound_effect/.test(m.name)) { const x = JSON.stringify(d).match(/crossfight:[a-z0-9_.]+/); if (x) sounds.push(x[0].slice(11)); } });
  log('vie', bot.health.toFixed(1), '| pieces du modele :', displays());
  bot.setQuickBarSlot(0); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem();
  for (const t of [1000, 1500, 700, 600]) { await sleep(t); log('vie', bot.health.toFixed(1), '| pieces du modele :', displays(), '| armure visible chez le joueur :', [5, 6, 7, 8].filter(i => bot.inventory.slots[i]).length); }
  log('sons :', [...new Set(sounds)].join(', '));
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
