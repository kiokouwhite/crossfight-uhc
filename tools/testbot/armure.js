const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function ensureTest(b) { b.chat('/cf test'); await sleep(2000); if (!b.inventory.slots[43] || b.inventory.slots[43].name !== 'golden_apple') { b.chat('/cf test'); await sleep(2000); } }
const armor = b => [5, 6, 7, 8].map(i => b.inventory.slots[i] ? b.inventory.slots[i].name : 'vide').join(', ');
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest(bot);
  bot.chat('/cf change marth'); await sleep(1300);
  bot.chat('/item replace entity @s armor.head with iron_helmet'); bot.chat('/item replace entity @s armor.chest with diamond_chestplate'); bot.chat('/item replace entity @s armor.legs with diamond_leggings'); bot.chat('/item replace entity @s armor.feet with diamond_boots'); await sleep(800);
  const eye = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester2', version: '1.21.10' });
  await new Promise(r => eye.once('spawn', r)); await sleep(2500);
  if (eye.health <= 0) { eye._client.write('client_command', { actionId: 0 }); await sleep(800); }
  bot.chat('/tp Tester2 Tester'); await sleep(1500);
  const flags = () => { const e = eye.players.Tester && eye.players.Tester.entity; return e && e.metadata ? e.metadata[0] : '?'; };
  const seen = () => { const e = eye.players.Tester && eye.players.Tester.entity; return e ? e.equipment.map(i => i ? i.name : 'vide').join(', ') : '?'; };
  let slots = []; bot._client.on('set_slot', p => { if (p.windowId === 0 && p.slot >= 5 && p.slot <= 8) slots.push(p.slot + ':' + (p.item && p.item.itemCount ? 'objet' : 'vide')); });
  bot._client.on('entity_effect', p => log('  acteur : effet recu', p.effectId)); bot._client.on('remove_entity_effect', p => log('  acteur : effet retire', p.effectId));
  log('avant   | armure cote acteur :', armor(bot), '| vu par le spectateur :', seen(), '| drapeaux :', flags());
  bot.chat('/cf pose marth_contre 2'); await sleep(1000);
  log('pendant | armure cote acteur :', armor(bot), '| vu par le spectateur :', seen(), '| drapeaux (32 = invisible) :', flags(), '| cases envoyees a l acteur :', slots.join(' ')); slots = [];
  await sleep(2500);
  log('apres   | armure cote acteur :', armor(bot), '| vu par le spectateur :', seen(), '| drapeaux :', flags());
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); eye.quit(); bot.quit(); process.exit(0);
});
