const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
bot.on('windowOpen', w => bot.closeWindow(w));
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p)));
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000); bot.chat('/cf change sova'); await sleep(2000);
  bot.setQuickBarSlot(8); await sleep(200); bars = [];
  bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(1200);
  const u = [...new Set(bars.map(b => b.replace(/"[a-z_]+":/g, '').replace(/[^A-Za-z0-9%:.,()/ !●➶]/g, '')))];
  console.log(u.slice(-4).join('\n'));
  await sleep(5500); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
