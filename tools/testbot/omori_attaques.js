// Les attaques animees d'Omori, phase par phase : le boss fonce, frappe, revient ; aucune erreur cote serveur.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const chat = []; bot.on('messagestr', m => chat.push(m));
bot.once('spawn', async () => {
  await sleep(4000); bot.chat('/cf test omori'); await sleep(4000); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await sleep(500);
  bot.setControlState('forward', true); await sleep(1400); bot.setControlState('forward', false); await sleep(2500);
  if (bot.currentWindow) await bot.clickWindow(3, 0, 0); await sleep(2500);
  bot.chat('/effect give @s minecraft:regeneration 600 4 true'); await sleep(300);
  for (const phase of [1, 2, 3, 4, 5]) {
    bot.chat('/cf omori phase ' + phase); chat.length = 0; let near = 99, lowest = 20, moved = 0; const p0 = bot.entity.position.clone();
    for (let i = 0; i < (phase === 5 ? 190 : 150); i++) {
      await sleep(200); const boss = bot.nearestEntity(e => e.name === 'zombie');
      if (boss) near = Math.min(near, boss.position.distanceTo(bot.entity.position)); lowest = Math.min(lowest, bot.health); moved = Math.max(moved, bot.entity.position.distanceTo(p0));
    }
    const boss = bot.nearestEntity(e => e.name === 'zombie');
    console.log(`phase ${phase} : boss au plus pres ${near.toFixed(1)} blocs, vie au plus bas ${lowest.toFixed(1)}, je suis deplace de ${moved.toFixed(1)}, boss maintenant a ${boss ? boss.position.distanceTo(bot.entity.position).toFixed(1) : '?'} | ${[...new Set(chat.filter(m => /Omori|effraye|Trauma|poignarde|attrape/.test(m)).map(m => m.replace('[Crossfight] ', '').slice(0, 34)))].join(' / ')}`);
  }
  bot.chat('/cf test omori'); await sleep(1500); bot.chat('/cf test'); await sleep(1500); bot.quit(); process.exit(0);
});
