// Prepare une scene pour la camera : le testeur passe en troisieme personne avec <perso>, se place, puis attend
// le fichier <go> pour utiliser l'objet dont le nom contient <objet> (une ou plusieurs fois).
const mineflayer = require('mineflayer');
const fs = require('fs');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const [perso, objet, go, z = '3.5', fois = '1', ecart = '900', accroupi = 'non'] = process.argv.slice(2);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
bot.on('death', () => setTimeout(() => bot._client.write('client_command', { actionId: 0 }), 300));
const body = () => Object.values(bot.entities).find(e => e.name === 'mannequin' && e !== bot.entity && /3e personne/.test(JSON.stringify(e.metadata || [])));
const slotOf = (txt) => bot.inventory.slots.findIndex((i, n) => n >= 36 && n <= 44 && i && JSON.stringify(i.customName || i.name).includes(txt)) - 36;
bot.once('spawn', async () => {
  await sleep(2500);
  bot.chat('/cf stop'); await sleep(800);
  bot.chat('/cf test third-person ' + perso); await sleep(5000);
  bot.chat(`/tp @s 0.5 65 ${z} 180 0`); bot.chat('/cf heal'); await sleep(1200);
  const b = body(); if (b) await bot.lookAt(b.position.offset(0, 1.5, 0), true);
  console.log('PRET');
  while (!fs.existsSync(go)) await sleep(100);
  for (let i = 0; i < Number(fois); i++) {
    const s = slotOf(objet); if (s < 0) { console.log('objet introuvable', objet); break; }
    bot.setQuickBarSlot(s); await sleep(200);
    if (accroupi === 'oui') { bot.setControlState('sneak', true); await sleep(200); }
    bot.activateItem(); await sleep(80); bot.deactivateItem();
    if (accroupi === 'oui') { await sleep(150); bot.setControlState('sneak', false); }
    await sleep(Number(ecart));
  }
  await sleep(4000);
  console.log('FINI');
  bot.chat('/cf test third-person'); await sleep(800);
  bot.quit(); process.exit(0);
});
