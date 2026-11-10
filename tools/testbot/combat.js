const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let hits = [];
bot._client.on('action_bar', p => { const t = JSON.stringify(p); if (t.includes('Mannequin')) hits.push('BAR ' + t.replace(/[^A-Za-z0-9%:., ]/g, '').slice(0, 90)); });
bot.on('entityHurt', e => { if (e.name === 'zombie') hits.push('HURT zombie'); });
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  bot.chat('/cf change marth'); await sleep(2000);
  bot.chat('/cf mannequin'); await sleep(1500);
  const z = Object.values(bot.entities).find(e => e.name === 'zombie');
  log('attack_speed =', JSON.stringify(bot.entity.attributes && (bot.entity.attributes['minecraft:attack_speed'] || bot.entity.attributes['attack_speed'])));
  await bot.lookAt(z.position.offset(0, 1.2, 0), true); await sleep(300);
  bot.setQuickBarSlot(0); await sleep(200);
  log('--- 20 balancements de bras sans attaquer (clic maintenu) sur 3 secondes');
  for (let i = 0; i < 20; i++) { bot.swingArm('right'); await sleep(150); }
  await sleep(500);
  log('coups enregistres :', hits.length); hits.slice(0, 4).forEach(h => log('  ', h)); hits = [];
  log('--- 10 vraies attaques (spam clic) sur 1 seconde');
  for (let i = 0; i < 10; i++) { bot.attack(z); await sleep(100); }
  await sleep(500);
  log('coups enregistres :', hits.length); hits.slice(0, 3).forEach(h => log('  ', h));
  bot.chat('/cf mannequin clear'); await sleep(500); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
