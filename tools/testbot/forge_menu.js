// Le menu de la forge : le bot l'ouvre, liste les cases occupees, clique la premiere musique et verifie qu'il part dans la salle.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  let win = null; bot.on('windowOpen', w => { win = w; });
  await new Promise(r => bot.once('spawn', r)); await sleep(2500);
  if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(300); }
  win = null; bot.chat('/cf rythme'); await sleep(1200);
  if (!win) { console.log('pas de menu'); process.exit(1); }
  console.log('cases du coffre :', win.slots.length - 36, '| occupees :', win.slots.slice(0, 54).map((s, i) => s ? i : null).filter(i => i != null).join(' '));
  const avant = bot.entity.position.floored();
  await bot.clickWindow(14, 0, 0).catch(() => {}); await sleep(2500);
  console.log('avant', avant.toString(), '| apres le clic', bot.entity.position.floored().toString(), '| devant :', bot.blockAt(bot.entity.position.offset(0, 0, 2))?.name);
  bot.setControlState('sneak', true); await sleep(600); bot.setControlState('sneak', false); await sleep(1000);
  console.log('retour', bot.entity.position.floored().toString());
  bot.quit(); setTimeout(() => process.exit(0), 400);
})();
