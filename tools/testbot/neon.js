const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
bot.on('windowOpen', w => bot.closeWindow(w));
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/[^A-Za-z0-9%:.,()/ !●]/g, '')));
const lastBar = re => { const b = bars.filter(x => re.test(x)).pop(); return b ? b.replace(/.*value:/, '').slice(0, 100) : '-'; };
const V = require('vec3').Vec3;
async function useSlot(i, ms) { bot.setQuickBarSlot(i); await sleep(150); bot.activateItem(); await sleep(ms); }
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000); await bot.look(0, 0, true);
  bot.chat('/cf change neon'); await sleep(2000); bot.chat('/cf mannequin'); await sleep(600);
  await useSlot(3, 800); log('Voie Rapide :', lastBar(/Voie/));
  let n = 0; for (let z = 0; z >= -52; z--) for (const x of [-2, 2]) { const b = bot.blockAt(new V(x, 66, z)); if (b && b.name === 'light_blue_stained_glass') n++; }
  log('colonnes de mur visibles (x=+-2, y=66, z 0..-52) :', n, '/ 2x50');
  await bot.look(0, 0.5, true); await sleep(200);
  await useSlot(2, 200); log('Eclair Relais lance');
  await sleep(3500); const z = Object.values(bot.entities).find(e => e.name === 'zombie'); log('mannequin apres 3.5 s :', z ? z.position.toString() : '-');
  bot.chat('/cf mannequin clear'); await sleep(400); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
