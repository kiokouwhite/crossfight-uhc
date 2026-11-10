const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let chat = []; bot.on('messagestr', m => chat.push(m));
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/"[a-z_]+":/g, '').replace(/[^A-Za-z0-9%:.,()/ !●➶\[\]+-]/g, '').replace(/compound|string|list|byte|white|yellow|aqua|red|gray|darkgray|green|gold|lightpurple|darkred|extra|text|color|value|type|,/g, ' ').replace(/\s+/g, ' ')));
const raw = re => { const b = bars.filter(x => re.test(x)).pop(); return b ? b.slice(-110) : '-'; };
const lastChat = re => { const b = chat.filter(x => re.test(x)).pop(); return b ? b.slice(0, 160) : '-'; };
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
async function zHealth() { chat = []; bot.chat('/data get entity @e[type=zombie,limit=1,sort=nearest] Health'); await sleep(300); return lastChat(/entity data/).replace(/.*data: /, ''); }
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change eirika'); await sleep(2000); bot.chat('/cf mannequin'); await sleep(600);
  const z = zomb(); bot.chat('/tp @s ' + (z.position.x + 1.5) + ' 65 ' + z.position.z); await sleep(400); await bot.lookAt(z.position.offset(0, 1, 0), true);
  bot.setQuickBarSlot(0); for (let i = 0; i < 8; i++) { bot.attack(z); await sleep(650); } log('8 coups -> bar', raw(/Marques/), '| mannequin', lastChat(/Marque de Sang/), '| vie', await zHealth());
  await sleep(2200); log('saignement -> vie', await zHealth());
  await click(0, 700); log('bloody rain ->', raw(/Bloody Rain|Marques/), 'vie', await zHealth());
  bot.chat('/cf change netsu'); await sleep(2000);
  await click(1, 600); log('papillons bar :', raw(/Clone|Papillons/));
  bot.chat('/damage @s 9'); await sleep(700); log('apres 9 dmg : vie', bot.health.toFixed(1), '| bar', raw(/Clone|Papillons/), '| flying?', bot.entity.position.y.toFixed(1));
  await bot.look(0, 0, true); await click(1, 800); log('reapparition -> pos', bot.entity.position.toString(), '|', raw(/reapparait|Papillons/));
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
