const mineflayer = require('mineflayer');
const V = require('vec3').Vec3;
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const zombies = () => Object.values(bot.entities).filter(e => e.name === 'zombie').map(z => z.position.y.toFixed(1)).join(',');
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  // Un toit de verre 40 blocs au-dessus, comme une bulle oubliee.
  bot.chat('/fill -15 105 -15 15 105 15 glass'); await sleep(800);
  bot.chat('/cf mannequin 2'); await sleep(900); log('avec toit : mannequins y =', zombies(), '(joueur y', bot.entity.position.y + ')');
  bot.chat('/cf mannequin adversaire kura'); await sleep(900); log('adversaire : y =', zombies());
  bot.chat('/cf mannequin clear'); await sleep(400);
  bot.chat('/cf clear'); await sleep(1500); const roof = bot.blockAt(new V(0, 105, 0)); log('apres /cf clear, bloc du toit :', roof ? roof.name : '?');
  bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
