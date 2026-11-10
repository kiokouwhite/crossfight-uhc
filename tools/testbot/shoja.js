const mineflayer = require('mineflayer');
const V = require('vec3').Vec3;
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let chat = []; bot.on('messagestr', m => chat.push(m));
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/"[a-z_]+":/g, '').replace(/[^A-Za-z0-9%:.,()/ !●➶\[\]+-]/g, '').replace(/compound|string|list|byte|white|yellow|aqua|red|gray|darkgray|green|gold|lightpurple|,/g, ' ').replace(/\s+/g, ' ')));
const lastBar = re => { const b = bars.filter(x => re.test(x)).pop(); return b ? (b.match(re) || [''])[0] : '-'; };
const lastChat = re => { const b = chat.filter(x => re.test(x)).pop(); return b ? b.slice(0, 160) : '-'; };
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
const hotbar = () => bot.inventory.slots.slice(36, 45).map(s => s ? s.name : '-').join(',');
function countBlocks(re, r) { let n = 0; const p = bot.entity.position; for (let x = -r; x <= r; x++) for (let z = -r; z <= r; z++) for (let y = -2; y <= 5; y++) { const b = bot.blockAt(new V(Math.floor(p.x) + x, Math.floor(p.y) + y, Math.floor(p.z) + z)); if (b && re.test(b.name)) n++; } return n; }
async function zHealth() { chat = []; bot.chat('/data get entity @e[type=zombie,limit=1,sort=nearest] Health'); await sleep(300); return lastChat(/entity data/).replace(/.*data: /, ''); }
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
async function aim() { const z = zomb(); await bot.lookAt(z.position.offset(0, 1, 0), true); await sleep(200); return z; }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  // ---------- HIRO
  bot.chat('/cf change hiro'); await sleep(2000); bot.chat('/cf mannequin'); await sleep(600); log('HIRO hotbar:', hotbar());
  await click(0, 4500); log(' charge 4.5 s :', lastBar(/Grenade \[[0-9]+\]/), '| puissance (barre boss) attendue ~12');
  await click(0, 300); await click(2, 400); log(' grenade :', lastBar(/Grenade : 15|Grenade \[/));
  await aim(); await sleep(1500); log(' vie mannequin apres grenade', await zHealth());
  await click(3, 400); log(' consumation :', lastBar(/Consumation Ardente \[[0-9]+\] : [0-9]+ s|15 Puissance/));
  await click(1, 1500); await click(1, 300); log(' dash explosif :', lastBar(/Dash Explosif[^|]{0,20}/), 'pos', bot.entity.position.toString());
  await click(8, 3000); log(' detonation : blocs magma autour =', countBlocks(/magma_block/, 12));
  bot.chat('/cf clear'); await sleep(800); bot.chat('/cf mannequin'); await sleep(600);
  // ---------- PINKOU
  bot.chat('/cf change pinkou'); await sleep(2000); log('PINKOU hotbar:', hotbar());
  await aim(); const hp0 = bot.health; bot.chat('/damage @s 6'); await sleep(400); log(' vie avant frappe', bot.health.toFixed(1)); bot.setQuickBarSlot(0); await sleep(100); bot.attack(zomb()); await sleep(500); log(' rapiere curative -> vie', bot.health.toFixed(1));
  await click(1, 800); log(' cocon : blocs mousse/feuilles =', countBlocks(/moss_block|mangrove_leaves/, 4), '| pos', bot.entity.position.toString());
  await sleep(5200); await click(2, 500); log(' ronces :', lastChat(/Armure de Ronces/));
  await click(3, 800); log(' vigne : tapis =', countBlocks(/moss_carpet/, 4)); await sleep(2000); log(' mannequin poison ? vie', await zHealth());
  await click(8, 1200); log(' nature eternelle : blocs =', countBlocks(/moss|mangrove|sweet_berry/, 13));
  bot.chat('/cf clear'); await sleep(1500); bot.chat('/cf mannequin'); await sleep(600);
  // ---------- AIDA
  bot.chat('/cf change aida'); await sleep(2000); log('AIDA hotbar:', hotbar());
  await aim(); await click(0, 600); log(' jet :', await zHealth());
  await click(1, 400); log(' bulle :', lastBar(/Bulles ●+/));
  await click(2, 400); log(' clone :', lastBar(/Clone d.eau[^|]{0,8}/));
  bot.chat('/effect give @s minecraft:slowness 5 0'); await sleep(500); log(' eponge (slowness renvoye ?) :', lastBar(/Eponge Capacitive[^|]{0,20}/), '| effets', Object.keys(bot.entity.effects || {}).join(','));
  await click(8, 1500); log(' pluie :', lastBar(/Pluie bouillante[^|]{0,10}/), '| vie mannequin', await zHealth());
  // ---------- CRYSTAL
  bot.chat('/cf change crystal'); await sleep(2000); bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf mannequin'); await sleep(600); log('CRYSTAL hotbar:', hotbar());
  await aim(); const h1 = bot.health; await click(2, 500); log(' crystallization : vie', h1, '->', bot.health.toFixed(1), lastBar(/Aucun adversaire|Souffrance/));
  await click(3, 500); log(' surcharge : vie', bot.health.toFixed(1)); await click(1, 800); log(' patin : glace =', countBlocks(/packed_ice|blue_ice/, 4), 'vie', bot.health.toFixed(1));
  await click(0, 600); log(' lance adamante : hotbar', hotbar());
  await click(8, 600); log(' oeil pour oeil :', lastBar(/Oeil pour Oeil : [0-9]+/), 'vie', bot.health.toFixed(1));
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf clear'); await sleep(1500); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
