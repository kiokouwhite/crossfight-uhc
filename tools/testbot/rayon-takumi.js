const mineflayer = require('mineflayer'); const fs = require('fs');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const console_ = c => fs.appendFileSync('/home/user/testserver/console.in', c + '\n');
bot.on('windowOpen', w => bot.closeWindow(w));
let hurts = 0; bot.on('entityHurt', e => { if (e.name === 'zombie') hurts++; });
bot._client.on('action_bar', p => { const t = JSON.stringify(p); if (t.includes('Mannequin')) log('BAR', t.replace(/[^A-Za-z0-9%:., ]/g, '').replace(/.*value:Mannequin/, 'Mannequin').slice(0, 80)); });
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  bot.chat('/cf change takumi'); await sleep(2000);
  await bot.look(0, 0, true); await sleep(300);
  bot.chat('/cf mannequin 3'); await sleep(500);
  bot.chat('/tp @e[type=zombie] ~ ~ ~-12'); await sleep(800);
  bot.chat('/cf ult 100'); await sleep(800);
  bot.setQuickBarSlot(8); await sleep(300);
  const zs = Object.values(bot.entities).filter(e => e.name === 'zombie'); log('mannequins :', zs.map(z => z.position.toString()).join(' '), 'moi', bot.entity.position.toString(), 'yaw', bot.entity.yaw.toFixed(2));
  log('ACTIVATE rayon'); bot.activateItem();
  // sondes serveur pendant le rayon (a t+4.5s) puis apres (t+7.5s)
  await sleep(4500);
  for (const m of ['white_stained_glass', 'pink_stained_glass', 'magenta_stained_glass', 'air']) console_(`execute in minecraft:crossfight_test if block 0 66 -10 minecraft:${m} run say PENDANT_centre=${m}`);
  for (const m of ['magenta_stained_glass', 'pink_stained_glass', 'air']) console_(`execute in minecraft:crossfight_test if block 4 66 -10 minecraft:${m} run say PENDANT_bord=${m}`);
  log('t+4.5s coups sur mannequins :', hurts);
  await sleep(3000);
  for (const m of ['white_stained_glass', 'air']) console_(`execute in minecraft:crossfight_test if block 0 66 -10 minecraft:${m} run say APRES_centre=${m}`);
  await sleep(1000);
  bot.chat('/cf mannequin clear'); await sleep(500); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
