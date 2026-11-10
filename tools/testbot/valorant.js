const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let wantBuy = null;
bot.on('windowOpen', async w => {
  if (wantBuy) { const slot = w.slots.findIndex(i => i && i.customName && JSON.stringify(i.customName).includes(wantBuy)); log('menu boutique, achat slot', slot, 'pour', wantBuy); if (slot >= 0) await bot.clickWindow(slot, 0, 0); wantBuy = null; }
  else bot.closeWindow(w);
});
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/[^A-Za-z0-9%:.,()/ !●]/g, '')));
const lastBar = re => { const b = bars.filter(x => re.test(x)).pop(); return b ? b.replace(/.*value:/, '').slice(0, 100) : '-'; };
let hurts = 0; bot.on('entityHurt', e => { if (e.name === 'zombie') hurts++; });
bot.on('message', m => { const s = m.toString(); if (/Achat|credits|palier|Surmultipliee|Tempete|Resurrection|ressuscite|Erreur/.test(s)) log('CHAT', s); });
const hotbar = () => [...Array(9)].map((_, i) => { const it = bot.inventory.slots[36 + i]; return it ? it.name : '-'; }).join(' ');
async function useSlot(i, ms) { bot.setQuickBarSlot(i); await sleep(150); bot.activateItem(); await sleep(ms); }
async function buy(name, slotShop) { wantBuy = name; await useSlot(slotShop, 1200); }
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000); await bot.look(0, 0, true);
  // ---- JETT + boutique
  bot.chat('/cf change jett'); await sleep(2000); log('JETT hotbar :', hotbar());
  bot.chat('/cf mannequin'); await sleep(800);
  await useSlot(4, 600); log('credits :', lastBar(/cr/));
  await buy('Classic', 4); await sleep(500); log('apres achat Classic :', hotbar());
  const gunSlot = hotbar().split(' ').findIndex(n => n.endsWith('_hoe'));
  hurts = 0; await useSlot(gunSlot, 400); await useSlot(gunSlot, 2200); await useSlot(gunSlot, 400); log('Classic (2 balles puis rechargement) : coups', hurts, '|', lastBar(/Classic/));
  bot.chat('/cf credits 9000'); await sleep(300);
  await buy('Operator', 4); await sleep(500); hurts = 0; await useSlot(gunSlot, 800); log('Operator : coups', hurts, '|', lastBar(/Operator|tete/));
  await buy('Bucky', 4); await sleep(500); hurts = 0; await useSlot(gunSlot, 800); log('Bucky (gerbe) : coups', hurts);
  await buy('Bulldog', 4); await sleep(500); hurts = 0; await useSlot(gunSlot, 1200); log('Bulldog (rafale de 3) : coups', hurts, '|', lastBar(/Bulldog/));
  await buy('Odin', 4); await sleep(500); hurts = 0; await useSlot(gunSlot, 11000); log('Odin (rafale complete 20 balles) : coups', hurts, '|', lastBar(/Odin/));
  bot.chat('/cf ult 100'); await sleep(400); await useSlot(8, 500); hurts = 0; await useSlot(8, 400); await useSlot(8, 400); log('Tempete de Lames : 2 lancers, coups', hurts, '|', lastBar(/Couteaux/));
  bot.chat('/cf mannequin clear'); await sleep(400);
  // ---- NEON
  bot.chat('/cf change neon'); await sleep(2000); log('NEON hotbar :', hotbar());
  bot.chat('/cf mannequin'); await sleep(600);
  await useSlot(2, 800); log('Voie Rapide :', lastBar(/Voie/));
  await useSlot(1, 3200); log('Eclair Relais lance (explosion a 2 s)');
  bot.chat('/cf ult 100'); await sleep(400); await useSlot(8, 1000); log('Surmultipliee hotbar :', hotbar());
  bot.chat('/cf mannequin clear'); await sleep(400);
  // ---- SAGE
  bot.chat('/cf change sage'); await sleep(2000); log('SAGE hotbar :', hotbar());
  bot.chat('/cf mannequin allie'); await sleep(600);
  bot.chat('/damage @e[type=zombie,limit=1] 20'); await sleep(400);
  await useSlot(0, 3500); log('Orbe de Soin :', lastBar(/Soin/));
  await useSlot(2, 800); log('Orbe Barriere :', lastBar(/Barriere/));
  bot.chat('/cf mannequin clear'); await sleep(400);
  // ---- SOVA
  bot.chat('/cf change sova'); await sleep(2000); log('SOVA hotbar :', hotbar());
  bot.chat('/cf mannequin'); await sleep(600);
  hurts = 0; await useSlot(0, 1200); log('Electrofleche : coups', hurts, '|', lastBar(/Electro/));
  await useSlot(1, 1200); log('Reco :', lastBar(/Reconnaissance/));
  await useSlot(2, 2500); log('Drone : allay present ?', Object.values(bot.entities).some(e => e.name === 'allay'));
  bot.chat('/cf ult 100'); await sleep(400); hurts = 0; await useSlot(8, 2500); log('Fureur du Chasseur : coups', hurts);
  bot.chat('/cf mannequin clear'); await sleep(400); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
