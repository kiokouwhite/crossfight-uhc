// /cf demo contre et /cf demo falchion : l'adversaire frappe au Contre (riposte, sans degats pour Marth) ; il attaque
// quand on le frappe et s'arrete a 5 coeurs.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', async d => {
  bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 3 }); await sleep(100);
  bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 0 });
});
const chat = []; bot._client.on('system_chat', d => chat.push(JSON.stringify(d.content).replace(/\{[^}]*"text":/g, '').replace(/[{}"\[\]]/g, '').slice(0, 160)));
const adversaire = () => Object.values(bot.entities).find(e => e.name === 'mannequin' && e.position.distanceTo(bot.entity.position) < 8);
bot.once('spawn', async () => {
  await sleep(5000);
  bot.chat('/cf test'); await sleep(2500);
  bot.chat('/cf change marth'); await sleep(3500);
  bot.chat('/cf heal'); await sleep(500);
  chat.length = 0;
  bot.chat('/cf demo contre'); await sleep(1500);
  let m = adversaire();
  console.log('contre : adversaire', m ? 'present a ' + m.position.distanceTo(bot.entity.position).toFixed(1) + ' blocs' : 'ABSENT', '|', chat.slice(-1)[0]);
  for (let i = 0; i < 2; i++) {
    const hp = bot.health; const avant = m && m.position.clone();
    chat.length = 0;
    bot.setQuickBarSlot(1); await sleep(200); bot.activateItem(); await sleep(60); bot.deactivateItem();
    await sleep(900);
    const apres = adversaire();
    console.log(`  contre ${i + 1} : vie ${hp} -> ${bot.health} | adversaire a ${apres ? apres.position.distanceTo(bot.entity.position).toFixed(1) : '?'} blocs (projete) | ${chat.join(' / ').slice(0, 200)}`);
    await sleep(5500);
    m = adversaire();
    console.log(`  retour : adversaire a ${m ? m.position.distanceTo(bot.entity.position).toFixed(1) : '?'} blocs`);
  }
  bot.chat('/cf demo falchion'); await sleep(1500);
  bot.chat('/cf heal'); await sleep(800);
  m = adversaire();
  console.log('falchion : adversaire', m ? 'present' : 'ABSENT', '| vie', bot.health);
  await sleep(2000);
  console.log('  sans le frapper, 2 s : vie', bot.health);
  await bot.lookAt(m.position.offset(0, 1.5, 0), true); bot.attack(m);
  const t0 = Date.now(); let derniere = bot.health;
  while (Date.now() - t0 < 14000) { await sleep(500); if (bot.health !== derniere) { console.log(`  ${((Date.now() - t0) / 1000).toFixed(1)} s : vie ${bot.health}`); derniere = bot.health; } }
  console.log('  fin : vie', bot.health, '(5 coeurs = 10)');
  bot.chat('/cf demo falchion'); await sleep(800);
  console.log('retire :', adversaire() ? 'encore la' : 'parti');
  bot.chat('/cf test'); await sleep(800);
  bot.quit(); process.exit(0);
});
bot.on('error', e => { console.log('erreur', e.message); process.exit(1); });
