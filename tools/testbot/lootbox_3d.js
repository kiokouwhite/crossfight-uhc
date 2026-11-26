// Lootbox en 3D : quatre lootbox (tiers 1 a 4) posees en ligne dans l'arene de test. Pour chacune : la barriere,
// les deux affichages (le coffre, et un bloc plus haut le parachute teint), la zone a cliquer. Puis le testeur ouvre la premiere en cliquant
// dessus, la vide, la referme : elle doit disparaitre. Les trois autres restent pour la camera jusqu'au fichier <go>.
// node lootbox_3d.js [go] [garder : n'ouvre pas la premiere, pour filmer les quatre]
const mineflayer = require('mineflayer');
const fs = require('fs');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const go = process.argv[2];
const garder = process.argv[3] === 'garder';
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const metas = {};
bot._client.on('entity_metadata', d => { metas[d.entityId] = Object.assign(metas[d.entityId] || {}, Object.fromEntries(d.metadata.map(m => [m.key, m.value]))); });
const V = (x, y, z) => bot.entity.position.clone().set(x, y, z);
const texte = c => { const j = JSON.stringify(c || ''); return (j.match(/"(?:text|value)":"([^"]*)"/g) || []).map(x => x.split(':"')[1].slice(0, -1)).join(''); };
const pres = (x, z, type) => Object.values(bot.entities).filter(e => e.name === type && Math.abs(e.position.x - x) < 0.6 && Math.abs(e.position.z - z) < 0.6);
bot.once('spawn', async () => {
  await sleep(3000);
  bot.chat('/cf test'); await sleep(9000);
  bot.chat('/cf clear'); await sleep(6000);
  const xs = [-4.5, -1.5, 1.5, 4.5];
  for (let t = 1; t <= 4; t++) {
    bot.chat(`/tp @s ${xs[t - 1]} 65 2.5 180 0`); await sleep(900);
    bot.chat(`/cf lootbox ${t}`); await sleep(700);
  }
  bot.chat('/tp @s 0.5 65 4.5 180 0'); await sleep(1500);
  for (let t = 1; t <= 4; t++) {
    const x = xs[t - 1], b = bot.blockAt(V(x, 65, 0.5));
    const aff = pres(x, 0.5, 'item_display'), zone = pres(x, 0.5, 'interaction');
    const decrit = a => {
      const m = metas[a.id];
      const objet = m ? Object.values(m).find(v => v && typeof v === 'object' && v.itemId !== undefined) : null;
      const comp = objet && objet.components ? JSON.stringify(objet.components).replace(/"type":/g, '').replace(/"floats":\[\],"flags":\[\],"strings":\[\],/, '').slice(0, 160) : '';
      return `y ${a.position.y.toFixed(2)} ${comp}`;
    };
    console.log(`tier ${t} : bloc ${b ? b.name : '?'} | affichages ${aff.length} : ${aff.map(decrit).join(' ; ')} | zone ${zone.length}`);
  }
  // Ouvrir la lootbox tier 1 en cliquant sur sa zone.
  const zone = garder ? null : pres(-4.5, 0.5, 'interaction')[0];
  if (zone) {
  bot.chat('/tp @s -4.5 65 2.5 180 20'); await sleep(1200);
  const ouverte = new Promise(r => bot.once('windowOpen', r));
  bot.activateEntity(zone);
  const w = await Promise.race([ouverte, sleep(3000).then(() => null)]);
  if (!w) console.log('ouverture : aucune fenetre');
  else {
    const contenu = w.slots.slice(0, 27).map((s, i) => s ? `${i}:${s.name}x${s.count}` : null).filter(Boolean);
    console.log('ouverture :', texte(w.title), '|', contenu.join(' '));
    for (const s of w.slots.slice(0, 27).map((s, i) => s ? i : -1).filter(i => i >= 0)) { await bot.clickWindow(s, 0, 1); await sleep(250); }
    bot.closeWindow(w); await sleep(1200);
    const b = bot.blockAt(V(-4.5, 65, 0.5));
    console.log('videe et refermee : bloc', b ? b.name : '?', '| affichage', pres(-4.5, 0.5, 'item_display').length, '| zone', pres(-4.5, 0.5, 'interaction').length);
  }
  }
  bot.chat('/tp @s 9.5 65 9.5 180 0'); await sleep(500);
  console.log('PRET');
  if (go) { while (!fs.existsSync(go)) await sleep(200); }
  bot.chat('/cf clear'); await sleep(4000);
  const reste = [-1.5, 1.5, 4.5].map(x => bot.blockAt(V(x, 65, 0.5))).map(b => b ? b.name : '?');
  console.log('apres /cf clear :', reste.join(' '), '| affichages', Object.values(bot.entities).filter(e => e.name === 'item_display' && Math.abs(e.position.z - 0.5) < 0.6).length);
  console.log('FINI');
  bot.chat('/cf test'); await sleep(800);
  bot.quit(); process.exit(0);
});
bot.on('error', e => { console.log('erreur', e.message); process.exit(1); });
