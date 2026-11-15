// Blake, Gambol Shroud en katana : plus de lancer en l'air au changement de mode ; au clic droit elle arme la lame
// des deux mains, la jette (la lame tournoie au bout de son ruban), fonce la ou elle se plante et la reprend.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
// Les affichages d'objets : pieces du pantin (modeles du pack) et lame lancee (une epee de fer ordinaire).
const kind = {}, billboard = {};
bot._client.on('entity_metadata', p => {
  const j = JSON.stringify(p.metadata);
  if (/crossfight:pose\//.test(j)) kind[p.entityId] = 'piece'; else if (/"itemId":\d+/.test(j) && !kind[p.entityId]) kind[p.entityId] = 'objet';
  for (const m of p.metadata) if (m.key === 15 && m.type === 'byte') billboard[p.entityId] = m.value;
});
let bars = []; bot._client.on('action_bar', d => bars.push(JSON.stringify(d.text).match(/"value":"([^"]{3,})"/g)?.map(x => x.slice(9, -1)).join(' ') || ''));
let dust = 0; bot._client.on('packet', (d, m) => { if (m.name === 'world_particles' && /dust/.test(JSON.stringify(d.particle || ''))) dust++; });
const snowballs = () => Object.values(bot.entities).filter(e => e.name === 'snowball').length;
const state = () => {
  const ds = Object.values(bot.entities).filter(e => e.name === 'item_display');
  const pieces = ds.filter(e => kind[e.id] === 'piece'), others = ds.filter(e => kind[e.id] !== 'piece');
  const blade = others.find(e => e.position.distanceTo(bot.entity.position) > 0.3) || others[0];
  return 'pantin ' + pieces.length + ' pieces' + (others.length - (blade ? 1 : 0) > 0 ? ' + objet en main' : '')
    + ' | lame ' + (blade ? 'a ' + blade.position.distanceTo(bot.entity.position).toFixed(1) + ' blocs (' + (billboard[blade.id] === 3 ? 'face au joueur' : billboard[blade.id] === 0 ? 'plantee' : 'affichage ' + billboard[blade.id]) + ')' : 'aucune')
    + ' | boules de neige visibles ' + snowballs() + ' | poussieres ' + dust
    + ' | position ' + bot.entity.position.x.toFixed(1) + ' ' + bot.entity.position.y.toFixed(1) + ' ' + bot.entity.position.z.toFixed(1);
};
const slotOf = (txt) => bot.inventory.slots.findIndex((i, n) => n >= 36 && n <= 44 && i && JSON.stringify(i.customName || i.name).includes(txt)) - 36;
async function use(txt, sneak) {
  const s = slotOf(txt); if (s < 0) { log('  objet introuvable :', txt); return false; }
  bot.setQuickBarSlot(s); await sleep(250);
  if (sneak) { bot.setControlState('sneak', true); await sleep(250); }
  bot.activateItem(); await sleep(60); bot.deactivateItem();
  if (sneak) { await sleep(150); bot.setControlState('sneak', false); }
  return true;
}
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change blake'); await sleep(5000);
  // Le choix du personnage ouvre un menu : tant qu'il reste ouvert, c'est lui que le serveur remet a jour, pas la barre du bot.
  if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(400); }
  const start = bot.entity.position.clone();
  // 1. Changement de mode : rien ne doit etre lance en l'air.
  t0 = Date.now(); log('--- accroupi + clic droit (couperet -> katana) |', state());
  await use('Gambol Shroud', true);
  for (const t of [200, 300, 500]) { await sleep(t); log(state()); }
  log('arme en main :', bot.inventory.slots[36 + bot.quickBarSlot] ? bot.inventory.slots[36 + bot.quickBarSlot].name : 'vide');
  // 2. Le lancer, vers le sol a une douzaine de blocs.
  await bot.look(bot.entity.yaw, -0.12, true); await sleep(300);
  dust = 0; t0 = Date.now(); log('--- clic droit (lancer) |', state());
  await use('Gambol Shroud');
  t0 = Date.now();
  for (let i = 0; i < 24; i++) { await sleep(50); log(state()); }
  await sleep(800); log('fin |', state(), '| deplacement', bot.entity.position.distanceTo(start).toFixed(1), 'blocs');
  // 3. Un lancer lointain (vers le haut) : l'elan du lancer est retombe quand la lame arrive ; elle le reprend pour foncer.
  const far = bot.entity.position.clone();
  await bot.look(bot.entity.yaw, 0.35, true); await sleep(300);
  dust = 0; await use('Gambol Shroud'); t0 = Date.now();
  let last = '';
  for (let i = 0; i < 60; i++) { await sleep(50); const now = state().replace(/ \| poussieres.*/, '').replace(/a [0-9.]+ blocs/, 'en vol'); if (now !== last) { log(state()); last = now; } }
  log('fin |', state(), '| deplacement', bot.entity.position.distanceTo(far).toFixed(1), 'blocs | arme en main :', bot.inventory.slots[36] ? bot.inventory.slots[36].name : 'vide');
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
