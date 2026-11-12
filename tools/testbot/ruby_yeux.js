const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function ensureTest(b) { b.chat('/cf test'); await sleep(2000); if (!b.inventory.slots[43] || b.inventory.slots[43].name !== 'golden_apple') { b.chat('/cf test'); await sleep(2000); } }
let sounds = []; bot._client.on('packet', (d, m) => { if (/sound_effect/.test(m.name)) { const x = JSON.stringify(d).match(/crossfight:[a-z0-9_.]+/); if (x) sounds.push(x[0].slice(11)); } });
let msgs = []; bot.on('message', m => msgs.push(m.toString()));
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest(bot);
  bot.chat('/cf change ruby'); await sleep(3000);
  bot.chat('/effect clear @s'); bot.chat('/item replace entity @s armor.chest with air'); bot.chat('/item replace entity @s armor.legs with air'); bot.chat('/item replace entity @s armor.head with air'); bot.chat('/item replace entity @s armor.feet with air'); await sleep(600);
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf mannequin'); await sleep(800);
  sounds = []; msgs = [];
  bot.chat('/damage @s 7 minecraft:mob_attack'); await sleep(700); console.log('vie apres 1er coup', bot.health.toFixed(1), '|', sounds.join(', ') || '(rien)');
  bot.chat('/damage @s 6 minecraft:mob_attack'); await sleep(1200); console.log('vie apres 2e coup', bot.health.toFixed(1), '|', sounds.join(', ') || '(rien)', '|', msgs.filter(x => /Yeux/.test(x)).join(' '));
  bot.chat('/cf mannequin clear'); bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
