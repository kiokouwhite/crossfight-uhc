// Anna regarde le joueur qui s'approche : son lacet suit le bot quand il tourne autour d'elle.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500); bot.chat('/gamemode creative');
  bot.chat('/cf quete tp pearl'); await sleep(5000);
  const a = Object.values(bot.entities).find(e => e.name === 'mannequin');
  if (!a) { console.log('Anna absente'); bot.quit(); return process.exit(0); }
  const deg = r => ((180 - r * 180 / Math.PI) % 360 + 540) % 360 - 180;       // lacet a la facon du jeu
  for (const [dx, dz] of [[3, 0], [0, 3], [-3, 0], [0, -3]]) {
    bot.chat(`/tp @s ${a.position.x + dx} ${a.position.y} ${a.position.z + dz}`); await sleep(2500);
    const want = Math.atan2(-dx, dz) * 180 / Math.PI;
    console.log(`joueur en (${dx}, ${dz}) : Anna regarde ${deg(a.yaw).toFixed(0)}, attendu ${want.toFixed(0)}`);
  }
  bot.chat(`/tp @s ${a.position.x + 30} ${a.position.y} ${a.position.z}`); await sleep(3500);
  console.log('joueur parti : Anna regarde', deg(a.yaw).toFixed(0));
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
