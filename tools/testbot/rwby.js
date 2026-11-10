const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
bot.on('windowOpen', w => bot.closeWindow(w));
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/[^A-Za-z0-9%:.,()/ !♥●]/g, '')));
let hurts = 0; bot.on('entityHurt', e => { if (e.name === 'zombie') hurts++; });
bot.on('message', m => { const s = m.toString(); if (/Yeux|Brulure|Courroux|Instinct|invoque|Ultime en Duo|lancent|Erreur/.test(s)) log('CHAT', s); });
const hotbar = () => [...Array(9)].map((_, i) => { const it = bot.inventory.slots[36 + i]; return it ? it.name : '-'; }).join(' ');
const lastBar = (re) => { const b = bars.filter(x => re.test(x)).pop(); return b ? b.replace(/.*value:/, '').slice(0, 110) : '-'; };
async function useSlot(i, ms) { bot.setQuickBarSlot(i); await sleep(150); bot.activateItem(); await sleep(ms); }
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  await bot.look(0, 0, true);

  // ---- RUBY
  bot.chat('/cf change ruby'); await sleep(2000); log('RUBY hotbar :', hotbar());
  bot.chat('/cf mannequin'); await sleep(800);
  hurts = 0; await useSlot(0, 600); await useSlot(0, 600); await useSlot(0, 600); await useSlot(0, 600);
  log('Crescent Rose : 4 clics ->', hurts, 'coups,', lastBar(/Crescent/));
  bot.chat('/tp @s 0 65 0'); await sleep(500); hurts = 0; await useSlot(1, 1500); log('Petales rapide : coups', hurts, 'pos', bot.entity.position.toString());
  await useSlot(2, 2500); log('Petales lent apres 2.5 s :', lastBar(/Petales/));
  await useSlot(2, 500);
  bot.chat('/cf ult 100'); await sleep(500); await useSlot(8, 1500); log('Rush : vitesse', bot.entity.velocity.x.toFixed(2), bot.entity.velocity.z.toFixed(2));
  await sleep(2000); bot.chat('/cf mannequin clear'); await sleep(300);

  // ---- YANG
  bot.chat('/cf change yang'); await sleep(2000); log('YANG hotbar :', hotbar());
  bot.chat('/cf mannequin'); await sleep(800); hurts = 0; await useSlot(0, 800); log('Ember Celica : coups', hurts);
  bot.chat('/damage @s 6'); await sleep(600); await useSlot(1, 600); log('Brulure :', lastBar(/Brulure/));
  await useSlot(2, 2500); log('Incendie Interne 2.5 s : vie', bot.health.toFixed(1), lastBar(/Incendie/)); await useSlot(2, 300);
  bot.chat('/cf ult 100'); await sleep(500); await useSlot(8, 500); bot.chat('/damage @s 100'); await sleep(800); log('Courroux : vie apres coup mortel', bot.health.toFixed(1), 'mort ?', bot.health <= 0);
  bot.chat('/cf heal'); bot.chat('/cf mannequin clear'); await sleep(500);

  // ---- WEISS
  bot.chat('/cf change weiss'); await sleep(2000); log('WEISS hotbar :', hotbar());
  bot.chat('/cf mannequin'); await sleep(800);
  await useSlot(0, 400); await useSlot(0, 400); log('Dust apres 2 clics :', lastBar(/Dust/));
  hurts = 0; await useSlot(2, 1200); log('Glyphs T2 (Jaune = eclair) : coups', hurts, lastBar(/Glyphs/));
  await useSlot(0, 400); await useSlot(0, 400); await useSlot(0, 400); await useSlot(0, 400); log('Dust :', lastBar(/Dust/));
  await useSlot(1, 600); log('Glyphs T1 :', lastBar(/Glyphs/));
  bot.chat('/cf mannequin clear'); await sleep(500);

  // ---- BLAKE
  bot.chat('/cf change blake'); await sleep(2000); log('BLAKE hotbar :', hotbar());
  bot.setControlState('sneak', true); await sleep(300); await useSlot(0, 600); await useSlot(0, 600); bot.setControlState('sneak', false); await sleep(300);
  log('Gambol apres 2 changements :', hotbar().split(' ')[0], lastBar(/Pistolet|Katana|Couperet/));
  bot.chat('/cf mannequin'); await sleep(800); hurts = 0; await useSlot(0, 1200); await useSlot(0, 1200); log('Pistolet : coups', hurts, lastBar(/Pistolet/));
  await useSlot(1, 1500); const clones = Object.values(bot.entities).filter(e => e.name === 'zombie').length - 1; log('Ombre : clones', clones);
  await useSlot(2, 2500); log('Discretion :', lastBar(/Discretion/)); await useSlot(2, 300);
  bot.chat('/cf ult 100'); await sleep(500); await useSlot(8, 500); await useSlot(8, 800); log('Instinct :', lastBar(/Instinct/));
  bot.chat('/cf mannequin clear'); await sleep(500); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
