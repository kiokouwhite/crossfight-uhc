const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
bot.on('windowOpen', w => bot.closeWindow(w));
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/[^A-Za-z0-9%:.,()/ !●]/g, '')));
const lastBar = re => { const b = bars.filter(x => re.test(x)).pop(); return b ? b.replace(/.*value:/, '').slice(0, 100) : '-'; };
const V = require('vec3').Vec3;
const CRYST = new Set(['cyan_stained_glass','light_blue_stained_glass','diamond_block','prismarine_bricks','white_stained_glass']);
function countCryst() { let n = 0; const p = bot.entity.position; for (let x = -8; x <= 8; x++) for (let z = -8; z <= 8; z++) for (let y = -2; y <= 1; y++) { const b = bot.blockAt(new V(Math.floor(p.x) + x, Math.floor(p.y) + y, Math.floor(p.z) + z)); if (b && CRYST.has(b.name)) n++; } return n; }
function displays() { return Object.values(bot.entities).filter(e => e.name === 'block_display').length; }
async function useSlot(i, ms) { bot.setQuickBarSlot(i); await sleep(150); bot.activateItem(); await sleep(ms); }
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  bot.chat('/cf change sage'); await sleep(2000); bot.chat('/cf mannequin'); await sleep(600);
  log('hotbar:', bot.inventory.slots.slice(36, 45).map(s => s ? s.name : '-').join(','));
  await bot.look(0, -0.4, true); await sleep(200);
  await useSlot(2, 300); log('orbe lancee, bar:', lastBar(/Lenteur|Orbe/));
  for (const t of [600, 1500, 3000]) { await sleep(t === 600 ? 600 : t - (t === 1500 ? 600 : 1500)); log('t+' + t + 'ms cristaux:', countCryst(), 'block_display:', displays()); }
  await sleep(3500); log('t+6.5s cristaux:', countCryst(), 'block_display:', displays());
  const z = Object.values(bot.entities).find(e => e.name === 'zombie'); log('mannequin:', z ? z.position.toString() : '-');
  bot.chat('/cf mannequin clear'); await sleep(400); bot.chat('/cf clear'); await sleep(400); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
