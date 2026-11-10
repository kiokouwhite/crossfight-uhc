const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
bot.on('windowOpen', w => bot.closeWindow(w));
const hotbar = () => [...Array(9)].map((_, i) => { const it = bot.inventory.slots[36 + i]; return it ? it.name : '-'; }).join(' ');
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  bot.chat('/cf change takumi'); await sleep(2000);
  log('avant   :', hotbar());
  bot.setQuickBarSlot(1); await sleep(300); bot.activateItem(); await sleep(1000);
  log('pendant :', hotbar());
  await sleep(30500);
  log('apres   :', hotbar());
  bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
