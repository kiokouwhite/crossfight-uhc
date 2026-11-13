// Animations de Blake en troisieme personne : pistolet, katana, Ombre (silhouettes violettes), Discretion (flaque), Instinct Animal.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
let bars = []; bot._client.on('action_bar', d => bars.push(JSON.stringify(d.text).match(/"value":"([^"]{3,})"/g)?.map(x => x.slice(9, -1)).join(' ') || ''));
const models = {}, tints = {}, glows = {};
bot._client.on('entity_metadata', d => { for (const m of d.metadata) { const j = JSON.stringify(m.value || ''); const x = j.match(/crossfight:[a-z_\/]+/); if (x) { models[d.entityId] = x[0]; tints[d.entityId] = /colors/.test(j) && /custom_model_data/.test(j) && /colors":\[\d/.test(j.replace(/\s/g, '')); } if (m.key === 22 || m.key === 0) glows[d.entityId] = m.value; } });
let particles = {}; bot._client.on('packet', (d, m) => { if (m.name === 'world_particles') { const k = JSON.stringify(d.particle || d.particleId).match(/type":"?([a-z_]+)/); const n = k ? k[1] : '?'; particles[n] = (particles[n] || 0) + 1; } });
let sounds = []; bot._client.on('packet', (d, m) => { if (m.name === 'sound_effect' || m.name === 'entity_sound_effect') sounds.push(JSON.stringify(d.sound || d.soundId || d).slice(0, 60)); });
const body = () => Object.values(bot.entities).find(e => e.name === 'mannequin' && e !== bot.entity && /3e personne/.test(JSON.stringify(e.metadata || [])));
const summary = (re) => { const c = {}; for (const e of Object.values(bot.entities)) { if (e.name !== 'item_display' || !re.test(models[e.id] || '')) continue; const k = models[e.id].replace('crossfight:', ''); c[k] = (c[k] || 0) + 1; } return Object.entries(c).map(([k, v]) => v > 1 ? k + 'x' + v : k).join(',') || '(rien)'; };
const slotOf = (txt) => bot.inventory.slots.findIndex((i, n) => n >= 36 && n <= 44 && i && JSON.stringify(i.customName || i.name).includes(txt)) - 36;
async function use(txt, sneak) {
  const s = slotOf(txt); if (s < 0) { console.log('  objet introuvable :', txt); return false; }
  bot.setQuickBarSlot(s); await sleep(250); bars = [];
  if (sneak) { bot.setControlState('sneak', true); await sleep(250); }
  bot.activateItem(); await sleep(80); bot.deactivateItem();
  if (sneak) { await sleep(150); bot.setControlState('sneak', false); }
  return true;
}
async function face() { const b = body(); if (b) await bot.lookAt(b.position.offset(0, 1.5, 0), true); }
bot.once('spawn', async () => {
  await sleep(3000);
  bot.chat('/cf stop'); await sleep(800);
  bot.chat('/cf test third-person blake'); await sleep(5000);
  bot.chat('/tp @s 0.5 65 7 180 0'); bot.chat('/cf heal'); await sleep(1000); await face();
  // Katana : accroupi + clic droit sur l'arme (couperet -> katana).
  await use('Gambol Shroud', true); await sleep(500);
  console.log('katana : pantin', summary(/pose\/blake/), '| epee volante ?', Object.values(bot.entities).filter(e => e.name === 'item_display' && !models[e.id]).length, '| barre', bars.filter(Boolean).join(' / ').slice(0, 60));
  await sleep(2500);
  // Pistolet : encore un changement de mode, puis deux tirs.
  await use('Gambol Shroud', true); await sleep(1500);
  particles = {};
  await use('Gambol Shroud'); await sleep(400);
  console.log('pistolet : pantin', summary(/pose\/blake|blake_pistolet/), '| particules', JSON.stringify(particles).slice(0, 120));
  await sleep(1300); await use('Gambol Shroud'); await sleep(300);
  console.log('2e tir : pantin', summary(/pose\/blake|blake_pistolet/));
  await sleep(2500);
  console.log('apres : pantin', summary(/pose\/blake/));
  // Ombre.
  particles = {};
  await use('Ombre'); await sleep(250);
  const parts = Object.values(bot.entities).filter(e => e.name === 'item_display' && /pose\/blake/.test(models[e.id] || ''));
  console.log('ombre : pantins', summary(/pose\/blake/), '| teintes', parts.filter(e => tints[e.id]).length, '/', parts.length);
  await sleep(1200);
  const parts2 = Object.values(bot.entities).filter(e => e.name === 'item_display' && /pose\/blake/.test(models[e.id] || ''));
  console.log('ombre +1.2s : pantins', parts2.length, '| encore teintes', parts2.filter(e => tints[e.id]).length, '| clones', Object.values(bot.entities).filter(e => e.name === 'mannequin' && e !== body()).length, '| particules', JSON.stringify(particles).slice(0, 160));
  await sleep(4000);
  // Discretion.
  await use('Discretion'); await sleep(300);
  console.log('discretion : pantin', summary(/pose\/blake/), '| flaque', summary(/flaque/));
  await sleep(1500);
  console.log('discretion +1.5s : pantin', summary(/pose\/blake/), '| flaque', summary(/flaque/));
  await use('Discretion'); await sleep(1500);
  // Instinct Animal : lancement (rugissement) puis un bond.
  sounds = [];
  await use('Instinct Animal'); await sleep(400);
  console.log('ultime : pantin', summary(/pose\/blake/), '| sons', sounds.filter(s => /ravager|cat|roar/.test(s)).join(' ').slice(0, 160));
  await sleep(1800);
  await use('Instinct Animal'); await sleep(200);
  console.log('bond : pantin', summary(/pose\/blake/));
  await sleep(1500);
  bot.chat('/cf test third-person'); await sleep(1000);
  bot.quit(); process.exit(0);
});
