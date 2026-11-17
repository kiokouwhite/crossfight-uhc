// /cf tp quete <lieu> : le bot doit arriver devant le personnage de chaque lieu, tourne vers lui.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  let said = [];
  bot._client.on('system_chat', m => { if (!m.isActionBar) said.push(flat(m.content)); });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500);
  bot.chat('/gamemode creative'); await sleep(500);
  for (const lieu of process.argv.slice(3)) {
    said = []; bot.chat('/cf tp quete ' + lieu); await sleep(5000);
    const anna = Object.values(bot.entities).filter(e => e.name === 'mannequin').sort((a, b) => a.position.distanceTo(bot.entity.position) - b.position.distanceTo(bot.entity.position))[0];
    if (!anna) { console.log(lieu, ': pas de personnage en vue |', said.join(' ').slice(0, 80)); continue; }
    const d = anna.position.minus(bot.entity.position), vers = Math.atan2(-d.x, -d.z), ecart = Math.abs(((bot.entity.yaw - vers) * 180 / Math.PI + 540) % 360 - 180);
    const sous = bot.blockAt(bot.entity.position.offset(0, -0.5, 0));
    console.log(lieu, ': a', Math.hypot(d.x, d.z).toFixed(1), 'blocs, regard a', ecart.toFixed(0), 'degres du personnage, sur', sous ? sous.name : '?');
  }
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
