const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function ensureTest(b) { b.chat('/cf test'); await sleep(2000); if (!b.inventory.slots[43] || b.inventory.slots[43].name !== 'golden_apple') { b.chat('/cf test'); await sleep(2000); } }
let sounds = []; bot._client.on('packet', (d, m) => { if (/sound_effect/.test(m.name)) { const x = JSON.stringify(d).match(/crossfight:[a-z0-9_.]+/); if (x) sounds.push(x[0].slice(11)); } });
let bars = []; bot.on('actionBar', m => bars.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest(bot);
  bot.chat('/cf change yang'); await sleep(2500);
  for (const dmg of [4, 12]) {
    bot.chat('/cf heal'); await sleep(300); bot.chat('/damage @s ' + dmg + ' minecraft:mob_attack'); await sleep(700); sounds = []; bars = [];
    bot.setQuickBarSlot(1); await sleep(150); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(1200);
    console.log('coup de ' + dmg + ' puis Brulure ->', sounds.join(', ') || '(rien)', '|', bars.filter(x => /Brulure/.test(x)).join(' / '));
  }
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
