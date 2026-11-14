// Ecran de selection (mur plat, un par joueur) : deux bots dans la meme equipe. Survol, choix, choix deja pris,
// lancement ; vitesse de marche (immobilisation sans changer la vue) et tableau lateral masque pendant le choix.
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(1) + 's', ...a);
function mk(name) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  bot.msgs = []; bot.on('message', m => bot.msgs.push(m.toString()));
  bot.bars = []; bot._client.on('action_bar', d => bot.bars.push(JSON.stringify(d.text).match(/"value":"([^"]{3,})"/g)?.map(x => x.slice(9, -1)).join(' ') || ''));
  bot.models = {}; bot.scale = {}; bot.texts = {}; bot.walk = null; bot.sidebar = null;
  bot._client.on('entity_metadata', d => {
    for (const m of d.metadata) {
      const j = JSON.stringify(m.value || '');
      const x = j.match(/crossfight:select\/[a-z_\/]+/); if (x) bot.models[d.entityId] = x[0].replace('crossfight:select/', '');
      if (m.key === 12 && m.value && typeof m.value.x === 'number') bot.scale[d.entityId] = m.value.x;
      if (/CHOISI|TON CHOIX/.test(j)) bot.texts[d.entityId] = /TON CHOIX/.test(j) ? 'TON CHOIX' : 'CHOISI';
    }
  });
  bot._client.on('abilities', d => { bot.walk = d.walkingSpeed; });
  bot._client.on('scoreboard_display_objective', d => { if (d.position === 1) bot.sidebar = d.name; });
  bot._client.on('scoreboard_objective', d => { if (d.action === 1 && d.name === bot.sidebar) bot.sidebar = null; });
  return bot;
}
// Les cartes d'un bot, de gauche a droite : l'image entiere (pack prive) ou la carte vide teinte (sans illustration).
const cards = bot => Object.values(bot.entities).filter(e => e.name === 'item_display' && /^card\/[a-z]+$|^card_plate$/.test(bot.models[e.id] || '')).sort((p, q) => p.position.x - q.position.x);
const halo = bot => Object.values(bot.entities).filter(e => e.name === 'item_display' && bot.models[e.id] === 'card_glow' && (bot.scale[e.id] || 0) > 0.5).map(e => (e.position.x - bot.entity.position.x).toFixed(2)).join(',') || '(aucun)';
const aim = async (bot, i) => { const c = cards(bot)[i]; if (!c) return false; await bot.lookAt(new Vec3(c.position.x, c.position.y, c.position.z), true); await sleep(900); return true; };
const dim = async bot => { bot.msgs = []; bot.chat('/data get entity @s Dimension'); await sleep(700); return (bot.msgs.join(' ').match(/minecraft:[a-z_0-9]+/) || ['?'])[0]; };
(async () => {
  const a = mk('Tester'); await new Promise(r => a.once('spawn', r));
  const b = mk('Tester2'); await new Promise(r => b.once('spawn', r));
  await sleep(4000);
  for (const bot of [a, b]) { if ((await dim(bot)).includes('test')) { bot.chat('/cf test'); await sleep(1500); } }
  a.chat('/cf stop'); await sleep(1500);
  a.chat('/execute in minecraft:overworld run tp Tester 0 120 0'); a.chat('/execute in minecraft:overworld run tp Tester2 2 120 0'); await sleep(2500);
  log('avant : vitesse de marche A', a.walk, '| tableau A', a.sidebar);
  a.chat('/cf start'); await sleep(7000);
  const ca = cards(a), cb = cards(b);
  log('cartes vues par A :', ca.length, ca.map(e => a.models[e.id]).join(','), '| par B :', cb.length);
  const common = ca.filter(e => cb.some(f => f.id === e.id)).length;
  log('affichages communs aux deux :', common, '| 1re carte de A en x', ca[0] && ca[0].position.x.toFixed(2), '| de B', cb[0] && cb[0].position.x.toFixed(2), '| joueurs en x :', a.entity.position.x.toFixed(2), b.entity.position.x.toFixed(2), '| distance du mur :', ca[0] && (a.entity.position.z - ca[0].position.z).toFixed(2));
  log('pendant le choix : vitesse de marche A', a.walk, 'B', b.walk, '| tableau lateral A', a.sidebar, 'B', b.sidebar);
  // Survol : A vise sa carte de gauche, B regarde toujours droit devant.
  await aim(a, 0);
  log('halo allume (decalage par rapport au joueur) chez A :', halo(a), '| chez B :', halo(b));
  a.msgs = []; a.swingArm(); await sleep(1500);
  log('A clique :', a.msgs.filter(x => /incarnes/.test(x)).join(' ').slice(0, 90) || a.msgs.join(' ').slice(0, 120));
  log('tampon chez A :', Object.values(a.texts).join(',') || '(rien)', '| chez B :', Object.values(b.texts).join(',') || '(rien)');
  // B tente le meme personnage sur son propre mur, puis celui de droite.
  await aim(b, 0); b.bars = []; b.msgs = []; b.swingArm(); await sleep(1000);
  log('B clique sur la meme carte :', (b.bars.filter(Boolean).join(' / ') || b.msgs.join(' ')).slice(0, 110));
  await aim(b, cards(b).length - 1); b.msgs = []; b.swingArm(); await sleep(1500);
  log('B clique sur la carte de droite :', b.msgs.filter(x => /incarnes/.test(x)).join(' ').slice(0, 90) || b.msgs.join(' ').slice(0, 120));
  await sleep(15000);
  log('apres le lancement : cartes restantes A', cards(a).length, '| vitesse de marche A', a.walk, 'B', b.walk, '| tableau A', a.sidebar, '| objet en main A', a.inventory.slots[36] ? a.inventory.slots[36].name : '-');
  a.chat('/cf stop'); await sleep(1500);
  a.quit(); b.quit(); process.exit(0);
})();
