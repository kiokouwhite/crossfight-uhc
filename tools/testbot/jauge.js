const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
bot.on('windowOpen', w => bot.closeWindow(w));
const bars = () => Object.values(bot.bossBars || {}).map(b => (b.title ? JSON.stringify(b.title).replace(/[^A-Za-z0-9%:() ]/g, '').replace(/.*value:/, '') : '?') + ' ' + (b.health !== undefined ? Math.round(b.health * 100) + '%' : '')).join(' | ');
let lastBar = ''; bot._client.on('action_bar', p => lastBar = JSON.stringify(p).replace(/[^A-Za-z0-9%:()/ |]/g, '').replace(/.*value:/, '').slice(0, 160));
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  bot.chat('/cf change ruby'); await sleep(3000);
  log('barres de boss :', bars()); log('ligne cooldowns :', lastBar);
  bot.setQuickBarSlot(2); await sleep(200); bot.activateItem(); await sleep(2500);
  log('mode lent actif 2.5 s -> barres :', bars());
  bot.activateItem(); await sleep(3500);
  log('desactive 3.5 s -> barres :', bars());
  bot.chat('/cf change blake'); await sleep(2500); log('Blake -> barres :', bars());
  bot.chat('/cf test'); await sleep(1500); log('hors test -> barres :', bars() || '(aucune)');
  bot.quit(); process.exit(0);
});
