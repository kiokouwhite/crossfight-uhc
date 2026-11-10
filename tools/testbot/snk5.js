const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let chat = []; bot.on('messagestr', m => chat.push(m));
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/"[a-z_]+":/g, '').replace(/[^A-Za-z0-9%:.,()/ !●➶\[\]-]/g, '')));
const lastBar = re => { const b = bars.filter(x => re.test(x)).pop(); return b ? (b.match(re) || [''])[0] : '-'; };
const lastChat = re => { const b = chat.filter(x => re.test(x)).pop(); return b ? b.slice(0, 160) : '-'; };
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
async function zHealth() { chat = []; bot.chat('/data get entity @e[type=zombie,limit=1,sort=nearest] Health'); await sleep(300); return lastChat(/entity data/).replace(/.*data: /, ''); }
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  bot.chat('/cf change livai'); await sleep(2000); bot.chat('/cf mannequin'); await sleep(800);
  const z = zomb(); bot.chat('/tp @s ' + (z.position.x + 1.5) + ' 65 ' + z.position.z); await sleep(500);
  log(' mannequin vie avant toupie:', await zHealth());
  bot.setQuickBarSlot(0); await sleep(200); bot.setControlState('jump', true); await sleep(350); bot.setControlState('jump', false);
  bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(400); log(' toupie msg:', lastBar(/seulement dans les airs/), 'enl air', !bot.entity.onGround, 'y', bot.entity.position.y.toFixed(2));
  await sleep(5200); log(' mannequin vie apres toupie:', await zHealth());
  bot.chat('/cf change armin'); await sleep(2000); const zt = zomb(); bot.setQuickBarSlot(0);
  for (let i = 0; i < 6; i++) { await bot.lookAt(zt.position.offset(0, 1, 0), true); bot.attack(zt); await sleep(700); log('  coup', i + 1, lastBar(/Annulation.{0,160}[0-9]\/5|annulees/)); }
  bot.chat('/cf mannequin clear'); await sleep(400); bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
