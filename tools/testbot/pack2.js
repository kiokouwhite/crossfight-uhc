const mineflayer = require('mineflayer');
const phase = process.argv[2];
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
let chat = []; bot.on('messagestr', m => chat.push(m));
let last = null;
bot._client.on('packet', (data, meta) => {
  if (meta.name === 'add_resource_pack') { last = data; log('DEMANDE DU PACK'); }
  if (meta.name === 'open_window') log('MENU OUVERT :', JSON.stringify(data.windowTitle).replace(/[^A-Za-z :]/g, '').slice(0, 60));
  if (meta.name === 'close_window') log('menu ferme par le serveur');
});
bot.once('spawn', async () => {
  t0 = Date.now(); log('connecte, phase', phase);
  if (phase === 'A') {
    await sleep(4000); if (last) bot._client.write('resource_pack_receive', { uuid: last.uuid, result: 1 });
    await sleep(500);
    const inTest = bot.inventory.slots[43] && bot.inventory.slots[43].name === 'golden_apple';
    if (!inTest) { bot.chat('/cf test'); await sleep(2000); }
    log('en mode test, sans personnage ; deconnexion'); bot.quit(); process.exit(0);
  }
  await sleep(7000); log('--- le joueur repond maintenant a la demande du pack');
  if (last) { bot._client.write('resource_pack_receive', { uuid: last.uuid, result: 3 }); await sleep(100); bot._client.write('resource_pack_receive', { uuid: last.uuid, result: 0 }); }
  await sleep(2000); log('fin ->', chat.filter(x => /Sons|repris/.test(x)).join(' | '));
  bot.quit(); process.exit(0);
});
