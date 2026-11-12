const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const pieces = () => Object.values(bot.entities).filter(e => e.name === 'item_display').length;
let particles = {}; bot._client.on('packet', (d, m) => { if (m.name === 'world_particles') { const k = JSON.stringify(d.particle || d.particleId).match(/type":"?([a-z_]+)/); const n = k ? k[1] : '?'; particles[n] = (particles[n] || 0) + 1; }
  if (/sound_effect/.test(m.name)) { const x = JSON.stringify(d).match(/crossfight:[a-z0-9_.]+/); if (x) log('   son', x[0].slice(11)); } });
// Compte le verre le long d'une direction, a une hauteur donnee au-dessus des pieds.
const glass = (o, dx, dz, from, to, dy) => { let n = 0; const kinds = new Set(); for (let d = from; d <= to; d++) for (let h = dy[0]; h <= dy[1]; h++) { const b = bot.blockAt(new Vec3(Math.floor(o.x + dx * d), Math.floor(o.y) + h, Math.floor(o.z + dz * d))); if (b && /glass/.test(b.name)) { n++; kinds.add(b.name.replace('_stained_glass', '')); } } return n + ' blocs de verre' + (n ? ' (' + [...kinds].join(', ') + ')' : ''); };
const D = Math.SQRT1_2;
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  // --- Takumi, tir en diagonale
  bot.chat('/cf change takumi'); await sleep(5000);
  let o = bot.entity.position.clone(); await bot.lookAt(new Vec3(o.x + 20, o.y + 1.62, o.z + 20), true); await sleep(400);
  t0 = Date.now(); particles = {}; log('--- Rayon Demoniaque, regard en diagonale'); bot.setQuickBarSlot(8); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem();
  await sleep(1200); log('charge : pantin', pieces(), 'pieces | particules :', Object.entries(particles).map(([k, v]) => k + ' x' + v).join(', '));
  await sleep(3700); log('rayon : en diagonale', glass(o, D, D, 4, 28, [1, 1]), '| droit devant (axe Z)', glass(o, 0, 1, 8, 28, [1, 1]), '| droit devant (axe X)', glass(o, 1, 0, 8, 28, [1, 1]), '| pantin', pieces());
  await sleep(4500); log('apres : en diagonale', glass(o, D, D, 4, 28, [1, 1]), '| pantin', pieces());
  // --- Corrin, vague en diagonale
  bot.chat('/cf change corrin'); await sleep(3000);
  o = bot.entity.position.clone(); await bot.lookAt(new Vec3(o.x - 20, o.y + 1.62, o.z + 20), true); await sleep(400);
  t0 = Date.now(); log('--- Vagues Bleues, regard en diagonale (autre cote)'); bot.setQuickBarSlot(8); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem();
  await sleep(1500); log('vague a 1,5 s : en diagonale', glass(o, -D, D, 6, 22, [1, 5]), '| droit devant (axe Z)', glass(o, 0, 1, 10, 22, [1, 5]), '| droit devant (axe -X)', glass(o, -1, 0, 10, 22, [1, 5]));
  await sleep(4000); log('apres : en diagonale', glass(o, -D, D, 2, 44, [1, 5]));
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
