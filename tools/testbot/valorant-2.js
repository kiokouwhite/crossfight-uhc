const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let wantBuy = null;
bot.on('windowOpen', async w => {
  if (wantBuy) { const slot = w.slots.findIndex(i => i && i.customName && JSON.stringify(i.customName).includes(wantBuy)); if (slot >= 0) await bot.clickWindow(slot, 0, 0); wantBuy = null; }
  else bot.closeWindow(w);
});
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/[^A-Za-z0-9%:.,()/ !●]/g, '')));
const lastBar = re => { const b = bars.filter(x => re.test(x)).pop(); return b ? b.replace(/.*value:/, '').slice(0, 100) : '-'; };
let hurts = 0; bot.on('entityHurt', e => { if (e.name === 'zombie') hurts++; });
bot.on('message', m => { const s = m.toString(); if (/Achat|Reconnaissance|Erreur/.test(s)) log('CHAT', s); });
async function useSlot(i, ms) { bot.setQuickBarSlot(i); await sleep(150); bot.activateItem(); await sleep(ms); }
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000); await bot.look(0, 0, true);
  bot.chat('/cf change jett'); await sleep(2000); bot.chat('/cf credits 9000'); await sleep(300);
  bot.chat('/cf mannequin'); await sleep(800);
  wantBuy = 'Bucky'; await useSlot(4, 1200); await sleep(300);
  hurts = 0; await useSlot(5, 1000); log('Bucky (gerbe a 3 blocs) : coups', hurts);
  bot.chat('/cf mannequin clear'); await sleep(400);
  // SAGE (slot 0 = arme blanche)
  bot.chat('/cf change sage'); await sleep(2000);
  bot.chat('/cf mannequin allie'); await sleep(600); bot.chat('/damage @e[type=zombie,limit=1] 20'); await sleep(400);
  await useSlot(1, 3500); log('Orbe de Soin :', lastBar(/Orbe de Soin/));
  await useSlot(2, 1000); log('Orbe de Lenteur lance');
  await useSlot(3, 800); log('Orbe Barriere :', lastBar(/Barriere/));
  bot.chat('/cf mannequin clear'); await sleep(400);
  // SOVA
  bot.chat('/cf change sova'); await sleep(2000); bot.chat('/cf mannequin'); await sleep(600);
  hurts = 0; await useSlot(1, 1500); log('Electrofleche : coups', hurts, '|', lastBar(/Electro/));
  await useSlot(2, 1500); log('Reco :', lastBar(/Reconnaissance|revele/));
  await useSlot(3, 2500); log('Drone : allay present ?', Object.values(bot.entities).some(e => e.name === 'allay'));
  await sleep(8000); log('Drone apres 10 s : allay present ?', Object.values(bot.entities).some(e => e.name === 'allay'));
  bot.chat('/cf mannequin clear'); await sleep(400); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
