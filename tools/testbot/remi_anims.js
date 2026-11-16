// Remi : le Dash electrique (pose de course) et l'Electricite (main tendue), sans erreur cote serveur.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
bot.once('spawn', async () => {
  await sleep(4000);
  bot.chat('/cf test'); await sleep(3500); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await sleep(400);
  bot.chat('/cf change remi'); await sleep(2500); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await sleep(400);
  const bar = bot.inventory.slots.slice(36, 45).map((it, i) => it ? i + ':' + it.name : null).filter(Boolean); console.log('barre :', bar.join(' '));
  for (const name of ['feather', 'lightning_rod']) {
    const slot = bot.inventory.slots.slice(36, 45).findIndex(it => it && it.name === name); if (slot < 0) { console.log(name, 'absent'); continue; }
    const before = bot.entity.position.clone(); bot.setQuickBarSlot(slot); await sleep(300); bot.activateItem(); await sleep(100); bot.deactivateItem();
    await sleep(400); const shown = Object.values(bot.entities).filter(e => e.name === 'item_display').length; await sleep(1200);
    console.log(name, ': deplacement', bot.entity.position.distanceTo(before).toFixed(1), '| affichages pendant l\'animation', shown);
  }
  bot.chat('/cf test'); await sleep(1500); bot.quit(); process.exit(0);
});
