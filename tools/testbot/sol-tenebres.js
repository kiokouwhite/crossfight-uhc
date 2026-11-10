const mineflayer = require('mineflayer'); const fs = require('fs');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const console_ = cmd => fs.appendFileSync('/home/user/testserver/console.in', cmd + '\n');
bot.once('spawn', async () => {
  await sleep(1500);
  console_('execute in minecraft:crossfight_tenebres run tp Tester 100000 80 100000');
  await sleep(3000);
  for (const y of [63, 64, 65, 66, 67, 70, 79]) console_(`execute in minecraft:crossfight_tenebres run data get block 0 ${y} 0`);
  for (const y of [63, 64, 65, 66, 67, 70]) for (const m of ['black_concrete', 'bedrock', 'air']) console_(`execute in minecraft:crossfight_tenebres if block 100009 ${y} 100009 minecraft:${m} run say X9Y${y}=${m}`);
  await sleep(2000);
  console.log('bot pos', bot.entity.position.toString(), 'below', (bot.blockAt(bot.entity.position.offset(0,-1,0))||{}).name);
  console_('execute in minecraft:crossfight_test run tp Tester 0 66 0');
  await sleep(1000); bot.quit(); process.exit(0);
});
