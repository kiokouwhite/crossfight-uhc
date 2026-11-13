const mineflayer = require('mineflayer');
const { Vec3 } = require('vec3');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
let msgs = []; bot.on('message', m => msgs.push(m.toString()));
const models = {}; const texts = {}; const glow = {};
bot._client.on('entity_metadata', d => {
  for (const m of d.metadata) {
    const js = JSON.stringify(m.value || '');
    const im = js.match(/crossfight:select\/[a-z_\/]+/); if (im) models[d.entityId] = im[0].slice(17);
    if (m.key === 23) texts[d.entityId] = js.replace(/"(type|value)":/g, '');
    if (m.key === 0 && typeof m.value === 'number') glow[d.entityId] = (m.value & 0x40) !== 0;
  }
});
const dim = async () => { msgs = []; bot.chat('/data get entity @s Dimension'); await sleep(700); return (msgs.join(' ').match(/minecraft:[a-z_0-9]+/) || ['?'])[0]; };
bot.once('spawn', async () => {
  await sleep(3000);
  bot.chat('/cf stop'); await sleep(1000);
  bot.chat('/cf test selection fire_emblem'); await sleep(4000);
  console.log('monde', await dim());
  const byModel = {}; for (const [id, m] of Object.entries(models)) byModel[m] = (byModel[m] || 0) + 1;
  console.log('modeles des affichages :', JSON.stringify(byModel));
  // Vise l'illustration de Corrin (2e panneau en partant de la gauche).
  const corrin = Object.entries(models).find(([id, m]) => m === '/art/corrin');
  const ent = corrin && bot.entities[corrin[0]];
  if (ent) await bot.lookAt(ent.position.offset(0, 0.5, 0), true); await sleep(800);
  console.log('Corrin brille :', !!glow[corrin && corrin[0]], '| fiche :', (Object.values(texts).find(t => /Voie/.test(t)) || '(aucune)').replace(/[{}"\[\]]|compound|string|list|byte|int|text:|extra:|color:|bold:|italic:/g, '').slice(0, 160));
  msgs = []; bot.swingArm(); await sleep(1200);
  console.log('choix :', msgs.filter(x => /incarnes|Ecran/.test(x)).join(' ').slice(0, 120));
  await sleep(2500);
  console.log('apres le choix : monde', await dim(), '| main', bot.inventory.slots[36] ? bot.inventory.slots[36].name : '-');
  bot.quit(); process.exit(0);
});
