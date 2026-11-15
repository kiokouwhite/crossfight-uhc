// /cf test arbre : la foret de test existe-t-elle, y arrive-t-on dans une clairiere, en revient-on ?
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
let chat = []; bot.on('messagestr', m => chat.push(m));
const count = () => { const c = {}; const p = bot.entity.position.floored(); let top = 0;
  for (let x = -40; x <= 40; x += 1) for (let z = -40; z <= 40; z += 1) for (let y = 65; y < 110; y++) { const b = bot.blockAt(p.offset(x, y - p.y, z)); if (b && b.name !== 'air') { c[b.name] = (c[b.name] || 0) + 1; if (/log/.test(b.name)) top = Math.max(top, y - 64); } }
  return JSON.stringify(c) + ' | plus haut tronc : ' + top + ' blocs'; };
bot.once('spawn', async () => {
  await sleep(1500); bot.chat('/cf test arbre'); await sleep(9000);
  if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  console.log('monde :', bot.game.dimension, '| position', bot.entity.position.floored().toString(), '|', chat.filter(m => /Foret/.test(m)).pop());
  console.log('autour :', count());
  bot.chat('/cf clear'); await sleep(6000); console.log('apres /cf clear :', count().slice(0, 200));
  bot.chat('/cf test arbre'); await sleep(2500); console.log('retour : monde', bot.game.dimension, '|', chat.slice(-1)[0]);
  bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
