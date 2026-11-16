// L'ultime de Remi : elle s'eleve, redescend frapper le sol, le dome part ; pas d'erreur cote serveur.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const chat = []; bot.on('messagestr', m => chat.push(m));
bot.once('spawn', async () => {
  await sleep(4000);
  bot.chat('/cf test'); await sleep(3500); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await sleep(400);
  bot.chat('/cf change remi'); await sleep(2500); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await sleep(400);
  bot.chat('/cf ult'); await sleep(600);
  const y0 = bot.entity.position.y; bot.setQuickBarSlot(8); await sleep(300); bot.activateItem(); await sleep(100); bot.deactivateItem();
  setTimeout(() => bot.chat('/data get entity @s Pos'), 450); setTimeout(() => bot.chat('/data get entity @s Motion'), 250);
  let top = y0; const ys = [];
  for (let i = 0; i < 30; i++) { await sleep(100); top = Math.max(top, bot.entity.position.y); if (i % 3 === 0) ys.push((bot.entity.position.y - y0).toFixed(1)); }
  console.log('messages :', chat.slice(-3).join(' | ').slice(0, 300), '| barre :', bot.inventory.slots.slice(36, 45).map(i => i ? i.name : '-').join(','));
  console.log('hauteur au fil du temps :', ys.join(' '), '| sommet +' + (top - y0).toFixed(1), '| vie', bot.health);
  bot.chat('/cf test'); await sleep(1500); bot.quit(); process.exit(0);
});
