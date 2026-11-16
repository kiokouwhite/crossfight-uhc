// Regna Ferox : deux solitaires en partie, la proposition, les deux acceptent, l'arene, l'un met l'autre a terre :
// le vaincu perd une vie, le vainqueur recoit une Lootbox de tier IV, tous deux reviennent sur la map.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
function join(name) {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: name, version: '1.21.10' });
  bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
  bot.chatLog = []; bot.on('messagestr', m => bot.chatLog.push(m));
  return new Promise(r => bot.once('spawn', () => r(bot)));
}
const where = b => b.game.dimension + ' ' + b.entity.position.floored();
const last = (b, re) => (b.chatLog.filter(m => re.test(m)).pop() || '-').slice(0, 170);
(async () => {
  const a = await join('Invite'); await sleep(3500); const b = await join('Bleu'); await sleep(3500);
  a.chat('/cf team create solo'); b.chat('/cf team create solo'); await sleep(800);
  a.chat('/cf start'); await sleep(3500); a.chat('/cf perso yuki'); b.chat('/cf perso shoja'); await sleep(1500);
  for (let i = 0; i < 40 && !a.chatLog.some(m => /La partie commence/.test(m)); i++) await sleep(500);
  await sleep(32000); console.log('partie :', last(a, /arene de Regna/), '|', where(a), '|', where(b));
  a.chat('/cf ferox lancer'); await sleep(1200); console.log('proposition lue par Bleu :', last(b, /Regna Ferox/));
  a.chat('/cf ferox oui'); await sleep(700); console.log('une equipe :', last(b, /inscrite/));
  b.chat('/cf ferox oui'); await sleep(2500); console.log('arene :', last(a, /affronte/), '|', where(a), '|', where(b), '| mode', b.game.gameMode);
  await sleep(3500);
  a.chat('/damage Bleu 200 minecraft:player_attack by Invite'); await sleep(3000);
  console.log('fin :', last(a, /emporte/), '|', last(a, /perd une vie|derniere vie/));
  console.log('retour :', where(a), '|', where(b), '| mode de Bleu', b.game.gameMode, '| vie de Bleu', b.health);
  console.log('recompense d\'Invite :', a.inventory.items().filter(i => ['diamond', 'gold_ingot', 'dead_bush', 'enchanted_book'].includes(i.name)).map(i => i.name + 'x' + i.count).join(' '));
  a.chat('/cf stop'); await sleep(2500); a.quit(); b.quit(); process.exit(0);
})();
