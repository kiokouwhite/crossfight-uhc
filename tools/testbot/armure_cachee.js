const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
let lines = []; bot.on('message', m => lines.push(m.toString()));
const ask = async cmd => { lines = []; bot.chat(cmd); await sleep(800); return lines.join(' | '); };
const piece = async slot => { const t = await ask('/data get entity @s equipment.' + slot); const id = (t.match(/id: "minecraft:([a-z_]+)"/) || [])[1] || 'rien'; const eq = (t.match(/"minecraft:equippable": \{([^}]*)\}/) || [])[1] || 'aucun composant'; return slot + ' = ' + id + ' [' + eq + ']'; };
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change byleth'); await sleep(4000);
  for (const s of ['head', 'chest', 'legs', 'feet']) console.log(await piece(s));
  console.log('points d armure :', (await ask('/attribute @s minecraft:armor get')).replace(/.* is /, ''));
  // Une citrouille sur la tete ne doit pas etre touchee.
  bot.chat('/item replace entity @s armor.head with carved_pumpkin'); await sleep(900); console.log('citrouille :', await piece('head'));
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
