const mineflayer = require('mineflayer');
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
async function click(slot, ms) { bot.setQuickBarSlot(slot); await sleep(120); bot.activateItem(); await sleep(60); bot.deactivateItem(); await sleep(ms); }
async function ensureTest() { bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); } }
const zomb = () => Object.values(bot.entities).find(e => e.name === 'zombie');
// Le texte d'un affichage : les morceaux de texte de son composant, avec leur couleur.
const flat = (v, out) => { if (v && typeof v === 'object') { if (typeof v.text === 'object' && v.text && 'value' in v.text) out.push((v.color && v.color.value ? v.color.value : '') + '[' + v.text.value + ']'); else if (typeof v.text === 'string') out.push((v.color || '') + '[' + v.text + ']'); for (const k of Object.keys(v)) flat(v[k], out); } return out; };
let watching = false, last = '';
bot._client.on('entity_metadata', p => { if (!watching) return; const e = bot.entities[p.entityId]; if (!e || e.name !== 'text_display') return; const txt = flat(p.metadata, []).join(' '); if (txt && txt !== last) { last = txt; log('barre', 'y+' + (e.position.y - (bot.players.Tester2 && bot.players.Tester2.entity ? bot.players.Tester2.entity.position.y : zomb() ? zomb().position.y : 0)).toFixed(2), txt.replace(/\|+/g, m => '|x' + m.length)); } });
bot._client.on('spawn_entity', p => { if (watching) setTimeout(() => { const e = bot.entities[p.entityId]; if (e && e.name === 'text_display') log('affichage apparu'); }, 50); });
bot._client.on('entity_destroy', p => { if (watching) for (const id of p.entityIds) { const e = bot.entities[id]; if (e && e.name === 'text_display') log('affichage retire'); } });
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest();
  bot.chat('/cf change marth'); await sleep(1300); bot.chat('/cf heal'); bot.chat('/cf mannequin clear'); await sleep(250); bot.chat('/cf mannequin'); await sleep(700);
  const z = zomb(); bot.chat('/tp @s ' + (z.position.x + 3) + ' ' + z.position.y + ' ' + z.position.z); await sleep(400); await bot.lookAt(z.position.offset(0, 1.2, 0), true);
  watching = true; log('ultime'); await click(8, 5000);
  watching = false; bot.chat('/cf mannequin clear'); await sleep(300);
  // Meme chose sur un joueur (20 points de vie).
  const bot2 = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester2', version: '1.21.10' });
  await new Promise(r => bot2.once('spawn', r)); await sleep(2500);
  if (bot2.health <= 0) { bot2._client.write('client_command', { actionId: 0 }); await sleep(800); }
  bot2.chat('/cf test'); await sleep(2500); bot2.chat('/cf change kaze'); await sleep(1500);
  bot.chat('/tp Tester2 Tester'); await sleep(800); bot.chat('/tp @s ~3 ~ ~'); await sleep(800);
  for (let i = 0; i < 20 && !(bot.players.Tester2 && bot.players.Tester2.entity); i++) await sleep(250);
  if (!(bot.players.Tester2 && bot.players.Tester2.entity)) { log('Tester2 invisible', JSON.stringify(Object.keys(bot.players))); bot2.quit(); bot.quit(); process.exit(1); }
  const t = bot.players.Tester2.entity; await bot.lookAt(t.position.offset(0, 1.2, 0), true);
  watching = true; last = ''; log('ultime sur un joueur, vie', bot2.health); await click(8, 5000); log('vie du joueur apres', bot2.health);
  watching = false; bot2.quit(); await sleep(200); bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800); bot.quit(); process.exit(0);
});
