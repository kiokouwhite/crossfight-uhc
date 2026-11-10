const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let chat = []; bot.on('messagestr', m => chat.push(m));
const lastChat = re => { const b = chat.filter(x => re.test(x)).pop(); return b ? b.slice(0, 170) : '-'; };
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const displays = () => Object.values(bot.entities).filter(e => e.name === 'block_display');
const stats = () => { const ds = displays(); if (!ds.length) return 'aucun'; const me = bot.entity.position; let minx = 99, maxx = -99, miny = 99, maxy = -99, minz = 99, maxz = -99; for (const d of ds) { const r = d.position.minus(me); minx = Math.min(minx, r.x); maxx = Math.max(maxx, r.x); miny = Math.min(miny, r.y); maxy = Math.max(maxy, r.y); minz = Math.min(minz, r.z); maxz = Math.max(maxz, r.z); } return ds.length + ' displays, dx [' + minx.toFixed(1) + ',' + maxx.toFixed(1) + '] dy [' + miny.toFixed(1) + ',' + maxy.toFixed(1) + '] dz [' + minz.toFixed(1) + ',' + maxz.toFixed(1) + ']'; };
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change corrin'); await sleep(2000); log('hotbar', bot.inventory.slots.slice(36, 45).map((s, i) => i + ':' + (s ? s.name : '-')).join(' '));
  await bot.look(0, 0, true); await sleep(300);
  await click(2, 600); log('sang draconique (regard sud, yaw 0) ->', stats());
  await bot.look(Math.PI / 2, 0, true); await sleep(400); log('regard est ->', stats());
  bot.chat('/tp @s ~10 ~5 ~'); await sleep(600); log('apres tp ->', stats(), '| moi', bot.entity.position.floored().toString());
  await sleep(5000); log('apres 5 s (fin du vol) ->', stats());
  await sleep(2500); log('apres atterrissage ->', stats(), '| eau ?', lastChat(/eau/));
  bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
