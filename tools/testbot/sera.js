const mineflayer = require('mineflayer');
const V = require('vec3').Vec3;
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/"[a-z_]+":/g, '').replace(/[^A-Za-z0-9%:.,()/ !●➶\[\]-]/g, '').replace(/compound|string|list|byte|,/g, ' ').replace(/\s+/g, ' ')));
const lastBar = re => { const b = bars.filter(x => re.test(x)).pop(); return b ? (b.match(re) || [''])[0] : '-'; };
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
function countGrey(r) { let n = 0; const p = bot.entity.position; for (let x = -r; x <= r; x++) for (let z = -r; z <= r; z++) for (let y = -2; y <= 1; y++) { const b = bot.blockAt(new V(Math.floor(p.x) + x, Math.floor(p.y) + y, Math.floor(p.z) + z)); if (b && /gray_concrete|cyan_terracotta/.test(b.name)) n++; } return n; }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change seraphina'); await sleep(2000); bot.chat('/cf mannequin'); await sleep(600);
  const start = bot.entity.position.clone(); await bot.look(0, 0, true); bot.setControlState('forward', true); await sleep(2500); bot.setControlState('forward', false); await sleep(300);
  const far = bot.entity.position.clone(); log('depart', start.toString(), '-> marche', far.toString());
  bot.setQuickBarSlot(1); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem();
  for (let i = 1; i <= 5; i++) { await sleep(150); log(' rembobinage t+' + (i * 0.15).toFixed(2) + 's :', bot.entity.position.toString()); }
  await sleep(1000); log(' final :', bot.entity.position.toString(), '| dist au depart', bot.entity.position.distanceTo(start).toFixed(2));
  await click(3, 500); log('arret temporel:', lastBar(/Arret Temporel : [0-9] ennemi/), '| blocs gris autour:', countGrey(11));
  await sleep(3500); log(' apres 3.5 s blocs gris:', countGrey(11));
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf clear'); await sleep(500); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
