// Troisieme personne : cas particuliers (variante accroupie, menu, mort, /cf clear, /cf pose, sortie et retour).
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
let t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(1) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
let msgs = []; bot.on('message', m => msgs.push(m.toString()));
let sounds = []; bot._client.on('packet', (d, m) => { if (/sound/.test(m.name)) { const x = JSON.stringify(d).match(/crossfight:[a-z0-9_.]+/); if (x) sounds.push(x[0].slice(11)); } });
let meta = {}; bot._client.on('entity_metadata', d => { meta[d.entityId] = (meta[d.entityId] || []).concat(d.metadata.map(x => x.key + '=' + String(JSON.stringify(x.value)).slice(0, 40))); });
bot.on('death', () => { setTimeout(() => bot._client.write('client_command', { actionId: 0 }), 300); });
const dim = async () => { msgs = []; bot.chat('/data get entity @s Dimension'); await sleep(700); return (msgs.join(' ').match(/minecraft:[a-z_0-9]+/) || ['?'])[0]; };
const body = () => Object.values(bot.entities).find(e => e.name === 'mannequin' && e !== bot.entity && /3e personne/.test(JSON.stringify(e.metadata || [])));
const hotbar = () => [...Array(9).keys()].map(k => { const s = bot.inventory.slots[36 + k]; return s ? s.name : '-'; }).join(',');
const use = async (k, sneak) => { bot.setQuickBarSlot(k); await sleep(200); if (sneak) { bot.setControlState('sneak', true); await sleep(300); } sounds = []; msgs = []; bot.activateItem(); await sleep(80); bot.deactivateItem(); await sleep(1500); if (sneak) bot.setControlState('sneak', false); };
bot.once('spawn', async () => {
  await sleep(3000); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(800);
  log('a la connexion : monde', await dim(), '| mannequin', !!body(), '| hotbar', hotbar());
  if (!body()) { bot.chat('/cf test third-person'); await sleep(4000); }
  // 1. Variante accroupie : Takumi (le mannequin s'accroupit aussi).
  bot.chat('/cf change takumi'); await sleep(1500);
  let m = body(); await bot.lookAt(m.position.offset(0, 1.5, 0), true);
  bot.setControlState('sneak', true); await sleep(600);
  const pose = (meta[m.id] || []).filter(x => /^6=/.test(x)).pop(); log('pose du mannequin quand le testeur s\'accroupit :', pose || '(pas de paquet)');
  bot.setControlState('sneak', false); await sleep(600);
  // 2. Un menu de capacite (Byleth : Tutorat) s'ouvre chez le testeur.
  bot.chat('/cf change byleth'); await sleep(1500);
  m = body(); await bot.lookAt(m.position.offset(0, 1.5, 0), true);
  for (let k = 1; k <= 8; k++) { const s = bot.inventory.slots[36 + k]; if (!s) continue; await use(k, false); if (bot.currentWindow) { log('menu ouvert par l\'objet', k, s.name, ':', bot.currentWindow.title.toString().slice(0, 80)); bot.closeWindow(bot.currentWindow); await sleep(800); log('apres fermeture : messages', msgs.join(' ').slice(0, 160)); break; } }
  // 3. /cf pose : c'est le mannequin qui la prend.
  bot.chat('/cf change marth'); await sleep(1500);
  msgs = []; bot.chat('/cf pose marth_contre 2'); await sleep(800);
  const displays = Object.values(bot.entities).filter(e => e.name === 'item_display' && body() && e.position.distanceTo(body().position) < 2).length;
  log('/cf pose :', msgs.join(' ').slice(0, 120), '| affichages autour du mannequin :', displays);
  await sleep(2500);
  // 4. /cf clear : le mannequin reste, revient a sa place.
  const before = body(); bot.chat('/cf clear'); await sleep(1500);
  log('/cf clear : meme mannequin', !!body() && before && body().id === before.id, '| position', body() && body().position.toString());
  // 5. Mort du testeur : reapparition face au mannequin, objets rendus.
  bot.chat('/kill @s'); await sleep(2500);
  log('apres la mort : monde', await dim(), '| position', bot.entity.position.toString(), '| hotbar', hotbar());
  // 6. Commande de test appliquee au mannequin.
  bot.chat('/cf change kura'); await sleep(1500); msgs = []; bot.chat('/cf kills 6'); await sleep(800); log('/cf kills :', msgs.join(' ').slice(0, 100));
  // 7. Sortie : le testeur reprend le personnage.
  msgs = []; bot.chat('/cf test third-person'); await sleep(2500);
  log('sortie : monde', await dim(), '| mannequin', !!body(), '| hotbar', hotbar(), '| msg', msgs.filter(x => /Retour|incarnes/.test(x)).join(' ').slice(0, 140));
  // 8. Retour en troisieme personne avec le personnage courant.
  msgs = []; bot.chat('/cf test third-person'); await sleep(3000);
  log('retour : monde', await dim(), '| mannequin', !!body(), '| hotbar', hotbar(), '| msg', msgs.filter(x => /incarne/.test(x)).join(' ').slice(0, 140));
  bot.quit(); process.exit(0);
});
