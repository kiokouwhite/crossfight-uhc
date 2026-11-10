const mineflayer = require('mineflayer');
const V = require('vec3').Vec3;
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let lastWin = null; bot.on('windowOpen', w => { lastWin = w; });
let chat = []; bot.on('messagestr', m => chat.push(m));
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/"[a-z_]+":/g, '').replace(/[^A-Za-z0-9%:.,()/ !●➶\[\]-]/g, '').replace(/compound|string|list|byte|,/g, ' ').replace(/\s+/g, ' ')));
const lastBar = re => { const b = bars.filter(x => re.test(x)).pop(); return b ? (b.match(re) || [''])[0] : '-'; };
const lastChat = re => { const b = chat.filter(x => re.test(x)).pop(); return b ? b.slice(0, 160) : '-'; };
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
const hotbar = () => bot.inventory.slots.slice(36, 45).map(s => s ? s.name : '-').join(',');
async function zHealth() { chat = []; bot.chat('/data get entity @e[type=zombie,limit=1,sort=nearest] Health'); await sleep(300); return lastChat(/entity data/).replace(/.*data: /, ''); }
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function clickMenu(idx, ms) { for (let i = 0; i < 20 && !lastWin; i++) await sleep(100); if (!lastWin) { return false; } const w = lastWin; lastWin = null; const slots = []; for (let i = 0; i < 18; i++) if (w.slots[i]) slots.push(i); await bot.clickWindow(slots[idx] ?? slots[0], 0, 0); await sleep(ms); return true; }
function countBlocks(name, r) { let n = 0; const p = bot.entity.position; for (let x = -r; x <= r; x++) for (let z = -r; z <= r; z++) for (let y = -1; y <= 3; y++) { const b = bot.blockAt(new V(Math.floor(p.x) + x, Math.floor(p.y) + y, Math.floor(p.z) + z)); if (b && b.name === name) n++; } return n; }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  // ------- REMI
  bot.chat('/cf change remi'); await sleep(2000); bot.chat('/cf mannequin'); await sleep(600); const z = zomb();
  await bot.lookAt(z.position.offset(0, 1, 0), true); await sleep(200);
  log('REMI vie avant', await zHealth()); await click(2, 3200); log(' electricite (3 s) ->', await zHealth());
  await click(3, 400); log(' electrocution bar:', lastBar(/Electrocution\s*[0-9]+s/));
  await bot.look(0, 0, true); await sleep(200); const p0 = bot.entity.position.clone(); await click(1, 800); log(' dash:', p0.toString(), '->', bot.entity.position.toString());
  // ------- SERAPHINA
  bot.chat('/cf change seraphina'); await sleep(2000);
  bot.chat('/tp @s 5 65 5'); await sleep(1200); bot.chat('/tp @s 20 65 20'); await sleep(2500);
  await click(1, 600); log('SERAPHINA retour -> pos', bot.entity.position.toString(), '(attendu ~5,65,5)');
  const z2 = zomb(); bot.chat('/tp @s ' + (z2.position.x + 2) + ' 65 ' + z2.position.z); await sleep(500);
  await click(3, 600); log(' arret temporel:', lastBar(/Arret Temporel : [0-9] ennemi/));
  // ------- ARLO
  bot.chat('/cf change arlo'); await sleep(2000);
  await click(1, 600); log('ARLO barriere:', lastBar(/Barriere activee|Pas assez/));
  const z3 = zomb(); await bot.lookAt(z3.position.offset(0, 1, 0), true); await sleep(200);
  await click(3, 800); log(' enfermement:', lastBar(/adversaire enferme|Vise un/), '| verre:', countBlocks('glass', 6));
  await sleep(2000);
  bot.setControlState('sneak', true); await sleep(300); await click(3, 800); bot.setControlState('sneak', false); log(' liberation:', lastBar(/Adversaire relache|pas enferme|Deja/), '| verre:', countBlocks('glass', 6));
  await bot.lookAt(z3.position.offset(0, 1, 0), true); await sleep(200);
  const h0 = await zHealth(); await click(4, 800); log(' etouffement:', lastBar(/adversaire enferme/), '| verre teinte:', countBlocks('tinted_glass', 6), 'vie', h0); await sleep(3000); log(' vie apres 3 s', await zHealth());
  bot.setControlState('sneak', true); await sleep(300); await click(4, 600); bot.setControlState('sneak', false); log(' liberation 2:', lastBar(/Adversaire relache/), 'verre teinte:', countBlocks('tinted_glass', 6));
  // ------- JOHN
  bot.chat('/cf change john'); await sleep(2000); bot.chat('/cf aura soin'); await sleep(400);
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf mannequin adversaire yang'); await sleep(1000);
  const zy = zomb(); log('JOHN hotbar:', hotbar(), '| mannequin', zy ? zy.position.toString() : '-');
  bot.chat('/tp @s ' + (zy.position.x + 1.5) + ' 65 ' + zy.position.z); await sleep(500); await bot.lookAt(zy.position.offset(0, 1, 0), true);
  bot.setQuickBarSlot(0); await sleep(200); for (let i = 0; i < 3; i++) { bot.attack(zy); await sleep(600); }
  log(' absorption:', lastChat(/Absorption/));
  bot.chat('/cf choose'); await sleep(400); const ok = await clickMenu(0, 800); log(' choose ->', ok, '|', lastChat(/Pouvoir absorbe/), '| hotbar', hotbar());
  bot.chat('/cf choose'); await sleep(400); await clickMenu(0, 800); log(' retirer ->', lastBar(/Pouvoir retire[^|]{0,30}/), '| hotbar', hotbar());
  await sleep(300); bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf clear'); await sleep(500); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
