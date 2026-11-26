// Roulettes d'une Lootbox : le testeur (pack charge) pose une lootbox du tier donne et l'ouvre. Pendant les roulettes :
// les titres recus (la fenetre du pack change de couleur), les objets des trois roulettes verticales (diamants, objet,
// or), et le tier des objets de la roulette du milieu compare a la couleur de la fenetre a ce moment-la ; un clic sur
// la case du centre (refuse). A la fin : la couleur finale, le contenu, puis il vide la lootbox, qui disparait.
// node lootbox_roulette.js [tier]
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const tier = process.argv[2] || '3';
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', async d => { bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 3 }); await sleep(100); bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 0 }); });
const V = (x, y, z) => bot.entity.position.clone().set(x, y, z);
const t0 = { v: 0 };
let couleur = 0, ouvert = false;
const titres = [], passages = { diamants: 0, objet: 0, or: 0 }, accord = { oui: 0, non: [] }, comptes = { diamants: new Set(), or: new Set() };
const ROULEAUX = { 2: 'diamants', 11: 'diamants', 20: 'diamants', 4: 'objet', 13: 'objet', 22: 'objet', 6: 'or', 15: 'or', 24: 'or' };
const tierDe = item => { const m = JSON.stringify(item || '').match(/Lootbox tier (\d)/); return m ? Number(m[1]) : 0; };
const vu = (slot, item) => {
  const r = ROULEAUX[slot];
  if (!ouvert || !r || !item || item.itemCount === 0) return;
  passages[r]++;
  if (r === 'objet') { const t = tierDe(item); if (t === couleur) accord.oui++; else accord.non.push(`fenetre ${couleur}, objet tier ${t}`); }
  else comptes[r].add(item.itemCount);
};
bot._client.on('open_window', d => {
  const c = [...JSON.stringify(d.windowTitle ?? d.title)].map(ch => ch.charCodeAt(0)).find(n => n >= 0xe2a0 && n <= 0xe2a3);
  if (c) { couleur = c - 0xe2a0 + 1; titres.push(`${Date.now() - t0.v} ms : fenetre ${couleur}`); }
});
bot._client.on('set_slot', d => vu(d.slot, d.item));
bot._client.on('window_items', d => (d.items || []).forEach((it, i) => vu(i, it)));
bot.once('spawn', async () => {
  await sleep(3000);
  bot.chat('/cf test'); await sleep(9000);
  bot.chat('/tp @s 0.5 65 4.5 180 20'); await sleep(1200);
  bot.chat('/cf lootbox ' + tier); await sleep(1200);
  const zone = Object.values(bot.entities).find(e => e.name === 'interaction' && Math.abs(e.position.z - 2.5) < 0.6);
  t0.v = Date.now(); ouvert = true;
  bot.activateEntity(zone);
  await sleep(1500);
  // Un clic sur la case du centre pendant la roulette : rien ne doit partir dans l'inventaire.
  const w = bot.currentWindow;
  if (w) { await bot.clickWindow(13, 0, 0).catch(() => {}); await sleep(300); }
  console.log('clic pendant la roulette, curseur :', bot.inventory.cursor ? bot.inventory.cursor.name : 'vide');
  await sleep(4500);
  ouvert = false;
  console.log('titres recus :', titres.length, '|', titres.map(t => t.split(' : ')[1].replace('fenetre ', '')).join(' '), '| derniere a', titres.length ? titres[titres.length - 1].split(' ')[0] : '-', 'ms');
  console.log('objets vus passer :', JSON.stringify(passages), '| diamants vus :', [...comptes.diamants].sort((a, b) => a - b).join(','), '| or vu :', [...comptes.or].sort((a, b) => a - b).join(','));
  console.log('roulette du milieu, objet du tier de la fenetre :', accord.oui, 'fois ; autrement :', accord.non.length, accord.non.slice(0, 6).join(' / '));
  const fin = bot.currentWindow;
  if (fin) {
    console.log('contenu final :', fin.slots.slice(0, 27).map((s, i) => s ? `${i}:${s.name}x${s.count}` : null).filter(Boolean).join(' '));
    for (const s of fin.slots.slice(0, 27).map((s, i) => s ? i : -1).filter(i => i >= 0)) { await bot.clickWindow(s, 0, 1); await sleep(250); }
    bot.closeWindow(fin); await sleep(1200);
    const b = bot.blockAt(V(0.5, 65, 2.5));
    console.log('videe et refermee : bloc', b ? b.name : '?', '| affichages', Object.values(bot.entities).filter(e => e.name === 'item_display' && Math.abs(e.position.z - 2.5) < 0.6).length);
  } else console.log('plus de fenetre ouverte');
  bot.chat('/cf clear'); await sleep(2500);
  bot.chat('/cf test'); await sleep(800);
  bot.quit(); process.exit(0);
});
bot.on('error', e => { console.log('erreur', e.message); process.exit(1); });
