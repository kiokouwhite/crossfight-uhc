const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let lastWin = null; bot.on('windowOpen', w => { lastWin = w; });
let chat = []; bot.on('messagestr', m => chat.push(m));
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/"[a-z_]+":/g, '').replace(/[^A-Za-z0-9%:.,()/ !●➶\[\]+-]/g, '').replace(/compound|string|list|byte|white|yellow|aqua|red|gray|darkgray|green|,/g, ' ').replace(/\s+/g, ' ')));
const lastBar = re => { const b = bars.filter(x => re.test(x)).pop(); return b ? (b.match(re) || [''])[0] : '-'; };
const lastChat = re => { const b = chat.filter(x => re.test(x)).pop(); return b ? b.slice(0, 160) : '-'; };
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
const hotbar = () => bot.inventory.slots.slice(36, 45).map(s => s ? s.name : '-').join(',');
const ents = name => Object.values(bot.entities).filter(e => e.name === name).length;
async function zHealth() { chat = []; bot.chat('/data get entity @e[type=zombie,limit=1,sort=nearest] Health'); await sleep(300); return lastChat(/entity data/).replace(/.*data: /, ''); }
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function sneakClick(slot, ms) { bot.setControlState('sneak', true); await sleep(200); bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(200); bot.setControlState('sneak', false); await sleep(ms); }
async function menuPick(idx, ms) { for (let i = 0; i < 20 && !lastWin; i++) await sleep(100); if (!lastWin) return false; const w = lastWin; lastWin = null; const slots = []; for (let i = 0; i < 18; i++) if (w.slots[i]) slots.push(i); await bot.clickWindow(slots[idx] ?? slots[0], 0, 0); await sleep(ms); return true; }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
async function aim() { const z = zomb(); await bot.lookAt(z.position.offset(0, 1, 0), true); await sleep(200); return z; }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  // ---------- GUNVOLT
  bot.chat('/cf change gunvolt'); await sleep(2000); bot.chat('/cf mannequin'); await sleep(600); log('GUNVOLT hotbar:', hotbar());
  await aim(); await click(0, 700); log(' cerbere -> vie', await zHealth(), '|', lastChat(/Septimale/));
  await click(1, 2500); log(' champ (2.5 s) -> vie', await zHealth(), '| bar', lastBar(/Champ Electrique[^|]{0,20}/)); await click(1, 300);
  const p0 = bot.entity.position.clone(); await click(3, 700); log(' dash azur:', p0.toString(), '->', bot.entity.position.toString());
  await click(8, 2500); log(' astrasphere (2.5 s) -> vie', await zHealth());
  bot.chat('/cf mannequin attaque'); await sleep(2500); log(' telesquive apres coups :', lastBar(/Telesquive[^|]{0,12}/), 'vie', bot.health);
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf mannequin'); await sleep(600);
  // ---------- ACURA
  bot.chat('/cf change acura'); await sleep(2000); log('ACURA hotbar:', hotbar());
  await aim(); await click(1, 1200); log(' avenger dash:', lastBar(/Cible verrouillee[^|]{0,20}/), '| pod bar', lastBar(/Pod \[[^\]]+\]/));
  await sleep(2500); log(' tir auto du pod (2.5 s) -> vie', await zHealth());
  await sneakClick(3, 400); await menuPick(2, 600); log(' ex choisie:', lastBar(/Ex Weapon : [A-Za-z ]+/));
  await aim(); await click(3, 1500); log(' photon -> vie', await zHealth());
  await sneakClick(3, 400); await menuPick(3, 600); await click(3, 400); log(' orbital: item_display =', ents('item_display'));
  await click(8, 1500); log(' twin shredder (1.5 s) -> vie', await zHealth());
  // ---------- KIRIN
  bot.chat('/cf change kirin'); await sleep(2000); log('KIRIN hotbar:', hotbar());
  const z = await aim(); await click(1, 800); log(' talisman:', lastBar(/Cibles [0-9]+/), '|', lastChat(/Synergie/) === '-' ? '' : '');
  bot.chat('/tp @s ' + (z.position.x + 8) + ' 65 ' + z.position.z); await sleep(500);
  await click(0, 600); log(' yakumo -> pos', bot.entity.position.toString(), '(mannequin', z.position.toString() + ')');
  await click(2, 600); log(' arc chain:', lastBar(/Arc Chain : [0-9]+ cible[^|]{0,30}/));
  await aim(); await click(3, 800); log(' slash immobile:', lastBar(/Issen Slash|Tensho|Ressen/));
  await click(8, 1200); log(' ult:', lastBar(/92nd Hit Rite : [0-9]+/));
  // ---------- ASIMOV
  bot.chat('/cf change asimov'); await sleep(2000); bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf mannequin'); await sleep(600); log('ASIMOV hotbar:', hotbar());
  await aim(); log(' vie avant', await zHealth()); await click(0, 3800); log(' fusil (3.8 s) -> vie', await zHealth(), lastBar(/bout portant/));
  await click(3, 600); log(' tourelle: item_display =', ents('item_display')); await sleep(5200); log(' apres 5 s salve -> vie', await zHealth());
  await click(8, 2500); log(' voltaic chain lancee, vie', await zHealth());
  await aim(); const p1 = bot.entity.position.clone(); await click(1, 1000); log(' septimal dash:', p1.toString(), '->', bot.entity.position.toString());
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf clear'); await sleep(500); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
