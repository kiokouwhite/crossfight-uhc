const mineflayer = require('mineflayer'); const fs = require('fs');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const console_ = c => fs.appendFileSync('/home/user/testserver/console.in', c + '\n');
bot.on('windowOpen', w => bot.closeWindow(w));
const effects = () => Object.values(bot.entity.effects || {}).map(e => e.id + ':' + (e.amplifier + 1)).join(' ');
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  bot.chat('/cf change corrin'); await sleep(2500);
  log('sur terre, effets =', effects());
  console_('execute in minecraft:crossfight_test run fill 10 65 10 12 66 12 minecraft:water');
  await sleep(500); bot.chat('/tp @s 11 66 11'); await sleep(2500);
  log('dans l eau, effets =', effects(), 'inWater=', bot.entity.isInWater);
  bot.chat('/tp @s 0 66 0'); await sleep(3500);
  log('ressorti, effets =', effects());
  console_('execute in minecraft:crossfight_test run fill 10 65 10 12 66 12 minecraft:air');
  bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
