// Faisceau d'une lootbox : sous elle, le verre teinte du tier, la balise et son socle de fer ; le verre ne se casse pas
// (le testeur essaie en survie) ; une fois la lootbox videe, les blocs d'avant reviennent.
// node lootbox_faisceau.js [tier]
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const tier = process.argv[2] || '2';
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const V = (x, y, z) => bot.entity.position.clone().set(x, y, z);
const nom = (x, y, z) => { const b = bot.blockAt(V(x, y, z)); return b ? b.name : '?'; };
const sous = () => [nom(0.5, 64, 2.5), nom(0.5, 63, 2.5), [-1, 0, 1].flatMap(dx => [-1, 0, 1].map(dz => nom(0.5 + dx, 62, 2.5 + dz))).join(',')];
bot.once('spawn', async () => {
  await sleep(3000);
  bot.chat('/cf test'); await sleep(9000);
  bot.chat('/tp @s 0.5 65 4.5 180 20'); await sleep(1500);
  const avant = sous();
  console.log('avant      : verre', avant[0], '| balise', avant[1], '| socle', avant[2]);
  bot.chat('/cf lootbox ' + tier); await sleep(1200);
  const pendant = sous();
  console.log('lootbox    : bloc', nom(0.5, 65, 2.5), '| verre', pendant[0], '| balise', pendant[1], '| socle', pendant[2]);
  // Casser le verre, en survie.
  bot.chat('/gamemode survival'); await sleep(600);
  bot.chat('/tp @s 0.5 65 4.5 180 60'); await sleep(800);
  try { await bot.dig(bot.blockAt(V(0.5, 64, 3.5)) && bot.blockAt(V(0.5, 64, 2.5)), true); } catch (e) { console.log('  creuser :', e.message); }
  await sleep(800);
  console.log('apres coup : verre', nom(0.5, 64, 2.5));
  bot.chat('/gamemode adventure'); await sleep(400);
  // Vider la lootbox apres sa roulette.
  bot.chat('/tp @s 0.5 65 4.5 180 20'); await sleep(800);
  const zone = Object.values(bot.entities).find(e => e.name === 'interaction' && Math.abs(e.position.z - 2.5) < 0.6);
  bot.activateEntity(zone); await sleep(6000);
  const w = bot.currentWindow;
  if (w) {
    for (const s of w.slots.slice(0, 27).map((s, i) => s ? i : -1).filter(i => i >= 0)) { await bot.clickWindow(s, 0, 1); await sleep(250); }
    bot.closeWindow(w); await sleep(1500);
  }
  const apres = sous();
  console.log('videe      : bloc', nom(0.5, 65, 2.5), '| verre', apres[0], '| balise', apres[1], '| socle', apres[2]);
  console.log('remis comme avant :', JSON.stringify(apres) === JSON.stringify(avant));
  bot.chat('/cf clear'); await sleep(2500);
  bot.chat('/cf test'); await sleep(800);
  bot.quit(); process.exit(0);
});
bot.on('error', e => { console.log('erreur', e.message); process.exit(1); });
