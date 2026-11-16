// Les Lootbox : chaque objet est recu, compte ses utilisations ; les sorts frappent un mannequin ; une lootbox
// posee contient diamants, or et un objet ; la vague annonce ses positions.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const chat = []; bot.on('messagestr', m => chat.push(m));
const health = async () => { chat.length = 0; bot.chat('/data get entity @e[type=zombie,distance=..40,limit=1,sort=nearest] Health'); await sleep(450); return parseFloat((chat.pop() || '').replace(/.*data: /, '')); };
const uses = it => { const m = JSON.stringify(it.components || it.nbt || '').match(/loot_uses[^0-9]*(\d+)/); return m ? +m[1] : '?'; };
async function use(id) {
  bot.chat('/clear @s'); await sleep(350); bot.chat('/cf loot ' + id); await sleep(500);
  const slot = bot.inventory.slots.findIndex((it, i) => i >= 36 && it); if (slot < 0) return id + ': pas recu';
  bot.setQuickBarSlot(slot - 36); await sleep(250);
  const before = await health(), hp = bot.health;
  bot.activateItem(); await sleep(120); bot.deactivateItem(); await sleep(1500);
  const it = bot.inventory.slots[slot]; const after = await health();
  return `${id}: mannequin ${before} -> ${after}, moi ${hp.toFixed(0)} -> ${bot.health.toFixed(0)}, ${it ? 'reste ' + uses(it) : 'objet parti'}`;
}
bot.once('spawn', async () => {
  await sleep(4000);
  bot.chat('/cf test'); await sleep(3500); if (bot.currentWindow) bot.closeWindow(bot.currentWindow); await sleep(400);
  bot.chat('/cf mannequin 1'); await sleep(1200);
  const dummy = bot.nearestEntity(e => e.name === 'zombie');
  if (dummy) { bot.chat(`/tp @s ${dummy.position.x} ${dummy.position.y} ${dummy.position.z + 4} 180 0`); await sleep(1000); await bot.lookAt(dummy.position.offset(0, 1.2, 0)); }
  bot.chat('/damage @s 8'); await sleep(500);
  for (const id of ['foudre', 'flux', 'vent', 'thoron', 'ruine', 'forseti', 'bolganone', 'mjolnir', 'excalibur', 'valflame', 'javelot', 'gae_bolg', 'gravidus', 'stellaire', 'baton_deesse', 'livre_naga', 'expiration', 'goethe', 'feu', 'soin', 'sauvetage']) {
    if (dummy) { bot.chat(`/tp @s ${dummy.position.x} ${dummy.position.y} ${dummy.position.z + 4} 180 0`); await sleep(500); const d = bot.nearestEntity(e => e.name === 'zombie'); if (d) await bot.lookAt(d.position.offset(0, 1.2, 0)); }
    console.log(await use(id));
  }
  // Une arme de melee : un coup, une utilisation de moins.
  bot.chat('/clear @s'); await sleep(350); bot.chat('/cf loot hache_bronze'); await sleep(500);
  const d2 = bot.nearestEntity(e => e.name === 'zombie');
  if (d2) { bot.chat(`/tp @s ${d2.position.x} ${d2.position.y} ${d2.position.z + 1.6} 180 0`); await sleep(800); const s = bot.inventory.slots.findIndex((it, i) => i >= 36 && it); bot.setQuickBarSlot(s - 36); await sleep(300);
    const b = await health(); await bot.lookAt(d2.position.offset(0, 1, 0)); bot.attack(d2); await sleep(900); console.log('hache_bronze : mannequin', b, '->', await health(), '| reste', uses(bot.inventory.slots[s])); }
  // Une lootbox posee, puis la vague.
  bot.chat('/cf lootbox 2'); await sleep(900);
  const chest = bot.findBlock({ matching: bot.registry.blocksByName.chest.id, maxDistance: 6 });
  if (chest) { const win = await bot.openContainer(chest); await sleep(500); console.log('lootbox :', win.containerItems().map(i => i.name + 'x' + i.count).join(' '), '|', chat.filter(m => /ouvre/.test(m)).pop()); win.close(); }
  else console.log('lootbox : coffre introuvable');
  chat.length = 0; bot.chat('/cf lootbox vague'); await sleep(2500); console.log((chat.filter(m => /Lootbox sont/.test(m)).pop() || 'pas d\'annonce').slice(0, 260));
  bot.chat('/cf mannequin clear'); await sleep(300); bot.chat('/cf test'); await sleep(1500); bot.quit(); process.exit(0);
});
