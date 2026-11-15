// Menu du mode test : les personnages qui ont un skin sont montres par leur tete.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test'); await sleep(3500);
  const w = bot.currentWindow; if (!w) { console.log('pas de menu'); process.exit(1); }
  const items = w.slots.slice(0, w.inventoryStart).filter(Boolean);
  const heads = items.filter(i => i.name === 'player_head');
  console.log(items.length, 'personnages |', heads.length, 'tetes | avec profil', heads.filter(i => /textures/.test(JSON.stringify(i.components || ''))).length, '| autres :', items.filter(i => i.name !== 'player_head').map(i => i.name).join(','));
  await bot.clickWindow(w.slots.findIndex(i => i && i.name === 'player_head'), 0, 0); await sleep(2500);
  console.log('clic sur la premiere tete : barre', bot.inventory.slots.slice(36, 40).map(i => i ? i.name : '-').join(','));
  bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
