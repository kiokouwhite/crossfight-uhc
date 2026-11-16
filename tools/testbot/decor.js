// Le decor d'un lieu pose : combien de cadres, tableaux, porte-armures autour d'un point, et ou sont les cadres par rapport aux blocs.
const mineflayer = require('mineflayer'); const { Vec3 } = require('vec3'); const { execSync } = require('child_process');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const [dim, x, y, z] = [process.argv[2], +process.argv[3], +process.argv[4], +process.argv[5]];
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
bot.once('spawn', async () => {
  await sleep(3000);
  execSync(`timeout 5 sh -c "echo 'gamemode spectator Invite' > /home/user/testserver/console.in"`);
  execSync(`timeout 5 sh -c "echo 'execute in ${dim} run tp Invite ${x} ${y} ${z}' > /home/user/testserver/console.in"`);
  await sleep(8000);
  const kinds = {};
  for (const e of Object.values(bot.entities)) if (e !== bot.entity) kinds[e.name] = (kinds[e.name] || 0) + 1;
  console.log('entites vues :', JSON.stringify(kinds));
  let ok = 0, bad = 0; const sample = [];
  for (const e of Object.values(bot.entities)) {
    if (e.name !== 'item_frame' && e.name !== 'glow_item_frame' && e.name !== 'painting') continue;
    // Un cadre est contre un bloc : l'un des six blocs voisins de sa case n'est pas vide.
    const p = e.position.floored(); let touch = false;
    for (const d of [[1,0,0],[-1,0,0],[0,1,0],[0,-1,0],[0,0,1],[0,0,-1]]) { const b = bot.blockAt(p.offset(d[0], d[1], d[2])); if (b && b.name !== 'air') touch = true; }
    if (touch) ok++; else bad++;
    if (sample.length < 4) sample.push(e.name + ' ' + e.position.toString());
  }
  console.log('cadres et tableaux contre un bloc :', ok, '| dans le vide :', bad, '|', sample.join(' ; '));
  bot.quit(); process.exit(0);
});
