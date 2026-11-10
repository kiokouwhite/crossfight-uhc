const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let chat = []; bot.on('messagestr', m => chat.push(m));
const zombies = () => Object.values(bot.entities).filter(e => e.name === 'zombie').length;
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  for (const cmd of ['/cf mannequin', '/cf mannequin 3', '/cf mannequin attaque', '/cf mannequin allie', '/cf mannequin adversaire hano', '/cf mannequin adversaire arlo', '/cf mannequin adversaire marth']) {
    chat = []; bot.chat(cmd); await sleep(900); log(cmd, '-> zombies', zombies(), '|', chat.filter(m => /Crossfight/.test(m)).map(m => m.slice(0, 90)).join(' / '));
  }
  bot.chat('/cf mannequin clear'); await sleep(500); log('clear -> zombies', zombies());
  bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
