const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
let particles = {}, rooted = false;
bot._client.on('packet', (d, m) => {
  if (m.name === 'world_particles') { const k = JSON.stringify(d.particle || d.particleId).replace(/[{}"]/g, '').slice(0, 28); particles[k] = (particles[k] || 0) + (d.amount || d.particles || 1); }
  if (/attribute/.test(m.name) && d.entityId === bot.entity.id) for (const p of d.properties) if (/movement_speed/.test(JSON.stringify(p.key))) { const r = p.modifiers.some(x => /root_speed/.test(String(x.uuid || x.id))); if (r !== rooted) { rooted = r; log(r ? '   immobilise' : '   libere'); } }
  if (/sound_effect/.test(m.name)) { const x = JSON.stringify(d).match(/crossfight:[a-z0-9_.]+/); if (x) log('   son', x[0].slice(11)); }
});
const take = () => { const s = Object.entries(particles).map(([k, v]) => k + ' x' + v).join(', ') || '(aucune)'; particles = {}; return s; };
const pieces = () => Object.values(bot.entities).filter(e => e.name === 'item_display').length;
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change takumi'); await sleep(5000); take();
  bot.chat('/damage @s 12 minecraft:out_of_world'); await sleep(700); take();
  t0 = Date.now(); log('--- Vengeance | vie', bot.health.toFixed(0)); bot.setQuickBarSlot(2); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem();
  for (const t of [450, 500, 800, 1500, 2000, 1000]) { await sleep(t); log('pantin', pieces(), 'pieces | particules :', take()); }
  bot.chat('/cf heal'); await sleep(500); take();
  t0 = Date.now(); log('--- Furie | vie', bot.health.toFixed(0)); bot.setQuickBarSlot(3); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem();
  for (const t of [350, 500, 900, 1500]) { await sleep(t); log('pantin', pieces(), 'pieces | vie', bot.health.toFixed(0), '| particules :', take()); }
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
