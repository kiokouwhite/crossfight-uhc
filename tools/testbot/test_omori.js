// /cf test omori : la vraie porte devant le testeur, le clic droit pour entrer, le boss, l'annonce des attaques
// dans la seconde barre, puis le retrait.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Invite', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
const chat = []; bot.on('messagestr', m => chat.push(m));
const last = re => (chat.filter(m => re.test(m)).pop() || '-').slice(0, 160);
const bars = new Set();
const note = b => { const t = b.title && b.title.toString ? b.title.toString() : String(b.title); if (t) bars.add(t); };
bot.on('bossBarCreated', note); bot.on('bossBarUpdated', note);
bot.once('spawn', async () => {
  await sleep(4000); bot.chat('/cf test omori'); await sleep(4000);
  const door = bot.findBlock({ matching: b => b.name === 'pale_oak_door', maxDistance: 12 });
  console.log(last(/porte blanche/), '| porte :', door ? door.name + ' ' + door.position + ' ' + JSON.stringify(door.getProperties()) : 'absente');
  if (!door) { bot.quit(); process.exit(1); }
  await bot.lookAt(door.position.offset(0.5, 0.5, 0.5)); await bot.activateBlock(door); await sleep(300);
  console.log('ouverte :', bot.blockAt(door.position).getProperties().open);
  await sleep(3000);
  console.log(last(/Espace Blanc/), '| boss :', !!bot.nearestEntity(e => e.name === 'zombie'), '| sol :', bot.blockAt(bot.entity.position.offset(0, -1, 0)).name);
  await sleep(16000);
  console.log('barres vues :', [...bars].join(' / '));
  bot.chat('/cf test omori'); await sleep(2000);
  console.log(last(/retiree/), '| sol :', bot.blockAt(bot.entity.position.offset(0, -1, 0)).name, '| porte restante :', !!bot.findBlock({ matching: b => b.name === 'pale_oak_door', maxDistance: 12 }));
  bot.chat('/cf test'); await sleep(1500); bot.quit(); process.exit(0);
});
