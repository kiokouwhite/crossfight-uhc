// Eren, Titan Assaillant : la morsure, l'eclair, puis le Titan qui se forme (os, muscles, peau), le rugissement,
// et le skin du Titan porte ensuite par le joueur.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
// Le modele que montre chaque affichage : "eren", "eren_squelette", "eren_muscles", "eren_titan", ou cache.
const look = {};
bot._client.on('entity_metadata', p => { const j = JSON.stringify(p.metadata); const m = j.match(/crossfight:pose\/([a-z_]+?)_(head|body|arm_r|arm_l|leg_r|leg_l)"/); if (m) look[p.entityId] = m[1]; else if (/crossfight:hidden/.test(j)) look[p.entityId] = 'cache'; });
let particles = {}; bot._client.on('packet', (d, m) => { if (m.name === 'world_particles') { const k = JSON.stringify(d.particle || d.particleId).match(/type":"?([a-z_]+)/); const n = k ? k[1] : '?'; particles[n] = (particles[n] || 0) + 1; } });
let sounds = []; bot._client.on('packet', (d, m) => { if (m.name === 'sound_effect' || m.name === 'entity_sound_effect') { const x = JSON.stringify(d).match(/"soundId":(\d+)|minecraft:[a-z_.]+|crossfight:[a-z0-9_.]+/); sounds.push(x ? x[0] : '?'); } });
let lightning = 0; bot._client.on('spawn_entity', d => { if (bot.registry.entities[d.type] && bot.registry.entities[d.type].name === 'lightning_bolt') lightning++; });
let skins = 0; bot._client.on('player_info', () => skins++); bot._client.on('player_remove', () => skins++);
let scale = '?'; bot._client.on('entity_update_attributes', d => { if (d.entityId === bot.entity.id) for (const a of d.properties) if (/scale/.test(JSON.stringify(a.key))) scale = a.value; });
const state = () => {
  const c = {};
  for (const e of Object.values(bot.entities)) if (e.name === 'item_display' && look[e.id]) c[look[e.id]] = (c[look[e.id]] || 0) + 1;
  return 'pantin : ' + (Object.entries(c).map(([k, v]) => k + ' x' + v).join(', ') || 'aucun') + ' | taille ' + scale + ' | eclairs ' + lightning;
};
const slotOf = (txt) => bot.inventory.slots.findIndex((i, n) => n >= 36 && n <= 44 && i && JSON.stringify(i.customName || i.name).includes(txt)) - 36;
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change eren'); await sleep(5000);
  if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(400); }
  bot.chat('/cf ult'); await sleep(600);
  const start = bot.entity.position.clone();
  const s = slotOf('Titan Assaillant'); log('ultime en case', s + 1, '| barre :', bot.inventory.slots.slice(36, 45).map(i => i ? i.name : '-').join(','));
  bot.setQuickBarSlot(s < 0 ? 8 : s); await sleep(300);
  particles = {}; sounds = []; skins = 0;
  bot.activateItem(); await sleep(60); bot.deactivateItem(); t0 = Date.now();
  let last = '';
  for (let i = 0; i < 90; i++) { await sleep(50); const now = state(); if (now !== last) { log(now); last = now; } }
  log('fin |', state(), '| paquets de profil (changement de skin)', skins, '| deplacement', bot.entity.position.distanceTo(start).toFixed(2));
  log('particules', JSON.stringify(particles));
  log('barre du Titan :', bot.inventory.slots.slice(36, 45).map(i => i ? i.name : '-').join(','));
  // La mort du Titan : Eren en sort, a sa taille, avec son skin.
  skins = 0; bot.chat('/kill @s'); await sleep(2500);
  log('apres la mort du Titan |', state(), '| paquets de profil', skins);
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
