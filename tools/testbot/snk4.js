const mineflayer = require('mineflayer');
const V = require('vec3').Vec3;
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let chat = []; bot.on('messagestr', m => chat.push(m));
const lastChat = re => { const b = chat.filter(x => re.test(x)).pop(); return b ? b.slice(0, 160) : '-'; };
async function srvPos() { chat = []; bot.chat('/data get entity @s Pos'); await sleep(300); return lastChat(/entity data/).replace(/.*following/, ''); }
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  bot.chat('/cf change eren'); await sleep(2000);
  bot.chat('/fill -2 65 -12 2 72 -12 stone'); await sleep(500);
  await bot.lookAt(new V(0.5, 69, -11.5), true); await sleep(300);
  log(' avant:', await srvPos(), 'client', bot.entity.position.toString());
  bot.setQuickBarSlot(1); await sleep(200); bot.activateItem(); await sleep(60); bot.deactivateItem();
  await sleep(500); log(' +0.5s:', await srvPos(), 'client', bot.entity.position.toString());
  await sleep(700); log(' +1.5s:', await srvPos(), 'client', bot.entity.position.toString());
  await sleep(1500); log(' +3s:', await srvPos(), 'client', bot.entity.position.toString());
  bot.chat('/fill -2 65 -12 2 72 -12 air'); await sleep(300); bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
