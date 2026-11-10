const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
bot.on('message', m => { const s = m.toString(); if (s.trim()) log('CHAT', s); });
bot._client.on('action_bar', p => { const t = JSON.stringify(p); if (!t.includes('Epee du')) log('BAR', t.replace(/[^A-Za-z0-9%:., ]/g, '').replace(/.*value:/, '').slice(0, 100)); });
bot.on('windowOpen', async w => {
  log('MENU ouvert, options :', w.slots.filter(Boolean).map(i => i.name).join(', '));
  await sleep(500);
  let slot = w.slots.findIndex(i => i && i.name === 'zombie_head');
  if (slot < 0) slot = w.slots.findIndex(i => i && i.name === 'iron_sword');
  log('clic sur le slot', slot);
  await bot.clickWindow(slot, 0, 0);
});
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  bot.chat('/cf change byleth'); await sleep(2000);
  bot.chat('/cf mannequin 2 allie'); await sleep(1000);
  bot.setQuickBarSlot(2); await sleep(300);
  log('ACTIVATE tutorat'); bot.activateItem(); await sleep(3000);
  log('ACTIVATE tutorat (2e fois, doit etre en cooldown)'); bot.activateItem(); await sleep(1500);
  bot.chat('/cf mannequin clear'); await sleep(500); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
