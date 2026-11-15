// Les animations de Valorant : pour chaque capacite de Jett, Neon, Sage et Sova, le pantin prend-il la pose,
// les images d'effets (couteaux, orbes, bourrasques) apparaissent-elles, et tout disparait-il ensuite ?
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const kind = {};
bot._client.on('entity_metadata', p => {
  const j = JSON.stringify(p.metadata);
  const m = j.match(/crossfight:(pose|glyph)\/([a-z_]+)/);
  if (m) kind[p.entityId] = m[1] === 'pose' ? 'pantin' : m[2]; else if (/"itemId":\d+/.test(j) && !kind[p.entityId]) kind[p.entityId] = 'objet';
});
const state = () => {
  const c = {};
  for (const e of Object.values(bot.entities)) {
    if (e.name === 'item_display') c[kind[e.id] || '?'] = (c[kind[e.id] || '?'] || 0) + 1;
    else if (e.name === 'block_display' || e.name === 'allay') c[e.name] = (c[e.name] || 0) + 1;
  }
  return Object.entries(c).map(([k, v]) => k + ' x' + v).join(', ') || 'rien';
};
// Les cases sont relevees une fois par personnage : pendant une pose, l'objet tenu est remplace chez le joueur par un objet invisible.
let slots = {};
let chat = []; bot.on('messagestr', m => chat.push(m));
const names = () => bot.inventory.slots.slice(36, 45).map(i => i ? (JSON.stringify(i.customName || '').match(/"value":"([^"]+)"/g) || []).map(x => x.slice(9, -1)).join('') || i.name : '-');
const slotOf = (txt) => { if (!(txt in slots)) slots[txt] = names().findIndex(n => n.includes(txt)); return slots[txt]; };
async function use(txt, hold = 60) { const s = slotOf(txt); if (s < 0) { console.log('   objet introuvable :', txt); return false; } bot.setQuickBarSlot(s); await sleep(250); bot.activateItem(); await sleep(hold); bot.deactivateItem(); return true; }
async function show(label, txt, { hold = 60, after = [150, 2500] } = {}) {
  if (!await use(txt, hold)) return;
  const out = [];
  let t = 0;
  for (const a of after) { await sleep(a - t); t = a; out.push('+' + a + ' ms : ' + state()); }
  console.log(' ' + label.padEnd(26), out.join(' | '));
}
async function become(who) {
  chat = []; bot.chat('/cf change ' + who); await sleep(4500); if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(400); }
  bot.chat('/tp @s 0.5 65 0.5 0 0'); await sleep(1200); slots = {};
  console.log(who.toUpperCase(), '| barre :', names().join(' / ').slice(0, 170), '|', state(), '| messages :', chat.slice(-2).join(' // ').slice(0, 160));
}
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  const only = process.argv.slice(2);
  const wanted = who => only.length === 0 || only.includes(who);
  if (wanted('jett')) {
  await become('jett');
  await show('Vent Arriere', 'Vent Arriere', { after: [150, 900] });
  await show('Courant Ascendant', 'Courant Ascendant', { after: [200, 1800] });
  bot.chat('/tp @s 0.5 85 0.5 0 0'); await sleep(300);
  await show('Flottement (en chute)', 'Flottement', { after: [700, 1500] });
  await use('Flottement'); await sleep(600); console.log('   Flottement coupe :', state()); bot.chat('/tp @s 0.5 65 0.5 0 0'); await sleep(1500);
  await show('Tempete de Lames', 'Tempete de Lames', { after: [300, 1200] });
  await show('   un lancer', 'Tempete de Lames', { after: [120, 700] });
  for (let i = 0; i < 4; i++) { await use('Tempete de Lames'); await sleep(350); }
  await sleep(800); console.log('   5 couteaux lances :', state());
  }
  if (wanted('neon')) {
  await become('neon');
  await show('Eclair Relais', 'Eclair Relais', { after: [150, 900] });
  await show('Voie Rapide', 'Voie Rapide', { after: [200, 1200] });
  await sleep(5000);
  await show('Vitesse Superieure', 'Vitesse Superieure', { after: [120, 700] }); await use('Vitesse Superieure'); await sleep(400);
  await show('Surmultipliee', 'Surmultipliee', { after: [300, 1500] });
  }
  if (wanted('sage')) {
  await become('sage');
  bot.chat('/cf mannequin allie'); await sleep(900);
  await show('Orbe de Soin', 'Orbe de Soin', { after: [200, 500, 4000] });
  await show('Orbe de Lenteur', 'Orbe de Lenteur', { after: [120, 350, 1200] });
  await show('Orbe Barriere', 'Orbe Barriere', { after: [200, 1500] });
  bot.chat('/cf effet resurrection'); await sleep(400); console.log(' /cf effet resurrection    +400 ms :', state()); await sleep(2200); console.log('                           +2600 ms :', state());
  bot.chat('/cf mannequin clear'); await sleep(400);
  }
  if (wanted('sova')) {
  await become('sova');
  await show('Drone Rapace', 'Drone Rapace', { after: [200, 1200] });
  await show('Electrofleche (arc bande)', 'Electrofleche', { hold: 900, after: [120, 900] });
  await show('Fleche de reconnaissance', 'Fleche de reco', { hold: 900, after: [120, 900] });
  await show('Fureur du Chasseur', 'Fureur du Chasseur', { after: [300, 1100] });
  await show('   un tir', 'Fureur du Chasseur', { after: [250, 600, 1500] });
  await sleep(6000); console.log('   fin :', state());
  }
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
