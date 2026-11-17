// Entre dans un lieu : le titre s'affiche, le tableau de bord annonce la quete, le personnage de quete est la et repond.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  const seen = { titles: [], said: [], board: new Set() };
  bot._client.on('set_title_text', m => seen.titles.push('T:' + flat(m.text)));
  bot._client.on('set_title_subtitle', m => seen.titles.push('S:' + flat(m.text)));
  bot._client.on('system_chat', m => { if (!m.isActionBar) seen.said.push(flat(m.content)); });
  bot._client.on('scoreboard_score', m => seen.board.add(flat(m.displayName) || m.itemName));
  await new Promise(r => bot.once('spawn', r));
  await sleep(2500);
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s ' + process.argv.slice(3).join(' '));
  await sleep(6000);
  console.log('titres :', JSON.stringify(seen.titles));
  console.log('tableau :', [...seen.board].filter(t => /uete|cours|professeur/i.test(t)));
  const npc = Object.values(bot.entities).find(e => (e.name === 'mannequin' || e.name === 'villager'));
  console.log('personnage :', npc ? `${npc.position} nom ${flat(npc.metadata && npc.metadata[2])}` : 'aucun');
  if (npc) { seen.said.length = 0; await bot.lookAt(npc.position.offset(0, 1, 0)); bot.useOn(npc); await sleep(1500); console.log('dit :', seen.said.join(' | ')); }
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
