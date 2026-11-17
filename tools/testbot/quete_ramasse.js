// Ramasser les objets d'une quete en se teleportant dessus : le compte monte, la quete se termine.
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
  bot.chat('/cf quete lancer ' + place); await sleep(1500);
  for (let i = 0; i < 12; i++) {
    const it = Object.values(bot.entities).filter(e => e.name === 'item_display' && e.metadata && e.position.distanceTo(bot.entity.position) < 300 && e.position.distanceTo(bot.entity.position) > 4.5)
      .sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position))[0];
    if (!it) break;
    bot.chat(`/tp @s ${it.position.x.toFixed(1)} ${(it.position.y - 0.5).toFixed(1)} ${it.position.z.toFixed(1)}`); await sleep(1800);
    said = []; bot.chat('/cf quete liste'); await sleep(700);
    console.log((said.join(' | ').split(' | ').find(t => t.includes(place)) || '?'));
    if (said.join(' ').includes(place) && /terminee/.test(said.join(' | ').split(' | ').find(t => t.includes(place)) || '')) break;
  }
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
