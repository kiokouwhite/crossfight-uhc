const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let chat = []; bot.on('messagestr', m => chat.push(m));
const lastChat = re => { const b = chat.filter(x => re.test(x)).pop(); return b ? b.slice(0, 160) : '-'; };
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
const stands = () => Object.values(bot.entities).filter(e => e.name === 'armor_stand').map(a => a.position.y.toFixed(2)).join(',');
async function zHealth() { chat = []; bot.chat('/data get entity @e[type=zombie,limit=1,sort=nearest] Health'); await sleep(300); return lastChat(/entity data/).replace(/.*data: /, ''); }
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change taka'); await sleep(2000);
  await bot.look(0, -0.6, true); await sleep(200); await click(1, 500); log('epee plantee : stand y =', stands(), '(sol 65)');
  bot.chat('/cf mannequin'); await sleep(600); const z = zomb(); const sy = []; log('mannequin', z.position.toString(), 'vie', await zHealth());
  for (let i = 0; i < 16; i++) { await sleep(100); sy.push(stands()); }
  log('hauteurs du stand pendant 1.6 s :', [...new Set(sy)].join(' '));
  await sleep(600); log('vie mannequin apres frappe :', await zHealth());
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
