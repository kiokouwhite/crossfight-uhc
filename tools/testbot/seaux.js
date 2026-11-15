// Mode test : deux seaux d'eau, qui se remplissent tout seuls.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const bar = () => bot.inventory.slots.slice(36, 45).map(i => i ? i.name : '-').join(',');
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(2500); if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  console.log('barre :', bar());
  bot.chat('/cf change marth'); await sleep(4000); if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  console.log('en Marth :', bar());
  const s = bot.inventory.slots.slice(36, 45).findIndex(i => i && i.name === 'water_bucket');
  bot.setQuickBarSlot(s); await bot.look(0, -1.2, true); await sleep(300); bot.activateItem(); await sleep(300);
  console.log('seau verse :', bar()); await sleep(1500); console.log('une seconde apres :', bar());
  bot.chat('/cf clear'); await sleep(1500); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
