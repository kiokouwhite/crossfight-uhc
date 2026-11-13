const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const models = {}; bot._client.on('entity_metadata', d => { for (const m of d.metadata) { const x = JSON.stringify(m.value || '').match(/crossfight:[a-z_\/]+/); if (x) models[d.entityId] = x[0]; } });
let particles = {}; bot._client.on('packet', (d, m) => { if (m.name === 'world_particles') { const k = JSON.stringify(d.particle || d.particleId).match(/type":"?([a-z_]+)/); const n = k ? k[1] : '?'; particles[n] = (particles[n] || 0) + 1; } });
let chat = []; bot.on('messagestr', m => chat.push(m));
const body = () => Object.values(bot.entities).find(e => e.name === 'mannequin' && e !== bot.entity && /3e personne/.test(JSON.stringify(e.metadata || [])));
const slotOf = (txt) => bot.inventory.slots.findIndex((i, n) => n >= 36 && n <= 44 && i && JSON.stringify(i.customName || i.name).includes(txt)) - 36;
bot.once('spawn', async () => {
  await sleep(3000);
  bot.chat('/cf stop'); await sleep(800);
  bot.chat('/cf test third-person ruby'); await sleep(5000);
  bot.chat('/tp @s 0.5 65 2.6 180 0'); bot.chat('/cf heal'); await sleep(1500);
  console.log('sante testeur', bot.health);
  for (let i = 0; i < 4; i++) {
    const b = body(); await bot.lookAt(b.position.offset(0, 1.2, 0), true);
    bot.setQuickBarSlot(Math.max(0, slotOf('Crescent Rose'))); await sleep(200);
    chat = []; bot.attack(b); await sleep(250);
    const parts = Object.values(bot.entities).filter(e => e.name === 'item_display' && /pose\/ruby/.test(models[e.id] || '')).length;
    console.log('coup', i + 1, '| sante corps', JSON.stringify(b.metadata[9]), '| pantin', parts, '| chat', chat.join(' | ').slice(0, 140));
    await sleep(700);
    if (parts) break;
  }
  await sleep(1200);
  console.log('sante testeur', bot.health, '| particules', JSON.stringify(particles).slice(0, 300)); console.log('chat apres', chat);
  bot.chat('/cf test third-person'); await sleep(800); bot.quit(); process.exit(0);
});
