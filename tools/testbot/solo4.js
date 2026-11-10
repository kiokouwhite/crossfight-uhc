const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const bot2 = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester2', version: '1.21.10' });
let chat = []; bot.on('messagestr', m => chat.push(m));
let chat2 = []; bot2.on('messagestr', m => chat2.push(m));
const lastChat = (re, c = chat) => { const b = c.filter(x => re.test(x)).pop(); return b ? b.slice(0, 200) : '-'; };
async function myData(b, k) { const c = b === bot ? chat : chat2; c.length = 0; b.chat('/data get entity @s ' + k); await sleep(300); return lastChat(/entity data/, c).replace(/.*data: /, '').slice(0, 220); }
async function click(b, slot, ms) { b.setQuickBarSlot(slot); await sleep(150); b.activateItem(); await sleep(60); b.deactivateItem(); await sleep(ms); }
async function ensureTest(b) { b.chat('/cf test'); await sleep(2000); if (!b.inventory.slots[43] || b.inventory.slots[43].name !== 'golden_apple') { b.chat('/cf test'); await sleep(2000); } }
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
let ready = 0;
const go = async () => {
  if (++ready < 2) return;
  await sleep(1500);
  for (const b of [bot, bot2]) { if (b.health <= 0) b._client.write('client_command', { actionId: 0 }); }
  await sleep(500); await ensureTest(bot); await ensureTest(bot2);
  // ---- Drifter : sortie de l'arene quand les ennemis disparaissent
  bot.chat('/cf change drifter'); await sleep(2000); bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf mannequin'); await sleep(800);
  bot.chat('/tp Tester2 Tester'); await sleep(500);
  await click(bot, 5, 1500); log('burst ->', lastChat(/Burst/), '| Dimension', await myData(bot, 'Dimension'), '| Tester2 dim', await myData(bot2, 'Dimension'));
  bot.chat('/cf mannequin clear'); await sleep(1500); log('mannequin retire, Tester2 reste ->', lastChat(/Monde en Ruine/), '| Dimension', await myData(bot, 'Dimension'));
  bot.chat('/damage Tester2 1000 minecraft:player_attack by Tester'); await sleep(2500); log('Tester2 tue ->', lastChat(/Monde en Ruine/), '| Dimension', await myData(bot, 'Dimension'), '| Tester2 vie', bot2.health);
  if (bot2.health <= 0) { bot2._client.write('client_command', { actionId: 0 }); await sleep(1500); }
  // ---- Shoja : Epee de l'Ego sur un joueur tue
  bot.chat('/cf change shoja'); await sleep(2000); bot2.chat('/cf change hano'); await sleep(2000);
  bot2.setQuickBarSlot(0); await sleep(300); log('Tester2 tient', bot2.heldItem ? bot2.heldItem.name : '-');
  bot.chat('/tp Tester2 Tester'); await sleep(500);
  log('avant : epee de Tester', await myData(bot, 'Inventory[{Slot:0b}].components'));
  bot.chat('/damage Tester2 1000 minecraft:player_attack by Tester'); await sleep(1500);
  log('kill ->', lastChat(/Ego/), '| apres :', await myData(bot, 'Inventory[{Slot:0b}].components'));
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); bot2.chat('/cf test'); await sleep(1000); bot.quit(); bot2.quit(); process.exit(0);
};
bot.once('spawn', go); bot2.once('spawn', go);
