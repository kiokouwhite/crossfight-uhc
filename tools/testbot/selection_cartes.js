// Ecran de selection : cartes, halo au survol, choix.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const models = {}, scale = {}, texts = {};
bot._client.on('entity_metadata', d => { for (const m of d.metadata) { const j = JSON.stringify(m.value || ''); const x = j.match(/crossfight:[a-z_\/]+/); if (x) models[d.entityId] = x[0]; if (m.key === 12 && m.value) scale[d.entityId] = m.value.x; if (typeof j === 'string' && /CHOISI|TON CHOIX/.test(j)) texts[d.entityId] = j.slice(0, 80); } });
const disp = re => Object.values(bot.entities).filter(e => e.name === 'item_display' && re.test(models[e.id] || ''));
const summary = re => { const c = {}; for (const e of disp(re)) { const k = models[e.id].replace('crossfight:select/', ''); c[k] = (c[k] || 0) + 1; } return Object.entries(c).map(([k, v]) => v > 1 ? k + 'x' + v : k).join(', ') || '(rien)'; };
bot.once('spawn', async () => {
  await sleep(3000);
  bot.chat('/cf stop'); await sleep(800);
  bot.chat('/cf test selection fire_emblem'); await sleep(6000);
  console.log('affichages :', summary(/select\//));
  const cards = disp(/select\/card\/[a-z]+/);
  for (const c of cards) {
    await bot.lookAt(c.position.offset(0, 0, 0), true); await sleep(700);
    const glows = disp(/card_glow/).map(e => (scale[e.id] || 0).toFixed(2)).join(' ');
    console.log('vise', models[c.id].split('/').pop(), '-> taille de la carte', (scale[c.id] || 0).toFixed(2), '| halos', glows);
  }
  const target = cards[1];
  await bot.lookAt(target.position, true); await sleep(500);
  bot.activateItem(); await sleep(80); bot.deactivateItem(); await sleep(1200);
  console.log('apres le clic :', Object.values(texts).join(' | ') || '(pas de tampon)');
  bot.chat('/cf test third-person'); await sleep(500);
  bot.quit(); process.exit(0);
});
