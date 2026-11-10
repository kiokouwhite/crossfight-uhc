const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now();
const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
bot.on('message', (m, pos) => { const s = m.toString(); if (s.trim()) log('CHAT[' + pos + ']', s); });
bot.on('actionBar', m => log('BAR', m.toString()));
bot._client.on('system_chat', p => { if (p.isActionBar) log('RAWBAR', JSON.stringify(p.content).slice(0, 200)); });
bot.on('kicked', r => log('KICKED', JSON.stringify(r)));
bot.on('error', e => log('ERROR', e.message));
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  bot.chat('/cf change byleth'); await sleep(2000);
  bot.chat('/cf mannequin'); await sleep(1500);
  const zombie = Object.values(bot.entities).find(e => e.name === 'zombie');
  log('mannequin', zombie ? zombie.position.toString() : 'absent', 'moi', bot.entity.position.toString());
  bot.setQuickBarSlot(3); await sleep(300);
  log('ACTIVATE');
  bot.activateItem();
  const start = Date.now();
  while (Date.now() - start < 3000) {
    const z = Object.values(bot.entities).find(e => e.name === 'zombie');
    log('moi', bot.entity.position.y.toFixed(2), 'vel', bot.entity.velocity.x.toFixed(2), bot.entity.velocity.y.toFixed(2),
        'mannequin', z ? z.position.toString() : '-');
    await sleep(150);
  }
  bot.chat('/cf mannequin clear'); await sleep(500);
  bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
