// La bulle d'Anna : l'accueil avant la quete, la recompense apres (chaque joueur ne voit que la sienne).
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  const place = process.argv[3];
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500);
  bot.chat('/cf quete oublier ' + place); await sleep(500);
  bot.chat('/cf quete tp ' + place); await sleep(5000);
  const texts = () => Object.values(bot.entities).filter(e => e.name === 'text_display' && e.position.distanceTo(bot.entity.position) < 8)
    .map(e => flat((e.metadata || []).find(m => m && typeof m === 'object' && (m.type || m.text || m.extra)))).filter(t => t.length > 20).map(t => t.slice(0, 60));
  console.log('avant :', texts());
  bot.chat('/cf quete finir ' + place); await sleep(3000);
  console.log('apres :', texts());
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
