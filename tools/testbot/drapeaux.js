// La montee de Terrace : des drapeaux a hauteurs croissantes, celui du sommet en dernier.
// node drapeaux.js <monde>
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  let said = [], bar = '';
  bot._client.on('system_chat', m => { const t = flat(m.content); if (m.isActionBar) bar = t; else said.push(t); });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500);
  bot.chat('/gamemode creative'); bot.chat('/cf quete oublier terrace'); await sleep(500);
  bot.chat('/cf quete tp terrace'); await sleep(5000);
  const anna = Object.values(bot.entities).find(e => e.name === 'mannequin' && e.position.distanceTo(bot.entity.position) < 8);
  console.log('Anna a la hauteur', anna ? Math.round(anna.position.y) : '?');
  bot.chat('/cf quete lancer terrace'); await sleep(3000);
  const drapeaux = () => Object.values(bot.entities).filter(e => e.name === 'item_display').sort((a, b) => a.position.y - b.position.y);
  const va = async p => { bot.chat(`/tp @s ${p.x.toFixed(1)} ${(p.y - 0.4).toFixed(1)} ${p.z.toFixed(1)}`); await sleep(2000); };
  // d'abord le plus haut drapeau visible (le sommet, s'il se voit d'ici) : il doit refuser tant qu'il en reste plus bas
  let liste = drapeaux();
  console.log('drapeaux vus au depart :', liste.length, '| hauteurs :', liste.map(e => Math.round(e.position.y)).join(' '));
  const haut = liste[liste.length - 1].position.clone();
  await va(haut); liste = drapeaux();
  console.log('au plus haut d abord : drapeaux vus', liste.length, '| hauteurs :', liste.map(e => Math.round(e.position.y)).join(' '));
  // puis du plus bas au plus haut, en regardant a chaque fois ce qui reste
  for (let tour = 0; tour < 10 && !said.some(t => /Tu l'as fait/.test(t)); tour++) {
    liste = drapeaux(); if (!liste.length) break;
    const p = liste[0].position.clone();
    await va(p);
    console.log('  passe a la hauteur', Math.round(p.y), '-> restants :', drapeaux().map(e => Math.round(e.position.y)).join(' ') || 'aucun');
  }
  await sleep(1500);
  console.log('fin :', (said.find(t => /Tu l'as fait/.test(t)) || 'pas de message de fin').slice(0, 160));
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
