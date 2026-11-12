const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
// Les affichages d'objets : pieces du pantin (collees au joueur) et arc au sol (ailleurs).
const items = {}; bot._client.on('entity_metadata', p => { const m = JSON.stringify(p.metadata).match(/crossfight:pose\/[a-z_]+|"itemId":\d+|minecraft:bow/); if (m) items[p.entityId] = /pose/.test(m[0]) ? 'piece' : 'objet'; });
const state = () => { const ds = Object.values(bot.entities).filter(e => e.name === 'item_display'); const near = ds.filter(e => e.position.distanceTo(bot.entity.position) < 0.05), far = ds.filter(e => e.position.distanceTo(bot.entity.position) >= 0.05);
  return 'pantin ' + near.length + ' affichages | au sol ' + far.length + (far.length ? ' (a ' + far[0].position.distanceTo(bot.entity.position).toFixed(1) + ' bloc, hauteur ' + (far[0].position.y - bot.entity.position.y).toFixed(2) + ')' : '') + ' | arme en main : ' + (bot.inventory.slots[36] ? bot.inventory.slots[36].name : 'vide'); };
bot._client.on('packet', (d, m) => { if (/sound_effect/.test(m.name)) { const x = JSON.stringify(d).match(/crossfight:[a-z0-9_.]+/); if (x) log('   son', x[0].slice(11)); } });
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change takumi'); await sleep(5000);
  t0 = Date.now(); log('--- Contre Rapproche |', state()); bot.setQuickBarSlot(1); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem();
  for (const t of [250, 250, 250, 400, 600, 2000]) { await sleep(t); bot.setQuickBarSlot(0); log(state()); }
  // Il s'eloigne de 6 blocs pendant que la lame dure.
  bot.chat('/tp @s ~6 ~ ~'); await sleep(1000); log('eloigne de 6 blocs |', state());
  await sleep(30200 - (Date.now() - t0)); log('--- 30 s ecoulees |', state());
  for (const t of [250, 250, 300, 400, 800]) { await sleep(t); log(state()); }
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
