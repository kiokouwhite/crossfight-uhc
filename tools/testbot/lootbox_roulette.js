// Roulette d'une Lootbox : le testeur (pack charge) pose une lootbox de tier 3 et l'ouvre. Pendant la roulette : les
// titres recus (la fenetre du pack change de couleur), les objets qui defilent dans la rangee du milieu, un clic sur la
// case du centre (refuse). A la fin : la couleur finale, le contenu, puis il vide la lootbox, qui disparait.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', async d => { bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 3 }); await sleep(100); bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 0 }); });
const V = (x, y, z) => bot.entity.position.clone().set(x, y, z);
const t0 = { v: 0 };
const titres = [], bandes = [];
const code = c => [...JSON.stringify(c)].filter(ch => ch.charCodeAt(0) >= 0xe000).map(ch => ch.charCodeAt(0).toString(16)).join(' ') || JSON.stringify(c).slice(0, 60);
bot._client.on('open_window', d => titres.push(`${Date.now() - t0.v} ms : ${code(d.windowTitle ?? d.title)}`));
bot._client.on('set_slot', d => { if (d.slot >= 9 && d.slot <= 17) bandes.push(d.slot); });
bot.once('spawn', async () => {
  await sleep(3000);
  bot.chat('/cf test'); await sleep(9000);
  bot.chat('/tp @s 0.5 65 4.5 180 20'); await sleep(1200);
  bot.chat('/cf lootbox 3'); await sleep(1200);
  const zone = Object.values(bot.entities).find(e => e.name === 'interaction' && Math.abs(e.position.z - 2.5) < 0.6);
  t0.v = Date.now();
  bot.activateEntity(zone);
  await sleep(1500);
  // Un clic sur la case du centre pendant la roulette : rien ne doit partir dans l'inventaire.
  const w = bot.currentWindow;
  if (w) { await bot.clickWindow(13, 0, 0).catch(() => {}); await sleep(300); }
  const pris = bot.inventory.items().filter(i => !['golden_apple', 'water_bucket'].includes(i.name)).map(i => i.name);
  console.log('clic pendant la roulette, objets pris :', pris.join(' ') || 'aucun', '| curseur :', bot.inventory.cursor ? bot.inventory.cursor.name : 'vide');
  await sleep(4500);
  console.log('titres recus :', titres.length);
  for (const t of titres) console.log('  ', t);
  console.log('mises a jour de la rangee du milieu :', bandes.length);
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
