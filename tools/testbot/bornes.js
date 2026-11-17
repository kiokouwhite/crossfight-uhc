// Ou sont la borne et le personnage de chaque lieu, et ce qu'il y a autour (pas dans l'eau, pas sous un plancher).
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500); bot.chat('/gamemode creative');
  for (const place of ['garreg_mach', 'shiganshina', 'pearl', 'central_town', 'arcadia', 'terrace', 'beacon', 'forest_of_hope', 'jouki']) {
    bot.chat('/cf quete tp ' + place); await sleep(5000);
    const npc = Object.values(bot.entities).find(e => (e.name === 'mannequin' || e.name === 'villager') && e.position.distanceTo(bot.entity.position) < 8);
    if (!npc) { console.log(place, ': pas de personnage'); continue; }
    const at = npc.position.floored();
    const col = []; for (let dy = -1; dy <= 12; dy++) { const b = bot.blockAt(at.offset(0, dy, 0)); col.push(b ? b.name : '?'); }
    const roof = col.slice(3).findIndex(n => n !== 'air' && n !== '?');
    console.log(place, ':', `${at}`, '| sous lui', col[0], '| a ses pieds', col[1], col[2], '| premier bloc au-dessus :', roof < 0 ? 'rien sur 10' : col[3 + roof] + ' a +' + (roof + 2));
  }
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
