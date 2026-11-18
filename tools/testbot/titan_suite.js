// La suite des attaques du Titan, vue par un bot qui ne fait rien : chaque changement de sa barre, avec l'heure.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  let t0 = Date.now(), suite = [];
  const barres = {};
  bot._client.on('boss_bar', d => { if (d.action === 0) barres[d.entityUUID] = flat(d.title); if (d.action === 1 && barres[d.entityUUID] && !/^Titan/.test(barres[d.entityUUID])) suite.push(((Date.now() - t0) / 1000).toFixed(1) + ' fin'); if (d.action === 2 && d.health === 0 && barres[d.entityUUID] && !/^Titan/.test(barres[d.entityUUID])) suite.push(((Date.now() - t0) / 1000).toFixed(1) + ' part'); if (d.title !== undefined) { const t = flat(d.title); if (!/^Titan/.test(t)) suite.push(((Date.now() - t0) / 1000).toFixed(1) + ' annonce ' + t); } });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500);
  bot.chat('/gamemode creative'); bot.chat('/cf quete oublier shiganshina'); await sleep(500); bot.chat('/cf tp quete shiganshina'); await sleep(5000);
  t0 = Date.now(); suite = []; bot.chat('/cf quete lancer shiganshina');
  await sleep(Number(process.argv[3] || 80) * 1000);
  const m = Object.values(bot.entities).filter(e => e.name === 'mannequin').map(e => e.position.distanceTo(bot.entity.position).toFixed(1));
  console.log(suite.join('  '));
  console.log('mannequins a', m.join(', '), 'blocs');
  bot.chat('/cf quete oublier shiganshina'); await sleep(500);
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
