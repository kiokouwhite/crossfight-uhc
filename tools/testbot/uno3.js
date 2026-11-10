const mineflayer = require('mineflayer');
const V = require('vec3').Vec3;
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let chat = []; bot.on('messagestr', m => chat.push(m));
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/"[a-z_]+":/g, '').replace(/[^A-Za-z0-9%:.,()/ !●➶\[\]-]/g, '').replace(/compound|string|list|byte|,/g, ' ').replace(/\s+/g, ' ')));
const lastBar = re => { const b = bars.filter(x => re.test(x)).pop(); return b ? (b.match(re) || [''])[0] : '-'; };
const lastChat = re => { const b = chat.filter(x => re.test(x)).pop(); return b ? b.slice(0, 160) : '-'; };
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
async function zHealth() { chat = []; bot.chat('/data get entity @e[type=zombie,limit=1,sort=nearest] Health'); await sleep(300); return lastChat(/entity data/).replace(/.*data: /, ''); }
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
function countBlocks(name, r) { let n = 0; const p = bot.entity.position; for (let x = -r; x <= r; x++) for (let z = -r; z <= r; z++) for (let y = -1; y <= 3; y++) { const b = bot.blockAt(new V(Math.floor(p.x) + x, Math.floor(p.y) + y, Math.floor(p.z) + z)); if (b && b.name === name) n++; } return n; }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change arlo'); await sleep(2000); bot.chat('/cf mannequin'); await sleep(600);
  const z3 = zomb(); await bot.lookAt(z3.position.offset(0, 1, 0), true); await sleep(200);
  await click(3, 800); log('enfermement:', lastBar(/adversaire enferme|Vise un|Un mur/), '| verre:', countBlocks('glass', 6));
  await sleep(2000);
  bot.setControlState('sneak', true); await sleep(300); await bot.lookAt(z3.position.offset(0, 1, 0), true); await click(3, 800); bot.setControlState('sneak', false); log('liberation:', lastBar(/Adversaire relache|pas enferme|Deja/), '| verre:', countBlocks('glass', 6));
  await sleep(300); await bot.lookAt(z3.position.offset(0, 1, 0), true); await sleep(200);
  const h0 = await zHealth(); await click(4, 800); log('etouffement:', lastBar(/adversaire enferme|Deja|Un mur/), '| verre teinte:', countBlocks('tinted_glass', 6), 'vie', h0); await sleep(3000); log('vie apres 3 s', await zHealth());
  bot.setControlState('sneak', true); await sleep(300); await bot.lookAt(z3.position.offset(0, 1, 0), true); await click(4, 800); bot.setControlState('sneak', false); log('liberation 2:', lastBar(/Adversaire relache/), 'verre teinte:', countBlocks('tinted_glass', 6));
  await sleep(300); bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf clear'); await sleep(500); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
