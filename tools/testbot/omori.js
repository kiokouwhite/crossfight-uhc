// L'evenement Omori : la porte dans l'arene, l'entree dans l'Espace Blanc, les coups comptes, les phases, les
// attaques qui blessent, la victoire et le pardon (une vie de plus).
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const chat = []; bot.on('messagestr', m => chat.push(m));
const last = re => (chat.filter(m => re.test(m)).pop() || '-').slice(0, 200);
const bar = () => { const b = Object.values(bot.bossBars || {})[0] || (bot.bossBars && bot.bossBars[0]); return b ? b.title.toString().slice(0, 60) : 'pas de barre'; };
bot.once('spawn', async () => {
  await sleep(3500); bot.chat('/cf team create solo'); await sleep(600); bot.chat('/cf start'); await sleep(3500); bot.chat('/cf perso yuki');
  for (let i = 0; i < 40 && !chat.some(m => /La partie commence/.test(m)); i++) await sleep(500);
  await sleep(31000);
  bot.chat('/cf omori lancer'); await sleep(1500); const msg = last(/porte blanche/); console.log(msg);
  const m = msg.match(/en (-?\d+) (-?\d+) (-?\d+)/); bot.chat(`/tp @s ${+m[1] + 1} ${m[2]} ${m[3]}`); await sleep(3000);
  console.log('menu de la porte :', bot.currentWindow ? bot.currentWindow.slots.slice(0, 9).filter(Boolean).map(i => i.name).join(' ') : 'pas ouvert');
  if (bot.currentWindow) await bot.clickWindow(3, 0, 0); await sleep(2500);
  const boss = bot.nearestEntity(e => e.name === 'zombie'); console.log('Espace Blanc :', last(/Espace Blanc/), '| boss a', boss ? boss.position.distanceTo(bot.entity.position).toFixed(1) : '?', 'blocs | sous les pieds :', bot.blockAt(bot.entity.position.offset(0, -1, 0)).name);
  const hp0 = bot.health; await sleep(9000); console.log('apres 9 s d\'attaques : vie', hp0, '->', bot.health);
  if (boss) { bot.chat(`/tp @s ${boss.position.x + 1.5} ${boss.position.y} ${boss.position.z}`); await sleep(800); await bot.lookAt(boss.position.offset(0, 1.2, 0)); for (let i = 0; i < 3; i++) { bot.attack(boss); await sleep(500); } }
  bot.chat('/cf heal'); bot.chat('/effect give @s minecraft:resistance 600 4 true'); await sleep(500);
  for (const [n, label] of [[30, 'phase 2'], [35, 'phase 3'], [40, 'phase 4'], [45, 'phase 5']]) { bot.chat('/cf omori coups ' + n); await sleep(1500); console.log('+' + n + ' coups :', last(/Omori : /)); }
  await sleep(6500); console.log('phase 5, effets :', Object.values(bot.entity.effects).map(e => e.id).join(','));
  bot.chat('/cf omori coups 50'); await sleep(3500); console.log('victoire :', last(/vaincu/), '| monde :', bot.game.dimension, bot.entity.position.floored().toString());
  console.log('menu du pardon :', bot.currentWindow ? bot.currentWindow.slots.slice(0, 9).filter(Boolean).map(i => i.name).join(' ') : 'pas ouvert');
  if (bot.currentWindow) await bot.clickWindow(3, 0, 0); await sleep(1200); console.log(last(/pardonne/));
  bot.chat('/cf stop'); await sleep(2500); bot.quit(); process.exit(0);
});
