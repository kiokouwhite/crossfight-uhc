// La salle de forge : le bot lance une musique, verifie qu'il est dans la salle (monde, enclume), frappe des notes
// (son bras doit partir), s'accroupit, et verifie qu'il revient d'ou il venait.
// node forge_salle.js <monde> <musique>
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  let bras = 0, apparus = [];
  bot._client.on('animation', d => { if (d.entityId === bot.entity.id && d.animation === 0) bras++; });
  bot.on('entitySpawn', e => { if (e.name === 'text_display') apparus.push({ x: e.position.x, z: e.position.z, t: Date.now() }); });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  bot.chat('/execute in minecraft:' + process.argv[2] + ' run tp @s 0 200 0'); await sleep(1500);
  bot.chat('/gamemode creative'); await sleep(500);
  const ou = () => bot.game.dimension + ' ' + bot.entity.position.floored();
  console.log('avant :', ou());
  bot.chat('/cf rythme ' + process.argv[3]); await sleep(3000);
  console.log('pendant :', ou(), '| devant :', bot.blockAt(bot.entity.position.offset(0, 0, 1))?.name, '| sol :', bot.blockAt(bot.entity.position.offset(0, -1, 0))?.name, '| affichages :', apparus.length);
  const colonnes = [1, 3, 5, 7].map(i => apparus[i]).filter(Boolean);
  let vu = 9, frappes = 0; const fin = Date.now() + 14000;
  while (Date.now() < fin) {
    while (vu < apparus.length) {
      const a = apparus[vu++];
      let voie = 0; for (let k = 1; k < 4; k++) if (Math.hypot(a.x - colonnes[k].x, a.z - colonnes[k].z) < Math.hypot(a.x - colonnes[voie].x, a.z - colonnes[voie].z)) voie = k;
      frappes++; setTimeout(() => bot._client.write('held_item_slot', { slotId: voie }), Math.max(0, a.t + 40 * 50 - Date.now()));
    }
    await sleep(10);
  }
  console.log('frappes :', frappes, '| coups de bras recus :', bras);
  bot.setControlState('sneak', true); await sleep(600); bot.setControlState('sneak', false); await sleep(1500);
  console.log('apres :', ou());
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
