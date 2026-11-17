// Le plugin distribue lui-meme le pack complet : le bot releve l'adresse et l'empreinte qu'on lui propose.
const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
bot._client.on('add_resource_pack', d => { console.log(d.url, d.hash); bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }); setTimeout(() => { bot.quit(); process.exit(0); }, 500); });
setTimeout(() => { console.log('aucun pack propose'); process.exit(0); }, 20000);
