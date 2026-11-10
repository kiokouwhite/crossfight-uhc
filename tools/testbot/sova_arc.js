const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
bot.on('windowOpen', w => bot.closeWindow(w));
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/[^A-Za-z0-9%:.,()/ !●]/g, '')));
const lastBar = re => { const b = bars.filter(x => re.test(x)).pop(); return b ? b.replace(/.*value:/, '').slice(0, 100) : '-'; };
let arrows = 0; bot.on('entitySpawn', e => { if (e.name === 'arrow') arrows++; });
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
const pct = z => { try { return JSON.stringify(z.metadata[2]).match(/(\d+)\s*%/)?.[1] + '%'; } catch (e) { return '?'; } };
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  bot.chat('/cf change sova'); await sleep(2000); bot.chat('/cf mannequin'); await sleep(800);
  log('hotbar:', bot.inventory.slots.slice(36, 45).map(s => s ? s.name : '-').join(','), '| slot9:', bot.inventory.slots[9] ? bot.inventory.slots[9].name : '-');
  const z = zomb(); log('mannequin:', z ? z.position.toString() : '-');
  await bot.lookAt(z.position.offset(0, 1.2, 0), true); await sleep(200);
  bot.setQuickBarSlot(1); await sleep(200);
  log('bande l arc...'); bot.activateItem(); await sleep(1300); bot.deactivateItem();
  await sleep(300); log('fleches spawn:', arrows, 'bar:', lastBar(/Electro/));
  const p0 = z ? pct(z) : '-';
  await sleep(1800); const z2 = zomb(); log('mannequin apres explosion: vie', p0, '->', z2 ? pct(z2) : 'absent', z2 ? z2.position.toString() : '');
  // Deuxieme tir puis troisieme (doit etre refuse)
  bot.activateItem(); await sleep(1300); bot.deactivateItem(); await sleep(300); log('tir 2 fleches spawn:', arrows, 'bar:', lastBar(/Electro/));
  bot.activateItem(); await sleep(1300); bot.deactivateItem(); await sleep(300); log('tir 3 fleches spawn:', arrows, 'bar:', lastBar(/aucune|Electro/));
  await sleep(1500);
  bot.chat('/cf mannequin clear'); await sleep(400); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
