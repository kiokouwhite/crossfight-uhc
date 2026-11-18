// La forge arretee, la musique importee doit s'arreter : le bot lance la musique, s'accroupit, et note ce que le serveur envoie.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  const vus = [];
  for (const nom of ['sound_effect', 'named_sound_effect', 'stop_sound', 'entity_sound_effect'])
    bot._client.on(nom, d => { const s = JSON.stringify(d); if (/forge|stop|music/.test(nom + s)) vus.push(nom + ' ' + s.slice(0, 220)); });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500);
  bot.chat('/gamemode creative'); await sleep(500);
  bot.chat('/cf rythme ' + (process.argv[3] || 'installation')); await sleep(7000);
  console.log('pendant la musique :', vus.join(' || ')); vus.length = 0;
  bot.setControlState('sneak', true); await sleep(1500); bot.setControlState('sneak', false); await sleep(1000);
  console.log('apres l\'arret :', vus.join(' || ') || 'rien');
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
