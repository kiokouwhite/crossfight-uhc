// Equipement tridimensionnel : les degats de chute sont reduits de 75 % pendant l'utilisation, plus annules.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
async function fall() { bot.chat('/cf heal'); await sleep(500); const before = bot.health; bot.chat('/damage @s 8 minecraft:fall'); await sleep(600); return (before - bot.health).toFixed(2); }
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); }
  bot.chat('/cf change mikasa'); await sleep(4500); if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(400); }
  bot.chat('/tp @s 0.5 65 0.5 0 0'); await sleep(1500);
  console.log('chute de 8 sans le grappin : -' + await fall(), 'points de vie');
  const s = bot.inventory.slots.slice(36, 45).findIndex(i => i && JSON.stringify(i.customName || '').includes('Equipement'));
  bot.setQuickBarSlot(s); await bot.look(0, 0.5, true); await sleep(300); await bot.look(0, -0.4, true); await sleep(300);
  bot.activateItem(); await sleep(80); bot.deactivateItem(); await sleep(700);
  console.log('chute de 8 juste apres le grappin : -' + await fall(), 'points de vie');
  await sleep(6000);
  console.log('chute de 8 six secondes plus tard : -' + await fall(), 'points de vie');
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
