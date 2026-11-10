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
const ents = name => Object.values(bot.entities).filter(e => e.name === name).length;
async function zHealth() { chat = []; bot.chat('/data get entity @e[type=zombie,limit=1,sort=nearest] Health'); await sleep(300); return lastChat(/entity data/).replace(/.*data: /, ''); }
async function myMax() { chat = []; bot.chat('/attribute @s minecraft:max_health get'); await sleep(300); return lastChat(/max_health|Max Health|value/i).replace(/.*is /, ''); }
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function menuPick(idx, ms) { for (let i = 0; i < 20 && !lastWin; i++) await sleep(100); if (!lastWin) return false; const w = lastWin; lastWin = null; const slots = []; for (let i = 0; i < 54; i++) if (w.slots[i]) slots.push(i); await bot.clickWindow(slots[idx] ?? slots[0], 0, 0); await sleep(ms); return true; }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  // ---------- KISHI + mutations
  bot.chat('/cf change kishi'); await sleep(2000); log('KISHI hotbar:', hotbar());
  bot.chat('/cf delta coeurs'); await sleep(400); bot.chat('/cf delta regen'); await sleep(400); log(' mutations:', lastChat(/Mutation/), '| max vie:', await myMax());
  await click(8, 600); log(' ult cones:', lastBar(/Paradis blanc : \+10 cones permanents \([0-9]+\)/));
  bot.chat('/cf mannequin'); await sleep(600); await click(3, 600); log(' lumiere:', lastBar(/Aveugle|Aucun ennemi|Aucun cone/));
  await click(2, 400); const okAura = await menuPick(0, 600); log(' aura menu:', okAura, lastBar(/Aura Lumineuse : [a-z-]+/));
  // ---------- KURA
  bot.chat('/cf change kura'); await sleep(2000); bot.chat('/cf kills 8'); await sleep(400); log('KURA hotbar:', hotbar());
  const z = zomb(); await bot.lookAt(z.position.offset(0, 1, 0), true); await sleep(200);
  await click(1, 600); log(' assassin -> pos', bot.entity.position.toString(), '(mannequin', z.position.toString() + ')');
  await click(3, 400); await menuPick(0, 600); log(' meurtre parfait:', lastBar(/est cible/));
  log(' vie mannequin', await zHealth()); await click(8, 1200); log(' pluie de couteaux -> vie', await zHealth(), lastBar(/Pluie de Couteaux : [0-9]+ coeur/));
  // ---------- TAKA
  bot.chat('/cf change taka'); await sleep(2000); log('TAKA hotbar:', hotbar());
  await bot.lookAt(z.position.offset(0, 0, 0), true); await sleep(200);
  await click(1, 600); await click(2, 600); await click(3, 600); log(' invocations: armor_stand =', ents('armor_stand'), '| bar', lastBar(/Armes [0-9]+/));
  await sleep(2500); log(' vie mannequin apres 2.5 s pres des armes', await zHealth());
  bot.setQuickBarSlot(0); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(1200); log(' multi-arme:', lastBar(/Multi-Arme : [0-9]+ arme/), '| armor_stand =', ents('armor_stand'), 'vie', await zHealth());
  await click(8, 1000); log(' ult: armor_stand =', ents('armor_stand'));
  // ---------- HANO
  bot.chat('/cf change hano'); await sleep(2000); log('HANO hotbar:', hotbar(), '|', lastChat(/Tes tomes/));
  bot.chat('/cf tome knockback'); await sleep(300); bot.chat('/cf tome sweeping_edge'); await sleep(300); bot.chat('/cf tome unbreaking'); await sleep(500); log(' hotbar apres tomes:', hotbar());
  const zz = zomb(); await bot.lookAt(zz.position.offset(0, 1, 0), true); await sleep(200);
  const sweep = bot.inventory.slots.slice(36, 45).findIndex(s => s && s.name === 'diamond_sword'); await click(sweep, 600); log(' triple slash:', lastBar(/Triple Slash|dans le vide/), 'vie', await zHealth());
  const kb = bot.inventory.slots.slice(36, 45).findIndex(s => s && s.name === 'piston'); await click(kb, 800); log(' projection -> mannequin', zomb() ? zomb().position.toString() : '-');
  bot.chat('/cf tome arc multishot'); await sleep(400); bot.setQuickBarSlot(0); await sleep(150); let arrows = 0; const h = e => { if (e.name === 'arrow') arrows++; }; bot.on('entitySpawn', h); bot.activateItem(); await sleep(1200); bot.deactivateItem(); await sleep(600); bot.removeListener('entitySpawn', h); log(' arc multishot: fleches =', arrows);
  await click(8, 800); log(' ult tomes:', lastChat(/Maitresse/), '| hotbar', hotbar());
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf clear'); await sleep(500); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
