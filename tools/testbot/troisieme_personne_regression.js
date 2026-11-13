// Non-regression : mode test normal, zone duo, et remise a neuf du testeur en troisieme personne.
const mineflayer = require('mineflayer');
const sleep = ms => new Promise(r => setTimeout(r, ms));
const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Tester', version: '1.21.10' });
bot._client.on('add_resource_pack', d => bot._client.write('resource_pack_receive', { uuid: d.uuid, result: 1 }));
let msgs = []; bot.on('message', m => msgs.push(m.toString()));
bot.on('death', () => { setTimeout(() => bot._client.write('client_command', { actionId: 0 }), 300); });
const dim = async () => { msgs = []; bot.chat('/data get entity @s Dimension'); await sleep(700); return (msgs.join(' ').match(/minecraft:[a-z_0-9]+/) || ['?'])[0]; };
const q = async (cmd) => { msgs = []; bot.chat(cmd); await sleep(800); return msgs.join(' ').slice(0, 160); };
const useSlot = async k => { bot.setQuickBarSlot(k); await sleep(200); bot.activateItem(); await sleep(80); bot.deactivateItem(); await sleep(1500); };
bot.once('spawn', async () => {
  await sleep(4000);
  console.log('connexion :', await dim());
  // 1. Retour au terrain normal et Marth joue lui-meme contre un mannequin d'entrainement.
  if ((await dim()).includes('3p')) { bot.chat('/cf test third-person'); await sleep(2500); }
  bot.chat('/cf change marth'); await sleep(1500);
  bot.chat('/cf mannequin clear'); await sleep(500);
  bot.chat('/tp @s 0.5 65 0.5 0 0'); await sleep(500);
  bot.chat('/cf mannequin 1'); await sleep(1500);
  const before = await q('/data get entity @e[type=zombie,tag=cf_mannequin,limit=1,sort=nearest] Health');
  const z = Object.values(bot.entities).find(e => e.name === 'zombie');
  if (z) await bot.lookAt(z.position.offset(0, 1.5, 0), true);
  await useSlot(8); await sleep(2500);
  const after = await q('/data get entity @e[type=zombie,tag=cf_mannequin,limit=1,sort=nearest] Health');
  console.log('mode normal, ultime de Marth sur un mannequin : avant', before.match(/[0-9.]+f/)?.[0], 'apres', after.match(/[0-9.]+f/)?.[0], '| monde', await dim());
  // 2. Zone duo.
  bot.chat('/cf change ruby'); await sleep(1500);
  bot.chat('/cf test duo weiss'); await sleep(3000);
  console.log('zone duo :', await dim());
  msgs = []; const k = [...Array(9).keys()].find(i => bot.inventory.slots[36 + i] && bot.inventory.slots[36 + i].name === 'amethyst_cluster');
  await useSlot(k); await sleep(1500); if (bot.currentWindow) bot.closeWindow(bot.currentWindow);
  console.log('ultime en duo :', msgs.filter(x => / lancent /.test(x)).join(' ').slice(0, 120) || msgs.join(' ').slice(0, 120));
  // 3. Troisieme personne depuis la zone duo : Ruby passe au mannequin ; le Monstre de Yuki, puis /cf change remet le testeur a neuf.
  bot.chat('/cf test third-person'); await sleep(4000);
  console.log('troisieme personne :', await dim(), '|', (await q('/data get entity @e[tag=cf_mannequin_pilote,limit=1] CustomName')).slice(0, 90));
  bot.chat('/cf change yuki'); await sleep(1500);
  const m = Object.values(bot.entities).find(e => e.name === 'mannequin' && /3e personne/.test(JSON.stringify(e.metadata || [])));
  bot.chat('/tp @s 0.5 65 4 180 0'); await sleep(500); if (m) await bot.lookAt(m.position.offset(0, 1.5, 0), true);
  const k5 = [...Array(9).keys()].find(i => bot.inventory.slots[36 + i] && bot.inventory.slots[36 + i].name === 'nether_star');
  await useSlot(k5); await sleep(3000);
  console.log('apres le Monstre : vie max', (await q('/attribute @s minecraft:max_health get')).match(/is [0-9.]+/)?.[0]);
  bot.chat('/cf change marth'); await sleep(1500);
  console.log('apres /cf change : vie max', (await q('/attribute @s minecraft:max_health get')).match(/is [0-9.]+/)?.[0]);
  bot.quit(); process.exit(0);
});
