const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let chat = []; bot.on('messagestr', m => chat.push(m));
const lastChat = re => { const b = chat.filter(x => re.test(x)).pop(); return b ? b.slice(0, 160) : '-'; };
async function scale() { chat = []; bot.chat('/attribute @s minecraft:scale base get'); await sleep(300); return lastChat(/scale|Base value/i); }
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  bot.chat('/cf change eren'); await sleep(2000); bot.chat('/tp @s 10 65 10'); await sleep(500);
  await click(8, 800); log('titan:', lastChat(/Titan Assaillant/), '| scale', await scale());
  bot.chat('/kill @s'); await sleep(1500);
  log('apres mort: pos', bot.entity.position.toString(), '| scale', await scale(), '| vie', bot.health);
  log('messages:', lastChat(/effondre/), '||', lastChat(/aucune vie/));
  await click(8, 600); log('ultime relancable (test):', lastChat(/utilise Titan Assaillant/) !== '-' ? 'oui' : 'non');
  await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
