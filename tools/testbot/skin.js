const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
async function ensureTest(b) { b.chat('/cf test'); await sleep(2000); if (!b.inventory.slots[43] || b.inventory.slots[43].name !== 'golden_apple') { b.chat('/cf test'); await sleep(2000); } }
// L'adresse de l'image dans la propriete "textures" d'un profil.
const skinOf = props => { const p = (props || []).find(x => x.name === 'textures' || x.key === 'textures'); if (!p) return 'aucun'; const j = JSON.parse(Buffer.from(p.value, 'base64').toString()); return j.textures.SKIN.url.slice(-12) + (j.textures.SKIN.metadata ? ' (bras fins)' : ' (bras larges)') + (p.signature ? ' signe' : ' NON SIGNE'); };
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500); await ensureTest(bot);
  const eye = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester2', version: '1.21.10' });
  let seen = 'rien recu';
  const watch = c => c.on('player_info', p => { for (const d of p.data) if (d.player && d.player.name === 'Tester') seen = skinOf(d.player.properties); });
  watch(eye._client);
  let self = 'rien recu'; bot._client.on('player_info', p => { for (const d of p.data) if (d.player && d.player.name === 'Tester') self = skinOf(d.player.properties); });
  let respawns = 0; bot._client.on('respawn', () => respawns++);
  await new Promise(r => eye.once('spawn', r)); await sleep(2500);
  if (eye.health <= 0) { eye._client.write('client_command', { actionId: 0 }); await sleep(800); }
  bot.chat('/tp Tester2 Tester'); await sleep(1500);
  log('au depart        | vu par le spectateur :', seen, '| vu par le joueur :', self);
  for (const c of ['marth', 'takumi', 'netsu', 'ruby']) {
    const pos = bot.entity.position.clone(); const hp = bot.health;
    bot.chat('/cf change ' + c); await sleep(2200);
    const inv = bot.inventory.slots.filter(Boolean).length;
    log(('/cf change ' + c).padEnd(16), '| vu par le spectateur :', seen, '| vu par le joueur :', self, '| deplacement', bot.entity.position.distanceTo(pos).toFixed(2), '| vie', hp, '->', bot.health, '| objets', inv, '| rechargements', respawns);
  }
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(2000);
  log('sortie du mode test | vu par le spectateur :', seen, '| vu par le joueur :', self);
  eye.quit(); bot.quit(); process.exit(0);
});
