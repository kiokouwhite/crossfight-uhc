const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let chat = []; bot.on('messagestr', m => chat.push(m));
let last = null;
bot._client.on('packet', (data, meta) => {
  if (meta.name === 'add_resource_pack') { last = data; log('pack propose :', data.url.split('/').pop(), 'hash', data.hash.slice(0, 8), 'force', data.forced); }
  if (/^(respawn|open_window|position)$/.test(meta.name) && Date.now() - t0 < 8000) log('  (paquet', meta.name + ')');
});
bot.once('spawn', async () => {
  t0 = Date.now(); log('connecte');
  await sleep(5000);
  if (last) { bot._client.write('resource_pack_receive', { uuid: last.uuid, result: 3 }); await sleep(100); bot._client.write('resource_pack_receive', { uuid: last.uuid, result: 0 }); await sleep(700); log('reponse "charge" ->', chat.filter(x => /Sons|pack/i.test(x)).pop()); }
  bot.chat('/cf pack'); await sleep(1000); log('/cf pack ->', chat.filter(x => /propose/.test(x)).pop());
  if (last) { bot._client.write('resource_pack_receive', { uuid: last.uuid, result: 1 }); await sleep(700); log('reponse "refuse" ->', chat.filter(x => /refuse/.test(x)).pop()); }
  bot.quit(); process.exit(0);
});
