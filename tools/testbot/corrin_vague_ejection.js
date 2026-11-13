// Ultime de Corrin lance par le bot (mode test normal) sur des zombies d'entrainement a plusieurs distances.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
let msgs = []; bot.on('message', m => msgs.push(m.toString()));
const D = [3, 4, 5, 6, 7, 8, 9, 10, 12, 14, 17, 20];
bot.once('spawn', async () => {
  await sleep(3000);
  bot.chat('/cf stop'); await sleep(800);
  if (!(await (async () => { msgs = []; bot.chat('/data get entity @s Dimension'); await sleep(600); return msgs.join(' ').includes('crossfight_test'); })())) { bot.chat('/cf test'); await sleep(3000); }
  bot.chat('/cf change corrin'); await sleep(1500);
  const rounds = [];
  for (let round = 0; round < 5; round++) {
    bot.chat('/cf clear'); await sleep(1500);
    bot.chat('/tp @s 0.5 65 0.5 0 0'); await sleep(500); // regarde vers le sud (+Z)
    D.forEach((d, i) => bot.chat(`/summon zombie ${(i % 2 ? 0.5 + (round - 1) * 1.3 : -1.5 + round)} 65 ${(0.5 + d + round * 0.23).toFixed(2)} {Tags:["cf_mannequin","v${d}"],PersistenceRequired:1b,Silent:1b,attributes:[{id:"minecraft:movement_speed",base:0.0},{id:"minecraft:max_health",base:100.0}],Health:100f}`));
    await sleep(1500);
    bot.setQuickBarSlot(8); await sleep(200);
    bot.activateItem(); await sleep(80); bot.deactivateItem();
    const max = {};
    const t0 = Date.now();
    while (Date.now() - t0 < 7500) {
      for (const d of D) {
        msgs = []; bot.chat(`/data get entity @e[tag=v${d},limit=1] Pos[1]`); await sleep(60);
        const y = parseFloat((msgs.join(' ').match(/data: ([0-9.]+)d/) || [0, 'NaN'])[1]);
        if (!isNaN(y)) max[d] = Math.max(max[d] || 0, y - 65);
      }
    }
    rounds.push(D.map(d => d + ':' + (max[d] === undefined ? '?' : max[d].toFixed(0))).join(' '));
    bot.chat('/kill @e[type=zombie]'); await sleep(4000);
  }
  console.log('hauteur max atteinte par chaque zombie (distance:blocs)\n' + rounds.join('\n'));
  bot.quit(); process.exit(0);
});
