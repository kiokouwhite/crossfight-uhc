const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
bot.on('windowOpen', w => bot.closeWindow(w));
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/[^A-Za-z0-9%:.,()/ !●➶]/g, '')));
const lastBar = re => { const b = bars.filter(x => re.test(x)).pop(); return b ? b.replace(/.*value:/, '').slice(0, 120) : '-'; };
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
const pct = z => { try { return JSON.stringify(z.metadata).match(/(\d+)\s*%/)?.[1] + '%'; } catch (e) { return '?'; } };
const glowing = z => { try { return (z.metadata[0] & 0x40) !== 0; } catch (e) { return '?'; } };
async function click(ms) { bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  bot.chat('/cf change sova'); await sleep(2000); bot.chat('/cf mannequin'); await sleep(800);
  const z = zomb(); log('mannequin:', z ? z.position.toString() : '-', 'vie', pct(z), 'glow', glowing(z));
  // Un mur de pierre entre le bot et le mannequin pour verifier la traversee.
  bot.chat('/fill -2 65 1 2 68 1 stone'); await sleep(600);
  await bot.lookAt(z.position.offset(0, 1.0, 0), true); await sleep(200);
  bot.setQuickBarSlot(8); await sleep(200);
  await click(400); log('ult lancee, bar:', lastBar(/FUREUR/));
  for (let i = 1; i <= 3; i++) { await click(900); const zz = zomb(); log('tir', i, 'vie', zz ? pct(zz) : '-', 'glow', zz ? glowing(zz) : '-', 'bar:', lastBar(/FUREUR|touche/)); }
  await click(500); log('4e clic (doit etre refuse / ultime normale) bar:', lastBar(/FUREUR|Ultime|touche/));
  await sleep(1500); log('apres fin: bar:', lastBar(/Ultime|FUREUR/));
  bot.chat('/fill -2 65 1 2 68 1 air'); await sleep(300);
  bot.chat('/cf mannequin clear'); await sleep(400); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
