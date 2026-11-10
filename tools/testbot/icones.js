const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const log = (...a) => console.log(...a);
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const WEAPON = /sword|_axe$|^bow$|crossbow|trident|mace|pickaxe|shovel|_hoe$/;
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  const chars = ['marth','corrin','takumi','byleth','ruby','yang','weiss','blake','neon','sage','sova','jett','eren','mikasa','livai','armin','arlo','john','remi','seraphina','kishi','kura','taka','hano','gunvolt','acura','kirin','asimov','hiro','pinkou','aida','crystal','netsu','kaze','hannah','eirika','yuki','drifter','shoja'];
  for (const c of chars) {
    bot.chat('/cf change ' + c); await sleep(1200);
    const items = bot.inventory.slots.slice(36, 45).map((s, i) => s ? i + ':' + s.name : null).filter(Boolean);
    const bad = items.filter((x, i) => i > 0 && WEAPON.test(x.split(':')[1]) && !x.startsWith('0:'));
    log(c.padEnd(10), bad.length ? 'ARME DANS LA BARRE -> ' + bad.join(' ') : 'ok', '|', items.join(' '));
  }
  bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
