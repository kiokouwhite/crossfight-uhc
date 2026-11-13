const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
let msgs = []; bot.on('message', m => msgs.push(m.toString()));
let bars = []; bot._client.on('action_bar', d => bars.push(JSON.stringify(d.text).match(/"value":"([^"]{4,})"/g)?.map(x => x.slice(9, -1)).join(' ') || ''));
const body = () => Object.values(bot.entities).find(e => e.name === 'mannequin' && e !== bot.entity && /3e personne/.test(JSON.stringify(e.metadata || [])));
const hpOf = async () => { msgs = []; bot.chat('/data get entity @e[tag=cf_mannequin_pilote,limit=1] Health'); await sleep(500); return (msgs.join(' ').match(/([0-9.]+)f/) || [0, '?'])[1]; };
bot.once('spawn', async () => {
  await sleep(4000);
  bot.chat('/cf change marth'); await sleep(2000);
  for (const weapon of [true, false]) {
    bot.chat('/tp @s 0.5 65 2.8 180 0'); bot.chat('/cf heal'); await sleep(800);
    const m = body(); await bot.lookAt(m.position.offset(0, 1.4, 0), true);
    const hp0 = await hpOf(); const me0 = bot.health; const p0 = bot.entity.position.clone(); const mp0 = m.position.clone();
    bot.setQuickBarSlot(1); await sleep(250); bars = [];
    bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(250);
    bot.setQuickBarSlot(weapon ? 0 : 2); await sleep(150);
    bot.attack(body()); await sleep(1500);
    console.log((weapon ? 'epee' : 'objet de capacite') + ' pendant le Contre : vie du mannequin', hp0, '->', await hpOf(), '| ma vie', me0, '->', bot.health.toFixed(1),
      '| projete de', p0.distanceTo(bot.entity.position).toFixed(1), 'blocs | mannequin deplace de', mp0.distanceTo(body().position).toFixed(2), '| barre :', bars.filter(Boolean).join(' / ').slice(0, 120));
    await sleep(5500);
  }
  bot.quit(); process.exit(0);
});
