const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
bot.on('windowOpen', w => bot.closeWindow(w));
const V = require('vec3').Vec3;
const col = (x, z) => [...Array(8)].map((_, y) => { const b = bot.blockAt(new V(x, 65 + y, z)); return b ? b.name.replace('_stained_glass', '') : '?'; }).join('/');
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000); await bot.look(0, 0, true);
  bot.chat('/cf change neon'); await sleep(2000);
  bot.setQuickBarSlot(3); await sleep(150); bot.activateItem();
  await sleep(150); let n = 0; for (let z = 0; z >= -50; z--) if ((bot.blockAt(new V(2, 66, z)) || {}).name === 'light_blue_stained_glass') n++; log('apres 3 ticks, colonnes posees (cote droit) :', n);
  await sleep(600); n = 0; for (let z = 0; z >= -50; z--) if ((bot.blockAt(new V(2, 66, z)) || {}).name === 'light_blue_stained_glass') n++; log('apres 0.75 s :', n, '| degrade colonne z=-10 :', col(2, -10));
  await sleep(5000); n = 0; for (let z = 0; z >= -50; z--) if ((bot.blockAt(new V(2, 66, z)) || {}).name === 'light_blue_stained_glass') n++; log('apres 5.75 s (murs retires ?) :', n);
  bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
