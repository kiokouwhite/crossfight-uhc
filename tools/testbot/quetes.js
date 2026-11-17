// Les quetes des lieux, essayees par un admin hors partie : verrou de la borne, lancement, deroulement, deblocage.
// Usage : node quetes.js <monde>
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  let said = [], bar = '';
  bot._client.on('system_chat', m => { const t = flat(m.content); if (m.isActionBar) bar = t; else said.push(t); });
  bot._client.on('action_bar', m => { bar = flat(m.text); });
  await new Promise(r => bot.once('spawn', r));
  await sleep(2500);
  const cmd = async (c, ms = 1500) => { said = []; bot.chat(c); await sleep(ms); return said.join(' | '); };
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(2000);
  bot.chat('/gamemode creative'); await sleep(500);
  console.log(await cmd('/cf quete liste'));
  for (const place of ['garreg_mach', 'shiganshina', 'pearl', 'central_town', 'arcadia', 'terrace', 'beacon', 'forest_of_hope', 'jouki']) {
    await cmd('/cf quete oublier ' + place, 300);
    console.log('==', place, '|', await cmd('/cf quete tp ' + place, 5000));
    // la borne, verrouillee
    const station = Object.values(bot.entities).find(e => e.name === 'interaction' && e.position.distanceTo(bot.entity.position) < 8);
    if (station) { bar = ''; bot.useOn(station); await sleep(800); console.log('   borne avant :', bar); }
    const npc = Object.values(bot.entities).find(e => e.name === 'villager' && e.position.distanceTo(bot.entity.position) < 8);
    if (!npc) { console.log('   pas de personnage'); continue; }
    said = []; bot.useOn(npc); await sleep(1500);
    console.log('   personnage :', said.join(' | '));
    const things = Object.values(bot.entities).filter(e => ['item_display', 'husk', 'giant'].includes(e.name) && e.position.distanceTo(bot.entity.position) < 200);
    console.log('   apparus :', things.map(e => e.name).reduce((a, n) => (a[n] = (a[n] || 0) + 1, a), {}));
    console.log('   etat :', (await cmd('/cf quete liste')).split(' | ').find(t => t.includes(place)));
    console.log('   finir :', await cmd('/cf quete finir ' + place));
    if (station) { bar = ''; bot.useOn(station); await sleep(800); console.log('   borne apres :', bar || '(pas de refus)'); }
  }
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
