// Le Titan de Shiganshina : le bot lance la quete, le frappe jusqu'a la fin, et note ses attaques et ce qu'il encaisse.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  let said = [], barre = '', attaques = {}, titres = new Set(), coups = 0, blessures = [], vie = 20, effets = new Set();
  bot._client.on('system_chat', m => { if (!m.isActionBar) said.push(flat(m.content)); });
  bot._client.on('boss_bar', d => { if (d.title !== undefined) { barre = flat(d.title); const a = barre.split('|')[1]; if (a) attaques[a.trim()] = (attaques[a.trim()] || 0) + 1; } });
  bot._client.on('set_title_text', d => { const t = flat(d.text); if (t) titres.add(t); });
  bot._client.on('entity_effect', d => { if (bot.entity && d.entityId === bot.entity.id) effets.add(d.effectId); });
  bot.on('health', () => { if (bot.health < vie - 0.5) blessures.push((vie - bot.health).toFixed(1)); vie = bot.health; });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500);
  bot.chat('/gamemode creative'); bot.chat('/cf quete oublier shiganshina'); await sleep(500); bot.chat('/cf tp quete shiganshina'); await sleep(5000);
  bot.chat('/cf quete lancer shiganshina'); await sleep(1500);
  bot.chat('/gamemode survival'); bot.chat('/effect give @s minecraft:regeneration 900 5 true'); bot.chat('/effect give @s minecraft:resistance 900 1 true'); await sleep(800);
  // deux mannequins dans les parages : Anna, tout pres, et le Titan, plus loin ; on garde l'identifiant du second
  const loin = Object.values(bot.entities).filter(e => e.name === 'mannequin').sort((x, y) => y.position.distanceTo(bot.entity.position) - x.position.distanceTo(bot.entity.position))[0];
  const id = loin ? loin.id : -1;
  const titan = () => bot.entities[id];
  console.log('titan :', titan() ? 'la, a ' + titan().position.distanceTo(bot.entity.position).toFixed(1) + ' blocs' : 'absent', '|', barre);
  const t0 = Date.now(); let fin = false;
  while (Date.now() - t0 < 230000 && !fin) {
    const g = titan();
    if (!g) { await sleep(500); said = []; bot.chat('/cf quete liste'); await sleep(500); if (said.some(t => /shiganshina.*termin/.test(t))) fin = true; continue; }
    if (g.position.distanceTo(bot.entity.position) > 3.5) { bot.chat(`/tp @s ${(g.position.x + 1.5).toFixed(2)} ${g.position.y.toFixed(2)} ${(g.position.z + 1.5).toFixed(2)}`); await sleep(250); }
    await bot.lookAt(g.position.offset(0, 1.5, 0), true); bot.attack(g); coups++; await sleep(520);
  }
  console.log('fini :', fin, 'en', ((Date.now() - t0) / 1000).toFixed(0), 's, apres', coups, 'coups donnes | barre :', barre);
  console.log('attaques vues :', JSON.stringify(attaques));
  console.log('titres :', [...titres].join(' / '));
  console.log('blessures :', blessures.length, 'fois', blessures.slice(0, 25).join(' '), '| effets recus (ids) :', [...effets].join(','));
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
