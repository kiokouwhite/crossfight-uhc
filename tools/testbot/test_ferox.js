// /cf test dans l'arene de Regna Ferox : sol sous les pieds a l'arrivee, arene entiere batie ensuite, /cf clear la remet
// a neuf, troisieme personne aussi dans l'arene.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const bloc = (x, y, z) => { const b = bot.blockAt(bot.entity.position.offset(0, 0, 0).set(x, y, z)); return b ? b.name : '?'; };
const etat = () => `monde ${bot.game.dimension} | pos ${bot.entity.position.x.toFixed(1)} ${bot.entity.position.y.toFixed(1)} ${bot.entity.position.z.toFixed(1)} | sous les pieds ${bloc(Math.floor(bot.entity.position.x), Math.floor(bot.entity.position.y) - 1, Math.floor(bot.entity.position.z))}`;
bot.once('spawn', async () => {
  await sleep(4000);
  bot.chat('/cf test'); await sleep(1200);
  console.log('arrivee     :', etat());
  await sleep(7000);
  console.log('apres 7 s   :', etat());
  // L'arene : la paroi invisible au-dessus de la fosse (y+20), un brasero, le mur de la fosse, les gradins, le toit.
  const y = 64;
  console.log('fosse : centre', bloc(0, y, 0), '| plafond invisible', bloc(0, y + 20, 0), '| pave', bloc(45, y, 5), '| mur', bloc(57, y + 3, 0), '| gradins', bloc(70, y + 12, 0), '| toit', bloc(0, y + 33 + Math.floor(92 * 0.3), 0));
  // Un bloc pose, puis /cf clear.
  bot.chat('/setblock 3 65 3 minecraft:gold_block'); await sleep(800);
  console.log('bloc pose   :', bloc(3, 65, 3));
  bot.chat('/cf clear'); await sleep(7000);
  console.log('apres clear :', bloc(3, 65, 3), '|', etat());
  bot.chat('/cf test third-person marth'); await sleep(7000);
  console.log('3e personne :', etat(), '| centre', bloc(0, y, 0), '| plafond', bloc(0, y + 20, 0));
  const m = Object.values(bot.entities).find(e => e.name === 'mannequin');
  console.log('mannequin   :', m ? m.position.toString() : 'absent');
  bot.chat('/cf test third-person'); await sleep(1500);
  bot.chat('/cf test'); await sleep(800);
  bot.quit(); process.exit(0);
});
bot.on('error', e => { console.log('erreur', e.message); process.exit(1); });
