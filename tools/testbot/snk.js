const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let lastWin = null; bot.on('windowOpen', w => { lastWin = w; });
let chat = []; bot.on('messagestr', m => chat.push(m));
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/"[a-z_]+":/g, '').replace(/[^A-Za-z0-9%:.,()/ !●➶\[\]-]/g, '')));
const lastBar = re => { const b = bars.filter(x => re.test(x)).pop(); return b ? b.slice(0, 140) : '-'; };
const lastChat = re => { const b = chat.filter(x => re.test(x)).pop(); return b ? b.slice(0, 140) : '-'; };
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
const pct = z => { try { return JSON.stringify(z.metadata).match(/(\d+)\s*%/)?.[1] + '%'; } catch (e) { return '?'; } };
const hotbar = () => bot.inventory.slots.slice(36, 45).map(s => s ? s.name : '-').join(',');
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function clickMenu(idx, ms) { for (let i = 0; i < 20 && !lastWin; i++) await sleep(100); if (!lastWin) { log('  (pas de menu)'); return; } const w = lastWin; lastWin = null; const slots = w.slots.map((s, i) => s ? i : -1).filter(i => i >= 0); const target = slots[idx] ?? slots[0]; await bot.clickWindow(target, 0, 0); await sleep(ms); }
async function pos() { return bot.entity.position.toString(); }
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  // ---------------- EREN
  bot.chat('/cf change eren'); await sleep(2000); bot.chat('/cf mannequin'); await sleep(800);
  log('EREN hotbar:', hotbar());
  const z = zomb(); log(' mannequin', z ? z.position.toString() : '-');
  // grappin sur un bloc au loin, vers le haut
  bot.chat('/fill 0 65 -12 0 70 -12 stone'); await sleep(500);
  await bot.look(0, 0.5, true); await sleep(200); // regarde vers +z? on vise le mur: yaw vers -z
  await bot.lookAt(new (require('vec3').Vec3)(0.5, 69, -11.5), true); await sleep(200);
  const p0 = bot.entity.position.clone();
  await click(1, 1500); log(' grappin: depart', p0.toString(), '-> arrivee', await pos(), '| bar', lastBar(/Equipement/));
  await sleep(1500);
  await bot.lookAt(z.position.offset(0, 1, 0), true); await click(2, 800); log(' charge 3D -> ', await pos(), 'bar', lastBar(/Charge|Aucun/));
  await click(4, 500); log(' titans sans forme:', lastBar(/Titan|pouvoir/i));
  await click(8, 800); log(' ultime (titan):', lastChat(/Titan Assaillant/) );
  bot.chat('/cf titan tous'); await sleep(600); log(' /cf titan:', lastChat(/pouvoir/));
  await click(4, 300); log(' menu titans ouvert:', lastWin ? lastWin.slots.filter(s => s).length + ' options' : 'non');
  await clickMenu(0, 800); log(' colossal -> ', lastBar(/Colossal|vide|Titan/));
  await click(4, 300); await clickMenu(4, 800); log(' machoire -> mannequin vie', zomb() ? pct(zomb()) : '-');
  await click(4, 300); await clickMenu(6, 800); log(' marteau -> ', lastBar(/Marteau/), '| hotbar', hotbar());
  await click(8, 800); log(' regen (clic ult sous forme titan):', lastBar(/regenere|Regeneration/));
  await sleep(1000);
  // ---------------- MIKASA
  bot.chat('/cf change mikasa'); await sleep(2000); log('MIKASA hotbar:', hotbar());
  await click(2, 500); log(' assommage:', lastBar(/Assommage/));
  await click(3, 400); log(' cible menu:', lastWin ? 'ouvert' : 'non'); await clickMenu(0, 800); log(' cible choisie:', lastChat(/Sus a la Cible/));
  // ---------------- LIVAI
  bot.chat('/cf change livai'); await sleep(2000); log('LIVAI hotbar:', hotbar());
  const zz = zomb(); await bot.lookAt(zz.position.offset(0, 1, 0), true);
  const hp0 = bot.health; await click(2, 800); log(' charge destructrice: vie', hp0, '->', bot.health, 'pos', await pos(), 'mannequin', zomb() ? pct(zomb()) : '-');
  await click(3, 2500); log(' assaut:', lastBar(/Assaut/), 'vie', bot.health);
  bot.setQuickBarSlot(0); await sleep(100); bot.setControlState('jump', true); await sleep(250); bot.setControlState('jump', false); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(800); log(' toupie (saut + clic):', lastBar(/Toupie/) );
  await click(8, 500); await bot.look(Math.PI, 0, true); await sleep(200); const lp = await pos(); await click(8, 600); log(' ult pro 3D:', lp, '->', await pos(), lastBar(/PRO/));
  // ---------------- ARMIN
  bot.chat('/cf change armin'); await sleep(2000); log('ARMIN hotbar:', hotbar());
  await click(2, 500); log(' regroupement:', lastBar(/allie/));
  await click(3, 500); log(' strategie:', lastBar(/allie|Strategie/));
  const zt = zomb(); if (zt) { await bot.lookAt(zt.position.offset(0, 1, 0), true); bot.setQuickBarSlot(0); for (let i = 0; i < 6; i++) { bot.attack(zt); await sleep(600); } }
  log(' annulation apres 6 coups:', lastBar(/Annulation/));
  await click(8, 400); await clickMenu(0, 800); log(' abnegation:', lastChat(/Abnegation/));
  await sleep(500);
  bot.chat('/fill 0 65 -12 0 70 -12 air'); bot.chat('/cf mannequin clear'); await sleep(400); bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
