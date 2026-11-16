// La musique d'Omori (piste a boucle) : elle part a l'entree dans l'Espace Blanc, s'arrete a la sortie ; et le relais
// d'un fichier a sa suite tombe a l'instant ecrit dans le fichier (ici la suite, qui se relaie elle-meme a 167,526 s).
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const plays = [];
bot._client.on('packet', (data, meta) => {
  const s = JSON.stringify(data);
  if ((meta.name === 'sound_effect' || meta.name === 'entity_sound_effect') && /crossfight:music/.test(s)) {
    const name = s.match(/crossfight:music\.[a-z0-9_]+/)[0];
    plays.push({ name, at: Date.now() });
    log('JOUE', name, 'categorie', data.soundCategory, 'volume', data.volume);
  }
  if (meta.name === 'stop_sound') log('STOP', s.slice(0, 140));
});
const chat = []; bot.on('messagestr', m => chat.push(m));
bot.once('spawn', async () => {
  await sleep(4000); bot.chat('/cf test omori'); await sleep(4000);
  const door = bot.findBlock({ matching: b => b.name === 'pale_oak_door', maxDistance: 12 });
  if (!door) { log('porte absente'); bot.quit(); process.exit(1); }
  log('--- entree dans l\'Espace Blanc');
  await bot.lookAt(door.position.offset(0.5, 0.5, 0.5)); await bot.activateBlock(door); await sleep(5000);
  log('--- sortie (la porte est retiree)');
  bot.chat('/cf test omori'); await sleep(3000);
  log('--- ecoute de la suite seule : elle doit se relayer elle-meme a 167,526 s');
  plays.length = 0;
  bot.chat('/cf musique omori_boucle'); await sleep(171000);
  const mine = plays.filter(p => p.name.endsWith('omori_boucle'));
  if (mine.length >= 2) log('relais apres', ((mine[1].at - mine[0].at) / 1000).toFixed(3), 's (attendu 167,526)');
  else log('pas de relais vu :', mine.length, 'lancement(s)');
  bot.chat('/cf musique stop'); await sleep(1500);
  bot.chat('/cf test'); await sleep(1500); bot.quit(); process.exit(0);
});
