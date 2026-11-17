// /cf quete placer : Anna vient la ou se tient l'admin ; /cf quete replacer la renvoie.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500); bot.chat('/gamemode creative');
  bot.chat('/cf quete tp terrace'); await sleep(5000);
  const anna = () => { const e = Object.values(bot.entities).find(e => e.name === 'mannequin' && e.position.distanceTo(bot.entity.position) < 40); return e ? `${e.position}` : 'absente'; };
  console.log('origine :', anna());
  bot.chat('/tp @s ~12 ~6 ~9'); await sleep(1500);
  console.log('moi :', `${bot.entity.position}`);
  bot.chat('/cf quete placer'); await sleep(4000);
  console.log('placee :', anna());
  bot.chat('/cf quete replacer'); await sleep(4000);
  console.log('replacee :', anna());
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
