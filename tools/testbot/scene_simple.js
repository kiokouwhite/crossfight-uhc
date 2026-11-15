// Prepare une scene pour la camera, en mode test ordinaire : le testeur prend <perso>, lance les commandes
// preparatoires (separees par ";"), se place, puis attend le fichier <go> pour utiliser l'objet <objet>.
// node scene_simple.js <perso> <objet> <go> [commandes] [x y z yaw pitch] [duree apres, ms] [accroupi]
const mineflayer = require('mineflayer');
const fs = require('fs');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const [perso, objet, go, commandes = '', x = '0.5', y = '65', z = '3.5', yaw = '180', pitch = '0', apres = '5000', accroupi = 'non'] = process.argv.slice(2);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const slotOf = (txt) => bot.inventory.slots.findIndex((i, n) => n >= 36 && n <= 44 && i && JSON.stringify(i.customName || i.name).includes(txt)) - 36;
bot.once('spawn', async () => {
  await sleep(1500); if (bot.health <= 0) bot._client.write('client_command', { actionId: 0 }); await sleep(500);
  bot.chat('/cf test'); await sleep(2000); if (!bot.inventory.slots[43] || bot.inventory.slots[43].name !== 'golden_apple') { bot.chat('/cf test'); await sleep(2000); }
  bot.chat('/cf change ' + perso); await sleep(4000);
  if (bot.currentWindow) { bot.closeWindow(bot.currentWindow); await sleep(400); }
  for (const c of commandes.split(';').map(c => c.trim()).filter(Boolean)) { bot.chat(c); await sleep(700); }
  bot.chat(`/tp @s ${x} ${y} ${z} ${yaw} ${pitch}`); await sleep(1200);
  console.log('PRET');
  while (!fs.existsSync(go)) await sleep(100);
  for (const o of objet.split(';')) {
    // nom@attente@tenue : l'objet, le temps d'attente ensuite, et combien de temps le clic droit reste enfonce (un arc qu'on bande).
    const [nom, attente = '900', tenue = '80'] = o.split('@');
    // Une commande (/cf effet resurrection...) au lieu d'un objet.
    if (nom.startsWith('/')) { bot.chat(nom); await sleep(Number(attente)); continue; }
    const s = slotOf(nom); if (s < 0) { console.log('objet introuvable', nom); continue; }
    bot.setQuickBarSlot(s); await sleep(200);
    if (accroupi === 'oui') { bot.setControlState('sneak', true); await sleep(200); }
    if (Number(tenue) > 0) { bot.activateItem(); await sleep(Number(tenue)); bot.deactivateItem(); }
    if (accroupi === 'oui') { await sleep(150); bot.setControlState('sneak', false); }
    await sleep(Number(attente));
  }
  await sleep(Number(apres));
  console.log('FINI');
  bot.chat('/cf clear'); await sleep(300); bot.chat('/cf test'); await sleep(800);
  bot.quit(); process.exit(0);
});
