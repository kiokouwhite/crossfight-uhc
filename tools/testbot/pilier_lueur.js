// Le pilier disparait pour le joueur tant que son epreuve est ouverte, et reparait quand il la ferme.
// node pilier_lueur.js <monde>
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500);
  bot.chat('/gamemode creative'); bot.chat('/cf quete oublier central_town'); await sleep(500);
  bot.chat('/cf quete tp central_town'); await sleep(5000);
  bot.chat('/cf quete lancer central_town'); await sleep(2000);
  const piliers = () => Object.values(bot.entities).filter(e => e.name === 'block_display').length;
  const anna = Object.values(bot.entities).find(e => e.name === 'mannequin');
  const pilier = Object.values(bot.entities).filter(e => e.name === 'interaction' && anna && Math.hypot(e.position.x - anna.position.x, e.position.z - anna.position.z) > 4.2 && e.position.distanceTo(anna.position) < 12)[0];
  if (!pilier) { console.log('pas de pilier'); bot.quit(); return process.exit(1); }
  bot.chat(`/tp @s ${pilier.position.x + 2.5} ${pilier.position.y} ${pilier.position.z}`); await sleep(1200);
  console.log('piliers visibles avant :', piliers());
  bot.useOn(pilier); await sleep(1500);
  console.log('epreuve ouverte :', piliers(), '| affichages du tableau :', Object.values(bot.entities).filter(e => e.name === 'text_display').length);
  bot.setControlState('sneak', true); await sleep(500); bot.setControlState('sneak', false); await sleep(1500);
  console.log('epreuve fermee :', piliers());
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
