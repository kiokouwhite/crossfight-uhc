const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
const bot2 = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester2', version: '1.21.10' });
function watch(b, name) {
  b._client.on('packet', (data, meta) => {
    const s = JSON.stringify(data);
    if ((meta.name === 'sound_effect' || meta.name === 'entity_sound_effect') && /crossfight:music/.test(s)) log(name, 'JOUE', s.match(/crossfight:music\.[a-z0-9_]+/)[0], 'cat', data.soundCategory, 'vol', data.volume);
    if (meta.name === 'stop_sound') log(name, 'STOP', s.slice(0, 120));
  });
}
watch(bot, 'Tester '); watch(bot2, 'Tester2');
async function ensureTest(b) { b.chat('/cf test'); await sleep(2000); if (!b.inventory.slots[43] || b.inventory.slots[43].name !== 'golden_apple') { b.chat('/cf test'); await sleep(2000); } }
let ready = 0;
const go = async () => {
  if (++ready < 2) return;
  await sleep(1500);
  for (const b of [bot, bot2]) if (b.health <= 0) b._client.write('client_command', { actionId: 0 });
  await sleep(500); await ensureTest(bot); await ensureTest(bot2);
  bot.chat('/cf change marth'); bot2.chat('/cf change ruby'); await sleep(2000);
  bot.chat('/tp Tester2 Tester'); await sleep(800);
  const target = () => bot.players['Tester2'] && bot.players['Tester2'].entity;
  log('--- premier coup (fire_emblem contre rwby)');
  bot.setQuickBarSlot(0); if (target()) { await bot.lookAt(target().position.offset(0, 1, 0), true); bot.attack(target()); } else log('cible introuvable');
  await sleep(4000);
  log('--- coups entretenus pendant 14 s (la piste de test dure 10 s : elle doit reboucler)');
  for (let i = 0; i < 7; i++) { if (target()) bot.attack(target()); bot.chat('/cf heal'); bot2.chat('/cf heal'); await sleep(2000); }
  log('--- plus aucun coup : arret attendu apres 20 s de silence');
  await sleep(24000);
  log('--- ecoute hors combat : /cf musique');
  bot.chat('/cf musique library_of_ruina_inspired_sketch'); await sleep(1500); bot.chat('/cf musique stop'); await sleep(1000);
  bot.chat('/cf test'); bot2.chat('/cf test'); await sleep(1000); bot.quit(); bot2.quit(); process.exit(0);
};
bot.once('spawn', go); bot2.once('spawn', go);
