// /cf test omori : la porte devant le testeur, l'entree, le boss, puis le retrait.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const chat = []; bot.on('messagestr', m => chat.push(m));
const last = re => (chat.filter(m => re.test(m)).pop() || '-').slice(0, 160);
bot.once('spawn', async () => {
  await sleep(4000); bot.chat('/cf test omori'); await sleep(4000); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await sleep(500);
  console.log(last(/porte blanche/), '| affichages proches :', Object.values(bot.entities).filter(e => e.name === 'item_display').length);
  bot.setControlState('forward', true); await sleep(1400); bot.setControlState('forward', false); await sleep(2500);
  console.log('menu :', bot.currentWindow ? bot.currentWindow.slots.slice(0, 9).filter(Boolean).map(i => i.name).join(' ') : 'pas ouvert');
  if (bot.currentWindow) await bot.clickWindow(3, 0, 0); await sleep(3000);
  console.log(last(/Espace Blanc/), '| boss :', !!bot.nearestEntity(e => e.name === 'zombie'), '| sol :', bot.blockAt(bot.entity.position.offset(0, -1, 0)).name);
  bot.chat('/cf omori phase 3'); await sleep(1200); console.log(last(/Omori : /));
  bot.chat('/cf test omori'); await sleep(2000); console.log(last(/retiree/), '| sol :', bot.blockAt(bot.entity.position.offset(0, -1, 0)).name);
  bot.chat('/cf test'); await sleep(1500); bot.quit(); process.exit(0);
});
