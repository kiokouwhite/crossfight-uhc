const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let chat = []; bot.on('messagestr', m => chat.push(m));
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/"[a-z_]+":/g, '').replace(/[^A-Za-z0-9%:.,()/ !\[\]+-]/g, '').replace(/compound|string|list|byte|white|yellow|aqua|red|gray|darkgray|green|gold|lightpurple|darkred|extra|text|color|value|type|,/g, ' ').replace(/\s+/g, ' ')));
const raw = re => { const b = bars.filter(x => re.test(x)).pop(); return b ? b.slice(-120) : '-'; };
const lastChat = re => { const b = chat.filter(x => re.test(x)).pop(); return b ? b.slice(0, 170) : '-'; };
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
async function zData(k) { chat = []; bot.chat('/data get entity @e[type=zombie,limit=1,sort=nearest] ' + k); await sleep(300); return lastChat(/entity data/).replace(/.*data: /, '').slice(0, 120); }
let particles = {}; bot._client.on('world_particles', p => { const id = p.particle && p.particle.type !== undefined ? p.particle.type : JSON.stringify(p).slice(0, 20); particles[id] = (particles[id] || 0) + 1; });
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change marth'); await sleep(2000);
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf mannequin attaque'); await sleep(800);
  const z = zomb(); bot.chat('/tp @s ' + (z.position.x + 1.5) + ' ' + z.position.y + ' ' + z.position.z); await sleep(400); await bot.lookAt(z.position.offset(0, 1, 0), true);
  particles = {}; const hp = bot.health;
  await click(1, 300); log('pose -> particules en 0.3 s :', JSON.stringify(particles));
  // on attend que le mannequin frappe pendant la pose
  const zp = z.position.clone();
  for (let i = 0; i < 14; i++) { await sleep(100); if (/Contre reussi/.test(bars.join(' '))) break; }
  log('contre ->', raw(/Contre/), '| vie', hp.toFixed(1), '->', bot.health.toFixed(1), '| motion mannequin', await zData('Motion'), '| vie mannequin', await zData('Health'));
  await sleep(900); log('0.9 s plus tard : mannequin a', zomb() ? zomb().position.distanceTo(zp).toFixed(1) : '?', 'blocs de son point de depart, pos', zomb() ? zomb().position.floored().toString() : '?');
  log('particules :', JSON.stringify(particles));
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
