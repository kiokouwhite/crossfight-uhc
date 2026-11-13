const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
let bars = []; bot._client.on('action_bar', d => bars.push(JSON.stringify(d.text).match(/"value":"([^"]{3,})"/g)?.map(x => x.slice(9, -1)).join(' ') || ''));
bot.on('death', () => { setTimeout(() => bot._client.write('client_command', { actionId: 0 }), 300); });
const models = {}; bot._client.on('entity_metadata', d => { for (const m of d.metadata) { const x = JSON.stringify(m.value || '').match(/crossfight:[a-z_\/]+/); if (x) models[d.entityId] = x[0]; } });
const body = () => Object.values(bot.entities).find(e => e.name === 'mannequin' && e !== bot.entity && /3e personne/.test(JSON.stringify(e.metadata || [])));
const puppetNear = () => { const m = body(); if (!m) return '?'; const ids = Object.values(bot.entities).filter(e => e.name === 'item_display' && e.position.distanceTo(m.position) < 3).map(e => models[e.id] || '?'); return ids.join(','); };
bot.once('spawn', async () => {
  await sleep(3000);
  bot.chat('/cf stop'); await sleep(800);
  bot.chat('/cf test third-person byleth'); await sleep(5000);
  for (const [slot, name, wait] of [[0, 'Epee du Createur', 500], [2, 'Tutorat', 1500], [8, 'Ultime', 2000]]) {
    bot.chat('/tp @s 0.5 65 6 180 0'); bot.chat('/cf heal'); await sleep(800);
    await bot.lookAt(body().position.offset(0, 1.5, 0), true);
    bot.setQuickBarSlot(slot); await sleep(300); bars = [];
    bot.activateItem(); await sleep(80); bot.deactivateItem(); await sleep(wait);
    console.log(name, ': modele articule ->', puppetNear() || '(rien)', '| barre:', bars.filter(Boolean).join(' / ').slice(0, 100));
    await sleep(6000);
  }
  bot.chat('/cf test third-person'); await sleep(1000);
  bot.quit(); process.exit(0);
});
