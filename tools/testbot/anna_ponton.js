// Anna a Garreg Mach : sur le quai, devant le ponton (des dalles d'epicea sur l'eau, juste au nord).
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500); bot.chat('/gamemode creative');
  bot.chat('/cf quete tp garreg_mach'); await sleep(5000); bot.chat('/cf quete tp garreg_mach'); await sleep(5000);
  const a = Object.values(bot.entities).find(e => e.name === 'mannequin');
  if (!a) { console.log('Anna absente'); bot.quit(); return process.exit(0); }
  const at = a.position.floored();
  console.log('Anna :', `${a.position}`, '| sous elle', bot.blockAt(at.offset(0, -1, 0)).name);
  for (let d = 1; d <= 6; d++) console.log('  au nord, a', d, ':', bot.blockAt(at.offset(0, -1, -d)).name, '/', bot.blockAt(at.offset(0, -2, -d)).name);
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
