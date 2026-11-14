const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
let eq = [];
bot._client.on('entity_equipment', d => eq.push(JSON.stringify(d).replace(/\s/g, '').slice(0, 400)));
bot.once('spawn', async () => {
  await sleep(2500); bot.chat('/cf stop'); await sleep(800);
  eq = [];
  bot.chat('/cf test third-person marth'); await sleep(5000);
  { const it = bot.inventory.slots[36]; const c = (it.components || []).find(x => x.type === 'custom_model_data'); console.log('custom_model_data :', JSON.stringify(c)); }
  console.log('equipement du mannequin :', eq.filter(x => /sword/.test(x) || /custom_model/.test(x)).slice(0, 2).join('\n'));
  bot.chat('/cf test third-person'); await sleep(800); bot.quit(); process.exit(0);
});
