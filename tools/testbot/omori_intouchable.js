// Omori : aucun coup ne compte pendant ses attaques (sa barre dit "intouchable"), et "Je ne peux pas le faire"
// retire un coeur par seconde pendant 10 secondes, sans deplacer le joueur.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
let main = '', name = '', watch = false;
const titles = [];
const note = b => {
  const t = b.title && b.title.toString ? b.title.toString() : String(b.title);
  if (/^OMORI/.test(t)) { if (t !== main) { main = t; titles.push(t); if (watch) log('barre :', t); } }
  else if (t && t !== name) { name = t; if (watch) log('attaque annoncee :', t); }
};
bot.on('bossBarCreated', note); bot.on('bossBarUpdated', note);
const left = t => { const m = /(\d+) coups/.exec(t); return m ? +m[1] : null; };
const boss = () => bot.nearestEntity(e => e.name === 'zombie');
async function approach() {
  for (let i = 0; i < 60; i++) {
    const b = boss(); if (!b) { await sleep(100); continue; }
    if (bot.entity.position.distanceTo(b.position) < 2.4) break;
    await bot.lookAt(b.position.offset(0, 1.2, 0), true); bot.setControlState('forward', true); await sleep(100);
  }
  bot.setControlState('forward', false);
}
bot.once('spawn', async () => {
  await sleep(4000); bot.chat('/cf test omori'); await sleep(4000);
  const door = bot.findBlock({ matching: b => b.name === 'pale_oak_door', maxDistance: 12 });
  if (!door) { log('porte absente'); bot.quit(); process.exit(1); }
  await bot.lookAt(door.position.offset(0.5, 0.5, 0.5)); await bot.activateBlock(door); await sleep(2500);
  await approach();
  log('--- 30 s de coups sans arret (un toutes les 300 ms), a portee de main');
  watch = true;
  let counted = 0, refused = 0, during = 0, outside = 0;
  for (let i = 0; i < 100; i++) {
    const b = boss();
    if (i % 10 === 0) bot.chat('/cf heal');
    if (b && bot.entity.position.distanceTo(b.position) < 3.2) {
      const before = left(main), was = /intouchable/.test(main);
      await bot.lookAt(b.position.offset(0, 1.2, 0), true); bot.attack(b); await sleep(300);
      const after = left(main);
      if (was && /intouchable/.test(main)) { during++; if (after !== before) counted++; else refused++; }
      else if (!was && !/intouchable/.test(main)) outside++;
    } else { await approach(); await sleep(200); }
  }
  watch = false;
  log(`coups portes pendant une attaque : ${during}, dont comptes : ${counted} (attendu 0), refuses : ${refused}`);
  log(`coups portes hors attaque : ${outside} ; barre au debut "${titles[0]}", a la fin "${main}"`);

  log('--- cinquieme phase : Suicide, puis "Je ne peux pas le faire"');
  bot.chat('/cf omori phase 5');
  for (let i = 0; i < 400 && name !== 'Je ne peux pas le faire'; i++) { if (i % 20 === 0) bot.chat('/cf heal'); await sleep(100); }
  if (name !== 'Je ne peux pas le faire') { log('attaque jamais annoncee'); bot.quit(); process.exit(1); }
  bot.chat('/cf heal'); await sleep(900);
  const from = bot.entity.position.clone();
  let last = bot.health, drops = [];
  const onHealth = () => { if (bot.health < last - 0.5) drops.push({ at: Date.now(), lost: +(last - bot.health).toFixed(1) }); last = bot.health; };
  bot.on('health', onHealth);
  log('vie au depart :', bot.health);
  await sleep(5300); bot.chat('/cf heal'); log('vie a mi-chemin :', bot.health, '(soin pour tenir jusqu\'au bout)');
  await sleep(6200);
  bot.removeListener('health', onHealth);
  log('pertes de vie :', drops.length, '| chacune :', drops.map(d => d.lost).join(' '), '| ecarts (s) :', drops.slice(1).map((d, i) => ((d.at - drops[i].at) / 1000).toFixed(2)).join(' '));
  log('deplacement pendant l\'attaque :', bot.entity.position.distanceTo(from).toFixed(2), 'bloc(s) | vie a la fin :', bot.health);
  bot.chat('/cf test omori'); await sleep(1500); bot.chat('/cf test'); await sleep(1500); bot.quit(); process.exit(0);
});
