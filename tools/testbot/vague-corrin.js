const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const V = require('vec3').Vec3; const fs = require('fs'); const console_ = c => fs.appendFileSync('/home/user/testserver/console.in', c + '\n');
bot.on('windowOpen', w => { bot.closeWindow(w); });
function column(z) { const names = []; for (let y = 65; y <= 70; y++) { const b = bot.blockAt(new V(0, y, z)); names.push(b ? b.name.replace('_stained_glass', '').replace('air', '.') : '?'); } return 'z=' + z + ':' + names.join('/'); }
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000);
  bot.chat('/cf change takumi'); await sleep(1500);
  for (let i = 0; i < 9; i++) { const it = bot.inventory.slots[36 + i]; if (it) log('takumi slot', i, it.name); }
  bot.chat('/cf change corrin'); await sleep(2000);
  for (let i = 0; i < 9; i++) { const it = bot.inventory.slots[36 + i]; if (it) log('corrin slot', i, it.name); }
  const sword = bot.inventory.slots[36]; log('lore Yato :', JSON.stringify(sword && (sword.customLore || sword.nbt)).slice(0, 300));
  await bot.look(0, 0, true); await sleep(300);
  bot.chat('/cf mannequin 5'); await sleep(1000);
  bot.chat('/cf ult 100'); await sleep(800);
  bot.setQuickBarSlot(8); await sleep(300);
  log('ACTIVATE vague, moi', bot.entity.position.toString());
  bot.activateItem();
  for (let i = 1; i <= 24; i++) {
    await sleep(250);
    const zs = Object.values(bot.entities).filter(e => e.name === 'zombie'); const z = { position: { y: Math.max(...zs.map(e => e.position.y)) } }; const maxY = z.position.y;
    const cols = []; for (let zz = -1; zz >= -45; zz--) { const c = column(zz); if (!/:\.\/\.\/\.\/\.\/\.\/\.$/.test(c)) cols.push(c); }
    if (i === 4) for (let zz = 1; zz <= 8; zz++) for (let y = 65; y <= 69; y++) for (const m of ['blue_stained_glass','light_blue_stained_glass','white_stained_glass']) console_(`execute in minecraft:crossfight_test if block 0 ${y} ${zz} minecraft:${m} run say WAVE z${zz} y${y} ${m}`);
    log('t+' + (i * 0.25) + 's', 'mannequins en l air=' + zs.filter(e => e.position.y > 66).length + '/' + zs.length + ' maxY=' + maxY.toFixed(1), cols.length ? 'colonnes de vague: ' + cols.length + ' (z ' + cols[0].split(':')[0] + ' a ' + cols[cols.length-1].split(':')[0] + ')' : 'aucun bloc de vague');
  }
  bot.chat('/cf mannequin clear'); await sleep(500); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
