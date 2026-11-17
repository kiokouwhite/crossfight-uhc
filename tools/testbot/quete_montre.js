// /cf quete montrer : la liste des objets restants d'une quete, puis on les ramasse tous d'apres ces coordonnees.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  let said = [];
  bot._client.on('system_chat', m => { if (!m.isActionBar) said.push(flat(m.content)); });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  const place = process.argv[3];
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500);
  bot.chat('/gamemode creative'); bot.chat('/cf quete oublier ' + place); await sleep(500);
  bot.chat('/cf quete tp ' + place); await sleep(4000);
  bot.chat('/cf quete lancer ' + place); await sleep(2500);
  said = []; bot.chat(place === 'pearl' ? '/cf pearl glowing' : '/cf quete montrer ' + place); await sleep(1500);
  const spots = said.map(t => t.match(/^- (-?\d+) (-?\d+) (-?\d+)/)).filter(Boolean).map(m => m.slice(1, 4).map(Number));
  console.log(place, ':', spots.length, 'objets', JSON.stringify(spots));
  for (const [x, y, z] of spots) { bot.chat(`/tp @s ${x + 0.5} ${y} ${z + 0.5}`); await sleep(1500); }
  said = []; bot.chat('/cf quete liste'); await sleep(800);
  console.log('   ', said.join(' | ').split(' | ').find(t => t.includes(place)));
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
