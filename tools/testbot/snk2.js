const mineflayer = require('mineflayer');
const V = require('vec3').Vec3;
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let lastWin = null; bot.on('windowOpen', w => { lastWin = w; });
let spawns = []; bot.on('entitySpawn', e => spawns.push(e.name));
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/"[a-z_]+":/g, '').replace(/[^A-Za-z0-9%:.,()/ !●➶\[\]-]/g, '')));
const lastBar = re => { const b = bars.filter(x => re.test(x)).pop(); return b ? b.slice(0, 140) : '-'; };
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
const pct = z => { try { return JSON.stringify(z.metadata).match(/(\d+)\s*%/)?.[1] + '%'; } catch (e) { return '?'; } };
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function clickMenu(idx, ms) { for (let i = 0; i < 20 && !lastWin; i++) await sleep(100); if (!lastWin) { log('  (pas de menu)'); return; } const w = lastWin; lastWin = null; await bot.clickWindow(idx, 0, 0); await sleep(ms); }
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  bot.chat('/cf change eren'); await sleep(2000);
  bot.chat('/fill -2 65 -12 2 72 -12 stone'); await sleep(500);
  await bot.lookAt(new V(0.5, 69, -11.5), true); await sleep(300);
  spawns = [];
  bot.setQuickBarSlot(1); await sleep(200); bot.activateItem();
  for (let i = 0; i < 10; i++) { await sleep(200); log(' grappin t+' + (i * 0.2 + 0.2).toFixed(1), bot.entity.position.toString(), 'spawns', spawns.join(','), 'onground', bot.entity.onGround); }
  bot.deactivateItem(); await sleep(1000);
  log(' bar:', lastBar(/gaz|Equipement|Titan/));
  // Machoire face au mannequin
  bot.chat('/cf mannequin'); await sleep(600); const z = zomb();
  await click(8, 800); bot.chat('/cf titan machoire'); await sleep(400);
  await bot.lookAt(z.position.offset(0, 1, 0), true); await sleep(200);
  await click(4, 300); await clickMenu(0, 800); log(' machoire -> mannequin', pct(zomb()), lastBar(/Machoire/));
  // Toupie avec Livai a cote du mannequin
  bot.chat('/cf change livai'); await sleep(2000);
  bot.setQuickBarSlot(0); await sleep(200); bot.setControlState('jump', true); await sleep(150); bot.setControlState('jump', false); await sleep(100);
  log(' en l air ?', !bot.entity.onGround); bot.activateItem(); await sleep(60); bot.deactivateItem();
  await sleep(600); log(' toupie msg:', lastBar(/Toupie :|seulement/)); await sleep(5000); log(' mannequin apres toupie', pct(zomb()));
  // Armin annulation
  bot.chat('/cf change armin'); await sleep(2000); const zt = zomb(); bot.setQuickBarSlot(0);
  for (let i = 0; i < 6; i++) { await bot.lookAt(zt.position.offset(0, 1, 0), true); bot.attack(zt); await sleep(600); log('  coup', i + 1, lastBar(/Annulation/).slice(-40)); }
  bot.chat('/fill -2 65 -12 2 72 -12 air'); bot.chat('/cf mannequin clear'); await sleep(400); bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
