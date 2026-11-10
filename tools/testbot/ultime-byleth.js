const mineflayer = require('mineflayer'); const fs = require('fs');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const console_ = cmd => fs.appendFileSync('/home/user/testserver/console.in', cmd + '\n');
bot.on('message', (m) => { const s = m.toString(); if (s.trim()) log('CHAT', s); });
bot._client.on('respawn', p => log('WORLD', p.worldState ? p.worldState.name : '?'));
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  bot.chat('/cf change byleth'); await sleep(2000);
  bot.chat('/cf mannequin'); await sleep(1000);
  const z = Object.values(bot.entities).find(e => e.name === 'zombie');
  log('mannequin equipement :', z ? JSON.stringify((z.equipment || []).map(i => i ? i.name : '-')) : 'absent');
  bot.chat('/cf ult 100'); await sleep(800);
  bot.setQuickBarSlot(8); await sleep(300);
  log('ACTIVATE'); bot.activateItem();
  for (let i = 0; i < 6; i++) { await sleep(1000); const zz = Object.values(bot.entities).find(e => e.name === 'zombie'); log('t+' + (i + 1) + 's', 'moi', bot.entity.position.toString(), 'mannequin', zz ? zz.position.toString() : '-'); }
  console_('execute in minecraft:crossfight_tenebres if block 0 64 0 minecraft:black_concrete run say FLOOR_BLACK_OK');
  console_('execute in minecraft:crossfight_tenebres if block 0 64 0 minecraft:bedrock run say FLOOR_BEDROCK');
  await sleep(1500);
  bot.chat('/kill @s'); await sleep(2500);
  log('apres mort', 'moi', bot.entity.position.toString());
  bot.chat('/cf mannequin clear'); await sleep(500); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
