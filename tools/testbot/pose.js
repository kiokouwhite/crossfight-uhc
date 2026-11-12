const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function click(b, slot, ms) { b.setQuickBarSlot(slot); await sleep(120); b.activateItem(); await sleep(60); b.deactivateItem(); await sleep(ms); }
async function ensureTest(b) { b.chat('/cf test'); await sleep(2000); if (!b.inventory.slots[43] || b.inventory.slots[43].name !== 'golden_apple') { b.chat('/cf test'); await sleep(2000); } }
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest(bot);
  bot.chat('/cf change marth'); await sleep(1300); bot.chat('/cf heal'); bot.chat('/cf mannequin clear'); await sleep(300);
  // Le spectateur.
  const eye = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester2', version: '1.21.10' });
  await new Promise(r => eye.once('spawn', r)); await sleep(2500);
  if (eye.health <= 0) { eye._client.write('client_command', { actionId: 0 }); await sleep(800); }
  bot.chat('/tp Tester2 Tester'); await sleep(1500);
  let sounds = []; eye._client.on('packet', (data, meta) => { if (meta.name === 'sound_effect' || meta.name === 'entity_sound_effect') { const m = JSON.stringify(data).match(/crossfight:[a-z0-9_.]+/); if (m) sounds.push(m[0].replace('crossfight:', '')); } });
  let watching = false; const stats = { spawn: 0, gone: 0, meta: 0, equip: [], effects: [], models: new Set() };
  const isDisp = id => eye.entities[id] && eye.entities[id].name === 'item_display';
  eye._client.on('spawn_entity', p => { if (watching) setTimeout(() => { if (isDisp(p.entityId)) stats.spawn++; }, 30); });
  eye._client.on('entity_destroy', p => { if (watching) for (const id of p.entityIds) if (isDisp(id)) stats.gone++; });
  eye._client.on('entity_metadata', p => { if (!watching || !isDisp(p.entityId)) return; stats.meta++; const m = JSON.stringify(p.metadata).match(/crossfight:pose\/[a-z_]+|iron_sword|diamond_sword/g); if (m) m.forEach(x => stats.models.add(x)); });
  eye._client.on('entity_equipment', p => { if (watching && eye.players.Tester && eye.players.Tester.entity && p.entityId === eye.players.Tester.entity.id) stats.equip.push(p.equipments.map(e => e.slot + ':' + (e.item && e.item.itemCount ? 'objet' : 'vide')).join(',')); });
  eye._client.on('entity_effect', p => { if (watching) stats.effects.push('+effet ' + p.effectId); });
  eye._client.on('remove_entity_effect', p => { if (watching) stats.effects.push('-effet ' + p.effectId); });
  const report = name => { log(name, JSON.stringify({ apparus: stats.spawn, retires: stats.gone, majs: stats.meta, modeles: [...stats.models].sort(), equipement: stats.equip, effets: stats.effects, sons: [...new Set(sounds)] })); sounds = []; stats.spawn = stats.gone = stats.meta = 0; stats.equip = []; stats.effects = []; stats.models = new Set(); };
  watching = true;
  bot.chat('/cf pose marth_contre 2'); await sleep(4000); report('/cf pose marth_contre 2');
  await click(bot, 1, 2500); report('Contre sans coup recu');
  bot.chat('/cf mannequin attaque'); await sleep(900);
  const z = Object.values(bot.entities).find(e => e.name === 'zombie'); bot.chat('/tp @s ' + (z.position.x + 1.5) + ' ' + z.position.y + ' ' + z.position.z); await sleep(500);
  let hit = false; bot.on('message', m => { if (/Contre reussi/.test(m.toString())) hit = true; }); bot._client.on('action_bar', p => { if (/Contre reussi/.test(JSON.stringify(p))) hit = true; }); bot._client.on('set_action_bar_text', p => { if (/Contre reussi/.test(JSON.stringify(p))) hit = true; });
  for (let i = 0; i < 6 && !hit; i++) { await click(bot, 1, 1800); }
  await sleep(1500); report('Contre face a un mannequin qui attaque (contre reussi : ' + hit + ')');
  log('affichages restants chez le spectateur :', Object.values(eye.entities).filter(e => e.name === 'item_display').length);
  log('effets de Tester a la fin :', JSON.stringify(Object.keys(bot.entity.effects || {})));
  watching = false; bot.chat('/cf mannequin clear'); await sleep(200); bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); eye.quit(); bot.quit(); process.exit(0);
});
