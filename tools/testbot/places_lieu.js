// Les places d'un lieu (structures.equipes-par-lieu) : deux joueurs sans equipe, une seule place.
// A termine la quete ; B s'y lance (A est prevenu), la termine, et choisit d'ecarter A.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const flat = c => { if (c == null) return ''; if (typeof c === 'string') return c; if (c.type) return flat(c.value); if (Array.isArray(c)) return c.map(flat).join(''); return flat(c.text ?? '') + flat(c[''] ?? '') + flat(c.extra ?? ''); };
const lieu = process.argv[3] || 'garreg_mach';
const joueur = async name => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  bot.dit = []; bot.fenetre = null;
  bot._client.on('system_chat', m => { if (!m.isActionBar) bot.dit.push(flat(m.content)); });
  bot.on('windowOpen', w => { bot.fenetre = w; });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500);
  bot.chat('/gamemode creative'); bot.chat('/cf quete oublier ' + lieu); await sleep(500); bot.chat('/cf quete tp ' + lieu); await sleep(4000);
  return bot;
};
const vide = (...bots) => bots.forEach(b => { b.dit = []; });
const montre = (titre, a, b) => { console.log('--- ' + titre); for (const [n, x] of [['A', a], ['B', b]]) for (const l of x.dit.filter(t => !/Quete oubliee|Te voila|Quete lancee|Bonjour|Héros/.test(t))) console.log('  ' + n + ' : ' + l.slice(0, 230)); };
(async () => {
  const a = await joueur('Invite'), b = await joueur('Invite2');
  if (b.fenetre) { b.closeWindow(b.fenetre); b.fenetre = null; } if (a.fenetre) { a.closeWindow(a.fenetre); a.fenetre = null; }
  vide(a, b); a.chat('/cf quete lancer ' + lieu); await sleep(1500); montre('A se lance', a, b);
  vide(a, b); a.chat('/cf quete finir ' + lieu); await sleep(1500); montre('A termine', a, b); console.log('  menu chez A :', a.fenetre ? flat(a.fenetre.title) : 'aucun');
  if (a.fenetre) { a.closeWindow(a.fenetre); a.fenetre = null; }
  vide(a, b); b.chat('/cf quete lancer ' + lieu); await sleep(1500); montre('B se lance sur un lieu complet', a, b);
  vide(a, b); b.fenetre = null; b.chat('/cf quete finir ' + lieu); await sleep(1500); montre('B termine', a, b);
  console.log('  menu chez B :', b.fenetre ? '"' + flat(b.fenetre.title) + '" ' + b.fenetre.slots.slice(0, 9).map(s => s ? s.name : '-').join(' ') : 'aucun');
  vide(a, b); a.chat('/cf quete liste'); b.chat('/cf quete liste'); await sleep(800);
  console.log('  avant le choix : A', (a.dit.find(t => t.startsWith(lieu)) || '').slice(0, 90), '| B', (b.dit.find(t => t.startsWith(lieu)) || '').slice(0, 90));
  vide(a, b); if (b.fenetre) await b.clickWindow(0, 0, 0).catch(() => {}); await sleep(1500); montre('B ecarte A', a, b);
  vide(a, b); a.chat('/cf quete liste'); b.chat('/cf quete liste'); await sleep(800);
  console.log('  a la fin : A', (a.dit.find(t => t.startsWith(lieu)) || '').slice(0, 90), '| B', (b.dit.find(t => t.startsWith(lieu)) || '').slice(0, 90));
  a.quit(); b.quit(); setTimeout(() => process.exit(0), 500);
})();
