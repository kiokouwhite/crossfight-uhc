const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
bot.on('windowOpen', w => bot.closeWindow(w));
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
const pct = z => { try { return JSON.stringify(z.metadata).match(/(\d+)\s*%/)?.[1] + '%'; } catch (e) { return '?'; } };
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  bot.chat('/cf change sova'); await sleep(2000); bot.chat('/cf mannequin'); await sleep(800);
  const z = zomb(); log('mannequin:', z ? z.position.toString() : '-', 'vie', pct(z));
  await bot.lookAt(z.position.offset(0.8, 0, 0), true); await sleep(200);
  bot.setQuickBarSlot(1); await sleep(200);
  bot.activateItem(); await sleep(1300); bot.deactivateItem();
  for (let i = 0; i < 9; i++) { await sleep(300); const zz = zomb(); log('t+' + (i * 0.3 + 0.3).toFixed(1) + 's vie', zz ? pct(zz) : '-', zz ? zz.position.toString() : ''); }
  bot.chat('/cf mannequin clear'); await sleep(400); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
