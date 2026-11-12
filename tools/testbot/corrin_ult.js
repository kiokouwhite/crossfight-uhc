const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const pieces = () => Object.values(bot.entities).filter(e => e.name === 'item_display').length;
let rooted = false;
bot._client.on('packet', (d, m) => {
  if (/attribute/.test(m.name) && d.entityId === bot.entity.id) for (const p of d.properties) if (/movement_speed/.test(JSON.stringify(p.key))) { const r = p.modifiers.some(x => /root_speed/.test(String(x.uuid || x.id))); if (r !== rooted) { rooted = r; log(r ? '   immobilisee' : '   liberee'); } }
  if (/sound_effect/.test(m.name)) { const x = JSON.stringify(d).match(/crossfight:[a-z0-9_.]+/); if (x) log('   son', x[0].slice(11)); }
});
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change corrin'); await sleep(5000);
  const models = new Set(); bot._client.on('entity_metadata', p => { const m = JSON.stringify(p.metadata).match(/crossfight:pose\/[a-z_]+/g); if (m) m.forEach(x => models.add(x.slice(16))); });
  t0 = Date.now(); log('--- Ultime'); bot.setQuickBarSlot(8); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem();
  for (const t of [500, 500, 500, 500, 600]) { await sleep(t); log('pieces du modele', pieces(), '| objet tenu masque :', !!(bot.inventory.slots[44] && /hidden/.test(JSON.stringify(bot.inventory.slots[44].components || '')))); }
  log('modeles vus :', [...models].sort().join(', '));
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
