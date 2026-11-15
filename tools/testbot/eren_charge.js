// Eren, Charge tridimensionnelle : le pantin fonce tete la premiere, les bras en arriere, une lame dans chaque main.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const kind = {};
bot._client.on('entity_metadata', p => { const j = JSON.stringify(p.metadata); if (/crossfight:pose\//.test(j)) kind[p.entityId] = 'piece'; else if (/"itemId":\d+/.test(j) && !kind[p.entityId]) kind[p.entityId] = 'objet'; });
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
const state = () => {
  const ds = Object.values(bot.entities).filter(e => e.name === 'item_display');
  const z = zomb();
  return 'pantin ' + ds.filter(e => kind[e.id] === 'piece').length + ' pieces, ' + ds.filter(e => kind[e.id] !== 'piece').length + ' lames'
    + ' | position ' + bot.entity.position.x.toFixed(1) + ' ' + bot.entity.position.y.toFixed(1) + ' ' + bot.entity.position.z.toFixed(1)
    + (z ? ' | a ' + z.position.distanceTo(bot.entity.position).toFixed(1) + ' blocs du mannequin' : '');
};
const slotOf = (txt) => bot.inventory.slots.findIndex((i, n) => n >= 36 && n <= 44 && i && JSON.stringify(i.customName || i.name).includes(txt)) - 36;
async function use(txt) { const s = slotOf(txt); if (s < 0) { log('  objet introuvable :', txt); return false; } bot.setQuickBarSlot(s); await sleep(250); bot.activateItem(); await sleep(60); bot.deactivateItem(); return true; }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change eren'); await sleep(5000);
  if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(400); }
  bot.chat('/cf mannequin'); await sleep(1000);
  const z = zomb(); if (!z) { log('pas de mannequin'); bot.quit(); process.exit(1); }
  for (const [dx, dy, label] of [[12, 0, 'a plat, 12 blocs'], [5, 0, 'a plat, 5 blocs'], [9, 6, 'de plus haut (la charge descend)']]) {
    bot.chat('/tp @s ' + (z.position.x + dx).toFixed(1) + ' ' + (z.position.y + dy).toFixed(1) + ' ' + z.position.z.toFixed(1)); await sleep(900);
    t0 = Date.now(); log('--- charge', label, '|', state());
    await use('Charge tridimensionnelle'); t0 = Date.now();
    let last = '';
    for (let i = 0; i < 20; i++) { await sleep(50); const now = state(); if (now !== last) { log(now); last = now; } }
    await sleep(600);
  }
  log('fin |', state(), '| arme en main :', bot.inventory.slots[36] ? bot.inventory.slots[36].name : 'vide');
  bot.chat('/cf mannequin clear'); await sleep(400); bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
