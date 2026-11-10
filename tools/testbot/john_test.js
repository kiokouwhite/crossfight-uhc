const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let lastWin = null; bot.on('windowOpen', w => { lastWin = w; });
let chat = []; bot.on('messagestr', m => chat.push(m));
const lastChat = re => { const b = chat.filter(x => re.test(x)).pop(); return b ? b.slice(0, 160) : '-'; };
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
const hotbar = () => bot.inventory.slots.slice(36, 45).map(s => s ? s.name : '-').join(',');
async function zHealth() { chat = []; bot.chat('/data get entity @e[type=zombie,limit=1,sort=nearest] Health'); await sleep(300); return lastChat(/entity data/).replace(/.*data: /, ''); }
async function menu() { for (let i = 0; i < 20 && !lastWin; i++) await sleep(100); const w = lastWin; lastWin = null; return w; }
async function pick(w, idx, ms) { const slots = []; for (let i = 0; i < 54; i++) if (w.slots[i]) slots.push(i); await bot.clickWindow(slots[idx] ?? slots[0], 0, 0); await sleep(ms); }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change john'); await sleep(2000);
  bot.chat('/cf choose'); const w1 = await menu(); const n1 = w1 ? w1.slots.slice(0, 54).filter(s => s).length : 0; log('menu 1 : personnages proposes =', n1);
  await pick(w1, 0, 600); const w2 = await menu(); const n2 = w2 ? w2.slots.slice(0, 54).filter(s => s).length : 0; log('menu 2 : pouvoirs =', n2);
  await pick(w2, 0, 800); log('choix :', lastChat(/Pouvoir absorbe/), '| hotbar', hotbar());
  for (let k = 0; k < 3; k++) { bot.chat('/cf choose'); const a = await menu(); await pick(a, 1 + k, 500); const b = await menu(); if (b) await pick(b, 0, 600); }
  log('apres 4 absorptions :', lastChat(/Pouvoir absorbe/), '| hotbar', hotbar());
  // Dash de Remi : degats de la trainee
  bot.chat('/cf change remi'); await sleep(2000); bot.chat('/cf mannequin'); await sleep(600); const z = zomb();
  await bot.lookAt(z.position.offset(0, 1, 0), true); await sleep(200); log('mannequin vie', await zHealth());
  bot.setQuickBarSlot(1); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(1500); log('dash -> pos', bot.entity.position.toString(), 'vie mannequin', await zHealth());
  // Electrocution (aura) puis Dome
  bot.setQuickBarSlot(3); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(600);
  const z2 = zomb(); bot.chat('/tp @s ' + (z2.position.x + 12) + ' 65 ' + z2.position.z); await sleep(500); log('mannequin a 12 blocs, vie', await zHealth());
  bot.setQuickBarSlot(8); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(2500); log('dome (2.5 s) -> vie mannequin', await zHealth());
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
