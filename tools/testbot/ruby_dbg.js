const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const models = {}; const meta = {};
bot._client.on('entity_metadata', d => { for (const m of d.metadata) { const x = JSON.stringify(m.value || '').match(/crossfight:[a-z_\/]+/); if (x) models[d.entityId] = x[0]; } meta[d.entityId] = (meta[d.entityId] || 0) + 1; });
let chat = []; bot.on('messagestr', m => chat.push(m));
const body = () => Object.values(bot.entities).find(e => e.name === 'mannequin' && e !== bot.entity && /3e personne/.test(JSON.stringify(e.metadata || [])));
const slotOf = (txt) => bot.inventory.slots.findIndex((i, n) => n >= 36 && n <= 44 && i && JSON.stringify(i.customName || i.name).includes(txt)) - 36;
bot.once('spawn', async () => {
  await sleep(3000);
  bot.chat('/cf stop'); await sleep(800);
  bot.chat('/cf test third-person ruby'); await sleep(5000);
  bot.chat('/tp @s 0.5 65 12 180 0'); bot.chat('/cf heal'); await sleep(1500);
  const b = body(); console.log('corps', b && b.position, 'testeur', bot.entity.position);
  await bot.lookAt(b.position.offset(0, 1.5, 0), true);
  const s = slotOf('Petales (rapide)'); console.log('case', s);
  bot.setQuickBarSlot(s); await sleep(300); chat = [];
  bot.activateItem(); await sleep(80); bot.deactivateItem();
  const t0 = Date.now();
  for (let i = 0; i < 20; i++) {
    await sleep(50);
    const bb = body(); const parts = Object.values(bot.entities).filter(e => e.name === 'item_display' && /pose\/ruby/.test(models[e.id] || ''));
    console.log(Date.now() - t0, 'ms corps', bb && bb.position.toString(), '| pantin', parts.length, 'meta', parts.map(e => meta[e.id]).join(','), '| sante', bot.health.toFixed(1));
  }
  console.log('chat', chat.slice(0, 5));
  bot.chat('/cf test third-person'); await sleep(800); bot.quit(); process.exit(0);
});
