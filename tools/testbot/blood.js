const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let chat = []; bot.on('messagestr', m => chat.push(m));
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/"[a-z_]+":/g, '').replace(/[^A-Za-z0-9%:.,()/ !●➶\[\]+-]/g, '').replace(/compound|string|list|byte|white|yellow|aqua|red|gray|darkgray|green|gold|lightpurple|darkred|,/g, ' ').replace(/\s+/g, ' ')));
const lastBar = re => { const b = bars.filter(x => re.test(x)).pop(); return b ? (b.match(re) || [''])[0] : '-'; };
const lastChat = re => { const b = chat.filter(x => re.test(x)).pop(); return b ? b.slice(0, 160) : '-'; };
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
const hotbar = () => bot.inventory.slots.slice(36, 45).map(s => s ? s.name : '-').join(',');
async function zHealth() { chat = []; bot.chat('/data get entity @e[type=zombie,limit=1,sort=nearest] Health'); await sleep(300); return lastChat(/entity data/).replace(/.*data: /, ''); }
async function reach() { chat = []; bot.chat('/attribute @s minecraft:entity_interaction_range get'); await sleep(300); return lastChat(/is [0-9.]+/).replace(/.*is /, ''); }
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
async function aim() { const z = zomb(); await bot.lookAt(z.position.offset(0, 1, 0), true); await sleep(200); return z; }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  // ---------- EIRIKA
  bot.chat('/cf change eirika'); await sleep(2000); bot.chat('/cf mannequin'); await sleep(600); log('EIRIKA hotbar:', hotbar());
  const z = await aim(); bot.setQuickBarSlot(0); for (let i = 0; i < 5; i++) { bot.attack(z); await sleep(500); } log(' 5 coups -> marques :', lastBar(/Marques [0-9]\/5 max [0-9]/), '| vie', await zHealth());
  await click(0, 600); log(' bloody rain -> vie', await zHealth());
  await sleep(2500); log(' saignement 2.5 s -> vie', await zHealth());
  await click(2, 400); log(' absorption :', lastBar(/Absorption de sang : \+[0-9.]+ coeur/));
  await click(3, 400); log(' effroi :', lastBar(/Effroi sanglant : [0-9]/));
  await click(8, 600); log(' execution :', lastBar(/Execution Atroce : [0-9]+ adversaire[^|]{0,30}/));
  // ---------- HANNAH
  bot.chat('/cf change hannah'); await sleep(2000); bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf mannequin attaque'); await sleep(2500); log('HANNAH hotbar:', hotbar(), '| barre apres coups recus :', lastBar(/Resistance [0-9]+\/10/));
  await click(2, 500); log(' protection : absorption =', bot.entity.metadata ? '?' : '', lastBar(/Protection chevaleresque : \+[0-9]+/));
  await click(3, 500); log(' abnegation :', lastBar(/Abnegation Enrageante : [0-9]/));
  bot.chat('/cf mannequin attaque'); await sleep(2000); await click(8, 500); log(' rage :', lastBar(/Relachement de Rage : Force [0-9.]+/));
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf mannequin'); await sleep(600);
  // ---------- KAZE
  bot.chat('/cf change kaze'); await sleep(2000); log('KAZE hotbar:', hotbar(), '| power hit :', lastBar(/Power Hit [a-z]+/));
  const z2 = await aim(); await click(2, 600); log(' prise en traitre -> pos', bot.entity.position.toString(), '(mannequin', z2.position.toString() + ')');
  await click(3, 400); log(' gout du risque : portee =', await reach()); await sleep(5200); log(' apres 5 s : portee =', await reach());
  await aim(); await click(8, 800); log(' amour du danger :', lastBar(/Amour du danger : [0-9]+ coeur/));
  // ---------- NETSU
  bot.chat('/cf change netsu'); await sleep(2000); bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf mannequin'); await sleep(600); log('NETSU hotbar:', hotbar(), '| portee offensif =', await reach());
  await click(0, 400); log(' mode defensif : portee =', await reach(), lastBar(/Crawnatus : [A-Za-z]+/));
  await click(1, 600); log(' papillons :', lastBar(/Clone [0-9.]+ coeur/));
  bot.chat('/damage @s 9'); await sleep(600); log(' apres 9 degats : vie', bot.health.toFixed(1), '| bar', lastBar(/Papillons clic|Clone [0-9.]+/));
  await bot.look(0, 0, true); await click(1, 800); log(' reapparition -> pos', bot.entity.position.toString(), lastBar(/Netsu reapparait/));
  const p0 = bot.entity.position.clone(); await click(2, 600); log(' ailes :', lastBar(/Ailes ●+/), 'pos', p0.toString(), '->', bot.entity.position.toString());
  await aim(); await click(3, 1500); log(' jetee :', lastBar(/Jetee Eclatante : touche/), 'vie', await zHealth());
  await click(8, 800); log(' vision :', lastBar(/VISION [0-9]+s/), '| text_display =', Object.values(bot.entities).filter(e => e.name === 'text_display').length);
  await aim(); await click(8, 600); log(' tp sur ennemi -> pos', bot.entity.position.toString());
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf clear'); await sleep(800); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
