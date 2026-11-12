const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const disp = () => Object.values(bot.entities).filter(e => e.name === 'item_display');
const yaws = () => [...new Set(disp().map(e => Math.round(((-e.yaw * 180 / Math.PI) % 360 + 360) % 360)))].join('/');
let held = new Set(); bot._client.on('entity_metadata', p => { const j = JSON.stringify(p.metadata); const m = j.match(/crossfight:pose\/[a-z_]+/); if (!m && /itemId/.test(j) && bot.entities[p.entityId] && bot.entities[p.entityId].name === 'item_display') held.add((j.match(/"itemId":(\d+)/) || [])[1]); });
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change byleth'); await sleep(4000);
  const o = bot.entity.position.clone(); await bot.lookAt(new Vec3(o.x, o.y + 1.62, o.z + 10), true); await sleep(400);
  const swordId = bot.inventory.slots[36] ? bot.inventory.slots[36].type : '?';
  t0 = Date.now(); log('--- Dechainement d Epine (regard vers le sud) | epee en main : objet', swordId); bot.setQuickBarSlot(3); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem();
  await sleep(250); log('preparation : pantin', disp().length, 'pieces | orientation des pieces', yaws());
  // Le joueur tourne la tete pendant le coup : le pantin ne doit pas tourner avec lui.
  await bot.lookAt(new Vec3(o.x + 10, o.y + 1.62, o.z), true); await sleep(350); log('le joueur regarde ailleurs : pantin', disp().length, '| orientation des pieces', yaws());
  await sleep(500); log('balayage : pantin', disp().length, '| avance de', bot.entity.position.distanceTo(o).toFixed(1), 'bloc');
  await sleep(600); log('fin : pantin', disp().length); await sleep(500); log('apres : pantin', disp().length, '| objet tenu par le pantin :', [...held].join(',') || '?');
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
