// La peche de Garreg Mach : le bot recoit la canne, vise l'eau, attend la touche et ferre quand le cercle est referme.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  let said = [], bar = '';
  const onBar = t => { bar = t; };
  bot._client.on('system_chat', m => { const t = flat(m.content); if (m.isActionBar) onBar(t); else said.push(t); });
  bot._client.on('action_bar', m => onBar(flat(m.text)));
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500);
  bot.chat('/gamemode creative'); bot.chat('/clear'); bot.chat('/cf quete oublier garreg_mach'); await sleep(500);
  bot.chat('/cf quete tp garreg_mach'); await sleep(5000);
  bot.chat('/cf quete lancer garreg_mach'); await sleep(1500);
  const rod = bot.inventory.items().find(i => i.name === 'fishing_rod');
  console.log('canne :', rod ? 'recue' : 'absente');
  if (rod) await bot.equip(rod, 'hand');
  // au bord de l'eau : le bassin du monastere
  const open = b => [1, 2, 3, 4, 5].every(d => { const u = bot.blockAt(b.position.offset(0, d, 0)); return u && u.name === 'air'; });
  const water = bot.findBlock({ matching: b => b && b.name === 'water', useExtraInfo: b => open(b), maxDistance: 120 });
  if (!water) { console.log('pas d\'eau a portee'); bot.quit(); return process.exit(0); }
  bot.chat(`/tp @s ${water.position.x + 0.5} ${water.position.y + 4} ${water.position.z + 0.5}`); await sleep(1500);
  bot.creative.startFlying(); await sleep(300);
  const mode = process.argv[3] || 'parfait';
  let hooks = 0; bot._client.on('spawn_entity', d => { const n = bot.registry.entities[d.type]; if (n && n.name === 'fishing_bobber') hooks++; });
  let sparks = 0, where = new Set(); bot._client.on('world_particles', d => { const n = d.particle && d.particle.type || d.particleId; if (d.y > 0) { sparks++; where.add(Math.floor(d.x) + ',' + Math.floor(d.z)); } });
  let bossbar = 0, lowest = 1, titles = [];
  bot._client.on('boss_bar', d => { bossbar++; if (d.health !== undefined) lowest = Math.min(lowest, d.health); });
  bot._client.on('set_title_text', d => { const t = flat(d.text); if (t && !titles.includes(t)) titles.push(t); });
  const t0 = Date.now(); let caught = 0, done = false;
  while (Date.now() - t0 < 150000 && !done) {
    await bot.look(0, -Math.PI / 2, true); bar = ''; bot.activateItem(); await sleep(200);
    if (/Lance ta ligne/.test(bar)) { console.log('ne voit pas l\'eau'); break; }
    const t1 = Date.now();
    while (Date.now() - t1 < 60000) {
      await sleep(25);
      if (/Clic droit, vite/.test(bar)) { bar = ''; if (mode !== 'lent') await sleep(100); else await sleep(1200); bot.activateItem(); }
      if (mode === 'parfait' && /PARFAIT/.test(bar)) { bar = ''; bot.activateItem(); }
      if (mode === 'tot' && /attends/.test(bar)) { bar = ''; bot.activateItem(); await sleep(400); }
      const got = said.find(t => /Tu as pris/.test(t));
      if (got) { caught++; console.log(got, '| ' + ((Date.now() - t1) / 1000).toFixed(1) + ' s'); said = said.filter(t => !/Tu as pris/.test(t)); break; }
    }
    await sleep(400); said = []; bot.chat('/cf quete liste'); await sleep(600);
    if (said.some(t => /garreg_mach.*termin/.test(t))) { console.log('quete terminee apres', caught, 'prises'); done = true; }
    said = [];
  }
  console.log('mode', mode, '| paquets barre de boss :', bossbar, '| etincelles :', sparks, 'sur', where.size, 'cases | bouchons :', hooks, '| titres :', titles.join(' / '));
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
