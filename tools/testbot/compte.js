// Compte les blocs non vides autour d'un point (verification d'un lieu pose).
const mineflayer = require('mineflayer'); const { Vec3 } = require('vec3'); const { execSync } = require('child_process');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const [dim, x, y, z] = [process.argv[2], +process.argv[3], +process.argv[4], +process.argv[5]];
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
bot.once('spawn', async () => {
  await sleep(3000);
  execSync(`timeout 5 sh -c "echo 'gamemode spectator Invite' > /home/user/testserver/console.in"`);
  execSync(`timeout 5 sh -c "echo 'execute in ${dim} run tp Invite ${x} ${y} ${z}' > /home/user/testserver/console.in"`);
  await sleep(7000);
  const names = {}; let n = 0;
  for (let dx = -24; dx <= 24; dx += 2) for (let dz = -24; dz <= 24; dz += 2) for (let dy = -90; dy <= 150; dy += 2) {
    const b = bot.blockAt(new Vec3(x + dx, y + dy, z + dz)); if (!b || b.name === 'air') continue; n++; names[b.name] = (names[b.name] || 0) + 1;
  }
  console.log('blocs non vides echantillonnes :', n);
  console.log(Object.entries(names).sort((a, b) => b[1] - a[1]).slice(0, 14).map(e => e.join(' ')).join(', '));
  bot.quit(); process.exit(0);
});
