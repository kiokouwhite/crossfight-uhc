// Avant le lancement : le joueur qui arrive attend sur une plateforme de verre dans les airs, sans pouvoir en tomber.
const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const where = () => bot.entity.position.x.toFixed(1) + ' ' + bot.entity.position.y.toFixed(1) + ' ' + bot.entity.position.z.toFixed(1);
bot.once('spawn', async () => {
  await sleep(4000);
  const p = bot.entity.position.floored();
  const under = bot.blockAt(p.offset(0, -1, 0));
  const count = {}; for (let x = -12; x <= 12; x++) for (let z = -12; z <= 12; z++) for (let y = -1; y <= 6; y++) { const b = bot.blockAt(p.offset(x, y, z)); if (b && b.name !== 'air') count[b.name] = (count[b.name] || 0) + 1; }
  console.log('arrivee :', where(), '| sous les pieds :', under && under.name, '| mode', bot.game.gameMode, '| blocs', JSON.stringify(count));
  // Il marche vers le bord : les barrieres l'arretent.
  await bot.look(0, 0, true); bot.setControlState('forward', true); bot.setControlState('sprint', true); await sleep(4500); bot.setControlState('forward', false);
  console.log('apres 4,5 s de course vers le bord :', where());
  bot.chat('/cf test'); await sleep(3000); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); console.log('en mode test :', where());
  bot.chat('/cf test'); await sleep(2000); console.log('sortie du mode test :', where());
  // /cf lobby : tout le monde revient sur la plateforme, d'ou qu'il soit.
  bot.chat('/tp @s 30.5 120 30.5'); await sleep(1200); console.log('envoye ailleurs :', where());
  let chat = []; bot.on('messagestr', m => chat.push(m)); bot.chat('/cf lobby'); await sleep(1500); console.log('/cf lobby :', where(), '|', chat.pop());
  bot.quit(); process.exit(0);
});
