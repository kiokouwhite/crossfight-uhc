// Falchion : pendant la regeneration (3 s), les coups de Marth ne portent pas ; apres, si. Et l'adversaire de
// /cf demo falchion frappe avec le modele articule (des item_display apparaissent a chaque coup).
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', async d => { bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 3 }); await sleep(100); bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 0 }); });
const texte = c => { const j = JSON.stringify(c || ''); return (j.match(/"(?:text|value)":"([^"]*)"/g) || []).map(x => x.split(':"')[1].slice(0, -1)).join(''); };
const cible = () => Object.values(bot.entities).find(e => e.name === 'zombie' || (e.name === 'mannequin' && /❤/.test(texte((e.metadata || [])[2]))));
const vie = () => { const c = cible(); return c ? (texte(c.metadata[2]).match(/(\d+)%/) || [])[1] : '?'; };
bot.once('spawn', async () => {
  await sleep(4000);
  bot.chat('/cf test'); await sleep(3000);
  bot.chat('/cf change marth'); await sleep(3000);
  bot.chat('/cf mannequin 1'); await sleep(1200);
  let c = cible(); console.log('cible', c && c.name, 'vie', vie());
  bot.chat('/tp @s ' + (c.position.x) + ' ' + c.position.y + ' ' + (c.position.z + 2.5)); await sleep(800);
  c = cible(); await bot.lookAt(c.position.offset(0, 1.4, 0), true);
  bot.setQuickBarSlot(0); await sleep(200);
  bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(400);
  for (let i = 0; i < 3; i++) { bot.attack(cible()); await sleep(700); }
  console.log('pendant la regeneration, 3 coups : vie de la cible', vie());
  await sleep(1500);
  for (let i = 0; i < 2; i++) { bot.attack(cible()); await sleep(700); }
  console.log('apres la regeneration, 2 coups : vie de la cible', vie());
  bot.chat('/cf mannequin clear'); await sleep(500);
  bot.chat('/cf demo falchion'); await sleep(1500);
  const a = Object.values(bot.entities).find(e => e.name === 'mannequin');
  await bot.lookAt(a.position.offset(0, 1.5, 0), true); bot.attack(a);
  let max = 0; const t0 = Date.now();
  while (Date.now() - t0 < 3000) { await sleep(100); max = Math.max(max, Object.values(bot.entities).filter(e => e.name === 'item_display' && e.position.distanceTo(bot.entity.position) < 8).length); }
  console.log('demo : pieces du modele articule pendant les coups (max)', max, '| vie du bot', bot.health);
  bot.chat('/cf demo falchion'); await sleep(500); bot.chat('/cf test'); await sleep(800);
  bot.quit(); process.exit(0);
});
bot.on('error', e => { console.log('erreur', e.message); process.exit(1); });
