const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
let msgs = []; bot.on('message', m => msgs.push(m.toString()));
const body = () => Object.values(bot.entities).find(e => e.name === 'mannequin' && e !== bot.entity && /3e personne/.test(JSON.stringify(e.metadata || [])));
bot.once('spawn', async () => {
  await sleep(3000);
  bot.chat('/cf stop'); await sleep(1000);
  bot.chat('/cf test third-person marth'); await sleep(5000);
  msgs = []; bot.chat('/data get entity @s Inventory[{Slot:102b}].id'); await sleep(500); console.log('armure portee :', msgs.join(' ').slice(-40));
  for (let i = 0; i < 2; i++) {
    bot.chat('/tp @s 0.5 65 5 180 0'); bot.chat('/cf heal'); await sleep(1500);
    const m = body(); await bot.lookAt(m.position.offset(0, 1.5, 0), true);
    bot.setQuickBarSlot(8); await sleep(300);
    const hp = bot.health; bot.activateItem(); await sleep(80); bot.deactivateItem(); await sleep(3500);
    console.log('Coup Critique du mannequin : vie', hp, '->', bot.health.toFixed(1), '=', ((hp - bot.health) / 2).toFixed(1), 'coeurs');
    await sleep(3000);
  }
  bot.chat('/cf test third-person'); await sleep(1500);
  bot.quit(); process.exit(0);
});
