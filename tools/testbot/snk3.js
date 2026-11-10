const mineflayer = require('mineflayer');
const V = require('vec3').Vec3;
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let lastWin = null; bot.on('windowOpen', w => { lastWin = w; });
let chat = []; bot.on('messagestr', m => chat.push(m));
const lastChat = re => { const b = chat.filter(x => re.test(x)).pop(); return b ? b.slice(0, 160) : '-'; };
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function clickMenu(idx, ms) { for (let i = 0; i < 20 && !lastWin; i++) await sleep(100); if (!lastWin) { log('  (pas de menu)'); return; } lastWin = null; await bot.clickWindow(idx, 0, 0); await sleep(ms); }
async function srvPos() { chat = []; bot.chat('/data get entity @s Pos'); await sleep(300); return lastChat(/entity data/).replace(/.*following/, ''); }
async function zHealth() { chat = []; bot.chat('/data get entity @e[type=zombie,limit=1] Health'); await sleep(300); return lastChat(/Health|data/).replace(/.*following/, ''); }
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  bot.chat('/cf change eren'); await sleep(2000);
  bot.chat('/fill -2 65 -12 2 72 -12 stone'); await sleep(500);
  await bot.lookAt(new V(0.5, 69, -11.5), true); await sleep(300);
  log(' avant:', await srvPos());
  bot.setQuickBarSlot(1); await sleep(200); bot.activateItem(); await sleep(60); bot.deactivateItem();
  await sleep(600); log(' +0.6s:', await srvPos());
  await sleep(1000); log(' +1.9s:', await srvPos());
  await sleep(1500);
  bot.chat('/cf mannequin'); await sleep(600);
  log(' mannequin vie avant:', await zHealth());
  await click(8, 800); bot.chat('/cf titan machoire'); await sleep(400);
  const z = zomb(); await bot.lookAt(z.position.offset(0, 1, 0), true); await sleep(300);
  log(' eren titan pos', await srvPos(), 'mannequin', z.position.toString());
  await click(4, 300); await clickMenu(4, 800); log(' machoire -> vie:', await zHealth(), '| msg', lastChat(/Machoire|vide/));
  // frappe normale pour comparer
  bot.setQuickBarSlot(0); await sleep(200); bot.attack(z); await sleep(600); log(' coup d epee -> vie:', await zHealth());
  bot.chat('/fill -2 65 -12 2 72 -12 air'); bot.chat('/cf mannequin clear'); await sleep(400); bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
