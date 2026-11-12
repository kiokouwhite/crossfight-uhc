const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const n = name => Object.values(bot.entities).filter(e => e.name === name);
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change corrin'); await sleep(5000);
  t0 = Date.now(); log('--- Ultime'); bot.setQuickBarSlot(8); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem();
  for (const t of [300, 400, 500, 500, 500]) {
    await sleep(t); const w = n('block_display').filter(e => e.position.distanceTo(bot.entity.position) < 6);
    const ys = w.map(e => e.position.y - bot.entity.position.y);
    log('modele', n('item_display').length, 'pieces | ailes', w.length, 'morceaux' + (w.length ? ', de ' + Math.min(...ys).toFixed(1) + ' a ' + Math.max(...ys).toFixed(1) + ' blocs au-dessus du sol' : ''));
  }
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
