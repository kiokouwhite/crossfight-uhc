const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let lastWin = null; bot.on('windowOpen', w => { lastWin = w; log('  [fenetre]', JSON.stringify(w.title).replace(/[^A-Za-z0-9 ():/]/g, '').slice(0, 80), 'slots', w.inventoryStart ?? '?', 'type', w.type); });
bot.on('windowClose', w => log('  [fermee]'));
let chat = []; bot.on('messagestr', m => { chat.push(m); if (/Pouvoir|absorb|deja/i.test(m)) log('  [chat]', m.slice(0, 100)); });
const names = w => { const out = []; for (let i = 0; i < (w.inventoryStart || 18); i++) if (w.slots[i]) out.push(i + ':' + w.slots[i].name); return out.join(' '); };
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change john'); await sleep(2000);
  bot.chat('/cf choose'); await sleep(800); const w1 = lastWin; lastWin = null; log('menu 1 :', names(w1));
  await bot.clickWindow(0, 0, 0); await sleep(800); const w2 = lastWin; lastWin = null; log('menu 2 :', w2 ? names(w2) : 'aucune');
  if (w2) { const first = w2.slots.findIndex((s, i) => s && i < 9); await bot.clickWindow(first, 0, 0); await sleep(1000); }
  log('hotbar :', bot.inventory.slots.slice(36, 45).map(s => s ? s.name : '-').join(','));
  await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
