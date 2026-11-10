const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let chat = []; bot.on('messagestr', m => chat.push(m));
const lastChat = re => { const b = chat.filter(x => re.test(x)).pop(); return b ? b.slice(0, 160) : '-'; };
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
async function zHealth() { chat = []; bot.chat('/data get entity @e[type=zombie,limit=1,sort=nearest] Health'); await sleep(300); return lastChat(/entity data/).replace(/.*data: /, ''); }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change remi'); await sleep(2000); bot.chat('/cf mannequin'); await sleep(600); const z = zomb();
  // Mannequin a 3.5 blocs, on vise 30 degres a cote : l'eclair doit se diriger tout seul vers lui.
  await bot.lookAt(z.position.offset(2.0, 1, 0), true); await sleep(200);
  log('vie avant', await zHealth());
  bot.setQuickBarSlot(2); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem();
  await sleep(700); log('eclair (0.7 s) -> vie', await zHealth());
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
