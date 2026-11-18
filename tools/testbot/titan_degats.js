// Le Titan fait-il mal, et casse-t-il des blocs ? Le bot reste a cote de lui sans se soigner et compte.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  let barre = '', attaque = '', journal = [], vie = 20, casses = 0;
  bot._client.on('boss_bar', d => { if (d.title !== undefined) { barre = flat(d.title); attaque = (barre.split('|')[1] || '').trim(); } });
  bot._client.on('block_change', d => { if (d.type === 0) casses++; });
  bot._client.on('multi_block_change', d => { casses += (d.records || []).length; });
  bot.on('health', () => { if (bot.health < vie - 0.4) journal.push((vie - bot.health).toFixed(1) + ' (' + (attaque || 'entre deux') + ')'); vie = bot.health; });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500);
  bot.chat('/gamemode creative'); bot.chat('/cf quete oublier shiganshina'); await sleep(500); bot.chat('/cf tp quete shiganshina'); await sleep(5000);
  bot.chat('/cf quete lancer shiganshina'); await sleep(1500); bot.chat('/effect clear @s'); bot.chat('/gamemode survival'); await sleep(500);
  const t0 = Date.now(); casses = 0;
  while (Date.now() - t0 < 75000) {
    await sleep(1000);
    if (bot.health < 8) { bot.chat('/effect give @s minecraft:instant_health 1 5 true'); await sleep(300); vie = bot.health; }
  }
  console.log('degats recus :', journal.length, 'fois :', journal.join(', '));
  console.log('blocs changes pres du bot :', casses);
  bot.chat('/cf quete oublier shiganshina'); await sleep(500);
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
