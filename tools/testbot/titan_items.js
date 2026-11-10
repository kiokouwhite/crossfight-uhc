const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let chat = []; bot.on('messagestr', m => chat.push(m));
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/"[a-z_]+":/g, '').replace(/[^A-Za-z0-9%:.,()/ !●➶\[\]-]/g, '').replace(/compound|string|list|byte|,/g, ' ').replace(/\s+/g, ' ')));
const lastChat = re => { const b = chat.filter(x => re.test(x)).pop(); return b ? b.slice(0, 160) : '-'; };
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
const hotbar = () => bot.inventory.slots.slice(36, 45).map(s => s ? s.name : '-').join(',');
const invRow = () => bot.inventory.slots.slice(9, 18).map(s => s ? s.name : '-').join(',');
async function zHealth() { chat = []; bot.chat('/data get entity @e[type=zombie,limit=1,sort=nearest] Health'); await sleep(300); return lastChat(/entity data/).replace(/.*data: /, ''); }
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500);
  bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); }
  bot.chat('/cf change eren'); await sleep(2000); bot.chat('/cf titan tous'); await sleep(500);
  log('avant ult hotbar:', hotbar());
  await click(8, 800); log('apres ult hotbar:', hotbar(), '| inv:', invRow());
  bot.chat('/cf mannequin'); await sleep(600); const z = zomb(); await bot.lookAt(z.position.offset(0, 1, 0), true); await sleep(200);
  log('mannequin vie', await zHealth());
  const machoire = bot.inventory.slots.slice(36, 45).findIndex(s => s && s.name === 'bone'); await click(machoire, 800); log('machoire (slot ' + machoire + ') -> vie', await zHealth());
  const cuir = bot.inventory.slots.slice(36, 45).findIndex(s => s && s.name === 'iron_chestplate'); bars = []; await click(cuir, 1200); log('cuirasse (slot ' + cuir + ') bar:', (bars.filter(b => /Cuirasse/.test(b)).pop() || '-').match(/Cuirasse.{0,30}/)?.[0]);
  bot.chat('/kill @s'); await sleep(1500); log('apres mort hotbar:', hotbar(), '| inv:', invRow());
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
