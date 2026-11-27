// Quatre lootbox (tiers 1 a 4) sous le ciel : sur le toit de l'arene de test (dessous, le toit eteindrait leur
// faisceau) ; attend le fichier <go>, puis les retire (/cf clear du mode test retire toutes les lootbox).
const mineflayer = require('mineflayer');
const fs = require('fs');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const go = process.argv[2];
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
bot.on('messagestr', m => { if (/Lootbox|Impossible|Spread|Could/i.test(m)) console.log('  chat :', m); });
bot.once('spawn', async () => {
  await sleep(3000);
  bot.chat('/cf test'); await sleep(9000);
  bot.chat('/tp @s -4.5 126 0.5'); await sleep(3000);
  const p = bot.entity.position.floored();
  console.log('surface :', bot.game.dimension, p.toString());
  for (let t = 1; t <= 4; t++) {
    bot.chat(`/tp @s ${p.x + 0.5 + 3 * (t - 1)} ${p.y + 1} ${p.z + 2.5} 180 0`); await sleep(1500);
    bot.chat('/cf lootbox ' + t); await sleep(800);
  }
  bot.chat(`/tp @s ${p.x + 0.5} ${p.y + 30} ${p.z + 30} 180 0`); await sleep(500);
  console.log('PRET', p.x, p.y, p.z);
  while (!fs.existsSync(go)) await sleep(200);
  bot.chat('/cf clear'); await sleep(2500);
  bot.chat('/cf test'); await sleep(800);
  console.log('FINI');
  bot.quit(); process.exit(0);
});
