// Sauts du HUD, pour chaque personnage et chaque objet de capacite : une case d'armure (ou la main gauche) qui portait
// une image du HUD et qui, en moins de 150 ms, passe a autre chose puis revient a cette image.
// Usage : node verif_hud_tous.js [id1,id2...]
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', async d => {
  bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 3 }); await sleep(100);
  bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 0 });
});
bot.on('death', () => { setTimeout(() => bot._client.write('client_command', { actionId: 0 }), 300); });
// L'image d'une case : la cle de son camera_overlay, sinon le nom de l'objet.
const look = it => {
  if (!it || !it.itemId) return 'vide';
  const name = (bot.registry.items[it.itemId] || {}).name || String(it.itemId);
  if (name !== 'paper') return name;
  const keys = (JSON.stringify(it).match(/crossfight:[a-z0-9_\/.]+/g) || []).filter(k => k !== 'crossfight:hidden');
  return keys.length ? 'img:' + keys[0].slice(11) : name;
};
const SLOTS = [5, 6, 7, 8, 45];
const hist = {}; for (const s of SLOTS) hist[s] = [];
let rec = false, found = [];
const seen = (slot, v) => {
  const h = hist[slot], now = Date.now();
  if (h.length && h[h.length - 1][1] === v) return;
  h.push([now, v]); if (h.length > 6) h.shift();
  // A (image) -> B -> A en moins de 150 ms : un saut.
  if (rec && h.length >= 3) {
    const [a, b, c] = h.slice(-3);
    if (a[1].startsWith('img:') && c[1] === a[1] && c[0] - b[0] <= 150) found.push(slot + ':' + a[1].slice(4) + '->' + b[1] + ' (' + (c[0] - b[0]) + ' ms)');
  }
};
bot._client.on('set_slot', d => { if (d.windowId === 0 && SLOTS.includes(d.slot)) seen(d.slot, look(d.item)); });
bot._client.on('window_items', d => { if (d.windowId === 0) for (const s of SLOTS) seen(s, look(d.items[s])); });
const only = process.argv[2] ? process.argv[2].split(',') : null;
bot.once('spawn', async () => {
  await sleep(5000);
  // Un passage en troisieme personne d'abord : c'est dans cet etat que les sauts se voyaient.
  bot.chat('/cf test third-person marth'); await sleep(6000); bot.chat('/cf test third-person'); await sleep(2000);
  let ids = (await bot.tabComplete('/cf change ')).map(x => typeof x === 'string' ? x : x.match);
  if (only) ids = ids.filter(i => only.includes(i));
  console.log(ids.length + ' personnages');
  const total = [];
  for (const id of ids) {
    bot.chat('/cf clear'); await sleep(600);
    bot.chat('/cf change ' + id); await sleep(3500);
    bot.chat('/tp @s 0.5 65 0.5 0 0'); await sleep(300);
    bot.chat('/cf mannequin 1 attaque'); await sleep(800);
    const z = Object.values(bot.entities).find(e => e !== bot.entity && e.position.distanceTo(bot.entity.position) < 12 && e.name !== 'item' && e.name !== 'item_display' && e.name !== 'text_display');
    const res = [];
    for (let k = 0; k < 9; k++) {
      const s = bot.inventory.slots[36 + k]; if (!s || s.name === 'golden_apple' || s.name === 'water_bucket') continue;
      if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(200); }
      bot.chat('/cf heal'); await sleep(200);
      if (z && z.isValid !== false) await bot.lookAt(z.position.offset(0, 1.5, 0), true);
      bot.setQuickBarSlot(k); await sleep(150);
      found = []; rec = true;
      bot.activateItem(); await sleep(60); bot.deactivateItem();
      await sleep(2600);
      if (bot.currentWindow) { const w = bot.currentWindow; const i = w.slots.findIndex((x, j) => x && j < w.inventoryStart); if (i >= 0) await bot.clickWindow(i, 0, 0); else bot.closeWindow(w); await sleep(1200); }
      // Un coup d'arme sur le mannequin aussi (capacites au contact).
      if (k === 0 && z) { try { bot.attack(z); } catch (e) { } await sleep(600); }
      rec = false;
      if (found.length) res.push(k + ' ' + s.name + ' : ' + [...new Set(found)].join(', '));
    }
    const line = id + (res.length ? ' | ' + res.join(' | ') : ' | aucun saut');
    console.log(line); total.push(line);
    bot.chat('/cf mannequin clear'); await sleep(300);
  }
  bot.chat('/cf test'); await sleep(1000);
  bot.quit(); process.exit(0);
});
bot.on('error', e => { console.log('erreur', e.message); process.exit(1); });
