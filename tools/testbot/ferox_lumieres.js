// Lumieres de l'arene de Ferox : niveau des lumieres invisibles de la fosse, lanternes du mur, lumiere au sol.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const V = (x, y, z) => bot.entity.position.clone().set(x, y, z);
bot.once('spawn', async () => {
  await sleep(4000);
  bot.chat('/cf test'); await sleep(9000);
  const l = bot.blockAt(V(5, 68, 5));
  console.log('lumiere de la fosse (5,68,5) :', l ? l.name + ' ' + JSON.stringify(l.getProperties()) : '?');
  const lanternes = bot.findBlocks({ matching: bot.registry.blocksByName.lantern.id, maxDistance: 90, count: 400 });
  console.log('lanternes :', lanternes.length, lanternes.slice(0, 4).map(p => p.toString()).join(' '));
  // La lumiere percue a hauteur d'yeux en plusieurs points de la fosse (lumiere des blocs).
  for (const [x, z] of [[0, 0], [20, 10], [-30, 25], [45, -5], [0, 50], [2, 2], [12, 13], [-37, -38]]) {
    const b = bot.blockAt(V(x, 65, z));
    console.log(`  lumiere en (${x}, ${z}) :`, b ? b.light : '?');
  }
  bot.chat('/cf test'); await sleep(800);
  bot.quit(); process.exit(0);
});
bot.on('error', e => { console.log('erreur', e.message); process.exit(1); });
