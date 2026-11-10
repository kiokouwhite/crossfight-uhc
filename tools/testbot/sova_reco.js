const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
bot.on('windowOpen', w => bot.closeWindow(w));
let bars = []; bot._client.on('action_bar', p => bars.push(JSON.stringify(p).replace(/[^A-Za-z0-9%:.,()/ !●]/g, '')));
const lastBar = re => { const b = bars.filter(x => re.test(x)).pop(); return b ? b.replace(/.*value:/, '').slice(0, 100) : '-'; };
let arrows = 0; bot.on('entitySpawn', e => { if (e.name === 'arrow') arrows++; });
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
const glowing = z => { try { return (z.metadata[0] & 0x40) !== 0; } catch (e) { return '?'; } };
const bolts = () => Object.values(bot.entities).filter(e => e.name === 'block_display').length;
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  bot.chat('/cf change sova'); await sleep(2000); bot.chat('/cf mannequin'); await sleep(800);
  log('hotbar:', bot.inventory.slots.slice(36, 45).map(s => s ? s.name : '-').join(','), '| slot9:', bot.inventory.slots[9] ? bot.inventory.slots[9].name : '-');
  const z = zomb(); log('mannequin:', z ? z.position.toString() : '-', 'glow:', glowing(z));
  await bot.lookAt(z.position.offset(3, 0, 0), true); await sleep(200);
  bot.setQuickBarSlot(2); await sleep(200);
  bot.activateItem(); await sleep(1300); bot.deactivateItem();
  await sleep(400); log('fleches spawn:', arrows, 'bar:', lastBar(/Reconnaissance|reco/i), 'bolt displays:', bolts());
  for (const t of [1.0, 2.5, 4.0, 5.5, 7.0]) { await sleep(1500); const zz = zomb(); log('t+' + t + 's glow:', zz ? glowing(zz) : '-', 'bolt:', bolts()); }
  bot.activateItem(); await sleep(1300); bot.deactivateItem(); await sleep(400); log('2e tir fleches spawn:', arrows, 'bar:', lastBar(/encore|Reconnaissance/i));
  bot.chat('/cf mannequin clear'); await sleep(400); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
