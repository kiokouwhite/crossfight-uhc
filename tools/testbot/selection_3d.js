// Ecran de selection en 3D : deux bots dans la meme equipe, survol, choix, choix deja pris, lancement.
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(1) + 's', ...a);
function mk(name) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  bot.msgs = []; bot.on('message', m => bot.msgs.push(m.toString()));
  bot.bars = []; bot._client.on('action_bar', d => bot.bars.push(JSON.stringify(d.text).match(/"value":"([^"]{3,})"/g)?.map(x => x.slice(9, -1)).join(' ') || ''));
  bot.glow = {}; bot.texts = {};
  bot._client.on('entity_metadata', d => {
    for (const m of d.metadata) {
      if (m.key === 0 && typeof m.value === 'number') bot.glow[d.entityId] = (m.value & 0x40) !== 0;
      if (m.key === 23 && m.value) bot.texts[d.entityId] = JSON.stringify(m.value).replace(/"(type|value)":/g, '').slice(0, 120);
    }
  });
  return bot;
}
const kinds = bot => { const o = {}; for (const e of Object.values(bot.entities)) if (e !== bot.entity) o[e.name] = (o[e.name] || 0) + 1; return JSON.stringify(o); };
const dim = async bot => { bot.msgs = []; bot.chat('/data get entity @s Dimension'); await sleep(700); return (bot.msgs.join(' ').match(/minecraft:[a-z_0-9]+/) || ['?'])[0]; };
(async () => {
  const a = mk('Tester'); await new Promise(r => a.once('spawn', r));
  const b = mk('Tester2'); await new Promise(r => b.once('spawn', r));
  await sleep(4000);
  for (const bot of [a, b]) { if ((await dim(bot)).includes('test')) { bot.chat('/cf test'); await sleep(1500); } }
  a.chat('/cf stop'); await sleep(1500);
  a.chat('/execute in minecraft:overworld run tp Tester 0 120 0'); a.chat('/execute in minecraft:overworld run tp Tester2 2 120 0'); await sleep(2500);
  a.chat('/cf start'); await sleep(6000);
  log('monde A', await dim(a), '| monde B', await dim(b));
  log('entites vues par A :', kinds(a)); log('entites vues par B :', kinds(b));
  a.msgs = []; a.chat('/execute in minecraft:crossfight_selection if entity @e[type=item_display]'); await sleep(700); log('item_display dans le monde :', a.msgs.join(' ').slice(0, 80));
  // Les panneaux : les item_display les plus larges de chaque bot. On vise celui du milieu-gauche.
  const panels = Object.values(a.entities).filter(e => e.name === 'item_display');
  const me = a.entity.position;
  const sorted = panels.map(e => ({ e, ang: Math.atan2(e.position.x - me.x, -(e.position.z - me.z)) })).sort((p, q) => p.ang - q.ang);
  log('angles des affichages (deg) :', [...new Set(sorted.map(p => Math.round(p.ang * 180 / Math.PI)))].join(','));
  // Survol : regarder le centre du 1er panneau (le plus a gauche).
  const target = sorted[0].e.position;
  a.glow = {};
  await a.lookAt(new Vec3(target.x, me.y + 1.62 + 1.0, target.z), true); await sleep(800);
  log('A vise le panneau de gauche : affichages qui brillent chez A', Object.values(a.glow).filter(Boolean).length, '| chez B', Object.values(b.glow).filter(Boolean).length);
  const infos = Object.values(a.texts).filter(t => /Voie/.test(t)); log('fiche affichee :', (infos[0] || '(aucune)').slice(0, 100));
  // Choix par A.
  a.msgs = []; a.swingArm(); await sleep(1500);
  log('A clique :', a.msgs.filter(x => /incarnes/.test(x)).join(' ').slice(0, 100) || a.msgs.join(' ').slice(0, 120));
  log('B voit le tampon :', Object.values(b.texts).filter(t => /CHOISI/.test(t)).join(' ').slice(0, 100) || '(rien)');
  // B tente le meme personnage, puis un autre.
  await b.lookAt(new Vec3(target.x, b.entity.position.y + 1.62 + 1.0, target.z), true); await sleep(800);
  b.bars = []; b.swingArm(); await sleep(1000);
  log('B clique sur le meme :', b.bars.filter(Boolean).join(' / ').slice(0, 120));
  const t2 = sorted[sorted.length - 1].e.position;
  await b.lookAt(new Vec3(t2.x, b.entity.position.y + 1.62 + 1.0, t2.z), true); await sleep(800);
  b.msgs = []; b.swingArm(); await sleep(1500);
  log('B clique sur un autre :', b.msgs.filter(x => /incarnes/.test(x)).join(' ').slice(0, 100) || b.msgs.join(' ').slice(0, 120));
  // Tout le monde a choisi : compte a rebours puis lancement.
  await sleep(14000);
  log('apres le lancement : monde A', await dim(a), '| entites A', kinds(a), '| objet en main A', a.inventory.slots[36] ? a.inventory.slots[36].name : '-');
  a.chat('/cf stop'); await sleep(1500);
  a.quit(); b.quit(); process.exit(0);
})();
