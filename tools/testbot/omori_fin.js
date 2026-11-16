// La fin du combat d'Omori : le sol noir de la derniere phase, sa mort en poussiere, la porte noire qui apparait
// la ou il est tombe, la sortie par cette porte, puis la question du pardon une fois dehors ; et l'Espace Blanc
// redevenu blanc pour la fois suivante.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const t0 = Date.now(); const log = (...a) => console.log(((Date.now() - t0) / 1000).toFixed(2) + 's', ...a);
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const chat = []; bot.on('messagestr', m => chat.push(m));
const last = re => (chat.filter(m => re.test(m)).pop() || '-').slice(0, 170);
const under = (dx = 0, dz = 0) => { const b = bot.blockAt(bot.entity.position.offset(dx, -1, dz)); return b ? b.name : '?'; };
const count = name => Object.values(bot.entities).filter(e => e.name === name).length;
const find = name => bot.findBlock({ matching: b => b.name === name, maxDistance: 40 });
async function enter() {
  bot.chat('/cf test omori'); await sleep(3500);
  const door = find('pale_oak_door');
  if (!door) { log('porte blanche absente'); return false; }
  await bot.lookAt(door.position.offset(0.5, 0.5, 0.5)); await bot.activateBlock(door); await sleep(2500);
  return true;
}
async function walkTo(pos, near) {
  for (let i = 0; i < 150 && bot.entity.position.distanceTo(pos) > near; i++) { await bot.lookAt(pos.offset(0.5, 1, 0.5), true); bot.setControlState('forward', true); await sleep(100); }
  bot.setControlState('forward', false);
}
bot.once('spawn', async () => {
  await sleep(4000);
  if (!await enter()) { bot.quit(); process.exit(1); }
  log('dans l\'Espace Blanc : sol', under(), '| boss :', count('zombie'), '| parties du pantin :', count('item_display'));
  bot.chat('/cf omori phase 5'); await sleep(600);
  log('0,6 s apres le debut de la phase 5 : sol sous les pieds', under(), '| a 15 blocs', under(15, 0));
  await sleep(2000);
  log('2,6 s apres : sol sous les pieds', under(), '| a 15 blocs', under(15, 0), under(-15, 9), '|', last(/cinquieme phase/));
  bot.chat('/cf heal');
  const kill = Date.now();
  bot.chat('/cf omori coups 63'); await sleep(700);
  log(last(/vaincu/), '| barres :', Object.keys(bot.bossBars || {}).length, '| boss :', count('zombie'), '| pantin :', count('item_display'));
  let door = null, seen = [];
  for (let i = 0; i < 48 && !door; i++) { await sleep(250); seen.push(count('item_display')); door = find('dark_oak_door'); }
  log('parties du pantin, toutes les 250 ms :', seen.join(' '));
  if (!door) { log('porte noire jamais apparue'); bot.quit(); process.exit(1); }
  log('porte noire apres', ((Date.now() - kill) / 1000).toFixed(1), 's :', String(door.position), JSON.stringify(door.getProperties()), '| boss :', count('zombie'), '|', last(/porte noire/));
  await walkTo(door.position, 2.2);
  log('devant la porte, a', bot.entity.position.distanceTo(door.position).toFixed(1), 'bloc(s) ; clic droit');
  await bot.lookAt(door.position.offset(0.5, 0.5, 0.5)); await bot.activateBlock(door); await sleep(250);
  log('porte ouverte :', bot.blockAt(door.position).getProperties().open);
  await sleep(900);
  log('apres la porte : sol', under(), '| menu ouvert tout de suite :', !!bot.currentWindow);
  await sleep(1500);
  log('menu du pardon :', bot.currentWindow ? bot.currentWindow.slots.slice(0, 9).filter(Boolean).map(i => i.name).join(' ') : 'pas ouvert');
  if (bot.currentWindow) await bot.clickWindow(3, 0, 0); await sleep(1200);
  log(last(/pardonne/));
  log('--- une nouvelle fois : tout doit etre revenu a l\'etat de depart');
  if (await enter()) log('sol', under(), '| a 15 blocs', under(15, 0), '| porte noire :', !!find('dark_oak_door'), '| boss :', count('zombie'), '| barre :', Object.values(bot.bossBars || {}).map(b => b.title.toString()).join(' / '));
  bot.chat('/cf test omori'); await sleep(1500); bot.chat('/cf test'); await sleep(1500); bot.quit(); process.exit(0);
});
