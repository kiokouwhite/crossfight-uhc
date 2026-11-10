const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let chat = []; bot.on('messagestr', m => chat.push(m));
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/"[a-z_]+":/g, '').replace(/[^A-Za-z0-9%:.,()/ !●➶\[\]+-]/g, '').replace(/compound|string|list|byte|,/g, ' ').replace(/\s+/g, ' ')));
const lastBar = re => { const b = bars.filter(x => re.test(x)).pop(); return b ? (b.match(re) || [''])[0] : '-'; };
const lastChat = re => { const b = chat.filter(x => re.test(x)).pop(); return b ? b.slice(0, 200) : '-'; };
const inv = () => bot.inventory.slots.slice(9, 45).map(s => s ? s.name : '-').filter(n => n !== '-').join(',');
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change kishi'); await sleep(2000); bot.chat('/cf mannequin'); await sleep(600);
  await click(8, 800); log('cones apres ult:', lastBar(/Cones [0-9]+/));
  await click(3, 800); log('lumiere -> cones:', lastBar(/Cones [0-9]+/), '| blade dmg lore?');
  bot.chat('/cf change hano'); await sleep(2000); await click(8, 800); log('hano ult:', lastChat(/Maitresse de la Connaissance :/)); log('inventaire:', inv());
  await sleep(300); bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
